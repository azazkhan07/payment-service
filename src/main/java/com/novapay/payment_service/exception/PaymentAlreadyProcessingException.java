package com.novapay.payment_service.exception;

public class PaymentAlreadyProcessingException extends RuntimeException{
    public PaymentAlreadyProcessingException(String message) {
        super(message);
    }
}
