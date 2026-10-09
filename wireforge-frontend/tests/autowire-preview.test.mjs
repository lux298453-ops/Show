import { test, after } from 'node:test'
import assert from 'node:assert/strict'
import { build } from 'esbuild'
import { mkdtemp, writeFile, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { basename, dirname, join, resolve } from 'node:path'
import { createRequire } from 'node:module'

const directory = await mkdtemp(join(tmpdir(), 'wireforge-preview-test-'))
const output = join(directory, 'preview.cjs')
const bundle = await build({ entryPoints: [resolve('src/utils/autowirePreview.ts')], bundle: true, platform: 'node', format: 'cjs', write: false })
await writeFile(output, bundle.outputFiles[0].contents)
const { locatePreviewHighlight, previewDimensions } = createRequire(import.meta.url)(output)
after(async () => {
  assert.equal(dirname(resolve(directory)), resolve(tmpdir()))
  assert.ok(basename(directory).startsWith('wireforge-preview-test-'))
  await rm(directory, { recursive: true })
})
const element = { id: 10, type: 'button', label: '兑换', x: 20, y: 900, width: 90, height: 36 }
const page = { id: 1, name: '商店', canvas_width: 375, canvas_height: 1600, elements: [element] }

test('long page click area uses exact element identity rather than matching labels', () => {
  const second = { ...element, id: 11, x: 150, y: 1000 }
  assert.deepEqual(locatePreviewHighlight({ ...page, elements: [element, second] }, 11), { rect: { x: 150, y: 1000, width: 90, height: 36 }, basis: 'element' })
  assert.equal(locatePreviewHighlight(page, 99), null)
})
test('navigation click area includes the container rather than only its text', () => {
  const navigation = { x: 250, y: 1524, width: 125, height: 76 }
  assert.deepEqual(locatePreviewHighlight(page, 10, navigation), { rect: navigation, basis: 'navigation' })
})
test('invalid and off-page coordinates never create a false hotspot', () => {
  for (const patch of [{ x: NaN }, { y: 1700 }, { width: 0 }, { height: -1 }]) {
    assert.equal(locatePreviewHighlight({ ...page, elements: [{ ...element, ...patch }] }, 10), null)
  }
})
test('partly off-page geometry is clipped to the actual page', () => {
  assert.deepEqual(locatePreviewHighlight({ ...page, elements: [{ ...element, x: -10, y: 1580, width: 90 }] }, 10)?.rect, { x: 0, y: 1580, width: 80, height: 20 })
})
test('measured long content can locate elements below the declared viewport', () => {
  assert.deepEqual(locatePreviewHighlight({ ...page, canvas_height: 812 }, 10, null, undefined, 1600)?.rect, { x: 20, y: 900, width: 90, height: 36 })
})
test('missing dimensions have deterministic mobile defaults', () => {
  assert.deepEqual(previewDimensions(), { width: 375, height: 812 })
  assert.deepEqual(previewDimensions({ canvas_width: Infinity, canvas_height: 0 }), { width: 375, height: 812 })
  assert.deepEqual(previewDimensions({ canvas_width: 1440, canvas_height: 3000 }), { width: 1440, height: 3000 })
})
