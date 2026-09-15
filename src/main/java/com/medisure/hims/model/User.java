package com.medisure.hims.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "NIC is required")
    @Pattern(regexp = "\\d{12}", message = "NIC must be the new 12-digit format, e.g. 200412345678")
    @Column(nullable = false, unique = true, length = 12)
    private String nic;

    @Column(nullable = false)
    private String passwordHash;

    @NotBlank(message = "Full name is required")
    @Column(nullable = false)
    private String fullName;

    @NotNull(message = "Date of birth is required")
    @Past(message = "Date of birth must be in the past")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    @Column(nullable = false)
    private LocalDate dateOfBirth;

    @NotNull(message = "Gender is required")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Gender gender;

    @NotBlank(message = "Address is required")
    @Column(nullable = false, length = 500)
    private String address;

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "\\d{9,10}", message = "Phone number must be 9-10 digits")
    @Column(nullable = false, length = 15)
    private String phoneNumber;

    @NotBlank(message = "Email is required")
    @Email(message = "Enter a valid email")
    @Column(nullable = false, unique = true)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    private boolean enabled = true;

    // ---------------- Medical & personal profile ----------------
    // Sensitive health data: only the member themself and staff whose role needs it
    // (underwriting, claims, sales, admin) may view these.

    @NotNull(message = "Blood group is required")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BloodGroup bloodGroup = BloodGroup.UNKNOWN;

    @Enumerated(EnumType.STRING)
    private MaritalStatus maritalStatus;

    private String occupation;

    @Min(value = 30, message = "Height must be at least 30 cm")
    @Max(value = 260, message = "Height must be under 260 cm")
    private Integer heightCm;

    @Min(value = 2, message = "Weight must be at least 2 kg")
    @Max(value = 400, message = "Weight must be under 400 kg")
    private Integer weightKg;

    @Column(length = 500)
    private String allergies;

    @Column(length = 1000)
    private String chronicConditions;

    @Column(length = 500)
    private String currentMedications;

    private String emergencyContactName;

    @Pattern(regexp = "^$|\\d{9,10}", message = "Emergency contact must be 9-10 digits")
    @Column(length = 15)
    private String emergencyContactPhone;

    private String emergencyContactRelation;

    // ---------------- Privacy consent (PDPA No. 9 of 2022) ----------------

    /** When the member accepted the privacy notice; null until they do. */
    private LocalDateTime privacyConsentAt;

    /** Which version of the notice was accepted, so a revised notice can be put to members again. */
    @Column(length = 20)
    private String privacyNoticeVersion;

    /** True once the member has accepted the privacy notice currently in force. */
    public boolean hasCurrentPrivacyConsent() {
        return privacyConsentAt != null && PrivacyNotice.VERSION.equals(privacyNoticeVersion);
    }

    /** First letters of the first two words of the full name, for avatar circles. */
    public String getInitials() {
        if (fullName == null || fullName.isBlank()) {
            return "?";
        }
        String[] parts = fullName.trim().split("\\s+");
        String first = parts[0].substring(0, 1);
        return parts.length > 1 ? (first + parts[1].substring(0, 1)).toUpperCase() : first.toUpperCase();
    }

    /** Stable 0-4 bucket so avatar colours stay consistent per person. */
    public int getAvatarTone() {
        return (int) (Math.abs((long) getInitials().hashCode()) % 5);
    }

    public int getAge() {
        return dateOfBirth == null ? 0 : java.time.Period.between(dateOfBirth, java.time.LocalDate.now()).getYears();
    }

    /** Body mass index, or null when height/weight have not been recorded. */
    public java.math.BigDecimal getBmi() {
        if (heightCm == null || weightKg == null || heightCm <= 0) {
            return null;
        }
        double metres = heightCm / 100.0;
        return java.math.BigDecimal.valueOf(weightKg / (metres * metres))
                .setScale(1, java.math.RoundingMode.HALF_UP);
    }

    public User(String nic, String passwordHash, String fullName, LocalDate dateOfBirth,
                Gender gender, String address, String phoneNumber, String email, Role role) {
        this.nic = nic;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.dateOfBirth = dateOfBirth;
        this.gender = gender;
        this.address = address;
        this.phoneNumber = phoneNumber;
        this.email = email;
        this.role = role;
    }
}
