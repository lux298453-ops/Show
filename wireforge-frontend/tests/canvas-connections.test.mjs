import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import { transformSync } from 'esbuild'
const module={exports:{}}
vm.runInNewContext(transformSync(readFileSync('src/utils/canvasConnections.ts','utf8'),{loader:'ts',format:'cjs'}).code,{module,exports:module.exports})
const {connectionPathBounds:bounds,connectionInViewport:visible}=module.exports

test('a curve crossing the viewport survives culling even with both endpoints outside',()=>{
  const path='M -500 200 C -400 -200, 1400 -200, 1500 200'
  assert.equal(visible(bounds(path),500,-100,{x:0,y:0,k:1},1000,700),true)
  assert.equal(visible(bounds('M 5000 200 C 5100 180, 5900 180, 6000 200'),5500,185,{x:0,y:0,k:1},1000,700),false)
})

test('self-loop bounds include the reflected S control point and camera scaling/negative coordinates',()=>{
  const b=bounds('M -200 300 C -160 300, -160 -35, -175 -35 S -200 -12, -200 0')
  assert.deepEqual({...b},{left:-200,top:-35,right:-160,bottom:300})
  assert.equal(visible(b,-175,-25,{x:300,y:200,k:1.5},800,600),true)
  assert.equal(visible(b,-175,-25,{x:1500,y:200,k:1.5},800,600),false)
  const reflected=bounds('M 0 0 C 0 0, 100 -300, 200 0 S 300 0, 400 0')
  assert.equal(reflected.bottom,300)
})

test('badge/arrow margin remains clickable near edges, unknown paths are never incorrectly culled',()=>{
  assert.equal(visible(bounds('M 1200 0 C 1210 0, 1220 0, 1230 0'),1100,0,{x:0,y:0,k:1},1000,700),true)
  assert.equal(visible(bounds('M 0 0 Q 1 2 3 4'),9000,0,{x:0,y:0,k:1},1000,700),true)
})
