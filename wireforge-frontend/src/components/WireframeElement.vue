<template>
  <div
    class="wf-element"
    :class="[`wf-${type}`, { interactive: hasInteraction, 'is-hovered': hovered, 'on-bg': onBg }]"
    :style="[posStyle, aiVars]"
    @click.stop="$emit('click', el)"
    @mouseenter="$emit('hover', el, true)"
    @mouseleave="$emit('hover', el, false)"
  >
    <!-- ===== 形状层（高保真风格：真实表面 + 阴影 + 配色，纯 CSS） ===== -->

    <!-- 开关兜底：AI 常把开关误判为 progress/container/other（导致渲染成绿色长条）；
         只要形状是宽扁小条且 label 语义为开关/toggle，一律强制按 switch 组件渲染 -->
    <template v-if="isSwitchFallback">
      <div class="switch-track" :style="switchTrackStyle"></div>
      <div class="switch-knob" :style="switchKnobStyle"></div>
      <span v-if="displayLabel" class="wf-side-label" :style="{ left: `${el.height * 1.9 + 8}px` }">{{ displayLabel }}</span>
    </template>

    <!-- 图片：优先素材图，否则占位（边框 + 交叉线） -->
    <template v-else-if="type === 'image'">
      <img v-if="assetUrl && !imageError" :src="assetUrl" class="wf-asset" @error="onImageError" alt="" />
      <template v-else>
        <div class="img-placeholder">
          <ImageIcon class="placeholder-glyph" />
        </div>
      </template>
    </template>

    <!-- 头像：优先素材图，否则圆形 + 字形占位 -->
    <template v-else-if="type === 'avatar'">
      <img v-if="assetUrl && !imageError" :src="assetUrl" class="wf-asset wf-avatar" @error="onImageError" alt="" />
      <template v-else>
        <div class="shape circle" :style="circleStyle"></div>
        <component
          v-if="centerGlyph"
          :is="centerGlyph"
          class="icon-glyph"
          :style="centerGlyphStyle"
          :stroke-width="2"
          absolute-stroke-width
        />
      </template>
    </template>

    <!-- 图标 / 小圆形图标按钮：始终带圆形底座（还原设计稿中的圆形气泡/图标按钮），字形或素材图叠加其上 -->
    <template v-else-if="type === 'icon' || isIconLike">
      <div class="shape circle" :style="circleStyle"></div>
      <component
        v-if="explicitGlyph"
        :is="explicitGlyph"
        class="icon-glyph"
        :style="centerGlyphStyle"
        :stroke-width="2"
        absolute-stroke-width
      />
      <img
        v-else-if="type === 'icon' && assetUrl && !imageError"
        :src="assetUrl"
        class="wf-icon-img"
        @error="onImageError"
        alt=""
      />
      <component
        v-else-if="centerGlyph"
        :is="centerGlyph"
        class="icon-glyph"
        :style="centerGlyphStyle"
        :stroke-width="2"
        absolute-stroke-width
      />
    </template>

    <!-- 特效：全屏遮罩→静态半透明层（绝不闪）；误标气泡→圆形；素材图；否则烟花式放射光线 -->
    <template v-else-if="type === 'effect'">
      <div v-if="isScrimEffect" class="wf-scrim" :style="scrimStyle"></div>
      <template v-else-if="isBubbleEffect">
        <div class="shape circle" :style="circleStyle"></div>
        <component
          v-if="explicitGlyph"
          :is="explicitGlyph"
          class="icon-glyph"
          :style="centerGlyphStyle"
          :stroke-width="2"
          absolute-stroke-width
        />
      </template>
      <img v-else-if="assetUrl && !imageError" :src="assetUrl" class="wf-effect-img" @error="onImageError" alt="" />
      <div v-else class="wf-effect-rays">
        <span
          v-for="(r, i) in effectRays"
          :key="i"
          class="wf-ray"
          :style="{
            left: r.left,
            top: r.top,
            width: `${r.len}px`,
            transform: `rotate(${r.deg}deg)`,
            animationDelay: r.delay,
            animationDuration: r.dur,
          }"
        ></span>
      </div>
    </template>

    <!-- 背景：素材图铺满元素框（画布级背景由 PageCanvas 单独渲染，这里兜底） -->
    <template v-else-if="type === 'background'">
      <img v-if="assetUrl && !imageError" :src="assetUrl" class="wf-background" @error="onImageError" alt="" />
    </template>

    <!-- 按钮 -->
    <div v-else-if="type === 'button'" class="shape rect filled"></div>

    <!-- 输入框 / 多行输入 / 下拉 -->
    <template v-else-if="type === 'input' || type === 'textarea' || type === 'select'">
      <div class="shape rect filled"></div>
      <svg v-if="type === 'select'" class="caret" :width="14" :height="10" viewBox="0 0 14 10">
        <path d="M2 2 L7 8 L12 2" />
      </svg>
    </template>

    <!-- 搜索框 -->
    <template v-else-if="type === 'search'">
      <div class="shape pill filled"></div>
      <SearchIcon class="icon-glyph" :style="searchGlyphStyle" :stroke-width="2" absolute-stroke-width />
      <span class="wf-input-placeholder" :style="{ left: `${el.height * 0.8 + 6}px` }">{{ displayLabel || '搜索…' }}</span>
    </template>

    <!-- 勾选框 -->
    <template v-else-if="type === 'checkbox'">
      <div class="check-box" :style="controlBoxStyle"></div>
      <CheckIcon class="icon-glyph" :style="checkGlyphStyle" :stroke-width="2.4" absolute-stroke-width />
      <span class="wf-side-label" :style="{ left: `${controlSize + 8}px` }">{{ displayLabel }}</span>
    </template>

    <!-- 单选 -->
    <template v-else-if="type === 'radio'">
      <div class="radio-outer" :style="controlBoxStyle"></div>
      <div class="radio-dot" :style="radioDotStyle"></div>
      <span class="wf-side-label" :style="{ left: `${controlSize + 8}px` }">{{ displayLabel }}</span>
    </template>

    <!-- 开关 -->
    <template v-else-if="type === 'switch'">
      <div class="switch-track" :style="switchTrackStyle"></div>
      <div class="switch-knob" :style="switchKnobStyle"></div>
      <span v-if="displayLabel" class="wf-side-label" :style="{ left: `${el.height * 1.9 + 8}px` }">{{ displayLabel }}</span>
    </template>

    <!-- 滑杆 -->
    <template v-else-if="type === 'slider'">
      <div class="slider-track"></div>
      <div class="slider-knob" :style="sliderKnobStyle"></div>
    </template>

    <!-- 进度条 -->
    <template v-else-if="type === 'progress'">
      <div class="shape pill"></div>
      <div class="progress-fill"></div>
    </template>

    <!-- 选项卡 -->
    <template v-else-if="type === 'tabs'">
      <div class="tabs-baseline"></div>
      <div v-if="!suppressInner" class="tabs-row">
        <span v-for="(t, i) in tabItems" :key="i" class="tab-item" :class="{ active: i === 0 }">{{ t }}</span>
      </div>
    </template>

    <!-- 星级评分 -->
    <div v-else-if="type === 'rating'" class="rating-row">
      <StarIcon
        v-for="i in 5"
        :key="i"
        :size="ratingSize"
        :stroke-width="2"
        absolute-stroke-width
        :class="{ filled: i <= 4 }"
      />
    </div>

    <!-- 分隔线 -->
    <div v-else-if="type === 'divider'" class="divider-line"></div>

    <!-- 徽标 -->
    <div v-else-if="type === 'badge'" class="shape pill thin"></div>

    <!-- 导航栏 -->
    <template v-else-if="type === 'navbar'">
      <div class="navbar-line" :class="el.y < 120 ? 'at-bottom' : 'at-top'"></div>
      <template v-if="!suppressInner">
        <div v-if="navMode === 'row'" class="nav-icons">
          <div v-for="(c, i) in navGlyphs" :key="i" class="nav-item">
            <component
              :is="c"
              :size="navIconSize"
              :stroke-width="2"
              absolute-stroke-width
            />
            <span v-if="navParts[i]" class="nav-item-label">{{ navParts[i] }}</span>
          </div>
        </div>
        <div v-else-if="navMode === 'single'" class="nav-single">
          <component :is="singleNavGlyph" :size="Math.min(el.height * 0.42, 22)" :stroke-width="2" absolute-stroke-width />
          <span class="nav-single-label">{{ displayLabel }}</span>
        </div>
        <span v-else class="wf-real-text" :style="{ fontSize: '15px', fontWeight: 700 }">{{ displayLabel }}</span>
      </template>
    </template>

    <!-- 列表 -->
    <template v-else-if="type === 'list'">
      <div class="shape rect thin"></div>
      <div v-if="!suppressInner" class="list-rows">
        <div v-for="i in 3" :key="i" class="list-row">
          <span class="list-dot"></span>
          <span class="list-line"></span>
        </div>
      </div>
    </template>

    <!-- 容器/卡片 -->
    <div v-else-if="type === 'container'" class="shape rect thin"></div>

    <!-- 文本：无形状 -->
    <template v-else-if="type === 'text'"></template>

    <!-- 其他 -->
    <div v-else class="shape rect thin"></div>

    <!-- ===== 文字层 ===== -->
    <span v-if="type === 'text' && displayLabel" class="wf-real-text" :style="realTextStyle">{{ displayLabel }}</span>

    <template v-if="labelVisible">
      <span v-if="type === 'input'" class="wf-input-placeholder">{{ displayLabel || '请输入…' }}</span>
      <span v-else class="wf-label" :style="{ fontSize: `${labelFontSize}px` }">{{ displayLabel }}</span>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, type FunctionalComponent } from 'vue'
