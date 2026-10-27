package com.wireforge.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wireforge.entity.AuthSession;
import com.wireforge.entity.ProjectMember;
import com.wireforge.mapper.AuthSessionMapper;
import com.wireforge.mapper.OperationLogMapper;
import com.wireforge.mapper.ProjectMemberMapper;
import com.wireforge.service.AuthService;
import com.wireforge.service.LogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;

import static com.wireforge.config.PermissionInterceptor.Need.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PermissionInterceptorTest {

    private ProjectMemberMapper projectMemberMapper;
    private AuthService authService;
    private PermissionInterceptor interceptor;

    @BeforeEach
    void setUp() {
        projectMemberMapper = mock(ProjectMemberMapper.class);
        when(projectMemberMapper.selectCount(any())).thenReturn(1L);
        Map<String, AuthSession> table = new HashMap<>();
        AuthSessionMapper sessionMapper = mock(AuthSessionMapper.class);
        when(sessionMapper.insert(any(AuthSession.class))).thenAnswer(inv -> {
            AuthSession s = inv.getArgument(0);
            table.put(s.getToken(), s);
            return 1;
        });
        when(sessionMapper.selectById(anyString())).thenAnswer(inv -> table.get(inv.<String>getArgument(0)));
        authService = new AuthService(projectMemberMapper, new LogService(mock(OperationLogMapper.class)), sessionMapper,
                mock(com.wireforge.mapper.AppUserMapper.class));
        ReflectionTestUtils.setField(authService, "adminIds", "ou_sys_admin");
        ReflectionTestUtils.setField(authService, "mockLoginEnabled", true);
        interceptor = new PermissionInterceptor(authService, new ObjectMapper());
    }

    private void memberRole(String role) {
        when(projectMemberMapper.selectOne(any())).thenReturn(
                role == null ? null : ProjectMember.builder().projectId(7L).userId("ou_user").role(role).build());
    }

    private MockHttpServletResponse call(String method, String path, String userId) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        if (userId != null) {
            String token = (String) authService.mockLogin(Map.of("userId", userId, "userName", "测试")).get("token");
            request.addHeader("Authorization", "Bearer " + token);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean passed = interceptor.preHandle(request, response, new Object());
        assertEquals(passed, response.getStatus() == 200, "放行时不应写入拒绝状态");
        return response;
    }

    @Test
    void requirementTable() {
        assertEquals(OPEN, PermissionInterceptor.requirement("GET", "/api/projects/7/prototype"));
        assertEquals(OPEN, PermissionInterceptor.requirement("POST", "/api/auth/feishu/callback"));
        assertEquals(OPEN, PermissionInterceptor.requirement("OPTIONS", "/api/projects/7/pages/1/html"));
        assertEquals(LOGIN, PermissionInterceptor.requirement("POST", "/api/projects"));
        assertEquals(PROJECT_ADMIN, PermissionInterceptor.requirement("DELETE", "/api/projects/7"));
        assertEquals(PROJECT_ADMIN, PermissionInterceptor.requirement("PUT", "/api/projects/7"));
        assertEquals(LOGIN, PermissionInterceptor.requirement("POST", "/api/projects/7/comments"));
        assertEquals(LOGIN, PermissionInterceptor.requirement("POST", "/api/projects/7/comments/3/replies"));
        assertEquals(LOGIN, PermissionInterceptor.requirement("PUT", "/api/projects/7/comments/3/resolve"));
        assertEquals(LOGIN, PermissionInterceptor.requirement("GET", "/api/projects/7/members"));
        assertEquals(PROJECT_ADMIN, PermissionInterceptor.requirement("POST", "/api/projects/7/members"));
        assertEquals(PROJECT_ADMIN, PermissionInterceptor.requirement("GET", "/api/projects/7/members/lookup"));
        assertEquals(PROJECT_ADMIN, PermissionInterceptor.requirement("PUT", "/api/projects/7/members/ou_x/role"));
        assertEquals(PROJECT_ADMIN, PermissionInterceptor.requirement("DELETE", "/api/projects/7/members/ou_x"));
        assertEquals(PROJECT_ADMIN, PermissionInterceptor.requirement("GET", "/api/projects/7/logs"));
        assertEquals(SYSTEM_ADMIN, PermissionInterceptor.requirement("GET", "/api/logs/projects"));
        assertEquals(EDITOR, PermissionInterceptor.requirement("PUT", "/api/projects/7/pages/1/html"));
        assertEquals(EDITOR, PermissionInterceptor.requirement("POST", "/api/projects/7/reanalyze-all"));
        assertEquals(EDITOR, PermissionInterceptor.requirement("DELETE", "/api/projects/7/pages/1"));
        assertEquals(LOGIN, PermissionInterceptor.requirement("POST", "/api/assets/upload"));
        assertEquals(LOGIN, PermissionInterceptor.requirement("GET", "/api/components/mine"));
        assertEquals(LOGIN, PermissionInterceptor.requirement("POST", "/api/components"));
        assertEquals(LOGIN, PermissionInterceptor.requirement("PUT", "/api/components/3"));
        assertEquals(LOGIN, PermissionInterceptor.requirement("DELETE", "/api/components/3"));
    }

    @Test
    void guestCanReadButNotWrite() throws Exception {
        assertEquals(200, call("GET", "/api/projects/7/prototype", null).getStatus());
        MockHttpServletResponse res = call("POST", "/api/projects/7/comments", null);
        assertEquals(401, res.getStatus());
        assertTrue(res.getContentAsString(java.nio.charset.StandardCharsets.UTF_8).contains("请先登录飞书账号"));
    }

    @Test
    void viewerCanCommentButNotEdit() throws Exception {
        memberRole(null);
        assertEquals(200, call("POST", "/api/projects/7/comments", "ou_user").getStatus());
        assertEquals(403, call("PUT", "/api/projects/7/pages/1/html", "ou_user").getStatus());
        assertEquals(403, call("POST", "/api/projects/7/members", "ou_user").getStatus());
    }

    @Test
    void editorCanEditButNotManageMembersOrDelete() throws Exception {
        memberRole("editor");
        assertEquals(200, call("PUT", "/api/projects/7/pages/1/html", "ou_user").getStatus());
        assertEquals(403, call("POST", "/api/projects/7/members", "ou_user").getStatus());
        assertEquals(403, call("DELETE", "/api/projects/7", "ou_user").getStatus());
        assertEquals(403, call("GET", "/api/projects/7/logs", "ou_user").getStatus());
    }

    @Test
    void projectAdminManagesOwnProjectOnly() throws Exception {
        memberRole("admin");
        assertEquals(200, call("POST", "/api/projects/7/members", "ou_user").getStatus());
        assertEquals(200, call("DELETE", "/api/projects/7", "ou_user").getStatus());
        assertEquals(403, call("GET", "/api/logs/projects", "ou_user").getStatus());
    }

    @Test
    void systemAdminPassesEverywhere() throws Exception {
        memberRole(null);
        assertEquals(200, call("GET", "/api/logs/projects", "ou_sys_admin").getStatus());
        assertEquals(200, call("DELETE", "/api/projects/7", "ou_sys_admin").getStatus());
        assertEquals(200, call("PUT", "/api/projects/7/pages/1/html", "ou_sys_admin").getStatus());
    }
}
