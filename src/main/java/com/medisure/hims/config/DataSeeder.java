package com.medisure.hims.config;

import com.medisure.hims.model.*;
import com.medisure.hims.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Seeds a full year of realistic demo data: staff accounts, policyholders, plans, the
 * application -> policy -> payments -> claims -> tickets chain, and a populated audit trail.
 * Written straight through the repositories so historical dates can be back-dated, which the
 * service layer (which always stamps "now") cannot do.
 *
 * Default password for every seeded account is "password123".
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final String DEFAULT_PASSWORD = "password123";

    private final UserRepository userRepository;
    private final InsurancePlanRepository planRepository;
    private final UnderwritingApplicationRepository applicationRepository;
    private final PolicyRepository policyRepository;
    private final DependentRepository dependentRepository;
    private final ClaimRepository claimRepository;
    private final PaymentRepository paymentRepository;
    private final SupportTicketRepository ticketRepository;
    private final AuditLogRepository auditLogRepository;
    private final ClaimDocumentRepository claimDocumentRepository;
    private final PasswordEncoder passwordEncoder;
    private final java.nio.file.Path uploadRoot;

    private final Random random = new Random(20260914L);
    /** Per-prefix counters so each year's codes run densely from 000001. */
    private final java.util.Map<String, Integer> sequences = new java.util.HashMap<>();

    private String nextCode(String kind, int year) {
        String prefix = kind + "-" + year + "-";
        return prefix + "%06d".formatted(sequences.merge(prefix, 1, Integer::sum));
    }

    public DataSeeder(UserRepository userRepository, InsurancePlanRepository planRepository,
                       UnderwritingApplicationRepository applicationRepository, PolicyRepository policyRepository,
                       DependentRepository dependentRepository, ClaimRepository claimRepository,
                       PaymentRepository paymentRepository, SupportTicketRepository ticketRepository,
                       AuditLogRepository auditLogRepository, ClaimDocumentRepository claimDocumentRepository,
                       PasswordEncoder passwordEncoder,
                       @org.springframework.beans.factory.annotation.Value("${app.upload-dir:uploads}") String uploadDir) {
        this.claimDocumentRepository = claimDocumentRepository;
        this.uploadRoot = java.nio.file.Paths.get(uploadDir).toAbsolutePath().normalize();
        this.userRepository = userRepository;
        this.planRepository = planRepository;
        this.applicationRepository = applicationRepository;
        this.policyRepository = policyRepository;
        this.dependentRepository = dependentRepository;
        this.claimRepository = claimRepository;
        this.paymentRepository = paymentRepository;
        this.ticketRepository = ticketRepository;
        this.auditLogRepository = auditLogRepository;
        this.passwordEncoder = passwordEncoder;
    }

    private static final String[] HOSPITALS = {
            "Asiri Central Hospital", "Nawaloka Hospital", "Lanka Hospitals", "Durdans Hospital",
            "Hemas Hospital Wattala", "Ninewells Hospital", "Asiri Surgical Hospital"
    };

    private static final ClaimCategory[] CATEGORIES = {
            ClaimCategory.ANNUAL_CHECKUP, ClaimCategory.DENTAL, ClaimCategory.HOSPITALIZATION,
            ClaimCategory.SURGERY, ClaimCategory.MATERNITY, ClaimCategory.CANCER_SCREENING,
            ClaimCategory.EMERGENCY, ClaimCategory.OTHER
    };

    private static final BloodGroup[] BLOOD_GROUPS = {
            BloodGroup.O_POSITIVE, BloodGroup.A_POSITIVE, BloodGroup.B_POSITIVE, BloodGroup.AB_POSITIVE,
            BloodGroup.O_NEGATIVE, BloodGroup.A_NEGATIVE, BloodGroup.B_NEGATIVE
    };

    private static final String[] OCCUPATIONS = {
            "Software Engineer", "Teacher", "Accountant", "Nurse", "Bank Officer", "Civil Engineer",
            "Business Owner", "Marketing Executive", "Government Officer", "Architect"
    };

    private static final String[] ALLERGIES = {
            "Penicillin", "Peanuts", "Dust mites", "Seafood", "Pollen", "Latex"
    };

    private static final String[] CONDITIONS = {
            "Type 2 diabetes, managed with medication", "Hypertension", "Asthma",
            "High cholesterol", "Hypothyroidism"
    };

    private static final String[] MEDICATIONS = {
            "Metformin 500mg twice daily", "Losartan 50mg daily", "Salbutamol inhaler as needed",
            "Atorvastatin 20mg nightly", "Levothyroxine 75mcg daily"
    };

    private static final String[] EMERGENCY_NAMES = {
            "Sunil Perera", "Chamari Silva", "Anoma Fernando", "Ranjith Bandara", "Nilanthi Jayasuriya"
    };

    private static final String[] EMERGENCY_RELATIONS = {
            "Spouse", "Parent", "Sibling", "Child", "Friend"
    };

    private static final String[] DIAGNOSES = {
            "Routine annual health screening", "Root canal treatment and crown fitting",
            "Inpatient treatment for acute bronchitis", "Arthroscopic knee surgery",
            "Maternity admission and delivery", "Routine cancer screening panel",
            "Emergency admission after road accident", "Physiotherapy course following injury",
            "Cardiac evaluation and stress test", "Cataract removal procedure"
    };

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.count() > 0) {
            return;
        }

        // ---------- Staff ----------
        User admin = staff("199001012345", "S. A. Fernando", LocalDate.of(1990, 1, 1), Gender.FEMALE,
                "12 Galle Road, Colombo 03", "0771234567", "admin@medisure.lk", Role.ADMIN);
        User salesAgent = staff("199203023456", "Lankadhikara L.R.M.M.P.", LocalDate.of(1992, 3, 2), Gender.FEMALE,
                "45 Kandy Road, Kadawatha", "0772345678", "agent@medisure.lk", Role.SALES_AGENT);
        User claimsOfficer = staff("198804034567", "Gunasinghe N.M.", LocalDate.of(1988, 4, 3), Gender.MALE,
                "9 Negombo Road, Wattala", "0773456789", "claims@medisure.lk", Role.CLAIMS_OFFICER);
        User underwriter = staff("197505045678", "De Zoysa A.I.", LocalDate.of(1975, 5, 4), Gender.MALE,
                "78 High Level Road, Nugegoda", "0774567890", "underwriting@medisure.lk", Role.UNDERWRITER);
        User cre = staff("199606056789", "Kavisekara K.M.H.N.", LocalDate.of(1996, 6, 5), Gender.FEMALE,
                "22 Baseline Road, Colombo 09", "0775678901", "support@medisure.lk", Role.CRE);
        staff("198207078765", "Karunarathna W.M.K.U.", LocalDate.of(1982, 7, 7), Gender.MALE,
                "31 Havelock Road, Colombo 05", "0776789012", "plans@medisure.lk", Role.ADMIN);
        // Premium & Payment Management (Ramanayaka U.K.D.) has the Policyholder as its owner
        // persona: making a payment, viewing receipts, changing auto-pay and cancelling a
        // payment are all member self-service actions. He is therefore seeded below as a
        // policyholder with his own cover, so his account can exercise the full CRUD set.

        // ---------- Plans ----------
        InsurancePlan familyShield = plan("MediSure Family Shield",
                "Comprehensive family floater cover for the policyholder and up to 4 dependents, including hospitalisation, surgery and outpatient care.",
                PlanType.FAMILY, "8500", "2000000");
        InsurancePlan individualBasic = plan("MediSure Individual Basic",
                "Essential individual health cover for a single adult, covering hospitalisation and day-care procedures.",
                PlanType.INDIVIDUAL, "3200", "750000");
        InsurancePlan seniorCare = plan("MediSure Senior Care",
                "Tailored cover for members above 55, with higher limits for chronic illness management and specialist consultations.",
                PlanType.INDIVIDUAL, "12000", "3000000");
        InsurancePlan criticalIllness = plan("MediSure Critical Illness Plus",
                "High-limit protection against critical illness including cancer, cardiac and renal treatment pathways.",
                PlanType.INDIVIDUAL, "6800", "5000000");
        InsurancePlan familyPremium = plan("MediSure Family Premium",
                "Our highest family floater tier with international treatment options and maternity cover included.",
                PlanType.FAMILY, "15500", "8000000");
        InsurancePlan[] plans = {familyShield, individualBasic, seniorCare, criticalIllness, familyPremium};

        // ---------- Policyholders ----------
        record Person(String nic, String name, LocalDate dob, Gender gender, String address, String phone, String email) {}
        List<Person> people = List.of(
                new Person("199408089876", "Ramanayaka U.K.D.", LocalDate.of(1994, 8, 8), Gender.MALE, "5 Union Place, Colombo 02", "0777890123", "payments@medisure.lk"),
                new Person("199009098765", "Kasun Perera", LocalDate.of(1990, 9, 9), Gender.MALE, "14 Lake Drive, Rajagiriya", "0778901234", "kasun.perera@example.com"),
                new Person("199211112345", "Nadeesha Silva", LocalDate.of(1992, 11, 11), Gender.FEMALE, "8 Temple Road, Maharagama", "0712345671", "nadeesha.silva@example.com"),
                new Person("198805123456", "Dilshan Fernando", LocalDate.of(1988, 5, 12), Gender.MALE, "102 Main Street, Moratuwa", "0712345672", "dilshan.fernando@example.com"),
                new Person("199507234567", "Amaya Jayawardena", LocalDate.of(1995, 7, 23), Gender.FEMALE, "27 Flower Road, Colombo 07", "0712345673", "amaya.j@example.com"),
                new Person("198203345678", "Ruwan Bandara", LocalDate.of(1982, 3, 15), Gender.MALE, "63 Peradeniya Road, Kandy", "0712345674", "ruwan.bandara@example.com"),
                new Person("199809456789", "Tharushi Wickramasinghe", LocalDate.of(1998, 9, 16), Gender.FEMALE, "19 Beach Road, Negombo", "0712345675", "tharushi.w@example.com"),
                new Person("196711567890", "Chamara Rajapaksa", LocalDate.of(1967, 11, 17), Gender.MALE, "5 Hill Street, Nuwara Eliya", "0712345676", "chamara.r@example.com"),
                new Person("199403678901", "Ishara Gunawardena", LocalDate.of(1994, 3, 18), Gender.FEMALE, "41 Station Road, Panadura", "0712345677", "ishara.g@example.com"),
                new Person("200101789012", "Sanduni Ratnayake", LocalDate.of(2001, 1, 19), Gender.FEMALE, "77 Lotus Avenue, Gampaha", "0712345678", "sanduni.r@example.com"),
                new Person("196607890123", "Nuwan Dissanayake", LocalDate.of(1966, 7, 20), Gender.MALE, "3 Park Lane, Colombo 05", "0712345679", "nuwan.d@example.com"),
                new Person("199612901234", "Piyumi Herath", LocalDate.of(1996, 12, 21), Gender.FEMALE, "56 Galle Road, Dehiwala", "0712345680", "piyumi.h@example.com"),
                new Person("199008012345", "Lahiru Weerasinghe", LocalDate.of(1990, 8, 22), Gender.MALE, "12 Sea View, Mount Lavinia", "0712345681", "lahiru.w@example.com"),
                new Person("199304123457", "Dilini Abeysekara", LocalDate.of(1993, 4, 23), Gender.FEMALE, "88 Kotte Road, Nugegoda", "0712345682", "dilini.a@example.com"),
                new Person("198512234568", "Roshan Mendis", LocalDate.of(1985, 12, 24), Gender.MALE, "34 Marine Drive, Colombo 04", "0712345683", "roshan.mendis@example.com")
        );

        List<User> policyholders = new ArrayList<>();
        for (Person p : people) {
            policyholders.add(policyholder(p.nic(), p.name(), p.dob(), p.gender(), p.address(), p.phone(), p.email()));
        }

        LocalDate today = LocalDate.now();
        List<Policy> allPolicies = new ArrayList<>();

        // ---------- Applications -> policies ----------
        for (int i = 0; i < policyholders.size(); i++) {
            User person = policyholders.get(i);
            InsurancePlan chosen = plans[i % plans.length];
            int monthsAgo = 11 - (i % 11);
            LocalDate submitted = today.minusMonths(monthsAgo).minusDays(random.nextInt(10));
            int age = Period.between(person.getDateOfBirth(), submitted).getYears();
            boolean preExisting = i % 4 == 0;
            int riskScore = 15 + random.nextInt(55) + (preExisting ? 15 : 0);
            BigDecimal loading = preExisting
                    ? BigDecimal.valueOf(5L + random.nextInt(15))
                    : BigDecimal.valueOf(random.nextInt(3) * 2.5);

            UnderwritingApplication app = application(person, chosen, age, preExisting,
                    preExisting ? "Declared hypertension, managed with medication." : null,
                    chosen.getPlanType() == PlanType.FAMILY ? 1 + random.nextInt(3) : 0,
                    ApplicationDecision.APPROVED, riskScore, loading, submitted.atTime(10, 15), underwriter);

            Policy policy = policy(app, person, chosen, loading,
                    i % 3 == 0 ? PremiumFrequency.QUARTERLY : PremiumFrequency.MONTHLY,
                    submitted.plusDays(2), salesAgent);
            allPolicies.add(policy);

            if (chosen.getPlanType() == PlanType.FAMILY) {
                dependent(policy, spouseNameFor(person), null, person.getDateOfBirth().plusYears(2),
                        person.getGender() == Gender.MALE ? Gender.FEMALE : Gender.MALE, RelationshipType.SPOUSE, salesAgent);
                dependent(policy, childNameFor(person), null, today.minusYears(4 + random.nextInt(10)),
                        random.nextBoolean() ? Gender.MALE : Gender.FEMALE, RelationshipType.CHILD, salesAgent);
            }
        }

        // ---------- A live underwriting queue ----------
        for (int i = 0; i < 4; i++) {
            User person = policyholders.get(policyholders.size() - 1 - i);
            InsurancePlan chosen = plans[(i + 2) % plans.length];
            LocalDate submitted = today.minusDays(2L + i * 3);
            int age = Period.between(person.getDateOfBirth(), submitted).getYears();
            application(person, chosen, age, i % 2 == 0,
                    i % 2 == 0 ? "Family history of diabetes noted on the proposal form." : null,
                    chosen.getPlanType() == PlanType.FAMILY ? 2 : 0,
                    ApplicationDecision.PENDING, 0, BigDecimal.ZERO, submitted.atTime(9, 30), null);
        }
        // ---------- A couple of declined applications ----------
        for (int i = 0; i < 2; i++) {
            User person = policyholders.get(i + 3);
            LocalDate submitted = today.minusMonths(3L + i);
            int age = Period.between(person.getDateOfBirth(), submitted).getYears();
            application(person, criticalIllness, age, true,
                    "Multiple undisclosed pre-existing conditions found during verification.", 0,
                    ApplicationDecision.REJECTED, 88 + i, BigDecimal.ZERO, submitted.atTime(14, 0), underwriter);
        }

        // ---------- Payments: monthly premium history per policy ----------
        for (Policy policy : allPolicies) {
            LocalDate period = policy.getStartDate();
            int monthStep = policy.getPremiumFrequency() == PremiumFrequency.QUARTERLY ? 3 : 1;
            while (period.isBefore(today)) {
                LocalDate periodEnd = period.plusMonths(monthStep);
                payment(policy, policy.getPremiumAmount(),
                        random.nextInt(10) < 7 ? PaymentMethod.CARD : PaymentMethod.ONLINE_WALLET,
                        random.nextInt(10) < 6, PaymentStatus.PAID, period, periodEnd,
                        period.atTime(9 + random.nextInt(9), random.nextInt(60)), policy.getPolicyholder());
                period = periodEnd;
            }
            policy.setNextDueDate(period);
            policyRepository.save(policy);
        }

        // ---------- Claims spread across the last 12 months ----------
        int[] claimsPerMonth = {4, 6, 5, 7, 6, 8, 7, 5, 6, 7, 6, 5};
        for (int monthOffset = 11; monthOffset >= 0; monthOffset--) {
            int count = claimsPerMonth[11 - monthOffset];
            LocalDate monthStart = today.minusMonths(monthOffset).withDayOfMonth(1);

            // Only policies that were already in force during this month can have claims against them.
            List<Policy> eligible = allPolicies.stream()
                    .filter(p -> !monthStart.isBefore(p.getStartDate()))
                    .toList();
            if (eligible.isEmpty()) {
                continue;
            }

            for (int c = 0; c < count; c++) {
                Policy policy = eligible.get(random.nextInt(eligible.size()));
                int dayRange = Math.min(27, monthStart.lengthOfMonth() - 1);
                LocalDate treatment = monthStart.plusDays(random.nextInt(dayRange + 1));
                if (treatment.isAfter(today)) {
                    treatment = today;
                }

                ClaimCategory category = CATEGORIES[random.nextInt(CATEGORIES.length)];
                BigDecimal amount = claimAmountFor(category);

                ClaimStatus status;
                if (monthOffset <= 1) {
                    // Recent claims are mostly still awaiting a decision, so the officer has a real queue.
                    status = random.nextInt(10) < 7 ? ClaimStatus.SUBMITTED : ClaimStatus.APPROVED;
                } else {
                    int roll = random.nextInt(10);
                    status = roll < 7 ? ClaimStatus.APPROVED
                            : roll < 9 ? ClaimStatus.REJECTED : ClaimStatus.WITHDRAWN;
                }

                Dependent dependent = null;
                List<Dependent> deps = dependentRepository.findByPolicy(policy);
                if (!deps.isEmpty() && random.nextInt(10) < 4) {
                    dependent = deps.get(random.nextInt(deps.size()));
                }

                claim(policy, policy.getPolicyholder(), dependent, category,
                        HOSPITALS[random.nextInt(HOSPITALS.length)], treatment,
                        DIAGNOSES[random.nextInt(DIAGNOSES.length)], amount, status,
                        treatment.plusDays(1).atTime(11, 20),
                        status == ClaimStatus.REJECTED ? "Treatment falls outside the policy's covered benefits." :
                        status == ClaimStatus.APPROVED ? "Documents verified, settled within cover." : null,
                        claimsOfficer);
            }
        }

        // ---------- Support tickets ----------
        String[][] ticketSeeds = {
                {"Question about my policy renewal date", "Could you confirm when my family plan is due for renewal and whether my dependents carry over automatically?"},
                {"Claim settlement is taking longer than expected", "I submitted a claim three weeks ago and it is still showing as submitted. Can someone look into it?"},
                {"Need to add my newborn to the policy", "We welcomed a baby last month and I would like to add the child as a dependent on our family plan."},
                {"Premium payment did not reflect", "I paid the premium through my card but the portal still shows the payment as due."},
                {"Request for a copy of my policy schedule", "I need a stamped copy of the policy schedule for a visa application."},
                {"Hospital not accepting cashless treatment", "Ninewells told me cashless is unavailable for my plan. Is this correct?"},
                {"Update my contact phone number", "I have changed my mobile number and would like the records updated."},
                {"Clarification on dental cover limits", "How much of my annual limit can be used for dental treatment?"},
                {"Wrong dependent name on my policy", "My spouse's name is misspelled on the policy document."},
                {"Cancel auto-pay on my premium", "I would like to switch from auto-pay to manual payments from next cycle."},
                {"Coverage for overseas emergency treatment", "Does my premium family plan cover emergency treatment while travelling abroad?"},
                {"Increase my annual coverage limit", "I would like to explore upgrading to a higher coverage tier."}
        };
        for (int i = 0; i < ticketSeeds.length; i++) {
            User raiser = policyholders.get(i % policyholders.size());
            Policy related = allPolicies.get(i % allPolicies.size());
            TicketStatus status = i < 4 ? TicketStatus.OPEN : (i < 7 ? TicketStatus.IN_PROGRESS : TicketStatus.CLOSED);
            String response = switch (status) {
                case OPEN -> null;
                case IN_PROGRESS -> "Thanks for reaching out — we are checking this with the relevant team and will update you shortly.";
                case CLOSED -> "This has now been resolved. Please let us know if you need anything further.";
            };
            // Open tickets are recent; resolved ones are spread back across the year.
            int daysAgo = switch (status) {
                case OPEN -> random.nextInt(12);
                case IN_PROGRESS -> 10 + random.nextInt(25);
                case CLOSED -> 40 + random.nextInt(290);
            };
            ticket(raiser, related, null, ticketSeeds[i][0], ticketSeeds[i][1], status, response,
                    today.minusDays(daysAgo).atTime(10 + random.nextInt(8), random.nextInt(60)), cre);
        }

        // Historical resolved tickets so the support trend covers the full year.
        for (int i = 0; i < 16; i++) {
            String[] seed = ticketSeeds[random.nextInt(ticketSeeds.length)];
            User raiser = policyholders.get(random.nextInt(policyholders.size()));
            Policy related = allPolicies.get(random.nextInt(allPolicies.size()));
            int daysAgo = 45 + random.nextInt(300);
            ticket(raiser, related, null, seed[0], seed[1], TicketStatus.CLOSED,
                    "This has now been resolved. Please let us know if you need anything further.",
                    today.minusDays(daysAgo).atTime(9 + random.nextInt(9), random.nextInt(60)), cre);
        }
    }

    // ------------------------------------------------------------------ helpers

    private BigDecimal claimAmountFor(ClaimCategory category) {
        int base = switch (category) {
            case ANNUAL_CHECKUP -> 8000 + random.nextInt(9000);
            case DENTAL -> 12000 + random.nextInt(28000);
            case HOSPITALIZATION -> 45000 + random.nextInt(120000);
            case SURGERY -> 90000 + random.nextInt(210000);
            case MATERNITY -> 120000 + random.nextInt(180000);
            case CANCER_SCREENING -> 25000 + random.nextInt(60000);
            case EMERGENCY -> 35000 + random.nextInt(95000);
            case OTHER -> 10000 + random.nextInt(40000);
        };
        return BigDecimal.valueOf(base).setScale(2, RoundingMode.HALF_UP);
    }

    private String spouseNameFor(User person) {
        String surname = person.getFullName().substring(person.getFullName().lastIndexOf(' ') + 1);
        String[] firsts = {"Anusha", "Malith", "Shanika", "Pradeep", "Hiruni", "Kavinda"};
        return firsts[Math.abs(person.getFullName().hashCode()) % firsts.length] + " " + surname;
    }

    private String childNameFor(User person) {
        String surname = person.getFullName().substring(person.getFullName().lastIndexOf(' ') + 1);
        String[] firsts = {"Dinuk", "Sheni", "Ravindu", "Nethmi", "Sasen", "Oshadi"};
        return firsts[Math.abs(person.getNic().hashCode()) % firsts.length] + " " + surname;
    }

    private User staff(String nic, String fullName, LocalDate dob, Gender gender, String address,
                        String phone, String email, Role role) {
        return newUser(nic, fullName, dob, gender, address, phone, email, role);
    }

    private User policyholder(String nic, String fullName, LocalDate dob, Gender gender, String address,
                               String phone, String email) {
        return newUser(nic, fullName, dob, gender, address, phone, email, Role.POLICYHOLDER);
    }

    private User newUser(String nic, String fullName, LocalDate dob, Gender gender, String address,
                          String phone, String email, Role role) {
        User user = new User();
        user.setNic(nic);
        user.setFullName(fullName);
        user.setDateOfBirth(dob);
        user.setGender(gender);
        user.setAddress(address);
        user.setPhoneNumber(phone);
        user.setEmail(email);
        user.setRole(role);
        user.setPasswordHash(passwordEncoder.encode(DEFAULT_PASSWORD));

        // Medical & personal profile — members carry fuller records than staff.
        user.setBloodGroup(BLOOD_GROUPS[random.nextInt(BLOOD_GROUPS.length)]);
        int age = Period.between(dob, LocalDate.now()).getYears();
        user.setMaritalStatus(age >= 28
                ? MaritalStatus.MARRIED
                : (age >= 22 ? MaritalStatus.SINGLE : MaritalStatus.SINGLE));
        user.setOccupation(OCCUPATIONS[random.nextInt(OCCUPATIONS.length)]);
        user.setHeightCm(gender == Gender.MALE ? 165 + random.nextInt(20) : 152 + random.nextInt(18));
        user.setWeightKg(gender == Gender.MALE ? 62 + random.nextInt(28) : 48 + random.nextInt(26));

        if (role == Role.POLICYHOLDER) {
            user.setAllergies(random.nextInt(10) < 4 ? ALLERGIES[random.nextInt(ALLERGIES.length)] : null);
            user.setChronicConditions(random.nextInt(10) < 4 ? CONDITIONS[random.nextInt(CONDITIONS.length)] : null);
            user.setCurrentMedications(random.nextInt(10) < 3 ? MEDICATIONS[random.nextInt(MEDICATIONS.length)] : null);
            user.setEmergencyContactName(EMERGENCY_NAMES[random.nextInt(EMERGENCY_NAMES.length)]);
            user.setEmergencyContactRelation(EMERGENCY_RELATIONS[random.nextInt(EMERGENCY_RELATIONS.length)]);
            user.setEmergencyContactPhone("07" + (10000000 + random.nextInt(89999999)));
        }
        return userRepository.save(user);
    }

    private InsurancePlan plan(String name, String description, PlanType type, String rate, String limit) {
        InsurancePlan plan = new InsurancePlan();
        plan.setPlanName(name);
        plan.setDescription(description);
        plan.setPlanType(type);
        plan.setPremiumRate(new BigDecimal(rate));
        plan.setCoverageLimit(new BigDecimal(limit));
        plan.setStatus(PlanStatus.ACTIVE);
        return planRepository.save(plan);
    }

    private UnderwritingApplication application(User applicant, InsurancePlan plan, int age, boolean preExisting,
                                                 String notes, int dependentsPlanned, ApplicationDecision decision,
                                                 int riskScore, BigDecimal loading, LocalDateTime submittedAt,
                                                 User decidedBy) {
        UnderwritingApplication app = new UnderwritingApplication();
        app.setApplicationCode(nextCode("APP", submittedAt.getYear()));
        app.setApplicant(applicant);
        app.setRequestedPlan(plan);
        app.setApplicantAge(age);
        app.setHasPreExistingConditions(preExisting);
        app.setConditionsNotes(notes);
        app.setNumDependentsPlanned(dependentsPlanned);
        app.setDecision(decision);
        app.setSubmittedAt(submittedAt);
        if (decision != ApplicationDecision.PENDING) {
            app.setRiskScore(riskScore);
            app.setPremiumLoadingPercent(loading);
            app.setDecidedAt(submittedAt.plusDays(1));
        }
        UnderwritingApplication saved = applicationRepository.save(app);
        audit("UnderwritingApplication", saved.getId(), "SUBMITTED", applicant, saved.getApplicationCode(), submittedAt);
        if (decision != ApplicationDecision.PENDING) {
            audit("UnderwritingApplication", saved.getId(), "DECIDED:" + decision, decidedBy,
                    "risk=" + riskScore + " loading=" + loading, submittedAt.plusDays(1));
        }
        return saved;
    }

    private Policy policy(UnderwritingApplication application, User holder, InsurancePlan plan, BigDecimal loading,
                           PremiumFrequency frequency, LocalDate startDate, User agent) {
        BigDecimal factor = BigDecimal.ONE.add(loading.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
        BigDecimal premium = plan.getPremiumRate().multiply(factor).setScale(2, RoundingMode.HALF_UP);

        Policy policy = new Policy();
        policy.setPolicyCode(nextCode("POL", startDate.getYear()));
        policy.setPolicyholder(holder);
        policy.setPlan(plan);
        policy.setSourceApplication(application);
        policy.setPremiumAmount(premium);
        policy.setPremiumFrequency(frequency);
        policy.setStartDate(startDate);
        policy.setEndDate(startDate.plusYears(1));
        policy.setNextDueDate(startDate.plusMonths(frequency == PremiumFrequency.QUARTERLY ? 3 : 1));
        policy.setStatus(PolicyStatus.ACTIVE);
        Policy saved = policyRepository.save(policy);
        audit("Policy", saved.getId(), "ISSUED", agent, saved.getPolicyCode(), startDate.atTime(11, 0));
        return saved;
    }

    private void dependent(Policy policy, String name, String nic, LocalDate dob, Gender gender,
                            RelationshipType relationship, User agent) {
        Dependent dependent = new Dependent();
        dependent.setPolicy(policy);
        dependent.setFullName(name);
        dependent.setDateOfBirth(dob);
        dependent.setGender(gender);
        dependent.setRelationship(relationship);

        // A covered member aged 18+ must carry a NIC; minors have not been issued one yet.
        int age = Period.between(dob, LocalDate.now()).getYears();
        if (nic != null) {
            dependent.setNic(nic);
        } else if (age >= 18) {
            dependent.setNic("%04d%08d".formatted(dob.getYear(), 10000000 + random.nextInt(89999999)));
        }

        dependent.setBloodGroup(BLOOD_GROUPS[random.nextInt(BLOOD_GROUPS.length)]);
        if (random.nextInt(10) < 3) {
            dependent.setAllergies(ALLERGIES[random.nextInt(ALLERGIES.length)]);
        }
        if (age > 40 && random.nextInt(10) < 4) {
            dependent.setChronicConditions(CONDITIONS[random.nextInt(CONDITIONS.length)]);
            dependent.setCurrentMedications(MEDICATIONS[random.nextInt(MEDICATIONS.length)]);
        }

        Dependent saved = dependentRepository.save(dependent);
        audit("Dependent", saved.getId(), "ADDED", agent, "policy " + policy.getPolicyCode(),
                policy.getStartDate().atTime(11, 30));
    }

    private void payment(Policy policy, BigDecimal amount, PaymentMethod method, boolean autoPay,
                          PaymentStatus status, LocalDate periodStart, LocalDate periodEnd,
                          LocalDateTime paidAt, User payer) {
        Payment payment = new Payment();
        payment.setPolicy(policy);
        payment.setAmount(amount);
        payment.setMethod(method);
        payment.setAutoPay(autoPay);
        payment.setStatus(status);
        payment.setBillingPeriodStart(periodStart);
        payment.setBillingPeriodEnd(periodEnd);
        payment.setPaidAt(paidAt);
        Payment saved = paymentRepository.save(payment);
        audit("Payment", saved.getId(), "PAID", payer, amount + " for " + policy.getPolicyCode(), paidAt);
    }

    private void claim(Policy policy, User claimant, Dependent dependent, ClaimCategory category, String hospital,
                        LocalDate treatmentDate, String diagnosis, BigDecimal amount, ClaimStatus status,
                        LocalDateTime submittedAt, String decisionNotes, User officer) {
        Claim claim = new Claim();
        claim.setClaimCode(nextCode("CLM", submittedAt.getYear()));
        claim.setPolicy(policy);
        claim.setClaimant(claimant);
        claim.setClaimantDependent(dependent);
        claim.setCategory(category);
        claim.setHospitalName(hospital);
        claim.setTreatmentDate(treatmentDate);
        claim.setDiagnosisSummary(diagnosis);
        claim.setAmountClaimed(amount);
        claim.setStatus(status);
        claim.setSubmittedAt(submittedAt);
        claim.setDecisionNotes(decisionNotes);
        Claim saved = claimRepository.save(claim);
        audit("Claim", saved.getId(), "SUBMITTED", claimant, saved.getClaimCode(), submittedAt);

        // Most members attach a bill; bigger claims usually carry a discharge summary too.
        if (status != ClaimStatus.WITHDRAWN) {
            seedDocument(saved, "Hospital Bill", claimant, submittedAt.plusMinutes(4));
            if (amount.compareTo(BigDecimal.valueOf(80000)) > 0) {
                seedDocument(saved, "Discharge Summary", claimant, submittedAt.plusMinutes(6));
            }
        }

        if (status != ClaimStatus.SUBMITTED) {
            audit("Claim", saved.getId(),
                    status == ClaimStatus.WITHDRAWN ? "WITHDRAWN" : "DECIDED:" + status,
                    status == ClaimStatus.WITHDRAWN ? claimant : officer,
                    decisionNotes, submittedAt.plusDays(2));
        }
    }

    private void ticket(User raisedBy, Policy policy, Claim claim, String subject, String message,
                         TicketStatus status, String response, LocalDateTime createdAt, User cre) {
        SupportTicket ticket = new SupportTicket();
        ticket.setRaisedBy(raisedBy);
        ticket.setRelatedPolicy(policy);
        ticket.setRelatedClaim(claim);
        ticket.setSubject(subject);
        ticket.setMessage(message);
        ticket.setStatus(status);
        ticket.setResponse(response);
        ticket.setCreatedAt(createdAt);
        if (response != null) {
            ticket.setUpdatedAt(createdAt.plusHours(5));
        }
        SupportTicket saved = ticketRepository.save(ticket);
        audit("SupportTicket", saved.getId(), "LOGGED", raisedBy, subject, createdAt);
        if (response != null) {
            audit("SupportTicket", saved.getId(), "RESPONDED:" + status, cre, response, createdAt.plusHours(5));
        }
    }

    /**
     * Writes a small generated PNG that stands in for a scanned hospital bill, so the claims
     * officer has a real file to open during a demo. Drawn with Java2D rather than shipping
     * binary fixtures into the repository.
     */
    private void seedDocument(Claim claim, String label, User uploader, LocalDateTime uploadedAt) {
        try {
            java.awt.image.BufferedImage img =
                    new java.awt.image.BufferedImage(640, 400, java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = img.createGraphics();
            g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                    java.awt.RenderingHints.VALUE_ANTIALIAS_ON);

            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, 640, 400);
            g.setColor(new java.awt.Color(0x25, 0x63, 0xEB));
            g.fillRect(0, 0, 640, 64);

            g.setColor(java.awt.Color.WHITE);
            g.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, 20));
            g.drawString(claim.getHospitalName(), 24, 40);

            g.setColor(new java.awt.Color(0x0F, 0x17, 0x2A));
            g.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, 16));
            g.drawString(label, 24, 105);

            g.setFont(new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 13));
            g.setColor(new java.awt.Color(0x33, 0x41, 0x55));
            g.drawString("Claim reference : " + claim.getClaimCode(), 24, 145);
            g.drawString("Patient         : " + (claim.getClaimantDependent() != null
                    ? claim.getClaimantDependent().getFullName() : claim.getClaimant().getFullName()), 24, 170);
            g.drawString("Treatment date  : " + claim.getTreatmentDate(), 24, 195);
            g.drawString("Category        : " + claim.getCategory().getLabel(), 24, 220);
            g.drawString("Diagnosis       : " + claim.getDiagnosisSummary(), 24, 245);

            g.setColor(new java.awt.Color(0xE2, 0xE8, 0xF0));
            g.fillRect(24, 275, 592, 1);

            g.setColor(new java.awt.Color(0x0F, 0x17, 0x2A));
            g.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, 18));
            g.drawString("Total: LKR " + claim.getAmountClaimed(), 24, 315);

            g.setColor(new java.awt.Color(0x94, 0xA3, 0xB8));
            g.setFont(new java.awt.Font("SansSerif", java.awt.Font.ITALIC, 11));
            g.drawString("Generated sample document for the MediSure demo environment.", 24, 365);
            g.dispose();

            String storedName = java.util.UUID.randomUUID() + ".png";
            java.nio.file.Path dir = uploadRoot.resolve("claims").resolve(String.valueOf(claim.getId()));
            java.nio.file.Files.createDirectories(dir);
            java.nio.file.Path target = dir.resolve(storedName);
            javax.imageio.ImageIO.write(img, "png", target.toFile());

            ClaimDocument document = new ClaimDocument();
            document.setClaim(claim);
            document.setOriginalFileName(label.toLowerCase(java.util.Locale.ROOT).replace(' ', '-')
                    + "-" + claim.getClaimCode() + ".png");
            document.setStoredFileName(storedName);
            document.setContentType("image/png");
            document.setSizeBytes(java.nio.file.Files.size(target));
            document.setUploadedBy(uploader);
            document.setUploadedAt(uploadedAt);
            ClaimDocument saved = claimDocumentRepository.save(document);

            audit("ClaimDocument", saved.getId(), "UPLOADED", uploader,
                    saved.getOriginalFileName() + " for " + claim.getClaimCode(), uploadedAt);
        } catch (java.io.IOException ex) {
            throw new java.io.UncheckedIOException("Could not seed a claim document", ex);
        }
    }

    private void audit(String entityType, Long entityId, String action, User performedBy, String notes,
                        LocalDateTime timestamp) {
        AuditLog log = new AuditLog(entityType, entityId, action, performedBy, notes);
        log.setTimestamp(timestamp);
        auditLogRepository.save(log);
    }
}
