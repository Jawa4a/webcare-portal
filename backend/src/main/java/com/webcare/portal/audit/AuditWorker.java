package com.webcare.portal.audit;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.net.ssl.SSLPeerUnverifiedException;

import com.webcare.portal.website.UrlNotAllowedException;
import com.webcare.portal.website.UrlPolicyService;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class AuditWorker {

    private static final Logger log = LoggerFactory.getLogger(AuditWorker.class);
    private static final Set<Integer> REDIRECT_STATUSES = Set.of(301, 302, 303, 307, 308);
    private static final String USER_AGENT = "WebCareBot/0.1 (+website-monitoring)";
    private final AuditPersistenceService persistenceService;
    private final AuditRuleEngine ruleEngine;
    private final UrlPolicyService urlPolicyService;
    private final HttpClient httpClient;
    private final Duration maxRuntime;
    private final Duration requestTimeout;
    private final long maxResponseBytes;
    private final int maxRedirects;

    public AuditWorker(
            AuditPersistenceService persistenceService,
            AuditRuleEngine ruleEngine,
            UrlPolicyService urlPolicyService,
            HttpClient httpClient,
            @Value("${webcare.audit.max-runtime}") Duration maxRuntime,
            @Value("${webcare.audit.request-timeout}") Duration requestTimeout,
            @Value("${webcare.audit.max-response-bytes}") long maxResponseBytes,
            @Value("${webcare.audit.max-redirects}") int maxRedirects) {
        this.persistenceService = persistenceService;
        this.ruleEngine = ruleEngine;
        this.urlPolicyService = urlPolicyService;
        this.httpClient = httpClient;
        this.maxRuntime = maxRuntime;
        this.requestTimeout = requestTimeout;
        this.maxResponseBytes = maxResponseBytes;
        this.maxRedirects = maxRedirects;
    }

    @Async("auditExecutor")
    public void runAsync(UUID auditId) {
        long startedNanos = System.nanoTime();
        try {
            AuditRunContext context = persistenceService.begin(auditId);
            log.info("Audit started: id={}, website={}, maxPages={}, maxDepth={}",
                    auditId, context.websiteName(), context.maxPages(), context.maxDepth());
            run(context);
        } catch (Exception exception) {
            log.error("Audit failed unexpectedly: id={}", auditId, exception);
            try {
                persistenceService.fail(auditId, "AUDIT_INTERNAL_ERROR",
                        "Внутренняя ошибка исполнителя аудита.");
            } catch (Exception persistenceException) {
                log.error("Could not persist audit failure: id={}", auditId, persistenceException);
            }
        } finally {
            long durationMs = Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
            log.info("Audit worker finished: id={}, durationMs={}", auditId, durationMs);
        }
    }

    private void run(AuditRunContext context) {
        Instant deadline = Instant.now().plus(maxRuntime);
        ArrayDeque<CrawlTarget> queue = new ArrayDeque<>();
        LinkedHashSet<String> discovered = new LinkedHashSet<>();
        Map<String, LinkOutcome> outcomes = new HashMap<>();
        Map<String, Set<UUID>> linkSources = new HashMap<>();
        queue.add(new CrawlTarget(context.startUrl(), 0));
        discovered.add(context.startUrl());

        int checked = 0;
        int findingsCount = 0;
        int penalty = 0;
        int responsesReceived = 0;

        while (!queue.isEmpty() && checked < context.maxPages() && Instant.now().isBefore(deadline)) {
            CrawlTarget target = queue.removeFirst();
            FetchedPage fetched = fetch(context, target, deadline);
            Document document = parseHtml(fetched);
            List<FindingDraft> pageFindings = ruleEngine.evaluate(new PageCheckInput(
                    fetched.finalUrl(),
                    fetched.httpStatus(),
                    fetched.responseTimeMs(),
                    fetched.redirectCount(),
                    fetched.certificateExpiresAt(),
                    fetched.errorCode(),
                    fetched.errorMessage(),
                    document));
            PageSnapshot snapshot = new PageSnapshot(
                    target.url(),
                    target.url(),
                    target.depth(),
                    fetched.httpStatus(),
                    fetched.responseTimeMs(),
                    fetched.contentType(),
                    fetched.responseBytes(),
                    fetched.errorCode(),
                    fetched.errorMessage());
            UUID pageId = persistenceService.savePage(context.auditId(), snapshot, pageFindings);
            checked++;
            findingsCount += pageFindings.size();
            penalty += penalty(pageFindings);
            if (fetched.httpStatus() != null) {
                responsesReceived++;
            }
            outcomes.put(target.url(), new LinkOutcome(
                    pageId,
                    fetched.errorCode() != null || fetched.httpStatus() == null || fetched.httpStatus() >= 400));

            if (document != null && fetched.httpStatus() != null && fetched.httpStatus() < 400) {
                discoverLinks(context, target, fetched.finalUrl(), document, pageId, queue, discovered, linkSources);
            }
            persistenceService.updateProgress(context.auditId(), discovered.size(), checked, findingsCount);
        }

        Map<UUID, List<FindingDraft>> brokenBySource = collectBrokenLinkFindings(outcomes, linkSources);
        for (Map.Entry<UUID, List<FindingDraft>> entry : brokenBySource.entrySet()) {
            int added = persistenceService.appendFindings(entry.getKey(), entry.getValue());
            findingsCount += added;
            penalty += penalty(entry.getValue());
        }

        if (responsesReceived == 0) {
            persistenceService.updateProgress(context.auditId(), discovered.size(), checked, findingsCount);
            persistenceService.fail(context.auditId(), "WEBSITE_UNAVAILABLE",
                    "Не удалось получить ни одного HTTP-ответа от сайта.");
            return;
        }
        int score = Math.max(0, 100 - Math.min(100, penalty));
        persistenceService.complete(context.auditId(), discovered.size(), checked, findingsCount, score);
        log.info("Audit completed: id={}, discovered={}, checked={}, findings={}, score={}",
                context.auditId(), discovered.size(), checked, findingsCount, score);
    }

    private FetchedPage fetch(AuditRunContext context, CrawlTarget target, Instant deadline) {
        String currentUrl = target.url();
        int redirects = 0;
        Instant certificateExpiresAt = null;
        long requestStarted = System.nanoTime();

        while (true) {
            Duration remaining = Duration.between(Instant.now(), deadline);
            if (remaining.isNegative() || remaining.isZero()) {
                return failure(target, currentUrl, elapsedMs(requestStarted), redirects,
                        "AUDIT_TIMEOUT", "Исчерпан общий лимит времени аудита.");
            }
            try {
                UrlPolicyService.ValidatedUrl validated = urlPolicyService.validateAndNormalize(currentUrl);
                if (!context.normalizedHost().equalsIgnoreCase(validated.host())) {
                    return failure(target, currentUrl, elapsedMs(requestStarted), redirects,
                            "DOMAIN_NOT_ALLOWED", "Переход на другой домен запрещён.");
                }
                currentUrl = validated.url();
                Duration timeout = remaining.compareTo(requestTimeout) < 0 ? remaining : requestTimeout;
                HttpRequest request = HttpRequest.newBuilder(URI.create(currentUrl))
                        .timeout(timeout)
                        .header("User-Agent", USER_AGENT)
                        .header("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.1")
                        .header("Accept-Encoding", "identity")
                        .GET()
                        .build();
                HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
                certificateExpiresAt = certificateExpiry(response).orElse(certificateExpiresAt);
                int status = response.statusCode();

                if (REDIRECT_STATUSES.contains(status)) {
                    try (InputStream ignored = response.body()) {
                        // Closing the stream releases the connection; redirect bodies are not needed.
                    }
                    if (redirects >= maxRedirects) {
                        return failure(target, currentUrl, elapsedMs(requestStarted), redirects,
                                "TOO_MANY_REDIRECTS", "Превышен лимит перенаправлений.");
                    }
                    String location = response.headers().firstValue("Location").orElse(null);
                    Optional<String> next = urlPolicyService.normalizeCrawlLink(
                            context.normalizedHost(), currentUrl, location);
                    if (next.isEmpty()) {
                        return failure(target, currentUrl, elapsedMs(requestStarted), redirects,
                                "REDIRECT_NOT_ALLOWED", "Перенаправление ведёт за пределы разрешённого сайта.");
                    }
                    currentUrl = next.get();
                    redirects++;
                    continue;
                }

                String contentType = response.headers().firstValue("Content-Type").orElse(null);
                byte[] body;
                try (InputStream input = response.body()) {
                    body = readBounded(input);
                } catch (ResponseTooLargeException exception) {
                    return new FetchedPage(
                            target.url(), currentUrl, status, elapsedMs(requestStarted), contentType,
                            maxResponseBytes + 1, redirects, certificateExpiresAt, null,
                            "RESPONSE_TOO_LARGE", "Ответ превышает допустимый размер.");
                }
                return new FetchedPage(
                        target.url(), currentUrl, status, elapsedMs(requestStarted), contentType,
                        (long) body.length, redirects, certificateExpiresAt, body, null, null);
            } catch (HttpTimeoutException exception) {
                return failure(target, currentUrl, elapsedMs(requestStarted), redirects,
                        "REQUEST_TIMEOUT", "Сервер не ответил за отведённое время.");
            } catch (UrlNotAllowedException exception) {
                return failure(target, currentUrl, elapsedMs(requestStarted), redirects,
                        exception.getCode(), exception.getMessage());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return failure(target, currentUrl, elapsedMs(requestStarted), redirects,
                        "REQUEST_INTERRUPTED", "Проверка страницы была прервана.");
            } catch (IOException | IllegalArgumentException exception) {
                return failure(target, currentUrl, elapsedMs(requestStarted), redirects,
                        "NETWORK_ERROR", "Сетевая ошибка при получении страницы.");
            }
        }
    }

    private Document parseHtml(FetchedPage fetched) {
        if (fetched.body() == null || fetched.contentType() == null) {
            return null;
        }
        String mediaType = fetched.contentType().toLowerCase();
        if (!mediaType.startsWith("text/html") && !mediaType.startsWith("application/xhtml+xml")) {
            return null;
        }
        try {
            return Jsoup.parse(new ByteArrayInputStream(fetched.body()), null, fetched.finalUrl());
        } catch (IOException exception) {
            log.debug("HTML parse failed for audit page", exception);
            return null;
        }
    }

    private void discoverLinks(
            AuditRunContext context,
            CrawlTarget source,
            String baseUrl,
            Document document,
            UUID sourcePageId,
            ArrayDeque<CrawlTarget> queue,
            LinkedHashSet<String> discovered,
            Map<String, Set<UUID>> linkSources) {
        for (Element link : document.select("a[href]")) {
            Optional<String> normalized = urlPolicyService.normalizeCrawlLink(
                    context.normalizedHost(), baseUrl, link.attr("href"));
            if (normalized.isEmpty()) {
                continue;
            }
            String targetUrl = normalized.get();
            linkSources.computeIfAbsent(targetUrl, ignored -> new LinkedHashSet<>()).add(sourcePageId);
            if (source.depth() < context.maxDepth()
                    && discovered.size() < context.maxPages()
                    && discovered.add(targetUrl)) {
                queue.addLast(new CrawlTarget(targetUrl, source.depth() + 1));
            }
        }
    }

    private Map<UUID, List<FindingDraft>> collectBrokenLinkFindings(
            Map<String, LinkOutcome> outcomes,
            Map<String, Set<UUID>> linkSources) {
        Map<UUID, List<FindingDraft>> result = new HashMap<>();
        for (Map.Entry<String, Set<UUID>> entry : linkSources.entrySet()) {
            LinkOutcome outcome = outcomes.get(entry.getKey());
            if (outcome == null || !outcome.broken()) {
                continue;
            }
            for (UUID sourcePageId : entry.getValue()) {
                result.computeIfAbsent(sourcePageId, ignored -> new ArrayList<>()).add(new FindingDraft(
                        "BROKEN_LINK",
                        FindingSeverity.HIGH,
                        "Неработающая внутренняя ссылка",
                        "Ссылка ведёт на недоступную страницу: " + entry.getKey(),
                        "Исправьте или удалите ссылку, либо восстановите целевую страницу.",
                        entry.getKey()));
            }
        }
        return result;
    }

    private Optional<Instant> certificateExpiry(HttpResponse<?> response) {
        if (response.sslSession().isEmpty()) {
            return Optional.empty();
        }
        try {
            for (Certificate certificate : response.sslSession().get().getPeerCertificates()) {
                if (certificate instanceof X509Certificate x509Certificate) {
                    return Optional.of(x509Certificate.getNotAfter().toInstant());
                }
            }
        } catch (SSLPeerUnverifiedException exception) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    private byte[] readBounded(InputStream input) throws IOException, ResponseTooLargeException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[16_384];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maxResponseBytes) {
                throw new ResponseTooLargeException();
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private FetchedPage failure(
            CrawlTarget target,
            String finalUrl,
            int elapsedMs,
            int redirects,
            String code,
            String message) {
        return new FetchedPage(
                target.url(), finalUrl, null, elapsedMs, null, null, redirects,
                null, null, code, message);
    }

    private int elapsedMs(long startedNanos) {
        return (int) Math.min(Integer.MAX_VALUE,
                Duration.ofNanos(System.nanoTime() - startedNanos).toMillis());
    }

    private int penalty(List<FindingDraft> drafts) {
        return drafts.stream().mapToInt(draft -> draft.severity().penalty()).sum();
    }

    private record CrawlTarget(String url, int depth) {
    }

    private record LinkOutcome(UUID pageId, boolean broken) {
    }

    private record FetchedPage(
            String requestedUrl,
            String finalUrl,
            Integer httpStatus,
            Integer responseTimeMs,
            String contentType,
            Long responseBytes,
            int redirectCount,
            Instant certificateExpiresAt,
            byte[] body,
            String errorCode,
            String errorMessage) {
    }

    private static final class ResponseTooLargeException extends Exception {
    }
}
