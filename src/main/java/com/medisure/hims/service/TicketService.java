package com.medisure.hims.service;

import com.medisure.hims.model.*;
import com.medisure.hims.repository.SupportTicketRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class TicketService {

    private final SupportTicketRepository ticketRepository;
    private final AuditService auditService;

    public TicketService(SupportTicketRepository ticketRepository, AuditService auditService) {
        this.ticketRepository = ticketRepository;
        this.auditService = auditService;
    }

    public List<SupportTicket> findAllFor(User currentUser) {
        if (currentUser.getRole() == Role.CRE || currentUser.getRole() == Role.ADMIN) {
            return ticketRepository.findAll();
        }
        return ticketRepository.findByRaisedBy(currentUser);
    }

    public SupportTicket findById(Long id) {
        return ticketRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Ticket not found"));
    }

    public void assertVisible(SupportTicket ticket, User currentUser) {
        boolean staff = currentUser.getRole() == Role.CRE || currentUser.getRole() == Role.ADMIN;
        if (!staff && !ticket.getRaisedBy().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("You may not view this ticket");
        }
    }

    public SupportTicket create(User raisedBy, Policy relatedPolicy, Claim relatedClaim,
                                 String subject, String message) {
        SupportTicket ticket = new SupportTicket();
        ticket.setRaisedBy(raisedBy);
        ticket.setRelatedPolicy(relatedPolicy);
        ticket.setRelatedClaim(relatedClaim);
        ticket.setSubject(subject);
        ticket.setMessage(message);
        ticket.setStatus(TicketStatus.OPEN);
        ticket.setCreatedAt(LocalDateTime.now());
        SupportTicket saved = ticketRepository.save(ticket);
        auditService.log("SupportTicket", saved.getId(), "LOGGED", raisedBy, subject);
        return saved;
    }

    public SupportTicket respond(Long id, String response, TicketStatus status, User actor) {
        if (actor.getRole() != Role.CRE && actor.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Only Customer Relations can respond to tickets");
        }
        SupportTicket ticket = findById(id);
        ticket.setResponse(response);
        ticket.setStatus(status);
        ticket.setUpdatedAt(LocalDateTime.now());
        SupportTicket saved = ticketRepository.save(ticket);
        auditService.log("SupportTicket", saved.getId(), "RESPONDED:" + status, actor, response);
        return saved;
    }

    public void close(Long id, User actor) {
        if (actor.getRole() != Role.CRE && actor.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Only Customer Relations can close tickets");
        }
        SupportTicket ticket = findById(id);
        ticket.setStatus(TicketStatus.CLOSED);
        ticket.setUpdatedAt(LocalDateTime.now());
        ticketRepository.save(ticket);
        auditService.log("SupportTicket", ticket.getId(), "CLOSED", actor, null);
    }
}
