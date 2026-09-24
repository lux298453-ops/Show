<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import {
  type VectorPoint,
  type VectorPathData,
  pointsToSvgPath,
  calculateBoundingBox,
  normalizePointsToOrigin,
  applyBendAlgorithm,
  togglePointType,
  rdpSimplify,
  catmullRomToBezier,
  cutOpenPathAt,
  cutClosedPathAt,
  cutOpenAtAnchor,
  cutClosedAtAnchor,
} from '@/utils/vectorEngine'

const props = withDefaults(
  defineProps<{
    activeTool: 'pen' | 'pencil'
    activeSubTool: 'move' | 'lasso' | 'paint' | 'bend' | 'cut' | 'erase'
    scale?: number
    artboardW: number
    artboardH: number
    initialPath?: VectorPathData | null
    initialLeft?: number
    initialTop?: number
    isSpacePressed?: boolean
  }>(),
  {
    scale: 1,
    initialPath: null,
    initialLeft: 0,
    initialTop: 0,
    isSpacePressed: false,
  },
)

const emit = defineEmits<{
  (e: 'commit', result: {
    html: string
    left: number
    top: number
    width: number
    height: number
    vectorData: VectorPathData
  }[]): void
  (e: 'cancel'): void
  (e: 'delete-original'): void
  (e: 'update:activeSubTool', tool: 'move' | 'lasso' | 'paint' | 'bend' | 'cut' | 'erase'): void
  (e: 'update:isClosed', closed: boolean): void
  (e: 'toast', msg: string): void
}>()

// 正在编辑的当前路径点集合。剪刀拆开的另一段先留在这里，按 Enter 再写成独立图形。
const points = ref<VectorPoint[]>([])
const loosePaths = ref<VectorPoint[][]>([])
const isClosed = ref(false)
const rootRef = ref<HTMLElement | null>(null)
let cutSeq = 0
const strokeColor = ref('#000000')
const strokeWidth = ref(2)
const fillColor = ref('none')

// 绘制过程临时状态
const mousePos = ref<{ x: number; y: number }>({ x: 0, y: 0 })
const isDraggingHandle = ref(false)
const dragStartPos = ref<{ x: number; y: number }>({ x: 0, y: 0 })
const currentDragPointIndex = ref<number | null>(null)
const selectedPointIndex = ref<number | null>(null)
const isAltPressed = ref(false)
const isShiftPressed = ref(false)

// 铅笔手绘临时采样点
const rawPencilPoints = ref<{ x: number; y: number }[]>([])
const isPencilDrawing = ref(false)

// Bend 弯曲状态
const isBending = ref(false)
const bendSegmentIndex = ref<number | null>(null)
const hoveredSegmentIndex = ref<number | null>(null)

// 拖动已有锚点 (Move Tool)。pathWhich = -1 是当前这段，>=0 是剪刀拆开的另一段。
const isDraggingAnchor = ref(false)
const dragAnchorIndex = ref<number | null>(null)
const dragAnchorPath = ref(-1)
const dragAnchorOffset = ref<{ x: number; y: number }>({ x: 0, y: 0 })
const selectedPathWhich = ref(-1)

// 拖动已有手柄 (Move Tool)
const isDraggingHandleObj = ref<{ pathWhich: number; ptIdx: number; which: 'in' | 'out' } | null>(null)

// 橡皮擦状态
const isErasing = ref(false)

// 当前悬停的线段
const hoveredSegment = computed(() => {
  if (hoveredSegmentIndex.value === null || points.value.length < 2) return null
  const i = hoveredSegmentIndex.value
  const p0 = points.value[i]
  const p1 = points.value[(i + 1) % points.value.length]
  return p0 && p1 ? { p0, p1 } : null
})

// 是否是二次编辑已有图形
const isEditingExisting = computed(() => !!props.initialPath)

// 初始化已有图形或新建
watch(
  () => props.initialPath,
  (newVal) => {
    if (newVal && newVal.points) {
      // 深度拷贝已有锚点
      const ox = props.initialLeft || 0
      const oy = props.initialTop || 0
      points.value = newVal.points.map((p) => ({
        id: p.id,
        x: p.x + ox,
        y: p.y + oy,
        handleIn: p.handleIn ? { ...p.handleIn } : null,
        handleOut: p.handleOut ? { ...p.handleOut } : null,
        type: p.type,
      }))
      loosePaths.value = []
      isClosed.value = !!newVal.closed
      strokeColor.value = newVal.strokeColor || '#000000'
      strokeWidth.value = newVal.strokeWidth || 2
      fillColor.value = newVal.fillColor || 'none'
      emit('update:isClosed', isClosed.value)
    } else {
      points.value = []
      loosePaths.value = []
      isClosed.value = false
      strokeColor.value = '#000000'
      strokeWidth.value = 2
      fillColor.value = 'none'
      emit('update:isClosed', false)
    }
  },
  { immediate: true },
)

// 实时 SVG 路径 d 字符串
const currentSvgD = computed(() => {
  return pointsToSvgPath(points.value, isClosed.value)
})

const nodeGroups = computed(() => {
  const groups: { key: string; pts: VectorPoint[]; main: boolean; which: number }[] = [
    { key: 'main', pts: points.value, main: true, which: -1 },
  ]
  loosePaths.value.forEach((pts, i) => {
    groups.push({ key: 'loose-' + i, pts, main: false, which: i })
  })
  return groups
})

