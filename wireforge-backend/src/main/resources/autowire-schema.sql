CREATE TABLE IF NOT EXISTS interaction_exclusion (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 project_id BIGINT NOT NULL, page_id BIGINT NOT NULL, element_id BIGINT NOT NULL,
 element_key VARCHAR(64) NOT NULL, element_label TEXT,
 scope VARCHAR(16) NOT NULL, relation_key VARCHAR(160) NOT NULL,
 reason TEXT, origin VARCHAR(32), active BOOLEAN NOT NULL DEFAULT TRUE,
 created_at DATETIME DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uq_exclusion (project_id, element_key, scope, relation_key),
 KEY idx_exclusion_project (project_id, active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS autowire_application (
 id VARCHAR(36) PRIMARY KEY, project_id BIGINT NOT NULL,
 idempotency_key VARCHAR(80) NOT NULL, request_hash VARCHAR(64) NOT NULL,
 result_json LONGTEXT NOT NULL, backup_json LONGTEXT NOT NULL,
 created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uq_autowire_application (project_id, idempotency_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS autowire_render_job (
 id VARCHAR(36) PRIMARY KEY, project_id BIGINT NOT NULL,
 page_ids LONGTEXT NOT NULL, bindings_json LONGTEXT NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'pending', error TEXT,
 created_at DATETIME DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
 KEY idx_autowire_job (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
