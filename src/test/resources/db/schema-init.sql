CREATE SCHEMA IF NOT EXISTS document_schema;
SET search_path TO document_schema;

CREATE TABLE documents (
    id          UUID PRIMARY KEY,
    "user"      VARCHAR(255) NOT NULL,
    name        VARCHAR(512) NOT NULL,
    minio_path  VARCHAR(1024) NOT NULL,
    size        BIGINT,
    file_type   VARCHAR(255),
    status      VARCHAR(20)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE tags (
    id   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE document_tags (
    document_id UUID   NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    tag_id      BIGINT NOT NULL REFERENCES tags(id)      ON DELETE CASCADE,
    PRIMARY KEY (document_id, tag_id)
);

CREATE INDEX idx_documents_user       ON documents("user");
CREATE INDEX idx_documents_created_at ON documents(created_at DESC);
CREATE INDEX idx_documents_status     ON documents(status);
CREATE INDEX idx_document_tags_doc    ON document_tags(document_id);
CREATE INDEX idx_document_tags_tag    ON document_tags(tag_id);
