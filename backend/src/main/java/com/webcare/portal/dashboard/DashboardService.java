package com.webcare.portal.dashboard;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.webcare.portal.audit.Audit;
import com.webcare.portal.audit.AuditRepository;
import com.webcare.portal.audit.AuditResponse;
import com.webcare.portal.audit.AuditStatus;
import com.webcare.portal.maintenance.MaintenanceTaskRepository;
import com.webcare.portal.maintenance.TaskStatus;
import com.webcare.portal.website.Website;
import com.webcare.portal.website.WebsiteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private final WebsiteRepository websiteRepository;
    private final AuditRepository auditRepository;
    private final MaintenanceTaskRepository taskRepository;

    public DashboardService(
            WebsiteRepository websiteRepository,
            AuditRepository auditRepository,
            MaintenanceTaskRepository taskRepository) {
        this.websiteRepository = websiteRepository;
        this.auditRepository = auditRepository;
        this.taskRepository = taskRepository;
    }

    @Transactional(readOnly = true)
    public DashboardResponse get() {
        List<Website> websites = websiteRepository.findAll().stream()
                .sorted(Comparator.comparing(Website::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        Map<UUID, Audit> latestByWebsite = new HashMap<>();
        for (Audit audit : auditRepository.findAllByStatusOrderByCreatedAtDesc(AuditStatus.COMPLETED)) {
            latestByWebsite.putIfAbsent(audit.getWebsite().getId(), audit);
        }
        int latestFindings = latestByWebsite.values().stream().mapToInt(Audit::getFindingsCount).sum();
        int scoreTotal = latestByWebsite.values().stream()
                .map(Audit::getScore).filter(score -> score != null).mapToInt(Integer::intValue).sum();
        long scored = latestByWebsite.values().stream().filter(audit -> audit.getScore() != null).count();
        Integer averageScore = scored == 0 ? null : (int) Math.round((double) scoreTotal / scored);
        List<DashboardSiteResponse> siteResponses = websites.stream().map(website -> {
            Audit audit = latestByWebsite.get(website.getId());
            return new DashboardSiteResponse(
                    website.getId(), website.getName(), website.getNormalizedHost(), website.isActive(),
                    audit == null ? null : audit.getId(),
                    audit == null ? null : audit.getStatus(),
                    audit == null ? null : audit.getScore(),
                    audit == null ? 0 : audit.getFindingsCount(),
                    audit == null ? null : audit.getFinishedAt());
        }).toList();
        return new DashboardResponse(
                websites.size(),
                websiteRepository.countByActiveTrue(),
                auditRepository.countByStatus(AuditStatus.COMPLETED),
                taskRepository.countByStatusIn(List.of(TaskStatus.OPEN, TaskStatus.IN_PROGRESS)),
                latestFindings,
                averageScore,
                siteResponses,
                auditRepository.findTop5ByOrderByCreatedAtDesc().stream().map(AuditResponse::from).toList());
    }
}
