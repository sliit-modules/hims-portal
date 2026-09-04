package com.medisure.hims.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "insurance_plans")
@Getter
@Setter
@NoArgsConstructor
public class InsurancePlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Plan name is required")
    @Column(nullable = false, unique = true)
    private String planName;

    @NotBlank(message = "Description is required")
    @Column(nullable = false, length = 1000)
    private String description;

    @NotNull(message = "Plan type is required")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlanType planType;

    @NotNull(message = "Premium rate is required")
    @Positive(message = "Premium rate must be positive")
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal premiumRate;

    @NotNull(message = "Coverage limit is required")
    @Positive(message = "Coverage limit must be positive")
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal coverageLimit;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlanStatus status = PlanStatus.ACTIVE;
}
