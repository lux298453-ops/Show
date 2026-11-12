export type CanvasPoint = { clientX: number; clientY: number }
type Point = CanvasPoint
export type CanvasWheel = Point & { deltaY: number; deltaX?: number; deltaMode?: number; ctrlKey?: boolean; metaKey?: boolean; shiftKey?: boolean }
export type CanvasNavigationMode = 'mouse' | 'trackpad'
export type CanvasShortcut = 'fit-all' | 'fit-selection' | 'reset-zoom' | 'zoom-in' | 'zoom-out'

export function canvasShortcut(e: Pick<KeyboardEvent, 'key' | 'code' | 'ctrlKey' | 'metaKey' | 'shiftKey' | 'altKey' | 'isComposing'>): CanvasShortcut | undefined {
  if (e.altKey || e.isComposing) return
  if (e.ctrlKey || e.metaKey) {
    if (e.key === '0') return 'reset-zoom'
    if (e.key === '+' || e.key === '=') return 'zoom-in'
    if (e.key === '-') return 'zoom-out'
  } else if (e.shiftKey) {
    if (e.code === 'Digit1' || e.key === '1' || e.key === '!') return 'fit-all'
    if (e.code === 'Digit2' || e.key === '2' || e.key === '@') return 'fit-selection'
  }
}

export function canvasWheelIntent(e: CanvasWheel, mode: CanvasNavigationMode, pageHeight: number) {
  const unit = e.deltaMode === 1 ? 16 : e.deltaMode === 2 ? pageHeight : 1
  const x = (e.deltaX || 0) * unit, y = e.deltaY * unit
  if (e.ctrlKey || e.metaKey || (mode === 'mouse' && !e.shiftKey)) return { kind: 'zoom' as const, factor: Math.exp(-y * .0012) }
  return { kind: 'pan' as const, x: e.shiftKey ? (x || y) : x, y: e.shiftKey ? 0 : y }
}

/** Same-origin workbench input must not take a postMessage + animation-frame detour. */
export function installCanvasInput(frame: HTMLIFrameElement, handlers: {
  point?: (point: CanvasPoint) => CanvasPoint | null
  wheel: (event: CanvasWheel) => void
  panKey: (pressed: boolean) => void
  panStart: (event: Point & { button: number }) => void
  panMove: (event: Point & { buttons: number }) => void
  panEnd: () => void
  shortcut?: (command: CanvasShortcut) => void
  settle?: () => void
}) {
  const doc = frame.contentDocument!
  const target = doc.defaultView!
  let origin: (Point & { screenX: number; screenY: number }) | null = null
  let space = false
  let wheelAnchor: (Point & { screenX: number; screenY: number; time: number }) | null = null
  const typing = (target: EventTarget | null) => (target as HTMLElement)?.closest?.('input,textarea,select,[contenteditable="true"],[role="textbox"]')
  const point = (e: Point) => {
    const mapped = handlers.point?.(e)
    if (mapped) return mapped
    const r = frame.getBoundingClientRect()
    return { clientX: r.left + e.clientX * r.width / (frame.clientWidth || 1), clientY: r.top + e.clientY * r.height / (frame.clientHeight || 1) }
  }
  const wheel = (e: WheelEvent) => {
    if (typing(e.target)) return
    e.preventDefault(); e.stopImmediatePropagation()
    const time = performance.now()
    // Screen deltas retain the cursor anchor as the iframe itself transforms,
    // without forcing layout after each preceding transform write.
    if (!wheelAnchor || time - wheelAnchor.time > 180) wheelAnchor = { ...point(e), screenX: e.screenX, screenY: e.screenY, time }
    const position = { clientX: wheelAnchor.clientX + e.screenX - wheelAnchor.screenX, clientY: wheelAnchor.clientY + e.screenY - wheelAnchor.screenY }
    wheelAnchor.time = time
    handlers.wheel({ ...position, deltaY: e.deltaY, deltaX: e.deltaX, deltaMode: e.deltaMode, ctrlKey: e.ctrlKey, metaKey: e.metaKey, shiftKey: e.shiftKey })
  }
  const keyDown = (e: KeyboardEvent) => {
    const command = canvasShortcut(e)
    if (command && handlers.shortcut && !typing(e.target)) {
      e.preventDefault(); e.stopImmediatePropagation(); wheelAnchor = null
      handlers.shortcut(command); return
    }
    if (e.code !== 'Space' || e.isComposing || e.ctrlKey || e.metaKey || e.altKey || typing(e.target)) return
    e.preventDefault(); e.stopImmediatePropagation()
    space = true; handlers.panKey(true)
  }
  const keyUp = (e: KeyboardEvent) => {
    if (e.code !== 'Space' || !space) return
    e.stopImmediatePropagation(); space = false; handlers.panKey(false)
  }
  const down = (e: MouseEvent) => {
    if ((e.button === 0 || e.button === 1) && !typing(e.target)) {
      // Editing/hit testing must start from the last painted camera, even if
      // the wheel's trailing RAF has not run yet. Do not consume normal clicks.
      handlers.settle?.(); wheelAnchor = null
    }
    if (e.button !== 1 && !(space && e.button === 0)) return
    if (typing(e.target)) return
    e.preventDefault(); e.stopImmediatePropagation()
    wheelAnchor = null
    origin = { ...point(e), screenX: e.screenX, screenY: e.screenY }
    handlers.panStart({ ...origin, button: e.button })
  }
  const move = (e: MouseEvent) => {
    if (!origin) return
    e.preventDefault(); e.stopImmediatePropagation()
    handlers.panMove({ clientX: origin.clientX + e.screenX - origin.screenX, clientY: origin.clientY + e.screenY - origin.screenY, buttons: e.buttons })
  }
  const up = (e: MouseEvent) => {
    if (!origin) return
    e.preventDefault(); e.stopImmediatePropagation()
    origin = null; handlers.panEnd()
  }
  const blur = () => {
    // Showing the gesture shield can move focus from the iframe to its parent.
    // Only losing the application window should end the ongoing drag.
    if (frame.ownerDocument?.hasFocus()) return
    const wasPanning = !!origin, wasSpace = space
    origin = null; space = false; wheelAnchor = null
    if (wasPanning) handlers.panEnd()
    if (wasSpace) handlers.panKey(false)
  }
  target.addEventListener('wheel', wheel, { capture: true, passive: false })
  target.addEventListener('keydown', keyDown, true)
  target.addEventListener('keyup', keyUp, true)
  target.addEventListener('mousedown', down, true)
  target.addEventListener('mousemove', move, true)
  target.addEventListener('mouseup', up, true)
  target.addEventListener('blur', blur)
  const resized = () => { wheelAnchor = null }
  target.addEventListener('resize', resized)
  return {
    stopPan() { origin = null; wheelAnchor = null },
    setSpacePressed(pressed: boolean) { space = pressed },
    dispose() {
      origin = null; wheelAnchor = null
      target.removeEventListener('wheel', wheel, true)
      target.removeEventListener('keydown', keyDown, true)
      target.removeEventListener('keyup', keyUp, true)
      target.removeEventListener('mousedown', down, true)
      target.removeEventListener('mousemove', move, true)
      target.removeEventListener('mouseup', up, true)
      target.removeEventListener('blur', blur)
      target.removeEventListener('resize', resized)
    },
  }
}
