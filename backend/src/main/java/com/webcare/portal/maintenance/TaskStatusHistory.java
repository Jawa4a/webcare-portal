package com.webcare.portal.maintenance;

import java.time.Instant;
import java.util.UUID;

import com.webcare.portal.auth.PortalUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "task_status_history")
public class TaskStatusHistory {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private MaintenanceTask task;

    @Enumerated(EnumType.STRING)
    @Column(name = "old_status", length = 24)
    private TaskStatus oldStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 24)
    private TaskStatus newStatus;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "changed_by_user_id", nullable = false)
    private PortalUser changedBy;

    private String comment;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    protected TaskStatusHistory() {
    }

    public static TaskStatusHistory create(
            MaintenanceTask task,
            TaskStatus oldStatus,
            TaskStatus newStatus,
            PortalUser changedBy,
            String comment) {
        TaskStatusHistory history = new TaskStatusHistory();
        history.id = UUID.randomUUID();
        history.task = task;
        history.oldStatus = oldStatus;
        history.newStatus = newStatus;
        history.changedBy = changedBy;
        history.comment = comment == null || comment.isBlank() ? null : comment.trim();
        return history;
    }

    @PrePersist
    void onCreate() {
        changedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public MaintenanceTask getTask() { return task; }
    public TaskStatus getOldStatus() { return oldStatus; }
    public TaskStatus getNewStatus() { return newStatus; }
    public PortalUser getChangedBy() { return changedBy; }
    public String getComment() { return comment; }
    public Instant getChangedAt() { return changedAt; }
}
