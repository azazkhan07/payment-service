package com.novapay.payment_service.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request to verify a Razorpay payment")
public record RazorpayPaymentVerifyRequest(

        @NotBlank(message = "Razorpay order ID is required")
        @Schema(example = "order_XXXXXXXXXXXX")
        String orderId,
        @NotBlank(message = "Razorpay payment ID is required")
        @Schema(example = "pay_XXXXXXXXXXXX")
        String paymentId,
        @NotBlank(message = "Razorpay signature is required")
        @Schema(example = "generated-by-razorpay")
        String signature
) { }