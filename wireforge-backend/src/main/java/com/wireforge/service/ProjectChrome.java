package com.wireforge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wireforge.entity.Page;
import org.jsoup.Jsoup;
import org.jsoup.nodes.DataNode;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Freezes project chrome while leaving page content and local interaction owners intact. */
public final class ProjectChrome {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String META = "wf-project-chrome";
    private static final String STATUS = "[data-wf-chrome=status],[data-wf-statusbar],[data-wf-role=status-bar],.status,.status-bar,.statusbar,.wf-statusbar,.wf-status-bar";
    private static final String NAV = "[data-wf-chrome=bottom-navigation],[data-wf-navigation=bar],nav:has([data-nav-item]),.bottom-nav,.bottom-navigation,.wf-tabbar,.wf-canonical-nav";
    private static final Pattern RULE = Pattern.compile("([^{}]+)\\{([^{}]*)}");
    private static final Pattern COLOUR = Pattern.compile("(?i)#[0-9a-f]{3,8}\\b|rgba?\\([0-9.,%\\s]+\\)|\\b(?:white|black)\\b");
    private static final Pattern TIME = Pattern.compile("\\d{1,2}[:：]\\d{2}");
    private static final String CSS = resource("/prototype/chrome.css") + resource("/prototype/chrome-selection.css")
            + resource("/prototype/chrome-layout.css");
    private static final String LAYOUT_RUNTIME = resource("/prototype/chrome-layout.js");
    private static final List<String> SELECTED_CLASSES = List.of("active", "selected", "on", "current", "wf-nav-active", "is-active");
    private static final String STATUS_GRAPHIC = "<path d='M1 5q8-7 16 0M4 8q5-4 10 0M7 11q2-2 4 0' fill='none' stroke='currentColor' stroke-width='1.6'/><circle cx='9' cy='14' r='1' fill='currentColor'/><path d='M26 15v-3m4 3V9m4 6V6m4 9V2' stroke='currentColor' stroke-width='2.5'/><rect x='47' y='3' width='20' height='12' rx='2.5' fill='none' stroke='currentColor' stroke-width='1.5'/><path d='M69 7v4' stroke='currentColor' stroke-width='2'/><rect x='50' y='6' width='14' height='6' rx='1' fill='currentColor'/>";

    public record Theme(String statusBackground, String statusInk, String navBackground, String navInk,
                        String activeInk, String border, int statusHeight, int navHeight, int navFont, String time,
                        String activeBackground, int activeRadius) {}
    public record Icon(String label, String viewBox, String geometry) {}
    public record Snapshot(int version, Long projectId, Theme theme, Map<String, List<Icon>> families) {}

    public static boolean generated(String html) {
        if (html == null || (!html.contains("ai-static-v1") && !html.contains(META))) return false;
        return parse(html).selectFirst("meta[name=wf-renderer][content=ai-static-v1],meta[name=" + META + "]") != null;
    }

    public String prompt(Page page, List<Page> pages) {
        List<Page> references = references(page, pages);
        String contract = "\n【项目共享状态栏与底部主导航】\n状态栏根节点标记 data-wf-chrome=\"status\"；"
                + "底部主导航标记 data-wf-chrome=\"bottom-navigation\"，各项保留本页自己的元素ID、跳转及选中状态。"
                + "状态栏的背景跟随本页顶部背景或渐变，深浅文字适配本页；禁止复制参考页的灰色背景条。"
                + "页头返回条和本页筛选标签不是底部主导航。弹窗或详情页原稿没有这两块时不要补造。\n";
        if (references.isEmpty()) return contract;
        Snapshot snapshot = snapshot(page, references, null);
        return contract + "以下是已固定的项目公共组件规范，严格复用高度、底栏颜色、字号、图标与顺序；状态栏背景与文字深浅以本页为准；"
                + "不得按本页重新设计。选中项的前景色、底色和圆角成套复用，禁止只保留白字却丢掉底色。"
                + "本页激活项标记 aria-current=\"page\"，不得复制其他页面的数据库ID。\n" + encode(snapshot) + "\n";
    }

