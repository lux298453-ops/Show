import puppeteer from 'puppeteer-core'
import assert from 'node:assert/strict'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const origin = process.env.NAV_TEST_ORIGIN || 'http://127.0.0.1:5174'
const screenshotPath = process.env.AUTOWIRE_SCREENSHOT || join(tmpdir(), 'wireforge-autowire-compare.png')
const browser = await puppeteer.launch({ executablePath: process.env.NAV_TEST_BROWSER || 'C:/Program Files/Google/Chrome/Application/chrome.exe', headless: true, args: ['--no-sandbox', '--disable-gpu'] })
const screen = content => `<!doctype html><html><head><style>body{margin:0;width:375px;height:812px;background:#f4f7fb;font:16px sans-serif;color:#183044}header{padding:28px 20px;background:white;font-size:22px;font-weight:700}.hero{margin:20px;border-radius:18px;padding:42px 20px;text-align:center;background:#dff4e9;font-size:70px}.tile{margin:14px 20px;background:white;border-radius:12px;padding:18px}.nav{position:absolute;top:752px;width:375px;height:60px;background:white;display:grid;grid-template-columns:repeat(4,1fr)}.nav>div{display:flex;align-items:center;justify-content:center;font-size:14px}.button{position:absolute;left:20px;top:190px;width:148px;height:44px;background:#dbeafe;border-radius:10px;display:flex;align-items:center;justify-content:center}</style></head><body>${content}</body></html>`
const sourceHtml = screen('<header>宠物家园首页</header><div class="hero">🐶</div><div class="button" data-wf-element-id="50">月卡</div><div class="tile">今日任务</div><div class="tile">照顾宠物，获得奖励</div><div class="nav"><div>首页</div><div data-wf-element-id="20">收集</div><div>商店</div><div data-wf-element-id="40" data-wf-nav-owner="40">我的</div></div>')
const pageModel = (id, name, html = screen(`<header>${name}</header><div class="hero">🐱</div><div class="tile">登录 / 注册</div><div class="tile">我的背包</div><div class="tile">任务记录</div><div class="tile">设置</div>`)) => ({ id, name, background_image: '', canvas_width: 375, canvas_height: 812, canvas_x: (id - 10) * 450, canvas_y: 100, analyzed: 1, html_content: html, elements: [], annotations: [] })
const home = pageModel(10, '宠物家园首页', sourceHtml)
home.elements = [
  { id: 40, type: 'button', label: '我的', x: 281.25, y: 752, width: 93.75, height: 60, interaction: { id: 401, trigger: 'click', action: 'navigate', target_page_id: 13, source: 'autowire' } },
  { id: 20, type: 'button', label: '收集', x: 93.75, y: 752, width: 93.75, height: 60 },
  { id: 50, type: 'button', label: '月卡', x: 20, y: 190, width: 148, height: 44 },
]
const model = { project: { id: 901, name: '页面对照审核回归' }, pages: [home, pageModel(11, '收集·次态'), pageModel(12, '我的（未登录）'), pageModel(13, '个人设置'), pageModel(15, '月卡中心')] }
const item = (id, patch = {}) => ({ id, category: 'uncertain', pageId: 10, pageName: home.name, elementId: 40, elementLabel: '我的', interactionId: 401, trigger: 'click', action: 'navigate', targetPageId: 12, targetPageName: '我的（未登录）', source: 'ai_inferred', reason: '请核对目标页面', evidenceRefs: ['navigation:40'], applicable: false, selectedByDefault: false, stableKey: id, decisionFingerprint: id, navigation: { familyKey: 'bottom', itemKey: 'mine', label: '我的', region: 'bottom', memberElementIds: [40], x: 281.25, y: 752, width: 93.75, height: 60, status: 'unresolved', basis: 'ai_suggestion', previousAction: 'navigate', previousTargetPageId: 13, candidateTargets: [{ id: 12, name: '我的（未登录）' }, { id: 13, name: '个人设置' }] }, ...patch })
const plan = { previewId: 'compare-preview', projectId: 901, expiresAt: Date.now() + 600000, warnings: [], exclusions: [], protectedCount: 1, items: [
  item('nav-my'),
  item('nav-collect', { category: 'add', elementId: 20, elementLabel: '收集', interactionId: null, targetPageId: 11, targetPageName: '收集·次态', applicable: true, source: 'autowire_review', navigation: { familyKey: 'bottom', itemKey: 'collect', label: '收集', region: 'bottom', memberElementIds: [20], x: 93.75, y: 752, width: 93.75, height: 60, status: 'resolved', basis: 'exact_name', previousAction: null, previousTargetPageId: null, candidateTargets: [{ id: 11, name: '收集·次态' }] } }),
  item('month', { elementId: 50, elementLabel: '月卡', interactionId: null, targetPageId: null, targetPageName: '', navigation: null, targetSelection: { basis: 'suggestion', candidateTargets: [{ id: 15, name: '月卡中心' }] } }),
  item('conflict', { elementLabel: '商店', targetPageId: null, navigation: { ...item('unused').navigation, status: 'conflict', basis: 'conflict' }, reason: '现有跳转有冲突，请在画布交互设置处理' }),
] }
let previewCalls = 0, saveCalls = 0
const applies = [], renderRetries = [], errors = []
const result = { applicationId: 'compare-application', added: 0, completed: 1, removed: 0, excluded: 0, renderStatus: 'pending', renderError: null }
try {
  const page = await browser.newPage()
  await page.setViewport({ width: 1600, height: 1100 })
  page.on('pageerror', error => errors.push(String(error)))
  await page.setRequestInterception(true)
  page.on('request', request => {
    const url = new URL(request.url())
    if (url.pathname.startsWith('/api/')) {
      let data = {}, status = 200, message = ''
      if (url.pathname.endsWith('/prototype')) data = model
      else if (url.pathname.endsWith('/edit-statuses')) data = {}
      else if (url.pathname.endsWith('/edit-lock')) data = { editing: true, conflict: false }
      else if (url.pathname.endsWith('/html')) saveCalls++
      else if (url.pathname.endsWith('/autowire/preview')) { previewCalls++; data = plan }
      else if (url.pathname.endsWith('/autowire/apply')) {
        applies.push(JSON.parse(request.postData()))
        if (applies.length < 3) { status = 503; message = '模拟保存响应失败，请重试' } else data = result
      } else if (url.pathname.endsWith('/retry-render')) { renderRetries.push(url.pathname); data = { ...result, renderStatus: 'done' } }
      else if (url.pathname.includes('/autowire/applications/')) data = { ...result, renderStatus: 'failed', renderError: '模拟预览失败' }
      else if (request.method() === 'GET') data = []
      return request.respond({ status, contentType: 'application/json', body: JSON.stringify({ code: status === 200 ? 0 : 1, message, data }) })
    }
    if (url.protocol.startsWith('http') && url.origin !== origin) return request.abort()
    return request.continue()
  })
  await page.goto(origin + '/projects/901/prototype')
  await page.waitForSelector('.page-block iframe.html-frame')
  await page.$$eval('button', nodes => nodes.find(node => node.textContent.trim() === '交互').click())
  await (await page.waitForSelector('button[title="检查已有关系，审核后补全目标或应用有业务依据的连线"]')).click()
  await page.waitForSelector('.relation-row[data-item-id="nav-my"]')
  const active = id => page.waitForSelector(`.relation-detail[data-active-item-id="${id}"]`)
  const row = id => `.relation-row[data-item-id="${id}"]`
  const target = 'select[aria-label="跳转目标 宠物家园首页 · 我的"]'
  const primary = '.autowire-dialog .actions .el-button--primary'
  await page.click(row('nav-my')); await active('nav-my')
  assert.equal(await page.$eval(target, node => node.value), '12', 'Suggested destinations have a real value, not an empty option masquerading as a selection')
  assert.match(await page.$eval('.relation-sentence', node => node.textContent), /宠物家园首页.*我的.*我的（未登录）/)
  assert.match(await page.$eval('.previous-relation', node => node.textContent), /个人设置/)
  assert.equal(await page.$eval(`${row('nav-my')} input`, node => node.checked), false, 'Inspecting a relationship does not apply it')
  await page.waitForSelector('[data-preview-source="true"] .preview-click-area')
  assert.equal(await page.$eval('[data-preview-source="true"] iframe', node => node.sandbox.contains('allow-scripts')), false, 'Preview cannot execute project scripts')
  assert.equal(await page.$eval('[data-preview-source="true"] .preview-viewport', node => node.scrollHeight <= node.clientHeight + 1), true, 'Single screens initially show their complete page composition')
  await page.click('[data-preview-source="true"] .preview-zoom')
  await page.waitForFunction(() => document.querySelector('[data-preview-source="true"] .preview-viewport').scrollTop > 0)
  assert.match(await page.$eval('[data-preview-source="true"] .preview-zoom', node => node.textContent), /完整页面/)
  await page.click('[data-preview-source="true"] .preview-zoom')
  await page.waitForFunction(() => document.querySelector('[data-preview-source="true"] .preview-viewport').scrollHeight <= document.querySelector('[data-preview-source="true"] .preview-viewport').clientHeight + 1)
  const sourceFrame = await (await page.$('[data-preview-source="true"] iframe')).contentFrame()
  const originalNavigations = await page.evaluate(() => window.location.href)
  const clickArea = await (await page.$('[data-preview-source="true"] .preview-click-area')).boundingBox()
  await page.mouse.click(clickArea.x + clickArea.width / 2, clickArea.y + clickArea.height / 2)
  await sourceFrame.evaluate(() => document.querySelector('[data-wf-element-id="40"]').click())
  assert.equal(await page.evaluate(() => window.location.href), originalNavigations, 'Inspecting the source never runs its navigation')
  await page.click(row('month')); await active('month')
  await page.select('select[aria-label="跳转目标 宠物家园首页 · 月卡"]', '15')
  assert.equal(await page.$eval(`${row('month')} input`, node => node.checked), true, 'Choosing a missing target immediately selects the relation')
  assert.equal(await page.$eval(primary, node => node.disabled), false, 'No separate AI recheck blocks applying a chosen target')
  await page.click(`${row('month')} input`)
  await page.click(row('nav-my')); await active('nav-my')
  await page.click(`${row('nav-my')} input`)
  await page.waitForFunction(() => !document.querySelector('.dialog-fade-enter-active'))
  await (await page.$('.autowire-dialog')).screenshot({ path: screenshotPath })
  await page.select(target, '13')
  assert.equal(await page.$$eval('.autowire-dialog button', nodes => nodes.some(node => node.textContent.includes('检查新目标'))), false)
  await page.click(primary)
  await page.waitForSelector('.autowire-dialog [role="alert"]')
  assert.deepEqual(applies[0].selectedIds, ['nav-my'])
  assert.deepEqual(applies[0].navigationResolutions, [{ stableKey: 'nav-my', targetPageId: 13 }])
  assert.deepEqual(applies[0].targetResolutions || [], [])
  assert.equal(previewCalls, 1, 'One apply sends the selected target without another project preview')
  await page.click(primary)
  await page.waitForFunction(() => document.querySelector('.review-progress') == null)
  assert.equal(applies.length, 2)
  assert.equal(applies[1].idempotencyKey, applies[0].idempotencyKey, 'Retrying identical decisions preserves their idempotency key')
  await page.select(target, '12')
  await page.evaluate(() => document.documentElement.classList.add('dark'))
  assert.equal(await page.$eval(target, node => getComputedStyle(node).color === getComputedStyle(node).backgroundColor), false, 'Dark mode keeps target text readable')
  await page.setViewport({ width: 760, height: 950 })
  await page.waitForFunction(() => document.querySelector('.autowire-dialog').scrollWidth <= document.querySelector('.autowire-dialog').clientWidth + 2)
  await page.setViewport({ width: 1600, height: 1100 })
  await page.evaluate(() => document.documentElement.classList.remove('dark'))
  await page.click(primary)
  await page.waitForFunction(() => document.querySelector('.autowire-dialog')?.textContent.includes('已保存'))
  assert.match(await page.$eval('.selected-indicator', node => node.textContent), /已应用/)
  assert.equal(applies.length, 3)
  assert.notEqual(applies[2].idempotencyKey, applies[1].idempotencyKey, 'Changing the chosen target starts a different application')
  assert.deepEqual(applies[2].navigationResolutions, [{ stableKey: 'nav-my', targetPageId: 12 }])
  await page.$$eval('.autowire-dialog button', nodes => nodes.find(node => node.textContent.includes('刷新状态')).click())
  await page.waitForFunction(() => document.querySelector('.autowire-dialog')?.textContent.includes('模拟预览失败'))
  await page.$$eval('.autowire-dialog button', nodes => nodes.find(node => node.textContent.includes('重试预览更新')).click())
  await page.waitForFunction(() => document.querySelector('.autowire-dialog')?.textContent.includes('交互预览已更新'))
  assert.equal(renderRetries.length, 1)
  assert.equal(applies.length, 3, 'Rendering retry never re-applies the saved relationships')
  assert.equal(previewCalls, 1)
  assert.equal(saveCalls, 0, 'Read-only preview never saves prototype HTML')
  assert.deepEqual(errors, [])
  console.log('PASS: real PrototypeView comparison, precise source preview, direct target application, unchanged retry idempotency, changed-target keys, dark/narrow layout, render-only retry; mocked APIs only.')
} finally { await browser.close() }
