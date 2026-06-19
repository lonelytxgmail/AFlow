-- ─── 原子能力组件表（V2） ───
-- 存储可复用的原子能力模板，用户可自定义并组合

CREATE TABLE IF NOT EXISTS atomic_component (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    category VARCHAR(64) NOT NULL DEFAULT 'general',
    node_type VARCHAR(64) NOT NULL,
    config_template JSON,
    input_schema JSON,
    output_schema JSON,
    icon VARCHAR(64),
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    created_by VARCHAR(64),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_atomic_category ON atomic_component(category);
CREATE INDEX IF NOT EXISTS idx_atomic_status ON atomic_component(status);
