# Accessibility Review (WCAG 2.1 AA) — PBI33

**Sprint 6 · whole team** · reviewed 21 Sep 2026

MediSure HIMS is used by members of the public, including older policyholders and people with
disabilities, so the portal was reviewed against the **Web Content Accessibility Guidelines (WCAG)
2.1, level AA** — the level most accessibility laws and public-sector policies refer to.

## How it was tested

| Step | What was done |
|---|---|
| Pages | 37 pages covering every role: sign-in and registration, all six dashboards, and the list, form and detail pages of all six modules, plus the printable receipt, certificate and report |
| Automated audit | [axe-core 4.10.2](https://github.com/dequelabs/axe-core) (the engine behind Lighthouse and many browser checkers) on every page, with the WCAG 2.0/2.1 A and AA rules and the axe best-practice rules |
| Screen sizes | Every page checked at 320 px (a phone, and the width WCAG uses for 400% zoom), 768 px (tablet, or 200% zoom on a laptop) and 1280 px (laptop) |
| Manual checks | Keyboard-only use (skip link, focus order, visible focus), page reflow, charts, and colour used as the only signal — the things automated tools cannot judge |

Automated tools find only part of the problems, so the manual checks above were done as well.

## Results

| | Before | After |
|---|---|---|
| Pages with WCAG failures | **37 of 37** | **0 of 37** |
| Failing rules | 4 (below) | none |
| Best-practice warnings (heading order, missing `<h1>`, landmarks, empty headers) | 5 kinds, found once the WCAG failures were fixed | none |
| Pages that scroll sideways at 320 px | every page with the top bar (it alone was 469 px wide) | 0 |
| Navigation on phones / at 200% zoom | none (the menu was hidden) | menu strip under the top bar |
| Dashboard charts with a text alternative | 0 of 12 | 12 of 12 |

Failures found at the start:

| axe rule | Impact | Pages | Elements |
|---|---|---|---|
| `color-contrast` — text below 4.5:1 | serious | 35 | 269 |
| `html-has-lang` — page language missing | serious | 37 | 37 |
| `label` — form field with no label | critical | 14 | 44 |
| `select-name` — dropdown with no name | critical | 14 | 20 |

## What was changed, by WCAG success criterion

| Criterion | Problem | Fix |
|---|---|---|
| **1.1.1** Non-text content | Dashboard charts were images with no text | Each chart gets `role="img"` and a description with its title and every figure, e.g. "Claim Volume, last 12 months: Oct 0, Nov 6, …" |
| **1.3.1** Info and relationships | 73 labels were not linked to their fields; page titles were `<h3>` and card titles `<h6>`; empty table headers | Labels linked with `for`/`id`; one `<h1>` per page with `<h2>` card titles (same look); action columns named "Actions" for screen readers |
| **1.3.1 / 2.4.1** Structure | No landmarks on some pages | Top bar is a `<header>`, the menu a `<nav aria-label="Main menu">`, the content a `<main>` on every page |
| **1.4.3** Contrast (minimum) | Pale greys, status badges (green, amber, red), and Bootstrap's outline buttons | Colours darkened to at least 4.5:1 while keeping their hue, e.g. success `#16a34a` → `#15803d`, warning `#d97706` → `#b45309`, grey text `#64748b` → `#5b6779` |
| **1.4.10** Reflow | Below 992 px the menu disappeared; tables, profile tiles and printable documents made pages scroll sideways | Menu becomes a scrolling strip; wide tables scroll inside their own box; documents stack into one column on phones |
| **2.1.1** Keyboard | Scrolling tables with no links could not be scrolled from the keyboard | Those areas can take focus (`tabindex="0"`, `role="region"` with a name) |
| **2.4.1** Bypass blocks | No way to skip the menu | "Skip to main content" link, shown when focused |
| **2.4.7** Focus visible | Focus was hard to see | 3 px outline in the brand colour on every link, button and field |
| **3.1.1** Language of page | No `lang` | `<html lang="en">` on all 38 pages |
| **3.3.2** Labels or instructions | Sign-in and registration used placeholders only, which vanish while typing | Every field has an accessible name |
| **4.1.2** Name, role, value | Icon-only buttons (notifications, change password, log out) were named only by a tooltip | `aria-label` on each; the current menu item is marked `aria-current="page"` |
| **4.1.3** Status messages | Errors and confirmations were not announced | Error alerts use `role="alert"`, confirmations `role="status"` |

Animations are switched off for people who ask their system for reduced motion.

## Checked and already fine

- **Colour is never the only signal.** Status badges always show the word (APPROVED, REJECTED…),
  and the claims ratio shows the percentage as well as its colour.
- **Images** — the app has no content images, only an icon font; the icons on the header buttons are
  hidden from screen readers because the buttons are named instead.
- **Confirmation prompts** use the browser's own dialog, which screen readers and keyboards already
  support.

## Screen-reader test

A manual pass with **VoiceOver** (macOS, Safari) on the live site on 21 Sep 2026, by Gunasinghe N.M.
— **all checks passed**:

| Page | Checked |
|---|---|
| Sign-in | page title announced; both fields read their names ("NIC (members) or email (staff)", "Password"); a failed sign-in's error is read out without moving focus |
| Dashboard | "Skip to main content" is the first Tab stop and jumps past the menu; one level-1 heading then level-2 headings; banner, "Main menu" navigation and main landmarks; the premium chart reads its title and every figure; header buttons read "Notifications", "Change password", "Log out" |
| File a Claim | every field reads its label and type; validation errors are read out |
| Claims list | table cells are read with their column headers; the action column is announced as "Actions" |

## Known limits

- **PayHere's checkout page** is run by PayHere, not by us, so it is outside this review.
- **The top search box** has no function yet; it is labelled but does nothing.

## Re-running the audit

1. Start the app and open any page.
2. In Chrome DevTools → **Lighthouse** → *Accessibility*, or install the free *axe DevTools* extension
   and click **Scan**.
3. Check at phone size too (DevTools → device toolbar, 320 px wide).
