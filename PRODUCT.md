# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

(This record covers the web viewer in `web/`. The Android app in `app/` is the field tool and has its own Material 3 theme.)

## Users

NHA Bulacan District Office staff, all about equally:
- heads and managers at a desk, checking how every project is doing;
- site and admin engineers back from the field, checking their own projects;
- project reviews and status meetings, with the page shown on a shared screen or projector.

Roles come from the app: Super Admin, Engineer Admin, Field Engineer, Viewer, Super Viewer. Super Admin, Super Viewer and "See all projects" accounts see every project; others see only their assigned ones.

## Product Purpose

A read-only web view of the same live data the NHA Monitor phones sync to Firestore. Field engineers record weekly and monthly reports, payments, documents and issues on their phones. The web lets anyone with an account see the result on a bigger screen without a phone.

Success: opening the dashboard answers "how is the whole portfolio doing?" in a few seconds. That means project count, total contract cost and average progress, then which projects need a look.

## Positioning

It is not a separate system. It shows exactly what the phones recorded, with the phones' own rules (contract cost with approved variation orders, net of retention, days left). It never writes, so it cannot disturb field data.

## Operating Context

- Office desktops and laptops; also shared screens in meetings, so it must stay readable from a distance.
- Philippine peso amounts, often in the tens of millions; percentages to two decimals; dates as "Oct 20, 2026".
- Terms used by the users: Summary, Progress, Payments, Documents; weekly report, billing, variation order, time extension, suspension, open issues, contractor, implementing office.

## Capabilities and Constraints

- Read-only. Sign-in uses the app's Firebase email and password accounts.
- Screens now: sign-in, dashboard (portfolio), project Summary. Planned: Progress, Payments, Documents, weekly report printing.
- Plain static HTML, CSS and JavaScript modules, no build step, on Firebase Hosting (`nha-monitor-bulacan-2026.web.app`).
- English only.

## Brand Commitments

The web must look and feel like the Android app (user's decision, 2026-11-08, after seeing other directions): the same palette, type, components and screen layouts as `app/src/main/java/com/example/ui/`, adapted to a wide screen. The app's theme is the source of truth. No official NHA logo is supplied. Do not imitate official NHA or government branding.

## Evidence on Hand

Real data comes only from Firestore after sign-in. The local emulator seed (`web/emulator/seed.mjs`) has sample projects for testing. There are no screenshots, testimonials or marketing copy, and none should be invented.

## Product Principles

1. The portfolio first: totals and the projects that need attention come before detail.
2. Same numbers as the phones, always; a figure that differs from the app is a bug.
3. Read-only and calm: nothing on screen suggests the user can or must change data here.
4. Legible on a projector as well as a laptop.
