package com.webcare.portal.website;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WebsiteRepository extends JpaRepository<Website, UUID> {
    boolean existsByNormalizedHost(String normalizedHost);
    boolean existsByNormalizedHostAndIdNot(String normalizedHost, UUID id);
    long countByActiveTrue();
}
