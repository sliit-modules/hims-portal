package com.medisure.hims.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One attempt to pay a premium through PayHere. The premium Payment is only recorded once PayHere
 * confirms the money was received, so an abandoned or failed checkout never looks like a payment.
 */
@Entity
@Table(name = "payhere_checkouts")
@Getter
@Setter
@NoArgsConstructor
public class PayHereCheckout {

    public static final String PENDING = "PENDING";
    public static final String COMPLETED = "COMPLETED";
    public static final String CANCELLED = "CANCELLED";
    public static final String FAILED = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The order_id sent to PayHere; unique for every attempt. */
    @Column(nullable = false, unique = true, length = 64)
    private String orderId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "policy_id", nullable = false)
    private Policy policy;

    @ManyToOne(optional = false)
    @JoinColumn(name = "requested_by_id", nullable = false)
    private User requestedBy;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency = "LKR";

    /** PENDING, COMPLETED, CANCELLED or FAILED — plain text, so a new state needs no schema change. */
    @Column(nullable = false, length = 20)
    private String status = PENDING;

    /** PayHere's own payment_id once the payment is confirmed. */
    @Column(length = 64)
    private String gatewayPaymentId;

    /** What PayHere last reported, e.g. "status_code 2" or the Retrieval API status. */
    @Column(length = 60)
    private String gatewayStatus;

    @OneToOne
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime completedAt;
}
