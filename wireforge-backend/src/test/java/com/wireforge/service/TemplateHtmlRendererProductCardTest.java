package com.wireforge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wireforge.entity.Element;
import com.wireforge.entity.Interaction;
import com.wireforge.entity.Page;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class TemplateHtmlRendererProductCardTest {
    private static Element element(long id, String type, String label, double x, double y, double w, double h) {
        Element e=new Element();e.setId(id);e.setType(type);e.setLabel(label);e.setPageId(1L);
        e.setPositionX(x);e.setPositionY(y);e.setWidth(w);e.setHeight(h);return e;
    }

    private static List<Element> product() {
        return new ArrayList<>(List.of(
                element(1,"container","",12,150,108,163),
                element(2,"badge","沧元图",20,157,40,14),
                element(3,"badge","典藏",78,147,26,14),
                element(4,"text","幽筱",20,284,44,16),
                element(5,"text","券后价",20,299,30,14),
                element(6,"text","¥23.8",54,297,48,16),
                element(7,"button","购买",30,322,72,28)));
    }

    private static String render(List<Element> elements) throws Exception {
        Page page=new Page();page.setId(1L);page.setName("商城");page.setCanvasWidth(375);page.setCanvasHeight(833);
        Interaction action=new Interaction();action.setElementId(7L);action.setActionType("navigate");action.setTargetPageId(2L);
        Interaction moreAction=new Interaction();moreAction.setElementId(10L);moreAction.setActionType("navigate");moreAction.setTargetPageId(2L);
        Interaction arrowAction=new Interaction();arrowAction.setElementId(11L);arrowAction.setActionType("back");
        ObjectMapper mapper=new ObjectMapper();String before=mapper.writeValueAsString(elements);
        var renderer=new TemplateHtmlRenderer(new DesignColorSampler(),mapper);
        var bundle=new TemplateHtmlRenderer.PageBundle(page,elements,Map.of(7L,List.of(action),10L,List.of(moreAction),11L,List.of(arrowAction)));
        Map<Long,TemplateHtmlRenderer.PageBundle> bundles=Map.of(1L,bundle);Map<Long,String> names=Map.of(1L,"商城",2L,"商品详情");
        String html=renderer.render(page,bundles,names,"商城",null);
        assertEquals(html,renderer.render(page,bundles,names,"商城",null),"Preset colors and slot order must be deterministic");
        assertEquals(before,mapper.writeValueAsString(elements),"Source positions and action IDs must remain unchanged");
        var ids=Pattern.compile("data-wf-element-id=\"(\\d+)\"").matcher(html);
        var seen=new java.util.HashSet<String>();while(ids.find())assertTrue(seen.add(ids.group(1)),"Duplicate binding "+ids.group(1));
        return html;
    }

    private static String block(String html, String cssClass) {
        var start=Pattern.compile("<div class=['\"][^'\"]*\\b"+cssClass+"\\b[^'\"]*['\"][^>]*>").matcher(html);
        assertTrue(start.find(),"Missing "+cssClass);
        var tags=Pattern.compile("</?div\\b[^>]*>").matcher(html);tags.region(start.start(),html.length());
        int depth=0;while(tags.find()) {depth+=tags.group().startsWith("</")?-1:1;if(depth==0)return html.substring(start.start(),tags.end());}
        fail("Unclosed "+cssClass);return "";
    }

    private static String opening(String html) {return html.substring(0,html.indexOf('>')+1);}

    @Test
    void pricedGenericContainerUsesTwoSectionsAndKeepsPurchaseActionOutside() throws Exception {
        String html=render(product()),card=block(html,"wf-product-card"),hero=block(card,"wf-card-hero"),info=block(card,"wf-card-info");
        assertTrue(opening(card).contains("height:156px"));assertTrue(opening(card).contains("border-radius:16px"));
        assertTrue(opening(hero).contains("height:96px"));assertTrue(opening(info).contains("height:60px"));
        assertTrue(hero.contains("wf-product-art"));assertTrue(hero.contains("data-icon='image'"));
        assertTrue(hero.contains("典藏"));assertFalse(card.contains("gradient"));assertTrue(card.contains("wf-image-placeholder"));
        assertTrue(block(info,"wf-card-identity").contains("幽筱"));assertTrue(block(info,"wf-card-identity").contains("沧元图"));
        assertTrue(block(info,"wf-card-price-row").contains("券后价"));assertTrue(block(info,"wf-card-price-row").contains("¥23.8"));
        assertTrue(opening(block(info,"wf-card-identity")).contains("justify-content:space-between"));
        assertTrue(opening(block(info,"wf-card-price-row")).contains("justify-content:space-between"));
        assertFalse(info.contains("position:absolute"));assertFalse(card.contains("data-wf-element-id=\"7\""));
        assertTrue(html.contains("data-wf-element-id=\"7\" data-nav=\"商品详情\""));
        var purchase=Pattern.compile("<div[^>]*data-wf-element-id=\"7\"[^>]*>").matcher(html);
        assertTrue(purchase.find());assertTrue(purchase.group().contains("top:315px"));
    }

    @ParameterizedTest
    @ValueSource(strings={"combined","split","text-metadata"})
    void equivalentRecognitionTokensKeepNamesSeriesAndAmounts(String mode) throws Exception {
        List<Element> elements=product();
        if (mode.equals("combined")) {
            elements.removeIf(e -> e.getId().equals(5L));elements.get(4).setLabel("券后价 ¥23.8");
        } else if (mode.equals("split")) {
            elements.get(5).setLabel("¥");elements.get(5).setWidth(8d);
            elements.add(element(8,"text","23.8",64,297,40,16));
        } else elements.get(1).setType("text");
        String card=block(render(elements),"wf-product-card"),info=block(card,"wf-card-info");
        assertTrue(info.contains("幽筱"));assertTrue(info.contains("沧元图"));assertTrue(info.contains("券后价"));
        assertTrue(info.contains("23.8"));assertTrue(info.contains("¥"));assertFalse(info.contains("position:absolute"));
    }

    @Test
    void aSingleNameNearTheTopIsNotMistakenForASeries() throws Exception {
        List<Element> elements=product();elements.removeIf(e -> e.getId().equals(2L));elements.get(2).setPositionY(170d);
        String info=block(render(elements),"wf-card-info");
        assertTrue(info.contains("wf-product-name"));assertFalse(info.contains("wf-product-series"));
    }

    @Test
    void presaleDoesNotInventAnAmountAndRetainsItsIndependentButton() throws Exception {
        String html=render(new ArrayList<>(List.of(element(1,"container","预售商品卡片",12,150,108,163),
                element(2,"badge","????",20,157,40,14),element(4,"text","即将上线",20,284,80,16),
                element(7,"button","一元预定",30,322,72,28))));
        String card=block(html,"wf-product-card");
        assertTrue(card.contains("即将上线"));assertTrue(card.contains("????"));assertTrue(card.contains("预售"));
        assertFalse(card.contains("wf-product-price"));assertFalse(card.contains("¥"));
        assertFalse(card.contains("一元预定"));assertTrue(html.contains("一元预定"));
    }

    @Test
    void clippedCardsUseFullWidthAndLowerRowKeepsTheSameHeroHeight() throws Exception {
        List<Element> elements=product();
        elements.add(element(20,"container","商品卡片",350,150,25,163));
        elements.add(element(21,"badge","灵动",351,157,20,14));
        elements.add(element(22,"container","商品卡片",12,669,108,98));
        elements.add(element(23,"badge","典藏",20,675,25,14));
        String html=render(elements);
        var roots=Pattern.compile("<div class=\"wf-content-card wf-product-card wf-el\"[^>]*>").matcher(html);
        int count=0;while(roots.find()) {count++;assertTrue(roots.group().contains("width:108px;height:156px"));}
        assertEquals(3,count);
        var heroes=Pattern.compile("<div class='wf-card-hero'[^>]*>").matcher(html);count=0;
        while(heroes.find()) {count++;assertTrue(heroes.group().contains("height:96px"));}assertEquals(3,count);
    }

    @Test
    void collectionCardsKeepTheExistingIllustratedLayoutAndFragmentCounters() throws Exception {
        String html=render(new ArrayList<>(List.of(element(1,"container","图鉴卡片",12,150,108,180),
                element(2,"image","",20,165,92,120),element(3,"text","刘邦",20,302,70,16),
                element(4,"progress","3/3",50,265,34,18))));
        assertTrue(html.contains("wf-collectible-card"));assertTrue(html.contains("wf-fragment-ready"));
        assertFalse(html.contains("wf-product-card"));assertTrue(html.contains("3/3"));
    }

    @ParameterizedTest
    @ValueSource(strings={"whole","combined","split"})
    void sectionMoreAlwaysShowsTextAndChevronWithOriginalBindings(String recognition) throws Exception {
        List<Element> elements=product();
        elements.add(element(10,recognition.equals("split")?"text":"button","更多",300,114,recognition.equals("split")?28:48,22));
        if (!recognition.equals("whole")) elements.add(element(11,"icon","右箭头",336,117,16,16));
        if (recognition.equals("combined")) elements.add(element(12,"text","更多",302,116,28,18));
        String html=render(elements),entry=block(html,"wf-more-entry");
        assertTrue(entry.contains("wf-more-label"));assertTrue(entry.contains("wf-more-arrow"));assertTrue(entry.contains("data-icon='next'"));
        assertFalse(entry.contains("data-icon='more'"));
        assertTrue(opening(entry).contains("background:none;border:0"));assertTrue(opening(entry).contains("box-shadow:none"));
        assertTrue(opening(entry).contains("data-wf-element-id=\"10\" data-nav=\"商品详情\""));
        if (!recognition.equals("whole")) assertTrue(entry.contains("data-wf-element-id=\"11\" data-action=\"back\""));
        if (recognition.equals("combined")) assertTrue(entry.contains("data-wf-element-id=\"12\""));
    }

    @Test
    void purchasePillUsesDesignColorsAndOnlyPairsWithItsProductCard() throws Exception {
        List<Element> elements=product();
        elements.add(element(8,"text","购买",42,327,28,16));
        elements.add(element(20,"button","购买",250,50,72,28));
        String html=render(elements),pill=block(html,"wf-product-purchase");
        assertTrue(opening(pill).contains("left:30px;top:315px;width:72px;height:28px"));
        assertTrue(opening(pill).contains("background:#F3E5DF;color:#FF6B16;border:0;box-shadow:none;border-radius:999px"));
        assertFalse(pill.contains("gradient"));assertTrue(pill.contains("data-wf-element-id=\"8\""));
        var separate=Pattern.compile("<div[^>]*data-wf-element-id=\"20\"[^>]*>").matcher(html);
        assertTrue(separate.find());assertFalse(separate.group().contains("wf-product-purchase"));
    }

    @Test
    void clippedEmptyPurchasePillKeepsTheSameWidthAndGapAsTheFullRow() throws Exception {
        List<Element> elements=product();
        elements.add(element(20,"container","商品卡片",350,150,25,163));
        elements.add(element(21,"badge","灵动",351,157,20,14));
        elements.add(element(22,"button","",367,322,8,28));
        String html=render(elements);
        var clipped=Pattern.compile("<div[^>]*data-wf-element-id=\"22\"[^>]*>").matcher(html);
        assertTrue(clipped.find());assertTrue(clipped.group().contains("wf-product-purchase"));
        assertTrue(clipped.group().contains("top:315px;width:72px;height:28px"));
    }
}
