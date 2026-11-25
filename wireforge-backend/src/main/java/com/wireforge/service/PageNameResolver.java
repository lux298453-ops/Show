package com.wireforge.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.wireforge.entity.Page;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Display naming only: business keywords and export numbers never determine a page's identity or type. */
public final class PageNameResolver {
    private static final Pattern EXTENSION = Pattern.compile("(?i)\\.(png|jpe?g|webp)$");
    private static final Pattern RESOLUTION = Pattern.compile("(?i)@\\d+x");
    private static final Pattern GENERIC = Pattern.compile(
            "(?i)^(image|img|screenshot|screen shot|frame|group|未命名|新建|截屏|截图|画板|组)[\\s\\p{Z}_\\-()（）\\d.]*$");
    private static final Set<String> CONFIRMED = Set.of("filename", "ai", "user", "legacy");

    private PageNameResolver() {}

    public record Resolution(String name, String source) {}

    public static Resolution resolve(Page page, String suggestedName, List<Page> peers) {
        String current = text(page.getName());
        String origin = text(page.getNameSource());
        // Historical and user names are protected; only a fresh scan gets an automatic name.
        if (!current.isBlank() && (CONFIRMED.contains(origin)
                || origin.isBlank() && Integer.valueOf(1).equals(page.getAnalyzed()))) {
            return new Resolution(current, origin.isBlank() ? "legacy" : origin);
        }
        String source = text(page.getSourceName());
        if (source.isBlank()) source = current.isBlank() ? basename(page.getBackgroundImage()) : current;
        String cleaned = cleanSourceName(source);
        String leaf = basename(cleaned);
        String group = cleaned.substring(0, cleaned.length() - leaf.length());
        String proposal = basename(cleanSourceName(suggestedName));
        String resolved = cleaned;
        String nameSource = "filename";
        if (isGenericFile(leaf)) {
            if (!proposal.isBlank() && !isGenericFile(proposal)) {
                resolved = group + proposal;
                nameSource = "ai";
            } else {
                // Keep an uninformative filename retryable instead of locking an invented name.
                nameSource = "scan";
            }
        }
        if (resolved.isBlank()) resolved = "未命名页面";
        return new Resolution(unique(resolved, page, peers), nameSource);
    }

    /** Compatibility for callers without a stored page; OCR text has no naming side effects. */
    public static String resolve(String path, JsonNode ignoredElements, String suggestedName) {
        Page page = new Page();
        page.setSourceName(basename(path));
        page.setNameSource("scan");
        return resolve(page, suggestedName, List.of()).name();
    }

    public static String cleanSourceName(String value) {
        String normalized = text(value).replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String prefix = slash < 0 ? "" : normalized.substring(0, slash + 1);
        String leaf = slash < 0 ? normalized : normalized.substring(slash + 1);
        leaf = EXTENSION.matcher(leaf).replaceFirst("");
        leaf = RESOLUTION.matcher(leaf).replaceAll("").trim();
        if (leaf.endsWith("弹框")) leaf = leaf.substring(0, leaf.length() - 2) + "弹窗";
        return (prefix + leaf).replaceAll("[\\s\\p{Z}]+", " ").trim();
    }

    public static String basename(String value) {
        String normalized = text(value).replace('\\', '/');
        return normalized.substring(normalized.lastIndexOf('/') + 1);
    }

    public static boolean isGenericFile(String value) {
        String name = text(value);
        return name.isBlank() || name.matches("^[\\d_\\-\\s.]+$") || GENERIC.matcher(name).matches()
                || name.matches("(?i)^[a-f\\d]{24,}$")
                || name.matches("(?i)^[a-f\\d]{8}(?:-[a-f\\d]{4}){3}-[a-f\\d]{12}$");
    }

    private static String unique(String proposed, Page page, List<Page> peers) {
        String base = limit(proposed, 255), name = base;
        int attempt = 1;
        while (conflicts(name, page.getId(), peers)) {
            String suffix = page.getId() == null ? "（" + (++attempt) + "）"
                    : "（#" + page.getId() + (attempt++ > 1 ? "-" + attempt : "") + "）";
            name = limit(base, 255 - suffix.length()) + suffix;
        }
        return name;
    }

    private static boolean conflicts(String name, Long id, List<Page> peers) {
        return peers.stream().anyMatch(other -> !Objects.equals(id, other.getId())
                && !Integer.valueOf(1).equals(other.getIsDeleted())
                && PageReferences.references(other).stream().anyMatch(alias -> PageReferences.normalize(name).equals(PageReferences.normalize(alias))));
    }

    private static String limit(String value, int length) {
        return value.codePointCount(0, value.length()) <= length ? value : value.substring(0, value.offsetByCodePoints(0, length));
    }

    private static String text(String value) { return value == null ? "" : value.trim(); }
}
