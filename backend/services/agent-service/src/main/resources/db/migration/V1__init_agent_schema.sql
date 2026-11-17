CREATE TABLE agent_audit_logs
(
    id             UUID                        NOT NULL,
    user_id        UUID                        NOT NULL,
    action_type    VARCHAR(50)                 NOT NULL,
    tool_name      VARCHAR(100)                NOT NULL,
    target_node_id VARCHAR(100),
    input_payload  TEXT,
    undo_payload   TEXT,
    status         VARCHAR(30)                 NOT NULL,
    created_at     TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT pk_agent_audit_logs PRIMARY KEY (id)
);

CREATE TABLE document_embeddings
(
    id           UUID                        NOT NULL,
    user_id      UUID                        NOT NULL,
    node_id      VARCHAR(100)                NOT NULL,
    chunk_index  INTEGER                     NOT NULL,
    content      TEXT                        NOT NULL,
    summary      TEXT,
    embedding_id VARCHAR(100),
    created_at   TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT pk_document_embeddings PRIMARY KEY (id)
);

CREATE INDEX idx_audit_action_type ON agent_audit_logs (action_type);

CREATE INDEX idx_audit_user_id ON agent_audit_logs (user_id);

CREATE INDEX idx_doc_node_id ON document_embeddings (node_id);

CREATE INDEX idx_doc_user_id ON document_embeddings (user_id);