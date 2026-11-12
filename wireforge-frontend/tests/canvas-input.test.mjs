import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import { transformSync } from 'esbuild'

const module = { exports: {} }
let clock = 0
vm.runInNewContext(transformSync(readFileSync('src/utils/canvasInput.ts', 'utf8'), { loader: 'ts', format: 'cjs' }).code, { module, exports: module.exports, performance: { now: () => clock } })
const { installCanvasInput, canvasWheelIntent, canvasShortcut } = module.exports
function setup() {
  const listeners = new Map(), events = []
  let rect = { left: 100, top: 200, width: 300, height: 640 }, reads = 0
  const target = { addEventListener(name, fn) { listeners.set(name, fn) }, removeEventListener(name, fn) { if (listeners.get(name) === fn) listeners.delete(name) } }
  const frame = { contentDocument: { defaultView: target }, clientWidth: 375, clientHeight: 800, getBoundingClientRect() { reads++; return rect } }
  clock = 0
  const handlers = Object.fromEntries(['wheel', 'panKey', 'panStart', 'panMove', 'panEnd', 'shortcut'].map(name => [name, value => events.push({ name, value })]))
  const bridge = installCanvasInput(frame, handlers)
  return { bridge, frame, handlers, listeners, events, advance(ms) { clock += ms }, moveFrame() { rect = { ...rect, left: 999, top: 999 } }, get reads() { return reads },
    send(name, values = {}) {
      let prevented = false, stopped = false
      const e = { clientX: 0, clientY: 0, screenX: 0, screenY: 0, deltaX: 0, ctrlKey: false, metaKey: false, shiftKey: false, target: { closest() { return false } }, ...values, preventDefault() { prevented = true }, stopImmediatePropagation() { stopped = true } }
      listeners.get(name)?.(e)
      return { prevented, stopped }
    },
  }
}

test('iframe wheel synchronously reaches canvas coordinates and consumes the native scroll', () => {
  const s = setup()
  assert.deepEqual(s.send('wheel', { clientX: 100, clientY: 50, deltaY: -120, deltaMode: 0 }), { prevented: true, stopped: true })
  assert.equal(s.events.length, 1)
  assert.deepEqual(JSON.parse(JSON.stringify(s.events[0])), { name: 'wheel', value: { clientX: 180, clientY: 240, deltaY: -120, deltaX: 0, deltaMode: 0, ctrlKey: false, metaKey: false, shiftKey: false } })
  s.send('wheel', { target: { closest() { return true } } })
  assert.equal(s.events.length, 1)
})

test('workbench point mapping avoids geometry reads for wheel and pan while standalone frames retain measurement',()=>{
  const s=setup()
  s.handlers.point=e=>({clientX:200+e.clientX*.5,clientY:300+e.clientY*.5})
  s.send('wheel',{clientX:100,clientY:50,screenX:500,screenY:400,deltaY:-1})
  assert.equal(s.events[0].value.clientX,250);assert.equal(s.events[0].value.clientY,325)
  s.send('mousedown',{button:1,clientX:120,clientY:80,screenX:500,screenY:400})
  assert.equal(s.events.at(-1).value.clientX,260);assert.equal(s.events.at(-1).value.clientY,340)
  assert.equal(s.reads,0)
  s.handlers.point=()=>null;s.advance(200)
  s.send('wheel',{clientX:100,clientY:50,deltaY:-1})
  assert.equal(s.reads,1)
  assert.equal(s.events.at(-1).value.clientX,180);assert.equal(s.events.at(-1).value.clientY,240)
})

test('an ordinary iframe click settles the pending camera before editing without consuming the event',()=>{
  const s=setup();let settled=0;s.handlers.settle=()=>settled++
  const event=s.send('mousedown',{button:0})
  assert.equal(settled,1);assert.deepEqual(event,{prevented:false,stopped:false})
  assert.equal(s.reads,0)
  s.send('mousedown',{button:0,target:{closest:()=>true}});assert.equal(settled,1)
})

test('wheel bursts measure the transformed frame once and follow cursor movement in screen coordinates', () => {
  const s = setup()
  s.send('wheel', { clientX: 100, clientY: 50, screenX: 500, screenY: 400, deltaY: 1 })
  s.moveFrame()
  for (let i = 1; i <= 120; i++) { s.advance(1); s.send('wheel', { screenX: 500 + i, screenY: 400 + i / 2, deltaY: -1, ctrlKey: true }) }
  assert.equal(s.reads, 1)
  assert.equal(s.events.at(-1).value.clientX, 300)
  assert.equal(s.events.at(-1).value.clientY, 300)
  assert.equal(s.events.at(-1).value.ctrlKey, true)
  s.advance(200); s.send('wheel', { clientX: 100, clientY: 50, screenX: 500, screenY: 400, deltaY: -1 })
  assert.equal(s.reads, 2)
  assert.equal(s.events.at(-1).value.clientX, 1079)
})

