-- ==============================================================================
-- WireForge 数据库结构初始化脚本 (MySQL 8.0+)
-- 字符集: utf8mb4 / 校对规则: utf8mb4_unicode_ci
-- 说明: 包含项目、页面、元素、交互路由、说明引线标注全套表结构及最新字段
-- ==============================================================================

CREATE DATABASE IF NOT EXISTS `wireforge` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `wireforge`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ------------------------------------------------------------------------------
-- 1. 项目表 (project)
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `project` (
  `id`          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '项目主键ID',
  `name`        VARCHAR(255) NOT NULL COMMENT '项目名称',
  `description` TEXT COMMENT '项目描述',
  `cover_image` VARCHAR(500) COMMENT '项目封面图URL',
  `app_map`     TEXT COMMENT 'App Map：全局共享组件定义（底部Tab栏、全局模态等）JSON',
  `created_at`  DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='原型项目表';

-- ------------------------------------------------------------------------------
-- 2. 页面表 (page)
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `page` (
  `id`               BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '页面主键ID',
  `project_id`       BIGINT NOT NULL COMMENT '所属项目ID',
  `name`             VARCHAR(255) NOT NULL COMMENT '页面名称',
  `background_image` VARCHAR(500) COMMENT '原设计稿截图URL/路径',
  `canvas_width`     INT DEFAULT 375 COMMENT '逻辑画布宽度 (px)',
  `canvas_height`    INT DEFAULT 812 COMMENT '逻辑画布高度 (px)',
  `canvas_x`         DOUBLE DEFAULT NULL COMMENT '无限画布坐标X (NULL为自动布局)',
  `canvas_y`         DOUBLE DEFAULT NULL COMMENT '无限画布坐标Y (NULL为自动布局)',
  `sort_order`       INT DEFAULT 0 COMMENT '页面展示排序权重',
  `analyzed`         TINYINT DEFAULT 0 COMMENT '0=未AI分析, 1=已完成分析',
  `html_content`     LONGTEXT COMMENT 'AI直出或用户微调后的高保真整页HTML源码',
  `created_at`       DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  KEY `idx_project_id` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目页面表';

-- ------------------------------------------------------------------------------
-- 3. 页面元素表 (element)
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `element` (
  `id`          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '元素主键ID',
  `page_id`     BIGINT NOT NULL COMMENT '所属页面ID',
  `type`        VARCHAR(50) DEFAULT NULL COMMENT '元素类型 (button, image, text, card等)',
  `label`       TEXT COMMENT '元素语义文案或用途标签',
  `asset_id`    VARCHAR(50) DEFAULT NULL COMMENT '高精度素材库资产ID (avatar-03, icon-home等)',
  `position_x`  DOUBLE DEFAULT 0 COMMENT '元素相对页面左上角X坐标',
  `position_y`  DOUBLE DEFAULT 0 COMMENT '元素相对页面左上角Y坐标',
  `width`       DOUBLE DEFAULT 0 COMMENT '元素宽度',
  `height`      DOUBLE DEFAULT 0 COMMENT '元素高度',
  `style`       TEXT COMMENT 'JSON扩展样式定义',
  `created_by`  VARCHAR(20) DEFAULT 'ai' COMMENT '创建来源 (ai / user)',
  `created_at`  DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  KEY `idx_page_id` (`page_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='页面组件元素表';

-- ------------------------------------------------------------------------------
-- 4. 交互跳转拓扑表 (interaction)
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `interaction` (
  `id`             BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '交互主键ID',
  `element_id`     BIGINT NOT NULL COMMENT '触发交互的源元素ID',
  `trigger_type`   VARCHAR(30) DEFAULT 'click' COMMENT '触发类型 (click, swipe, dblclick)',
  `action_type`    VARCHAR(30) DEFAULT 'navigate' COMMENT '交互行为 (navigate, modal, back)',
  `target_page_id` BIGINT DEFAULT NULL COMMENT '目标页面ID',
  `params`         TEXT COMMENT 'JSON扩展参数（转场动画、弹层浮层定位等）',
  KEY `idx_element_id` (`element_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='页面交互路由拓扑表';

-- ------------------------------------------------------------------------------
-- 5. 业务说明与引线标注表 (annotation)
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `annotation` (
  `id`          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '标注主键ID',
  `page_id`     BIGINT NOT NULL COMMENT '所属页面ID',
  `element_id`  BIGINT DEFAULT NULL COMMENT '关联的页面元素ID',
  `text`        TEXT COMMENT '标注说明文案内容',
  `position_x`  DOUBLE DEFAULT NULL COMMENT '锚点中心X',
  `position_y`  DOUBLE DEFAULT NULL COMMENT '锚点中心Y',
  `box_x`       DOUBLE DEFAULT NULL COMMENT '说明卡片在画布上的X坐标 (NULL为自动吸附)',
  `box_y`       DOUBLE DEFAULT NULL COMMENT '说明卡片在画布上的Y坐标 (NULL为自动吸附)',
  `anchor_x`    DOUBLE DEFAULT NULL COMMENT '引线元素端锚点X (逻辑像素/百分比)',
  `anchor_y`    DOUBLE DEFAULT NULL COMMENT '引线元素端锚点Y (逻辑像素/百分比)',
  `elbow_x`     DOUBLE DEFAULT NULL COMMENT '引线折线竖向转折点X坐标',
  `sort_order`  INT DEFAULT 0 COMMENT '页面说明列表排序序号（升序）',
  `created_at`  DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  KEY `idx_page_id` (`page_id`),
  KEY `idx_element_id` (`element_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='说明标注与引线坐标表';

SET FOREIGN_KEY_CHECKS = 1;
