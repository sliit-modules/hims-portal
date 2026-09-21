package com.medisure.hims.repository;

import com.medisure.hims.model.PayHereCheckout;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PayHereCheckoutRepository extends JpaRepository<PayHereCheckout, Long> {
    Optional<PayHereCheckout> findByOrderId(String orderId);
}
