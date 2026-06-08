package com.novapay.payment_service.dto.response;

import com.novapay.payment_service.entity.enums.PaymentMethod;
import com.novapay.payment_service.entity.enums.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "payment response")
public record PaymentResponse(
        @Schema(example = "PAY-8f3c4d2a")
        String paymentReference,
        @Schema(example = "1001")
        Long payerWalletId,
        @Schema(example = "1002")
        Long payeeWalletId,
        @Schema(example = "500.00")
        BigDecimal amount,
        @Schema(example = "UPI")
        PaymentMethod paymentMethod,
        @Schema(example = "SUCCESS")
        PaymentStatus status,
        @Schema(example = "Payment completed successfully")
        String message,
        @Schema(example = "2026-06-05T10:30:45")
        LocalDateTime createdAt) {
}
