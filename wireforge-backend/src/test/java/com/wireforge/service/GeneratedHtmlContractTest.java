package com.wireforge.service;

import com.wireforge.entity.Element;
import com.wireforge.entity.Page;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class GeneratedHtmlContractTest {
    private final GeneratedHtmlContract contract = new GeneratedHtmlContract();
    private final Page page = page(1, "商城");
    private final Page detail = page(2, "商品详情");
    private final Element button = source(10);

    private static Page page(long id, String name) { Page p = new Page(); p.setId(id); p.setName(name); return p; }
    private static Element source(long id) { Element e = new Element(); e.setId(id); return e; }
    private String prepare(String body) { return contract.prepare("<!doctype html><html><head><style>:root{--primary:#ff7022}</style></head><body>" + body + "</body></html>", page, List.of(button), List.of(page, detail), Set.of("/api/assets/u-witch"), Set.of(10L)); }

    @Test void keepsStaticEditsAndStableIdsAcrossSerialization() {
        String result = prepare("<button data-wf-element-id='10' data-nav='商品详情'>购买</button><p>说明</p>");
        var doc = Jsoup.parse(result);
        assertEquals("wf-p1-e10", doc.selectFirst("button").attr("data-wf-uid"));
        doc.selectFirst("button").text("购买·已编辑");
        String reloaded = contract.prepare(GeneratedHtmlContract.forModel(doc.outerHtml()), page, List.of(button), List.of(page, detail), Set.of(), Set.of(10L));
        var saved = Jsoup.parse(reloaded);
        assertEquals("购买·已编辑", saved.selectFirst("button").text());
        assertEquals("wf-p1-e10", saved.selectFirst("button").attr("data-wf-uid"));
        assertEquals(2, saved.select("script").size());
        assertEquals(1, saved.select("script[data-wf-prototype-runtime]").size());
        assertEquals(1, saved.select("script[data-wf-controls-runtime]").size());
        assertFalse(saved.select("script").stream().anyMatch(s -> s.data().contains("innerHTML")));
        assertEquals(1, saved.select("style[data-wf-controls-style]").size());
    }

    @Test void refusesTheLoadTimeRenderThatOverwroteEditorChangesInTheTrial() {
        assertThrows(IllegalArgumentException.class, () -> prepare("<button data-wf-element-id='10'>购买</button><script>document.body.innerHTML='原始文案'</script>"));
        assertThrows(IllegalArgumentException.class, () -> prepare("<button data-wf-element-id='10' onclick='render()'>购买</button>"));
    }

    @Test void requiresRealUniqueBindingsForClickableSourceElements() {
        assertThrows(IllegalArgumentException.class, () -> prepare("<button>购买</button>"));
        assertThrows(IllegalArgumentException.class, () -> prepare("<button data-wf-element-id='999'>购买</button>"));
        assertThrows(IllegalArgumentException.class, () -> prepare("<button data-wf-element-id='10'>购买</button><a data-wf-element-id='10'>详情</a>"));
    }

    @Test void usesOnlySuppliedAssetsAndRemovesInventedDestinations() {
        var doc = Jsoup.parse(prepare("<button data-wf-element-id='10' data-nav='编造页面'>购买</button><img src='/api/assets/u-witch' alt='魔女'><img src='https://fake.test/character.png' alt='角色'><svg viewBox='0 0 24 24'></svg>"));
        assertEquals("/api/assets/u-witch", doc.selectFirst("img").attr("src"));
        assertEquals(1, doc.select("img").size());
        assertEquals("角色", doc.selectFirst("[data-wf-asset-missing]").text());
        assertFalse(doc.selectFirst("button").hasAttr("data-nav"));
        assertEquals("20", doc.selectFirst("svg").attr("width"));
        assertThrows(IllegalArgumentException.class, () -> prepare("<style>@import url('https://fake.test/font.css');</style><button data-wf-element-id='10'>购买</button>"));
    }

    @Test void validatesLocalModalReferencesAndAvoidsDuplicateRuntimeOnRepair() {
        assertThrows(IllegalArgumentException.class, () -> prepare("<button data-wf-element-id='10' data-modal='missing'>购买</button>"));
        var doc = Jsoup.parse(prepare("<button data-wf-element-id='10' data-modal='detail'>购买</button><div id='wf-modal-detail'><button data-action='close'>关闭</button></div>"));
        assertTrue(doc.getElementById("wf-modal-detail").hasClass("wf-modal"));
        assertEquals("dialog", doc.getElementById("wf-modal-detail").attr("role"));
        String clean = GeneratedHtmlContract.forModel(doc.outerHtml());
        assertFalse(clean.contains("<script"));
        assertFalse(clean.contains("wf-prototype-feedback"));
        assertFalse(clean.contains("data-wf-controls-style"));
    }

    @Test void suppliesSharedControlsWithoutChangingEditableRootsOrBindings() {
        var doc = Jsoup.parse(prepare("<button data-wf-element-id='10' data-wf-control='switch' role='switch' aria-checked='false' aria-label='挂件开关'></button>"
                + "<input type='checkbox' data-wf-control='checkbox' checked aria-label='同意协议'>"));
        assertEquals("wf-p1-e10", doc.selectFirst("[role=switch]").attr("data-wf-uid"));
        assertTrue(doc.selectFirst("input").hasAttr("checked"));
        assertTrue(doc.selectFirst("style[data-wf-controls-style]").data().contains("border: 0 !important"));
        assertTrue(doc.selectFirst("script[data-wf-controls-runtime]").elementSiblingIndex()
                < doc.selectFirst("script[data-wf-prototype-runtime]").elementSiblingIndex());
    }
}
