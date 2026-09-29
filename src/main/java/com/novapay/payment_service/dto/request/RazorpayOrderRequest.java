package com.novapay.payment_service.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

@Schema(description = "Request to create a Razorpay payment order")
public record RazorpayOrderRequest(

        @NotBlank(message = "Payment reference is required")
        @Schema(description = "PayFlow payment reference", example = "8c3d7c7e-1234-4567-8901-123456789abc")
        String paymentReference,
        @NotNull(message = "Amount is required")
        @DecimalMin(value = "1.00", message = "Amount must be greater than zero")
        @Schema(description = "Payment amount in INR", example = "500.00")
        BigDecimal amount,
        @NotBlank(message = "Currency is required")
        @Schema(description = "Payment currency", example = "INR")
        String currency
) { }