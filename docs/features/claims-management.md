# Claims Management
**Owner:** Gunasinghe N.M. (IT25101773)
**Persona:** Senior Claims Processing Officer

## Implementation Log

### [PBI05] Claim Submission & Document Uploads
- Claim form (`claims/form.html`) with a category (Annual check-up, Dental, Hospitalization,
  Surgery, Maternity, Cancer screening, Emergency, Other), the treated member (the policyholder or
  a covered dependent), hospital, treatment date, diagnosis and amount.
- Supporting documents (PDF, JPEG, PNG) are checked before the claim is saved and stored under
  `uploads/claims/`; they can be added later and removed while the claim is pending.
- A claim is refused if the policy is not in force, the treatment is before cover started, or the
  amount is more than the cover left in that policy year.

### [PBI06 & PBI07] Claim Tracking & Adjudication
- Statuses: `SUBMITTED` (pending), `APPROVED`, `REJECTED`, `WITHDRAWN`, `VOIDED`, shown as badges.
- A claims officer (or admin) approves or rejects a pending claim once; approval re-checks the
  cover left in the policy year of the treatment date.

### [PBI08] Claim Withdrawal
- The policyholder can withdraw their own pending claim. It is kept as `WITHDRAWN` for the record.

### [PBI32] Voiding Duplicate Claims (Sprint 6)
- **Warning:** when a pending claim has the same policy, patient and treatment date as another
  pending or approved claim, officers see "Possible duplicate" on the claim page. It never voids
  anything by itself.
- **Void:** `ClaimService.voidAsDuplicate(id, originalId, reason, actor)` — claims officer or admin
  only; the claim must still be pending; the original must be a different claim on the same policy
  for the same patient that is not voided or withdrawn; a reason is required.
- The claim becomes `VOIDED`, links to the original (`duplicate_of_id`), is never paid and never
  counts against cover. The member is notified and the change is written to the audit log.
- `SchemaUpgrade` adds `VOIDED` to the existing `claims.status` column, so team members' databases
  update without being rebuilt.
