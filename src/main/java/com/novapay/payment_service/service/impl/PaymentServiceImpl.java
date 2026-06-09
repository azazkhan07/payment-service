    package com.novapay.payment_service.service.impl;

    import com.novapay.payment_service.dto.request.PaymentRequest;
    import com.novapay.payment_service.dto.response.PaymentResponse;
    import com.novapay.payment_service.entity.Payment;
    import com.novapay.payment_service.entity.enums.PaymentStatus;
    import com.novapay.payment_service.exception.ResourceNotFoundException;
    import com.novapay.payment_service.mapper.PaymentMapper;
    import com.novapay.payment_service.repository.PaymentRepository;
    import com.novapay.payment_service.service.PaymentService;
    import lombok.RequiredArgsConstructor;
    import org.slf4j.Logger;
    import org.slf4j.LoggerFactory;
    import org.springframework.data.domain.Page;
    import org.springframework.data.domain.Pageable;
    import org.springframework.stereotype.Service;
    import org.springframework.transaction.annotation.Transactional;

    @Service
    @RequiredArgsConstructor
    public class PaymentServiceImpl implements PaymentService {

        private static final Logger LOGGER = LoggerFactory.getLogger(PaymentServiceImpl.class);

        private final PaymentRepository paymentRepository;
        private final PaymentMapper paymentMapper;

        @Override
        @Transactional
        public PaymentResponse createPayment(PaymentRequest request) {
            LOGGER.info("Payment request received | payerWalletId={} payeeWalletId={} amount={}",
                    request.getPayerWalletId(),
                    request.getPayeeWalletId(),
                    request.getAmount());

            if (request.getPayerWalletId().equals(request.getPayeeWalletId())) {
                throw new IllegalArgumentException(
                        "Payer and Payee wallet cannot be same");
            }

            Payment payments = Payment.builder()
                    .payerWalletId(request.getPayerWalletId())
                    .payeeWalletId(request.getPayeeWalletId())
                    .amount(request.getAmount())
                    .paymentMethod(request.getPaymentMethod())
                    .status(PaymentStatus.SUCCESS)
                    .remarks(request.getRemarks())
                    .message("Payment processed successfully")
                    .build();

            Payment savedPayment = paymentRepository.save(payments);

            LOGGER.info("Payment created successfully | paymentReference={}",
                    savedPayment.getPaymentReference());

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

            payments.setStatus(PaymentStatus.REFUNDED);
            payments.setMessage("Payment refunded successfully");

            Payment savedPayment = paymentRepository.save(payments);

            LOGGER.info("Payment refunded successfully | paymentReference={}",
                    savedPayment.getPaymentReference());

            return paymentMapper.toPaymentResponse(savedPayment);
        }
    }
