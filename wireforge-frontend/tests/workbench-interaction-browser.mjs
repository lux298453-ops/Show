import puppeteer from 'puppeteer-core'
import assert from 'node:assert/strict'

const origin=process.env.NAV_TEST_ORIGIN || 'http://127.0.0.1:5174'
const browser=await puppeteer.launch({executablePath:process.env.NAV_TEST_BROWSER || 'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true,args:['--no-sandbox','--disable-gpu']})
const html='<!doctype html><html><body style="margin:0;background:#fff;width:375px;height:300px"><div id="card" class="wf-box" style="position:absolute;left:30px;top:40px;width:150px;height:80px;background:#dbeafe">功能卡片</div><input id="name" style="position:absolute;left:30px;top:160px" /></body></html>'
const model={project:{id:900,name:'交互回归'},pages:[{id:1,name:'首页',background_image:'',canvas_width:375,canvas_height:300,canvas_x:100,canvas_y:100,analyzed:1,html_content:html,elements:[],annotations:[]}]}
const plan={previewId:'checked',projectId:900,expiresAt:Date.now()+600000,items:[],exclusions:[],warnings:[],protectedCount:0}
let previewCalls=0,saveCalls=0,releaseSave,releasePreview
const pendingSave=new Promise(resolve=>{releaseSave=resolve})
const pendingPreview=new Promise(resolve=>{releasePreview=resolve})
const errors=[]
try {
  const page=await browser.newPage();await page.setViewport({width:1600,height:1100})
  await page.exposeFunction('mockRequestCounts',()=>({saveCalls,previewCalls}))
  page.on('pageerror',e=>errors.push(String(e)))
  await page.setRequestInterception(true)
  page.on('request',async request=> {
    const url=new URL(request.url())
    if(url.pathname.startsWith('/api/')) {
      let data={},status=200,message=''
      if(url.pathname.endsWith('/prototype'))data=model
      else if(url.pathname.endsWith('/edit-statuses'))data={}
      else if(url.pathname.endsWith('/edit-lock'))data={editing:true,conflict:false}
      else if(url.pathname.endsWith('/pages/1/html')) {saveCalls++;await pendingSave}
      else if(url.pathname.endsWith('/autowire/preview')) {
        previewCalls++
        if(previewCalls===1){await pendingPreview;status=503;message='模拟服务暂不可用，请重试'}else data=plan
      } else if(request.method()==='GET') data=[]
      return request.respond({status,contentType:'application/json',body:JSON.stringify({code:status===200?0:1,message,data})})
    }
    if(url.protocol.startsWith('http') && url.origin!==origin)return request.abort()
    return request.continue()
  })
  await page.goto(origin+'/projects/900/prototype')
  await page.waitForSelector('.page-block[data-block-key="proto-1"] iframe.html-frame')
  const handle=await page.$('.page-block[data-block-key="proto-1"] iframe.html-frame'),frame=await handle.contentFrame()
  await frame.waitForSelector('#card')
  await page.waitForFunction(()=>!document.querySelector('.canvas-content').classList.contains('is-animating'))
  const offset=()=>page.$eval('.canvas-content',n=>{const m=new DOMMatrix(getComputedStyle(n).transform);return {x:m.m41,y:m.m42}})
  const drag=async (x,y,dx,dy,button='left')=>{
    await page.mouse.move(x,y)
    await page.mouse.down({button})
    await page.waitForSelector('.canvas-content.is-dragging')
    await page.mouse.move(x+dx,y+dy,{steps:4})
    await page.mouse.up({button})
    await page.waitForFunction(()=>!document.querySelector('.canvas-content').classList.contains('is-dragging'),{timeout:3000})
  }
  for(let i=0;i<3;i++) {
    await frame.click('#card')
    assert.equal(await page.evaluate(()=>document.activeElement.tagName),'IFRAME','The shortcut originates in the prototype iframe')
    const box=await handle.boundingBox(),before=await offset()
    await page.keyboard.down('Space');await page.waitForSelector('.canvas-pan-surface')
    await drag(box.x+260,box.y+220,60,20)
    await page.keyboard.up('Space');await page.waitForFunction(()=>!document.querySelector('.canvas-pan-surface'))
    const after=await offset();assert.ok(Math.abs(after.x-before.x-60)<2 && Math.abs(after.y-before.y-20)<2,'Space pans after iframe focus and across consecutive drags')
  }
  const box=await handle.boundingBox(),beforeMiddle=await offset()
  await drag(box.x+260,box.y+220,35,15,'middle')
  const afterMiddle=await offset();assert.ok(Math.abs(afterMiddle.x-beforeMiddle.x-35)<2 && Math.abs(afterMiddle.y-beforeMiddle.y-15)<2,'Middle-button pan moves once per pointer delta and releases inside the iframe')
  await frame.focus('#name');await page.keyboard.type('a b')
  assert.equal(await frame.$eval('#name',n=>n.value),'a b','Input fields retain literal spaces')
  assert.equal(await page.$('.canvas-pan-surface'),null)
  await frame.evaluate(()=>document.querySelector('#name').blur());await page.keyboard.down('Space');await page.waitForSelector('.canvas-pan-surface')
  await page.evaluate(()=>window.dispatchEvent(new Event('blur')));await page.waitForFunction(()=>!document.querySelector('.canvas-pan-surface'))
  await page.keyboard.up('Space')
  await page.evaluate(()=>window.dispatchEvent(new KeyboardEvent('keydown',{key:'c',code:'KeyC',bubbles:true})))
  await page.waitForSelector('.draw-placement-overlay')
  const viewport=await page.$('.canvas-viewport'),vr=await viewport.boundingBox(),beforeComment=await offset()
  await page.mouse.move(vr.x+20,vr.y+20);await page.keyboard.down('Space')
  await page.waitForSelector('.canvas-pan-surface')
  await drag(vr.x+20,vr.y+20,30,10);await page.keyboard.up('Space')
  const afterComment=await offset();assert.ok(afterComment.x-beforeComment.x>25,'Temporary pan takes priority over the comment tool')
  assert.equal(await page.$('.draft-comment-container'),null,'Panning does not create a comment')
  await page.evaluate(()=>window.dispatchEvent(new KeyboardEvent('keydown',{key:'v',code:'KeyV',bubbles:true})))
  // Edit the real iframe, hold its mock save open, and click AI exactly once.
  await frame.click('#card');await page.keyboard.press('ArrowRight')
  const buttons=await page.$$('button');for(const button of buttons) {if(await button.evaluate(n=>n.textContent.trim()==='交互')){await button.click();break}}
  const ai=await page.waitForSelector('button[title="检查已有关系，审核后补全目标或应用有业务依据的连线"]');assert.ok(ai)
  await ai.click();await page.waitForSelector('.review-progress')
  await page.waitForFunction(()=>document.querySelector('.review-progress')?.textContent.includes('保存'))
  await page.waitForFunction(async()=>(await window.mockRequestCounts()).saveCalls>0)
  assert.equal(previewCalls,0,'One click waits for edits instead of aborting or starting preview against stale data')
  assert.equal(saveCalls,1,'Pending iframe export is flushed once')
  releaseSave()
  await page.waitForFunction(async()=>(await window.mockRequestCounts()).previewCalls>0)
  await page.waitForFunction(()=>document.querySelector('.review-progress')?.textContent.includes('已有跳转'))
  assert.equal(previewCalls,1,'Preview automatically starts when the save completes')
  assert.equal(await page.$$eval('.autowire-dialog .actions .el-button--primary',nodes=>nodes.every(node=>node.disabled)),true,'Checking cannot apply incomplete results')
  releasePreview()
  await page.waitForSelector('.autowire-dialog [role=alert]')
  assert.match(await page.$eval('.autowire-dialog [role=alert]',n=>n.textContent),/模拟服务暂不可用/)
  await new Promise(resolve=>setTimeout(resolve,3500))
  assert.ok(await page.$('.autowire-dialog [role=alert]'),'Failure stays visible after a toast would have disappeared')
  await page.$$eval('.autowire-dialog .actions button',nodes=>nodes.find(n=>n.textContent.includes('重试检查')).click())
  await page.waitForFunction(()=>document.querySelector('.review-summary')?.textContent.includes('0 条建议'))
  assert.equal(previewCalls,2);assert.equal(await page.$('.autowire-dialog [role=alert]'),null)
  assert.equal(saveCalls,1,'Checking again never exports unchanged frames')
  assert.deepEqual(errors,[])
  console.log('PASS: real workbench iframe Space and middle pan; repeated drags; typing and blur; pan priority; one-click save waiting; persistent preview errors and retry; mocked APIs only.')
} finally {releaseSave();releasePreview();await browser.close()}
