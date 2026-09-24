/**
 * WireForge Vector Engine - 矢量绘制与贝塞尔路径核心算法库
 * 包含锚点计算、手柄对称联动、RDP 降噪平滑、Catmull-Rom 转贝塞尔、Bend 弯曲算法及 SVG 序列化
 */

export interface VectorHandle {
  x: number // 相对该锚点的水平偏移
  y: number // 相对该锚点的垂直偏移
}

export interface VectorPoint {
  id: string
  x: number // 锚点 X 坐标
  y: number // 锚点 Y 坐标
  handleIn: VectorHandle | null // 入射手柄 (相对自身坐标)
  handleOut: VectorHandle | null // 出射手柄 (相对自身坐标)
  type: 'corner' | 'symmetric' | 'smooth' | 'disconnected'
}

export interface VectorPathData {
  origW?: number
  origH?: number
  points: VectorPoint[]
  closed: boolean
  strokeColor: string
  strokeWidth: number
  fillColor: string
}

/**
 * 将 VectorPoint 列表转换为标准 SVG d 属性字符串
 * 控制手柄均为相对锚点自身的偏移
 */
export function pointsToSvgPath(points: VectorPoint[], closed: boolean): string {
  if (!points || points.length === 0) return ''
  if (points.length === 1) {
    return `M ${Math.round(points[0].x * 10) / 10} ${Math.round(points[0].y * 10) / 10}`
  }

  let d = `M ${Math.round(points[0].x * 10) / 10} ${Math.round(points[0].y * 10) / 10}`

  for (let i = 1; i < points.length; i++) {
    const prev = points[i - 1]
    const curr = points[i]

    const hasPrevOut = prev.handleOut && (Math.abs(prev.handleOut.x) > 0.1 || Math.abs(prev.handleOut.y) > 0.1)
    const hasCurrIn = curr.handleIn && (Math.abs(curr.handleIn.x) > 0.1 || Math.abs(curr.handleIn.y) > 0.1)

    if (hasPrevOut || hasCurrIn) {
      const cp1x = prev.x + (prev.handleOut ? prev.handleOut.x : 0)
      const cp1y = prev.y + (prev.handleOut ? prev.handleOut.y : 0)
      const cp2x = curr.x + (curr.handleIn ? curr.handleIn.x : 0)
      const cp2y = curr.y + (curr.handleIn ? curr.handleIn.y : 0)

      d += ` C ${Math.round(cp1x * 10) / 10} ${Math.round(cp1y * 10) / 10}, ${Math.round(cp2x * 10) / 10} ${Math.round(cp2y * 10) / 10}, ${Math.round(curr.x * 10) / 10} ${Math.round(curr.y * 10) / 10}`
    } else {
      d += ` L ${Math.round(curr.x * 10) / 10} ${Math.round(curr.y * 10) / 10}`
    }
  }

  if (closed && points.length > 2) {
    const last = points[points.length - 1]
    const first = points[0]
    const hasLastOut = last.handleOut && (Math.abs(last.handleOut.x) > 0.1 || Math.abs(last.handleOut.y) > 0.1)
    const hasFirstIn = first.handleIn && (Math.abs(first.handleIn.x) > 0.1 || Math.abs(first.handleIn.y) > 0.1)

    if (hasLastOut || hasFirstIn) {
      const cp1x = last.x + (last.handleOut ? last.handleOut.x : 0)
      const cp1y = last.y + (last.handleOut ? last.handleOut.y : 0)
      const cp2x = first.x + (first.handleIn ? first.handleIn.x : 0)
      const cp2y = first.y + (first.handleIn ? first.handleIn.y : 0)
      d += ` C ${Math.round(cp1x * 10) / 10} ${Math.round(cp1y * 10) / 10}, ${Math.round(cp2x * 10) / 10} ${Math.round(cp2y * 10) / 10}, ${Math.round(first.x * 10) / 10} ${Math.round(first.y * 10) / 10} Z`
    } else {
      d += ' Z'
    }
  }

  return d
}

/**
 * 计算路径外接矩形。包含贝塞尔曲线鼓出锚点的部分，以及描边伸出的一半。
 */
