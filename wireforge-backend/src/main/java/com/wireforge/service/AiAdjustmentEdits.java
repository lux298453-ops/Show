package com.wireforge.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Apply small declarative edits to the source document, keeping all untouched subtrees. */
final class AiAdjustmentEdits {
    private static final Set<String> IDS = Set.of("id", "data-wf-id", "data-wf-element-id", "data-wf-uid", "data-wf-select-id");
    private static final Set<String> ATTRIBUTES = Set.of("src", "alt", "title", "placeholder", "value", "width", "height",
            "disabled", "checked", "selected", "hidden", "data-nav", "data-action", "data-modal-target");
    private static final Pattern PROPERTY = Pattern.compile("(?:--[a-zA-Z0-9_-]+|[a-z][a-z0-9-]{0,63})");
    private static final Pattern UNSAFE_VALUE = Pattern.compile("(?is).*?(?:expression\\s*\\(|javascript\\s*:|vbscript\\s*:|@import|<|>|[{}]).*");
    private static final Pattern DELETE = Pattern.compile("删除|移除|去掉|删掉|delete|remove", Pattern.CASE_INSENSITIVE);

    private AiAdjustmentEdits() { }

    static String prompt() {
        return """

                【优先使用局部修改指令，避免重写整页】
                移动、改样式、改文字、替换图片、删除元素时，优先返回 edits 数组，不要返回 modified_html/modified_snippet。
                服务端会在原始 HTML 上执行修改，无关节点、SVG、脚本与编辑标识不需要复制到输出中。
                每项只包含要改的一个目标，target_attribute 从 id/data-wf-id/data-wf-element-id/data-wf-uid/data-wf-select-id 中选择，target_id 是该属性的唯一原值，禁止用CSS选择器或编造标识。
                style 示例：{"op":"style","target_attribute":"data-wf-id","target_id":"new-outfit-tip","styles":{"top":"68%","left":"18px","transform":"none"}}。
                只指定实际要改的CSS属性。服务端会覆盖这些属性并保留其他样式；值为null表示移除该内联属性。不要用追加相同元素的方式移动。
                text 示例：{"op":"text","target_attribute":"id","target_id":"label","text":"新文字"}；必须定位单个文字节点所在的标签，不得把包含图标或子控件的容器改成纯文字。
                attributes 示例：{"op":"attributes","target_attribute":"id","target_id":"photo","attributes":{"src":"已有真实素材URL","alt":"说明"}}；不能修改稳定标识、class或事件处理器。
                delete 示例：{"op":"delete","target_attribute":"id","target_id":"unwanted"}；仅在用户明确要求删除该目标时使用。
                仅重新生成图片时可以返回 edits:[] 和 image_tasks。生成图片与移动文字可在同一响应组合，图片目标必须来自保留的原节点。
                选中元素模式下可以用 target_attribute:"root"、target_id:"" 指向原片段根节点；不能访问其他页面或片段外的节点。
                最多32项。确需新增结构或复杂重排时仍可返回完整 modified_html/modified_snippet，不能与 edits 同时返回；完整HTML模式继续严格保留编辑标识和无关图层。
                优先的整页结果示例：{"summary":"已移动提示文字","diff_list":["[修改] 提示文字位置"],"edits":[{"op":"style","target_attribute":"data-wf-id","target_id":"new-outfit-tip","styles":{"top":"68%"}}],"image_tasks":[]}。
                元素模式修改时保留 action:"REPLACE"，用 edits 替代 modified_snippet。删除选中根元素时返回 action:"DELETE"，不返回 edits；无需修改时返回空 edits 或 action:"NO_CHANGE"。
                """;
    }

    static String apply(String original, JsonNode edits, String instruction, boolean fragment) {
        if (edits == null || !edits.isArray() || edits.size() > 32) throw invalid("局部修改指令必须是最多32项的数组");
        if (edits.isEmpty()) return original;
        Document doc = fragment ? Jsoup.parseBodyFragment(original) : Jsoup.parse(original);
        doc.outputSettings().prettyPrint(false);
        for (JsonNode edit : edits) {
            Element target = target(doc, edit, fragment);
            if (target.is("script,style,head,link,meta,iframe,object,embed")) throw invalid("不能通过普通元素调整修改脚本或依赖");
            switch (text(edit, "op")) {
                case "style" -> setStyles(target, edit.path("styles"));
                case "text" -> setText(target, edit);
                case "attributes" -> setAttributes(target, edit.path("attributes"));
                case "delete" -> {
                    if (instruction == null || !DELETE.matcher(instruction).find() || target.is("html,body")
                            || (fragment && target.parent() == doc.body())) throw invalid("不能删除未要求删除的元素或文档根节点");
                    target.remove();
                }
                default -> throw invalid("不支持的局部修改操作");
            }
        }
        return fragment ? doc.body().html() : doc.outerHtml();
    }

