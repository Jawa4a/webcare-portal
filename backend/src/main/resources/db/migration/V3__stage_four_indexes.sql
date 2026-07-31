CREATE INDEX audited_pages_audit_depth_idx
    ON audited_pages (audit_id, depth);

CREATE INDEX findings_rule_severity_idx
    ON findings (rule_code, severity);

CREATE INDEX maintenance_tasks_updated_at_idx
    ON maintenance_tasks (updated_at DESC);
