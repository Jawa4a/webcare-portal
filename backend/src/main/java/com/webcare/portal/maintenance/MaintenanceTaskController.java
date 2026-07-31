package com.webcare.portal.maintenance;

import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class MaintenanceTaskController {

    private final MaintenanceTaskService taskService;

    public MaintenanceTaskController(MaintenanceTaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping("/findings/{findingId}/tasks")
    ResponseEntity<TaskResponse> create(
            @PathVariable UUID findingId,
            @RequestBody TaskCreateRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(taskService.create(findingId, request, authentication));
    }

    @GetMapping("/tasks")
    TaskPageResponse list(
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) TaskPriority priority,
            @RequestParam(required = false) UUID assignedToUserId,
            @RequestParam(required = false) String query) {
        return taskService.list(status, priority, assignedToUserId, query);
    }

    @GetMapping("/tasks/{taskId}")
    TaskResponse get(@PathVariable UUID taskId) {
        return taskService.get(taskId);
    }

    @PutMapping("/tasks/{taskId}")
    TaskResponse update(
            @PathVariable UUID taskId,
            @Valid @RequestBody TaskUpdateRequest request,
            Authentication authentication) {
        return taskService.update(taskId, request, authentication);
    }
}
