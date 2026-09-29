package com.wireforge.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wireforge.ai.AiClient;
import com.wireforge.entity.Element;
import com.wireforge.entity.Interaction;
import com.wireforge.entity.Page;
import com.wireforge.mapper.ElementMapper;
import com.wireforge.mapper.InteractionMapper;
import com.wireforge.mapper.PageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 全局交互拓扑与自动布线引擎（Interaction Autowire Service）：
 * 依据工程化图谱拓扑推导多页面之间的语义跳转关系：
 * 1. 全局 Tabbar 广播矩阵对齐（所有页面底部 Tab 自动绑定到对应主页面）；
 * 2. 功能入口/卡片/按钮下钻智能匹配（如"兑换记录"、"装扮"等自动跳转对应详情页）；
 * 3. 树状层级返回链路反推（顶部返回箭头自动绑定 action="back"）；
 * 4. 模态弹窗与浮层遮罩识别（中奖结果、更新弹窗绑定 action="modal"）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InteractionAutowireService {

    private final PageMapper pageMapper;
    private final ElementMapper elementMapper;
    private final InteractionMapper interactionMapper;
    private final com.wireforge.ai.AiClient aiClient;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    /**
     * 对指定项目执行全量交互拓扑自动布线
     *
     * @param projectId 项目 ID
     * @return 新增或更新的交互连线数量
     */
    @Transactional
    public int autowireProjectInteractions(Long projectId) {
        List<Page> pages = pageMapper.selectList(
                Wrappers.<Page>lambdaQuery()
                        .eq(Page::getProjectId, projectId)
                        .orderByAsc(Page::getSortOrder)
                        .orderByAsc(Page::getId));
        if (pages.isEmpty()) {
            return 0;
        }

        Map<Long, Page> pageMap = pages.stream().collect(Collectors.toMap(Page::getId, p -> p));
        List<Element> allElements = elementMapper.selectList(
                Wrappers.<Element>lambdaQuery()
                        .in(Element::getPageId, pageMap.keySet()));

        Map<Long, List<Element>> elementsByPage = allElements.stream()
                .filter(e -> e.getPageId() != null)
                .collect(Collectors.groupingBy(Element::getPageId));

        int wiredCount = 0;

        for (Page page : pages) {
            List<Element> els = elementsByPage.getOrDefault(page.getId(), Collections.emptyList());
            if (els.isEmpty()) continue;
            double canvasW = page.getCanvasWidth() == null ? 375 : page.getCanvasWidth();
            double canvasH = page.getCanvasHeight() == null ? 812 : page.getCanvasHeight();

            List<Long> elIds = els.stream().map(Element::getId).toList();
            List<Interaction> existingInters = elIds.isEmpty() ? Collections.emptyList() :
                    interactionMapper.selectList(Wrappers.<Interaction>lambdaQuery().in(Interaction::getElementId, elIds));
            Map<Long, List<Interaction>> interMap = existingInters.stream()
                    .collect(Collectors.groupingBy(Interaction::getElementId));
            Map<String, List<Element>> byGroup = els.stream()
                    .filter(e -> e.getGroupKey() != null && !e.getGroupKey().isBlank())
                    .collect(Collectors.groupingBy(Element::getGroupKey));

            for (Element el : els) {
                List<Interaction> curInters = tidyLines(el, interMap.getOrDefault(el.getId(), Collections.emptyList()), canvasW, canvasH);
                interMap.put(el.getId(), curInters);
                if (!eligible(el, canvasW, canvasH)) continue;
                if (curInters.stream().anyMatch(i -> "user".equals(i.getSource()))) continue;
                if (hasLine(curInters)) continue;

                String label = combinedLabel(el, byGroup.getOrDefault(el.getGroupKey(), List.of(el)));
                String type = el.getType() == null ? "" : el.getType().toLowerCase();
                double y = el.getPositionY() == null ? 0 : el.getPositionY();
                double x = el.getPositionX() == null ? 0 : el.getPositionX();
                double w = el.getWidth() == null ? 0 : el.getWidth();
                double h = el.getHeight() == null ? 0 : el.getHeight();
                String key = el.getGroupKey() == null ? "" : el.getGroupKey();

                if (isBackButton(el, x, y, w, h, label)) {
                    insertAutowire(el.getId(), "back", null);
                    wiredCount++;
                    continue;
                }

                if (key.contains("-tab-") || (y >= canvasH * ClickGrouper.BOTTOM_BAND && "anchor".equals(el.getGroupRole()))) {
                    Page targetTab = matchByContainment(label, pages, page.getId(), 2, false);
                    if (targetTab != null) {
                        insertAutowire(el.getId(), "navigate", targetTab.getId());
                        wiredCount++;
                    }
                    continue;
                }

                boolean cardSized = !"container".equals(type) || (w > 30 && h > 30 && w <= (page.getCanvasWidth() == null ? 375 : page.getCanvasWidth()) * 0.92);
                if (("button".equals(type) || "container".equals(type)) && cardSized) {
                    Page targetPage = matchByContainment(label, pages, page.getId(), 3, true);
                    if (targetPage != null) {
                        String action = isModalPage(targetPage) ? "popup" : "navigate";
                        insertAutowire(el.getId(), action, targetPage.getId());
                        wiredCount++;
                    }
                }
            }
        }

        log.info("[交互布线] 项目 {} 交互自动对齐完成，共自动建立/更新 {} 条交互连线", projectId, wiredCount);
        return wiredCount;
    }

    /** 只给一组的代表，或单独的按钮、卡片、图标、返回补线。组员不再各拉一条。 */
    private static boolean eligible(Element el, double canvasW, double canvasH) {
        if (el == null || ClickGrouper.skip(el)) return false;
        String role = el.getGroupRole() == null ? "" : el.getGroupRole();
        if ("member".equals(role)) return false;
        if ("anchor".equals(role)) return true;
        String type = el.getType() == null ? "" : el.getType().toLowerCase();
        if ("button".equals(type) || "icon".equals(type) || "tab".equals(type)) return true;
        if ("container".equals(type)) {
            double w = el.getWidth() == null ? 0 : el.getWidth();
            double h = el.getHeight() == null ? 0 : el.getHeight();
            return w > 30 && h > 20 && w <= canvasW * 0.95 && h <= canvasH * 0.55;
        }
        String label = el.getLabel() == null ? "" : el.getLabel();
        return label.contains("返回") || label.contains("关闭");
    }

    /**
     * 说明文字、散落图标上的旧自动线删掉。人手动保存的留下。
     * 同一个元素上重复的自动线收成一条。
     */
    private List<Interaction> tidyLines(Element el, List<Interaction> lines, double canvasW, double canvasH) {
        if (lines.isEmpty()) return lines;
        List<Interaction> users = new ArrayList<>();
        List<Interaction> autos = new ArrayList<>();
        for (Interaction it : lines) {
            if ("user".equals(it.getSource())) users.add(it);
            else autos.add(it);
        }
        if (!eligible(el, canvasW, canvasH)) {
            for (Interaction auto : autos) interactionMapper.deleteById(auto.getId());
            return users;
        }
        if (!users.isEmpty()) {
            for (Interaction auto : autos) interactionMapper.deleteById(auto.getId());
            return users;
        }
        if (autos.size() <= 1) return autos;
        Interaction keep = autos.get(0);
        for (Interaction it : autos) {
            if (it.getTargetPageId() != null) {
                keep = it;
                break;
            }
        }
        for (Interaction it : autos) {
            if (!it.getId().equals(keep.getId())) interactionMapper.deleteById(it.getId());
        }
        return List.of(keep);
    }

    private static boolean hasLine(List<Interaction> lines) {
        for (Interaction i : lines) {
            if ("back".equals(i.getActionType())) return true;
            if (i.getTargetPageId() != null) return true;
            if (i.getParams() != null && i.getParams().contains("target_name")) return true;
        }
        return false;
    }

    private static String combinedLabel(Element anchor, List<Element> group) {
        StringBuilder sb = new StringBuilder();
        if (anchor.getLabel() != null) sb.append(anchor.getLabel().trim());
        for (Element e : group) {
            if (e.getId() != null && e.getId().equals(anchor.getId())) continue;
            if (e.getLabel() == null || e.getLabel().isBlank()) continue;
            if (sb.indexOf(e.getLabel().trim()) >= 0) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(e.getLabel().trim());
        }
        return sb.toString();
    }

    /**
     * 去掉空格标点后，一边完整包含另一边。minLen 是较短一边至少要有的字数。
     * ambiguous 时不连。includeModal 为 false 时跳过弹窗页（底部导航只对主页面）。
     */
    private Page matchByContainment(String label, List<Page> pages, Long selfId, int minLen, boolean includeModal) {
        String clean = strip(label);
        if (clean.length() < minLen) return null;
        List<Page> hits = new ArrayList<>();
        for (Page p : pages) {
            if (p.getId() == null || p.getId().equals(selfId)) continue;
            if (!includeModal && isModalPage(p)) continue;
            String name = strip(p.getName());
            if (name.length() < minLen && clean.length() < minLen) continue;
            boolean hit = name.equals(clean)
                    || (name.contains(clean) && clean.length() >= minLen)
                    || (clean.contains(name) && name.length() >= minLen);
            if (hit) hits.add(p);
        }
        if (hits.isEmpty()) return null;
        hits.sort(Comparator.comparingInt(p -> {
            String name = strip(p.getName());
            if (name.equals(clean)) return 0;
            return name.length();
        }));
        if (hits.size() >= 2) {
            int a = strip(hits.get(0).getName()).length();
            int b = strip(hits.get(1).getName()).length();
            boolean exact = strip(hits.get(0).getName()).equals(clean);
            if (!exact && a == b) return null;
        }
        return hits.get(0);
    }

    private static String strip(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("[\\s\\p{P}\\p{S}]+", "").toLowerCase();
    }

    private void insertAutowire(Long elementId, String actionType, Long targetPageId) {
        insertOrUpdateInteraction(elementId, actionType, targetPageId, "autowire");
    }

    private void insertOrUpdateInteraction(Long elementId, String actionType, Long targetPageId, String source) {
        List<Interaction> existing = interactionMapper.selectList(
                Wrappers.<Interaction>lambdaQuery().eq(Interaction::getElementId, elementId));
        // 绝不覆盖人手动保存的线
        if (existing.stream().anyMatch(i -> "user".equals(i.getSource()))) {
            return;
        }
        if (!existing.isEmpty()) {
            Interaction it = existing.get(0);
            it.setTriggerType("click");
            it.setActionType(actionType);
            it.setTargetPageId(targetPageId);
            it.setSource(source);
            interactionMapper.updateById(it);
            for (int i = 1; i < existing.size(); i++) {
                interactionMapper.deleteById(existing.get(i).getId());
            }
        } else {
            Interaction it = new Interaction();
            it.setElementId(elementId);
            it.setTriggerType("click");
            it.setActionType(actionType);
            it.setTargetPageId(targetPageId);
            it.setSource(source);
            interactionMapper.insert(it);
        }
    }

    /**
     * 候选元素结构体：用于消除歧义与提供全量屏幕语义上下文
     */
    public record CandidateInfo(Element element, Page page, String label, String locationDesc) {}

    /**
     * 阶段二：增量语义智能连线（Incremental Topological AI Wiring）
     * 1. 严格遵守零浪费原则：先由本地确定性规则布线（autowireProjectInteractions）；
     * 2. 严格保护用户手动连线（source == 'user' 永不覆盖）；
     * 3. 严格保护已有确定性连线（仅提取未决、无目标页面的可交互元素）；
     * 4. 消除歧义与上下文描述：向纯文本模型提供 [编号、页面原名、文案、屏幕具体方位/坐标]；
     * 5. 严格白名单约束：模型只能输出有效目标页面原名（或 null），不瞎猜不存在的页面；
     * 6. 耗费极低（纯文本 1000~2000 token，2~4秒返回），永不超时断开。
     */
    public int autowireUnresolvedWithAi(Long projectId) {
        // 1. 先由本地规则布线（0 Token，0 幻觉）
        autowireProjectInteractions(projectId);

        List<Page> pages = pageMapper.selectList(
                Wrappers.<Page>lambdaQuery()
                        .eq(Page::getProjectId, projectId)
                        .orderByAsc(Page::getSortOrder)
                        .orderByAsc(Page::getId));
        if (pages.size() <= 1) {
            return 0;
        }

        Map<Long, Page> pageMap = pages.stream().collect(Collectors.toMap(Page::getId, p -> p));
        List<Element> allElements = elementMapper.selectList(
                Wrappers.<Element>lambdaQuery()
                        .in(Element::getPageId, pageMap.keySet()));
        if (allElements.isEmpty()) {
            return 0;
        }

        Map<Long, List<Element>> elementsByPage = allElements.stream()
                .filter(e -> e.getPageId() != null)
                .collect(Collectors.groupingBy(Element::getPageId));

        List<Long> allElIds = allElements.stream().map(Element::getId).toList();
        List<Interaction> existingInters = interactionMapper.selectList(
                Wrappers.<Interaction>lambdaQuery().in(Interaction::getElementId, allElIds));
        Map<Long, List<Interaction>> interMap = existingInters.stream()
                .collect(Collectors.groupingBy(Interaction::getElementId));

        // 收集全项目待推导的未决候选元素
        List<CandidateInfo> candidates = new ArrayList<>();

        for (Page page : pages) {
            List<Element> els = elementsByPage.getOrDefault(page.getId(), Collections.emptyList());
            if (els.isEmpty()) continue;
            double canvasW = page.getCanvasWidth() == null ? 375 : page.getCanvasWidth();
            double canvasH = page.getCanvasHeight() == null ? 812 : page.getCanvasHeight();
            Map<String, List<Element>> byGroup = els.stream()
                    .filter(e -> e.getGroupKey() != null && !e.getGroupKey().isBlank())
                    .collect(Collectors.groupingBy(Element::getGroupKey));

            for (Element el : els) {
                if (el == null || ClickGrouper.skip(el)) continue;
                String role = el.getGroupRole() == null ? "" : el.getGroupRole();
                if ("member".equals(role)) continue; // 组员由代表统一响应

                List<Interaction> curInters = interMap.getOrDefault(el.getId(), Collections.emptyList());
                // 绝不碰用户手动连过的线
                if (curInters.stream().anyMatch(i -> "user".equals(i.getSource()))) {
                    continue;
                }
                // 如果已经有确定目标页面，或已有返回行为，则跳过
                if (hasLine(curInters)) {
                    continue;
                }

                if (!isClickableCandidate(el, canvasW, canvasH)) {
                    continue;
                }

                String label = combinedLabel(el, byGroup.getOrDefault(el.getGroupKey(), List.of(el)));
                String locDesc = describeLocation(el, canvasW, canvasH);
                candidates.add(new CandidateInfo(el, page, label, locDesc));
            }
        }

        if (candidates.isEmpty()) {
            log.info("[AI拓扑连线] 项目 {} 无未决可点击元素，无需调用文本模型", projectId);
            return 0;
        }

        log.info("[AI拓扑连线] 项目 {} 筛选出 {} 个未决候选元素，准备调用纯文本模型进行语义拓扑推导", projectId, candidates.size());

        // 目标页面白名单（必须严格使用这些原始名称）
        List<String> validPageNames = pages.stream().map(Page::getName).filter(Objects::nonNull).distinct().toList();

        int totalAiWired = 0;
        // 分批发送，每批最多 25 个候选，确保大模型上下文精细聚焦，输出格式绝不被截断
        int batchSize = 25;
        for (int i = 0; i < candidates.size(); i += batchSize) {
            List<CandidateInfo> batch = candidates.subList(i, Math.min(i + batchSize, candidates.size()));
            totalAiWired += processAiBatch(projectId, batch, pages, validPageNames);
        }

        log.info("[AI拓扑连线] 项目 {} 阶段二增量推导完成，成功建立 {} 条语义连线", projectId, totalAiWired);
        return totalAiWired;
    }

    private boolean isClickableCandidate(Element el, double canvasW, double canvasH) {
        String type = el.getType() == null ? "" : el.getType().toLowerCase();
        double w = el.getWidth() == null ? 0 : el.getWidth();
        double h = el.getHeight() == null ? 0 : el.getHeight();
        double y = el.getPositionY() == null ? 0 : el.getPositionY();
        String label = el.getLabel() == null ? "" : el.getLabel().trim();

        if ("button".equals(type) || "anchor".equals(el.getGroupRole())) {
            return true;
        }
        if ("icon".equals(type)) {
            if (!label.isBlank()) return true;
            if (y >= canvasH * ClickGrouper.BOTTOM_BAND) return true;
            if (y <= canvasH * 0.15 && w >= 16 && h >= 16) return true;
            return false;
        }
        if ("container".equals(type)) {
            boolean cardSize = w > 24 && h > 20 && w <= canvasW * 0.95 && h <= canvasH * 0.55;
            return cardSize && !label.isBlank();
        }
        if ("text".equals(type) || "tab".equals(type)) {
            if (label.length() >= 2 && (label.contains("更多") || label.contains("查看") || label.contains("详情")
                    || label.contains("去") || label.contains("兑换") || label.contains("规则")
                    || label.contains("说明") || label.contains("记录") || label.contains("全部")
                    || label.contains("设置") || label.contains("背包") || label.contains("商城"))) {
                return true;
            }
        }
        return false;
    }

    private String describeLocation(Element el, double canvasW, double canvasH) {
        double x = el.getPositionX() == null ? 0 : el.getPositionX();
        double y = el.getPositionY() == null ? 0 : el.getPositionY();
        double w = el.getWidth() == null ? 0 : el.getWidth();
        double h = el.getHeight() == null ? 0 : el.getHeight();
        double cx = x + w / 2.0;

        String vert;
        if (y < canvasH * 0.12) {
            vert = "顶部导航/状态栏";
        } else if (y < canvasH * 0.35) {
            vert = "页面上部";
        } else if (y < canvasH * 0.70) {
            vert = "页面中部内容区";
        } else if (y < canvasH * 0.88) {
            vert = "页面下部操作区";
        } else {
            vert = "底部导航/吸底栏";
        }

        String horiz;
        if (cx < canvasW * 0.33) {
            horiz = "偏左侧";
        } else if (cx > canvasW * 0.67) {
            horiz = "偏右侧";
        } else {
            horiz = "居中";
        }

        return String.format("%s%s (x=%.0f, y=%.0f, w=%.0f, h=%.0f)", vert, horiz, x, y, w, h);
    }

    private int processAiBatch(Long projectId, List<CandidateInfo> batch, List<Page> allPages, List<String> validPageNames) {
        StringBuilder sbPrompt = new StringBuilder();
        sbPrompt.append("【可用目标页面原名白名单（只能从该列表中挑选目标页面全名）】\n");
        for (int i = 0; i < validPageNames.size(); i++) {
            sbPrompt.append(i + 1).append(". ").append(validPageNames.get(i)).append("\n");
        }
        sbPrompt.append("\n【待推导的未连线元素列表】\n");
        for (CandidateInfo c : batch) {
            sbPrompt.append(String.format("- [编号: %d] 所在页面: \"%s\" | 组件类型: %s | 文案: \"%s\" | 屏幕位置: %s\n",
                    c.element().getId(), c.page().getName(), c.element().getType(),
                    c.label() == null || c.label().isBlank() ? "(无文案)" : c.label(),
                    c.locationDesc()));
        }
        sbPrompt.append("\n请逐一分析上述元素在对应业务场景下的点击跳转目标。\n" +
                "若是纯页内状态变更（如声音开关、勾选协议、当前页选项卡、购买/消耗、抽奖动画等无对应落地页的操作）必须映射为 null。\n" +
                "请严格输出 JSON 字典，键为字符串形式的元素编号，值为白名单中的目标页面名称或 null。不要输出任何其他说明。");

        String systemPrompt = """
                你是一个移动端原型产品架构师与拓扑连线专家。
                你的任务是将原型页面中尚未连线的【可点击元素】与【目标页面】进行精准关联匹配。

                【全局约束规则】
                1. 目标页面必须 100% 精确使用【可用目标页面原名白名单】中的名称，严禁臆造或修改页面名称。
                2. 必须综合结合元素所在页面的业务场景、元素文案、组件类型及屏幕具体方位进行推导。
                3. 若元素不跳转任何其他页面（例如：纯声音开关、勾选协议、仅切换当前页局部状态、无对应落地页的占位按钮），其目标页面必须输出 null。
                4. 严格输出合法的 JSON 格式对象，示例：
                {
                  "40584": "兑换记录",
                  "40588": "探险与属性说明弹窗@3x",
                  "40593": "背包@3x",
                  "40585": null
                }
                不得输出 Markdown 代码块外任何无关文字。
                """;

        String response;
        AiClient.setUsageLabel("项目" + projectId + "拓扑智能连线");
        try {
            response = aiClient.generateText(systemPrompt, sbPrompt.toString());
        } catch (Exception e) {
            log.error("[AI拓扑连线] 调用大模型失败: {}", e.getMessage());
            return 0;
        } finally {
            AiClient.setUsageLabel(null);
        }

        if (response == null || response.isBlank()) {
            return 0;
        }

        return applyAiBatchResult(response, batch, allPages);
    }

    private int applyAiBatchResult(String rawJson, List<CandidateInfo> batch, List<Page> allPages) {
        String clean = rawJson.trim();
        if (clean.startsWith("```json")) {
            clean = clean.substring(7);
        } else if (clean.startsWith("```")) {
            clean = clean.substring(3);
        }
        if (clean.endsWith("```")) {
            clean = clean.substring(0, clean.length() - 3);
        }
        clean = clean.trim();

        Map<String, Object> map;
        try {
            map = objectMapper.readValue(clean, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("[AI拓扑连线] 解析大模型返回 JSON 失败: {}, 原始内容: {}", e.getMessage(), clean);
            return 0;
        }

        Map<Long, CandidateInfo> candidateMap = batch.stream()
                .collect(Collectors.toMap(c -> c.element().getId(), c -> c));

        int wired = 0;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String idStr = entry.getKey();
            Object targetVal = entry.getValue();
            if (targetVal == null) continue;
            String targetName = targetVal.toString().trim();
            if (targetName.isBlank() || "null".equalsIgnoreCase(targetName)) continue;

            Long elId;
            try {
                elId = Long.parseLong(idStr);
            } catch (Exception e) {
                continue;
            }

            CandidateInfo cand = candidateMap.get(elId);
            if (cand == null) continue;

            Page targetPage = matchTargetPage(targetName, allPages, cand.page().getId());
            if (targetPage == null) {
                log.info("[AI拓扑连线] 元素 {} (文案: '{}') 模型推导目标 '{}' 不在项目页面中，已安全过滤",
                        elId, cand.label(), targetName);
                continue;
            }

            if (targetPage.getId().equals(cand.page().getId())) {
                continue; // 忽略自跳转
            }

            String action = isModalPage(targetPage) ? "popup" : "navigate";
            insertOrUpdateInteraction(elId, action, targetPage.getId(), "ai_inferred");
            wired++;
            log.info("[AI拓扑连线] 成功建立语义连线: 页面「{}」元素[{} - {}] -> 页面「{}」({})",
                    cand.page().getName(), elId, cand.label(), targetPage.getName(), action);
        }

        return wired;
    }

    private Page matchTargetPage(String targetName, List<Page> allPages, Long selfPageId) {
        if (targetName == null || targetName.isBlank()) return null;
        String cleanTarget = strip(targetName);

        // 1. 精确原名匹配
        for (Page p : allPages) {
            if (p.getId().equals(selfPageId)) continue;
            if (targetName.equals(p.getName())) return p;
        }

        // 2. 去符号及小写匹配
        for (Page p : allPages) {
            if (p.getId().equals(selfPageId)) continue;
            if (cleanTarget.equals(strip(p.getName()))) return p;
        }

        // 3. 稳健包含匹配（字数 >= 3）
        return matchByContainment(targetName, allPages, selfPageId, 3, true);
    }

    /**
     * 左上角小图标，或文案里就是返回/关闭，标成返回。
     */
    private boolean isBackButton(Element el, double x, double y, double w, double h, String label) {
        String text = label == null ? "" : label.trim();
        boolean named = text.contains("返回") || text.equals("<") || text.contains("后退") || text.equalsIgnoreCase("back")
                || (text.contains("关闭") && text.length() <= 6);
        String type = el.getType() == null ? "" : el.getType();
        if ("button".equals(type) && named) return true;
        if (y > 100 || x > 80) return false;
        if (named) return true;
        return ("icon".equals(type) || "button".equals(type)) && x <= 48 && y <= 72 && w <= 52 && h <= 52;
    }

    /**
     * 判断是否为弹窗/浮层类页面（与 TemplateHtmlRenderer.isModalPage 保持同步）
     */
    private boolean isModalPage(Page p) {
        if (p.getBackgroundImage() != null && p.getBackgroundImage().contains("背包@3x.png") && !p.getBackgroundImage().contains("背包@3x-2.png")) {
            return false; // 背包抽屉页是全尺寸主展示页，绝不能误判为模态弹窗
        }
        String name = p.getName() == null ? "" : p.getName();
        return name.contains("弹窗") || name.contains("结果") || name.contains("提示") || name.contains("遮罩")
                || name.contains("到期") || name.contains("确认") || name.contains("购买") || name.contains("奖励")
                || name.contains("说明") || name.contains("混搭") || name.contains("升级") || name.contains("每日")
                || name.contains("提醒") || name.contains("骨架");
    }
}
