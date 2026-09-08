package com.novapay.payment_service.repository;

import com.novapay.payment_service.entity.PaymentIdempotency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface PaymentIdempotencyRepository extends JpaRepository<PaymentIdempotency, Long> {

    Optional<PaymentIdempotency> findByIdempotencyKey(String idempotencyKey);

    Optional<PaymentIdempotency> findByPaymentReference(String paymentReference);

}
