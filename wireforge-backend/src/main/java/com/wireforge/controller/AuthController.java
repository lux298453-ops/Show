package com.wireforge.controller;

import com.wireforge.common.Result;
import com.wireforge.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 获取飞书配置（AppId 与跳转授权地址）
     */
    @GetMapping("/feishu/config")
    public Result<Map<String, Object>> getFeishuConfig(@RequestParam(required = false) String redirectUri) {
        return Result.ok(authService.getFeishuConfig(redirectUri));
    }

    /**
     * 飞书 OAuth 登录回调：接收 code 并换取 user_info
     */
    @PostMapping("/feishu/callback")
    public Result<Map<String, Object>> feishuCallback(
            @RequestBody(required = false) Map<String, Object> body,
            @RequestParam(required = false) String code) {
        String authCode = code;
        if (body != null && body.containsKey("code") && body.get("code") != null) {
            authCode = body.get("code").toString();
        }
        return Result.ok(authService.feishuCallback(authCode));
    }

    /**
     * 本地开发快捷登录：入参 { userId, userName, userAvatar }，颁发 Token。需开启 wireforge.auth.mock-login
     */
    @PostMapping("/mock-login")
    public Result<Map<String, Object>> mockLogin(@RequestBody Map<String, Object> body) {
        return Result.ok(authService.mockLogin(body));
    }

    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        authService.logout(request);
        return Result.ok(null);
    }

    /**
     * 获取当前用户信息、是否系统管理员，以及在项目中的角色
     */
    @GetMapping("/me")
    public Result<Map<String, Object>> getMe(
            @RequestParam(required = false) Long projectId,
            HttpServletRequest request) {
        return Result.ok(authService.getMe(projectId, request));
    }
}
