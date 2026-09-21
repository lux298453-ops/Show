package com.wireforge.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wireforge.entity.Annotation;
import com.wireforge.entity.Element;
import com.wireforge.entity.Interaction;
import com.wireforge.entity.Page;
import com.wireforge.entity.Project;
import com.wireforge.mapper.AnnotationMapper;
import com.wireforge.mapper.ElementMapper;
import com.wireforge.mapper.InteractionMapper;
import com.wireforge.mapper.PageMapper;
import com.wireforge.mapper.ProjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectMapper projectMapper;
    private final PageMapper pageMapper;
    private final ElementMapper elementMapper;
    private final InteractionMapper interactionMapper;
    private final AnnotationMapper annotationMapper;
    private final AnalyzeService analyzeService;
    private final AppMapService appMapService;
    private final InteractionAutowireService interactionAutowireService;
    private final com.wireforge.ai.HtmlRenderer htmlRenderer;

    /** 分析完成后是否自动跑 Playwright 交互验证（仅记录报告，不阻断） */
    @Value("${wireforge.verify:true}")
    private boolean verifyEnabled;

    @Value("${wireforge.designs-dir}")
    private String designsDir;

    /**
     * 交互验证闭环：用无头 Chromium 逐页真实点击所有交互元素并断言行为，失败明细写日志。
     * 返回失败条目（空 = 全部通过）。
     */
    public List<com.wireforge.ai.HtmlRenderer.CheckResult> verifyProject(long projectId) {
        List<Page> pages = pageMapper.selectList(
                Wrappers.<Page>lambdaQuery().eq(Page::getProjectId, projectId));
        List<com.wireforge.ai.HtmlRenderer.CheckResult> failures = new ArrayList<>();
        int total = 0;
        for (Page p : pages) {
            if (p.getHtmlContent() == null || p.getHtmlContent().length() < 100) continue;
            try {
                List<com.wireforge.ai.HtmlRenderer.CheckResult> rs =
                        htmlRenderer.verifyInteractions(p.getName(), p.getHtmlContent());
                for (var r : rs) {
                    total++;
                    if (!r.ok()) {
                        failures.add(r);
                        log.warn("[交互验证] {} {} -> {} 失败: {}", p.getName(), r.kind(), r.target(), r.note());
                    }
                }
            } catch (Exception e) {
                log.warn("[交互验证] 页面 [{}] 执行失败: {}", p.getName(), e.getMessage());
            }
        }
        log.info("[交互验证] 项目 {} 完成：{} 项检查，{} 项失败", projectId, total, failures.size());
        return failures;
    }

    /**
     * 重建 App Map 并重渲染项目全部页面（不重跑 AI）。返回 Tab 项数。
     */
    public int rebuildAppMapAndRender(long projectId) {
        var appMap = appMapService.buildAppMap(projectId);
        List<Page> pages = pageMapper.selectList(
                Wrappers.<Page>lambdaQuery().eq(Page::getProjectId, projectId));
        int n = 0;
        for (Page p : pages) {
            try {
                String html = analyzeService.renderTemplateHtml(p);
                pageMapper.update(null,
                        Wrappers.<Page>lambdaUpdate()
                                .eq(Page::getId, p.getId())
                                .set(Page::getHtmlContent, html));
                n++;
            } catch (Exception e) {
                log.warn("页面 [{}] 重渲染失败", p.getName(), e);
            }
        }
        List<com.wireforge.ai.HtmlRenderer.CheckResult> failures =
                verifyEnabled ? verifyProject(projectId) : List.of();
        log.info("项目 {} App Map 重建完成：appMap={}，重渲染 {} 页，验证失败 {} 项",
                projectId, appMap != null ? "已生成" : "未识别", n, failures.size());
        return n;
    }

    public List<Project> listProjects() {
        return projectMapper.selectList(
                Wrappers.<Project>lambdaQuery().orderByDesc(Project::getCreatedAt));
    }

    public Project getProject(Long id) {
        Project project = projectMapper.selectById(id);
        if (project == null) throw new IllegalStateException("项目不存在: " + id);
        return project;
    }

    @Transactional
    public Project createProject(String name, String description) {
        Project project = new Project();
        project.setName(name == null || name.isBlank() ? "未命名项目" : name.trim());
        project.setDescription(description);
        project.setCreatedAt(LocalDateTime.now());
        projectMapper.insert(project);
        return project;
    }

    /**
     * 删除项目及其全部子数据（页面 → 元素 → 交互 / 标注）。
     * schema 未设外键级联，这里按依赖顺序手动清理，避免留下孤儿数据。
     */
    @Transactional
    public void deleteProject(Long id) {
        getProject(id);

        List<Long> pageIds = pageMapper.selectList(
                        Wrappers.<Page>lambdaQuery().eq(Page::getProjectId, id).select(Page::getId))
                .stream().map(Page::getId).collect(Collectors.toList());

        if (!pageIds.isEmpty()) {
            List<Long> elementIds = elementMapper.selectList(
                            Wrappers.<Element>lambdaQuery().in(Element::getPageId, pageIds).select(Element::getId))
                    .stream().map(Element::getId).collect(Collectors.toList());

            if (!elementIds.isEmpty()) {
                interactionMapper.delete(
                        Wrappers.<Interaction>lambdaQuery().in(Interaction::getElementId, elementIds));
            }
            annotationMapper.delete(
                    Wrappers.<Annotation>lambdaQuery().in(Annotation::getPageId, pageIds));
            elementMapper.delete(
                    Wrappers.<Element>lambdaQuery().in(Element::getPageId, pageIds));
            pageMapper.delete(
                    Wrappers.<Page>lambdaQuery().eq(Page::getProjectId, id));
        }

        projectMapper.deleteById(id);
        log.info("项目已删除: id={}，共清理 {} 个页面及其子数据", id, pageIds.size());
    }


    /**
     * 扫描设计稿目录，为新增图片创建页面。
     */
    @Transactional
    public List<Page> scanDesigns(Long projectId) {
        getProject(projectId);
        List<Page> created = createPagesForNewImages(projectId);
        return created;
    }

    /**
     * 启动自动生成：把目录中未被任何项目引用的新设计稿追加到最近的项目并分析；
     * 没有任何项目时才新建"自动原型"项目。只分析新增页面，已有页面不受影响。
     */
    public Project autoGenerate() {
        Path dir = Paths.get(designsDir);
        if (!Files.isDirectory(dir)) {
            log.warn("设计稿目录不存在，跳过自动生成: {}", designsDir);
            return null;
        }

        List<Page> fresh = listNewImages(dir);
        if (fresh.isEmpty()) {
            log.info("设计稿目录无新增图片，跳过自动生成");
            return null;
        }

        Project project = projectMapper.selectList(
                        Wrappers.<Project>lambdaQuery().orderByDesc(Project::getCreatedAt).last("LIMIT 1"))
                .stream().findFirst().orElse(null);
        if (project == null) {
            project = new Project();
            project.setName("自动原型 " + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm")));
            project.setDescription("启动时自动扫描 " + designsDir + " 生成");
            project.setCreatedAt(LocalDateTime.now());
            projectMapper.insert(project);
            log.info("自动创建项目: {} ({} 张设计稿)", project.getName(), fresh.size());
        } else {
            log.info("检测到 {} 张新设计稿，追加到项目 [{}] (id={})", fresh.size(), project.getName(), project.getId());
        }

        int sortBase = pageMapper.selectCount(
                Wrappers.<Page>lambdaQuery().eq(Page::getProjectId, project.getId())).intValue();
        for (Page page : fresh) {
            page.setProjectId(project.getId());
            page.setSortOrder(sortBase + page.getSortOrder());
            pageMapper.insert(page);
        }

        try {
            analyzePages(project.getId(), fresh);
        } catch (Exception e) {
            log.error("自动分析失败: {}", e.getMessage(), e);
        }
        return project;
    }

    /** 找出目录中尚未被任何项目引用的图片（递归扫描分组子目录；assets/ 目录视为素材，跳过） */
    private List<Page> listNewImages(Path dir) {
        List<Page> allPages = pageMapper.selectList(null);
        java.util.Set<String> used = allPages.stream()
                .map(Page::getBackgroundImage)
                .collect(Collectors.toSet());

        List<Page> fresh = new ArrayList<>();
        int sortOrder = 0;
        List<Path> images = collectDesignImages(dir);
        for (Path image : images) {
            String abs = image.toAbsolutePath().normalize().toString();
            if (used.contains(abs)) continue;
            Page page = new Page();
            page.setName(pageNameFor(image, dir));
            page.setBackgroundImage(abs);
            applyCanvasSize(page, image);
            page.setSortOrder(sortOrder++);
            page.setAnalyzed(0);
            page.setCreatedAt(LocalDateTime.now());
            fresh.add(page);
        }
        return fresh;
    }

    /** 递归收集设计稿图片：排除任何层级的 assets/ 素材目录，按相对路径排序保证顺序稳定 */
    private List<Path> collectDesignImages(Path dir) {
        List<Path> result = new ArrayList<>();
        if (!Files.isDirectory(dir)) return result;
        try (var walk = Files.walk(dir, 3)) {
            walk.filter(p -> !p.equals(dir))
                    .filter(p -> {
                        for (Path part : p) {
                            if ("assets".equalsIgnoreCase(part.toString())) return false;
                        }
                        return true;
                    })
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String n = p.getFileName().toString().toLowerCase();
                        return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".webp");
                    })
                    .sorted(Comparator.comparing(p -> p.toAbsolutePath().normalize().toString()))
                    .forEach(result::add);
        } catch (IOException e) {
            throw new IllegalStateException("扫描设计稿目录失败: " + e.getMessage(), e);
        }
        return result;
    }

    /** 页面名：含分组的相对路径去扩展名（如「装扮修改/装扮首页」），便于识别页面归属 */
    private String pageNameFor(Path image, Path dir) {
        var rel = dir.relativize(image.toAbsolutePath().normalize());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < rel.getNameCount(); i++) {
            if (i > 0) sb.append('/');
            sb.append(fileNameWithoutExt(rel.getName(i).toString()));
        }
        return sb.toString();
    }

    /** 为项目创建新图片对应的页面（复用建页逻辑；递归分组子目录，跳过 assets/ 素材目录） */
    private List<Page> createPagesForNewImages(Long projectId) {
        Path dir = Paths.get(designsDir);
        if (!Files.isDirectory(dir)) {
            throw new IllegalStateException("设计稿目录不存在: " + designsDir);
        }

        List<Page> existing = pageMapper.selectList(
                Wrappers.<Page>lambdaQuery().eq(Page::getProjectId, projectId));
        Map<String, Long> pathByImage = new HashMap<>();
        for (Page p : existing) {
            pathByImage.put(p.getBackgroundImage(), p.getId());
        }

        List<Page> created = new ArrayList<>();
        int sortOrder = existing.size();
        for (Path image : collectDesignImages(dir)) {
            String abs = image.toAbsolutePath().normalize().toString();
            if (pathByImage.containsKey(abs)) continue;

            Page page = new Page();
            page.setProjectId(projectId);
            page.setName(pageNameFor(image, dir));
            page.setBackgroundImage(abs);
            applyCanvasSize(page, image);
            page.setSortOrder(sortOrder++);
            page.setAnalyzed(0);
            page.setCreatedAt(LocalDateTime.now());
            pageMapper.insert(page);
            created.add(page);
            log.info("扫描到新设计稿: {}", abs);
        }
        return created;
    }

    /** 正在分析中的项目（防止同项目并发分析互相锁等待） */
    private final java.util.Set<Long> analyzingProjects = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * 对项目中尚未生成线稿的页面执行 AI 识别（已有线稿的页面不重新生成，节省调用成本）。
     * 全部页面已生成时返回空列表。
     */
    public List<Map<String, Object>> analyzeProject(Long projectId) {
        getProject(projectId);
        List<Page> pending = pageMapper.selectList(
                Wrappers.<Page>lambdaQuery()
                        .eq(Page::getProjectId, projectId)
                        .and(w -> w.isNull(Page::getAnalyzed).or().eq(Page::getAnalyzed, 0))
                        .orderByAsc(Page::getSortOrder));
        if (pending.isEmpty()) {
            log.info("项目 {} 所有页面均已生成线稿，跳过分析", projectId);
            return new ArrayList<>();
        }
        return analyzePages(projectId, pending);
    }

    /**
     * 强制重新对指定单页执行 AI 深度识别（清空旧元素并重跑视觉大模型）
     */
    public List<Map<String, Object>> reanalyzePage(Long projectId, Long pageId) {
        getProject(projectId);
        Page page = pageMapper.selectById(pageId);
        if (page == null || !projectId.equals(page.getProjectId())) {
            throw new IllegalArgumentException("页面不存在: id=" + pageId);
        }
        return analyzePages(projectId, List.of(page));
    }

    /**
     * 强制重新对全项目所有页面执行 AI 深度识别
     */
    public List<Map<String, Object>> reanalyzeAll(Long projectId) {
        getProject(projectId);
        List<Page> all = pageMapper.selectList(
                Wrappers.<Page>lambdaQuery()
                        .eq(Page::getProjectId, projectId)
                        .orderByAsc(Page::getSortOrder));
        if (all.isEmpty()) {
            return Collections.emptyList();
        }
        return analyzePages(projectId, all);
    }

    /**
     * 对项目内指定页面执行 AI 识别（页面名列表与补链快照基于项目全部页面）。
     */
    private List<Map<String, Object>> analyzePages(Long projectId, List<Page> pagesToAnalyze) {
        if (!analyzingProjects.add(projectId)) {
            throw new IllegalStateException("该项目正在分析中，请等待当前分析完成");
        }
        try {
            List<Page> allPages = pageMapper.selectList(
                    Wrappers.<Page>lambdaQuery()
                            .eq(Page::getProjectId, projectId)
                            .orderByAsc(Page::getSortOrder));

            List<String> pageNames = analyzeService.buildPageNames(allPages);
            // 分析前的页面名快照：AI 的交互 target 引用原始名，页面改名后靠它补链
            Map<String, Long> originalNameToId = new HashMap<>();
            for (Page p : allPages) {
                originalNameToId.put(p.getName(), p.getId());
            }
            List<Map<String, Object>> results = new ArrayList<>();
            for (Page page : pagesToAnalyze) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("page_id", page.getId());
                item.put("page_name", page.getName());
                try {
                    int count = analyzeService.analyzePage(page, pageNames);
                    item.put("status", "ok");
                    item.put("elements", count);
                } catch (Exception e) {
                    log.error("页面 [{}] 分析失败: {}", page.getName(), e.getMessage());
                    item.put("status", "error");
                    item.put("error", e.getMessage());
                }
                results.add(item);
            }
            analyzeService.resolvePendingTargets(projectId, originalNameToId);
            // 变体页面公共骨架像素级对齐（吸附同源状态页面的导航、立绘框、底栏坐标）
            analyzeService.alignVariantPages(projectId);
            // 自动布线全项目交互（为弹窗唤起、Tab 切换、返回及页面跳转建立稳定连线）
            interactionAutowireService.autowireProjectInteractions(projectId);
            // 交互补链完成后再构建 App Map（共享底栏依赖稳定的 target_page_id），
            // 然后统一重渲染一遍，保证 data-nav / data-modal / 共享底栏都按最终数据生成。
            appMapService.buildAppMap(projectId);
            for (Page p : allPages) {
                try {
                    String html = analyzeService.renderTemplateHtml(p);
                    pageMapper.update(null,
                            Wrappers.<Page>lambdaUpdate()
                                    .eq(Page::getId, p.getId())
                                    .set(Page::getHtmlContent, html));
                } catch (Exception e) {
                    log.warn("页面 [{}] 补链后重渲染失败: {}", p.getName(), e.getMessage());
                }
            }
            verifyProject(projectId);
            return results;
        } finally {
            analyzingProjects.remove(projectId);
        }
    }

    /**
     * 组装完整原型 JSON（前端渲染所需全部数据）。
     */
    public Map<String, Object> getPrototype(Long projectId) {
        Project project = getProject(projectId);
        List<Page> pages = pageMapper.selectList(
                Wrappers.<Page>lambdaQuery()
                        .eq(Page::getProjectId, projectId)
                        .orderByAsc(Page::getSortOrder));

        List<Map<String, Object>> pageList = new ArrayList<>();
        for (Page page : pages) {
            pageList.add(buildPageVo(page));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("project", project);
        result.put("pages", pageList);
        return result;
    }

    private Map<String, Object> buildPageVo(Page page) {
        Map<String, Object> vo = new LinkedHashMap<>();
        vo.put("id", page.getId());
        vo.put("name", page.getName());
        vo.put("background_image", toFileUrl(page.getBackgroundImage()));
        vo.put("canvas_width", page.getCanvasWidth());
        vo.put("canvas_height", page.getCanvasHeight());
        vo.put("canvas_x", page.getCanvasX());
        vo.put("canvas_y", page.getCanvasY());
        vo.put("analyzed", page.getAnalyzed());
        vo.put("html_content", page.getHtmlContent());

        List<Element> elements = elementMapper.selectList(
                Wrappers.<Element>lambdaQuery().eq(Element::getPageId, page.getId()));
        List<Interaction> allInteractions = elements.isEmpty() ? List.of() : interactionMapper.selectList(
                Wrappers.<Interaction>lambdaQuery()
                        .in(Interaction::getElementId, elements.stream().map(Element::getId).toList()));
        Map<Long, List<Interaction>> interactionsByElement = allInteractions.stream()
                .collect(Collectors.groupingBy(Interaction::getElementId));

        List<Map<String, Object>> elementList = new ArrayList<>();
        for (Element e : elements) {
            Map<String, Object> ev = new LinkedHashMap<>();
            ev.put("id", e.getId());
            ev.put("type", e.getType());
            ev.put("label", e.getLabel());
            ev.put("asset_id", e.getAssetId());
            ev.put("icon", extractIconName(e.getStyle()));
            ev.put("style", e.getStyle());
            ev.put("x", e.getPositionX());
            ev.put("y", e.getPositionY());
            ev.put("width", e.getWidth());
            ev.put("height", e.getHeight());
            List<Interaction> interactions = interactionsByElement.getOrDefault(e.getId(), List.of());
            if (!interactions.isEmpty()) {
                Interaction first = interactions.get(0);
                Map<String, Object> iv = new LinkedHashMap<>();
                iv.put("id", first.getId());
                iv.put("trigger", first.getTriggerType());
                iv.put("action", first.getActionType());
                iv.put("target_page_id", first.getTargetPageId());
                iv.put("params", first.getParams());
                ev.put("interaction", iv);
            }
            elementList.add(ev);
        }
        vo.put("elements", elementList);

        List<Annotation> annotations;
        try {
            annotations = annotationMapper.selectList(
                    Wrappers.<Annotation>lambdaQuery()
                            .eq(Annotation::getPageId, page.getId())
                            .orderByAsc(Annotation::getSortOrder)
                            .orderByAsc(Annotation::getId));
        } catch (Exception e) {
            log.warn("Query annotations with sort_order failed, fallback: {}", e.getMessage());
            annotations = annotationMapper.selectList(
                    Wrappers.<Annotation>lambdaQuery()
                            .select(Annotation::getId, Annotation::getPageId, Annotation::getElementId, Annotation::getText,
                                    Annotation::getPositionX, Annotation::getPositionY, Annotation::getBoxX, Annotation::getBoxY,
                                    Annotation::getAnchorX, Annotation::getAnchorY, Annotation::getElbowX, Annotation::getCreatedAt)
                            .eq(Annotation::getPageId, page.getId())
                            .orderByAsc(Annotation::getId));
        }
        List<Map<String, Object>> annotationList = new ArrayList<>();
        for (Annotation a : annotations) {
            Map<String, Object> av = new LinkedHashMap<>();
            av.put("id", a.getId());
            av.put("element_id", a.getElementId());
            av.put("text", a.getText());
            av.put("x", a.getPositionX());
            av.put("y", a.getPositionY());
            av.put("box_x", a.getBoxX());
            av.put("box_y", a.getBoxY());
            av.put("anchor_x", a.getAnchorX());
            av.put("anchor_y", a.getAnchorY());
            av.put("elbow_x", a.getElbowX());
            av.put("sort_order", a.getSortOrder() != null ? a.getSortOrder() : 0);
            annotationList.add(av);
        }
        vo.put("annotations", annotationList);
        return vo;
    }

    /** 从元素 style JSON 中取出 AI 指定的图标名（无则为 null） */
    private static String extractIconName(String style) {
        if (style == null || style.isBlank()) return null;
        try {
            var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(style);
            String icon = node.path("icon").asText("");
            return icon.isBlank() ? null : icon;
        } catch (Exception e) {
            return null;
        }
    }

    private static String fileNameWithoutExt(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    /** 画布宽固定 375，高按设计稿图片宽高比计算（读取失败时回退 812） */
    private static void applyCanvasSize(Page page, Path image) {
        page.setCanvasWidth(375);
        page.setCanvasHeight(812);
        try {
            var img = javax.imageio.ImageIO.read(image.toFile());
            if (img != null && img.getWidth() > 0) {
                page.setCanvasHeight((int) Math.round(img.getHeight() * 375.0 / img.getWidth()));
            }
        } catch (IOException ignored) {
        }
    }

    /**
     * 更新标注（文字 / 位置），仅允许 text、positionX、positionY 字段。
     */
    public Map<String, Object> updateAnnotation(Long projectId, Long annId, Map<String, Object> body) {
        getProject(projectId);
        Annotation ann = annotationMapper.selectById(annId);
        if (ann == null) {
            throw new IllegalStateException("标注不存在: " + annId);
        }
        Page page = pageMapper.selectById(ann.getPageId());
        if (page == null || !projectId.equals(page.getProjectId())) {
            throw new IllegalStateException("标注不属于该项目");
        }

        if (body.containsKey("text") && body.get("text") != null) {
            ann.setText(body.get("text").toString());
        }
        String updatedTitle = null;
        if (body.containsKey("title") && body.get("title") != null && ann.getElementId() != null) {
            Element el = elementMapper.selectById(ann.getElementId());
            if (el != null) {
                el.setLabel(body.get("title").toString());
                elementMapper.updateById(el);
                updatedTitle = el.getLabel();
            }
        }
        if (body.containsKey("positionX")) {
            ann.setPositionX(toDouble(body.get("positionX")));
        }
        if (body.containsKey("positionY")) {
            ann.setPositionY(toDouble(body.get("positionY")));
        }
        if (body.containsKey("boxX")) {
            ann.setBoxX(toDouble(body.get("boxX")));
        }
        if (body.containsKey("boxY")) {
            ann.setBoxY(toDouble(body.get("boxY")));
        }
        if (body.containsKey("anchorX")) {
            ann.setAnchorX(toDouble(body.get("anchorX")));
        }
        if (body.containsKey("anchorY")) {
            ann.setAnchorY(toDouble(body.get("anchorY")));
        }
        if (body.containsKey("elbowX")) {
            ann.setElbowX(toDouble(body.get("elbowX")));
        }
        annotationMapper.updateById(ann);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", ann.getId());
        result.put("text", ann.getText());
        if (updatedTitle != null) {
            result.put("title", updatedTitle);
        }
        result.put("positionX", ann.getPositionX());
        result.put("positionY", ann.getPositionY());
        result.put("boxX", ann.getBoxX());
        result.put("boxY", ann.getBoxY());
        result.put("anchorX", ann.getAnchorX());
        result.put("anchorY", ann.getAnchorY());
        result.put("elbowX", ann.getElbowX());
        return result;
    }

    private static Double toDouble(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(value.toString().trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(value.toString().trim());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 保存用户在整页原型上的手动微调：前端把调整后的完整 HTML 序列化回来，直接落库。
     */
    public void updatePageHtml(Long pageId, String html) {
        updatePageHtml(pageId, html, null);
    }

    /**
     * 保存用户在整页原型上的手动微调（增加页面级独占锁防覆盖校验）
     */
    public void updatePageHtml(Long pageId, String html, String clientId) {
        if (html == null || html.isBlank()) {
            throw new IllegalArgumentException("HTML 内容不能为空");
        }
        Page page = pageMapper.selectById(pageId);
        if (page == null) {
            throw new IllegalArgumentException("页面不存在: " + pageId);
        }
        // 页面级独占锁保护：如果该页面当前被其他人独占编辑且未超时，拒绝保存
        if (clientId != null && !clientId.isBlank()) {
            EditSession session = PAGE_EDIT_SESSIONS.get(pageId);
            long now = System.currentTimeMillis();
            if (session != null && !session.getClientId().equals(clientId) && (now - session.getLastHeartbeat() < 12000)) {
                String editor = session.getUserName() != null ? session.getUserName() : "其他成员";
                throw new IllegalStateException("页面当前正被 " + editor + " 独占微调中，无法保存覆盖！");
            }
        }
        page.setHtmlContent(html);
        pageMapper.updateById(page);
    }

    /**
     * 更新页面区块在无限画布上的位置。
     */
    public Map<String, Object> updatePagePosition(Long projectId, Long pageId, Map<String, Object> body) {
        getProject(projectId);
        Page page = pageMapper.selectById(pageId);
        if (page == null || !projectId.equals(page.getProjectId())) {
            throw new IllegalStateException("页面不属于该项目: " + pageId);
        }
        if (body.containsKey("canvasX")) {
            page.setCanvasX(toDouble(body.get("canvasX")));
        }
        if (body.containsKey("canvasY")) {
            page.setCanvasY(toDouble(body.get("canvasY")));
        }
        pageMapper.updateById(page);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", page.getId());
        result.put("canvasX", page.getCanvasX());
        result.put("canvasY", page.getCanvasY());
        return result;
    }

    /**
     * 批量更新页面下的业务说明卡片排序。
     */
    @Transactional
    public List<Long> updatePageAnnotationOrders(Long projectId, Long pageId, List<Long> orderedAnnIds) {
        getProject(projectId);
        Page page = pageMapper.selectById(pageId);
        if (page == null || !projectId.equals(page.getProjectId())) {
            throw new IllegalStateException("页面不属于该项目: " + pageId);
        }
        if (orderedAnnIds == null || orderedAnnIds.isEmpty()) {
            return Collections.emptyList();
        }
        for (int i = 0; i < orderedAnnIds.size(); i++) {
            Long annId = orderedAnnIds.get(i);
            try {
                Annotation ann = annotationMapper.selectById(annId);
                if (ann != null && pageId.equals(ann.getPageId())) {
                    ann.setSortOrder(i);
                    annotationMapper.updateById(ann);
                }
            } catch (Exception e) {
                log.warn("Update annotation sort_order failed for annId {}: {}", annId, e.getMessage());
            }
        }
        return orderedAnnIds;
    }

    /**
     * 记录各页面当前正在编辑的用户与心跳时间戳: pageId -> EditSession
     */
    private static final java.util.concurrent.ConcurrentHashMap<Long, EditSession> PAGE_EDIT_SESSIONS =
            new java.util.concurrent.ConcurrentHashMap<>();

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class EditSession {
        private String clientId;
        private String userName;
        private long lastHeartbeat;
    }

    /**
     * 上报/续期页面编辑状态（心跳），并返回冲突状态。
     * 只要距离上次心跳不超过 10 秒，且 clientId 不同，即视为冲突（其他人正在编辑）。
     */
    public Map<String, Object> lockPageEditing(Long pageId, String clientId, String userName, boolean active) {
        long now = System.currentTimeMillis();
        // 清理超过 12 秒的过期会话
        PAGE_EDIT_SESSIONS.entrySet().removeIf(entry -> now - entry.getValue().getLastHeartbeat() > 12000);

        Map<String, Object> result = new LinkedHashMap<>();
        if (!active) {
            EditSession existing = PAGE_EDIT_SESSIONS.get(pageId);
            if (existing != null && existing.getClientId().equals(clientId)) {
                PAGE_EDIT_SESSIONS.remove(pageId);
            }
            result.put("editing", false);
            result.put("conflict", false);
            return result;
        }

        EditSession current = PAGE_EDIT_SESSIONS.get(pageId);
        if (current != null && !current.getClientId().equals(clientId) && (now - current.getLastHeartbeat() < 12000)) {
            result.put("editing", true);
            result.put("conflict", true);
            result.put("editor", current.getUserName() != null ? current.getUserName() : "其他成员");
            return result;
        }

        // 无冲突，更新/持有锁
        PAGE_EDIT_SESSIONS.put(pageId, new EditSession(clientId, userName, now));
        result.put("editing", true);
        result.put("conflict", false);
        return result;
    }

    /**
     * 查询页面的当前编辑冲突状态
     */
    public Map<String, Object> getPageEditingStatus(Long pageId, String clientId) {
        long now = System.currentTimeMillis();
        EditSession current = PAGE_EDIT_SESSIONS.get(pageId);
        Map<String, Object> result = new LinkedHashMap<>();
        if (current != null && (now - current.getLastHeartbeat() < 12000)) {
            boolean conflict = !current.getClientId().equals(clientId);
            result.put("editing", true);
            result.put("conflict", conflict);
            result.put("editor", current.getUserName() != null ? current.getUserName() : "其他成员");
        } else {
            result.put("editing", false);
            result.put("conflict", false);
        }
        return result;
    }

    /**
     * 查询项目中所有正在被编辑的页面状态 (批量查询)
     */
    public Map<Long, Map<String, Object>> getAllPageEditingStatuses(Long projectId, String clientId) {
        long now = System.currentTimeMillis();
        PAGE_EDIT_SESSIONS.entrySet().removeIf(entry -> now - entry.getValue().getLastHeartbeat() > 12000);

        Map<Long, Map<String, Object>> result = new LinkedHashMap<>();
        for (Map.Entry<Long, EditSession> entry : PAGE_EDIT_SESSIONS.entrySet()) {
            Long pageId = entry.getKey();
            EditSession session = entry.getValue();
            if (now - session.getLastHeartbeat() < 12000) {
                Map<String, Object> item = new LinkedHashMap<>();
                boolean conflict = !session.getClientId().equals(clientId);
                item.put("editing", true);
                item.put("conflict", conflict);
                item.put("editor", session.getUserName() != null ? session.getUserName() : "其他成员");
                result.put(pageId, item);
            }
        }
        return result;
    }

    /**
     * 保存或更新交互连线：若该 elementId 已有交互则更新，否则新增，保存到 interaction 表并返回保存后的 Interaction 对象。
     */
    @Transactional
    public Interaction saveInteraction(Long projectId, Map<String, Object> body) {
        getProject(projectId);

        Long elementId = toLong(body.get("elementId") != null ? body.get("elementId") : body.get("element_id"));
        if (elementId == null || elementId <= 0) {
            Long pageId = toLong(body.get("pageId") != null ? body.get("pageId") : body.get("page_id"));
            if (pageId != null) {
                List<Element> pageEls = elementMapper.selectList(
                        Wrappers.<Element>lambdaQuery().eq(Element::getPageId, pageId));
                if (!pageEls.isEmpty()) {
                    elementId = pageEls.get(0).getId();
                } else {
                    Element newEl = new Element();
                    newEl.setPageId(pageId);
                    newEl.setType("button");
                    newEl.setLabel("画板跳转");
                    newEl.setPositionX(0.0);
                    newEl.setPositionY(0.0);
                    newEl.setWidth(100.0);
                    newEl.setHeight(40.0);
                    newEl.setCreatedBy("manual");
                    newEl.setCreatedAt(LocalDateTime.now());
                    elementMapper.insert(newEl);
                    elementId = newEl.getId();
                }
            } else {
                throw new IllegalArgumentException("elementId 不能为空");
            }
        }

        Element element = elementMapper.selectById(elementId);
        if (element == null) {
            throw new IllegalArgumentException("元素不存在: " + elementId);
        }
        Page elementPage = pageMapper.selectById(element.getPageId());
        if (elementPage == null || !projectId.equals(elementPage.getProjectId())) {
            throw new IllegalArgumentException("元素不属于该项目");
        }

        Long targetPageId = toLong(body.get("targetPageId") != null ? body.get("targetPageId") : body.get("target_page_id"));

        String actionType = null;
        if (body.get("actionType") != null) {
            actionType = body.get("actionType").toString();
        } else if (body.get("action_type") != null) {
            actionType = body.get("action_type").toString();
        } else if (body.get("action") != null) {
            actionType = body.get("action").toString();
        }

        String triggerType = null;
        if (body.get("triggerType") != null) {
            triggerType = body.get("triggerType").toString();
        } else if (body.get("trigger_type") != null) {
            triggerType = body.get("trigger_type").toString();
        } else if (body.get("trigger") != null) {
            triggerType = body.get("trigger").toString();
        }
        if (triggerType == null || triggerType.isBlank()) {
            triggerType = "click";
        }

        Object paramsObj = body.get("params");
        String paramsStr = null;
        if (paramsObj != null) {
            if (paramsObj instanceof String s) {
                paramsStr = s;
            } else {
                try {
                    paramsStr = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(paramsObj);
                } catch (Exception e) {
                    paramsStr = paramsObj.toString();
                }
            }
        }

        List<Interaction> existingList = interactionMapper.selectList(
                Wrappers.<Interaction>lambdaQuery().eq(Interaction::getElementId, elementId));
        Interaction interaction;
        if (!existingList.isEmpty()) {
            interaction = existingList.get(0);
            interaction.setTargetPageId(targetPageId);
            interaction.setActionType(actionType);
            interaction.setTriggerType(triggerType);
            interaction.setParams(paramsStr);
            interactionMapper.updateById(interaction);
            for (int i = 1; i < existingList.size(); i++) {
                interactionMapper.deleteById(existingList.get(i).getId());
            }
        } else {
            interaction = new Interaction();
            interaction.setElementId(elementId);
            interaction.setTargetPageId(targetPageId);
            interaction.setActionType(actionType);
            interaction.setTriggerType(triggerType);
            interaction.setParams(paramsStr);
            interactionMapper.insert(interaction);
        }
        return interaction;
    }

    /**
     * 删除指定的交互连线路由
     */
    @Transactional
    public void deleteInteraction(Long projectId, Long interactionId) {
        getProject(projectId);
        Interaction interaction = interactionMapper.selectById(interactionId);
        if (interaction == null) {
            return;
        }
        Element el = elementMapper.selectById(interaction.getElementId());
        if (el != null) {
            Page page = pageMapper.selectById(el.getPageId());
            if (page != null && !projectId.equals(page.getProjectId())) {
                throw new IllegalStateException("交互连线不属于该项目");
            }
        }
        interactionMapper.deleteById(interactionId);
    }

    /**
     * 为拖拽新组件/方框提供快速注册元素能力
     */
    @Transactional
    public Element createElement(Long projectId, Long pageId, Map<String, Object> body) {
        getProject(projectId);
        Page page = pageMapper.selectById(pageId);
        if (page == null || !projectId.equals(page.getProjectId())) {
            throw new IllegalArgumentException("页面不存在或不属于该项目: " + pageId);
        }

        Element element = new Element();
        element.setPageId(pageId);

        String type = body.get("type") != null ? body.get("type").toString().trim() : "box";
        element.setType(type.isBlank() ? "box" : type);

        String label = body.get("label") != null ? body.get("label").toString().trim() : "";
        element.setLabel(label);

        Double posX = body.get("positionX") != null ? toDouble(body.get("positionX")) : toDouble(body.get("x"));
        element.setPositionX(posX != null ? posX : 0.0);

        Double posY = body.get("positionY") != null ? toDouble(body.get("positionY")) : toDouble(body.get("y"));
        element.setPositionY(posY != null ? posY : 0.0);

        Double width = body.get("width") != null ? toDouble(body.get("width")) : toDouble(body.get("w"));
        element.setWidth(width != null ? width : 100.0);

        Double height = body.get("height") != null ? toDouble(body.get("height")) : toDouble(body.get("h"));
        element.setHeight(height != null ? height : 40.0);

        Object styleObj = body.get("style");
        String styleStr = null;
        if (styleObj != null) {
            if (styleObj instanceof String s) {
                styleStr = s;
            } else {
                try {
                    styleStr = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(styleObj);
                } catch (Exception e) {
                    styleStr = styleObj.toString();
                }
            }
        }
        element.setStyle(styleStr);

        String assetId = body.get("assetId") != null ? body.get("assetId").toString()
                : (body.get("asset_id") != null ? body.get("asset_id").toString() : null);
        element.setAssetId(assetId);

        element.setCreatedBy("manual");
        element.setCreatedAt(LocalDateTime.now());

        elementMapper.insert(element);
        return element;
    }

    public Page createPage(Long projectId, Map<String, Object> body) {
        getProject(projectId);
        Page page = new Page();
        page.setProjectId(projectId);

        Long pageCount = pageMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Page>()
                .eq(Page::getProjectId, projectId));
        String defaultName = "画板 " + (pageCount + 1);
        String name = body.get("name") != null ? body.get("name").toString().trim() : defaultName;
        page.setName(name.isEmpty() ? defaultName : name);

        Number width = body.get("width") instanceof Number n ? n : 375;
        Number height = body.get("height") instanceof Number n ? n : 812;
        page.setCanvasWidth(width.intValue());
        page.setCanvasHeight(height.intValue());

        if (body.containsKey("x") && body.get("x") != null) {
            page.setCanvasX(toDouble(body.get("x")));
        }
        if (body.containsKey("y") && body.get("y") != null) {
            page.setCanvasY(toDouble(body.get("y")));
        }

        page.setSortOrder(100 + pageCount.intValue());
        page.setAnalyzed(1);

        String initialHtml = "<!DOCTYPE html>\n<html>\n<head>\n  <meta charset=\"utf-8\">\n  <meta name=\"viewport\" content=\"width="
                + page.getCanvasWidth() + "\">\n  <style>\n    body { margin: 0; padding: 16px; background: #ffffff; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; box-sizing: border-box; min-height: "
                + page.getCanvasHeight() + "px; position: relative; }\n  </style>\n</head>\n<body>\n</body>\n</html>";
        String htmlContent = body.get("htmlContent") != null ? body.get("htmlContent").toString() : initialHtml;
        page.setHtmlContent(htmlContent);
        page.setCreatedAt(LocalDateTime.now());

        pageMapper.insert(page);
        return page;
    }

    /**
     * 删除指定画板/页面及其下属元素、交互和说明
     */
    @Transactional
    public void deletePage(Long projectId, Long pageId) {
        getProject(projectId);
        Page page = pageMapper.selectById(pageId);
        if (page == null || !projectId.equals(page.getProjectId())) {
            return;
        }
        List<Element> els = elementMapper.selectList(Wrappers.<Element>lambdaQuery().eq(Element::getPageId, pageId));
        for (Element el : els) {
            interactionMapper.delete(Wrappers.<Interaction>lambdaQuery().eq(Interaction::getElementId, el.getId()));
            elementMapper.deleteById(el.getId());
        }
        annotationMapper.delete(Wrappers.<Annotation>lambdaQuery().eq(Annotation::getPageId, pageId));
        pageMapper.deleteById(pageId);
    }

    private String toFileUrl(String absolutePath) {
        if (absolutePath == null || absolutePath.isBlank()) return "";
        try {
            Path designs = Paths.get(designsDir).toAbsolutePath().normalize();
            Path file = Paths.get(absolutePath).toAbsolutePath().normalize();
            if (file.startsWith(designs)) {
                String relative = designs.relativize(file).toString().replace('\\', '/');
                return "/files/" + relative;
            }
        } catch (Exception ignored) {
        }
        String normalized = absolutePath.replace('\\', '/');
        int idx = normalized.lastIndexOf('/');
        String filename = idx >= 0 ? normalized.substring(idx + 1) : normalized;
        return "/files/" + filename;
    }
}