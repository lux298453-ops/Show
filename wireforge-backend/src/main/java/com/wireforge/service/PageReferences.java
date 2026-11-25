package com.wireforge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wireforge.entity.Page;
import org.jsoup.Jsoup;

import java.util.*;

/** Project-scoped name/alias lookup. Ambiguous references never choose an arbitrary page. */
public final class PageReferences {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final List<Page> pages;

    public PageReferences(List<Page> pages) {
        this.pages = pages.stream().filter(page -> !Integer.valueOf(1).equals(page.getIsDeleted())).toList();
    }

    public static String normalize(String value) {
        return value == null ? "" : value.trim().replace('\\', '/').replaceAll("[\\s\\p{Z}]+", " ").toLowerCase(Locale.ROOT);
    }

    public List<Page> candidates(String reference) {
        String key = normalize(reference);
        if (key.isBlank()) return List.of();
        List<Page> aliases = pages.stream().filter(p -> references(p).stream().anyMatch(alias -> key.equals(normalize(alias)))).toList();
        if (!aliases.isEmpty()) return aliases;
        // A qualified reference must never silently jump to another folder with the same basename.
        if (key.contains("/")) return List.of();
        return pages.stream().filter(p -> references(p).stream()
                .anyMatch(alias -> key.equals(normalize(PageNameResolver.basename(alias))))).toList();
    }

    public Page resolve(String reference) {
        List<Page> matches = candidates(reference);
        return matches.size() == 1 ? matches.get(0) : null;
    }

    public Page byId(String reference) {
        try {
            long id = Long.parseLong(reference);
            return pages.stream().filter(page -> Objects.equals(page.getId(), id)).findFirst().orElse(null);
        } catch (NumberFormatException ignored) { return null; }
    }

    public static Set<String> references(Page page) {
        Set<String> aliases = new LinkedHashSet<>();
        add(aliases, page.getName());
        add(aliases, page.getSourceName());
        try {
            var node = JSON.readTree(page.getNameAliases() == null ? "[]" : page.getNameAliases());
            if (node.isArray()) for (var item : node) if (item.isTextual()) add(aliases, item.asText());
        } catch (Exception ignored) { /* A corrupt historical alias cannot break project loading. */ }
        return aliases;
    }

    public static void remember(Page page, String... values) {
        Set<String> aliases = references(page);
        for (String value : values) add(aliases, value);
        try { page.setNameAliases(JSON.writeValueAsString(aliases)); }
        catch (Exception e) { throw new IllegalStateException("页面名称记录失败", e); }
    }

    private static void add(Set<String> aliases, String value) {
        if (value != null && !value.isBlank()) aliases.add(value.trim());
    }

    /** Update attributes only; never replace page text, button labels or saved interaction IDs. */
    public String rewriteHtml(String html) {
        if (html == null || html.isBlank() || !html.contains("data-nav")) return html;
        var doc = Jsoup.parse(html);
        doc.outputSettings().prettyPrint(false);
        boolean changed = false;
        for (var node : doc.select("[data-nav],[data-nav-page-id]")) {
            Page target = node.hasAttr("data-nav-page-id") ? byId(node.attr("data-nav-page-id")) : null;
            if (target == null) target = resolve(node.attr("data-nav"));
            if (target == null) continue;
            if (!target.getName().equals(node.attr("data-nav")) || !String.valueOf(target.getId()).equals(node.attr("data-nav-page-id"))) {
                node.attr("data-nav", target.getName()).attr("data-nav-page-id", String.valueOf(target.getId()));
                changed = true;
            }
        }
        return changed ? doc.outerHtml() : html;
    }
}
