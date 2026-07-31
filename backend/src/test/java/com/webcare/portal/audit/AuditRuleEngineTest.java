package com.webcare.portal.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

class AuditRuleEngineTest {

    private final AuditRuleEngine engine = new AuditRuleEngine();

    @Test
    void detectsTechnicalSeoAndAccessibilityProblems() {
        var document = Jsoup.parse("""
                <html><head>
                  <meta name="robots" content="noindex,follow">
                </head><body><img src="hero.png"></body></html>
                """, "https://example.com/");

        var findings = engine.evaluate(new PageCheckInput(
                "https://example.com/",
                404,
                2_500,
                3,
                Instant.now().plusSeconds(10 * 24 * 60 * 60),
                null,
                null,
                document));

        assertThat(findings).extracting(FindingDraft::ruleCode).contains(
                "HTTP_STATUS_ERROR",
                "SLOW_RESPONSE",
                "REDIRECT_CHAIN",
                "TLS_EXPIRES_SOON",
                "TITLE_MISSING",
                "META_DESCRIPTION_MISSING",
                "H1_MISSING",
                "CANONICAL_MISSING",
                "NOINDEX",
                "IMAGE_ALT_MISSING");
    }

    @Test
    void acceptsHealthyHtmlPageWithoutFindings() {
        var document = Jsoup.parse("""
                <html><head>
                  <title>WebCare</title>
                  <meta name="description" content="Portal monitoring">
                  <link rel="canonical" href="https://example.com/">
                </head><body><h1>WebCare</h1><img src="logo.png" alt="WebCare"></body></html>
                """, "https://example.com/");

        var findings = engine.evaluate(new PageCheckInput(
                "https://example.com/", 200, 120, 0,
                Instant.now().plusSeconds(90L * 24 * 60 * 60),
                null, null, document));

        assertThat(findings).isEmpty();
    }

    @Test
    void recordsFetchFailureAsFinding() {
        var findings = engine.evaluate(new PageCheckInput(
                "https://example.com/unavailable", null, 10_000, 0,
                null, "REQUEST_TIMEOUT", "Сервер не ответил.", null));

        assertThat(findings).extracting(FindingDraft::ruleCode)
                .containsExactly("FETCH_FAILED", "SLOW_RESPONSE");
    }
}
