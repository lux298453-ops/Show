package com.wireforge.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.wireforge.entity.*;
import com.wireforge.mapper.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real MySQL/API checks; uses only isolated fixtures and removes them afterwards. */
@EnabledIfSystemProperty(named = "wireforge.integration", matches = "true")
@ActiveProfiles("dev")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"wireforge.auto-analyze=false"})
class AnnotationMysqlIntegrationTest {
    @Autowired ProjectService projects;
    @Autowired PageMapper pages;
    @Autowired ElementMapper elements;
    @Autowired AnnotationMapper annotations;
    @Autowired InteractionMapper interactions;
    @Autowired AnalyzeService analyze;
    @Autowired TestRestTemplate http;
    @Autowired PlatformTransactionManager tx;
    long projectId;
    Page page, otherPage;
    Element element;
    String html = "<html><body><button>兑换</button></body></html>";

    @BeforeEach void seed() {
        projectId = projects.createProject("__annotation_test__" + UUID.randomUUID(), "临时标注验证").getId();
        page = page("积分商城"); otherPage = page("兑换记录");
        element = new Element(); element.setPageId(page.getId()); element.setType("button"); element.setLabel("兑换");
        element.setPositionX(20.0); element.setPositionY(120.0); element.setWidth(100.0); element.setHeight(32.0); elements.insert(element);
        Interaction line = new Interaction(); line.setElementId(element.getId()); line.setTriggerType("click");
        line.setActionType("navigate"); line.setTargetPageId(otherPage.getId()); line.setSource("user"); interactions.insert(line);
    }
    Page page(String name) {
        Page p = new Page(); p.setProjectId(projectId); p.setName(name); p.setCanvasWidth(375); p.setCanvasHeight(812);
        p.setSortOrder(0); p.setHtmlContent(html); pages.insert(p); return p;
    }
    @AfterEach void cleanup() {
        if (projectId > 0 && projects.getProject(projectId).getName().startsWith("__annotation_test__")) projects.deleteProject(projectId);
    }
    String url() { return "/api/projects/" + projectId + "/pages/" + page.getId() + "/annotations"; }
    JsonNode add(Map<String, Object> body) {
        var response = http.postForEntity(url(), body, JsonNode.class);
        assertEquals(200, response.getStatusCode().value(), String.valueOf(response.getBody()));
        assertEquals(0, response.getBody().path("code").asInt()); return response.getBody().path("data");
    }
    long count() { return annotations.selectList(com.baomidou.mybatisplus.core.toolkit.Wrappers.<Annotation>lambdaQuery().eq(Annotation::getPageId, page.getId())).size(); }

