import test from 'node:test'
import assert from 'node:assert/strict'
import { rememberReview, restoreReview, reviewKey } from '../src/utils/autowireReviewState.ts'
import type { AutowirePlan, AutowireItem } from '../src/types/autowire.ts'

const item = (patch: Partial<AutowireItem> = {}): AutowireItem => ({ id: 'a', category: 'add', pageId: 1, pageName: '首页', elementId: 10, elementLabel: '我的', interactionId: null, trigger: 'click', action: 'navigate', targetPageId: 2, targetPageName: '我的', source: 'autowire_review', reason: '唯一匹配', evidenceRefs: [], applicable: true, selectedByDefault: true, stableKey: '1:nav:family:item:new', decisionFingerprint: 'target-2', ...patch })
const plan = (...items: AutowireItem[]): AutowirePlan => ({ previewId: 'preview', projectId: 1, expiresAt: Date.now() + 10000, items, exclusions: [], warnings: [], protectedCount: 0 })

test('recomputed proposals preserve both checked and unchecked decisions', () => {
  const a = item(), b = item({ id: 'b', stableKey: 'other' })
  const memory = rememberReview(plan(a, b), ['a'], {}, {})
  assert.deepEqual(restoreReview(plan({ ...a, id: 'new-a' }, { ...b, id: 'new-b' }), memory).selected, ['new-a'])
})
test('changed target or action clears check and relation exclusion', () => {
  const a = item()
  const checked = rememberReview(plan(a), ['a'], {}, {})
  const excluded = rememberReview(plan(a), [], { a: 'relation' }, {})
  const next = plan(item({ targetPageId: 3, decisionFingerprint: 'target-3' }))
  assert.deepEqual(restoreReview(next, checked).selected, [])
  assert.equal(restoreReview(next, excluded).excluded.a, '')
})
test('unchanged exclusions survive and cannot become selected', () => {
  const a = item(), memory = rememberReview(plan(a), ['a'], { a: 'element' }, {})
  const state = restoreReview(plan(item({ id: 'new' })), memory)
  assert.equal(state.excluded.new, 'element'); assert.deepEqual(state.selected, [])
})
test('multiple relationships on one element have distinct fallback keys', () => {
  assert.notEqual(reviewKey(item({ stableKey: undefined, interactionId: 100 })), reviewKey(item({ stableKey: undefined, interactionId: 101 })))
})
test('manual target memory is dropped when target is no longer selectable', () => {
  const a = item(), memory = rememberReview(plan(a), [], {}, { [reviewKey(a)]: 3 })
  assert.deepEqual(restoreReview(plan(a), memory).targets, {})
})