// 橡皮筋预览线：从最后一个锚点到当前鼠标位置
const rubberBandD = computed(() => {
  if (isClosed.value || points.value.length === 0 || props.activeTool !== 'pen' || (props.activeSubTool && props.activeSubTool !== 'pen') || isPencilDrawing.value) {
    return ''
  }
  const last = points.value[points.value.length - 1]
  let curX = mousePos.value.x
  let curY = mousePos.value.y

  // 按住 Shift 锁定 0°/45°/90°
  if (isShiftPressed.value) {
    const dx = curX - last.x
    const dy = curY - last.y
    const angle = Math.atan2(dy, dx)
    const snapAngle = Math.round(angle / (Math.PI / 4)) * (Math.PI / 4)
    const dist = Math.hypot(dx, dy)
    curX = last.x + dist * Math.cos(snapAngle)
    curY = last.y + dist * Math.sin(snapAngle)
  }

  // 若上一个点有出射手柄，渲染贝塞尔预览线；否则直线预览
  if (last.handleOut && (Math.abs(last.handleOut.x) > 0.1 || Math.abs(last.handleOut.y) > 0.1)) {
    const cp1x = last.x + last.handleOut.x
    const cp1y = last.y + last.handleOut.y
    return `M ${last.x} ${last.y} C ${cp1x} ${cp1y}, ${curX} ${curY}, ${curX} ${curY}`
  }
  return `M ${last.x} ${last.y} L ${curX} ${curY}`
})

// 铅笔实时手绘折线预览
const pencilPreviewD = computed(() => {
  if (!isPencilDrawing.value || rawPencilPoints.value.length === 0) return ''
  return 'M ' + rawPencilPoints.value.map((p) => `${p.x} ${p.y}`).join(' L ')
})

// 悬停线段高亮路径 (用于 Bend/Cut/Erase)
const hoveredSegmentD = computed(() => {
  if (hoveredSegmentIndex.value === null || points.value.length < 2) return ''
  const idx = hoveredSegmentIndex.value
  const p0 = points.value[idx]
  const p1 = points.value[(idx + 1) % points.value.length]
  if (!p0 || !p1) return ''
  const hasP0Out = p0.handleOut && (Math.abs(p0.handleOut.x) > 0.1 || Math.abs(p0.handleOut.y) > 0.1)
  const hasP1In = p1.handleIn && (Math.abs(p1.handleIn.x) > 0.1 || Math.abs(p1.handleIn.y) > 0.1)
  if (!hasP0Out && !hasP1In) {
    return `M ${p0.x} ${p0.y} L ${p1.x} ${p1.y}`
  }
  const cp1x = hasP0Out ? p0.x + p0.handleOut!.x : p0.x
  const cp1y = hasP0Out ? p0.y + p0.handleOut!.y : p0.y
  const cp2x = hasP1In ? p1.x + p1.handleIn!.x : p1.x
  const cp2y = hasP1In ? p1.y + p1.handleIn!.y : p1.y
  return `M ${p0.x} ${p0.y} C ${cp1x} ${cp1y}, ${cp2x} ${cp2y}, ${p1.x} ${p1.y}`
})

// 靠近起点（10px）吸附判定
const isNearFirstPoint = computed(() => {
  if (isClosed.value || points.value.length < 2) return false
  const first = points.value[0]
  const dist = Math.hypot(mousePos.value.x - first.x, mousePos.value.y - first.y)
  return dist <= screenToUser(8)
})

/** 屏幕上 1 像素对应画板里的长度。节点用这个换算，放大后不会跟着变大。 */
function userPerScreen() {
  return 1 / Math.max(props.scale || 1, 0.05)
}

function screenToUser(screenPx: number) {
  return screenPx * userPerScreen()
}

const anchorSize = computed(() => screenToUser(2))
const anchorStroke = computed(() => screenToUser(1))
const handleRadius = computed(() => screenToUser(1.25))
const handleStroke = computed(() => screenToUser(1))

function nextCutId() {
  cutSeq += 1
  return 'vp_cut_' + cutSeq + '_' + Math.random().toString(36).substring(2, 5)
}

/** 获取画板内本地坐标。用覆盖层实际宽高换算，避免鼠标进到节点上时坐标系跳动。 */
function getLocalCoords(e: MouseEvent): { x: number; y: number } {
  const target = rootRef.value || (e.currentTarget as HTMLElement | null)
  if (!target) return { x: 0, y: 0 }
  const rect = target.getBoundingClientRect()
  const w = rect.width || 1
  const h = rect.height || 1
  return {
    x: ((e.clientX - rect.left) / w) * props.artboardW,
    y: ((e.clientY - rect.top) / h) * props.artboardH,
  }
}

/** 计算三阶贝塞尔线段上的插值点 */
function getBezierPoint(p0: VectorPoint, p1: VectorPoint, t: number): { x: number; y: number } {
  const hasP0Out = p0.handleOut && (Math.abs(p0.handleOut.x) > 0.1 || Math.abs(p0.handleOut.y) > 0.1)
  const hasP1In = p1.handleIn && (Math.abs(p1.handleIn.x) > 0.1 || Math.abs(p1.handleIn.y) > 0.1)
  if (!hasP0Out && !hasP1In) {
    return {
      x: p0.x + t * (p1.x - p0.x),
      y: p0.y + t * (p1.y - p0.y),
    }
  }
  const cp1x = p0.x + (p0.handleOut ? p0.handleOut.x : 0)
  const cp1y = p0.y + (p0.handleOut ? p0.handleOut.y : 0)
  const cp2x = p1.x + (p1.handleIn ? p1.handleIn.x : 0)
  const cp2y = p1.y + (p1.handleIn ? p1.handleIn.y : 0)
  const u = 1 - t
  const tt = t * t
  const uu = u * u
  const uuu = uu * u
  const ttt = tt * t
  return {
    x: uuu * p0.x + 3 * uu * t * cp1x + 3 * u * tt * cp2x + ttt * p1.x,
    y: uuu * p0.y + 3 * uu * t * cp1y + 3 * u * tt * cp2y + ttt * p1.y,
  }
}

