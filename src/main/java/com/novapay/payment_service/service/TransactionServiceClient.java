package com.novapay.payment_service.service;

import com.novapay.payment_service.dto.request.TransactionRequest;
import com.novapay.payment_service.dto.response.TransactionResponse;

public interface TransactionServiceClient {

    TransactionResponse transferMoney(TransactionRequest request);

    TransactionResponse reverseTransaction(String referenceId);
}
