package com.webcare.portal.audit;

import java.time.Instant;
import java.util.UUID;

import com.webcare.portal.auth.PortalUser;
import com.webcare.portal.website.Website;
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
@Table(name = "audits")
public class Audit {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "website_id", nullable = false)
    private Website website;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "started_by_user_id", nullable = false)
    private PortalUser startedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private AuditStatus status;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "pages_discovered", nullable = false)
    private int pagesDiscovered;

    @Column(name = "pages_checked", nullable = false)
    private int pagesChecked;

    @Column(name = "findings_count", nullable = false)
    private int findingsCount;

    private Short score;

    @Column(name = "failure_code", length = 64)
    private String failureCode;

    @Column(name = "failure_message")
    private String failureMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Audit() {
    }

    public static Audit create(Website website, PortalUser startedBy) {
        Audit audit = new Audit();
        audit.id = UUID.randomUUID();
        audit.website = website;
        audit.startedBy = startedBy;
        audit.status = AuditStatus.CREATED;
        return audit;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public void markRunning() {
        status = AuditStatus.RUNNING;
        startedAt = Instant.now();
        finishedAt = null;
        failureCode = null;
        failureMessage = null;
    }

    public void updateProgress(int discovered, int checked, int findings) {
        pagesDiscovered = Math.max(pagesDiscovered, discovered);
        pagesChecked = Math.max(pagesChecked, checked);
        findingsCount = Math.max(findingsCount, findings);
    }

    public void complete(int discovered, int checked, int findings, int finalScore) {
        updateProgress(discovered, checked, findings);
        score = (short) Math.max(0, Math.min(100, finalScore));
        status = AuditStatus.COMPLETED;
        finishedAt = Instant.now();
    }

    public void fail(String code, String message) {
        status = AuditStatus.FAILED;
        failureCode = code;
        failureMessage = message;
        finishedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Website getWebsite() { return website; }
    public PortalUser getStartedBy() { return startedBy; }
    public AuditStatus getStatus() { return status; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public int getPagesDiscovered() { return pagesDiscovered; }
    public int getPagesChecked() { return pagesChecked; }
    public int getFindingsCount() { return findingsCount; }
    public Integer getScore() { return score == null ? null : score.intValue(); }
    public String getFailureCode() { return failureCode; }
    public String getFailureMessage() { return failureMessage; }
    public Instant getCreatedAt() { return createdAt; }
}
