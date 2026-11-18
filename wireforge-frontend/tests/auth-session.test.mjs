import { test, after } from 'node:test'
import assert from 'node:assert/strict'
import { build } from 'esbuild'
import { mkdtemp, writeFile, unlink, rmdir } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'
import { createRequire } from 'node:module'

const directory = await mkdtemp(join(tmpdir(), 'wireforge-auth-session-test-'))
const result = await build({
  stdin: { contents: "export {default as http} from './src/api/http'; export * from './src/services/auth';", resolveDir: resolve('.'), loader: 'ts' },
  bundle: true, platform: 'node', format: 'cjs', write: false,
  define: { 'import.meta.env.VITE_API_BASE_URL': '""' },
})
const originalStorage = Object.getOwnPropertyDescriptor(globalThis, 'localStorage')
const outputs = []
const require = createRequire(import.meta.url)
const tick = () => new Promise(resolve => setImmediate(resolve))
const user = (id = 'user-a') => ({ id, name: id, avatar: '' })
const me = (id = 'user-a', role = 'admin') => ({ authenticated: true, ...user(id), systemAdmin: true, projectRole: role })
const response = (config, data) => ({ status: 200, statusText: 'OK', headers: {}, config, data: { code: 0, data } })

async function fixture() {
  const storage = new Map([['wf_token', 'token-a'], ['wf_current_user', JSON.stringify(user())]])
  Object.defineProperty(globalThis, 'localStorage', { configurable: true, value: {
    getItem: key => storage.get(key) ?? null,
    setItem: (key, value) => storage.set(key, String(value)), removeItem: key => storage.delete(key),
  } })
  const output = join(directory, `auth-${outputs.length}.cjs`)
  outputs.push(output); await writeFile(output, result.outputFiles[0].contents)
  const auth = require(output)
  auth.http.defaults.adapter = config => Promise.resolve(response(config, me()))
  return { auth, storage }
}

after(async () => {
  if (originalStorage) Object.defineProperty(globalThis, 'localStorage', originalStorage)
  else delete globalThis.localStorage
  for (const output of outputs) await unlink(output)
  await rmdir(directory)
})

test('late identity and role responses cannot restore user or permissions after logout', async () => {
  const { auth, storage } = await fixture()
  await auth.loadProjectRole(1)
  assert.equal(auth.isEditor.value, true)
  assert.equal(auth.isSystemAdmin.value, true)
  const pending = []
  let logoutHeader
  auth.http.defaults.adapter = config => {
    if (config.url === '/auth/logout') {
      logoutHeader = config.headers.get('Authorization')
      return Promise.resolve(response(config, null))
    }
    return new Promise(resolve => pending.push(() => resolve(response(config, me()))))
  }
  const refresh = auth.refreshSession()
  const role = auth.loadProjectRole(1)
  await tick(); assert.equal(pending.length, 2)
  await auth.logout()
  assert.equal(logoutHeader, 'Bearer token-a')
  assert.equal(auth.currentUser.value, null)
  assert.equal(auth.projectRole.value, null)
  assert.equal(storage.has('wf_token'), false)
  pending.forEach(complete => complete()); await Promise.all([refresh, role])
  assert.equal(auth.isAuthenticated.value, false)
  assert.equal(auth.isSystemAdmin.value, false)
  assert.equal(auth.isEditor.value, false)
  assert.equal(auth.projectRole.value, null)
  assert.equal(storage.has('wf_current_user'), false)
})

test('a slow logout revokes the captured token without clearing a newer login', async () => {
  const { auth, storage } = await fixture()
  let finishLogout, finishOldMe, logoutHeader
  auth.http.defaults.adapter = config => {
    if (config.url === '/auth/logout') {
      logoutHeader = config.headers.get('Authorization')
      return new Promise(resolve => { finishLogout = () => resolve(response(config, null)) })
    }
    if (config.headers.get('Authorization') === 'Bearer token-a') {
      return new Promise(resolve => { finishOldMe = () => resolve(response(config, { authenticated: false })) })
    }
    return Promise.resolve(response(config, me('user-b', 'editor')))
  }
  const oldRefresh = auth.refreshSession(); await tick()
  const signingOut = auth.logout()
  assert.equal(auth.isAuthenticated.value, false)
  await auth.completeLogin('token-b', user('user-b'))
  assert.equal(logoutHeader, 'Bearer token-a')
  finishOldMe(); finishLogout(); await Promise.all([oldRefresh, signingOut])
  assert.equal(auth.currentUser.value.id, 'user-b')
  assert.equal(storage.get('wf_token'), 'token-b')
})

test('changing a token in another window fences responses before storage sync finishes', async () => {
  const { auth, storage } = await fixture()
  let finishOldRole
  auth.http.defaults.adapter = config => config.headers.get('Authorization') === 'Bearer token-a'
    ? new Promise(resolve => { finishOldRole = () => resolve(response(config, me())) })
    : Promise.resolve(response(config, me('user-b', 'viewer')))
  const oldRole = auth.loadProjectRole(1); await tick()
  storage.set('wf_token', 'token-b'); storage.set('wf_current_user', JSON.stringify(user('user-b')))
  finishOldRole(); await oldRole
  assert.equal(auth.isSystemAdmin.value, false)
  assert.equal(auth.isEditor.value, false)
  await auth.syncLoginFromStorage(); await auth.loadProjectRole(1)
  assert.equal(auth.currentUser.value.id, 'user-b')
  assert.equal(auth.projectRole.value, 'viewer')
})

