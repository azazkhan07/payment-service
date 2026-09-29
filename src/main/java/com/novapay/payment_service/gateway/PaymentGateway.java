package com.novapay.payment_service.gateway;

import com.novapay.payment_service.dto.response.RazorpayOrderResponse;
import com.novapay.payment_service.dto.response.RazorpayRefundResponse;

import java.math.BigDecimal;

public interface PaymentGateway {

    RazorpayOrderResponse createOrder(BigDecimal amount, String currency, String receipt);

    boolean verifyPayment(String orderId, String paymentId, String signature);

    RazorpayRefundResponse refundPayment(String paymentId, BigDecimal amount);

}
