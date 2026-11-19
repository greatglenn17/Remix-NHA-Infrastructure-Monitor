# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

(This record covers the website in `web/`. The Android app in `app/` is the field tool and has its own Material 3 theme.)

## Users

NHA Bulacan District Office staff, all about equally:
- heads and managers at a desk, checking how every project is doing;
- site and admin engineers back from the field, checking and updating their own projects at a desk;
- project reviews and status meetings, with the page shown on a shared screen or projector.

Roles come from the app: Super Admin, Engineer Admin, Field Engineer, Viewer, Super Viewer. Super Admin, Super Viewer and "See all projects" accounts see every project; others see only their assigned ones.

## Product Purpose

The same live data the NHA Monitor phones sync to Firestore, on a bigger screen. Field engineers record weekly and monthly reports, payments, documents and issues on their phones; the website shows the result to anyone with an account, and lets the people the app lets edit do the desk work there too: weekly reports, billings, abstracts, orders and letters, plans and lots, and the Blue Book prints.

Success: opening the dashboard answers "how is the whole portfolio doing?" in a few seconds. That means project count and how many projects are on-going, completed or late, then which projects need a look. Work done, billed and contract cost are read per project, never averaged or totalled.

## Positioning

It is not a separate system. It shows exactly what the phones recorded, with the phones' own rules (contract cost with approved variation orders, net of retention, days left). Every change it saves is written the way a phone writes one (revision up, with the app's audit entry), so phones take it like another phone's edit.

## Operating Context

- Office desktops and laptops; also shared screens in meetings, so it must stay readable from a distance.
- Philippine peso amounts, often in the tens of millions; percentages to two decimals; dates as "Oct 20, 2026".
- Terms used by the users: Summary, Progress, Actual Accomplishment, Payments, Documents; weekly report, abstract, billing, variation order, time extension, suspension, open issues, lot, subdivision plan, contractor, implementing office.

## Capabilities and Constraints

- Sign-in and Create account use the app's Firebase email and password accounts. Who may edit what follows the phone's roles and project assignments; viewers only read.
- Screens: sign-in and sign-up, dashboard (portfolio), the five project tabs (Summary, Progress, Actual Accomplishment, Payments, Documents), Profile, Users, and A4 prints (weekly and monthly reports, Project Profile, abstracts, the subdivision plan).
- Plain static HTML, CSS and JavaScript modules, no build step, on Firebase Hosting (`nha-monitor-bulacan-2026.web.app`).
- English only.

## Brand Commitments

The web keeps the Android app's type, components and screen layouts (`app/src/main/java/com/example/ui/`), adapted to a wide screen (user's decision, 2026-11-08). Its colours follow the user's later "Architectural Command Center" brief the same day: a #0B0F17 canvas with a blueprint dot grid, #111827 panels with white 8% hairlines, always dark. No official NHA logo is supplied. Do not imitate official NHA or government branding.

## Evidence on Hand

Real data comes only from Firestore after sign-in. The local emulator seed (`web/emulator/seed.mjs`) has sample projects for testing. There are no screenshots, testimonials or marketing copy, and none should be invented.

## Product Principles

1. The portfolio first: totals and the projects that need attention come before detail.
2. Same numbers as the phones, always; a figure that differs from the app is a bug.
3. Calm and safe to edit: edit buttons show only to the people the app lets edit, and every save is the write a phone would make.
4. Legible on a projector as well as a laptop.
