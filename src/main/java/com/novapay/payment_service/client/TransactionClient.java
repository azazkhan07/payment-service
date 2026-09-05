package com.novapay.payment_service.client;

import com.novapay.payment_service.dto.request.TransactionRequest;
import com.novapay.payment_service.dto.response.TransactionResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "transaction-service")
public interface TransactionClient {

    @PostMapping("/api/v1/transactions/transfer")
    TransactionResponse transferMoney(@RequestBody TransactionRequest transactionRequest);

    @PutMapping("/api/v1/transactions/reverse/{referenceId}")
    TransactionResponse reverseTransaction(@PathVariable String referenceId);

}
