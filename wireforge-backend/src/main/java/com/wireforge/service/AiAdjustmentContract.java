package com.wireforge.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Validate adjustment output before it can become a preview or a saved version. */
final class AiAdjustmentContract {
    private static final List<String> IDS = List.of("id", "data-wf-id", "data-wf-element-id", "data-wf-uid", "data-wf-select-id");
    private static final List<String> INTERACTIONS = List.of("data-nav", "data-action", "data-modal-target", "data-wf-role");
    private static final Pattern DELETE = Pattern.compile("删除|移除|去掉|删掉|隐藏|精简|delete|remove", Pattern.CASE_INSENSITIVE);
    private static final Pattern INTERACTION = Pattern.compile("跳转|导航|交互|链接|返回|关闭|打开|切换|navigation|interaction|link", Pattern.CASE_INSENSITIVE);
    private static final Pattern IMAGE = Pattern.compile("图片|背景|配图|插画|立绘|角色|宠物|头像|商品图|素材|图标|替换|更换|image|asset|replace|illustration|character|artwork|portrait", Pattern.CASE_INSENSITIVE);

    private AiAdjustmentContract() { }

    /** Restore omitted attributes only when another unique, unchanged ID proves
     * that the original node still exists. Never guess from text or position. */
    static String restorePageIdentifiers(String original, String modified) {
        requireCompletePage(modified);
        Document before = Jsoup.parse(original);
        Document after = Jsoup.parse(modified);
        return restoreIdentifiers(before, after) ? after.outerHtml() : modified;
    }

    static String restoreElementIdentifiers(String original, String modified) {
        // Keep invalid output intact for validateElement; parsing must not turn
        // a full document or multiple roots into an accepted replacement.
        if (modified == null || modified.matches("(?is).*<\\s*/?\\s*(?:html|head|body)\\b.*")) return modified;
        Document before = Jsoup.parseBodyFragment(original);
        Document after = Jsoup.parseBodyFragment(modified);
        if (after.body().childrenSize() != 1 || !after.body().ownText().isBlank()
                || !after.head().children().isEmpty() || after.body().child(0).is("script,style,link,meta")) return modified;
        return restoreIdentifiers(before, after) ? after.body().html() : modified;
    }

    static boolean validatePage(String original, String modified, String instruction) {
        requireCompletePage(modified);
        Document before = Jsoup.parse(original);
        Document after = Jsoup.parse(modified);
        validateStructure(before, after, instruction);
        return !before.outerHtml().equals(after.outerHtml());
    }

    private static void requireCompletePage(String modified) {
        if (modified == null || !modified.matches("(?is)^\\s*(?:<!doctype[^>]*>\\s*)?<html\\b.*</html>\\s*$")
                || !modified.matches("(?is).*<body\\b.*</body>.*")) {
            throw new IllegalStateException("AI 返回的页面 HTML 不完整，未修改画板，请重试");
        }
    }

    static boolean validateElement(String original, String modified, String instruction) {
        if (modified == null || modified.matches("(?is).*<\\s*/?\\s*(?:html|head|body)\\b.*")) {
            throw new IllegalStateException("AI 返回了整页结构，不能替换单个元素");
        }
        Document before = Jsoup.parseBodyFragment(original);
        Document after = Jsoup.parseBodyFragment(modified == null ? "" : modified);
        if (after.body().childrenSize() != 1 || !after.body().ownText().isBlank()
                || !after.head().children().isEmpty() || after.body().child(0).is("script,style,link,meta")) {
            throw new IllegalStateException("AI 返回的元素必须是单个完整节点，未修改画板");
        }
        Element root = after.body().child(0);
        Element oldRoot = before.body().children().first();
        if (oldRoot == null) throw new IllegalArgumentException("原始元素无效");
        for (String id : IDS) {
            if (oldRoot.hasAttr(id) && !oldRoot.attr(id).equals(root.attr(id))) {
                throw new IllegalStateException("AI 修改了目标元素的稳定标识，未修改画板");
            }
        }
        validateStructure(before, after, instruction);
        return !before.body().html().equals(after.body().html());
    }

