package com.wireforge.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;
import java.util.Arrays;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final PermissionInterceptor permissionInterceptor;

    @Value("${wireforge.designs-dir}")
    private String designsDir;

    @Value("${wireforge.web.allowed-origins:${CORS_ALLOWED_ORIGINS:http://localhost:5173,http://127.0.0.1:5173}}")
    private String allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(Arrays.stream(allowedOrigins.split(",")).map(String::trim)
                        .filter(origin -> !origin.isEmpty() && !origin.contains("*") && !origin.equals("null"))
                        .toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                .allowedHeaders("*")
                // Authentication uses an explicit Authorization header, never ambient cookies.
                .allowCredentials(false)
                .maxAge(3600);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AssetSecurityHeadersInterceptor())
                .addPathPatterns("/api/assets/**", "/files/**");
        registry.addInterceptor(permissionInterceptor).addPathPatterns("/api/**");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        java.nio.file.Path p = Paths.get(designsDir).toAbsolutePath().normalize();
        String uri = p.toUri().toString();
        if (!uri.endsWith("/")) {
            uri += "/";
        }
        registry.addResourceHandler("/files/**").addResourceLocations(uri);
    }
}
