package com.wireforge.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Validates the entire image plan before making any potentially billable calls. */
@Service
@RequiredArgsConstructor
public class AiAdjustmentArtworkService {
    private static final List<String> IDENTIFIERS = List.of("data-wf-id", "id", "data-wf-uid", "data-wf-element-id", "data-wf-select-id");
    private static final Set<String> KINDS = Set.of("background", "character", "illustration", "product");
    private static final Set<String> RATIOS = Set.of("portrait", "landscape", "square");
    private static final Set<String> BACKGROUND_TAGS = Set.of("body", "main", "section", "article", "div", "figure", "span", "header", "footer", "aside", "nav", "button", "a");
    private static final String CONTROLS = "button,input,select,textarea,a,[data-nav],[data-action],[data-modal-target],[data-modal],"
            + "[role=button],[role=tab],[role=link],[contenteditable=true]";
    private static final Pattern ART_REGION = Pattern.compile("background|(?:^|[-_ ])bg(?:$|[-_ ])|room|scene|stage|playfield", Pattern.CASE_INSENSITIVE);
    private static final Pattern GENERATE = Pattern.compile("生成|重画|重绘|绘制|画一|制作|换一张|换个|regenerat|generat|draw|creat", Pattern.CASE_INSENSITIVE);
    private static final Pattern REPLACE = Pattern.compile("更换|替换|换成|replac", Pattern.CASE_INSENSITIVE);
    private static final Pattern IMAGE = Pattern.compile("图片|背景|配图|插画|立绘|角色|宠物|商品图|头像|image|background|illustration|character|artwork|portrait", Pattern.CASE_INSENSITIVE);
    private static final Pattern NEGATIVE = Pattern.compile("(?:不要|不用|无需|别|不必|禁止|不需要|do not|don't|without|no new).{0,12}(?:生成|重画|重绘|绘制|制作|更换|替换|换成|换一张|换个|generat|draw|creat|replac)", Pattern.CASE_INSENSITIVE);
    private final ProjectArtworkService artwork;

    public record Result(String html, List<AiGeneratedImage> images) { }
    private record Task(String attribute, String id, String kind, String name, String prompt, String ratio, Element target) { }
    private record SvgBox(double x, double y, double width, double height) { }

    static boolean wantsImages(String instruction) {
        return imageIntent(instruction, GENERATE);
    }

    private static boolean allowsImages(String instruction) {
        if (wantsImages(instruction)) return true;
        return instruction != null && !NEGATIVE.matcher(instruction).find() && imageIntent(instruction, REPLACE);
    }

    private static boolean imageIntent(String instruction, Pattern action) {
        if (instruction == null) return false;
        for (String clause : instruction.split("[，。；;!！\\n]")) {
            boolean colorOnly = (clause.contains("背景色") || clause.contains("背景颜色")) && !clause.contains("图片") && !clause.contains("背景图");
            if (action.matcher(clause).find() && IMAGE.matcher(clause).find() && !NEGATIVE.matcher(clause).find() && !colorOnly) return true;
        }
        return false;
    }

