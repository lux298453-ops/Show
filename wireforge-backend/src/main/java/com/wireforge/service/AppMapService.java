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
 *  - 底栏带 = 画布底部 20% 区域；
 *  - 带内 icon 元素：有 navigate 交互 → 项（目标=交互目标页）；无任何交互 → 自指项（目标=本页，激活态）；
 *  - 项文案 = 图标自身 label，或其正下方 30px 内的相邻 text 文案；
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
    private record Cand(long target, String label, double x, double y, double w, double h, long pageId) {}

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
        Map<Long, List<Cand>> candsByPage = new LinkedHashMap<>();
        for (Page p : pages) {
            candsByPage.put(p.getId(), collectBottomItems(p));
        }

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
        // 最多 5 项（移动端底栏上限），按中位 x 排序
        norms.sort(Comparator.comparingDouble(Norm::medX));
        if (norms.size() > 5) {
            norms = new ArrayList<>(norms.subList(0, 5));
        }

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
    private List<Cand> collectBottomItems(Page p) {
        List<Cand> out = new ArrayList<>();
        double canvasH = p.getCanvasHeight() == null ? 812 : p.getCanvasHeight();
        double band = canvasH * 0.80;
        List<Element> els = elementMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<Element>lambdaQuery()
                        .eq(Element::getPageId, p.getId()));
        if (els.isEmpty()) {
            return out;
        }
        List<Long> ids = els.stream().map(Element::getId).toList();
        Map<Long, Long> navTarget = new LinkedHashMap<>();
        for (Interaction it : interactionMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<Interaction>lambdaQuery()
                        .in(Interaction::getElementId, ids)
                        .eq(Interaction::getActionType, "navigate")
                        .isNotNull(Interaction::getTargetPageId))) {
            navTarget.put(it.getElementId(), it.getTargetPageId());
        }

        for (Element e : els) {
            if (!"icon".equals(e.getType()) && !"text".equals(e.getType())) continue;
            double y = nz(e.getPositionY());
            if (y < band) continue;
            Long target = navTarget.get(e.getId());
            if (target == null && !"icon".equals(e.getType())) continue;
            if (target == null) {
                // 底栏带内无交互的图标 = 自指项（本页激活 Tab）
                target = p.getId();
            }
            out.add(new Cand(target, resolveLabel(e, els), nz(e.getPositionX()), y,
                    nz(e.getWidth()), nz(e.getHeight()), p.getId()));
        }
        return out;
    }

    /** 项文案：图标自身 label，或正下方 30px 内的相邻 text 文案（底栏常见"图上字下"结构） */
    private String resolveLabel(Element icon, List<Element> all) {
        String own = icon.getLabel() == null ? "" : icon.getLabel().trim();
        double cx = nz(icon.getPositionX()) + nz(icon.getWidth()) / 2;
        double bottom = nz(icon.getPositionY()) + nz(icon.getHeight());
        for (Element t : all) {
            if (!"text".equals(t.getType()) || t.getId().equals(icon.getId())) continue;
            double tcx = nz(t.getPositionX()) + nz(t.getWidth()) / 2;
            double tcy = nz(t.getPositionY()) + nz(t.getHeight()) / 2;
            if (Math.abs(tcx - cx) <= Math.max(24, nz(icon.getWidth()))
                    && tcy >= bottom - 6 && tcy <= bottom + 30) {
                String lbl = t.getLabel() == null ? "" : t.getLabel().trim();
                if (!lbl.isBlank() && lbl.length() <= 6) {
                    return lbl;
                }
            }
        }
        return own;
    }

    private static double nz(Double v) {
        return v == null ? 0 : v;
    }
}
