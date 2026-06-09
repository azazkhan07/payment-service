package com.novapay.payment_service.dto.request;


import com.novapay.payment_service.entity.enums.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Setter
@Getter
public class PaymentRequest {
    @NotNull(message = "Payer wallet id is required")
    Long payerWalletId;
    @NotNull(message = "Payee wallet id is required")
    Long payeeWalletId;
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "1.0", message = "Amount must be greater than zero")
    BigDecimal amount;
    @NotNull(message = "Payment method is required")
    PaymentMethod paymentMethod;
    @Size(max = 255, message = "Remarks can not exceed 255 characters")
    String remarks;
}
