package com.novapay.payment_service.service.impl;

import com.novapay.payment_service.entity.PaymentIdempotency;
import com.novapay.payment_service.entity.enums.PaymentStatus;
import com.novapay.payment_service.repository.PaymentIdempotencyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentIdempotencyService {

    private final PaymentIdempotencyRepository paymentIdempotencyRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentIdempotency reserveKey(
            String idempotencyKey,
            String requestHash) {

        log.debug("Reserving idempotency key | idempotencyKey={}",
                idempotencyKey);

        PaymentIdempotency idempotency = PaymentIdempotency.builder()
                .idempotencyKey(idempotencyKey)
                .requestHash(requestHash)
                .status(PaymentStatus.PENDING)
                .build();

        PaymentIdempotency savedIdempotency =
                paymentIdempotencyRepository.saveAndFlush(idempotency);

        log.debug("Idempotency key reserved successfully | idempotencyKey={}",
                idempotencyKey);

        return savedIdempotency;
    }
}