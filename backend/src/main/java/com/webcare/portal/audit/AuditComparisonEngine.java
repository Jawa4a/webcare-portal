package com.webcare.portal.audit;

import java.util.List;
import java.util.Map;

public class AuditComparisonEngine {

    public <T> Difference<T> compare(Map<String, T> current, Map<String, T> previous) {
        List<T> newItems = current.entrySet().stream()
                .filter(entry -> !previous.containsKey(entry.getKey()))
                .map(Map.Entry::getValue)
                .toList();
        List<T> ongoingItems = current.entrySet().stream()
                .filter(entry -> previous.containsKey(entry.getKey()))
                .map(Map.Entry::getValue)
                .toList();
        List<T> fixedItems = previous.entrySet().stream()
                .filter(entry -> !current.containsKey(entry.getKey()))
                .map(Map.Entry::getValue)
                .toList();
        return new Difference<>(newItems, ongoingItems, fixedItems);
    }

    public record Difference<T>(List<T> newItems, List<T> ongoingItems, List<T> fixedItems) {
    }
}
