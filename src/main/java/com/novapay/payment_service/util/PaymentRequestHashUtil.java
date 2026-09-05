package com.novapay.payment_service.util;

import com.novapay.payment_service.dto.request.PaymentRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class PaymentRequestHashUtil {
    public PaymentRequestHashUtil() {
    }

    public static String generateHash(PaymentRequest request) {
        String requestData =
                request.getPayerWalletId() + "|" +
                        request.getPayeeWalletId() + "|" +
                        request.getAmount() + "|" +
                        request.getPaymentMethod() + "|" +
                        request.getRemarks();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                    requestData.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();

            for (byte b : hash) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

}








