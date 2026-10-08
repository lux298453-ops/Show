package com.wireforge.model;

import java.util.List;

/** Server-owned proposals: clients submit item IDs, never arbitrary relation payloads. */
public record AutowirePlan(String previewId, long projectId, long expiresAt, List<Item> items,
                           List<ExclusionView> exclusions, List<String> warnings, int protectedCount) {
    public record Item(String id, String category, long pageId, String pageName, long elementId,
                       String elementLabel, Long interactionId, String trigger, String action,
                       Long targetPageId, String targetPageName, String source, String reason,
                       List<String> evidenceRefs, boolean applicable, boolean selectedByDefault) {}
    public record ExclusionView(long id, String pageName, String elementLabel, String scope,
                                String reason, boolean matched) {}
    public record ExcludeDecision(String itemId, String scope) {}
    public record ApplyRequest(String previewId, String idempotencyKey, List<String> selectedIds,
                               List<ExcludeDecision> exclusions, List<Long> restoreExclusionIds) {}
    public record ApplyResult(String applicationId, int added, int completed, int removed,
                              int excluded, String renderStatus, String renderError) {}
}
