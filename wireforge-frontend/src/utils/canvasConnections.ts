import type { CanvasView } from './canvasMotion'

export type ConnectionBounds = { left: number; top: number; right: number; bottom: number }

/** Conservative control-polygon bounds for our absolute M/C/S connection paths.
 * Includes the reflected control point of S; endpoint-only clipping loses loops
 * and curves crossing the viewport with both endpoints outside it.
 */
export function connectionPathBounds(path: string): ConnectionBounds | null {
  const parts = [...path.matchAll(/([MCS])([^MCS]*)/g)]
  if (!parts.length || parts.map(p => p[0]).join('').trim() !== path.trim()) return null
  const xs: number[] = [], ys: number[] = []
  let x = 0, y = 0, cx = 0, cy = 0
  for (const [, command, args] of parts) {
    const values = args.trim().split(/[\s,]+/).map(Number)
    if (values.some(v => !Number.isFinite(v)) || values.length !== (command === 'M' ? 2 : command === 'C' ? 6 : 4)) return null
    if (command === 'S') { xs.push(2 * x - cx); ys.push(2 * y - cy) }
    for (let i = 0; i < values.length; i += 2) { xs.push(values[i]); ys.push(values[i + 1]) }
    x = values[values.length - 2]; y = values[values.length - 1]
    cx = command === 'M' ? x : values[values.length - 4]
    cy = command === 'M' ? y : values[values.length - 3]
  }
  return { left: Math.min(...xs), top: Math.min(...ys), right: Math.max(...xs), bottom: Math.max(...ys) }
}

export function connectionInViewport(bounds: ConnectionBounds | null, midX: number, midY: number,
  view: CanvasView, width: number, height: number, margin = 160): boolean {
  if (!bounds || view.k <= 0) return true
  // Include badge, wide click stroke and arrowheads in the conservative bounds.
  const left = Math.min(bounds.left - 20, midX - 160), right = Math.max(bounds.right + 20, midX + 160)
  const top = Math.min(bounds.top - 20, midY - 24), bottom = Math.max(bounds.bottom + 20, midY + 24)
  return right * view.k + view.x >= -margin && left * view.k + view.x <= width + margin
    && bottom * view.k + view.y >= -margin && top * view.k + view.y <= height + margin
}
