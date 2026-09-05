package com.novapay.payment_service.event.payment;

import com.novapay.payment_service.entity.enums.PaymentMethod;
import com.novapay.payment_service.entity.enums.PaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentCompletedEvent(
        String paymentReference,

        String transactionReference,

        Long payerWalletId,

        Long payeeWalletId,

        Long payeeUserId,

        BigDecimal amount,

        PaymentMethod paymentMethod,

        PaymentStatus paymentStatus,

        String message,

        LocalDateTime paymentTime
) {}
