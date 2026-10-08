package com.wireforge.service;

import com.wireforge.entity.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AutowirePlannerTest {
    final AutowirePlanner planner = new AutowirePlanner();
    final Page store = page(1, "积分商城"), confirm = page(2, "兑换确认弹窗"), records = page(3, "兑换记录");
    final List<Page> pages = List.of(store, confirm, records);
    static Page page(long id, String name) { Page p=new Page();p.setId(id);p.setProjectId(1L);p.setName(name);p.setCanvasWidth(375);p.setCanvasHeight(812);return p; }
    static Element el(long id, String type, String label, double x, double y, double w, double h) {
        Element e=new Element();e.setId(id);e.setPageId(1L);e.setType(type);e.setLabel(label);e.setPositionX(x);e.setPositionY(y);e.setWidth(w);e.setHeight(h);return e;
    }
    static Interaction line(long id, Element e, String source, Long target) {
        Interaction i=new Interaction();i.setId(id);i.setElementId(e.getId());i.setSource(source);i.setTriggerType("click");i.setActionType("navigate");i.setTargetPageId(target);return i;
    }
    static Annotation annotation(long id, Element e, String text) { Annotation a=new Annotation();a.setId(id);a.setPageId(1L);a.setElementId(e.getId());a.setText(text);return a; }

    @Test void productCardsAndPurchaseButtonsAreNotNavigationEvidence() {
        Element card=el(10,"container","兑换记录",10,100,140,180);
        Element card2=el(11,"container","商品二",175,100,140,180);
        Element button=el(12,"button","兑换",30,230,100,30);
        assertTrue(planner.listing(card,List.of(card,card2,button)));
        assertTrue(planner.plan(pages,List.of(card,card2,button),List.of(),List.of(),List.of()).items().isEmpty());
    }
    @Test void childButtonOwnsExplicitActionAndCardDoesNotInheritIt() {
        Element card=el(10,"container","商品卡片",10,100,150,180), button=el(12,"button","兑换",30,230,100,30);
        var result=planner.plan(pages,List.of(card,button),List.of(),List.of(annotation(20,button,"点击兑换按钮，打开兑换确认弹窗")),List.of());
        assertEquals(1,result.items().size());assertEquals(12,result.items().get(0).elementId());assertEquals("popup",result.items().get(0).action());
    }
    @Test void explicitWholeCardCannotPierceAnIndependentChildButton() {
        Element card=el(10,"container","商品卡片",10,100,150,180), button=el(12,"button","兑换",30,230,100,30);
        var result=planner.plan(pages,List.of(card,button),List.of(),List.of(annotation(20,button,"点击兑换按钮打开兑换确认弹窗"),annotation(21,card,"点击整卡进入兑换记录")),List.of());
        assertEquals(1,result.items().size());assertEquals(12,result.items().get(0).elementId());
    }
    @Test void explicitWholeCardWithoutChildControlCanNavigate() {
        Element card=el(10,"container","商品卡片",10,100,150,180);
        var result=planner.plan(pages,List.of(card),List.of(),List.of(annotation(21,card,"点击整卡进入兑换记录")),List.of());
        assertEquals(1,result.items().size());assertEquals(10,result.items().get(0).elementId());
    }
    @Test void unresolvedExistingIntentIsCompletedWithoutInventingNewOperation() {
        Element button=el(12,"button","兑换",30,230,100,30);Interaction i=line(30,button,"ai",null);
        i.setParams("{\"target_name\":\"兑换确认弹窗\",\"animation\":\"fade\"}");i.setActionType("popup");
        var result=planner.plan(pages,List.of(button),List.of(i),List.of(),List.of());
        assertEquals("complete",result.items().get(0).category());assertEquals(2L,result.items().get(0).targetPageId());
    }
    @Test void manualRelationsAndLocalStateActionsArePreserved() {
        Element card=el(10,"container","商品卡片",10,100,150,180), button=el(12,"button","兑换",30,230,100,30);
        Interaction user=line(30,card,"user",3L), local=line(31,button,"ai",null);local.setActionType("toggle");
        var result=planner.plan(pages,List.of(card,button),List.of(user,local),List.of(),List.of());
        assertEquals(1,result.protectedCount());assertTrue(result.items().isEmpty());
    }
    @Test void unsupportedAutomaticCardLinesAreSuggestedForRemovalOnly() {
        Element card=el(10,"container","商品卡片",10,100,150,180);
        var item=planner.plan(pages,List.of(card),List.of(line(30,card,"ai_inferred",2L)),List.of(),List.of()).items().get(0);
        assertEquals("remove",item.category());assertFalse(item.selectedByDefault());
    }
    @Test void unknownLegacySourceCannotBeDeletedAutomatically() {
        Element card=el(10,"container","商品卡片",10,100,150,180);
        var item=planner.plan(pages,List.of(card),List.of(line(30,card,null,2L)),List.of(),List.of()).items().get(0);
        assertEquals("uncertain",item.category());assertFalse(item.applicable());
    }
    @Test void existingNavigationWithoutAnnotationsNeedsReviewRatherThanRemoval() {
        Element button=el(10,"button","每日任务",10,100,100,30);
        var item=planner.plan(pages,List.of(button),List.of(line(30,button,"ai",3L)),List.of(),List.of()).items().get(0);
        assertEquals("uncertain",item.category());assertFalse(item.applicable());
    }
    @Test void existingCloseBehaviorIsPreserved() {
        Element close=el(10,"icon","关闭",10,100,24,24);Interaction back=line(30,close,"ai",null);back.setActionType("back");
        assertTrue(planner.plan(pages,List.of(close),List.of(back),List.of(),List.of()).items().isEmpty());
    }
    @Test void exactNavigationEntryWorksButLooseNameContainmentDoesNot() {
        Element exact=el(10,"button","兑换记录",10,100,100,30), loose=el(11,"button","查看兑换记录奖励",10,150,160,30);
        var result=planner.plan(pages,List.of(exact,loose),List.of(),List.of(),List.of());
        assertEquals(1,result.items().size());assertEquals(10,result.items().get(0).elementId());
    }
    @Test void ambiguousTargetsAreNotSelected() {
        Element button=el(12,"button","查看",30,230,100,30);
        var item=planner.plan(pages,List.of(button),List.of(),List.of(annotation(20,button,"点击打开兑换确认弹窗或进入兑换记录")),List.of()).items().get(0);
        assertEquals("uncertain",item.category());assertFalse(item.applicable());
    }
    @Test void negativeBusinessAnnotationStopsLinking() {
        Element button=el(12,"button","兑换记录",30,230,100,30);
        assertTrue(planner.plan(pages,List.of(button),List.of(),List.of(annotation(20,button,"仅展示兑换记录，不需要跳转")),List.of()).items().isEmpty());
    }
    @Test void excludedRelationDoesNotReappearAndCanBeRestored() {
        Element e=el(10,"button","兑换记录",10,100,100,30);InteractionExclusion x=new InteractionExclusion();x.setId(40L);x.setPageId(1L);x.setElementId(10L);x.setElementKey(AutowirePlanner.elementKey(e));x.setScope("relation");x.setRelationKey("click|navigate|3");x.setActive(true);
        assertTrue(planner.plan(pages,List.of(e),List.of(),List.of(),List.of(x)).items().isEmpty());
        x.setActive(false);assertEquals(1,planner.plan(pages,List.of(e),List.of(),List.of(),List.of(x)).items().size());
    }
    @Test void stableFingerprintOnlyMigratesAnUnambiguousExactMatch() {
        Element old=el(10,"button","兑换记录",10,100,100,30), fresh=el(11,"button","兑换记录",10,100,100,30), duplicate=el(12,"button","兑换记录",10,100,100,30);
        InteractionExclusion x=new InteractionExclusion();x.setPageId(1L);x.setElementId(10L);x.setElementKey(AutowirePlanner.elementKey(old));x.setActive(true);
        assertTrue(AutowirePlanner.matches(x,fresh,List.of(fresh)));
        assertFalse(AutowirePlanner.matches(x,fresh,List.of(fresh,duplicate)));
        fresh.setPositionX(30.0);assertFalse(AutowirePlanner.matches(x,fresh,List.of(fresh)));
    }
    @Test void duplicateAutomaticLineHasOneRemovalProposal() {
        Element button=el(10,"button","兑换记录",10,100,100,30);
        var result=planner.plan(pages,List.of(button),List.of(line(30,button,"autowire",3L),line(31,button,"autowire",3L)),List.of(),List.of());
        assertEquals(1,result.items().size());assertEquals(31L,result.items().get(0).interactionId());assertEquals("remove",result.items().get(0).category());
    }
    @Test void patchPreservesHtmlAndEscapesScriptPayload() {
        String original="<html><body><div style=\"color:red\">用户微调</div></body></html>";
        String patched=AutowireRenderService.patchHtml(original,"[{\"label\":\"</script><script>alert(1)</script>\"}]","");
        assertTrue(patched.contains("<div style=\"color:red\">用户微调</div>"));assertFalse(patched.contains("<script>alert(1)"));
        String twice=AutowireRenderService.patchHtml(patched,"[]","");assertEquals(1,twice.split("<!-- wf-autowire-start -->",-1).length-1);
    }
}
