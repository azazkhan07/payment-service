package com.novapay.payment_service.saga.event;

import java.math.BigDecimal;

public record WalletRefundedEvent(
        String paymentReference,
        Long payerWalletId,
        BigDecimal amount
) { }
