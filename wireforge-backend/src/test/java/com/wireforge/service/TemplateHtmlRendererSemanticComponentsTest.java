package com.wireforge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wireforge.entity.Element;
import com.wireforge.entity.Page;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class TemplateHtmlRendererSemanticComponentsTest {
    private static Element element(long id, String type, String label, double x, double y, double w, double h) {
        Element e=new Element();e.setId(id);e.setPageId(1L);e.setType(type);e.setLabel(label);
        e.setPositionX(x);e.setPositionY(y);e.setWidth(w);e.setHeight(h);return e;
    }

    private static String render(List<Element> elements) throws Exception {
        Page page=new Page();page.setId(1L);page.setName("宠物家园首页");page.setCanvasWidth(375);page.setCanvasHeight(833);
        ObjectMapper mapper=new ObjectMapper();String before=mapper.writeValueAsString(elements);
        var renderer=new TemplateHtmlRenderer(new DesignColorSampler(),mapper);
        var bundle=new TemplateHtmlRenderer.PageBundle(page,elements,Map.of());
        String html=renderer.render(page,Map.of(1L,bundle),Map.of(1L,page.getName()),page.getName(),null);
        assertEquals(before,mapper.writeValueAsString(elements),"Rendering must leave recognition coordinates and grouping intact");
        return html;
    }

    private static List<String> styles(String html, String cssClass) {
        var matcher=Pattern.compile("<div class=\"[^\"]*\\b"+Pattern.quote(cssClass)+"\\b[^\"]*\"[^>]* style=\"([^\"]*)\">").matcher(html);
        List<String> result=new ArrayList<>();while(matcher.find())result.add(matcher.group(1));return result;
    }

    private static double px(String style, String name) {
        var matcher=Pattern.compile("(?:^|;)"+Pattern.quote(name)+":([0-9.]+)px;").matcher(style);
        assertTrue(matcher.find(),name+" absent from "+style);return Double.parseDouble(matcher.group(1));
    }

    private static boolean hasIcon(String html, String key) {
        return Pattern.compile("data-icon=['\"]"+Pattern.quote(key)+"['\"]").matcher(html).find();
    }

    @ParameterizedTest
    @ValueSource(strings={"button","split","container"})
    void equivalentRecognitionStructuresKeepThreeDistinctAlignedShortcutsAndCompactChat(String structure) throws Exception {
        List<Element> elements=new ArrayList<>();String[] labels={"每日任务","扭蛋机","其他收集"};
        for(int i=0;i<labels.length;i++) {
            long id=10+i*10;double y=570+i*66;
            if(structure.equals("button")) elements.add(element(id,"button",labels[i],304,y,60,54));
            else {
                if(structure.equals("container"))elements.add(element(id,"container",labels[i]+"卡片",304,y,60,54));
                elements.add(element(id+1,structure.equals("container")?"image":"icon","",316,y+2,30,25));
                elements.add(element(id+2,"text",labels[i],304,y+33,60,14));
            }
            if(i==0)elements.add(element(id+3,"badge","领奖励",329,y-3,33,12));
            if(i==2)elements.add(element(id+3,"progress","10/18",315,y+19,36,12));
        }
        elements.add(element(50,"container","收集提示",190,690,150,40));
        elements.add(element(51,"text","获得1 个新装扮",200,700,116,18));
        String html=render(elements);List<String> shortcuts=styles(html,"wf-shortcut");
        assertEquals(3,shortcuts.size());
        for(String icon:List.of("task","ticket","collection"))assertTrue(hasIcon(html,icon),icon);
        for(String style:shortcuts) {
            assertEquals(px(shortcuts.get(0),"left"),px(style,"left"));
            assertEquals(60,px(style,"width"));assertEquals(54,px(style,"height"));
        }
        assertEquals(px(shortcuts.get(1),"top")-px(shortcuts.get(0),"top"),px(shortcuts.get(2),"top")-px(shortcuts.get(1),"top"));
        assertTrue(html.contains("10/18"));assertTrue(html.contains("领奖励"));
        assertEquals(0,styles(html,"wf-image-placeholder").size());
        assertEquals(0,styles(html,"wf-card").size(),"Shortcut and chat container backgrounds must be absorbed");
        List<String> messages=styles(html,"wf-message");assertEquals(1,messages.size());
        String message=messages.get(0);assertEquals(24,px(message,"height"));assertTrue(px(message,"width")<=120);
        assertTrue(message.contains("pointer-events:auto"));
        assertTrue(px(message,"left")+px(message,"width")+6<=px(shortcuts.get(2),"left"),"Chat must sit beside the last shortcut, leaving row spacing unchanged");
    }

    @ParameterizedTest
    @CsvSource({"蓝钻,gem,icon","闪电,energy,icon","小光点,reward,image"})
    void rewardAliasesUseRoundPresetAndAbsorbTheOuterRewardCard(String name, String key, String type) throws Exception {
        Element icon=element(3,type,name,126,125,22,22);icon.setAssetId("icon-camera");
        List<Element> elements=new ArrayList<>(List.of(
                element(1,"container","下载奖励",110,110,54,86),
                element(2,"effect","",111,111,52,52),icon,
                element(4,"text","+88",126,166,22,14),
                element(5,"text","下载完成",110,174,54,14),
                element(6,"text","领取奖励",110,188,54,14)));
        if(!key.equals("reward"))elements.add(element(7,"icon",name,210,55,18,18));
        else {
            elements.removeIf(e -> e.getId().equals(5L) || e.getId().equals(6L));
            elements.add(element(5,"text","下载完成\n领取奖励",110,174,54,28));
        }
        String html=render(elements);
        assertEquals(1,styles(html,"wf-reward-entry").size());
        assertEquals(0,styles(html,"wf-card").size(),"The reward wrapper cannot survive as a rectangular card");
        assertTrue(html.contains("width:36px;height:36px;border-radius:50%"));
        assertTrue(hasIcon(html,key));
        assertFalse(hasIcon(html,"entry"));
        for(String copy:List.of("+88","下载完成","领取奖励"))assertTrue(html.contains(copy));
    }

    @Test
    void smallPromotionalPicturesWithOverlappingLabelsGetPresetIconsRatherThanPhotoFrames() throws Exception {
        List<Element> elements=new ArrayList<>();
        for(int i=0;i<3;i++) {
            elements.add(element(10+i*2,"image","",16,106+i*60,42,44));
            elements.add(element(11+i*2,"badge",i==0?"上新低价":"上新耀宝",15,135+i*60,46,15));
        }
        String html=render(elements);
        assertEquals(3,styles(html,"wf-promotion-entry").size());
        assertEquals(0,styles(html,"wf-image-placeholder").size());
        assertTrue(hasIcon(html,"sale"));assertTrue(hasIcon(html,"star"));
    }

    @Test
    void unrelatedContentStillUsesAConservativeImagePlaceholder() throws Exception {
        String html=render(new ArrayList<>(List.of(element(1,"image","",100,300,60,60),element(2,"text","未知装饰",100,365,80,18))));
        assertEquals(1,styles(html,"wf-image-placeholder").size());
        assertEquals(0,styles(html,"wf-shortcut").size());
    }

    @ParameterizedTest
    @CsvSource({"image,51,55,36,text","image,44,47,36,text","image,47,50,34,text","icon,22,22,26,badge","image,50,50,34,progress"})
    void rewardNumbersInsideOrBelowUnlabelledPicturesAlwaysGetAPreset(String type, double width, double height, double offset, String numberType) throws Exception {
        Element picture=element(1,type,"",120,110,width,height);picture.setAssetId("product-03");
        String html=render(new ArrayList<>(List.of(picture,
                element(2,numberType,"+88",120+width/2-11,110+offset,22,15),
                element(3,"text","下载完成\n领取奖励",116,110+height+3,56,29))));
        List<String> rewards=styles(html,"wf-reward-entry");assertEquals(1,rewards.size());
        assertTrue(px(rewards.get(0),"width")>=36);
        assertTrue(hasIcon(html,"reward"));assertFalse(hasIcon(html,"entry"));
        assertEquals(0,styles(html,"wf-image-placeholder").size());
        assertTrue(html.contains("width:36px;height:36px;border-radius:50%"));
    }

    @ParameterizedTest
    @ValueSource(strings={"+88","+88\n送个礼物","+88\n下载完成\n领取奖励"})
    void aWholeRewardRecognizedAsOneButtonAlsoHasAPreset(String label) throws Exception {
        String html=render(new ArrayList<>(List.of(element(1,"button",label,110,110,56,90))));
        assertEquals(1,styles(html,"wf-entry").size());
        assertFalse(hasIcon(html,"entry"));assertTrue(hasIcon(html,"reward")||hasIcon(html,"gift"));
    }

    @Test
    void aDuplicateBubblePictureAndInnerIconBecomeOneReward() throws Exception {
        String html=render(new ArrayList<>(List.of(
                element(1,"image","",110,110,52,55),element(2,"icon","蓝钻",125,120,22,22),
                element(3,"text","+88",125,146,22,15))));
        assertEquals(1,styles(html,"wf-reward-entry").size());
        assertEquals(0,styles(html,"wf-image-placeholder").size());assertTrue(hasIcon(html,"gem"));
    }

    @Test
    void unnamedSmallCurrencyImagesUseACurrencyPreset() throws Exception {
        String html=render(new ArrayList<>(List.of(element(1,"container","货币栏",196,49,76,27),
                element(2,"image","",207,54,17,18),element(3,"text","4939",232,57,30,15))));
        assertTrue(hasIcon(html,"currency"));assertEquals(0,styles(html,"wf-image-placeholder").size());
        assertTrue(html.contains("4939"));
    }
}
