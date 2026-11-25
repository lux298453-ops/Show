package com.wireforge.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wireforge.entity.Page;
import com.wireforge.entity.Interaction;
import com.wireforge.mapper.PageMapper;
import com.wireforge.mapper.ProjectMapper;
import com.wireforge.mapper.InteractionMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PageMetadataServiceTest {
    final PageMapper pages = mock(PageMapper.class);
    final ProjectMapper projects = mock(ProjectMapper.class);
    final InteractionMapper interactions = mock(InteractionMapper.class);
    final PageMetadataService service = new PageMetadataService(pages, projects, interactions);
    final ObjectMapper json = new ObjectMapper();
    Page current;

    @BeforeEach void setup() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "page-metadata"), Page.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "page-metadata-interaction"), Interaction.class);
        current = PageNameResolverTest.scan(1, "A版/image_123"); current.setProjectId(10L);
        when(pages.selectOne(any(Wrapper.class))).thenReturn(current);
        when(pages.selectList(any(Wrapper.class))).thenReturn(List.of(current));
        when(pages.update(isNull(), any(Wrapper.class))).thenReturn(1);
    }

    @Test void firstRecognitionKeepsGroupAndRecordsOriginalNameAsAnAlias() throws Exception {
        Page snapshot = PageNameResolverTest.scan(1, "A版/image_123"); snapshot.setProjectId(10L);
        service.applyAnalysis(snapshot, json.readTree("{\"page_name\":\"确认购买\",\"page_type\":\"modal\"}"));
        assertEquals("A版/确认购买", snapshot.getName()); assertEquals("ai", snapshot.getNameSource());
        assertEquals("modal", snapshot.getPageType());
        assertEquals("A版/image_123", snapshot.getSourceName());
        assertSame(current, new PageReferences(List.of(current)).resolve("A版/image_123"));
        verifyNoInteractions(interactions);
    }

    @Test void aRenameAndTypeOverrideMadeDuringTheModelRequestAreProtected() throws Exception {
        current.setName("我的名字"); current.setNameSource("user"); current.setPageType("drawer"); current.setPageTypeSource("user");
        Page stale = PageNameResolverTest.scan(1, "旧名字"); stale.setProjectId(10L);
        service.applyAnalysis(stale, json.readTree("{\"page_name\":\"AI新名字\",\"page_type\":\"modal\"}"));
        assertEquals("我的名字", stale.getName()); assertEquals("drawer", stale.getPageType());
        assertEquals("user", stale.getNameSource()); assertEquals("user", stale.getPageTypeSource());
    }

    @Test void changingMetadataUpdatesNavigationAttributesWithoutReplacingContentOrRelations() {
        current.setName("确认购买"); current.setPageType("modal"); current.setPageTypeSource("ai");
        Page source = PageNameResolverTest.scan(2, "商城"); source.setProjectId(10L);
        source.setHtmlContent("<button data-nav='确认购买'>确认购买</button><p>用户编辑的正文</p>");
        when(pages.selectList(any(Wrapper.class))).thenReturn(List.of(current, source));
        var saved = service.update(10L, 1L, Map.of("name", "买这个", "pageType", "drawer"));
        assertEquals("买这个", saved.get("name")); assertEquals("drawer", saved.get("page_type"));
        assertEquals("user", saved.get("name_source")); assertEquals("user", saved.get("page_type_source"));
        assertTrue(source.getHtmlContent().contains("data-nav=\"买这个\""));
        assertTrue(source.getHtmlContent().contains(">确认购买</button>"));
        assertTrue(source.getHtmlContent().contains("用户编辑的正文"));
        var updates = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(pages, times(2)).update(isNull(), updates.capture());
        assertFalse(updates.getAllValues().get(0).getSqlSet().contains("html_content"));
        assertFalse(updates.getAllValues().get(0).getSqlSet().contains("canvas_"));
        assertTrue(updates.getAllValues().get(1).getSqlSegment().contains("BINARY html_content"));
        verify(pages, never()).updateById(any()); verifyNoInteractions(interactions);
    }

    @Test void duplicateNamesAndInvalidTypesAreRejectedBeforeWriting() {
        Page other = PageNameResolverTest.scan(2, "商城"); other.setProjectId(10L);
        when(pages.selectList(any(Wrapper.class))).thenReturn(List.of(current, other));
        assertThrows(IllegalArgumentException.class, () -> service.update(10L, 1L, Map.of("name", "商城")));
        assertThrows(IllegalArgumentException.class, () -> service.update(10L, 1L, Map.of("pageType", "unsupported")));
        verify(pages, never()).update(any(), any());
    }

    @Test void aConcurrentHtmlEditCausesTheRenameTransactionToFailInsteadOfOverwritingIt() {
        current.setName("旧名字"); current.setHtmlContent("<button data-nav='旧名字'>按钮</button>");
        when(pages.update(isNull(), any(Wrapper.class))).thenReturn(1, 0);
        assertThrows(IllegalStateException.class, () -> service.update(10L, 1L, Map.of("name", "新名字")));
        assertEquals("<button data-nav='旧名字'>按钮</button>", current.getHtmlContent());
    }

    @Test void migrationKeepsHistoricalNamesAndOnlyBackfillsMetadata() {
        current.setName("历史自定义名称"); current.setNameSource(null); current.setSourceName("原始设计稿"); current.setPageType("unknown");
        when(pages.selectList(any(Wrapper.class))).thenReturn(List.of(current), List.of());
        Interaction incoming = new Interaction(); incoming.setTargetPageId(1L); incoming.setActionType("popup");
        when(interactions.selectList(any(Wrapper.class))).thenReturn(List.of(incoming));
        service.run(null);
        assertEquals("历史自定义名称", current.getName());
        var updates = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(pages).update(isNull(), updates.capture());
        String set = updates.getValue().getSqlSet();
        assertFalse(set.matches("(?s).*(?:^|,)name=.*")); assertFalse(set.contains("html_content"));
        assertTrue(updates.getValue().getParamNameValuePairs().containsValue("modal"));
    }
}
