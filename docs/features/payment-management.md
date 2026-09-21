# Premium & Payment Management
**Owner:** Ramanayaka U.K.D. (IT25100714)
**Persona:** Policyholder

## Implementation Log

### [PBI13] Online Premium Payment Checkout
- Implemented premium payment form (`payments/form.html`) with payment method selection
  (`CARD`, `BANK_TRANSFER`, `CASH`, `ONLINE_WALLET`).
- `PaymentService.makePayment(policy, amount, method, autoPay, actor)` records the payment,
  derives the billing period from the policy's premium frequency, and advances the policy's
  `nextDueDate`. Payments made another way (bank transfer, cash) are still recorded here; card
  payments can go through the PayHere gateway since Sprint 6 (PBI35, below).

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

### [PBI31] Printable Payment Receipt (Sprint 6)
- `/payments/{id}/receipt` is an A4 receipt (receipt number `RCPT-<year>-<id>`) that prints or
  saves as a PDF from the browser. Refunded payments are stamped REFUNDED and voided ones VOID.

### [PBI35] Online Payment with PayHere (Sprint 6)
- *Pay Premium* offers **Pay online with PayHere** when it is set up. The amount always comes from
  the policy's premium, never from the browser.
- `PayHereService` records each attempt as a `PayHereCheckout` (`payhere_checkouts` table) and sends
  the member to PayHere with a signed request:
  `hash = UPPER(MD5(merchant_id + order_id + amount(2 dp) + currency + UPPER(MD5(merchant_secret))))`.
- The premium `Payment` is recorded **only when PayHere confirms it**, and only once:
  - PayHere's server-to-server notification (`POST /payments/payhere/notify`) is accepted only if
    `md5sig = UPPER(MD5(merchant_id + order_id + payhere_amount + payhere_currency + status_code +
    UPPER(MD5(merchant_secret))))` matches and the amount is the one we asked for.
  - When the member returns, the app also asks PayHere's **Retrieval API** whether the order was
    paid — this works on a laptop, where PayHere cannot reach the notify URL.
- The PayHere payment reference is shown on the receipt.

**Setting up the sandbox** (each developer, locally):
1. On https://sandbox.payhere.lk: *Integrations → Add Domain/App*, type **Domain**, name `localhost`
   → note the **Merchant ID** and **Merchant Secret**.
2. *Settings → API Keys → Create API Key*: allowed domain `localhost`, **only** the
   "Payment Retrieval API" permission → note the **App ID** and **App Secret**.
3. Copy the `payhere:` block from `application.yml.example` into your own `application.yml` and
   fill in the four values (or set `PAYHERE_ENABLED=true` and the `PAYHERE_*` environment variables).
   Never commit them.
4. Pay a premium as a policyholder and use a PayHere sandbox test card.

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
