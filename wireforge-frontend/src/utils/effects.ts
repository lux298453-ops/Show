export type WfEffectType = 'inner-shadow' | 'drop-shadow' | 'layer-blur' | 'background-blur'

export interface WfEffect {
  id: string
  type: WfEffectType
  visible: boolean
  x: number
  y: number
  blur: number
  spread: number
  color: string
  opacity: number
}

export const effectMenu: { type: WfEffectType; label: string; hint: string }[] = [
  { type: 'inner-shadow', label: '内阴影', hint: 'Inner shadow' },
  { type: 'drop-shadow', label: '外阴影', hint: 'Drop shadow' },
  { type: 'layer-blur', label: '图层模糊', hint: 'Layer blur' },
  { type: 'background-blur', label: '背景模糊', hint: 'Background blur' },
]

export function effectLabel(type: WfEffectType): string {
  return effectMenu.find((item) => item.type === type)?.label || '效果'
}

export function isBlurEffect(type: WfEffectType): boolean {
  return type === 'layer-blur' || type === 'background-blur'
}

export function createEffect(type: WfEffectType): WfEffect {
  const blur = type === 'background-blur' ? 16 : 4
  return {
    id: 'fx' + Math.random().toString(36).slice(2, 8),
    type,
    visible: true,
    x: 0,
    y: type === 'drop-shadow' || type === 'inner-shadow' ? 4 : 0,
    blur,
    spread: 0,
    color: '#000000',
    opacity: 25,
  }
}

function splitList(value: string): string[] {
  const out: string[] = []
  let buf = ''
  let depth = 0
  for (const ch of value) {
    if (ch === '(') depth++
    if (ch === ')') depth--
    if (ch === ',' && depth === 0) {
      if (buf.trim()) out.push(buf.trim())
      buf = ''
      continue
    }
    buf += ch
  }
  if (buf.trim()) out.push(buf.trim())
  return out
}

function colorFromCss(raw: string): { color: string; opacity: number } {
  const text = raw.trim()
  const hex = text.match(/#([0-9a-fA-F]{6}|[0-9a-fA-F]{3})/)
  if (hex) return { color: '#' + hex[1], opacity: 100 }
  const rgba = text.match(/rgba?\(\s*(\d+)[,\s]+(\d+)[,\s]+(\d+)(?:[,\s/]+([\d.]+))?/)
  if (!rgba) return { color: '#000000', opacity: 25 }
  const toHex = (n: string) => Math.max(0, Math.min(255, parseInt(n, 10) || 0)).toString(16).padStart(2, '0')
  const alpha = rgba[4] === undefined ? 1 : parseFloat(rgba[4])
  return {
    color: '#' + toHex(rgba[1]) + toHex(rgba[2]) + toHex(rgba[3]),
    opacity: Math.round((Number.isFinite(alpha) ? alpha : 1) * 100),
  }
}

function shadowFromCss(chunk: string, index: number): WfEffect | null {
  const inset = /inset/i.test(chunk)
  const nums = chunk.match(/-?\d+(?:\.\d+)?px/g)
  if (!nums || nums.length < 2) return null
  const parsed = colorFromCss(chunk)
  return {
    id: 'legacy' + index,
    type: inset ? 'inner-shadow' : 'drop-shadow',
    visible: true,
    x: parseFloat(nums[0]) || 0,
    y: parseFloat(nums[1]) || 0,
    blur: nums[2] ? parseFloat(nums[2]) || 0 : 0,
    spread: nums[3] ? parseFloat(nums[3]) || 0 : 0,
    color: parsed.color,
    opacity: parsed.opacity,
  }
}

function effectsFromLegacy(info?: { inlineShadow?: string; inlineFilter?: string; boxShadow?: string }): WfEffect[] {
  const list: WfEffect[] = []
  const shadow = info?.inlineShadow || ''
  if (shadow && shadow !== 'none') {
    splitList(shadow).forEach((chunk, index) => {
      const fx = shadowFromCss(chunk, index)
      if (fx) list.push(fx)
    })
  }
  const filter = info?.inlineFilter || ''
  const drops = filter.match(/drop-shadow\(([^)]+)\)/g) || []
  drops.forEach((chunk, index) => {
    const fx = shadowFromCss(chunk, list.length + index)
    if (fx) list.push({ ...fx, type: 'drop-shadow', spread: 0 })
  })
  const layer = filter.match(/blur\(([\d.]+)px\)/)
  if (layer) {
    list.push({ ...createEffect('layer-blur'), id: 'legacy-blur', blur: parseFloat(layer[1]) || 0 })
  }
  return list
}

export function parseStoredEffects(info?: {
  effects?: string
  inlineShadow?: string
  inlineFilter?: string
  boxShadow?: string
} | null): WfEffect[] {
  const raw = info?.effects || ''
  if (raw) {
    try {
      const parsed = JSON.parse(raw)
      if (Array.isArray(parsed)) {
        return parsed
          .filter((item) => item && effectMenu.some((menu) => menu.type === item.type))
          .map((item, index) => ({
            id: String(item.id || 'fx' + index),
            type: item.type as WfEffectType,
            visible: item.visible !== false,
            x: Number(item.x) || 0,
            y: Number(item.y) || 0,
            blur: Number(item.blur) || 0,
            spread: Number(item.spread) || 0,
            color: typeof item.color === 'string' ? item.color : '#000000',
            opacity: Number.isFinite(Number(item.opacity)) ? Number(item.opacity) : 25,
          }))
      }
    } catch {
      /* 旧数据继续往下解析 */
    }
  }
  return effectsFromLegacy(info || undefined)
}