/** 检测光标到指定线段的最近距离 (采样 10 个贝塞尔插值点) */
function distToSegment(pos: { x: number; y: number }, segIdx: number): number {
  const p0 = points.value[segIdx]
  const p1 = points.value[(segIdx + 1) % points.value.length]
  if (!p0 || !p1) return Infinity

  let minDist = Infinity
  for (let s = 0; s <= 10; s++) {
    const pt = getBezierPoint(p0, p1, s / 10)
    const d = Math.hypot(pos.x - pt.x, pos.y - pt.y)
    if (d < minDist) minDist = d
  }
  return minDist
}

/** 查找最近的线段索引 */
function findNearestSegment(pos: { x: number; y: number }, threshold = 18): number | null {
  if (points.value.length < 2) return null
  const n = isClosed.value ? points.value.length : points.value.length - 1
  let bestIdx: number | null = null
  let bestDist = Infinity

  for (let i = 0; i < n; i++) {
    const d = distToSegment(pos, i)
    if (d <= threshold && d < bestDist) {
      bestDist = d
      bestIdx = i
    }
  }
  return bestIdx
}

function pathPoints(which: number): VectorPoint[] | null {
  if (which < 0) return points.value
  return loosePaths.value[which] || null
}

/** 在当前这段和剪刀拆开的每一段里找最近的锚点 */
function findAnchorAnywhere(pos: { x: number; y: number }, threshold = 0): { which: number; idx: number } | null {
  const limit = threshold > 0 ? threshold : screenToUser(12)
  let best: { which: number; idx: number; dist: number } | null = null
  const groups = [{ which: -1, pts: points.value }]
  loosePaths.value.forEach((pts, i) => groups.push({ which: i, pts }))
  for (const group of groups) {
    for (let i = 0; i < group.pts.length; i++) {
      const d = Math.hypot(pos.x - group.pts[i].x, pos.y - group.pts[i].y)
      if (d > limit) continue
      // 切开处两个节点叠在一起时，优先抓后画的那段，拖开后底下那个才能再被点到
      const closer = !best || d < best.dist - 0.4 || (Math.abs(d - best.dist) <= 0.4 && group.which > best.which)
      if (closer) best = { which: group.which, idx: i, dist: d }
    }
  }
  return best ? { which: best.which, idx: best.idx } : null
}

/** 查找最近的锚点索引（只看当前这段，给钢笔续画用） */
function findNearestAnchor(pos: { x: number; y: number }, threshold = 0): number | null {
  const limit = threshold > 0 ? threshold : screenToUser(8)
  let bestIdx: number | null = null
  let bestDist = Infinity
  for (let i = 0; i < points.value.length; i++) {
    const d = Math.hypot(pos.x - points.value[i].x, pos.y - points.value[i].y)
    if (d <= limit && d < bestDist) {
      bestDist = d
      bestIdx = i
    }
  }
  return bestIdx
}

/** 查找最近的手柄 */
function findNearestHandle(pos: { x: number; y: number }, threshold = 14): { pathWhich: number; ptIdx: number; which: 'in' | 'out' } | null {
  const groups = [{ which: -1, pts: points.value }]
  loosePaths.value.forEach((pts, i) => groups.push({ which: i, pts }))
  for (const group of groups) {
    for (let i = 0; i < group.pts.length; i++) {
      const p = group.pts[i]
      if (p.handleIn && (Math.abs(p.handleIn.x) > 0.1 || Math.abs(p.handleIn.y) > 0.1)) {
        const hx = p.x + p.handleIn.x
        const hy = p.y + p.handleIn.y
        if (Math.hypot(pos.x - hx, pos.y - hy) <= threshold) {
          return { pathWhich: group.which, ptIdx: i, which: 'in' }
        }
      }
      if (p.handleOut && (Math.abs(p.handleOut.x) > 0.1 || Math.abs(p.handleOut.y) > 0.1)) {
        const hx = p.x + p.handleOut.x
        const hy = p.y + p.handleOut.y
        if (Math.hypot(pos.x - hx, pos.y - hy) <= threshold) {
          return { pathWhich: group.which, ptIdx: i, which: 'out' }
        }
      }
    }
  }
  return null
}

/** 填色处理 (Paint Bucket) */
function handlePaintClick(pos: { x: number; y: number }) {
  if (points.value.length < 3) {
    emit('toast', '至少需要 3 个锚点才可闭合填充')
    return
  }
  if (!isClosed.value) {
    isClosed.value = true
    emit('update:isClosed', true)
    fillColor.value = fillColor.value === 'none' ? '#0D99FF' : 'none'
    emit('toast', fillColor.value === 'none' ? '已移除填充色' : '路径已自动闭合并填充天蓝色')
  } else {
    fillColor.value = fillColor.value === 'none' ? '#0D99FF' : 'none'
    emit('toast', fillColor.value === 'none' ? '已移除填充色' : '已填充天蓝色背景')
  }
}

function toggleFill() {
  handlePaintClick(mousePos.value)
}

/** 在当前路径或已经剪开的那段上，找到离点击最近的线上位置 */
function locateCutPoint(pos: { x: number; y: number }, threshold: number) {
  const paths: { pts: VectorPoint[]; closed: boolean; which: number }[] = [
    { pts: points.value, closed: isClosed.value, which: -1 },
  ]
  loosePaths.value.forEach((pts, i) => paths.push({ pts, closed: false, which: i }))

  let best: { which: number; segIdx: number; t: number; dist: number } | null = null
  for (const path of paths) {
    if (path.pts.length < 2) continue
    const n = path.closed ? path.pts.length : path.pts.length - 1
    for (let i = 0; i < n; i++) {
      const p0 = path.pts[i]
      const p1 = path.pts[(i + 1) % path.pts.length]
      if (!p0 || !p1) continue
      for (let s = 1; s < 24; s++) {
        const t = s / 24
        const pt = getBezierPoint(p0, p1, t)
        const dist = Math.hypot(pos.x - pt.x, pos.y - pt.y)
        if (dist <= threshold && (!best || dist < best.dist)) {
          best = { which: path.which, segIdx: i, t, dist }
        }
      }
    }
  }
  return best
}

