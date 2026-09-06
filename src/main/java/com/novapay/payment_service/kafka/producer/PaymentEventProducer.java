package com.novapay.payment_service.kafka.producer;

import com.novapay.payment_service.event.payment.PaymentCompletedEvent;
import com.novapay.payment_service.kafka.constants.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor
@Slf4j
@Component
public class PaymentEventProducer {

    private final KafkaTemplate<String, PaymentCompletedEvent> kafkaTemplate;

    public CompletableFuture<SendResult<String, PaymentCompletedEvent>> publish(
            PaymentCompletedEvent event) {
        log.info(
                "Publishing payment event with reference: {}",
                event.paymentReference());

        return kafkaTemplate.send(KafkaTopics.PAYMENT_EVENTS, event);
    }
}
