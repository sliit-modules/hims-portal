package com.medisure.hims.e2e;

import com.medisure.hims.model.*;
import com.medisure.hims.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import com.medisure.hims.service.ClaimDocumentService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end tests of the six main flows. The whole application starts (security, controllers,
 * services, Thymeleaf pages and JPA) against an in-memory database filled by the normal
 * DataSeeder, and every step goes through HTTP exactly as a browser would: signing in with the
 * real login form, sending the CSRF token, and following each role's permissions.
 *
 * Each test creates its own member and records, so the tests do not depend on each other or on
 * the randomly generated demo data.
 */
@SpringBootTest
@AutoConfigureMockMvc
class EndToEndFlowTest {

    private static final String PASSWORD = "password123";
    private static final String ADMIN = "admin@medisure.lk";
    private static final String AGENT = "agent@medisure.lk";
    private static final String OFFICER = "claims@medisure.lk";
    private static final String UNDERWRITER = "underwriting@medisure.lk";
    private static final String CRE = "support@medisure.lk";
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private InsurancePlanRepository plans;
    @Autowired private UnderwritingApplicationRepository applications;
    @Autowired private PolicyRepository policies;
    @Autowired private PaymentRepository payments;
    @Autowired private ClaimRepository claims;
    @Autowired private SupportTicketRepository tickets;
    @Autowired private NotificationRepository notifications;
    @Autowired private ClaimDocumentRepository documents;
    @Autowired private ClaimDocumentService documentService;
    @Autowired private JdbcTemplate jdbc;

    // ------------------------------------------------------------------ helpers

    /** Signs in through the real login form and returns the signed-in browser session. */
    private MockHttpSession login(String identifier) throws Exception {
        MvcResult result = mvc.perform(formLogin("/login").userParameter("identifier")
                        .user(identifier).password(PASSWORD))
                .andExpect(authenticated())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private MockHttpServletRequestBuilder postAs(MockHttpSession session, String url) {
        return post(url).session(session).with(csrf());
    }

    /** A new policyholder who registers through the public form and accepts the privacy notice. */
    private User registerMember() throws Exception {
        int n = SEQUENCE.incrementAndGet();
        String nic = "19950101" + String.format("%04d", 7000 + n);
        mvc.perform(post("/register").with(csrf())
                        .param("nic", nic)
                        .param("fullName", "Test Member " + n)
                        .param("dateOfBirth", "1995-01-01")
                        .param("gender", "FEMALE")
                        .param("bloodGroup", "O_POSITIVE")
                        .param("phoneNumber", "0771234" + String.format("%03d", n))
                        .param("address", "1 Test Lane, Colombo")
                        .param("email", "member" + n + "@example.test")
                        .param("password", PASSWORD)
                        .param("confirmPassword", PASSWORD)
                        .param("acceptPrivacy", "true"))
                .andExpect(redirectedUrl("/login?registered"));
        return users.findByNic(nic).orElseThrow();
    }

    private InsurancePlan activePlan(PlanType type) {
        return plans.findAll().stream()
                .filter(p -> p.getStatus() == PlanStatus.ACTIVE && p.getPlanType() == type)
                .findFirst().orElseThrow();
    }

    /** Member applies, underwriter approves with a loading, agent issues the policy. */
    private Policy issuedPolicyFor(User member, InsurancePlan plan, String loading) throws Exception {
        MockHttpSession memberSession = login(member.getNic());
        mvc.perform(postAs(memberSession, "/underwriting")
                        .param("planId", plan.getId().toString())
                        .param("numDependentsPlanned", "0"))
                .andExpect(status().is3xxRedirection());
        UnderwritingApplication app = applications.findAll().stream()
                .filter(a -> a.getApplicant().getId().equals(member.getId()))
                .reduce((a, b) -> b).orElseThrow();
        assertEquals(ApplicationDecision.PENDING, app.getDecision());

        mvc.perform(postAs(login(UNDERWRITER), "/underwriting/" + app.getId() + "/decide")
                        .param("decision", "APPROVED")
                        .param("riskScore", "40")
                        .param("premiumLoadingPercent", loading)
                        .param("overrideReason", "End-to-end test decision"))
                .andExpect(status().is3xxRedirection());
        assertEquals(ApplicationDecision.APPROVED, applications.findById(app.getId()).orElseThrow().getDecision());

        mvc.perform(postAs(login(AGENT), "/policies/issue/" + app.getId())
                        .param("premiumFrequency", "MONTHLY"))
                .andExpect(status().is3xxRedirection());
        return policies.findAll().stream()
                .filter(p -> p.getSourceApplication().getId().equals(app.getId()))
                .findFirst().orElseThrow();
    }

    // ------------------------------------------------------------------ 1. plans

    @Test
    @DisplayName("1. Plans: the admin publishes a plan that members can then see, but not change")
    void adminPublishesPlan() throws Exception {
        String name = "E2E Wellness Plan " + SEQUENCE.incrementAndGet();
        mvc.perform(postAs(login(ADMIN), "/plans")
                        .param("planName", name)
                        .param("description", "Plan created by the end-to-end test")
                        .param("planType", "INDIVIDUAL")
                        .param("premiumRate", "2500.00")
                        .param("coverageLimit", "400000.00"))
                .andExpect(status().is3xxRedirection());

        InsurancePlan plan = plans.findAll().stream().filter(p -> p.getPlanName().equals(name)).findFirst().orElseThrow();
        assertEquals(PlanStatus.ACTIVE, plan.getStatus());

        MockHttpSession member = login(registerMember().getNic());
        mvc.perform(get("/plans").session(member))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(name)));
        mvc.perform(postAs(member, "/plans")
                        .param("planName", "Not allowed").param("description", "x").param("planType", "INDIVIDUAL")
                        .param("premiumRate", "1").param("coverageLimit", "1"))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ 2 + 3. underwriting and policies

