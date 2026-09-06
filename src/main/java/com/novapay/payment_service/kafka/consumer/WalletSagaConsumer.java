package com.novapay.payment_service.kafka.consumer;

import com.novapay.payment_service.kafka.constants.KafkaTopics;
import com.novapay.payment_service.saga.event.WalletDebitedEvent;
import com.novapay.payment_service.saga.service.PaymentSagaOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WalletSagaConsumer {

    private final PaymentSagaOrchestrator paymentSagaOrchestrator;

    @KafkaListener(
            topics = KafkaTopics.WALLET_EVENTS,
            groupId = "payment-saga-group")

    public void handleWalletDebited(WalletDebitedEvent event) {

        log.info("Received WALLET_DEBITED event. Payment Reference: {}",
                event.paymentReference());

        paymentSagaOrchestrator.handleWalletDebited(event);
    }
}