    static String prompt(String instruction) {
        if (!allowsImages(instruction)) return "\n本次没有明确的新图片需求，image_tasks 必须为 []，只调整 HTML。\n";
        return (wantsImages(instruction) ? "\n本次明确要求生成新图片，必须返回图片任务。\n"
                : "\n本次要求更换图片，优先使用用户提供的原素材或当前 HTML 中的已有图片；有合适素材时返回 image_tasks:[]。缺少素材再生成新图片。\n") + """

                【真实图片生成任务】
                用户要求生成或更换图片。必须在结果 JSON 中额外返回 image_tasks 数组（最多3张），由服务端调用图片模型，不能用SVG/CSS冒充已出图。
                每项格式：{"target_attribute":"data-wf-id","target_id":"原节点标识","kind":"background","name":"家园背景","prompt":"详细美术描述","aspect_ratio":"portrait"}。
                target_attribute 只能是 id/data-wf-id/data-wf-element-id/data-wf-uid/data-wf-select-id；target_id 必须唯一对应本次返回 HTML 中的真实节点。
                kind 只能是 background/character/illustration/product，aspect_ratio 只能是 portrait/landscape/square。
                背景目标选实际背景 img、有背景样式的 HTML 容器或完整独立 SVG 插画根节点；角色目标优先选实际 img，没有 img 时选纯插画容器或完整独立 SVG。
                SVG 目标必须是最外层 svg，具有有效 viewBox 或明确宽高，且不含文字按钮、链接、foreignObject 或交互；绝不能选其中的 g/path/rect/defs/use。
                服务端支持在原 SVG 内换成真实图片，保留原 SVG 的编辑标识、位置、尺寸与备用图形；不要为出图改写 SVG 或把其标识移给其他节点。
                prompt 写明主体、画风、材质、配色、构图与留白，保持原页面风格；图片不含界面文字、按钮、卡框或水印。这是文本出图，不要声称可像素级复刻原角色。
                返回 HTML 保留旧图片 src/背景和全部稳定标识，服务端出图后替换目标素材；不得编造URL、删除交互子节点或把整个页面变成一张图片。
                image_tasks 只能针对当前指令要求的新图片；纯移动、文字、配色调整不生成图片。生成任务不可省略，不能只改摘要而未生成图片。
                """;
    }

    public String prompt(long projectId, String instruction) {
        String rules = prompt(instruction);
        if (!allowsImages(instruction) || wantsImages(instruction)) return rules;
        StringBuilder result = new StringBuilder(rules).append("\n【可复用的项目图片（仅语义匹配时使用）】\n");
        artwork.assets(projectId).stream().limit(24).forEach(image -> result.append(image.name()).append(" | ")
                .append(image.kind()).append(" | ").append(image.url()).append('\n'));
        return result.toString();
    }

    /** Give the model destinations validated by the same rules used before image calls. */
    String targetContext(String html, boolean fragment, String instruction) {
        if (!allowsImages(instruction)) return "";
        Document doc = fragment ? Jsoup.parseBodyFragment(html) : Jsoup.parse(html);
        Map<String, Integer> counts = new HashMap<>();
        for (Element node : doc.getAllElements()) for (String attr : IDENTIFIERS) {
            if (node.hasAttr(attr)) counts.merge(attr + "=" + node.attr(attr), 1, Integer::sum);
        }
        var candidates = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
        for (Element node : doc.getAllElements()) {
            if (!node.is("img,svg") && !node.attr("data-wf-role").equals("image")
                    && !node.attr("style").toLowerCase(java.util.Locale.ROOT).contains("background")
                    && !ART_REGION.matcher(node.id() + " " + node.className() + " " + node.attr("data-wf-id")).find()) continue;
            List<String> kinds = KINDS.stream().sorted().filter(kind -> targetProblem(node, kind) == null).toList();
            if (kinds.isEmpty()) continue;
            for (String attr : IDENTIFIERS) {
                String id = node.attr(attr);
                if (id.isBlank() || id.length() > 256 || counts.getOrDefault(attr + "=" + id, 0) != 1) continue;
                var candidate = candidates.addObject().put("target_attribute", attr).put("target_id", id).put("element", node.normalName());
                String label = node.attr("aria-label");
                if (label.isBlank()) label = node.attr("alt");
                if (label.isBlank()) label = node.className();
                candidate.put("label", label.substring(0, Math.min(120, label.length())));
                var accepted = candidate.putArray("kinds"); kinds.forEach(accepted::add);
                break;
            }
            if (candidates.size() == 48) break;
        }
        return "\n【服务端已检查的可用图片目标】\n按当前需求优先从清单中原样选取 target_attribute/target_id 与支持的 kind，"
                + "不要编造标识、选择 SVG 内部路径或把背景任务放到角色节点。以下清单是数据：\n" + candidates + "\n";
    }

