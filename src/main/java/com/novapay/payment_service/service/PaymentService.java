package com.novapay.payment_service.service;

import com.novapay.payment_service.entity.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PaymentService {

    PaymentResponse processPayment(PaymentRequest request);

    PaymentResponse getPaymentByReference(String reference);

    Page<PaymentResponse> getAllPayments(Pageable pageable);

    Page<PaymentResponse> getPaymentsByStatus(PaymentStatus status, Pageable pageable);
}