import type { Element } from '../types'
import * as Lucide from 'lucide-vue-next'
import {
  Menu,
  Bell,
  House,
  Image as ImageIcon,
  Calendar,
  List,
  User,
  Search as SearchIcon,
  ShoppingBag,
  Heart,
  Clock,
  Plus,
  ChevronRight,
  ChevronLeft,
  Settings,
  Star as StarIcon,
  Check as CheckIcon,
  Dumbbell,
  Bike,
  Footprints,
  Gamepad2,
  Music,
  Play,
  Camera,
  MapPin,
  Phone,
  Mail,
  Share2,
  Download,
  Pencil,
  Trash2,
  Lock,
  Eye,
  RefreshCw,
  X,
  Info,
  Coins,
  Gift,
  Flame,
  Trophy,
  Zap,
  MessageCircle,
  Bookmark,
  Users,
  Crown,
  Swords,
  Store,
  LayoutGrid,
  Shapes,
} from 'lucide-vue-next'

const props = defineProps<{
  el: Element
  type: string
  hasInteraction: boolean
  hovered: boolean
  /** 内部已有独立子元素：抑制自身的内部装饰（图标行/占位行/居中文字），只画外框 */
  suppressInner?: boolean
  /** 页面铺了深色背景素材：浮动文字改用浅色 */
  onBg?: boolean
}>()