export function calculateBoundingBox(points: VectorPoint[], strokeWidth = 0, closed = false): {
  minX: number
  minY: number
  maxX: number
  maxY: number
  width: number
  height: number
} {
  if (!points || points.length === 0) {
    return { minX: 0, minY: 0, maxX: 0, maxY: 0, width: 0, height: 0 }
  }

  let minX = Infinity
  let minY = Infinity
  let maxX = -Infinity
  let maxY = -Infinity

  const include = (x: number, y: number) => {
    if (x < minX) minX = x
    if (x > maxX) maxX = x
    if (y < minY) minY = y
    if (y > maxY) maxY = y
  }
  const includeX = (x: number) => {
    if (x < minX) minX = x
    if (x > maxX) maxX = x
  }
  const includeY = (y: number) => {
    if (y < minY) minY = y
    if (y > maxY) maxY = y
  }

  const includeCubic = (
    x0: number, y0: number,
    x1: number, y1: number,
    x2: number, y2: number,
    x3: number, y3: number,
  ) => {
    for (const x of cubicAxisExtrema(x0, x1, x2, x3)) includeX(x)
    for (const y of cubicAxisExtrema(y0, y1, y2, y3)) includeY(y)
    include(x0, y0)
    include(x3, y3)
  }

  for (let i = 0; i < points.length; i++) {
    const curr = points[i]
    include(curr.x, curr.y)
    const next = i < points.length - 1 ? points[i + 1] : null
    if (!next) continue
    addSegmentBounds(includeCubic, include, curr, next)
  }

  if (closed && points.length > 2) {
    addSegmentBounds(includeCubic, include, points[points.length - 1], points[0])
  }

  const pad = Math.max(0, strokeWidth) / 2
  minX -= pad
  minY -= pad
  maxX += pad
  maxY += pad

  const width = Math.max(1, maxX - minX)
  const height = Math.max(1, maxY - minY)
  return { minX, minY, maxX, maxY, width, height }
}

function addSegmentBounds(
  includeCubic: (
    x0: number, y0: number,
    x1: number, y1: number,
    x2: number, y2: number,
    x3: number, y3: number,
  ) => void,
  include: (x: number, y: number) => void,
  prev: VectorPoint,
  curr: VectorPoint,
) {
  const hasPrevOut = !!(prev.handleOut && (Math.abs(prev.handleOut.x) > 0.1 || Math.abs(prev.handleOut.y) > 0.1))
  const hasCurrIn = !!(curr.handleIn && (Math.abs(curr.handleIn.x) > 0.1 || Math.abs(curr.handleIn.y) > 0.1))
  if (!hasPrevOut && !hasCurrIn) {
    include(prev.x, prev.y)
    include(curr.x, curr.y)
    return
  }
  const cp1x = prev.x + (prev.handleOut ? prev.handleOut.x : 0)
  const cp1y = prev.y + (prev.handleOut ? prev.handleOut.y : 0)
  const cp2x = curr.x + (curr.handleIn ? curr.handleIn.x : 0)
  const cp2y = curr.y + (curr.handleIn ? curr.handleIn.y : 0)
  includeCubic(prev.x, prev.y, cp1x, cp1y, cp2x, cp2y, curr.x, curr.y)
}

/** 三阶贝塞尔在某一轴上的端点与曲线极值 */
function cubicAxisExtrema(p0: number, p1: number, p2: number, p3: number): number[] {
  const a = 3 * (-p0 + 3 * p1 - 3 * p2 + p3)
  const b = 6 * (p0 - 2 * p1 + p2)
  const c = 3 * (p1 - p0)
  const values = [p0, p3]
  const ts: number[] = []
  if (Math.abs(a) < 1e-6) {
    if (Math.abs(b) > 1e-6) ts.push(-c / b)
  } else {
    const disc = b * b - 4 * a * c
    if (disc >= 0) {
      const root = Math.sqrt(disc)
      ts.push((-b + root) / (2 * a), (-b - root) / (2 * a))
    }
  }
  for (const t of ts) {
    if (t <= 0 || t >= 1) continue
    const mt = 1 - t
    values.push(mt * mt * mt * p0 + 3 * mt * mt * t * p1 + 3 * mt * t * t * p2 + t * t * t * p3)
  }
  return values
}

/**
 * 锚点归一化到 (0, 0)
 * 注意：手柄是相对锚点的偏移，绝对不能再减 minX, minY！
 */
export function normalizePointsToOrigin(
  points: VectorPoint[],
  minX: number,
  minY: number,
): VectorPoint[] {
  return points.map((p) => ({
    id: p.id,
    x: p.x - minX,
    y: p.y - minY,
    handleIn: p.handleIn ? { x: p.handleIn.x, y: p.handleIn.y } : null,
    handleOut: p.handleOut ? { x: p.handleOut.x, y: p.handleOut.y } : null,
    type: p.type,
  }))
}

