package com.novapay.payment_service.saga.event;

import java.math.BigDecimal;

public record WalletRefundCommand(
        String paymentReference,
        Long payerWalletId,
        Long payeeWalletId,
        BigDecimal amount
) { }
