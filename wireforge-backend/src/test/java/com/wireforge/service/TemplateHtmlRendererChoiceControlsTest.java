package com.wireforge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wireforge.entity.Element;
import com.wireforge.entity.Interaction;
import com.wireforge.entity.Page;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class TemplateHtmlRendererChoiceControlsTest {
    private static Element e(long id,String type,String text,double x,double y,double w,double h){
        Element e=new Element();e.setId(id);e.setType(type);e.setLabel(text);e.setPositionX(x);e.setPositionY(y);e.setWidth(w);e.setHeight(h);return e;
    }
    private static String render(List<Element> elements,Map<Long,List<Interaction>> actions) throws Exception {
        Page p=new Page();p.setId(1L);p.setName("选择组件");p.setCanvasWidth(375);p.setCanvasHeight(833);
        ObjectMapper mapper=new ObjectMapper();String before=mapper.writeValueAsString(elements);
        var renderer=new TemplateHtmlRenderer(new DesignColorSampler(),mapper);
        var bundles=Map.of(1L,new TemplateHtmlRenderer.PageBundle(p,elements,actions));
        String html=renderer.render(p,bundles,Map.of(1L,p.getName(),2L,"目标页",3L,"第三页"),p.getName(),null);
        assertEquals(html,renderer.render(p,bundles,Map.of(1L,p.getName(),2L,"目标页",3L,"第三页"),p.getName(),null));
        assertEquals(before,mapper.writeValueAsString(elements));
        var ids=Pattern.compile("data-wf-element-id=\"(\\d+)\"").matcher(html);var seen=new HashSet<String>();
        while(ids.find())assertTrue(seen.add(ids.group(1)),ids.group(1));
        assertTrue(html.contains("name=\"wf-template-revision\""));return html;
    }
    private static String tag(String html,long id){
        var tag=Pattern.compile("<div[^>]*data-wf-element-id=\""+id+"\"[^>]*>").matcher(html);assertTrue(tag.find());return tag.group();
    }
    private static long count(String html,String cls){
        return Pattern.compile("class=['\"](?:[^'\"]*\\s)?"+Pattern.quote(cls)+"(?:\\s[^'\"]*)?['\"]").matcher(html).results().count();
    }
    private static List<String> inputs(String html){return Pattern.compile("<input class='wf-radio-input'[^>]*>").matcher(html).results().map(x -> x.group()).toList();}
    private static List<Element> product(){
        return new ArrayList<>(List.of(e(1,"container","商品卡片",20,150,108,150),e(2,"image","",28,158,92,88),
                e(3,"text","角色",28,250,80,18),e(4,"text","¥23.8",28,276,80,16),e(5,"button","购买",24,310,100,26)));
    }

    @ParameterizedTest
    @ValueSource(strings={"更多","右箭头",">"})
    void moreArrowIsDrawnOnceEvenWhenRecognizedAsMoreOrLiteralText(String arrowLabel) throws Exception {
        List<Element> elements=product();elements.add(e(10,"text","更多",300,110,30,18));
        elements.add(e(11,arrowLabel.equals(">")?"text":"icon",arrowLabel,338,110,12,16));
        Interaction action=new Interaction();action.setElementId(11L);action.setActionType("navigate");action.setTargetPageId(2L);
        String html=render(elements,Map.of(11L,List.of(action)));
        assertEquals(1,count(html,"wf-more-entry"));assertEquals(1,count(html,"wf-more-arrow"));
        assertEquals(1,Pattern.compile("data-icon='next'").matcher(html).results().count());
        assertTrue(html.contains("data-wf-element-id=\"11\" data-nav=\"目标页\""));
        assertFalse(Pattern.compile("class=\"wf-ic wf-el\"[^>]*data-wf-element-id=\"11\"").matcher(html).find());
    }

    @Test
    void inlineChevronAndSeparateArrowDoNotProduceDuplicateGlyphs() throws Exception {
        List<Element> elements=product();elements.add(e(10,"button","更多 >",300,110,49,20));
        elements.add(e(11,"icon","更多",338,110,12,16));elements.add(e(12,"text",">",338,110,12,16));
        String html=render(elements,Map.of());assertEquals(1,count(html,"wf-more-arrow"));
        assertEquals(1,Pattern.compile("data-icon='next'").matcher(html).results().count());
        assertFalse(html.contains("更多 &gt;"));
    }

    @Test
    void narrowTallChoiceTabsStackWhileNormalWideTabsStayHorizontal() throws Exception {
        String html=render(List.of(e(1,"tabs","钻石抽奖/典藏抽奖",260,174,79,68),e(2,"tabs","扭蛋/收集",122,44,143,35)),Map.of());
        assertTrue(tag(html,1).contains("wf-tabs-vertical"));assertFalse(tag(html,2).contains("wf-tabs-vertical"));
        assertTrue(html.contains("钻石抽奖"));assertTrue(html.contains("典藏抽奖"));
    }

    @Test
    void radioOptionsRenderNativeExclusiveControlsWithOneDefaultSelection() throws Exception {
        String html=render(List.of(e(100,"container","抽奖选项",262,417,74,101),e(101,"radio","单抽",270,424,56,22),
                e(102,"radio","10抽",270,474,47,19),e(103,"text","300钻石/次",269,449,60,15),e(104,"text","2700钻石",270,497,60,15)),Map.of());
        var controls=inputs(html);assertEquals(2,controls.size());assertTrue(controls.get(0).contains(" checked"));assertFalse(controls.get(1).contains(" checked"));
        for(String input:controls)assertTrue(input.contains("name='wf-radio-p1-g100'"));
        assertEquals(2,count(html,"wf-radio-box"));assertEquals(0,count(html,"wf-ck"));
        assertTrue(tag(html,101).contains("wf-radio"));assertTrue(tag(html,102).contains("wf-radio"));
        assertTrue(html.contains("300钻石/次"));assertTrue(html.contains("2700钻石"));
    }

    @Test
    void explicitSelectionOverridesFirstOptionAndSeparatePanelsHaveSeparateGroups() throws Exception {
        Element first=e(101,"radio","单抽",270,424,56,22);first.setStyle("{\"checked\":false}");
        Element second=e(102,"radio","10抽",270,474,47,19);second.setStyle("{\"checked\":true}");
        String html=render(List.of(e(100,"container","抽奖选项",262,417,74,101),first,second,
                e(200,"container","其他选项",20,417,90,101),e(201,"radio","标准",25,424,70,22)),Map.of());
        var controls=inputs(html);assertEquals(3,controls.size());
        assertTrue(controls.stream().anyMatch(x -> x.contains("g100'") && x.contains("aria-label='10抽'") && x.contains(" checked")));
        assertTrue(controls.stream().noneMatch(x -> x.contains("aria-label='单抽'") && x.contains(" checked")));
        assertTrue(controls.stream().anyMatch(x -> x.contains("g200'") && x.contains(" checked")));
    }

    @Test
    void compactIconsReserveSpaceAboveTheirOverlappingCaptionsWithoutChangingHitAreaOrAction() throws Exception {
        Element prize=e(11,"icon","奖池",262,133,32,33),caption=e(12,"text","奖池",267,153,21,11);
        prize.setGroupKey("prize");caption.setGroupKey("prize");
        Element bag=e(13,"icon","背包",302,133,32,33),bagCaption=e(14,"text","背包",308,153,21,11);
        bag.setGroupKey("bag");bagCaption.setGroupKey("bag");
        Interaction action=new Interaction();action.setElementId(13L);action.setActionType("navigate");action.setTargetPageId(2L);
        String html=render(List.of(prize,caption,bag,bagCaption),Map.of(13L,List.of(action)));
        assertEquals(2,count(html,"wf-captioned-icon"));
        for(long id:List.of(11L,13L)){
            assertTrue(tag(html,id).contains("top:133px;width:32px;height:33px;"));
            var graphic=Pattern.compile("<div[^>]*data-wf-element-id=\""+id+"\"[^>]*>(.*?)</div>",Pattern.DOTALL).matcher(html);
            assertTrue(graphic.find());assertTrue(graphic.group(1).contains("height:16px;"));
            assertTrue(graphic.group(1).contains("width='16' height='16'"));
            assertFalse(tag(html,id).contains("border-radius:50%"));
        }
        assertTrue(html.contains("data-icon='gift'"));assertTrue(html.contains("data-icon='backpack'"));
        assertTrue(tag(html,13).contains("data-nav=\"目标页\""));
        assertTrue(tag(html,12).contains("top:153px"));assertTrue(tag(html,14).contains("top:153px"));
    }

    @Test
    void iconsWithSeparateOrSideCaptionsKeepTheirOriginalLayout() throws Exception {
        String html=render(List.of(e(11,"icon","背包",20,130,32,33),e(12,"text","背包",25,167,21,11),
                e(13,"icon","奖池",200,130,32,33),e(14,"text","奖池",237,142,30,12)),Map.of());
        assertEquals(0,count(html,"wf-captioned-icon"));
        assertFalse(tag(html,11).contains("align-items:flex-start"));assertFalse(tag(html,13).contains("align-items:flex-start"));
    }

    @ParameterizedTest
    @ValueSource(strings={"back","close"})
    void backgroundReturnIsPreservedAsTransparentHitArea(String actionType) throws Exception {
        Interaction action=new Interaction();action.setElementId(1L);action.setActionType(actionType);
        String html=render(List.of(e(1,"background","背景",0,0,375,833),e(2,"text","抽奖结果",80,180,200,30)),Map.of(1L,List.of(action)));
        assertEquals(1,count(html,"wf-page-dismiss"));
        assertTrue(tag(html,1).contains("data-action=\""+actionType+"\""));
        assertTrue(tag(html,1).contains("background:transparent"));assertTrue(tag(html,1).contains("z-index:1"));
        assertFalse(html.contains(">背景<"));assertTrue(html.contains("抽奖结果"));
        assertEquals(0,count(render(List.of(e(1,"background","背景",0,0,375,833)),Map.of()),"wf-page-dismiss"));
    }

    @Test
    void tabSegmentsHaveStableIndependentIdentitiesAndKeepManualBindingsAfterRegeneration() throws Exception {
        Element tabs=e(10,"tabs","扭蛋/收集",120,44,142,36);
        Element left=e(101,"button","扭蛋",123,47,68,30),right=e(102,"button","收集",191,47,68,30);
        Interaction a=new Interaction();a.setElementId(101L);a.setActionType("navigate");a.setTargetPageId(2L);a.setParams("{\"domUid\":\"wf-tab-p1-e10-0\"}");
        Interaction b=new Interaction();b.setElementId(102L);b.setActionType("navigate");b.setTargetPageId(3L);b.setParams("{\"domUid\":\"wf-tab-p1-e10-1\"}");
        String html=render(List.of(tabs,left,right),Map.of(101L,List.of(a),102L,List.of(b)));
        assertTrue(html.contains("data-wf-tab-segment='0' data-wf-uid='wf-tab-p1-e10-0' data-wf-element-id=\"101\" data-nav=\"目标页\""));
        assertTrue(html.contains("data-wf-tab-segment='1' data-wf-uid='wf-tab-p1-e10-1' data-wf-element-id=\"102\" data-nav=\"第三页\""));
        assertEquals(1,count(html,"wf-tabs"));assertEquals(0,count(html,"wf-btn"));
        assertEquals(2,Pattern.compile("data-wf-tab-segment=").matcher(html).results().count());
    }

    @Test
    void explicitBackgroundReturnKeepsVisibleArtworkAndCardStylesByteForByte() throws Exception {
        List<Element> original=product();String before=render(original,Map.of());
        Element setting=e(90,"background","页面空白区域",0,0,375,833);setting.setStyle(PageBackgroundAction.STYLE);setting.setCreatedBy("manual");
        Interaction back=new Interaction();back.setElementId(90L);back.setActionType("back");back.setSource("user");
        List<Element> configured=new ArrayList<>(original);configured.add(setting);
        String after=render(configured,Map.of(90L,List.of(back)));
        assertEquals(before,after.replaceAll("<div class=\"wf-page-dismiss wf-el\"[^>]*></div>",""));
        back.setActionType("none");assertEquals(before,render(configured,Map.of(90L,List.of(back))));
    }

    @Test
    void explicitDisabledSettingSuppressesAiInferredBackgroundReturn() throws Exception {
        Element ai=e(89,"background","",0,0,375,833),setting=e(90,"background","页面空白区域",0,0,375,833);
        setting.setStyle(PageBackgroundAction.STYLE);setting.setCreatedBy("manual");
        Interaction back=new Interaction();back.setElementId(89L);back.setActionType("back");
        Interaction disabled=new Interaction();disabled.setElementId(90L);disabled.setActionType("none");
        String html=render(List.of(ai,setting,e(10,"text","魔女",150,450,75,20)),Map.of(89L,List.of(back),90L,List.of(disabled)));
        assertEquals(0,count(html,"wf-page-dismiss"));assertTrue(html.contains("魔女"));
    }
}