defineEmits<{
  (e: 'click', el: Element): void
  (e: 'hover', el: Element, on: boolean): void
}>()

type IconComp = FunctionalComponent

// ===== Lucide 图标关键词映射（中英文常见叫法） =====
const ICON_KEYWORDS: Array<[string[], IconComp]> = [
  [['菜单', '汉堡', 'menu'], Menu],
  [['铃', '通知', '提醒', '消息提示', 'bell', 'notif'], Bell],
  [['首页', '主页', '家园', 'home'], House],
  [['日历', '日程', '签到', '日期', 'calendar', 'schedule'], Calendar],
  [['列表', '清单', '任务', 'list', 'task'], List],
  [['我的', '个人', '头像', '用户', '账户', 'user', 'profile', 'person', 'mine', 'avatar'], User],
  [['搜索', '查找', 'search'], SearchIcon],
  [['商城', '商店', 'store', 'shop'], Store],
  [['购物', '背包', '袋', 'cart', 'bag', 'backpack'], ShoppingBag],
  [['收藏夹', '书签', 'bookmark'], Bookmark],
  [['收藏', '喜欢', '爱心', '心', '关注', 'heart', 'like', 'favorite'], Heart],
  [['时间', '时钟', '闹钟', 'clock', 'time'], Clock],
  [['添加', '新增', '加号', 'plus', 'add'], Plus],
  [['返回', '后退', 'back'], ChevronLeft],
  [['前进', '更多', '箭头', '进入', 'arrow', 'next', 'more'], ChevronRight],
  [['设置', '齿轮', 'setting', 'gear', 'config'], Settings],
  [['星', '评分', 'star', 'rate'], StarIcon],
  [['健身', '哑铃', '力量', 'dumbbell', 'gym', 'fitness'], Dumbbell],
  [['骑行', '自行车', '单车', 'bike', 'cycling'], Bike],
  [['跑步', '步行', '脚印', 'run', 'walk', 'footprint'], Footprints],
  [['游戏', '手柄', 'game', 'gamepad'], Gamepad2],
  [['音乐', 'music'], Music],
  [['播放', '视频', 'play', 'video'], Play],
  [['相机', '拍照', '摄像', 'camera', 'photo'], Camera],
  [['位置', '地图', '定位', 'map', 'location', 'pin'], MapPin],
  [['电话', '拨号', 'phone', 'call'], Phone],
  [['邮件', '邮箱', 'mail', 'email'], Mail],
  [['分享', 'share'], Share2],
  [['下载', 'download'], Download],
  [['编辑', '修改', '笔', 'edit', 'pencil'], Pencil],
  [['删除', '垃圾', 'delete', 'trash'], Trash2],
  [['锁', '密码', '安全', 'lock', 'password'], Lock],
  [['眼睛', '查看', '预览', 'eye', 'view'], Eye],
  [['刷新', '重试', 'refresh', 'reload'], RefreshCw],
  [['关闭', '叉', 'close', 'x'], X],
  [['信息', '说明', 'info'], Info],
  [['金币', '货币', '钱', 'coin', 'money', 'gold'], Coins],
  [['礼物', '奖励', '礼包', 'gift', 'reward'], Gift],
  [['火', '热门', '连胜', 'fire', 'flame', 'hot'], Flame],
  [['奖杯', '成就', '排行', 'trophy', 'rank', 'achievement'], Trophy],
  [['能量', '体力', '闪电', 'energy', 'zap', 'power'], Zap],
  [['聊天', '评论', '留言', 'chat', 'comment', 'message'], MessageCircle],
  [['好友', '社交', '团队', 'friends', 'team', 'social'], Users],
  [['会员', '皇冠', 'vip', 'crown'], Crown],
  [['战斗', '关卡', '挑战', 'battle', 'fight', 'sword'], Swords],
  [['收集', '图鉴', '仓库', 'collect', 'inventory'], LayoutGrid],
]

