package com.novapay.payment_service.outbox.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novapay.payment_service.event.payment.PaymentCompletedEvent;
import com.novapay.payment_service.kafka.producer.PaymentEventProducer;
import com.novapay.payment_service.outbox.entity.OutboxEvent;
import com.novapay.payment_service.outbox.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxEventPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final PaymentEventProducer paymentEventProducer;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 5000)
    public void publishEvents() {

        List<OutboxEvent> events =
                outboxEventRepository.findTop100ByStatusOrderByCreatedAtAsc("NEW");

        for (OutboxEvent event : events) {
            try {
                PaymentCompletedEvent paymentEvent =
                        objectMapper.readValue(
                                event.getPayload(),
                                PaymentCompletedEvent.class);

                paymentEventProducer.publish(paymentEvent);

                event.setStatus("PROCESSED");
                event.setProcessedAt(LocalDateTime.now());

                outboxEventRepository.save(event);

                log.info(
                        "Outbox event processed successfully. Event ID: {}, Payment Reference: {}",
                        event.getId(),
                        event.getAggregateId());

            } catch (Exception e) {
                log.error(
                        "Failed to publish outbox event. Event ID: {}",
                        event.getId(),
                        e);
            }
        }
    }}
