package com.webcare.portal.audit;

public record FindingDraft(
        String ruleCode,
        FindingSeverity severity,
        String title,
        String description,
        String recommendation,
        String elementContext) {
}
