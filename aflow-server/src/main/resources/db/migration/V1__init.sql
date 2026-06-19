-- ─── AFlow 初始化 Schema（V1） ───
-- 4 张核心表：流程定义、流程实例、上下文快照、执行事件
-- 兼容 H2(MySQL模式) 和 MySQL

-- 流程定义表：存储 DSL JSON、名称、版本、状态
CREATE TABLE IF NOT EXISTS flow_definition (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    version INT NOT NULL DEFAULT 1,
    dsl_content JSON,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 流程实例表：跟踪运行中的/已完成的实例，含变量、执行路径、断点等运行时状态
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

-- 上下文快照表：记录每个节点执行前后的 FlowContext 状态（用于 diff 调试）
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

-- 执行事件表：流程/节点生命周期事件审计日志（含执行耗时）
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
