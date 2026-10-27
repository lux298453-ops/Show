package com.wireforge.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wireforge.common.Result;
import com.wireforge.model.AuthUser;
import com.wireforge.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 所有接口的权限都在这里按「什么请求 → 要什么身份」查表决定。
 * 查看类请求默认对所有人开放；项目内的修改默认要编辑者；未列出的其它修改要登录。
 */
@Component
@RequiredArgsConstructor
public class PermissionInterceptor implements HandlerInterceptor {

    enum Need { OPEN, LOGIN, EDITOR, PROJECT_ADMIN, SYSTEM_ADMIN }

    private record Rule(String method, String pattern, Need need) {}

    private static final String PROJECT_SCOPE = "/api/projects/{id}/**";

    private static final List<Rule> RULES = List.of(
            new Rule("*", "/api/auth/**", Need.OPEN),
            new Rule("*", "/api/projects/{id}/sync-elfship-interactions", Need.OPEN),
            new Rule("GET", "/api/logs/**", Need.SYSTEM_ADMIN),
            new Rule("GET", "/api/projects/{id}/logs", Need.PROJECT_ADMIN),
            new Rule("POST", "/api/projects/{id}/logs", Need.LOGIN),
            new Rule("GET", "/api/projects/{id}/members", Need.LOGIN),
            new Rule("*", "/api/projects/{id}/members", Need.PROJECT_ADMIN),
            new Rule("*", "/api/projects/{id}/members/**", Need.PROJECT_ADMIN),
            new Rule("POST", "/api/projects", Need.LOGIN),
            new Rule("PUT", "/api/projects/{id}", Need.PROJECT_ADMIN),
            new Rule("DELETE", "/api/projects/{id}", Need.PROJECT_ADMIN),
            new Rule("POST", "/api/projects/{id}/comments", Need.LOGIN),
            new Rule("POST", "/api/projects/{id}/comments/*/replies", Need.LOGIN),
            new Rule("PUT", "/api/projects/{id}/comments/*/resolve", Need.LOGIN),
            // 只读评审可以删自己发的评论，是否本人由接口里判断
            new Rule("DELETE", "/api/projects/{id}/comments/*", Need.LOGIN),
            new Rule("POST", "/api/assets/upload", Need.LOGIN),
            new Rule("*", "/api/components", Need.LOGIN),
            new Rule("*", "/api/components/**", Need.LOGIN)
    );

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    private final AuthService authService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        String method = request.getMethod();
        String path = request.getRequestURI().substring(request.getContextPath().length());
        Need need = requirement(method, path);
        if (need == Need.OPEN) return true;

        AuthUser user = authService.getCurrentUser(request);
        if (user == null) {
            return deny(response, 401, "请先登录飞书账号");
        }
        switch (need) {
            case SYSTEM_ADMIN -> {
                if (!authService.isUserAdmin(user)) return deny(response, 403, "只有系统管理员可以查看");
            }
            case PROJECT_ADMIN -> {
                if (!AuthService.ADMIN.equals(authService.getUserProjectRole(projectId(path), user))) {
                    return deny(response, 403, "只有这个项目的管理员可以进行此操作");
                }
            }
            case EDITOR -> {
                if (!AuthService.canEdit(authService.getUserProjectRole(projectId(path), user))) {
                    return deny(response, 403, "你在这个项目里是只读评审，不能修改，可以请项目管理员把你设为编辑者");
                }
            }
            default -> { }
        }
        return true;
    }

    static Need requirement(String method, String path) {
        if ("OPTIONS".equalsIgnoreCase(method)) return Need.OPEN;
        for (Rule rule : RULES) {
            if (("*".equals(rule.method()) || rule.method().equalsIgnoreCase(method))
                    && MATCHER.match(rule.pattern(), path)) {
                return rule.need();
            }
        }
        if ("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method)) return Need.OPEN;
        if (MATCHER.match(PROJECT_SCOPE, path) || MATCHER.match("/api/projects/{id}", path)) return Need.EDITOR;
        return Need.LOGIN;
    }

    private static Long projectId(String path) {
        String pattern = MATCHER.match(PROJECT_SCOPE, path) ? PROJECT_SCOPE : "/api/projects/{id}";
        Map<String, String> vars = MATCHER.extractUriTemplateVariables(pattern, path);
        try {
            return Long.valueOf(vars.get("id"));
        } catch (Exception e) {
            return null;
        }
    }

    private boolean deny(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Result.error(status, message)));
        return false;
    }
}
