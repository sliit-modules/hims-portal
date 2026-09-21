package com.medisure.hims.controller;

import com.medisure.hims.config.UserPrincipal;
import com.medisure.hims.service.AuditService;
import com.medisure.hims.service.ReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/** Management reports for the System Administrator: on screen, as CSV, or as a printable page. */
@Controller
@RequestMapping("/reports")
public class ReportController {

    private final ReportService reportService;
    private final AuditService auditService;

    public ReportController(ReportService reportService, AuditService auditService) {
        this.reportService = reportService;
        this.auditService = auditService;
    }

    @GetMapping
    public String view(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                       Model model) {
        model.addAttribute("report", build(from, to, model));
        return "reports/index";
    }

    @GetMapping("/print")
    public String print(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                        @AuthenticationPrincipal UserPrincipal principal, Model model) {
        ReportService.Report report = build(from, to, model);
        auditExport(principal, "PDF", report);
        model.addAttribute("report", report);
        return "reports/print";
    }

    @GetMapping("/export.csv")
    public ResponseEntity<byte[]> csv(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                      @AuthenticationPrincipal UserPrincipal principal) {
        ReportService.Report report = build(from, to, null);
        auditExport(principal, "CSV", report);
        // The byte-order mark makes Excel read the file as UTF-8.
        byte[] body = ("﻿" + reportService.toCsv(report)).getBytes(StandardCharsets.UTF_8);
        String name = "medisure-report-" + report.from() + "-to-" + report.to() + ".csv";
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
                .body(body);
    }

    /** The chosen period, or this year so far when no (or an invalid) period is given. */
    private ReportService.Report build(LocalDate from, LocalDate to, Model model) {
        LocalDate today = LocalDate.now();
        LocalDate start = from != null ? from : today.withDayOfYear(1);
        LocalDate end = to != null ? to : today;
        try {
            return reportService.build(start, end);
        } catch (IllegalArgumentException ex) {
            if (model != null) {
                model.addAttribute("errorMessage", ex.getMessage() + ". Showing this year so far instead.");
            }
            return reportService.build(today.withDayOfYear(1), today);
        }
    }

    private void auditExport(UserPrincipal principal, String format, ReportService.Report report) {
        auditService.log("Report", 0L, "EXPORTED", principal.getUser(),
                format + " management report for " + report.from() + " to " + report.to());
    }
}
