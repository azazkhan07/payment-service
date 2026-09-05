package com.novapay.payment_service.outbox.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novapay.payment_service.outbox.entity.OutboxEvent;
import com.novapay.payment_service.outbox.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class OutboxEventService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public void saveEvent(
            String aggregateType,
            String aggregateId,
            String eventType,
            Object event
    ) {
        try {
            String payload = objectMapper.writeValueAsString(event);

            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .aggregateType(aggregateType)
                    .aggregateId(aggregateId)
                    .eventType(eventType)
                    .payload(payload)
                    .status("NEW")
                    .build();

            outboxEventRepository.save(outboxEvent);

        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Failed to serialize outbox event", e
            );
        }
    }
}
