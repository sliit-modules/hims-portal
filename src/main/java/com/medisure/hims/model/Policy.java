package com.medisure.hims.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "policies")
@Getter
@Setter
@NoArgsConstructor
public class Policy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String policyCode;

    @NotNull
    @ManyToOne
    @JoinColumn(name = "policyholder_id", nullable = false)
    private User policyholder;

    @NotNull
    @ManyToOne
    @JoinColumn(name = "plan_id", nullable = false)
    private InsurancePlan plan;

    @NotNull
    @OneToOne
    @JoinColumn(name = "source_application_id", nullable = false, unique = true)
    private UnderwritingApplication sourceApplication;

    @NotNull
    @Positive(message = "Premium amount must be positive")
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal premiumAmount;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PremiumFrequency premiumFrequency = PremiumFrequency.MONTHLY;

    @NotNull
    private LocalDate nextDueDate;

    @NotNull
    private LocalDate startDate;

    @NotNull
    private LocalDate endDate;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PolicyStatus status = PolicyStatus.ACTIVE;

    @OneToMany(mappedBy = "policy", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Dependent> dependents = new ArrayList<>();
}
