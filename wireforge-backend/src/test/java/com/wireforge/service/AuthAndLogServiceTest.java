package com.wireforge.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wireforge.common.Result;
import com.wireforge.controller.LogController;
import com.wireforge.controller.WorkspaceLogController;
import com.wireforge.entity.AppUser;
import com.wireforge.entity.AuthSession;
import com.wireforge.entity.OperationLog;
import com.wireforge.entity.ProjectMember;
import com.wireforge.mapper.AppUserMapper;
import com.wireforge.mapper.AuthSessionMapper;
import com.wireforge.mapper.OperationLogMapper;
import com.wireforge.mapper.ProjectMemberMapper;
import com.wireforge.model.AuthUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthAndLogServiceTest {

    static final String ADMIN_OPEN_ID = "ou_admin_001";

    private ProjectMemberMapper projectMemberMapper;
    private OperationLogMapper operationLogMapper;
    private AuthSessionMapper authSessionMapper;
    private final Map<String, AuthSession> sessionTable = new HashMap<>();
    private AppUserMapper appUserMapper;
    private final Map<String, AppUser> userTable = new HashMap<>();
    private AuthService authService;
    private LogService logService;
    private LogController logController;
    private WorkspaceLogController workspaceLogController;

    @BeforeEach
    void setUp() {
        projectMemberMapper = mock(ProjectMemberMapper.class);
        when(projectMemberMapper.selectCount(any())).thenReturn(1L);
        operationLogMapper = mock(OperationLogMapper.class);
        authSessionMapper = fakeSessionMapper(sessionTable);
        appUserMapper = fakeUserMapper(userTable);
        logService = new LogService(operationLogMapper);
        authService = newAuthService();
        logController = new LogController(logService, authService);
        workspaceLogController = new WorkspaceLogController(logService, authService);
    }

    static AuthSessionMapper fakeSessionMapper(Map<String, AuthSession> table) {
        AuthSessionMapper mapper = mock(AuthSessionMapper.class);
        when(mapper.insert(any(AuthSession.class))).thenAnswer(inv -> {
            AuthSession s = inv.getArgument(0);
            table.put(s.getToken(), s);
            return 1;
        });
        when(mapper.selectById(anyString())).thenAnswer(inv -> table.get(inv.<String>getArgument(0)));
        when(mapper.deleteById(anyString())).thenAnswer(inv -> table.remove(inv.<String>getArgument(0)) != null ? 1 : 0);
        return mapper;
    }

    static AppUserMapper fakeUserMapper(Map<String, AppUser> table) {
        AppUserMapper mapper = mock(AppUserMapper.class);
        when(mapper.insert(any(AppUser.class))).thenAnswer(inv -> {
            AppUser u = inv.getArgument(0);
            table.put(u.getUserId(), u);
            return 1;
        });
        when(mapper.updateById(any(AppUser.class))).thenAnswer(inv -> {
            AppUser u = inv.getArgument(0);
            table.put(u.getUserId(), u);
            return 1;
        });
        when(mapper.selectById(anyString())).thenAnswer(inv -> table.get(inv.<String>getArgument(0)));
        return mapper;
    }

    private AuthService newAuthService() {
        AuthService service = new AuthService(projectMemberMapper, logService, authSessionMapper, appUserMapper);
        ReflectionTestUtils.setField(service, "adminIds", ADMIN_OPEN_ID);
        ReflectionTestUtils.setField(service, "mockLoginEnabled", true);
        return service;
    }

    private MockHttpServletRequest requestAs(String userId, String userName) {
        String token = (String) authService.mockLogin(Map.of("userId", userId, "userName", userName)).get("token");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    @Test
    void testFeishuConfigWhenNotConfigured() {
        Map<String, Object> config = authService.getFeishuConfig(null);
        assertEquals(false, config.get("feishuEnabled"));
        assertEquals("", config.get("appId"));
        assertEquals("", config.get("authUrl"));
    }

    @Test
    void testMockLoginRefusedWhenDisabled() {
        ReflectionTestUtils.setField(authService, "mockLoginEnabled", false);
        assertThrows(IllegalStateException.class, () -> authService.mockLogin(Map.of("userId", "u1")));
        assertThrows(IllegalStateException.class, () -> authService.feishuCallback("mock_abc"));
    }

    @Test
    void testMockLoginIgnoresRequestedRole() {
        Map<String, Object> result = authService.mockLogin(Map.of(
                "userId", "user_001", "userName", "测试工程师", "role", "admin"));
        assertEquals(false, result.get("systemAdmin"));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + result.get("token"));
        AuthUser user = authService.getCurrentUser(request);
        assertEquals("user_001", user.getUserId());
        assertEquals("测试工程师", user.getUserName());
        assertEquals("viewer", authService.getUserProjectRole(1L, user));
    }

    @Test
    void testSelfReportedHeadersAreIgnored() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", ADMIN_OPEN_ID);
        request.addHeader("X-User-Name", "李浩城");
        request.addHeader("X-User-Role", "admin");
        assertNull(authService.getCurrentUser(request));
    }

    @Test
    void testSessionSurvivesBackendRestart() {
        String token = (String) authService.mockLogin(Map.of("userId", "u_restart", "userName", "重启测试")).get("token");

        AuthService restarted = newAuthService();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        assertEquals("u_restart", restarted.getCurrentUser(request).getUserId());
    }

    @Test
    void testExpiredSessionIsRejectedAndRemoved() {
        sessionTable.put("old_token", AuthSession.builder()
                .token("old_token").userId("u_old").userName("过期用户")
                .createdAt(LocalDateTime.now().minusDays(8))
                .expiresAt(LocalDateTime.now().minusDays(1))
                .build());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer old_token");
        assertNull(authService.getCurrentUser(request));
        assertFalse(sessionTable.containsKey("old_token"));
    }

    @Test
    void testLogoutRevokesToken() {
        MockHttpServletRequest request = requestAs("u_logout", "退出测试");
        authService.logout(request);

        MockHttpServletRequest again = new MockHttpServletRequest();
        again.addHeader("Authorization", request.getHeader("Authorization"));
        assertNull(authService.getCurrentUser(again));
    }

    @Test
    void testSystemAdminMatchesFeishuIdOnly() {
        AuthUser sameName = AuthUser.builder().userId("ou_other").userName("李浩城").build();
        assertFalse(authService.isUserAdmin(sameName), "同名不算管理员");

        AuthUser admin = AuthUser.builder().userId(ADMIN_OPEN_ID).openId(ADMIN_OPEN_ID).userName("任意名字").build();
        assertTrue(authService.isUserAdmin(admin));
        assertEquals("admin", authService.getUserProjectRole(100L, admin));
        verify(projectMemberMapper, never()).insert(any(ProjectMember.class));
    }

    @Test
    void testProjectRoleComesFromMemberListWithoutWriting() {
        AuthUser user = AuthUser.builder().userId("ou_editor").userName("编辑同事").build();
        when(projectMemberMapper.selectOne(any())).thenReturn(
                ProjectMember.builder().projectId(1L).userId("ou_editor").role("editor").build());
        assertEquals("editor", authService.getUserProjectRole(1L, user));

        when(projectMemberMapper.selectOne(any())).thenReturn(null);
        assertEquals("viewer", authService.getUserProjectRole(1L, user));
        assertEquals("viewer", authService.getUserProjectRole(1L, null));

        verify(projectMemberMapper, never()).insert(any(ProjectMember.class));
        verify(projectMemberMapper, never()).updateById(any(ProjectMember.class));
    }

    @Test
    void testLookupFindsNameOfAnyoneWhoLoggedIn() {
        requestAs("ou_colleague", "王小明");

        Map<String, Object> found = authService.lookupUser("ou_colleague");
        assertEquals(true, found.get("found"));
        assertEquals("王小明", found.get("userName"));

        Map<String, Object> unknown = authService.lookupUser("ou_never_logged_in");
        assertEquals(false, unknown.get("found"));
    }

    @Test
    void testAddedMemberGetsKnownNameInsteadOfId() {
        requestAs("ou_colleague", "王小明");
        when(projectMemberMapper.selectOne(any())).thenReturn(null);

        ProjectMember named = authService.saveOrUpdateProjectMember(1L, "ou_colleague", "ou_colleague", "", "editor");
        assertEquals("王小明", named.getUserName());

        ProjectMember unnamed = authService.saveOrUpdateProjectMember(1L, "ou_stranger", "ou_stranger", "", "viewer");
        assertEquals("ou_stranger", unnamed.getUserName());
    }

    @Test
    void testMemberListFillsInNamesLearnedLater() {
        ProjectMember unnamed = ProjectMember.builder().id(5L).projectId(1L)
                .userId("ou_late").userName("ou_late").role("editor").build();
        when(projectMemberMapper.selectList(any())).thenReturn(List.of(unnamed));
        requestAs("ou_late", "后来登录的人");

        List<ProjectMember> members = authService.listProjectMembers(1L);
        assertEquals("后来登录的人", members.get(0).getUserName());
        verify(projectMemberMapper).updateById(unnamed);
    }

    @Test
    void testMeReportsSystemAdminAndProjectRole() {
        MockHttpServletRequest request = requestAs(ADMIN_OPEN_ID, "李浩城");
        Map<String, Object> me = authService.getMe(5L, request);
        assertEquals(true, me.get("authenticated"));
        assertEquals(true, me.get("systemAdmin"));
        assertEquals("admin", me.get("projectRole"));

        Map<String, Object> guest = authService.getMe(5L, new MockHttpServletRequest());
        assertEquals(false, guest.get("authenticated"));
        assertEquals(false, guest.get("systemAdmin"));
        assertEquals("viewer", guest.get("projectRole"));
    }

    @Test
    void testProjectLogsRejectViewer() {
        when(projectMemberMapper.selectOne(any())).thenReturn(
                ProjectMember.builder().projectId(1L).userId("viewer_001").role("viewer").build());

        Result<?> result = logController.listLogs(1L, 1, 20, null, null, requestAs("viewer_001", "普通查看者"));
        assertEquals(403, result.getCode());
        verify(operationLogMapper, never()).selectPage(any(), any());
    }

    @Test
    void testProjectLogs401WhenNotLoggedIn() {
        Result<?> result = logController.listLogs(1L, 1, 20, null, null, new MockHttpServletRequest());
        assertEquals(401, result.getCode());
        assertEquals("请先登录飞书账号", result.getMessage());
    }

    @Test
    void testProjectLogsAllowProjectAdmin() {
        when(projectMemberMapper.selectOne(any())).thenReturn(
                ProjectMember.builder().projectId(1L).userId("admin_demo").role("admin").build());
        IPage<OperationLog> mockPage = new Page<>(1, 20);
        mockPage.setRecords(List.of(OperationLog.builder().id(1L).projectId(1L)
                .actionType("PAGE_CREATE").actionDesc("新建空白画板").userName("管理员")
                .createdAt(LocalDateTime.now()).build()));
        when(operationLogMapper.selectPage(any(), any())).thenReturn(mockPage);

        Result<?> result = logController.listLogs(1L, 1, 20, null, null, requestAs("admin_demo", "管理员"));
        assertEquals(0, result.getCode());
        verify(operationLogMapper, times(1)).selectPage(any(), any());
    }

    @Test
    void testPostedLogUsesLoggedInUser() {
        MockHttpServletRequest request = requestAs("ou_real", "真实的人");
        logController.createLog(1L, Map.of(
                "userId", "ou_fake", "userName", "冒充的人",
                "actionType", "PAGE_EDIT", "actionDesc", "修改画板", "targetName", "首页"), request);

        ArgumentCaptor<OperationLog> captor = ArgumentCaptor.forClass(OperationLog.class);
        verify(operationLogMapper).insert(captor.capture());
        assertEquals("ou_real", captor.getValue().getUserId());
        assertEquals("真实的人", captor.getValue().getUserName());
        assertEquals("PAGE_EDIT", captor.getValue().getActionType());
    }

    @Test
    void testPostedLogCannotForgeProjectOperations() {
        logController.createLog(1L, Map.of("actionType", "PROJECT_DELETE", "actionDesc", "删除了项目「X」"),
                requestAs("ou_real", "真实的人"));

        ArgumentCaptor<OperationLog> captor = ArgumentCaptor.forClass(OperationLog.class);
        verify(operationLogMapper).insert(captor.capture());
        assertEquals("OPERATION", captor.getValue().getActionType());
    }

    @Test
    void testRecordLog() {
        logService.record(1L, "u1", "张工", "avatar.jpg", "PAGE_EDIT", "修改画板", "首页", "127.0.0.1");

        ArgumentCaptor<OperationLog> captor = ArgumentCaptor.forClass(OperationLog.class);
        verify(operationLogMapper).insert(captor.capture());
        OperationLog captured = captor.getValue();
        assertEquals(1L, captured.getProjectId());
        assertEquals("u1", captured.getUserId());
        assertEquals("张工", captured.getUserName());
        assertEquals("PAGE_EDIT", captured.getActionType());
        assertEquals("修改画板", captured.getActionDesc());
        assertEquals("首页", captured.getTargetName());
    }

    @Test
    void testWorkspaceProjectLogsRejectGuestAndNonAdmin() {
        Result<?> guestResult = workspaceLogController.listProjectOperations(1, 20, null, null, new MockHttpServletRequest());
        assertEquals(401, guestResult.getCode());

        Result<?> userResult = workspaceLogController.listProjectOperations(1, 20, null, null,
                requestAs("ou_someone", "普通同事"));
        assertEquals(403, userResult.getCode());
        verify(operationLogMapper, never()).selectPage(any(), any());
    }

    @Test
    void testWorkspaceProjectLogsAllowSystemAdmin() {
        IPage<OperationLog> mockPage = new Page<>(1, 20);
        mockPage.setRecords(List.of(OperationLog.builder().id(9L).projectId(3L)
                .actionType("PROJECT_DELETE").actionDesc("删除了项目「旧项目」").targetName("旧项目")
                .userName("李浩城").createdAt(LocalDateTime.now()).build()));
        when(operationLogMapper.selectPage(any(), any())).thenReturn(mockPage);

        Result<?> result = workspaceLogController.listProjectOperations(1, 20, null, null,
                requestAs(ADMIN_OPEN_ID, "李浩城"));
        assertEquals(0, result.getCode());
        verify(operationLogMapper, times(1)).selectPage(any(), any());
    }
}
