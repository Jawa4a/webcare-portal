package com.webcare.portal.audit;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "audited_pages")
public class AuditedPage {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "audit_id", nullable = false)
    private Audit audit;

    @Column(nullable = false)
    private String url;

    @Column(name = "normalized_url", nullable = false)
    private String normalizedUrl;

    @Column(nullable = false)
    private int depth;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "response_time_ms")
    private Integer responseTimeMs;

    @Column(name = "content_type", length = 160)
    private String contentType;

    @Column(name = "response_bytes")
    private Long responseBytes;

    @Column(name = "checked_at")
    private Instant checkedAt;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "error_message")
    private String errorMessage;

    protected AuditedPage() {
    }

    public static AuditedPage create(Audit audit, PageSnapshot snapshot) {
        AuditedPage page = new AuditedPage();
        page.id = UUID.randomUUID();
        page.audit = audit;
        page.url = snapshot.url();
        page.normalizedUrl = snapshot.normalizedUrl();
        page.depth = snapshot.depth();
        page.httpStatus = snapshot.httpStatus();
        page.responseTimeMs = snapshot.responseTimeMs();
        page.contentType = snapshot.contentType();
        page.responseBytes = snapshot.responseBytes();
        page.checkedAt = Instant.now();
        page.errorCode = snapshot.errorCode();
        page.errorMessage = snapshot.errorMessage();
        return page;
    }

    public UUID getId() { return id; }
    public Audit getAudit() { return audit; }
    public String getUrl() { return url; }
    public String getNormalizedUrl() { return normalizedUrl; }
    public int getDepth() { return depth; }
    public Integer getHttpStatus() { return httpStatus; }
    public Integer getResponseTimeMs() { return responseTimeMs; }
    public String getContentType() { return contentType; }
    public Long getResponseBytes() { return responseBytes; }
    public Instant getCheckedAt() { return checkedAt; }
    public String getErrorCode() { return errorCode; }
    public String getErrorMessage() { return errorMessage; }
}
