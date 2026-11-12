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
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

/** Completes only unresolved illustration slots, reusing the saved HTML and all cached images. */
@EnabledIfSystemProperty(named = "wireforge.complete-artwork", matches = "true")
@SpringBootTest(classes = WireforgeApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"spring.profiles.active=dev", "wireforge.auto-analyze=false", "spring.sql.init.mode=never",
                "wireforge.ai.artwork.max-per-page=12", "wireforge.ai.asset-base-url=http://127.0.0.1:8091"})
class CollectionArtworkCompletionLiveTest {
    @Autowired PageMapper pages;
    @Autowired ProjectArtworkService artwork;
    @Autowired HtmlRenderer renderer;
    @Autowired AutowireRenderService bindings;

    @Test void completesTheRemainingCharactersWithoutAnotherHtmlModelCall() throws Exception {
        long id=Long.parseLong(System.getProperty("wireforge.recover-page"));
        Page page=pages.selectById(id); assertNotNull(page);
        String before=page.getHtmlContent();
        var allowed=new HashSet<String>();
        Jsoup.parse(before).select("img[src]").forEach(n->allowed.add(n.attr("src")));
        artwork.assets(page.getProjectId()).forEach(a->allowed.add(a.url()));
        String html=artwork.resolve(page.getProjectId(),before,allowed,Boolean.getBoolean("wireforge.complete-artwork-create"));
        var doc=Jsoup.parse(html);
        for(var slot:doc.select("[data-wf-artwork-key]"))
            assertNotNull(slot.selectFirst("img[src]"),"Still missing: "+slot.attr("data-wf-artwork-key"));
        Path output=Path.of(System.getProperty("wireforge.recover-output"));
        Files.writeString(output.resolve("collection-after.html"),html);
        Files.write(output.resolve("collection-after.png"),renderer.screenshot(html,page.getCanvasWidth(),page.getCanvasHeight()));
        assertEquals(1,pages.update(null,com.baomidou.mybatisplus.core.toolkit.Wrappers.<Page>lambdaUpdate()
                .eq(Page::getId,id).eq(Page::getHtmlContent,before).set(Page::getHtmlContent,html)));
        bindings.refreshProjectBindings(page.getProjectId());
    }
}
