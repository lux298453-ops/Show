package com.wireforge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wireforge.entity.Element;
import com.wireforge.entity.Interaction;
import com.wireforge.entity.Page;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class TemplateHtmlRendererUnifiedComponentsTest {
    private static Element e(long id,String type,String label,double x,double y,double w,double h) {
        Element element=new Element();element.setId(id);element.setPageId(1L);element.setType(type);element.setLabel(label);
        element.setPositionX(x);element.setPositionY(y);element.setWidth(w);element.setHeight(h);return element;
    }

    private static String render(List<Element> elements,long... clickable) throws Exception {
        return renderNamed("测试面板",elements,clickable);
    }

    private static String renderNamed(String name,List<Element> elements,long... clickable) throws Exception {
        Page page=new Page();page.setId(1L);page.setName(name);page.setCanvasWidth(375);page.setCanvasHeight(833);
        Map<Long,List<Interaction>> actions=new HashMap<>();
        for(long id:clickable){Interaction action=new Interaction();action.setElementId(id);action.setActionType("navigate");action.setTargetPageId(2L);actions.put(id,List.of(action));}
        ObjectMapper mapper=new ObjectMapper();String before=mapper.writeValueAsString(elements);
        var renderer=new TemplateHtmlRenderer(new DesignColorSampler(),mapper);
        var bundle=new TemplateHtmlRenderer.PageBundle(page,elements,actions);
        Map<Long,TemplateHtmlRenderer.PageBundle> bundles=Map.of(1L,bundle);Map<Long,String> names=Map.of(1L,"测试面板",2L,"目标页面");
        String html=renderer.render(page,bundles,names,page.getName(),null);
        assertEquals(html,renderer.render(page,bundles,names,page.getName(),null));
        assertEquals(before,mapper.writeValueAsString(elements),"Component grouping must not mutate recognition results");
        var ids=Pattern.compile("data-wf-element-id=\"(\\d+)\"").matcher(html);var seen=new HashSet<String>();
        while(ids.find())assertTrue(seen.add(ids.group(1)),"Duplicate source binding "+ids.group(1));
        for(long id:clickable)assertTrue(html.contains("data-wf-element-id=\""+id+"\" data-nav=\"目标页面\""),"Lost action "+id);
        return html;
    }

    private static String block(String html,String cssClass) {
        var start=Pattern.compile("<div class=['\"][^'\"]*\\b"+cssClass+"\\b[^'\"]*['\"][^>]*>").matcher(html);
        assertTrue(start.find(),"Missing "+cssClass);var tags=Pattern.compile("</?div\\b[^>]*>").matcher(html);tags.region(start.start(),html.length());
        int depth=0;while(tags.find()){depth+=tags.group().startsWith("</")?-1:1;if(depth==0)return html.substring(start.start(),tags.end());}
        fail("Unclosed "+cssClass);return "";
    }

    private static long count(String html,String cssClass) {
        return Pattern.compile("class=['\"](?:[^'\"]*\\s)?"+Pattern.quote(cssClass)+"(?:\\s[^'\"]*)?['\"]").matcher(html).results().count();
    }

    private static String elementTag(String html,long id) {
        var match=Pattern.compile("<[^>]*data-wf-element-id=\""+id+"\"[^>]*>").matcher(html);
        assertTrue(match.find(),"Missing element "+id);return match.group();
    }

    private static double dimension(String tag,String property) {
        var match=Pattern.compile("(?:[;'\"])"+property+":([\\d.]+)px").matcher(tag);
        assertTrue(match.find(),"Missing "+property+" in "+tag);return Double.parseDouble(match.group(1));
    }

    @Test
    void ownedAndRedeemableCardsHaveOneHeightAndCannotOverlapExternalButtons() throws Exception {
        List<Element> items=new ArrayList<>();
        for(int row=0;row<2;row++)for(int col=0;col<3;col++){
            long id=10+row*40+col*10;double x=20+col*100,y=190+row*180;
            items.add(e(id,"container","卡片",x,y,86,132));items.add(e(id+1,"image","",x+6,y+8,74,98));
            items.add(e(id+2,"text",col==0?"钻石*12800":"伊莱克斯",x+4,y+110,78,16));
            if(col==2)items.add(e(id+3,"badge","已拥有",x+12,y+40,40,18));
            else items.add(e(id+4,"button","x1兑换",x+8,y+136,70,26));
        }
        String html=render(items,14,24,54,64);Double height=null;
        for(long id:List.of(10L,20L,30L,50L,60L,70L)){
            String card=elementTag(html,id);double h=dimension(card,"height");
            if(height==null)height=h;else assertEquals(height,h);
            if(id%40!=30)assertTrue(dimension(card,"top")+h<=dimension(elementTag(html,id+4),"top")-5);
        }
        assertTrue(elementTag(html,12).contains("white-space:nowrap"));
    }

    @Test
    void allSevenToolbarItemsRenderAboveTheirOpaqueDock() throws Exception {
        List<Element> items=new ArrayList<>();items.add(e(1,"container","底部功能栏",17,746,341,60));
        String[] labels={"锁屏","手势","充电","待机","表演","来电","自定义"};
        for(int i=0;i<labels.length;i++)items.add(e(10+i,"button",labels[i],30+i*43,754,36,44));
        String html=render(items,10,11,12,13,14,15,16);assertEquals(7,count(html,"wf-shortcut"));
        for(int i=0;i<labels.length;i++)assertTrue(elementTag(html,10+i).contains("z-index:46"),labels[i]);
        assertTrue(elementTag(html,1).contains("z-index:45"));
    }

    @Test
    void bottomPoseSwitchShowsBothStateIconsAndBothPillsAboveDock() throws Exception {
        String html=render(List.of(e(1,"container","底部切换栏",0,778,375,55),e(2,"tabs","形象一/形象二",63,781,250,44)),2);
        assertEquals(2,count(html,"wf-pose-option"));assertEquals(2,count(html,"wf-pose-label"));
        assertTrue(html.contains("data-icon='lightbulb'"));assertTrue(html.contains("data-icon='eye-off'"));
        assertTrue(elementTag(html,2).contains("z-index:46"));
    }

    @Test
    void textCloseAndHeaderCrossUseDifferentUnfilledComponents() throws Exception {
        String html=render(List.of(e(1,"container","弹窗背景卡片",0,120,375,713),
                e(2,"button","关闭",329,144,18,18),e(3,"button","关闭",168,765,41,23)),2,3);
        assertTrue(elementTag(html,2).contains("wf-close-icon"));assertFalse(elementTag(html,2).contains("wf-close-circle"));
        assertTrue(elementTag(html,3).contains("wf-close-text"));assertFalse(elementTag(html,3).contains("wf-btn"));
        assertTrue(elementTag(html,3).contains("background:none"));
    }

    @Test
    void detachedCloseCannotBeIncludedInAnOversizedActivityPanel() throws Exception {
        String html=renderNamed("活动弹窗",List.of(e(1,"container","弹窗背景卡片",30,145,312,594),
                e(2,"text","新品资源",70,165,110,30),e(3,"image","",70,265,96,147),
                e(4,"button","活动规则",153,635,70,22),e(5,"button","关闭",170,704,34,34)),4,5);
        String panel=elementTag(html,1);assertTrue(dimension(panel,"top")+dimension(panel,"height")<704-12);
        assertTrue(elementTag(html,5).contains("wf-close-circle"));
    }

    @Test
    void recordLinesShareTheSameLeftEdgeAndDatesAlignRight() throws Exception {
        String html=render(List.of(e(1,"container","弹窗背景卡片",0,120,375,713),e(2,"list","",25,195,325,400),
                e(3,"text","阿四与小铃铛 -经典耀宝",25,199,164,17),e(4,"text","经典兑换卡x1",25,221,78,16),
                e(5,"text","2023-03-2113:02:01",217,199,133,17)));
        for(long id:List.of(3L,4L)){
            assertTrue(elementTag(html,id).contains("justify-content:flex-start"));assertTrue(elementTag(html,id).contains("padding-left:0"));
        }
        assertTrue(elementTag(html,5).contains("justify-content:flex-end"));
    }

    @Test
    void skeletonPreservesEveryLoadingBlockAndItsDropdownArrow() throws Exception {
        List<Element> items=new ArrayList<>(List.of(e(1,"container","弹窗背景卡片",0,352,375,481),
                e(2,"icon","收起",171,360,32,9),e(3,"container","",12,436,350,381),
                e(4,"container","",25,393,83,28),e(5,"container","",324,393,26,28)));
        for(int i=0;i<4;i++)items.add(e(10+i,"container","",25,448+i*76,325,45));
        String html=renderNamed("资源详情页-骨架图",items,2);
        assertEquals(6,count(html,"wf-skeleton-bar"));assertEquals(1,count(html,"wf-skeleton-content"));
        assertTrue(elementTag(html,2).contains("wf-sheet-chevron"));assertEquals(352,dimension(elementTag(html,1),"top"));
        for(long id:List.of(3L,4L,5L,10L,11L,12L,13L))assertNotNull(elementTag(html,id));
    }

    @Test
    void itemStatusAndTaskRewardsWithTabKeysCannotBecomeBottomNavigation() throws Exception {
        List<Element> items=new ArrayList<>(List.of(e(1,"icon","闪电",110,690,14,14),e(2,"text","+100",130,690,30,16),
                e(3,"icon","未启用",290,754,20,20),e(4,"text","左滑：起舞",275,785,70,16)));
        items.get(0).setGroupKey("p1-tab-0");items.get(1).setGroupKey("p1-tab-0");
        items.get(2).setGroupKey("p1-tab-1");items.get(3).setGroupKey("p1-tab-1");
        String html=render(items);
        assertFalse(html.contains("wf-canonical-nav"));assertTrue(html.contains("+100"));assertTrue(html.contains("左滑：起舞"));
    }

    @ParameterizedTest
    @ValueSource(strings={"50/100","0/100","0/0","125%"})
    void progressAlwaysRetainsItsActualNumericLabel(String value) throws Exception {
        String progress=block(render(List.of(e(1,"progress",value,100,100,100,14))),"wf-progress");
        assertTrue(progress.contains(value));assertTrue(progress.contains("wf-progress-value"));assertFalse(progress.contains("gradient"));
        if(value.startsWith("0/"))assertTrue(progress.contains("width:0px;height:100%"));
        if(value.equals("50/100"))assertTrue(progress.contains("width:49px;height:100%"));
    }

    @Test
    void taskRowsKeepTheirOwnCopyRewardAndExternalActionsInsideOneList() throws Exception {
        List<Element> items=new ArrayList<>();items.add(e(1,"list","",12,300,350,192));
        String[] names={"观看大表演","从挂件返回","每日宝箱领取"};String[] controls={"领取","去完成","已完成"};
        for(int i=0;i<3;i++) {
            long id=10+i*10;double y=300+i*64;
            items.add(e(id,"container","任务项",12,y,350,64));items.add(e(id+1,"image","",24,y+12,34,34));
            items.add(e(id+2,"text",names[i],78,y+10,90,18));items.add(e(id+3,"icon","钻石",170,y+12,14,14));
            items.add(e(id+4,"text","+100",190,y+12,36,16));items.add(e(id+5,"text","保留这行任务说明",78,y+34,160,16));
            items.add(e(id+6,"button",controls[i],284,y+16,62,28));
        }
        String html=render(items,16,26,36),list=block(html,"wf-task-list");
        assertEquals(3,count(list,"wf-detail-row"));assertEquals(3,count(list,"wf-task-control"));
        assertEquals(3,count(list,"wf-detail-reward-value"));assertEquals(3,count(list,"wf-detail-description"));
        assertFalse(html.contains("wf-canonical-nav"));assertFalse(list.contains("gradient"));
        for(String name:names)assertTrue(list.contains(name));for(String control:controls)assertTrue(list.contains(control));
    }

    @Test
    void settingRowsPreserveDescriptionsAndExplicitOffState() throws Exception {
        String html=render(List.of(e(1,"container","设置卡片",12,150,350,110),
                e(2,"text","自动下载",24,160,100,18),e(3,"text","空闲时更新资源",24,184,200,16),e(4,"switch","开启",315,166,36,22),
                e(5,"text","自动清理",24,214,100,18),e(6,"text","清理长期未使用的资源",24,238,200,16),e(7,"switch","关闭",315,218,36,22)));
        String list=block(html,"wf-settings-list");assertEquals(2,count(list,"wf-detail-row"));assertEquals(2,count(list,"wf-sw"));
        assertTrue(list.contains("data-state='off'"));assertTrue(list.contains("data-state='on'"));assertTrue(list.contains("清理长期未使用的资源"));
        assertFalse(list.contains("gradient"));
    }

    @ParameterizedTest
    @ValueSource(strings={"未选","未启用","关闭","off"})
    void negativeCheckboxStatesDoNotBecomeSelected(String value) throws Exception {
        String html=render(List.of(e(1,"checkbox",value,30,100,20,20)));
        assertFalse(html.contains("wf-ck checked"));
    }

    @Test
    void lotteryChoicesBesideCharacterCardsCannotCollectCharacterNamesAsSettingRows() throws Exception {
        String html=render(List.of(e(1,"container","扭蛋机",25,200,325,430),
                e(2,"text","姜立",75,425,22,12),e(3,"text","悠娜",133,425,22,12),e(4,"text","苏白衣",189,425,30,12),
                e(5,"checkbox","单抽",272,424,18,18),e(6,"text","单抽",292,426,30,15),
                e(7,"text","300钻石/次",269,450,66,14),e(8,"checkbox","10抽",272,474,18,18),e(9,"text","10抽",292,474,30,15)),5,8);
        assertEquals(0,count(html,"wf-settings-list"));assertEquals(2,count(html,"wf-ck"));
        for(String name:List.of("姜立","悠娜","苏白衣","300钻石/次"))assertTrue(html.contains(name));
    }

    @Test
    void inventoryTilesUnifyCountsBonusAndCaptionsWithoutTurningIntoRewardBubbles() throws Exception {
        List<Element> items=new ArrayList<>();String[] labels={"香蕉道具","冰淇淋道具"};
        for(int i=0;i<2;i++) {
            long id=10+i*10;double x=24+i*84;
            items.add(e(id,"container","",x,300,76,74));items.add(e(id+1,"image","",x+14,315,44,35));
            items.add(e(id+2,"badge","4",x+56,304,15,17));items.add(e(id+3,"text","+30",x+40,354,30,16));
            items.add(e(id+4,"text",labels[i],x+4,382,68,16));
        }
        String html=render(items,11,21);
        assertEquals(2,count(html,"wf-inventory-tile"));assertEquals(2,count(html,"wf-inventory-count"));
        assertEquals(2,count(html,"wf-inventory-bonus"));assertTrue(html.contains("data-icon='food'"));assertTrue(html.contains("data-icon='icecream'"));
        assertFalse(html.contains("wf-reward-entry"));for(String label:labels)assertTrue(html.contains(label));
    }

    @ParameterizedTest
    @ValueSource(strings={"container","image"})
    void effectTilesAbsorbTheirCornerStateAndCaptionAndUseEqualImageHeights(String structure) throws Exception {
        List<Element> items=new ArrayList<>();
        for(int i=0;i<2;i++) {
            long id=10+i*10;double x=12+i*100;
            if(structure.equals("container"))items.add(e(id,"container","卡片",x,200,90,132));
            items.add(e(id+1,"image","",x+2,202,86,120+i*8));items.add(e(id+2,"icon","已启用",x,198,20,20));
            items.add(e(id+3,"text","左滑：起舞",x+2,334+i*8,86,16));
        }
        String html=render(items,12,22);assertEquals(2,count(html,"wf-display-card"));assertEquals(2,count(html,"wf-display-state"));
        var imageHeights=Pattern.compile("class='wf-display-art'[^>]*height:(\\d+)px").matcher(html).results().map(match -> match.group(1)).toList();
        assertEquals(2,imageHeights.size());assertEquals(1,new HashSet<>(imageHeights).size());
        assertFalse(html.contains("wf-canonical-nav"));assertTrue(html.contains("左滑：起舞"));
    }

    @Test
    void userAuthoredTabHitAreaRetainsItsOwnNavigationBinding() throws Exception {
        String html=render(List.of(e(1,"tabs","照片壁纸/纪念品",20,550,160,32),e(7,"button","纪念品",100,550,80,32)),7);
        assertTrue(html.contains("wf-tab-hit"));assertTrue(html.contains("照片壁纸"));assertTrue(html.contains("纪念品"));
    }

    @Test
    void leftImageRightNameCharacterCardsKeepTheirHorizontalStructure() throws Exception {
        String html=render(List.of(e(1,"container","",12,200,169,92),e(2,"image","",12,200,86,92),
                e(3,"badge","典藏",108,208,40,15),e(4,"text","奶龙",105,236,60,18)),2);
        String card=block(html,"wf-character-row");
        assertTrue(card.contains("wf-character-thumbnail"));assertTrue(card.contains("wf-character-copy"));
        assertFalse(card.contains("wf-collectible-art"));assertTrue(card.contains("典藏"));assertTrue(card.contains("奶龙"));
    }

    @Test
    void serviceRowKeepsBothTheLeadingIconAndArrowActionsVisible() throws Exception {
        String html=render(List.of(e(1,"text","我的服务",20,250,100,20),e(2,"container","服务列表",12,290,350,60),
                e(3,"icon","功能设置",24,310,24,24),e(4,"text","功能设置",60,312,100,20),e(5,"icon","进入",330,312,18,20)),3,5);
        assertTrue(Pattern.compile("class='wf-service-row'[^>]*data-wf-element-id=\"3\"").matcher(html).find());
        assertTrue(Pattern.compile("class='wf-service-arrow'[^>]*data-wf-element-id=\"5\"").matcher(html).find());
    }
}
