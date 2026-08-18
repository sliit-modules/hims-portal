# SE2030 · SOFTWARE ENGINEERING — LAB 02
## Agile Development: Sprint Planning & Backlog Management
**Topic:** Web-based Health Insurance Management System — MediSure Lanka Insurance PLC

---

### Document Information

| Field | Detail |
| :--- | :--- |
| **Batch** | 05 |
| **Group ID** | `2026-Y2-S1-MLB-B5G2-02` |
| **Module** | SE2030 – Software Engineering, Year 2 Semester 1 2026 |
| **Lab** | Lab 02 — Agile Development (Scrum) |
| **Related Labs** | Lab 01 (Topic Selection), Lab 04 (Activity Diagrams) |

---

### Group Members & Roles

| Name | Student ID | Scrum Role | Module Ownership | GitHub Email |
| :--- | :--- | :--- | :--- | :--- |
| **Lankadhikara L.R.M.M.P.** | `IT25101639` | Product Owner / Developer | Policy Management | `IT25101639@my.sliit.lk` |
| **Gunasinghe N.M.** | `IT25101773` | Developer | Claims Management | `IT25101773@my.sliit.lk` |
| **De Zoysa A.I.** | `IT25102600` | Developer | Underwriting & Risk Assessment | `IT25102600@my.sliit.lk` |
| **Ramanayaka U.K.D.** | `IT25100714` | Developer | Premium & Payment Management | `IT25100714@my.sliit.lk` |
| **Karunarathna W.M.K.U.** | `IT25103657` | Scrum Master / Developer | Benefits & Coverage Plan Mgmt. | `IT25103657@my.sliit.lk` |
| **Kavisekara K.M.H.N.** | `IT25103510` | Developer | Customer Support & Inquiry Mgmt. | `IT25103510@my.sliit.lk` |

---

## 1. Group Details

* **Group ID:** `2026-Y2-S1-MLB-B5G2-02`  
* **Topic:** Web-based Health Insurance Management System — MediSure Lanka Insurance PLC  
* **Note:** The same project topic assigned in Lab 01 is used for this Scrum exercise.

---

## 2. Scrum Roles & Responsibilities

| Scrum Role | Member(s) | Responsibility |
| :--- | :--- | :--- |
| **Product Owner** | Lankadhikara L.R.M.M.P. | Prioritises the product backlog based on customer and business needs; represents MediSure Lanka's stakeholders. |
| **Scrum Master** | Karunarathna W.M.K.U. | Facilitates sprint planning, stand-ups, and reviews; removes blockers and protects the team's focus. |
| **Developers** | Gunasinghe N.M.<br>De Zoysa A.I.<br>Ramanayaka U.K.D.<br>Kavisekara K.M.H.N. | Design, build, and test the user stories assigned in each sprint across backend, frontend, and QA. |

---

## 3. Identified Personas & Module Mapping

Six personas carried over from Lab 01 and modeled into the activity diagrams in Lab 04:

1. **Insurance Sales Agent** ➔ *Policy Management*
2. **Senior Claims Processing Officer** ➔ *Claims Management*
3. **Head of Health Insurance Operations** ➔ *Underwriting & Risk Assessment*
4. **Policyholder** ➔ *Premium & Payment Management*
5. **System Administration Manager** ➔ *Benefits & Coverage Plan Management*
6. **Customer Relations Executive** ➔ *Customer Support & Inquiry Management*

---

## 4. User Stories by Persona (Minimum 4 per Persona)

### 4.1 Insurance Sales Agent (Policy Management)
*Assigned Member: Lankadhikara L.R.M.M.P. (`IT25101639`)*

| # | User Story | Priority |
| :-: | :--- | :-: |
| 1 | As an Insurance Sales Agent, I want to issue a new policy to an approved applicant so that the customer is enrolled in an active coverage plan. | High |
| 2 | As an Insurance Sales Agent, I want to view existing policy details so that I can verify coverage and status when assisting a customer. | High |
| 3 | As an Insurance Sales Agent, I want to modify or renew an existing policy so that the customer's coverage stays up to date. | Medium |
| 4 | As an Insurance Sales Agent, I want to cancel a lapsed or requested policy so that inactive coverage is properly closed out. | Low |

### 4.2 Senior Claims Processing Officer (Claims Management)
*Assigned Member: Gunasinghe N.M. (`IT25101773`)*

