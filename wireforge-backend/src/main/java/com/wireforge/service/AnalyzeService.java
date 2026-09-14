package com.wireforge.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wireforge.ai.AiClient;
import com.wireforge.ai.HtmlRenderer;
import com.wireforge.ai.WireframePrompt;
import com.wireforge.entity.Annotation;
import com.wireforge.entity.Element;
import com.wireforge.entity.Interaction;
import com.wireforge.entity.Page;
import com.wireforge.entity.Project;
import com.wireforge.mapper.AnnotationMapper;
import com.wireforge.mapper.ElementMapper;
import com.wireforge.mapper.InteractionMapper;
import com.wireforge.mapper.PageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 识别服务：设计稿图片 → 结构化线框 JSON → 落库（元素/交互/标注）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyzeService {

    private final AiClient aiClient;
    private final AssetService assetService;
    private final PageMapper pageMapper;
    private final ElementMapper elementMapper;
    private final InteractionMapper interactionMapper;
    private final AnnotationMapper annotationMapper;
    private final ObjectMapper objectMapper;
    private final HtmlRenderer htmlRenderer;
    private final TemplateHtmlRenderer templateHtmlRenderer;
    private final InteractionAutowireService interactionAutowireService;
    private final com.wireforge.mapper.ProjectMapper projectMapper;

    /** 是否开启"渲染→截图→对比原稿→回修"闭环（默认开；Chromium 不可用时自动跳过） */
    @org.springframework.beans.factory.annotation.Value("${wireforge.ai.repair:true}")
    private boolean repairEnabled;

    /** 回修最大轮数（默认 1；模型能力弱可提到 2，但每轮都是一次最贵的视觉调用） */
    @org.springframework.beans.factory.annotation.Value("${wireforge.ai.repair-rounds:1}")
    private int repairRounds;

    @org.springframework.beans.factory.annotation.Value("${wireforge.mock:false}")
    private boolean mock;

    /**
     * 分析单张设计稿页面。返回识别到的元素数量。
     */
    @Transactional
    public int analyzePage(Page page, List<String> allPageNames) {
        // 注意：先完成耗时的 AI 调用，再清理/写库，避免事务长时间持有行锁（并发分析会锁等待超时）
        JsonNode root;
        int imgW = 0;
        int imgH = 0;
        String mime = null;
        byte[] imageBytes = null;
        java.nio.file.Path imagePath = null;
        if (mock) {
            root = mockResult(page);
            log.warn("[mock 模式] 页面 [{}] 使用内置示例数据", page.getName());
        } else {
            imagePath = Path.of(page.getBackgroundImage());
            if (!Files.exists(imagePath)) {
                throw new IllegalStateException("设计稿图片不存在: " + page.getBackgroundImage());
            }

            String filename = imagePath.getFileName().toString().toLowerCase();
            if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) {
                mime = "image/jpeg";
            } else if (filename.endsWith(".webp")) {
                mime = "image/webp";
            } else {
                mime = "image/png";
            }

            try {
                imageBytes = Files.readAllBytes(imagePath);
            } catch (IOException e) {
                throw new IllegalStateException("读取设计稿失败: " + e.getMessage(), e);
            }

            try {
                var img = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(imageBytes));
                if (img != null) {
                    imgW = img.getWidth();
                    imgH = img.getHeight();
                }
            } catch (IOException e) {
                log.warn("读取设计稿尺寸失败，坐标将不做缩放: {}", e.getMessage());
            }

            String userPrompt = WireframePrompt.buildUserPrompt(allPageNames, imgW, imgH);
            String raw = aiClient.generateWithImage(
                    WireframePrompt.buildSystemPrompt(),
                    userPrompt, mime, imageBytes);
            root = parseJson(raw);
        }

        clearPageData(page.getId());

        JsonNode pageNameNode = root.path("page_name");
        String aiName = (pageNameNode != null && !pageNameNode.isMissingNode()) ? pageNameNode.asText("") : "";
        String resolvedName = PageNameResolver.resolve(page.getBackgroundImage(), root.path("elements"), aiName);
        page.setName(resolvedName);

        // 画布按设计稿宽高比设置（宽固定 375），元素坐标从原图像素等比缩放到画布
        double scaleX = 1;
        double scaleY = 1;
        if (imgW > 0 && imgH > 0) {
            int canvasW = 375;
            int canvasH = (int) Math.round(imgH * 375.0 / imgW);
            page.setCanvasWidth(canvasW);
            page.setCanvasHeight(canvasH);
            scaleX = canvasW / (double) imgW;
            scaleY = canvasH / (double) imgH;
        }

        page.setAnalyzed(1);
        pageMapper.updateById(page);

        int count = saveElements(page, root.path("elements"), scaleX, scaleY);
        log.info("页面 [{}] 分析完成: {} 个元素", page.getName(), count);

        // 确定性整页渲染（零 AI）：颜色采样 + 坐标直出，替代原"AI 直出 HTML + 截图回修"链路
        if (!mock) {
            try {
                String html = renderTemplateHtml(page);
                page.setHtmlContent(html);
                pageMapper.updateById(page);
                log.info("页面 [{}] 模板化整页 HTML 渲染完成（{} 字符）", page.getName(), html.length());
            } catch (Exception e) {
                log.warn("页面 [{}] 模板化渲染失败（保留组件模式）: {}", page.getName(), e.getMessage());
            }
        }
        return count;
    }

    /**
     * 用确定性模板渲染器生成某页的整页 HTML：
     * 读取整个项目的页面包（供弹窗浮层注入），采样设计稿真实颜色后坐标直出。不调用任何模型。
     */
    public String renderTemplateHtml(Page page) {
        try {
            interactionAutowireService.autowireProjectInteractions(page.getProjectId());
        } catch (Exception e) {
            log.warn("项目 {} 交互自动布线异常: {}", page.getProjectId(), e.getMessage());
        }
        List<Page> pages = pageMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<Page>lambdaQuery()
                        .eq(Page::getProjectId, page.getProjectId()));
        Map<Long, String> nameById = new HashMap<>();
        Map<Long, TemplateHtmlRenderer.PageBundle> bundles = new HashMap<>();
        for (Page p : pages) {
            nameById.put(p.getId(), p.getName());
            List<Element> els = elementMapper.selectList(
                    com.baomidou.mybatisplus.core.toolkit.Wrappers.<Element>lambdaQuery()
                            .eq(Element::getPageId, p.getId())
                            .orderByAsc(Element::getPositionY).orderByAsc(Element::getPositionX));
            Map<Long, List<Interaction>> m = new HashMap<>();
            if (!els.isEmpty()) {
                for (Interaction it : interactionMapper.selectList(
                        com.baomidou.mybatisplus.core.toolkit.Wrappers.<Interaction>lambdaQuery()
                                .in(Interaction::getElementId,
                                        els.stream().map(Element::getId).toList()))) {
                    m.computeIfAbsent(it.getElementId(), k -> new ArrayList<>()).add(it);
                }
            }
            bundles.put(p.getId(), new TemplateHtmlRenderer.PageBundle(p, els, m));
        }
        com.fasterxml.jackson.databind.JsonNode appMap = null;
        Project proj = projectMapper.selectById(page.getProjectId());
        if (proj != null && proj.getAppMap() != null && !proj.getAppMap().isBlank()) {
            try {
                appMap = objectMapper.readTree(proj.getAppMap());
            } catch (Exception e) {
                log.warn("项目 {} app_map 解析失败: {}", page.getProjectId(), e.getMessage());
            }
        }
        return templateHtmlRenderer.render(page, bundles, nameById, page.getName(), appMap);
    }

    /**
     * 查找与该页面有关联（互有跳转关系）且已有整页 HTML 的"风格参考页"。
     * 关联页面通常是同一场景的不同状态（如扭蛋机页 → 抽奖遮罩页），
     * 以其 HTML 为基准生成可保证视觉统一。找不到返回 null。
     */
    private Page findStyleReferencePage(Page page) {
        List<Element> els = elementMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<Element>lambdaQuery()
                        .eq(Element::getPageId, page.getId()));
        java.util.Set<Long> related = new java.util.LinkedHashSet<>();
        if (!els.isEmpty()) {
            List<Long> elIds = els.stream().map(Element::getId).toList();
            // 本页跳出去的目标页
            interactionMapper.selectList(
                            com.baomidou.mybatisplus.core.toolkit.Wrappers.<Interaction>lambdaQuery()
                                    .in(Interaction::getElementId, elIds)
                                    .isNotNull(Interaction::getTargetPageId))
                    .forEach(i -> related.add(i.getTargetPageId()));
            // 跳进本页的来源页
            var inRels = interactionMapper.selectList(
                    com.baomidou.mybatisplus.core.toolkit.Wrappers.<Interaction>lambdaQuery()
                            .eq(Interaction::getTargetPageId, page.getId()));
            List<Long> srcElIds = inRels.stream().map(Interaction::getElementId)
                    .filter(java.util.Objects::nonNull).toList();
            if (!srcElIds.isEmpty()) {
                elementMapper.selectBatchIds(srcElIds)
                        .forEach(e -> { if (e.getPageId() != null) related.add(e.getPageId()); });
            }
        }
        related.remove(page.getId());
        // 打分选择参考页：与本项目跳转关系越紧密的页面，视觉风格越应该保持一致。
        // 正向（本页→该页）计 3 分；反向（该页→本页）计 2 分。避免"取最长HTML"误选无关页面。
        java.util.Map<Long, Integer> scores = new java.util.LinkedHashMap<>();
        for (Long pid : related) {
            if (pid != null) scores.put(pid, 0);
        }
        if (!els.isEmpty()) {
            List<Long> elIds = els.stream().map(Element::getId).toList();
            interactionMapper.selectList(
                            com.baomidou.mybatisplus.core.toolkit.Wrappers.<Interaction>lambdaQuery()
                                    .in(Interaction::getElementId, elIds)
                                    .isNotNull(Interaction::getTargetPageId))
                    .forEach(i -> {
                        if (i.getTargetPageId() != null && scores.containsKey(i.getTargetPageId())) {
                            scores.merge(i.getTargetPageId(), 3, Integer::sum);
                        }
                    });
            var inRels = interactionMapper.selectList(
                    com.baomidou.mybatisplus.core.toolkit.Wrappers.<Interaction>lambdaQuery()
                            .eq(Interaction::getTargetPageId, page.getId()));
            List<Long> srcElIds = inRels.stream().map(Interaction::getElementId)
                    .filter(java.util.Objects::nonNull).toList();
            if (!srcElIds.isEmpty()) {
                elementMapper.selectBatchIds(srcElIds).forEach(e -> {
                    if (e.getPageId() != null && scores.containsKey(e.getPageId())) {
                        scores.merge(e.getPageId(), 2, Integer::sum);
                    }
                });
            }
        }
        Page bestPage = null;
        int bestScore = -1;
        for (var entry : scores.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue()).toList()) {
            Page p = pageMapper.selectById(entry.getKey());
            if (p != null && p.getHtmlContent() != null && p.getHtmlContent().length() > 50) {
                bestPage = p;
                bestScore = entry.getValue();
                break;
            }
        }
        if (bestPage != null) {
            log.info("页面 [{}] 找到风格参考页: [{}]（{} 字符，关联度 {} 分）",
                    page.getName(), bestPage.getName(), bestPage.getHtmlContent().length(), bestScore);
        } else {
            log.info("页面 [{}] 未找到风格参考页（无关联页面或关联页尚无 HTML）", page.getName());
        }
        return bestPage;
    }

    /**
     * 清理整页 HTML 中引用了不存在素材的 <img>：AI 可能臆造 /api/assets/u-xxx URL，
     * 渲染出来就是裂图。校验素材 ID 是否真实存在（内置或用户上传），无效则移除该 <img>。
     */
    private String sanitizeAssetRefs(String html) {
        if (html == null || !html.contains("/api/assets/")) return html;
        var pattern = java.util.regex.Pattern
                .compile("<img[^>]*src=[\"'](/api/assets/([^\"'?#\\s]+))[\"'][^>]*>");
        var m = pattern.matcher(html);
        StringBuilder sb = new StringBuilder();
        int last = 0;
        while (m.find()) {
            String assetId = m.group(2);
            if (!assetService.exists(assetId)) {
                sb.append(html, last, m.start());
                last = m.end();
                log.warn("移除引用了不存在素材的 <img>: {}", assetId);
            }
        }
        sb.append(html.substring(last));
        return sb.toString();
    }

    /**
     * 仅重新生成某页的整页 HTML（不重跑元素识别），供前端"重新生成"按钮快速迭代。
     * 返回新的 HTML 内容。
     */
    @Transactional
    public String regeneratePageHtml(long pageId) {
        Page page = pageMapper.selectById(pageId);
        if (page == null) {
            throw new IllegalArgumentException("页面不存在: " + pageId);
        }
        String html = renderTemplateHtml(page);
        page.setHtmlContent(html);
        pageMapper.updateById(page);
        log.info("页面 [{}] 模板化整页 HTML 重新渲染完成（{} 字符）", page.getName(), html.length());
        return html;
    }

    /**
     * 生成整页 HTML：若有风格参考页，则把"参考页设计稿底图 + 本页设计稿"两张图一起给 AI，
     * 让它真正"看着底图"来画，而不是仅靠一段 HTML 文本想象（这是页面不统一、乱用的关键修复）。
     */
    private String generateHtmlWithRef(Page page, String prompt, String mime, byte[] imageBytes,
                                        Page refPage, Path pageImagePath) {
        if (refPage != null && refPage.getBackgroundImage() != null && !refPage.getBackgroundImage().isBlank()) {
            Path refImg = Path.of(refPage.getBackgroundImage());
            if (Files.exists(refImg) && !refImg.toAbsolutePath().equals(pageImagePath.toAbsolutePath())) {
                try {
                    String refMime = guessMime(refImg.getFileName().toString());
                    byte[] refBytes = Files.readAllBytes(refImg);
                    log.info("页面 [{}] 使用多图方式生成：参考底图 [{}] + 本页设计稿", page.getName(), refPage.getName());
                    return aiClient.generateHtmlWithImages(
                            WireframePrompt.HTML_SYSTEM_PROMPT, prompt,
                            List.of(new AiClient.ImageInput(mime, imageBytes),
                                    new AiClient.ImageInput(refMime, refBytes)));
                } catch (Exception e) {
                    log.warn("参考底图读取失败，退回单图生成: {}", e.getMessage());
                }
            }
        }
        return aiClient.generateHtmlWithImage(WireframePrompt.HTML_SYSTEM_PROMPT, prompt, mime, imageBytes);
    }

    /**
     * 生成整页 HTML 并（可选）执行"渲染→截图→对比原稿→回修"闭环。
     * 供 analyzePage 与 regeneratePageHtml 共用，保证两条入口行为一致。
     */
    private String generateFinalHtml(Page page, String mime, byte[] imageBytes, Path imagePath) {
        Page refPage = findStyleReferencePage(page);
        String refHtml = refPage == null ? null : refPage.getHtmlContent();
        String elementSummary = buildElementSummary(page.getId());
        String navSummary = buildNavSummary(page.getId());
        String assetsSummary = assetService.getUserAssetsHtmlPrompt(imagePath);
        String prompt = WireframePrompt.buildHtmlUserPrompt(
                page.getCanvasWidth() == null ? 375 : page.getCanvasWidth(),
                page.getCanvasHeight() == null ? 812 : page.getCanvasHeight(),
                elementSummary, navSummary, assetsSummary, refHtml);
        String rawHtml = generateHtmlWithRef(page, prompt, mime, imageBytes, refPage, imagePath);
        String html = sanitizeAssetRefs(stripCodeFence(rawHtml));
        if (repairEnabled && htmlRenderer != null && htmlRenderer.isAvailable()) {
            html = repairHtml(html, page, elementSummary, navSummary, assetsSummary, mime, imageBytes, refHtml);
        }
        return html;
    }

    /**
     * 回修闭环：把当前 HTML 渲染成截图，连同原始设计稿一起喂回模型做"对比→修复"，最多 repairRounds 轮。
     * 模型从没见过自己的输出，这一闭环是纠正错位/色差/字体失衡的关键。
     */
    private String repairHtml(String html, Page page, String elementSummary, String navSummary,
                              String assetsSummary, String mime, byte[] imageBytes, String refHtml) {
        int canvasW = page.getCanvasWidth() == null ? 375 : page.getCanvasWidth();
        int canvasH = page.getCanvasHeight() == null ? 812 : page.getCanvasHeight();
        String current = html;
        for (int round = 1; round <= Math.max(1, repairRounds); round++) {
            try {
                byte[] shot = htmlRenderer.screenshot(current, canvasW);
                String repairPrompt = WireframePrompt.buildRepairUserPrompt(
                        canvasW, canvasH, elementSummary, navSummary, assetsSummary, refHtml);
                String repaired = aiClient.generateHtmlWithImages(
                        WireframePrompt.REPAIR_SYSTEM_PROMPT, repairPrompt,
                        List.of(new AiClient.ImageInput(mime, imageBytes),
                                new AiClient.ImageInput("image/png", shot)));
                String fixed = sanitizeAssetRefs(stripCodeFence(repaired));
                if (fixed != null && fixed.length() > 200 && fixed.contains("<!DOCTYPE")) {
                    current = fixed;
                    log.info("页面 [{}] 回修第 {} 轮完成（{} 字符）", page.getName(), round, current.length());
                } else {
                    log.warn("页面 [{}] 回修第 {} 轮模型未返回有效 HTML，停止回修", page.getName(), round);
                    break;
                }
            } catch (Exception e) {
                log.warn("页面 [{}] 回修第 {} 轮失败（保留上一版 HTML）：{}", page.getName(), round, e.getMessage());
                break;
            }
        }
        return current;
    }

    private static String guessMime(String filename) {
        String n = filename.toLowerCase();
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".webp")) return "image/webp";
        if (n.endsWith(".svg")) return "image/svg+xml";
        return "image/png";
    }

    /** 跳转清单摘要（供整页 HTML 生成时给元素打 data-nav 标记）：元素文案 → 目标页名 */
    private String buildNavSummary(long pageId) {
        List<Element> els = elementMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<Element>lambdaQuery()
                        .eq(Element::getPageId, pageId));
        if (els.isEmpty()) {
            return "";
        }
        Map<Long, String> labelById = new HashMap<>();
        for (Element e : els) {
            labelById.put(e.getId(), e.getLabel() == null ? "" : e.getLabel());
        }
        List<Interaction> inters = interactionMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<Interaction>lambdaQuery()
                        .in(Interaction::getElementId, els.stream().map(Element::getId).toList())
                        .isNotNull(Interaction::getTargetPageId).or()
                        .isNotNull(Interaction::getParams));
        StringBuilder sb = new StringBuilder();
        for (Interaction it : inters) {
            String targetName = null;
            if (it.getTargetPageId() != null) {
                Page target = pageMapper.selectById(it.getTargetPageId());
                targetName = target == null ? null : target.getName();
            } else if (it.getParams() != null && !it.getParams().isBlank()) {
                // 分析刚完成、补链未跑时，目标还存放在 params.target_name 里
                try {
                    targetName = objectMapper.readTree(it.getParams()).path("target_name").asText(null);
                } catch (Exception ignore) {
                    // 非法 JSON 忽略
                }
            }
            if (targetName == null || targetName.isBlank()) continue;
            Element src = els.stream().filter(e -> e.getId().equals(it.getElementId())).findFirst().orElse(null);
            String type = src != null && src.getType() != null ? src.getType() : "按钮";
            String pos = "";
            if (src != null && src.getPositionY() != null && src.getHeight() != null) {
                double y = src.getPositionY();
                double h = src.getHeight();
                pos = String.format("（类型=%s，约在画布纵向 y=%d~%d 区域）", type, (int) y, (int) (y + h));
            }
            String label = labelById.getOrDefault(it.getElementId(), "");
            sb.append(label.isBlank() ? "(未命名元素)" : label).append(pos).append(" → ").append(targetName).append('\n');
        }
        return sb.toString();
    }

    /**
     * 清空页面旧的分析数据（重复分析时避免脏数据）。
     */
    private void clearPageData(long pageId) {
        List<Element> oldElements = elementMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<Element>lambdaQuery()
                        .eq(Element::getPageId, pageId));
        if (oldElements.isEmpty()) return;
        List<Long> elementIds = oldElements.stream().map(Element::getId).toList();
        interactionMapper.delete(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<Interaction>lambdaQuery()
                        .in(Interaction::getElementId, elementIds));
        annotationMapper.delete(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<Annotation>lambdaQuery()
                        .in(Annotation::getElementId, elementIds));
        elementMapper.deleteBatchIds(elementIds);
    }

    /**
     * 内置示例识别结果（mock 模式），供中转站不可用时联调前端。
     */
    private JsonNode mockResult(Page page) {
        String fileName = java.nio.file.Paths.get(page.getBackgroundImage()).getFileName().toString();
        String json;
        if (fileName.contains("level_detail")) {
            json = """
                    {"page_name":"关卡详情","elements":[
                      {"type":"navbar","label":"关卡 1","bbox":[0,0,375,70],"description":"顶部导航栏，展示当前关卡名称，可返回上一页。"},
                      {"type":"image","label":"","asset_id":"product-03","bbox":[40,95,295,170],"description":"关卡地图预览区域，展示关卡地形与怪物分布。"},
                      {"type":"avatar","label":"","asset_id":"avatar-02","bbox":[24,285,40,40],"description":"玩家头像，展示当前角色形象。"},
                      {"type":"text","label":"通关条件：击败 5 只怪物","bbox":[72,290,279,30],"description":"明确通关目标，玩家需击败 5 只怪物才能获胜。"},
                      {"type":"text","label":"体力消耗：10 / 预计 3 分钟","bbox":[24,322,300,26],"description":"展示进入关卡的成本与预计耗时，方便玩家决策。"},
                      {"type":"container","label":"通关奖励","bbox":[24,350,327,90],"description":"奖励卡片：通关后可获得金币 x500。"},
                      {"type":"button","label":"开始挑战","bbox":[24,470,327,56],"interaction":{"trigger":"click","action":"navigate","target":"首页"},"description":"核心操作按钮，点击后消耗体力进入关卡挑战。"}
                    ]}
                    """;
        } else {
            json = """
                    {"page_name":"首页","elements":[
                      {"type":"avatar","label":"","asset_id":"avatar-03","bbox":[20,24,48,48],"description":"玩家头像，展示当前角色形象。"},
                      {"type":"text","label":"星辰冒险","bbox":[80,30,200,40],"description":"游戏主标题，展示品牌名称。"},
                      {"type":"image","label":"","asset_id":"product-02","bbox":[20,90,335,200],"description":"主视觉 Hero 图，展示游戏世界观氛围。"},
                      {"type":"icon","label":"首页","asset_id":"icon-home","bbox":[24,760,40,40],"description":"底部标签栏：首页图标，点击返回本页。"},
                      {"type":"button","label":"开始游戏","bbox":[20,320,335,56],"interaction":{"trigger":"click","action":"navigate","target":"关卡详情"},"description":"主入口按钮，点击进入关卡详情页开始游戏。"},
                      {"type":"container","label":"关卡 1","bbox":[20,400,335,88],"interaction":{"trigger":"click","action":"navigate","target":"关卡详情"},"description":"关卡卡片，展示关卡难度与奖励，点击可查看详情。"},
                      {"type":"container","label":"关卡 2","bbox":[20,500,335,88],"description":"关卡卡片，展示关卡难度与奖励。"},
                      {"type":"container","label":"关卡 3","bbox":[20,600,335,88],"description":"关卡卡片，展示关卡难度与奖励。"},
                      {"type":"navbar","label":"首页/商城/背包","bbox":[0,750,375,62],"description":"底部标签栏：首页、商城、背包三个主模块入口。"}
                    ]}
                    """;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException("mock 数据解析失败", e);
        }
    }

    /**
     * 补链：页面全部分析完后，把分析期间未匹配目标的交互（params 里存了 target_name）按最终页面名补上。
     *
     * @param originalNameToId 分析前的页面名（文件名）→ 页面 id 快照；AI 的 target 引用的是原始名，
     *                         页面分析后会被改成语义名，必须用快照才能解析
     */
    public void resolvePendingTargets(long projectId, Map<String, Long> originalNameToId) {
        List<Page> projectPages = pageMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<Page>lambdaQuery()
                        .eq(Page::getProjectId, projectId));
        if (projectPages.isEmpty()) return;

        Map<String, Long> pageIdByName = new HashMap<>();
        for (Page p : projectPages) {
            pageIdByName.put(p.getName(), p.getId());
        }
        if (originalNameToId != null) {
            pageIdByName.putAll(originalNameToId);
        }

        // 只补链本项目页面下的交互，避免跨项目按页面名串链
        List<Element> projectElements = elementMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<Element>lambdaQuery()
                        .in(Element::getPageId, projectPages.stream().map(Page::getId).toList()));
        if (projectElements.isEmpty()) return;

        List<Interaction> pending = interactionMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<Interaction>lambdaQuery()
                        .in(Interaction::getElementId, projectElements.stream().map(Element::getId).toList())
                        .isNotNull(Interaction::getParams)
                        .ne(Interaction::getParams, "")
                        .isNull(Interaction::getTargetPageId));
        if (pending.isEmpty()) return;

        int linked = 0;
        for (Interaction interaction : pending) {
            try {
                JsonNode params = objectMapper.readTree(interaction.getParams());
                String targetName = params.path("target_name").asText("");
                if (targetName.isBlank()) continue;
                Long targetPageId = pageIdByName.get(targetName);
                if (targetPageId != null) {
                    interaction.setTargetPageId(targetPageId);
                    interaction.setParams(null);
                    interactionMapper.updateById(interaction);
                    linked++;
                }
            } catch (Exception e) {
                log.warn("补链解析失败, interaction={}: {}", interaction.getId(), e.getMessage());
            }
        }
        if (linked > 0) {
            log.info("交互补链完成: {} 条", linked);
        }
    }

    /**
     * 状态变体页面公共骨架自动对齐：
     * 当项目中存在同源页面（如《个性装扮预览页》、《个性装扮形象待机页》、《个性装扮动效页》）时，
     * 自动识别公共组件（顶部导航条、返回按钮、角色/模特立绘框、公共操作栏、底部卡片托盘），
     * 强制吸附坐标与尺寸，确保切换交互时 100% 像素级吻合、无跳跃感。
     */
    @Transactional
    public void alignVariantPages(long projectId) {
        List<Page> pages = pageMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<Page>lambdaQuery()
                        .eq(Page::getProjectId, projectId));
        if (pages.size() < 2) return;

        // 1. 同源变体聚类（最长公共子串 LCS 聚类 + 关键词聚类）
        List<List<Page>> clusters = new ArrayList<>();
        boolean[] visited = new boolean[pages.size()];

        for (int i = 0; i < pages.size(); i++) {
            if (visited[i]) continue;
            List<Page> group = new ArrayList<>();
            group.add(pages.get(i));
            visited[i] = true;
            String nameI = pages.get(i).getName() == null ? "" : pages.get(i).getName();

            for (int j = i + 1; j < pages.size(); j++) {
                if (visited[j]) continue;
                String nameJ = pages.get(j).getName() == null ? "" : pages.get(j).getName();

                // 排除全屏遮罩/提醒弹窗
                if (nameI.contains("弹窗") || nameJ.contains("弹窗") || nameI.contains("提醒") || nameJ.contains("提醒")) {
                    continue;
                }

                // 判断是否包含公共主干词（如"个性装扮"、"装扮预览"、"壁纸"等）
                boolean isVariant = false;
                if ((nameI.contains("装扮") && nameJ.contains("装扮"))
                        || (nameI.contains("壁纸") && nameJ.contains("壁纸"))
                        || (nameI.contains("家园") && nameJ.contains("家园"))
                        || (nameI.contains("扭蛋") && nameJ.contains("扭蛋"))
                        || (nameI.contains("商店") && nameJ.contains("商店"))) {
                    isVariant = true;
                } else {
                    int lcsLen = longestCommonSubstringLen(nameI, nameJ);
                    if (lcsLen >= 3) {
                        isVariant = true;
                    }
                }

                if (isVariant) {
                    group.add(pages.get(j));
                    visited[j] = true;
                }
            }
            if (group.size() >= 2) {
                clusters.add(group);
            }
        }

        int alignedCount = 0;
        for (List<Page> groupPages : clusters) {
            // 选名字最标准/最短且最早创建的作为基准页（Base Page）
            Page basePage = groupPages.stream()
                    .min(Comparator.comparingInt((Page p) -> p.getName().length())
                            .thenComparing(Page::getId))
                    .orElse(groupPages.get(0));

            List<Element> baseEls = elementMapper.selectList(
                    com.baomidou.mybatisplus.core.toolkit.Wrappers.<Element>lambdaQuery()
                            .eq(Element::getPageId, basePage.getId()));
            if (baseEls.isEmpty()) continue;

            for (Page varPage : groupPages) {
                if (varPage.getId().equals(basePage.getId())) continue;
                List<Element> varEls = elementMapper.selectList(
                        com.baomidou.mybatisplus.core.toolkit.Wrappers.<Element>lambdaQuery()
                                .eq(Element::getPageId, varPage.getId()));

                for (Element ve : varEls) {
                    double vx = nz(ve.getPositionX()), vy = nz(ve.getPositionY());
                    double vw = nz(ve.getWidth()), vh = nz(ve.getHeight());
                    double vcx = vx + vw / 2.0, vcy = vy + vh / 2.0;
                    String vty = ve.getType() == null ? "" : ve.getType();
                    String vlb = ve.getLabel() == null ? "" : ve.getLabel().trim();

                    // 在 Base 页中寻找最匹配的公共骨架元素
                    Element bestMatch = null;
                    double bestDist = Double.MAX_VALUE;

                    for (Element be : baseEls) {
                        String bty = be.getType() == null ? "" : be.getType();
                        String blb = be.getLabel() == null ? "" : be.getLabel().trim();
                        boolean typeMatch = vty.equals(bty)
                                || (("image".equals(vty) || "banner".equals(vty) || "background".equals(vty))
                                    && ("image".equals(bty) || "banner".equals(bty) || "background".equals(bty)));
                        if (!typeMatch) continue;

                        double bx = nz(be.getPositionX()), by = nz(be.getPositionY());
                        double bw = nz(be.getWidth()), bh = nz(be.getHeight());
                        double bcx = bx + bw / 2.0, bcy = by + bh / 2.0;
                        double dist = Math.hypot(vcx - bcx, vcy - bcy);

                        // 1. 顶部导航栏 / 顶部返回键 (y < 70)
                        boolean isTopNav = ("navbar".equals(vty) || "icon".equals(vty)) && vy < 70 && by < 70;
                        // 2. 模特/角色立绘大占位框 (宽高 >= 100)
                        boolean isHeroArt = (vw >= 90 && vh >= 90 && bw >= 90 && bh >= 90);
                        // 3. 底部装扮卡片槽位/选项卡 (y >= 450 且尺寸相近)
                        boolean isBottomSlot = (vy >= 450 && by >= 450 && Math.abs(vw - bw) < 25 && Math.abs(vh - bh) < 25);
                        // 4. 具有相同或包含文案的按钮/文字
                        boolean isSameLabel = !vlb.isEmpty() && (vlb.equals(blb) || vlb.contains(blb) || blb.contains(vlb));

                        if ((isTopNav || isHeroArt || isBottomSlot || isSameLabel) && dist < 55.0) {
                            if (dist < bestDist) {
                                bestDist = dist;
                                bestMatch = be;
                            }
                        }
                    }

                    if (bestMatch != null && (Math.abs(vx - nz(bestMatch.getPositionX())) > 0.5
                            || Math.abs(vy - nz(bestMatch.getPositionY())) > 0.5
                            || Math.abs(vw - nz(bestMatch.getWidth())) > 0.5
                            || Math.abs(vh - nz(bestMatch.getHeight())) > 0.5)) {
                        // 执行像素级坐标吸附
                        ve.setPositionX(bestMatch.getPositionX());
                        ve.setPositionY(bestMatch.getPositionY());
                        ve.setWidth(bestMatch.getWidth());
                        ve.setHeight(bestMatch.getHeight());
                        elementMapper.updateById(ve);
                        alignedCount++;
                    }
                }
            }
        }
        if (alignedCount > 0) {
            log.info("项目 {} 变体页面公共骨架对齐完成，共吸附校正 {} 个元素", projectId, alignedCount);
        }
    }

    private static int longestCommonSubstringLen(String a, String b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return 0;
        int max = 0;
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                if (a.charAt(i - 1) == b.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                    max = Math.max(max, dp[i][j]);
                }
            }
        }
        return max;
    }

    private static double nz(Double v) {
        return v == null ? 0.0 : v;
    }

    /**
     * 轻量交互提取：对已识别完元素的项目，仅让 AI 补答"哪个元素 → 跳哪"并落库，
     * 不重跑元素识别/整页渲染。返回新插入的交互条数。
     */
    @Transactional
    public int extractInteractions(long projectId) {
        List<Page> pages = pageMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<Page>lambdaQuery()
                        .eq(Page::getProjectId, projectId)
                        .orderByAsc(Page::getSortOrder));
        if (pages.isEmpty()) return 0;

        Map<String, Long> pageIdByName = new HashMap<>();
        List<String> pageNames = new ArrayList<>();
        for (Page p : pages) {
            pageIdByName.put(p.getName(), p.getId());
            pageNames.add(p.getName());
        }

        // 先跑完所有 AI 调用（不写库），统一在末尾清理旧数据+插入，缩短事务写锁持有时间
        List<Interaction> toInsert = new ArrayList<>();
        for (Page page : pages) {
            if (page.getAnalyzed() == null || page.getAnalyzed() != 1) continue;
            if (mock) continue; // mock 模式不做 AI 交互提取
            List<Element> els = elementMapper.selectList(
                    com.baomidou.mybatisplus.core.toolkit.Wrappers.<Element>lambdaQuery()
                            .eq(Element::getPageId, page.getId())
                            .orderByAsc(Element::getPositionY).orderByAsc(Element::getPositionX));
            if (els.isEmpty()) continue;

            Map<Long, Element> elById = new HashMap<>();
            StringBuilder list = new StringBuilder();
            for (Element e : els) {
                elById.put(e.getId(), e);
                list.append(e.getId()).append(" | ").append(e.getType() == null ? "" : e.getType())
                        .append(" | ").append(e.getLabel() == null ? "" : e.getLabel())
                        .append(" | ").append(e.getPositionX() == null ? 0 : Math.round(e.getPositionX()))
                        .append(',').append(e.getPositionY() == null ? 0 : Math.round(e.getPositionY()))
                        .append(',').append(e.getWidth() == null ? 0 : Math.round(e.getWidth()))
                        .append(',').append(e.getHeight() == null ? 0 : Math.round(e.getHeight()))
                        .append('\n');
            }

            JsonNode root;
            try {
                java.nio.file.Path imagePath = Path.of(page.getBackgroundImage());
                if (!Files.exists(imagePath)) {
                    log.warn("交互提取跳过页面 [{}]：设计稿图片不存在: {}", page.getName(), page.getBackgroundImage());
                    continue;
                }
                String mime = guessMime(imagePath.getFileName().toString());
                byte[] imageBytes = Files.readAllBytes(imagePath);
                String userPrompt = WireframePrompt.buildInteractionUserPrompt(
                        page.getName(),
                        page.getCanvasWidth() == null ? 375 : page.getCanvasWidth(),
                        page.getCanvasHeight() == null ? 812 : page.getCanvasHeight(),
                        pageNames, list.toString());
                String raw = aiClient.generateWithImage(
                        WireframePrompt.INTERACTION_SYSTEM_PROMPT, userPrompt, mime, imageBytes);
                root = parseJson(raw);
            } catch (Exception e) {
                log.warn("页面 [{}] 交互提取失败（跳过）: {}", page.getName(), e.getMessage());
                continue;
            }

            java.util.Set<String> seen = new java.util.HashSet<>();
            for (JsonNode node : root.path("interactions")) {
                if (!node.isObject()) continue;
                long elId = node.path("element_id").asLong(0);
                if (elId <= 0 || !elById.containsKey(elId)) {
                    log.debug("交互提取：元素 id {} 不属于页面 [{}]，忽略", elId, page.getName());
                    continue;
                }
                String action = normalizeAction(node.path("action").asText(""));
                if (action == null) continue;
                String target = node.path("target").asText("");
                boolean needsTarget = "navigate".equals(action) || "popup".equals(action);
                if (needsTarget && target.isBlank()) continue;

                String dedupeKey = elId + ":" + action + ":" + target;
                if (!seen.add(dedupeKey)) continue;

                Interaction it = new Interaction();
                it.setElementId(elId);
                it.setTriggerType("click");
                it.setActionType(action);
                if (needsTarget) {
                    Long targetPageId = pageIdByName.get(target);
                    if (targetPageId != null) {
                        it.setTargetPageId(targetPageId);
                    } else {
                        // 页面名未匹配：存入 params，末尾 resolvePendingTargets 兜底补链
                        it.setParams("{\"target_name\":\"" + target.replace("\"", "") + "\"}");
                        log.debug("交互目标 [{}] 暂未匹配，待补链", target);
                    }
                }
                toInsert.add(it);
            }
        }

        if (toInsert.isEmpty()) {
            log.info("项目 {} 交互提取完成：0 条", projectId);
            return 0;
        }

        // 清掉项目旧交互后插入新交互（重复执行幂等）
        List<Element> projectElements = elementMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<Element>lambdaQuery()
                        .in(Element::getPageId, pages.stream().map(Page::getId).toList()));
        if (!projectElements.isEmpty()) {
            interactionMapper.delete(
                    com.baomidou.mybatisplus.core.toolkit.Wrappers.<Interaction>lambdaQuery()
                            .in(Interaction::getElementId,
                                    projectElements.stream().map(Element::getId).toList()));
        }
        for (Interaction it : toInsert) {
            interactionMapper.insert(it);
        }
        resolvePendingTargets(projectId, null);
        log.info("项目 {} 交互提取完成：{} 条", projectId, toInsert.size());
        return toInsert.size();
    }

    /** 交互动作归一化：映射到渲染器支持的四种动作，未知动作返回 null（直接丢弃） */
    private static String normalizeAction(String raw) {
        String a = raw == null ? "" : raw.trim().toLowerCase();
        return switch (a) {
            case "navigate", "navigation", "jump", "link" -> "navigate";
            case "popup", "modal", "overlay", "dialog" -> "popup";
            case "back", "close", "dismiss" -> "back";
            case "tab_switch", "tab", "switch_tab" -> "tab_switch";
            default -> null;
        };
    }

    private int saveElements(Page page, JsonNode elementsNode, double scaleX, double scaleY) {
        if (!elementsNode.isArray()) return 0;

        double canvasW = page.getCanvasWidth() == null ? 375 : page.getCanvasWidth();
        double canvasH = page.getCanvasHeight() == null ? 812 : page.getCanvasHeight();

        // 页面名 → 页面 id，用于解析交互 target
        Map<String, Long> pageIdByName = new HashMap<>();
        for (Page p : pageMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<Page>lambdaQuery()
                        .eq(Page::getProjectId, page.getProjectId()))) {
            pageIdByName.put(p.getName(), p.getId());
        }

        int index = 0;
        int count = 0;
        for (JsonNode node : elementsNode) {
            if (!node.isObject()) continue;

            Element element = new Element();
            element.setPageId(page.getId());
            String type = node.path("type").asText("other");
            element.setType(normalizeType(type));
            element.setLabel(node.path("label").asText(""));
            // style：合并 icon 与 AI 提取的视觉样式（填充色/文字色/描边/圆角），供前端高保真还原设计稿
            ObjectNode styleNode = objectMapper.createObjectNode();
            String iconName = node.path("icon").asText("");
            if (!iconName.isBlank()) {
                styleNode.put("icon", iconName.replace("\"", ""));
            }
            JsonNode styleIn = node.path("style");
            if (styleIn.isObject()) {
                copyTextStyle(styleIn, styleNode, "fill");
                copyTextStyle(styleIn, styleNode, "text_color");
                copyTextStyle(styleIn, styleNode, "border_color");
                copyTextStyle(styleIn, styleNode, "gradient");
                if (styleIn.path("radius").isNumber()) {
                    styleNode.put("radius", styleIn.path("radius").asDouble());
                }
            }
            if (styleNode.size() > 0) {
                element.setStyle(styleNode.toString());
            }
            // 素材库：识别到素材元素时优先使用 AI 给出的 asset_id，否则自动分配一个
            String assetId = node.path("asset_id").asText("").trim();
            if (assetId.isBlank() && isAssetType(type)) {
                assetId = assetService.getRandomAsset(type);
            }
            // 校验：AI 可能臆造不存在的素材 ID，直接落库会导致前端渲染 404 裂图——丢弃并告警
            if (!assetId.isBlank() && !assetService.exists(assetId)) {
                log.warn("元素 [{}] 引用了不存在的素材 {}，已忽略", element.getLabel(), assetId);
                assetId = "";
            }
            if (!assetId.isBlank()) {
                element.setAssetId(assetId);
            }
            element.setCreatedBy("ai");

            JsonNode bbox = node.path("bbox");
            if (bbox.isArray() && bbox.size() >= 4) {
                double rawX1 = bbox.get(0).asDouble();
                double rawY1 = bbox.get(1).asDouble();
                double rawV2 = bbox.get(2).asDouble();
                double rawV3 = bbox.get(3).asDouble();

                // 智能坐标格式识别：判断大模型给出的是 [x, y, w, h] 还是 [x1, y1, x2, y2]
                double rawW, rawH;
                boolean isX2Y2 = false;
                if (rawV2 > rawX1 && rawV3 > rawY1) {
                    // 当 (x1 + v2) 越界或 (y1 + v3) 严重超出，或者 (v3 - y1) 为合理高度时
                    if ((rawX1 * scaleX + rawV2 * scaleX > canvasW + 10 && rawV2 * scaleX <= canvasW + 10)
                            || (rawY1 * scaleY + rawV3 * scaleY > canvasH + 10 && rawV3 * scaleY <= canvasH + 10)
                            || (rawY1 * scaleY > 100 && (rawV3 - rawY1) > 0 && (rawV3 - rawY1) < rawV3 * 0.75)) {
                        isX2Y2 = true;
                    }
                }

                if (isX2Y2) {
                    rawW = rawV2 - rawX1;
                    rawH = rawV3 - rawY1;
                } else {
                    rawW = rawV2;
                    rawH = rawV3;
                }

                double x = rawX1 * scaleX;
                double y = rawY1 * scaleY;
                double w = rawW * scaleX;
                double h = rawH * scaleY;
                x = Math.max(0, Math.min(x, canvasW - 4));
                y = Math.max(0, Math.min(y, canvasH - 4));
                w = Math.max(4, Math.min(w, canvasW - x));
                h = Math.max(4, Math.min(h, canvasH - y));

                // 尺寸异常保护网：防止 AI 把 Y2 误填成 height 导致出现穿透多层的巨型按钮/角标/卡片
                String normType = element.getType() == null ? "" : element.getType();
                if ("button".equals(normType) && h > 60) {
                    h = Math.min(38.0, h);
                } else if ("badge".equals(normType) && h > 45) {
                    h = Math.min(22.0, h);
                } else if ("icon".equals(normType) && h > 80 && w <= 80) {
                    h = w;
                } else if ("text".equals(normType) && h > 80 && element.getLabel() != null && element.getLabel().length() <= 20) {
                    h = Math.min(24.0, h);
                } else if ("container".equals(normType) && w <= 115 && h > 150) {
                    String lbl = element.getLabel() == null ? "" : element.getLabel();
                    boolean isDockOrSidebar = lbl.contains("菜单") || lbl.contains("栏") || lbl.contains("侧边") || lbl.contains("悬浮") || lbl.contains("导航");
                    if (!isDockOrSidebar) {
                        h = Math.min(135.0, h);
                    }
                } else if ("image".equals(normType) && w <= 115 && h > 130) {
                    h = Math.min(105.0, h);
                }

                element.setPositionX(x);
                element.setPositionY(y);
                element.setWidth(w);
                element.setHeight(h);
            }
            elementMapper.insert(element);

            // 交互
            JsonNode interactionNode = node.path("interaction");
            if (interactionNode != null && interactionNode.isObject()) {
                String action = interactionNode.path("action").asText("");
                if (!action.isBlank()) {
                    Interaction interaction = new Interaction();
                    interaction.setElementId(element.getId());
                    interaction.setTriggerType(interactionNode.path("trigger").asText("click"));
                    interaction.setActionType(action);
                    String target = interactionNode.path("target").asText("");
                    Long targetPageId = pageIdByName.get(target);
                    if (targetPageId != null) {
                        interaction.setTargetPageId(targetPageId);
                    } else if (action.equals("modal") || action.equals("carousel")) {
                        // 弹窗/轮播不需要目标页
                    } else if (!target.isBlank()) {
                        // 目标页面可能尚未分析完成（页面名会被 AI 改写），先存目标名，末尾统一补链
                        interaction.setParams("{\"target_name\":\"" + target + "\"}");
                        log.debug("交互目标 [{}] 暂未匹配，待补链", target);
                    }
                    interactionMapper.insert(interaction);
                }
            }

            // 标注说明
            String description = node.path("description").asText("");
            if (!description.isBlank()) {
                Annotation annotation = new Annotation();
                annotation.setPageId(page.getId());
                annotation.setElementId(element.getId());
                annotation.setText(description);
                if (element.getPositionX() != null && element.getPositionY() != null) {
                    annotation.setPositionX(element.getPositionX() + element.getWidth() / 2.0);
                    annotation.setPositionY(element.getPositionY());
                }
                annotationMapper.insert(annotation);
            }

            index++;
            count++;
        }
        return count;
    }

    /**
     * 从模型输出中提取 JSON（兼容带 Markdown 代码块或前后废话的输出）。
     */
    public JsonNode parseJson(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("AI 返回为空");
        }
        String candidate = raw.trim();
        // 去掉 Markdown 代码块
        if (candidate.startsWith("```")) {
            candidate = candidate.replaceAll("^```[a-zA-Z]*\\s*", "").replaceAll("```\\s*$", "").trim();
        }
        int start = candidate.indexOf('{');
        int end = candidate.lastIndexOf('}');
        if (start >= 0 && end > start) {
            candidate = candidate.substring(start, end + 1);
        }
        try {
            return objectMapper.readTree(candidate);
        } catch (Exception e) {
            throw new IllegalStateException("AI 输出不是合法 JSON: " + e.getMessage(), e);
        }
    }

    public List<String> buildPageNames(List<Page> pages) {
        List<String> names = new ArrayList<>();
        for (Page p : pages) {
            names.add(p.getName());
        }
        return names;
    }

    /** 是否为需要绑定素材库的视觉元素类型 */
    /** 从 AI style 对象中复制文本字段（空值跳过） */
    private static void copyTextStyle(JsonNode from, ObjectNode to, String field) {
        String v = from.path(field).asText("");
        if (!v.isBlank()) {
            to.put(field, v);
        }
    }

    /** AI 输出类型别名 → 渲染器标准类型（防止 toggle/tabbar/card 等写法落到 other 退化成方框） */
    private static final Map<String, String> TYPE_ALIASES = Map.ofEntries(
            Map.entry("toggle", "switch"),
            Map.entry("toggleswitch", "switch"),
            Map.entry("switchbutton", "switch"),
            Map.entry("bottomnav", "navbar"),
            Map.entry("tabbar", "navbar"),
            Map.entry("tab", "tabs"),
            Map.entry("card", "container"),
            Map.entry("panel", "container"),
            Map.entry("btn", "button"),
            Map.entry("img", "image"),
            Map.entry("picture", "image"),
            Map.entry("photo", "image"),
            Map.entry("label", "text"),
            Map.entry("title", "text"),
            Map.entry("star", "rating"),
            Map.entry("rate", "rating"),
            Map.entry("head", "avatar"),
            Map.entry("bubble", "icon"),
            Map.entry("floatbutton", "icon"),
            Map.entry("fab", "icon"),
            Map.entry("bg", "background"),
            Map.entry("glow", "effect"),
            Map.entry("separator", "divider"),
            Map.entry("tag", "badge"),
            Map.entry("searchbar", "search"));

    /** 归一化元素类型：统一小写、分隔符转下划线后查别名表 */
    private static String normalizeType(String raw) {
        if (raw == null || raw.isBlank()) {
            return "other";
        }
        String key = raw.trim().toLowerCase().replace('-', '_').replace(' ', '_');
        return TYPE_ALIASES.getOrDefault(key, raw.trim().toLowerCase());
    }

    /** 元素清单摘要（供整页 HTML 生成的第二次 AI 调用使用） */
    private String buildElementSummary(long pageId) {
        List<Element> els = elementMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<Element>lambdaQuery()
                        .eq(Element::getPageId, pageId)
                        .orderByAsc(Element::getPositionY)
                        .orderByAsc(Element::getPositionX));
        // 防重复：若某 container 的 label 已被其内部的 text/icon 子元素承载（相同文案且 bbox 重叠），
        // 则抑制容器自身 label，避免整页生成时同一句文案被渲染两次并重叠（如"限时兑换商店"）。
        StringBuilder sb = new StringBuilder();
        for (Element e : els) {
            String label = e.getLabel() == null ? "" : e.getLabel().trim();
            boolean suppressLabel = "container".equals(e.getType()) && !label.isBlank()
                    && els.stream().anyMatch(o -> isTextBearer(o)
                            && !o.getId().equals(e.getId())
                            && label.equalsIgnoreCase((o.getLabel() == null ? "" : o.getLabel()).trim())
                            && bboxOverlap(e, o));
            String effectiveLabel = suppressLabel ? "" : (e.getLabel() == null ? "" : e.getLabel());
            String fill = styleAttr(e.getStyle(), "fill");
            sb.append(String.format("%s | %s | %.0f,%.0f,%.0f,%.0f%s%n",
                    e.getType(),
                    effectiveLabel,
                    e.getPositionX(), e.getPositionY(), e.getWidth(), e.getHeight(),
                    fill.isBlank() ? "" : " | " + fill));
        }
        return sb.toString();
    }

    /** 能承载可见文案的元素（其 label 会被渲染成文字） */
    private static boolean isTextBearer(Element e) {
        return "text".equals(e.getType()) || "icon".equals(e.getType());
    }

    /** 两个元素 bbox 是否重叠（用于判断"子元素是否在容器内"） */
    private static boolean bboxOverlap(Element a, Element b) {
        if (a.getPositionX() == null || b.getPositionX() == null) {
            return false;
        }
        double ax2 = a.getPositionX() + a.getWidth(), ay2 = a.getPositionY() + a.getHeight();
        double bx2 = b.getPositionX() + b.getWidth(), by2 = b.getPositionY() + b.getHeight();
        return !(ax2 < b.getPositionX() || a.getPositionX() > bx2
                || ay2 < b.getPositionY() || a.getPositionY() > by2);
    }

    /** 从元素 style JSON 中取指定属性 */
    private static String styleAttr(String styleJson, String attr) {
        if (styleJson == null || styleJson.isBlank()) {
            return "";
        }
        try {
            String v = objectMapperValue(styleJson, attr);
            return v == null ? "" : v;
        } catch (Exception e) {
            return "";
        }
    }

    private static String objectMapperValue(String json, String attr) throws IOException {
        return new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(json).path(attr).asText(null);
    }

    /** 去掉模型可能包裹的 Markdown 代码围栏，只保留 HTML 主体 */
    private static String stripCodeFence(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim();
        if (s.startsWith("```")) {
            int firstLineBreak = s.indexOf('\n');
            if (firstLineBreak > 0) {
                s = s.substring(firstLineBreak + 1);
            }
            int lastFence = s.lastIndexOf("```");
            if (lastFence > 0) {
                s = s.substring(0, lastFence);
            }
        }
        return s.trim();
    }

    private static boolean isAssetType(String type) {
        return "avatar".equals(type) || "image".equals(type) || "icon".equals(type)
                || "background".equals(type) || "effect".equals(type);
    }
}