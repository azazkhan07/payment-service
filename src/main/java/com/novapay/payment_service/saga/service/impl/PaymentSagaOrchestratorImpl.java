package com.novapay.payment_service.saga.service.impl;

import com.novapay.payment_service.client.WalletClient;
import com.novapay.payment_service.entity.Payment;
import com.novapay.payment_service.entity.PaymentIdempotency;
import com.novapay.payment_service.entity.enums.PaymentStatus;
import com.novapay.payment_service.event.payment.PaymentCompletedEvent;
import com.novapay.payment_service.kafka.producer.SagaCommandProducer;
import com.novapay.payment_service.outbox.service.OutboxEventService;
import com.novapay.payment_service.repository.PaymentIdempotencyRepository;
import com.novapay.payment_service.repository.PaymentRepository;
import com.novapay.payment_service.saga.entity.PaymentSaga;
import com.novapay.payment_service.saga.entity.enums.SagaStatus;
import com.novapay.payment_service.saga.event.*;
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
    private final PaymentRepository paymentRepository;
    private final PaymentIdempotencyRepository paymentIdempotencyRepository;
    private final WalletClient walletClient;
    private final OutboxEventService outboxEventService;

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

        Payment payment = paymentRepository
                .findByPaymentReference(event.paymentReference())
                .orElseThrow(() -> new IllegalStateException(
                        "Payment not found for payment reference: "
                                + event.paymentReference()));

        // Update Payment
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setTransactionReference(event.transactionReference());
        payment.setMessage("Payment processed successfully");

        Payment savedPayment = paymentRepository.save(payment);

        // Update Idempotency
        PaymentIdempotency idempotency =
                paymentIdempotencyRepository
                        .findByPaymentReference(
                                event.paymentReference()
                        )
                        .orElseThrow(() -> new IllegalStateException(
                                "Idempotency record not found for payment reference: "
                                        + event.paymentReference()
                        ));

        idempotency.setStatus(PaymentStatus.SUCCESS);

        paymentIdempotencyRepository.save(idempotency);

        // Complete Saga
        saga.setStatus(SagaStatus.TRANSACTION_CREATED.name());
        saga.setStatus(SagaStatus.COMPLETED.name());

        paymentSagaRepository.save(saga);

        // Get payee user id for notification event
        Long payeeUserId =
                walletClient.getWalletById(
                        savedPayment.getPayeeWalletId()
                ).userId();

        // Create payment completed event
        PaymentCompletedEvent paymentCompletedEvent =
                new PaymentCompletedEvent(
                        savedPayment.getPaymentReference(),
                        savedPayment.getTransactionReference(),
                        savedPayment.getPayerWalletId(),
                        savedPayment.getPayeeWalletId(),
                        payeeUserId,
                        savedPayment.getAmount(),
                        savedPayment.getPaymentMethod(),
                        savedPayment.getStatus(),
                        savedPayment.getMessage(),
                        savedPayment.getCreatedAt()
                );

        // Save event to Outbox
        outboxEventService.saveEvent(
                "PAYMENT",
                savedPayment.getPaymentReference(),
                "PAYMENT_COMPLETED",
                paymentCompletedEvent);
    }

    @Override
    @Transactional
    public void handleTransactionFailed(TransactionFailedEvent event) {

        PaymentSaga saga = paymentSagaRepository
                .findByPaymentReference(event.paymentReference())
                .orElseThrow(() -> new IllegalStateException(
                        "Saga not found for payment reference: "
                                + event.paymentReference()));

        saga.setStatus(SagaStatus.REFUND_PENDING.name());

        paymentSagaRepository.save(saga);

        sagaCommandProducer.refundWallet(
                new WalletRefundCommand(
                        saga.getPaymentReference(),
                        saga.getPayerWalletId(),
                        saga.getPayeeWalletId(),
                        saga.getAmount()));
    }

    @Override
    @Transactional
    public void handleWalletRefunded(WalletRefundedEvent event) {

        PaymentSaga saga = paymentSagaRepository
                .findByPaymentReference(event.paymentReference())
                .orElseThrow(() -> new IllegalStateException(
                        "Saga not found for payment reference: " + event.paymentReference()));

        Payment payment = paymentRepository
                .findByPaymentReference(event.paymentReference())
                .orElseThrow(() -> new IllegalStateException(
                        "Payment not found for payment reference: " + event.paymentReference()));

        payment.setStatus(PaymentStatus.FAILED);
        payment.setMessage("Payment failed and wallet was refunded");

        paymentRepository.save(payment);

        PaymentIdempotency idempotency = paymentIdempotencyRepository
                        .findByPaymentReference(event.paymentReference())
                        .orElseThrow(() -> new IllegalStateException(
                                "Idempotency record not found for payment reference: "
                                        + event.paymentReference()));

        idempotency.setStatus(PaymentStatus.FAILED);

        paymentIdempotencyRepository.save(idempotency);

        saga.setStatus(SagaStatus.FAILED.name());

        paymentSagaRepository.save(saga);
    }
}