    private static Element target(Document doc, JsonNode edit, boolean fragment) {
        String attribute = text(edit, "target_attribute"), id = text(edit, "target_id");
        if (fragment && attribute.equals("root") && id.isEmpty() && doc.body().childrenSize() == 1) return doc.body().child(0);
        if (!IDS.contains(attribute) || id.isBlank() || id.length() > 256) throw invalid("局部修改缺少有效目标标识");
        List<Element> matches = doc.getAllElements().stream().filter(node -> id.equals(node.attr(attribute))).toList();
        if (matches.size() != 1) throw invalid("局部修改目标不存在或标识重复");
        return matches.get(0);
    }

    private static void setStyles(Element target, JsonNode styles) {
        if (!styles.isObject() || styles.size() == 0 || styles.size() > 40) throw invalid("样式修改格式无效");
        Map<String, String> changes = new LinkedHashMap<>();
        styles.fields().forEachRemaining(entry -> {
            String name = entry.getKey();
            JsonNode node = entry.getValue();
            if (!PROPERTY.matcher(name).matches() || Set.of("behavior", "-moz-binding").contains(name)
                    || (!node.isTextual() && !node.isNumber() && !node.isNull())) throw invalid("样式属性无效");
            String value = node.isNull() ? null : node.asText().strip();
            if (value != null) {
                List<String> declarations = splitDeclarations(value);
                if (value.length() > 8000 || value.isEmpty() || UNSAFE_VALUE.matcher(value).matches()
                        || declarations.size() != 1 || !declarations.get(0).equals(value)) throw invalid("样式值包含无效声明");
            }
            changes.put(name, value);
        });
        List<String> result = new ArrayList<>();
        for (String declaration : splitDeclarations(target.attr("style"))) {
            int colon = declaration.indexOf(':');
            String property = colon < 0 ? "" : declaration.substring(0, colon).strip();
            if (!property.startsWith("--")) property = property.toLowerCase(Locale.ROOT);
            if (!changes.containsKey(property)) result.add(declaration);
        }
        changes.forEach((property, value) -> {
            if (value != null) result.add(property + ":" + value.replaceFirst("(?i)\\s*!\\s*important\\s*$", "") + "!important");
        });
        target.attr("style", String.join(";", result) + ";");
    }

    /** Semicolons in quoted strings/data URLs/functions are part of the value. */
    private static List<String> splitDeclarations(String css) {
        List<String> result = new ArrayList<>();
        int start = 0, depth = 0;
        char quote = 0;
        boolean escaped = false, comment = false;
        for (int i = 0; i < css.length(); i++) {
            char c = css.charAt(i), next = i + 1 < css.length() ? css.charAt(i + 1) : 0;
            if (comment) { if (c == '*' && next == '/') { comment = false; i++; } continue; }
            if (escaped) { escaped = false; continue; }
            if (c == '\\') { escaped = true; continue; }
            if (quote != 0) { if (c == quote) quote = 0; continue; }
            if (c == '/' && next == '*') { comment = true; i++; continue; }
            if (c == '\'' || c == '"') quote = c;
            else if (c == '(' || c == '[') depth++;
            else if (c == ')' || c == ']') depth--;
            else if (c == ';' && depth == 0) { if (!css.substring(start, i).isBlank()) result.add(css.substring(start, i)); start = i + 1; }
            if (depth < 0) throw invalid("样式括号不完整");
        }
        if (quote != 0 || depth != 0 || comment || escaped) throw invalid("样式声明不完整");
        if (!css.substring(start).isBlank()) result.add(css.substring(start));
        return result;
    }

    private static void setText(Element target, JsonNode edit) {
        JsonNode value = edit.get("text");
        if (value == null || !value.isTextual() || value.asText().length() > 8000) throw invalid("文字修改格式无效");
        List<TextNode> own = target.textNodes().stream().filter(node -> !node.isBlank()).toList();
        if (target.childrenSize() > 0 || own.size() > 1 || target.is("svg,path,g,defs")) throw invalid("请选择独立文字标签，不能覆盖内部图标或子控件");
        target.text(value.asText());
    }

    private static void setAttributes(Element target, JsonNode attributes) {
        if (!attributes.isObject() || attributes.isEmpty() || attributes.size() > 16) throw invalid("属性修改格式无效");
        attributes.fields().forEachRemaining(entry -> {
            String name = entry.getKey();
            JsonNode value = entry.getValue();
            if (!(ATTRIBUTES.contains(name) || name.matches("aria-[a-z-]+")) || (!value.isTextual() && !value.isBoolean() && !value.isNumber() && !value.isNull())) {
                throw invalid("不能修改稳定标识或未支持的属性");
            }
            if (value.isNull()) target.removeAttr(name);
            else {
                String content = value.asText();
                if (content.length() > 8000 || content.matches("(?is)\\s*(?:javascript|vbscript):.*")) throw invalid("属性值无效");
                if (Set.of("disabled", "checked", "selected", "hidden").contains(name) && value.isBoolean()) {
                    target.attr(name, value.asBoolean());
                } else target.attr(name, content);
            }
        });
    }

    private static String text(JsonNode node, String name) { return node.path(name).isTextual() ? node.path(name).asText() : ""; }
    private static IllegalStateException invalid(String message) { return new IllegalStateException(message + "，已保留原画板"); }
}
