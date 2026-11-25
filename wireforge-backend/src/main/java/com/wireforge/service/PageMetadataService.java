package com.wireforge.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.wireforge.entity.Page;
import com.wireforge.entity.Project;
import com.wireforge.entity.Interaction;
import com.wireforge.mapper.PageMapper;
import com.wireforge.mapper.ProjectMapper;
import com.wireforge.mapper.InteractionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/** Owns stable display metadata; HTML/position saves do not write these columns. */
@Slf4j
@Service
@RequiredArgsConstructor
public class PageMetadataService implements ApplicationRunner {
    private final PageMapper pageMapper;
    private final ProjectMapper projectMapper;
    private final InteractionMapper interactionMapper;
    @Value("${wireforge.designs-dir}")
    private String designsDir;

    @Override
    public void run(ApplicationArguments args) {
        // Existing names are retained verbatim. Each successful row is marked, making retries idempotent.
        long after = 0;
        int count = 0;
        while (true) {
            List<Page> batch = pageMapper.selectList(Wrappers.<Page>lambdaQuery().isNull(Page::getNameSource)
                    .gt(Page::getId, after).orderByAsc(Page::getId).last("LIMIT 200"));
            if (batch.isEmpty()) break;
            List<Long> ids = batch.stream().map(Page::getId).toList();
            Map<Long, List<String>> incoming = interactionMapper.selectList(Wrappers.<Interaction>lambdaQuery()
                    .in(Interaction::getTargetPageId, ids)).stream().collect(Collectors.groupingBy(
                            Interaction::getTargetPageId, Collectors.mapping(Interaction::getActionType, Collectors.toList())));
            Map<Long, Project> projects = new HashMap<>();
            for (Page page : batch) {
                Project project = projects.computeIfAbsent(page.getProjectId(), projectMapper::selectById);
                page.setSourceName(sourceName(page, project));
                PageReferences.remember(page, page.getName(), page.getSourceName(), PageNameResolver.basename(page.getBackgroundImage()));
                String type = PageTypes.normalize(page.getPageType());
                if (PageTypes.UNKNOWN.equals(type)) type = PageTypes.legacy(page, incoming.getOrDefault(page.getId(), List.of()));
                count += pageMapper.update(null, Wrappers.<Page>lambdaUpdate().eq(Page::getId, page.getId())
                        .isNull(Page::getNameSource).set(Page::getNameSource, "legacy")
                        .set(Page::getSourceName, page.getSourceName()).set(Page::getNameAliases, page.getNameAliases())
                        .set(Page::getPageType, type).set(Page::getPageTypeSource, "legacy"));
            }
            after = batch.get(batch.size() - 1).getId();
        }
        if (count > 0) log.info("Initialized display metadata for {} historical pages; names and documents retained", count);
    }

    private String sourceName(Page page, Project project) {
        if (page.getSourceName() != null && !page.getSourceName().isBlank()) return page.getSourceName();
        if (page.getBackgroundImage() == null || page.getBackgroundImage().isBlank()) return page.getName();
        try {
            Path root = DesignDirectory.resolve(designsDir, project == null ? null : project.getDesignFolder());
            Path image = Path.of(page.getBackgroundImage()).toAbsolutePath().normalize();
            if (image.startsWith(root)) {
                String relative = root.relativize(image).toString().replace('\\', '/');
                return relative.replaceFirst("(?i)\\.(png|jpe?g|webp)$", "");
            }
        } catch (RuntimeException ignored) { /* Missing/moved source folders must not rename old pages. */ }
        return PageNameResolver.basename(page.getBackgroundImage()).replaceFirst("(?i)\\.(png|jpe?g|webp)$", "");
    }

    /** Called inside the analysis write transaction after the slow model request has finished. */
    @Transactional
    public void applyAnalysis(Page analyzed, JsonNode root) {
        Page current = locked(analyzed.getProjectId(), analyzed.getId());
        if (current.getSourceName() == null) current.setSourceName(sourceName(current, projectMapper.selectById(current.getProjectId())));
        List<Page> peers = pages(current.getProjectId());
        String oldName = current.getName();
        PageReferences.remember(current, oldName, current.getSourceName(), PageNameResolver.basename(current.getBackgroundImage()));
        var name = PageNameResolver.resolve(current, root.path("page_name").asText(""), peers);
        current.setName(name.name());
        current.setNameSource(name.source());
        if (!"user".equals(current.getPageTypeSource())) {
            String type = PageTypes.fromAnalysis(root);
            if (!PageTypes.UNKNOWN.equals(type) || PageTypes.UNKNOWN.equals(PageTypes.normalize(current.getPageType()))) {
                current.setPageType(type);
                current.setPageTypeSource("ai");
            }
        }
        saveMetadata(current);
        if (!Objects.equals(oldName, current.getName())) synchronizeReferences(current, peers);
        copyMetadata(current, analyzed);
    }

