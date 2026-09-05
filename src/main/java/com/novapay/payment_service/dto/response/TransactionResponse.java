package com.novapay.payment_service.dto.response;

import java.math.BigDecimal;

public record TransactionResponse(
        String referenceId,
        Long senderWalletId,
        Long receiverWalletId,
        BigDecimal amount,
        String status
) { }
