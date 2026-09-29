package com.novapay.payment_service.service;

import com.novapay.payment_service.dto.request.PaymentRequest;
import com.novapay.payment_service.dto.request.RazorpayOrderRequest;
import com.novapay.payment_service.dto.response.PaymentResponse;
import com.novapay.payment_service.dto.response.RazorpayOrderResponse;
import com.novapay.payment_service.entity.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PaymentService {

    PaymentResponse createPayment(PaymentRequest request, String idempotencyKey);

    RazorpayOrderResponse createRazorpayOrder(RazorpayOrderRequest request);

    boolean verifyRazorpayPayment(String orderId, String paymentId, String signature);

    PaymentResponse getPaymentByReference(String paymentReference);

    Page<PaymentResponse> getAllPayments(Pageable pageable);

    Page<PaymentResponse> getPaymentsByStatus(PaymentStatus status, Pageable pageable);

    PaymentResponse refundPayment(String paymentReference);

    void handleRazorpayWebhook(String payload, String signature);
}
