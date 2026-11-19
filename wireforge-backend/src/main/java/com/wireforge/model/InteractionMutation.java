package com.wireforge.model;

import com.wireforge.entity.Interaction;
import java.util.List;

/** Relation-only edits: no page HTML, geometry or annotation data is accepted. */
public final class InteractionMutation {
    private InteractionMutation() {}

    public record Request(List<Change> changes) {}
    public record Change(Long pageId, Long elementId, boolean remove, Long targetPageId,
                         String triggerType, String actionType, String params) {}
    public record State(Long pageId, Long elementId, Interaction interaction) {}
}
