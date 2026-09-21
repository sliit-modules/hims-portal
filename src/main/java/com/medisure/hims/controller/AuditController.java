package com.medisure.hims.controller;

import com.medisure.hims.service.AuditService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/audit")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    /** Two views of the log: state-changing actions (default) and who opened sensitive records. */
    @GetMapping
    public String list(@RequestParam(defaultValue = "changes") String show, Model model) {
        boolean access = "access".equals(show);
        model.addAttribute("logs", access ? auditService.recordAccess() : auditService.changes());
        model.addAttribute("showAccess", access);
        return "audit/list";
    }
}
