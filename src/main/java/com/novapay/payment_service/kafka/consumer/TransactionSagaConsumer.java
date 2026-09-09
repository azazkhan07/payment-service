package com.novapay.payment_service.kafka.consumer;

import com.novapay.payment_service.kafka.constants.KafkaTopics;
import com.novapay.payment_service.saga.event.TransactionCreatedEvent;
import com.novapay.payment_service.saga.event.TransactionFailedEvent;
import com.novapay.payment_service.saga.service.PaymentSagaOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@Slf4j
@RetryableTopic(
        attempts = "4",
        backoff = @Backoff(
                delay = 2000,
                multiplier = 2.0))
@KafkaListener(
        topics = KafkaTopics.TRANSACTION_EVENTS,
        groupId = "payment-saga-group")
public class TransactionSagaConsumer {

    private final PaymentSagaOrchestrator paymentSagaOrchestrator;

    @KafkaHandler
    public void handleTransactionCreated(TransactionCreatedEvent event) {
        log.info("Received TRANSACTION_CREATED event. Payment Reference: {}", event.paymentReference());
        paymentSagaOrchestrator.handleTransactionCreated(event);
    }

    @KafkaHandler
    public void handleTransactionFailed(TransactionFailedEvent event) {
        log.info("Received TRANSACTION_FAILED event. Payment Reference: {}", event.paymentReference());
        paymentSagaOrchestrator.handleTransactionFailed(event);
    }

    @DltHandler
    public void handleDlt(Object event) {
        log.error("Message moved to DLT after retries exhausted. Event: {}", event);
    }
}
