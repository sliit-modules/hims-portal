# Team Setup Guide — MediSure HIMS

Getting the project running on your machine from scratch, and the workflow to follow once
it is. If something below does not work, check [Common Problems](#common-problems) at the
bottom — most first-run issues are covered there.

**Repository:** https://github.com/sliit-modules/hims-portal

---

## 1. Prerequisites

| Requirement | Version | Notes |
|---|---|---|
| **JDK** | **17** | ⚠️ Not 21 or 26 — newer JDKs silently break Lombok |
| MySQL Server | 8.0+ | Must be running on `localhost:3306` |
| Maven | 3.8+ | |
| IntelliJ IDEA | any recent | With the **Lombok plugin** installed |

> **Why JDK 17 specifically:** on a newer JDK, Lombok's annotation processing fails without a
> clear error. The symptom is hundreds of `cannot find symbol: method getX()` errors across
> every entity, which looks like the code is broken when it is not.

---

## 2. Clone the repository

```bash
cd ~/Desktop
git clone https://github.com/sliit-modules/hims-portal.git
cd hims-portal
```

---

## 3. Configure your database password

`src/main/resources/application.yml` is git-ignored so nobody's password is ever committed.
Create your own from the template:

```bash
cp src/main/resources/application.yml.example src/main/resources/application.yml
```

Open it and replace `your_mysql_password_here` with your local MySQL password.

> Do **not** bother creating a `.env` file. Spring Boot does not read `.env` files — there is
> no dotenv library on the classpath, so it will have no effect. Use `application.yml`, or
> export `DB_USERNAME` / `DB_PASSWORD` as real environment variables.

You do not need to create the database by hand — the JDBC URL uses
`createDatabaseIfNotExist=true`, so `hims_db` is created on first start.

---

## 4. Set your git identity for this repository

Run these with **your own** name and SLIIT email so your commits are attributed correctly:

```bash
git config user.name "Your Name"
git config user.email "ITxxxxxxxx@my.sliit.lk"
```

| Member | Branch | Email |
|---|---|---|
| Lankadhikara L.R.M.M.P. | `feature/policy-management` | `IT25101639@my.sliit.lk` |
| Gunasinghe N.M. | `feature/claims-management` | `IT25101773@my.sliit.lk` |
| De Zoysa A.I. | `feature/underwriting-assessment` | `IT25102600@my.sliit.lk` |
| Ramanayaka U.K.D. | `feature/payment-management` | `IT25100714@my.sliit.lk` |
| Karunarathna W.M.K.U. | `feature/plan-management` | `IT25103657@my.sliit.lk` |
| Kavisekara K.M.H.N. | `feature/support-inquiries` | `IT25103510@my.sliit.lk` |

---

## 5. Switch to your branch and sync it — do not skip this

Every feature branch is behind `dev`. If you skip this step you will hit around **50 compile
errors** in the test files and assume you have broken something.

```bash
git checkout feature/your-branch
git merge origin/dev
```

The service test suites were originally written against methods that never existed
(`ClaimService.adjudicate()`, `User.setUsername()`, `ClaimStatus.PENDING`, and others), so
`mvn test` failed for everyone. That is fixed on `dev`, which is why you need to merge it in
before starting work.

---

## 6. IntelliJ configuration

1. **File → Open** → select the `hims-portal` folder. Maven imports automatically.
2. **⌘;** (File → Project Structure) → **Project** → set **SDK to 17** and language level 17.
3. **⌘,** (Settings) → Build, Execution, Deployment → Compiler → **Annotation Processors** →
   tick **Enable annotation processing**.
4. **⌘,** → Plugins → confirm **Lombok** is installed (restart if prompted).

---

## 7. Run the application

Open `src/main/java/com/medisure/hims/HimsPortalApplication.java` and click the green ▶ in the
gutter, or from a terminal:

```bash
mvn spring-boot:run
```

Then open **http://localhost:8080**

The first start takes roughly 30 seconds while Hibernate creates the schema and the seeder
populates a full year of demo data: members, insurance plans, policies with dependents,
premium payments, claims with generated supporting documents, support tickets and an audit
trail.

---

## 8. Demo logins

Staff sign in with their **work email**; members sign in with their **NIC**.
The password for every seeded account is `password123`.

| Role | Username | Module Owner |
|---|---|---|
| Insurance Sales Agent | `agent@medisure.lk` | Lankadhikara L.R.M.M.P. |
| Claims Officer | `claims@medisure.lk` | Gunasinghe N.M. |
| Underwriter | `underwriting@medisure.lk` | De Zoysa A.I. |
| Plan Administrator | `plans@medisure.lk` | Karunarathna W.M.K.U. |
| Customer Relations | `support@medisure.lk` | Kavisekara K.M.H.N. |
| **Policyholder** | `199408089876` | Ramanayaka U.K.D. |
| System Administrator | `admin@medisure.lk` | *(system account)* |
| Policyholder (demo member) | `199009098765` | — |

> **Note for Ramanayaka:** your login is a **NIC**, not an email. Premium & Payment Management
> is the one module whose owner persona is the Policyholder — paying a premium, viewing
> receipts, changing auto-pay and cancelling a payment are all member self-service actions.
> That account holds a real policy with existing payment history, so all four CRUD operations
> can be demonstrated against live cover.

---

## 9. Verify your setup

```bash
mvn test
```

Expected: `Tests run: 34, Failures: 0, Errors: 0` and `BUILD SUCCESS`.

---

## Day-to-day workflow

Work only on your own feature branch. Commit messages follow:

```
type(module): description [PBI-ID]
```

Types in use are `feat`, `fix` and `test`. Module tags are `policy`, `claims`,
`underwriting`, `payments`, `plans`, `tickets`. For example:

```
feat(claims): add duplicate claim detection [PBI07]
```

Before starting a new piece of work, sync with `dev` first:

```bash
git checkout feature/your-branch
git merge origin/dev
```

Merges into `dev` and `main` are handled by the Scrum Master (Karunarathna W.M.K.U.).

---

## Common problems

| Symptom | Cause and fix |
|---|---|
| Hundreds of `cannot find symbol: method getX()` | Wrong JDK. Set the project SDK to **17**, and confirm the Lombok plugin plus annotation processing are enabled. |
| ~50 errors inside `*ServiceTest.java` | You skipped step 5. Run `git merge origin/dev`. |
| `Web server failed to start. Port 8080 was already in use.` | Another instance is running. `pkill -f "spring-boot:run"`, wait a few seconds, then start again. |
| `Access denied for user 'root'@'localhost'` | Wrong password in `application.yml`, or you created a `.env` expecting it to be read — it is not. |
| Demo data looks wrong, or you want a clean slate | `DROP DATABASE hims_db;` then restart the app. The seeder only runs when the database is **empty**. |
| Claim documents fail to open | The database rows and the files in `uploads/` are out of sync. Drop the database, delete `uploads/claims/`, and restart to regenerate both together. |
| Login keeps failing | Staff use their **email**; policyholders use their **NIC**. |