    private static void validateStructure(Document before, Document after, String instruction) {
        instruction = instruction == null ? "" : instruction;
        for (String attribute : IDS) {
            Map<String, Element> oldIds = index(before, attribute);
            Map<String, Element> newIds = index(after, attribute);
            Map<String, Integer> beforeCounts = counts(before, attribute);
            Map<String, Integer> afterCounts = counts(after, attribute);
            for (var entry : afterCounts.entrySet()) {
                if (entry.getValue() > Math.max(1, beforeCounts.getOrDefault(entry.getKey(), 0))) {
                    throw new IllegalStateException("AI 引入了重复元素标识，未修改画板");
                }
            }
            for (var entry : oldIds.entrySet()) {
                // Existing duplicated SVG IDs are not made worse by unrelated adjustments.
                if (beforeCounts.get(entry.getKey()) != 1) continue;
                Element replacement = newIds.get(entry.getKey());
                if (replacement == null) {
                    if (!DELETE.matcher(instruction).find()) {
                        throw new IllegalStateException("AI 返回结果缺少「" + describe(entry.getValue())
                                + "」的元素标识（" + attribute + "），已保留原画板，请重试");
                    }
                    continue;
                }
                Element old = entry.getValue();
                if (!INTERACTION.matcher(instruction).find()) {
                    for (String name : INTERACTIONS) {
                        if (old.hasAttr(name) && !Objects.equals(old.attr(name), replacement.attr(name))) {
                            throw new IllegalStateException("AI 改动了无关的交互属性，未修改画板");
                        }
                    }
                }
                if (!IMAGE.matcher(instruction).find() && old.hasAttr("src") && !old.attr("src").equals(replacement.attr("src"))) {
                    throw new IllegalStateException("AI 改动了无关的图片地址，未修改画板");
                }
            }
        }
        // A local style edit must not introduce new scripts or external dependencies.
        List<String> oldDependencies = dependencies(before);
        List<String> newDependencies = dependencies(after);
        if (!oldDependencies.containsAll(newDependencies)
                || (!INTERACTION.matcher(instruction).find()
                && !before.select("script").outerHtml().equals(after.select("script").outerHtml()))) {
            throw new IllegalStateException("AI 引入了无关脚本或外部依赖，未修改画板");
        }
        if (!IMAGE.matcher(instruction).find()) {
            List<String> oldImages = imageSources(before);
            List<String> newImages = imageSources(after);
            List<String> remaining = new ArrayList<>(oldImages);
            for (String src : newImages) {
                if (!remaining.remove(src)) throw new IllegalStateException("AI 改动了无关图片素材，未修改画板");
            }
            if (!remaining.isEmpty() && !DELETE.matcher(instruction).find()) {
                throw new IllegalStateException("AI 丢失了原图片素材，未修改画板");
            }
        }
    }

    private static boolean restoreIdentifiers(Document before, Document after) {
        Map<String, Map<String, Element>> oldIds = new HashMap<>();
        Map<String, Map<String, Element>> newIds = new HashMap<>();
        for (String attribute : IDS) {
            oldIds.put(attribute, uniqueIndex(before, attribute));
            newIds.put(attribute, uniqueIndex(after, attribute));
        }
        boolean restored = false;
        String selector = IDS.stream().map(attribute -> "[" + attribute + "]").collect(Collectors.joining(","));
        for (Element old : before.select(selector)) {
            Element candidate = null;
            for (String attribute : IDS) {
                String value = old.attr(attribute);
                if (oldIds.get(attribute).get(value) != old) continue;
                Element match = newIds.get(attribute).get(value);
                if (match == null) continue;
                if (candidate != null && candidate != match) {
                    throw new IllegalStateException("AI 混用了「" + describe(old) + "」的元素标识，已保留原画板，请重试");
                }
                candidate = match;
            }
            if (candidate == null) continue;
            for (String attribute : IDS) {
                Element owner = oldIds.get(attribute).get(candidate.attr(attribute));
                if (owner != null && owner != old) {
                    throw new IllegalStateException("AI 混用了「" + describe(old) + "」的元素标识，已保留原画板，请重试");
                }
            }
            for (String attribute : IDS) {
                String value = old.attr(attribute);
                if (!value.isBlank() && oldIds.get(attribute).get(value) == old && candidate.attr(attribute).isBlank()) {
                    candidate.attr(attribute, value);
                    // Keep this index current so a later node cannot claim the same ID.
                    newIds.get(attribute).put(value, candidate);
                    restored = true;
                }
            }
        }
        return restored;
    }

    private static Map<String, Element> uniqueIndex(Document document, String attribute) {
        Map<String, Element> result = index(document, attribute);
        Map<String, Integer> counts = counts(document, attribute);
        result.keySet().removeIf(value -> counts.get(value) != 1);
        return result;
    }

    private static String describe(Element element) {
        String label = element.attr("aria-label");
        if (label.isBlank()) label = element.attr("alt");
        if (label.isBlank()) label = element.ownText();
        if (label.isBlank() && element.is("svg") && element.parent() != null && !element.parent().ownText().isBlank()) {
            label = element.parent().ownText() + "图标";
        }
        if (label.isBlank()) label = element.attr("data-wf-id");
        if (label.isBlank()) label = element.tagName() + (element.id().isBlank() ? "" : "#" + element.id());
        label = label.replaceAll("\\s+", " ").trim();
        return label.substring(0, Math.min(label.length(), 48));
    }

    private static Map<String, Element> index(Document document, String attribute) {
        Map<String, Element> result = new HashMap<>();
        for (Element element : document.select("[" + attribute + "]")) {
            String value = element.attr(attribute);
            if (!value.isBlank()) result.putIfAbsent(value, element);
        }
        return result;
    }

    private static Map<String, Integer> counts(Document document, String attribute) {
        Map<String, Integer> result = new HashMap<>();
        for (Element element : document.select("[" + attribute + "]")) {
            if (!element.attr(attribute).isBlank()) result.merge(element.attr(attribute), 1, Integer::sum);
        }
        return result;
    }

    private static List<String> dependencies(Document document) {
        List<String> result = new ArrayList<>(document.select("script[src]").eachAttr("src"));
        result.addAll(document.select("link[rel=stylesheet][href]").eachAttr("href"));
        return result;
    }

    private static List<String> imageSources(Document document) {
        List<String> result = new ArrayList<>(document.select("img[src]").eachAttr("src"));
        for (Element image : document.select("svg image")) {
            if (image.hasAttr("href")) result.add(image.attr("href"));
            if (image.hasAttr("xlink:href")) result.add(image.attr("xlink:href"));
        }
        return result;
    }
}
