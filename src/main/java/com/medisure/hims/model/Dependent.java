package com.medisure.hims.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;

@Entity
@Table(name = "dependents")
@Getter
@Setter
@NoArgsConstructor
public class Dependent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne
    @JoinColumn(name = "policy_id", nullable = false)
    private Policy policy;

    @NotBlank(message = "Full name is required")
    @Column(nullable = false)
    private String fullName;

    /** Optional for minors — Sri Lankans are only issued a NIC at 16+, so it is required from 18. */
    @Pattern(regexp = "^$|\\d{12}", message = "NIC must be the new 12-digit format")
    @Column(length = 12)
    private String nic;

    @NotNull(message = "Date of birth is required")
    @Past(message = "Date of birth must be in the past")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateOfBirth;

    @NotNull(message = "Gender is required")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Gender gender;

    @NotNull(message = "Relationship is required")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RelationshipType relationship;

    // ---------------- Medical profile ----------------

    @NotNull(message = "Blood group is required")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BloodGroup bloodGroup = BloodGroup.UNKNOWN;

    @Column(length = 500)
    private String allergies;

    @Column(length = 1000)
    private String chronicConditions;

    @Column(length = 500)
    private String currentMedications;

    // ---------------- Privacy consent ----------------

    /**
     * When an adult dependent's own consent was confirmed. Minors have none: the policyholder
     * consents for them as parent or guardian.
     */
    private LocalDateTime consentConfirmedAt;

    /** Form-only tick box: the agent confirms the adult dependent agreed. Not stored as a column. */
    @Transient
    private boolean consentConfirmed;

    public int getAge() {
        return dateOfBirth == null ? 0 : Period.between(dateOfBirth, LocalDate.now()).getYears();
    }

    /** A NIC is mandatory once a covered member turns 18. */
    public boolean isNicRequired() {
        return getAge() >= 18;
    }

    public String getInitials() {
        if (fullName == null || fullName.isBlank()) {
            return "?";
        }
        String[] parts = fullName.trim().split("\\s+");
        String first = parts[0].substring(0, 1);
        return parts.length > 1 ? (first + parts[1].substring(0, 1)).toUpperCase() : first.toUpperCase();
    }

    public int getAvatarTone() {
        return (int) (Math.abs((long) getInitials().hashCode()) % 5);
    }
}