    @Test
    @DisplayName("2-3. Underwriting and policies: an approved application becomes a policy with the loading applied")
    void applicationBecomesPolicy() throws Exception {
        User member = registerMember();
        InsurancePlan plan = activePlan(PlanType.INDIVIDUAL);

        Policy policy = issuedPolicyFor(member, plan, "20");

        BigDecimal expected = plan.getPremiumRate().multiply(new BigDecimal("1.20")).setScale(2, java.math.RoundingMode.HALF_UP);
        assertEquals(0, expected.compareTo(policy.getPremiumAmount()), "premium = base rate + 20% loading");
        assertEquals(PolicyStatus.ACTIVE, policy.getStatus());
        assertEquals(member.getId(), policy.getPolicyholder().getId());
        assertEquals(LocalDate.now().plusMonths(1), policy.getNextDueDate());

        // The member sees their policy; another member cannot.
        mvc.perform(get("/policies/" + policy.getId()).session(login(member.getNic()))).andExpect(status().isOk());
        mvc.perform(get("/policies/" + policy.getId()).session(login(registerMember().getNic())))
                .andExpect(status().isForbidden());

        // The same application cannot be issued twice.
        mvc.perform(postAs(login(AGENT), "/policies/issue/" + policy.getSourceApplication().getId())
                        .param("premiumFrequency", "MONTHLY"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("already been issued")));
        assertEquals(1, policies.findAll().stream()
                .filter(p -> p.getSourceApplication().getId().equals(policy.getSourceApplication().getId())).count());
    }

    // ------------------------------------------------------------------ 4. payments

    @Test
    @DisplayName("4. Payments: paying moves the due date on; a refund needs staff approval")
    void premiumPaymentAndRefund() throws Exception {
        User member = registerMember();
        Policy policy = issuedPolicyFor(member, activePlan(PlanType.INDIVIDUAL), "0");
        LocalDate dueBefore = policy.getNextDueDate();
        MockHttpSession memberSession = login(member.getNic());

        mvc.perform(postAs(memberSession, "/payments")
                        .param("policyId", policy.getId().toString())
                        .param("amount", policy.getPremiumAmount().toPlainString())
                        .param("method", "CARD"))
                .andExpect(status().is3xxRedirection());

        Payment payment = payments.findAll().stream()
                .filter(p -> p.getPolicy().getId().equals(policy.getId())).findFirst().orElseThrow();
        assertEquals(PaymentStatus.PAID, payment.getStatus());
        assertEquals(dueBefore.plusMonths(1), policies.findById(policy.getId()).orElseThrow().getNextDueDate());

        // The member asks for a refund; the payment stays PAID until staff decide.
        mvc.perform(postAs(memberSession, "/payments/" + payment.getId() + "/refund-request")
                        .param("reason", "Charged twice by mistake"))
                .andExpect(status().is3xxRedirection());
        assertEquals(PaymentStatus.PAID, payments.findById(payment.getId()).orElseThrow().getStatus());

        // The member cannot approve their own refund.
        mvc.perform(postAs(memberSession, "/payments/" + payment.getId() + "/refund-decision").param("approve", "true"))
                .andExpect(status().isForbidden());

        mvc.perform(postAs(login(OFFICER), "/payments/" + payment.getId() + "/refund-decision")
                        .param("approve", "true").param("notes", "Duplicate charge confirmed"))
                .andExpect(status().is3xxRedirection());
        assertEquals(PaymentStatus.REFUNDED, payments.findById(payment.getId()).orElseThrow().getStatus());
    }

