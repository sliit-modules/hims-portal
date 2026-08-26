# Underwriting & Risk Assessment
**Owner:** De Zoysa A.I. (IT25102600)
**Persona:** Head of Health Insurance Operations

## Implementation Log

### [PBI09] Underwriting Application Submission
- Implemented applicant medical questionnaire and intake form (orm.html).
- Created UnderwritingApplication model linked to user and chosen plan.

### [PBI10] Automated Risk Scoring Engine
- Implemented risk assessment calculation based on BMI, age, and pre-existing medical conditions.
- Displayed risk score tiers (Low, Medium, High) in assessment dashboard.

### [PBI11] Underwriting Decision & Premium Loading
- Created decision approval workflow with loading percentage adjustments.
- Stored decision notes and transitioned status to APPROVED for policy issuance.

### [PBI12] High-Risk Application Rejection & Withdrawal
- Implemented application rejection logic with mandatory justification recording.
- Integrated customer notification triggers for rejected submissions.