function applyCutPieces(which: number, part1: VectorPoint[], part2: VectorPoint[] | null) {
  if (which < 0) {
    points.value = part1
    isClosed.value = false
    emit('update:isClosed', false)
    selectedPointIndex.value = Math.max(0, part1.length - 1)
    if (part2) loosePaths.value = [...loosePaths.value, part2]
  } else if (part2) {
    const next = loosePaths.value.slice()
    next.splice(which, 1, part1, part2)
    loosePaths.value = next
  } else {
    const next = loosePaths.value.slice()
    next.splice(which, 1, part1)
    loosePaths.value = next
  }
}

/** 剪刀：在点击处增加节点，把这一段拆开，不删整条线 */
function handleCutClick(pos: { x: number; y: number }) {
  const hit = locateCutPoint(pos, screenToUser(14))
  if (!hit) {
    emit('toast', '请点在线条上，会在该处加一个节点并拆开')
    return
  }

  const src = hit.which < 0 ? points.value : loosePaths.value[hit.which]
  const closed = hit.which < 0 ? isClosed.value : false
  if (!src || src.length < 2) {
    emit('toast', '这条线太短，没法拆开')
    return
  }

  if (hit.t <= 0.06) {
    splitPathAtAnchor(hit.which, hit.segIdx)
    return
  }
  const endIdx = (hit.segIdx + 1) % src.length
  if (hit.t >= 0.94) {
    splitPathAtAnchor(hit.which, closed ? endIdx : endIdx)
    return
  }

  if (closed) {
    const opened = cutClosedPathAt(src, hit.segIdx, hit.t, nextCutId)
    if (!opened) {
      emit('toast', '这里没法剪开')
      return
    }
    applyCutPieces(hit.which, opened, null)
    emit('toast', '已在点击处加上节点，闭合路径从这里打开')
    return
  }

  const pieces = cutOpenPathAt(src, hit.segIdx, hit.t, nextCutId)
  if (!pieces) {
    emit('toast', '这里没法剪开')
    return
  }
  applyCutPieces(hit.which, pieces[0], pieces[1])
  emit('toast', '已在点击处加上节点，线条分成了两段')
}

function splitPathAtAnchor(which: number, idx: number) {
  const src = which < 0 ? points.value : loosePaths.value[which]
  const closed = which < 0 ? isClosed.value : false
  if (!src) return
  if (closed) {
    const opened = cutClosedAtAnchor(src, idx, nextCutId)
    if (!opened) return
    applyCutPieces(which, opened, null)
    emit('toast', '已在这个节点处把闭合路径剪开')
    return
  }
  const pieces = cutOpenAtAnchor(src, idx, nextCutId)
  if (!pieces) {
    emit('toast', '端点不能再拆，请点在线条中间')
    return
  }
  applyCutPieces(which, pieces[0], pieces[1])
  emit('toast', '已在这个节点处把线条分成两段')
}

/** 在线段处剪开 */
function splitAtSegment(segIdx: number) {
  if (isClosed.value) {
    // 闭合路径剪第一刀：解闭合为一条开口路径
    const reordered: VectorPoint[] = []
    const n = points.value.length
    for (let i = 0; i < n; i++) {
      reordered.push(points.value[(segIdx + 1 + i) % n])
    }
    points.value = reordered
    isClosed.value = false
    emit('update:isClosed', false)
    emit('toast', '已将闭合路径剪切为开口路径')
  } else {
    // 开口路径拆分成两条独立路径并生成两个 SVG
    const part1 = points.value.slice(0, segIdx + 1)
    const part2 = points.value.slice(segIdx + 1)
    if (part1.length >= 2 && part2.length >= 2) {
      const s1 = buildSvgResult(part1, false)
      const s2 = buildSvgResult(part2, false)
      emit('commit', [s1, s2])
      emit('toast', '已将路径剪裁拆分为 2 个独立矢量图形')
    } else {
      emit('toast', '拆分后点数不足，未执行剪切')
    }
  }
}

/** 橡皮擦处理 (Erase) */
function eraseAtPos(pos: { x: number; y: number }) {
  const nearPt = findNearestAnchor(pos, screenToUser(8))
  if (nearPt !== null) {
    points.value.splice(nearPt, 1)
    selectedPointIndex.value = null
    emit('toast', '已擦除锚点')
    if (points.value.length < 2) {
      if (isEditingExisting.value) emit('delete-original')
      else emit('cancel')
    }
    return
  }

  const nearSeg = findNearestSegment(pos, 18)
  if (nearSeg !== null) {
    if (isClosed.value) {
      splitAtSegment(nearSeg)
      emit('toast', '已擦除闭合边')
    } else {
      if (nearSeg === 0) {
        points.value.shift()
      } else if (nearSeg === points.value.length - 2) {
        points.value.pop()
      } else {
        splitAtSegment(nearSeg)
      }
      emit('toast', '已擦除线段')
    }
    if (points.value.length < 2) {
      if (isEditingExisting.value) emit('delete-original')
      else emit('cancel')
    }
  }
}

