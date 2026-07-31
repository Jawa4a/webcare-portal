package com.webcare.portal.maintenance;

import java.time.Instant;
import java.util.UUID;

import com.webcare.portal.audit.Finding;
import com.webcare.portal.auth.PortalUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "maintenance_tasks")
public class MaintenanceTask {

    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "finding_id", nullable = false, unique = true)
    private Finding finding;

    @Column(nullable = false)
    private String title;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TaskPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private TaskStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to_user_id")
    private PortalUser assignedTo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private PortalUser createdBy;

    @Column(name = "due_at")
    private Instant dueAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MaintenanceTask() {
    }

    public static MaintenanceTask create(
            Finding finding,
            PortalUser createdBy,
            PortalUser assignedTo,
            TaskPriority priority,
            Instant dueAt) {
        MaintenanceTask task = new MaintenanceTask();
        task.id = UUID.randomUUID();
        task.finding = finding;
        task.title = finding.getTitle();
        task.description = finding.getDescription();
        task.priority = priority;
        task.status = TaskStatus.OPEN;
        task.assignedTo = assignedTo;
        task.createdBy = createdBy;
        task.dueAt = dueAt;
        return task;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public TaskStatus update(
            String title,
            String description,
            TaskPriority priority,
            TaskStatus status,
            PortalUser assignedTo,
            Instant dueAt) {
        TaskStatus oldStatus = this.status;
        this.title = title.trim();
        this.description = description == null || description.isBlank() ? null : description.trim();
        this.priority = priority;
        this.status = status;
        this.assignedTo = assignedTo;
        this.dueAt = dueAt;
        if (status == TaskStatus.DONE && oldStatus != TaskStatus.DONE) {
            completedAt = Instant.now();
        } else if (status != TaskStatus.DONE) {
            completedAt = null;
        }
        return oldStatus;
    }

    public UUID getId() { return id; }
    public Finding getFinding() { return finding; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public TaskPriority getPriority() { return priority; }
    public TaskStatus getStatus() { return status; }
    public PortalUser getAssignedTo() { return assignedTo; }
    public PortalUser getCreatedBy() { return createdBy; }
    public Instant getDueAt() { return dueAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