/**
 * 缩放锚点与手柄
 * 手柄只跟着锚点走，缩放时手柄也必须乘 scaleX, scaleY
 * 保护：scaleX, scaleY 必须大于 0，避免除以 0
 */
export function scalePointsAndHandles(
  points: VectorPoint[],
  scaleX: number,
  scaleY: number,
): VectorPoint[] {
  const sx = isNaN(scaleX) || !isFinite(scaleX) ? 1 : scaleX
  const sy = isNaN(scaleY) || !isFinite(scaleY) ? 1 : scaleY

  return points.map((p) => ({
    id: p.id,
    x: p.x * sx,
    y: p.y * sy,
    handleIn: p.handleIn ? { x: p.handleIn.x * sx, y: p.handleIn.y * sy } : null,
    handleOut: p.handleOut ? { x: p.handleOut.x * sx, y: p.handleOut.y * sy } : null,
    type: p.type,
  }))
}

/**
 * Ramer-Douglas-Peucker (RDP) 曲线抽样降噪算法
 * 用于手绘铅笔平滑，容差默认 1.5 像素
 */
export function rdpSimplify(
  points: { x: number; y: number }[],
  tolerance = 1.5,
): { x: number; y: number }[] {
  if (points.length <= 2) return points

  let maxDist = 0
  let index = 0
  const end = points.length - 1

  for (let i = 1; i < end; i++) {
    const d = perpendicularDistance(points[i], points[0], points[end])
    if (d > maxDist) {
      maxDist = d
      index = i
    }
  }

  if (maxDist > tolerance) {
    const left = rdpSimplify(points.slice(0, index + 1), tolerance)
    const right = rdpSimplify(points.slice(index), tolerance)
    return left.slice(0, left.length - 1).concat(right)
  }
  return [points[0], points[end]]
}

function perpendicularDistance(
  p: { x: number; y: number },
  p1: { x: number; y: number },
  p2: { x: number; y: number },
): number {
  const dx = p2.x - p1.x
  const dy = p2.y - p1.y
  const mag = Math.hypot(dx, dy)
  if (mag < 0.0001) return Math.hypot(p.x - p1.x, p.y - p1.y)
  const u = Math.abs(dy * p.x - dx * p.y + p2.x * p1.y - p2.y * p1.x)
  return u / mag
}

/**
 * Catmull-Rom 样条平滑转换为三阶贝塞尔曲线
 * 相邻四点 p0 p1 p2 p3 时：
 * p1 的出射手柄 = (p2 - p0) / 6
 * p2 的入射手柄 = (p1 - p3) / 6
 */
export function catmullRomToBezier(
  rawPoints: { x: number; y: number }[],
  closed: boolean,
): VectorPoint[] {
  if (rawPoints.length < 2) return []

  const n = rawPoints.length
  const res: VectorPoint[] = []

  for (let i = 0; i < n; i++) {
    const curr = rawPoints[i]
    let p0: { x: number; y: number }
    let p2: { x: number; y: number }

    if (closed) {
      p0 = rawPoints[(i - 1 + n) % n]
      p2 = rawPoints[(i + 1) % n]
    } else {
      p0 = i === 0 ? curr : rawPoints[i - 1]
      p2 = i === n - 1 ? curr : rawPoints[i + 1]
    }

    // p.handleOut = (p2 - p0) / 6
    const handleOut: VectorHandle = {
      x: (p2.x - p0.x) / 6,
      y: (p2.y - p0.y) / 6,
    }

    // 入射手柄为当前出射手柄的反向 (共线平滑)
    const handleIn: VectorHandle = {
      x: -handleOut.x,
      y: -handleOut.y,
    }

    res.push({
      id: 'vp_' + i + '_' + Math.random().toString(36).substring(2, 6),
      x: curr.x,
      y: curr.y,
      handleIn: i === 0 && !closed ? null : handleIn,
      handleOut: i === n - 1 && !closed ? null : handleOut,
      type: 'smooth',
    })
  }

  return res
}

