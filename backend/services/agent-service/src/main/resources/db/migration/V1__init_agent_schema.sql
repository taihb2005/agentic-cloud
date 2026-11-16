-- =============================================================================
-- Migration V1: Init Agent Schema (Audit Logs & Document Embeddings)
-- =============================================================================

CREATE TABLE IF NOT EXISTS agent_audit_logs (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    action_type VARCHAR(50) NOT NULL,
    tool_name VARCHAR(100) NOT NULL,
    target_node_id VARCHAR(100),
    input_payload TEXT,
    undo_payload TEXT,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_audit_user_id ON agent_audit_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_action_type ON agent_audit_logs(action_type);

CREATE TABLE IF NOT EXISTS document_embeddings (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    node_id VARCHAR(100) NOT NULL,
    chunk_index INT NOT NULL,
    content TEXT NOT NULL,
    summary TEXT,
    embedding_id VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_doc_user_id ON document_embeddings(user_id);
CREATE INDEX IF NOT EXISTS idx_doc_node_id ON document_embeddings(node_id);