test('renewing the same user revalidates project permissions instead of keeping old admin status', async () => {
  const { auth } = await fixture()
  await auth.loadProjectRole(1)
  auth.http.defaults.adapter = config => Promise.resolve(response(config, { ...me('user-a', 'viewer'), systemAdmin: false }))
  await auth.completeLogin('renewed-token', user())
  assert.equal(auth.isAuthenticated.value, true)
  assert.equal(auth.isEditor.value, false)
  assert.equal(auth.isSystemAdmin.value, false)
  assert.equal(auth.projectRole.value, 'viewer')
  assert.equal(auth.projectRoleLoading.value, false)
})

test('an in-flight login verification cannot recreate the user after logout', async () => {
  const { auth } = await fixture()
  let finishMe
  auth.http.defaults.adapter = config => config.url === '/auth/logout'
    ? Promise.resolve(response(config, null))
    : new Promise(resolve => { finishMe = () => resolve(response(config, me())) })
  const login = auth.completeLogin('new-token', user()); await tick()
  await auth.logout(); finishMe()
  assert.equal(await login, null)
  assert.equal(auth.isAuthenticated.value, false)
})

test('renewing while project permissions are pending refreshes the same project', async () => {
  const { auth } = await fixture()
  let finishOldRole
  auth.http.defaults.adapter = config => config.headers.get('Authorization') === 'Bearer token-a'
    ? new Promise(resolve => { finishOldRole = () => resolve(response(config, me())) })
    : Promise.resolve(response(config, me('user-a', 'editor')))
  const oldRole = auth.loadProjectRole(1); await tick()
  await auth.completeLogin('renewed-token', user())
  finishOldRole(); await oldRole
  assert.equal(auth.projectRole.value, 'editor')
  assert.equal(auth.projectRoleLoading.value, false)
})

test('login verification cannot restore permissions for a project already left', async () => {
  const { auth } = await fixture()
  await auth.loadProjectRole(1)
  let finishMe
  const projects = []
  auth.http.defaults.adapter = config => {
    if (config.params?.projectId) {
      projects.push(config.params.projectId)
      return Promise.resolve(response(config, me('user-a', config.params.projectId === 1 ? 'admin' : 'viewer')))
    }
    return new Promise(resolve => { finishMe = () => resolve(response(config, me())) })
  }
  const login = auth.completeLogin('renewed-token', user()); await tick()
  await auth.loadProjectRole(2)
  finishMe(); await login
  assert.deepEqual(projects, [2])
  assert.equal(auth.projectRole.value, 'viewer')
  const nextLogin = auth.completeLogin('another-token', user()); await tick()
  auth.clearProjectRole(); finishMe(); await nextLogin
  assert.deepEqual(projects, [2])
  assert.equal(auth.projectRole.value, null)
})

test('failed server logout leaves local identity and project permissions cleared', async () => {
  const { auth, storage } = await fixture()
  await auth.loadProjectRole(1)
  auth.http.defaults.adapter = config => Promise.reject(Object.assign(new Error('Network Error'), { config }))
  await auth.logout()
  assert.equal(storage.has('wf_token'), false)
  assert.equal(auth.isAuthenticated.value, false)
  assert.equal(auth.isSystemAdmin.value, false)
  assert.equal(auth.isEditor.value, false)
})

test('anonymous viewing resolves as viewer without leaving permission loading stuck', async () => {
  const { auth, storage } = await fixture()
  storage.clear()
  auth.http.defaults.adapter = () => { throw new Error('Guest permission lookup should not need the network') }
  assert.equal(await auth.loadProjectRole(1), 'viewer')
  assert.equal(auth.projectRole.value, 'viewer')
  assert.equal(auth.projectRoleLoading.value, false)
  assert.equal(auth.isAuthenticated.value, false)
})

test('an expired current session clears identity and resolves the project as read-only', async () => {
  const { auth, storage } = await fixture()
  auth.http.defaults.adapter = config => Promise.resolve(response(config, { authenticated: false }))
  assert.equal(await auth.loadProjectRole(1), 'viewer')
  assert.equal(auth.projectRole.value, 'viewer')
  assert.equal(auth.projectRoleLoading.value, false)
  assert.equal(auth.isAuthenticated.value, false)
  assert.equal(storage.has('wf_token'), false)
})

test('a callback finishing after logout does not replace the logged-out session', async () => {
  const { auth } = await fixture()
  let finishCallback
  auth.http.defaults.adapter = config => config.url === '/auth/feishu/callback'
    ? new Promise(resolve => { finishCallback = () => resolve(response(config, { token: 'callback-token', user: user() })) })
    : Promise.resolve(response(config, null))
  const callback = assert.rejects(auth.loginWithFeishu('review-code'), /登录状态已变化/)
  await tick(); await auth.logout(); finishCallback(); await callback
  assert.equal(auth.isAuthenticated.value, false)
})
