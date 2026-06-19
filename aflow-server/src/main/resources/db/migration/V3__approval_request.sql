-- ─── 审批请求表（V3） ───
-- 存储人工审批节点产生的审批请求，支持超时自动处理

CREATE TABLE IF NOT EXISTS approval_request (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    flow_id VARCHAR(64) NOT NULL,
    node_id VARCHAR(128) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    options JSON,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    deadline DATETIME,
    timeout_action VARCHAR(32) DEFAULT 'reject',
    approval_data JSON,
    reject_reason TEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_approval_flow_id ON approval_request(flow_id);
CREATE INDEX IF NOT EXISTS idx_approval_status ON approval_request(status);
CREATE INDEX IF NOT EXISTS idx_approval_deadline ON approval_request(deadline);
