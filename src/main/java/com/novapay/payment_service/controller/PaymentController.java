package com.novapay.payment_service.controller;

import com.novapay.payment_service.dto.request.PaymentRequest;
import com.novapay.payment_service.dto.request.RazorpayOrderRequest;
import com.novapay.payment_service.dto.request.RazorpayPaymentVerifyRequest;
import com.novapay.payment_service.dto.response.PaymentResponse;
import com.novapay.payment_service.dto.response.RazorpayOrderResponse;
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
            @ApiResponse(responseCode = "400", description = "Invalid payment request"),})
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

    @Operation(summary = "Create Razorpay order",
            description = "Creates a Razorpay order for an external payment")
    @ApiResponses(value = {@ApiResponse(responseCode = "201", description = "Razorpay order created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid order request"),
            @ApiResponse(responseCode = "500", description = "Unable to create Razorpay order")})
    @PostMapping("/razorpay/order")
    public ResponseEntity<RazorpayOrderResponse> createRazorpayOrder(@Valid @RequestBody RazorpayOrderRequest request) {

        LOGGER.info("Razorpay order request received | amount={} currency={}", request.amount(), request.currency());

        RazorpayOrderResponse response = paymentService.createRazorpayOrder(request);

        LOGGER.info("Razorpay order created successfully | orderId={}", response.orderId());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Verify Razorpay payment", description = "Verifies the Razorpay payment signature")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Payment signature verified"),
            @ApiResponse(responseCode = "400", description = "Payment signature verification failed"),
            @ApiResponse(responseCode = "500", description = "Unable to verify Razorpay payment")})
    @PostMapping("/razorpay/verify")
    public ResponseEntity<Boolean> verifyRazorpayPayment(
            @Valid @RequestBody RazorpayPaymentVerifyRequest request) {

        LOGGER.info("Razorpay payment verification request received | orderId={} paymentId={}",
                request.orderId(),
                request.paymentId());

        boolean verified = paymentService.verifyRazorpayPayment(
                request.orderId(),
                request.paymentId(),
                request.signature());

        return ResponseEntity.ok(verified);
    }
    @Operation(summary = "Handle Razorpay webhook", description = "Receives and processes webhook events sent by Razorpay")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Webhook processed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid webhook request"),
            @ApiResponse(responseCode = "401", description = "Invalid Razorpay webhook signature"),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    @PostMapping("/razorpay/webhook")
    public ResponseEntity<Void> handleRazorpayWebhook(@RequestBody String payload, @RequestHeader("X-Razorpay-Signature") String signature) {

        LOGGER.info("Razorpay webhook received");

        paymentService.handleRazorpayWebhook(payload, signature);

        return ResponseEntity.ok().build();
    }
}
