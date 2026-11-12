package com.wireforge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Playwright;
import com.wireforge.entity.Page;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Offline rendering of a saved local prototype fixture. No model calls or database writes. */
@EnabledIfSystemProperty(named = "wireforge.chrome-fixture", matches = ".+")
class ProjectChromeVisualTest {
    @Test void existingPagesRenderWithMatchingChromeAndKeepTheirContent() throws Exception {
        ObjectMapper json = new ObjectMapper();
        String fixture = Files.readString(Path.of(System.getProperty("wireforge.chrome-fixture"))).replace("\uFEFF", "");
        var root = json.readTree(fixture);
        if (root.has("data")) root = root.path("data");
        Path output = Path.of(System.getProperty("wireforge.chrome-output", "target/chrome-review")); Files.createDirectories(output);
        List<Page> pages = new ArrayList<>();
        for (var node : root.path("pages")) {
            Page page = new Page(); page.setId(node.path("id").asLong()); page.setProjectId(root.path("project").path("id").asLong());
            page.setName(node.path("name").asText()); page.setCanvasWidth(node.path("canvas_width").asInt(375));
            page.setCanvasHeight(node.path("canvas_height").asInt(833)); page.setHtmlContent(node.path("html_content").asText("")); pages.add(page);
        }
        ProjectChrome chrome = new ProjectChrome();
        List<com.fasterxml.jackson.databind.JsonNode> metrics = new ArrayList<>();
        try (Playwright playwright = Playwright.create(); Browser browser = playwright.chromium().launch()) {
            for (Page page : pages) {
                if (!ProjectChrome.generated(page.getHtmlContent())) continue;
                String before = page.getHtmlContent(), after = chrome.apply(page, pages, before);
                var beforeDoc = Jsoup.parse(before); var afterDoc = Jsoup.parse(after);
                for (var doc : List.of(beforeDoc, afterDoc)) {
                    doc.select("[data-wf-chrome],[data-wf-shared-chrome],[data-wf-navigation=bar],[data-wf-chrome-spacer],.status,.status-bar,.statusbar,.wf-statusbar,.wf-status-bar").remove();
                    doc.select("[data-wf-chrome-viewport]").removeAttr("data-wf-chrome-viewport");
                }
                assertEquals(dom(beforeDoc.body()), dom(afterDoc.body()), "正文DOM发生变化: " + page.getName());
                Files.writeString(output.resolve("page-" + page.getId() + ".html"), after);
                try (var tab = browser.newPage(new Browser.NewPageOptions().setViewportSize(page.getCanvasWidth(), page.getCanvasHeight()))) {
                    tab.route("**/*", route -> { String url = route.request().url(); if (url.startsWith("http://127.0.0.1:") || url.startsWith("http://localhost:")) route.resume(); else route.abort(); });
                    tab.setContent(before); tab.waitForTimeout(150);
                    tab.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions().setPath(output.resolve("before-" + page.getId() + ".png")));
                    tab.setContent(after); tab.waitForTimeout(150);
                    tab.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions().setPath(output.resolve("after-" + page.getId() + ".png")));
                    var measured = json.valueToTree(tab.evaluate("""
                        () => [...document.querySelectorAll('[data-wf-shared-chrome]')].map(n => {
                          const r=n.getBoundingClientRect(),s=getComputedStyle(n);
                          const icon=n.querySelector('[data-wf-chrome-part=signals],[data-wf-chrome-part=icon]');
                          const label=n.querySelector('[data-wf-chrome-part=label]');
                          return {kind:n.getAttribute('data-wf-shared-chrome'),height:r.height,bottom:r.bottom,
                            background:s.backgroundColor,color:s.color,iconWidth:icon?.getBoundingClientRect().width,
                            iconHeight:icon?.getBoundingClientRect().height,font:label?getComputedStyle(label).fontSize:s.fontSize};
                        })
                        """));
                    var entry = json.createObjectNode(); entry.put("pageId", page.getId()); entry.put("name", page.getName()); entry.set("components", measured); metrics.add(entry);
                }
                page.setHtmlContent(after);
            }
        }
        Files.writeString(output.resolve("metrics.json"), json.writerWithDefaultPrettyPrinter().writeValueAsString(metrics));
        assertTrue(metrics.size() >= 2, "至少需要两个已经生成的页面");
        for (String kind : List.of("status", "navigation")) {
            var components = metrics.stream().flatMap(n -> { List<com.fasterxml.jackson.databind.JsonNode> list = new ArrayList<>(); n.path("components").forEach(list::add); return list.stream(); })
                    .filter(n -> kind.equals(n.path("kind").asText())).toList();
            assertTrue(components.size() >= 2);
            for (String field : List.of("height", "bottom", "iconWidth", "iconHeight", "font"))
                assertEquals(1, components.stream().map(n -> n.path(field).asText()).distinct().count(), kind + ": " + field);
            if (kind.equals("navigation")) assertEquals(1, components.stream().map(n -> n.path("background").asText()).distinct().count());
        }
    }

    // SVG's <path/> and <path></path> are equivalent. Compare nodes/attributes rather than serialization flags.
    private static List<String> dom(org.jsoup.nodes.Node root) {
        List<String> result = new ArrayList<>();
        root.traverse(new org.jsoup.select.NodeVisitor() {
            @Override public void head(org.jsoup.nodes.Node node, int depth) {
                String attributes = node.attributes().asList().stream().map(Object::toString).sorted().collect(java.util.stream.Collectors.joining("|"));
                String text = node instanceof org.jsoup.nodes.TextNode t ? t.getWholeText() : node instanceof org.jsoup.nodes.DataNode d ? d.getWholeData() : "";
                result.add(depth + ":" + node.nodeName() + ":" + attributes + ":" + text);
            }
            @Override public void tail(org.jsoup.nodes.Node node, int depth) {}
        });
        return result;
    }
}
