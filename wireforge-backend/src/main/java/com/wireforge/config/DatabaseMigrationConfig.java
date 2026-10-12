package com.wireforge.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/**
 * 数据库动态表结构自动迁移组件
 * 兼容云端与多环境（如 Aiven、Render、本地不同历史版本），
 * 启动时自动检测并补齐缺失的字段，杜绝 Unknown column 异常。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseMigrationConfig {

    private final DataSource dataSource;

    @PostConstruct
    public void migrate() {
        log.info("Checking and applying database schema auto-migrations...");
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            ScriptUtils.executeSqlScript(conn, new ClassPathResource("autowire-schema.sql"));
            // 页面表扩展字段
            addColumnIfNotExists(stmt, "page", "canvas_x", "DOUBLE COMMENT '画布位置X'");
            addColumnIfNotExists(stmt, "page", "canvas_y", "DOUBLE COMMENT '画布位置Y'");
            addColumnIfNotExists(stmt, "page", "sort_order", "INT DEFAULT 0 COMMENT '页面排序'");
            addColumnIfNotExists(stmt, "page", "analyzed", "TINYINT DEFAULT 0 COMMENT '0=未分析 1=已分析'");
            addColumnIfNotExists(stmt, "page", "html_content", "LONGTEXT COMMENT 'AI生成的HTML'");
            addColumnIfNotExists(stmt, "page", "layout_norm", "TINYINT DEFAULT 1 COMMENT '1=渲染时组内排版 0=用原始方框'");
            addColumnIfNotExists(stmt, "page", "image_hash", "VARCHAR(64) COMMENT '上次识别时设计稿指纹'");

            // 组件表扩展字段
            addColumnIfNotExists(stmt, "element", "asset_id", "VARCHAR(50) COMMENT '素材库 ID'");
            addColumnIfNotExists(stmt, "element", "group_key", "VARCHAR(64) COMMENT '同一次点击的共用编号'");
            addColumnIfNotExists(stmt, "element", "group_role", "VARCHAR(16) COMMENT 'anchor 或 member'");

            addColumnIfNotExists(stmt, "interaction", "source", "VARCHAR(16) COMMENT 'ai / autowire / user'");

            // 项目表扩展字段
            addColumnIfNotExists(stmt, "project", "app_map", "TEXT COMMENT 'App Map 全局定义'");

            // 标注说明表扩展字段（彻底解决 sort_order 缺失问题）
            addColumnIfNotExists(stmt, "annotation", "box_x", "DOUBLE COMMENT '标注框X'");
            addColumnIfNotExists(stmt, "annotation", "box_y", "DOUBLE COMMENT '标注框Y'");
            addColumnIfNotExists(stmt, "annotation", "anchor_x", "DOUBLE COMMENT '引线元素端锚点X'");
            addColumnIfNotExists(stmt, "annotation", "anchor_y", "DOUBLE COMMENT '引线元素端锚点Y'");
            addColumnIfNotExists(stmt, "annotation", "elbow_x", "DOUBLE COMMENT '引线折线X'");
            addColumnIfNotExists(stmt, "annotation", "sort_order", "INT DEFAULT 0 COMMENT '排序权重'");
            addColumnIfNotExists(stmt, "annotation", "title", "VARCHAR(200) COMMENT '说明标题'");
            addColumnIfNotExists(stmt, "annotation", "source", "VARCHAR(30) COMMENT '说明来源，user=人工'");

            // 评论作者信息扩展字段
            addColumnIfNotExists(stmt, "comment_thread", "author_id", "VARCHAR(64) COMMENT '作者ID'");
            addColumnIfNotExists(stmt, "comment_thread", "author_avatar", "VARCHAR(500) COMMENT '作者头像'");
            addColumnIfNotExists(stmt, "comment_reply", "author_id", "VARCHAR(64) COMMENT '作者ID'");
            addColumnIfNotExists(stmt, "comment_reply", "author_avatar", "VARCHAR(500) COMMENT '作者头像'");

            // 操作审计日志表
            executeTableCreation(stmt, "operation_log",
                    "CREATE TABLE IF NOT EXISTS `operation_log` (" +
                    " `id` BIGINT AUTO_INCREMENT PRIMARY KEY," +
                    " `project_id` BIGINT NOT NULL," +
                    " `user_id` VARCHAR(64)," +
                    " `user_name` VARCHAR(64)," +
                    " `user_avatar` VARCHAR(500)," +
                    " `action_type` VARCHAR(32) COMMENT 'PAGE_CREATE/PAGE_EDIT/PAGE_DELETE/ELEMENT_ADD/INTERACTION_ADD/COMMENT_ADD等'," +
                    " `action_desc` TEXT COMMENT '具体中文变更说明'," +
                    " `target_name` VARCHAR(255) COMMENT '涉及的画板或对象名称'," +
                    " `ip_address` VARCHAR(64)," +
                    " `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP," +
                    " INDEX `idx_project_time` (`project_id`, `created_at`)" +
                    ") ENGINE = InnoDB DEFAULT CHARSET = utf8mb4");

            // 项目成员与权限表
            executeTableCreation(stmt, "project_member",
                    "CREATE TABLE IF NOT EXISTS `project_member` (" +
                    " `id` BIGINT AUTO_INCREMENT PRIMARY KEY," +
                    " `project_id` BIGINT NOT NULL," +
                    " `user_id` VARCHAR(64) NOT NULL," +
                    " `user_name` VARCHAR(64) NOT NULL," +
                    " `user_avatar` VARCHAR(500)," +
                    " `role` VARCHAR(20) DEFAULT 'viewer' COMMENT 'admin / editor / viewer'," +
                    " `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP," +
                    " UNIQUE KEY `uk_proj_user` (`project_id`, `user_id`)" +
                    ") ENGINE = InnoDB DEFAULT CHARSET = utf8mb4");

            // 登录凭证表
            executeTableCreation(stmt, "auth_session",
                    "CREATE TABLE IF NOT EXISTS `auth_session` (" +
                    " `token` VARCHAR(64) PRIMARY KEY," +
                    " `user_id` VARCHAR(64) NOT NULL," +
                    " `user_name` VARCHAR(64)," +
                    " `user_avatar` VARCHAR(500)," +
                    " `open_id` VARCHAR(64)," +
                    " `union_id` VARCHAR(64)," +
                    " `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP," +
                    " `expires_at` DATETIME NOT NULL," +
                    " KEY `idx_expires` (`expires_at`)" +
                    ") ENGINE = InnoDB DEFAULT CHARSET = utf8mb4");

            // 认识的人（按飞书编号显示名字）
            executeTableCreation(stmt, "app_user",
                    "CREATE TABLE IF NOT EXISTS `app_user` (" +
                    " `user_id` VARCHAR(64) PRIMARY KEY COMMENT '飞书 open_id'," +
                    " `user_name` VARCHAR(64) NOT NULL," +
                    " `user_avatar` VARCHAR(500)," +
                    " `union_id` VARCHAR(64)," +
                    " `last_login_at` DATETIME COMMENT '从飞书通讯录查到但未登录过时为空'," +
                    " `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP" +
                    ") ENGINE = InnoDB DEFAULT CHARSET = utf8mb4");
            // 从历史操作记录里补上已知的人；名字里带 % 的是旧版本没解码的脏数据
            try {
                int known = stmt.executeUpdate(
                        "INSERT IGNORE INTO `app_user` (`user_id`, `user_name`, `user_avatar`, `updated_at`)" +
                        " SELECT `user_id`, MAX(`user_name`), MAX(`user_avatar`), MAX(`created_at`) FROM `operation_log`" +
                        " WHERE `user_id` LIKE 'ou\\_%' AND `user_name` IS NOT NULL AND `user_name` NOT LIKE '%\\%%'" +
                        " GROUP BY `user_id`");
                if (known > 0) log.info("Auto-migration: remembered {} users from operation_log", known);
            } catch (SQLException e) {
                log.warn("Auto-migration: backfill app_user error: {}", e.getMessage());
            }

            log.info("Database schema auto-migrations completed successfully.");
        } catch (Exception e) {
            log.warn("Database schema auto-migration encountered warning: {}", e.getMessage());
        }
    }

    private void executeTableCreation(Statement stmt, String tableName, String sql) {
        try {
            stmt.executeUpdate(sql);
            log.info("Auto-migration: ensured table {}", tableName);
        } catch (SQLException e) {
            log.warn("Auto-migration: check table {} error: {}", tableName, e.getMessage());
        }
    }

    private void addColumnIfNotExists(Statement stmt, String tableName, String columnName, String columnDef) {
        try {
            String sql = "ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnDef;
            stmt.executeUpdate(sql);
            log.info("Auto-migration: added column {}.{}", tableName, columnName);
        } catch (SQLException e) {
            // MySQL 错误码 1060: Duplicate column name 说明字段已存在，安全忽略
            if (e.getErrorCode() == 1060 || (e.getMessage() != null && e.getMessage().toLowerCase().contains("duplicate column"))) {
                log.debug("Column {}.{} already exists, skip.", tableName, columnName);
            } else {
                log.warn("Could not add column {}.{}: {}", tableName, columnName, e.getMessage());
            }
        }
    }
}
