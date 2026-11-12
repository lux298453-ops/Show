package com.wireforge.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WebConfigTest {
    private static class Registry extends CorsRegistry {
        Map<String, CorsConfiguration> configurations() { return getCorsConfigurations(); }
    }

    private CorsConfiguration policy(String origins) {
        WebConfig config = new WebConfig(null);
        ReflectionTestUtils.setField(config, "allowedOrigins", origins);
        Registry registry = new Registry();
        config.addCorsMappings(registry);
        return registry.configurations().get("/**");
    }

    @Test void onlyConfiguredOriginsCanUseBearerRequestsAndCookiesAreNeverAllowed() {
        CorsConfiguration cors = policy("http://localhost:5173, https://wireforge.example.com");
        assertEquals("http://localhost:5173", cors.checkOrigin("http://localhost:5173"));
        assertEquals("https://wireforge.example.com", cors.checkOrigin("https://wireforge.example.com"));
        assertNull(cors.checkOrigin("https://evil.example.com"));
        assertNull(cors.checkOrigin("https://wireforge.example.com.evil.example"));
        assertNull(cors.checkOrigin("null"));
        assertEquals(false, cors.getAllowCredentials());
        assertNotNull(cors.checkHeaders(java.util.List.of("Authorization", "Content-Type")));
    }

    @Test void wildcardConfigurationCannotAccidentallyReopenAllOrigins() {
        CorsConfiguration cors = policy("*,https://*.example.com,null,");
        assertNull(cors.checkOrigin("https://evil.example.com"));
        assertNull(cors.checkOrigin("null"));
    }
}
