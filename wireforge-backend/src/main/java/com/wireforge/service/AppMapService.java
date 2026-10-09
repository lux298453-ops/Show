package com.wireforge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wireforge.entity.Element;
import com.wireforge.entity.Interaction;
import com.wireforge.entity.Page;
import com.wireforge.entity.Project;
import com.wireforge.mapper.ElementMapper;
import com.wireforge.mapper.InteractionMapper;
import com.wireforge.mapper.PageMapper;
import com.wireforge.mapper.ProjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * App Map 构建器：从各页"底部导航区的跳转交互"中投票选出规范底部 Tab 栏，
 * 写入 project.app_map。渲染器据此在所有 Tab 页注入同一份底栏，保证每页完全一致。
 * 纯确定性算法，不调用 AI。
 *
 * 识别规则：
 *  - 与导航预检共用图标、文字和条带分组；缺失交互不代表自指；
 *  - 仅使用现有有效跳转，或名称唯一匹配本页的自指证据；
 *  - 同组指纹的导航才能参与共识，冲突项不自动合并；
 *  - 跨页按目标页归并，文案取多数票；贡献 ≥2 个规范目标的页视为 Tab 页；
 *  - bar 盒 = 各 Tab 页最小项 y - 16 起，到这些页画布高度中位数止。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppMapService {

    private final ProjectMapper projectMapper;
    private final PageMapper pageMapper;
    private final ElementMapper elementMapper;
    private final InteractionMapper interactionMapper;
    private final ObjectMapper objectMapper;

    /** 候选项（单页单图标） */
    private record Cand(long target, String label, double x, double y, double w, double h, long pageId, String family, String item) {}

    /**
     * 构建并保存 app_map。返回解析后的 app_map 节点；无法识别出底栏（<2 项）时返回 null。
     */
    public ObjectNode buildAppMap(long projectId) {
        List<Page> pages = pageMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<Page>lambdaQuery()
                        .eq(Page::getProjectId, projectId)
                        .orderByAsc(Page::getSortOrder));
        if (pages.size() < 2) {
            return null;
        }

        // 1) 收集每页的底栏候选项
        List<Element> allElements = elementMapper.selectList(com.baomidou.mybatisplus.core.toolkit.Wrappers.<Element>lambdaQuery()
                .in(Element::getPageId,pages.stream().map(Page::getId).toList()));
        if (allElements.isEmpty()) return null;
        List<Interaction> allLines = interactionMapper.selectList(com.baomidou.mybatisplus.core.toolkit.Wrappers.<Interaction>lambdaQuery()
                .in(Interaction::getElementId,allElements.stream().map(Element::getId).toList()));
        Map<Long, List<Cand>> candsByPage = new LinkedHashMap<>();
        for (Page p : pages) {
            candsByPage.put(p.getId(), collectBottomItems(p, pages, allElements, allLines));
        }
        // The current app_map format describes one shared bar. Never combine different families.
        Map<String, java.util.Set<Long>> families = new LinkedHashMap<>();
        Map<String, java.util.Set<Long>> destinations = new LinkedHashMap<>();
        for (List<Cand> candidates : candsByPage.values()) for (Cand c : candidates) {
            families.computeIfAbsent(c.family(), k -> new java.util.HashSet<>()).add(c.pageId());
            destinations.computeIfAbsent(c.family() + ":" + c.item(), k -> new java.util.HashSet<>()).add(c.target());
        }
        List<String> eligible = families.keySet().stream().filter(f -> families.get(f).size() >= 2
                && destinations.entrySet().stream().noneMatch(e -> e.getKey().startsWith(f + ":") && e.getValue().size() > 1))
                .sorted(Comparator.comparingInt((String f) -> families.get(f).size()).reversed()).toList();
        if (eligible.isEmpty() || (eligible.size() > 1 && families.get(eligible.get(0)).size() == families.get(eligible.get(1)).size())) return null;
        String family = eligible.get(0);
        candsByPage.replaceAll((id, candidates) -> candidates.stream().filter(c -> family.equals(c.family())).toList());

        // 2) 共识投票：只有出现在 ≥2 个页面底栏带里的目标才是 Tab 项。
        //    单页独有的底部图标多为"页面级功能工具条"（锁屏/充电/自定义…），必须排除。
        Map<Long, java.util.Set<Long>> targetPages = new LinkedHashMap<>();
        for (Page p : pages) {
            for (Cand c : candsByPage.get(p.getId())) {
                targetPages.computeIfAbsent(c.target(), k -> new java.util.HashSet<>()).add(p.getId());
            }
        }
        Map<Long, List<Cand>> consensus = new LinkedHashMap<>();
        for (var entry : targetPages.entrySet()) {
            if (entry.getValue().size() >= 2) {
                consensus.put(entry.getKey(), new ArrayList<>());
            }
        }
        if (consensus.size() < 2) {
            log.info("项目 {} 底栏共识目标不足 2 个（App 级 Tab 栏不明确），跳过", projectId);
            return null;
        }

        // 3) Tab 页 = 底栏带内含 ≥2 个共识目标的页
        List<Long> tabPages = new ArrayList<>();
        for (Page p : pages) {
            long hit = candsByPage.get(p.getId()).stream()
                    .map(Cand::target).distinct().filter(consensus::containsKey).count();
            if (hit >= 2) {
                tabPages.add(p.getId());
            }
        }
        if (tabPages.isEmpty()) {
            log.info("项目 {} 无页面满足 Tab 栏条件，跳过", projectId);
            return null;
        }

        // 4) Tab 铁证：目标页自己的底栏里也有指向自己的项（激活态自指）。
        //    "自定义/收集"这类跨页共享的功能按钮永远不会有自指 → 借此与真 Tab 区分。
        Map<Long, List<Cand>> byTargetAll = new LinkedHashMap<>();
        for (Page p : pages) {
            for (Cand c : candsByPage.get(p.getId())) {
                if (consensus.containsKey(c.target())) {
                    byTargetAll.computeIfAbsent(c.target(), k -> new ArrayList<>()).add(c);
                }
            }
        }
        java.util.Set<Long> tabTargets = new java.util.LinkedHashSet<>();
        for (var entry : byTargetAll.entrySet()) {
            for (Cand c : entry.getValue()) {
                if (c.pageId() == entry.getKey()) {
                    tabTargets.add(entry.getKey());
                    break;
                }
            }
        }
        if (tabTargets.size() < 2) {
            // 兜底：自指证据不足时退回纯共识集（避免整个 App Map 丢失）
            tabTargets.addAll(consensus.keySet());
        }
        Map<Long, List<Cand>> byTarget = new LinkedHashMap<>();
        for (var entry : byTargetAll.entrySet()) {
            if (tabTargets.contains(entry.getKey())) {
                byTarget.put(entry.getKey(), entry.getValue());
            }
        }

        // 5) 规范项：文案多数票 + 中位 x；Tab 页 = 底栏含 ≥2 个规范目标的页
        record Norm(long target, String label, double medX) {}
        List<Norm> norms = new ArrayList<>();
        for (var entry : byTarget.entrySet()) {
            List<Cand> cs = entry.getValue();
            Map<String, Integer> votes = new LinkedHashMap<>();
            List<Double> xs = new ArrayList<>();
            for (Cand c : cs) {
                if (!c.label().isBlank()) {
                    votes.merge(c.label(), 1, Integer::sum);
                }
                xs.add(c.x() + c.w() / 2);
            }
            String label = votes.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse("");
            xs.sort(Double::compare);
            norms.add(new Norm(entry.getKey(), label, xs.get(xs.size() / 2)));
        }
        tabPages.clear();
        for (Page p : pages) {
            long hit = candsByPage.get(p.getId()).stream()
                    .map(Cand::target).distinct().filter(byTarget::containsKey).count();
            if (hit >= 2) {
                tabPages.add(p.getId());
            }
        }
        if (norms.size() < 2 || tabPages.isEmpty()) {
            log.info("项目 {} 规范 Tab 项不足（{} 项 / {} 页），跳过", projectId, norms.size(), tabPages.size());
            return null;
        }
        // 保留实际导航项数量，按中位 x 排序。
        norms.sort(Comparator.comparingDouble(Norm::medX));

        // 5) bar 盒与输出 JSON
        double minY = Double.MAX_VALUE;
        List<Double> heights = new ArrayList<>();
        for (Page p : pages) {
            if (!tabPages.contains(p.getId())) continue;
            if (p.getCanvasHeight() != null) heights.add(p.getCanvasHeight().doubleValue());
            for (Cand c : candsByPage.get(p.getId())) {
                if (consensus.containsKey(c.target()) || c.target() == p.getId()) {
                    minY = Math.min(minY, c.y());
                }
            }
        }
        heights.sort(Double::compare);
        double medH = heights.isEmpty() ? 812 : heights.get(heights.size() / 2);
        double barY = (minY == Double.MAX_VALUE ? medH * 0.92 : minY) - 16;
        barY = Math.max(medH * 0.80, barY);
        double barH = Math.max(48, medH - barY);

        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode tb = root.putObject("tab_bar");
        ArrayNode pagesArr = tb.putArray("pages");
        tabPages.forEach(pagesArr::add);
        ObjectNode bar = tb.putObject("bar");
        bar.put("x", 0);
        bar.put("y", Math.round(barY));
        bar.put("w", 375);
        bar.put("h", Math.round(barH));
        ArrayNode items = tb.putArray("items");
        for (Norm n : norms) {
            ObjectNode it = items.addObject();
            it.put("target", n.target());
            it.put("label", n.label());
        }

        Project project = projectMapper.selectById(projectId);
        if (project != null) {
            project.setAppMap(root.toString());
            projectMapper.updateById(project);
        }
        log.info("项目 {} App Map 构建完成：{} 个 Tab 项，覆盖 {} 页", projectId, norms.size(), tabPages.size());
        return root;
    }

    /** 收集一页底栏带内的候选 Tab 项 */
    private List<Cand> collectBottomItems(Page p, List<Page> pages, List<Element> allElements, List<Interaction> lines) {
        List<Cand> out = new ArrayList<>();
        List<Element> els = allElements.stream().filter(e -> p.getId().equals(e.getPageId())).toList();
        if (els.isEmpty()) {
            return out;
        }
        for (var entry : NavigationPlanner.detect(List.of(p), els)) {
            if (!"bottom".equals(entry.region()) || "local".equals(NavigationPlanner.hint(entry.anchor(),"kind")) || NavigationPlanner.localLabel(entry.label())) continue;
            List<Interaction> current = lines.stream().filter(i -> entry.ids().contains(i.getElementId())).toList();
            if (current.stream().anyMatch(i -> !"navigate".equals(i.getActionType()))) continue;
            List<Long> targets = current.stream().map(Interaction::getTargetPageId).filter(java.util.Objects::nonNull)
                    .filter(id -> pages.stream().anyMatch(page -> page.getId().equals(id))).distinct().toList();
            Long target = targets.size() == 1 ? targets.get(0) : null;
            if (targets.isEmpty() && current.isEmpty()) {
                List<Page> exact = pages.stream().filter(page -> AutowirePlanner.normalize(page.getName()).equals(AutowirePlanner.normalize(entry.label()))).toList();
                if (exact.size() == 1 && exact.get(0).getId().equals(p.getId())) target = p.getId();
            }
            if (target == null) continue;
            out.add(new Cand(target,entry.label(),entry.x(),entry.y(),entry.width(),entry.height(),p.getId(),entry.familyKey(),entry.itemKey()));
        }
        return out;
    }

}
