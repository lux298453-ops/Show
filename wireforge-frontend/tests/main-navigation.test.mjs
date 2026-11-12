import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import { transformSync } from 'esbuild'
import { DOMParser } from 'linkedom'

const module = { exports: {} }
vm.runInNewContext(transformSync(readFileSync('src/utils/mainNavigation.ts', 'utf8'), { loader: 'ts', format: 'cjs' }).code,
  { module, exports: module.exports, DOMParser })
const { mainNavigation } = module.exports
const labels = ['家园', '养成', '商城', '精灵', '我的', '社区']
function fixtures(count) {
  return labels.slice(0, count).map((name, active) => ({ id: active + 1, name, elements: [],
    html_content: `<html><body><nav data-wf-chrome="bottom-navigation">${labels.slice(0, count).map((label, slot) =>
      `<button data-nav-item="1" data-wf-element-id="${(active + 1) * 100 + slot}" aria-current="${active === slot ? 'page' : 'false'}">${label}</button>`).join('')}</nav></body></html>` }))
}

for (const count of [3, 5, 6]) test(`${count} actual navigation items produce ${count} main pages in navigation order`, () => {
  const pages = fixtures(count)
  const result = mainNavigation(pages)
  assert.equal(result.length, count)
  assert.deepEqual(Array.from(result, item => item.page?.id), pages.map(page => page.id))
})

test('a stale four-item app map cannot truncate five navigation items or include unrelated dialogs', () => {
  const pages = fixtures(5)
  pages.push({ id: 90, name: '道具购买弹窗', elements: [], html_content: '<section role="dialog">确认购买</section>' })
  const result = mainNavigation(pages, JSON.stringify({ tab_bar: { items: pages.slice(0, 4).map(page => ({ target: page.id })) } }))
  assert.deepEqual(Array.from(result, item => item.page?.id), [1, 2, 3, 4, 5])
})

test('unresolved navigation destinations remain listed without a fabricated page', () => {
  const pages = fixtures(5).slice(0, 4)
  const result = mainNavigation(pages)
  assert.equal(result.length, 5)
  assert.equal(result[4].label, '我的')
  assert.equal(result[4].page, undefined)
})

test('a same-name active variant counts as one main entry and a child link does not imply self', () => {
  const pages = fixtures(5)
  pages.push({ ...pages[1], id: 20 })
  pages.push({ id: 30, name: '奖励弹窗', elements: [], html_content: pages[0].html_content.replace('aria-current="page"', 'aria-current="page" data-nav="家园"') })
  const result = mainNavigation(pages)
  assert.deepEqual(Array.from(result, item => item.page?.id), [1, 2, 3, 4, 5])
})

test('navigation labels may differ across designs while explicit targets and active pages identify the same five entries', () => {
  const pages = fixtures(5)
  pages[0].html_content = pages[0].html_content.replace('>家园<', '>互动<').replace('>养成<', '>角色养成<')
  const result = mainNavigation(pages)
  assert.deepEqual(Array.from(result, item => item.page?.id), [1, 2, 3, 4, 5])
})

test('projects without navigation do not fabricate four main pages from artboard order', () => {
  assert.equal(mainNavigation([{ id: 1, name: '弹窗', html_content: '<div>内容</div>', elements: [] }]).length, 0)
})
