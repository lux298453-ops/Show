package com.wireforge.service;

import com.wireforge.entity.*;
import com.wireforge.model.AutowirePlan.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class NavigationPlannerTest {
    final NavigationPlanner planner = new NavigationPlanner();
    static Page page(long id,String name) { return AutowirePlannerTest.page(id,name); }
    static List<Element> bar(long pageId,String... labels) {
        List<Element> out=new ArrayList<>();
        for(int i=0;i<labels.length;i++) {
            double cx=375.0*(i+.5)/labels.length;
            Element icon=AutowirePlannerTest.el(pageId*100+i*2,"icon","图标",cx-12,744,24,24);
            Element text=AutowirePlannerTest.el(pageId*100+i*2+1,"text",labels[i],cx-24,774,48,16);
            icon.setPageId(pageId);text.setPageId(pageId);out.add(icon);out.add(text);
        }
        return out;
    }
    NavigationPlanner.Result plan(List<Page> pages,List<Element> els,List<Interaction> lines) {return planner.plan(pages,els,lines,List.of(),List.of(),List.of(),Map.of());}
    Item label(NavigationPlanner.Result result,String name) {return result.items().stream().filter(i->name.equals(i.elementLabel())).findFirst().orElseThrow();}
    @Test void missingBottomRelationshipsAreDetectedWithoutAnnotations() {
        var result=plan(List.of(page(1,"首页"),page(2,"商城"),page(3,"我的")),bar(1,"首页","商城","我的"),List.of());
        assertEquals(3,result.entries().size());assertEquals(2,result.items().size());
        assertEquals(2L,label(result,"商城").targetPageId());assertTrue(label(result,"商城").selectedByDefault());
        assertEquals("current_page",result.diagnostics().get(0).status());
        assertEquals(2,result.entries().get(1).members().size());
        assertEquals(125,result.entries().get(1).width());
    }
    @Test void familyUsesOrderedLabelsAndRegionAcrossPages() {
        var first=NavigationPlanner.detect(List.of(page(1,"首页")),bar(1,"首页","商城","我的"));
        var second=NavigationPlanner.detect(List.of(page(2,"商城")),bar(2,"首页","商城","我的"));
        var reordered=NavigationPlanner.detect(List.of(page(3,"我的")),bar(3,"商城","首页","我的"));
        assertEquals(first.get(0).familyKey(),second.get(0).familyKey());
        assertNotEquals(first.get(0).familyKey(),reordered.get(0).familyKey());
        assertEquals(first.get(0).itemKey(),second.get(0).itemKey());
    }
    @Test void confirmedPeerTargetWinsOverNameAndCannotCrossFamilies() {
        var pages=List.of(page(1,"首页"),page(2,"账户中心"),page(3,"我的"));
        var els=new ArrayList<>(bar(1,"首页","我的"));els.addAll(bar(2,"首页","我的"));
        Interaction peer=AutowirePlannerTest.line(10,els.get(6),"user",2L);
        assertEquals(2L,label(plan(pages,els,List.of(peer)),"我的").targetPageId());
        els.subList(4,8).clear();els.addAll(bar(2,"我的","首页"));peer.setElementId(els.get(4).getId());
        assertEquals(3L,label(plan(pages,els,List.of(peer)),"我的").targetPageId());
    }
    @Test void validExistingTargetAndManualRelationshipsArePreserved() {
        var els=bar(1,"首页","我的");var pages=List.of(page(1,"首页"),page(2,"我的"),page(3,"个人中心"));
        Interaction old=AutowirePlannerTest.line(10,els.get(2),"ai",3L);
        var result=plan(pages,els,List.of(old));assertTrue(result.items().isEmpty());assertTrue(result.diagnostics().stream().anyMatch(d->d.status().equals("correct")));
        old.setSource("user");result=plan(pages,els,List.of(old));assertTrue(result.diagnostics().stream().anyMatch(d->d.status().equals("protected")));
    }
    @Test void ambiguousOrDuplicatedLabelsRequireManualChoice() {
        var pages=List.of(page(1,"首页"),page(2,"我的"),page(3,"我的"));
        assertFalse(label(plan(pages,bar(1,"首页","我的"),List.of()),"我的").applicable());
        var duplicate=plan(List.of(page(1,"首页"),page(2,"我的")),bar(1,"首页","我的","我的"),List.of());
        assertEquals(2,duplicate.items().size());assertTrue(duplicate.items().stream().noneMatch(Item::applicable));
    }
    @Test void localTabsRemainLocalWhileCrossPageConversionNeedsReview() {
        var els=bar(1,"首页","我的");var old=AutowirePlannerTest.line(10,els.get(2),"ai",null);old.setActionType("tab_switch");
        var item=label(plan(List.of(page(1,"首页"),page(2,"我的")),els,List.of(old)),"我的");
        assertEquals("complete",item.category());assertTrue(item.applicable());assertFalse(item.selectedByDefault());assertEquals("tab_switch",item.navigation().previousAction());
        var local=bar(1,"全部","进行中","已完成");
        assertTrue(plan(List.of(page(1,"全部"),page(2,"进行中"),page(3,"已完成")),local,List.of()).items().isEmpty());
        assertEquals(3,plan(List.of(page(1,"全部")),local,List.of()).diagnostics().stream().filter(d->d.status().equals("local_tab")).count());
    }
    @Test void memberExclusionOverridesConfirmedMapping() {
        var els=bar(1,"首页","我的");var pages=List.of(page(1,"首页"),page(2,"账户中心"));var entry=NavigationPlanner.detect(pages,els).get(1);
        var x=new InteractionExclusion();x.setId(1L);x.setActive(true);x.setScope("element");x.setPageId(1L);x.setElementId(els.get(3).getId());x.setElementKey(AutowirePlanner.elementKey(els.get(3)));x.setRelationKey("*");
        var result=planner.plan(pages,els,List.of(),List.of(),List.of(x),List.of(new NavigationPlanner.Mapping(entry.familyKey(),entry.itemKey(),"我的",2,"review",null,true)),Map.of());
        assertTrue(result.items().isEmpty());assertTrue(result.diagnostics().stream().anyMatch(d->d.status().equals("excluded")));
    }
    @Test void conflictingConfirmedMappingsAreNotAutoMerged() {
        var els=bar(1,"首页","我的");var pages=List.of(page(1,"首页"),page(2,"账户"),page(3,"设置"));var entry=NavigationPlanner.detect(pages,els).get(1);
        var maps=List.of(new NavigationPlanner.Mapping(entry.familyKey(),entry.itemKey(),"我的",2,"review",null,true),new NavigationPlanner.Mapping(entry.familyKey(),entry.itemKey(),"我的",3,"review",null,true));
        var item=label(planner.plan(pages,els,List.of(),List.of(),List.of(),maps,Map.of()),"我的");assertEquals("conflict",item.navigation().status());assertFalse(item.applicable());
    }
    @Test void userChoiceHasStableIdentityButChangedDecisionNeedsReview() {
        var els=bar(1,"首页","我的");var pages=List.of(page(1,"首页"),page(2,"我的"),page(3,"设置"));var entry=NavigationPlanner.detect(pages,els).get(1);
        var before=label(plan(pages,els,List.of()),"我的");
        var after=label(planner.plan(pages,els,List.of(),List.of(),List.of(),List.of(),Map.of(entry.stableKey(),3L)),"我的");
        assertEquals(before.id(),after.id());assertEquals(before.stableKey(),after.stableKey());assertNotEquals(before.decisionFingerprint(),after.decisionFingerprint());assertFalse(after.selectedByDefault());
    }
    @Test void listingButtonsNearBottomAreNotNavigation() {
        var els=bar(1,"兑换","购买","领取");assertTrue(NavigationPlanner.detect(List.of(page(1,"兑换商城")),els).isEmpty());
    }
    @Test void missingAnchorsAreReportedWithoutInventedElements() {
        var page=page(1,"首页");page.setHtmlContent("<div class='nav-item'>首页</div><div class='nav-item'>我的</div>");
        var result=plan(List.of(page),List.of(),List.of());assertTrue(result.items().isEmpty());assertEquals("unbound",result.diagnostics().get(0).status());
    }
    @Test void topAndSideHintsAreIndependentFamilies() {
        var top=bar(1,"首页","我的");top.forEach(e->{e.setPositionY(e.getPositionY()-700);e.setStyle("{\"navigation\":{\"region\":\"top\"}}");});
        // Use one clickable anchor per item for explicit top navigation.
        var buttons=new ArrayList<Element>();for(int i=0;i<2;i++){var e=top.get(i*2+1);e.setType("button");buttons.add(e);}
        var found=NavigationPlanner.detect(List.of(page(1,"首页")),buttons);assertEquals(2,found.size());assertEquals("top",found.get(0).region());
        assertNotEquals(found.get(0).familyKey(),NavigationPlanner.detect(List.of(page(1,"首页")),bar(1,"首页","我的")).get(0).familyKey());
    }
}
