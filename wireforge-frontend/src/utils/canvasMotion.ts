export type CanvasView = { x: number; y: number; k: number }

/** Fit artboards above the floating toolbar, with room for their titles. */
export function fitCanvasBounds(items: { x: number; y: number; w: number; h: number }[], width: number, height: number): CanvasView | null {
  const bounds = items.filter(b => [b.x, b.y, b.w, b.h].every(Number.isFinite) && b.w > 0 && b.h > 0)
  if (!bounds.length || width <= 0 || height <= 0) return null
  const left = Math.min(...bounds.map(b => b.x)), top = Math.min(...bounds.map(b => b.y - 36))
  const right = Math.max(...bounds.map(b => b.x + b.w)), bottom = Math.max(...bounds.map(b => b.y + b.h))
  const availableW = Math.max(1, width - Math.min(64, width * .08) * 2), availableH = Math.max(1, height - 136)
  const k = Math.min(1.5, Math.max(.04, Math.min(availableW / (right - left), availableH / (bottom - top))))
  return { k, x: width / 2 - (left + right) / 2 * k, y: 40 + availableH / 2 - (top + bottom) / 2 * k }
}

export function canvasTransform(view: CanvasView): string {
  return `translate3d(${view.x}px, ${view.y}px, 0) scale(${view.k})`
}

export function zoomCanvasAt(view: CanvasView, x: number, y: number, factor: number): CanvasView {
  const k = Math.min(3, Math.max(0.04, view.k * factor))
  return { k, x: x - ((x - view.x) * k) / view.k, y: y - ((y - view.y) * k) / view.k }
}

/** One paint per animation frame. Hidden windows need no timer-driven paints;
 * gesture release still flushes the last sample through finish(). */
export function scheduleCanvasPaint(callback: () => void): () => void {
  const frame = requestAnimationFrame(callback)
  return () => cancelAnimationFrame(frame)
}

/** Keep logical input synchronous; coalesce all gesture DOM writes per frame.
 * Release flushes the final sample before commit, even before the first RAF. */
export function createCanvasMotion(options: {
  read: () => CanvasView
  update?: (view: CanvasView) => void
  paint: (view: CanvasView) => void
  commit: (view: CanvasView) => void
  schedule?: (callback: () => void) => () => void
}) {
  let dirty = false
  let disposed = false
  let target: CanvasView | null = null, pending: CanvasView | null = null
  let cancelScheduled: (() => void) | null = null
  let generation = 0

  function paint(next: CanvasView) {
    if (disposed) return
    dirty = true
    target = next
    options.update?.(next)
    if (!options.schedule) { options.paint(next); return }
    pending = next
    if (cancelScheduled) return
    const current = generation
    cancelScheduled = options.schedule(() => {
      if (disposed || current !== generation) return
      cancelScheduled = null
      if (pending) { const last = pending; pending = null; options.paint(last) }
    })
  }

  function cancel() {
    generation++
    cancelScheduled?.(); cancelScheduled = null
    pending = null; target = null
    dirty = false
  }

  return {
    panTo(x: number, y: number) {
      paint({ ...(target || options.read()), x, y })
    },
    zoomAt(x: number, y: number, factor: number) {
      // Compose every wheel sample, including samples with a different anchor.
      paint(zoomCanvasAt(target || options.read(), x, y, factor))
    },
    finish() {
      if (disposed) return
      if (!dirty) return
      const next = { ...(target || options.read()) }
      if (pending) options.paint(pending)
      cancel()
      options.commit(next)
    },
    cancel,
    dispose() {
      cancel()
      disposed = true
    },
  }
}
