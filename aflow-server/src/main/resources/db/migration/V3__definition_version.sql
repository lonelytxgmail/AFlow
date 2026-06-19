-- ─── 定义版本管理表（V3） ───
-- 每次 publish 创建版本快照，支持版本历史查看和回滚

CREATE TABLE IF NOT EXISTS definition_version (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    definition_id VARCHAR(64) NOT NULL,
    version_number INT NOT NULL,
    snapshot_json JSON NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_def_version (definition_id, version_number)
);
CREATE INDEX IF NOT EXISTS idx_def_version_def_id ON definition_version(definition_id);
