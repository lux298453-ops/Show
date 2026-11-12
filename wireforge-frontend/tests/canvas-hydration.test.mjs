import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import { transformSync } from 'esbuild'
import ts from 'typescript'

const module={exports:{}}
vm.runInNewContext(transformSync(readFileSync('src/utils/canvasHydration.ts','utf8'),{loader:'ts',format:'cjs'}).code,{module,exports:module.exports})
const {visiblePrototypeBlocks}=module.exports
const block=(key,x,y,blockType='prototype')=>({key,x,y,w:375,h:833,blockType})
const blocks=[block('near',450,0),block('current',0,0),block('far',20000,0),block('design',0,0,'design')]
const view={x:0,y:0,k:1}

test('initial canvas queues only visible and nearby prototypes, ordered by distance',()=>{
  assert.deepEqual(Array.from(visiblePrototypeBlocks(blocks,new Set(),view,800,900),b=>b.key),['current','near'])
  assert.equal(visiblePrototypeBlocks(blocks,new Set(['current','near']),view,800,900).length,0)
})

test('panning loads the newly visible page; overview can still load all pages in view',()=>{
  assert.deepEqual(Array.from(visiblePrototypeBlocks(blocks,new Set(['current']),{x:-20000,y:0,k:1},800,900),b=>b.key),['far'])
  assert.equal(visiblePrototypeBlocks(blocks,new Set(),{x:0,y:0,k:.03},800,900).length,3)
})

test('preload margin is measured in screen pixels at different zoom levels',()=>{
  assert.equal(visiblePrototypeBlocks([block('outside',530,0)],new Set(),{x:0,y:0,k:2},800,900).length,0)
  assert.equal(visiblePrototypeBlocks([block('near',515,0)],new Set(),{x:0,y:0,k:2},800,900).length,1)
})

test('rescheduling drops distant pending work without unloading an already edited page',()=>{
  const script=readFileSync('src/views/PrototypeView.vue','utf8').split('<script setup lang="ts">')[1].split('</script>')[0]
  const ast=ts.createSourceFile('view.ts',script,ts.ScriptTarget.Latest,true,ts.ScriptKind.TS)
  const fn=ast.statements.find(n=>ts.isFunctionDeclaration(n)&&n.name?.text==='scheduleQueueUpdate').getText(ast)
  const context={isAnyDragging:{value:false},isWheelZooming:{value:false},blocks:{value:blocks},openedBlockKeys:{value:new Set(['current','near'])},view:{value:view},viewportWidth:{value:800},viewportHeight:{value:900},pendingQueue:['far'],startQueuePump(){},visiblePrototypeBlocks}
  vm.createContext(context);vm.runInContext(transformSync(fn+'\nscheduleQueueUpdate()',{loader:'ts'}).code,context)
  assert.equal(context.pendingQueue.length,0)
  assert.deepEqual([...context.openedBlockKeys.value],['current','near'])
  context.view.value={x:-20000,y:0,k:1}
  vm.runInContext('scheduleQueueUpdate()',context)
  assert.deepEqual([...context.openedBlockKeys.value],['current','near','far'])
})
