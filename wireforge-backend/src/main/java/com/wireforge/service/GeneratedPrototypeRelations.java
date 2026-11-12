package com.wireforge.service;

import com.wireforge.entity.Element;
import com.wireforge.entity.Interaction;
import com.wireforge.entity.Page;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.util.*;

/** Reads declared prototype links and the actual shared navigation; never invents pages or controls. */
public final class GeneratedPrototypeRelations {
    private static final String BAR = "[data-wf-chrome=bottom-navigation],.bottom-nav,.bottom-navigation,.wf-tabbar,.wf-canonical-nav";
    private static final String ITEM = "[data-nav-item],[data-wf-chrome-part=item]";

    private GeneratedPrototypeRelations() {}

    public record MainItem(String label, Long targetId) {}
    public record Link(long pageId, long elementId, List<Long> memberIds, long targetId, String domUid) {}
    private record Bar(Page page, org.jsoup.nodes.Element node, List<org.jsoup.nodes.Element> items) {}

    public static List<MainItem> mainItems(List<Page> pages) {
        return mainItems(pages, List.of());
    }

    public static List<MainItem> mainItems(List<Page> pages, List<Interaction> lines) {
        return mainItems(bars(pages), pages, lines);
    }

    public static List<Link> links(List<Page> pages, List<Element> elements) {
        return links(pages, elements, List.of());
    }

    public static List<Link> links(List<Page> pages, List<Element> elements, List<Interaction> lines) {
        List<Bar> bars = bars(pages);
        List<MainItem> main = mainItems(bars, pages, lines);
        Bar reference = reference(bars);
        List<Link> result = new ArrayList<>();
        for (Page page : pages) {
            if (!hasPrototypeHtml(page)) continue;
            List<Element> sources = elements.stream().filter(e -> page.getId().equals(e.getPageId())).toList();
            Map<Long, Element> byId = new LinkedHashMap<>();
            sources.forEach(e -> byId.put(e.getId(), e));
            Document doc = Jsoup.parse(page.getHtmlContent());
            org.jsoup.nodes.Element nav = doc.selectFirst(BAR);
            List<org.jsoup.nodes.Element> navItems = nav == null ? List.of() : items(nav);
            Bar current = new Bar(page, nav, navItems);
            Set<org.jsoup.nodes.Element> controls = new LinkedHashSet<>(doc.select("[data-nav]"));
            if (reference != null && compatible(reference, current)) controls.addAll(navItems);
            for (org.jsoup.nodes.Element control : controls) {
                if (control.closest("[data-wf-autowire-modal],.wf-autowire-modal") != null) continue;
                List<Long> ids = boundIds(control, byId);
                if (ids.isEmpty()) continue;
                Element owner = ids.stream().map(byId::get).filter(GeneratedPrototypeRelations::clickable)
                        .min(Comparator.comparingInt(e -> ownerRank(e, control))).orElse(null);
                if (owner == null) continue;
                Page target = uniqueTarget(control.attr("data-nav"), pages);
                int slot = navItems.indexOf(control);
                if (slot >= 0 && reference != null && compatible(reference, current) && slot < main.size()) {
                    Long canonical = main.get(slot).targetId();
                    if (target == null && canonical != null) {
                        Page candidate = pages.stream().filter(p -> canonical.equals(p.getId())).findFirst().orElse(null);
                        String declared = control.attr("data-nav");
                        if (candidate != null && (declared.isBlank() || name(candidate.getName()).equals(name(declared)))) target = candidate;
                    }
                }
                if (target == null || target.getId().equals(page.getId())) continue;
                String uid = control.attr("data-wf-uid");
                if (uid.isBlank()) uid = "wf-p" + page.getId() + "-e" + owner.getId();
                result.add(new Link(page.getId(), owner.getId(), ids, target.getId(), uid));
            }
        }
        // More than one declaration for the same owner must agree before anything is synchronized.
        Map<Long, List<Link>> grouped = new LinkedHashMap<>();
        result.forEach(link -> grouped.computeIfAbsent(link.elementId(), k -> new ArrayList<>()).add(link));
        return grouped.values().stream().filter(group -> group.stream().map(Link::targetId).distinct().count() == 1)
                .map(group -> group.get(0)).toList();
    }

