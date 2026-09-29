package com.novapay.payment_service.service.impl;

import com.novapay.payment_service.config.RazorpayConfig;
import com.novapay.payment_service.dto.request.PaymentRequest;
import com.novapay.payment_service.dto.request.RazorpayOrderRequest;
import com.novapay.payment_service.dto.response.PaymentResponse;
import com.novapay.payment_service.dto.response.RazorpayOrderResponse;
import com.novapay.payment_service.dto.response.RazorpayRefundResponse;
import com.novapay.payment_service.entity.Payment;
import com.novapay.payment_service.entity.PaymentIdempotency;
import com.novapay.payment_service.entity.enums.PaymentStatus;
import com.novapay.payment_service.exception.IdempotencyKeyReuseException;
import com.novapay.payment_service.exception.PaymentAlreadyProcessingException;
import com.novapay.payment_service.exception.ResourceNotFoundException;
import com.novapay.payment_service.exception.ServiceUnavailableException;
import com.novapay.payment_service.gateway.PaymentGateway;
import com.novapay.payment_service.mapper.PaymentMapper;
import com.novapay.payment_service.repository.PaymentIdempotencyRepository;
import com.novapay.payment_service.repository.PaymentRepository;
import com.novapay.payment_service.saga.entity.PaymentSaga;
import com.novapay.payment_service.saga.service.PaymentSagaOrchestrator;
import com.novapay.payment_service.service.PaymentService;
import com.novapay.payment_service.service.TransactionServiceClient;
import com.novapay.payment_service.util.PaymentRequestHashUtil;
import com.razorpay.Utils;
import lombok.RequiredArgsConstructor;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentServiceImpl.class);

    private final PaymentRepository paymentRepository;
    private final PaymentIdempotencyRepository paymentIdempotencyRepository;
    private final PaymentIdempotencyService paymentIdempotencyService;
    private final PaymentMapper paymentMapper;
    private final TransactionServiceClient transactionServiceClient;
    private final PaymentSagaOrchestrator paymentSagaOrchestrator;
    private final PaymentGateway paymentGateway;
    private final RazorpayConfig razorpayConfig;

    @Override
    @Transactional
    public PaymentResponse createPayment(PaymentRequest request, String idempotencyKey) {

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


        // Create payment in PENDING state.At this point the transaction reference does not exist yet.
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


        // Connect idempotency record with payment reference.
        idempotency.setPaymentReference(savedPayment.getPaymentReference());

        idempotency.setStatus(PaymentStatus.PENDING);

        paymentIdempotencyRepository.save(idempotency);


        // Create Saga.
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

        return paymentMapper.toPaymentResponse(savedPayment);
    }

    @Override
    @Transactional
    public RazorpayOrderResponse createRazorpayOrder(RazorpayOrderRequest request) {

        LOGGER.info("Creating Razorpay order | paymentReference={} amount={} currency={}",
                request.paymentReference(),
                request.amount(),
                request.currency());

        Payment payment = paymentRepository
                .findByPaymentReference(request.paymentReference())
                .orElseThrow(() ->
                        new ResourceNotFoundException("Payment not found with reference: " + request.paymentReference()));

        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new IllegalStateException("Only pending payments can be processed through Razorpay");
        }

        if (payment.getAmount().compareTo(request.amount()) != 0) {
            throw new IllegalArgumentException("Payment amount does not match Razorpay order amount");
        }

        String receipt = "RZP-" + UUID.randomUUID();

        RazorpayOrderResponse response = paymentGateway.createOrder(request.amount(), request.currency(), receipt);

        payment.setGatewayOrderId(response.orderId());

        payment.setMessage("Razorpay order created successfully");

        paymentRepository.save(payment);

        LOGGER.info("Razorpay order created and linked | paymentReference={} gatewayOrderId={}",
                payment.getPaymentReference(),
                response.orderId());

        return response;
    }

    @Override
    @Transactional
    public boolean verifyRazorpayPayment(String orderId, String paymentId, String signature) {

        LOGGER.info("Verifying Razorpay payment | orderId={} paymentId={}", orderId, paymentId);

        boolean verified = paymentGateway.verifyPayment(orderId, paymentId, signature);

        if (!verified) {

            LOGGER.warn("Razorpay payment verification failed | orderId={} paymentId={}", orderId, paymentId);

            return false;
        }

        Payment payment = paymentRepository
                .findByGatewayOrderId(orderId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Payment not found for Razorpay order: " + orderId));

        payment.setGatewayPaymentId(paymentId);
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setMessage("Razorpay payment verified successfully");

        Payment savedPayment = paymentRepository.save(payment);

        LOGGER.info("Razorpay payment verified and payment updated | paymentReference={} orderId={} paymentId={} status={}",
                savedPayment.getPaymentReference(),
                orderId,
                paymentId,
                savedPayment.getStatus());

        return true;
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
    @Transactional(noRollbackFor = ServiceUnavailableException.class)
    public PaymentResponse refundPayment(String paymentReference) {

        LOGGER.info("Refund request received | paymentReference={}", paymentReference);

        Payment payments = paymentRepository
                .findByPaymentReference(paymentReference)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Payment not found with reference: " + paymentReference));

        if (payments.getStatus() == PaymentStatus.REFUNDED) {
            throw new IllegalStateException("Payment already refunded");
        }

        if (payments.getStatus() != PaymentStatus.SUCCESS
                && payments.getStatus() != PaymentStatus.REFUND_PENDING) {

            throw new IllegalStateException(
                    "Payment cannot be refunded in current status: "
                            + payments.getStatus());
        }

        // 1. First request -> SUCCESS to REFUND_PENDING
        if (payments.getStatus() == PaymentStatus.SUCCESS) {

            payments.setStatus(PaymentStatus.REFUND_PENDING);
            payments.setMessage("Refund processing");

            paymentRepository.save(payments);

            // 2. Refund money through Razorpay
            RazorpayRefundResponse refundResponse =
                    paymentGateway.refundPayment(
                            payments.getGatewayPaymentId(),
                            payments.getAmount());

            // 3. Save Razorpay refund ID
            payments.setGatewayRefundId(refundResponse.refundId());
            payments.setMessage("Razorpay refund successful");

            paymentRepository.save(payments);

            LOGGER.info(
                    "Razorpay refund successful | paymentReference={} gatewayRefundId={}",
                    payments.getPaymentReference(),
                    payments.getGatewayRefundId());
        }

        // 4. Reverse internal transaction only when it exists
        if (payments.getTransactionReference() != null) {

            transactionServiceClient.reverseTransaction(
                    payments.getTransactionReference());

        } else {

            LOGGER.warn(
                    "Transaction reference is null | paymentReference={}",
                    payments.getPaymentReference());
        }

        // 5. Mark payment fully refunded
        payments.setStatus(PaymentStatus.REFUNDED);
        payments.setMessage("Payment refunded successfully");

        Payment savedPayment = paymentRepository.save(payments);

        LOGGER.info(
                "Payment refunded successfully | paymentReference={} status={}",
                savedPayment.getPaymentReference(),
                savedPayment.getStatus());

        return paymentMapper.toPaymentResponse(savedPayment);
    }

    @Override
    @Transactional
    public void handleRazorpayWebhook(String payload, String signature) {

        LOGGER.info("Razorpay webhook received");

        try {
            Utils.verifyWebhookSignature(payload, signature, razorpayConfig.getWebhookSecret());

            JSONObject webhook = new JSONObject(payload);

            String event = webhook.getString("event");

            LOGGER.info("Razorpay webhook event received | event={}", event);

            if ("order.paid".equals(event)) {

                JSONObject payloadObject = webhook.getJSONObject("payload");

                JSONObject orderEntity = payloadObject
                        .getJSONObject("order")
                        .getJSONObject("entity");

                JSONObject paymentEntity = payloadObject
                        .getJSONObject("payment")
                        .getJSONObject("entity");

                String orderId = orderEntity.getString("id");
                String paymentId = paymentEntity.getString("id");

                LOGGER.info("Processing Razorpay order.paid | orderId={} paymentId={}", orderId, paymentId);

                Payment payment = paymentRepository
                        .findByGatewayOrderId(orderId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException("Payment not found for Razorpay order: " + orderId));

                if (payment.getStatus() == PaymentStatus.SUCCESS) {

                    LOGGER.info("Payment already successful | paymentReference={} orderId={} paymentId={}",
                            payment.getPaymentReference(),
                            orderId,
                            paymentId);

                    return;
                }

                if (payment.getStatus() == PaymentStatus.REFUNDED) {

                    LOGGER.warn("Ignoring order.paid webhook for already refunded payment | paymentReference={} orderId={}",
                            payment.getPaymentReference(),
                            orderId);

                    return;
                }

                payment.setStatus(PaymentStatus.SUCCESS);
                payment.setGatewayPaymentId(paymentId);
                payment.setMessage("Razorpay payment confirmed by webhook");

                paymentRepository.save(payment);

                LOGGER.info("Payment updated from Razorpay webhook | paymentReference={} orderId={} paymentId={} status={}",
                        payment.getPaymentReference(),
                        orderId,
                        paymentId,
                        payment.getStatus());
            }

        } catch (Exception exception) {

            LOGGER.error("Razorpay webhook processing failed", exception);

            throw new IllegalArgumentException("Razorpay webhook processing failed", exception);
        }
    }

}

