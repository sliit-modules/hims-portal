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

    /** Set when the policyholder asks for their money back; an admin or claims officer decides. */
    private LocalDateTime refundRequestedAt;

    @Column(length = 500)
    private String refundReason;

    @ManyToOne
    @JoinColumn(name = "refund_decided_by_id")
    private User refundDecidedBy;

    private LocalDateTime refundDecidedAt;

    @Column(length = 500)
    private String refundDecisionNotes;

    /** Why staff voided this payment (e.g. a duplicate entry), when its status is CANCELLED. */
    @Column(length = 500)
    private String voidReason;

    /** A refund request that is still waiting for an admin or claims officer. */
    public boolean isRefundPending() {
        return refundRequestedAt != null && refundDecidedAt == null;
    }
}
