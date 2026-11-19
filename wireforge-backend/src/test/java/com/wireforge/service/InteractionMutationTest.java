package com.wireforge.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wireforge.entity.Element;
import com.wireforge.entity.Interaction;
import com.wireforge.entity.Page;
import com.wireforge.entity.Project;
import com.wireforge.mapper.*;
import com.wireforge.model.InteractionMutation.*;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InteractionMutationTest {
    private final ProjectMapper projects = mock(ProjectMapper.class);
    private final PageMapper pages = mock(PageMapper.class);
    private final ElementMapper elements = mock(ElementMapper.class);
    private final InteractionMapper lines = mock(InteractionMapper.class);
    private final AutowireRenderService render = mock(AutowireRenderService.class);
    private final InteractionAutowireService autowire = mock(InteractionAutowireService.class);
    private final AppMapService appMap = mock(AppMapService.class);
    private final ProjectService service = new ProjectService(projects, pages, elements, lines,
            mock(AnnotationMapper.class), mock(CommentThreadMapper.class), mock(CommentReplyMapper.class),
            mock(AnalyzeService.class), appMap, autowire, render, mock(ElementGroupService.class), mock(com.wireforge.ai.HtmlRenderer.class));
    private Element first, second;
    private Interaction original, duplicate, other;

    @BeforeEach void setup() {
        for (Class<?> type : List.of(Project.class, Page.class, Element.class, Interaction.class))
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), type);
        Project project = new Project(); project.setId(7L); when(projects.selectById(7L)).thenReturn(project);
        Page page = new Page(); page.setId(1L); page.setProjectId(7L); page.setHtmlContent("edited layout");
        Page target = new Page(); target.setId(2L); target.setProjectId(7L);
        when(pages.selectById(1L)).thenReturn(page); when(pages.selectById(2L)).thenReturn(target);
        first = element(10); second = element(11);
        when(elements.selectById(10L)).thenReturn(first); when(elements.selectById(11L)).thenReturn(second);
        original = line(100, 10, "autowire"); duplicate = line(102, 10, "ai"); other = line(101, 11, "user");
        when(lines.selectList(any())).thenReturn(List.of(original, duplicate, other));
    }

    private Element element(long id) {
        Element el = new Element(); el.setId(id); el.setPageId(1L); el.setType("button"); el.setLabel("购买"); return el;
    }
    private Interaction line(long id, long elementId, String source) {
        Interaction line = new Interaction(); line.setId(id); line.setElementId(elementId); line.setSource(source);
        line.setTriggerType("click"); line.setActionType("navigate"); line.setTargetPageId(2L);
        line.setParams("{\"domUid\":\"buy-" + elementId + "\"}"); return line;
    }
    private Change delete(long elementId) { return new Change(1L, elementId, true, null, "click", null, null); }

    @Test void multiDeleteCommitsDisabledBindingsAndEnqueuesOneJobWithoutUpdatingPageDocuments() {
        var result = service.mutateInteractions(7L, new Request(List.of(delete(10), delete(11))));
        assertEquals(2, result.size());
        for (State state : result) {
            assertEquals("none", state.interaction().getActionType()); assertNull(state.interaction().getTargetPageId());
            assertEquals("user", state.interaction().getSource()); assertNotNull(state.interaction().getParams());
        }
        assertEquals(2L, original.getTargetPageId(), "history/render input must retain the old relation");
        verify(autowire).remember(eq(7L), eq(first), eq("relation"), eq("click|navigate|2"), anyString(), eq("canvas_delete"));
        verify(lines).deleteById(102L);
        verify(render).enqueueManual(eq(7L), eq(List.of(first, second)), eq(List.of(original, duplicate, other)));
        verifyNoInteractions(appMap);
        verify(pages, never()).updateById(any(Page.class));
        verify(autowire, never()).clearManualExclusions(anyLong(), any(), any());
        ArgumentCaptor<Wrapper<Interaction>> updates = ArgumentCaptor.forClass(Wrapper.class);
        verify(lines, times(2)).update(isNull(), updates.capture());
        for (var wrapper : updates.getAllValues()) {
            var update = (LambdaUpdateWrapper<Interaction>) wrapper;
            assertTrue(update.getSqlSet().contains("target_page_id="));
            assertTrue(update.getParamNameValuePairs().containsValue(null), "SQL must explicitly clear the old target");
        }
    }

    @Test void undoRestoresOnlyTheRelationAndClearsItsManualExclusion() {
        var restored = service.mutateInteractions(7L, new Request(List.of(
                new Change(1L, 10L, false, 2L, "click", "popup", "{\"domUid\":\"buy-10\"}"))));
        assertEquals("popup", restored.get(0).interaction().getActionType());
        assertEquals(2L, restored.get(0).interaction().getTargetPageId());
        verify(autowire).clearManualExclusions(eq(7L), eq(first), eq(restored.get(0).interaction()));
        verify(render).enqueueManual(eq(7L), eq(List.of(first)), anyList());
        verify(pages, never()).updateById(any(Page.class));
    }

    @Test void foreignElementOrTargetRejectsTheEntireBatchBeforeAnyWrite() {
        Page foreign = new Page(); foreign.setId(3L); foreign.setProjectId(9L);
        when(pages.selectById(3L)).thenReturn(foreign);
        assertThrows(IllegalArgumentException.class, () -> service.mutateInteractions(7L, new Request(List.of(
                delete(10), new Change(1L, 11L, false, 3L, "click", "navigate", null)))));
        when(elements.selectById(11L)).thenReturn(element(11));
        when(pages.selectById(1L)).thenReturn(foreign);
        assertThrows(IllegalArgumentException.class, () -> service.mutateInteractions(7L, new Request(List.of(delete(11)))));
        verifyNoInteractions(lines, render, autowire);
    }

    @Test void duplicateOrDeletedElementsRejectBeforeMutation() {
        assertThrows(IllegalArgumentException.class, () -> service.mutateInteractions(7L, new Request(List.of(delete(10), delete(10)))));
        Page deleted = new Page(); deleted.setId(1L); deleted.setProjectId(7L); deleted.setIsDeleted(1);
        when(pages.selectById(1L)).thenReturn(deleted);
        assertThrows(IllegalArgumentException.class, () -> service.mutateInteractions(7L, new Request(List.of(delete(10)))));
        verifyNoInteractions(lines, render, autowire);
    }

    @Test void manualRenderJobIsDurableButNeverRunsInTheRequestOrAfterCommitCallback() {
        var jdbc = mock(JdbcTemplate.class);
        var renderer = spy(new AutowireRenderService(jdbc, new ObjectMapper(), pages, elements, lines, appMap, mock(PlatformTransactionManager.class)));
        when(pages.selectList(any())).thenReturn(List.of()); when(lines.selectList(any())).thenReturn(List.of());
        TransactionSynchronizationManager.initSynchronization();
        try {
            renderer.enqueueManual(7L, List.of(first, second), List.of(original));
            assertTrue(TransactionSynchronizationManager.getSynchronizations().isEmpty(), "afterCommit must not block HTTP completion");
            verify(jdbc).update(contains("INSERT INTO autowire_render_job"), anyString(), eq(7L), eq("[1]"), anyString());
            verify(renderer, never()).process(anyString());
            verifyNoInteractions(appMap);
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
    }
}