    // ------------------------------------------------------------------ 5. claims

    @Test
    @DisplayName("5. Claims: an approved claim uses up cover, an over-limit claim is refused, a duplicate is voided")
    void claimLifecycle() throws Exception {
        User member = registerMember();
        InsurancePlan plan = activePlan(PlanType.INDIVIDUAL);
        Policy policy = issuedPolicyFor(member, plan, "0");
        MockHttpSession memberSession = login(member.getNic());
        MockHttpSession officer = login(OFFICER);
        String today = LocalDate.now().toString();

        mvc.perform(postAs(memberSession, "/claims")
                        .param("policyId", policy.getId().toString())
                        .param("category", "HOSPITALIZATION")
                        .param("hospitalName", "Asiri Central")
                        .param("treatmentDate", today)
                        .param("diagnosisSummary", "Dengue fever, 3 nights")
                        .param("amountClaimed", "50000.00"))
                .andExpect(status().is3xxRedirection());
        Claim claim = claimsOn(policy).get(0);
        assertEquals(ClaimStatus.SUBMITTED, claim.getStatus());

        // Only a claims officer can decide.
        mvc.perform(postAs(memberSession, "/claims/" + claim.getId() + "/status").param("status", "APPROVED"))
                .andExpect(status().isForbidden());
        mvc.perform(postAs(officer, "/claims/" + claim.getId() + "/status")
                        .param("status", "APPROVED").param("notes", "Bills verified"))
                .andExpect(status().is3xxRedirection());
        assertEquals(ClaimStatus.APPROVED, claims.findById(claim.getId()).orElseThrow().getStatus());

        // A claim larger than the cover left is refused and nothing is saved.
        BigDecimal tooMuch = plan.getCoverageLimit();
        mvc.perform(postAs(memberSession, "/claims")
                        .param("policyId", policy.getId().toString())
                        .param("category", "SURGERY")
                        .param("hospitalName", "Nawaloka")
                        .param("treatmentDate", today)
                        .param("diagnosisSummary", "Knee surgery")
                        .param("amountClaimed", tooMuch.toPlainString()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("exceeds the remaining coverage")));
        assertEquals(1, claimsOn(policy).size());

        // The same bill filed again is flagged to the officer and voided as a duplicate.
        mvc.perform(postAs(memberSession, "/claims")
                        .param("policyId", policy.getId().toString())
                        .param("category", "HOSPITALIZATION")
                        .param("hospitalName", "Asiri Central")
                        .param("treatmentDate", today)
                        .param("diagnosisSummary", "Dengue fever, 3 nights")
                        .param("amountClaimed", "50000.00"))
                .andExpect(status().is3xxRedirection());
        Claim duplicate = claimsOn(policy).stream().filter(c -> c.getStatus() == ClaimStatus.SUBMITTED).findFirst().orElseThrow();
        mvc.perform(get("/claims/" + duplicate.getId()).session(officer))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Possible duplicate")));
        mvc.perform(postAs(officer, "/claims/" + duplicate.getId() + "/void")
                        .param("originalId", claim.getId().toString())
                        .param("reason", "Same hospital bill submitted twice"))
                .andExpect(status().is3xxRedirection());
        Claim voided = claims.findById(duplicate.getId()).orElseThrow();
        assertEquals(ClaimStatus.VOIDED, voided.getStatus());
        assertEquals(claim.getId(), voided.getDuplicateOf().getId());
    }

    private List<Claim> claimsOn(Policy policy) {
        return claims.findByPolicy(policy);
    }

    // ------------------------------------------------------------------ 6. support

    @Test
    @DisplayName("6. Support: a member's ticket about their policy is answered by Customer Relations")
    void supportTicketIsAnswered() throws Exception {
        User member = registerMember();
        Policy policy = issuedPolicyFor(member, activePlan(PlanType.INDIVIDUAL), "0");
        MockHttpSession memberSession = login(member.getNic());

        mvc.perform(postAs(memberSession, "/tickets")
                        .param("subject", "When is my next premium due?")
                        .param("message", "I would like to confirm my due date.")
                        .param("relatedPolicyId", policy.getId().toString()))
                .andExpect(status().is3xxRedirection());
        SupportTicket ticket = tickets.findAll().stream()
                .filter(t -> t.getRaisedBy().getId().equals(member.getId())).findFirst().orElseThrow();
        assertEquals(TicketStatus.OPEN, ticket.getStatus());
        assertEquals(policy.getId(), ticket.getRelatedPolicy().getId());

        // Members cannot answer tickets.
        mvc.perform(postAs(memberSession, "/tickets/" + ticket.getId() + "/respond")
                        .param("response", "x").param("status", "CLOSED"))
                .andExpect(status().isForbidden());

        mvc.perform(postAs(login(CRE), "/tickets/" + ticket.getId() + "/respond")
                        .param("response", "Your next premium is due on " + policy.getNextDueDate())
                        .param("status", "CLOSED"))
                .andExpect(status().is3xxRedirection());
        SupportTicket answered = tickets.findById(ticket.getId()).orElseThrow();
        assertEquals(TicketStatus.CLOSED, answered.getStatus());
        assertTrue(answered.getResponse().contains(policy.getNextDueDate().toString()));
        assertTrue(notifications.findAll().stream().anyMatch(n -> n.getRecipient().getId().equals(member.getId())
                && n.getTitle().contains("Reply to your ticket")), "the member is notified of the reply");
    }

