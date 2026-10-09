package com.wireforge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wireforge.ai.AiClient;
import com.wireforge.entity.*;
import com.wireforge.mapper.*;
import com.wireforge.model.AutowirePlan.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class AutowireReviewServiceTest {
    PageMapper pages=mock(PageMapper.class);ElementMapper elements=mock(ElementMapper.class);InteractionMapper lines=mock(InteractionMapper.class);
    AnnotationMapper annotations=mock(AnnotationMapper.class);ProjectMapper projects=mock(ProjectMapper.class);InteractionExclusionMapper exclusions=mock(InteractionExclusionMapper.class);
    AiClient ai=mock(AiClient.class);JdbcTemplate jdbc=mock(JdbcTemplate.class);PlatformTransactionManager tx=mock(PlatformTransactionManager.class);
    AutowireRenderService render=mock(AutowireRenderService.class);ObjectMapper json=new ObjectMapper().findAndRegisterModules();
    Page page=AutowirePlannerTest.page(1,"积分商城"), target=AutowirePlannerTest.page(3,"兑换记录");Element button=AutowirePlannerTest.el(10,"button","兑换记录",10,100,100,30);
    InteractionAutowireService service;
    @BeforeEach void setup() {
        Project p=new Project();p.setId(1L);when(projects.selectOne(any())).thenReturn(p);when(pages.selectList(any())).thenReturn(List.of(page,target));
        when(elements.selectList(any())).thenReturn(List.of(button));when(lines.selectList(any())).thenReturn(List.of());when(annotations.selectList(any())).thenReturn(List.of());when(exclusions.selectList(any())).thenReturn(List.of());
        when(tx.getTransaction(any())).thenReturn(new SimpleTransactionStatus());when(render.bindings(anyList(),anyList(),anyList())).thenReturn(List.of());
        service=new InteractionAutowireService(pages,elements,lines,annotations,projects,exclusions,ai,json,new AutowirePlanner(),jdbc,tx,render);
    }
    ApplyRequest request(String token,List<String> selected) {return new ApplyRequest(token,"review-key-1234",selected,List.of(),List.of());}
    @Test void previewNeverWritesOrRenders() {
        var plan=service.preview(1L);assertEquals(1,plan.items().size());
        verifyNoInteractions(render,tx);verify(jdbc,never()).update(anyString(),any(Object[].class));verify(lines,never()).insert(any(Interaction.class));verify(lines,never()).deleteById(anyLong());verifyNoInteractions(ai);
    }
    @Test void stalePlanRollsBackWithoutChangingRelations() {
        var plan=service.preview(1L);button.setLabel("同时编辑后的入口");
        assertThrows(ResponseStatusException.class,()->service.apply(1L,request(plan.previewId(),List.of(plan.items().get(0).id()))));
        verify(tx).rollback(any());verify(lines,never()).insert(any(Interaction.class));verifyNoInteractions(render);
    }
    @Test void forgedItemOrOtherProjectTokenCannotBeApplied() {
        var plan=service.preview(1L);
        assertThrows(IllegalStateException.class,()->service.apply(1L,request(plan.previewId(),List.of("forged"))));
        assertThrows(ResponseStatusException.class,()->service.apply(2L,request(plan.previewId(),List.of())));
        verify(lines,never()).insert(any(Interaction.class));
    }
    @Test void uncheckedItemDoesNotBecomeAnExclusion() {
        var plan=service.preview(1L);var result=service.apply(1L,request(plan.previewId(),List.of()));
        assertEquals(0,result.added());assertEquals(0,result.excluded());verify(lines,never()).insert(any(Interaction.class));
        verify(jdbc,never()).update(startsWith("INSERT INTO interaction_exclusion"),any(Object[].class));verify(tx).commit(any());
    }
    @Test void idempotencyReturnsOriginalResultWithoutInsertingAgain() throws Exception {
        var plan=service.preview(1L);ApplyRequest req=request(plan.previewId(),List.of(plan.items().get(0).id()));
        String hash=AutowirePlanner.sha(json.writeValueAsString(req));var saved=new ApplyResult("old-application",1,0,0,0,"done",null);
        when(jdbc.queryForList(startsWith("SELECT request_hash"),eq(1L),eq("review-key-1234"))).thenReturn(List.of(Map.of("request_hash",hash,"result_json",json.writeValueAsString(saved))));
        assertEquals(saved,service.apply(1L,req));verify(lines,never()).insert(any(Interaction.class));verify(tx).commit(any());
    }
    @Test void applyUsesOnlyServerProposalAndCommitsBeforeRendering() {
        var plan=service.preview(1L);var result=service.apply(1L,request(plan.previewId(),List.of(plan.items().get(0).id())));
        assertEquals(1,result.added());verify(lines).insert(argThat((Interaction i)->i.getTargetPageId()==3L&&"autowire_review".equals(i.getSource())));
        var order=inOrder(tx,render);order.verify(tx).commit(any());order.verify(render).process(result.applicationId());
    }
    @Test void manualNavigationTargetsMustBelongToPriorServerPreview() {
        when(elements.selectList(any())).thenReturn(NavigationPlannerTest.bar(1,"积分商城","兑换记录"));
        var plan=service.preview(1L);var item=plan.items().get(0);
        var next=service.preview(1L,new PreviewRequest(plan.previewId(),List.of(new NavigationResolution(item.stableKey(),3L))));
        assertEquals("user_choice",next.items().get(0).navigation().basis());assertFalse(next.items().get(0).selectedByDefault());
        assertThrows(IllegalStateException.class,()->service.preview(1L,new PreviewRequest(plan.previewId(),List.of(new NavigationResolution(item.stableKey(),999L)))));
        assertThrows(ResponseStatusException.class,()->service.preview(1L,new PreviewRequest(null,List.of(new NavigationResolution(item.stableKey(),3L)))));
        verify(lines,never()).insert(any(Interaction.class));verifyNoInteractions(render,tx);
    }
    @Test void reviewedNavigationWritesMappingAndParamsInSameTransaction() {
        when(elements.selectList(any())).thenReturn(NavigationPlannerTest.bar(1,"积分商城","兑换记录"));
        doAnswer(inv->{((Interaction)inv.getArgument(0)).setId(500L);return 1;}).when(lines).insert(any(Interaction.class));
        var plan=service.preview(1L);var result=service.apply(1L,request(plan.previewId(),List.of(plan.items().get(0).id())));
        assertEquals(1,result.added());verify(lines).insert(argThat((Interaction i)->i.getParams().contains("\"navigation\"")&&i.getTargetPageId()==3));
        var order=inOrder(jdbc,tx);order.verify(jdbc).update(startsWith("INSERT INTO project_navigation_mapping"),any(Object[].class));order.verify(tx).commit(any());
    }
    @Test void ordinaryUnresolvedJumpCanBeReviewedAndAppliedWithoutPreviewWrites() {
        button.setLabel("功能入口");
        var old=AutowirePlannerTest.line(40,button,"ai",null);when(lines.selectList(any())).thenReturn(List.of(old));
        var plan=service.preview(1L);var item=plan.items().get(0);
        assertNull(item.navigation());assertFalse(item.applicable());assertEquals(List.of(new TargetOption(3,"兑换记录")),item.targetSelection().candidateTargets());
        var next=service.preview(1L,new PreviewRequest(plan.previewId(),List.of(),List.of(new NavigationResolution(item.stableKey(),3))));
        var resolved=next.items().get(0);assertEquals("complete",resolved.category());assertEquals(3L,resolved.targetPageId());
        assertTrue(resolved.applicable());assertFalse(resolved.selectedByDefault());assertEquals("user_choice",resolved.targetSelection().basis());
        verifyNoInteractions(render,tx,ai);verify(lines,never()).updateById(any(Interaction.class));
        var result=service.apply(1L,request(next.previewId(),List.of(resolved.id())));assertEquals(1,result.completed());
        verify(lines).updateById(argThat((Interaction i)->i.getId()==40&&i.getTargetPageId()==3));
    }
    @Test void ordinaryAnnotatedIntentCanReceiveAHumanTargetWithoutAiGuessing() {
        button.setLabel("功能入口");when(annotations.selectList(any())).thenReturn(List.of(AutowirePlannerTest.annotation(1,button,"点击后跳转到对应功能页面")));
        var plan=service.preview(1L);var item=plan.items().get(0);assertFalse(item.applicable());assertNotNull(item.targetSelection());
        var next=service.preview(1L,new PreviewRequest(plan.previewId(),List.of(),List.of(new NavigationResolution(item.stableKey(),3))));
        assertEquals("add",next.items().get(0).category());assertFalse(next.items().get(0).selectedByDefault());verifyNoInteractions(ai,render,tx);
    }
    @Test void anExistingTargetStillNeedsReviewButCanBeConfirmedWithoutChangingThePage() {
        button.setLabel("收集");var old=AutowirePlannerTest.line(40,button,"ai",3L);when(lines.selectList(any())).thenReturn(List.of(old));
        var plan=service.preview(1L);var item=plan.items().get(0);assertEquals("uncertain",item.category());assertEquals(3L,item.targetPageId());assertFalse(item.applicable());
        assertNotNull(item.targetSelection());
        assertThrows(IllegalStateException.class,()->service.apply(1L,request(plan.previewId(),List.of(item.id()))));
        var next=service.preview(1L,new PreviewRequest(plan.previewId(),List.of(),List.of(new NavigationResolution(item.stableKey(),3))));
        var verified=next.items().get(0);assertTrue(verified.applicable());assertEquals(3L,verified.targetPageId());assertEquals("user_choice",verified.targetSelection().basis());
        verify(lines,never()).updateById(any(Interaction.class));verifyNoInteractions(render);
    }
    @Test void ordinaryTargetSelectionRejectsForgeryDuplicatesAndStalePreview() {
        var plan=service.preview(1L);var item=plan.items().get(0);var choice=new NavigationResolution(item.stableKey(),3);
        assertThrows(IllegalStateException.class,()->service.preview(1L,new PreviewRequest(plan.previewId(),List.of(),List.of(new NavigationResolution(item.stableKey(),999)))));
        assertThrows(IllegalStateException.class,()->service.preview(1L,new PreviewRequest(plan.previewId(),List.of(),List.of(choice,choice))));
        assertThrows(ResponseStatusException.class,()->service.preview(1L,new PreviewRequest(null,List.of(),List.of(choice))));
        assertThrows(ResponseStatusException.class,()->service.preview(2L,new PreviewRequest(plan.previewId(),List.of(),List.of(choice))));
        button.setLabel("页面已修改");assertThrows(ResponseStatusException.class,()->service.preview(1L,new PreviewRequest(plan.previewId(),List.of(),List.of(choice))));
        verifyNoInteractions(render,tx);verify(lines,never()).insert(any(Interaction.class));
    }
    @Test void targetPickerCannotBypassDecorationsUnknownSourcesOrConflictingLines() {
        button.setType("image");when(lines.selectList(any())).thenReturn(List.of(AutowirePlannerTest.line(40,button,"ai",null)));
        assertTrue(service.preview(1L).items().stream().allMatch(i->i.targetSelection()==null));
        button.setType("button");when(lines.selectList(any())).thenReturn(List.of(AutowirePlannerTest.line(40,button,null,null)));
        var unknown=service.preview(1L);assertNull(unknown.items().get(0).targetSelection());
        assertThrows(IllegalStateException.class,()->service.preview(1L,new PreviewRequest(unknown.previewId(),List.of(),List.of(new NavigationResolution(unknown.items().get(0).stableKey(),3)))));
        when(lines.selectList(any())).thenReturn(List.of(AutowirePlannerTest.line(40,button,"ai",null),AutowirePlannerTest.line(41,button,"ai",null)));
        assertTrue(service.preview(1L).items().stream().allMatch(i->i.targetSelection()==null));
        when(lines.selectList(any())).thenReturn(List.of(AutowirePlannerTest.line(40,button,"user",3L)));assertTrue(service.preview(1L).items().isEmpty());
        verifyNoInteractions(render,tx);
    }
    @Test void aiNavigationOnlySuggestsAllowedTargetsAndRemainsUnchecked() throws Exception {
        page.setName("首页");target.setName("个人中心");when(elements.selectList(any())).thenReturn(NavigationPlannerTest.bar(1,"首页","我的"));
        when(ai.generateText(anyString(),anyString())).thenAnswer(inv->{var inputs=json.readTree(inv.getArgument(1,String.class));return "[{\"item_id\":\""+inputs.get(0).path("item_id").asText()+"\",\"decision\":\"link\",\"target_page_id\":3,\"reason\":\"账户入口\"}]";});
        var plan=service.preview(1L);var item=plan.items().get(0);assertEquals("ai_suggestion",item.navigation().basis());assertTrue(item.applicable());assertFalse(item.selectedByDefault());
        verify(lines,never()).insert(any(Interaction.class));verifyNoInteractions(render,tx);
    }
    @Test void invalidAiTargetOrAiFailureLeavesManualReview() throws Exception {
        page.setName("首页");target.setName("个人中心");when(elements.selectList(any())).thenReturn(NavigationPlannerTest.bar(1,"首页","我的"));
        when(ai.generateText(anyString(),anyString())).thenAnswer(inv->{var inputs=json.readTree(inv.getArgument(1,String.class));return "[{\"item_id\":\""+inputs.get(0).path("item_id").asText()+"\",\"decision\":\"link\",\"target_page_id\":999}]";});
        assertFalse(service.preview(1L).items().get(0).applicable());
        doThrow(new IllegalStateException("test failure")).when(ai).generateText(anyString(),anyString());
        var plan=service.preview(1L);assertFalse(plan.items().get(0).applicable());assertFalse(plan.warnings().isEmpty());verify(lines,never()).insert(any(Interaction.class));
    }
    @Test void backgroundAutowireCannotSilentlyApplyNavigationCandidates() {
        when(elements.selectList(any())).thenReturn(NavigationPlannerTest.bar(1,"积分商城","兑换记录"));
        assertEquals(0,service.autowireProjectInteractions(1L));verify(lines,never()).insert(any(Interaction.class));verifyNoInteractions(tx,render,ai);
    }
    @Test void excludingNavigationUnitRemovesAutomaticLinesOnIconAndText() {
        var els=NavigationPlannerTest.bar(1,"积分商城","兑换记录");when(elements.selectList(any())).thenReturn(els);
        var icon=AutowirePlannerTest.line(50,els.get(2),"ai",3L);var text=AutowirePlannerTest.line(51,els.get(3),"ai",3L);
        when(lines.selectList(any())).thenReturn(List.of(icon,text));when(lines.deleteById(anyLong())).thenReturn(1);
        var plan=service.preview(1L);assertEquals("conflict",plan.items().get(0).navigation().status());
        var result=service.apply(1L,new ApplyRequest(plan.previewId(),"exclude-nav-unit",List.of(),List.of(new ExcludeDecision(plan.items().get(0).id(),"element")),List.of()));
        assertEquals(2,result.removed());verify(lines).deleteById(50L);verify(lines).deleteById(51L);
        verify(render).bindings(argThat(changed->changed.size()==2),anyList(),anyList());
    }
}