    @Transactional
    public Map<String, Object> update(Long projectId, Long pageId, Map<String, String> body) {
        if (!body.containsKey("name") && !body.containsKey("pageType")) throw new IllegalArgumentException("未提供页面名称或类型");
        Page page = locked(projectId, pageId);
        List<Page> peers = pages(projectId);
        String oldName = page.getName();
        if (body.containsKey("name")) {
            String name = body.get("name") == null ? "" : body.get("name").trim().replaceAll("[\\s\\p{Z}]+", " ");
            if (name.isBlank() || name.length() > 255) throw new IllegalArgumentException("页面名称须为 1～255 个字符");
            if (peers.stream().anyMatch(p -> !Objects.equals(p.getId(), pageId) && PageReferences.references(p).stream()
                    .anyMatch(alias -> PageReferences.normalize(alias).equals(PageReferences.normalize(name)))))
                throw new IllegalArgumentException("该名称与其他画板名称或历史跳转名称重复，请增加分组或其他区分");
            PageReferences.remember(page, oldName, page.getSourceName());
            page.setName(name);
            page.setNameSource("user");
        }
        if (body.containsKey("pageType")) {
            String type = body.get("pageType");
            if (!PageTypes.valid(type)) throw new IllegalArgumentException("不支持的页面类型");
            page.setPageType(type);
            page.setPageTypeSource(PageTypes.UNKNOWN.equals(type) ? "ai" : "user");
        }
        saveMetadata(page);
        if (!Objects.equals(oldName, page.getName())) synchronizeReferences(page, peers);
        return view(page);
    }

    private Page locked(Long projectId, Long pageId) {
        Page page = pageMapper.selectOne(Wrappers.<Page>lambdaQuery().eq(Page::getId, pageId)
                .eq(Page::getProjectId, projectId).and(q -> q.isNull(Page::getIsDeleted).or().eq(Page::getIsDeleted, 0)).last("FOR UPDATE"));
        if (page == null) throw new IllegalArgumentException("页面不存在或已删除");
        return page;
    }

    private List<Page> pages(Long projectId) {
        return pageMapper.selectList(Wrappers.<Page>lambdaQuery().eq(Page::getProjectId, projectId)
                .and(q -> q.isNull(Page::getIsDeleted).or().eq(Page::getIsDeleted, 0)).orderByAsc(Page::getId));
    }

    private void saveMetadata(Page page) {
        pageMapper.update(null, Wrappers.<Page>lambdaUpdate().eq(Page::getId, page.getId())
                .set(Page::getName, page.getName()).set(Page::getNameSource, page.getNameSource())
                .set(Page::getSourceName, page.getSourceName()).set(Page::getNameAliases, page.getNameAliases())
                .set(Page::getPageType, PageTypes.normalize(page.getPageType())).set(Page::getPageTypeSource, page.getPageTypeSource()));
    }

    private void synchronizeReferences(Page renamed, List<Page> peers) {
        List<Page> updated = peers.stream().map(p -> Objects.equals(p.getId(), renamed.getId()) ? renamed : p).toList();
        PageReferences references = new PageReferences(updated);
        for (Page page : updated) {
            String html = page.getHtmlContent(), next = references.rewriteHtml(html);
            if (Objects.equals(html, next)) continue;
            int changed = pageMapper.update(null, Wrappers.<Page>lambdaUpdate().eq(Page::getId, page.getId())
                    .apply("BINARY html_content = {0}", html).set(Page::getHtmlContent, next));
            if (changed != 1) throw new IllegalStateException("关联页面正在被编辑，请稍后重试改名");
            page.setHtmlContent(next);
        }
    }

    private static void copyMetadata(Page from, Page to) {
        to.setName(from.getName()); to.setNameSource(from.getNameSource()); to.setSourceName(from.getSourceName());
        to.setNameAliases(from.getNameAliases()); to.setPageType(from.getPageType()); to.setPageTypeSource(from.getPageTypeSource());
    }

    public static Map<String, Object> view(Page page) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", page.getId()); view.put("name", page.getName()); view.put("name_source", page.getNameSource());
        view.put("source_name", page.getSourceName()); view.put("name_aliases", PageReferences.references(page));
        view.put("page_type", PageTypes.normalize(page.getPageType())); view.put("page_type_source", page.getPageTypeSource());
        return view;
    }
}
