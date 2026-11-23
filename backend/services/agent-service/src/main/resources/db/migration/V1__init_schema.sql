CREATE TABLE agent_audit_logs
(
    id               UUID                        NOT NULL,
    user_id          UUID                        NOT NULL,
    conversation_id  UUID,
    turn_id          UUID,
    message_id       UUID,
    tool_call_id     VARCHAR(100),
    execution_order  INTEGER                     NOT NULL,
    action_type      VARCHAR(50)                 NOT NULL,
    tool_name        VARCHAR(100)                NOT NULL,
    target_node_id   VARCHAR(100),
    input_payload    TEXT,
    undo_payload     TEXT,
    status           VARCHAR(30)                 NOT NULL,
    hitl_status      VARCHAR(30)                 NOT NULL,
    undo_status      VARCHAR(30)                 NOT NULL,
    undone_at        TIMESTAMP WITHOUT TIME ZONE,
    undone_by_log_id UUID,
    reason           TEXT,
    created_at       TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT pk_agent_audit_logs PRIMARY KEY (id)
);

CREATE TABLE chat_conversations
(
    id                     UUID                        NOT NULL,
    user_id                UUID                        NOT NULL,
    title                  VARCHAR(255),
    status                 VARCHAR(30)                 NOT NULL,
    active_leaf_message_id UUID,
    current_turn_number    INTEGER                     NOT NULL,
    last_sequence_number   INTEGER                     NOT NULL,
    last_message_at        TIMESTAMP WITHOUT TIME ZONE,
    created_at             TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at             TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    deleted_at             TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT pk_chat_conversations PRIMARY KEY (id)
);

CREATE TABLE chat_messages
(
    id                UUID                        NOT NULL,
    conversation_id   UUID                        NOT NULL,
    user_id           UUID                        NOT NULL,
    turn_id           UUID                        NOT NULL,
    turn_number       INTEGER                     NOT NULL,
    sequence_number   INTEGER                     NOT NULL,
    parent_message_id UUID,
    role              VARCHAR(30)                 NOT NULL,
    content           TEXT,
    langchain_payload TEXT,
    tool_call_id      VARCHAR(100),
    tool_name         VARCHAR(100),
    status            VARCHAR(30)                 NOT NULL,
    created_at        TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT pk_chat_messages PRIMARY KEY (id)
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

CREATE INDEX idx_audit_turn_exec_order ON agent_audit_logs (turn_id, execution_order);

CREATE INDEX idx_audit_user_id ON agent_audit_logs (user_id);

CREATE INDEX idx_chat_conv_user_id ON chat_conversations (user_id);

CREATE INDEX idx_chat_conv_user_status_last_msg ON chat_conversations (user_id, status, last_message_at);

CREATE INDEX idx_chat_msg_conv_seq ON chat_messages (conversation_id, sequence_number);

CREATE INDEX idx_chat_msg_conv_turn ON chat_messages (conversation_id, turn_id);

CREATE INDEX idx_chat_msg_turn_id ON chat_messages (turn_id);

CREATE INDEX idx_chat_msg_user_id ON chat_messages (user_id);

CREATE INDEX idx_doc_node_id ON document_embeddings (node_id);

CREATE INDEX idx_doc_user_id ON document_embeddings (user_id);

ALTER TABLE agent_audit_logs
    ADD CONSTRAINT FK_AGENT_AUDIT_LOGS_ON_CONVERSATION FOREIGN KEY (conversation_id) REFERENCES chat_conversations (id);

CREATE INDEX idx_audit_conversation_id ON agent_audit_logs (conversation_id);

ALTER TABLE agent_audit_logs
    ADD CONSTRAINT FK_AGENT_AUDIT_LOGS_ON_MESSAGE FOREIGN KEY (message_id) REFERENCES chat_messages (id);

CREATE INDEX idx_audit_message_id ON agent_audit_logs (message_id);

ALTER TABLE agent_audit_logs
    ADD CONSTRAINT FK_AGENT_AUDIT_LOGS_ON_UNDONE_BY_LOG FOREIGN KEY (undone_by_log_id) REFERENCES agent_audit_logs (id);

ALTER TABLE chat_conversations
    ADD CONSTRAINT FK_CHAT_CONVERSATIONS_ON_ACTIVE_LEAF_MESSAGE FOREIGN KEY (active_leaf_message_id) REFERENCES chat_messages (id);

ALTER TABLE chat_messages
    ADD CONSTRAINT FK_CHAT_MESSAGES_ON_CONVERSATION FOREIGN KEY (conversation_id) REFERENCES chat_conversations (id);

ALTER TABLE chat_messages
    ADD CONSTRAINT FK_CHAT_MESSAGES_ON_PARENT_MESSAGE FOREIGN KEY (parent_message_id) REFERENCES chat_messages (id);

CREATE INDEX idx_chat_msg_parent_id ON chat_messages (parent_message_id);