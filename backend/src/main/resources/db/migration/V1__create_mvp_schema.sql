CREATE TABLE roles (
    id UUID PRIMARY KEY,
    code VARCHAR(32) NOT NULL UNIQUE,
    display_name VARCHAR(80) NOT NULL
);

CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX users_email_lower_uk ON users (LOWER(email));

CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE RESTRICT,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE websites (
    id UUID PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    start_url TEXT NOT NULL,
    normalized_host VARCHAR(253) NOT NULL UNIQUE,
    max_pages INTEGER NOT NULL DEFAULT 50 CHECK (max_pages BETWEEN 1 AND 100),
    max_depth INTEGER NOT NULL DEFAULT 2 CHECK (max_depth BETWEEN 0 AND 3),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX websites_created_at_idx ON websites (created_at DESC);

CREATE TABLE audits (
    id UUID PRIMARY KEY,
    website_id UUID NOT NULL REFERENCES websites(id) ON DELETE RESTRICT,
    started_by_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    status VARCHAR(24) NOT NULL CHECK (status IN ('CREATED', 'RUNNING', 'COMPLETED', 'FAILED')),
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    pages_discovered INTEGER NOT NULL DEFAULT 0 CHECK (pages_discovered >= 0),
    pages_checked INTEGER NOT NULL DEFAULT 0 CHECK (pages_checked >= 0),
    findings_count INTEGER NOT NULL DEFAULT 0 CHECK (findings_count >= 0),
    score SMALLINT CHECK (score BETWEEN 0 AND 100),
    failure_code VARCHAR(64),
    failure_message TEXT,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX audits_website_created_at_idx ON audits (website_id, created_at DESC);
CREATE INDEX audits_status_created_at_idx ON audits (status, created_at);
CREATE UNIQUE INDEX audits_one_active_per_website_uk
    ON audits (website_id) WHERE status IN ('CREATED', 'RUNNING');

CREATE TABLE audited_pages (
    id UUID PRIMARY KEY,
    audit_id UUID NOT NULL REFERENCES audits(id) ON DELETE CASCADE,
    url TEXT NOT NULL,
    normalized_url TEXT NOT NULL,
    depth INTEGER NOT NULL CHECK (depth BETWEEN 0 AND 3),
    http_status INTEGER,
    response_time_ms INTEGER CHECK (response_time_ms >= 0),
    content_type VARCHAR(160),
    response_bytes BIGINT CHECK (response_bytes >= 0),
    checked_at TIMESTAMPTZ,
    error_code VARCHAR(64),
    error_message TEXT,
    UNIQUE (audit_id, normalized_url)
);

CREATE TABLE findings (
    id UUID PRIMARY KEY,
    audited_page_id UUID NOT NULL REFERENCES audited_pages(id) ON DELETE CASCADE,
    rule_code VARCHAR(64) NOT NULL,
    severity VARCHAR(16) NOT NULL CHECK (severity IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO')),
    title TEXT NOT NULL,
    description TEXT NOT NULL,
    recommendation TEXT NOT NULL,
    element_context TEXT,
    fingerprint CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (audited_page_id, fingerprint)
);

CREATE INDEX findings_page_severity_idx ON findings (audited_page_id, severity);
CREATE INDEX findings_fingerprint_idx ON findings (fingerprint);

CREATE TABLE maintenance_tasks (
    id UUID PRIMARY KEY,
    finding_id UUID NOT NULL UNIQUE REFERENCES findings(id) ON DELETE RESTRICT,
    title TEXT NOT NULL,
    description TEXT,
    priority VARCHAR(16) NOT NULL CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    status VARCHAR(24) NOT NULL CHECK (status IN ('OPEN', 'IN_PROGRESS', 'DONE', 'CANCELED')),
    assigned_to_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    created_by_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    due_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX maintenance_tasks_work_queue_idx
    ON maintenance_tasks (status, priority, assigned_to_user_id);

CREATE TABLE task_status_history (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL REFERENCES maintenance_tasks(id) ON DELETE CASCADE,
    old_status VARCHAR(24),
    new_status VARCHAR(24) NOT NULL,
    changed_by_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    comment TEXT,
    changed_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX task_status_history_task_changed_idx
    ON task_status_history (task_id, changed_at);
