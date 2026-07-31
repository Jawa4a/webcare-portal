package com.webcare.portal.website;

import java.time.Instant;
import java.util.UUID;

import com.webcare.portal.auth.PortalUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "websites")
public class Website {

    @Id
    private UUID id;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(name = "start_url", nullable = false)
    private String startUrl;

    @Column(name = "normalized_host", nullable = false, unique = true, length = 253)
    private String normalizedHost;

    @Column(name = "max_pages", nullable = false)
    private int maxPages;

    @Column(name = "max_depth", nullable = false)
    private int maxDepth;

    @Column(nullable = false)
    private boolean active;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private PortalUser createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Website() {
    }

    public static Website create(
            String name,
            UrlPolicyService.ValidatedUrl url,
            int maxPages,
            int maxDepth,
            boolean active,
            PortalUser createdBy) {
        Website website = new Website();
        website.id = UUID.randomUUID();
        website.apply(name, url, maxPages, maxDepth, active);
        website.createdBy = createdBy;
        return website;
    }

    public void update(
            String name,
            UrlPolicyService.ValidatedUrl url,
            int maxPages,
            int maxDepth,
            boolean active) {
        apply(name, url, maxPages, maxDepth, active);
    }

    private void apply(
            String name,
            UrlPolicyService.ValidatedUrl url,
            int maxPages,
            int maxDepth,
            boolean active) {
        this.name = name.trim();
        this.startUrl = url.url();
        this.normalizedHost = url.host();
        this.maxPages = maxPages;
        this.maxDepth = maxDepth;
        this.active = active;
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

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getStartUrl() { return startUrl; }
    public String getNormalizedHost() { return normalizedHost; }
    public int getMaxPages() { return maxPages; }
    public int getMaxDepth() { return maxDepth; }
    public boolean isActive() { return active; }
    public PortalUser getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
