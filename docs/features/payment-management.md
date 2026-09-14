# Premium & Payment Management
**Owner:** Ramanayaka U.K.D. (IT25100714)
**Persona:** Policyholder

## Implementation Log

### [PBI13] Online Premium Payment Checkout
- Implemented premium payment form (`payments/form.html`) with payment method selection
  (`CARD`, `BANK_TRANSFER`, `CASH`, `ONLINE_WALLET`).
- `PaymentService.makePayment(policy, amount, method, autoPay, actor)` records the payment,
  derives the billing period from the policy's premium frequency, and advances the policy's
  `nextDueDate`. Gateway settlement is simulated — no external payment API is called.

### [PBI14] Payment History & Receipts
- Built payment transaction history view with status badges. `PaymentStatus` is
  `PAID`, `REFUNDED` or `CANCELLED`.
- Receipt summary rendered at `payments/view.html`, showing amount, method, billing period
  and paid-at timestamp.

### [PBI15] Auto-Pay Settings & Billing Frequency
- `autoPay` flag stored per payment and editable via
  `PaymentService.updateMethod(id, method, autoPay, actor)`.
- Billing frequency is held on the policy (`MONTHLY`, `QUARTERLY`, `ANNUAL`) and drives how
  far `nextDueDate` moves after each payment.

### [PBI16] Payment Refund & Transaction Voiding
- `PaymentService.cancelOrRefund(id, newStatus, actor)` handles both refunds and cancellations
  for duplicate or overcharged transactions.
- Ownership is enforced by `assertOwner(...)`: a policyholder can only act on payments
  belonging to their own policies.
- Every state change is written to the audit log.

## Demo Account
This module's owner persona is the **Policyholder** — every operation below is a member
self-service action — so the owner signs in with a **NIC**, not a staff email:
`199408089876` / `password123`. That account holds its own policy, so all four CRUD
operations can be demonstrated against real cover:

| | Operation | Where |
|---|---|---|
| **C** | Make a premium payment | Policy page → *Pay Premium* |
| **R** | View payment history and receipts | Payments list → receipt view |
| **U** | Change payment method / auto-pay | Receipt view → *Change Method / Auto-Pay* |
| **D** | Cancel or refund a payment | Receipt view → *Cancel* / *Refund* |
