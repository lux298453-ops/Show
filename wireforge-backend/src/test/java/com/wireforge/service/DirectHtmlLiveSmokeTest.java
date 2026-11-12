package com.wireforge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wireforge.WireforgeApplication;
import com.wireforge.ai.HtmlRenderer;
import com.wireforge.entity.Page;
import com.wireforge.mapper.PageMapper;
import com.wireforge.model.AuthUser;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in: calls the configured model and keeps a separate dev project for visual review. */
@EnabledIfSystemProperty(named = "wireforge.live-html", matches = "true")
@SpringBootTest(classes = WireforgeApplication.class, webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
        properties = {"spring.profiles.active=dev", "server.port=8092", "wireforge.auto-analyze=false",
                "spring.sql.init.mode=never", "wireforge.verify=false", "wireforge.ai.asset-base-url=http://127.0.0.1:8091"})
class DirectHtmlLiveSmokeTest {
    @Autowired ProjectService projects;
    @Autowired AnalyzeService analyzer;
    @Autowired PageMapper pages;
    @Autowired HtmlRenderer renderer;
    @Autowired ObjectMapper json;

    @Test void generatesAndSavesOneRealShopWithoutOverwritingOtherProjects() throws Exception {
        Path image = Path.of(System.getProperty("wireforge.live-image"));
        Path output = Path.of(System.getProperty("wireforge.live-output", "target/html-smoke"));
        Files.createDirectories(output);
        AuthUser user = AuthUser.builder().userId("codex-prompt-trial-20261103").userName("原型提示词验证").build();
        var project = projects.createProject("中保真直出 · 实施验证", "真实模型、静态DOM与保存回归，仅操作本试验项目", user, null);
        Page page = projects.createPage(project.getId(), Map.of("name", "商城中心", "width", 375, "height", 834), user);
        Page target = projects.createPage(project.getId(), Map.of("name", "商品详情", "width", 375, "height", 834), user);
        projects.updatePageBackground(project.getId(), page.getId(), image.toAbsolutePath().toString());
        page = pages.selectById(page.getId());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("projectId", project.getId()); result.put("pageId", page.getId()); result.put("targetPageId", target.getId());
        Files.writeString(output.resolve("implementation-live-result.json"), json.writerWithDefaultPrettyPrinter().writeValueAsString(result));
        int count = analyzer.analyzePage(page, List.of("商城中心", "商品详情"));
        String generated = pages.selectById(page.getId()).getHtmlContent();
        Files.writeString(output.resolve("implementation-shop.html"), generated == null ? "" : generated);
        assertTrue(count > 0);
        assertNotNull(generated);
        assertTrue(generated.contains("ai-static-v1"), "真实模型输出未通过静态HTML契约，已落回模板；请检查生成日志");
        result.put("elements", count); result.put("directHtml", true); result.put("rendererAvailable", renderer.isAvailable());
        if (renderer.isAvailable()) Files.write(output.resolve("implementation-shop.png"), renderer.screenshot(generated, 375, 834));
        var doc = Jsoup.parse(generated);
        var button = doc.selectFirst("button"); assertNotNull(button);
        String uid = button.attr("data-wf-uid");
        button.text("原型保存验证");
        projects.updatePageHtml(page.getId(), doc.outerHtml(), null);
        projects.rebuildAppMapAndRender(project.getId());
        String saved = pages.selectById(page.getId()).getHtmlContent();
        assertTrue(saved.contains("原型保存验证"));
        assertTrue(saved.contains(uid));
        assertTrue(saved.contains("ai-static-v1"));
        result.put("editSurvivesAppMapRebuild", true);
        result.put("workbench", "http://localhost:5173/projects/" + project.getId() + "/prototype?page=" + page.getId());
        Files.writeString(output.resolve("implementation-live-result.json"), json.writerWithDefaultPrettyPrinter().writeValueAsString(result));
        // Keep the review page in its original generated state after exercising persistence.
        projects.updatePageHtml(page.getId(), generated, null);
    }
}