/** 弯曲工具按下 (Bend) */
function handleBendDown(pos: { x: number; y: number }) {
  const nearPt = findNearestAnchor(pos, screenToUser(8))
  if (nearPt !== null) {
    const pt = points.value[nearPt]
    const prev = nearPt > 0 ? points.value[nearPt - 1] : (isClosed.value ? points.value[points.value.length - 1] : null)
    const next = nearPt < points.value.length - 1 ? points.value[nearPt + 1] : (isClosed.value ? points.value[0] : null)
    togglePointType(pt, prev, next)
    emit('toast', pt.type === 'corner' ? '已转为角点' : '已转为平滑手柄')
    return
  }

  const nearSeg = findNearestSegment(pos, 22)
  if (nearSeg !== null) {
    isBending.value = true
    bendSegmentIndex.value = nearSeg
  } else {
    emit('toast', '按住任意线段拖拽即可拉弯，或点击锚点重置手柄')
  }
}

function beginAnchorDrag(hit: { which: number; idx: number }, pos: { x: number; y: number }) {
  const list = pathPoints(hit.which)
  const pt = list?.[hit.idx]
  if (!pt) return
  selectedPathWhich.value = hit.which
  selectedPointIndex.value = hit.idx
  isDraggingAnchor.value = true
  dragAnchorPath.value = hit.which
  dragAnchorIndex.value = hit.idx
  dragAnchorOffset.value = {
    x: pos.x - pt.x,
    y: pos.y - pt.y,
  }
}

/** 移动工具按下 (Move) */
function handleMoveDown(pos: { x: number; y: number }) {
  const handleHit = findNearestHandle(pos, screenToUser(12))
  if (handleHit) {
    isDraggingHandleObj.value = handleHit
    selectedPathWhich.value = handleHit.pathWhich
    selectedPointIndex.value = handleHit.ptIdx
    return
  }

  const nearPt = findAnchorAnywhere(pos, screenToUser(12))
  if (nearPt) {
    beginAnchorDrag(nearPt, pos)
    return
  }

  selectedPointIndex.value = null
  selectedPathWhich.value = -1
}

/** 钢笔工具按下 (Pen) */
function handlePenDown(pos: { x: number; y: number }) {
  if (isNearFirstPoint.value) {
    isClosed.value = true
    emit('update:isClosed', true)
    emit('toast', '路径已闭合，按 Enter 完成或使用油漆桶上色')
    return
  }

  if (isClosed.value) {
    commitPath()
    return
  }

  const nearPt = findNearestAnchor(pos, screenToUser(8))
  if (nearPt !== null && isEditingExisting.value) {
    selectedPointIndex.value = nearPt
    isDraggingAnchor.value = true
    dragAnchorIndex.value = nearPt
    dragAnchorOffset.value = {
      x: pos.x - points.value[nearPt].x,
      y: pos.y - points.value[nearPt].y,
    }
    return
  }

  const newPoint: VectorPoint = {
    id: 'vp_' + Date.now() + '_' + Math.random().toString(36).substring(2, 5),
    x: pos.x,
    y: pos.y,
    handleIn: null,
    handleOut: null,
    type: 'corner',
  }
  points.value.push(newPoint)
  currentDragPointIndex.value = points.value.length - 1
  selectedPointIndex.value = points.value.length - 1
  isDraggingHandle.value = true
  dragStartPos.value = pos
}

function dispatchPointerDown(pos: { x: number; y: number }) {
  mousePos.value = pos

  // 1. 油漆桶 (Paint Bucket)
  if (props.activeSubTool === 'paint') {
    handlePaintClick(pos)
    return
  }

  // 2. 剪刀 (Cut)
  if (props.activeSubTool === 'cut') {
    handleCutClick(pos)
    return
  }

  // 3. 橡皮擦 (Erase)
  if (props.activeSubTool === 'erase') {
    isErasing.value = true
    eraseAtPos(pos)
    return
  }

  // 4. 弯曲工具 (Bend)
  if (props.activeSubTool === 'bend') {
    handleBendDown(pos)
    return
  }

  // 5. 铅笔模式 (Pencil)
  if (props.activeTool === 'pencil') {
    isPencilDrawing.value = true
    rawPencilPoints.value = [pos]
    return
  }

  // 6. 移动工具 (Move) - 选择与拖动已有锚点/手柄
  if (props.activeSubTool === 'move') {
    handleMoveDown(pos)
    return
  }

  // 7. 钢笔模式 (Pen)
  if (props.activeTool === 'pen') {
    handlePenDown(pos)
  }
}

/** 鼠标按下总入口 */
function onMouseDown(e: MouseEvent) {
  if (props.isSpacePressed || e.button !== 0) return
  dispatchPointerDown(getLocalCoords(e))
}

function onOverlayDblClick(e: MouseEvent) {
  if (props.isSpacePressed) return
  const idx = findNearestAnchor(getLocalCoords(e), screenToUser(10))
  if (idx === null) return
  onAnchorDblClick(idx, e)
}

function externalPointerDown(pos: { x: number; y: number }) {
  if (props.isSpacePressed) return
  dispatchPointerDown(pos)
}

function externalPointerUp() {
  onMouseUp()
}

function hasActivePoints() {
  return points.value.length > 0 || isPencilDrawing.value
}

