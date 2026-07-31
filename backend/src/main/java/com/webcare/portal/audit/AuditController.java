package com.webcare.portal.audit;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @PostMapping("/websites/{websiteId}/audits")
    ResponseEntity<AuditResponse> start(@PathVariable UUID websiteId, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(auditService.start(websiteId, authentication));
    }

    @GetMapping("/audits/{auditId}")
    AuditResponse get(@PathVariable UUID auditId) {
        return auditService.get(auditId);
    }

    @GetMapping("/audits")
    AuditPageResponse list(
            @RequestParam(required = false) UUID websiteId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        var result = auditService.list(websiteId, page, size);
        return new AuditPageResponse(
                result.getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }
}
