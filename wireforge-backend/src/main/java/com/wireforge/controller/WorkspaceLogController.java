package com.wireforge.controller;

import com.wireforge.common.Result;
import com.wireforge.model.AuthUser;
import com.wireforge.service.AuthService;
import com.wireforge.service.LogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 主页上的项目级审计：创建、改名、改说明、删除。不包含画布里的画板和评论记录。
 */
@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class WorkspaceLogController {

    private final LogService logService;
    private final AuthService authService;

    @GetMapping("/projects")
    public Result<?> listProjectOperations(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String actionType,
            HttpServletRequest request) {
        AuthUser currentUser = authService.getCurrentUser(request);
        if (currentUser == null) {
            return Result.error(401, "请先登录飞书账号");
        }
        if (!authService.isUserAdmin(currentUser)) {
            return Result.error(403, "仅管理员可查看项目操作日志");
        }
        return Result.ok(logService.listProjectOperations(page, pageSize, keyword, actionType));
    }
}