    // ------------------------------------------------------------------ encryption at rest [PBI34]

    @Test
    @DisplayName("Encryption at rest: the diagnosis and the claim document are unreadable in the database and on disk, but readable in the app")
    void medicalDataIsEncryptedAtRest() throws Exception {
        User member = registerMember();
        Policy policy = issuedPolicyFor(member, activePlan(PlanType.INDIVIDUAL), "0");
        MockHttpSession memberSession = login(member.getNic());
        byte[] bill = "PNG-bytes: Asiri hospital bill, dengue ward".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        mvc.perform(multipart("/claims")
                        .file(new MockMultipartFile("documents", "bill.png", "image/png", bill))
                        .session(memberSession).with(csrf())
                        .param("policyId", policy.getId().toString())
                        .param("category", "HOSPITALIZATION")
                        .param("hospitalName", "Asiri Central")
                        .param("treatmentDate", LocalDate.now().toString())
                        .param("diagnosisSummary", "Dengue haemorrhagic fever")
                        .param("amountClaimed", "25000.00"))
                .andExpect(status().is3xxRedirection());
        Claim claim = claimsOn(policy).get(0);

        // In the database: ciphertext only.
        String stored = jdbc.queryForObject("SELECT diagnosis_summary FROM claims WHERE id = ?", String.class, claim.getId());
        assertTrue(stored.startsWith("enc:v1:"), "diagnosis is stored encrypted");
        assertFalse(stored.contains("Dengue"));

        // On disk: ciphertext only.
        ClaimDocument document = documents.findAll().stream()
                .filter(d -> d.getClaim().getId().equals(claim.getId())).findFirst().orElseThrow();
        byte[] onDisk = java.nio.file.Files.readAllBytes(documentService.pathOf(document));
        assertFalse(new String(onDisk, java.nio.charset.StandardCharsets.ISO_8859_1).contains("hospital bill"));

        // In the app: the member reads their own diagnosis and downloads the original file.
        assertEquals("Dengue haemorrhagic fever", claims.findById(claim.getId()).orElseThrow().getDiagnosisSummary());
        mvc.perform(get("/claims/" + claim.getId()).session(memberSession))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Dengue haemorrhagic fever")));
        mvc.perform(get("/claims/" + claim.getId() + "/documents/" + document.getId()).session(memberSession))
                .andExpect(status().isOk())
                .andExpect(content().bytes(bill));
    }

    // ------------------------------------------------------------------ security

    @Test
    @DisplayName("Security: each role is kept out of the pages it should not use")
    void rolesAreKeptToTheirPages() throws Exception {
        mvc.perform(get("/dashboard")).andExpect(redirectedUrlPattern("**/login"));

        MockHttpSession member = login(registerMember().getNic());
        for (String page : List.of("/audit", "/reports", "/users", "/plans/new", "/underwriting/1/decide")) {
            mvc.perform(get(page).session(member)).andExpect(status().isForbidden());
        }
        MockHttpSession cre = login(CRE);
        for (String page : List.of("/audit", "/reports", "/payments", "/underwriting")) {
            mvc.perform(get(page).session(cre)).andExpect(status().isForbidden());
        }
        mvc.perform(get("/reports").session(login(ADMIN))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Security: a form posted without its CSRF token is rejected")
    void formsNeedCsrfToken() throws Exception {
        mvc.perform(post("/tickets").session(login(registerMember().getNic()))
                        .param("subject", "Forged").param("message", "No token"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Security: five wrong passwords lock the account, even against the right password")
    void wrongPasswordsLockTheAccount() throws Exception {
        User member = registerMember();
        for (int i = 0; i < 5; i++) {
            mvc.perform(formLogin("/login").userParameter("identifier").user(member.getNic()).password("wrong-" + i))
                    .andExpect(unauthenticated());
        }
        mvc.perform(formLogin("/login").userParameter("identifier").user(member.getNic()).password(PASSWORD))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?locked"));
        assertNotNull(users.findById(member.getId()).orElseThrow().getLockedUntil());
    }
}
