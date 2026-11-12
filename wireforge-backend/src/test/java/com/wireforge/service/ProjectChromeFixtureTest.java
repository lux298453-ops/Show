package com.wireforge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wireforge.entity.Page;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Reads an explicitly supplied saved fixture; no database writes or model calls. */
@EnabledIfSystemProperty(named = "wireforge.chrome-fixture", matches = ".+")
class ProjectChromeFixtureTest {
    @Test void existingSelectionsMigrateWithoutLosingBindingsOrPageContent() throws Exception {
        var json = new ObjectMapper();
        var root = json.readTree(Files.readString(Path.of(System.getProperty("wireforge.chrome-fixture"))).replace("\uFEFF", ""));
        if (root.has("data")) root = root.path("data");
        Path output = Path.of(System.getProperty("wireforge.chrome-output", "target/chrome-review"));
        Files.createDirectories(output);
        List<Page> pages = new ArrayList<>();
        for (var node : root.path("pages")) {
            Page page = new Page();
            page.setId(node.path("id").asLong()); page.setProjectId(root.path("project").path("id").asLong());
            page.setName(node.path("name").asText()); page.setCanvasHeight(node.path("canvas_height").asInt(833));
            page.setHtmlContent(node.path("html_content").asText()); pages.add(page);
        }
        ProjectChrome chrome = new ProjectChrome();
        int checked = 0;
        for (Page page : pages) {
            String before = page.getHtmlContent(), after = chrome.apply(page, pages, before);
            var a = Jsoup.parse(before); var b = Jsoup.parse(after);
            for (String attr : List.of("data-wf-uid", "data-wf-element-id")) {
                var ids = a.select("[" + attr + "]").eachAttr(attr);
                assertTrue(b.select("[" + attr + "]").eachAttr(attr).containsAll(ids), page.getName() + ": " + attr);
            }
            var activeBefore = a.select("[data-wf-shared-chrome=navigation] [aria-current=page]").eachAttr("data-wf-uid");
            assertEquals(activeBefore, b.select("[data-wf-shared-chrome=navigation] [aria-current=page]").eachAttr("data-wf-uid"), page.getName());
            for (var doc : List.of(a, b)) doc.select("[data-wf-shared-chrome],[data-wf-chrome-spacer]").remove();
            // Tag serialization can change, so compare visible text and source identities outside chrome.
            assertEquals(a.body().text(), b.body().text(), page.getName());
            assertEquals(a.body().select("[data-wf-uid]").stream().map(n -> n.attr("data-wf-uid")).collect(Collectors.toList()),
                    b.body().select("[data-wf-uid]").stream().map(n -> n.attr("data-wf-uid")).collect(Collectors.toList()), page.getName());
            Files.writeString(output.resolve("after-" + page.getId() + ".html"), after);
            if (!activeBefore.isEmpty()) checked++;
        }
        assertTrue(checked > 0, "Fixture must include existing selected navigation items");
    }
}
