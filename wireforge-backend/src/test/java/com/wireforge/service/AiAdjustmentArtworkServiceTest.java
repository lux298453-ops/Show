package com.wireforge.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AiAdjustmentArtworkServiceTest {
    private final ObjectMapper json = new ObjectMapper();
    private final ProjectArtworkService artwork = mock(ProjectArtworkService.class);
    private final AiAdjustmentArtworkService service = new AiAdjustmentArtworkService(artwork);
    private static final String URL = "/api/assets/artwork/7/generated.png";
    private static final String PAGE = "<!doctype html><html><head></head><body><div id=\"scene\" style=\"height:600px\"><img id=\"pet\" src=\"old.png\"><button id=\"buy\" data-nav=\"商城\">购买</button></div><div id=\"stage\" style=\"position:absolute;width:120px;height:160px\"><svg id=\"old-art\"></svg></div></body></html>";

    @BeforeEach void setup() {
        when(artwork.generateAdjustment(anyLong(), anyLong(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new ProjectArtworkService.Artwork("key", "图片", "描述", URL));
    }

    private Map<String, String> task(String id, String kind) {
        return Map.of("target_attribute", "id", "target_id", id, "kind", kind, "name", "图片", "prompt", "精细游戏插画，暖色调，不含文字", "aspect_ratio", "portrait");
    }
    private JsonNode tasks(Map<String, String>... values) { return json.valueToTree(List.of(values)); }
    private AiAdjustmentArtworkService.Result apply(String instruction, JsonNode tasks) {
        return service.apply(7, 1, "request-1", PAGE, instruction, PAGE, false, tasks);
    }

    @Test void imageIntentExcludesOrdinaryEditsAndExplicitNegations() {
        for (String text : List.of("把角色向下移动", "把背景颜色换成蓝色", "不要生成图片，只修改文字", "背景图不用重新生成", "do not generate an image", "不要生成图片，用原素材替换背景图")) {
            assertFalse(AiAdjustmentArtworkService.wantsImages(text), text);
            assertEquals(PAGE, apply(text, json.createArrayNode()).html());
            assertThrows(IllegalStateException.class, () -> apply(text, tasks(task("pet", "character"))), text);
        }
        assertTrue(AiAdjustmentArtworkService.wantsImages("把文字下移，再重新生成背景图"));
        assertTrue(AiAdjustmentArtworkService.wantsImages("不要生成头像，重新生成背景图"));
        verifyNoInteractions(artwork);
    }

    @Test void validatesAllTargetsBeforeGeneratingAnything() {
        for (JsonNode plan : List.of(tasks(task("pet", "character"), task("missing", "background")),
                tasks(task("pet", "character"), task("pet", "background")),
                tasks(task("pet", "character"), task("scene", "background")),
                tasks(task("scene", "character")), tasks(task("old-art", "illustration")),
                tasks(task("pet", "character"), task("stage", "character"), task("scene", "background"), task("missing", "background")))) {
            assertThrows(IllegalStateException.class, () -> apply("重新生成图片", plan));
        }
        verifyNoInteractions(artwork);
    }

    @Test void rejectsInvalidPlanShapesAndRequestIdsBeforeCallingProvider() {
        assertThrows(IllegalStateException.class, () -> apply("生成背景图", json.createObjectNode()));
        assertThrows(IllegalStateException.class, () -> apply("生成背景图", json.createArrayNode()));
        Map<String, String> invalid = new java.util.HashMap<>(task("pet", "character"));
        invalid.put("target_attribute", "id][src]");
        assertThrows(IllegalStateException.class, () -> apply("生成图片", tasks(invalid)));
        assertThrows(IllegalStateException.class, () -> service.apply(7, 1, "../other", PAGE, "生成图片", PAGE, false, tasks(task("pet", "character"))));
        verifyNoInteractions(artwork);
    }

    @Test void changesBackgroundWithoutReplacingItsButtonsOrLayers() {
        var result = apply("重新生成背景图", tasks(task("scene", "background")));
        var doc = Jsoup.parse(result.html());
        assertTrue(doc.getElementById("scene").attr("style").contains(URL));
        assertTrue(doc.getElementById("scene").attr("style").contains("height:600px"));
        assertEquals("商城", doc.getElementById("buy").attr("data-nav"));
        assertEquals("old.png", doc.getElementById("pet").attr("src"));
        assertTrue(AiAdjustmentContract.validatePage(PAGE, result.html(), "重新生成背景图"));
    }

    @Test void imageReplacementDisablesResponsiveSourcesThatWouldOverrideIt() {
        String source = "<picture id=\"portrait\"><source srcset=\"old-large.png\"><img id=\"pet\" src=\"old.png\" srcset=\"old-small.png 1x\"></picture>";
        var result = service.apply(7, 1, "request-2", source, "重绘宠物图片", source, true, tasks(task("pet", "character")));
        var doc = Jsoup.parseBodyFragment(result.html());
        assertEquals(1, doc.body().childrenSize());
        assertEquals(URL, doc.getElementById("pet").attr("src"));
        assertFalse(doc.getElementById("pet").hasAttr("srcset"));
        assertFalse(doc.selectFirst("source").hasAttr("srcset"));
    }

    @Test void pureIllustrationKeepsFallbackIdentifiersAndItsOriginalPosition() {
        var result = apply("重新生成角色图片", tasks(task("stage", "character")));
        var doc = Jsoup.parse(result.html());
        assertNotNull(doc.getElementById("old-art"));
        assertTrue(doc.getElementById("old-art").attr("style").contains("display:none!important"));
        assertTrue(doc.getElementById("stage").attr("style").contains("position:absolute"));
        assertFalse(doc.getElementById("stage").attr("style").contains("position:relative"));
        assertEquals(URL, doc.selectFirst("#stage img").attr("src"));
        assertTrue(AiAdjustmentContract.validatePage(PAGE, result.html(), "重新生成角色图片"));
    }

    @Test void imageFailureDoesNotReturnAnAppliedHtmlResult() {
        when(artwork.generateAdjustment(anyLong(), anyLong(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("图片生成失败，原画板已保留"));
        assertThrows(IllegalStateException.class, () -> apply("重新生成背景图", tasks(task("scene", "background"))));
        assertFalse(PAGE.contains(URL));
    }

    @Test void imageBackgroundCannotBeInstalledOnAnSvgDescendant() {
        String source = PAGE.replace("<svg id=\"old-art\"></svg>", "<svg id=\"old-art\"><g id=\"group\"></g></svg>");
        assertThrows(IllegalStateException.class, () -> service.apply(7, 1, "request", source, "生成背景图", source, false, tasks(task("group", "background"))));
        verifyNoInteractions(artwork);
    }

    private static final String SVG_PAGE = "<!doctype html><html><head></head><body><main id=\"stage\">"
            + "<svg id=\"scene-art\" data-wf-uid=\"scene-uid\" data-wf-role=\"image\" class=\"room\" viewBox=\"0 0 375 675\" width=\"375\" height=\"675\" style=\"position:absolute;inset:0;z-index:0\">"
            + "<title>暖橙色家园背景</title><defs><linearGradient id=\"wall\"><stop stop-color=\"orange\"/></linearGradient></defs>"
            + "<g id=\"walls\"><rect id=\"wall-shape\" width=\"375\" height=\"675\" fill=\"url(#wall)\"/></g></svg>"
            + "<button id=\"collect\" data-nav=\"收集\"><svg id=\"collect-icon\" viewBox=\"0 0 24 24\"><path d=\"M1 2L3 4\"/></svg>收集</button>"
            + "<span id=\"tip\">获得1个新装扮</span></main></body></html>";

    @Test void standaloneSvgBackgroundReplacesOnlyItsArtworkAndRetainsTheOriginalRootAndFallbacks() {
        var result = service.apply(7, 1, "svg-background", SVG_PAGE, "重新生成背景图", SVG_PAGE, false, tasks(task("scene-art", "background")));
        var before = Jsoup.parse(SVG_PAGE); var after = Jsoup.parse(result.html());
        var root = after.getElementById("scene-art"); var image = root.selectFirst("image[data-wf-adjustment-image]");
        assertNotNull(image); assertEquals(URL, image.attr("href"));
        for (String attr : List.of("id", "data-wf-uid", "data-wf-role", "class", "viewBox", "width", "height", "style")) {
            assertEquals(before.getElementById("scene-art").attr(attr), root.attr(attr), attr);
        }
        assertEquals("375.0", image.attr("width")); assertEquals("675.0", image.attr("height"));
        assertEquals("xMidYMid slice", image.attr("preserveAspectRatio"));
        assertTrue(after.getElementById("walls").attr("style").contains("display:none!important"));
        assertNotNull(after.getElementById("wall-shape")); assertEquals(before.getElementById("wall").outerHtml(), after.getElementById("wall").outerHtml());
        assertEquals(before.getElementById("collect").outerHtml(), after.getElementById("collect").outerHtml());
        assertEquals(before.getElementById("tip").outerHtml(), after.getElementById("tip").outerHtml());
        assertTrue(AiAdjustmentContract.validatePage(SVG_PAGE, result.html(), "重新生成背景图"));
        verify(artwork, times(1)).generateAdjustment(anyLong(), anyLong(), anyString(), anyString(), anyString(), anyString(), eq("background"), anyString(), anyString(), anyString());
    }

    @Test void svgImageRegenerationUpdatesTheExistingManagedImageWithoutAccumulatingLayers() {
        var first = service.apply(7, 1, "first", SVG_PAGE, "生成背景图", SVG_PAGE, false, tasks(task("scene-art", "background")));
        var firstImage = Jsoup.parse(first.html()).selectFirst("image[data-wf-adjustment-image]");
        when(artwork.generateAdjustment(anyLong(), anyLong(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new ProjectArtworkService.Artwork("next", "图片", "描述", "/api/assets/artwork/7/next.png"));
        var second = service.apply(7, 1, "second", first.html(), "重新生成背景图", first.html(), false, tasks(task("scene-art", "background")));
        var doc = Jsoup.parse(second.html());
        assertEquals(1, doc.select("image[data-wf-adjustment-image]").size());
        assertEquals(firstImage.attr("data-wf-uid"), doc.selectFirst("image[data-wf-adjustment-image]").attr("data-wf-uid"));
        assertEquals("/api/assets/artwork/7/next.png", doc.selectFirst("image").attr("href"));
        assertNotNull(doc.getElementById("wall-shape"));
        assertTrue(AiAdjustmentContract.validatePage(first.html(), second.html(), "重新生成背景图"));
    }

    @Test void selectedSvgSupportsOffsetsAndExplicitPixelDimensionsWithoutChangingItsBox() {
        for (String geometry : List.of("viewBox=\"-10,20,200,300\"", "width=\"200px\" height=\"300px\"")) {
            String source = "<svg id=\"portrait\" " + geometry + "><g id=\"original\"><circle r=\"50\"/></g></svg>";
            var result = service.apply(7, 1, "svg-portrait", source, "重新生成角色图片", source, true, tasks(task("portrait", "character")));
            var doc = Jsoup.parseBodyFragment(result.html());
            assertEquals(1, doc.body().childrenSize());
            assertEquals(geometry.startsWith("viewBox") ? "-10.0" : "0.0", doc.selectFirst("image").attr("x"));
            assertEquals("xMidYMid meet", doc.selectFirst("image").attr("preserveAspectRatio"));
            assertTrue(AiAdjustmentContract.validateElement(source, result.html(), "重新生成角色图片"));
        }
    }

    @Test void interactiveSvgsAndInvalidGeometryAreRejectedBeforeAnyImageCall() {
        for (String svg : List.of("<svg id=\"scene-art\" viewBox=\"0 0 200 300\"><a href=\"#next\"><rect/></a></svg>",
                "<svg id=\"scene-art\" viewBox=\"0 0 200 300\"><g data-action=\"back\"><path/></g></svg>",
                "<svg id=\"scene-art\" viewBox=\"0 0 200 300\"><foreignObject><div>UI</div></foreignObject></svg>",
                "<svg id=\"scene-art\" viewBox=\"0 0 200 300\"><text>界面文字</text></svg>",
                "<svg id=\"scene-art\" viewBox=\"0 0 200 300\" onclick=\"alert(1)\"><path/></svg>",
                "<svg id=\"scene-art\" viewBox=\"0 0 NaN 300\"><path/></svg>",
                "<svg id=\"scene-art\" viewBox=\"0 0 -2 300\"><path/></svg>",
                "<svg id=\"scene-art\" width=\"100%\"><path/></svg>")) {
            String source = PAGE.replace("</body>", svg + "</body>");
            assertThrows(IllegalStateException.class, () -> service.apply(7, 1, "invalid-svg", source, "生成背景图", source, false,
                    tasks(task("pet", "character"), task("scene-art", "background"))), svg);
        }
        verifyNoInteractions(artwork);
    }

    @Test void imageTargetContextListsSupportedSvgRootsAndExcludesPathsAndAmbiguousIds() throws Exception {
        String source = SVG_PAGE.replace("</body>", "<img id=\"duplicate\" src=\"a.png\"><img id=\"duplicate\" src=\"b.png\"></body>");
        String context = service.targetContext(source, false, "重新生成背景图");
        JsonNode candidates = json.readTree(context.substring(context.indexOf("\n[") + 1).strip());
        var room = java.util.stream.StreamSupport.stream(candidates.spliterator(), false).filter(node -> node.path("target_id").asText().equals("scene-art")).findFirst().orElseThrow();
        assertEquals("svg", room.path("element").asText());
        assertTrue(room.path("kinds").toString().contains("background"));
        assertFalse(context.contains("\"target_id\":\"walls\""));
        assertFalse(context.contains("\"target_id\":\"wall-shape\""));
        assertFalse(context.contains("\"target_id\":\"duplicate\""));
        assertEquals("", service.targetContext(source, false, "下移提示文字"));
    }

    @Test void ordinaryTextEditsCannotSilentlyReplaceAnExistingSvgImageUrl() {
        for (String attribute : List.of("href", "xlink:href")) {
            String source = SVG_PAGE.replace("</svg>", "<image " + attribute + "=\"old.png\"/></svg>");
            assertThrows(IllegalStateException.class, () -> AiAdjustmentContract.validatePage(source, source.replace("old.png", "other.png"), "修改提示文字"));
        }
    }
}
