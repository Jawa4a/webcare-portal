package com.webcare.portal.audit;

import java.util.List;
import java.util.UUID;

import com.webcare.portal.api.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditPersistenceService {

    private final AuditRepository auditRepository;
    private final AuditedPageRepository pageRepository;
    private final FindingRepository findingRepository;

    public AuditPersistenceService(
            AuditRepository auditRepository,
            AuditedPageRepository pageRepository,
            FindingRepository findingRepository) {
        this.auditRepository = auditRepository;
        this.pageRepository = pageRepository;
        this.findingRepository = findingRepository;
    }

    @Transactional
    public AuditRunContext begin(UUID auditId) {
        Audit audit = findAudit(auditId);
        audit.markRunning();
        var website = audit.getWebsite();
        return new AuditRunContext(
                audit.getId(),
                website.getName(),
                website.getStartUrl(),
                website.getNormalizedHost(),
                website.getMaxPages(),
                website.getMaxDepth());
    }

    @Transactional
    public UUID savePage(UUID auditId, PageSnapshot snapshot, List<FindingDraft> drafts) {
        Audit audit = findAudit(auditId);
        AuditedPage page = pageRepository.save(AuditedPage.create(audit, snapshot));
        if (!drafts.isEmpty()) {
            findingRepository.saveAll(drafts.stream().map(draft -> Finding.create(page, draft)).toList());
        }
        return page.getId();
    }

    @Transactional
    public int appendFindings(UUID pageId, List<FindingDraft> drafts) {
        if (drafts.isEmpty()) {
            return 0;
        }
        AuditedPage page = pageRepository.findById(pageId)
                .orElseThrow(() -> new NotFoundException("Проверенная страница не найдена."));
        findingRepository.saveAll(drafts.stream().map(draft -> Finding.create(page, draft)).toList());
        return drafts.size();
    }

    @Transactional
    public void updateProgress(UUID auditId, int discovered, int checked, int findings) {
        findAudit(auditId).updateProgress(discovered, checked, findings);
    }

    @Transactional
    public void complete(UUID auditId, int discovered, int checked, int findings, int score) {
        findAudit(auditId).complete(discovered, checked, findings, score);
    }

    @Transactional
    public void fail(UUID auditId, String code, String message) {
        Audit audit = findAudit(auditId);
        if (audit.getStatus() != AuditStatus.COMPLETED && audit.getStatus() != AuditStatus.FAILED) {
            audit.fail(code, safeMessage(message));
        }
    }

    private Audit findAudit(UUID id) {
        return auditRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Аудит не найден."));
    }

    private String safeMessage(String message) {
        if (message == null || message.isBlank()) {
            return "Аудит завершился с внутренней ошибкой.";
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }
}
