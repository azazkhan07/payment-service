package com.novapay.payment_service.client;

import com.novapay.payment_service.dto.response.WalletResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "wallet-service")
public interface WalletClient {

    @GetMapping("/api/v1/wallets/id/{walletId}")
    WalletResponse getWalletById(@PathVariable Long walletId);
}