    private static List<MainItem> mainItems(List<Bar> bars, List<Page> pages, List<Interaction> lines) {
        Bar reference = reference(bars);
        if (reference == null) return List.of();
        List<Bar> family = bars.stream().filter(bar -> compatible(reference, bar)).toList();
        List<MainItem> result = new ArrayList<>();
        for (int slot = 0; slot < reference.items().size(); slot++) {
            Map<String, Map<Long, Integer>> votes = new LinkedHashMap<>();
            for (Bar bar : family) {
                org.jsoup.nodes.Element item = bar.items().get(slot);
                Page explicit = explicitTarget(item, pages, lines);
                if (explicit != null) vote(votes, explicit, 2);
                // An active item with an explicit different destination belongs to a child/overlay page.
                if (active(item) && (explicit == null ? item.attr("data-nav").isBlank() : explicit.getId().equals(bar.page().getId())))
                    vote(votes, bar.page(), 3);
            }
            List<Map.Entry<String, Map<Long, Integer>>> ranked = new ArrayList<>(votes.entrySet());
            ranked.sort(Comparator.comparingInt((Map.Entry<String, Map<Long, Integer>> e) -> e.getValue().values().stream().mapToInt(Integer::intValue).sum()).reversed());
            Long target = null;
            if (!ranked.isEmpty()) {
                int best = ranked.get(0).getValue().values().stream().mapToInt(Integer::intValue).sum();
                int second = ranked.size() > 1 ? ranked.get(1).getValue().values().stream().mapToInt(Integer::intValue).sum() : -1;
                if (best > second) {
                    // Same-name active variants share one main entry; prefer the project's first variant.
                    Set<Long> ids = ranked.get(0).getValue().keySet();
                    target = pages.stream().filter(p -> ids.contains(p.getId())).min(pageOrder()).map(Page::getId).orElse(null);
                }
            }
            if (target == null) {
                Page exact = uniqueTarget(label(reference.items().get(slot)), pages);
                if (exact != null) target = exact.getId();
            }
            result.add(new MainItem(label(reference.items().get(slot)), target));
        }
        return List.copyOf(result);
    }

    private static void vote(Map<String, Map<Long, Integer>> votes, Page page, int weight) {
        votes.computeIfAbsent(name(page.getName()), k -> new LinkedHashMap<>()).merge(page.getId(), weight, Integer::sum);
    }

    private static Page explicitTarget(org.jsoup.nodes.Element item, List<Page> pages, List<Interaction> lines) {
        Set<String> ids = new HashSet<>();
        item.getAllElements().forEach(node -> ids.add(node.attr("data-wf-element-id")));
        List<Long> targets = lines.stream().filter(line -> ids.contains(String.valueOf(line.getElementId()))
                && "navigate".equals(line.getActionType()) && line.getTargetPageId() != null)
                .map(Interaction::getTargetPageId).filter(id -> pages.stream().anyMatch(p -> p.getId().equals(id))).distinct().toList();
        if (targets.size() == 1) return pages.stream().filter(p -> p.getId().equals(targets.get(0))).findFirst().orElse(null);
        return targets.isEmpty() ? uniqueTarget(item.attr("data-nav"), pages) : null;
    }

    private static List<Bar> bars(List<Page> pages) {
        List<Bar> result = new ArrayList<>();
        for (Page page : pages) {
            if (!hasPrototypeHtml(page)) continue;
            Document doc = Jsoup.parse(page.getHtmlContent());
            org.jsoup.nodes.Element nav = doc.selectFirst(BAR);
            if (nav == null) continue;
            List<org.jsoup.nodes.Element> items = items(nav);
            if (items.size() >= 2) result.add(new Bar(page, nav, items));
        }
        return result;
    }

