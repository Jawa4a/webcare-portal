package com.webcare.portal.audit;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AuditResultsController {

    private final AuditResultsService resultsService;

    public AuditResultsController(AuditResultsService resultsService) {
        this.resultsService = resultsService;
    }

    @GetMapping("/audits/{auditId}/results")
    AuditResultsResponse results(
            @PathVariable UUID auditId,
            @RequestParam(required = false) FindingSeverity severity,
            @RequestParam(required = false) String ruleCode,
            @RequestParam(required = false) FindingChangeType changeType,
            @RequestParam(required = false) String query) {
        return resultsService.results(auditId, severity, ruleCode, changeType, query);
    }

    @GetMapping("/websites/{websiteId}/audit-comparison")
    AuditComparisonResponse compareLatest(@PathVariable UUID websiteId) {
        return resultsService.compareLatest(websiteId);
    }
}
