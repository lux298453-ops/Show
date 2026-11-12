package com.wireforge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wireforge.entity.Element;
import com.wireforge.entity.Interaction;
import com.wireforge.entity.Page;
import com.wireforge.mapper.*;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AutowireRenderPreservationTest {
    @Test void maintenanceRebindsStaticNodesAndClearsRemovedLinesWithoutRecreatingEditedMarkup() throws Exception {
        var jdbc = mock(JdbcTemplate.class); var json = new ObjectMapper(); var pages = mock(PageMapper.class);
        var elements = mock(ElementMapper.class); var lines = mock(InteractionMapper.class);
        var tx = mock(PlatformTransactionManager.class); when(tx.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        var service = new AutowireRenderService(jdbc, json, pages, elements, lines, null, tx);
        Page page = new Page(); page.setId(1L); page.setProjectId(7L); page.setName("商城");
        String edited = "<!doctype html><html><head><style>.card{border-radius:17px;color:#135}</style></head><body><article class='card' data-wf-id='card'><button data-wf-element-id='10' data-wf-uid='wf-p1-e10' data-nav='旧页面'>人工编辑后的购买</button></article></body></html>";
        String old = AutowireRenderService.patchHtml(edited, "[{\"pageId\":1,\"elementId\":10,\"label\":\"购买\",\"type\":\"button\",\"group\":\"\",\"domUid\":\"wf-p1-e10\",\"x\":8,\"y\":300,\"width\":88,\"height\":30,\"action\":\"navigate\",\"targetId\":2,\"targetName\":\"旧页面\"}]", "{}");
        page.setHtmlContent(old);
        Element anchor = new Element(); anchor.setId(10L); anchor.setPageId(1L); anchor.setLabel("购买"); anchor.setType("button");
        when(pages.selectList(any())).thenReturn(List.of(page)); when(pages.selectById(1L)).thenReturn(page);
        when(elements.selectList(any())).thenReturn(List.of(anchor)); when(lines.selectList(any())).thenReturn(List.of());
        when(jdbc.queryForObject(anyString(), eq(String.class), eq(7L))).thenReturn("");
        var saved = new AtomicReference<String>();
        when(jdbc.update(eq("UPDATE page SET html_content=? WHERE id=? AND html_content=?"), anyString(), eq(1L), eq(old)))
                .thenAnswer(call -> { saved.set(call.getArgument(1)); return 1; });
        service.refreshProjectBindings(7L);
        assertNotNull(saved.get());
        var doc = Jsoup.parse(saved.get());
        assertEquals("人工编辑后的购买", doc.selectFirst("button").text());
        assertEquals("wf-p1-e10", doc.selectFirst("button").attr("data-wf-uid"));
        assertTrue(doc.selectFirst("style").data().contains("border-radius:17px"));
        var payload = json.readTree(doc.getElementById("wf-autowire-bindings").data());
        assertEquals(1, payload.size());
        assertEquals("none", payload.get(0).path("action").asText());
        assertEquals(1, doc.select("#wf-autowire-bindings").size());
    }
}
