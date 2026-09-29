package com.novapay.payment_service.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Razorpay payment order response")
public record RazorpayOrderResponse(
        @Schema(description = "Razorpay order ID", example = "order_RaZorPaY123") String orderId,
        @Schema(description = "Payment amount", example = "500.00") BigDecimal amount,
        @Schema(description = "Payment currency", example = "INR") String currency,
        @Schema(description = "Unique receipt/reference for the order", example = "PAY-123456")
        String receipt)
{}