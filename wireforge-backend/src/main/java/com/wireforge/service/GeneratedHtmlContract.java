package com.wireforge.service;

import com.wireforge.entity.Element;
import com.wireforge.entity.Page;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.DataNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Validates model output before attaching the application's state-only runtime. */
public final class GeneratedHtmlContract {
    private static final String EDITABLE = "button,a,input,textarea,select,h1,h2,h3,p,img,svg,[data-wf-id],[data-wf-role],[data-wf-element-id],[data-wf-artwork-key]";
    private static final Pattern CSS_URL = Pattern.compile("url\\(\\s*['\"]?([^)'\"\\s]+)['\"]?\\s*\\)", Pattern.CASE_INSENSITIVE);
    private static final String RUNTIME = readRuntime();

    public static String forModel(String html) {
        Document doc = Jsoup.parse(html == null ? "" : html);
        doc.outputSettings().prettyPrint(false);
        doc.select("script,[data-wf-inject],[data-wf-prototype-style],[data-wf-controls-style],[data-wf-chrome-selection-style],[data-wf-chrome-layout-style],meta[name=wf-renderer],meta[name=wf-project-chrome],#wf-prototype-feedback").remove();
        return doc.outerHtml();
    }

    public String prepare(String raw, Page page, List<Element> sources, List<Page> pages,
                          Set<String> allowedAssets, Set<Long> requiredBindings) {
        String html = raw == null ? "" : raw.trim().replaceFirst("(?is)^```(?:html)?\\s*", "").replaceFirst("\\s*```$", "");
        if (!html.toLowerCase(Locale.ROOT).contains("</html>") || !html.toLowerCase(Locale.ROOT).contains("<body")
                || !html.toLowerCase(Locale.ROOT).contains("</body>")) {
            throw new IllegalArgumentException("模型未返回完整 HTML 文档");
        }
        Document doc = Jsoup.parse(html);
        doc.outputSettings().prettyPrint(false);
        if (!doc.select("script,iframe,object,embed,base,link,meta[http-equiv]").isEmpty()) {
            throw new IllegalArgumentException("生成页面必须使用静态 HTML，不能依赖模型脚本或外部文档");
        }
        if (doc.body().text().isBlank() && doc.select("img,svg,input").isEmpty()) {
            throw new IllegalArgumentException("生成页面没有静态可见内容");
        }
        Set<Long> sourceIds = sources.stream().map(Element::getId).collect(Collectors.toSet());
        Set<String> pageNames = pages.stream().map(Page::getName).collect(Collectors.toSet());
        Set<Long> bound = new HashSet<>();
        Set<String> uids = new HashSet<>();
        for (org.jsoup.nodes.Element node : doc.getAllElements()) {
            for (var attr : node.attributes().asList()) {
                if (attr.getKey().toLowerCase(Locale.ROOT).startsWith("on")) {
                    throw new IllegalArgumentException("生成页面禁止内联事件脚本");
                }
            }
            if (node.hasAttr("style")) validateCss(node.attr("style"), allowedAssets);
            if (node.normalName().equals("style")) validateCss(node.data(), allowedAssets);
            if (node.hasAttr("data-nav") && !pageNames.contains(node.attr("data-nav"))) node.removeAttr("data-nav");
            if (node.hasAttr("href") && !node.attr("href").startsWith("#")) node.removeAttr("href");
            if (node.hasAttr("xlink:href") && !node.attr("xlink:href").startsWith("#")) node.removeAttr("xlink:href");
            if (node.hasAttr("srcset")) node.removeAttr("srcset");
            if (node.hasAttr("src") && !node.normalName().equals("img")) node.removeAttr("src");
            if (node.normalName().equals("img") && !allowedAssets.contains(node.attr("src"))) {
                String label = node.attr("alt");
                node.tagName("div").removeAttr("src").attr("data-wf-asset-missing", "true")
                        .attr("data-wf-role", "image")
                        .attr("role", "img").attr("aria-label", label.isBlank() ? "素材待补充" : label)
                        .appendElement("span").text(label.isBlank() ? "素材待补充" : label);
            }
            if (node.normalName().equals("svg")) {
                if (!node.hasAttr("width")) node.attr("width", "20");
                if (!node.hasAttr("height")) node.attr("height", "20");
            }
        }
        int index = 0;
        for (org.jsoup.nodes.Element node : doc.select(EDITABLE)) {
            String uid = node.attr("data-wf-uid");
            if (node.hasAttr("data-wf-element-id")) {
                Long id = parseId(node.attr("data-wf-element-id"));
                if (id == null || !sourceIds.contains(id) || !bound.add(id)) {
                    throw new IllegalArgumentException("元素绑定 ID 不存在或重复: " + node.attr("data-wf-element-id"));
                }
                uid = "wf-p" + page.getId() + "-e" + id;
            }
            if (uid.isBlank()) uid = "wf-p" + page.getId() + "-" + node.attr("data-wf-id");
            if (!uid.matches("[A-Za-z0-9_-]+") || uid.endsWith("-")) uid = "wf-p" + page.getId() + "-node-" + (++index);
            String base = uid;
            while (!uids.add(uid)) uid = base + "-" + (++index);
            node.attr("data-wf-uid", uid);
            if (!node.hasAttr("data-wf-role")) node.attr("data-wf-role", role(node));
            if (node.normalName().equals("button")) node.attr("type", "button");
        }
        if (!bound.containsAll(requiredBindings)) {
            Set<Long> missing = new HashSet<>(requiredBindings); missing.removeAll(bound);
            throw new IllegalArgumentException("缺少可点击节点的数据库绑定: " + missing);
        }
        for (org.jsoup.nodes.Element node : doc.select("[data-modal]")) {
            org.jsoup.nodes.Element modal = doc.getElementById("wf-modal-" + node.attr("data-modal"));
            if (modal == null) throw new IllegalArgumentException("缺少本页弹窗: " + node.attr("data-modal"));
            modal.addClass("wf-modal").attr("role", "dialog").attr("aria-modal", "true");
        }
        doc.head().appendElement("meta").attr("name", "wf-renderer").attr("content", "ai-static-v1");
        doc.head().appendElement("style").attr("data-wf-prototype-style", "true").appendChild(new DataNode("""
                [hidden]{display:none!important}
                [data-wf-asset-missing]{display:flex;align-items:center;justify-content:center;min-height:80px;border-radius:14px;background:var(--primary-subtle,#eeeaf9);color:var(--text-sub,#64748b)}
                .wf-modal{position:fixed;inset:0;display:flex;align-items:center;justify-content:center;background:rgba(0,0,0,.4);z-index:1000}
                .wf-modal:not(.wf-show){opacity:0;visibility:hidden;pointer-events:none}
                .wf-modal.wf-show{display:flex!important;opacity:1;visibility:visible;pointer-events:auto}
                #wf-prototype-feedback{position:fixed;bottom:84px;left:50%;transform:translateX(-50%);max-width:90%;padding:10px 16px;border-radius:12px;background:#172033;color:white;font:13px/1.5 system-ui;z-index:100001}
                """));
        doc.body().appendElement("div").attr("id", "wf-prototype-feedback").attr("role", "status").attr("hidden", "");
        PrototypeControls.apply(doc);
        doc.body().appendElement("script").attr("data-wf-prototype-runtime", "true").appendChild(new DataNode(RUNTIME));
        return "<!DOCTYPE html>\n" + doc.outerHtml().replaceFirst("(?is)<!doctype[^>]*>\\s*", "");
    }

    private static void validateCss(String css, Set<String> allowed) {
        if (css.toLowerCase(Locale.ROOT).contains("@import")) throw new IllegalArgumentException("禁止外部 CSS 导入");
        var urls = CSS_URL.matcher(css);
        while (urls.find()) if (!urls.group(1).startsWith("#") && !allowed.contains(urls.group(1))) {
            throw new IllegalArgumentException("CSS 引用了素材清单以外的资源");
        }
    }

    private static Long parseId(String value) {
        try { return Long.valueOf(value); } catch (NumberFormatException e) { return null; }
    }

    private static String role(org.jsoup.nodes.Element node) {
        return switch (node.normalName()) {
            case "button", "a" -> "button";
            case "input", "textarea", "select" -> "input";
            case "svg" -> "icon";
            case "img" -> "image";
            default -> "text";
        };
    }

    private static String readRuntime() {
        try (var in = GeneratedHtmlContract.class.getResourceAsStream("/prototype/runtime.js")) {
            if (in == null) throw new IllegalStateException("缺少原型交互运行时");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) { throw new IllegalStateException(e); }
    }
}
