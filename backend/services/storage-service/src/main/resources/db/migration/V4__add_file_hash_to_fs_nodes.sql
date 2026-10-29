ALTER TABLE fs_nodes
    ADD COLUMN file_hash VARCHAR(128);

CREATE INDEX idx_fs_nodes_file_hash ON fs_nodes (file_hash) WHERE file_hash IS NOT NULL;
