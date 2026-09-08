package com.novapay.payment_service.saga.event;

public record TransactionFailedEvent(
        String paymentReference,
        String reason
) { }
