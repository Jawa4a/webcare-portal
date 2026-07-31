package com.webcare.portal.website;

import java.util.Set;
import java.util.UUID;

import com.webcare.portal.api.ConflictException;
import com.webcare.portal.api.NotFoundException;
import com.webcare.portal.auth.PortalUser;
import com.webcare.portal.auth.PortalUserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WebsiteService {

    private static final Logger log = LoggerFactory.getLogger(WebsiteService.class);
    private static final Set<String> SORTABLE_FIELDS = Set.of("name", "createdAt", "updatedAt");
    private final WebsiteRepository websiteRepository;
    private final PortalUserRepository userRepository;
    private final UrlPolicyService urlPolicyService;

    public WebsiteService(
            WebsiteRepository websiteRepository,
            PortalUserRepository userRepository,
            UrlPolicyService urlPolicyService) {
        this.websiteRepository = websiteRepository;
        this.userRepository = userRepository;
        this.urlPolicyService = urlPolicyService;
    }

    @Transactional(readOnly = true)
    public Page<WebsiteResponse> list(int page, int size, String sort) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        String safeSort = SORTABLE_FIELDS.contains(sort) ? sort : "name";
        return websiteRepository.findAll(PageRequest.of(safePage, safeSize, Sort.by(safeSort).ascending()))
                .map(WebsiteResponse::from);
    }

    @Transactional(readOnly = true)
    public WebsiteResponse get(UUID id) {
        return WebsiteResponse.from(find(id));
    }

    @Transactional
    public WebsiteResponse create(WebsiteRequest request, Authentication authentication) {
        UrlPolicyService.ValidatedUrl validatedUrl = urlPolicyService.validateAndNormalize(request.url());
        if (websiteRepository.existsByNormalizedHost(validatedUrl.host())) {
            throw new ConflictException("Сайт с таким доменом уже добавлен.");
        }
        PortalUser creator = userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new NotFoundException("Текущий пользователь не найден."));
        Website website = Website.create(
                request.name(),
                validatedUrl,
                request.maxPages() == null ? 50 : request.maxPages(),
                request.maxDepth() == null ? 2 : request.maxDepth(),
                request.active() == null || request.active(),
                creator);
        Website saved = websiteRepository.save(website);
        log.info("Website created: id={}, host={}, user={}", saved.getId(), saved.getNormalizedHost(), authentication.getName());
        return WebsiteResponse.from(saved);
    }

    @Transactional
    public WebsiteResponse update(UUID id, WebsiteRequest request) {
        Website website = find(id);
        UrlPolicyService.ValidatedUrl validatedUrl = urlPolicyService.validateAndNormalize(request.url());
        if (websiteRepository.existsByNormalizedHostAndIdNot(validatedUrl.host(), id)) {
            throw new ConflictException("Сайт с таким доменом уже добавлен.");
        }
        website.update(
                request.name(),
                validatedUrl,
                request.maxPages() == null ? website.getMaxPages() : request.maxPages(),
                request.maxDepth() == null ? website.getMaxDepth() : request.maxDepth(),
                request.active() == null ? website.isActive() : request.active());
        log.info("Website updated: id={}, host={}", website.getId(), website.getNormalizedHost());
        return WebsiteResponse.from(website);
    }

    private Website find(UUID id) {
        return websiteRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Сайт не найден."));
    }
}
