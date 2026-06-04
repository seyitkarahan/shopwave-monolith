package com.shopwave.inventory.service;

import com.shopwave.inventory.domain.AuditLog;
import com.shopwave.inventory.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @Transactional
    public void log(String eventType, String aggregate, Long aggregateId, String payload) {
        auditLogRepository.save(AuditLog.builder()
                .eventType(eventType)
                .aggregate(aggregate)
                .aggregateId(aggregateId)
                .payload(payload)
                .build());
    }
}