    private static Bar reference(List<Bar> bars) {
        return bars.stream().max(Comparator.comparingLong((Bar bar) -> bars.stream().filter(peer -> compatible(bar, peer)).count())
                .thenComparing(Comparator.comparing((Bar bar) -> bar.page(), pageOrder()).reversed())).orElse(null);
    }

    private static boolean compatible(Bar a, Bar b) {
        if (a.items().size() != b.items().size() || a.items().isEmpty()) return false;
        int same = 0;
        for (int i = 0; i < a.items().size(); i++) if (name(label(a.items().get(i))).equals(name(label(b.items().get(i))))) same++;
        return same >= Math.max(2, (a.items().size() + 1) / 2);
    }

    private static List<org.jsoup.nodes.Element> items(org.jsoup.nodes.Element nav) {
        List<org.jsoup.nodes.Element> result = new ArrayList<>(nav.select(ITEM));
        if (result.isEmpty()) result.addAll(nav.select("button,a,.nav-item,.wf-tabit,.tab-item"));
        result.removeIf(node -> node.parents().stream().anyMatch(parent -> parent != nav && parent.is(ITEM)));
        return result;
    }

    private static List<Long> boundIds(org.jsoup.nodes.Element control, Map<Long, Element> sources) {
        List<Long> result = new ArrayList<>();
        for (org.jsoup.nodes.Element node : control.getAllElements()) {
            if (!node.hasAttr("data-wf-element-id")) continue;
            org.jsoup.nodes.Element nested = node.closest("[data-nav]");
            if (nested != null && nested != control && control.hasAttr("data-nav")) continue;
            try {
                long id = Long.parseLong(node.attr("data-wf-element-id"));
                if (sources.containsKey(id)) result.add(id);
            } catch (NumberFormatException ignored) { }
        }
        return result.stream().distinct().toList();
    }

    private static boolean clickable(Element element) {
        return Set.of("button", "icon", "tab", "tabs", "nav_item").contains(AutowirePlanner.safe(element.getType()))
                && !"member".equals(element.getGroupRole());
    }

    private static int ownerRank(Element element, org.jsoup.nodes.Element node) {
        if (String.valueOf(element.getId()).equals(node.attr("data-wf-element-id"))) return 0;
        if ("anchor".equals(element.getGroupRole())) return 1;
        return "button".equals(element.getType()) ? 2 : 3;
    }

    public static Page uniqueTarget(String value, List<Page> pages) {
        if (value == null || value.isBlank()) return null;
        List<Page> exact = pages.stream().filter(p -> AutowirePlanner.normalize(p.getName()).equals(AutowirePlanner.normalize(value))).toList();
        if (exact.size() == 1) return exact.get(0);
        if (!exact.isEmpty()) return null;
        List<Page> renamed = pages.stream().filter(p -> name(p.getName()).equals(name(value))).toList();
        return renamed.size() == 1 ? renamed.get(0) : null;
    }

    private static String name(String value) {
        String normalized = AutowirePlanner.safe(value).replace('\\', '/').replace('\u00a0', ' ');
        return AutowirePlanner.normalize(normalized.substring(normalized.lastIndexOf('/') + 1));
    }

    private static String label(org.jsoup.nodes.Element item) {
        org.jsoup.nodes.Element label = item.selectFirst("[data-wf-chrome-part=label]");
        return (label == null ? item.text() : label.text()).trim();
    }

    private static boolean active(org.jsoup.nodes.Element item) {
        return "page".equals(item.attr("aria-current")) || "true".equals(item.attr("aria-selected"))
                || item.classNames().stream().anyMatch(Set.of("active", "selected", "current", "wf-nav-active", "is-active")::contains);
    }

    static boolean hasPrototypeHtml(Page page) {
        String html = page.getHtmlContent();
        // Editor saves can remove renderer metadata; stable bindings remain the source of ownership.
        return html != null && (html.contains("ai-static-v1")
                || (html.contains("data-wf-uid=") && html.contains("data-wf-element-id=")));
    }

    private static Comparator<Page> pageOrder() {
        return Comparator.comparing(Page::getSortOrder, Comparator.nullsLast(Integer::compareTo)).thenComparing(Page::getId);
    }
}
