package com.medisure.hims.controller;

import com.medisure.hims.config.UserPrincipal;
import com.medisure.hims.model.*;
import com.medisure.hims.service.ClaimDocumentService;
import com.medisure.hims.service.ClaimService;
import com.medisure.hims.service.PolicyService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;

@Controller
@RequestMapping("/claims")
public class ClaimController {

    private final ClaimService claimService;
    private final PolicyService policyService;
    private final ClaimDocumentService documentService;

    public ClaimController(ClaimService claimService, PolicyService policyService,
                            ClaimDocumentService documentService) {
        this.claimService = claimService;
        this.policyService = policyService;
        this.documentService = documentService;
    }

    @GetMapping
    public String list(@AuthenticationPrincipal UserPrincipal principal, Model model) {
        model.addAttribute("claims", claimService.findAllFor(principal.getUser()));
        model.addAttribute("docCounts", documentService.documentCounts());
        return "claims/list";
    }

    @GetMapping("/new")
    public String newForm(@RequestParam Long policyId, @AuthenticationPrincipal UserPrincipal principal,
                           Model model) {
        Policy policy = policyService.findById(policyId);
        policyService.assertVisible(policy, principal.getUser());
        model.addAttribute("policy", policy);
        model.addAttribute("remainingCoverage", claimService.remainingCoverage(policy));
        return "claims/form";
    }

    @PostMapping
    public String submit(@RequestParam Long policyId, @RequestParam(required = false) Long claimantDependentId,
                          @RequestParam ClaimCategory category,
                          @RequestParam String hospitalName, @RequestParam LocalDate treatmentDate,
                          @RequestParam String diagnosisSummary, @RequestParam BigDecimal amountClaimed,
                          @RequestParam(required = false) MultipartFile[] documents,
                          @AuthenticationPrincipal UserPrincipal principal, Model model) {
        Policy policy = policyService.findById(policyId);
        policyService.assertVisible(policy, principal.getUser());
        Dependent dependent = null;
        if (claimantDependentId != null) {
            dependent = policy.getDependents().stream()
                    .filter(d -> d.getId().equals(claimantDependentId)).findFirst().orElse(null);
        }
        try {
            Claim claim = claimService.submit(policy, policy.getPolicyholder(), dependent, category, hospitalName,
                    treatmentDate, diagnosisSummary, amountClaimed, principal.getUser());
            documentService.storeAll(claim, documents, principal.getUser());
            return "redirect:/claims/" + claim.getId();
        } catch (IllegalStateException ex) {
            model.addAttribute("policy", policy);
            model.addAttribute("remainingCoverage", claimService.remainingCoverage(policy));
            model.addAttribute("errorMessage", ex.getMessage());
            return "claims/form";
        }
    }

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal, Model model) {
        Claim claim = claimService.findById(id);
        claimService.assertVisible(claim, principal.getUser());
        model.addAttribute("claim", claim);
        model.addAttribute("documents", documentService.findForClaim(claim));
        return "claims/view";
    }

    /** Adds further supporting documents to a claim that has already been filed. */
    @PostMapping("/{id}/documents")
    public String addDocuments(@PathVariable Long id, @RequestParam MultipartFile[] documents,
                                @AuthenticationPrincipal UserPrincipal principal, Model model) {
        Claim claim = claimService.findById(id);
        claimService.assertVisible(claim, principal.getUser());
        try {
            documentService.storeAll(claim, documents, principal.getUser());
        } catch (IllegalStateException ex) {
            model.addAttribute("claim", claim);
            model.addAttribute("documents", documentService.findForClaim(claim));
            model.addAttribute("errorMessage", ex.getMessage());
            return "claims/view";
        }
        return "redirect:/claims/" + id;
    }

    /**
     * Streams a supporting document. Access is gated by the same rule as the claim itself, so a
     * policyholder can only ever reach their own files.
     */
    @GetMapping("/{id}/documents/{documentId}")
    public ResponseEntity<Resource> download(@PathVariable Long id, @PathVariable Long documentId,
                                              @RequestParam(defaultValue = "false") boolean download,
                                              @AuthenticationPrincipal UserPrincipal principal) {
        Claim claim = claimService.findById(id);
        claimService.assertVisible(claim, principal.getUser());

        ClaimDocument document = documentService.findById(documentId);
        if (!document.getClaim().getId().equals(claim.getId())) {
            throw new AccessDeniedException("That document does not belong to this claim");
        }

        Resource resource = documentService.loadAsResource(document);
        String disposition = (download ? "attachment" : "inline")
                + "; filename=\"" + document.getOriginalFileName().replace("\"", "") + "\"";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(document.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .body(resource);
    }

    @PostMapping("/{id}/documents/{documentId}/delete")
    public String deleteDocument(@PathVariable Long id, @PathVariable Long documentId,
                                  @AuthenticationPrincipal UserPrincipal principal) {
        Claim claim = claimService.findById(id);
        claimService.assertVisible(claim, principal.getUser());
        ClaimDocument document = documentService.findById(documentId);
        if (!document.getClaim().getId().equals(claim.getId())) {
            throw new AccessDeniedException("That document does not belong to this claim");
        }
        if (claim.getStatus() != ClaimStatus.SUBMITTED) {
            throw new IllegalStateException("Documents can only be removed while the claim is still pending");
        }
        documentService.delete(document, principal.getUser());
        return "redirect:/claims/" + id;
    }

    @PostMapping("/{id}/status")
    public String decide(@PathVariable Long id, @RequestParam ClaimStatus status,
                          @RequestParam(required = false) String notes,
                          @AuthenticationPrincipal UserPrincipal principal, Model model) {
        try {
            claimService.decide(id, status, notes, principal.getUser());
        } catch (IllegalStateException ex) {
            Claim claim = claimService.findById(id);
            model.addAttribute("claim", claim);
            model.addAttribute("documents", documentService.findForClaim(claim));
            model.addAttribute("errorMessage", ex.getMessage());
            return "claims/view";
        }
        return "redirect:/claims/" + id;
    }

    @PostMapping("/{id}/withdraw")
    public String withdraw(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        claimService.withdraw(id, principal.getUser());
        return "redirect:/claims/" + id;
    }
}
