package com.novapay.payment_service.service.impl;

import com.novapay.payment_service.client.TransactionClient;
import com.novapay.payment_service.dto.request.TransactionRequest;
import com.novapay.payment_service.dto.response.TransactionResponse;
import com.novapay.payment_service.exception.ServiceUnavailableException;
import com.novapay.payment_service.service.TransactionServiceClient;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TransactionServiceClientImpl implements TransactionServiceClient {

    private final TransactionClient transactionClient;

    @Override
    @CircuitBreaker(name = "transactionService", fallbackMethod = "transferMoneyFallback")
    public TransactionResponse transferMoney(TransactionRequest request) {
        return transactionClient.transferMoney(request);
    }

    @Override
    @CircuitBreaker(name = "transactionService", fallbackMethod = "reverseTransactionFallback")
    public TransactionResponse reverseTransaction(String referenceId) {
        return transactionClient.reverseTransaction(referenceId);
    }

    public TransactionResponse transferMoneyFallback(TransactionRequest request, Throwable throwable) {
        throw new ServiceUnavailableException("Transaction Service is currently unavailable");
    }

    public TransactionResponse reverseTransactionFallback(String referenceId, Throwable throwable) {
        throw new ServiceUnavailableException("Transaction Service is currently unavailable");
    }
}
