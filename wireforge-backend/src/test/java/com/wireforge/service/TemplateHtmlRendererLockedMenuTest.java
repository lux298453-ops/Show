package com.wireforge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wireforge.entity.Element;
import com.wireforge.entity.Interaction;
import com.wireforge.entity.Page;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class TemplateHtmlRendererLockedMenuTest {
    private static Element e(long id,String type,String label,double x,double y,double w,double h){
        Element e=new Element();e.setId(id);e.setPageId(1L);e.setType(type);e.setLabel(label);e.setPositionX(x);e.setPositionY(y);e.setWidth(w);e.setHeight(h);return e;
    }
    private static List<Element> menu(boolean locked){
        List<Element> items=new ArrayList<>();items.add(e(1,"container","装扮选项面板",200,320,162,463));
        for(int i=0;i<4;i++)items.add(e(10+i,"image","",212+i*30,360,21,38));
        if(locked)items.add(e(20,"icon","锁定",247,373,10,15));
        items.add(e(30,"icon","回到桌面",212,416,12,12));items.add(e(31,"text","回到桌面",230,416,54,15));
        return items;
    }
    private static String render(List<Element> items,Map<Long,List<Interaction>> actions) throws Exception {
        Page p=new Page();p.setId(1L);p.setName("预览菜单");p.setCanvasWidth(375);p.setCanvasHeight(833);
        ObjectMapper mapper=new ObjectMapper();String snapshot=mapper.writeValueAsString(items);
        var renderer=new TemplateHtmlRenderer(new DesignColorSampler(),mapper);
        var bundles=Map.of(1L,new TemplateHtmlRenderer.PageBundle(p,items,actions));var names=Map.of(1L,p.getName(),2L,"目标页",3L,"锁说明");
        String html=renderer.render(p,bundles,names,p.getName(),null);
        assertEquals(html,renderer.render(p,bundles,names,p.getName(),null));assertEquals(snapshot,mapper.writeValueAsString(items));
        var ids=Pattern.compile("data-wf-element-id=\"(\\d+)\"").matcher(html);var seen=new HashSet<String>();
        while(ids.find())assertTrue(seen.add(ids.group(1)),ids.group(1));
        return html;
    }
    private static String node(String html,long id){
        var match=Pattern.compile("<div[^>]*data-wf-element-id=\""+id+"\"[^>]*>.*?</div>",Pattern.DOTALL).matcher(html);
        assertTrue(match.find(),"Missing "+id);return match.group();
    }
    private static String icon(String node){
        var match=Pattern.compile("<svg data-icon='([^']+)'[^>]*width='([^']+)' height='([^']+)'").matcher(node);
        assertTrue(match.find());return match.group(1)+":"+match.group(2)+"x"+match.group(3);
    }

    @Test void lockedThumbnailUsesTheSameBaseAsItsPeersInsteadOfTheFollowingSectionTitle() throws Exception {
        String html=render(menu(true),Map.of());
        String base=icon(node(html,10));assertTrue(base.startsWith("image:"));
        for(long id:List.of(11L,12L,13L))assertEquals(base,icon(node(html,id)));
        assertTrue(icon(node(html,20)).startsWith("lock:"));assertTrue(icon(node(html,30)).startsWith("desktop:"));
        assertEquals(1,Pattern.compile("data-icon='desktop'").matcher(html).results().count());
        assertTrue(node(html,20).contains("left:247px;top:373px;width:10px;height:15px;"));
    }

    @Test void lockIsAnIndependentOverlayAndBothSourceBindingsArePreserved() throws Exception {
        Interaction image=new Interaction();image.setElementId(11L);image.setActionType("navigate");image.setTargetPageId(2L);
        Interaction lock=new Interaction();lock.setElementId(20L);lock.setActionType("navigate");lock.setTargetPageId(3L);
        String html=render(menu(true),Map.of(11L,List.of(image),20L,List.of(lock)));
        assertTrue(node(html,11).contains("data-nav=\"目标页\""));assertTrue(node(html,20).contains("data-nav=\"锁说明\""));
        String withoutLock=render(menu(false),Map.of());
        for(long id:List.of(10L,11L,12L,13L))assertEquals(icon(node(withoutLock,id)),icon(node(html,id)));
    }

    @Test void explicitlyNamedIconsAreStillHonoredWithinAPreviewRow() throws Exception {
        List<Element> items=menu(true);items.stream().filter(e->e.getId()==11).findFirst().orElseThrow().setLabel("钻石");
        String html=render(items,Map.of());assertTrue(icon(node(html,11)).startsWith("gem:"));assertTrue(icon(node(html,20)).startsWith("lock:"));
    }

    @Test void standaloneImageWithRealCaptionCanStillUseItsSemanticPreset() throws Exception {
        String html=render(List.of(e(10,"image","",230,360,21,38),e(11,"text","礼物",229,404,23,15)),Map.of());
        assertTrue(icon(node(html,10)).startsWith("gift:"));
    }
}
