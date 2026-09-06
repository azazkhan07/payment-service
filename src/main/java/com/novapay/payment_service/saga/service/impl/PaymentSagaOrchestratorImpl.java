package com.novapay.payment_service.saga.service.impl;

import com.novapay.payment_service.kafka.producer.SagaCommandProducer;
import com.novapay.payment_service.saga.entity.PaymentSaga;
import com.novapay.payment_service.saga.entity.enums.SagaStatus;
import com.novapay.payment_service.saga.event.CreateTransactionCommand;
import com.novapay.payment_service.saga.event.TransactionCreatedEvent;
import com.novapay.payment_service.saga.event.WalletDebitedEvent;
import com.novapay.payment_service.saga.repository.PaymentSagaRepository;
import com.novapay.payment_service.saga.service.PaymentSagaOrchestrator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentSagaOrchestratorImpl implements PaymentSagaOrchestrator {

    private final PaymentSagaRepository paymentSagaRepository;
    private final SagaCommandProducer sagaCommandProducer;

    @Override
    @Transactional
    public void startSaga(PaymentSaga saga) {

        saga.setStatus(SagaStatus.WALLET_DEBIT_PENDING.name());

        paymentSagaRepository.save(saga);

        sagaCommandProducer.debitWallet(
                new com.novapay.payment_service.saga.event.DebitWalletCommand(
                        saga.getPaymentReference(),
                        saga.getPayerWalletId(),
                        saga.getPayeeWalletId(),
                        saga.getAmount()));
    }

    @Override
    @Transactional
    public void handleWalletDebited(WalletDebitedEvent event) {

        PaymentSaga saga = paymentSagaRepository
                .findByPaymentReference(event.paymentReference())
                .orElseThrow(() -> new IllegalStateException(
                        "Saga not found for payment reference: "
                                + event.paymentReference()));

        saga.setStatus(SagaStatus.WALLET_DEBITED.name());

        saga.setStatus(SagaStatus.TRANSACTION_PENDING.name());

        paymentSagaRepository.save(saga);

        sagaCommandProducer.createTransaction(
                new CreateTransactionCommand(
                        saga.getPaymentReference(),
                        saga.getPayerWalletId(),
                        saga.getPayeeWalletId(),
                        saga.getAmount()));
    }

    @Override
    @Transactional
    public void handleTransactionCreated(TransactionCreatedEvent event) {

        PaymentSaga saga = paymentSagaRepository
                .findByPaymentReference(event.paymentReference())
                .orElseThrow(() -> new IllegalStateException(
                        "Saga not found for payment reference: "
                                + event.paymentReference()));

        saga.setStatus(SagaStatus.TRANSACTION_CREATED.name());

        saga.setStatus(SagaStatus.COMPLETED.name());

        paymentSagaRepository.save(saga);
    }
}
