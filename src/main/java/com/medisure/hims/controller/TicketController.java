package com.medisure.hims.controller;

import com.medisure.hims.config.UserPrincipal;
import com.medisure.hims.model.*;
import com.medisure.hims.service.ClaimService;
import com.medisure.hims.service.PolicyService;
import com.medisure.hims.service.TicketService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/tickets")
public class TicketController {

    private final TicketService ticketService;
    private final PolicyService policyService;
    private final ClaimService claimService;

    public TicketController(TicketService ticketService, PolicyService policyService, ClaimService claimService) {
        this.ticketService = ticketService;
        this.policyService = policyService;
        this.claimService = claimService;
    }

    @GetMapping
    public String list(@AuthenticationPrincipal UserPrincipal principal, Model model) {
        model.addAttribute("tickets", ticketService.findAllFor(principal.getUser()));
        return "tickets/list";
    }

    @GetMapping("/new")
    public String newForm(@AuthenticationPrincipal UserPrincipal principal, Model model) {
        model.addAttribute("policies", policyService.findAllFor(principal.getUser()));
        model.addAttribute("claims", claimService.findAllFor(principal.getUser()));
        return "tickets/form";
    }

    @PostMapping
    public String create(@RequestParam String subject, @RequestParam String message,
                          @RequestParam(required = false) Long relatedPolicyId,
                          @RequestParam(required = false) Long relatedClaimId,
                          @AuthenticationPrincipal UserPrincipal principal) {
        Policy policy = relatedPolicyId != null ? policyService.findById(relatedPolicyId) : null;
        Claim claim = relatedClaimId != null ? claimService.findById(relatedClaimId) : null;
        SupportTicket ticket = ticketService.create(principal.getUser(), policy, claim, subject, message);
        return "redirect:/tickets/" + ticket.getId();
    }

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal, Model model) {
        SupportTicket ticket = ticketService.findById(id);
        ticketService.assertVisible(ticket, principal.getUser());
        model.addAttribute("ticket", ticket);
        return "tickets/view";
    }

    @PostMapping("/{id}/respond")
    public String respond(@PathVariable Long id, @RequestParam String response,
                           @RequestParam TicketStatus status, @AuthenticationPrincipal UserPrincipal principal) {
        ticketService.respond(id, response, status, principal.getUser());
        return "redirect:/tickets/" + id;
    }

    @PostMapping("/{id}/close")
    public String close(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        ticketService.close(id, principal.getUser());
        return "redirect:/tickets/" + id;
    }
}
