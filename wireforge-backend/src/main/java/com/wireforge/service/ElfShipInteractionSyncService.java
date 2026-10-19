package com.wireforge.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ElfShipInteractionSyncService implements ApplicationRunner {

    private final ProjectMapper projectMapper;
    private final PageMapper pageMapper;
    private final ElementMapper elementMapper;
    private final InteractionMapper interactionMapper;
    private final ObjectMapper objectMapper;

    public static class HotspotDto {
        public String sourcePrefix;
        public String targetPrefix;
        public String sourceKey;
        public String targetKey;
        public String uid;
        public String label;
        public int x;
        public int y;
        public int w;
        public int h;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            syncMatchingProjects();
        } catch (Exception e) {
            log.error("[ElfShipSync] Startup sync failed: {}", e.getMessage(), e);
        }
    }

    /**
     * 自动检测所有 ElfShip 相关项目并执行增量连线同步
     */
    public List<Map<String, Object>> syncMatchingProjects() {
        List<Project> allProjects = projectMapper.selectList(null);
        List<Map<String, Object>> results = new ArrayList<>();

        for (Project p : allProjects) {
            String name = p.getName() != null ? p.getName().trim() : "";
            // 匹配线上项目 4 ("ElfShip · 账号权限与配置表管理版") 以及本地项目 161 ("ElfShip")
            if (p.getId() == 4L || name.toLowerCase().contains("elfship") || name.contains("账号权限与配置表管理版")) {
                log.info("[ElfShipSync] Found target project: id={}, name={}", p.getId(), p.getName());
                Map<String, Object> res = syncProject(p.getId());
                results.add(res);
            }
        }
        return results;
    }

    /**
     * 对指定项目执行全量热区增量补全
     */
    @Transactional
    public Map<String, Object> syncProject(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new IllegalArgumentException("项目不存在: " + projectId);
        }

        List<HotspotDto> hotspotList = loadHotspots();
        if (hotspotList.isEmpty()) {
            log.warn("[ElfShipSync] No hotspots loaded from classpath:elfship-interactions.json");
            return Map.of("success", false, "message", "No hotspots found in classpath");
        }

        // 1. 获取该项目的所有有效页面
        List<Page> pages = pageMapper.selectList(Wrappers.<Page>lambdaQuery()
                .eq(Page::getProjectId, projectId)
                .and(w -> w.isNull(Page::getIsDeleted).or().eq(Page::getIsDeleted, 0)));

        if (pages.isEmpty()) {
            return Map.of("success", false, "message", "Project has no pages");
        }

        // 2. 建立 prefix -> Page 映射（如 "00" -> 页面总览, "01" -> 仪表盘, "02" -> 环节进度）
        Map<String, Page> prefixToPage = new HashMap<>();
        for (Page p : pages) {
            if (p.getName() != null) {
                String prefix = p.getName().trim().split("\\s+")[0];
                prefixToPage.put(prefix, p);
            }
        }

        List<Long> pageIds = pages.stream().map(Page::getId).toList();

        // 3. 查询当前项目中已有元素及其交互连线
        List<Element> existingElements = elementMapper.selectList(
                Wrappers.<Element>lambdaQuery().in(Element::getPageId, pageIds));

        Map<Long, Element> elementById = existingElements.stream()
                .collect(Collectors.toMap(Element::getId, e -> e, (a, b) -> a));

        List<Long> existingElementIds = existingElements.stream().map(Element::getId).toList();
        List<Interaction> existingInteractions = existingElementIds.isEmpty() ? List.of() :
                interactionMapper.selectList(Wrappers.<Interaction>lambdaQuery().in(Interaction::getElementId, existingElementIds));

        // 4. 统计已经绑定了 domUid 的连线缓存: "pageId:domUid" -> Interaction
        Set<String> boundKeys = new HashSet<>();
        for (Interaction ix : existingInteractions) {
            Element el = elementById.get(ix.getElementId());
            if (el != null && el.getPageId() != null && ix.getParams() != null) {
                String domUid = extractDomUid(ix.getParams());
                if (!domUid.isEmpty()) {
                    boundKeys.add(el.getPageId() + ":" + domUid);
                }
            }
        }

        log.info("[ElfShipSync] Project {}: Total pages={}, Existing bound hotspots={}",
                projectId, pages.size(), boundKeys.size());

        int addedCount = 0;
        int skippedCount = 0;
        LocalDateTime now = LocalDateTime.now();

        // 5. 遍历 manifest 中所有有效热区，增量补全
        for (HotspotDto h : hotspotList) {
            Page sourcePage = prefixToPage.get(h.sourcePrefix);
            Page targetPage = prefixToPage.get(h.targetPrefix);

            if (sourcePage == null || targetPage == null) {
                skippedCount++;
                continue;
            }

            // 同页面自跳转无需连线
            if (sourcePage.getId().equals(targetPage.getId())) {
                skippedCount++;
                continue;
            }

            String cacheKey = sourcePage.getId() + ":" + h.uid;
            if (boundKeys.contains(cacheKey)) {
                skippedCount++;
                continue;
            }

            // 注册新 Element
            Element el = new Element();
            el.setPageId(sourcePage.getId());
            el.setType("button");
            el.setLabel(h.label != null ? h.label : "");
            el.setPositionX((double) h.x);
            el.setPositionY((double) h.y);
            el.setWidth((double) Math.max(16, h.w));
            el.setHeight((double) Math.max(14, h.h));
            el.setGroupRole("anchor");
            el.setCreatedBy("manual");
            el.setCreatedAt(now);
            elementMapper.insert(el);

            // 构造包含精准坐标与 domUid 的 params JSON
            Map<String, Object> hitMap = Map.of(
                    "x", h.x,
                    "y", h.y,
                    "w", Math.max(16, h.w),
                    "h", Math.max(14, h.h)
            );
            Map<String, Object> paramsMap = Map.of(
                    "animation", "push",
                    "domUid", h.uid != null ? h.uid : "",
                    "hit", hitMap
            );

            String paramsJson = "";
            try {
                paramsJson = objectMapper.writeValueAsString(paramsMap);
            } catch (Exception e) {
                paramsJson = "{\"animation\":\"push\",\"domUid\":\"" + h.uid + "\"}";
            }

            // 注册新 Interaction
            Interaction ix = new Interaction();
            ix.setElementId(el.getId());
            ix.setTriggerType("click");
            ix.setActionType("navigate");
            ix.setTargetPageId(targetPage.getId());
            ix.setParams(paramsJson);
            ix.setSource("user");
            interactionMapper.insert(ix);

            boundKeys.add(cacheKey);
            addedCount++;
        }

        log.info("[ElfShipSync] Project {}: Finished sync! Added {} new connections, Skipped {} existing/unmatched. Total now: {}",
                projectId, addedCount, skippedCount, boundKeys.size());

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("success", true);
        summary.put("projectId", projectId);
        summary.put("projectName", project.getName());
        summary.put("added", addedCount);
        summary.put("skipped", skippedCount);
        summary.put("totalActiveConnections", boundKeys.size());
        return summary;
    }

    private List<HotspotDto> loadHotspots() {
        try {
            ClassPathResource resource = new ClassPathResource("elfship-interactions.json");
            if (!resource.exists()) {
                log.warn("[ElfShipSync] Resource elfship-interactions.json not found on classpath");
                return List.of();
            }
            try (InputStream is = resource.getInputStream()) {
                return objectMapper.readValue(is, new TypeReference<List<HotspotDto>>() {});
            }
        } catch (Exception e) {
            log.error("[ElfShipSync] Failed to read elfship-interactions.json: {}", e.getMessage(), e);
            return List.of();
        }
    }

    private String extractDomUid(String params) {
        if (params == null || params.isBlank()) return "";
        try {
            Map<?, ?> map = objectMapper.readValue(params, Map.class);
            Object uid = map.get("domUid");
            return uid != null ? uid.toString().trim() : "";
        } catch (Exception e) {
            // 正则兜底
            int idx = params.indexOf("\"domUid\":\"");
            if (idx != -1) {
                int start = idx + 10;
                int end = params.indexOf("\"", start);
                if (end != -1) return params.substring(start, end);
            }
            return "";
        }
    }
}
