package com.novapay.payment_service.service;

import com.novapay.payment_service.dto.request.PaymentRequest;
import com.novapay.payment_service.dto.response.PaymentResponse;
import com.novapay.payment_service.entity.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PaymentService {

    PaymentResponse createPayment(PaymentRequest request);

    PaymentResponse getPaymentByReference(String paymentReference);

    Page<PaymentResponse> getAllPayments(Pageable pageable);

    Page<PaymentResponse> getPaymentsByStatus(PaymentStatus status, Pageable pageable);

    PaymentResponse refundPayment(String paymentReference);
}
