package com.novapay.payment_service.kafka.producer;

import com.novapay.payment_service.event.payment.PaymentCompletedEvent;
import com.novapay.payment_service.kafka.constants.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Slf4j
@Component
public class PaymentEventProducer {

    private final KafkaTemplate<String, PaymentCompletedEvent> kafkaTemplate;

    public void publish(PaymentCompletedEvent event) {

        log.info("Publishing payment event with reference: {}", event.paymentReference());

        kafkaTemplate.send(KafkaTopics.PAYMENT_EVENTS, event)
                .whenComplete((result, exception) -> {
                    if (exception == null) {
                        log.info("Payment event published successfully. Reference: {}, Topic: {}, Partition: {}, Offset: {}",
                                event.paymentReference(),
                                result.getRecordMetadata().topic(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    } else {log.error("Failed to publish payment event to topic {} with reference: {}",
                                KafkaTopics.PAYMENT_EVENTS, event.paymentReference(), exception);}});
    }
}
