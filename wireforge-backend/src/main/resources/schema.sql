-- WireForge 建表脚本（MySQL 8）
CREATE TABLE IF NOT EXISTS `project` (
  `id`          BIGINT AUTO_INCREMENT PRIMARY KEY,
  `name`        VARCHAR(255) NOT NULL,
  `description` TEXT,
  `cover_image` VARCHAR(500),
  `created_at`  DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS `page` (
  `id`               BIGINT AUTO_INCREMENT PRIMARY KEY,
  `project_id`       BIGINT       NOT NULL,
  `name`             VARCHAR(255) NOT NULL,
  `background_image` VARCHAR(500),
  `canvas_width`     INT          DEFAULT 375,
  `canvas_height`    INT          DEFAULT 812,
  `canvas_x`         DOUBLE COMMENT '区块在无限画布上的位置（NULL=自动布局）',
  `canvas_y`         DOUBLE COMMENT '区块在无限画布上的位置（NULL=自动布局）',
  `sort_order`       INT          DEFAULT 0,
  `analyzed`         TINYINT      DEFAULT 0 COMMENT '0=未AI分析 1=已分析',
  `html_content`     LONGTEXT COMMENT 'AI 生成的完整 HTML/CSS 页面（Stitch 式整页直出，NULL=未生成）',
  `created_at`       DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_project` (`project_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS `element` (
  `id`          BIGINT AUTO_INCREMENT PRIMARY KEY,
  `page_id`     BIGINT NOT NULL,
  `type`        VARCHAR(50),
  `label`       TEXT,
  `asset_id`    VARCHAR(50) COMMENT '素材库 ID（avatar-03 / product-01 / icon-home / bg-01 / effect-glow 等）',
  `position_x`  DOUBLE DEFAULT 0,
  `position_y`  DOUBLE DEFAULT 0,
  `width`       DOUBLE DEFAULT 0,
  `height`      DOUBLE DEFAULT 0,
  `style`       TEXT COMMENT 'JSON 扩展样式',
  `created_by`  VARCHAR(20) DEFAULT 'ai',
  `created_at`  DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_page` (`page_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 兼容已存在的库：仅当字段不存在时追加 asset_id 列（MySQL 无原生 IF NOT EXISTS）
SET @exist_asset_id = (SELECT COUNT(*) FROM information_schema.columns
                       WHERE table_schema = DATABASE() AND table_name = 'element' AND column_name = 'asset_id');
SET @sql_asset_id = IF(@exist_asset_id = 0,
  'ALTER TABLE element ADD COLUMN asset_id VARCHAR(50) COMMENT \'素材库 ID（avatar-03 / product-01 / icon-home / bg-01 / effect-glow 等）\'',
  'SELECT 1');
PREPARE stmt_asset_id FROM @sql_asset_id;
EXECUTE stmt_asset_id;
DEALLOCATE PREPARE stmt_asset_id;

-- 兼容已存在的库：仅当字段不存在时追加 project.app_map 列
SET @exist_app_map = (SELECT COUNT(*) FROM information_schema.columns
                      WHERE table_schema = DATABASE() AND table_name = 'project' AND column_name = 'app_map');
SET @sql_app_map = IF(@exist_app_map = 0,
  'ALTER TABLE project ADD COLUMN app_map TEXT COMMENT \'App Map：全局共享组件定义（底部Tab栏等）JSON\'',
  'SELECT 1');
PREPARE stmt_app_map FROM @sql_app_map;
EXECUTE stmt_app_map;
DEALLOCATE PREPARE stmt_app_map;

CREATE TABLE IF NOT EXISTS `interaction` (
  `id`             BIGINT AUTO_INCREMENT PRIMARY KEY,
  `element_id`     BIGINT NOT NULL,
  `trigger_type`   VARCHAR(30) DEFAULT 'click',
  `action_type`    VARCHAR(30),
  `target_page_id` BIGINT,
  `params`         TEXT COMMENT 'JSON 扩展参数（动画/弹窗定位等）',
  KEY `idx_element` (`element_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS `annotation` (
  `id`          BIGINT AUTO_INCREMENT PRIMARY KEY,
  `page_id`     BIGINT NOT NULL,
  `element_id`  BIGINT,
  `text`        TEXT,
  `position_x`  DOUBLE,
  `position_y`  DOUBLE,
  `box_x`       DOUBLE COMMENT '标注框位置（画布 px，NULL=自动布局）',
  `box_y`       DOUBLE COMMENT '标注框位置（画布 px，NULL=自动布局）',
  `anchor_x`    DOUBLE COMMENT '引线元素端锚点（线框逻辑坐标，NULL=自动）',
  `anchor_y`    DOUBLE COMMENT '引线元素端锚点（线框逻辑坐标，NULL=自动）',
  `elbow_x`     DOUBLE COMMENT '引线竖折线 X（画布 px，NULL=自动）',
  `created_at`  DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_page` (`page_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
