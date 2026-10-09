import assert from 'node:assert/strict'
import puppeteer from 'puppeteer-core'

const origin=process.env.PREVIEW_TEST_ORIGIN || 'http://127.0.0.1:5174'
const browser=await puppeteer.launch({executablePath:process.env.PREVIEW_TEST_BROWSER || 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',headless:true,args:['--no-sandbox','--disable-gpu']})
try {
  const page=await browser.newPage()
  await page.setViewport({width:900,height:1100})
  const unexpected=[]
  const errors=[]
  page.on('pageerror',error=>errors.push(error.message))
  await page.setRequestInterception(true)
  page.on('request',request=>{
    const url=request.url()
    if(url.includes('/__should_not_')||/^\/api\//.test(new URL(url).pathname)){unexpected.push(url);void request.abort();return}
    if(url.endsWith('/uploads/test.png')){void request.respond({contentType:'image/png',body:Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScLbtAAAAABJRU5ErkJggg==','base64')});return}
    if(/^https?:/.test(url)&&!url.startsWith(origin)){void request.abort();return}
    void request.continue()
  })
  await page.goto(`${origin}/tests/autowire-preview-harness.html`)
  await page.waitForSelector('#source .preview-click-area')
  await page.waitForFunction(()=>[...document.querySelectorAll('.preview-caption')].every(node=>!node.textContent.includes('正在加载')))
  const source=await page.$eval('#source',node=>({area:node.querySelector('.preview-click-area').getAttribute('style'),scroll:node.querySelector('.preview-viewport').scrollTop,scrollHeight:node.querySelector('.preview-viewport').scrollHeight,viewportHeight:node.querySelector('.preview-viewport').clientHeight,caption:node.querySelector('.preview-caption').textContent,sandbox:node.querySelector('iframe').getAttribute('sandbox')}))
  assert.match(source.area,/left: 30px/)
  assert.match(source.area,/top: 1100px/)
  assert.equal(source.sandbox,'allow-same-origin')
  assert.ok(source.scroll>500,'long source page automatically scrolls to the actual source control')
  assert.ok(source.scrollHeight>source.viewportHeight*2,'long page stays readable instead of shrinking its whole height')
  assert.match(source.caption,/可滚动查看/)
  assert.equal(await page.$('#target .preview-click-area'),null)
  assert.equal(await page.$('#missing .preview-click-area'),null)
  assert.match(await page.$eval('#missing .preview-caption',node=>node.textContent),/无法定位该控件/)
  assert.match(await page.$eval('#empty',node=>node.textContent),/这个页面还没有预览/)
  assert.match(await page.$eval('#uid .preview-click-area',node=>node.getAttribute('style')),/left: 55px; top: 90px/)
  assert.match(await page.$eval('#navigation .preview-click-area',node=>node.getAttribute('style')),/left: 250px; top: 1524px/)
  await page.waitForSelector('#image .preview-click-area')
  assert.equal(await page.$eval('#image img',node=>new URL(node.src).pathname),'/uploads/test.png')
  const frameHandle=await page.$('#source iframe')
  const frame=await frameHandle.contentFrame()
  const inert=await frame.evaluate(()=>({scripts:document.querySelectorAll('script').length,iframes:document.querySelectorAll('iframe').length,href:document.querySelector('a').getAttribute('href'),action:document.querySelector('form').getAttribute('action'),handlers:[...document.querySelectorAll('*')].flatMap(node=>[...node.attributes].filter(attribute=>attribute.name.startsWith('on'))).length,inert:document.body.inert}))
  assert.deepEqual(inert,{scripts:0,iframes:0,href:null,action:null,handlers:0,inert:true})
  // Even deliberately dispatched events have no behavior or editor messages.
  await frame.evaluate(()=>{document.querySelector('.exchange').click();document.querySelector('a').click();document.querySelector('form').requestSubmit()})
  await new Promise(resolve=>setTimeout(resolve,200))
  assert.equal(await page.evaluate(()=>window.previewLeaks),0)
  assert.deepEqual(await page.evaluate(()=>window.previewMessages),[])
  assert.deepEqual(unexpected,[])
  assert.deepEqual(errors,[])
  const rewritten=await page.evaluate(()=>window.sanitizedUpload)
  assert.match(rewritten,/https:\/\/api\.example\.com\/uploads\/picture\.png/)
  assert.match(rewritten,/https:\/\/api\.example\.com\/uploads\/background\.png/)
  console.log('Readonly preview browser regression passed: real HTML, exact IDs/UIDs, navigation geometry, long-page scroll, image/empty fallback, API uploads and inert sandbox.')
} finally { await browser.close() }
