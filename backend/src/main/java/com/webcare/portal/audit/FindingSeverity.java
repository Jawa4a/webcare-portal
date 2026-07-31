package com.webcare.portal.audit;

public enum FindingSeverity {
    CRITICAL(25),
    HIGH(12),
    MEDIUM(6),
    LOW(2),
    INFO(0);

    private final int penalty;

    FindingSeverity(int penalty) {
        this.penalty = penalty;
    }

    public int penalty() {
        return penalty;
    }
}
