package com.novapay.payment_service.entity;

import com.novapay.payment_service.entity.enums.PaymentMethod;
import com.novapay.payment_service.entity.enums.PaymentStatus;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payments")
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private String paymentReference;
    @Column(nullable = false)
    private Long payerWalletId;
    @Column(nullable = false)
    private Long payeeWalletId;
    @Column(nullable = false)
    private BigDecimal amount;
    @Enumerated(EnumType.STRING)
    private PaymentMethod paymentMethod;
    @Enumerated(EnumType.STRING)
    private PaymentStatus status;
    private String remarks;
    private String message;
    private LocalDateTime createdAt;
    @PrePersist
    public void prePersist() {
        this.paymentReference = UUID.randomUUID().toString();
        this.createdAt = LocalDateTime.now();
    }
}
