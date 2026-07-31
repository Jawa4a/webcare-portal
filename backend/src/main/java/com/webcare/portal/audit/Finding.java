package com.webcare.portal.audit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "findings")
public class Finding {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "audited_page_id", nullable = false)
    private AuditedPage auditedPage;

    @Column(name = "rule_code", nullable = false, length = 64)
    private String ruleCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FindingSeverity severity;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private String recommendation;

    @Column(name = "element_context")
    private String elementContext;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 64)
    private String fingerprint;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Finding() {
    }

    public static Finding create(AuditedPage page, FindingDraft draft) {
        Finding finding = new Finding();
        finding.id = UUID.randomUUID();
        finding.auditedPage = page;
        finding.ruleCode = draft.ruleCode();
        finding.severity = draft.severity();
        finding.title = draft.title();
        finding.description = draft.description();
        finding.recommendation = draft.recommendation();
        finding.elementContext = draft.elementContext();
        finding.fingerprint = sha256(draft.ruleCode() + "|" + String.valueOf(draft.elementContext()));
        return finding;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public UUID getId() { return id; }
    public AuditedPage getAuditedPage() { return auditedPage; }
    public String getRuleCode() { return ruleCode; }
    public FindingSeverity getSeverity() { return severity; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getRecommendation() { return recommendation; }
    public String getElementContext() { return elementContext; }
    public String getFingerprint() { return fingerprint; }
    public Instant getCreatedAt() { return createdAt; }
}