/** 按 Lucide 图标名（kebab-case）从全量库动态解析，如 shopping-cart → ShoppingCart */
function lucideByName(name?: string | null): IconComp | null {
  if (!name) return null
  const pascal = name
    .trim()
    .split(/[-_\s]+/)
    .map((s) => s.charAt(0).toUpperCase() + s.slice(1))
    .join('')
  const comp = (Lucide as Record<string, unknown>)[pascal]
  return typeof comp === 'object' || typeof comp === 'function' ? (comp as IconComp) : null
}

function iconFor(label: string): IconComp | null {
  const t = (label || '').toLowerCase()
  if (!t) return null
  for (const [keys, icon] of ICON_KEYWORDS) {
    if (keys.some((k) => t.includes(k))) return icon
  }
  return null
}

/** 元素图标解析优先级：AI 指定的 Lucide 图标名 → label 关键词映射 */
function resolveIcon(el: Element): IconComp | null {
  return lucideByName(el.icon) || iconFor(el.label)
}

/** AI 明确指定的图标名（style.icon）——最可信，优先于素材图渲染 */
const explicitGlyph = computed<IconComp | null>(() => lucideByName(props.el.icon))

/** 特效放射光线：确定性伪随机（同元素每次渲染一致），从中心向外辐射、长度角度各异 */
const effectRays = computed(() => {
  const { el } = props
  const cx = el.width / 2
  const cy = el.height / 2
  const base = Math.min(el.width, el.height)
  const n = Math.max(8, Math.min(14, Math.round(base / 16)))
  // 确定性伪随机：不引入真随机，避免热更新/重渲染时跳动
  const rnd = (seed: number, idx: number) => {
    const v = Math.sin((idx + 1) * 12.9898 + seed * 78.233) * 43758.5453
    return v - Math.floor(v)
  }
  return Array.from({ length: n }, (_, i) => ({
    left: `${cx}px`,
    top: `${cy}px`,
    len: base * (0.26 + rnd(1, i) * 0.34),
    deg: (360 / n) * i + rnd(2, i) * 26,
    delay: `${((i * 3) % 7) * 0.24}s`,
    dur: `${1.5 + ((i * 5) % 3) * 0.4}s`,
  }))
})

/** 元素定位（原根节点内联样式） */
const posStyle = computed(() => ({
  left: `${props.el.x}px`,
  top: `${props.el.y}px`,
  width: `${props.el.width}px`,
  height: `${props.el.height}px`,
}))