| # | User Story | Priority |
| :-: | :--- | :-: |
| 1 | As a Senior Claims Processing Officer, I want to submit a new claim against a policy so that it enters the review process. | High |
| 2 | As a Senior Claims Processing Officer, I want to view a claim's status and history so that I can track its progress. | High |
| 3 | As a Senior Claims Processing Officer, I want to update a claim's status to approved or rejected so that payout decisions are recorded. | High |
| 4 | As a Senior Claims Processing Officer, I want to withdraw or void a duplicate or invalid claim so that it is removed from active processing. | Low |

### 4.3 Head of Health Insurance Operations (Underwriting & Risk Assessment)
*Assigned Member: De Zoysa A.I. (`IT25102600`)*

| # | User Story | Priority |
| :-: | :--- | :-: |
| 1 | As the Head of Health Insurance Operations, I want to submit a new underwriting application so that a risk assessment can begin for the applicant. | High |
| 2 | As the Head of Health Insurance Operations, I want to view the automated risk score and medical history so that I can evaluate the applicant's risk profile. | High |
| 3 | As the Head of Health Insurance Operations, I want to update the underwriting decision and set premium loading so that approved applicants can proceed to policy issuance. | High |
| 4 | As the Head of Health Insurance Operations, I want to reject or withdraw a high-risk application so that it is removed from the issuance queue. | Medium |

### 4.4 Policyholder (Premium & Payment Management)
*Assigned Member: Ramanayaka U.K.D. (`IT25100714`)*

| # | User Story | Priority |
| :-: | :--- | :-: |
| 1 | As a Policyholder, I want to pay my premium through the portal so that my policy remains active. | High |
| 2 | As a Policyholder, I want to view my payment history so that I can track past transactions and download receipts. | Medium |
| 3 | As a Policyholder, I want to update my auto-pay settings so that future premiums are paid automatically. | Medium |
| 4 | As a Policyholder, I want to cancel a payment or request a refund so that incorrect transactions are corrected. | Low |

### 4.5 System Administration Manager (Benefits & Coverage Plan Management)
*Assigned Member: Karunarathna W.M.K.U. (`IT25103657`)*

| # | User Story | Priority |
| :-: | :--- | :-: |
| 1 | As a System Administration Manager, I want to create a new insurance plan so that agents and customers can access new coverage options. | High |
| 2 | As a System Administration Manager, I want to view the catalog of insurance plans so that I can review current offerings. | High |
| 3 | As a System Administration Manager, I want to update premium rates and coverage limits so that plans reflect current pricing policy. | Medium |
| 4 | As a System Administration Manager, I want to discontinue an outdated plan so that it can no longer be issued while historical data is preserved. | Low |

### 4.6 Customer Relations Executive (Customer Support & Inquiry Management)
*Assigned Member: Kavisekara K.M.H.N. (`IT25103510`)*

| # | User Story | Priority |
| :-: | :--- | :-: |
| 1 | As a Customer Relations Executive, I want to log a customer inquiry so that it can be tracked as a support ticket. | Medium |
| 2 | As a Customer Relations Executive, I want to view ticket details and history so that I can understand the context of an issue. | Medium |
| 3 | As a Customer Relations Executive, I want to update a ticket's status and respond to the customer so that they are kept informed of progress. | Medium |
| 4 | As a Customer Relations Executive, I want to close a resolved ticket so that it is removed from the active queue. | Low |

---

## 5. Consolidated Product Backlog

