export interface AutowireItem {
  id: string
  category: 'complete' | 'add' | 'remove' | 'uncertain'
  pageId: number
  pageName: string
  elementId: number
  elementLabel: string
  interactionId: number | null
  trigger: string
  action: string
  targetPageId: number | null
  targetPageName: string
  source: string
  reason: string
  evidenceRefs: string[]
  applicable: boolean
  selectedByDefault: boolean
}
export interface AutowirePlan {
  previewId: string
  projectId: number
  expiresAt: number
  items: AutowireItem[]
  exclusions: { id: number; pageName: string; elementLabel: string; scope: string; reason: string; matched: boolean }[]
  warnings: string[]
  protectedCount: number
}
export interface AutowireDecisions {
  selectedIds: string[]
  exclusions: { itemId: string; scope: 'relation' | 'element' }[]
  restoreExclusionIds: number[]
}
export interface AutowireApplyRequest extends AutowireDecisions {
  previewId: string
  idempotencyKey: string
}
export interface AutowireApplyResult {
  applicationId: string
  added: number
  completed: number
  removed: number
  excluded: number
  renderStatus: 'pending' | 'done' | 'failed'
  renderError: string | null
}
