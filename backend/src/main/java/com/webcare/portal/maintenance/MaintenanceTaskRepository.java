package com.webcare.portal.maintenance;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MaintenanceTaskRepository extends JpaRepository<MaintenanceTask, UUID> {
    boolean existsByFindingId(UUID findingId);
    List<MaintenanceTask> findByFindingIdIn(Collection<UUID> findingIds);
    long countByStatusIn(Collection<TaskStatus> statuses);

    @Query("""
            select t from MaintenanceTask t
            join fetch t.finding f
            join fetch f.auditedPage p
            join fetch p.audit a
            join fetch a.website
            join fetch t.createdBy
            left join fetch t.assignedTo
            order by t.updatedAt desc
            """)
    List<MaintenanceTask> findAllDetailed();

    @Query("""
            select t from MaintenanceTask t
            join fetch t.finding f
            join fetch f.auditedPage p
            join fetch p.audit a
            join fetch a.website
            join fetch t.createdBy
            left join fetch t.assignedTo
            where t.id = :id
            """)
    Optional<MaintenanceTask> findDetailedById(@Param("id") UUID id);
}
