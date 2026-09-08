package com.novapay.payment_service.saga.service;

import com.novapay.payment_service.saga.entity.PaymentSaga;
import com.novapay.payment_service.saga.event.TransactionCreatedEvent;
import com.novapay.payment_service.saga.event.TransactionFailedEvent;
import com.novapay.payment_service.saga.event.WalletDebitedEvent;
import com.novapay.payment_service.saga.event.WalletRefundedEvent;

public interface PaymentSagaOrchestrator {

    void startSaga(PaymentSaga saga);

    void handleWalletDebited(WalletDebitedEvent event);

    void handleTransactionCreated(TransactionCreatedEvent event);

    void handleTransactionFailed(TransactionFailedEvent event);

    void handleWalletRefunded(WalletRefundedEvent event);
}
