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
- Originally a policyholder could mark their own payment REFUNDED or CANCELLED directly.
  That was replaced in Sprint 5 by PBI25 below, so no member can move money on their own.
- Ownership is enforced by `assertOwner(...)`: a policyholder can only view payments
  belonging to their own policies.
- Every state change is written to the audit log.

### [PBI25] Staff-Approved Refunds (Sprint 5)
- **Request (policyholder):** `requestRefund(id, reason, actor)` — only the policy's own holder,
  only for a `PAID` payment, one pending request at a time, and a reason is required. The payment
  stays `PAID` while the request waits.
- **Decide (admin or claims officer):** `decideRefund(id, approve, notes, actor)` — approving sets
  the status to `REFUNDED`; rejecting keeps it `PAID` and requires notes. Who decided, when and
  why are stored on the payment and in the audit log.
- **Void (admin or claims officer):** `voidPayment(id, reason, actor)` replaces the member's old
  Cancel button. It is for a payment recorded in error (e.g. a duplicate) and needs a reason;
  the status becomes `CANCELLED`.
- Staff see a banner on the payments list with the number of pending requests and a
  "Review requests" filter (`/payments?refunds=pending`).
- The request is stored in new nullable columns on `payments` (`refund_requested_at`,
  `refund_reason`, `refund_decided_by_id`, `refund_decided_at`, `refund_decision_notes`,
  `void_reason`), so existing databases update automatically — no status value was added.

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
| **D** | Request a refund | Receipt view → *Request a Refund* (then approved or rejected by staff) |

To show the approval step, sign in as `claims@medisure.lk` or `admin@medisure.lk`, open
*Payments → Review requests*, and approve or reject the request.
