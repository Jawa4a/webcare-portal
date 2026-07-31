package com.webcare.portal.audit;

import java.util.Collection;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditRepository extends JpaRepository<Audit, UUID> {
    boolean existsByWebsiteIdAndStatusIn(UUID websiteId, Collection<AuditStatus> statuses);
    Page<Audit> findByWebsiteId(UUID websiteId, Pageable pageable);
    List<Audit> findTop2ByWebsiteIdAndStatusOrderByCreatedAtDesc(UUID websiteId, AuditStatus status);
    Optional<Audit> findFirstByWebsiteIdAndStatusAndCreatedAtLessThanOrderByCreatedAtDesc(
            UUID websiteId, AuditStatus status, Instant createdAt);
    List<Audit> findAllByStatusOrderByCreatedAtDesc(AuditStatus status);
    List<Audit> findTop5ByOrderByCreatedAtDesc();
    long countByStatus(AuditStatus status);
}
