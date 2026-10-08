package com.wireforge.service;

import com.fasterxml.jackson.databind.*;
import com.wireforge.entity.*;
import com.wireforge.mapper.*;
import com.wireforge.model.AutowirePlan;
import com.wireforge.model.AutowirePlan.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in only: exercises the configured local development MySQL with isolated, cleaned fixtures. */
@EnabledIfSystemProperty(named="wireforge.integration", matches="true")
@ActiveProfiles("dev")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={"wireforge.auto-analyze=false"})
class AutowireMysqlIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper json;
    @Autowired ProjectService projects;
    @Autowired PageMapper pages;
    @Autowired ElementMapper elements;
    @Autowired InteractionMapper interactions;
    @Autowired AnnotationMapper annotations;
    @Autowired InteractionAutowireService autowire;
    @Autowired AutowireRenderService render;
    @Autowired com.wireforge.ai.HtmlRenderer htmlRenderer;
    long projectId;
    Page store, records, popup;
    Element button, card;
    String marker="独立验证布局";
    @BeforeEach void seed() {
        projectId=projects.createProject("__autowire_test__"+UUID.randomUUID(),"临时自动连线验证").getId();
        store=page("积分商城");records=page("兑换记录");popup=page("兑换确认弹窗");
        button=element("button","兑换记录",10,100,100,30);
        card=element("container","商品卡片",10,160,150,180);
        String html="<html><body><div style=\"color:red\">"+marker+"</div><button data-wf-element-id=\""+button.getId()+"\">兑换记录</button><div data-wf-element-id=\""+card.getId()+"\">商品卡片</div></body></html>";
        jdbc.update("UPDATE page SET html_content=? WHERE id=?",html,store.getId());
    }
    Page page(String name) {
        Page p=new Page();p.setProjectId(projectId);p.setName(name);p.setCanvasWidth(375);p.setCanvasHeight(812);p.setSortOrder(0);p.setHtmlContent("<html><body>"+name+"</body></html>");pages.insert(p);return p;
    }
    Element element(String type,String label,double x,double y,double w,double h) {
        Element e=AutowirePlannerTest.el(1,type,label,x,y,w,h);e.setId(null);e.setPageId(store.getId());elements.insert(e);return e;
    }
    Interaction line(Element e,String source,Long target) {
        Interaction i=AutowirePlannerTest.line(1,e,source,target);i.setId(null);interactions.insert(i);return i;
    }
    @AfterEach void cleanup() {
        if(projectId>0 && projects.getProject(projectId).getName().startsWith("__autowire_test__")) projects.deleteProject(projectId);
    }
    AutowirePlan preview() {
        JsonNode body=http.postForObject("/api/projects/"+projectId+"/autowire/preview",Map.of(),JsonNode.class);
        assertEquals(0,body.path("code").asInt(),body.toString());return json.convertValue(body.path("data"),AutowirePlan.class);
    }
    ApplyRequest request(AutowirePlan p,List<String> selected,List<ExcludeDecision> excluded,List<Long> restore) {
        return new ApplyRequest(p.previewId(),UUID.randomUUID().toString(),selected,excluded,restore);
    }
    ApplyResult apply(ApplyRequest request) {
        var response=http.postForEntity("/api/projects/"+projectId+"/autowire/apply",request,JsonNode.class);
        assertEquals(200,response.getStatusCode().value(),String.valueOf(response.getBody()));
        assertEquals(0,response.getBody().path("code").asInt());return json.convertValue(response.getBody().path("data"),ApplyResult.class);
    }
    int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE project_id=?",Integer.class,projectId); }

    @Test void previewIsReadOnlyAndApplyIsIdempotentAndPreservesLayout() {
        String before=pages.selectById(store.getId()).getHtmlContent();AutowirePlan p=preview();
        assertEquals(before,pages.selectById(store.getId()).getHtmlContent());assertEquals(0,count("autowire_application"));assertEquals(0,count("interaction_exclusion"));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM interaction WHERE element_id=?",Integer.class,button.getId()));
        ApplyRequest req=request(p,List.of(p.items().get(0).id()),List.of(),List.of());ApplyResult result=apply(req);
        assertEquals(1,result.added());assertEquals("done",result.renderStatus());assertEquals(result,apply(req));assertEquals(1,count("autowire_application"));
        String html=pages.selectById(store.getId()).getHtmlContent();assertTrue(html.contains("<div style=\"color:red\">"+marker+"</div>"));assertTrue(html.contains("wf-autowire-bindings"));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM interaction WHERE element_id=?",Integer.class,button.getId()));
        assertTrue(jdbc.queryForObject("SELECT backup_json FROM autowire_application WHERE id=?",String.class,result.applicationId()).contains("interactions"));
        var checks=htmlRenderer.verifyInteractions("独立验证",html);assertEquals(1,checks.size());assertTrue(checks.stream().allMatch(com.wireforge.ai.HtmlRenderer.CheckResult::ok),checks.toString());
    }
    @Test void exclusionPersistsAcrossRunsAndCanBeRestored() {
        AutowirePlan p=preview();apply(request(p,List.of(),List.of(new ExcludeDecision(p.items().get(0).id(),"relation")),List.of()));
        AutowirePlan next=preview();assertTrue(next.items().isEmpty());assertEquals(1,next.exclusions().size());
        apply(request(next,List.of(),List.of(),List.of(next.exclusions().get(0).id())));
        assertEquals(1,preview().items().size());
    }
    @Test void removingAutoCardPersistsExclusionAndManualCardRemains() {
        Interaction auto=line(card,"ai",records.getId());Element manual=element("container","人工卡片",180,160,150,180);Interaction user=line(manual,"user",popup.getId());
        AutowirePlan p=preview();String removal=p.items().stream().filter(i->"remove".equals(i.category())).findFirst().orElseThrow().id();
        apply(request(p,List.of(removal),List.of(),List.of()));assertNull(interactions.selectById(auto.getId()));assertNotNull(interactions.selectById(user.getId()));assertEquals(1,count("interaction_exclusion"));
    }
    @Test void canvasDeleteAndManualRestoreRespectPersistentExclusion() {
        AutowirePlan p=preview();apply(request(p,List.of(p.items().get(0).id()),List.of(),List.of()));
        Long id=jdbc.queryForObject("SELECT id FROM interaction WHERE element_id=?",Long.class,button.getId());projects.deleteInteraction(projectId,id);
        assertTrue(preview().items().isEmpty());assertEquals(1,count("interaction_exclusion"));
        projects.saveInteraction(projectId,Map.of("elementId",button.getId(),"targetPageId",records.getId(),"actionType","navigate"));
        assertTrue(preview().exclusions().isEmpty());assertEquals("user",jdbc.queryForObject("SELECT source FROM interaction WHERE element_id=?",String.class,button.getId()));
    }
    @Test void stalePlanRejectsEveryMutation() {
        AutowirePlan p=preview();jdbc.update("UPDATE page SET html_content=CONCAT(html_content,'<!--编辑-->') WHERE id=?",store.getId());
        var response=http.postForEntity("/api/projects/"+projectId+"/autowire/apply",request(p,List.of(p.items().get(0).id()),List.of(),List.of()),JsonNode.class);
        assertEquals(409,response.getStatusCode().value());assertEquals(0,count("autowire_application"));assertEquals(0,count("interaction_exclusion"));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM interaction WHERE element_id=?",Integer.class,button.getId()));
    }
    @Test void laterRenderKeepsPreviouslyReviewedPopupBindings() {
        Element buy=element("button","兑换",20,220,100,30);Annotation a=new Annotation();a.setPageId(store.getId());a.setElementId(buy.getId());a.setText("点击兑换按钮打开兑换确认弹窗");annotations.insert(a);
        jdbc.update("UPDATE page SET html_content=REPLACE(html_content,'</body>',?) WHERE id=?","<button data-wf-element-id=\""+buy.getId()+"\">兑换</button></body>",store.getId());
        AutowirePlan p=preview();String popupItem=p.items().stream().filter(i->i.elementId()==buy.getId()).findFirst().orElseThrow().id();apply(request(p,List.of(popupItem),List.of(),List.of()));
        projects.saveInteraction(projectId,Map.of("elementId",button.getId(),"targetPageId",records.getId(),"actionType","navigate"));
        for(String id:jdbc.queryForList("SELECT id FROM autowire_render_job WHERE project_id=? AND status='pending'",String.class,projectId)) render.process(id);
        String html=pages.selectById(store.getId()).getHtmlContent();assertTrue(html.contains("modalPages"));assertTrue(html.contains("兑换确认弹窗"));
        var matcher=java.util.regex.Pattern.compile("(?s)<script id=\"wf-autowire-map\" type=\"application/json\">(.*?)</script>").matcher(html);assertTrue(matcher.find());
        try{assertTrue(json.readTree(matcher.group(1)).path("modalPages").has(String.valueOf(popup.getId())));}catch(Exception ex){fail(ex);}
        var checks=htmlRenderer.verifyInteractions("独立验证弹窗",html);assertEquals(2,checks.size());assertTrue(checks.stream().allMatch(com.wireforge.ai.HtmlRenderer.CheckResult::ok),checks.toString());
    }
    @Test void newAnalysisOutputCannotBypassExclusionsOrCardRules() {
        AutowirePlan p=preview();apply(request(p,List.of(),List.of(new ExcludeDecision(p.items().get(0).id(),"relation")),List.of()));
        Interaction excluded=line(button,"ai",records.getId()), badCard=line(card,"ai",records.getId());
        Interaction legacy=line(card,null,popup.getId());
        autowire.filterIdentifiedRelations(projectId,List.of(excluded.getId(),badCard.getId(),legacy.getId()));
        assertNull(interactions.selectById(excluded.getId()));assertNull(interactions.selectById(badCard.getId()));assertNotNull(interactions.selectById(legacy.getId()));
    }
}
