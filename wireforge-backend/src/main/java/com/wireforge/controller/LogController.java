package com.wireforge.controller;

import com.wireforge.common.Result;
import com.wireforge.entity.OperationLog;
import com.wireforge.model.AuthUser;
import com.wireforge.service.AuthService;
import com.wireforge.service.LogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/projects/{id}/logs")
@RequiredArgsConstructor
public class LogController {

    private final LogService logService;
    private final AuthService authService;

    /**
     * 查询项目操作日志列表（支持分页、关键词搜索、actionType 过滤），仅项目管理员
     */
    @GetMapping
    public Result<?> listLogs(
            @PathVariable("id") Long projectId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String actionType,
            HttpServletRequest request) {

        AuthUser currentUser = authService.getCurrentUser(request);
        if (currentUser == null) {
            return Result.error(401, "请先登录飞书账号");
        }
        if (!AuthService.ADMIN.equals(authService.getUserProjectRole(projectId, currentUser))) {
            return Result.error(403, "仅项目管理员可查看该项目的操作审计日志");
        }

        Map<String, Object> logPage = logService.listLogs(projectId, page, pageSize, keyword, actionType);
        return Result.ok(logPage);
    }

    /**
     * 记录项目操作日志，操作人取当前登录的人
     */
    @PostMapping
    public Result<OperationLog> createLog(
            @PathVariable("id") Long projectId,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        AuthUser currentUser = authService.getCurrentUser(request);
        if (currentUser == null) {
            return Result.error(401, "请先登录飞书账号");
        }
        return Result.ok(logService.recordFromApi(projectId, body, currentUser, request));
    }
}
