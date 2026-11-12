package com.wireforge.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.wireforge.entity.Page;
import com.wireforge.mapper.PageMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AiAdjustmentSaveTest {
    private final PageMapper pages = mock(PageMapper.class);
    private final ProjectService service = new ProjectService(null, pages, null, null, null, null, null, null, null, null, null, null, null);
    private final Page page = new Page();

    @BeforeEach void setup() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), Page.class);
        page.setId(71001L); page.setProjectId(7L); page.setHtmlContent("original");
        when(pages.selectById(71001L)).thenReturn(page);
        when(pages.update(isNull(), any(Wrapper.class))).thenReturn(1);
    }

    @Test void guardedSaveUsesAtomicBaselineComparisonAndRejectsConcurrentChanges() {
        service.updatePageHtml(7L, 71001L, "changed", "ai-client", "original", null);
        verify(pages).update(isNull(), argThat(wrapper -> wrapper.getSqlSegment().contains("BINARY html_content")));
        verify(pages, never()).updateById(any());
        page.setHtmlContent("someone else changed");
        assertThrows(IllegalStateException.class, () -> service.updatePageHtml(7L, 71001L, "changed", "ai-client", "original", null));
        when(pages.update(isNull(), any(Wrapper.class))).thenReturn(0);
        page.setHtmlContent("original");
        assertThrows(IllegalStateException.class, () -> service.updatePageHtml(7L, 71001L, "changed", "ai-client", "original", null));
    }

    @Test void wrongProjectDeletedPageAndMissingClientCannotSave() {
        assertThrows(IllegalArgumentException.class, () -> service.updatePageHtml(99L, 71001L, "changed", "ai", "original", null));
        assertThrows(IllegalArgumentException.class, () -> service.updatePageHtml(7L, 71001L, "changed", "", "original", null));
        page.setIsDeleted(1);
        assertThrows(IllegalArgumentException.class, () -> service.updatePageHtml(7L, 71001L, "changed", "ai", "original", null));
        verify(pages, never()).update(isNull(), any(Wrapper.class));
    }

    @Test void existingEditorLockIsRespectedAndLegacySavesStillWork() {
        service.lockPageEditing(71001L, "other", "Other editor", true);
        try {
            assertThrows(IllegalStateException.class, () -> service.updatePageHtml(7L, 71001L, "changed", "ai", "original", null));
        } finally { service.lockPageEditing(71001L, "other", "Other editor", false); }
        service.updatePageHtml(71001L, "legacy changed", "editor", null);
        verify(pages).updateById(page); assertEquals("legacy changed", page.getHtmlContent());
    }

    @Test void retryAfterLostAcknowledgementDoesNotReportAFalseConflict() {
        page.setHtmlContent("already saved");
        assertDoesNotThrow(() -> service.updatePageHtml(7L, 71001L, "already saved", "ai", "original", null));
        verify(pages, never()).update(isNull(), any(Wrapper.class));
    }
}