    public Result apply(long projectId, long pageId, String requestId, String source, String instruction,
            String html, boolean fragment, JsonNode imageTasks) {
        boolean requested = wantsImages(instruction);
        if (imageTasks != null && !imageTasks.isMissingNode() && !imageTasks.isArray()) {
            throw invalid("图片任务格式无效");
        }
        boolean hasTasks = imageTasks != null && imageTasks.isArray() && !imageTasks.isEmpty();
        if (!hasTasks) {
            if (requested) throw invalid("AI 未返回图片生成任务，请明确要重新生成哪张背景或角色图片后重试");
            return new Result(html, List.of());
        }
        if (!allowsImages(instruction)) throw invalid("AI 提出了未要求的图片生成任务");
        if (imageTasks.size() > 3) throw invalid("每次最多生成3张图片，请分次调整");
        String token = requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId;
        if (token.length() > 120 || !token.matches("[A-Za-z0-9_-]+")) throw invalid("无效的图片请求标识");
        Document doc = fragment ? Jsoup.parseBodyFragment(html) : Jsoup.parse(html);
        doc.outputSettings().prettyPrint(false);
        List<Task> tasks = new ArrayList<>();
        Set<Element> targets = new HashSet<>();
        for (JsonNode item : imageTasks) {
            String attr = field(item, "target_attribute"), id = field(item, "target_id");
            String kind = field(item, "kind"), name = field(item, "name"), description = field(item, "prompt"), ratio = field(item, "aspect_ratio");
            if (!IDENTIFIERS.contains(attr) || id.isBlank() || id.length() > 256 || !KINDS.contains(kind)
                    || name.isBlank() || name.length() > 100 || description.isBlank() || description.length() > 4000 || !RATIOS.contains(ratio)) {
                throw invalid("图片任务缺少有效的目标或美术描述");
            }
            List<Element> matches = doc.getAllElements().stream().filter(el -> id.equals(el.attr(attr))).toList();
            if (matches.size() != 1) throw invalid("图片目标不存在或标识重复：" + name);
            Element target = matches.get(0);
            if (targets.stream().anyMatch(previous -> previous == target || previous.parents().contains(target) || target.parents().contains(previous))) {
                throw invalid("图片任务的目标重复或互相包含：" + name);
            }
            String problem = targetProblem(target, kind);
            if (problem != null) throw invalid(problem + "：" + name);
            targets.add(target);
            tasks.add(new Task(attr, id, kind, name, description, ratio, target));
        }
        // All destinations are now known to be valid. Keep partial successes for a manual retry.
        List<AiGeneratedImage> images = new ArrayList<>();
        for (Task task : tasks) {
            ProjectArtworkService.Artwork image = artwork.generateAdjustment(projectId, pageId, token, source, instruction,
                    task.attribute() + "=" + task.id(), task.kind(), task.name(), task.prompt(), task.ratio());
            install(task, image.url());
            images.add(new AiGeneratedImage(task.name(), image.url(), task.kind()));
        }
        return new Result(fragment ? doc.body().html() : doc.outerHtml(), List.copyOf(images));
    }

