package com.wireforge.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.wireforge.entity.Page;
import org.jsoup.Jsoup;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;

/** Page classification comes from recognition or an explicit override, never the display name. */
public final class PageTypes {
    public static final String PAGE = "page", MODAL = "modal", DRAWER = "drawer", UNKNOWN = "unknown";
    private static final Set<String> TYPES = Set.of(PAGE, MODAL, DRAWER, UNKNOWN);
    private PageTypes() {}

    public static String normalize(String value) {
        if (value == null) return UNKNOWN;
        String type = value.trim().toLowerCase(Locale.ROOT);
        return TYPES.contains(type) ? type : UNKNOWN;
    }

    public static boolean valid(String value) { return value != null && TYPES.contains(value); }

    public static String effective(Page page) {
        String type = normalize(page.getPageType());
        return UNKNOWN.equals(type) && page.getPageTypeSource() == null ? fromHtml(page.getHtmlContent()) : type;
    }

    public static boolean overlay(Page page) {
        String type = effective(page);
        return MODAL.equals(type) || DRAWER.equals(type);
    }

    public static String prompt(Page page) {
        return "\n本页独立类型：" + normalize(page.getPageType())
                + "（page=普通页面，modal=模态弹窗，drawer=抽屉，unknown=待判断）。类型与页面名称无关。"
                + "已确定类型时按该类型生成；unknown时根据截图结构判断。整页弹窗/抽屉的可见前景必须标记"
                + "role=dialog与data-wf-role=modal/drawer，关闭返回原页面；普通页面中的局部弹窗保持默认隐藏。\n";
    }

    public static String fromAnalysis(JsonNode root) {
        String type = normalize(root.path("page_type").asText(""));
        if (!UNKNOWN.equals(type)) return type;
        // Older model responses may omit page_type. Only explicit structural roles are accepted.
        for (JsonNode element : root.path("elements")) {
            String role = element.path("type").asText("");
            if ("drawer".equals(role) || "sheet".equals(role)) return DRAWER;
            if ("modal".equals(role) || "dialog".equals(role)) return MODAL;
        }
        return UNKNOWN;
    }

    public static String fromHtml(String html) {
        if (html == null || html.isBlank()) return UNKNOWN;
        var doc = Jsoup.parse(html);
        var metadata = doc.selectFirst("meta[name=wf-page-type]");
        if (metadata != null && !UNKNOWN.equals(normalize(metadata.attr("content")))) return normalize(metadata.attr("content"));
        for (var node : doc.select("[role=dialog],[aria-modal=true],dialog[open],[data-wf-role=modal],[data-wf-role=drawer],[data-wf-role=sheet]")) {
            boolean hidden = false;
            for (var parent = node; parent != null; parent = parent.parent()) {
                String style = parent.attr("style").replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
                if (parent.hasAttr("hidden") || "true".equals(parent.attr("aria-hidden"))
                        || style.contains("display:none") || style.contains("visibility:hidden")
                        || parent.hasClass("wf-modal") && !parent.hasClass("wf-show")) { hidden = true; break; }
            }
            if (!hidden) return node.is("[data-wf-role=drawer],[data-wf-role=sheet]") ? DRAWER : MODAL;
        }
        return UNKNOWN;
    }

    /** One-time historical compatibility based on markup and saved interactions; uncertain types stay unknown. */
    static String legacy(Page page, Collection<String> incomingActions) {
        String html = fromHtml(page.getHtmlContent());
        if (!UNKNOWN.equals(html)) return html;
        if (!incomingActions.contains("navigate") && incomingActions.stream().anyMatch(action -> action != null && Set.of("popup", "modal").contains(action))) return MODAL;
        return UNKNOWN;
    }
}
