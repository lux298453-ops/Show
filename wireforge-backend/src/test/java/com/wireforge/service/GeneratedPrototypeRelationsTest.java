package com.wireforge.service;

import com.wireforge.entity.Element;
import com.wireforge.entity.Page;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GeneratedPrototypeRelationsTest {
    private static final List<String> LABELS = List.of("家园", "养成", "商城", "精灵", "我的", "社区");

    private Page page(long id, String name, String body) {
        Page page = new Page(); page.setId(id); page.setProjectId(1L); page.setName(name); page.setSortOrder((int)id);
        page.setHtmlContent("<html><head><meta name='wf-renderer' content='ai-static-v1'></head><body>" + body + "</body></html>");
        return page;
    }

    private List<Page> pages(int count) {
        List<Page> pages = new ArrayList<>();
        for (int active = 0; active < count; active++) {
            StringBuilder nav = new StringBuilder("<nav data-wf-chrome='bottom-navigation'>");
            for (int slot = 0; slot < count; slot++) nav.append("<button data-nav-item='1' data-wf-element-id='")
                    .append((active + 1) * 100 + slot).append("' aria-current='").append(active == slot ? "page" : "false")
                    .append("'>").append(LABELS.get(slot)).append("</button>");
            pages.add(page(active + 1, LABELS.get(active), nav.append("</nav>").toString()));
        }
        return pages;
    }

    private Element element(long id, long pageId) {
        Element element = new Element(); element.setId(id); element.setPageId(pageId); element.setType("button"); return element;
    }

    @Test void actualNavigationDeterminesThreeFiveAndSixMainDestinations() {
        for (int count : List.of(3, 5, 6)) {
            var main = GeneratedPrototypeRelations.mainItems(pages(count));
            assertEquals(count, main.size());
            for (int slot = 0; slot < count; slot++) assertEquals(slot + 1L, main.get(slot).targetId());
        }
    }

    @Test void sameNameVariantsAndChildPagesDoNotCreateExtraMainEntries() {
        List<Page> pages = pages(5);
        Page variant = page(20, "养成", ""); variant.setHtmlContent(pages.get(1).getHtmlContent()); pages.add(variant);
        Page dialog = page(30, "奖励弹窗", ""); dialog.setHtmlContent(pages.get(0).getHtmlContent().replace("aria-current='page'", "aria-current='page' data-nav='家园'")); pages.add(dialog);
        assertEquals(List.of(1L, 2L, 3L, 4L, 5L), GeneratedPrototypeRelations.mainItems(pages).stream().map(GeneratedPrototypeRelations.MainItem::targetId).toList());
    }

    @Test void nonBreakingSpacesAndAlternateLabelsStillIdentifyTheSharedNavigation() {
        List<Page> pages = pages(5);
        pages.get(1).setHtmlContent(pages.get(1).getHtmlContent().replace(">家园<", ">\u00a0家园<").replace(">精灵<", ">收集<"));
        assertEquals(2L, GeneratedPrototypeRelations.mainItems(pages).get(1).targetId());
    }

    @Test void wrappersResolveThroughOwnedChildIdsAndLegacyRenamedTargets() {
        Page source = page(1, "家园", "<button data-wf-uid='backpack' data-nav='5/背包弹窗'><svg data-wf-element-id='10'></svg><span>背包</span></button>");
        Page target = page(2, "背包弹窗", "<section role='dialog'>背包</section>");
        var links = GeneratedPrototypeRelations.links(List.of(source, target), List.of(element(10, 1)));
        assertEquals(1, links.size()); assertEquals(10L, links.get(0).elementId()); assertEquals(2L, links.get(0).targetId()); assertEquals("backpack", links.get(0).domUid());
        source.setHtmlContent(source.getHtmlContent().replace("<meta name='wf-renderer' content='ai-static-v1'>", ""));
        assertEquals(1, GeneratedPrototypeRelations.links(List.of(source, target), List.of(element(10, 1))).size());
    }

    @Test void unknownForeignAmbiguousAndConflictingDeclarationsAreNotLinked() {
        Page source = page(1, "家园", "<button data-nav='不存在' data-wf-element-id='10'></button><button data-nav='背包' data-wf-element-id='999'></button>");
        Page target = page(2, "背包", "背包");
        assertTrue(GeneratedPrototypeRelations.links(List.of(source, target), List.of(element(10, 1))).isEmpty());
        source.setHtmlContent(source.getHtmlContent().replace("不存在", "背包"));
        assertTrue(GeneratedPrototypeRelations.links(List.of(source, target, page(3, "背包", "另一个背包")), List.of(element(10, 1))).isEmpty());
        source = page(1, "家园", "<button data-nav='背包' data-wf-element-id='10'></button><button data-nav='商城' data-wf-element-id='10'></button>");
        assertTrue(GeneratedPrototypeRelations.links(List.of(source, target, page(3, "商城", "商城")), List.of(element(10, 1))).isEmpty());
    }

    @Test void navigationWithoutExplicitLinksUsesActualActiveDestinationsAndKeepsSelfLocal() {
        List<Page> pages = pages(5); List<Element> elements = new ArrayList<>();
        for (int page = 1; page <= 5; page++) for (int slot = 0; slot < 5; slot++) elements.add(element(page * 100L + slot, page));
        var links = GeneratedPrototypeRelations.links(pages, elements);
        assertEquals(20, links.size()); assertTrue(links.stream().noneMatch(link -> link.pageId() == link.targetId()));
    }
}
