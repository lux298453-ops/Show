package com.wireforge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wireforge.entity.OperationLog;
import com.wireforge.mapper.OperationLogMapper;
import com.wireforge.model.AuthUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogService {

    private final OperationLogMapper operationLogMapper;

    /**
     * 查询项目操作审计日志（分页、关键词、类型过滤）
     */
    public Map<String, Object> listLogs(Long projectId, int page, int pageSize, String keyword, String actionType) {
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(100, pageSize));

        LambdaQueryWrapper<OperationLog> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(OperationLog::getProjectId, projectId);

        if (StringUtils.hasText(actionType) && !"all".equalsIgnoreCase(actionType.trim())) {
            String at = actionType.trim().toLowerCase();
            switch (at) {
                case "page_edit" -> wrapper.likeRight(OperationLog::getActionType, "PAGE_");
                case "connection_change" -> wrapper.likeRight(OperationLog::getActionType, "INTERACTION_");
                case "comment_discuss" -> wrapper.likeRight(OperationLog::getActionType, "COMMENT_");
                case "member_change" -> wrapper.likeRight(OperationLog::getActionType, "MEMBER_");
                case "project_manage" -> wrapper.likeRight(OperationLog::getActionType, "PROJECT_");
                default -> wrapper.eq(OperationLog::getActionType, actionType.trim().toUpperCase());
            }
        }

        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(OperationLog::getActionDesc, kw)
                    .or().like(OperationLog::getTargetName, kw)
                    .or().like(OperationLog::getUserName, kw)
                    .or().like(OperationLog::getActionType, kw));
        }

        wrapper.orderByDesc(OperationLog::getCreatedAt);

        Page<OperationLog> pageParam = new Page<>(safePage, safeSize);
        IPage<OperationLog> pageResult = operationLogMapper.selectPage(pageParam, wrapper);

        Map<String, Long> counts = new LinkedHashMap<>();
        try {
            counts.put("all", operationLogMapper.selectCount(Wrappers.<OperationLog>lambdaQuery().eq(OperationLog::getProjectId, projectId)));
            counts.put("page_edit", operationLogMapper.selectCount(Wrappers.<OperationLog>lambdaQuery().eq(OperationLog::getProjectId, projectId).likeRight(OperationLog::getActionType, "PAGE_")));
            counts.put("connection_change", operationLogMapper.selectCount(Wrappers.<OperationLog>lambdaQuery().eq(OperationLog::getProjectId, projectId).likeRight(OperationLog::getActionType, "INTERACTION_")));
            counts.put("comment_discuss", operationLogMapper.selectCount(Wrappers.<OperationLog>lambdaQuery().eq(OperationLog::getProjectId, projectId).likeRight(OperationLog::getActionType, "COMMENT_")));
            counts.put("member_change", operationLogMapper.selectCount(Wrappers.<OperationLog>lambdaQuery().eq(OperationLog::getProjectId, projectId).likeRight(OperationLog::getActionType, "MEMBER_")));
            counts.put("project_manage", operationLogMapper.selectCount(Wrappers.<OperationLog>lambdaQuery().eq(OperationLog::getProjectId, projectId).likeRight(OperationLog::getActionType, "PROJECT_")));
        } catch (Exception e) {
            log.warn("统计日志各分类总数异常: {}", e.getMessage());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", pageResult.getRecords());
        result.put("total", pageResult.getTotal());
        result.put("page", pageResult.getCurrent());
        result.put("pageSize", pageResult.getSize());
        result.put("pages", pageResult.getPages());
        result.put("counts", counts);
        return result;
    }

    /**
     * 跨项目的项目级操作：创建、修改资料、删除。项目删掉后记录仍留在这里。
     */
    public Map<String, Object> listProjectOperations(int page, int pageSize, String keyword, String actionType) {
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(100, pageSize));
        List<String> projectActions = List.of("PROJECT_CREATE", "PROJECT_UPDATE", "PROJECT_DELETE");

        LambdaQueryWrapper<OperationLog> wrapper = Wrappers.lambdaQuery();
        if (StringUtils.hasText(actionType) && !"all".equalsIgnoreCase(actionType.trim())) {
            String at = actionType.trim().toLowerCase();
            switch (at) {
                case "add" -> wrapper.eq(OperationLog::getActionType, "PROJECT_CREATE");
                case "edit" -> wrapper.eq(OperationLog::getActionType, "PROJECT_UPDATE");
                case "delete" -> wrapper.eq(OperationLog::getActionType, "PROJECT_DELETE");
                default -> {
                    if (projectActions.contains(actionType.trim().toUpperCase())) {
                        wrapper.eq(OperationLog::getActionType, actionType.trim().toUpperCase());
                    } else {
                        wrapper.in(OperationLog::getActionType, projectActions);
                    }
                }
            }
        } else {
            wrapper.in(OperationLog::getActionType, projectActions);
        }

        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(OperationLog::getActionDesc, kw)
                    .or().like(OperationLog::getTargetName, kw)
                    .or().like(OperationLog::getUserName, kw));
        }
        wrapper.orderByDesc(OperationLog::getCreatedAt);

        Page<OperationLog> pageParam = new Page<>(safePage, safeSize);
        IPage<OperationLog> pageResult = operationLogMapper.selectPage(pageParam, wrapper);

        Map<String, Long> counts = new LinkedHashMap<>();
        try {
            counts.put("all", operationLogMapper.selectCount(Wrappers.<OperationLog>lambdaQuery().in(OperationLog::getActionType, projectActions)));
            counts.put("add", operationLogMapper.selectCount(Wrappers.<OperationLog>lambdaQuery().eq(OperationLog::getActionType, "PROJECT_CREATE")));
            counts.put("edit", operationLogMapper.selectCount(Wrappers.<OperationLog>lambdaQuery().eq(OperationLog::getActionType, "PROJECT_UPDATE")));
            counts.put("delete", operationLogMapper.selectCount(Wrappers.<OperationLog>lambdaQuery().eq(OperationLog::getActionType, "PROJECT_DELETE")));
        } catch (Exception e) {
            log.warn("统计工作区操作日志分类总数异常: {}", e.getMessage());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", pageResult.getRecords());
        result.put("total", pageResult.getTotal());
        result.put("page", pageResult.getCurrent());
        result.put("pageSize", pageResult.getSize());
        result.put("pages", pageResult.getPages());
        result.put("counts", counts);
        return result;
    }

    /**
     * 写入操作日志
     */
    public OperationLog record(Long projectId, String userId, String userName, String userAvatar,
                               String actionType, String actionDesc, String targetName, String ipAddress) {
        if (projectId == null) return null;
        try {
            OperationLog opLog = OperationLog.builder()
                    .projectId(projectId)
                    .userId(userId)
                    .userName(StringUtils.hasText(userName) ? userName : "匿名用户")
                    .userAvatar(userAvatar != null ? userAvatar : "")
                    .actionType(StringUtils.hasText(actionType) ? actionType : "OPERATION")
                    .actionDesc(actionDesc != null ? actionDesc : "")
                    .targetName(targetName != null ? targetName : "")
                    .ipAddress(ipAddress != null ? ipAddress : "127.0.0.1")
                    .createdAt(LocalDateTime.now())
                    .build();
            operationLogMapper.insert(opLog);
            return opLog;
        } catch (Exception e) {
            log.warn("写入操作审计日志失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 重载：使用 AuthUser 写入日志
     */
    public OperationLog record(Long projectId, AuthUser user, String actionType,
                               String actionDesc, String targetName, String ipAddress) {
        String userId = user != null ? user.getUserId() : null;
        String userName = user != null ? user.getUserName() : null;
        String userAvatar = user != null ? user.getUserAvatar() : null;
        return record(projectId, userId, userName, userAvatar, actionType, actionDesc, targetName, ipAddress);
    }

    public OperationLog record(Long projectId, AuthUser user, String actionType,
                               String actionDesc, String targetName) {
        return record(projectId, user, actionType, actionDesc, targetName, null);
    }

    /**
     * 页面上报的操作日志。操作人一律取当前登录的人，请求体里的身份字段不采信；
     * 项目的创建、修改、删除只由服务器自己记。
     */
    public OperationLog recordFromApi(Long projectId, Map<String, Object> body,
                                      AuthUser currentUser, HttpServletRequest request) {
        if (currentUser == null) return null;
        String actionType = body.get("actionType") != null ? body.get("actionType").toString().trim() : "";
        if (!StringUtils.hasText(actionType) || actionType.startsWith("PROJECT_")) {
            actionType = "OPERATION";
        }
        String actionDesc = body.get("actionDesc") != null ? body.get("actionDesc").toString() : "";
        String targetName = body.get("targetName") != null ? body.get("targetName").toString() : "";
        return record(projectId, currentUser, actionType, actionDesc, targetName, resolveIp(request));
    }

    /**
     * 解析请求客户端真实 IP
     */
    public static String resolveIp(HttpServletRequest request) {
        if (request == null) return "127.0.0.1";
        String ip = request.getHeader("X-Forwarded-For");
        if (!StringUtils.hasText(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (!StringUtils.hasText(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (StringUtils.hasText(ip) && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip != null ? ip : "127.0.0.1";
    }
}
