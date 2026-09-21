package com.medisure.hims.service;

import com.medisure.hims.model.PrivacyNotice;
import com.medisure.hims.model.Role;
import com.medisure.hims.model.User;
import com.medisure.hims.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public static final int MIN_PASSWORD_LENGTH = 8;

    /** Letters and digits that cannot be confused when read aloud or copied (no 0/O, 1/l/I). */
    private static final String TEMP_PASSWORD_ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final int TEMP_PASSWORD_LENGTH = 10;
    private final SecureRandom random = new SecureRandom();

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    public User registerPolicyholder(User user, String rawPassword, boolean acceptedPrivacyNotice) {
        if (!acceptedPrivacyNotice) {
            throw new IllegalArgumentException("Please read and accept the Privacy Notice to create an account");
        }
        if (userRepository.existsByNic(user.getNic())) {
            throw new IllegalArgumentException("An account with this NIC already exists");
        }
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new IllegalArgumentException("An account with this email already exists");
        }
        user.setId(null);   // always a new account: a stray id must never point the save at an existing one
        user.setRole(Role.POLICYHOLDER);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setPrivacyConsentAt(LocalDateTime.now());
        user.setPrivacyNoticeVersion(PrivacyNotice.VERSION);
        User saved = userRepository.save(user);
        auditService.log("User", saved.getId(), "REGISTERED", saved,
                "Self-registered as policyholder; accepted privacy notice " + PrivacyNotice.VERSION);
        return saved;
    }

    /** Records that an existing member accepted the current privacy notice. */
    public User recordPrivacyConsent(Long userId, User actor) {
        User user = findById(userId);
        user.setPrivacyConsentAt(LocalDateTime.now());
        user.setPrivacyNoticeVersion(PrivacyNotice.VERSION);
        User saved = userRepository.save(user);
        auditService.log("User", saved.getId(), "PRIVACY_CONSENT", actor,
                "Accepted privacy notice " + PrivacyNotice.VERSION);
        return saved;
    }

    public User createStaffUser(User user, String rawPassword, Role role) {
        if (userRepository.existsByNic(user.getNic())) {
            throw new IllegalArgumentException("An account with this NIC already exists");
        }
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new IllegalArgumentException("An account with this email already exists");
        }
        user.setId(null);   // always a new account, never an overwrite
        user.setRole(role);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        User saved = userRepository.save(user);
        auditService.log("User", saved.getId(), "CREATED", saved, "Staff account created: " + role);
        return saved;
    }

    public User findByNic(String nic) {
        return userRepository.findByNic(nic)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "User not found"));
    }

    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "User not found"));
    }

    public List<User> findByRole(Role role) {
        return userRepository.findByRole(role);
    }

    public List<User> findAll() {
        return userRepository.findAll();
    }

    /**
     * Updates the editable parts of a member's own profile. NIC, role and password are
     * deliberately not touched here: NIC is the login identity, role is an admin decision,
     * and passwords go through the encoder on their own path.
     */
    public User updateProfile(Long userId, User changes, User actor) {
        User user = findById(userId);

        if (changes.getEmail() != null && !changes.getEmail().equalsIgnoreCase(user.getEmail())
                && userRepository.existsByEmail(changes.getEmail())) {
            throw new IllegalArgumentException("Another account already uses this email");
        }

        user.setFullName(changes.getFullName());
        user.setDateOfBirth(changes.getDateOfBirth());
        user.setGender(changes.getGender());
        user.setAddress(changes.getAddress());
        user.setPhoneNumber(changes.getPhoneNumber());
        user.setEmail(changes.getEmail());

        user.setBloodGroup(changes.getBloodGroup());
        user.setMaritalStatus(changes.getMaritalStatus());
        user.setOccupation(changes.getOccupation());
        user.setHeightCm(changes.getHeightCm());
        user.setWeightKg(changes.getWeightKg());
        user.setAllergies(changes.getAllergies());
        user.setChronicConditions(changes.getChronicConditions());
        user.setCurrentMedications(changes.getCurrentMedications());
        user.setEmergencyContactName(changes.getEmergencyContactName());
        user.setEmergencyContactPhone(changes.getEmergencyContactPhone());
        user.setEmergencyContactRelation(changes.getEmergencyContactRelation());

        User saved = userRepository.save(user);
        auditService.log("User", saved.getId(), "PROFILE_UPDATED", actor, null);
        return saved;
    }

    /**
     * An administrator resets a password: a random temporary password is issued and returned so it
     * can be handed to the user once. It is stored only as a hash and never written to the audit log.
     * The user must choose their own password when they next sign in.
     */
    public String resetPassword(Long userId, User actor) {
        User user = findById(userId);
        StringBuilder temporary = new StringBuilder(TEMP_PASSWORD_LENGTH);
        for (int i = 0; i < TEMP_PASSWORD_LENGTH; i++) {
            temporary.append(TEMP_PASSWORD_ALPHABET.charAt(random.nextInt(TEMP_PASSWORD_ALPHABET.length())));
        }
        user.setPasswordHash(passwordEncoder.encode(temporary.toString()));
        user.setPasswordChangeRequired(true);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);
        auditService.log("User", user.getId(), "PASSWORD_RESET", actor,
                "Temporary password issued; a new password is required at next sign-in");
        return temporary.toString();
    }

    /** An administrator releases a locked account before its lock runs out. */
    public void unlock(Long userId, User actor) {
        User user = findById(userId);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);
        auditService.log("User", user.getId(), "UNLOCKED", actor, null);
    }

    /** A signed-in user changes their own password; the current one must be given first. */
    public User changePassword(Long userId, String currentPassword, String newPassword, String confirmPassword,
                               User actor) {
        User user = findById(userId);
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Your current password is incorrect");
        }
        if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException(
                    "Your new password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        if (!newPassword.equals(confirmPassword)) {
            throw new IllegalArgumentException("The new passwords do not match");
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Choose a password different from your current one");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordChangeRequired(false);
        User saved = userRepository.save(user);
        auditService.log("User", saved.getId(), "PASSWORD_CHANGED", actor, null);
        return saved;
    }

    /** The audit entry names the administrator who acted, not the account that was changed. */
    public void setEnabled(Long id, boolean enabled, User actor) {
        User user = findById(id);
        user.setEnabled(enabled);
        userRepository.save(user);
        auditService.log("User", user.getId(), enabled ? "ENABLED" : "DISABLED", actor, null);
    }
}
