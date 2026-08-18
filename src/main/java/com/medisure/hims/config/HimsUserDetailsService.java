package com.medisure.hims.config;

import com.medisure.hims.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class HimsUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public HimsUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Policyholders log in with their NIC; every other role logs in with their work email.
     * The login form has a single "identifier" field, so we try NIC first, then email.
     */
    @Override
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        return userRepository.findByNic(identifier)
                .or(() -> userRepository.findByEmail(identifier))
                .map(UserPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException("No account found for " + identifier));
    }
}
