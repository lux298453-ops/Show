package com.wireforge.service;

import com.wireforge.WireforgeApplication;
import com.wireforge.ai.HtmlRenderer;
import com.wireforge.entity.Page;
import com.wireforge.mapper.PageMapper;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit local recovery: validate real artwork before replacing the requested collection page. */
@EnabledIfSystemProperty(named = "wireforge.recover-collection", matches = "true")
@SpringBootTest(classes = WireforgeApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"spring.profiles.active=dev", "wireforge.auto-analyze=false", "spring.sql.init.mode=never",
                "wireforge.ai.repair=false", "wireforge.ai.timeout-seconds=600",
                "wireforge.ai.asset-base-url=http://127.0.0.1:8091"})
class CollectionRegenerationLiveTest {
    @Autowired PageMapper pages;
    @Autowired AnalyzeService analyzer;
    @Autowired HtmlRenderer renderer;
    @Autowired AutowireRenderService bindings;

    @Test void recoversTheCollectionWithRealCharacterImages() throws Exception {
        long id = Long.parseLong(System.getProperty("wireforge.recover-page"));
        Page page = pages.selectById(id); assertNotNull(page);
        Path output = Path.of(System.getProperty("wireforge.recover-output")); Files.createDirectories(output);
        String before = page.getHtmlContent();
        Files.writeString(output.resolve("collection-before.html"), before);
        String html = analyzer.renderPageHtml(page);
        Files.writeString(output.resolve("collection-after.html"), html);
        var doc = Jsoup.parse(html);
        assertNotNull(doc.selectFirst("meta[name=wf-renderer][content=ai-static-v1]"));
        assertTrue(doc.select("img[src^=/api/assets/artwork/]").size() >= 6,
                "Not enough character images; leave the current page intact");
        Files.write(output.resolve("collection-after.png"), renderer.screenshot(html, page.getCanvasWidth(), page.getCanvasHeight()));
        assertEquals(1, pages.update(null, com.baomidou.mybatisplus.core.toolkit.Wrappers.<Page>lambdaUpdate()
                .eq(Page::getId,id).eq(Page::getHtmlContent,before).set(Page::getHtmlContent,html)), "Page changed during generation");
        bindings.refreshProjectBindings(page.getProjectId());
        assertTrue(pages.selectById(id).getHtmlContent().contains("/api/assets/artwork/"));
    }
}
