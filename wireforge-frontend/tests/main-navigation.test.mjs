import { test } from 'node:test'
import assert from 'node:assert/strict'
import vm from 'node:vm'
import { buildSync } from 'esbuild'
import { DOMParser } from 'linkedom'

const module = { exports: {} }
vm.runInNewContext(buildSync({ entryPoints: ['src/utils/mainNavigation.ts'], bundle: true, write: false, format: 'cjs', platform: 'node' }).outputFiles[0].text,
  { module, exports: module.exports, DOMParser })
const { activeMainNavigationKey, mainNavigation } = module.exports
const labels = ['家园', '养成', '商城', '精灵', '我的', '社区']
function fixtures(count) {
  return labels.slice(0, count).map((name, active) => ({ id: active + 1, name, elements: [],
    html_content: `<html><body><nav data-wf-chrome="bottom-navigation">${labels.slice(0, count).map((label, slot) =>
      `<button data-nav-item="1" data-wf-element-id="${(active + 1) * 100 + slot}" aria-current="${active === slot ? 'page' : 'false'}">${label}</button>`).join('')}</nav></body></html>` }))
}

for (const count of [1, 3, 5, 6]) test(`${count} actual navigation items produce ${count} main pages in navigation order`, () => {
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
  pages.push({ ...pages[1], id: 20, html_content: pages[1].html_content.replace('aria-current="page"', 'aria-current="page" data-nav-page-id="2"') })
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

test('independent modal types are excluded even when their names and HTML look like ordinary pages', () => {
  const pages = fixtures(5).map(page => ({ ...page, page_type: 'page' }))
  pages.push({ ...pages[0], id: 99, name: '买这个', page_type: 'modal' })
  assert.deepEqual(Array.from(mainNavigation(pages), item => item.page?.id), [1, 2, 3, 4, 5])
})

test('stable navigation IDs survive renamed targets and old data-nav text', () => {
  const pages = fixtures(3)
  pages[0].html_content = pages[0].html_content.replace('data-wf-element-id="100"', 'data-wf-element-id="100" data-nav="旧名称" data-nav-page-id="1"')
  pages[0].name = '用户指定的家园'
  pages[0].name_aliases = ['家园']
  assert.deepEqual(Array.from(mainNavigation(pages), item => item.page?.id), [1, 2, 3])
})

test('same basenames in different folder groups are not merged into an arbitrary main page', () => {
  const pages = fixtures(2).map((page, i) => ({ ...page, name: `${i ? 'B版' : 'A版'}/家园`,
    html_content: fixtures(2)[0].html_content.replace(/data-wf-element-id="\d+"/g, '') }))
  assert.equal(mainNavigation(pages)[0].page, undefined)
})

test('the marked five-item navigation takes precedence over earlier legacy markup and shorter copies', () => {
  const pages = fixtures(5)
  const old = fixtures(4)
  for (let i = 0; i < old.length; i++) pages[i].html_content = old[i].html_content
  pages[4].html_content = pages[4].html_content
    .replace('data-wf-chrome="bottom-navigation"', 'data-wf-navigation="bar"')
    .replace('<body>', '<body><nav class="bottom-nav"><button>旧数据</button><button>旧页面</button></nav>')
  const result = mainNavigation(pages)
  assert.deepEqual(Array.from(result, item => item.label), labels.slice(0, 5))
  assert.deepEqual(Array.from(result, item => item.page?.id), [1, 2, 3, 4, 5])
})

test('each uniquely identified artboard is selectable without any saved interactions', () => {
  const pages = fixtures(4)
  pages[1].name = '新养成页面'
  pages[1].source_name = 'A版/养成'
  pages[2].name = '商城中心'
  pages[2].name_aliases = ['商城']
  for (const page of pages.slice(1)) page.html_content = '<main>原型内容</main>'
  assert.deepEqual(Array.from(mainNavigation(pages), item => item.page?.id), [1, 2, 3, 4])
})

test('a legacy app map can fill a matching label but cannot fill another item by position', () => {
  const pages = fixtures(3)
  pages[1].name = '角色主页'
  pages[2].name = '购物页面'
  for (const page of pages.slice(1)) page.html_content = '<main>原型内容</main>'
  const oldMap = { tab_bar: { items: [{ label: '商城', target: 3 }, { label: '过期入口', target: 1 }, { label: '家园', target: 1 }] } }
  assert.deepEqual(Array.from(mainNavigation(pages, oldMap), item => item.page?.id), [1, undefined, 3])
})

test('a DOM UID link wins over the declared ID and conflicting links leave the slot unresolved', () => {
  const pages = fixtures(3)
  pages[0].html_content = pages[0].html_content.replace('data-wf-element-id="101"', 'data-wf-uid="tab-entry" data-nav-page-id="2"')
  pages[0].elements = [{ id: 9, interaction: { action: 'navigate', target_page_id: 3, params: '{"dom_uid":"tab-entry"}' } }]
  assert.equal(mainNavigation(pages)[1].page?.id, 3)
  pages[0].elements.push({ id: 10, interaction: { action: 'navigate', target_page_id: 2, params: '{"domUid":"tab-entry"}' } })
  assert.equal(mainNavigation(pages, { tab_bar: { items: [{ label: '养成', target: 2 }] } })[1].page, undefined)
})

test('an explicitly linked modal remains outside the main-page menu destinations', () => {
  const pages = fixtures(3)
  pages.push({ id: 80, name: '购买确认', page_type: 'modal', html_content: '<section>确认</section>', elements: [] })
  pages[0].elements = [{ id: 101, interaction: { action: 'navigate', target_page_id: 80 } }]
  assert.equal(mainNavigation(pages)[1].page, undefined)
})

test('the current main page and its detail state highlight just the verified navigation item', () => {
  const pages = fixtures(3)
  pages[2].name = '精选商店'
  pages[2].name_aliases = ['商城']
  const navigation = mainNavigation(pages)
  assert.equal(activeMainNavigationKey(pages[2], navigation, pages), navigation[2].key)

  const detail = { id: 94, name: '商品详情', elements: [], html_content: `<nav data-wf-navigation="bar">
    <button data-nav-item aria-current="false" class="active">家园</button>
    <button data-nav-item aria-current="page" data-nav-page-id="3">精选商品</button>
  </nav>` }
  assert.equal(activeMainNavigationKey(detail, navigation, [...pages, detail]), navigation[2].key)

  const legacy = { ...detail, html_content: '<nav class="bottom-nav"><button>家园</button><button aria-selected="true">商城</button></nav>' }
  assert.equal(activeMainNavigationKey(legacy, navigation, [...pages, legacy]), navigation[2].key)
  assert.equal(activeMainNavigationKey({ ...detail, html_content: '<main>商品详情</main>' }, navigation, pages), undefined)
  assert.equal(activeMainNavigationKey({ ...detail, html_content: detail.html_content.replace('aria-current="false"', 'aria-current="page"') }, navigation, pages), undefined)
})
