package com.wireforge.service;

import com.wireforge.entity.Page;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class ProjectChromeTest {
    private final ProjectChrome chrome = new ProjectChrome();

    private Page page(long id, long projectId, String name, String html) {
        Page page = new Page(); page.setId(id); page.setProjectId(projectId); page.setName(name); page.setHtmlContent(html);
        return page;
    }

    private String html(int base, String bg, int height, String path) {
        return "<!doctype html><html><head><meta name='wf-renderer' content='ai-static-v1'><style>"
                + ".screen{background:#363641}.status{height:35px;color:#fff}.nav{height:" + height + "px;background:" + bg + ";border-top:1px solid #ccc}"
                + ".nav button{color:#888;font-size:11px}.nav .selected{color:#ff8c27}</style></head><body><div class='screen'>"
                + "<div class='status'><span data-wf-uid='time-" + base + "' data-wf-element-id='" + base + "'>08:30</span>"
                + "<div data-wf-uid='signals-" + base + "'><svg data-wf-uid='signal-" + base + "' viewBox='0 0 24 24'><path d='M1 1'/></svg>"
                + "<svg data-wf-uid='battery-" + base + "' data-wf-element-id='" + (base+1) + "' viewBox='0 0 24 24'><rect width='10' height='10'/></svg></div></div>"
                + "<article id='content' style='border-radius:17px'><h2>人工编辑的正文</h2><button data-wf-uid='buy-" + base + "' data-nav='详情'>购买</button></article>"
                + "<nav class='nav' data-wf-navigation='bar'><button class='selected' data-nav-item='1' data-wf-uid='home-" + base + "' data-wf-element-id='" + (base+2) + "' data-nav='家园'>"
                + "<span data-wf-uid='icon-wrapper-" + base + "'><svg viewBox='0 0 24 24' data-wf-uid='home-icon-" + base + "'><path d='" + path + "'/></svg></span>"
                + "<span data-wf-element-id='" + (base+3) + "' data-wf-uid='home-label-" + base + "'>家园</span></button>"
                + "<button data-nav-item='1' data-wf-uid='shop-" + base + "' data-wf-element-id='" + (base+4) + "' data-nav='商城'>"
                + "<svg viewBox='0 0 24 24' data-wf-uid='shop-icon-" + base + "'><rect x='4' y='4' width='16' height='16'/></svg><span>商城</span></button>"
                + "</nav></div></body></html>";
    }

    @Test void generatedPagesIncludeTheSameGeometryRuntimeWithoutDroppingNestedWrappersOrBindings() {
        String raw = html(10, "#efedf3", 75, "M2 2L20 20")
                .replace("<button class='selected'", "<div id='nav-items'><div id='home-slot'><button class='selected'")
                .replace("<button data-nav-item='1'", "</div><div id='shop-slot'><button data-nav-item='1'")
                .replace("</nav>", "</div></div><span class='home-indicator' data-wf-uid='indicator'/></nav>");
        Page home = page(1, 7, "家园", raw);
        String generated = chrome.apply(home, List.of(home), raw);
        var doc = Jsoup.parse(generated);
        assertEquals(1, doc.select("script[data-wf-chrome-layout-runtime]").size());
        assertTrue(doc.selectFirst("script[data-wf-chrome-layout-runtime]").data().contains("data-wf-chrome-layout"));
        assertTrue(doc.selectFirst("style[data-wf-chrome-style]").data().contains("height:min(40px"));
        assertNotNull(doc.selectFirst("#nav-items > #home-slot > [data-nav-item]"));
        assertNotNull(doc.selectFirst("#nav-items > #shop-slot > [data-nav-item]"));
        assertEquals(ids(raw, "data-wf-element-id"), ids(generated, "data-wf-element-id"));
        home.setHtmlContent(generated);
        assertEquals(generated, chrome.apply(home, List.of(home), generated));
    }

    @Test void projectPagesReuseExactlyTheSameStyleAndIconsButKeepTheirOwnLinksAndIds() {
        Page home = page(1, 7, "家园", html(10, "#efedf3", 70, "M2 2L20 20"));
        Page shop = page(2, 7, "商城", html(20, "#ff0000", 55, "M4 4L12 12"));
        String first = chrome.apply(home, List.of(home, shop), home.getHtmlContent()); home.setHtmlContent(first);
        String second = chrome.apply(shop, List.of(home, shop), shop.getHtmlContent());
        var a = Jsoup.parse(first); var b = Jsoup.parse(second);
        assertEquals(a.selectFirst("style[data-wf-chrome-style]").data(), b.selectFirst("style[data-wf-chrome-style]").data());
        assertEquals(a.selectFirst("[data-wf-chrome-part=icon]").html(), b.selectFirst("[data-wf-chrome-part=icon]").html());
        assertEquals("20", b.selectFirst("[data-wf-chrome-part=time]").attr("data-wf-element-id"));
        assertEquals("24", b.select("[data-wf-chrome-part=item]").get(1).attr("data-wf-element-id"));
        assertEquals("商城", b.selectFirst("[aria-current=page]").attr("data-nav"));
        assertEquals("家园", b.select("[data-wf-chrome-part=item]").get(0).attr("data-nav"));
        assertFalse(second.contains("home-icon-10"));
        assertEquals(Jsoup.parse(shop.getHtmlContent()).getElementById("content").outerHtml(), b.getElementById("content").outerHtml());
    }

    @Test void regenerationOfTheReferencePageKeepsItsSavedProjectContract() {
        Page home = page(1, 7, "家园", html(10, "#efedf3", 70, "M2 2L20 20"));
        String first = chrome.apply(home, List.of(home), home.getHtmlContent()); home.setHtmlContent(first);
        String regenerated = chrome.apply(home, List.of(home), html(10, "#ff0000", 55, "M4 4L12 12"));
        var a = Jsoup.parse(first); var b = Jsoup.parse(regenerated);
        assertEquals(a.selectFirst("style[data-wf-chrome-style]").data(), b.selectFirst("style[data-wf-chrome-style]").data());
        assertEquals(a.selectFirst("[data-wf-chrome-part=icon]").html(), b.selectFirst("[data-wf-chrome-part=icon]").html());
        assertTrue(chrome.prompt(home, List.of(home)).contains("严格复用"));
        assertFalse(chrome.prompt(home, List.of(home)).contains("home-icon-10"));
    }

    @Test void flatteningDecorativeWrappersPreservesAllLocalSourceIdsAndUidsAndIsIdempotent() {
        String raw = html(10, "#efedf3", 70, "M2 2L20 20");
        Page home = page(1, 7, "家园", raw);
        String first = chrome.apply(home, List.of(home), raw); home.setHtmlContent(first);
        String second = chrome.apply(home, List.of(home), first);
        assertEquals(ids(raw, "data-wf-element-id"), ids(second, "data-wf-element-id"));
        assertTrue(ids(second, "data-wf-uid").containsAll(ids(raw, "data-wf-uid")));
        var doc = Jsoup.parse(second);
        assertEquals(doc.select("[data-wf-uid]").size(), ids(second, "data-wf-uid").size());
        assertEquals(first, second);
    }

    @Test void unrelatedProjectsAndDifferentNavigationFamiliesDoNotShareIconsOrOrder() {
        Page foreign = page(1, 99, "家园", html(10, "#ff0000", 55, "M1 1L2 2"));
        foreign.setHtmlContent(chrome.apply(foreign, List.of(foreign), foreign.getHtmlContent()));
        Page own = page(2, 7, "家园", html(20, "#efedf3", 70, "M3 3L4 4"));
        String local = chrome.apply(own, List.of(foreign, own), own.getHtmlContent());
        assertTrue(Jsoup.parse(local).selectFirst("style[data-wf-chrome-style]").data().contains("--wf-chrome-nav-bg:#efedf3"));
        own.setHtmlContent(local);
        String otherFamily = html(30, "#ff0000", 55, "M5 5L6 6").replace("家园", "动态").replace("商城", "消息");
        Page feed = page(3, 7, "动态", otherFamily);
        String result = chrome.apply(feed, List.of(own, feed), otherFamily);
        assertEquals(List.of("动态", "消息"), Jsoup.parse(result).select("[data-wf-chrome-part=label]").eachText());
        assertTrue(Jsoup.parse(result).selectFirst("[data-wf-chrome-part=icon]").html().contains("M5 5L6 6"));
    }

    @Test void modalOrDetailWithoutChromeDoesNotAcquireExtraBarsAndLocalTabsAreUntouched() {
        String raw = "<!doctype html><html><head><meta name='wf-renderer' content='ai-static-v1'></head><body><nav><button data-wf-tab='filter'>全部</button><button data-wf-tab='filter'>新品</button></nav><div role='dialog'>确认弹窗</div></body></html>";
        Page detail = page(2, 7, "详情", raw);
        assertEquals(raw, chrome.apply(detail, List.of(detail), raw));
        assertFalse(ProjectChrome.generated("<html><head></head><body><script type='application/json'>{\"popup\":\"ai-static-v1\"}</script></body></html>"));
    }

    @Test void sharedNavigationMaterializesLocalSymbolIconsSoOtherPagesDoNotNeedTheOriginalDefs() {
        String raw = html(10, "#efedf3", 70, "M2 2L20 20")
                .replace("<path d='M2 2L20 20'/>", "<use href='#home-shape'/>")
                .replace("<body>", "<body><svg hidden><defs><symbol id='home-shape'><path d='M3 10L12 3L21 10'/></symbol></defs></svg>");
        Page home = page(1, 7, "家园", raw);
        home.setHtmlContent(chrome.apply(home, List.of(home), raw));
        Page shop = page(2, 7, "商城", html(20, "#ff0000", 55, "M4 4L12 12"));
        String result = chrome.apply(shop, List.of(home, shop), shop.getHtmlContent());
        var icon = Jsoup.parse(result).selectFirst("[data-wf-chrome-part=icon]");
        assertTrue(icon.html().contains("M3 10L12 3L21 10"));
        assertTrue(icon.select("use,[id]").isEmpty());
    }

    private Set<String> ids(String html, String attribute) {
        return Jsoup.parse(html).select("[" + attribute + "]").stream().map(n -> n.attr(attribute)).collect(Collectors.toSet());
    }

    @Test void legacyGrayStatusBackgroundDoesNotSpreadToLightPagesAndHeaderGradientContinues() {
        Page anchor = page(1, 7, "家园", html(10, "#efedf3", 70, "M2 2L20 20"));
        var legacy = Jsoup.parse(chrome.apply(anchor, List.of(anchor), anchor.getHtmlContent()));
        var meta = legacy.selectFirst("meta[name=wf-project-chrome]");
        meta.attr("content", meta.attr("content").replace("\"version\":3", "\"version\":1")
                .replace("\"statusBackground\":\"transparent\"", "\"statusBackground\":\"#686873\""));
        anchor.setHtmlContent(legacy.outerHtml());
        String raw = html(20, "#ff0000", 55, "M4 4L12 12")
                .replace(".screen{background:#363641}", ".screen{background:#f3f4f8}.header{background:linear-gradient(110deg,#fff7e4,#fce9e3)}")
                .replace("<article id=", "<header class='header'>商城</header><article id=");
        Page shop = page(2, 7, "商城", raw);
        String result = chrome.apply(shop, List.of(anchor, shop), raw);
        var doc = Jsoup.parse(result);
        var status = doc.selectFirst("[data-wf-chrome=status]");
        assertTrue(status.attr("style").contains("--wf-local-status-bg:linear-gradient(110deg,#fff7e4,#fce9e3)"));
        assertTrue(status.attr("style").contains("--wf-local-status-ink:#302c32"));
        assertTrue(doc.selectFirst("style[data-wf-chrome-style]").data().contains("--wf-chrome-nav-bg:#efedf3"));
        assertFalse(doc.selectFirst("meta[name=wf-project-chrome]").attr("content").contains("#686873"));
        shop.setHtmlContent(result);
        assertEquals(result, chrome.apply(shop, List.of(anchor, shop), result));
    }

    @Test void transparentStatusChoosesInkFromItsOwnPageRatherThanTheDeviceSurround() {
        Page dark = page(1, 7, "家园", html(10, "#efedf3", 70, "M2 2L20 20"));
        dark.setHtmlContent(chrome.apply(dark, List.of(dark), dark.getHtmlContent()));
        Page light = page(2, 7, "商城", html(20, "#efedf3", 70, "M2 2L20 20").replace("background:#363641", "background:#f3f4f8"));
        var darkStatus = Jsoup.parse(dark.getHtmlContent()).selectFirst("[data-wf-chrome=status]");
        var lightStatus = Jsoup.parse(chrome.apply(light, List.of(dark, light), light.getHtmlContent())).selectFirst("[data-wf-chrome=status]");
        assertTrue(darkStatus.attr("style").contains("--wf-local-status-bg:transparent"));
        assertTrue(darkStatus.attr("style").contains("--wf-local-status-ink:#ffffff"));
        assertTrue(lightStatus.attr("style").contains("--wf-local-status-ink:#302c32"));
    }

    @Test void selectedWhiteIconsAndLabelsKeepTheirGradientSurfaceAcrossPages() {
        String raw = html(10, "#fff", 70, "M2 2L20 20").replace(".nav .selected{color:#ff8c27}",
                ".nav .selected{color:#fff;background:linear-gradient(#879bfa,#88d4ff);border-radius:9px}");
        Page home = page(1, 7, "家园", raw);
        home.setHtmlContent(chrome.apply(home, List.of(home), raw));
        Page shop = page(2, 7, "商城", html(20, "#ff0000", 55, "M4 4L12 12"));
        String result = chrome.apply(shop, List.of(home, shop), shop.getHtmlContent());
        var doc = Jsoup.parse(result);
        String css = doc.selectFirst("[data-wf-chrome-style]").data();
        assertTrue(css.contains("--wf-chrome-active:#fff;"));
        assertTrue(css.contains("--wf-chrome-active-bg:linear-gradient(#879bfa,#88d4ff)"));
        assertTrue(css.contains("--wf-chrome-active-radius:9px"));
        assertTrue(css.contains("background:var(--wf-chrome-active-bg,transparent)!important"));
        assertEquals("商城", doc.selectFirst("[aria-current=page]").attr("data-nav"));
        assertEquals(ids(shop.getHtmlContent(), "data-wf-element-id"), ids(result, "data-wf-element-id"));
    }

    @Test void legacySnapshotRecoversTheBackgroundFromTheOriginalActiveRule() throws Exception {
        String raw = html(10, "#fff", 70, "M2 2L20 20").replace(".nav .selected{color:#ff8c27}",
                ".nav .selected{color:#fff;background:linear-gradient(#879bfa,#88d4ff);border-radius:9px}");
        Page home = page(1, 7, "家园", raw);
        home.setHtmlContent(legacySnapshot(chrome.apply(home, List.of(home), raw)));
        String fixed = chrome.apply(home, List.of(home), home.getHtmlContent());
        var doc = Jsoup.parse(fixed);
        assertTrue(doc.selectFirst("[data-wf-chrome-style]").data().contains("--wf-chrome-active-bg:linear-gradient(#879bfa,#88d4ff)"));
        assertTrue(doc.selectFirst("meta[name=wf-project-chrome]").attr("content").contains("\"version\":3"));
        home.setHtmlContent(fixed);
        assertEquals(fixed, chrome.apply(home, List.of(home), fixed));
    }

    @Test void selectionSurfaceCanComeFromADecorativePseudoElement() {
        String raw = html(10, "#fff", 70, "M2 2L20 20").replace(".nav .selected{color:#ff8c27}",
                ".nav .selected{color:#fff}.nav .selected::before{background:linear-gradient(#879bfa,#88d4ff);border-radius:9px}");
        Page home = page(1, 7, "家园", raw);
        var doc = Jsoup.parse(chrome.apply(home, List.of(home), raw));
        assertTrue(doc.selectFirst("[data-wf-chrome-style]").data().contains("--wf-chrome-active-bg:linear-gradient(#879bfa,#88d4ff)"));
        assertTrue(doc.selectFirst("[data-wf-chrome-style]").data().contains("--wf-chrome-active-radius:9px"));
    }

    @Test void unrecoverableWhiteOnWhiteSelectionGetsAVisibleForeground() throws Exception {
        String raw = html(10, "#fff", 70, "M2 2L20 20").replace(".nav .selected{color:#ff8c27}", ".nav .selected{color:#fff}");
        Page home = page(1, 7, "家园", raw);
        var broken = Jsoup.parse(legacySnapshot(chrome.apply(home, List.of(home), raw)));
        var meta = broken.selectFirst("meta[name=wf-project-chrome]");
        meta.attr("content", meta.attr("content").replace("#365ac7", "#fff"));
        home.setHtmlContent(broken.outerHtml());
        var doc = Jsoup.parse(chrome.apply(home, List.of(home), home.getHtmlContent()));
        assertTrue(doc.selectFirst("[data-wf-chrome-style]").data().contains("--wf-chrome-active:#365ac7;"));
    }

    @Test void pressedAndIsActiveStatesAreRecognizedWithoutANameBasedLink() {
        for (String state : List.of("aria-pressed='true'", "class='is-active'")) {
            String raw = html(10, "#fff", 70, "M2 2L20 20").replace("class='selected'", state);
            Page current = page(1, 7, "家园首页", raw);
            var doc = Jsoup.parse(chrome.apply(current, List.of(current), raw));
            assertEquals("家园", doc.selectFirst("[aria-current=page]").attr("data-nav"));
            assertEquals(1, doc.select("[data-nav-item][aria-current=page]").size());
        }
    }

    private String legacySnapshot(String html) throws Exception {
        var doc = Jsoup.parse(html);
        var meta = doc.selectFirst("meta[name=wf-project-chrome]");
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        var snapshot = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(meta.attr("content"));
        snapshot.put("version", 2);
        var theme = (com.fasterxml.jackson.databind.node.ObjectNode) snapshot.path("theme");
        theme.remove(List.of("activeBackground", "activeRadius"));
        meta.attr("content", json.writeValueAsString(snapshot));
        var style = doc.selectFirst("[data-wf-chrome-style]");
        style.text(style.data().replaceAll(";--wf-chrome-active-(?:bg|radius):[^;}]+", ""));
        doc.outputSettings().prettyPrint(false);
        return doc.outerHtml();
    }
}