/**
 * Figma Bend 招牌弯曲算法：
 * 拖拽点相对线段中点的偏移记为 delta，弦向量为 p1 - p0：
 * 起点 p0.handleOut = delta + 弦 * 0.33
 * 终点 p1.handleIn = delta - 弦 * 0.33
 * 锚点若为 symmetric，另一侧手柄按反向等长一起更新
 */
export function applyBendAlgorithm(
  p0: VectorPoint,
  p1: VectorPoint,
  dragPoint: { x: number; y: number },
): void {
  const midX = (p0.x + p1.x) / 2
  const midY = (p0.y + p1.y) / 2

  const deltaX = dragPoint.x - midX
  const deltaY = dragPoint.y - midY

  const chordX = p1.x - p0.x
  const chordY = p1.y - p0.y

  p0.handleOut = {
    x: deltaX + chordX * 0.33,
    y: deltaY + chordY * 0.33,
  }

  p1.handleIn = {
    x: deltaX - chordX * 0.33,
    y: deltaY - chordY * 0.33,
  }

  // 若 p0 为 symmetric，反向同步 handleIn
  if (p0.type === 'symmetric') {
    p0.handleIn = {
      x: -p0.handleOut.x,
      y: -p0.handleOut.y,
    }
  }

  // 若 p1 为 symmetric，反向同步 handleOut
  if (p1.type === 'symmetric') {
    p1.handleOut = {
      x: -p1.handleIn.x,
      y: -p1.handleIn.y,
    }
  }
}

/**
 * 双击锚点：
 * 有手柄则清成尖角 (corner)；没有手柄则根据相邻点恢复一对共线手柄 (smooth)
 */
export function togglePointType(
  point: VectorPoint,
  prevPoint?: VectorPoint | null,
  nextPoint?: VectorPoint | null,
): void {
  const hasHandles = (point.handleIn && (point.handleIn.x !== 0 || point.handleIn.y !== 0)) ||
                     (point.handleOut && (point.handleOut.x !== 0 || point.handleOut.y !== 0))

  if (hasHandles) {
    point.handleIn = null
    point.handleOut = null
    point.type = 'corner'
  } else {
    // 恢复共线平滑手柄
    const pPrev = prevPoint || { x: point.x - 30, y: point.y }
    const pNext = nextPoint || { x: point.x + 30, y: point.y }
    const hx = (pNext.x - pPrev.x) / 6
    const hy = (pNext.y - pPrev.y) / 6

    point.handleOut = { x: hx, y: hy }
    point.handleIn = { x: -hx, y: -hy }
    point.type = 'symmetric'
  }
}

export function copyVectorPoint(p: VectorPoint, id?: string): VectorPoint {
  return {
    id: id || p.id,
    x: p.x,
    y: p.y,
    handleIn: p.handleIn ? { x: p.handleIn.x, y: p.handleIn.y } : null,
    handleOut: p.handleOut ? { x: p.handleOut.x, y: p.handleOut.y } : null,
    type: p.type,
  }
}

function tinyHandle(h: VectorHandle | null | undefined): VectorHandle | null {
  if (!h) return null
  if (Math.abs(h.x) < 0.2 && Math.abs(h.y) < 0.2) return null
  return { x: h.x, y: h.y }
}

/**
 * 把一段曲线在 t 处切开，形状保持不变。
 * 手柄仍是相对各自锚点的偏移。
 */
export function splitCubicSegment(
  p0: VectorPoint,
  p1: VectorPoint,
  t: number,
  midId: string,
): { p0: VectorPoint; mid: VectorPoint; p1: VectorPoint } {
  const u = Math.min(0.999, Math.max(0.001, t))
  const c1x = p0.x + (p0.handleOut?.x || 0)
  const c1y = p0.y + (p0.handleOut?.y || 0)
  const c2x = p1.x + (p1.handleIn?.x || 0)
  const c2y = p1.y + (p1.handleIn?.y || 0)
  const straight = !tinyHandle(p0.handleOut) && !tinyHandle(p1.handleIn)
  const lerp = (a: number, b: number) => a + (b - a) * u
  if (straight) {
    return {
      p0: { ...copyVectorPoint(p0), handleOut: null },
      mid: {
        id: midId,
        x: lerp(p0.x, p1.x),
        y: lerp(p0.y, p1.y),
        handleIn: null,
        handleOut: null,
        type: 'corner',
      },
      p1: { ...copyVectorPoint(p1), handleIn: null },
    }
  }
  const ax = lerp(p0.x, c1x)
  const ay = lerp(p0.y, c1y)
  const bx = lerp(c1x, c2x)
  const by = lerp(c1y, c2y)
  const cx = lerp(c2x, p1.x)
  const cy = lerp(c2y, p1.y)
  const dx = lerp(ax, bx)
  const dy = lerp(ay, by)
  const ex = lerp(bx, cx)
  const ey = lerp(by, cy)
  const px = lerp(dx, ex)
  const py = lerp(dy, ey)
  return {
    p0: { ...copyVectorPoint(p0), handleOut: tinyHandle({ x: ax - p0.x, y: ay - p0.y }) },
    mid: {
      id: midId,
      x: px,
      y: py,
      handleIn: tinyHandle({ x: dx - px, y: dy - py }),
      handleOut: tinyHandle({ x: ex - px, y: ey - py }),
      type: 'disconnected',
    },
    p1: { ...copyVectorPoint(p1), handleIn: tinyHandle({ x: cx - p1.x, y: cy - p1.y }) },
  }
}