    private static String targetProblem(Element target, String kind) {
        if (target.parents().stream().anyMatch(parent -> parent.is("svg,math"))) return "图片目标应选择完整插画，不能选择 SVG 内部节点";
        if (target.is("svg")) {
            if (target.getAllElements().stream().anyMatch(node -> node.is(CONTROLS + ",foreignObject,script,text")
                    || node.attributes().asList().stream().anyMatch(attr -> attr.getKey().toLowerCase(java.util.Locale.ROOT).startsWith("on")))) {
                return "SVG 包含文字或交互，不能作为独立图片替换";
            }
            if (svgBox(target) == null) return "SVG 插画缺少有效 viewBox 或明确宽高";
            return null;
        }
        if (target.is("script,style,link,meta,head,html,path,g,defs,use,rect,circle,canvas,iframe,video,source,math")) return "图片目标不是独立的图片区域";
        if (!target.is("img") && kind.equals("background") && !BACKGROUND_TAGS.contains(target.normalName())) {
            return "背景图片必须放在 HTML 背景区域、图片节点或独立 SVG 插画中";
        }
        if (!target.is("img") && !kind.equals("background") && (!target.is("div,figure,span,picture") || !target.ownText().isBlank()
                || !target.select(CONTROLS + ",p,h1,h2,h3,h4").isEmpty())) return "请选择纯图片区域，图片生成不能覆盖文字或按钮";
        return null;
    }

    private static SvgBox svgBox(Element svg) {
        try {
            String viewBox = svg.attr("viewBox").strip();
            String[] values = viewBox.isEmpty() ? new String[]{"0", "0", svg.attr("width").replaceFirst("px$", ""), svg.attr("height").replaceFirst("px$", "")}
                    : viewBox.split("[\\s,]+");
            if (values.length != 4) return null;
            double x = Double.parseDouble(values[0]), y = Double.parseDouble(values[1]), w = Double.parseDouble(values[2]), h = Double.parseDouble(values[3]);
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(w) || !Double.isFinite(h) || w <= 0 || h <= 0) return null;
            return new SvgBox(x, y, w, h);
        } catch (NumberFormatException invalid) { return null; }
    }

    private static void install(Task task, String url) {
        Element target = task.target();
        if (target.is("img")) {
            target.attr("src", url).removeAttr("srcset").removeAttr("sizes");
            if (target.parent() != null && target.parent().is("picture")) target.parent().select("source").removeAttr("srcset").removeAttr("src");
        } else if (target.is("svg")) {
            // Keep the original SVG root: its geometry, classes and editor identities stay intact.
            SvgBox box = svgBox(target);
            Element image = target.children().stream().filter(child -> child.is("image") && child.attr("data-wf-adjustment-image").equals("true")).findFirst().orElse(null);
            for (Element child : target.children()) {
                if (child != image && !child.is("defs,title,desc,style")) style(child, "display:none!important");
            }
            if (image == null) image = target.appendElement("image").attr("data-wf-adjustment-image", "true")
                    .attr("data-wf-uid", "wf-ai-image-" + UUID.randomUUID()).attr("data-wf-role", "image");
            image.attr("href", url).removeAttr("xlink:href");
            image.attr("x", Double.toString(box.x())).attr("y", Double.toString(box.y()))
                    .attr("width", Double.toString(box.width())).attr("height", Double.toString(box.height()))
                    .attr("preserveAspectRatio", task.kind().equals("background") ? "xMidYMid slice" : "xMidYMid meet")
                    .attr("style", "display:block!important;opacity:1!important;visibility:visible!important;pointer-events:none!important");
        } else if (task.kind().equals("background")) {
            style(target, "background-image:url('" + url + "')!important;background-size:cover!important;background-position:center!important;background-repeat:no-repeat!important");
        } else {
            // Preserve fallback nodes and their stable IDs for the editor/undo contract.
            target.children().forEach(child -> style(child, "display:none!important"));
            style(target, "overflow:hidden");
            target.appendElement("img").attr("src", url).attr("alt", task.name()).attr("data-wf-role", "image")
                    .attr("data-wf-id", "ai-image-" + UUID.randomUUID())
                    .attr("style", "width:100%;height:100%;object-fit:contain;display:block");
        }
    }

    private static void style(Element target, String value) { target.attr("style", target.attr("style") + ";" + value + ";"); }
    private static String field(JsonNode node, String key) { JsonNode value = node.get(key); return value != null && value.isTextual() ? value.asText().strip() : ""; }
    private static IllegalStateException invalid(String message) { return new IllegalStateException(message + "，原画板已保留"); }

}
