# Underwriting & Risk Assessment
**Owner:** De Zoysa A.I. (IT25102600)
**Persona:** Head of Health Insurance Operations

## Implementation Log

### [PBI09] Underwriting Application Submission
- Implemented the application intake form (`underwriting/form.html`): requested plan, declared
  pre-existing conditions with notes, and the number of dependents to be covered.
- A policyholder applies for themselves; a sales agent can apply on a member's behalf by NIC.
- `UnderwritingApplication` is linked to the applicant and the requested plan; the applicant's age
  is worked out from their date of birth when the application is submitted.

### [PBI10] Risk Score and Medical History Review
- The application page shows the applicant's details, declared conditions and planned dependents.
- In Sprints 1–4 the underwriter entered the 0–100 risk score by hand. A rule-based suggestion was
  added in Sprint 5 (PBI29, below).

### [PBI11] Underwriting Decision & Premium Loading
- Underwriter-only decision form: approve or reject, risk score and premium loading %.
- An application can be decided only once, and every decision is written to the audit log.
- An approved application can be issued as a policy; the loading is applied to the plan's rate.

### [PBI12] High-Risk Application Rejection
- High-risk applications are rejected through the same decision workflow, with the risk score kept.
- Email/SMS notification of decisions is not built yet — it is planned for Sprint 6 (PBI30).

### [PBI29] Suggested Risk Score & Application Withdrawal (Sprint 5)
- **Suggested risk score** from fixed, published rules (`UnderwritingService.assessRisk`), capped at 100:

  | Factor | Points |
  |---|---|
  | Base score | 10 |
  | Age under 30 / 30–44 / 45–59 / 60 or over | 0 / 10 / 25 / 40 |
  | Declared pre-existing conditions | 25 |
  | Each dependent to be covered | 5 (at most 15) |
  | BMI 30 or over / 25–29.9 / under 18.5 (from the member's height and weight) | 10 / 5 / 5 |

- The suggested loading follows the score: under 30 → 0%, 30–49 → 10%, 50–69 → 25%,
  70 or over → 50% with a "high risk — consider rejecting" warning.
- The decide page shows the breakdown and is pre-filled with the suggestion. The underwriter makes
  the decision and may change the score; a score more than 10 points from the suggestion needs a
  reason. The suggested score and the reason are stored with the decision and in the audit log.
- **Withdrawal:** the applicant can withdraw their own pending application (status `WITHDRAWN`).
  A withdrawn application cannot be decided or issued as a policy.
- `SchemaUpgrade` adds `WITHDRAWN` to the existing MySQL column on start-up, so existing databases
  keep working without being dropped.

## Demo
Sign in as `underwriting@medisure.lk` / `password123` → *Underwriting* → a pending application →
*Review & Decide* to see the suggested score and its breakdown.
