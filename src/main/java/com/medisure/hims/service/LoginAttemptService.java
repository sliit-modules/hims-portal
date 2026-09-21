package com.medisure.hims.service;

import com.medisure.hims.model.User;
import com.medisure.hims.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Counts wrong passwords and locks an account for a while after too many in a row, which stops
 * someone guessing a member's password by trying again and again.
 */
@Service
public class LoginAttemptService {

    public static final int MAX_FAILED_ATTEMPTS = 5;
    public static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final UserRepository userRepository;
    private final AuditService auditService;

    public LoginAttemptService(UserRepository userRepository, AuditService auditService) {
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    /**
     * Records a wrong password for the NIC or email typed on the sign-in form.
     *
     * @return true when the account is now locked (including when this attempt locked it)
     */
    public boolean recordFailure(String identifier) {
        Optional<User> found = find(identifier);
        if (found.isEmpty()) {
            return false;   // no such account: nothing to count
        }
        User user = found.get();
        if (user.isLocked()) {
            return true;
        }
        int attempts = (user.getFailedLoginAttempts() == null ? 0 : user.getFailedLoginAttempts()) + 1;
        if (attempts >= MAX_FAILED_ATTEMPTS) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(LocalDateTime.now().plus(LOCK_DURATION));
            userRepository.save(user);
            auditService.log("User", user.getId(), "LOCKED", null,
                    MAX_FAILED_ATTEMPTS + " wrong passwords in a row; locked for "
                            + LOCK_DURATION.toMinutes() + " minutes");
            return true;
        }
        user.setFailedLoginAttempts(attempts);
        userRepository.save(user);
        return false;
    }

    /** A successful sign-in clears the count of wrong passwords. */
    public void recordSuccess(User signedIn) {
        boolean hadFailures = signedIn.getFailedLoginAttempts() != null && signedIn.getFailedLoginAttempts() > 0;
        if (!hadFailures && signedIn.getLockedUntil() == null) {
            return;
        }
        userRepository.findById(signedIn.getId()).ifPresent(user -> {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            userRepository.save(user);
        });
        signedIn.setFailedLoginAttempts(0);
        signedIn.setLockedUntil(null);
    }

    private Optional<User> find(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return Optional.empty();
        }
        String id = identifier.trim();
        return userRepository.findByNic(id).or(() -> userRepository.findByEmail(id));
    }
}
