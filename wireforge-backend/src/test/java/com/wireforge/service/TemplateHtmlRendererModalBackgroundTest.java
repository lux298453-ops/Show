package com.wireforge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wireforge.entity.Element;
import com.wireforge.entity.Interaction;
import com.wireforge.entity.Page;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class TemplateHtmlRendererModalBackgroundTest {
    @TempDir Path temp;
    private static Element e(long id,String type,String text,double x,double y,double w,double h) {
        Element e=new Element();e.setId(id);e.setPageId(1L);e.setType(type);e.setLabel(text);
        e.setPositionX(x);e.setPositionY(y);e.setWidth(w);e.setHeight(h);return e;
    }
    private Page page(long id,String name,String image) {
        Page p=new Page();p.setId(id);p.setName(name);p.setBackgroundImage(image);p.setCanvasWidth(375);p.setCanvasHeight(833);return p;
    }
    private String artwork(String color) throws Exception {
        BufferedImage image=new BufferedImage(375,833,BufferedImage.TYPE_INT_RGB);
        var g=image.createGraphics();g.setColor(Color.decode(color));g.fillRect(0,0,375,833);
        g.setColor(Color.WHITE);g.fillRoundRect(25,300,325,230,20,20);
        g.setColor(Color.decode("#FF6B16"));g.fillRoundRect(80,465,215,38,20,20);g.dispose();
        Path file=temp.resolve("source-"+color.substring(1)+".png");ImageIO.write(image,"png",file.toFile());return file.toString();
    }
    private List<Element> content() {
        return List.of(e(10,"container","弹窗卡片",25,300,325,230),e(11,"text","提示内容 158",60,340,200,30),
                e(12,"button","领取",80,465,215,38),e(13,"button","×",170,570,34,34));
    }
    private String render(Page p,List<Element> elements) throws Exception {
        var mapper=new ObjectMapper();String before=mapper.writeValueAsString(elements);
        Interaction action=new Interaction();action.setElementId(12L);action.setActionType("navigate");action.setTargetPageId(2L);
        var bundles=Map.of(p.getId(),new TemplateHtmlRenderer.PageBundle(p,elements,Map.of(12L,List.of(action))));
        var renderer=new TemplateHtmlRenderer(new DesignColorSampler(),mapper);
        String html=renderer.render(p,bundles,Map.of(p.getId(),p.getName(),2L,"目标页"),p.getName(),null);
        assertEquals(before,mapper.writeValueAsString(elements));return html;
    }

    @ParameterizedTest @ValueSource(strings={"版本更新提醒弹窗","探险与属性说明弹窗","宝箱弹窗","多张横滑弹窗","横滑活动特价弹窗","单张活动弹窗"})
    void independentPopupUsesNeutralBackingInsteadOfSampledSceneColor(String name) throws Exception {
        String html=render(page(1,name,artwork("#3D1908")),content());
        assertTrue(html.contains("body{width:375px;height:833px;position:relative;background:#EEF0F3;"));
        assertTrue(html.contains("background:#FFFFFF;border-radius:"));
        assertTrue(html.contains("提示内容 158"));assertTrue(html.contains("data-wf-element-id=\"12\" data-nav=\"目标页\""));
        assertFalse(html.contains("wf-statusbar wf-el"));
    }
    @Test void missingOrDifferentlyColoredArtworkDoesNotChangePopupBacking() throws Exception {
        for(String image:java.util.Arrays.asList(null,artwork("#241811"),artwork("#112244"),artwork("#FFFFFF"))) {
            String html=render(page(1,"系统提醒",image),content());
            assertTrue(html.contains("position:relative;background:#EEF0F3;"));
        }
    }
    @Test void ordinaryPageKeepsWhiteBackgroundEvenWhenItsDesignIsDark() throws Exception {
        String html=render(page(1,"家园",artwork("#3D1908")),content());
        assertTrue(html.contains("body{width:375px;height:833px;position:relative;background:#FFFFFF;"));
    }
    @Test void descriptionPopupStaysAnOverlayAfterItsBackgroundBecomesLight() throws Exception {
        String html=render(page(1,"属性说明",null),content());
        assertFalse(html.contains("wf-statusbar wf-el"));assertFalse(html.contains("wf-canonical-nav wf-el"));
    }
    @Test void openedPopupRetainsHostPageAndUsesOneTransparentNeutralMask() throws Exception {
        var renderer=new TemplateHtmlRenderer(new DesignColorSampler(),new ObjectMapper());
        Page host=page(1,"家园",null),popup=page(2,"系统提醒",artwork("#3D1908"));
        Element open=e(1,"button","打开提示",10,100,100,40);
        Interaction action=new Interaction();action.setElementId(1L);action.setActionType("modal");action.setTargetPageId(2L);
        var bundles=Map.of(1L,new TemplateHtmlRenderer.PageBundle(host,List.of(open),Map.of(1L,List.of(action))),
                2L,new TemplateHtmlRenderer.PageBundle(popup,content(),Map.of()));
        String html=renderer.render(host,bundles,Map.of(1L,"家园",2L,"系统提醒"),"家园",null);
        assertTrue(html.contains("position:relative;background:#FFFFFF;"));assertTrue(html.contains("打开提示"));
        assertTrue(html.contains(".wf-modal{position:absolute;inset:0;z-index:999;display:none;background:rgba(0,0,0,.35);"));
        assertTrue(html.contains("<div class=\"wf-modal\" id=\"wf-modal-2\">"));
        String overlay=html.substring(html.indexOf("<div class=\"wf-modal\" id=\"wf-modal-2\">"));
        assertTrue(overlay.contains("wf-modal-dismiss\" data-action=\"close\""));
        assertTrue(overlay.contains("提示内容 158"));assertFalse(overlay.contains("background:#EEF0F3"));
    }
}
