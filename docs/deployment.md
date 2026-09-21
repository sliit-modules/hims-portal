# Security & Deployment — PBI34

**Owner:** Karunarathna W.M.K.U. · Sprint 6

The Phase 2 ethics review found three gaps in how the portal protects medical data once it leaves
a developer's laptop: it only ran over plain HTTP, medical fields were stored as readable text, and
there were no backups. PBI34 closes them.

## 1. Hosting over HTTPS

HIMS is served at **https://hims.cresscent.site** from the team's host machine through a
**Cloudflare Tunnel**:

```
Browser ──HTTPS──▶ Cloudflare ──encrypted tunnel──▶ cloudflared on the host ──▶ HIMS (localhost:8080)
```

- **HTTPS** is handled by Cloudflare, so traffic is encrypted between the browser and Cloudflare, and
  inside the tunnel from Cloudflare to the host. The host opens no ports to the internet: the tunnel
  connects outwards.
- The app is told it sits behind HTTPS (`server.forward-headers-strategy: native`), so the session
  cookie is marked **Secure** and **HttpOnly** and every redirect stays on `https://`.
- PayHere's server-to-server payment confirmation reaches the app at the public address, so online
  payments are confirmed without any extra tooling.
- The **database admin panel** (phpMyAdmin) is on its own address and behind **Cloudflare Access**:
  only the six team members can reach it, after a one-time code sent to their university email.
  The public never sees its login page.
- The tunnel's routes live in a config file on the host (`~/.cloudflared/hims.yml`), not in this
  repository, and a login service keeps the tunnel running across restarts.

## 2. Encryption of medical data at rest

| Data | Where | Encrypted |
|---|---|---|
| Allergies, chronic conditions, current medications | members (`users`) and dependants (`dependents`) | ✅ |
| Pre-existing condition notes | underwriting applications | ✅ |
| Diagnosis summary | claims | ✅ |
| Supporting documents (bills, discharge sheets) | files under `uploads/claims/` | ✅ |
| Blood group, height, weight, "has pre-existing conditions" flag | | — by design, see below |

**How it works**

- **AES-256-GCM** (`security/FieldCipher`). Every value gets a fresh random nonce, so the same
  diagnosis never looks the same twice in the database, and GCM's authentication tag means a
  changed value is rejected instead of being shown as wrong data.
- **Transparent to the rest of the code.** The fields are marked
  `@Convert(converter = EncryptedStringConverter.class)`: Hibernate encrypts on save and decrypts on
  load, so services, pages and reports did not change. Documents are encrypted when uploaded and
  decrypted only when an allowed user opens them.
- **Storage format:** text is `enc:v1:` + Base64(nonce + ciphertext); files begin with `HIMSENC1`.
  The version tag leaves room for a future key rotation.
- **Existing data** is converted automatically: on start-up `SchemaUpgrade` widens the columns
  (ciphertext is about a third longer) and `EncryptionMigration` encrypts every value and file that
  is still plain, then records the count in the audit log. It is safe to run again — encrypted
  values are recognised and skipped.

**The key**

- A random 256-bit key per deployment, set as `app.encryption.key` in the local `application.yml`
  or the `HIMS_FIELD_KEY` environment variable — never in Git. Generate it with
  `openssl rand -base64 32`.
- The app **refuses to start without a valid key**, so medical data can never be saved as plain text
  by mistake.
- **The key must be kept safe and separate from the database** (for example in a password
  manager). Without it the medical data cannot be recovered; with it alone, nothing can be read
  without the database. This separation is the point: a leaked backup or database dump shows only
  ciphertext.

**Why some fields are not encrypted**

- The *has pre-existing conditions* flag drives the automatic underwriting risk score.
- Blood group is a fixed list shown to staff for emergencies; height and weight are low-risk
  numbers. Encrypting them would change their column types for little privacy gain, and they are
  still protected by role-based access and read-access logging (PBI28).

**Limits**

- Encrypted fields cannot be searched or sorted in SQL. Nothing in the app does so today; a future
  search on them would have to decrypt in the application.
- Encryption protects data at rest (a stolen disk, database file or backup). While the app is
  running it can read the data, so access control and audit logging remain the first line of
  defence.

**Evidence**

- Unit tests (`FieldCipherTest`): text and files round-trip; the same text encrypts differently each
  time; tampered data and the wrong key are rejected; old plain values still read; the app will not
  start without a valid key.
- End-to-end test: a claim filed through the web app is stored as ciphertext in the database and on
  disk, yet the member reads the diagnosis and downloads the original document.
- The switch-over was rehearsed on a copy of the live data before it ran for real
  (105 medical values and 86 documents encrypted, none left in plain text). In phpMyAdmin the
  columns show only `enc:v1:…`.

## 3. Encrypted backups

*To be added with the second half of PBI34.*