| ID | Persona | User Story Summary | Priority | Story Owner |
| :--- | :--- | :--- | :---: | :--- |
| **PBI01** | Insurance Sales Agent | Issue a new policy to an approved applicant | High | Lankadhikara L.R.M.M.P. |
| **PBI02** | Insurance Sales Agent | View existing policy details and verify coverage | High | Lankadhikara L.R.M.M.P. |
| **PBI03** | Insurance Sales Agent | Modify or renew an existing policy | Medium | Lankadhikara L.R.M.M.P. |
| **PBI04** | Insurance Sales Agent | Cancel a lapsed or requested policy | Low | Lankadhikara L.R.M.M.P. |
| **PBI05** | Claims Officer | Submit a new claim against a policy | High | Gunasinghe N.M. |
| **PBI06** | Claims Officer | View a claim's status and history | High | Gunasinghe N.M. |
| **PBI07** | Claims Officer | Update a claim's status (Approve/Reject) | High | Gunasinghe N.M. |
| **PBI08** | Claims Officer | Withdraw or void an invalid/duplicate claim | Low | Gunasinghe N.M. |
| **PBI09** | Head of Operations | Submit a new underwriting application | High | De Zoysa A.I. |
| **PBI10** | Head of Operations | View automated risk score and medical history | High | De Zoysa A.I. |
| **PBI11** | Head of Operations | Update underwriting decision and set premium loading | High | De Zoysa A.I. |
| **PBI12** | Head of Operations | Reject/withdraw a high-risk application | Medium | De Zoysa A.I. |
| **PBI13** | Policyholder | Pay premium through the portal | High | Ramanayaka U.K.D. |
| **PBI14** | Policyholder | View payment history and receipts | Medium | Ramanayaka U.K.D. |
| **PBI15** | Policyholder | Update auto-pay settings | Medium | Ramanayaka U.K.D. |
| **PBI16** | Policyholder | Cancel a payment or request a refund | Low | Ramanayaka U.K.D. |
| **PBI17** | System Admin Manager | Create a new insurance plan | High | Karunarathna W.M.K.U. |
| **PBI18** | System Admin Manager | View catalog of insurance plans | High | Karunarathna W.M.K.U. |
| **PBI19** | System Admin Manager | Update premium rates and coverage limits | Medium | Karunarathna W.M.K.U. |
| **PBI20** | System Admin Manager | Discontinue an outdated plan | Low | Karunarathna W.M.K.U. |
| **PBI21** | Customer Relations Exec | Log customer inquiry as support ticket | Medium | Kavisekara K.M.H.N. |
| **PBI22** | Customer Relations Exec | View ticket details and history | Medium | Kavisekara K.M.H.N. |
| **PBI23** | Customer Relations Exec | Update ticket status and respond to customer | Medium | Kavisekara K.M.H.N. |
| **PBI24** | Customer Relations Exec | Close resolved ticket | Low | Kavisekara K.M.H.N. |

---

## 6. Sprint Plan (4 Sprints)

### Sprint 1: Core Issuance & Catalog
- **Goal:** Policy creation, underwriting intake, and plan catalog setup
- **Target Items:** PBI01, PBI02, PBI09, PBI10, PBI17, PBI18

### Sprint 2: Core Processing & Payments
- **Goal:** Claims handling, underwriting decisions, and premium payments
- **Target Items:** PBI05, PBI06, PBI07, PBI11, PBI13, PBI03

### Sprint 3: Supporting Workflows & Customer Support
- **Goal:** Plan updates, payment history & auto-pay, customer support inquiries
- **Target Items:** PBI19, PBI15, PBI14, PBI21, PBI22, PBI23

### Sprint 4: Refinement, Exception Handling & Closure
- **Goal:** Cancellations, rejections, voids, and record closures across all modules
- **Target Items:** PBI04, PBI08, PBI12, PBI16, PBI20, PBI24

---

## 7. Sprint Backlog — Task Breakdown Sample

### PBI-01 — Issue Policy (Owner: Lankadhikara L.R.M.M.P.)
| Task ID | Task | Est. Hours |
| :--- | :--- | :---: |
| T-01.1 | Design policy issuance UI | 6 |
| T-01.2 | Build backend API to create policy and assign Policy ID | 7 |
| T-01.3 | Validate applicant, plan selection, and policy terms | 3 |
| T-01.4 | Test policy issuance flow end-to-end | 3 |

### PBI-17 — Create Insurance Plan (Owner: Karunarathna W.M.K.U.)
| Task ID | Task | Est. Hours |
| :--- | :--- | :---: |
| T-17.1 | Design plan creation UI (coverage limits, benefits, pricing) | 5 |
| T-17.2 | Build backend API to create and publish plans | 6 |
| T-17.3 | Validate plan configuration rules | 3 |
| T-17.4 | Test plan creation and publishing flow | 3 |

---

## 8. Activity Diagram Alignment (Lab 04 Integration)

The user stories above correspond directly to the Use Case Scenarios and Activity Diagrams documented in `MediSure activity diagrams Lab04.pdf`:

| Use Case ID | Name | Assigned Member | Activity Diagram in Lab 04 |
| :--- | :--- | :--- | :--- |
| **UC-01** | Issue New Policy | Lankadhikara L.R.M.M.P. | Figure 01 (Page 3) |
| **UC-02** | Submit and Process Claim | Gunasinghe N.M. | Figure 02 (Page 5) |
| **UC-03** | Assess Underwriting Application | De Zoysa A.I. | Figure 03 (Page 7) |
| **UC-04** | Make Premium Payment | Ramanayaka U.K.D. | Figure 04 (Page 9) |
| **UC-05** | Create Coverage Plan | Karunarathna W.M.K.U. | Figure 05 (Page 11) |
| **UC-06** | Handle Customer Support Inquiry | Kavisekara K.M.H.N. | Figure 06 (Page 13) |
