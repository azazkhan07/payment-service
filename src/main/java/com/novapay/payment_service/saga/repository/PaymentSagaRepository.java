package com.novapay.payment_service.saga.repository;

import com.novapay.payment_service.saga.entity.PaymentSaga;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentSagaRepository extends JpaRepository<PaymentSaga, UUID> {

    Optional<PaymentSaga> findByPaymentReference(String paymentReference);
}
