import {test} from 'node:test'
import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import vm from 'node:vm'
import {transformSync} from 'esbuild'
import ts from 'typescript'
import {ref} from 'vue'

const script=readFileSync('src/views/PrototypeView.vue','utf8').split('<script setup lang="ts">')[1].split('</script>')[0]
const ast=ts.createSourceFile('view.ts',script,ts.ScriptTarget.Latest,true,ts.ScriptKind.TS)
const source=ast.statements.find(n=>ts.isFunctionDeclaration(n)&&n.name?.text==='syncEditingSession').getText(ast)
function setup(initial={}){
  const locks=[],statuses=ref(initial)
  const c={id:1,clientId:ref('fixture'),pageEditingConflicts:statuses,activeEditingPageId:ref(null),lastLockedPageId:null,
    pages:ref([{id:2,name:'Test page'}]),showToast(){},
    projectApi:{getAllPageEditingStatuses:async()=>JSON.parse(JSON.stringify(initial)),lockPageEditing:async(_id,pageId,body)=>{locks.push({pageId,...body});return{conflict:false}}}}
  vm.createContext(c);vm.runInContext(transformSync(source,{loader:'ts'}).code,c)
  return{c,locks,statuses}
}

test('unchanged collaboration status retains its reactive identity instead of redrawing all artboards',async()=>{
  const s=setup({2:{editing:true,conflict:false,editor:'Alice'},3:{editing:false,conflict:false}}),before=s.statuses.value
  for(let i=0;i<3;i++)await s.c.syncEditingSession()
  assert.equal(s.statuses.value,before)
  s.c.projectApi.getAllPageEditingStatuses=async()=>({3:{editing:false,conflict:false},2:{editing:true,conflict:false,editor:'Alice'}})
  await s.c.syncEditingSession();assert.equal(s.statuses.value,before)
})

test('actual lock, editor and page status changes are still published immediately',async()=>{
  const s=setup()
  for(const next of [{2:{editing:true,conflict:false,editor:'Alice'}},{2:{editing:true,conflict:true,editor:'Alice'}},
    {2:{editing:true,conflict:true,editor:'Bob'}},{2:{editing:false,conflict:false}},{}]){
    const before=s.statuses.value
    s.c.projectApi.getAllPageEditingStatuses=async()=>next
    await s.c.syncEditingSession()
    assert.notEqual(s.statuses.value,before)
    assert.deepEqual(JSON.parse(JSON.stringify(s.statuses.value)),next)
  }
})

test('skipping an unchanged UI update still renews and releases the current editing lock',async()=>{
  const s=setup(),before=s.statuses.value
  s.c.activeEditingPageId.value=2
  await s.c.syncEditingSession();await s.c.syncEditingSession()
  assert.equal(s.statuses.value,before)
  assert.deepEqual(s.locks.map(l=>l.active),[true,true])
  s.c.activeEditingPageId.value=null;await s.c.syncEditingSession()
  assert.deepEqual(s.locks.map(l=>l.active),[true,true,false])
  assert.equal(s.c.lastLockedPageId,null)
})
