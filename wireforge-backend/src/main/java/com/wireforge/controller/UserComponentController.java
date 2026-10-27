package com.wireforge.controller;

import com.wireforge.common.Result;
import com.wireforge.model.AuthUser;
import com.wireforge.service.AuthService;
import com.wireforge.service.UserComponentService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 自定义组件跟着登录账号走，不跟项目。 */
@RestController
@RequestMapping("/api/components")
@RequiredArgsConstructor
public class UserComponentController {
    private final UserComponentService service;
    private final AuthService authService;

    @GetMapping("/mine")
    public Result<List<Map<String, Object>>> mine(HttpServletRequest request) {
        return Result.ok(service.listMine(currentUserId(request)));
    }

    @PostMapping
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return Result.ok(service.create(
                currentUserId(request),
                text(body, "name"),
                text(body, "html"),
                number(body, "width"),
                number(body, "height")));
    }

    @PutMapping("/{id}")
    public Result<Map<String, Object>> rename(@PathVariable Long id, @RequestBody Map<String, Object> body, HttpServletRequest request) {
        return Result.ok(service.rename(currentUserId(request), id, text(body, "name")));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        service.delete(currentUserId(request), id);
        return Result.ok();
    }

    private String currentUserId(HttpServletRequest request) {
        AuthUser user = authService.getCurrentUser(request);
        if (user == null || user.getUserId() == null || user.getUserId().isBlank()) {
            throw new IllegalStateException("请先登录飞书账号");
        }
        return user.getUserId();
    }

    private static String text(Map<String, Object> body, String key) {
        Object value = body == null ? null : body.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private static Integer number(Map<String, Object> body, String key) {
        Object value = body == null ? null : body.get(key);
        if (value instanceof Number number) return number.intValue();
        if (value == null) return null;
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
