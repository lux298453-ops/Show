package com.wireforge.service;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Playwright;
import com.wireforge.entity.Page;
import org.jsoup.Jsoup;
import org.jsoup.nodes.DataNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Offline Chromium check of native backgrounds and shared geometry, with no AI or project writes. */
@EnabledIfSystemProperty(named = "wireforge.chrome-render", matches = "true")
class ProjectChromePaletteRenderTest {
    private static String html(String background, String surface, int height) {
        return "<!doctype html><html><head><meta name='wf-renderer' content='ai-static-v1'><style>"
                + "*{box-sizing:border-box}body{margin:0}.screen{height:812px;background:" + surface + ";--ink:#acb8ce;--accent:#fff;display:flex;flex-direction:column}"
                + ".status{height:35px}article{flex:1;padding:32px;color:#537089}.nav{height:" + height + "px;" + background + ";display:flex;justify-content:space-around}"
                + ".nav button{border:0;color:var(--ink);font-size:11px}.nav .active{color:var(--accent);background:linear-gradient(90deg,#17614a,#417e61);border-radius:10px}"
                + "svg{width:22px;height:22px}</style></head><body><div class='screen'>"
                + "<div class='status'><span data-wf-uid='time'>09:41</span></div><article id='content'>原有正文与布局</article>"
                + "<nav class='nav' data-wf-chrome='bottom-navigation'><button class='active' data-nav-item='1' data-wf-uid='home' data-nav='家园'>"
                + "<svg viewBox='0 0 24 24'><path d='M2 12L12 2L22 12'/></svg><span>家园</span></button>"
                + "<button data-nav-item='1' data-wf-uid='shop' data-nav='商城'><svg viewBox='0 0 24 24'><rect x='4' y='4' width='16' height='16'/></svg><span>商城</span></button>"
                + "</nav></div></body></html>";
    }

    private static Object measure(com.microsoft.playwright.Page tab, String bar, String active, String normal) {
        return tab.evaluate("""
            selectors => {
              const bar=document.querySelector(selectors[0]), active=document.querySelector(selectors[1]), normal=document.querySelector(selectors[2]);
              const surface=getComputedStyle(bar), selected=getComputedStyle(active), inactive=getComputedStyle(normal);
              return {background:surface.backgroundColor,image:surface.backgroundImage,filter:surface.backdropFilter,
                activeInk:selected.color,activeBackground:selected.backgroundColor,activeImage:selected.backgroundImage,normalInk:inactive.color};
            }
            """, List.of(bar, active, normal));
    }

