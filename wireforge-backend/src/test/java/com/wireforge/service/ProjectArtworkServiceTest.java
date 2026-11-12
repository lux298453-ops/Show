package com.wireforge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ProjectArtworkServiceTest {
    @TempDir Path dir;
    HttpServer server; AtomicInteger calls = new AtomicInteger(); int status = 200;
    final List<com.fasterxml.jackson.databind.JsonNode> requests = new ArrayList<>();

    @BeforeEach void start() throws Exception {
        var png = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(256, 384, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", png);
        String body = new ObjectMapper().writeValueAsString(Map.of("data", List.of(Map.of("b64_json", Base64.getEncoder().encodeToString(png.toByteArray())))));
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/images/generations", exchange -> {
            calls.incrementAndGet(); requests.add(new ObjectMapper().readTree(exchange.getRequestBody()));
            byte[] response = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, response.length); try(var out = exchange.getResponseBody()) { out.write(response); }
        }); server.start();
    }
    @AfterEach void stop() { server.stop(0); }
    ProjectArtworkService service(boolean enabled) { return new ProjectArtworkService(dir.toString(), "http://127.0.0.1:" + server.getAddress().getPort(), "test-only", "test-image", enabled, 10, 6); }
    String html() { return "<html><head></head><body><div style='height:120px' data-wf-uid='card-art' data-wf-element-id='10' data-wf-artwork-key='角色-形态' data-wf-artwork-name='角色' data-wf-artwork-prompt='Long silver hair and ornate golden armor'><svg data-wf-uid='old-illustration'><circle r='5'/></svg></div><button data-nav='详情'>购买</button></body></html>"; }

    @Test void generatesOnceThenReusesAcrossPagesServiceRestartsAndRepairsWithProjectIsolation() {
        Set<String> allowed = new HashSet<>(); ProjectArtworkService service = service(true);
        String first = service.resolve(7, html(), allowed, true);
        var doc = Jsoup.parse(first); var image = doc.selectFirst("img[data-wf-artwork-image]");
        assertNotNull(image); assertTrue(allowed.contains(image.attr("src")));
        assertEquals("10", doc.selectFirst("[data-wf-artwork-key]").attr("data-wf-element-id"));
        assertEquals("详情", doc.selectFirst("button").attr("data-nav"));
        assertNotNull(doc.selectFirst("svg[data-wf-uid=old-illustration][data-wf-artwork-fallback]"));
        String second = service(true).resolve(7, html(), new HashSet<>(), false);
        assertEquals(image.attr("src"), Jsoup.parse(second).selectFirst("img").attr("src"));
        assertEquals(1, calls.get()); assertEquals(first, service.resolve(7, first, allowed, true));
        assertTrue(service.assets(8).isEmpty());
        assertFalse(service.resolve(8, html(), new HashSet<>(), false).contains("data-wf-artwork-image"));
        assertTrue(service.prompt(7).contains(image.attr("src")));
    }
    @Test void originalsTakePriorityAndDisabledGenerationOrRepairNeverCallsImageProvider() {
        String original = html().replace("<svg ", "<img src='/api/assets/original'><svg ");
        assertEquals(original, service(true).resolve(7, original, new HashSet<>(Set.of("/api/assets/original")), true));
        assertEquals(html(), service(false).resolve(7, html(), new HashSet<>(), true));
        assertEquals(html(), service(true).resolve(7, html(), new HashSet<>(), false));
        assertEquals(0, calls.get());
    }
    @Test void failedImageCallsKeepThePreviousIllustrationAndAreNotRetriedForRepeatedCharacters() {
        status = 503; String duplicate = html().replace("</body>", html()+"</body>");
        String result = service(true).resolve(7, duplicate, new HashSet<>(), true);
        assertEquals(duplicate, result); assertEquals(1, calls.get()); assertTrue(service(true).assets(7).isEmpty());
    }

    @Test void repairRestoresArtworkStylesWithoutGeneratingOrDuplicatingImages() {
        Set<String> allowed = new HashSet<>(); ProjectArtworkService service = service(true);
        String first = service.resolve(7, html(), allowed, true);
        var damaged = Jsoup.parse(first); damaged.select("style[data-wf-artwork-style]").remove();
        String repaired = service.resolve(7, damaged.outerHtml(), allowed, false);
        var doc = Jsoup.parse(repaired);
        assertEquals(1, calls.get());
        assertEquals(1, doc.select("img[data-wf-artwork-image]").size());
        assertNotNull(doc.selectFirst("style[data-wf-artwork-style]"));
        assertTrue(doc.selectFirst("style[data-wf-artwork-style]").data().contains("display:none!important"));
        assertNotNull(doc.selectFirst("svg[data-wf-uid=old-illustration]"));
    }

    @Test void adjustmentImagesHaveImmutableVersionsAndRetrySurvivesServiceRestart() throws Exception {
        var service = service(true);
        var original = service.generateAdjustment(7, 1, "request-1", "source", "生成背景图", "id=scene", "background", "背景", "森林营地，有留白", "landscape");
        byte[] originalBytes = service.image(7, original.url().replaceAll("^.*/|\\.png$", "")).getInputStream().readAllBytes();
        var retry = service(true).generateAdjustment(7, 1, "request-1", "source", "生成背景图", "id=scene", "background", "背景", "模型第二次表达稍有差别", "landscape");
        assertEquals(original.url(), retry.url());
        assertEquals("background", retry.kind());
        assertEquals(1, calls.get());
        assertEquals("1536x1024", requests.get(0).path("size").asText());
        assertTrue(requests.get(0).path("prompt").asText().contains("background illustration"));
        assertFalse(requests.get(0).path("prompt").asText().contains("Create ONE polished game character"));
        var next = service.generateAdjustment(7, 1, "request-2", "source", "生成背景图", "id=scene", "background", "背景", "森林营地，有留白", "portrait");
        assertNotEquals(original.url(), next.url());
        assertArrayEquals(originalBytes, service.image(7, original.url().replaceAll("^.*/|\\.png$", "")).getInputStream().readAllBytes());
        assertEquals(2, calls.get());
        assertTrue(service.assets(8).isEmpty());
    }

    @Test void partialAdjustmentImagesAreReusedOnManualRetryWithoutOverwritingOriginals() {
        var service = service(true);
        var first = service.generateAdjustment(7, 1, "batch", "source", "生成角色和背景图片", "id=pet", "character", "角色", "精细角色", "square");
        status = 503;
        var error = assertThrows(IllegalStateException.class, () -> service.generateAdjustment(7, 1, "batch", "source", "生成角色和背景图片", "id=scene", "background", "背景", "森林", "portrait"));
        assertTrue(error.getMessage().contains("原画板已保留"));
        assertEquals(2, calls.get(), "failed provider calls are not automatically repeated");
        status = 200;
        assertEquals(first.url(), service(true).generateAdjustment(7, 1, "batch", "source", "生成角色和背景图片", "id=pet", "character", "角色", "精细角色", "square").url());
        service.generateAdjustment(7, 1, "batch", "source", "生成角色和背景图片", "id=scene", "background", "背景", "森林", "portrait");
        assertEquals(3, calls.get());
        assertEquals(2, service.assets(7).size());
    }

    @Test void disabledAdjustmentImagesFailClearlyWithoutCallingProvider() {
        assertThrows(IllegalStateException.class, () -> service(false).generateAdjustment(7, 1, "request", "source", "生成图片", "id=pet", "character", "宠物", "精细宠物", "portrait"));
        assertEquals(0, calls.get());
    }
}
