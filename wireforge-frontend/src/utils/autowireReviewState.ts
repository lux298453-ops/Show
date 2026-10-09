import type { AutowireItem, AutowirePlan } from '../types/autowire'

export type ExclusionScope = '' | 'relation' | 'element'
export interface RememberedDecision { fingerprint: string; selected: boolean; excluded: ExclusionScope; target?: number }
export const reviewKey = (item: AutowireItem) => item.stableKey || `${item.pageId}:${item.elementId}:${item.interactionId ?? 'new'}`
export const reviewTargets = (item: AutowireItem) => item.navigation?.candidateTargets || item.targetSelection?.candidateTargets || []
export const reviewTargetBasis = (item: AutowireItem) => item.navigation?.basis || item.targetSelection?.basis
export const reviewFingerprint = (item: AutowireItem) => item.decisionFingerprint || JSON.stringify([item.category, item.trigger, item.action, item.targetPageId, item.reason, item.evidenceRefs])

export function rememberReview(plan: AutowirePlan | null, selected: string[], excluded: Record<string, ExclusionScope>, targets: Record<string, number | undefined>) {
  return new Map((plan?.items || []).map(item => [reviewKey(item), { fingerprint: reviewFingerprint(item), selected: selected.includes(item.id), excluded: excluded[item.id] || '', target: targets[reviewKey(item)] } as RememberedDecision]))
}

export function restoreReview(plan: AutowirePlan, memory: Map<string, RememberedDecision>) {
  const selected: string[] = []
  const excluded: Record<string, ExclusionScope> = {}
  const targets: Record<string, number | undefined> = {}
  for (const item of plan.items) {
    const old = memory.get(reviewKey(item))
    const unchanged = old?.fingerprint === reviewFingerprint(item)
    excluded[item.id] = unchanged ? old!.excluded : ''
    if (item.applicable && !excluded[item.id] && (unchanged ? old!.selected : old ? false : item.selectedByDefault)) selected.push(item.id)
    if (old?.target != null && reviewTargets(item).some(t => t.id === old.target)) targets[reviewKey(item)] = old.target
  }
  return { selected, excluded, targets }
}