    @Test void nativeBackgroundsAndLocalSelectionColorsSurviveWithMatchingNavigationGeometry() throws Exception {
        var backgrounds = List.of("background:#fff", "background:linear-gradient(100deg,#fce9d5,#d9e9ff)",
                "background:rgba(255,255,255,.45);backdrop-filter:blur(4px)", "background:transparent",
                "background-color:#182540;background-image:linear-gradient(90deg,rgba(40,70,100,.6),transparent)");
        var chrome = new ProjectChrome();
        List<Page> pages = new ArrayList<>();
        for (int i = 0; i < backgrounds.size(); i++) {
            Page page = new Page(); page.setId((long) i + 1); page.setProjectId(7L); page.setName(i == 0 ? "家园" : "商城");
            page.setCanvasHeight(812); page.setHtmlContent(html(backgrounds.get(i), i % 2 == 0 ? "#ede4d9" : "#172538", i == 0 ? 72 : 55));
            pages.add(page);
        }
        Path output = Path.of("target/nav-palette-review"); Files.createDirectories(output);
        try (Playwright playwright = Playwright.create(); Browser browser = playwright.chromium().launch();
             var tab = browser.newPage(new Browser.NewPageOptions().setViewportSize(375, 812))) {
            tab.route("**/*", route -> route.abort());
            for (int i = 0; i < pages.size(); i++) {
                Page page = pages.get(i);
                String raw = page.getHtmlContent();
                tab.setContent(raw);
                Object expected = measure(tab, ".nav", ".nav .active", ".nav button:not(.active)");
                String normalized = chrome.apply(page, pages, raw);
                var before = Jsoup.parse(raw); var after = Jsoup.parse(normalized);
                assertEquals(before.getElementById("content").outerHtml(), after.getElementById("content").outerHtml());
                assertTrue(after.select("[data-wf-uid]").eachAttr("data-wf-uid").containsAll(before.select("[data-wf-uid]").eachAttr("data-wf-uid")));
                assertEquals(before.select("[data-nav]").eachAttr("data-nav"), after.select("[data-nav]").eachAttr("data-nav"));
                tab.setContent(normalized);
                assertEquals(expected, measure(tab, ".nav", ".nav [aria-current=page]", ".nav [aria-current=false]"), "palette " + i);
                @SuppressWarnings("unchecked") Map<String, Object> geometry = (Map<String, Object>) tab.evaluate("""
                    () => { const bar=document.querySelector('.nav'), active=bar.querySelector('[aria-current=page]'), icon=active.querySelector('svg');
                      return {height:bar.getBoundingClientRect().height,bottom:bar.getBoundingClientRect().bottom,
                        selectedHeight:active.getBoundingClientRect().height,iconWidth:icon.getBoundingClientRect().width}; }
                    """);
                assertEquals(72, ((Number) geometry.get("height")).intValue());
                assertEquals(812, ((Number) geometry.get("bottom")).intValue());
                assertEquals(40, ((Number) geometry.get("selectedHeight")).intValue());
                assertEquals(22, ((Number) geometry.get("iconWidth")).intValue());
                tab.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions().setPath(output.resolve("navigation-" + i + ".png")));
                Files.writeString(output.resolve("navigation-" + i + ".html"), normalized);
                page.setHtmlContent(normalized);
                assertEquals(normalized, chrome.apply(page, pages, normalized));
            }
        }
    }

    @Test void legacyPreviewRecoversItsOwnPaletteEvenWhenTheSavedSharedColorsAreDifferent() throws Exception {
        String raw = html("background:linear-gradient(100deg,#fce9d5,#d9e9ff)", "#172538", 72);
        Page page = new Page(); page.setId(1L); page.setProjectId(7L); page.setName("商城"); page.setCanvasHeight(812); page.setHtmlContent(raw);
        var legacy = Jsoup.parse(new ProjectChrome().apply(page, List.of(page), raw));
        var bar = legacy.selectFirst("[data-wf-shared-chrome=navigation]");
        bar.removeAttr("data-wf-chrome-palette").removeAttr("style");
        String variables = ":root{--wf-chrome-nav-height:72px;--wf-chrome-nav-font:11px;--wf-chrome-status-height:35px;--wf-chrome-viewport-height:812px;--wf-chrome-active-radius:10px;"
                + "--wf-chrome-nav-bg:#ff0000;--wf-chrome-active:#ff0000;--wf-chrome-active-bg:#ff0000;}";
        var style = legacy.selectFirst("style[data-wf-chrome-style]");
        style.empty().appendChild(new DataNode(variables + "body [data-wf-shared-chrome=navigation]{background:var(--wf-chrome-nav-bg)!important}"));
        try (Playwright playwright = Playwright.create(); Browser browser = playwright.chromium().launch();
             var tab = browser.newPage(new Browser.NewPageOptions().setViewportSize(375, 812))) {
            tab.route("**/*", route -> route.abort());
            tab.setContent(raw);
            Object expected = measure(tab, ".nav", ".nav .active", ".nav button:not(.active)");
            tab.setContent(legacy.outerHtml());
            assertNotEquals(expected, measure(tab, ".nav", ".nav [aria-current=page]", ".nav [aria-current=false]"));
            // The host installer retains saved metrics and replaces only owned common CSS (also covered by the frontend tests).
            style.empty().appendChild(new DataNode(variables + Files.readString(Path.of("src/main/resources/prototype/chrome.css"))
                    + Files.readString(Path.of("src/main/resources/prototype/chrome-selection.css"))
                    + Files.readString(Path.of("src/main/resources/prototype/chrome-layout.css"))));
            legacy.body().appendElement("script").appendChild(new DataNode(Files.readString(Path.of("src/main/resources/prototype/chrome-selection.js"))));
            tab.setContent(legacy.outerHtml());
            assertEquals(expected, measure(tab, ".nav", ".nav [aria-current=page]", ".nav [aria-current=false]"));
            assertEquals(List.of("家园", "商城"), tab.locator("[data-nav]").evaluateAll("nodes => nodes.map(n => n.getAttribute('data-nav'))"));
            Path output = Path.of("target/nav-palette-review"); Files.createDirectories(output);
            tab.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions().setPath(output.resolve("legacy-repaired.png")));
        }
    }
}
