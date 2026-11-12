import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import ts from 'typescript'
import { transformSync } from 'esbuild'

const module = { exports: {} }
vm.runInNewContext(transformSync(readFileSync('src/utils/previewEntry.ts', 'utf8'), { loader: 'ts', format: 'cjs' }).code, { module, exports: module.exports })
const { previewEntryPage } = module.exports
const detail = { id: 1, name: '背包弹窗' }, home = { id: 20, name: '家园' }, shop = { id: 10, name: '商城' }
const pages = [detail, shop, home]
const navigation = [{ key: 'home', label: '家园', page: home }, { key: 'shop', label: '商城', page: shop }]

function functions(file, names) {
  const source = readFileSync(file, 'utf8').split('<script setup lang="ts">')[1].split('</script>')[0]
  const tree = ts.createSourceFile(file, source, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS)
  return transformSync(tree.statements.filter(n => ts.isFunctionDeclaration(n) && names.includes(n.name?.text)).map(n => n.getText(tree)).join('\n'), { loader: 'ts' }).code
}
const canvas = functions('src/views/PrototypeView.vue', ['openPurePreview'])
const preview = functions('src/views/PurePreviewView.vue', ['loadData', 'resetPrototype', 'handleBack', 'selectPageFromDropdown'])

test('default presentation follows navigation order rather than artboard or ID order', () => {
  assert.equal(previewEntryPage(pages, navigation), home)
})
test('canvas entry ignores a secondary artboard but retains a selected main page', () => {
  assert.equal(previewEntryPage(pages, navigation, { requestedPageId: 1, mainOnly: true }), home)
  assert.equal(previewEntryPage(pages, navigation, { requestedPageId: 10, mainOnly: true }), shop)
})
test('explicit page links can still open secondary artboards', () => {
  assert.equal(previewEntryPage(pages, navigation, { requestedPageId: 1 }), detail)
})
test('deleted or invalid destinations fall back to the first available main page', () => {
  const missing = [{ key: 'missing', label: '未关联' }, { key: 'deleted', label: '已删除', page: { id: 999 } }, ...navigation]
  for (const requestedPageId of [999, NaN, null]) assert.equal(previewEntryPage(pages, missing, { requestedPageId }), home)
  assert.equal(previewEntryPage([], missing), undefined)
})
test('projects without linked navigation retain explicit selected artboard previews', () => {
  assert.equal(previewEntryPage(pages, [], { requestedPageId: 10, mainOnly: true }), shop)
  assert.equal(previewEntryPage(pages, []), detail)
})
for (const [focus, previous, expected] of [[1, 10, 20], [10, 1, 10], [20, 10, 20]]) test(`canvas focused on ${focus} opens ${expected} despite stale preview ${previous}`, () => {
  const routes = []
  const context = { pages: { value: pages }, proto: { value: { project: {} } }, mode: { value: 'edit' }, focusPageId: { value: focus }, previewPageId: { value: previous }, mainNavigation: () => navigation, previewEntryPage, router: { push: route => routes.push(route) }, id: 901 }
  vm.createContext(context); vm.runInContext(canvas + '\nopenPurePreview()', context)
  assert.equal(routes[0].query.entry, 'main')
  assert.equal(routes[0].query.page, String(expected))
})

function previewContext(query = {}) {
  const context = { projectApi: { prototype: async () => ({ pages, project: {} }) }, id: 901, proto: { value: null }, route: { query }, mainFlowItems: { value: navigation }, defaultPreviewPage: { value: home }, previewEntryPage, currentPageId: { value: null }, activeSegmentTab: { value: 'main' }, nextTick() {}, updatePhoneScale() {}, showToast() {}, historyStack: { value: [] }, activeModal: { value: null }, transitionDirection: { value: 'forward' }, pageDropdownOpen: { value: false }, searchQuery: { value: '' }, closeModal() { context.activeModal.value = null } }
  vm.createContext(context); vm.runInContext(preview, context)
  return context
}
for (const [query, expected, segment] of [[{}, 20, 'main'], [{ page: '1', entry: 'main' }, 20, 'main'], [{ page: '10', entry: 'main' }, 10, 'main'], [{ page: '1' }, 1, 'all'], [{ page: '999' }, 20, 'main']]) test(`preview initialization ${JSON.stringify(query)} starts on ${expected}`, async () => {
  const context = previewContext(query)
  await vm.runInContext('loadData()', context)
  assert.equal(context.currentPageId.value, expected)
  assert.equal(context.activeSegmentTab.value, segment)
})
test('reset and empty-history back return to main navigation without changing actual back history', () => {
  const context = previewContext()
  context.currentPageId.value = 1
  vm.runInContext('handleBack()', context)
  assert.equal(context.currentPageId.value, 20)
  context.historyStack.value = [1]
  vm.runInContext('handleBack()', context)
  assert.equal(context.currentPageId.value, 1)
  context.activeSegmentTab.value = 'all'; context.searchQuery.value = '背包'; context.pageDropdownOpen.value = true
  vm.runInContext('resetPrototype()', context)
  assert.equal(context.currentPageId.value, 20)
  assert.equal(context.activeSegmentTab.value, 'main')
  assert.equal(context.searchQuery.value, '')
  assert.equal(context.pageDropdownOpen.value, false)
})

test('choosing a main page uses the same in-place transition as its navigation tab',()=>{
  const calls=[],context=previewContext()
  context.navigateTo=(...args)=>calls.push(args)
  context.pageDropdownOpen.value=true;context.searchQuery.value='商城'
  vm.runInContext('selectPageFromDropdown(10)',context)
  assert.deepEqual(calls[0],[10,true])
  assert.equal(context.pageDropdownOpen.value,false)
  assert.equal(context.searchQuery.value,'')
  vm.runInContext('selectPageFromDropdown(1)',context)
  assert.deepEqual(calls[1],[1,false])
})
