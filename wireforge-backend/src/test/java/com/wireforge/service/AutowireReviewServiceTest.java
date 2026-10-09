package com.wireforge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wireforge.ai.AiClient;
import com.wireforge.entity.*;
import com.wireforge.mapper.*;
import com.wireforge.model.AutowirePlan.*;
import com.wireforge.model.AutowirePlan;
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

    ApplyRequest targetRequest(AutowirePlan plan, List<String> selected, List<NavigationResolution> navigation, List<NavigationResolution> targets) {
        return new ApplyRequest(plan.previewId(),"direct-target-1234",selected,List.of(),List.of(),navigation,targets);
    }
    @Test void unresolvedOrdinaryTargetCanBeConfirmedInOneApplyWithoutAiOrNewPreview() {
        button.setLabel("功能入口");var old=AutowirePlannerTest.line(40,button,"ai",null);old.setTriggerType("double_click");
        when(lines.selectList(any())).thenReturn(List.of(old));
        var plan=service.preview(1L);var item=plan.items().get(0);assertFalse(item.applicable());
        var result=service.apply(1L,targetRequest(plan,List.of(item.id()),List.of(),List.of(new NavigationResolution(item.stableKey(),3))));
        assertEquals(1,result.completed());assertFalse(plan.items().get(0).applicable());
        verify(lines).updateById(argThat((Interaction i)->i.getId()==40&&i.getTargetPageId()==3&&"double_click".equals(i.getTriggerType())&&"navigate".equals(i.getActionType())));
        verifyNoInteractions(ai);verify(tx).commit(any());
    }
    @Test void existingUncertainTargetCanBeExplicitlyConfirmedInOneApply() {
        button.setLabel("收集");var old=AutowirePlannerTest.line(40,button,"ai",3L);when(lines.selectList(any())).thenReturn(List.of(old));
        var plan=service.preview(1L);var item=plan.items().get(0);assertFalse(item.applicable());
        var result=service.apply(1L,targetRequest(plan,List.of(item.id()),List.of(),List.of(new NavigationResolution(item.stableKey(),3))));
        assertEquals(1,result.completed());verify(lines).updateById(argThat((Interaction i)->i.getId()==40&&i.getTargetPageId()==3));verifyNoInteractions(ai);
    }
    @Test void annotatedHumanTargetCreatesOnlyTheCheckedRelation() {
        button.setLabel("功能入口");when(annotations.selectList(any())).thenReturn(List.of(AutowirePlannerTest.annotation(1,button,"点击后跳转到对应功能页面")));
        var plan=service.preview(1L);var item=plan.items().get(0);
        var result=service.apply(1L,targetRequest(plan,List.of(item.id()),List.of(),List.of(new NavigationResolution(item.stableKey(),3))));
        assertEquals(1,result.added());verify(lines,times(1)).insert(argThat((Interaction i)->i.getElementId()==10&&i.getTargetPageId()==3));verifyNoInteractions(ai);
    }
    @Test void choosingUnresolvedNavigationDoesNotApplyOtherPagesOrRunAiAgain() {
        page.setName("首页");target.setName("个人中心");var peer=AutowirePlannerTest.page(2,"发现");
        when(pages.selectList(any())).thenReturn(List.of(page,peer,target));
        var els=new ArrayList<>(NavigationPlannerTest.bar(1,"首页","我的"));els.addAll(NavigationPlannerTest.bar(2,"首页","我的"));when(elements.selectList(any())).thenReturn(els);
        var plan=service.preview(1L);var item=plan.items().stream().filter(i->i.pageId()==1&&i.elementLabel().equals("我的")).findFirst().orElseThrow();assertFalse(item.applicable());
        clearInvocations(ai);
        var result=service.apply(1L,targetRequest(plan,List.of(item.id()),List.of(new NavigationResolution(item.stableKey(),3)),List.of()));
        assertEquals(1,result.added());verify(lines,times(1)).insert(argThat((Interaction i)->i.getElementId()==item.elementId()&&i.getTargetPageId()==3&&i.getParams().contains("user_choice")));
        verify(render).bindings(argThat(changed->changed.size()==1&&changed.get(0).getPageId()==1),anyList(),anyList());verifyNoInteractions(ai);
    }
    @Test void chosenNavigationCanChangeAProposalTargetWithoutChangingItsSource() {
        var alternate=AutowirePlannerTest.page(4,"账户中心");when(pages.selectList(any())).thenReturn(List.of(page,target,alternate));
        when(elements.selectList(any())).thenReturn(NavigationPlannerTest.bar(1,"积分商城","兑换记录"));
        var plan=service.preview(1L);var item=plan.items().get(0);assertEquals(3L,item.targetPageId());
        var result=service.apply(1L,targetRequest(plan,List.of(item.id()),List.of(new NavigationResolution(item.stableKey(),4)),List.of()));
        assertEquals(1,result.added());verify(lines).insert(argThat((Interaction i)->i.getElementId()==item.elementId()&&i.getTargetPageId()==4&&"click".equals(i.getTriggerType())&&"navigate".equals(i.getActionType())));verifyNoInteractions(ai);
    }
    @Test void applyTargetsRejectUncheckedForgedDuplicateAndWrongKindChoicesBeforeWrites() {
        var plan=service.preview(1L);var item=plan.items().get(0);var choice=new NavigationResolution(item.stableKey(),3);
        assertThrows(IllegalStateException.class,()->service.apply(1L,targetRequest(plan,List.of(),List.of(),List.of(choice))));
        assertThrows(IllegalStateException.class,()->service.apply(1L,targetRequest(plan,List.of(item.id()),List.of(),List.of(new NavigationResolution(item.stableKey(),999)))));
        assertThrows(IllegalStateException.class,()->service.apply(1L,targetRequest(plan,List.of(item.id()),List.of(),List.of(choice,choice))));
        assertThrows(IllegalStateException.class,()->service.apply(1L,targetRequest(plan,List.of(item.id()),List.of(choice),List.of())));
        assertThrows(IllegalStateException.class,()->service.apply(1L,targetRequest(plan,List.of(item.id()),List.of(),List.of(new NavigationResolution("forged",3)))));
        verify(lines,never()).insert(any(Interaction.class));verify(jdbc,never()).update(anyString(),any(Object[].class));verifyNoInteractions(render,ai);
    }
    @Test void directTargetsCannotOverrideUnknownSourcesConflictsOrDecorations() {
        when(lines.selectList(any())).thenReturn(List.of(AutowirePlannerTest.line(40,button,null,null)));
        var unknown=service.preview(1L);var unknownItem=unknown.items().get(0);
        assertThrows(IllegalStateException.class,()->service.apply(1L,targetRequest(unknown,List.of(unknownItem.id()),List.of(),List.of(new NavigationResolution(unknownItem.stableKey(),3)))));
        button.setType("image");when(lines.selectList(any())).thenReturn(List.of(AutowirePlannerTest.line(40,button,"ai",3L)));
        var decoration=service.preview(1L);var decorationItem=decoration.items().get(0);
        assertThrows(IllegalStateException.class,()->service.apply(1L,targetRequest(decoration,List.of(decorationItem.id()),List.of(),List.of(new NavigationResolution(decorationItem.stableKey(),3)))));
        var els=NavigationPlannerTest.bar(1,"积分商城","兑换记录");when(elements.selectList(any())).thenReturn(els);
        when(lines.selectList(any())).thenReturn(List.of(AutowirePlannerTest.line(50,els.get(2),"ai",3L),AutowirePlannerTest.line(51,els.get(3),"ai",3L)));
        var conflict=service.preview(1L);var conflictItem=conflict.items().get(0);assertEquals("conflict",conflictItem.navigation().status());
        assertThrows(IllegalStateException.class,()->service.apply(1L,targetRequest(conflict,List.of(conflictItem.id()),List.of(new NavigationResolution(conflictItem.stableKey(),3)),List.of())));
        verify(lines,never()).insert(any(Interaction.class));verify(lines,never()).updateById(any(Interaction.class));verify(lines,never()).deleteById(anyLong());verifyNoInteractions(render);
    }
    @Test void navigationMemberExclusionCannotBeBypassedByChoosingAnAllowedPage() {
        page.setName("首页");target.setName("个人中心");var els=NavigationPlannerTest.bar(1,"首页","我的");when(elements.selectList(any())).thenReturn(els);
        var x=new InteractionExclusion();x.setId(1L);x.setActive(true);x.setScope("relation");x.setPageId(1L);x.setElementId(els.get(3).getId());x.setElementKey(AutowirePlanner.elementKey(els.get(3)));x.setRelationKey("click|navigate|3");when(exclusions.selectList(any())).thenReturn(List.of(x));
        var plan=service.preview(1L);var item=plan.items().get(0);assertTrue(item.navigation().candidateTargets().stream().anyMatch(t->t.id()==3));clearInvocations(ai);
        assertThrows(ResponseStatusException.class,()->service.apply(1L,targetRequest(plan,List.of(item.id()),List.of(new NavigationResolution(item.stableKey(),3)),List.of())));
        verify(lines,never()).insert(any(Interaction.class));verify(jdbc,never()).update(anyString(),any(Object[].class));verifyNoInteractions(ai,render);
    }
    @Test void directOrdinaryTargetMustRespectRelationExclusions() {
        button.setLabel("功能入口");when(lines.selectList(any())).thenReturn(List.of(AutowirePlannerTest.line(40,button,"ai",null)));
        when(pages.selectList(any())).thenReturn(List.of(page,target,AutowirePlannerTest.page(4,"另一个功能")));
        var x=new InteractionExclusion();x.setId(1L);x.setActive(true);x.setScope("relation");x.setPageId(1L);x.setElementId(button.getId());x.setElementKey(AutowirePlanner.elementKey(button));x.setRelationKey("click|navigate|3");when(exclusions.selectList(any())).thenReturn(List.of(x));
        var plan=service.preview(1L);var item=plan.items().get(0);assertEquals(List.of(new TargetOption(4,"另一个功能")),item.targetSelection().candidateTargets());
        assertThrows(IllegalStateException.class,()->service.apply(1L,targetRequest(plan,List.of(item.id()),List.of(),List.of(new NavigationResolution(item.stableKey(),3)))));
        verify(lines,never()).updateById(any(Interaction.class));verifyNoInteractions(ai,render);
    }
    @Test void differentChosenTargetsForTheSameNavigationFamilyFailAtomically() {
        page.setName("首页");target.setName("我的");var peer=AutowirePlannerTest.page(2,"发现");when(pages.selectList(any())).thenReturn(List.of(page,peer,target));
        var els=new ArrayList<>(NavigationPlannerTest.bar(1,"首页","我的"));els.addAll(NavigationPlannerTest.bar(2,"首页","我的"));when(elements.selectList(any())).thenReturn(els);
        var plan=service.preview(1L);var chosen=plan.items().stream().filter(i->i.elementLabel().equals("我的")).toList();assertEquals(2,chosen.size());
        assertThrows(IllegalStateException.class,()->service.apply(1L,targetRequest(plan,chosen.stream().map(Item::id).toList(),List.of(new NavigationResolution(chosen.get(0).stableKey(),3),new NavigationResolution(chosen.get(1).stableKey(),1)),List.of())));
        verify(lines,never()).insert(any(Interaction.class));verify(jdbc,never()).update(anyString(),any(Object[].class));verify(tx).rollback(any());verifyNoInteractions(ai,render);
    }
    @Test void newManualRelationsOrChangedTriggersCannotBeOverwrittenByDirectChoices() {
        button.setLabel("功能入口");var old=AutowirePlannerTest.line(40,button,"ai",null);when(lines.selectList(any())).thenReturn(List.of(old));
        var plan=service.preview(1L);var item=plan.items().get(0);var req=targetRequest(plan,List.of(item.id()),List.of(),List.of(new NavigationResolution(item.stableKey(),3)));
        old.setTriggerType("hover");assertThrows(ResponseStatusException.class,()->service.apply(1L,req));
        old.setTriggerType("click");old.setSource("user");assertThrows(ResponseStatusException.class,()->service.apply(1L,req));
        old.setSource(null);assertThrows(ResponseStatusException.class,()->service.apply(1L,req));
        verify(lines,never()).updateById(any(Interaction.class));verify(jdbc,never()).update(anyString(),any(Object[].class));verifyNoInteractions(ai,render);
    }
    @Test void directChoicesRespectUncheckedConfirmedPeerNavigationTargets() {
        page.setName("首页");target.setName("我的");var peer=AutowirePlannerTest.page(2,"发现");var alternate=AutowirePlannerTest.page(4,"账户中心");
        when(pages.selectList(any())).thenReturn(List.of(page,peer,target,alternate));
        var els=new ArrayList<>(NavigationPlannerTest.bar(1,"首页","我的"));els.addAll(NavigationPlannerTest.bar(2,"首页","我的"));when(elements.selectList(any())).thenReturn(els);
        for (String source : List.of("user","autowire_review")) {
            var confirmed=AutowirePlannerTest.line(50,els.get(6),source,3L);when(lines.selectList(any())).thenReturn(List.of(confirmed));
            var plan=service.preview(1L);var item=plan.items().stream().filter(i->i.pageId()==1&&"我的".equals(i.elementLabel())).findFirst().orElseThrow();
            assertEquals("confirmed_mapping",item.navigation().basis());
            var failure=assertThrows(ResponseStatusException.class,()->service.apply(1L,targetRequest(plan,List.of(item.id()),List.of(new NavigationResolution(item.stableKey(),4)),List.of())));
            assertTrue(failure.getReason().contains("发现"));assertTrue(failure.getReason().contains("我的"));
        }
        verify(lines,never()).insert(any(Interaction.class));verify(lines,never()).updateById(any(Interaction.class));verify(jdbc,never()).update(anyString(),any(Object[].class));
        verify(tx,times(2)).rollback(any());verifyNoInteractions(ai,render);
    }
    @Test void sameTargetCanBeAppliedWithoutTouchingTheConfirmedPeer() {
        page.setName("首页");target.setName("我的");var peer=AutowirePlannerTest.page(2,"发现");when(pages.selectList(any())).thenReturn(List.of(page,peer,target));
        var els=new ArrayList<>(NavigationPlannerTest.bar(1,"首页","我的"));els.addAll(NavigationPlannerTest.bar(2,"首页","我的"));when(elements.selectList(any())).thenReturn(els);
        var confirmed=AutowirePlannerTest.line(50,els.get(6),"user",3L);when(lines.selectList(any())).thenReturn(List.of(confirmed));
        var plan=service.preview(1L);var item=plan.items().stream().filter(i->i.pageId()==1&&"我的".equals(i.elementLabel())).findFirst().orElseThrow();
        var result=service.apply(1L,targetRequest(plan,List.of(item.id()),List.of(new NavigationResolution(item.stableKey(),3)),List.of()));
        assertEquals(1,result.added());verify(lines,times(1)).insert(argThat((Interaction i)->i.getElementId()==item.elementId()&&i.getTargetPageId()==3));
        verify(lines,never()).updateById(any(Interaction.class));verifyNoInteractions(ai);verify(tx).commit(any());
    }
    @Test void directChoicesCannotOverwriteAValidStandaloneNavigationMapping() {
        page.setName("首页");target.setName("我的");var alternate=AutowirePlannerTest.page(4,"账户中心");when(pages.selectList(any())).thenReturn(List.of(page,target,alternate));
        var els=NavigationPlannerTest.bar(1,"首页","我的");when(elements.selectList(any())).thenReturn(els);
        var entry=NavigationPlanner.detect(List.of(page),els).stream().filter(e->"我的".equals(e.label())).findFirst().orElseThrow();
        var mapping=new HashMap<String,Object>();mapping.put("nav_family_key",entry.familyKey());mapping.put("nav_item_key",entry.itemKey());mapping.put("nav_label","我的");
        mapping.put("target_page_id",3L);mapping.put("origin","user_choice");mapping.put("active",true);
        when(jdbc.queryForList(startsWith("SELECT * FROM project_navigation_mapping"),eq(1L))).thenReturn(List.of(mapping));
        var plan=service.preview(1L);var item=plan.items().get(0);assertEquals("confirmed_mapping",item.navigation().basis());
        var failure=assertThrows(ResponseStatusException.class,()->service.apply(1L,targetRequest(plan,List.of(item.id()),List.of(new NavigationResolution(item.stableKey(),4)),List.of())));
        assertTrue(failure.getReason().contains("已确认映射"));verify(lines,never()).insert(any(Interaction.class));verify(jdbc,never()).update(anyString(),any(Object[].class));verifyNoInteractions(ai,render);
    }
    @Test void staleNavigationMappingDoesNotBlockAnExplicitCheckedTarget() {
        page.setName("首页");target.setName("我的");var alternate=AutowirePlannerTest.page(4,"账户中心");when(pages.selectList(any())).thenReturn(List.of(page,target,alternate));
        var els=NavigationPlannerTest.bar(1,"首页","我的");when(elements.selectList(any())).thenReturn(els);
        var entry=NavigationPlanner.detect(List.of(page),els).stream().filter(e->"我的".equals(e.label())).findFirst().orElseThrow();
        var mapping=Map.<String,Object>of("nav_family_key",entry.familyKey(),"nav_item_key",entry.itemKey(),"nav_label","我的","target_page_id",3L,"origin","user_choice","active",true,"source_interaction_id",999L);
        when(jdbc.queryForList(startsWith("SELECT * FROM project_navigation_mapping"),eq(1L))).thenReturn(List.of(mapping));
        var plan=service.preview(1L);var item=plan.items().get(0);
        var result=service.apply(1L,targetRequest(plan,List.of(item.id()),List.of(new NavigationResolution(item.stableKey(),4)),List.of()));
        assertEquals(1,result.added());verify(lines).insert(argThat((Interaction i)->i.getTargetPageId()==4));verifyNoInteractions(ai);verify(tx).commit(any());
    }
    @Test void oldEmptyTriggersCanBeConfirmedWithTheirExistingClickMeaning() {
        button.setLabel("功能入口");
        for (String trigger : Arrays.asList(null,"","  ")) {
            var old=AutowirePlannerTest.line(40,button,"ai",null);old.setTriggerType(trigger);when(lines.selectList(any())).thenReturn(List.of(old));
            var plan=service.preview(1L);var item=plan.items().get(0);assertEquals("click",item.trigger());
            var result=service.apply(1L,targetRequest(plan,List.of(item.id()),List.of(),List.of(new NavigationResolution(item.stableKey(),3))));assertEquals(1,result.completed());
            assertEquals("click",old.getTriggerType());assertEquals(3L,old.getTargetPageId());
        }
        verify(lines,times(3)).updateById(any(Interaction.class));verifyNoInteractions(ai);verify(tx,times(3)).commit(any());
    }
    @Test void ordinaryManualTargetPersistsTheChosenTargetInsteadOfTheOldHint() throws Exception {
        button.setLabel("功能入口");var old=AutowirePlannerTest.line(40,button,"ai",null);old.setParams("{\"target_name\":\"兑换记录\",\"animation\":\"fade\"}");
        var alternate=AutowirePlannerTest.page(4,"账户中心");when(pages.selectList(any())).thenReturn(List.of(page,target,alternate));when(lines.selectList(any())).thenReturn(List.of(old));
        var plan=service.preview(1L);var item=plan.items().get(0);
        var result=service.apply(1L,targetRequest(plan,List.of(item.id()),List.of(),List.of(new NavigationResolution(item.stableKey(),4))));assertEquals(1,result.completed());
        assertEquals("账户中心",json.readTree(old.getParams()).path("target_name").asText());assertEquals("fade",json.readTree(old.getParams()).path("animation").asText());
        assertTrue(service.preview(1L).items().isEmpty(),"人工确认的目标不应被旧目标提示再次否定");verifyNoInteractions(ai);
    }
    @Test void expiredDirectChoiceRejectsTheWholeBatch() {
        var plan=service.preview(1L);var item=plan.items().get(0);
        @SuppressWarnings("unchecked") var cache=(Map<String,InteractionAutowireService.Cached>)org.springframework.test.util.ReflectionTestUtils.getField(service,"previews");
        var cached=cache.get(plan.previewId());var expired=new AutowirePlan(plan.previewId(),plan.projectId(),System.currentTimeMillis()-1,plan.items(),plan.exclusions(),plan.warnings(),plan.protectedCount(),plan.navigationDiagnostics());
        cache.put(plan.previewId(),new InteractionAutowireService.Cached(expired,cached.fingerprint()));
        assertThrows(ResponseStatusException.class,()->service.apply(1L,targetRequest(plan,List.of(item.id()),List.of(),List.of(new NavigationResolution(item.stableKey(),3)))));
        verify(lines,never()).insert(any(Interaction.class));verify(tx).rollback(any());verifyNoInteractions(ai,render);
    }
    @Test void directChoiceRetryIsIdempotentAndDifferentTargetCannotReuseTheKey() throws Exception {
        var alternate=AutowirePlannerTest.page(4,"另一个功能");when(pages.selectList(any())).thenReturn(List.of(page,target,alternate));
        var plan=service.preview(1L);var item=plan.items().get(0);var req=targetRequest(plan,List.of(item.id()),List.of(),List.of(new NavigationResolution(item.stableKey(),3)));
        var saved=new ApplyResult("direct-saved",1,0,0,0,"done",null);var hash=AutowirePlanner.sha(json.writeValueAsString(req));
        when(jdbc.queryForList(startsWith("SELECT request_hash"),eq(1L),eq(req.idempotencyKey()))).thenReturn(List.of(Map.of("request_hash",hash,"result_json",json.writeValueAsString(saved))));
        assertEquals(saved,service.apply(1L,req));
        assertThrows(ResponseStatusException.class,()->service.apply(1L,targetRequest(plan,List.of(item.id()),List.of(),List.of(new NavigationResolution(item.stableKey(),4)))));
        verify(lines,never()).insert(any(Interaction.class));verifyNoInteractions(ai);
    }
    @Test void oldJsonAndPreviouslySavedIdempotencyHashesRemainCompatible() throws Exception {
        var plan=service.preview(1L);var item=plan.items().get(0);var req=request(plan.previewId(),List.of(item.id()));
        var legacy=json.valueToTree(req);((com.fasterxml.jackson.databind.node.ObjectNode)legacy).remove(List.of("navigationResolutions","targetResolutions"));
        var decoded=json.treeToValue(legacy,ApplyRequest.class);assertTrue(decoded.navigationResolutions().isEmpty());assertTrue(decoded.targetResolutions().isEmpty());
        var saved=new ApplyResult("legacy-saved",1,0,0,0,"done",null);
        when(jdbc.queryForList(startsWith("SELECT request_hash"),eq(1L),eq(req.idempotencyKey()))).thenReturn(List.of(Map.of("request_hash",AutowirePlanner.sha(json.writeValueAsString(legacy)),"result_json",json.writeValueAsString(saved))));
        assertEquals(saved,service.apply(1L,decoded));verify(lines,never()).insert(any(Interaction.class));verifyNoInteractions(ai);
    }
}
