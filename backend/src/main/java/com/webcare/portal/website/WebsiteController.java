package com.webcare.portal.website;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/websites")
public class WebsiteController {

    private final WebsiteService websiteService;

    public WebsiteController(WebsiteService websiteService) {
        this.websiteService = websiteService;
    }

    @GetMapping
    WebsitePageResponse list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "name") String sort) {
        var result = websiteService.list(page, size, sort);
        return new WebsitePageResponse(
                result.getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @GetMapping("/{id}")
    WebsiteResponse get(@PathVariable UUID id) {
        return websiteService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    WebsiteResponse create(@Valid @RequestBody WebsiteRequest request, Authentication authentication) {
        return websiteService.create(request, authentication);
    }

    @PutMapping("/{id}")
    WebsiteResponse update(@PathVariable UUID id, @Valid @RequestBody WebsiteRequest request) {
        return websiteService.update(id, request);
    }

    public record WebsitePageResponse(
            List<WebsiteResponse> items,
            int page,
            int size,
            long totalItems,
            int totalPages) {
    }
}
