package com.webcare.portal.audit;

import java.util.UUID;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FindingRepository extends JpaRepository<Finding, UUID> {
    @Query("""
            select f from Finding f
            join fetch f.auditedPage p
            join fetch p.audit a
            join fetch a.website
            where a.id = :auditId
            """)
    List<Finding> findDetailedByAuditId(@Param("auditId") UUID auditId);

    @Query("""
            select f from Finding f
            join fetch f.auditedPage p
            join fetch p.audit a
            join fetch a.website
            where f.id = :id
            """)
    Optional<Finding> findDetailedById(@Param("id") UUID id);
}
