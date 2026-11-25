package com.wireforge.service;

import com.wireforge.entity.Page;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PageReferencesTest {
    static Page page(long id, String name, String... aliases) {
        Page page = PageNameResolverTest.scan(id, name); PageReferences.remember(page, aliases); return page;
    }
    @Test void resolvesHistoricalNamesAndFilenameAliasesAfterRename() {
        Page page = page(1, "确认购买", "背包@3x-2", "背包@3x-2.png");
        var refs = new PageReferences(List.of(page));
        assertSame(page, refs.resolve("背包@3x-2.png")); assertSame(page, refs.resolve("确认购买"));
    }
    @Test void duplicateNamesAndAliasesNeverPickTheFirstPage() {
        Page a = page(1, "A版/商城", "商城"), b = page(2, "B版/商城", "商城");
        var refs = new PageReferences(List.of(a, b));
        assertNull(refs.resolve("商城")); assertSame(a, refs.resolve("A版/商城"));
        assertNull(refs.resolve("C版/商城"));
        a.setName("商城"); b.setName("商城"); assertNull(refs.resolve("商城"));
    }
    @Test void variantsRemainDistinctAndDeletedPagesAreNotDestinations() {
        Page normal = page(1, "背包"), variant = page(2, "背包-2"), deleted = page(3, "背包_2"); deleted.setIsDeleted(1);
        var refs = new PageReferences(List.of(normal, variant, deleted));
        assertSame(variant, refs.resolve("背包-2")); assertSame(normal, refs.resolve("背包"));
        assertNull(refs.resolve("背包_2")); assertNull(refs.byId("3"));
    }
    @Test void aCurrentNameCannotStealAnotherPagesHistoricalLink() {
        var refs = new PageReferences(List.of(page(1, "商店", "商城"), page(2, "商城")));
        assertNull(refs.resolve("商城"));
    }
    @Test void rewritesOnlyNavigationAttributesAndCarriesAStablePageId() {
        Page target = page(20, "确认购买", "道具购买确认弹窗");
        String original = "<!DOCTYPE html><html><body><button data-nav='道具购买确认弹窗'>道具购买确认弹窗</button>"
                + "<script>var label='道具购买确认弹窗';</script></body></html>";
        String rewritten = new PageReferences(List.of(target)).rewriteHtml(original);
        var doc = Jsoup.parse(rewritten);
        assertEquals("确认购买", doc.selectFirst("button").attr("data-nav"));
        assertEquals("20", doc.selectFirst("button").attr("data-nav-page-id"));
        assertEquals("道具购买确认弹窗", doc.selectFirst("button").text());
        assertTrue(doc.selectFirst("script").data().contains("道具购买确认弹窗"));
        target.setName("买这个");
        assertEquals("买这个", Jsoup.parse(new PageReferences(List.of(target)).rewriteHtml(rewritten)).selectFirst("button").attr("data-nav"));
    }
    @Test void ambiguousAndUnknownReferencesDoNotGetArbitraryIds() {
        String html = "<button data-nav='商城'>去商城</button>";
        assertEquals(html, new PageReferences(List.of(page(1, "A/商城"), page(2, "B/商城"))).rewriteHtml(html));
    }
}