    public String apply(Page page, List<Page> pages, String html) {
        if (!generated(html)) return html;
        Document doc = parse(html);
        List<Element> statuses = statusRoots(doc);
        List<Element> bars = navRoots(doc);
        if (statuses.isEmpty() && bars.isEmpty()) return html;
        Snapshot snapshot = snapshot(page, references(page, pages), doc);
        Theme theme = snapshot.theme();
        statuses.forEach(status -> normalizeStatus(status, page, theme));
        for (Element bar : bars) {
            List<Element> items = items(bar);
            List<Icon> icons = snapshot.families().get(family(items));
            if (icons == null || icons.size() != items.size()) continue;
            Element active = items.stream().filter(item -> page.getName() != null && page.getName().equals(item.attr("data-nav")))
                    .findFirst().orElseGet(() -> items.stream().filter(ProjectChrome::selected).findFirst().orElse(null));
            bar.attr("data-wf-shared-chrome", "navigation").attr("data-wf-navigation", "bar");
            for (int i = 0; i < items.size(); i++) normalizeItem(items.get(i), icons.get(i), page, i, items.get(i) == active);
            anchorToViewport(bar);
        }
        doc.select("meta[name=" + META + "],style[data-wf-chrome-style],[data-wf-chrome-layout-runtime],style[data-wf-chrome-layout-style]").remove();
        doc.head().appendElement("meta").attr("name", META).attr("content", encode(snapshot));
        String variables = ":root{--wf-chrome-status-bg:" + theme.statusBackground() + ";--wf-chrome-status-ink:" + theme.statusInk()
                + ";--wf-chrome-nav-bg:" + theme.navBackground() + ";--wf-chrome-nav-ink:" + theme.navInk()
                + ";--wf-chrome-active:" + theme.activeInk() + ";--wf-chrome-border:" + theme.border()
                + ";--wf-chrome-active-bg:" + theme.activeBackground() + ";--wf-chrome-active-radius:" + theme.activeRadius() + "px"
                + ";--wf-chrome-status-height:" + theme.statusHeight() + "px;--wf-chrome-nav-height:" + theme.navHeight()
                + "px;--wf-chrome-nav-font:" + theme.navFont() + "px;--wf-chrome-viewport-height:"
                + (page.getCanvasHeight() == null ? 812 : page.getCanvasHeight()) + "px;}\n";
        doc.head().appendElement("style").attr("data-wf-chrome-style", "true").appendChild(new DataNode(variables + CSS));
        if (!bars.isEmpty()) doc.body().appendElement("script").attr("data-wf-chrome-layout-runtime", "true")
                .appendChild(new DataNode(LAYOUT_RUNTIME));
        return doc.outerHtml();
    }

    private static Snapshot snapshot(Page page, List<Page> references, Document current) {
        Theme theme = null;
        Map<String, List<Icon>> families = new LinkedHashMap<>();
        List<Document> documents = references.stream().map(p -> parse(p.getHtmlContent())).toList();
        for (Document doc : documents) {
            Element meta = doc.selectFirst("meta[name=" + META + "]");
            if (meta == null) continue;
            try {
                Snapshot saved = JSON.readValue(meta.attr("content"), Snapshot.class);
                if (saved.version() < 1 || saved.version() > 3 || !Objects.equals(saved.projectId(), page.getProjectId())) continue;
                if (theme == null) theme = saved.theme();
                saved.families().forEach(families::putIfAbsent);
            } catch (Exception ignored) { /* Older or edited metadata is not a project contract. */ }
        }
        if (theme == null) {
            Document anchor = documents.stream().filter(d -> !navRoots(d).isEmpty() && !statusRoots(d).isEmpty())
                    .findFirst().orElse(documents.isEmpty() ? current : documents.get(0));
            theme = theme(anchor);
        }
        List<Document> all = new ArrayList<>(documents);
        if (current != null) all.add(current);
        for (Document doc : all) for (Element bar : navRoots(doc)) {
            List<Element> items = items(bar);
            families.putIfAbsent(family(items), items.stream().map(ProjectChrome::icon).toList());
        }
        // v1 froze the status background; v2 kept the selected foreground but discarded its background.
        Selection selection = theme.activeBackground() == null ? all.stream().map(ProjectChrome::selection)
                .filter(Objects::nonNull).findFirst().orElse(new Selection("transparent", 0))
                : new Selection(theme.activeBackground(), theme.activeRadius());
        String activeInk = theme.activeInk();
        if (!visiblePaint(selection.background()) && indistinguishable(activeInk, theme.navBackground())) {
            activeInk = darkBackground(theme.navBackground()) ? "#a8c7fa" : "#365ac7";
        }
        theme = new Theme("transparent", "#20212b", theme.navBackground(), theme.navInk(), activeInk,
                theme.border(), theme.statusHeight(), theme.navHeight(), theme.navFont(), theme.time(), selection.background(), selection.radius());
        return new Snapshot(3, page.getProjectId(), theme, families);
    }

