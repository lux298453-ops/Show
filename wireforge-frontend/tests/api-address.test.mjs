import { test, after } from 'node:test'
import assert from 'node:assert/strict'
import { build } from 'esbuild'
import { mkdtemp, writeFile, unlink, rmdir } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'
import { createRequire } from 'node:module'

const directory = await mkdtemp(join(tmpdir(), 'wireforge-api-address-test-'))
const outputs = []
const require = createRequire(import.meta.url)
const originalWindow = Object.getOwnPropertyDescriptor(globalThis, 'window')

after(async () => {
  if (originalWindow) Object.defineProperty(globalThis, 'window', originalWindow)
  else delete globalThis.window
  for (const output of outputs) await unlink(output)
  await rmdir(directory)
})

async function loadHttp(hostname, configured = '') {
  Object.defineProperty(globalThis, 'window', { configurable: true, value: { location: { hostname } } })
  const result = await build({
    stdin: { contents: "export {default as http, apiUrl, getFileUrl, withBackendFiles, withoutBackendFiles} from './src/api/http';", resolveDir: resolve('.'), loader: 'ts' },
    bundle: true, platform: 'node', format: 'cjs', write: false,
    define: { 'import.meta.env.VITE_API_BASE_URL': JSON.stringify(configured) },
  })
  const output = join(directory, `http-${outputs.length}.cjs`)
  outputs.push(output); await writeFile(output, result.outputFiles[0].contents)
  return require(output)
}

test('localhost, LAN, IPv6 and custom hosts use same-origin APIs when no backend is configured', async () => {
  for (const host of ['localhost', '127.0.0.1', '192.168.1.20', '[::1]', 'wireforge.internal']) {
    const api = await loadHttp(host)
    assert.equal(api.http.defaults.baseURL, '/api', host)
    assert.equal(api.apiUrl('/projects'), '/api/projects')
    assert.equal(api.getFileUrl('/files/design.png'), '/files/design.png')
    assert.equal(api.withBackendFiles('<img src="/api/assets/pet.png">'), '<img src="/api/assets/pet.png">')
  }
})

test('explicit cloud configuration still directs APIs and assets to that backend', async () => {
  const api = await loadHttp('prototype.example', ' https://backend.example/api/ ')
  assert.equal(api.http.defaults.baseURL, 'https://backend.example/api')
  assert.equal(api.apiUrl('auth/me'), 'https://backend.example/api/auth/me')
  assert.equal(api.getFileUrl('/files/design.png'), 'https://backend.example/files/design.png')
  const source = '<img src="/api/assets/artwork/1/abc.png"><img src="/files/design.png">'
  const preview = api.withBackendFiles(source)
  assert.match(preview, /https:\/\/backend\.example\/api\/assets\/artwork/)
  assert.equal(api.withoutBackendFiles(preview), source)
})

test('an explicit /api value uses the bundled same-origin proxy', async () => {
  const api = await loadHttp('192.168.1.20', '/api')
  assert.equal(api.http.defaults.baseURL, '/api')
  assert.equal(api.getFileUrl('/files/design.png'), '/files/design.png')
})
