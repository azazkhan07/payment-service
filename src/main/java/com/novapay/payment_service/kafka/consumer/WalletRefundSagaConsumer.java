package com.novapay.payment_service.kafka.consumer;

import com.novapay.payment_service.kafka.constants.KafkaTopics;
import com.novapay.payment_service.saga.event.WalletRefundedEvent;
import com.novapay.payment_service.saga.service.PaymentSagaOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class WalletRefundSagaConsumer {

    private final PaymentSagaOrchestrator paymentSagaOrchestrator;

    @KafkaListener(
            topics = KafkaTopics.WALLET_EVENTS,
            groupId = "payment-saga-group")

    public void handleWalletRefunded(WalletRefundedEvent event) {

        log.info("Received WALLET_REFUNDED event. Payment Reference: {}",
                event.paymentReference());

        paymentSagaOrchestrator.handleWalletRefunded(event);
    }
}
