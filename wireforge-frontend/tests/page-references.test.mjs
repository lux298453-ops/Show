import { test } from 'node:test'
import assert from 'node:assert/strict'
import { buildSync } from 'esbuild'
import vm from 'node:vm'

const module = { exports: {} }
vm.runInNewContext(buildSync({ entryPoints: ['src/utils/pageReferences.ts'], bundle: true, write: false, format: 'cjs', platform: 'node' }).outputFiles[0].text,
  { module, exports: module.exports })
const { resolvePageReference, pageReferenceCandidates, isOverlayPage } = module.exports

test('renamed pages retain original filenames and former-name links', () => {
  const page = { id: 20, name: '买这个', source_name: '商城/IMG_1', name_aliases: ['确认购买'] }
  for (const name of ['买这个', '确认购买', '商城/IMG_1', 'IMG_1', 'wf-page-id:20']) assert.equal(resolvePageReference(name, [page]), page)
})

test('same-name folder groups are ambiguous without an explicit group or stable ID', () => {
  const pages = [{ id: 1, name: 'A版/商城' }, { id: 2, name: 'B版/商城' }]
  assert.equal(pageReferenceCandidates('商城', pages).length, 2)
  assert.equal(resolvePageReference('商城', pages), undefined)
  assert.equal(resolvePageReference('A版/商城', pages), pages[0])
  assert.equal(resolvePageReference('C版/商城', pages), undefined)
})

test('variant suffixes and numeric filenames are retained', () => {
  const pages = [{ id: 20, name: '背包' }, { id: 21, name: '背包-2' }, { id: 22, name: '20' }]
  assert.equal(resolvePageReference('背包-2', pages), pages[1])
  assert.equal(resolvePageReference('20', pages), pages[2])
  assert.equal(resolvePageReference('wf-page-id:20', pages), pages[0])
  assert.equal(resolvePageReference('背包_2', pages), undefined)
})

test('a canonical name conflicting with another historical alias remains ambiguous', () => {
  assert.equal(resolvePageReference('商城', [{ id: 1, name: '商店', name_aliases: ['商城'] }, { id: 2, name: '商城' }]), undefined)
})

test('page type wins regardless of words in its display name', () => {
  assert.equal(isOverlayPage({ name: '买这个', page_type: 'modal' }), true)
  assert.equal(isOverlayPage({ name: '背包', page_type: 'drawer' }), true)
  assert.equal(isOverlayPage({ name: '弹窗制作教程', page_type: 'page' }), false)
})