test('trackpad pans on both axes; mouse retains wheel zoom and Shift pans horizontally in either mode', () => {
  const event = { clientX: 0, clientY: 0, deltaX: 12, deltaY: 30 }
  assert.equal(canvasWheelIntent(event, 'mouse', 800).kind, 'zoom')
  assert.deepEqual({ ...canvasWheelIntent(event, 'trackpad', 800) }, { kind: 'pan', x: 12, y: 30 })
  for (const mode of ['mouse', 'trackpad']) {
    assert.deepEqual({ ...canvasWheelIntent({ ...event, deltaX: 0, shiftKey: true }, mode, 800) }, { kind: 'pan', x: 30, y: 0 })
    assert.equal(canvasWheelIntent({ ...event, ctrlKey: true }, mode, 800).kind, 'zoom')
    assert.equal(canvasWheelIntent({ ...event, metaKey: true }, mode, 800).kind, 'zoom')
  }
  assert.deepEqual({ ...canvasWheelIntent({ ...event, deltaMode: 1 }, 'trackpad', 800) }, { kind: 'pan', x: 192, y: 480 })
})

test('canvas shortcuts work over iframes while text fields keep their own input', () => {
  const s = setup()
  assert.deepEqual(s.send('keydown', { key: '@', code: 'Digit2', shiftKey: true }), { prevented: true, stopped: true })
  assert.equal(s.events.at(-1).value, 'fit-selection')
  s.send('keydown', { key: '0', ctrlKey: true, target: { closest() { return true } } })
  assert.equal(s.events.length, 1)
  assert.equal(canvasShortcut({ key: '=', ctrlKey: true }), 'zoom-in')
  assert.equal(canvasShortcut({ key: '-', metaKey: true }), 'zoom-out')
  assert.equal(canvasShortcut({ key: '1', shiftKey: true, altKey: true }), undefined)
  assert.equal(canvasShortcut({ key: '1', shiftKey: true, isComposing: true }), undefined)
})

test('losing frame focus releases pan and Space and resets wheel geometry', () => {
  const s = setup()
  s.send('wheel', { deltaY: -1 }); s.send('keydown', { code: 'Space' })
  s.send('mousedown', { button: 0, screenX: 500, screenY: 400 })
  s.send('blur')
  assert.equal(s.events.at(-2).name, 'panEnd')
  assert.equal(s.events.at(-1).value, false)
  const before = s.events.length
  s.send('mousedown', { button: 0 })
  assert.equal(s.events.length, before)
})

test('the gesture shield does not end a drag, and releasing Space in the parent clears the iframe hand tool', () => {
  const s = setup()
  s.frame.ownerDocument = { hasFocus: () => true }
  s.send('keydown', { code: 'Space' })
  s.send('mousedown', { button: 0, screenX: 500, screenY: 400 })
  const before = s.events.length
  s.send('blur')
  assert.equal(s.events.length, before)
  s.send('mousemove', { screenX: 510, screenY: 420, buttons: 1 })
  assert.equal(s.events.at(-1).name, 'panMove')
  s.bridge.stopPan(); s.bridge.setSpacePressed(false)
  const after = s.events.length
  s.send('mousedown', { button: 0 })
  assert.equal(s.events.length, after)
})

test('middle-button pan uses screen deltas even as its frame moves, with no per-move layout reads', () => {
  const s = setup()
  s.send('mousedown', { button: 1, clientX: 100, clientY: 50, screenX: 500, screenY: 400 })
  s.moveFrame()
  s.send('mousemove', { screenX: 524, screenY: 412, buttons: 4 })
  assert.deepEqual(JSON.parse(JSON.stringify(s.events[1])), { name: 'panMove', value: { clientX: 204, clientY: 252, buttons: 4 } })
  assert.equal(s.reads, 1)
  s.send('mouseup'); s.send('mousemove', { screenX: 800, screenY: 800, buttons: 0 })
  assert.equal(s.events.length, 3)
  assert.equal(s.events[2].name, 'panEnd')
})

test('space pan retains the held key after mouse release; dispose removes every bridge listener', () => {
  const s = setup()
  s.send('keydown', { code: 'Space' })
  s.send('mousedown', { button: 0, screenX: 500, screenY: 400 })
  s.bridge.stopPan()
  s.send('keyup', { code: 'Space' })
  assert.equal(s.events.at(-1).name, 'panKey')
  assert.equal(s.events.at(-1).value, false)
  s.bridge.dispose()
  assert.equal(s.listeners.size, 0)
  const next = installCanvasInput(s.frame, s.handlers)
  s.send('wheel', { deltaY: 10 }); assert.equal(s.events.filter(e => e.name === 'wheel').length, 1)
  next.dispose(); assert.equal(s.listeners.size, 0)
})