    private static List<Page> references(Page page, List<Page> pages) {
        return pages.stream().filter(p -> Objects.equals(page.getProjectId(), p.getProjectId())
                        && !Integer.valueOf(1).equals(p.getIsDeleted()) && generated(p.getHtmlContent()))
                .sorted(Comparator.comparing((Page p) -> p.getSortOrder() == null ? 0 : p.getSortOrder())
                        .thenComparing(p -> p.getId() == null ? 0L : p.getId())).toList();
    }

    private static Theme theme(Document doc) {
        Element status = doc == null ? null : statusRoots(doc).stream().findFirst().orElse(null);
        Element nav = doc == null ? null : navRoots(doc).stream().findFirst().orElse(null);
        List<Element> items = nav == null ? List.of() : items(nav);
        Element active = items.stream().filter(ProjectChrome::selected).findFirst().orElse(null);
        Element normal = items.stream().filter(i -> !selected(i)).findFirst().orElse(nav);
        String bg = "#f7f7fa";
        for (Element owner = status; owner != null; owner = owner.parent()) {
            String found = colour(owner, "background", null);
            if (found != null) { bg = found; break; }
        }
        Selection selection = selection(doc);
        return new Theme(bg, colour(status, "color", "#20212b"), colour(nav, "background", "#f7f7fa"),
                colour(normal, "color", "#8b8b96"), colour(active, "color", "#ff8c27"),
                colour(nav, "border-top", "#dedee5"), pixels(status, "height", 35, 24, 48),
                pixels(nav, "height", 68, 48, 88), pixels(normal, "font-size", 11, 10, 13),
                status == null ? "09:41" : Optional.ofNullable(timeNode(status)).map(Element::text).orElse("09:41"),
                selection == null ? "transparent" : selection.background(), selection == null ? 0 : selection.radius());
    }

    private record Selection(String background, int radius) {}

    /** Read the original active rule even when an older normalization removed its class. */
    private static Selection selection(Document doc) {
        if (doc == null) return null;
        for (Element nav : navRoots(doc)) {
            Element active = items(nav).stream().filter(ProjectChrome::selected).findFirst().orElse(null);
            if (active == null) continue;
            String original = active.attr("class");
            try {
                SELECTED_CLASSES.forEach(active::addClass);
                String background = resolveVariables(active, declaration(active, "background")).replace("!important", "").trim();
                boolean decoration = !visiblePaint(background);
                if (decoration) background = resolveVariables(active, declaration(active, "background", true)).replace("!important", "").trim();
                if (!visiblePaint(background)) background = "transparent";
                String radius = declaration(active, "border-radius", decoration);
                var number = Pattern.compile("^(\\d+)(?:\\.\\d+)?px").matcher(radius);
                return new Selection(background, number.find() ? Math.min(32, Integer.parseInt(number.group(1))) : 0);
            } finally {
                if (original.isEmpty()) active.removeAttr("class"); else active.attr("class", original);
            }
        }
        return null;
    }

    private static boolean indistinguishable(String foreground, String background) {
        double a = luminance(foreground), b = luminance(background);
        return a >= 0 && b >= 0 && (Math.max(a, b) + .05) / (Math.min(a, b) + .05) < 1.5;
    }

    private static double luminance(String paint) {
        if (paint == null) return -1;
        var match = COLOUR.matcher(paint);
        if (!match.find()) return -1;
        String value = match.group().toLowerCase(Locale.ROOT);
        if (value.equals("white")) return 1;
        if (value.equals("black")) return 0;
        try {
            int[] rgb = new int[3];
            if (value.startsWith("#")) {
                String hex = value.substring(1);
                if (hex.length() == 3 || hex.length() == 4) hex = "" + hex.charAt(0) + hex.charAt(0) + hex.charAt(1) + hex.charAt(1) + hex.charAt(2) + hex.charAt(2);
                for (int i = 0; i < 3; i++) rgb[i] = Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
            } else {
                String[] parts = value.substring(value.indexOf('(') + 1, value.indexOf(')')).split(",");
                for (int i = 0; i < 3; i++) rgb[i] = channel(parts[i]);
            }
            double result = 0;
            double[] weights = {.2126, .7152, .0722};
            for (int i = 0; i < 3; i++) { double c = rgb[i] / 255.; result += weights[i] * (c <= .04045 ? c / 12.92 : Math.pow((c + .055) / 1.055, 2.4)); }
            return result;
        } catch (RuntimeException ignored) { return -1; }
    }

