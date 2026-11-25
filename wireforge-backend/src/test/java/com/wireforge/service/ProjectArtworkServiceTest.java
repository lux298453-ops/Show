package com.wireforge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ProjectArtworkServiceTest {
    @TempDir Path dir;
    HttpServer server; AtomicInteger calls = new AtomicInteger(); int status = 200;
    final List<com.fasterxml.jackson.databind.JsonNode> requests = new ArrayList<>();

    @BeforeEach void start() throws Exception {
        var mapper = new ObjectMapper();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/images/generations", exchange -> {
            var request = mapper.readTree(exchange.getRequestBody());
            requests.add(request);
            calls.incrementAndGet();
            String[] dimensions = request.path("size").asText().split("x");
            byte[] png = png(Integer.parseInt(dimensions[0]) / 4, Integer.parseInt(dimensions[1]) / 4);
            byte[] response = mapper.writeValueAsBytes(Map.of("data", List.of(Map.of("b64_json", Base64.getEncoder().encodeToString(png)))));
            exchange.sendResponseHeaders(status, response.length); try(var out = exchange.getResponseBody()) { out.write(response); }
        }); server.start();
    }

    private static byte[] png(int width, int height) throws java.io.IOException {
        var out = new java.io.ByteArrayOutputStream();
        var bitmap = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_RGB);
        javax.imageio.ImageIO.write(bitmap, "png", out);
        return out.toByteArray();
    }
    @AfterEach void stop() { server.stop(0); }
    ProjectArtworkService service(boolean enabled) { return new ProjectArtworkService(dir.toString(), "http://127.0.0.1:" + server.getAddress().getPort(), "test-only", "test-image", enabled, 10, 6); }
    private static final ArtworkStyle SIMPLE = new ArtworkStyle("2D flat cartoon", "large head, short body", "clean outlines", "few yellow color blocks", "one light shadow", "no texture", "simple");
    private static final ArtworkStyle INTRICATE = new ArtworkStyle("2D painted illustration", "original proportions", "fine outlines", "gold and silver", "layered shading", "ornate armor", "intricate");
    String html() { return styledHtml(INTRICATE); }
    String styledHtml(ArtworkStyle profile) {
        var doc = Jsoup.parse("<html><head></head><body><div style='height:120px' data-wf-uid='card-art' data-wf-element-id='10' data-wf-artwork-key='角色-形态' data-wf-artwork-name='角色' data-wf-artwork-prompt='原稿中的角色及形态'><svg data-wf-uid='old-illustration'><circle r='5'/></svg></div><button data-nav='详情'>购买</button></body></html>");
        doc.outputSettings().prettyPrint(false);
        doc.selectFirst("[data-wf-artwork-key]").attr(ArtworkStyle.ATTRIBUTE, profile.json());
        return doc.outerHtml();
    }

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

    @Test void recognizedArtworkAreaCorrectsAnIncorrectModelAspectAndKeepsTheHeadshotComposition() {
        var recognized = new com.wireforge.entity.Element();
        recognized.setId(10L);
        recognized.setType("avatar");
        recognized.setWidth(100d);
        recognized.setHeight(100d);
        var draft = Jsoup.parse(styledHtml(SIMPLE));
        var region = draft.selectFirst("[data-wf-artwork-key]");
        region.attr("style", "height:auto;width:100%;border-radius:50%");
        region.attr(ArtworkAspect.ATTRIBUTE, "portrait");
        region.attr("data-wf-artwork-framing", "headshot");

        var result = Jsoup.parse(service(true).resolve(7, draft.outerHtml(), new HashSet<>(), true, List.of(recognized)));

        assertEquals("1024x1024", requests.get(0).path("size").asText());
        assertEquals("medium", requests.get(0).path("quality").asText());
        assertTrue(requests.get(0).path("prompt").asText().contains("Frame only the centered head"));
        assertNotNull(result.selectFirst("img[data-wf-artwork-framing=headshot]"));
        assertEquals(region.attr("style"), result.selectFirst("[data-wf-artwork-key]").attr("style"));
    }

    @Test void aSquareHeadshotAndSquareFullBodyImageHaveSeparateCachedVersions() {
        var service = service(true);
        var draft = Jsoup.parse(styledHtml(SIMPLE));
        var region = draft.selectFirst("[data-wf-artwork-key]");
        region.attr("style", "width:100px;height:100px");
        region.attr("data-wf-artwork-framing", "headshot");
        String headshot = service.resolve(7, draft.outerHtml(), new HashSet<>(), true);

        region.attr("data-wf-artwork-framing", "full-body");
        String fullBody = service.resolve(7, draft.outerHtml(), new HashSet<>(), true);

        assertNotEquals(Jsoup.parse(headshot).selectFirst("img").attr("src"), Jsoup.parse(fullBody).selectFirst("img").attr("src"));
        assertEquals(List.of("1024x1024", "1024x1024"), requests.stream().map(request -> request.path("size").asText()).toList());
    }

    @Test void fixedOrResponsiveImageRegionsUseTheirClosestSpecAndOnlyReuseMatchingProportions() {
        var service = service(true);
        var draft = Jsoup.parse(styledHtml(SIMPLE));
        var region = draft.selectFirst("[data-wf-artwork-key]");
        region.attr("data-wf-artwork-framing", "full-body");
        region.attr("style", "width:100%;aspect-ratio:2/3");
        String portrait = service.resolve(7, draft.outerHtml(), new HashSet<>(), true);
        String portraitUrl = Jsoup.parse(portrait).selectFirst("img").attr("src");
        assertEquals("1024x1536", requests.get(0).path("size").asText());
        assertNotNull(Jsoup.parse(portrait).selectFirst("img[data-wf-artwork-framing=full-body]"));
        assertTrue(requests.get(0).path("prompt").asText().contains("including the feet"));
        assertTrue(portrait.contains("[data-wf-artwork-framing=full-body]:not([data-wf-artwork-layout]){object-fit:contain"));

        region.attr("style", "width:300px;height:150px");
        region.appendElement("img").attr("src", portraitUrl);
        var allowed = new HashSet<>(Set.of(portraitUrl));
        String landscape = service.resolve(7, draft.outerHtml(), allowed, true);
        String landscapeUrl = Jsoup.parse(landscape).selectFirst("img[data-wf-artwork-image]").attr("src");
        assertEquals("1536x1024", requests.get(1).path("size").asText());
        assertNotEquals(portraitUrl, landscapeUrl);
        String repeat = service(true).resolve(7, draft.outerHtml(), allowed, true);
        assertEquals(landscapeUrl, Jsoup.parse(repeat).selectFirst("img[data-wf-artwork-image]").attr("src"));
        assertEquals(2, calls.get());
    }

    @Test void knownAspectDeclarationsStillWorkForFlexibleRegionsWithoutRecognizedDimensions() {
        var draft = Jsoup.parse(styledHtml(SIMPLE));
        draft.selectFirst("[data-wf-artwork-key]").attr("style", "width:100%;height:auto").attr(ArtworkAspect.ATTRIBUTE, "square");
        service(true).resolve(7, draft.outerHtml(), new HashSet<>(), true);
        assertEquals("1024x1024", requests.get(0).path("size").asText());
    }

    @Test void anOlderCachedPngIsReusedOnlyWhenItsDimensionsMatchTheSelectedProportion() throws Exception {
        String oldKey = "style-" + digest(ArtworkStyle.VERSION + "\ntest-image\ncharacter\n角色-形态\n" + SIMPLE.identity());
        String digest = digest(oldKey);
        String oldUrl = "/api/assets/artwork/7/" + digest + ".png";
        var legacy = new ProjectArtworkService.Artwork(oldKey, "角色", "原稿中的角色及形态", oldUrl,
                "character", "角色-形态", ArtworkStyle.VERSION, SIMPLE);
        Path folder = Files.createDirectories(dir.resolve(".wireforge-artwork/7"));
        Files.write(folder.resolve(digest + ".png"), png(256, 256));
        Files.writeString(folder.resolve(digest + ".json"), new ObjectMapper().writeValueAsString(legacy));
        var draft = Jsoup.parse(styledHtml(SIMPLE));
        var region = draft.selectFirst("[data-wf-artwork-key]");
        region.attr("style", "width:100px;height:100px");
        var service = service(true);

        String square = service.resolve(7, draft.outerHtml(), new HashSet<>(), true);
        assertEquals(oldUrl, Jsoup.parse(square).selectFirst("img").attr("src"));
        assertEquals(0, calls.get());

        region.attr("style", "width:100px;height:150px");
        String portrait = service.resolve(7, draft.outerHtml(), new HashSet<>(), true);
        assertNotEquals(oldUrl, Jsoup.parse(portrait).selectFirst("img").attr("src"));
        assertEquals("1024x1536", requests.get(0).path("size").asText());
        assertTrue(service.image(7, digest).exists());
    }

    private static String digest(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
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

    @Test void sameCharacterInDifferentStylesHasSeparateImmutableImagesAndReusesOnlyItsMatchingStyle() throws Exception {
        var service = service(true);
        String simple = service.resolve(7, styledHtml(SIMPLE), new HashSet<>(), true);
        String simpleUrl = Jsoup.parse(simple).selectFirst("img").attr("src");
        String intricate = service.resolve(7, styledHtml(INTRICATE), new HashSet<>(), true);
        String intricateUrl = Jsoup.parse(intricate).selectFirst("img").attr("src");
        assertNotEquals(simpleUrl, intricateUrl);
        assertEquals(2, calls.get());
        assertEquals(simpleUrl, Jsoup.parse(service(true).resolve(7, styledHtml(SIMPLE), new HashSet<>(), true)).selectFirst("img").attr("src"));
        assertEquals(2, calls.get(), "same character and reference style still reuse across pages and restarts");
        assertEquals(SIMPLE, service(true).assets(7).stream().filter(a -> a.url().equals(simpleUrl)).findFirst().orElseThrow().styleProfile());
        assertTrue(requests.get(0).path("prompt").asText().contains(SIMPLE.json()));
        assertTrue(requests.get(1).path("prompt").asText().contains(INTRICATE.json()));
        assertFalse(requests.get(0).path("prompt").asText().contains("Detailed face, hair or fur"));
        assertNotNull(Jsoup.parse(simple).selectFirst("svg[data-wf-uid=old-illustration]"));
        assertEquals("10", Jsoup.parse(simple).selectFirst("[data-wf-artwork-key]").attr("data-wf-element-id"));
        assertEquals("详情", Jsoup.parse(simple).selectFirst("button").attr("data-nav"));
    }

    @Test void mixedPageKeepsSimpleCharacterAndIntricateBackgroundProfilesIndependent() {
        var doc = Jsoup.parse(styledHtml(SIMPLE));
        var background = doc.body().appendElement("figure").attr("data-wf-uid", "background")
                .attr("data-wf-artwork-key", "场景").attr("data-wf-artwork-name", "场景").attr("data-wf-artwork-kind", "background")
                .attr("data-wf-artwork-prompt", "原稿背景及留白").attr(ArtworkStyle.ATTRIBUTE, INTRICATE.json());
        background.appendElement("svg").attr("data-wf-uid", "background-fallback");
        var result = Jsoup.parse(service(true).resolve(7, doc.outerHtml(), new HashSet<>(), true));
        assertEquals(2, calls.get());
        assertTrue(requests.get(0).path("prompt").asText().contains(SIMPLE.json()));
        assertTrue(requests.get(1).path("prompt").asText().contains(INTRICATE.json()));
        assertTrue(requests.get(1).path("prompt").asText().contains("background illustration"));
        assertEquals(2, result.select("img[data-wf-artwork-image]").size());
    }

    @Test void oldCachedPicturesStayReadableButAreExcludedFromNewStyleCandidatesAndDoNotOverrideTheReference() throws Exception {
        var service = service(true);
        String first = service.resolve(7, html(), new HashSet<>(), true);
        String oldUrl = Jsoup.parse(first).selectFirst("img").attr("src");
        var folder = dir.resolve(".wireforge-artwork/7");
        Path metadata;
        try (var files = java.nio.file.Files.list(folder)) { metadata = files.filter(p -> p.toString().endsWith(".json")).findFirst().orElseThrow(); }
        var json = new ObjectMapper();
        var old = json.readTree(java.nio.file.Files.readString(metadata));
        ((com.fasterxml.jackson.databind.node.ObjectNode) old).remove(List.of("styleProfile", "promptVersion", "sourceKey"));
        java.nio.file.Files.writeString(metadata, old.toString());
        assertTrue(service.reusableAssets(7).isEmpty());
        assertFalse(service.prompt(7).contains(oldUrl));
        assertTrue(service.image(7, oldUrl.replaceAll("^.*/|\\.png$", "")).exists());
        var next = Jsoup.parse(styledHtml(SIMPLE));
        next.selectFirst("[data-wf-artwork-key]").appendElement("img").attr("src", oldUrl).attr("data-wf-uid", "old-cached-image");
        var updated = Jsoup.parse(service.resolve(7, next.outerHtml(), new HashSet<>(Set.of(oldUrl)), true));
        assertNotEquals(oldUrl, updated.selectFirst("img[data-wf-artwork-image]").attr("src"));
        assertNotNull(updated.selectFirst("[data-wf-uid=old-cached-image]"), "previous identities remain available for editing/undo");
        assertEquals(2, calls.get());
    }

    @Test void databaseOnlyStyleMetadataSurvivesDiskLossWithoutLoadingImageBlobs() throws Exception {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), "artwork-test"),
                com.wireforge.entity.ArtworkAsset.class);
        var service = service(true);
        service.resolve(7, styledHtml(SIMPLE), new HashSet<>(), true);
        var item = service.assets(7).get(0);
        var mapper = org.mockito.Mockito.mock(com.wireforge.mapper.ArtworkAssetMapper.class);
        var asset = com.wireforge.entity.ArtworkAsset.builder().artworkKey(item.key()).name(item.name()).prompt(item.prompt()).kind(item.kind())
                .digest(item.url().replaceAll("^.*/|\\.png$", "")).jsonData(new ObjectMapper().writeValueAsString(item)).build();
        org.mockito.Mockito.when(mapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(asset));
        var restored = new ProjectArtworkService(dir.resolve("empty").toString(), "http://127.0.0.1", "test", "test-image", true, 10, 6, mapper);
        assertEquals(SIMPLE, restored.reusableAssets(7).get(0).styleProfile());
        assertTrue(restored.prompt(7).contains(item.url()));
        var wrapper = org.mockito.ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);
        org.mockito.Mockito.verify(mapper, org.mockito.Mockito.atLeastOnce()).selectList(wrapper.capture());
        assertFalse(wrapper.getValue().getSqlSelect().contains("image_data"));
    }

    @Test void changingStyleOnExistingHtmlKeepsUniqueIdentitiesAndDoesNotTreatHiddenOldArtworkAsTheActiveImage() {
        var service = service(true); Set<String> allowed = new HashSet<>();
        String first = service.resolve(7, styledHtml(SIMPLE), allowed, true);
        String firstUrl = Jsoup.parse(first).selectFirst("img[data-wf-artwork-image]").attr("src");
        var changed = Jsoup.parse(first); changed.selectFirst("[data-wf-artwork-key]").attr(ArtworkStyle.ATTRIBUTE, INTRICATE.json());
        String second = service.resolve(7, changed.outerHtml(), allowed, true);
        assertNotEquals(firstUrl, Jsoup.parse(second).selectFirst("img[data-wf-artwork-image]").attr("src"));
        assertTrue(AiAdjustmentContract.validatePage(first, second, "重新生成角色图片"));
        changed = Jsoup.parse(second); changed.selectFirst("[data-wf-artwork-key]").attr(ArtworkStyle.ATTRIBUTE, SIMPLE.json());
        String third = service.resolve(7, changed.outerHtml(), allowed, true);
        assertEquals(firstUrl, Jsoup.parse(third).selectFirst("img[data-wf-artwork-image]").attr("src"));
        assertTrue(AiAdjustmentContract.validatePage(second, third, "重新生成角色图片"));
        assertEquals(2, calls.get());
    }
}
