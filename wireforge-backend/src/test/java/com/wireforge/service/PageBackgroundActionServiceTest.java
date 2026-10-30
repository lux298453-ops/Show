package com.wireforge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wireforge.entity.Element;
import com.wireforge.entity.Interaction;
import com.wireforge.entity.Page;
import com.wireforge.mapper.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PageBackgroundActionServiceTest {
    @BeforeAll static void metadata(){
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(),"test"),Interaction.class);
    }
    private final ProjectService projects=mock(ProjectService.class);
    private final ProjectMapper projectMapper=mock(ProjectMapper.class);
    private final PageMapper pages=mock(PageMapper.class);
    private final ElementMapper elements=mock(ElementMapper.class);
    private final InteractionMapper actions=mock(InteractionMapper.class);
    private final PageBackgroundActionService service=new PageBackgroundActionService(projects,projectMapper,pages,elements,actions);
    private final Page page=new Page();

    private static Element setting(long id){
        Element e=new Element();e.setId(id);e.setPageId(1L);e.setType("background");e.setStyle(PageBackgroundAction.STYLE);e.setCreatedBy("manual");return e;
    }

    @BeforeEach void setup(){
        page.setId(1L);page.setProjectId(7L);page.setCanvasWidth(375);page.setCanvasHeight(833);page.setHtmlContent("<p>原来的奖励卡片</p>");
        when(pages.selectOne(any())).thenReturn(page);
        when(projects.getPageEditingStatus(1L,"client")).thenReturn(Map.of("conflict",false));
        when(elements.selectList(any())).thenReturn(List.of());when(actions.selectList(any())).thenReturn(List.of());
        when(elements.insert(any(Element.class))).thenAnswer(invocation -> {Element e=invocation.getArgument(0);e.setId(9L);return 1;});
    }

    @Test void createsExplicitUserReturnWithoutChangingPageHtml(){
        Element created=service.configure(7L,1L,true,"client");
        assertTrue(PageBackgroundAction.persistent(created));assertEquals(375,created.getWidth());assertEquals(833,created.getHeight());
        var captured=ArgumentCaptor.forClass(Interaction.class);verify(actions).insert(captured.capture());
        assertEquals("back",captured.getValue().getActionType());assertEquals("user",captured.getValue().getSource());
        assertNull(captured.getValue().getTargetPageId());assertEquals(9L,captured.getValue().getElementId());
        verify(pages,never()).updateById(any());assertEquals("<p>原来的奖励卡片</p>",page.getHtmlContent());
    }

    @Test void repeatedSettingReusesIdentityAndDisablingIsAnExplicitPersistentChoice(){
        Element setting=setting(9);when(elements.selectList(any())).thenReturn(List.of(setting));
        Interaction prior=new Interaction();prior.setId(11L);prior.setTargetPageId(55L);prior.setActionType("back");
        when(actions.selectList(any())).thenReturn(List.of(prior));
        service.configure(7L,1L,false,"client");
        verify(elements,never()).insert(any(Element.class));verify(elements).updateById(setting);verify(actions).updateById(prior);
        assertEquals("none",prior.getActionType());assertNull(prior.getTargetPageId());assertTrue(PageBackgroundAction.persistent(setting));
    }

    @Test void wrongProjectAndEditingLockCannotChangeSettings(){
        page.setProjectId(99L);assertThrows(IllegalArgumentException.class,()->service.configure(7L,1L,true,"client"));
        verifyNoInteractions(elements,actions);
        page.setProjectId(7L);when(projects.getPageEditingStatus(1L,"client")).thenReturn(Map.of("conflict",true));
        assertThrows(IllegalStateException.class,()->service.configure(7L,1L,true,"client"));verifyNoInteractions(elements,actions);
    }

    @Test void reanalysisDeletesOnlyRecognitionDataAndKeepsManualBackgroundIdentityAndInteraction(){
        Element managed=setting(9);Element recognized=new Element();recognized.setId(2L);recognized.setType("text");recognized.setLabel("魔女");
        when(elements.selectList(any())).thenReturn(List.of(managed,recognized));
        AnnotationMapper notes=mock(AnnotationMapper.class);when(notes.selectList(any())).thenReturn(List.of());
        AnalyzeService analyzer=new AnalyzeService(null,null,pages,elements,actions,notes,new ObjectMapper(),null,null,null,null,null,null);
        ReflectionTestUtils.invokeMethod(analyzer,"clearPageData",1L);
        ArgumentCaptor<Collection> deleted=ArgumentCaptor.forClass(Collection.class);verify(elements).deleteBatchIds(deleted.capture());
        assertEquals(List.of(2L),deleted.getValue());
        ArgumentCaptor<LambdaQueryWrapper> query=ArgumentCaptor.forClass(LambdaQueryWrapper.class);verify(actions).delete(query.capture());
        query.getValue().getSqlSegment();assertFalse(query.getValue().getParamNameValuePairs().containsValue(9L));
        assertTrue(query.getValue().getParamNameValuePairs().containsValue(2L));
    }

    @Test void settingsOnlyPageSurvivesReanalysisWithoutDeletingAnyActions(){
        when(elements.selectList(any())).thenReturn(List.of(setting(9)));
        AnalyzeService analyzer=new AnalyzeService(null,null,pages,elements,actions,null,new ObjectMapper(),null,null,null,null,null,null);
        ReflectionTestUtils.invokeMethod(analyzer,"clearPageData",1L);verifyNoInteractions(actions);verify(elements,never()).deleteBatchIds(any());
    }

    @Test void ordinaryManualBackgroundAndMalformedAiStyleDoNotBecomePersistentPageSettings(){
        Element plain=setting(9);plain.setStyle(null);assertFalse(PageBackgroundAction.persistent(plain));
        Element ai=setting(10);ai.setCreatedBy("ai");assertFalse(PageBackgroundAction.persistent(ai));
        ai.setStyle("not json");assertFalse(PageBackgroundAction.managed(ai));
    }
}