    private static void normalizeStatus(Element status, Page page, Theme theme) {
        String localBackground = statusBackground(status);
        String inkBackground = "transparent".equals(localBackground) ? pageBackground(status) : localBackground;
        String ink = darkBackground(inkBackground) ? "#ffffff" : "#302c32";
        String style = status.attr("style").replaceAll("(?i)(?:^|;)\\s*--wf-local-status-(?:bg|ink)\\s*:[^;]*", "");
        status.attr("style", style.replaceAll(";+\\s*$", "") + ";--wf-local-status-bg:" + localBackground + ";--wf-local-status-ink:" + ink + ";");
        Element time = timeNode(status);
        if (time == null) time = new Element("span").attr("data-wf-uid", "wf-p" + page.getId() + "-chrome-time");
        Element graphic = status.selectFirst("svg");
        if (graphic == null) graphic = new Element("svg").attr("data-wf-uid", "wf-p" + page.getId() + "-chrome-signals");
        List<Element> aliases = aliases(status, Set.of(time, graphic));
        time.text(theme.time()).attr("data-wf-chrome-part", "time");
        graphic.empty().attr("viewBox", "0 0 72 18").attr("width", "72").attr("height", "18")
                .attr("data-wf-chrome-part", "signals").html(STATUS_GRAPHIC);
        status.empty().attr("data-wf-chrome", "status").attr("data-wf-shared-chrome", "status");
        status.appendChild(time).appendChild(graphic);
        aliases.forEach(status::appendChild);
    }

    private static String statusBackground(Element status) {
        Element header = status.nextElementSibling();
        if (header != null && header.is("header,.header,.page-header,.topbar,.top-bar,.top,.head,[data-wf-role=navigation-bar]")) {
            String paint = resolveVariables(header, declaration(header, "background")).replace("!important", "").trim();
            if (visiblePaint(paint)) return paint;
        }
        return "transparent";
    }

    private static String pageBackground(Element status) {
        for (Element owner = status.parent(); owner != null && !owner.is("html,body"); owner = owner.parent()) {
            String paint = resolveVariables(owner, declaration(owner, "background"));
            if (visiblePaint(paint)) return paint;
        }
        // The body's dark surround represents the editor/device, not the page surface.
        return "#f7f7fa";
    }

    private static boolean visiblePaint(String paint) {
        String value = paint.replace("!important", "").trim();
        return !value.isBlank() && !Set.of("none", "transparent", "inherit", "initial", "unset").contains(value)
                && !value.matches("rgba\\([^)]*,\\s*0(?:\\.0+)?\\s*\\)");
    }

    private static boolean darkBackground(String paint) {
        var match = COLOUR.matcher(paint);
        if (!match.find()) return false;
        String colour = match.group().toLowerCase(Locale.ROOT);
        if (colour.equals("black")) return true;
        if (colour.equals("white")) return false;
        try {
            int r, g, b;
            if (colour.startsWith("#")) {
                String hex = colour.substring(1);
                if (hex.length() == 3 || hex.length() == 4) hex = "" + hex.charAt(0) + hex.charAt(0) + hex.charAt(1) + hex.charAt(1) + hex.charAt(2) + hex.charAt(2);
                r = Integer.parseInt(hex.substring(0, 2), 16); g = Integer.parseInt(hex.substring(2, 4), 16); b = Integer.parseInt(hex.substring(4, 6), 16);
            } else {
                String[] rgb = colour.substring(colour.indexOf('(') + 1, colour.indexOf(')')).split(",");
                r = channel(rgb[0]); g = channel(rgb[1]); b = channel(rgb[2]);
            }
            return r * .2126 + g * .7152 + b * .0722 < 145;
        } catch (RuntimeException ignored) { return false; }
    }

    private static int channel(String value) {
        return (int) Math.round(Double.parseDouble(value.trim().replace("%", "")) * (value.contains("%") ? 2.55 : 1));
    }

