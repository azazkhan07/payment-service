package com.novapay.payment_service.repository;

import com.novapay.payment_service.entity.Payment;
import com.novapay.payment_service.entity.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByPaymentReference(String paymentReference);

    Page<Payment> findByStatus(PaymentStatus status, Pageable pageable);
}
