package com.wireforge.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.wireforge.ai.HtmlRenderer;
import com.wireforge.entity.Project;
import com.wireforge.mapper.*;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProjectMetadataTest {
    private final ProjectMapper projects = mock(ProjectMapper.class);
    private final PageMapper pages = mock(PageMapper.class);
    private final ElementMapper elements = mock(ElementMapper.class);
    private final InteractionMapper interactions = mock(InteractionMapper.class);
    private final AnnotationMapper annotations = mock(AnnotationMapper.class);
    private final CommentThreadMapper comments = mock(CommentThreadMapper.class);
    private final CommentReplyMapper replies = mock(CommentReplyMapper.class);
    private final AnalyzeService analyze = mock(AnalyzeService.class);
    private final AppMapService appMap = mock(AppMapService.class);
    private final InteractionAutowireService autowire = mock(InteractionAutowireService.class);
    private final AutowireRenderService render = mock(AutowireRenderService.class);
    private final ElementGroupService groups = mock(ElementGroupService.class);
    private final HtmlRenderer html = mock(HtmlRenderer.class);
    private final ProjectService service = new ProjectService(projects, pages, elements, interactions,
            annotations, comments, replies, analyze, appMap, autowire, render, groups, html);
    private Project saved;

    @BeforeEach
    void setup() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), Project.class);
        saved = new Project();
        saved.setId(159L);
        saved.setName("修改后的项目");
        saved.setDescription("修改后的说明");
        saved.setCoverImage("/files/95.png");
        saved.setAppMap("{\"navigation\":[]}");
        saved.setCreatedAt(LocalDateTime.of(2026, 10, 9, 12, 0));
        when(projects.update(isNull(), any(Wrapper.class))).thenReturn(1);
        when(projects.selectById(159L)).thenReturn(saved);
    }

    @Test
    void updatesOnlyMetadataAndReturnsPersistedProjectWithoutAiOrPageWork() {
        var body = new HashMap<>(Map.of("name", "  修改后的项目  ", "description", "修改后的说明",
                "coverImage", "forged", "appMap", "forged", "createdAt", "forged"));
        assertSame(saved, service.updateProject(159L, body));
        LambdaUpdateWrapper<Project> update = capturedUpdate();
        assertEquals(2, update.getSqlSet().split(",").length);
        assertTrue(update.getSqlSet().startsWith("name="));
        assertTrue(update.getSqlSet().contains("description="));
        assertTrue(update.getParamNameValuePairs().containsValue("修改后的项目"));
        assertTrue(update.getParamNameValuePairs().containsValue("修改后的说明"));
        assertFalse(update.getParamNameValuePairs().containsValue("forged"));
        assertTrue(update.getSqlSegment().contains("id ="));
        assertTrue(update.getParamNameValuePairs().containsValue(159L));
        assertEquals("/files/95.png", saved.getCoverImage());
        assertEquals("{\"navigation\":[]}", saved.getAppMap());
        assertEquals(LocalDateTime.of(2026, 10, 9, 12, 0), saved.getCreatedAt());
        verifyNoSideEffects();
    }

    @Test
    void omittedDescriptionDoesNotOverwriteIt() {
        service.updateProject(159L, Map.of("name", "修改后的项目"));
        assertFalse(capturedUpdate().getSqlSet().contains("description"));
        assertEquals("修改后的说明", saved.getDescription());
        verifyNoSideEffects();
    }

    @Test
    void emptyDescriptionIsWrittenSoItCanBeCleared() {
        saved.setDescription("");
        service.updateProject(159L, Map.of("name", "修改后的项目", "description", ""));
        var update = capturedUpdate();
        assertTrue(update.getSqlSet().contains("description="));
        assertTrue(update.getParamNameValuePairs().containsValue(""));
        verifyNoSideEffects();
    }

    @Test
    void blankOrMissingNameIsRejectedBeforeAnyDatabaseWork() {
        assertThrows(IllegalStateException.class, () -> service.updateProject(159L, Map.of("name", " \t\n")));
        assertThrows(IllegalStateException.class, () -> service.updateProject(159L, Map.of("description", "说明")));
        assertThrows(IllegalStateException.class, () -> service.updateProject(159L, null));
        verifyNoInteractions(projects);
        verifyNoSideEffects();
    }

    @Test
    void nameLimitMatchesVarcharCharactersIncludingSupplementaryUnicode() {
        String name = "😀".repeat(255);
        saved.setName(name);
        assertSame(saved, service.updateProject(159L, Map.of("name", name)));
        clearInvocations(projects);
        assertThrows(IllegalStateException.class, () -> service.updateProject(159L, Map.of("name", name + "a")));
        verifyNoInteractions(projects);
        verifyNoSideEffects();
    }

    @Test
    void descriptionLimitUsesUtf8BytesRatherThanCharacterCount() {
        String description = "说".repeat(21845); // Exactly 65,535 UTF-8 bytes.
        saved.setDescription(description);
        assertSame(saved, service.updateProject(159L, Map.of("name", "修改后的项目", "description", description)));
        clearInvocations(projects);
        assertThrows(IllegalStateException.class, () -> service.updateProject(159L,
                Map.of("name", "修改后的项目", "description", description + "a")));
        verifyNoInteractions(projects);
        verifyNoSideEffects();
    }

    @Test
    void unknownProjectUsesExistingNotFoundError() {
        when(projects.update(isNull(), any(Wrapper.class))).thenReturn(0);
        when(projects.selectById(159L)).thenReturn(null);
        var error = assertThrows(IllegalStateException.class,
                () -> service.updateProject(159L, Map.of("name", "修改后的项目")));
        assertEquals("项目不存在: 159", error.getMessage());
        verifyNoSideEffects();
    }

    @Test
    void zeroRowsIsSuccessfulOnlyForAnAlreadySavedValue() {
        when(projects.update(isNull(), any(Wrapper.class))).thenReturn(0);
        assertSame(saved, service.updateProject(159L, Map.of("name", "修改后的项目", "description", "修改后的说明")));
        verifyNoSideEffects();
    }

    @Test
    void zeroRowsDoesNotReportUnsavedValuesAsSuccess() {
        when(projects.update(isNull(), any(Wrapper.class))).thenReturn(0);
        assertThrows(IllegalStateException.class,
                () -> service.updateProject(159L, Map.of("name", "未保存的名称")));
        assertThrows(IllegalStateException.class,
                () -> service.updateProject(159L, Map.of("name", "修改后的项目", "description", "未保存的说明")));
        verifyNoSideEffects();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private LambdaUpdateWrapper<Project> capturedUpdate() {
        ArgumentCaptor<Wrapper> capture = ArgumentCaptor.forClass(Wrapper.class);
        verify(projects).update(isNull(), capture.capture());
        return (LambdaUpdateWrapper<Project>) capture.getValue();
    }

    private void verifyNoSideEffects() {
        verifyNoInteractions(pages, elements, interactions, annotations, comments, replies,
                analyze, appMap, autowire, render, groups, html);
    }
}
