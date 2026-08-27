package com.medisure.hims.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "underwriting_applications")
@Getter
@Setter
@NoArgsConstructor
public class UnderwritingApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String applicationCode;

    @NotNull
    @ManyToOne
    @JoinColumn(name = "applicant_id", nullable = false)
    private User applicant;

    @NotNull(message = "Please select a plan")
    @ManyToOne
    @JoinColumn(name = "requested_plan_id", nullable = false)
    private InsurancePlan requestedPlan;

    @NotNull
    @Min(value = 0, message = "Age cannot be negative")
    private Integer applicantAge;

    private boolean hasPreExistingConditions;

    @Column(length = 1000)
    private String conditionsNotes;

    @NotNull
    @Min(value = 0, message = "Number of dependents cannot be negative")
    private Integer numDependentsPlanned = 0;

    @Min(value = 0) @Max(value = 100)
    private Integer riskScore;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApplicationDecision decision = ApplicationDecision.PENDING;

    @DecimalMin(value = "0.0", message = "Premium loading cannot be negative")
    private BigDecimal premiumLoadingPercent = BigDecimal.ZERO;

    @NotNull
    private LocalDateTime submittedAt = LocalDateTime.now();

    private LocalDateTime decidedAt;
}
