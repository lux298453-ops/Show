package com.wireforge.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wireforge.common.GlobalExceptionHandler;
import com.wireforge.controller.AssetController;
import com.wireforge.service.AssetService;
import com.wireforge.service.AuthService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Uses the real MVC configuration so /files resources cannot bypass the asset response policy. */
class AssetSecurityHeadersTest {
    @TempDir Path designs;
    private AnnotationConfigWebApplicationContext context;
    private AssetService assets;
    private MockMvc mvc;
    private static final byte[] ACTIVE = ("<svg xmlns='http://www.w3.org/2000/svg' width='80' height='80' onload='window.__svgOnloadProbe=1'>"
            + "<script>window.__svgScriptProbe=1</script><rect width='80' height='80' fill='#f80'/></svg>")
            .getBytes(StandardCharsets.UTF_8);

    @RestController
    static class ReadOnlyPage {
        @GetMapping("/api/projects/7/prototype") Map<String, Object> prototype() { return Map.of("pages", java.util.List.of()); }
    }

    @Configuration
    @EnableWebMvc
    static class TestConfig {
        @Bean AuthService auth() { return mock(AuthService.class); }
        @Bean AssetService assets() { return mock(AssetService.class); }
        @Bean PermissionInterceptor permissions(AuthService auth) { return new PermissionInterceptor(auth, new ObjectMapper()); }
        @Bean WebConfig webConfig(PermissionInterceptor permissions, org.springframework.core.env.Environment env) {
            WebConfig config = new WebConfig(permissions);
            ReflectionTestUtils.setField(config, "designsDir", env.getRequiredProperty("test.designs"));
            return config;
        }
        @Bean AssetController controller(AssetService assets, AuthService auth) { return new AssetController(assets, auth); }
        @Bean GlobalExceptionHandler errors() { return new GlobalExceptionHandler(); }
        @Bean ReadOnlyPage readOnlyPage() { return new ReadOnlyPage(); }
    }

    @BeforeEach void setup() throws Exception {
        Files.createDirectories(designs.resolve("assets"));
        Files.write(designs.resolve("assets/legacy.SVG"), ACTIVE);
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        String root = designs.toString().replace('\\', '/');
        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context, "test.designs=" + root,
                "wireforge.designs-dir=" + root);
        context.register(TestConfig.class);
        context.refresh();
        assets = context.getBean(AssetService.class);
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @AfterEach void close() { if (context != null) context.close(); }

    @Test void publicSvgGetAndHeadHaveSandboxAndNoSniffHeaders() throws Exception {
        byte[] svg = "<svg xmlns='http://www.w3.org/2000/svg'><rect width='10' height='10'/></svg>".getBytes(StandardCharsets.UTF_8);
        when(assets.getAssetResource("u-safe")).thenReturn(new AssetService.NamedByteArrayResource(svg, "safe.svg", "image/svg+xml"));
        for (var request : new org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder[]{get("/api/assets/u-safe"), head("/api/assets/u-safe")}) {
            mvc.perform(request).andExpect(status().isOk())
                    .andExpect(header().string("Content-Security-Policy", AssetSecurityHeadersInterceptor.CONTENT_SECURITY_POLICY))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(content().contentType("image/svg+xml"));
        }
        mvc.perform(get("/api/assets/u-safe")).andExpect(content().bytes(svg));
    }

    @Test void legacyStaticFilesAndRangeResponsesReceiveTheSamePolicy() throws Exception {
        for (var request : new org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder[]{get("/files/assets/legacy.SVG"), head("/files/assets/legacy.SVG")}) {
            mvc.perform(request).andExpect(status().isOk())
                    .andExpect(header().string("Content-Security-Policy", AssetSecurityHeadersInterceptor.CONTENT_SECURITY_POLICY))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                    .andExpect(header().string("Cache-Control", "no-store"));
        }
        mvc.perform(get("/files/assets/legacy.SVG").header("Range", "bytes=0-15"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string("Content-Security-Policy", AssetSecurityHeadersInterceptor.CONTENT_SECURITY_POLICY));
    }

    @Test void unsafeStoredAssetsAreNotReturnedAndUploadsStillRequireLogin() throws Exception {
        when(assets.getAssetResource("u-unsafe")).thenThrow(new IllegalArgumentException("不安全的 SVG"));
        mvc.perform(get("/api/assets/u-unsafe")).andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Security-Policy", AssetSecurityHeadersInterceptor.CONTENT_SECURITY_POLICY))
                .andExpect(jsonPath("$.message").value("不安全的 SVG"));
        mvc.perform(post("/api/assets/upload")).andExpect(status().isUnauthorized());
        verify(assets, never()).saveUploadedAsset(any(), any(), any(), any(), any());
    }

    @Test void policyIsScopedToRawResourcesAndDoesNotSandboxProjectApiResponses() throws Exception {
        mvc.perform(get("/api/projects/7/prototype"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Content-Security-Policy"));
        byte[] png = new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47};
        when(assets.getAssetResource("u-png")).thenReturn(new AssetService.NamedByteArrayResource(png, "pet.png", "image/png"));
        mvc.perform(get("/api/assets/u-png")).andExpect(status().isOk())
                .andExpect(content().contentType("image/png")).andExpect(content().bytes(png));
        String policy = AssetSecurityHeadersInterceptor.CONTENT_SECURITY_POLICY;
        assertFalse(policy.contains("allow-scripts"));
        assertFalse(policy.contains("allow-same-origin"));
    }

    @Test void exportsProtectedBrowserResponsesFromTheActualMvcHandlers() throws Exception {
        // Deliberately bypass service validation to test response isolation as an independent second boundary.
        when(assets.getAssetResource("u-probe")).thenReturn(new AssetService.NamedByteArrayResource(ACTIVE, "probe.svg", "image/svg+xml"));
        byte[] safe = ("<svg xmlns='http://www.w3.org/2000/svg' width='80' height='80'><defs>"
                + "<linearGradient id='g'><stop stop-color='#f80'/><stop offset='1' stop-color='#fdd'/></linearGradient>"
                + "</defs><rect width='80' height='80' fill='url(#g)'/></svg>").getBytes(StandardCharsets.UTF_8);
        when(assets.getAssetResource("u-safe")).thenReturn(new AssetService.NamedByteArrayResource(safe, "safe.svg", "image/svg+xml"));
        Map<String, Object> fixture = new LinkedHashMap<>();
        for (String path : new String[]{"/files/assets/legacy.SVG", "/api/assets/u-probe", "/api/assets/u-safe"}) {
            var response = mvc.perform(get(path)).andExpect(status().isOk())
                    .andExpect(header().string("Content-Security-Policy", AssetSecurityHeadersInterceptor.CONTENT_SECURITY_POLICY))
                    .andReturn().getResponse();
            Map<String, String> headers = new LinkedHashMap<>();
            response.getHeaderNames().forEach(name -> headers.put(name, response.getHeader(name)));
            fixture.put(path, Map.of("headers", headers, "body", response.getContentAsString(StandardCharsets.UTF_8)));
        }
        Path directory = Path.of("target/svg-security-browser");
        Files.createDirectories(directory);
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(directory.resolve("responses.json").toFile(), fixture);
    }
}
