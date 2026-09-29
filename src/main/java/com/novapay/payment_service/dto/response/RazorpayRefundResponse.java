package com.novapay.payment_service.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "Razorpay refund response")
public record RazorpayRefundResponse(
        @Schema(description = "Razorpay refund ID")
        String refundId,
        @Schema(description = "Refund amount")
        BigDecimal amount,
        @Schema(description = "Refund status")
        String status) { }