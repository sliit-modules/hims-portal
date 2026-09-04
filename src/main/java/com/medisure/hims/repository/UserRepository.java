package com.medisure.hims.repository;

import com.medisure.hims.model.Role;
import com.medisure.hims.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByNic(String nic);
    Optional<User> findByEmail(String email);
    boolean existsByNic(String nic);
    boolean existsByEmail(String email);
    List<User> findByRole(Role role);
}
