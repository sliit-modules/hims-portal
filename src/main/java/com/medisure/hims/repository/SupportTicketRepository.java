package com.medisure.hims.repository;

import com.medisure.hims.model.SupportTicket;
import com.medisure.hims.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {
    List<SupportTicket> findByRaisedBy(User raisedBy);
}