    private static void normalizeItem(Element item, Icon icon, Page page, int index, boolean active) {
        Element svg = item.selectFirst("svg");
        if (svg == null) svg = new Element("svg").attr("data-wf-uid", "wf-p" + page.getId() + "-chrome-icon-" + index);
        Element label = item.getAllElements().stream().filter(n -> n != item && n.children().isEmpty()
                && key(n.text()).equals(key(icon.label()))).findFirst().orElse(null);
        if (label == null) label = new Element("span").attr("data-wf-uid", "wf-p" + page.getId() + "-chrome-label-" + index);
        List<Element> aliases = aliases(item, Set.of(svg, label));
        svg.empty().attr("viewBox", icon.viewBox()).attr("width", "22").attr("height", "22")
                .attr("fill", "none").attr("stroke", "currentColor").attr("stroke-width", "1.8")
                .attr("data-wf-chrome-part", "icon").html(icon.geometry());
        label.text(icon.label()).attr("data-wf-chrome-part", "label");
        item.empty().attr("data-wf-chrome-part", "item").attr("data-nav-item", "1")
                .attr("data-wf-edit-role", "navigation-item").attr("aria-current", active ? "page" : "false");
        SELECTED_CLASSES.forEach(item::removeClass);
        item.appendChild(svg).appendChild(label);
        aliases.forEach(item::appendChild);
    }

    private static void anchorToViewport(Element bar) {
        Element host = bar.parent() == null ? null : bar.parent().closest("[data-wf-role=page],.page,.phone,.screen");
        if (host == null) return;
        String position = declaration(bar, "position").replace("!important", "").trim();
        boolean inFlow = position.isBlank() || Set.of("static", "relative", "sticky").contains(position);
        boolean hasSpacer = host.selectFirst("[data-wf-chrome-spacer=navigation]") != null;
        if (inFlow && !hasSpacer) bar.before(new Element("div").attr("data-wf-chrome-spacer", "navigation").attr("aria-hidden", "true"));
        host.attr("data-wf-chrome-viewport", "true");
        if (bar.parent() != host) host.appendChild(bar);
    }

    /** Preserve local IDs even when decorative wrappers are flattened. Never copy IDs from the reference page. */
    private static List<Element> aliases(Element root, Set<Element> kept) {
        List<Element> result = new ArrayList<>();
        for (Element old : root.select("[data-wf-element-id],[data-wf-uid]")) {
            if (old == root || kept.contains(old)) continue;
            Element alias = new Element("span").attr("data-wf-chrome-alias", "true").attr("hidden", "");
            old.attributes().forEach(attr -> { if (attr.getKey().startsWith("data-wf-") || attr.getKey().startsWith("data-group-")) alias.attr(attr.getKey(), attr.getValue()); });
            result.add(alias);
        }
        return result;
    }

    private static Icon icon(Element item) {
        Element original = item.selectFirst("svg");
        Element svg = original == null ? Jsoup.parseBodyFragment("<svg viewBox='0 0 24 24'><circle cx='12' cy='12' r='8'/></svg>").selectFirst("svg") : original.clone();
        if (original != null) for (int depth = 0; depth < 4; depth++) {
            List<Element> uses = new ArrayList<>(svg.select("use"));
            if (uses.isEmpty()) break;
            for (Element use : uses) {
                String href = use.hasAttr("href") ? use.attr("href") : use.attr("xlink:href");
                Element symbol = href.startsWith("#") ? original.ownerDocument().getElementById(href.substring(1)) : null;
                if (symbol == null) { use.remove(); continue; }
                Element group = new Element("g");
                if (use.hasAttr("transform")) group.attr("transform", use.attr("transform"));
                for (var child : symbol.childNodes()) group.appendChild(child.clone());
                use.replaceWith(group);
            }
        }
        svg.select("script,foreignObject,image,use,style").remove();
        for (Element node : svg.getAllElements()) for (var attr : node.attributes().asList()) {
            String name = attr.getKey();
            if (name.startsWith("data-") || name.startsWith("on") || Set.of("id", "class", "style", "href", "xlink:href").contains(name)) node.removeAttr(name);
            else if (Set.of("fill", "stroke").contains(name) && !"none".equals(attr.getValue())) node.attr(name, "currentColor");
        }
        String viewBox = svg.attr("viewBox");
        if (!viewBox.matches("[0-9.]+[ ,]+[0-9.]+[ ,]+[0-9.]+[ ,]+[0-9.]+")) viewBox = "0 0 24 24";
        return new Icon(item.text().trim(), viewBox, svg.html());
    }

    private static List<Element> statusRoots(Document doc) {
        return doc.select(STATUS).stream().filter(n -> n.hasAttr("data-wf-chrome") || TIME.matcher(n.text()).find())
                .filter(n -> n.parents().stream().noneMatch(p -> p.is(STATUS))).toList();
    }