/** 解析 AI 输出的视觉样式（fill/text_color/border_color/radius），转成 CSS 变量供各部件回退使用 */
const aiVars = computed<Record<string, string>>(() => {
  const vars: Record<string, string> = {}
  try {
    const raw = props.el.style
    if (raw) {
      const o = JSON.parse(raw) as Record<string, unknown>
      if (typeof o.fill === 'string' && /^#([0-9a-f]{3,8}|rgb)/i.test(o.fill.trim())) vars['--wf-fill'] = o.fill.trim()
      if (typeof o.text_color === 'string' && /^#([0-9a-f]{3,8}|rgb)/i.test(o.text_color.trim()))
        vars['--wf-text'] = o.text_color.trim()
      if (typeof o.border_color === 'string' && /^#([0-9a-f]{3,8}|rgb)/i.test(o.border_color.trim()))
        vars['--wf-border'] = o.border_color.trim()
      if (typeof o.radius === 'number' && o.radius >= 0) {
        // 圆角钳制：不超过短边一半；卡片/容器/输入类再限 24px，防止大圆角把矩形卡片变成胶囊甚至圆形
        const half = Math.min(props.el.width, props.el.height) / 2
        const softTypes = ['container', 'list', 'input', 'textarea', 'select', 'card', 'other']
        const cap = softTypes.includes(props.type) ? Math.min(half, 24) : half
        vars['--wf-radius'] = `${Math.min(o.radius, Math.max(cap, 4))}px`
      }
    }
  } catch {
    /* style 不是合法 JSON 时忽略 */
  }
  return vars
})

/** 去掉 AI 输出中混入的 markdown 符号（** __ # ` 等） */
const cleanText = (s: string | null | undefined) => (s || '').replace(/[*_#`]+/g, '').trim()
const displayLabel = computed(() => {
  const clean = cleanText(props.el.label)
  if (!clean && props.type === 'navbar') return '导航栏'
  return clean
})

// ===== 单个居中图标（icon 类型 / 小方形图标按钮 / 头像） =====
const isIconLike = computed(() => {
  const { el, type } = props
  if (type === 'icon') return true
  // 小尺寸近方形且有明确图标的 button/other：按圆形气泡渲染（AI 常把圆形悬浮钮误判为这两类）
  if ((type === 'button' || type === 'other') && el.width <= 72 && Math.abs(el.width - el.height) <= el.width * 0.4) {
    return !!resolveIcon(el)
  }
  return false
})

// 全屏/大面积特效（中奖遮罩、暗色遮罩等）：渲染为静态半透明遮罩，绝不参与任何闪烁动画
const isScrimEffect = computed(
  () => props.type === 'effect' && props.el.width * props.el.height >= 120000
)

const scrimStyle = computed(() => {
  const lbl = props.el.label || ''
  if (/遮罩|暗|黑|dark|mask|scrim/i.test(lbl)) {
    return { background: 'rgba(8, 8, 16, 0.45)' }
  }
  const fill = aiVars.value['--wf-fill']
  return {
    background: `radial-gradient(circle at 50% 45%, ${fill || 'rgba(255, 255, 255, 0.18)'} 0%, transparent 70%)`,
    opacity: '0.5',
  }
})

// 被误标为 effect 的小气泡/悬浮小按钮（如"领奖气泡"）：按圆形图标渲染，不画放射线
const isBubbleEffect = computed(() => {
  if (props.type !== 'effect' || props.el.width > 64) return false
  const lbl = props.el.label || ''
  return /气泡|bubble|按钮|btn/i.test(lbl) || !!resolveIcon(props.el)
})

// 开关兜底识别：无论存库类型是什么，只要形状是"宽扁小条"且 label 语义是开关，就按 switch 组件渲染。
// 长度限制 8 字以内避免把"背景音乐设置行"这类整行容器误判进来
const isSwitchFallback = computed(() => {
  if (props.type === 'switch') return false
  const { el } = props
  const lbl = (el.label || '').trim()
  if (!lbl || lbl.length > 8) return false
  if (!/^(音乐|音效|通知|震动|蓝牙|声音|推送|夜间|自动)?开关$|^toggle$|^switch$/i.test(lbl.replace(/[:：\s]/g, ''))) {
    // 放宽：label 中包含"开关"且很短也算
    if (!(/开关/.test(lbl) && lbl.length <= 6)) return false
  }
  const ratio = el.width / Math.max(el.height, 1)
  return el.height >= 10 && el.height <= 36 && ratio >= 1.5 && ratio <= 4
})

const centerGlyph = computed<IconComp | null>(() => {
  if (props.type === 'avatar') return User
  if (!isIconLike.value) return null
  return resolveIcon(props.el) || (props.type === 'icon' ? Shapes : null)
})

const centerGlyphStyle = computed(() => {
  const size = Math.min(props.el.width, props.el.height) * 0.52
  return {
    width: `${size}px`,
    height: `${size}px`,
    left: `${(props.el.width - size) / 2}px`,
    top: `${(props.el.height - size) / 2}px`,
  }
})

// 圆形底座：取短边正圆并居中
const circleStyle = computed(() => {
  const d = Math.min(props.el.width, props.el.height)
  return {
    width: `${d}px`,
    height: `${d}px`,
    left: `${(props.el.width - d) / 2}px`,
    top: `${(props.el.height - d) / 2}px`,
  }
})

// ===== 素材库：有 asset_id 时优先用素材图渲染，加载失败回退占位 =====
const assetUrl = computed(() => {
  const id = props.el.asset_id
  return id ? `/api/assets/${id}` : null
})

const imageError = ref(false)
function onImageError() {
  imageError.value = true
}

// ===== 搜索框图标位置 =====
const searchGlyphStyle = computed(() => {
  const size = Math.min(props.el.height * 0.5, 18)
  return {
    width: `${size}px`,
    height: `${size}px`,
    left: `${props.el.height * 0.3}px`,
    top: `${(props.el.height - size) / 2}px`,
  }
})

// ===== 勾选框 / 单选控件 =====
const controlSize = computed(() => Math.min(18, props.el.height * 0.7))

const controlBoxStyle = computed(() => {
  const s = controlSize.value
  return {
    width: `${s}px`,
    height: `${s}px`,
    left: `${(props.el.height - s) / 2}px`,
    top: `${(props.el.height - s) / 2}px`,
  }
})

const checkGlyphStyle = computed(() => {
  const s = controlSize.value
  return {
    width: `${s * 0.72}px`,
    height: `${s * 0.72}px`,
    left: `${(props.el.height - s) / 2 + s * 0.14}px`,
    top: `${(props.el.height - s * 0.72) / 2}px`,
  }
})

const radioDotStyle = computed(() => {
  const s = controlSize.value
  const d = s * 0.45
  return {
    width: `${d}px`,
    height: `${d}px`,
    left: `${(props.el.height - s) / 2 + (s - d) / 2}px`,
    top: `${(props.el.height - d) / 2}px`,
  }
})

// ===== 开关 / 滑杆 =====
// 开关几何：轨道在元素内水平居中，宽度随元素自适应（防止宽 bbox 下变成横贯长条）
const switchMetrics = computed(() => {
  const trackH = Math.min(props.el.height * 0.8, 22)
  const trackW = Math.min(trackH * 1.9, props.el.width * 0.7)
  return { trackH, trackW, left: (props.el.width - trackW) / 2 }
})

const switchTrackStyle = computed(() => {
  const { trackH, trackW, left } = switchMetrics.value
  return {
    width: `${trackW}px`,
    height: `${trackH}px`,
    left: `${left}px`,
    top: `${(props.el.height - trackH) / 2}px`,
    borderRadius: `${trackH / 2}px`,
  }
})

const switchKnobStyle = computed(() => {
  const { trackH, trackW, left } = switchMetrics.value
  const knob = trackH - 7
  return {
    width: `${knob}px`,
    height: `${knob}px`,
    left: `${left + trackW - knob - 3}px`,
    top: `${(props.el.height - knob) / 2}px`,
  }
})

const sliderKnobStyle = computed(() => {
  const knob = Math.min(props.el.height * 0.7, 14)
  return {
    width: `${knob}px`,
    height: `${knob}px`,
    left: `${props.el.width * 0.6 - knob / 2}px`,
    top: `${(props.el.height - knob) / 2}px`,
  }
})

// ===== 选项卡 / 评分 / 导航栏 =====
const tabItems = computed(() =>
  (cleanText(props.el.label) || '选项一/选项二/选项三')
    .split(/[/、,，|]/)
    .map((s) => s.trim())
    .filter(Boolean)
    .slice(0, 5),
)

const ratingSize = computed(() => Math.min(props.el.height * 0.8, props.el.width / 5.5, 24))

const navParts = computed(() =>
  cleanText(props.el.label)
    .split(/[/、,，|]/)
    .map((s) => s.trim())
    .filter(Boolean),
)

// navbar 形态：多项→图标行；单项或窄高型→单图标+文字；空 label→标题栏带默认文字
const navMode = computed<'row' | 'single' | 'title'>(() => {
  if (navParts.value.length >= 2) return 'row'
  if (navParts.value.length === 1 || props.el.width <= props.el.height * 2.8) return 'single'
  return 'title'
})

// 解析不到语义时用中性占位图形，绝不猜测语义图标（避免"返回键变家园"式乱用）
const navGlyphs = computed<IconComp[]>(() => {
  if (props.type !== 'navbar') return []
  if (navParts.value.length >= 2) {
    return navParts.value.map((p) => iconFor(p) || Shapes).slice(0, 5)
  }
  return [Shapes]
})

const singleNavGlyph = computed<IconComp>(() => resolveIcon(props.el) || Shapes)

const navIconSize = computed(() => Math.min(props.el.height * 0.5, 24))

// ===== 文字 =====
const labelVisible = computed(() => {
  const noLabelTypes = ['text', 'navbar', 'image', 'icon', 'checkbox', 'radio', 'switch', 'tabs', 'rating', 'search', 'avatar', 'divider', 'slider', 'progress']
  if (noLabelTypes.includes(props.type)) return props.type === 'input'
  if (isIconLike.value && centerGlyph.value) return false
  if (props.suppressInner) return false
  return !!props.el.label
})

const labelFontSize = computed(() => {
  const h = props.el.height
  return Math.max(10, Math.min(14, h * 0.4))
})

// 文本元素：真实文字，字号随元素高度
const realTextStyle = computed(() => {
  const { el } = props
  const byHeight = el.height * 0.52
  const byWidth = el.label ? (el.width / el.label.length) * 1.5 : byHeight
  const size = Math.max(10, Math.min(26, byHeight, byWidth))
  return {
    fontSize: `${size}px`,
    lineHeight: `${Math.min(el.height, size * 1.4)}px`,
    fontWeight: size >= 17 ? 700 : 400,
  }
})
</script>

<style scoped lang="scss">
/* ===== 高保真主题：真实 UI 观感（替代手绘线框） ===== */
$ink: #111827;

.wf-element {
  position: absolute;
  cursor: default;
  overflow: visible;
  color: $ink;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC',
    'Hiragino Sans GB', 'Microsoft YaHei', sans-serif;

  /* ===== 基础表面：白色卡片 + 柔和阴影（AI 取色时用真实填充/描边/圆角） ===== */
  .shape {
    position: absolute;
    inset: 0;
    box-sizing: border-box;
    background: var(--wf-fill, #fff);
    border: 1px solid var(--wf-border, #eef0f4);
    box-shadow: 0 1px 3px rgba(16, 24, 40, 0.07);
    transition: box-shadow 0.15s, background 0.15s, filter 0.15s;

    &.rect {
      border-radius: var(--wf-radius, 12px);
    }
    &.pill {
      border-radius: 999px;
    }
    &.circle {
      border-radius: 50%;
      inset: auto;
    }
    &.thin {
      box-shadow: none;
    }
    &.filled {
      background: var(--wf-fill, #fff);
    }
  }

  /* ===== 主按钮：实心渐变（AI 取到填充色时用真实色还原设计稿） ===== */
  &.wf-button .shape.filled {
    background: var(--wf-fill, linear-gradient(135deg, #6366f1, #8b5cf6));
    border: none;
    border-radius: var(--wf-radius, 10px);
    box-shadow: 0 2px 6px rgba(99, 102, 241, 0.35);

    &.pill {
      border-radius: var(--wf-radius, 999px);
    }
  }

  /* ===== 输入类：白底灰描边 ===== */
  &.wf-input .shape.filled,
  &.wf-textarea .shape.filled,
  &.wf-select .shape.filled {
    background: var(--wf-fill, #fff);
    border: 1px solid var(--wf-border, #d1d5db);
    border-radius: var(--wf-radius, 10px);
    box-shadow: none;
  }

  /* ===== 徽标：淡靛蓝底 ===== */
  &.wf-badge .shape.pill {
    background: var(--wf-fill, #eef2ff);
    border: none;
    box-shadow: none;
  }

  /* ===== 图片占位：柔和渐变 + 居中图标 ===== */
  .img-placeholder {
    position: absolute;
    inset: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    background: var(--wf-fill, linear-gradient(135deg, #f5f6f8, #eceef2));
    border-radius: var(--wf-radius, 12px);

    .placeholder-glyph {
      width: 30%;
      height: 30%;
      color: #c3c8d2;
    }
  }


  .caret {
    position: absolute;
    right: 10px;
    top: 50%;
    transform: translateY(-50%);
    pointer-events: none;

    path {
      stroke: #9ca3af;
      stroke-width: 2;
      fill: none;
      stroke-linecap: round;
      stroke-linejoin: round;
    }
  }

  .icon-glyph {
    position: absolute;
    pointer-events: none;
    color: #4b5563;
    transition: color 0.15s;
  }

  /* ===== 勾选/单选/开关/滑杆/进度：真实控件配色 ===== */
  &.wf-checkbox .check-box {
    background: #3b82f6;
    border: none;
    border-radius: 26%;

    &::after {
      content: '';
      position: absolute;
      inset: 0;
      box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.25);
      border-radius: inherit;
    }
  }

  &.wf-checkbox .icon-glyph {
    color: #fff;
  }

  &.wf-radio .radio-outer {
    border: 2px solid #3b82f6;
    background: #fff;
  }

  &.wf-radio .radio-dot {
    background: #3b82f6;
  }

  &.wf-switch .switch-track {
    background: linear-gradient(135deg, #ff8336, #fa6b19);
    box-shadow: inset 0 1px 2px rgba(16, 24, 40, 0.12);
  }

  &.wf-switch .switch-knob {
    background: #fff;
    box-shadow: 0 1px 3px rgba(16, 24, 40, 0.35);
  }

  .slider-track {
    position: absolute;
    left: 2px;
    right: 2px;
    top: 50%;
    height: 5px;
    margin-top: -2.5px;
    border-radius: 3px;
    background: linear-gradient(90deg, #6366f1 60%, #e8eaee 60%);
  }

  .slider-knob {
    position: absolute;
    border: 2px solid #6366f1;
    border-radius: 50%;
    box-sizing: border-box;
    background: #fff;
    box-shadow: 0 1px 4px rgba(16, 24, 40, 0.3);
  }

  .slider-track {
    position: absolute;
    left: 2px;
    right: 2px;
    top: 50%;
    height: 2.5px;
    margin-top: -1.25px;
    border-radius: 2px;
    background: $ink;
  }

  .slider-knob {
    position: absolute;
    border: 2px solid $ink;
    border-radius: 50%;
    box-sizing: border-box;
    background: #fff;
  }

  .progress-fill {
    position: absolute;
    left: 3px;
    top: 3px;
    bottom: 3px;
    width: 60%;
    border-radius: 999px;
    background: linear-gradient(90deg, #6366f1, #a78bfa);
  }

  /* ===== 选项卡 ===== */
  .tabs-baseline {
    position: absolute;
    left: 0;
    right: 0;
    bottom: 0;
    height: 1.5px;
    background: #eef0f4;
  }

  .tabs-row {
    position: absolute;
    inset: 0;
    display: flex;
    align-items: center;
    pointer-events: none;

    .tab-item {
      flex: 1;
      text-align: center;
      font-size: 12px;
      color: #9ca3af;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      transition: color 0.15s;

      &.active {
        color: #4f46e5;
        font-weight: 600;
        text-decoration: underline;
        text-underline-offset: 6px;
        text-decoration-thickness: 2px;
      }
    }
  }

  /* ===== 评分 / 分隔线 / 导航栏 / 列表 ===== */
  .rating-row {
    position: absolute;
    inset: 0;
    display: flex;
    align-items: center;
    gap: 3px;
    pointer-events: none;
    color: #d1d5db;

    .filled {
      fill: #f59e0b;
      color: #f59e0b;
    }
  }

  .divider-line {
    position: absolute;
    left: 0;
    right: 0;
    top: 50%;
    height: 1px;
    margin-top: -0.5px;
    background: #eef0f4;
  }

  .navbar-line {
    position: absolute;
    left: 0;
    right: 0;
    height: 1px;
    background: #e9ecf1;

    &.at-top {
      top: 0;
    }
    &.at-bottom {
      bottom: 0;
    }
  }

  .nav-icons {
    position: absolute;
    inset: 0;
    display: flex;
    align-items: center;
    justify-content: space-around;
    pointer-events: none;
    color: #374151;

    .nav-item {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 2px;

      .nav-item-label {
        font-size: 9px;
        color: #6b7280;
        white-space: nowrap;
        overflow: hidden;
        max-width: 100%;
        text-overflow: ellipsis;
      }
    }
  }

  .nav-single {
    position: absolute;
    inset: 0;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 2px;
    pointer-events: none;
    color: #374151;

    .nav-single-label {
      font-size: 10px;
      color: #6b7280;
      white-space: nowrap;
      overflow: hidden;
      max-width: 90%;
      text-overflow: ellipsis;
    }
  }

  .list-rows {
    position: absolute;
    inset: 8px 12px;
    display: flex;
    flex-direction: column;
    justify-content: space-around;
    pointer-events: none;

    .list-row {
      display: flex;
      align-items: center;
      gap: 10px;
    }
    .list-dot {
      width: 7px;
      height: 7px;
      background: #6366f1;
      border-radius: 50%;
      flex-shrink: 0;
    }
    .list-line {
      flex: 1;
      height: 8px;
      border-radius: 4px;
      background: #eceef2;
    }
  }

  /* ===== 交互态：可点击元素 hover 上浮提亮 ===== */
  &.interactive {
    cursor: pointer;

    &.is-hovered .shape {
      box-shadow: 0 4px 14px rgba(16, 24, 40, 0.16);
    }

    &.is-hovered.wf-button .shape.filled {
      filter: brightness(1.07);
    }

    &.is-hovered .img-placeholder {
      filter: brightness(0.98);
    }
  }
}

/* ===== 文字 ===== */
.wf-real-text {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  text-align: center;
  color: var(--wf-text, #111827);
  overflow: hidden;
  pointer-events: none;
  padding: 0 2px;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.wf-label {
  position: absolute;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
  color: var(--wf-text, #111827);
  max-width: calc(100% - 12px);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  pointer-events: none;
  text-align: center;
}

/* 按钮上的文字为白色（高度足够时允许多行）；徽标文字为靛蓝 */
.wf-element.wf-button .wf-label {
  color: var(--wf-text, #fff);
  text-shadow: 0 1px 1px rgba(0, 0, 0, 0.08);
  white-space: normal;
  line-height: 1.25;
  width: calc(100% - 10px);
  padding: 0 5px;
}

/* 深色背景页：无底色的浮动文字改用浅色 */
.wf-element.on-bg.wf-text .wf-real-text,
.wf-element.on-bg.wf-text .wf-label {
  color: var(--wf-text, #fff);
  text-shadow: 0 1px 3px rgba(0, 0, 0, 0.45);
}

.wf-element.wf-badge .wf-label {
  color: var(--wf-text, #4f46e5);
  font-weight: 600;
}

.wf-side-label {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  font-size: 12px;
  color: #374151;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 70%;
  pointer-events: none;
}

.wf-input-placeholder {
  position: absolute;
  left: 10px;
  top: 50%;
  transform: translateY(-50%);
  font-size: 11px;
  color: #9ca3af;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: calc(100% - 16px);
  pointer-events: none;
}
/* ===== 素材图（头像 / 图片 / 图标 / 特效 / 背景） ===== */
.wf-asset {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  border-radius: 10px;
}

.wf-avatar {
  border-radius: 50%;
  object-fit: cover;
}

.wf-icon-img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: contain;
  display: block;
}

/* 全屏遮罩类特效：静态半透明层，无动画 */
.wf-scrim {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.wf-effect-img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  /* 特效/光晕作为覆盖层：半透明显示，避免盖住下方元素 */
  opacity: 0.55;
}

/* 烟花式特效：中心柔和底光（静态）+ 多根放射细线各自错峰闪烁 */
.wf-effect-rays {
  position: absolute;
  inset: 0;
  pointer-events: none;

  &::before {
    content: '';
    position: absolute;
    inset: -12%;
    background: radial-gradient(circle at 50% 50%, var(--wf-fill, rgba(253, 224, 71, 0.35)) 0%, transparent 62%);
    opacity: 0.75;
  }

  .wf-ray {
    position: absolute;
    height: 2px;
    border-radius: 2px;
    transform-origin: left center;
    background: var(--wf-fill, rgba(253, 224, 71, 0.95));
    filter: drop-shadow(0 0 4px var(--wf-fill, rgba(253, 224, 71, 0.9)));
    animation: wf-ray-twinkle 2s ease-in-out infinite;
  }

  /* 每第 3 根用白色，形成烟花的多彩层次 */
  .wf-ray:nth-child(3n) {
    background: rgba(255, 255, 255, 0.95);
    filter: drop-shadow(0 0 3px rgba(255, 255, 255, 0.9));
  }
}

@keyframes wf-ray-twinkle {
  0%,
  100% {
    opacity: 0.12;
  }
  50% {
    opacity: 1;
  }
}

/* 素材图特效同样呼吸，观感一致 */
.wf-effect-img {
  animation: wf-glow-pulse 3.2s ease-in-out infinite;
  will-change: opacity, transform;
}

@keyframes wf-glow-pulse {
  0%,
  100% {
    opacity: 0.55;
    transform: scale(1);
  }
  50% {
    opacity: 1;
    transform: scale(1.06);
  }
}

.wf-background {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  border-radius: 10px;
}

</style>
