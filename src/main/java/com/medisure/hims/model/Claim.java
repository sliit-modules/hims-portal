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
@Table(name = "claims")
@Getter
@Setter
@NoArgsConstructor
public class Claim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String claimCode;

    @NotNull
    @ManyToOne
    @JoinColumn(name = "policy_id", nullable = false)
    private Policy policy;

    @NotNull
    @ManyToOne
    @JoinColumn(name = "claimant_id", nullable = false)
    private User claimant;

    @ManyToOne
    @JoinColumn(name = "claimant_dependent_id")
    private Dependent claimantDependent;

    @NotNull(message = "Claim category is required")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClaimCategory category = ClaimCategory.OTHER;

    @NotBlank(message = "Hospital name is required")
    @Column(nullable = false)
    private String hospitalName;

    @NotNull(message = "Treatment date is required")
    @PastOrPresent(message = "Treatment date cannot be in the future")
    private LocalDate treatmentDate;

    @NotBlank(message = "Diagnosis summary is required")
    @Column(nullable = false, length = 1000)
    private String diagnosisSummary;

    @NotNull
    @Positive(message = "Amount claimed must be positive")
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amountClaimed;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClaimStatus status = ClaimStatus.SUBMITTED;

    @NotNull
    private LocalDateTime submittedAt = LocalDateTime.now();

    @Column(length = 500)
    private String decisionNotes;

    /** For a VOIDED claim, the claim it duplicated. */
    @ManyToOne
    @JoinColumn(name = "duplicate_of_id")
    private Claim duplicateOf;

    /** True when both claims are for the same treated person: the policyholder or the same dependent. */
    public boolean samePatientAs(Claim other) {
        if (!claimant.getId().equals(other.getClaimant().getId())) {
            return false;
        }
        if (claimantDependent == null || other.getClaimantDependent() == null) {
            return claimantDependent == null && other.getClaimantDependent() == null;
        }
        return claimantDependent.getId().equals(other.getClaimantDependent().getId());
    }
}
