package com.wireforge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wireforge.ai.AiClient;
import com.wireforge.entity.Page;
import com.wireforge.mapper.PageMapper;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AiAdjustServiceTest {
    private final PageMapper pages = mock(PageMapper.class);
    private final AiClient ai = mock(AiClient.class);
    private final ObjectMapper json = new ObjectMapper();
    private final ProjectArtworkService artwork = mock(ProjectArtworkService.class);
    private final AiAdjustService service = new AiAdjustService(pages, ai, json, new AiAdjustmentArtworkService(artwork));
    private final String original = page("<button id=\"buy\" data-nav=\"背包\">购买</button><img id=\"pet\" src=\"/api/assets/pet.png\">");

    private static String page(String body) { return "<!doctype html><html><head></head><body>" + body + "</body></html>"; }

    @BeforeEach void setup() {
        Page page = new Page(); page.setId(1L); page.setProjectId(7L); page.setName("商城"); page.setHtmlContent(original);
        when(pages.selectById(1L)).thenReturn(page);
    }

    private void fullResponse(String html) throws Exception {
        when(ai.generateHtmlText(anyString(), anyString())).thenReturn(json.writeValueAsString(Map.of(
                "summary", "调整方案", "modified_html", html, "diff_list", List.of("修改按钮"))));
    }

    @Test void acceptsValidEditAndKeepsBoundedConversationContext() throws Exception {
        String changed = original.replace("购买", "立即购买"); fullResponse(changed);
        AiAdjustResponse response = service.adjustPage(7L, 1L, "修改购买文案", original, "user: 使用蓝色按钮");
        assertTrue(response.isChanged()); assertEquals(1L, response.getPageId()); assertEquals(changed, response.getModifiedHtml());
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(ai).generateHtmlText(anyString(), prompt.capture()); assertTrue(prompt.getValue().contains("使用蓝色按钮"));
    }

    @Test void unchangedIsDistinctFromInvalidOutput() throws Exception {
        fullResponse(original);
        assertFalse(service.adjustPage(7L, 1L, "检查文案", original).isChanged());
        when(ai.generateHtmlText(anyString(), anyString())).thenReturn("{\"summary\":\"完成\"}");
        assertThrows(IllegalStateException.class, () -> service.adjustPage(7L, 1L, "改颜色", original));
        when(ai.generateHtmlText(anyString(), anyString())).thenReturn("{\"modified_html\":\"<html><body>截断\"}");
        assertThrows(IllegalStateException.class, () -> service.adjustPage(7L, 1L, "改颜色", original));
    }

    @Test void rejectsAccidentalIdentityInteractionAndImageDamage() throws Exception {
        for (String invalid : List.of(original.replace("id=\"buy\"", "id=\"other\""),
                original.replace("data-nav=\"背包\"", "data-nav=\"抽奖\""), original.replace("/api/assets/pet.png", "broken.png"),
                original.replace("</body>", "<div id=\"buy\"></div></body>"))) {
            fullResponse(invalid);
            assertThrows(IllegalStateException.class, () -> service.adjustPage(7L, 1L, "按钮改蓝", original));
        }
    }

    @Test void intentionalNavigationAndDeletionRemainPossible() throws Exception {
        fullResponse(original.replace("data-nav=\"背包\"", "data-nav=\"抽奖\""));
        assertTrue(service.adjustPage(7L, 1L, "购买按钮跳转到抽奖", original).isChanged());
        fullResponse(page("<img id=\"pet\" src=\"/api/assets/pet.png\">"));
        assertTrue(service.adjustPage(7L, 1L, "删除购买按钮", original).isChanged());
    }

    private AiElementAdjustRequest element() {
        return AiElementAdjustRequest.builder().elementId("buy").elementTag("button")
                .elementHtml("<button id=\"buy\" data-nav=\"背包\">购买</button>")
                .instruction("按钮改蓝").layoutContext(".buy { display:flex }").conversationContext("使用品牌色").build();
    }

    @Test void elementRejectsInvalidJsonMissingSnippetMultipleRootsAndUnexpectedDeletion() throws Exception {
        for (String invalid : List.of("Sorry, I cannot edit this", "{\"action\":\"REPLACE\"}",
                json.writeValueAsString(Map.of("action", "REPLACE", "modified_snippet", "<button id=\"buy\">A</button><div>B</div>")),
                "{\"action\":\"DELETE\"}", "{\"action\":\"RUN_SCRIPT\"}")) {
            when(ai.generateHtmlText(anyString(), anyString())).thenReturn(invalid);
            assertThrows(IllegalStateException.class, () -> service.adjustElement(7L, 1L, element()));
        }
    }

    @Test void localEditHasVisualContextAndPreservesContracts() throws Exception {
        String modified = "<button id=\"buy\" data-nav=\"背包\" style=\"color:blue\">购买</button>";
        when(ai.generateHtmlText(anyString(), anyString())).thenReturn(json.writeValueAsString(Map.of("action", "REPLACE", "modified_snippet", modified)));
        var result = service.adjustElement(7L, 1L, element());
        assertTrue(result.isChanged()); assertEquals("REPLACE", result.getAction()); assertEquals("buy", result.getElementId());
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class); verify(ai).generateHtmlText(anyString(), prompt.capture());
        assertTrue(prompt.getValue().contains("display:flex")); assertTrue(prompt.getValue().contains("使用品牌色"));
    }

    @Test void elementNoChangeAndExplicitDeleteAreValidOutcomes() {
        when(ai.generateHtmlText(anyString(), anyString())).thenReturn("{\"action\":\"NO_CHANGE\"}");
        assertFalse(service.adjustElement(7L, 1L, element()).isChanged());
        AiElementAdjustRequest request = element(); request.setInstruction("删除按钮");
        when(ai.generateHtmlText(anyString(), anyString())).thenReturn("{\"action\":\"DELETE\"}");
        assertEquals("DELETE", service.adjustElement(7L, 1L, request).getAction());
    }

    @Test void wrongProjectNeverCallsAi() {
        assertThrows(IllegalArgumentException.class, () -> service.adjustPage(99L, 1L, "修改", original));
        verifyNoInteractions(ai);
    }

    @Test void rejectsNewDependenciesAndUnidentifiedImageDamageButToleratesExistingSvgIds() {
        String source = page("<svg><defs><linearGradient id=\"g\"/></defs></svg><svg><defs><linearGradient id=\"g\"/></defs></svg><p>Original</p>");
        assertTrue(AiAdjustmentContract.validatePage(source, source.replace("Original", "New"), "修改文字"));
        String imagePage = page("<img src=\"pet.png\"><p>Original</p>");
        assertThrows(IllegalStateException.class, () -> AiAdjustmentContract.validatePage(imagePage, imagePage.replace("pet.png", "wrong.png"), "修改文字"));
        assertThrows(IllegalStateException.class, () -> AiAdjustmentContract.validatePage(imagePage, imagePage.replace("</head>", "<link rel=\"stylesheet\" href=\"https://cdn.invalid/style.css\"></head>"), "修改颜色"));
    }

    @Test void movingNoticeAndChangingBackgroundRestoresOnlyUnambiguouslyOmittedIds() throws Exception {
        String source = page("<div id=\"notice\" data-wf-id=\"outfit-notice\" data-wf-uid=\"wf-notice\" data-wf-element-id=\"51\" style=\"top:80px\">"
                + "<span id=\"notice-label\" data-wf-uid=\"wf-label\">获得1个新装扮</span></div>"
                + "<img id=\"bg\" src=\"/api/assets/bg.png\"><button id=\"collect\" data-nav=\"收集\">收集</button>");
        String modified = source.replace(" data-wf-uid=\"wf-notice\"", "")
                .replace(" data-wf-element-id=\"51\"", "").replace(" data-wf-uid=\"wf-label\"", "")
                .replace("top:80px", "top:340px").replace("/api/assets/bg.png", "/api/assets/bg-new.png");
        fullResponse(modified);
        var result = service.adjustPage(7L, 1L, "获得1个新装扮往下挪到收集图标旁边，替换背景图", source);
        var doc = Jsoup.parse(result.getModifiedHtml());
        assertTrue(result.isChanged());
        assertEquals("wf-notice", doc.getElementById("notice").attr("data-wf-uid"));
        assertEquals("51", doc.getElementById("notice").attr("data-wf-element-id"));
        assertEquals("wf-label", doc.getElementById("notice-label").attr("data-wf-uid"));
        assertEquals("top:340px", doc.getElementById("notice").attr("style"));
        assertEquals("/api/assets/bg-new.png", doc.getElementById("bg").attr("src"));
        assertEquals("收集", doc.getElementById("collect").attr("data-nav"));
        assertEquals(1, doc.select("#notice").size());
        verify(ai, times(1)).generateHtmlText(anyString(), anyString());
        verify(pages, never()).updateById(any());
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(ai).generateHtmlText(prompt.capture(), anyString());
        assertTrue(prompt.getValue().contains("包括本次调整目标"));
        assertTrue(prompt.getValue().contains("保留原值"));
    }

    @Test void missingWholeNodeOrAllItsIdentitiesIsStillRejectedWithAnActionableMessage() throws Exception {
        String source = page("<span id=\"outfit-notice\">获得1个新装扮</span><button id=\"collect\">收集</button>");
        for (String modified : List.of(page("<button id=\"collect\">收集</button>"), source.replace(" id=\"outfit-notice\"", ""))) {
            fullResponse(modified);
            var error = assertThrows(IllegalStateException.class,
                    () -> service.adjustPage(7L, 1L, "获得1个新装扮往下移动", source));
            assertTrue(error.getMessage().contains("获得1个新装扮"));
            assertTrue(error.getMessage().contains("已保留原画板"));
            assertFalse(error.getMessage().contains("明确删除"));
        }
    }

    @Test void explicitIdentifierRenamesAreNotSilentlyOverwritten() throws Exception {
        String source = page("<button id=\"buy\" data-wf-uid=\"buy-uid\">购买</button>");
        fullResponse(source.replace("id=\"buy\"", "id=\"other\""));
        assertThrows(IllegalStateException.class, () -> service.adjustPage(7L, 1L, "按钮改蓝", source));
    }

    @Test void ambiguousAndCrossedIdentitiesCannotRepairOrReassignAnUnrelatedNode() throws Exception {
        String source = page("<button id=\"buy\" data-wf-uid=\"buy-uid\">购买</button>"
                + "<button id=\"collect\" data-wf-uid=\"collect-uid\">收集</button>");
        fullResponse(source.replace("buy-uid", "temp-uid").replace("collect-uid", "buy-uid").replace("temp-uid", "collect-uid"));
        assertThrows(IllegalStateException.class, () -> service.adjustPage(7L, 1L, "按钮改蓝", source));
        fullResponse(page("<button id=\"buy\">购买</button><button id=\"buy\">收集</button>"));
        assertThrows(IllegalStateException.class, () -> service.adjustPage(7L, 1L, "按钮改蓝", source));
    }

    @Test void metadataRepairDoesNotHideImageInteractionOrScriptDamage() throws Exception {
        String source = original.replace("id=\"buy\"", "id=\"buy\" data-wf-uid=\"buy-uid\"");
        String omitted = source.replace(" data-wf-uid=\"buy-uid\"", "");
        for (String modified : List.of(omitted.replace("data-nav=\"背包\"", "data-nav=\"抽奖\""),
                omitted.replace("/api/assets/pet.png", "wrong.png"),
                omitted.replace("</body>", "<script>alert(1)</script></body>"))) {
            fullResponse(modified);
            assertThrows(IllegalStateException.class, () -> service.adjustPage(7L, 1L, "按钮改蓝", source));
        }
    }

    @Test void localElementCanRestoreOmittedRootIdThroughItsSurvivingStableUid() throws Exception {
        var request = element();
        request.setElementHtml("<button id=\"buy\" data-wf-uid=\"buy-uid\" data-nav=\"背包\">购买</button>");
        String snippet = "<button data-wf-uid=\"buy-uid\" data-nav=\"背包\" style=\"color:blue\">购买</button>";
        when(ai.generateHtmlText(anyString(), anyString())).thenReturn(json.writeValueAsString(Map.of("action", "REPLACE", "modified_snippet", snippet)));
        var result = service.adjustElement(7L, 1L, request);
        assertTrue(result.isChanged());
        assertEquals("buy", Jsoup.parseBodyFragment(result.getModifiedSnippet()).body().child(0).id());
        assertTrue(result.getModifiedSnippet().contains("color:blue"));
    }

    @Test void metadataRepairCannotHealIncompleteDocumentsOrInvalidElementEnvelopes() throws Exception {
        fullResponse("<html><body><button id=\"buy\">购买</button>");
        assertThrows(IllegalStateException.class, () -> service.adjustPage(7L, 1L, "按钮改蓝", original));
        var request = element();
        request.setElementHtml("<button id=\"buy\" data-wf-uid=\"buy-uid\">购买</button>");
        for (String snippet : List.of("<html><body><button data-wf-uid=\"buy-uid\">购买</button></body></html>",
                "<button data-wf-uid=\"buy-uid\">购买</button><p>多余节点</p>")) {
            when(ai.generateHtmlText(anyString(), anyString())).thenReturn(json.writeValueAsString(Map.of("action", "REPLACE", "modified_snippet", snippet)));
            assertThrows(IllegalStateException.class, () -> service.adjustElement(7L, 1L, request));
        }
    }

    @Test void generatesRealImageAfterRestoringIdentifiersAndKeepsOtherEdits() throws Exception {
        String source = page("<img id=\"bg\" data-wf-uid=\"bg-image\" src=\"/api/assets/old.png\"><p id=\"notice\">获得1个新装扮</p><button id=\"buy\" data-nav=\"收集\">收集</button>");
        String modified = source.replace(" data-wf-uid=\"bg-image\"", "").replace("<p id=\"notice\">", "<p id=\"notice\" style=\"top:340px\">");
        when(ai.generateHtmlText(anyString(), anyString())).thenReturn(json.writeValueAsString(Map.of(
                "modified_html", modified, "summary", "移动文字并生成背景", "image_tasks", List.of(imageTask("bg", "background")))));
        when(artwork.generateAdjustment(anyLong(), anyLong(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new ProjectArtworkService.Artwork("version-key", "背景", "场景", "/api/assets/artwork/7/new.png"));
        var result = service.adjustPage(7L, 1L, "把提示下移，并重新生成背景图", source, "", "request-1");
        var doc = Jsoup.parse(result.getModifiedHtml());
        assertEquals("/api/assets/artwork/7/new.png", doc.getElementById("bg").attr("src"));
        assertEquals("bg-image", doc.getElementById("bg").attr("data-wf-uid"));
        assertEquals("top:340px", doc.getElementById("notice").attr("style"));
        assertEquals("收集", doc.getElementById("buy").attr("data-nav"));
        assertEquals(1, result.getGeneratedImages().size());
        assertTrue(result.isChanged());
        verify(pages, never()).updateById(any());
    }

    @Test void localImageGenerationStaysInsideTheSelectedSubtree() throws Exception {
        var req = AiElementAdjustRequest.builder().elementId("pet").elementHtml("<img id=\"pet\" data-wf-uid=\"pet-uid\" src=\"old.png\">")
                .instruction("重新生成宠物图片").requestId("local-request").build();
        when(ai.generateHtmlText(anyString(), anyString())).thenReturn(json.writeValueAsString(Map.of("action", "REPLACE",
                "modified_snippet", req.getElementHtml(), "image_tasks", List.of(imageTask("pet", "character")))));
        when(artwork.generateAdjustment(anyLong(), anyLong(), eq("local-request"), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new ProjectArtworkService.Artwork("key", "宠物", "场景", "/api/assets/artwork/7/pet-new.png"));
        var result = service.adjustElement(7L, 1L, req);
        assertEquals("REPLACE", result.getAction());
        assertTrue(result.isChanged());
        assertEquals("pet-uid", Jsoup.parseBodyFragment(result.getModifiedSnippet()).selectFirst("img").attr("data-wf-uid"));
        assertEquals("/api/assets/artwork/7/pet-new.png", result.getGeneratedImages().get(0).url());
    }

    @Test void invalidHtmlOrMissingImagePlanNeverCallsTheImageProvider() throws Exception {
        fullResponse(original);
        assertThrows(IllegalStateException.class, () -> service.adjustPage(7L, 1L, "重新生成角色图片", original));
        when(ai.generateHtmlText(anyString(), anyString())).thenReturn(json.writeValueAsString(Map.of("modified_html", original.replace("id=\"buy\"", "id=\"lost\""),
                "image_tasks", List.of(imageTask("pet", "character")))));
        assertThrows(IllegalStateException.class, () -> service.adjustPage(7L, 1L, "重新生成角色图片", original));
        verifyNoInteractions(artwork);
    }

    private static Map<String, String> imageTask(String id, String kind) {
        return Map.of("target_attribute", "id", "target_id", id, "kind", kind, "name", "背景", "prompt", "清新的游戏插画，暖色光照，不含文字", "aspect_ratio", "portrait");
    }

    @Test void declarativeMoveAndGeneratedBackgroundNeverReconstructUnrelatedSvgs() throws Exception {
        String source = page("<div id=\"scene\" style=\"height:833px\"><div id=\"tip\" data-wf-uid=\"tip-uid\"><span id=\"message\">获得 1 个新装扮</span></div>"
                + "<button id=\"collect\" data-nav=\"收集\"><svg data-wf-id=\"collect-icon\" data-wf-uid=\"svg-a\"><path id=\"p-a\" d=\"M1 2L3 4\"/></svg>收集</button>"
                + "<svg data-wf-id=\"reward-icon\" data-wf-uid=\"svg-b\"><path id=\"p-b\" d=\"M2 3L4 5\"/></svg></div>");
        when(ai.generateHtmlText(anyString(), anyString())).thenReturn(json.writeValueAsString(Map.of("summary", "下移提示并生成背景", "edits", List.of(
                Map.of("op", "style", "target_attribute", "id", "target_id", "tip", "styles", Map.of("top", "68%", "left", "18px"))), "image_tasks", List.of(imageTask("scene", "background")))));
        when(artwork.generateAdjustment(anyLong(), anyLong(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new ProjectArtworkService.Artwork("new-background", "背景", "场景", "/api/assets/artwork/7/generated.png", "background"));
        var result = service.adjustPage(7L, 1L, "现在的获得1个新装扮有点挡住扭蛋机图标了，往下一点挪到其他收集的图标那里，然后重新生成背景图", source, "", "move-background");
        var before = Jsoup.parse(source); var after = Jsoup.parse(result.getModifiedHtml());
        assertEquals(before.select("svg").outerHtml(), after.select("svg").outerHtml());
        assertEquals(before.getElementById("collect").outerHtml(), after.getElementById("collect").outerHtml());
        assertEquals("tip-uid", after.getElementById("tip").attr("data-wf-uid"));
        assertTrue(after.getElementById("tip").attr("style").contains("top:68%!important"));
        assertTrue(after.getElementById("scene").attr("style").contains("/api/assets/artwork/7/generated.png"));
        assertTrue(result.isChanged()); assertEquals(1, result.getGeneratedImages().size());
        verify(pages, never()).updateById(any());
    }

    @Test void selectedCompositeMovesWithoutReplacingItsSvgChildren() throws Exception {
        var req = element();
        req.setElementHtml("<button id=\"buy\" data-nav=\"背包\"><svg data-wf-id=\"buy-icon\" data-wf-uid=\"svg-a\"><path id=\"p-a\"/></svg><span id=\"label\">购买</span></button>");
        when(ai.generateHtmlText(anyString(), anyString())).thenReturn(json.writeValueAsString(Map.of("action", "REPLACE", "edits", List.of(
                Map.of("op", "style", "target_attribute", "root", "target_id", "", "styles", Map.of("top", "20px"))))));
        var result = service.adjustElement(7L, 1L, req);
        assertTrue(result.isChanged());
        assertEquals(Jsoup.parseBodyFragment(req.getElementHtml()).select("svg").outerHtml(), Jsoup.parseBodyFragment(result.getModifiedSnippet()).select("svg").outerHtml());
        verifyNoInteractions(artwork);
    }

    @Test void invalidEditsCannotReachTheImageProviderOrSilentlyFallBackToHtml() throws Exception {
        List<Map<String, Object>> invalidEdit = List.of(Map.of("op", "style", "target_attribute", "id", "target_id", "missing", "styles", Map.of("top", "20px")));
        for (Map<String, Object> response : List.<Map<String, Object>>of(
                Map.of("edits", invalidEdit, "image_tasks", List.of(imageTask("pet", "character"))),
                Map.of("edits", List.of(), "modified_html", original, "image_tasks", List.of(imageTask("pet", "character"))),
                Map.of("edits", "invalid", "image_tasks", List.of(imageTask("pet", "character"))))) {
            when(ai.generateHtmlText(anyString(), anyString())).thenReturn(json.writeValueAsString(response));
            assertThrows(IllegalStateException.class, () -> service.adjustPage(7L, 1L, "移动提示并重新生成角色图片", original));
        }
        verifyNoInteractions(artwork);
    }

    @Test void imageOnlyAndNoChangeEditsRemainDistinctAndDoNotRequireEchoingThePage() throws Exception {
        when(ai.generateHtmlText(anyString(), anyString())).thenReturn("{\"summary\":\"无需调整\",\"edits\":[],\"image_tasks\":[]}");
        assertFalse(service.adjustPage(7L, 1L, "检查页面", original).isChanged());
        verifyNoInteractions(artwork);
        when(ai.generateHtmlText(anyString(), anyString())).thenReturn(json.writeValueAsString(Map.of("summary", "生成新角色", "edits", List.of(), "image_tasks", List.of(imageTask("pet", "character")))));
        when(artwork.generateAdjustment(anyLong(), anyLong(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new ProjectArtworkService.Artwork("new", "角色", "场景", "/api/assets/artwork/7/new.png"));
        var result = service.adjustPage(7L, 1L, "重新生成角色图片", original);
        assertTrue(result.isChanged());
        assertEquals("/api/assets/artwork/7/new.png", Jsoup.parse(result.getModifiedHtml()).getElementById("pet").attr("src"));
    }

    @Test void structuredEditsCannotBypassImageAndInteractionContracts() throws Exception {
        for (Map<String, String> attribute : List.of(Map.of("src", "wrong.png"), Map.of("data-nav", "抽奖"))) {
            String target = attribute.containsKey("src") ? "pet" : "buy";
            when(ai.generateHtmlText(anyString(), anyString())).thenReturn(json.writeValueAsString(Map.of("edits", List.of(
                    Map.of("op", "attributes", "target_attribute", "id", "target_id", target, "attributes", attribute)))));
            assertThrows(IllegalStateException.class, () -> service.adjustPage(7L, 1L, "文字改蓝", original));
        }
        verifyNoInteractions(artwork);
    }
}
