package com.wireforge.controller;

import com.wireforge.common.Result;
import com.wireforge.entity.CommentReply;
import com.wireforge.entity.CommentThread;
import com.wireforge.entity.Element;
import com.wireforge.entity.Interaction;
import com.wireforge.entity.Page;
import com.wireforge.entity.Project;
import com.wireforge.service.AnalyzeService;
import com.wireforge.service.InteractionAutowireService;
import com.wireforge.service.ProjectService;
import com.wireforge.model.ProjectAnalysisStatus;
import com.wireforge.model.AutowirePlan;
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

    /** 启动 AI 原型分析（后台异步执行，前端轮询 analysis-status） */
    @PostMapping("/{id}/analyze")
    public Result<ProjectAnalysisStatus> analyze(@PathVariable Long id) {
        return Result.ok(projectService.startAsyncAnalyze(id));
    }

    /** 查询项目当前 AI 分析进度与状态（以服务端为准，刷新网页不丢失） */
    @GetMapping("/{id}/analysis-status")
    public Result<ProjectAnalysisStatus> getAnalysisStatus(@PathVariable Long id) {
        return Result.ok(projectService.getAnalysisStatus(id));
    }

    /** 重新对指定单页执行 AI 识别；设计稿没换时跳过，force=true 时照样重跑 */
    @PostMapping("/{id}/pages/{pageId}/reanalyze")
    public Result<List<Map<String, Object>>> reanalyzePage(@PathVariable Long id, @PathVariable Long pageId,
                                                           @RequestParam(defaultValue = "false") boolean force) {
        return Result.ok(projectService.reanalyzePage(id, pageId, force));
    }

    /** 重新对全项目页面执行 AI 识别；设计稿没换的页跳过，force=true 时全部重跑 */
    @PostMapping("/{id}/reanalyze-all")
    public Result<List<Map<String, Object>>> reanalyzeAll(@PathVariable Long id,
                                                          @RequestParam(defaultValue = "false") boolean force) {
        return Result.ok(projectService.reanalyzeAll(id, force));
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

    /** 按现有元素重算点击分组和连线，不重跑视觉模型 */
    @PostMapping("/{id}/regroup")
    public Result<Integer> regroup(@PathVariable Long id) {
        return Result.ok(analyzeService.regroupProject(id));
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

    /** 手动添加说明，关联元素可选，不创建交互关系。 */
    @PostMapping("/{id}/pages/{pageId}/annotations")
    public Result<Map<String, Object>> createAnnotation(@PathVariable Long id, @PathVariable Long pageId,
                                                       @RequestBody Map<String, Object> body) {
        return Result.ok(projectService.createAnnotation(id, pageId, body));
    }

    @DeleteMapping("/{id}/annotations/{annId}")
    public Result<Void> deleteAnnotation(@PathVariable Long id, @PathVariable Long annId) {
        projectService.deleteAnnotation(id, annId);
        return Result.ok(null);
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

    /** 更新页面画板宽高 */
    @PutMapping("/{id}/pages/{pageId}/size")
    public Result<Map<String, Object>> updatePageSize(
            @PathVariable Long id,
            @PathVariable Long pageId,
            @RequestBody Map<String, Object> body) {
        return Result.ok(projectService.updatePageSize(id, pageId, body));
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
    public Result<AutowirePlan> autowireInteractions(@PathVariable Long id) {
        return Result.ok(interactionAutowireService.preview(id));
    }

    /** 手动触发全局交互拓扑自动布线（含阶段二 AI 语义增量推导） */
    @PostMapping("/{id}/autowire-ai")
    public Result<AutowirePlan> autowireInteractionsWithAi(@PathVariable Long id) {
        return Result.ok(interactionAutowireService.preview(id));
    }

    @PostMapping("/{id}/autowire/preview")
    public Result<AutowirePlan> previewAutowire(@PathVariable Long id, @RequestBody(required = false) AutowirePlan.PreviewRequest body) {
        return Result.ok(interactionAutowireService.preview(id, body));
    }

    @PostMapping("/{id}/autowire/apply")
    public Result<AutowirePlan.ApplyResult> applyAutowire(@PathVariable Long id, @RequestBody AutowirePlan.ApplyRequest body) {
        return Result.ok(interactionAutowireService.apply(id, body));
    }

    @GetMapping("/{id}/autowire/applications/{applicationId}")
    public Result<AutowirePlan.ApplyResult> autowireStatus(@PathVariable Long id, @PathVariable String applicationId) {
        return Result.ok(interactionAutowireService.status(id, applicationId));
    }

    @PostMapping("/{id}/autowire/applications/{applicationId}/retry-render")
    public Result<AutowirePlan.ApplyResult> retryAutowireRender(@PathVariable Long id, @PathVariable String applicationId) {
        return Result.ok(interactionAutowireService.retry(id, applicationId));
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

    /**
     * 新建空白画板/框架 (Figma Frame 工具支持)
     */
    @PostMapping("/{id}/pages")
    public Result<Page> createPage(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        return Result.ok(projectService.createPage(id, body));
    }

    /**
     * 删除指定画板/页面
     */
    @DeleteMapping("/{id}/pages/{pageId}")
    public Result<Void> deletePage(
            @PathVariable Long id,
            @PathVariable Long pageId) {
        projectService.deletePage(id, pageId);
        return Result.ok(null);
    }

    // ==========================================
    // Figma 风格评论系统 (Comment Thread & Reply)
    // ==========================================

    /**
     * 查询项目下全部评论线程（含回复列表）
     */
    @GetMapping("/{id}/comments")
    public Result<List<CommentThread>> listComments(@PathVariable Long id) {
        return Result.ok(projectService.getProjectComments(id));
    }

    /**
     * 在画板上发表新评论（创建线程 + 首条评论）
     */
    @PostMapping("/{id}/comments")
    public Result<CommentThread> createComment(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        return Result.ok(projectService.createCommentThread(id, body));
    }

    /**
     * 为评论线程追加回复
     */
    @PostMapping("/{id}/comments/{threadId}/replies")
    public Result<CommentReply> addReply(
            @PathVariable Long id,
            @PathVariable Long threadId,
            @RequestBody Map<String, Object> body) {
        return Result.ok(projectService.addCommentReply(id, threadId, body));
    }

    /**
     * 解决 / 重新打开评论线程
     */
    @PutMapping("/{id}/comments/{threadId}/resolve")
    public Result<CommentThread> toggleResolve(
            @PathVariable Long id,
            @PathVariable Long threadId,
            @RequestBody(required = false) Map<String, Object> body) {
        return Result.ok(projectService.toggleResolveCommentThread(id, threadId, body));
    }

    /**
     * 删除整条评论线程
     */
    @DeleteMapping("/{id}/comments/{threadId}")
    public Result<Void> deleteCommentThread(
            @PathVariable Long id,
            @PathVariable Long threadId) {
        projectService.deleteCommentThread(id, threadId);
        return Result.ok(null);
    }
}