/** 鼠标移动 */
function onMouseMove(e: MouseEvent) {
  if (props.isSpacePressed) return
  const pos = getLocalCoords(e)
  mousePos.value = pos
  const buttonDown = (e.buttons & 1) === 1

  // 没按着左键时不能改节点位置。否则鼠标只是移到节点上，节点也会被当成正在拖动。
  if (!buttonDown && (isDraggingAnchor.value || isDraggingHandle.value || isDraggingHandleObj.value || isErasing.value || isBending.value || isPencilDrawing.value)) {
    onMouseUp()
  }

  // 1. 橡皮擦按住拖拽擦除
  if (isErasing.value && props.activeSubTool === 'erase') {
    eraseAtPos(pos)
    return
  }

  // 2. 拖动已有锚点 (Move Tool)，当前这段和剪刀拆开的那段都能拖
  if (isDraggingAnchor.value && dragAnchorIndex.value !== null) {
    const pt = pathPoints(dragAnchorPath.value)?.[dragAnchorIndex.value]
    if (pt) {
      pt.x = pos.x - dragAnchorOffset.value.x
      pt.y = pos.y - dragAnchorOffset.value.y
    }
    return
  }

  // 3. 拖动已有手柄 (Move Tool)
  if (isDraggingHandleObj.value) {
    const { pathWhich, ptIdx, which } = isDraggingHandleObj.value
    const pt = pathPoints(pathWhich)?.[ptIdx]
    if (pt) {
      let dx = pos.x - pt.x
      let dy = pos.y - pt.y
      if (isShiftPressed.value) {
        const angle = Math.atan2(dy, dx)
        const snapAngle = Math.round(angle / (Math.PI / 4)) * (Math.PI / 4)
        const dist = Math.hypot(dx, dy)
        dx = dist * Math.cos(snapAngle)
        dy = dist * Math.sin(snapAngle)
      }
      if (which === 'out') {
        pt.handleOut = { x: dx, y: dy }
        if (!isAltPressed.value && pt.type === 'symmetric') {
          pt.handleIn = { x: -dx, y: -dy }
        }
      } else {
        pt.handleIn = { x: dx, y: dy }
        if (!isAltPressed.value && pt.type === 'symmetric') {
          pt.handleOut = { x: -dx, y: -dy }
        }
      }
    }
    return
  }

  // 4. 钢笔拖动手柄。移动超过几像素才拉出手柄，避免点击后手柄粘着光标乱漂。
  if (buttonDown && isDraggingHandle.value && currentDragPointIndex.value !== null) {
    const pt = points.value[currentDragPointIndex.value]
    if (pt) {
      const arm = Math.hypot(pos.x - dragStartPos.value.x, pos.y - dragStartPos.value.y)
      if (arm < screenToUser(4)) return
      let dx = pos.x - pt.x
      let dy = pos.y - pt.y
      if (isShiftPressed.value) {
        const angle = Math.atan2(dy, dx)
        const snapAngle = Math.round(angle / (Math.PI / 4)) * (Math.PI / 4)
        const dist = Math.hypot(dx, dy)
        dx = dist * Math.cos(snapAngle)
        dy = dist * Math.sin(snapAngle)
      }
      pt.handleOut = { x: dx, y: dy }
      if (!isAltPressed.value) {
        pt.handleIn = { x: -dx, y: -dy }
        pt.type = 'symmetric'
      } else {
        pt.type = 'disconnected'
      }
    }
    return
  }

  // 5. 铅笔自由手绘收集点（距离上一采样点 >= 2px 才记录）
  if (isPencilDrawing.value) {
    const pts = rawPencilPoints.value
    const last = pts[pts.length - 1]
    if (!last || Math.hypot(pos.x - last.x, pos.y - last.y) >= 2) {
      pts.push(pos)
    }
    return
  }

  // 6. Bend 弯曲拖动中
  if (isBending.value && bendSegmentIndex.value !== null) {
    const idx = bendSegmentIndex.value
    const p0 = points.value[idx]
    const p1 = points.value[(idx + 1) % points.value.length]
    if (p0 && p1) {
      applyBendAlgorithm(p0, p1, pos)
    }
    return
  }

  // 7. 悬停线段检测 (Bend / Cut / Erase)
  if (props.activeSubTool === 'bend' || props.activeSubTool === 'cut' || props.activeSubTool === 'erase') {
    hoveredSegmentIndex.value = findNearestSegment(pos, 18)
  } else {
    hoveredSegmentIndex.value = null
  }
}

/** 鼠标抬起 */
function onMouseUp() {
  if (isDraggingHandle.value) {
    isDraggingHandle.value = false
    currentDragPointIndex.value = null
  }
  if (isDraggingAnchor.value) {
    isDraggingAnchor.value = false
    dragAnchorIndex.value = null
    dragAnchorPath.value = -1
  }
  if (isDraggingHandleObj.value) {
    isDraggingHandleObj.value = null
  }
  if (isErasing.value) {
    isErasing.value = false
  }

  // 铅笔松手：执行 RDP 降噪与贝塞尔样条转换
  if (isPencilDrawing.value) {
    isPencilDrawing.value = false
    const raw = rawPencilPoints.value
    rawPencilPoints.value = []

    if (raw.length < 2) return

    // 距离起点 <= 10px 则闭合
    const first = raw[0]
    const last = raw[raw.length - 1]
    const closed = Math.hypot(last.x - first.x, last.y - first.y) <= 10

    // RDP 抽点降噪 (容差 1.5px)
    const simplified = rdpSimplify(raw, 1.5)
    if (simplified.length < 2) {
      emit('toast', '绘制距离过短')
      return
    }

    // 转为三阶贝塞尔锚点
    const pts = catmullRomToBezier(simplified, closed)
    const strokeResult = buildSvgResult(pts, closed)
    // 提交此笔手绘
    emit('commit', [strokeResult])
    // 关键：清空当前临时绘制点，保留铅笔模式，准备下一笔手绘！
    points.value = []
    isClosed.value = false
    emit('update:isClosed', false)
  }

  // 弯曲结束
  if (isBending.value) {
    isBending.value = false
    bendSegmentIndex.value = null
  }
}

/** 双击锚点：切换尖角与共线手柄 */
function onAnchorDblClick(idx: number, e: MouseEvent) {
  e.stopPropagation()
  const pt = points.value[idx]
  if (!pt) return
  const prev = idx > 0 ? points.value[idx - 1] : (isClosed.value ? points.value[points.value.length - 1] : null)
  const next = idx < points.value.length - 1 ? points.value[idx + 1] : (isClosed.value ? points.value[0] : null)
  togglePointType(pt, prev, next)
}

