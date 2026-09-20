package com.wireforge.controller;

import com.wireforge.common.Result;
import com.wireforge.entity.Element;
import com.wireforge.entity.Interaction;
import com.wireforge.entity.Page;
import com.wireforge.entity.Project;
import com.wireforge.service.AnalyzeService;
import com.wireforge.service.InteractionAutowireService;
import com.wireforge.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final AnalyzeService analyzeService;
    private final InteractionAutowireService interactionAutowireService;

    @GetMapping
    public Result<List<Project>> list() {
        return Result.ok(projectService.listProjects());
    }

    @PostMapping
    public Result<Project> create(@RequestBody Map<String, String> body) {
        return Result.ok(projectService.createProject(body.get("name"), body.get("description")));
    }

    /** 删除项目（级联删除其页面、元素、交互、标注） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        projectService.deleteProject(id);
        return Result.ok(null);
    }


    @GetMapping("/{id}")
    public Result<Project> get(@PathVariable Long id) {
        return Result.ok(projectService.getProject(id));
    }

    /** 扫描设计稿目录，为项目新建页面 */
    @PostMapping("/{id}/scan")
    public Result<List<Page>> scan(@PathVariable Long id) {
        return Result.ok(projectService.scanDesigns(id));
    }

    /** 对未分析页面执行 AI 识别 */
    @PostMapping("/{id}/analyze")
    public Result<List<Map<String, Object>>> analyze(@PathVariable Long id) {
        return Result.ok(projectService.analyzeProject(id));
    }

    /** 强制重新对指定单页执行 AI 深度识别 */
    @PostMapping("/{id}/pages/{pageId}/reanalyze")
    public Result<List<Map<String, Object>>> reanalyzePage(@PathVariable Long id, @PathVariable Long pageId) {
        return Result.ok(projectService.reanalyzePage(id, pageId));
    }

    /** 强制重新对全项目所有页面执行 AI 深度识别 */
    @PostMapping("/{id}/reanalyze-all")
    public Result<List<Map<String, Object>>> reanalyzeAll(@PathVariable Long id) {
        return Result.ok(projectService.reanalyzeAll(id));
    }

    /** 重建 App Map（共享底栏）并重渲染全部页面 + 交互验证（不调用 AI） */
    @PostMapping("/{id}/appmap")
    public Result<Integer> rebuildAppMap(@PathVariable Long id) {
        return Result.ok(projectService.rebuildAppMapAndRender(id));
    }

    /** 仅跑交互验证（无头浏览器逐页点击所有交互元素），返回失败清单 */
    @PostMapping("/{id}/verify")
    public Result<List<com.wireforge.ai.HtmlRenderer.CheckResult>> verify(@PathVariable Long id) {
        return Result.ok(projectService.verifyProject(id));
    }

    /** 轻量交互提取：仅让 AI 补答"哪个元素→跳哪"并落库（不重跑元素识别），返回交互条数 */
    @PostMapping("/{id}/extract-interactions")
    public Result<Integer> extractInteractions(@PathVariable Long id) {
        return Result.ok(analyzeService.extractInteractions(id));
    }

    /** 完整原型数据（页面 + 元素 + 交互 + 标注） */
    @GetMapping("/{id}/prototype")
    public Result<Map<String, Object>> prototype(@PathVariable Long id) {
        return Result.ok(projectService.getPrototype(id));
    }

    /** 更新标注（文字 / 位置） */
    @PutMapping("/{id}/annotations/{annId}")
    public Result<Map<String, Object>> updateAnnotation(
            @PathVariable Long id,
            @PathVariable Long annId,
            @RequestBody Map<String, Object> body) {
        return Result.ok(projectService.updateAnnotation(id, annId, body));
    }

    /** 批量更新页面下的说明排序 */
    @PutMapping("/{id}/pages/{pageId}/annotation-orders")
    public Result<List<Long>> updatePageAnnotationOrders(
            @PathVariable Long id,
            @PathVariable Long pageId,
            @RequestBody List<Long> orderedAnnIds) {
        return Result.ok(projectService.updatePageAnnotationOrders(id, pageId, orderedAnnIds));
    }

    /** 更新页面区块在画布上的位置 */
    @PutMapping("/{id}/pages/{pageId}/position")
    public Result<Map<String, Object>> updatePagePosition(
            @PathVariable Long id,
            @PathVariable Long pageId,
            @RequestBody Map<String, Object> body) {
        return Result.ok(projectService.updatePagePosition(id, pageId, body));
    }

    /** 仅重新生成某页的整页 HTML（Stitch 式直出，不重跑元素识别），用于快速迭代效果 */
    @PostMapping("/{id}/pages/{pageId}/regenerate-html")
    public Result<String> regeneratePageHtml(@PathVariable Long id, @PathVariable Long pageId) {
        analyzeService.alignVariantPages(id);
        return Result.ok(analyzeService.regeneratePageHtml(pageId));
    }

    /** 保存用户在整页原型上的手动微调（前端序列化后的完整 HTML 直接落库） */
    @PutMapping("/{id}/pages/{pageId}/html")
    public Result<Void> updatePageHtml(
            @PathVariable Long id,
            @PathVariable Long pageId,
            @RequestBody Map<String, String> body) {
        String clientId = body.getOrDefault("clientId", "");
        projectService.updatePageHtml(pageId, body.get("html"), clientId);
        return Result.ok(null);
    }

    /** 上报/释放页面编辑锁（心跳） */
    @PostMapping("/{id}/pages/{pageId}/edit-lock")
    public Result<Map<String, Object>> lockPageEditing(
            @PathVariable Long id,
            @PathVariable Long pageId,
            @RequestBody Map<String, Object> body) {
        String clientId = body.getOrDefault("clientId", "").toString();
        String userName = body.getOrDefault("userName", "其他成员").toString();
        boolean active = Boolean.TRUE.equals(body.get("active"));
        return Result.ok(projectService.lockPageEditing(pageId, clientId, userName, active));
    }

    /** 查询页面编辑冲突状态 */
    @GetMapping("/{id}/pages/{pageId}/edit-status")
    public Result<Map<String, Object>> getPageEditingStatus(
            @PathVariable Long id,
            @PathVariable Long pageId,
            @RequestParam(required = false, defaultValue = "") String clientId) {
        return Result.ok(projectService.getPageEditingStatus(pageId, clientId));
    }

    /** 批量查询项目中所有正在被编辑的页面冲突状态 */
    @GetMapping("/{id}/edit-statuses")
    public Result<Map<Long, Map<String, Object>>> getAllPageEditingStatuses(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "") String clientId) {
        return Result.ok(projectService.getAllPageEditingStatuses(id, clientId));
    }

    /** 手动触发全局交互拓扑自动布线 */
    @PostMapping("/{id}/autowire-interactions")
    public Result<Integer> autowireInteractions(@PathVariable Long id) {
        return Result.ok(interactionAutowireService.autowireProjectInteractions(id));
    }

    /**
     * 添加或更新交互连线：若该 elementId 已有交互则更新，否则新增，保存到 interaction 表并返回保存后的 Interaction 对象
     */
    @PostMapping("/{id}/interactions")
    public Result<Interaction> saveInteraction(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        return Result.ok(projectService.saveInteraction(id, body));
    }

    /**
     * 删除指定的交互连线路由
     */
    @DeleteMapping("/{id}/interactions/{interactionId}")
    public Result<Void> deleteInteraction(
            @PathVariable Long id,
            @PathVariable Long interactionId) {
        projectService.deleteInteraction(id, interactionId);
        return Result.ok(null);
    }

    /**
     * 为拖拽新组件/方框提供快速注册元素能力
     */
    @PostMapping("/{id}/pages/{pageId}/elements")
    public Result<Element> createElement(
            @PathVariable Long id,
            @PathVariable Long pageId,
            @RequestBody Map<String, Object> body) {
        return Result.ok(projectService.createElement(id, pageId, body));
    }
}