package com.novapay.payment_service.dto.request;

import java.math.BigDecimal;

public record TransactionRequest(
        Long senderWalletId,
        Long receiverWalletId,
        BigDecimal amount,
        String remarks
) {}
