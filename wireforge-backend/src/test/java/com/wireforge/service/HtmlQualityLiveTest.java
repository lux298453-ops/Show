package com.wireforge.service;

import com.wireforge.WireforgeApplication;
import com.wireforge.ai.HtmlRenderer;
import com.wireforge.mapper.PageMapper;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit review run: reads one existing page, writes HTML/screenshots and artwork cache, never saves page HTML. */
@EnabledIfSystemProperty(named = "wireforge.live-quality", matches = "true")
@SpringBootTest(classes = WireforgeApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"spring.profiles.active=dev", "wireforge.auto-analyze=false", "spring.sql.init.mode=never",
                "wireforge.ai.asset-base-url=http://127.0.0.1:8091"})
class HtmlQualityLiveTest {
    @Autowired PageMapper pages;
    @Autowired AnalyzeService analyzer;
    @Autowired HtmlRenderer renderer;

    @Test void generatesAReviewableShopWithoutReplacingTheCurrentPage() throws Exception {
        long pageId = Long.parseLong(System.getProperty("wireforge.live-quality-page"));
        var page = pages.selectById(pageId); assertNotNull(page);
        String original = page.getHtmlContent();
        Path output = Path.of(System.getProperty("wireforge.live-quality-output")); Files.createDirectories(output);
        String html = analyzer.renderPageHtml(page);
        Files.writeString(output.resolve("shop-quality-generated.html"), html);
        assertNotNull(Jsoup.parse(html).selectFirst("meta[name=wf-renderer][content=ai-static-v1]"), "真实HTML未通过契约，已经退回模板");
        assertFalse(Jsoup.parse(html).select("img[src^=/api/assets/artwork/]").isEmpty(), "生成页没有使用项目角色图片");
        Files.write(output.resolve("shop-quality-generated.png"), renderer.screenshot(html, page.getCanvasWidth(), page.getCanvasHeight()));
        assertEquals(original, pages.selectById(pageId).getHtmlContent(), "验证不应覆盖现有页面");
    }
}
