package com.novapay.payment_service.mapper;

import com.novapay.payment_service.dto.request.PaymentRequest;
import com.novapay.payment_service.dto.response.PaymentResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PaymentMapper {

    PaymentResponse toPaymentResponse(PaymentRequest request);
}
