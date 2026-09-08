package com.novapay.payment_service.service.impl;

import com.novapay.payment_service.client.TransactionClient;
import com.novapay.payment_service.dto.request.PaymentRequest;
import com.novapay.payment_service.dto.response.PaymentResponse;
import com.novapay.payment_service.entity.Payment;
import com.novapay.payment_service.entity.PaymentIdempotency;
import com.novapay.payment_service.entity.enums.PaymentStatus;
import com.novapay.payment_service.exception.IdempotencyKeyReuseException;
import com.novapay.payment_service.exception.PaymentAlreadyProcessingException;
import com.novapay.payment_service.exception.ResourceNotFoundException;
import com.novapay.payment_service.mapper.PaymentMapper;
import com.novapay.payment_service.repository.PaymentIdempotencyRepository;
import com.novapay.payment_service.repository.PaymentRepository;
import com.novapay.payment_service.saga.entity.PaymentSaga;
import com.novapay.payment_service.saga.service.PaymentSagaOrchestrator;
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
    private final PaymentSagaOrchestrator paymentSagaOrchestrator;

    @Override
    @Transactional
    public PaymentResponse createPayment(
            PaymentRequest request,
            String idempotencyKey) {

        LOGGER.info("Payment request received | payerWalletId={} payeeWalletId={} amount={}",
                request.getPayerWalletId(),
                request.getPayeeWalletId(),
                request.getAmount());

        String requestHash = PaymentRequestHashUtil.generateHash(request);

        var existingIdempotency = paymentIdempotencyRepository.findByIdempotencyKey(idempotencyKey);

        if (existingIdempotency.isPresent()) {

            PaymentIdempotency idempotency = existingIdempotency.get();

            if (!idempotency.getRequestHash().equals(requestHash)) {

                throw new IdempotencyKeyReuseException("The Idempotency-Key has already been used with a different request");
            }

            if (idempotency.getPaymentReference() != null) {

                LOGGER.info("Duplicate payment request detected | idempotencyKey={}", idempotencyKey);

                return getPaymentByReference(idempotency.getPaymentReference());
            }

            throw new PaymentAlreadyProcessingException("A payment with this Idempotency-Key is currently being processed");
        }

        if (request.getPayerWalletId().equals(request.getPayeeWalletId())) {
            throw new IllegalArgumentException("Payer and Payee wallet cannot be same");
        }

        PaymentIdempotency idempotency;

        try {
            idempotency = paymentIdempotencyService.reserveKey(idempotencyKey, requestHash);
        } catch (DataIntegrityViolationException ex) {

            LOGGER.info("Concurrent duplicate payment request detected | idempotencyKey={}", idempotencyKey);

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

        /*
         * Create payment in PENDING state.
         *
         * At this point the transaction reference does not exist yet.
         */
        Payment payment = Payment.builder()
                .payerWalletId(request.getPayerWalletId())
                .payeeWalletId(request.getPayeeWalletId())
                .amount(request.getAmount())
                .paymentMethod(request.getPaymentMethod())
                .status(PaymentStatus.PENDING)
                .remarks(request.getRemarks())
                .message("Payment processing started")
                .transactionReference(null)
                .build();

        Payment savedPayment = paymentRepository.save(payment);

        /*
         * Connect idempotency record with payment reference.
         */
        idempotency.setPaymentReference(savedPayment.getPaymentReference());

        idempotency.setStatus(PaymentStatus.PENDING);

        paymentIdempotencyRepository.save(idempotency);

        /*
         * Create Saga.
         */
        PaymentSaga saga = PaymentSaga.builder()
                .paymentReference(savedPayment.getPaymentReference())
                .payerWalletId(savedPayment.getPayerWalletId())
                .payeeWalletId(savedPayment.getPayeeWalletId())
                .amount(savedPayment.getAmount())
                .status("STARTED")
                .build();

        paymentSagaOrchestrator.startSaga(saga);

        LOGGER.info("Payment Saga started | paymentReference={}",
                savedPayment.getPaymentReference());

        return paymentMapper.toPaymentResponse(
                savedPayment);
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
