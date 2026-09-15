export interface Project {
  id: number
  name: string
  description?: string
  coverImage?: string
  createdAt?: string
}

export interface Interaction {
  trigger: string
  action: string
  target_page_id?: number | null
  params?: string | null
}

export interface Element {
  id: number
  type: string
  label: string
  /** AI 指定的 Lucide 图标名（kebab-case，如 shopping-cart），null 表示未指定 */
  icon?: string | null
  /** AI 提取的视觉样式 JSON：{icon, fill, text_color, border_color, radius}，用于高保真还原设计稿 */
  style?: string | null
  /** 素材库 ID（avatar-03 / product-01 / icon-home / bg-01 / effect-glow 等），有则优先用素材图渲染 */
  asset_id?: string | null
  x: number
  y: number
  width: number
  height: number
  interaction?: Interaction
}

export interface Annotation {
  id: number
  element_id: number | null
  text: string
  x?: number | null
  y?: number | null
  /** 标注框位置（画布 px，null=自动布局） */
  box_x?: number | null
  box_y?: number | null
  /** 引线元素端锚点（线框逻辑坐标，null=自动跟随元素） */
  anchor_x?: number | null
  anchor_y?: number | null
  /** 引线竖折线 X（画布 px，null=自动） */
  elbow_x?: number | null
  /** 排序权重（越小越靠前） */
  sort_order?: number
}

export interface Page {
  id: number
  name: string
  background_image: string
  canvas_width: number
  canvas_height: number
  /** 区块在无限画布上的位置（null=自动布局） */
  canvas_x?: number | null
  canvas_y?: number | null
  analyzed: number
  /** Stitch 式整页直出：AI 生成的完整 HTML/CSS 页面（null=未生成） */
  html_content?: string | null
  elements: Element[]
  annotations: Annotation[]
}

export interface Prototype {
  project: Project
  pages: Page[]
}