    private static List<Element> navRoots(Document doc) {
        return doc.select(NAV).stream().filter(n -> items(n).size() >= 2)
                .filter(n -> n.parents().stream().noneMatch(p -> p.is(NAV))).toList();
    }

    private static List<Element> items(Element nav) {
        List<Element> marked = nav.select("[data-nav-item]").stream().filter(n -> n != nav).toList();
        return marked.isEmpty() ? nav.children().stream().filter(n -> n.is("button,a,[data-nav]"))
                .filter(n -> !n.text().isBlank()).toList() : marked;
    }

    private static String family(List<Element> items) { return items.stream().map(n -> key(n.text())).collect(Collectors.joining("|")); }
    private static String key(String value) { return value.replaceAll("[\\s\\p{P}]+", "").toLowerCase(Locale.ROOT); }
    private static boolean selected(Element n) { return "page".equals(n.attr("aria-current")) || "true".equals(n.attr("aria-selected"))
            || "true".equals(n.attr("aria-pressed")) || n.classNames().stream().anyMatch(SELECTED_CLASSES::contains); }
    private static Element timeNode(Element root) { return root.getAllElements().stream().filter(n -> n != root && n.children().isEmpty() && TIME.matcher(n.text().trim()).matches()).findFirst().orElse(null); }

    private static String declaration(Element node, String property) {
        return declaration(node, property, false);
    }

    private static String declaration(Element node, String property, boolean decoration) {
        if (node == null) return "";
        Pattern declaration = Pattern.compile("(?i)(?:^|;)\\s*" + Pattern.quote(property) + "(?:-color)?\\s*:\\s*([^;]+)");
        String value = "";
        for (Element style : node.ownerDocument().select("style:not([data-wf-chrome-style]):not([data-wf-chrome-selection-style])")) {
            var rules = RULE.matcher(style.data());
            while (rules.find()) for (String selector : rules.group(1).split(",")) try {
                String target = selector.trim();
                boolean pseudo = target.matches("(?i).*::?(?:before|after)$");
                if (pseudo != decoration) continue;
                if (pseudo) target = target.replaceFirst("(?i)::?(?:before|after)$", "");
                if (node.is(target)) { var found = declaration.matcher(rules.group(2)); while (found.find()) value = found.group(1); }
            } catch (Exception ignored) { /* Ignore selectors unsupported by Jsoup. */ }
        }
        if (!decoration) { var inline = declaration.matcher(node.attr("style")); while (inline.find()) value = inline.group(1); }
        return value;
    }

    private static String colour(Element node, String property, String fallback) {
        if (node == null) return fallback;
        String value = resolveVariables(node, declaration(node, property));
        var match = COLOUR.matcher(value);
        return match.find() ? match.group() : fallback;
    }

    private static String resolveVariables(Element node, String value) {
        for (int i = 0; i < 5 && value.contains("var("); i++) {
            var variable = Pattern.compile("var\\((--[\\w-]+)(?:,([^()]+))?\\)").matcher(value);
            if (!variable.find()) break;
            String resolved = "";
            Pattern definition = Pattern.compile(Pattern.quote(variable.group(1)) + "\\s*:\\s*([^;}]+)");
            for (Element style : node.ownerDocument().select("style")) { var match = definition.matcher(style.data()); while (match.find()) resolved = match.group(1); }
            if (resolved.isBlank()) resolved = variable.group(2) == null ? "" : variable.group(2);
            value = value.substring(0, variable.start()) + resolved + value.substring(variable.end());
        }
        return value;
    }

    private static int pixels(Element node, String property, int fallback, int min, int max) {
        var match = Pattern.compile("^\\s*(\\d+)(?:\\.\\d+)?px").matcher(declaration(node, property));
        return match.find() ? Math.max(min, Math.min(max, Integer.parseInt(match.group(1)))) : fallback;
    }
    private static Document parse(String html) { Document doc = Jsoup.parse(html); doc.outputSettings().prettyPrint(false); return doc; }
    private static String encode(Snapshot snapshot) { try { return JSON.writeValueAsString(snapshot); } catch (Exception e) { throw new IllegalStateException(e); } }
    private static String resource(String name) {
        try (var in = ProjectChrome.class.getResourceAsStream(name)) {
            if (in == null) throw new IllegalStateException("缺少公共组件样式");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }
}
