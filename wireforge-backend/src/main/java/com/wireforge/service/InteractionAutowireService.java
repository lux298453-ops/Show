package com.wireforge.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
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

        // 1. 寻找核心 Tab 对应的根页面候选
        Map<String, Page> tabRoots = findTabRootPages(pages);
        log.info("[交互布线] 项目 {} 识别到的 Tab 根页面映射: {}", projectId,
                tabRoots.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getName())));

        int wiredCount = 0;

        for (Page page : pages) {
            List<Element> els = elementsByPage.getOrDefault(page.getId(), Collections.emptyList());
            if (els.isEmpty()) continue;

            // 获取该页现有交互，避免覆盖用户自定义或已有效配置的连线
            List<Long> elIds = els.stream().map(Element::getId).toList();
            List<Interaction> existingInters = elIds.isEmpty() ? Collections.emptyList() :
                    interactionMapper.selectList(Wrappers.<Interaction>lambdaQuery().in(Interaction::getElementId, elIds));
            Map<Long, List<Interaction>> interMap = existingInters.stream()
                    .collect(Collectors.groupingBy(Interaction::getElementId));

            for (Element el : els) {
                List<Interaction> curInters = interMap.getOrDefault(el.getId(), Collections.emptyList());
                boolean hasBack = curInters.stream().anyMatch(i -> "back".equals(i.getActionType()));
                if (hasBack) {
                    continue; // 返回按钮不覆盖
                }
                // 清理"开始探险"游戏操作按钮被错误绑定到"探险与属性说明弹窗"的历史连线
                if (el.getLabel() != null && el.getLabel().contains("开始探险")) {
                    for (Interaction i : curInters) {
                        if (i.getTargetPageId() != null && pageMap.containsKey(i.getTargetPageId())) {
                            Page target = pageMap.get(i.getTargetPageId());
                            if (target.getName() != null && target.getName().contains("说明")) {
                                interactionMapper.deleteById(i.getId());
                                wiredCount++;
                            }
                        }
                    }
                }
                // 清理扭蛋抽奖页"规则说明"被错误绑定到"探险与属性说明弹窗"的历史连线
                if (el.getLabel() != null && el.getLabel().contains("规则")) {
                    for (Interaction i : curInters) {
                        if (i.getTargetPageId() != null && pageMap.containsKey(i.getTargetPageId())) {
                            Page target = pageMap.get(i.getTargetPageId());
                            if (target.getName() != null && target.getName().contains("探险与属性说明")) {
                                interactionMapper.deleteById(i.getId());
                                wiredCount++;
                            }
                        }
                    }
                }
                curInters = interactionMapper.selectList(Wrappers.<Interaction>lambdaQuery().eq(Interaction::getElementId, el.getId()));

                // 检查是否有 navigate 类型指向弹窗页的交互，需要升级为 modal
                boolean hasNavigateToModal = curInters.stream().anyMatch(i ->
                        "navigate".equals(i.getActionType()) && i.getTargetPageId() != null
                                && pageMap.containsKey(i.getTargetPageId())
                                && isModalPage(pageMap.get(i.getTargetPageId())));
                if (hasNavigateToModal) {
                    // 升级：将 navigate 改为 modal
                    for (Interaction i : curInters) {
                        if ("navigate".equals(i.getActionType()) && i.getTargetPageId() != null
                                && pageMap.containsKey(i.getTargetPageId())
                                && isModalPage(pageMap.get(i.getTargetPageId()))) {
                            interactionMapper.update(null, Wrappers.<Interaction>lambdaUpdate()
                                    .eq(Interaction::getId, i.getId())
                                    .set(Interaction::getActionType, "modal"));
                            wiredCount++;
                        }
                    }
                    continue;
                }
                boolean hasTarget = curInters.stream().anyMatch(i -> i.getTargetPageId() != null);
                if (hasTarget) {
                    continue; // 已有明确跳转关系且目标不是弹窗，不覆盖
                }

                String label = el.getLabel() == null ? "" : el.getLabel().trim();
                String type = el.getType() == null ? "" : el.getType().toLowerCase();
                double y = el.getPositionY() == null ? 0 : el.getPositionY();
                double x = el.getPositionX() == null ? 0 : el.getPositionX();
                double w = el.getWidth() == null ? 0 : el.getWidth();
                double h = el.getHeight() == null ? 0 : el.getHeight();

                // ===== 策略 1: 顶部左侧返回按钮自动绑定 action="back" =====
                if (isBackButton(el, x, y, w, h, label)) {
                    createOrUpdateInteraction(el.getId(), "click", "back", null, null);
                    wiredCount++;
                    continue;
                }

                // ===== 策略 2: 底部 Tabbar 广播矩阵对齐 =====
                if (y >= 680 && (label.length() <= 6 || "icon".equals(type) || "text".equals(type))) {
                    Page targetTab = matchTabRoot(label, tabRoots);
                    if (targetTab != null && !targetTab.getId().equals(page.getId())) {
                        createOrUpdateInteraction(el.getId(), "click", "navigate", targetTab.getId(), null);
                        wiredCount++;
                        continue;
                    }
                }

                // ===== 策略 2.5: 扭蛋抽奖页出货槽点击唤起抽奖结果弹窗 =====
                if ((label.contains("出货") || (y >= 550 && y <= 600 && w >= 150 && page.getName().contains("扭蛋")))) {
                    Page resultModal = pages.stream().filter(p -> p.getName() != null && (p.getName().contains("抽奖结果") || p.getName().contains("结果"))).findFirst().orElse(null);
                    if (resultModal != null) {
                        createOrUpdateInteraction(el.getId(), "click", "modal", resultModal.getId(), null);
                        wiredCount++;
                        continue;
                    }
                }

                // ===== 策略 3: 功能卡片/按钮/入口模糊匹配目标页面 =====
                if (!"navbar".equals(type) && !"tabs".equals(type) && ("button".equals(type) || "icon".equals(type) || "container".equals(type) || label.length() >= 2)) {
                    Page targetPage = matchTargetPageByLabel(label, pages, page.getId());
                    if (targetPage != null) {
                        String action = isModalPage(targetPage) ? "modal" : "navigate";
                        createOrUpdateInteraction(el.getId(), "click", action, targetPage.getId(), null);
                        wiredCount++;
                    }
                }
            }
        }

        log.info("[交互布线] 项目 {} 交互自动对齐完成，共自动建立/更新 {} 条交互连线", projectId, wiredCount);
        return wiredCount;
    }

    /**
     * 判断是否为返回按钮（位于左上角、文案包含返回/<、或者左侧狭窄返回图标）
     */
    private boolean isBackButton(Element el, double x, double y, double w, double h, String label) {
        if (y > 90 || x > 75) return false;
        if (label.contains("返回") || label.equals("<") || label.contains("后退") || label.equalsIgnoreCase("back")) {
            return true;
        }
        // 位于左上角的小图标/小区域，通常为返回导航
        return ("icon".equals(el.getType()) || "button".equals(el.getType())) && x <= 45 && y <= 65 && w <= 48 && h <= 48;
    }

    /**
     * 发现项目中承载各个核心 Tab（家园、商城、收集、我的、装扮、扭蛋）的主页面
     */
    private Map<String, Page> findTabRootPages(List<Page> pages) {
        Map<String, Page> map = new HashMap<>();
        // 优先精确匹配最核心的根页面
        for (Page p : pages) {
            String name = p.getName() == null ? "" : p.getName();
            if (isModalPage(p)) continue;
            if (name.equals("宠物家园首页") || name.equals("家园") || (name.contains("家园") && name.contains("首页"))) {
                map.putIfAbsent("家园", p);
            } else if (name.equals("商城") || (name.contains("商城") && !name.contains("详情"))) {
                map.putIfAbsent("商城", p);
            } else if (name.equals("收集_次态") || name.equals("收集")) {
                map.putIfAbsent("收集", p);
            } else if (name.equals("我的（已登录）") || name.equals("我的")) {
                map.putIfAbsent("我的", p);
            } else if (name.equals("扭蛋抽奖页") || name.equals("扭蛋")) {
                map.putIfAbsent("扭蛋", p);
            } else if (name.equals("角色待机详情页") || name.equals("其他装扮")) {
                map.putIfAbsent("装扮", p);
            }
        }
        // 兜底通用项目匹配
        String[] keywords = {"家园", "商城", "商店", "收集", "我的", "扭蛋", "抽奖", "装扮", "待机"};
        for (String kw : keywords) {
            String normKw = kw.equals("商店") ? "商城" : (kw.equals("抽奖") ? "扭蛋" : kw);
            if (map.containsKey(normKw)) continue;
            for (Page p : pages) {
                String name = p.getName() == null ? "" : p.getName();
                if (name.contains(kw) && !isModalPage(p) && !name.contains("展开") && !name.contains("设置")) {
                    map.putIfAbsent(normKw, p);
                    break;
                }
            }
        }
        return map;
    }

    /**
     * 匹配底部 Tab 对应的根页面
     */
    private Page matchTabRoot(String label, Map<String, Page> tabRoots) {
        if (label == null || label.isBlank()) return null;
        for (Map.Entry<String, Page> entry : tabRoots.entrySet()) {
            if (label.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * 基于文案语义与关键词匹配目标页面
     */
    private Page matchTargetPageByLabel(String label, List<Page> allPages, Long curPageId) {
        if (label == null || label.trim().length() < 2) return null;
        String clean = label.replaceAll("[\\s\\p{P}\\p{S}]+", "").toLowerCase();

        for (Page p : allPages) {
            if (p.getId().equals(curPageId)) continue;
            String pName = p.getName() == null ? "" : p.getName().replaceAll("[\\s\\p{P}\\p{S}]+", "").toLowerCase();

            // 1. 完全包含或高度重合
            if (pName.contains(clean) && clean.length() >= 3) {
                return p;
            }
            if (clean.contains(pName) && pName.length() >= 3) {
                return p;
            }

            // 2. 核心特征词匹配
            if ((clean.contains("兑换") || clean.contains("商店")) && pName.contains("商店")) {
                return p;
            }
            if ((clean.contains("抽奖") || clean.contains("扭蛋")) && pName.contains("扭蛋")) {
                return p;
            }
            if ((clean.contains("装扮") || clean.contains("形象")) && (pName.contains("装扮") || pName.contains("待机") || pName.contains("预览"))) {
                return p;
            }
            if (clean.contains("壁纸") && pName.contains("壁纸")) {
                return p;
            }
            if (clean.contains("家园") && pName.contains("家园")) {
                return p;
            }
            if ((clean.contains("中奖") || clean.contains("结果") || clean.contains("出货") || clean.contains("播报")) && (pName.contains("结果") || pName.contains("中奖"))) {
                return p;
            }
            if (clean.contains("背包") && (pName.contains("背包") && !pName.contains("确认") && !pName.contains("购买"))) {
                return p;
            }
            if ((clean.contains("更新") || clean.contains("升级")) && pName.contains("更新")) {
                return p;
            }
            // 弹窗类精确匹配：按钮文案含关键词 → 弹窗页名含同类词
            if (clean.contains("购买") && (pName.contains("购买") || pName.contains("确认"))) {
                return p;
            }
            if ((clean.contains("属性") || clean.contains("说明")) && (pName.contains("属性") || pName.contains("说明"))) {
                return p;
            }
            // "开始探险"是游戏核心玩法操作按钮，不是打开说明书；仅当页面是真正独立的"探险"主页面时才匹配，严禁误连"探险与属性说明"弹窗
            if (clean.contains("探险") && !clean.contains("开始") && pName.contains("探险") && !pName.contains("说明")) {
                return p;
            }
            // "规则"或"规则说明"仅匹配真正包含"规则"的页面，严禁误连"探险与属性说明"
            if (clean.contains("规则") && pName.contains("规则") && !pName.contains("探险") && !pName.contains("属性")) {
                return p;
            }
        }
        return null;
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

    private void createOrUpdateInteraction(Long elementId, String triggerType, String actionType, Long targetPageId, String params) {
        Interaction it = new Interaction();
        it.setElementId(elementId);
        it.setTriggerType(triggerType);
        it.setActionType(actionType);
        it.setTargetPageId(targetPageId);
        it.setParams(params);
        interactionMapper.insert(it);
    }
}
