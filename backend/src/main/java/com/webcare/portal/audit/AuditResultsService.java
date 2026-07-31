package com.webcare.portal.audit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.webcare.portal.api.NotFoundException;
import com.webcare.portal.maintenance.MaintenanceTask;
import com.webcare.portal.maintenance.MaintenanceTaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditResultsService {

    private final AuditRepository auditRepository;
    private final AuditedPageRepository pageRepository;
    private final FindingRepository findingRepository;
    private final MaintenanceTaskRepository taskRepository;
    private final AuditComparisonEngine comparisonEngine = new AuditComparisonEngine();

    public AuditResultsService(
            AuditRepository auditRepository,
            AuditedPageRepository pageRepository,
            FindingRepository findingRepository,
            MaintenanceTaskRepository taskRepository) {
        this.auditRepository = auditRepository;
        this.pageRepository = pageRepository;
        this.findingRepository = findingRepository;
        this.taskRepository = taskRepository;
    }

    @Transactional(readOnly = true)
    public AuditResultsResponse results(
            UUID auditId,
            FindingSeverity severity,
            String ruleCode,
            FindingChangeType changeType,
            String query) {
        Audit audit = findAudit(auditId);
        List<Finding> current = sorted(findingRepository.findDetailedByAuditId(auditId));
        Audit previousAudit = previous(audit);
        List<Finding> previous = previousAudit == null
                ? List.of()
                : findingRepository.findDetailedByAuditId(previousAudit.getId());
        var difference = comparisonEngine.compare(index(current), index(previous));
        Map<UUID, FindingChangeType> changes = new LinkedHashMap<>();
        difference.newItems().forEach(finding -> changes.put(finding.getId(), FindingChangeType.NEW));
        difference.ongoingItems().forEach(finding -> changes.put(finding.getId(), FindingChangeType.ONGOING));
        List<Finding> comparisonScope = new ArrayList<>(current);
        comparisonScope.addAll(previous);
        Map<String, UUID> taskIds = taskIdsByComparisonKey(comparisonScope);

        Map<FindingSeverity, Long> severityCounts = new EnumMap<>(FindingSeverity.class);
        for (FindingSeverity value : FindingSeverity.values()) {
            severityCounts.put(value, current.stream().filter(item -> item.getSeverity() == value).count());
        }

        String normalizedRule = normalize(ruleCode);
        String normalizedQuery = normalize(query);
        List<FindingResponse> filtered = current.stream()
                .filter(finding -> severity == null || finding.getSeverity() == severity)
                .filter(finding -> normalizedRule.isEmpty()
                        || finding.getRuleCode().toLowerCase(Locale.ROOT).equals(normalizedRule))
                .filter(finding -> changeType == null || changes.get(finding.getId()) == changeType)
                .filter(finding -> matches(finding, normalizedQuery))
                .map(finding -> FindingResponse.from(
                        finding, changes.get(finding.getId()), taskIds.get(comparisonKey(finding))))
                .toList();

        return new AuditResultsResponse(
                AuditResponse.from(audit),
                previousAudit == null ? null : previousAudit.getId(),
                severityCounts,
                pageRepository.findByAuditIdOrderByDepthAscUrlAsc(auditId).stream()
                        .map(AuditedPageResultResponse::from).toList(),
                filtered,
                current.size(),
                filtered.size());
    }

    @Transactional(readOnly = true)
    public AuditComparisonResponse compareLatest(UUID websiteId) {
        List<Audit> audits = auditRepository.findTop2ByWebsiteIdAndStatusOrderByCreatedAtDesc(
                websiteId, AuditStatus.COMPLETED);
        if (audits.isEmpty()) {
            throw new NotFoundException("Для сайта ещё нет завершённых аудитов.");
        }
        Audit latest = audits.get(0);
        Audit previous = audits.size() > 1 ? audits.get(1) : null;
        List<Finding> currentFindings = sorted(findingRepository.findDetailedByAuditId(latest.getId()));
        List<Finding> previousFindings = previous == null
                ? List.of()
                : sorted(findingRepository.findDetailedByAuditId(previous.getId()));
        var difference = comparisonEngine.compare(index(currentFindings), index(previousFindings));
        List<Finding> all = new ArrayList<>(currentFindings);
        all.addAll(previousFindings);
        Map<String, UUID> taskIds = taskIdsByComparisonKey(all);
        Integer scoreDelta = previous == null || latest.getScore() == null || previous.getScore() == null
                ? null
                : latest.getScore() - previous.getScore();
        return new AuditComparisonResponse(
                websiteId,
                latest.getWebsite().getName(),
                AuditResponse.from(latest),
                previous == null ? null : AuditResponse.from(previous),
                scoreDelta,
                responses(difference.newItems(), FindingChangeType.NEW, taskIds),
                responses(difference.ongoingItems(), FindingChangeType.ONGOING, taskIds),
                responses(difference.fixedItems(), FindingChangeType.FIXED, taskIds));
    }

    private List<FindingResponse> responses(
            List<Finding> findings,
            FindingChangeType type,
            Map<String, UUID> taskIds) {
        return findings.stream().map(finding -> FindingResponse.from(
                finding, type, taskIds.get(comparisonKey(finding)))).toList();
    }

    private Audit findAudit(UUID auditId) {
        return auditRepository.findById(auditId)
                .orElseThrow(() -> new NotFoundException("Аудит не найден."));
    }

    private Audit previous(Audit audit) {
        return auditRepository
                .findFirstByWebsiteIdAndStatusAndCreatedAtLessThanOrderByCreatedAtDesc(
                        audit.getWebsite().getId(), AuditStatus.COMPLETED, audit.getCreatedAt())
                .orElse(null);
    }

    private Map<String, Finding> index(List<Finding> findings) {
        return findings.stream().collect(Collectors.toMap(
                this::comparisonKey,
                Function.identity(),
                (first, ignored) -> first,
                LinkedHashMap::new));
    }

    private String comparisonKey(Finding finding) {
        return finding.getAuditedPage().getNormalizedUrl() + "|" + finding.getFingerprint().trim();
    }

    private Map<String, UUID> taskIdsByComparisonKey(List<Finding> findings) {
        if (findings.isEmpty()) {
            return Map.of();
        }
        return taskRepository.findByFindingIdIn(findings.stream().map(Finding::getId).toList()).stream()
                .collect(Collectors.toMap(
                        task -> comparisonKey(task.getFinding()), MaintenanceTask::getId, (first, ignored) -> first));
    }

    private List<Finding> sorted(List<Finding> findings) {
        return findings.stream().sorted(Comparator
                .comparingInt((Finding finding) -> severityRank(finding.getSeverity()))
                .thenComparing(finding -> finding.getAuditedPage().getUrl())
                .thenComparing(Finding::getRuleCode)).toList();
    }

    private int severityRank(FindingSeverity severity) {
        return switch (severity) {
            case CRITICAL -> 0;
            case HIGH -> 1;
            case MEDIUM -> 2;
            case LOW -> 3;
            case INFO -> 4;
        };
    }

    private boolean matches(Finding finding, String query) {
        if (query.isEmpty()) {
            return true;
        }
        return java.util.stream.Stream.of(
                        finding.getTitle(), finding.getDescription(), finding.getRuleCode(),
                        finding.getAuditedPage().getUrl(), finding.getElementContext())
                .filter(value -> value != null)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(value -> value.contains(query));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
