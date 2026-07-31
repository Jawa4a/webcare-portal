package com.webcare.portal.audit;

import java.util.UUID;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditedPageRepository extends JpaRepository<AuditedPage, UUID> {
    List<AuditedPage> findByAuditIdOrderByDepthAscUrlAsc(UUID auditId);
}
