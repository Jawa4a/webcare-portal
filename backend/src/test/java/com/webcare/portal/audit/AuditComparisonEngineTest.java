package com.webcare.portal.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class AuditComparisonEngineTest {

    private final AuditComparisonEngine engine = new AuditComparisonEngine();

    @Test
    void separatesNewOngoingAndFixedItems() {
        Map<String, String> previous = new LinkedHashMap<>();
        previous.put("/|TITLE", "old-title");
        previous.put("/about|H1", "fixed-h1");
        Map<String, String> current = new LinkedHashMap<>();
        current.put("/|TITLE", "current-title");
        current.put("/contacts|ALT", "new-alt");

        var result = engine.compare(current, previous);

        assertThat(result.newItems()).containsExactly("new-alt");
        assertThat(result.ongoingItems()).containsExactly("current-title");
        assertThat(result.fixedItems()).containsExactly("fixed-h1");
    }

    @Test
    void treatsFirstAuditFindingsAsNew() {
        var result = engine.compare(Map.of("/|TITLE", "title"), Map.of());

        assertThat(result.newItems()).containsExactly("title");
        assertThat(result.ongoingItems()).isEmpty();
        assertThat(result.fixedItems()).isEmpty();
    }
}
