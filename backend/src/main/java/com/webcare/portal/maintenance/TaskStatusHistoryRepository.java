package com.webcare.portal.maintenance;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskStatusHistoryRepository extends JpaRepository<TaskStatusHistory, UUID> {
    List<TaskStatusHistory> findByTaskIdOrderByChangedAtDesc(UUID taskId);
    List<TaskStatusHistory> findByTaskIdInOrderByChangedAtDesc(Collection<UUID> taskIds);
}
