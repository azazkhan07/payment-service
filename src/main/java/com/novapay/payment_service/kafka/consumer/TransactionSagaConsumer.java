package com.novapay.payment_service.kafka.consumer;

import com.novapay.payment_service.kafka.constants.KafkaTopics;
import com.novapay.payment_service.saga.event.TransactionCreatedEvent;
import com.novapay.payment_service.saga.service.PaymentSagaOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@Slf4j
public class TransactionSagaConsumer {

    private final PaymentSagaOrchestrator paymentSagaOrchestrator;

    @KafkaListener(
            topics = KafkaTopics.TRANSACTION_EVENTS,
            groupId = "payment-saga-group")
    public void handleTransactionCreated(TransactionCreatedEvent event) {

        log.info(
                "Received TRANSACTION_CREATED event. Payment Reference: {}",
                event.paymentReference()
        );

        paymentSagaOrchestrator.handleTransactionCreated(event);
    }
}
