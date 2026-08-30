# Policy Management
**Owner:** Lankadhikara L.R.M.M.P. (IT25101639)
**Persona:** Insurance Sales Agent

## Implementation Log

### [PBI01] Policy Issuance & Validation Rules
- Built PolicyService.issue() to generate unique policy numbers (e.g. POL-2026-xxxxxx).
- Linked approved underwriting applications to active coverage plans and set start/end dates.

### [PBI02] Policy Inspection & Certificate View
- Developed policy list and details template (policies/view.html, policies/issue.html).
- Added printable policy schedule showing covered dependents, benefits, and deductibles.

### [PBI03] Annual Policy Renewal & Coverage Modification
- Added policy renewal logic extending policy validity period by 12 months.
- Enabled Sales Agents to add/remove dependents during the policy modification window.
