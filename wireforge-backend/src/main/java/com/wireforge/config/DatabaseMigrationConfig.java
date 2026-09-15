package com.wireforge.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

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
            // 页面表扩展字段
            addColumnIfNotExists(stmt, "page", "canvas_x", "DOUBLE COMMENT '画布位置X'");
            addColumnIfNotExists(stmt, "page", "canvas_y", "DOUBLE COMMENT '画布位置Y'");
            addColumnIfNotExists(stmt, "page", "sort_order", "INT DEFAULT 0 COMMENT '页面排序'");
            addColumnIfNotExists(stmt, "page", "analyzed", "TINYINT DEFAULT 0 COMMENT '0=未分析 1=已分析'");
            addColumnIfNotExists(stmt, "page", "html_content", "LONGTEXT COMMENT 'AI生成的HTML'");

            // 组件表扩展字段
            addColumnIfNotExists(stmt, "element", "asset_id", "VARCHAR(50) COMMENT '素材库 ID'");

            // 项目表扩展字段
            addColumnIfNotExists(stmt, "project", "app_map", "TEXT COMMENT 'App Map 全局定义'");

            // 标注说明表扩展字段（彻底解决 sort_order 缺失问题）
            addColumnIfNotExists(stmt, "annotation", "box_x", "DOUBLE COMMENT '标注框X'");
            addColumnIfNotExists(stmt, "annotation", "box_y", "DOUBLE COMMENT '标注框Y'");
            addColumnIfNotExists(stmt, "annotation", "anchor_x", "DOUBLE COMMENT '引线元素端锚点X'");
            addColumnIfNotExists(stmt, "annotation", "anchor_y", "DOUBLE COMMENT '引线元素端锚点Y'");
            addColumnIfNotExists(stmt, "annotation", "elbow_x", "DOUBLE COMMENT '引线折线X'");
            addColumnIfNotExists(stmt, "annotation", "sort_order", "INT DEFAULT 0 COMMENT '排序权重'");

            log.info("Database schema auto-migrations completed successfully.");
        } catch (Exception e) {
            log.warn("Database schema auto-migration encountered warning: {}", e.getMessage());
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
