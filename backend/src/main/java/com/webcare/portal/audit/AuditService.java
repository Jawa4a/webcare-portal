package com.webcare.portal.audit;

import java.util.List;
import java.util.UUID;

import com.webcare.portal.api.ConflictException;
import com.webcare.portal.api.NotFoundException;
import com.webcare.portal.auth.PortalUser;
import com.webcare.portal.auth.PortalUserRepository;
import com.webcare.portal.website.Website;
import com.webcare.portal.website.WebsiteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private static final List<AuditStatus> ACTIVE_STATUSES = List.of(AuditStatus.CREATED, AuditStatus.RUNNING);
    private final AuditRepository auditRepository;
    private final WebsiteRepository websiteRepository;
    private final PortalUserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    public AuditService(
            AuditRepository auditRepository,
            WebsiteRepository websiteRepository,
            PortalUserRepository userRepository,
            ApplicationEventPublisher eventPublisher) {
        this.auditRepository = auditRepository;
        this.websiteRepository = websiteRepository;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public AuditResponse start(UUID websiteId, Authentication authentication) {
        Website website = websiteRepository.findById(websiteId)
                .orElseThrow(() -> new NotFoundException("Сайт не найден."));
        if (!website.isActive()) {
            throw new ConflictException("Нельзя запустить аудит для неактивного сайта.");
        }
        if (auditRepository.existsByWebsiteIdAndStatusIn(websiteId, ACTIVE_STATUSES)) {
            throw new ConflictException("Для этого сайта уже выполняется аудит.");
        }
        PortalUser starter = userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new NotFoundException("Текущий пользователь не найден."));
        Audit audit = auditRepository.saveAndFlush(Audit.create(website, starter));
        eventPublisher.publishEvent(new AuditStartedEvent(audit.getId()));
        log.info("Audit created: id={}, websiteId={}, user={}", audit.getId(), websiteId, authentication.getName());
        return AuditResponse.from(audit);
    }

    @Transactional(readOnly = true)
    public AuditResponse get(UUID auditId) {
        return AuditResponse.from(find(auditId));
    }

    @Transactional(readOnly = true)
    public Page<AuditResponse> list(UUID websiteId, int page, int size) {
        PageRequest request = PageRequest.of(
                Math.max(0, page),
                Math.max(1, Math.min(size, 100)),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Audit> result = websiteId == null
                ? auditRepository.findAll(request)
                : auditRepository.findByWebsiteId(websiteId, request);
        return result.map(AuditResponse::from);
    }

    private Audit find(UUID id) {
        return auditRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Аудит не найден."));
    }
}
