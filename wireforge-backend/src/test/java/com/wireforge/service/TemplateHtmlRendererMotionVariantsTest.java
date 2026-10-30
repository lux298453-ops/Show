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

class TemplateHtmlRendererMotionVariantsTest {
    private static Element e(long id,String kind,String label,double x,double y,double w,double h){
        Element e=new Element();e.setId(id);e.setType(kind);e.setLabel(label);e.setPositionX(x);e.setPositionY(y);e.setWidth(w);e.setHeight(h);return e;
    }
    private static Page page(long id,String hash){
        Page p=new Page();p.setId(id);p.setName("动效列表");p.setCanvasWidth(375);p.setCanvasHeight(833);p.setImageHash(hash);return p;
    }
    private static List<Element> cards(long offset,String stateType){
        List<Element> items=new ArrayList<>();
        for(int i=0;i<2;i++){
            long id=offset+i*10;double x=14+i*100;
            items.add(e(id,"image","",x,200,80,127));
            items.add(e(id+1,stateType,stateType.equals("badge")?"":i==0?"灯泡":"隐藏",x-2,196,21,21));
            items.add(e(id+2,"text","左滑：起舞",x,335,80,14));
        }
        return items;
    }
    private static long count(String html,String cls){
        return Pattern.compile("class=['\"](?:[^'\"]*\\s)?"+Pattern.quote(cls)+"(?:\\s[^'\"]*)?['\"]").matcher(html).results().count();
    }
    private static String render(Page p,List<Element> source,Page peer,List<Element> hints,Map<Long,List<Interaction>> actions) throws Exception {
        ObjectMapper mapper=new ObjectMapper();String original=mapper.writeValueAsString(source),originalPeer=mapper.writeValueAsString(hints);
        var renderer=new TemplateHtmlRenderer(new DesignColorSampler(),mapper);
        var bundles=Map.of(p.getId(),new TemplateHtmlRenderer.PageBundle(p,source,actions),peer.getId(),new TemplateHtmlRenderer.PageBundle(peer,hints,Map.of()));
        var names=Map.of(p.getId(),p.getName(),peer.getId(),peer.getName());String html=renderer.render(p,bundles,names,p.getName(),null);
        assertEquals(html,renderer.render(p,bundles,names,p.getName(),null));
        assertEquals(original,mapper.writeValueAsString(source));assertEquals(originalPeer,mapper.writeValueAsString(hints));
        var ids=Pattern.compile("data-wf-element-id=\"(\\d+)\"").matcher(html);var seen=new HashSet<String>();
        while(ids.find())assertTrue(seen.add(ids.group(1)),"Duplicate binding "+ids.group(1));
        return html;
    }

    @Test
    void sameDraftCanFillEmptyCornerSymbolsWithoutBorrowingPeerIdsOrActions() throws Exception {
        Page self=page(1,"a".repeat(64)),peer=page(2,"a".repeat(64));
        Interaction action=new Interaction();action.setElementId(101L);action.setActionType("back");
        String html=render(self,cards(100,"badge"),peer,cards(200,"icon"),Map.of(101L,List.of(action)));
        assertEquals(2,count(html,"wf-display-state"));assertEquals(0,count(html,"wf-badge"));
        assertTrue(html.contains("data-icon='lightbulb'"));assertTrue(html.contains("data-icon='eye-off'"));
        assertTrue(html.contains("data-state='inactive'"));assertFalse(html.contains("data-state='unknown'"));
        assertTrue(html.contains("data-wf-element-id=\"101\" data-action=\"back\""));
        assertFalse(html.contains("data-wf-element-id=\"201\""));
    }

    @Test
    void differentDraftsCannotTransferStateAndUnknownBadgesStayNeutral() throws Exception {
        String html=render(page(1,"a".repeat(64)),cards(100,"badge"),page(2,"b".repeat(64)),cards(200,"icon"),Map.of());
        assertEquals(2,count(html,"wf-display-state"));assertEquals(0,count(html,"wf-badge"));
        assertTrue(html.contains("data-state='unknown'"));assertFalse(html.contains("data-state='inactive'"));
    }

    @Test
    void ambiguousCornerMatchesCannotTransferState() throws Exception {
        List<Element> hints=cards(200,"icon");hints.add(e(299,"icon","隐藏",12,196,21,21));
        String html=render(page(1,"a".repeat(64)),cards(100,"badge"),page(2,"a".repeat(64)),hints,Map.of());
        assertTrue(html.contains("data-state='unknown'"));
    }

    @Test
    void splitButtonsAndSegmentWithSeparateLabelsUseTheSamePoseDock() throws Exception {
        for(boolean split:List.of(false,true)){
            List<Element> items=cards(100,"icon");items.add(e(150,"container","底部切换栏",0,778,375,55));
            items.add(e(152,"icon","动效",64,784,30,30));items.add(e(154,"icon","隐藏动效",203,784,30,30));
            if(split){items.add(e(151,"button","形象一",105,784,72,34));items.add(e(155,"button","形象二",244,784,72,34));}
            else{items.add(e(151,"tabs","形象一/形象二",105,784,232,34));items.add(e(153,"text","形象一",123,793,40,16));items.add(e(155,"text","形象二",260,793,40,16));}
            Interaction action=new Interaction();action.setElementId(151L);action.setActionType("tab_switch");
            String html=render(page(1,null),items,page(2,null),List.of(),Map.of(151L,List.of(action)));
            assertEquals(1,count(html,"wf-pose-dock"));assertEquals(1,count(html,"wf-pose-switch"));assertEquals(2,count(html,"wf-pose-option"));
            assertEquals(2,count(html,"wf-pose-label"));assertEquals(2,count(html,"wf-pose-state"));
            assertTrue(html.contains("data-wf-element-id=\"151\" data-action=\"tab\""));
        }
    }

    @Test
    void thinVisibleBottomSlicesKeepTheirCornerBindingsAndPeerImageHeight() throws Exception {
        List<Element> items=cards(100,"icon");items.add(e(130,"image","",14,761,80,9));items.add(e(131,"icon","灯泡",12,756,21,14));
        items.add(e(140,"image","",114,761,80,9));
        String html=render(page(1,null),items,page(2,null),List.of(),Map.of());
        assertEquals(4,count(html,"wf-display-card"));assertEquals(3,count(html,"wf-display-state"));
        assertTrue(Pattern.compile("class='wf-display-state'[^>]*data-wf-element-id=\"131\"").matcher(html).find());
    }

    @Test
    void croppedPictureCanUseItsOwnCornerAndCaptionToRecoverThePeerCardSlot() throws Exception {
        List<Element> items=cards(100,"icon");
        items.add(e(130,"image","",17,609,65,105));items.add(e(131,"icon","灯泡",12,555,21,21));
        items.add(e(132,"text","左滑：起舞",14,701,80,14));
        String html=render(page(1,null),items,page(2,null),List.of(),Map.of());
        assertEquals(3,count(html,"wf-display-state"));
        var tag=Pattern.compile("<div[^>]*data-wf-element-id=\"130\"[^>]*>").matcher(html);assertTrue(tag.find());
        assertTrue(tag.group().contains("top:559px;width:80px;"),tag.group());
    }
}