/** 开口路径在某一段的 t 处加节点，分成两段，两段都保留切点 */
export function cutOpenPathAt(
  points: VectorPoint[],
  segIdx: number,
  t: number,
  makeId: () => string,
): [VectorPoint[], VectorPoint[]] | null {
  if (segIdx < 0 || segIdx >= points.length - 1) return null
  const split = splitCubicSegment(points[segIdx], points[segIdx + 1], t, makeId())
  const midL = copyVectorPoint(split.mid, makeId())
  midL.handleOut = null
  const midR = copyVectorPoint(split.mid, makeId())
  midR.handleIn = null
  const part1 = [
    ...points.slice(0, segIdx).map((p) => copyVectorPoint(p)),
    copyVectorPoint(split.p0),
    midL,
  ]
  const part2 = [
    midR,
    copyVectorPoint(split.p1),
    ...points.slice(segIdx + 2).map((p) => copyVectorPoint(p)),
  ]
  if (part1.length < 2 || part2.length < 2) return null
  return [part1, part2]
}

/** 闭合路径在某一段切开后变成一条开口路径，切点同时是起点和终点 */
export function cutClosedPathAt(
  points: VectorPoint[],
  segIdx: number,
  t: number,
  makeId: () => string,
): VectorPoint[] | null {
  const n = points.length
  if (n < 2 || segIdx < 0 || segIdx >= n) return null
  const nextIdx = (segIdx + 1) % n
  const split = splitCubicSegment(points[segIdx], points[nextIdx], t, makeId())
  const start = copyVectorPoint(split.mid, makeId())
  start.handleIn = null
  const end = copyVectorPoint(split.mid, makeId())
  end.handleOut = null
  const seq: VectorPoint[] = [start]
  for (let k = 1; k <= n; k++) {
    const idx = (segIdx + k) % n
    if (idx === nextIdx) seq.push(copyVectorPoint(split.p1))
    else if (idx === segIdx) seq.push(copyVectorPoint(split.p0))
    else seq.push(copyVectorPoint(points[idx]))
  }
  seq.push(end)
  return seq.length >= 2 ? seq : null
}

/** 在已有锚点处把开口路径拆成两段 */
export function cutOpenAtAnchor(
  points: VectorPoint[],
  idx: number,
  makeId: () => string,
): [VectorPoint[], VectorPoint[]] | null {
  if (idx <= 0 || idx >= points.length - 1) return null
  const part1 = points.slice(0, idx + 1).map((p) => copyVectorPoint(p))
  const part2 = points.slice(idx).map((p) => copyVectorPoint(p))
  part2[0] = copyVectorPoint(points[idx], makeId())
  part1[part1.length - 1].handleOut = null
  part2[0].handleIn = null
  if (part1.length < 2 || part2.length < 2) return null
  return [part1, part2]
}

/** 在已有锚点处把闭合路径剪开 */
export function cutClosedAtAnchor(points: VectorPoint[], idx: number, makeId: () => string): VectorPoint[] | null {
  const n = points.length
  if (n < 2 || idx < 0 || idx >= n) return null
  const seq: VectorPoint[] = []
  for (let k = 0; k < n; k++) {
    const src = points[(idx + k) % n]
    seq.push(copyVectorPoint(src, k === 0 ? makeId() : undefined))
  }
  const end = copyVectorPoint(seq[0], makeId())
  seq[0].handleIn = null
  end.handleOut = null
  seq.push(end)
  return seq
}
