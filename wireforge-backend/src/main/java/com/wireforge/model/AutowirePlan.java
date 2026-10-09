package com.wireforge.model;

import java.util.List;

/** Server-owned proposals: clients submit item IDs, never arbitrary relation payloads. */
public record AutowirePlan(String previewId, long projectId, long expiresAt, List<Item> items,
                           List<ExclusionView> exclusions, List<String> warnings, int protectedCount,
                           List<NavigationDiagnostic> navigationDiagnostics) {
    public AutowirePlan(String previewId, long projectId, long expiresAt, List<Item> items,
                        List<ExclusionView> exclusions, List<String> warnings, int protectedCount) {
        this(previewId, projectId, expiresAt, items, exclusions, warnings, protectedCount, List.of());
    }
    public record Item(String id, String category, long pageId, String pageName, long elementId,
                       String elementLabel, Long interactionId, String trigger, String action,
                       Long targetPageId, String targetPageName, String source, String reason,
                       List<String> evidenceRefs, boolean applicable, boolean selectedByDefault,
                       String stableKey, String decisionFingerprint, NavigationInfo navigation) {
        public Item(String id, String category, long pageId, String pageName, long elementId,
                    String elementLabel, Long interactionId, String trigger, String action,
                    Long targetPageId, String targetPageName, String source, String reason,
                    List<String> evidenceRefs, boolean applicable, boolean selectedByDefault) {
            this(id, category, pageId, pageName, elementId, elementLabel, interactionId, trigger, action,
                    targetPageId, targetPageName, source, reason, evidenceRefs, applicable, selectedByDefault,
                    pageId + ":" + elementId + ":" + (interactionId == null ? "new" : interactionId),
                    com.wireforge.service.AutowirePlanner.sha(category + "|" + trigger + "|" + action + "|" + targetPageId + "|" + reason + "|" + evidenceRefs), null);
        }
    }
    public record TargetOption(long id, String name) {}
    public record NavigationInfo(String familyKey, String itemKey, String label, String region,
                                 List<Long> memberElementIds, double x, double y, double width, double height,
                                 String status, String basis, String previousAction, Long previousTargetPageId,
                                 List<TargetOption> candidateTargets) {}
    public record NavigationDiagnostic(long pageId, String pageName, String label, String status, String reason) {}
    public record NavigationResolution(String stableKey, long targetPageId) {}
    public record PreviewRequest(String previousPreviewId, List<NavigationResolution> navigationResolutions) {}
    public record ExclusionView(long id, String pageName, String elementLabel, String scope,
                                String reason, boolean matched) {}
    public record ExcludeDecision(String itemId, String scope) {}
    public record ApplyRequest(String previewId, String idempotencyKey, List<String> selectedIds,
                               List<ExcludeDecision> exclusions, List<Long> restoreExclusionIds) {}
    public record ApplyResult(String applicationId, int added, int completed, int removed,
                              int excluded, String renderStatus, String renderError) {}
}
