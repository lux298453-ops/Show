package com.wireforge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wireforge.entity.*;
import com.wireforge.mapper.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class AppMapNavigationTest {
    PageMapper pages=mock(PageMapper.class);ElementMapper elements=mock(ElementMapper.class);InteractionMapper lines=mock(InteractionMapper.class);ProjectMapper projects=mock(ProjectMapper.class);
    List<Page> fixtures=List.of(NavigationPlannerTest.page(1,"首页"),NavigationPlannerTest.page(2,"我的"));
    AppMapService service=new AppMapService(projects,pages,elements,lines,new ObjectMapper());
    List<Element> bars(String... second) {var out=new ArrayList<>(NavigationPlannerTest.bar(1,"首页","我的"));out.addAll(NavigationPlannerTest.bar(2,second));return out;}
    void setup(List<Element> els,List<Interaction> interactions) {
        when(pages.selectList(any())).thenReturn(fixtures);when(elements.selectList(any())).thenReturn(els);when(lines.selectList(any())).thenReturn(interactions);
        var p=new Project();p.setId(1L);when(projects.selectById(1L)).thenReturn(p);
    }
    @Test void missingRelationshipsAreNotAllMistakenForActiveSelfItems() {
        setup(bars("首页","我的"),List.of());assertNull(service.buildAppMap(1));verify(projects,never()).updateById(any());
    }
    @Test void reviewedCrossLinksAndExactSelfEvidenceBuildSharedBar() {
        var els=bars("首页","我的");setup(els,List.of(AutowirePlannerTest.line(1,els.get(2),"autowire_review",2L),AutowirePlannerTest.line(2,els.get(4),"user",1L)));
        var map=service.buildAppMap(1);assertNotNull(map);assertEquals(2,map.path("tab_bar").path("items").size());verify(projects).updateById(any());
    }
    @Test void differentOrderFamiliesCannotBeCombined() {
        var els=bars("我的","首页");setup(els,List.of(AutowirePlannerTest.line(1,els.get(2),"autowire_review",2L),AutowirePlannerTest.line(2,els.get(6),"user",1L)));
        assertNull(service.buildAppMap(1));
    }
    @Test void conflictAcrossFamilyMembersCannotRewriteSharedDefaults() {
        var els=bars("首页","我的");setup(els,List.of(AutowirePlannerTest.line(1,els.get(2),"autowire_review",2L),AutowirePlannerTest.line(2,els.get(6),"user",1L)));
        assertNull(service.buildAppMap(1));
    }
}