    @Test void attachedNotePersistsWithOwnTitleAndAnchorWithoutChangingPrototype() {
        JsonNode added = add(Map.of("elementId", element.getId(), "title", "兑换条件", "text", "积分足够时可兑换。\n每日限兑一次。"));
        Annotation note = annotations.selectById(added.path("id").asLong());
        assertEquals(element.getId(), note.getElementId()); assertEquals("user", note.getSource());
        assertEquals("兑换条件", note.getTitle()); assertTrue(note.getText().contains("\n"));
        assertEquals(70.0, note.getPositionX()); assertEquals(136.0, note.getPositionY());
        assertEquals("兑换", elements.selectById(element.getId()).getLabel()); assertEquals(html, pages.selectById(page.getId()).getHtmlContent());
        assertEquals(1, interactions.selectList(null).stream().filter(i -> i.getElementId().equals(element.getId())).count());
        JsonNode prototype = http.getForObject("/api/projects/" + projectId + "/prototype", JsonNode.class).path("data");
        JsonNode saved = prototype.path("pages").findValues("annotations").stream().flatMap(n -> { List<JsonNode> values = new ArrayList<>(); n.forEach(values::add); return values.stream(); }).findFirst().orElseThrow();
        assertEquals("兑换条件", saved.path("title").asText()); assertEquals("user", saved.path("source").asText());
    }
    @Test void pageNoteNeedsNoInteractiveElementAndAppendsAfterExistingOrder() {
        JsonNode first = add(Map.of("text", "页面活动说明"));
        JsonNode second = add(Map.of("title", "展示规则", "text", "商品按类别展示"));
        assertTrue(first.path("element_id").isNull()); assertEquals("页面说明", first.path("title").asText());
        assertEquals(0, first.path("sort_order").asInt()); assertEquals(1, second.path("sort_order").asInt());
        assertEquals(2, count());
    }
    @Test void editingTitleNeverRenamesElementAndMarksManualEdits() {
        Annotation ai = new Annotation(); ai.setPageId(page.getId()); ai.setElementId(element.getId()); ai.setText("旧说明"); annotations.insert(ai);
        var response = http.exchange("/api/projects/" + projectId + "/annotations/" + ai.getId(), HttpMethod.PUT,
                new HttpEntity<>(Map.of("title", "人工业务说明", "text", "新的操作条件")), JsonNode.class);
        assertEquals(200, response.getStatusCode().value()); Annotation saved = annotations.selectById(ai.getId());
        assertEquals("人工业务说明", saved.getTitle()); assertEquals("user", saved.getSource());
        assertEquals("兑换", elements.selectById(element.getId()).getLabel()); assertEquals(html, pages.selectById(page.getId()).getHtmlContent());
    }
    @Test void rejectsElementsFromAnotherPageWithoutInserting() {
        Element foreign = new Element(); foreign.setPageId(otherPage.getId()); foreign.setType("text"); foreign.setLabel("记录"); elements.insert(foreign);
        assertEquals(400, http.postForEntity(url(), Map.of("text", "说明", "elementId", foreign.getId()), JsonNode.class).getStatusCode().value());
        assertEquals(400, http.postForEntity(url(), Map.of("text", "说明", "elementId", "3.5"), JsonNode.class).getStatusCode().value());
        assertEquals(0, count());
    }
    @Test void rejectsMissingOrOversizedContentAndDoesNotPartiallyUpdate() {
        for (Map<String, Object> body : List.of(Map.<String,Object>of("text", "  "), Map.<String,Object>of("text", "x".repeat(5001)), Map.<String,Object>of("text", 42), Map.<String,Object>of("text", "说明", "title", "x".repeat(201)))) {
            assertEquals(400, http.postForEntity(url(), body, JsonNode.class).getStatusCode().value());
        }
        assertEquals(0, count());
        long noteId = add(Map.of("text", "原说明")).path("id").asLong();
        assertEquals(400, http.exchange("/api/projects/" + projectId + "/annotations/" + noteId, HttpMethod.PUT,
                new HttpEntity<>(Map.of("title", "新标题", "text", " ")), JsonNode.class).getStatusCode().value());
        assertEquals("原说明", annotations.selectById(noteId).getText());
    }
    @Test void anotherProjectCannotAddEditOrDeleteTheseNotes() {
        long anotherId = projects.createProject("__annotation_test__" + UUID.randomUUID(), "隔离验证").getId();
        try {
            long noteId = add(Map.of("text", "说明")).path("id").asLong();
            assertEquals(400, http.postForEntity("/api/projects/" + anotherId + "/pages/" + page.getId() + "/annotations", Map.of("text", "错误项目"), JsonNode.class).getStatusCode().value());
            assertEquals(400, http.exchange("/api/projects/" + anotherId + "/annotations/" + noteId, HttpMethod.PUT, new HttpEntity<>(Map.of("text", "错误编辑")), JsonNode.class).getStatusCode().value());
            assertEquals(400, http.exchange("/api/projects/" + anotherId + "/annotations/" + noteId, HttpMethod.DELETE, HttpEntity.EMPTY, JsonNode.class).getStatusCode().value());
            assertEquals("说明", annotations.selectById(noteId).getText());
        } finally { projects.deleteProject(anotherId); }
    }
    @Test void deletingNoteLeavesElementAndRelationshipIntact() {
        long noteId = add(Map.of("elementId", element.getId(), "text", "说明")).path("id").asLong();
        assertEquals(200, http.exchange("/api/projects/" + projectId + "/annotations/" + noteId, HttpMethod.DELETE, HttpEntity.EMPTY, JsonNode.class).getStatusCode().value());
        assertNull(annotations.selectById(noteId)); assertNotNull(elements.selectById(element.getId()));
        assertEquals(1, interactions.selectList(null).stream().filter(i -> i.getElementId().equals(element.getId())).count());
        assertEquals(html, pages.selectById(page.getId()).getHtmlContent());
    }
    @Test void reanalysisCleanupKeepsManualNotesAndTheirLastAnchors() {
        long manualId = add(Map.of("elementId", element.getId(), "title", "兑换条件", "text", "人工填写的规则")).path("id").asLong();
        long pageNoteId = add(Map.of("text", "整页说明")).path("id").asLong();
        Annotation ai = new Annotation(); ai.setPageId(page.getId()); ai.setElementId(element.getId()); ai.setText("自动说明"); annotations.insert(ai);
        AnalyzeService target = AopTestUtils.getUltimateTargetObject(analyze);
        new TransactionTemplate(tx).executeWithoutResult(status -> ReflectionTestUtils.invokeMethod(target, "clearPageData", page.getId().longValue()));
        Annotation manual = annotations.selectById(manualId); assertNotNull(manual); assertNull(manual.getElementId());
        assertEquals("兑换条件", manual.getTitle()); assertEquals("人工填写的规则", manual.getText()); assertEquals(70.0, manual.getPositionX());
        assertNotNull(annotations.selectById(pageNoteId)); assertNull(annotations.selectById(ai.getId())); assertNull(elements.selectById(element.getId()));
    }
}
