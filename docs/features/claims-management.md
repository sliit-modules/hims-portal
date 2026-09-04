# Claims Management
**Owner:** Gunasinghe N.M. (IT25101773)
**Persona:** Senior Claims Processing Officer

## Implementation Log

### [PBI05] Claim Submission & Document Uploads
- Created claim submission form (claims/form.html) with category selection (Hospitalization, Pharmacy, Surgery, Outpatient).
- Built multipart document uploader storing hospital bills and discharge sheets into uploads/claims/.

### [PBI06 & PBI07] Claim Tracking Timeline & Adjudication
- Implemented claim timeline and status badges (PENDING, UNDER_REVIEW, APPROVED, REJECTED).
- Built decision view (claims/view.html) for Claims Officers to evaluate against policy coverage limit.

### [PBI08] Claim Withdrawal & Duplicate Claim Voiding
- Implemented claim withdrawal option for policyholders before review begins.
- Enabled Claims Officers to void duplicate or fraudulent claims.
