package com.wireforge.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/** Raw asset documents are untrusted, including legacy files served without AssetController. */
public final class AssetSecurityHeadersInterceptor implements HandlerInterceptor {
    public static final String CONTENT_SECURITY_POLICY = "sandbox; default-src 'none'; script-src 'none'; "
            + "style-src 'unsafe-inline'; img-src data:; object-src 'none'; base-uri 'none'; form-action 'none'";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // No allow-scripts/allow-same-origin: direct SVG navigation cannot acquire application privileges.
        response.setHeader("Content-Security-Policy", CONTENT_SECURITY_POLICY);
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Referrer-Policy", "no-referrer");
        // 允许各接口独立设置 Cache-Control（如角色立绘与切图使用强缓存），杜绝全链路 no-store 导致重复慢速下载
        return true;
    }
}
