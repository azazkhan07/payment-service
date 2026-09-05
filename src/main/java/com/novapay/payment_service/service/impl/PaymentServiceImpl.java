package com.novapay.payment_service.service.impl;

import com.novapay.payment_service.client.TransactionClient;
import com.novapay.payment_service.client.WalletClient;
import com.novapay.payment_service.dto.request.PaymentRequest;
import com.novapay.payment_service.dto.request.TransactionRequest;
import com.novapay.payment_service.dto.response.PaymentResponse;
import com.novapay.payment_service.dto.response.TransactionResponse;
import com.novapay.payment_service.dto.response.WalletResponse;
import com.novapay.payment_service.entity.Payment;
import com.novapay.payment_service.entity.PaymentIdempotency;
import com.novapay.payment_service.entity.enums.PaymentStatus;
import com.novapay.payment_service.event.payment.PaymentCompletedEvent;
import com.novapay.payment_service.exception.IdempotencyKeyReuseException;
import com.novapay.payment_service.exception.PaymentAlreadyProcessingException;
import com.novapay.payment_service.exception.ResourceNotFoundException;
import com.novapay.payment_service.kafka.producer.PaymentEventProducer;
import com.novapay.payment_service.mapper.PaymentMapper;
import com.novapay.payment_service.outbox.service.OutboxEventService;
import com.novapay.payment_service.repository.PaymentIdempotencyRepository;
import com.novapay.payment_service.repository.PaymentRepository;
import com.novapay.payment_service.service.PaymentService;
import com.novapay.payment_service.util.PaymentRequestHashUtil;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentServiceImpl.class);

    private final PaymentRepository paymentRepository;
    private final PaymentIdempotencyRepository paymentIdempotencyRepository;
    private final PaymentIdempotencyService paymentIdempotencyService;
    private final PaymentMapper paymentMapper;
    private final TransactionClient transactionClient;
    private final WalletClient walletClient;
    private final OutboxEventService outboxEventService;

    @Override
    @Transactional
    public PaymentResponse createPayment(PaymentRequest request, String idempotencyKey) {
        LOGGER.info("Payment request received | payerWalletId={} payeeWalletId={} amount={}",
                request.getPayerWalletId(),
                request.getPayeeWalletId(),
                request.getAmount());

        // Generate hash of the payment request
        String requestHash = PaymentRequestHashUtil.generateHash(request);

        // Check whether this idempotency key was already used
        var existingIdempotency =
                paymentIdempotencyRepository.findByIdempotencyKey(idempotencyKey);

        if (existingIdempotency.isPresent()) {
            PaymentIdempotency idempotency = existingIdempotency.get();

            // Same key but different request
            if (!idempotency.getRequestHash().equals(requestHash)) {
                throw new IdempotencyKeyReuseException(
                        "The Idempotency-Key has already been used with a different request");
            }
            // Same key + same request
            if (idempotency.getPaymentReference() != null) {

                LOGGER.info("Duplicate payment request detected | idempotencyKey={}",
                        idempotencyKey);

                return getPaymentByReference(idempotency.getPaymentReference());
            }
            // Payment is still being processed
            throw new PaymentAlreadyProcessingException(
                    "A payment with this Idempotency-Key is currently being processed");
        }
        // Validate payer and payee
        if (request.getPayerWalletId().equals(request.getPayeeWalletId())) {
            throw new IllegalArgumentException(
                    "Payer and Payee wallet cannot be same");
        }

        // Create idempotency record before processing payment
        PaymentIdempotency idempotency;

        try {

            idempotency = paymentIdempotencyService
                    .reserveKey(idempotencyKey, requestHash);

        } catch (DataIntegrityViolationException ex) {

            LOGGER.info("Concurrent duplicate payment request detected | idempotencyKey={}",
                    idempotencyKey);

            idempotency = paymentIdempotencyRepository
                    .findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> ex);

            if (!idempotency.getRequestHash().equals(requestHash)) {
                throw new IdempotencyKeyReuseException(
                        "The Idempotency-Key has already been used with a different request");
            }

            if (idempotency.getPaymentReference() != null) {
                return getPaymentByReference(idempotency.getPaymentReference());
            }

            throw new PaymentAlreadyProcessingException(
                    "A payment with this Idempotency-Key is currently being processed");
        }
        // Get payee wallet
        WalletResponse payeeWallet =
                walletClient.getWalletById(request.getPayeeWalletId());

        Long payeeUserId = payeeWallet.userId();

        // Process transaction
        TransactionResponse transactionResponse =
                transactionClient.transferMoney(
                        new TransactionRequest(
                                request.getPayerWalletId(),
                                request.getPayeeWalletId(),
                                request.getAmount(),
                                request.getRemarks()));

        LOGGER.info("Transaction created successfully | transactionReference={}", transactionResponse.referenceId());

        // Create payment
        Payment payments = Payment.builder()
                .payerWalletId(request.getPayerWalletId())
                .payeeWalletId(request.getPayeeWalletId())
                .amount(request.getAmount())
                .paymentMethod(request.getPaymentMethod())
                .status(PaymentStatus.SUCCESS)
                .remarks(request.getRemarks())
                .message("Payment processed successfully")
                .transactionReference(transactionResponse.referenceId())
                .build();

        Payment savedPayment = paymentRepository.save(payments);

        // Update idempotency record
        idempotency.setPaymentReference(savedPayment.getPaymentReference());
        idempotency.setStatus(PaymentStatus.SUCCESS);

        paymentIdempotencyRepository.save(idempotency);

        // Publish payment event
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
                        savedPayment.getCreatedAt());

        outboxEventService.saveEvent(
                "PAYMENT",
                savedPayment.getPaymentReference(),
                "PAYMENT_COMPLETED",
                paymentCompletedEvent
        );

        LOGGER.info("Payment created successfully | paymentReference={} transactionReference={}",
                savedPayment.getPaymentReference(),
                savedPayment.getTransactionReference());
        return paymentMapper.toPaymentResponse(savedPayment);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentByReference(String paymentReference) {

        LOGGER.info("Fetching payment | paymentReference={}", paymentReference);

        Payment payments = paymentRepository
                .findByPaymentReference(paymentReference)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with reference: " + paymentReference));

        LOGGER.info("Payment fetched successfully | paymentReference={} status={}",
                payments.getPaymentReference(),
                payments.getStatus());

        return paymentMapper.toPaymentResponse(payments);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PaymentResponse> getAllPayments(Pageable pageable) {

        LOGGER.info("Fetching all payments | page={} size={}",
                pageable.getPageNumber(),
                pageable.getPageSize());

        Page<Payment> payments = paymentRepository.findAll(pageable);

        LOGGER.info("Payments fetched successfully | totalRecords={}",
                payments.getTotalElements());

        return payments.map(paymentMapper::toPaymentResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PaymentResponse> getPaymentsByStatus(PaymentStatus status, Pageable pageable) {

        LOGGER.info("Fetching payments by status={} page={} size={}",
                status,
                pageable.getPageNumber(),
                pageable.getPageSize());

        Page<Payment> payments = paymentRepository.findByStatus(status, pageable);

        LOGGER.info("Payments fetched successfully | status={} totalRecords={}",
                status,
                payments.getTotalElements());

        return payments.map(paymentMapper::toPaymentResponse);
    }

    @Override
    @Transactional
    public PaymentResponse refundPayment(String paymentReference) {

        LOGGER.info("Refund request received | paymentReference={}", paymentReference);

        Payment payments = paymentRepository.findByPaymentReference(paymentReference).orElseThrow(() ->
                new ResourceNotFoundException("Payment not found with reference: " + paymentReference));

        if (payments.getStatus() == PaymentStatus.REFUNDED) {
            throw new IllegalStateException("Payment already refunded");
        }

        if (payments.getStatus() != PaymentStatus.SUCCESS) {
            throw new IllegalStateException("Only successful payments can be refunded");
        }
        transactionClient.reverseTransaction(payments.getTransactionReference());

        payments.setStatus(PaymentStatus.REFUNDED);
        payments.setMessage("Payment refunded successfully");

        Payment savedPayment = paymentRepository.save(payments);

        LOGGER.info("Payment refunded successfully | paymentReference={}",
                savedPayment.getPaymentReference());

        return paymentMapper.toPaymentResponse(savedPayment);
    }
}
