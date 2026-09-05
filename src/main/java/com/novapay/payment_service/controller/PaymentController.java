package com.novapay.payment_service.controller;

import com.novapay.payment_service.dto.request.PaymentRequest;
import com.novapay.payment_service.dto.response.PaymentResponse;
import com.novapay.payment_service.entity.enums.PaymentStatus;
import com.novapay.payment_service.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@Tag(name = "Payment APIs", description = "Payment processing endpoints")
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentController.class);

    private final PaymentService paymentService;

    @Operation(summary = "Create a payment")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Payment created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid payment request"),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment(@RequestHeader(value = "Idempotency-Key",
            required = true) String idempotencyKey,
            @Valid @RequestBody PaymentRequest paymentRequest) {

        LOGGER.info("Payment request received | payerWalletId={} payeeWalletId={} amount={}",
                paymentRequest.getPayerWalletId(),
                paymentRequest.getPayeeWalletId(),
                paymentRequest.getAmount());

        PaymentResponse paymentResponse = paymentService.createPayment(paymentRequest, idempotencyKey);

        LOGGER.info("Payment created successfully | paymentReference={}", paymentResponse.paymentReference());
        return ResponseEntity.status(HttpStatus.OK).body(paymentResponse);
    }

    @Operation(summary = "Get payment by reference")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Payment found"),
            @ApiResponse(responseCode = "404", description = "Payment not found")})
    @GetMapping("/{paymentReference}")
    public ResponseEntity<PaymentResponse> getPaymentByReference(@PathVariable String paymentReference) {

        LOGGER.info("Fetching payment | paymentReference={}", paymentReference);

        return ResponseEntity.status(HttpStatus.OK).body(paymentService.getPaymentByReference(paymentReference));
    }

    @Operation(summary = "Get all payments")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Payments fetched successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid payment request"),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    @GetMapping
    public ResponseEntity<Page<PaymentResponse>> getAllPayments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDirection) {

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.fromString(sortDirection), sortBy));

        LOGGER.info("Fetching all payments | page={} size={}", page, size);
        return ResponseEntity.status(HttpStatus.OK).body(paymentService.getAllPayments(pageable));
    }

    @Operation(summary = "Get payments by status")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Payments fetched successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid payment request"),
    })
    @GetMapping("/filter/status/{status}")
    public ResponseEntity<Page<PaymentResponse>> getPaymentsByStatus(
            @PathVariable PaymentStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDirection) {

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.fromString(sortDirection), sortBy));

        LOGGER.info("Fetching payments by status={} page={} size={}", status, page, size);

        return ResponseEntity.status(HttpStatus.OK).body(paymentService.getPaymentsByStatus(status, pageable));
    }

    @Operation(summary = "Refund payment")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Payment refunded successfully"),
            @ApiResponse(responseCode = "404", description = "Payment not found"),
            @ApiResponse(responseCode = "400", description = "Payment cannot be refunded")})
    @PutMapping("/refund/{paymentReference}")
    public ResponseEntity<PaymentResponse> refundPayment(@PathVariable String paymentReference) {
        LOGGER.info("Refund request received | paymentReference={}", paymentReference);
        return ResponseEntity.status(HttpStatus.OK).body(paymentService.refundPayment(paymentReference));
    }
}
