package com.novapay.payment_service.mapper;

import com.novapay.payment_service.dto.response.PaymentResponse;
import com.novapay.payment_service.entity.Payment;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PaymentMapper {

    PaymentResponse toPaymentResponse(Payment payment);
}
