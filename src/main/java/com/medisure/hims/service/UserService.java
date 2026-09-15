package com.medisure.hims.service;

import com.medisure.hims.model.PrivacyNotice;
import com.medisure.hims.model.Role;
import com.medisure.hims.model.User;
import com.medisure.hims.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

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

    public void setEnabled(Long id, boolean enabled) {
        User user = findById(id);
        user.setEnabled(enabled);
        userRepository.save(user);
        auditService.log("User", user.getId(), enabled ? "ENABLED" : "DISABLED", user, null);
    }
}
