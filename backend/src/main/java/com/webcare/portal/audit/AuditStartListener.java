package com.webcare.portal.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class AuditStartListener {

    private static final Logger log = LoggerFactory.getLogger(AuditStartListener.class);
    private final AuditWorker auditWorker;
    private final AuditPersistenceService persistenceService;

    public AuditStartListener(AuditWorker auditWorker, AuditPersistenceService persistenceService) {
        this.auditWorker = auditWorker;
        this.persistenceService = persistenceService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void start(AuditStartedEvent event) {
        try {
            auditWorker.runAsync(event.auditId());
        } catch (TaskRejectedException exception) {
            log.error("Audit queue rejected id={}", event.auditId(), exception);
            persistenceService.fail(event.auditId(), "AUDIT_QUEUE_FULL",
                    "Очередь аудитов временно переполнена. Повторите запуск позже.");
        }
    }
}
