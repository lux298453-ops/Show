package com.wireforge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wireforge.entity.Page;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PageTypesTest {
    @Test void explicitTypesAreIndependentOfNamesAndGeneratedMarkup() {
        Page page = PageNameResolverTest.scan(1, "确认购买"); page.setPageType("modal");
        assertTrue(PageTypes.overlay(page)); page.setName("买这个"); assertTrue(PageTypes.overlay(page));
        page.setPageType("page"); page.setName("弹窗制作教程");
        page.setHtmlContent("<section role='dialog'>内容</section>"); assertFalse(PageTypes.overlay(page));
    }
    @Test void onlyVisualStructureClassifiesNewRecognition() throws Exception {
        var json = new ObjectMapper();
        assertEquals("page", PageTypes.fromAnalysis(json.readTree("{\"page_name\":\"抽奖结果弹窗\",\"page_type\":\"page\"}")));
        assertEquals("unknown", PageTypes.fromAnalysis(json.readTree("{\"page_name\":\"抽奖结果弹窗\",\"elements\":[]}")));
        assertEquals("drawer", PageTypes.fromAnalysis(json.readTree("{\"elements\":[{\"type\":\"drawer\"}]}")));
    }
    @Test void hiddenLocalDialogsDoNotClassifyTheWholePage() {
        assertEquals("unknown", PageTypes.fromHtml("<main>商城</main><div hidden role='dialog'>确认</div>"));
        assertEquals("unknown", PageTypes.fromHtml("<main>商城</main><section style='display: none'><div role='dialog'>确认</div></section>"));
        assertEquals("modal", PageTypes.fromHtml("<div role='dialog'>确认</div>"));
        assertEquals("drawer", PageTypes.fromHtml("<div data-wf-role='drawer'>背包</div>"));
    }
    @Test void legacyMigrationUsesSavedEvidenceInsteadOfTheOldDisplayName() {
        Page page = PageNameResolverTest.scan(1, "确认弹窗");
        assertEquals("unknown", PageTypes.legacy(page, List.of()));
        page.setName("背包"); assertEquals("modal", PageTypes.legacy(page, List.of("popup")));
        page.setPageType("unknown"); page.setPageTypeSource("ai");
        assertFalse(PageTypes.overlay(page));
    }
}
