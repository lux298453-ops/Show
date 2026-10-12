package com.wireforge.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wireforge.entity.AppUser;
import com.wireforge.entity.AuthSession;
import com.wireforge.entity.ProjectMember;
import com.wireforge.mapper.AppUserMapper;
import com.wireforge.mapper.AuthSessionMapper;
import com.wireforge.mapper.ProjectMemberMapper;
import com.wireforge.model.AuthUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 登录与权限。
 * 身份只认登录凭证；系统管理员看配置里的飞书用户编号名单；
 * 项目里能做什么看项目成员名单，不在名单里的人只读。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    public static final String ADMIN = "admin";
    public static final String EDITOR = "editor";
    public static final String VIEWER = "viewer";
    private static final Set<String> ROLES = Set.of(ADMIN, EDITOR, VIEWER);
    private static final Duration SESSION_TTL = Duration.ofDays(7);
    private static final String REQUEST_USER_ATTR = AuthService.class.getName() + ".user";

    private final ProjectMemberMapper projectMemberMapper;
    private final LogService logService;
    private final AuthSessionMapper authSessionMapper;
    private final AppUserMapper appUserMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Value("${wireforge.feishu.app-id:}")
    private String configAppId;

    @Value("${wireforge.feishu.app-secret:}")
    private String configAppSecret;

    @Value("${wireforge.feishu.redirect-uri:http://localhost:5173/auth/callback}")
    private String defaultRedirectUri;

    /** 逗号分隔的飞书用户编号（open_id） */
    @Value("${wireforge.auth.admins:}")
    private String adminIds;

    @Value("${wireforge.auth.mock-login:false}")
    private boolean mockLoginEnabled;

    private final Map<String, AuthSession> sessionCache = new ConcurrentHashMap<>();

    private String tenantToken;
    private long tenantTokenExpiresAt;
    /** 飞书应用没开通通讯录权限时，暂停查询一段时间，避免每次都白跑一趟 */
    private volatile long contactUnavailableUntil;

    public static boolean isValidRole(String role) {
        return role != null && ROLES.contains(role);
    }

    public static boolean canEdit(String role) {
        return ADMIN.equals(role) || EDITOR.equals(role);
    }

    public String getAppId() {
        return configAppId != null ? configAppId.trim() : "";
    }

    public String getAppSecret() {
        return configAppSecret != null ? configAppSecret.trim() : "";
    }

    public boolean isFeishuEnabled() {
        return StringUtils.hasText(getAppId()) && StringUtils.hasText(getAppSecret());
    }

    /**
     * 获取飞书配置与授权跳转地址
     */
    public Map<String, Object> getFeishuConfig(String redirectUri) {
        String appId = getAppId();
        boolean enabled = isFeishuEnabled();
        String redirect = StringUtils.hasText(redirectUri) ? redirectUri.trim() : defaultRedirectUri;
        String authUrl = "";
        if (enabled) {
            authUrl = "https://accounts.feishu.cn/open-apis/authen/v1/authorize?client_id=" + appId
                    + "&redirect_uri=" + URLEncoder.encode(redirect, StandardCharsets.UTF_8)
                    + "&response_type=code"
                    + "&state=wireforge";
        }

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("feishuEnabled", enabled);
        config.put("appId", appId);
        config.put("redirectUri", redirect);
        config.put("authUrl", authUrl);
        return config;
    }

    /**
     * 飞书 OAuth 回调：换取用户信息并发放登录凭证
     */
    public Map<String, Object> feishuCallback(String code) {
        if (!StringUtils.hasText(code)) {
            throw new IllegalStateException("授权码 code 不能为空");
        }
        if (code.startsWith("mock_")) {
            if (!mockLoginEnabled) {
                throw new IllegalStateException("演示登录只在本地开发环境可用");
            }
            return issueSession(AuthUser.builder()
                    .userId("mock_" + Math.abs(code.hashCode() % 100000))
                    .userName("飞书演示用户")
                    .userAvatar("")
                    .build());
        }
        if (!isFeishuEnabled()) {
            throw new IllegalStateException("飞书登录未启用：请配置 FEISHU_APP_ID 与 FEISHU_APP_SECRET");
        }

        AuthUser user;
        try {
            String appAccessToken = fetchFeishuAppAccessToken();
            user = fetchFeishuUserInfo(appAccessToken, code);
        } catch (Exception e) {
            log.error("飞书授权登录异常: {}", e.getMessage(), e);
            throw new IllegalStateException("飞书登录换取用户信息失败: " + e.getMessage());
        }
        return issueSession(user);
    }

    /**
     * 获取飞书企业自建应用的 app_access_token
     */
    private String fetchFeishuAppAccessToken() throws Exception {
        Map<String, String> bodyMap = new HashMap<>();
        bodyMap.put("app_id", getAppId());
        bodyMap.put("app_secret", getAppSecret());
        String reqJson = objectMapper.writeValueAsString(bodyMap);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://open.feishu.cn/open-apis/auth/v3/app_access_token/internal"))
                .header("Content-Type", "application/json; charset=utf-8")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(reqJson))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode root = objectMapper.readTree(response.body());
        if (root.path("code").asInt(1) != 0) {
            throw new RuntimeException("飞书接口错误 (" + root.path("code").asInt() + "): " + root.path("msg").asText());
        }
        return root.path("app_access_token").asText();
    }

    /**
     * 通过 code 换取 user_access_token 与用户基本信息
     */
    private AuthUser fetchFeishuUserInfo(String appAccessToken, String code) throws Exception {
        Map<String, String> bodyMap = new HashMap<>();
        bodyMap.put("grant_type", "authorization_code");
        bodyMap.put("code", code);
        String reqJson = objectMapper.writeValueAsString(bodyMap);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://open.feishu.cn/open-apis/authen/v1/oidc/access_token"))
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Authorization", "Bearer " + appAccessToken)
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(reqJson))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode root = objectMapper.readTree(response.body());
        if (root.path("code").asInt(1) != 0) {
            throw new RuntimeException("飞书 OIDC 换取 token 失败: " + root.path("msg").asText());
        }

        JsonNode data = root.path("data");
        String userAccessToken = data.path("access_token").asText("");
        String name = data.path("name").asText("");
        String avatar = data.path("avatar_url").asText("");
        String openId = data.path("open_id").asText("");
        String unionId = data.path("union_id").asText("");

        // 若部分信息缺失且有 userAccessToken，可再调用 user_info 补充
        if ((!StringUtils.hasText(name) || !StringUtils.hasText(avatar) || !StringUtils.hasText(openId))
                && StringUtils.hasText(userAccessToken)) {
            try {
                HttpRequest infoReq = HttpRequest.newBuilder()
                        .uri(URI.create("https://open.feishu.cn/open-apis/authen/v1/user_info"))
                        .header("Authorization", "Bearer " + userAccessToken)
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build();
                HttpResponse<String> infoResp = httpClient.send(infoReq, HttpResponse.BodyHandlers.ofString());
                JsonNode infoRoot = objectMapper.readTree(infoResp.body());
                if (infoRoot.path("code").asInt(1) == 0) {
                    JsonNode infoData = infoRoot.path("data");
                    if (!StringUtils.hasText(name)) {
                        name = infoData.path("name").asText("");
                        if (!StringUtils.hasText(name)) name = infoData.path("en_name").asText("");
                    }
                    if (!StringUtils.hasText(avatar)) {
                        avatar = infoData.path("avatar_url").asText("");
                        if (!StringUtils.hasText(avatar)) avatar = infoData.path("avatar_thumb").asText("");
                        if (!StringUtils.hasText(avatar)) avatar = infoData.path("avatar_middle").asText("");
                        if (!StringUtils.hasText(avatar)) avatar = infoData.path("avatar_big").asText("");
                    }
                    if (!StringUtils.hasText(openId)) openId = infoData.path("open_id").asText("");
                    if (!StringUtils.hasText(unionId)) unionId = infoData.path("union_id").asText("");
                }
            } catch (Exception e) {
                log.warn("飞书补充用户信息失败: {}", e.getMessage());
            }
        }

        // 成员名单和管理员名单都按 open_id 对人，拿不到就无法确定是谁
        if (!StringUtils.hasText(openId)) {
            throw new RuntimeException("飞书没有返回用户编号 open_id");
        }

        return AuthUser.builder()
                .userId(openId)
                .userName(StringUtils.hasText(name) ? name : "飞书用户")
                .userAvatar(avatar)
                .openId(openId)
                .unionId(unionId)
                .build();
    }

    /**
     * 本地开发快捷登录。只能指定身份，能做什么仍按名单计算。
     */
    public Map<String, Object> mockLogin(Map<String, Object> body) {
        if (!mockLoginEnabled) {
            throw new IllegalStateException("演示登录只在本地开发环境可用");
        }
        String userId = text(body, "userId");
        String userName = text(body, "userName");
        return issueSession(AuthUser.builder()
                .userId(StringUtils.hasText(userId) ? userId : "user_" + UUID.randomUUID().toString().substring(0, 8))
                .userName(StringUtils.hasText(userName) ? userName : "演示用户")
                .userAvatar(Optional.ofNullable(text(body, "userAvatar")).orElse(""))
                .openId(StringUtils.hasText(userId) && userId.startsWith("ou_") ? userId : null)
                .build());
    }

    private static String text(Map<String, Object> body, String key) {
        Object value = body != null ? body.get(key) : null;
        if (value == null) return null;
        String s = value.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private Map<String, Object> issueSession(AuthUser user) {
        LocalDateTime now = LocalDateTime.now();
        AuthSession session = AuthSession.builder()
                .token(UUID.randomUUID().toString().replace("-", ""))
                .userId(user.getUserId())
                .userName(user.getUserName())
                .userAvatar(user.getUserAvatar())
                .openId(user.getOpenId())
                .unionId(user.getUnionId())
                .createdAt(now)
                .expiresAt(now.plus(SESSION_TTL))
                .build();
        authSessionMapper.delete(Wrappers.<AuthSession>lambdaQuery().lt(AuthSession::getExpiresAt, now));
        authSessionMapper.insert(session);
        sessionCache.put(session.getToken(), session);
        rememberUser(user, true);
        syncMemberProfile(user);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("token", session.getToken());
        res.put("user", user);
        res.put("systemAdmin", isUserAdmin(user));
        return res;
    }

    private void rememberUser(AuthUser user, boolean loggedIn) {
        if (!StringUtils.hasText(user.getUserId()) || !StringUtils.hasText(user.getUserName())) return;
        try {
            LocalDateTime now = LocalDateTime.now();
            AppUser existing = appUserMapper.selectById(user.getUserId());
            AppUser row = AppUser.builder()
                    .userId(user.getUserId())
                    .userName(user.getUserName())
                    .userAvatar(user.getUserAvatar() != null ? user.getUserAvatar() : "")
                    .unionId(user.getUnionId())
                    .lastLoginAt(loggedIn ? now : (existing != null ? existing.getLastLoginAt() : null))
                    .updatedAt(now)
                    .build();
            if (existing == null) appUserMapper.insert(row);
            else appUserMapper.updateById(row);
        } catch (Exception e) {
            log.warn("记录用户信息失败: {}", e.getMessage());
        }
    }

    /**
     * 按飞书编号找名字：先看登录过的人，再问飞书通讯录（需要飞书应用开通通讯录读取权限）。
     */
    public Optional<AppUser> resolveUser(String userId) {
        if (!StringUtils.hasText(userId)) return Optional.empty();
        AppUser known = appUserMapper.selectById(userId);
        if (known != null && StringUtils.hasText(known.getUserName())) return Optional.of(known);
        Optional<AuthUser> fromFeishu = fetchFeishuContact(userId);
        fromFeishu.ifPresent(u -> rememberUser(u, false));
        return fromFeishu.map(u -> AppUser.builder()
                .userId(u.getUserId()).userName(u.getUserName()).userAvatar(u.getUserAvatar()).build());
    }

    public Map<String, Object> lookupUser(String userId) {
        Optional<AppUser> found = resolveUser(userId);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("userId", userId);
        res.put("found", found.isPresent());
        res.put("userName", found.map(AppUser::getUserName).orElse(null));
        res.put("userAvatar", found.map(AppUser::getUserAvatar).orElse(null));
        return res;
    }

    private Optional<AuthUser> fetchFeishuContact(String openId) {
        if (!isFeishuEnabled() || !openId.matches("ou_[A-Za-z0-9]+")
                || System.currentTimeMillis() < contactUnavailableUntil) {
            return Optional.empty();
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://open.feishu.cn/open-apis/contact/v3/users/" + openId + "?user_id_type=open_id"))
                    .header("Authorization", "Bearer " + tenantAccessToken())
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            JsonNode root = objectMapper.readTree(httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body());
            int code = root.path("code").asInt(-1);
            if (code != 0) {
                if (code == 99991672) {
                    contactUnavailableUntil = System.currentTimeMillis() + Duration.ofMinutes(10).toMillis();
                    log.info("飞书应用未开通通讯录读取权限，暂时只能显示登录过的人的名字");
                } else {
                    log.info("飞书通讯录查询 {} 失败 ({}): {}", openId, code, root.path("msg").asText());
                }
                return Optional.empty();
            }
            JsonNode user = root.path("data").path("user");
            String name = user.path("name").asText("");
            if (!StringUtils.hasText(name)) name = user.path("en_name").asText("");
            if (!StringUtils.hasText(name)) return Optional.empty();
            JsonNode avatar = user.path("avatar");
            return Optional.of(AuthUser.builder()
                    .userId(openId)
                    .openId(openId)
                    .unionId(user.path("union_id").asText(null))
                    .userName(name)
                    .userAvatar(avatar.path("avatar_72").asText(avatar.path("avatar_origin").asText("")))
                    .build());
        } catch (Exception e) {
            log.warn("飞书通讯录查询失败: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private synchronized String tenantAccessToken() throws Exception {
        if (tenantToken != null && System.currentTimeMillis() < tenantTokenExpiresAt) return tenantToken;
        String reqJson = objectMapper.writeValueAsString(Map.of("app_id", getAppId(), "app_secret", getAppSecret()));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal"))
                .header("Content-Type", "application/json; charset=utf-8")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(reqJson))
                .build();
        JsonNode root = objectMapper.readTree(httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body());
        if (root.path("code").asInt(1) != 0) {
            throw new RuntimeException("飞书接口错误 (" + root.path("code").asInt() + "): " + root.path("msg").asText());
        }
        tenantToken = root.path("tenant_access_token").asText();
        // 提前 5 分钟换新，避免用到刚好过期的凭证
        tenantTokenExpiresAt = System.currentTimeMillis() + Math.max(60, root.path("expire").asLong(7200) - 300) * 1000L;
        return tenantToken;
    }

    /** 管理员按飞书编号添加成员时还不知道名字，本人登录后把名字和头像补到成员名单上 */
    private void syncMemberProfile(AuthUser user) {
        if (!StringUtils.hasText(user.getUserId()) || !StringUtils.hasText(user.getUserName())) return;
        try {
            projectMemberMapper.update(null, Wrappers.<ProjectMember>lambdaUpdate()
                    .eq(ProjectMember::getUserId, user.getUserId())
                    .set(ProjectMember::getUserName, user.getUserName())
                    .set(ProjectMember::getUserAvatar, user.getUserAvatar() != null ? user.getUserAvatar() : ""));
        } catch (Exception e) {
            log.warn("同步成员名字失败: {}", e.getMessage());
        }
    }

    private static String readToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (!StringUtils.hasText(header)) return null;
        String token = header.startsWith("Bearer ") ? header.substring(7).trim() : header.trim();
        return StringUtils.hasText(token) ? token : null;
    }

    /**
     * 当前登录的人；没带凭证、凭证无效或已过期都返回 null。
     */
    public AuthUser getCurrentUser(HttpServletRequest request) {
        if (request == null) return null;
        Object cached = request.getAttribute(REQUEST_USER_ATTR);
        if (cached instanceof AuthUser user) return user;
        if (cached != null) return null;

        AuthUser user = null;
        String token = readToken(request);
        if (token != null) {
            AuthSession session = sessionCache.get(token);
            if (session == null) {
                session = authSessionMapper.selectById(token);
                if (session != null) sessionCache.put(token, session);
            }
            if (session != null && session.getExpiresAt() != null && session.getExpiresAt().isAfter(LocalDateTime.now())) {
                user = AuthUser.builder()
                        .userId(session.getUserId())
                        .userName(session.getUserName())
                        .userAvatar(session.getUserAvatar())
                        .openId(session.getOpenId())
                        .unionId(session.getUnionId())
                        .build();
            } else if (session != null) {
                sessionCache.remove(token);
                authSessionMapper.deleteById(token);
            }
        }
        request.setAttribute(REQUEST_USER_ATTR, user != null ? user : Boolean.FALSE);
        return user;
    }

    public void logout(HttpServletRequest request) {
        String token = request != null ? readToken(request) : null;
        if (token == null) return;
        sessionCache.remove(token);
        authSessionMapper.deleteById(token);
    }

    /** 系统管理员：飞书用户编号在配置名单里 */
    public boolean isUserAdmin(AuthUser user) {
        if (user == null || !StringUtils.hasText(adminIds)) return false;
        Set<String> ids = Arrays.stream(adminIds.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        return ids.contains(user.getUserId())
                || (user.getOpenId() != null && ids.contains(user.getOpenId()));
    }

    /**
     * 这个人在这个项目里的角色：系统管理员 → admin；在成员名单里 → 名单上的角色；其余 → viewer。
     */
    public String getUserProjectRole(Long projectId, AuthUser user) {
        if (user == null) return VIEWER;
        if (isUserAdmin(user)) return ADMIN;
        if (projectId == null) return VIEWER;
        ProjectMember member = projectMemberMapper.selectOne(
                Wrappers.<ProjectMember>lambdaQuery()
                        .eq(ProjectMember::getProjectId, projectId)
                        .eq(ProjectMember::getUserId, user.getUserId())
        );
        return member != null && isValidRole(member.getRole()) ? member.getRole() : VIEWER;
    }

    /**
     * 获取当前用户信息、是否系统管理员，以及在指定项目中的角色
     */
    public Map<String, Object> getMe(Long projectId, HttpServletRequest request) {
        AuthUser user = getCurrentUser(request);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("authenticated", user != null);
        res.put("userId", user != null ? user.getUserId() : null);
        res.put("userName", user != null ? user.getUserName() : null);
        res.put("userAvatar", user != null ? user.getUserAvatar() : null);
        res.put("openId", user != null ? user.getOpenId() : null);
        res.put("systemAdmin", isUserAdmin(user));
        res.put("projectId", projectId);
        res.put("projectRole", projectId != null ? getUserProjectRole(projectId, user) : null);
        res.put("feishuEnabled", isFeishuEnabled());
        return res;
    }

    /**
     * 记录或更新项目成员与角色
     */
    public ProjectMember saveOrUpdateProjectMember(Long projectId, String userId, String userName, String userAvatar, String role) {
        if (projectId == null || !StringUtils.hasText(userId)) return null;
        if (role != null && !isValidRole(role)) {
            throw new IllegalStateException("无效的角色类型，仅支持 admin, editor, viewer");
        }
        Optional<AppUser> profile = resolveUser(userId);
        String name = profile.map(AppUser::getUserName)
                .orElse(StringUtils.hasText(userName) && !userName.equals(userId) ? userName : null);
        String avatar = profile.map(AppUser::getUserAvatar)
                .orElse(StringUtils.hasText(userAvatar) ? userAvatar : null);
        ProjectMember existing = projectMemberMapper.selectOne(
                Wrappers.<ProjectMember>lambdaQuery()
                        .eq(ProjectMember::getProjectId, projectId)
                        .eq(ProjectMember::getUserId, userId)
        );
        if (existing != null) {
            if (name != null) existing.setUserName(name);
            if (avatar != null) existing.setUserAvatar(avatar);
            if (role != null) existing.setRole(role);
            projectMemberMapper.updateById(existing);
            return existing;
        }
        ProjectMember member = ProjectMember.builder()
                .projectId(projectId)
                .userId(userId)
                .userName(name != null ? name : userId)
                .userAvatar(avatar != null ? avatar : "")
                .role(role != null ? role : VIEWER)
                .createdAt(LocalDateTime.now())
                .build();
        projectMemberMapper.insert(member);
        return member;
    }

    /** 名单里还没有名字的成员（名字一栏是编号），顺便再查一次补上 */
    public List<ProjectMember> listProjectMembers(Long projectId) {
        List<ProjectMember> members = projectMemberMapper.selectList(
                Wrappers.<ProjectMember>lambdaQuery()
                        .eq(ProjectMember::getProjectId, projectId)
                        .orderByAsc(ProjectMember::getId)
        );
        for (ProjectMember m : members) {
            if (StringUtils.hasText(m.getUserName()) && !m.getUserName().equals(m.getUserId())) continue;
            resolveUser(m.getUserId()).ifPresent(p -> {
                m.setUserName(p.getUserName());
                m.setUserAvatar(p.getUserAvatar() != null ? p.getUserAvatar() : "");
                projectMemberMapper.updateById(m);
            });
        }
        return members;
    }

    public void removeProjectMember(Long projectId, String userId) {
        projectMemberMapper.delete(
                Wrappers.<ProjectMember>lambdaQuery()
                        .eq(ProjectMember::getProjectId, projectId)
                        .eq(ProjectMember::getUserId, userId)
        );
    }

    /**
     * 更新项目成员角色并写入审计日志（仅项目管理员可操作）
     */
    public ProjectMember updateMemberRole(Long projectId, String targetUserId, String newRole, HttpServletRequest request) {
        AuthUser operator = getCurrentUser(request);
        if (operator == null) {
            throw new IllegalStateException("请先登录飞书账号");
        }
        if (!ADMIN.equals(getUserProjectRole(projectId, operator))) {
            throw new IllegalStateException("仅项目管理员可修改成员角色与权限");
        }
        if (!isValidRole(newRole)) {
            throw new IllegalStateException("无效的角色类型，仅支持 admin, editor, viewer");
        }

        ProjectMember existing = projectMemberMapper.selectOne(
                Wrappers.<ProjectMember>lambdaQuery()
                        .eq(ProjectMember::getProjectId, projectId)
                        .eq(ProjectMember::getUserId, targetUserId)
        );
        if (existing == null) {
            throw new IllegalStateException("未找到该项目成员记录");
        }
        String oldRole = existing.getRole();
        existing.setRole(newRole);
        projectMemberMapper.updateById(existing);

        if (logService != null) {
            String roleCn = ADMIN.equals(newRole) ? "管理员" : (EDITOR.equals(newRole) ? "编辑者" : "只读评审");
            String oldRoleCn = ADMIN.equals(oldRole) ? "管理员" : (EDITOR.equals(oldRole) ? "编辑者" : "只读评审");
            logService.record(
                    projectId,
                    operator,
                    "MEMBER_ROLE_CHANGE",
                    String.format("将成员「%s」的角色由 %s 调整为 %s", existing.getUserName(), oldRoleCn, roleCn),
                    existing.getUserName(),
                    LogService.resolveIp(request)
            );
        }

        return existing;
    }
}
