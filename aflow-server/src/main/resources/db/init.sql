CREATE TABLE IF NOT EXISTS flow_definition (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    version INT NOT NULL DEFAULT 1,
    dsl_content JSON,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS flow_instance (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    definition_id VARCHAR(64) NOT NULL,
    definition_version INT NOT NULL DEFAULT 1,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    current_node_id VARCHAR(128),
    variables JSON,
    metadata JSON,
    trigger_type VARCHAR(32) DEFAULT 'MANUAL',
    execution_path JSON,
    breakpoints JSON,
    debug_mode BOOLEAN DEFAULT FALSE,
    started_at DATETIME,
    completed_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_definition_id ON flow_instance(definition_id);
CREATE INDEX IF NOT EXISTS idx_status ON flow_instance(status);

CREATE TABLE IF NOT EXISTS context_snapshot (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    flow_instance_id VARCHAR(64) NOT NULL,
    node_id VARCHAR(128) NOT NULL,
    phase VARCHAR(16) NOT NULL,
    context_data JSON,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_flow_instance_id ON context_snapshot(flow_instance_id);
CREATE INDEX IF NOT EXISTS idx_flow_node ON context_snapshot(flow_instance_id, node_id);

CREATE TABLE IF NOT EXISTS flow_event (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    flow_instance_id VARCHAR(64) NOT NULL,
    node_id VARCHAR(128),
    event_type VARCHAR(32) NOT NULL,
    event_data JSON,
    duration_ms BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_event_flow_instance_id ON flow_event(flow_instance_id);
