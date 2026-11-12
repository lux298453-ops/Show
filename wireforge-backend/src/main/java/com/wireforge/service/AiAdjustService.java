package com.wireforge.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wireforge.ai.AiClient;
import com.wireforge.entity.Page;
import com.wireforge.mapper.PageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 全屏演示 AI 原型调整服务：
 * 严格按照选中的单个画板 pageId 读取内容，调用大模型生成增量修改方案与新 HTML。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiAdjustService {

    private final PageMapper pageMapper;
    private final AiClient aiClient;
    private final ObjectMapper objectMapper;
    private final AiAdjustmentArtworkService adjustmentArtwork;

    public AiAdjustResponse adjustPage(Long projectId, Long pageId, String instruction, String contextHtml) {
        return adjustPage(projectId, pageId, instruction, contextHtml, null);
    }

    public AiAdjustResponse adjustPage(Long projectId, Long pageId, String instruction, String contextHtml, String conversationContext) {
        return adjustPage(projectId, pageId, instruction, contextHtml, conversationContext, null);
    }

    public AiAdjustResponse adjustPage(Long projectId, Long pageId, String instruction, String contextHtml, String conversationContext, String requestId) {
        // 1. 严格按画板 ID 检索，并校验项目归属，杜绝跨画板与跨项目误改
        Page page = pageMapper.selectById(pageId);
        if (page == null || !page.getProjectId().equals(projectId) || (page.getIsDeleted() != null && page.getIsDeleted() == 1)) {
            throw new IllegalArgumentException("指定画板不存在或已被删除: " + pageId);
        }

        // 2. 获取当前待修改的 HTML（优先使用前端传入的实时上下文，默认取数据库当前内容）
        String currentHtml = (contextHtml != null && !contextHtml.isBlank()) ? contextHtml : page.getHtmlContent();
        if (currentHtml == null || currentHtml.isBlank()) {
            throw new IllegalArgumentException("画板「" + page.getName() + "」暂无整页原型 HTML，无法进行 AI 调整");
        }

        // 3. 构造精准克制、杜绝误伤、彻底清理废弃节点的系统提示词
        String systemPrompt = """
                你是移动端 UI/UX 原型重构与代码微调专家。你的核心任务是根据用户的指令对提供的页面 HTML 进行精准、极简、无副作用的局部修改。
                必须严格执行以下规则：
                1. 【零副作用与防误伤原则（最高优先级）】：
                   - 严格禁止修改任何与用户指令无关的 DOM 节点！
                   - 除用户指令明确要求变动的元素外，页面上其余所有节点的属性（如 data-nav、data-wf-element-id）、样式类名、坐标位置、文本、图标必须 100% 逐字符保持原样。
                   - 严禁擅自“优化”周围元素的间距、排版、文字或色彩。
                2. 【编辑标识与修改范围】：
                   - id、data-wf-id、data-wf-element-id、data-wf-uid、data-wf-select-id 是编辑与连线的稳定标识。
                     所有保留元素（包括本次调整目标）的这些属性必须保留原值，不能省略、重命名、互换或分配给别的节点。
                   - 移动、改样式、替换图标或背景时，优先修改原节点的样式、src 或内部视觉内容；保持目标节点及已有子元素标识，不要删除后另建。
                   - 仅用户明确要求删除的目标才可移除整个 DOM 节点，不能因为出现“替换”“重新生成”就删除整个区域。
                     隐藏使用 hidden 或样式控制，保留节点；替换素材不等于删除控件。
                   - 严禁保留旧元素又叠加一个新元素。调整位置不应复制或重建页面、图层和交互节点。
                   - 只能引用已有真实素材地址；新位图通过 image_tasks 请求服务端生成，不编造图片 URL。
                3. 【精准定位（Anchor Targeting）】：
                   - 画板 HTML 内部元素通常带有 data-wf-element-id、语义类名（如 .wf-btn, .wf-entry, .wf-card）或明确的文本内容。
                   - 请先在当前 HTML 中精准识别出用户所指的具体元素节点，仅对该目标节点执行单点修改或删除，不可波及周围相邻元素。
                4. 【保持完整运行结构】：
                   - 优先返回局部 edits；仅使用完整 HTML 模式时，以 <!DOCTYPE html> 开头，以 </html> 结尾，保持全部结构。
                   - 严格输出标准 JSON 格式，绝不要使用 Markdown 代码块包裹（不要输出 ```json ... ```），也不要输出任何多余的解释文字。
                
                JSON 结构必须严格如下：
                {
                  "summary": "简要概括改动，例如：已彻底移除右侧月卡入口，并更新按钮配色",
                  "diff_list": [
                    "[删除] 彻底移除右侧「月卡」入口节点（避免残留）",
                    "[修改] 将主按钮背景色调整为暖橙色渐变",
                    "[新增] 在主按钮上方新增限时倒计时卡片"
                  ],
                  "edits": [],
                  "image_tasks": []
                }
                """;
        systemPrompt += AiAdjustmentEdits.prompt() + adjustmentArtwork.prompt(projectId, instruction);

        String userPrompt = String.format("""
                【目标画板信息】
                画板名称：%s
                画布尺寸：%d x %d

                【用户调整指令】
                %s

                【特别执行要求】
                1. 移动或替换视觉内容时，保留原节点及稳定标识；只有明确要求删除目标时才移除节点，严禁新旧重复叠加。
                2. 与指令无关的其余所有节点必须 100%% 保持原样，不得擅自改动。

                【当前页面完整 HTML 代码】
                %s
                """,
                page.getName(),
                page.getCanvasWidth() != null ? page.getCanvasWidth() : 375,
                page.getCanvasHeight() != null ? page.getCanvasHeight() : 812,
                instruction,
                currentHtml
        );

        log.info("开始对画板 [{} (id={})] 执行 AI 原型微调，指令: {}", page.getName(), pageId, instruction);

        userPrompt += "\n【最近对话（需求参考，当前指令优先）】\n" + bounded(conversationContext, 6000);
        userPrompt += adjustmentArtwork.targetContext(currentHtml, false, instruction);
        String rawResponse = aiClient.generateHtmlText(systemPrompt, userPrompt);
        if (rawResponse == null || rawResponse.isBlank()) {
            throw new IllegalStateException("AI 服务未能返回有效响应，请稍后重试");
        }

        return parseAiResponse(page, rawResponse, currentHtml, instruction, requestId);
    }

    private AiAdjustResponse parseAiResponse(Page page, String raw, String original, String instruction, String requestId) {
        String clean = stripFence(raw);
        String summary;
        String modified;
        List<String> differences = new ArrayList<>();
        JsonNode imageTasks = null;
        if (clean.matches("(?is)^(?:<!doctype[^>]*>\\s*)?<html\\b.*")) {
            modified = clean;
            summary = "已生成调整方案";
        } else {
            JsonNode root = parseObject(clean);
            modified = text(root, "modified_html");
            if (root.has("edits")) {
                if (!modified.isBlank()) throw new IllegalStateException("AI 同时返回局部修改和整页替换，已保留原画板，请重试");
                modified = AiAdjustmentEdits.apply(original, root.get("edits"), instruction, false);
            }
            if (modified.isBlank()) throw new IllegalStateException("AI 未返回有效修改方案，未修改画板，请重试");
            summary = text(root, "summary");
            if (root.path("diff_list").isArray()) root.path("diff_list").forEach(item -> differences.add(item.asText()));
            imageTasks = root.path("image_tasks");
        }
        modified = AiAdjustmentContract.restorePageIdentifiers(original, modified);
        AiAdjustmentContract.validatePage(original, modified, instruction);
        var result = adjustmentArtwork.apply(page.getProjectId(), page.getId(), requestId, original, instruction, modified, false, imageTasks);
        modified = result.html();
        boolean changed = AiAdjustmentContract.validatePage(original, modified, instruction);
        for (AiGeneratedImage image : result.images()) differences.add("[图片] 已生成「" + image.name() + "」");
        return AiAdjustResponse.builder().pageId(page.getId()).pageName(page.getName())
                .summary(changed ? (summary.isBlank() ? "已生成调整方案" : summary) : "当前页面无需修改")
                .diffList(changed ? differences : List.of()).modifiedHtml(changed ? modified : original).changed(changed)
                .generatedImages(result.images()).build();
    }

    /** Single-element changes return only the selected subtree. Layout context is read-only. */
    public AiElementAdjustResponse adjustElement(Long projectId, Long pageId, AiElementAdjustRequest req) {
        Page page = pageMapper.selectById(pageId);
        if (page == null || !page.getProjectId().equals(projectId) || (page.getIsDeleted() != null && page.getIsDeleted() == 1)) {
            throw new IllegalArgumentException("指定画板不存在或已被删除: " + pageId);
        }

        String elementHtml = req.getElementHtml();
        if (elementHtml == null || elementHtml.isBlank()) {
            throw new IllegalArgumentException("未提供选中元素的 HTML 代码片段");
        }

        String systemPrompt = """
                你是高精度 UI 组件微调引擎（类似 Figma AI 与 Cursor 节点微调模式）。
                你的任务是根据用户的指令对提供的单个 HTML 元素片段进行精准、极致的局部调整。
                严格执行以下规则：
                1. 【判定操作类型】：
                   - 若无需修改：action 为 "NO_CHANGE"，省略 edits 和 modified_snippet，summary 说明无需修改。
                   - 若用户指令明确要求“删除”、“去掉”、“移除”该元素：action 设定为 "DELETE"，modified_snippet 留空字符串 ""。
                   - 若用户要求修改样式、颜色、文案、尺寸或图片：action 设定为 "REPLACE"，优先用 edits 描述要改的属性。
                   - 只有复杂结构变化才返回 modified_snippet，输出修改后的该元素完整外层标签（outerHTML），不与 edits 同时返回。
                2. 【保持核心规范】：
                   - 若为 REPLACE，必须保留原元素和保留子元素的 id、data-wf-id、data-wf-element-id、data-wf-uid、data-wf-select-id 原值。
                   - 保留原元素必要的定位样式（如 position、left、top、width、height 等），只修改用户要求变化的布局或样式。
                   - 严格针对用户要求变动属性，不要随意删改未被要求的内部结构。
                3. 【严格输出标准 JSON】：
                   - 严禁 Markdown 代码块包裹（不要输出 ```json ... ```），也不要输出任何多余解释。
                JSON 结构必须严格如下：
                {
                  "action": "REPLACE",
                  "summary": "已将按钮背景改为暖橙色渐变并加粗字号",
                  "edits": [],
                  "image_tasks": []
                }
                """;
        systemPrompt += AiAdjustmentEdits.prompt() + adjustmentArtwork.prompt(projectId, req.getInstruction());

        String userPrompt = String.format("""
                【所属画板】：%s
                【目标元素】：%s (ID: %s, 标签: %s)
                【微调指令】：
                %s

                【目标元素当前原始 HTML】：
                %s
                """,
                page.getName(),
                req.getElementLabel() != null ? req.getElementLabel() : "未命名元素",
                req.getElementId() != null ? req.getElementId() : "未知",
                req.getElementTag() != null ? req.getElementTag() : "div",
                req.getInstruction(),
                elementHtml
        );

        log.info("开始对画板 [{} (id={})] 选中元素 [{} (id={})] 执行单点微调，指令: {}",
                page.getName(), pageId, req.getElementLabel(), req.getElementId(), req.getInstruction());

        userPrompt += "\n【最近对话（只读参考）】\n" + bounded(req.getConversationContext(), 6000)
                + "\n【父布局与样式（只读参考，只返回目标元素）】\n" + bounded(req.getLayoutContext(), 14000);
        userPrompt += adjustmentArtwork.targetContext(elementHtml, true, req.getInstruction());
        String raw = aiClient.generateHtmlText(systemPrompt, userPrompt);
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("AI 服务未能返回有效响应，请重试");
        }

        return parseElementResponse(projectId, pageId, req, raw, elementHtml);
    }

    private AiElementAdjustResponse parseElementResponse(Long projectId, Long pageId, AiElementAdjustRequest req, String raw, String original) {
        String elementId = req.getElementId(), instruction = req.getInstruction();
        JsonNode root = parseObject(stripFence(raw));
        String action = text(root, "action").toUpperCase(java.util.Locale.ROOT);
        String snippet = text(root, "modified_snippet");
        String summary = text(root, "summary");
        if (root.has("edits")) {
            if (!snippet.isBlank()) throw new IllegalStateException("AI 同时返回局部修改和片段替换，已保留原画板，请重试");
            if (action.isBlank()) action = "REPLACE";
            if (!"REPLACE".equals(action) && (!root.path("edits").isArray() || !root.path("edits").isEmpty())) {
                throw new IllegalStateException("AI 返回互相冲突的元素操作，已保留原画板，请重试");
            }
            if ("REPLACE".equals(action)) snippet = AiAdjustmentEdits.apply(original, root.get("edits"), instruction, true);
        }
        if (!List.of("REPLACE", "DELETE", "NO_CHANGE").contains(action)) {
            throw new IllegalStateException("AI 返回了无效的元素操作，未修改画板");
        }
        boolean changed = false;
        List<AiGeneratedImage> images = List.of();
        if (!"REPLACE".equals(action) && (AiAdjustmentArtworkService.wantsImages(instruction) || root.path("image_tasks").size() > 0)) {
            throw new IllegalStateException("图片调整必须返回保留原节点的替换方案，原画板已保留");
        }
        if ("DELETE".equals(action)) {
            if (instruction == null || !instruction.matches("(?is).*(删除|移除|去掉|删掉|delete|remove).*")) {
                throw new IllegalStateException("AI 尝试删除未要求删除的元素，未修改画板");
            }
            changed = true;
            snippet = "";
        } else if ("REPLACE".equals(action)) {
            if (snippet.isBlank()) throw new IllegalStateException("AI 未返回 modified_snippet，未修改画板");
            snippet = AiAdjustmentContract.restoreElementIdentifiers(original, snippet);
            AiAdjustmentContract.validateElement(original, snippet, instruction);
            var result = adjustmentArtwork.apply(projectId, pageId, req.getRequestId(), original, instruction, snippet, true, root.path("image_tasks"));
            snippet = result.html();
            images = result.images();
            changed = AiAdjustmentContract.validateElement(original, snippet, instruction);
        }
        return AiElementAdjustResponse.builder().elementId(elementId).action(changed ? action : "NO_CHANGE")
                .summary(changed ? (summary.isBlank() ? "已生成元素调整方案" : summary) : "当前元素无需修改")
                .modifiedSnippet(changed ? snippet : original).changed(changed).generatedImages(images).build();
    }

    private JsonNode parseObject(String raw) {
        try {
            JsonNode root = objectMapper.readTree(raw);
            if (root != null && root.isObject()) return root;
        } catch (com.fasterxml.jackson.core.JsonProcessingException ignored) {
            // Never install an unparsed model response as executable element HTML.
        }
        throw new IllegalStateException("AI 返回的数据格式无效，未修改画板，请重试");
    }

    private static String text(JsonNode root, String field) {
        JsonNode value = root.get(field);
        return value != null && value.isTextual() ? value.asText() : "";
    }

    private static String stripFence(String raw) {
        return raw.trim().replaceFirst("^```(?:json|html)?\\s*", "").replaceFirst("\\s*```$", "").trim();
    }

    private static String bounded(String value, int limit) {
        return value == null ? "" : value.substring(0, Math.min(value.length(), limit));
    }
}
