package com.wireforge.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AiAdjustmentEditsTest {
    private final ObjectMapper json = new ObjectMapper();
    private static final String SOURCE = "<!doctype html><html><head><style>.tip{top:40%!important}</style>"
            + "<script>document.title='原型';</script></head><body><div id=\"tip\" data-wf-uid=\"tip-uid\" style=\"top:40%!important;left:8px;\">"
            + "<span id=\"message\" data-wf-uid=\"message-uid\">获得 1 个新装扮</span></div>"
            + "<button id=\"collect\" data-nav=\"收集\"><svg data-wf-id=\"collect-icon\" data-wf-uid=\"svg-a\"><path id=\"p-a\" d=\"M1 2L3 4\"/></svg>收集</button>"
            + "<svg data-wf-id=\"reward-icon\" data-wf-uid=\"svg-b\"><path id=\"p-b\" d=\"M2 3L4 5\"/></svg><img id=\"photo\" src=\"old.png\"></body></html>";

    private JsonNode edits(Map<String, ?>... values) { return json.valueToTree(List.of(values)); }
    private Map<String, Object> style(String id, Map<String, ?> values) {
        return Map.of("op", "style", "target_attribute", "id", "target_id", id, "styles", values);
    }

    @Test void movingNoticeRetainsEverySvgScriptAndInteractionFromTheSource() {
        String changed = AiAdjustmentEdits.apply(SOURCE, edits(style("tip", Map.of("top", "68%", "left", "20px"))), "提示往下移动", false);
        var before = Jsoup.parse(SOURCE); var after = Jsoup.parse(changed);
        assertEquals(before.select("svg").outerHtml(), after.select("svg").outerHtml());
        assertEquals(before.select("script").outerHtml(), after.select("script").outerHtml());
        assertEquals(before.getElementById("collect").outerHtml(), after.getElementById("collect").outerHtml());
        assertEquals("tip-uid", after.getElementById("tip").attr("data-wf-uid"));
        assertTrue(after.getElementById("tip").attr("style").contains("top:68%!important"));
        assertFalse(after.getElementById("tip").attr("style").contains("top:40%"));
        assertTrue(AiAdjustmentContract.validatePage(SOURCE, changed, "提示往下移动"));
    }

    @Test void unrelatedStylesWithDataUrlsQuotesFunctionsAndCustomPropertiesRemainIntact() {
        String source = "<div id=\"tip\" style=\"background-image:url('data:image/png;base64,abc;def');--label:'a;b';color:rgb(1,2,3);top:10px !important\">Text</div>";
        String changed = AiAdjustmentEdits.apply(source, edits(style("tip", Map.of("top", "calc(50% + 2px)"))), "下移", true);
        String css = Jsoup.parseBodyFragment(changed).getElementById("tip").attr("style");
        assertTrue(css.contains("url('data:image/png;base64,abc;def')"));
        assertTrue(css.contains("--label:'a;b'"));
        assertTrue(css.contains("color:rgb(1,2,3)"));
        assertTrue(css.contains("top:calc(50% + 2px)!important"));
        assertFalse(css.contains("top:10px"));
    }

    @Test void malformedOrInjectedCssCannotApplyAnyPartialEdit() {
        for (String css : List.of("red;position:fixed", "red;", "url('unfinished", "calc(1px", "x)", "expression(alert(1))", "url(javascript:alert(1))", "red}body{display:none")) {
            assertThrows(IllegalStateException.class, () -> AiAdjustmentEdits.apply(SOURCE,
                    edits(style("tip", Map.of("top", "20px")), style("tip", Map.of("color", css))), "下移", false), css);
        }
        assertTrue(SOURCE.contains("top:40%"));
    }

    @Test void plainTextChangeEscapesMarkupAndCannotEraseNestedIcons() {
        String changed = AiAdjustmentEdits.apply(SOURCE, edits(Map.of("op", "text", "target_attribute", "id", "target_id", "message", "text", "<img onerror=bad>")), "改文案", false);
        var doc = Jsoup.parse(changed);
        assertEquals("<img onerror=bad>", doc.getElementById("message").text());
        assertTrue(doc.getElementById("message").children().isEmpty());
        assertNotNull(doc.getElementById("p-a"));
        assertThrows(IllegalStateException.class, () -> AiAdjustmentEdits.apply(SOURCE,
                edits(Map.of("op", "text", "target_attribute", "id", "target_id", "collect", "text", "新文案")), "改文案", false));
    }

    @Test void stableIdentitiesHandlersClassesAndSelectorsCannotBeMutated() {
        for (String name : List.of("id", "data-wf-id", "data-wf-uid", "class", "onclick", "style")) {
            assertThrows(IllegalStateException.class, () -> AiAdjustmentEdits.apply(SOURCE, edits(Map.of("op", "attributes", "target_attribute", "id", "target_id", "tip", "attributes", Map.of(name, "other"))), "改样式", false));
        }
        assertThrows(IllegalStateException.class, () -> AiAdjustmentEdits.apply(SOURCE,
                edits(Map.of("op", "style", "target_attribute", "id][src]", "target_id", "tip", "styles", Map.of("top", "2px"))), "移动", false));
        assertThrows(IllegalStateException.class, () -> AiAdjustmentEdits.apply(SOURCE,
                edits(style("missing", Map.of("top", "2px"))), "移动", false));
    }

    @Test void replacingKnownImagesAndBooleanPropertiesRetainsTheSourceStructure() {
        String changed = AiAdjustmentEdits.apply(SOURCE, edits(Map.of("op", "attributes", "target_attribute", "id", "target_id", "photo", "attributes", Map.of("src", "/api/assets/new.png", "alt", "原角色"))), "更换角色图片", false);
        assertEquals("/api/assets/new.png", Jsoup.parse(changed).getElementById("photo").attr("src"));
        assertTrue(AiAdjustmentContract.validatePage(SOURCE, changed, "更换角色图片"));
        String hidden = AiAdjustmentEdits.apply(SOURCE, edits(Map.of("op", "attributes", "target_attribute", "id", "target_id", "photo", "attributes", Map.of("hidden", true))), "隐藏角色图片", false);
        assertTrue(Jsoup.parse(hidden).getElementById("photo").hasAttr("hidden"));
    }

    @Test void deletionRequiresUserIntentAndTheFragmentRootIsHandledByTheExistingDeleteAction() {
        JsonNode deletion = edits(Map.of("op", "delete", "target_attribute", "id", "target_id", "tip"));
        assertThrows(IllegalStateException.class, () -> AiAdjustmentEdits.apply(SOURCE, deletion, "移动提示", false));
        String deleted = AiAdjustmentEdits.apply(SOURCE, deletion, "删除提示", false);
        assertNull(Jsoup.parse(deleted).getElementById("tip"));
        assertTrue(AiAdjustmentContract.validatePage(SOURCE, deleted, "删除提示"));
        assertThrows(IllegalStateException.class, () -> AiAdjustmentEdits.apply("<div id=tip>A</div>", deletion, "删除提示", true));
    }

    @Test void unmarkedSelectedElementCanBeEditedAsRootWithoutAccessingItsParent() {
        var rootEdit = Map.of("op", "style", "target_attribute", "root", "target_id", "", "styles", Map.of("color", "blue"));
        String changed = AiAdjustmentEdits.apply("<span>Text</span>", edits(rootEdit), "改蓝", true);
        assertTrue(changed.contains("color:blue!important"));
        assertThrows(IllegalStateException.class, () -> AiAdjustmentEdits.apply(SOURCE, edits(rootEdit), "改蓝", false));
    }

    @Test void explicitNullRemovesAStyleAndEmptyEditsReturnTheExactOriginal() {
        Map<String, Object> styles = new java.util.HashMap<>(); styles.put("top", null);
        String changed = AiAdjustmentEdits.apply(SOURCE, edits(style("tip", styles)), "去掉top样式", false);
        assertEquals("left:8px;", Jsoup.parse(changed).getElementById("tip").attr("style"));
        assertEquals(SOURCE, AiAdjustmentEdits.apply(SOURCE, json.createArrayNode(), "检查", false));
        assertThrows(IllegalStateException.class, () -> AiAdjustmentEdits.apply(SOURCE, json.createObjectNode(), "检查", false));
    }
}
