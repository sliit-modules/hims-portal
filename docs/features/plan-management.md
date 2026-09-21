# Benefits & Coverage Plan Management
**Owner:** Karunarathna W.M.K.U. (IT25103657)
**Persona:** System Administration Manager

## Implementation Log

### [PBI17] Plan Package Creation & Entity Setup
- Implemented InsurancePlan domain entity with status and plan types.
- Configured repository layer and initial validation constraints.

### [PBI18] Plan Catalog & Browsing Interface
- Developed PlanController and Thymeleaf views (list.html, iew.html, orm.html).
- Added filtering by active status and plan type.

### [PBI19] Premium Rate & Coverage Limit Updates
- Implemented update method in PlanService with audit trail integration.
- Added validation for non-negative premium rates and maximum coverage limits.

### [PBI20] Plan Discontinuation & Archival
- Added safe discontinuation logic setting plan status to DISCONTINUED.
- Preserved historical plan references for existing active policies.

### [PBI32] Management Reports (Sprint 6)
- **Reports** page (`/reports`, System Administrator only) for a chosen period — this year so far
  by default:
  - **Premium income:** payments still `PAID` in the period, so refunded and voided payments are
    left out; given per plan and per month.
  - **Claims:** claims submitted in the period — count, amount claimed and amount approved per plan,
    and the number in each status.
  - **Claims ratio:** approved claims ÷ premium income, per plan and overall; amber above 70% and red
    above 100%. Shown as "—" when a plan had no income in the period.
  - **Policies in force:** active and renewed policies per plan today.
- **Export:** a CSV download that opens in Excel (`/reports/export.csv`) and a print-ready A4 page
  that saves as PDF from the browser (`/reports/print`), the same approach as the PBI31 receipt and
  certificate, so no PDF library was added.
- CSV cells are quoted when needed, and text starting with `=`, `+`, `-` or `@` is prefixed with an
  apostrophe so a spreadsheet cannot run it as a formula (CSV injection).
- Every export is written to the audit log (`EXPORTED`, with the format and period).
