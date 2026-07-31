package com.webcare.portal.maintenance;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.webcare.portal.api.ConflictException;
import com.webcare.portal.api.NotFoundException;
import com.webcare.portal.audit.Finding;
import com.webcare.portal.audit.FindingRepository;
import com.webcare.portal.audit.FindingSeverity;
import com.webcare.portal.auth.PortalUser;
import com.webcare.portal.auth.PortalUserRepository;
import com.webcare.portal.auth.RoleCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MaintenanceTaskService {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceTaskService.class);
    private final MaintenanceTaskRepository taskRepository;
    private final TaskStatusHistoryRepository historyRepository;
    private final FindingRepository findingRepository;
    private final PortalUserRepository userRepository;

    public MaintenanceTaskService(
            MaintenanceTaskRepository taskRepository,
            TaskStatusHistoryRepository historyRepository,
            FindingRepository findingRepository,
            PortalUserRepository userRepository) {
        this.taskRepository = taskRepository;
        this.historyRepository = historyRepository;
        this.findingRepository = findingRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public TaskResponse create(UUID findingId, TaskCreateRequest request, Authentication authentication) {
        if (taskRepository.existsByFindingId(findingId)) {
            throw new ConflictException("Для этой проблемы задача уже создана.");
        }
        Finding finding = findingRepository.findDetailedById(findingId)
                .orElseThrow(() -> new NotFoundException("Проблема аудита не найдена."));
        boolean logicalTaskExists = taskRepository.findAllDetailed().stream()
                .map(MaintenanceTask::getFinding)
                .anyMatch(existing -> comparisonKey(existing).equals(comparisonKey(finding)));
        if (logicalTaskExists) {
            throw new ConflictException("Для этой продолжающейся проблемы задача уже существует.");
        }
        PortalUser creator = currentUser(authentication);
        PortalUser assignedTo = assignee(request.assignedToUserId());
        TaskPriority priority = request.priority() == null
                ? defaultPriority(finding.getSeverity())
                : request.priority();
        MaintenanceTask task = taskRepository.saveAndFlush(MaintenanceTask.create(
                finding, creator, assignedTo, priority, request.dueAt()));
        TaskStatusHistory history = historyRepository.saveAndFlush(TaskStatusHistory.create(
                task, null, TaskStatus.OPEN, creator, "Задача создана из результата аудита."));
        log.info("Maintenance task created: id={}, findingId={}, user={}",
                task.getId(), findingId, authentication.getName());
        return TaskResponse.from(task, List.of(history));
    }

    @Transactional(readOnly = true)
    public TaskPageResponse list(
            TaskStatus status,
            TaskPriority priority,
            UUID assignedToUserId,
            String query) {
        List<MaintenanceTask> all = taskRepository.findAllDetailed();
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<MaintenanceTask> filtered = all.stream()
                .filter(task -> status == null || task.getStatus() == status)
                .filter(task -> priority == null || task.getPriority() == priority)
                .filter(task -> assignedToUserId == null
                        || task.getAssignedTo() != null && assignedToUserId.equals(task.getAssignedTo().getId()))
                .filter(task -> matches(task, normalizedQuery))
                .toList();
        Map<UUID, List<TaskStatusHistory>> histories = histories(filtered);
        return new TaskPageResponse(
                filtered.stream().map(task -> TaskResponse.from(
                        task, histories.getOrDefault(task.getId(), List.of()))).toList(),
                filtered.size());
    }

    @Transactional(readOnly = true)
    public TaskResponse get(UUID taskId) {
        MaintenanceTask task = find(taskId);
        return TaskResponse.from(task, historyRepository.findByTaskIdOrderByChangedAtDesc(taskId));
    }

    @Transactional
    public TaskResponse update(UUID taskId, TaskUpdateRequest request, Authentication authentication) {
        MaintenanceTask task = find(taskId);
        PortalUser changedBy = currentUser(authentication);
        PortalUser assignedTo = assignee(request.assignedToUserId());
        TaskStatus oldStatus = task.update(
                request.title(), request.description(), request.priority(), request.status(),
                assignedTo, request.dueAt());
        taskRepository.saveAndFlush(task);
        if (oldStatus != request.status()) {
            historyRepository.saveAndFlush(TaskStatusHistory.create(
                    task, oldStatus, request.status(), changedBy, request.comment()));
            log.info("Maintenance task status changed: id={}, from={}, to={}, user={}",
                    taskId, oldStatus, request.status(), authentication.getName());
        }
        return TaskResponse.from(task, historyRepository.findByTaskIdOrderByChangedAtDesc(taskId));
    }

    private MaintenanceTask find(UUID taskId) {
        return taskRepository.findDetailedById(taskId)
                .orElseThrow(() -> new NotFoundException("Задача обслуживания не найдена."));
    }

    private PortalUser currentUser(Authentication authentication) {
        return userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new NotFoundException("Текущий пользователь не найден."));
    }

    private PortalUser assignee(UUID userId) {
        if (userId == null) {
            return null;
        }
        PortalUser user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Ответственный пользователь не найден."));
        boolean canBeAssigned = user.isActive() && user.getRoles().stream()
                .anyMatch(role -> role.getCode() == RoleCode.ADMIN || role.getCode() == RoleCode.SPECIALIST);
        if (!canBeAssigned) {
            throw new ConflictException("Ответственным может быть активный администратор или специалист.");
        }
        return user;
    }

    private TaskPriority defaultPriority(FindingSeverity severity) {
        return switch (severity) {
            case CRITICAL -> TaskPriority.CRITICAL;
            case HIGH -> TaskPriority.HIGH;
            case MEDIUM -> TaskPriority.MEDIUM;
            case LOW, INFO -> TaskPriority.LOW;
        };
    }

    private boolean matches(MaintenanceTask task, String query) {
        if (query.isEmpty()) {
            return true;
        }
        Finding finding = task.getFinding();
        return Stream.of(
                        task.getTitle(),
                        task.getDescription(),
                        finding.getRuleCode(),
                        finding.getAuditedPage().getUrl(),
                        finding.getAuditedPage().getAudit().getWebsite().getName())
                .filter(value -> value != null)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(value -> value.contains(query));
    }

    private Map<UUID, List<TaskStatusHistory>> histories(Collection<MaintenanceTask> tasks) {
        if (tasks.isEmpty()) {
            return Map.of();
        }
        return historyRepository.findByTaskIdInOrderByChangedAtDesc(
                        tasks.stream().map(MaintenanceTask::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(
                        history -> history.getTask().getId(),
                        Collectors.toList()));
    }

    private String comparisonKey(Finding finding) {
        return finding.getAuditedPage().getAudit().getWebsite().getId()
                + "|" + finding.getAuditedPage().getNormalizedUrl()
                + "|" + finding.getFingerprint().trim();
    }
}
