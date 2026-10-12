package com.wireforge.service;

import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.util.Map;

/** Signs the shared test HTTP client in as a system admin so API writes pass the permission gate. */
final class IntegrationAuth {
    static final String ADMIN_PROPERTY = "wireforge.auth.admins=ou_integration_admin";

    private IntegrationAuth() {}

    static void signInAsAdmin(TestRestTemplate http, AuthService auth) {
        var interceptors = http.getRestTemplate().getInterceptors();
        if (interceptors.stream().anyMatch(i -> i instanceof Bearer)) return;
        String token = (String) auth.mockLogin(Map.of("userId", "ou_integration_admin", "userName", "集成测试")).get("token");
        interceptors.add(new Bearer(token));
    }

    private record Bearer(String token) implements ClientHttpRequestInterceptor {
        @Override
        public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
            request.getHeaders().setBearerAuth(token);
            return execution.execute(request, body);
        }
    }
}