/** 构建单个 SVG 成果对象 */
function buildSvgResult(pts: VectorPoint[], closed: boolean) {
  const box = calculateBoundingBox(pts, strokeWidth.value, closed)
  const normPoints = normalizePointsToOrigin(pts, box.minX, box.minY)
  const dStr = pointsToSvgPath(normPoints, closed)

  const vData: VectorPathData = {
    origW: Math.max(1, box.width),
    origH: Math.max(1, box.height),
    points: normPoints,
    closed: closed,
    strokeColor: strokeColor.value,
    strokeWidth: strokeWidth.value,
    fillColor: fillColor.value,
  }

  const vJson = JSON.stringify(vData).replace(/"/g, '&quot;')
  const svgHtml = `<svg class="wf-el wf-vector-shape" style="position: absolute; left: ${box.minX}px; top: ${box.minY}px; width: ${box.width}px; height: ${box.height}px; overflow: visible; box-sizing: border-box; z-index: 999;" viewBox="0 0 ${box.width} ${box.height}" data-wf-vector="${vJson}"><path d="${dStr}" fill="${fillColor.value}" stroke="${strokeColor.value}" stroke-width="${strokeWidth.value}" stroke-linecap="round" stroke-linejoin="round"/></svg>`

  return {
    html: svgHtml,
    left: box.minX,
    top: box.minY,
    width: box.width,
    height: box.height,
    vectorData: vData,
  }
}

/** 提交当前绘制成果 */
function commitPath() {
  const pieces: { pts: VectorPoint[]; closed: boolean }[] = []
  if (points.value.length >= 2) pieces.push({ pts: points.value, closed: isClosed.value })
  for (const extra of loosePaths.value) {
    if (extra.length >= 2) pieces.push({ pts: extra, closed: false })
  }
  if (pieces.length === 0) {
    // 铅笔每一笔在松手时已经入画板。空路径再按 Enter / 完成，不能把铅笔模式关掉。
    if (props.activeTool === 'pencil' && !isEditingExisting.value) return
    if (isEditingExisting.value) {
      emit('delete-original')
    } else {
      emit('cancel')
    }
    return
  }
  emit('commit', pieces.map((piece) => buildSvgResult(piece.pts, piece.closed)))
}

/** 退出或放弃 */
function cancelPath() {
  emit('cancel')
}

// 键盘事件监听与前置拦截
function onKeyDown(e: KeyboardEvent) {
  if (e.key === 'Alt') isAltPressed.value = true
  if (e.key === 'Shift') isShiftPressed.value = true

  // 矢量编辑模式前置拦截快捷键
  if (e.key === 'Escape') {
    e.preventDefault()
    e.stopPropagation()
    if (isEditingExisting.value) {
      emit('cancel')
    } else {
      if (points.value.length >= 2) commitPath()
      else emit('cancel')
    }
    return
  }

  if (e.key === 'Enter') {
    e.preventDefault()
    e.stopPropagation()
    // 铅笔每一笔在松手时已经入画板。Enter 不再退出，方便连续画。
    if (props.activeTool === 'pencil') {
      if (points.value.length >= 2) {
        const result = buildSvgResult(points.value, isClosed.value)
        points.value = []
        isClosed.value = false
        emit('update:isClosed', false)
        emit('commit', [result])
      }
      return
    }
    commitPath()
    return
  }

  if (e.key === 'Delete' || e.key === 'Backspace') {
    e.preventDefault()
    e.stopPropagation()
    if (selectedPointIndex.value !== null && points.value.length > 0) {
      points.value.splice(selectedPointIndex.value, 1)
      selectedPointIndex.value = points.value.length ? Math.min(selectedPointIndex.value, points.value.length - 1) : null
      if (points.value.length < 2) {
        if (isEditingExisting.value) emit('delete-original')
        else emit('cancel')
      }
    } else if (points.value.length > 0) {
      points.value.pop()
      if (points.value.length < 2) {
        if (isEditingExisting.value) emit('delete-original')
        else emit('cancel')
      }
    }
    return
  }

  // 快捷键切换子工具
  if (e.key.toLowerCase() === 'p') {
    e.preventDefault()
    emit('update:activeSubTool', 'pen')
  } else if (e.key.toLowerCase() === 'v') {
    e.preventDefault()
    emit('update:activeSubTool', 'move')
  } else if (e.key.toLowerCase() === 'l') {
    e.preventDefault()
    emit('update:activeSubTool', 'lasso')
  } else if (e.key.toLowerCase() === 'b') {
    e.preventDefault()
    emit('update:activeSubTool', 'paint')
  } else if (e.key.toLowerCase() === 'x') {
    e.preventDefault()
    emit('update:activeSubTool', 'cut')
  } else if (e.key.toLowerCase() === 'e') {
    e.preventDefault()
    emit('update:activeSubTool', 'erase')
  }
}

function onKeyUp(e: KeyboardEvent) {
  if (e.key === 'Alt') isAltPressed.value = false
  if (e.key === 'Shift') isShiftPressed.value = false
}

onMounted(() => {
  window.addEventListener('keydown', onKeyDown, true)
  window.addEventListener('keyup', onKeyUp, true)
  window.addEventListener('mouseup', onMouseUp)
})

onUnmounted(() => {
  window.removeEventListener('keydown', onKeyDown, true)
  window.removeEventListener('keyup', onKeyUp, true)
  window.removeEventListener('mouseup', onMouseUp)
})

/** 单击锚点：支持橡皮擦删除或剪刀拆分，或选中 */
function onAnchorClick(idx: number, e: MouseEvent) {
  e.stopPropagation()
  if (props.activeSubTool === 'erase') {
    points.value.splice(idx, 1)
    selectedPointIndex.value = null
    emit('toast', '已擦除锚点')
    if (points.value.length < 2) {
      if (isEditingExisting.value) emit('delete-original')
      else emit('cancel')
    }
  } else if (props.activeSubTool === 'cut') {
    splitPathAtAnchor(-1, idx)
  } else {
    selectedPointIndex.value = idx
  }
}

const overlayCursorClass = computed(() => {
  if (props.isSpacePressed) return 'cursor-grab'
  if (props.activeTool === 'pencil') return 'cursor-crosshair'
  switch (props.activeSubTool) {
    case 'pen':
      return 'cursor-crosshair'
    case 'move':
      return 'cursor-default'
    case 'lasso':
      return 'cursor-crosshair'
    case 'paint':
      return 'cursor-pointer'
    case 'bend':
      return isBending.value ? 'cursor-grabbing' : 'cursor-grab'
    case 'cut':
      return 'cursor-crosshair'
    case 'erase':
      return 'cursor-pointer'
    default:
      return 'cursor-crosshair'
  }
})

defineExpose({
  commitPath,
  cancelPath,
  toggleFill,
  externalPointerDown,
  externalPointerUp,
  hasActivePoints,
})
</script>

<template>
  <div
    ref="rootRef"
    class="vector-draw-overlay absolute inset-0 select-none"
    :class="[
      isSpacePressed ? 'pointer-events-none' : 'pointer-events-auto',
      overlayCursorClass
    ]"
    :style="{ width: `${artboardW}px`, height: `${artboardH}px` }"
    @mousedown.stop="onMouseDown"
    @mousemove="onMouseMove"
    @mouseup="onMouseUp"
    @dblclick.stop="onOverlayDblClick"
  >
    <svg
      class="w-full h-full overflow-visible pointer-events-none"
      :viewBox="`0 0 ${artboardW} ${artboardH}`"
    >
      <!-- 0. 悬停线段高亮反馈 (Bend: 蓝色 / Cut: 橙色 / Erase: 红色) -->
      <path
        v-if="hoveredSegmentD"
        :d="hoveredSegmentD"
        fill="none"
        :stroke="activeSubTool === 'erase' ? '#ef4444' : activeSubTool === 'cut' ? '#f59e0b' : '#0D99FF'"
        stroke-width="3"
        stroke-linecap="round"
        class="pointer-events-none transition-all opacity-80"
      />

      <!-- 1. 已确立路径填充与描边 -->
      <path
        v-for="(extra, extraIdx) in loosePaths"
        :key="'loose-stroke-' + extraIdx"
        :d="pointsToSvgPath(extra, false)"
        fill="none"
        :stroke="strokeColor"
        :stroke-width="strokeWidth"
        stroke-linecap="round"
        stroke-linejoin="round"
      />
      <path
        v-if="currentSvgD"
        :d="currentSvgD"
        :fill="fillColor"
        :stroke="strokeColor"
        :stroke-width="strokeWidth"
        stroke-linecap="round"
        stroke-linejoin="round"
      />

      <!-- 2. 钢笔橡皮筋虚线预览 -->
      <path
        v-if="rubberBandD"
        :d="rubberBandD"
        fill="none"
        stroke="#0D99FF"
        stroke-width="1.5"
        stroke-dasharray="4,4"
        stroke-linecap="round"
      />

      <!-- 3. 铅笔实时自由手绘折线 -->
      <path
        v-if="pencilPreviewD"
        :d="pencilPreviewD"
        fill="none"
        stroke="#0D99FF"
        stroke-width="2"
        stroke-linecap="round"
      />

      <!-- 4. 锚点控制线与手柄 -->
      <template v-for="group in nodeGroups" :key="group.key">
        <g v-for="(p, idx) in group.pts" :key="group.key + '-' + p.id">
          <template v-if="p.handleIn && (Math.abs(p.handleIn.x) > 0.1 || Math.abs(p.handleIn.y) > 0.1)">
            <line
              :x1="p.x"
              :y1="p.y"
              :x2="p.x + p.handleIn.x"
              :y2="p.y + p.handleIn.y"
              stroke="#0D99FF"
              :stroke-width="handleStroke"
              opacity="0.8"
            />
            <circle
              :cx="p.x + p.handleIn.x"
              :cy="p.y + p.handleIn.y"
              :r="handleRadius"
              fill="#ffffff"
              stroke="#0D99FF"
              :stroke-width="handleStroke"
            />
          </template>

          <template v-if="p.handleOut && (Math.abs(p.handleOut.x) > 0.1 || Math.abs(p.handleOut.y) > 0.1)">
            <line
              :x1="p.x"
              :y1="p.y"
              :x2="p.x + p.handleOut.x"
              :y2="p.y + p.handleOut.y"
              stroke="#0D99FF"
              :stroke-width="handleStroke"
              opacity="0.8"
            />
            <circle
              :cx="p.x + p.handleOut.x"
              :cy="p.y + p.handleOut.y"
              :r="handleRadius"
              fill="#ffffff"
              stroke="#0D99FF"
              :stroke-width="handleStroke"
            />
          </template>

          <rect
            :x="p.x - anchorSize / 2"
            :y="p.y - anchorSize / 2"
            :width="anchorSize"
            :height="anchorSize"
            :fill="group.which === selectedPathWhich && idx === selectedPointIndex ? '#0D99FF' : '#ffffff'"
            stroke="#0D99FF"
            :stroke-width="anchorStroke"
          />

          <circle
            v-if="group.main && idx === 0 && isNearFirstPoint && !isClosed"
            :cx="p.x"
            :cy="p.y"
            :r="screenToUser(5)"
            fill="none"
            stroke="#0D99FF"
            :stroke-width="anchorStroke"
          />
        </g>
      </template>
    </svg>
  </div>
</template>

<style scoped>
.vector-draw-overlay {
  z-index: 140;
}
</style>
