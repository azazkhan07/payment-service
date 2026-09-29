package com.novapay.payment_service.gateway;

import com.novapay.payment_service.config.RazorpayConfig;
import com.novapay.payment_service.dto.response.RazorpayOrderResponse;
import com.novapay.payment_service.dto.response.RazorpayRefundResponse;
import com.razorpay.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Slf4j
@Component
@RequiredArgsConstructor
public class RazorpayPaymentGateway implements PaymentGateway {

    private final RazorpayClient razorpayClient;
    private final RazorpayConfig razorpayConfig;

    @Override
    public RazorpayOrderResponse createOrder(BigDecimal amount, String currency, String receipt) {

        log.info("Creating Razorpay order | amount={} currency={} receipt={}",
                amount, currency, receipt);

        try {
            long amountInPaise = amount
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(0, RoundingMode.UNNECESSARY)
                    .longValueExact();

            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", amountInPaise);
            orderRequest.put("currency", currency);
            orderRequest.put("receipt", receipt);

            log.info("Sending request to Razorpay: {}", orderRequest);

            Order order = razorpayClient.orders.create(orderRequest);

            log.info("Razorpay response received: {}", order);

            String orderId = order.get("id");
            Number razorpayAmount = (Number) order.get("amount");

            BigDecimal amountInRupees = BigDecimal
                    .valueOf(razorpayAmount.longValue())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.UNNECESSARY);

            String razorpayCurrency = (String) order.get("currency");
            String razorpayReceipt = (String) order.get("receipt");

            log.info("Razorpay order created successfully | orderId={} receipt={}", orderId, razorpayReceipt);

            return new RazorpayOrderResponse(
                    orderId,
                    amountInRupees,
                    razorpayCurrency,
                    razorpayReceipt);

        } catch (RazorpayException | ArithmeticException | ClassCastException exception) {

            log.error("Failed to create Razorpay order | receipt={}", receipt, exception);

            throw new IllegalStateException("Failed to create Razorpay order", exception);
        }
    }

    @Override
    public boolean verifyPayment(String orderId, String paymentId, String signature) {

        try {
            JSONObject attributes = new JSONObject();

            attributes.put("razorpay_order_id", orderId);
            attributes.put("razorpay_payment_id", paymentId);
            attributes.put("razorpay_signature", signature);

            return Utils.verifyPaymentSignature(attributes, razorpayConfig.getKeySecret());

        } catch (RazorpayException e) {
            log.error("Razorpay verification failed", e);
            throw new IllegalStateException("Failed to verify Razorpay payment", e);
        }
    }

    @Override
    public RazorpayRefundResponse refundPayment(String paymentId, BigDecimal amount) {

        log.info("Creating Razorpay refund | paymentId={} amount={}", paymentId, amount);

        try {
            long amountInPaise = amount
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(0, RoundingMode.UNNECESSARY)
                    .longValueExact();

            JSONObject refundRequest = new JSONObject();
            refundRequest.put("amount", amountInPaise);

            log.info("Sending refund request to Razorpay | paymentId={} amountInPaise={}", paymentId, amountInPaise);

            Refund refund = razorpayClient.payments.refund(paymentId, refundRequest);

            String refundId = refund.get("id");
            Number refundAmount = (Number) refund.get("amount");
            String refundStatus = refund.get("status");

            BigDecimal refundAmountInRupees = BigDecimal
                    .valueOf(refundAmount.longValue())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.UNNECESSARY);

            log.info("Razorpay refund created successfully | paymentId={} refundId={} amount={} status={}",
                    paymentId,
                    refundId,
                    refundAmountInRupees,
                    refundStatus);

            return new RazorpayRefundResponse(
                    refundId,
                    refundAmountInRupees,
                    refundStatus);

        } catch (RazorpayException | ArithmeticException | ClassCastException exception) {

            log.error("Failed to refund Razorpay payment | paymentId={} amount={}",
                    paymentId,
                    amount,
                    exception);

            throw new IllegalStateException("Failed to refund Razorpay payment", exception);
        }
    }
    }
