CREATE TABLE ls_tag (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(64) NOT NULL,
    category VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (name, category)
);

CREATE INDEX idx_ls_tag_category ON ls_tag (category);

CREATE TABLE ls_script_tag (
    script_uuid VARCHAR(64) NOT NULL,
    tag_id BIGINT NOT NULL,
    source VARCHAR(16) NOT NULL DEFAULT 'AI',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (script_uuid, tag_id)
);

CREATE INDEX idx_ls_script_tag_tag_id ON ls_script_tag (tag_id);
