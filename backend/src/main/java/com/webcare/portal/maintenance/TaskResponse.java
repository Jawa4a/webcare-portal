package com.webcare.portal.maintenance;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.webcare.portal.audit.AuditedPage;
import com.webcare.portal.audit.Finding;

public record TaskResponse(
        UUID id,
        UUID findingId,
        UUID auditId,
        UUID websiteId,
        String websiteName,
        String pageUrl,
        String ruleCode,
        String findingTitle,
        String title,
        String description,
        TaskPriority priority,
        TaskStatus status,
        UUID assignedToUserId,
        String assignedTo,
        UUID createdByUserId,
        String createdBy,
        Instant dueAt,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt,
        List<TaskStatusHistoryResponse> history) {

    static TaskResponse from(MaintenanceTask task, List<TaskStatusHistory> history) {
        Finding finding = task.getFinding();
        AuditedPage page = finding.getAuditedPage();
        var assigned = task.getAssignedTo();
        return new TaskResponse(
                task.getId(), finding.getId(), page.getAudit().getId(), page.getAudit().getWebsite().getId(),
                page.getAudit().getWebsite().getName(), page.getUrl(), finding.getRuleCode(), finding.getTitle(),
                task.getTitle(), task.getDescription(), task.getPriority(), task.getStatus(),
                assigned == null ? null : assigned.getId(), assigned == null ? null : assigned.getDisplayName(),
                task.getCreatedBy().getId(), task.getCreatedBy().getDisplayName(), task.getDueAt(),
                task.getCompletedAt(), task.getCreatedAt(), task.getUpdatedAt(),
                history.stream().map(TaskStatusHistoryResponse::from).toList());
    }
}
