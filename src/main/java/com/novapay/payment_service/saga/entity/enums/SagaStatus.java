package com.novapay.payment_service.saga.entity.enums;

public enum SagaStatus {

    STARTED,
    WALLET_DEBIT_PENDING,
    WALLET_DEBITED,
    TRANSACTION_PENDING,
    TRANSACTION_CREATED,
    REFUND_PENDING,
    WALLET_REFUNDED,
    COMPLETED,
    FAILED
}
