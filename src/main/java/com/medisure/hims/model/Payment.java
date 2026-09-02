package com.medisure.hims.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne
    @JoinColumn(name = "policy_id", nullable = false)
    private Policy policy;

    @NotNull
    @Positive(message = "Amount must be positive")
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @NotNull(message = "Payment method is required")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod method;

    private boolean autoPay;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status = PaymentStatus.PAID;

    @NotNull(message = "Billing period start is required")
    private LocalDate billingPeriodStart;

    @NotNull(message = "Billing period end is required")
    private LocalDate billingPeriodEnd;

    @NotNull
    private LocalDateTime paidAt = LocalDateTime.now();
}
