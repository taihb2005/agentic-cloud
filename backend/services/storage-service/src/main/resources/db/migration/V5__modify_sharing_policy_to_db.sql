ALTER TABLE fs_users
    ADD deleted_at TIMESTAMP WITHOUT TIME ZONE;

ALTER TABLE fs_shares
    ADD is_password_protected BOOLEAN;

ALTER TABLE fs_shares
    ADD password_hash VARCHAR(255);

ALTER TABLE fs_shares
    ADD permission_id BIGINT;

ALTER TABLE fs_shares
    ADD share_mode VARCHAR(20);

ALTER TABLE fs_shares
    ADD share_token VARCHAR(64);

ALTER TABLE fs_shares
    ALTER COLUMN is_password_protected SET NOT NULL;

ALTER TABLE fs_shares
    ADD CONSTRAINT uc_fs_shares_token UNIQUE (share_token);

ALTER TABLE fs_users
    ADD CONSTRAINT uc_fs_users_storage_prefix UNIQUE (storage_prefix);

ALTER TABLE fs_node_share_user
    ADD CONSTRAINT uq_node_share_user UNIQUE (node_id, user_id);

ALTER TABLE fs_shares
    ADD CONSTRAINT FK_FS_SHARES_ON_PERMISSION FOREIGN KEY (permission_id) REFERENCES fs_node_permission (id);

ALTER TABLE fs_users
    ALTER COLUMN created_at SET NOT NULL;

ALTER TABLE fs_users
    ALTER COLUMN storage_bucket_name TYPE VARCHAR(100) USING (storage_bucket_name::VARCHAR(100));

ALTER TABLE fs_users
    ALTER COLUMN storage_prefix TYPE VARCHAR(100) USING (storage_prefix::VARCHAR(100));