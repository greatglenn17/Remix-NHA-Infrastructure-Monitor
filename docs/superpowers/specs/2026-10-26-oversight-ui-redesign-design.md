# Oversight UI redesign ("command center")

Date: 2026-10-26
Status: approved design, not implemented

## Goal

The Super Admin sees where every project stands without opening each one: schedule
slippage, billing against progress, pending approvals, and deadlines and field activity.
Today the dashboard is a grid of fully tinted cards, the project screen has 10 tabs in a
sideways-scrolling strip, and the Payments tab is an 820dp table that scrolls sideways.

## Decisions (from the owner)

- Main goal is oversight. All four signals matter: slippage, billing vs progress,
  pending approvals, deadlines and activity.
- Devices: the Super Admin on a tablet (Phone A, about 743dp wide upright, 1185dp
  sideways), engineers on phones.
- Light theme becomes the default. Dark stays available from the existing theme switch.
- Direction A ("command center") over a visual refresh only (B) or a timeline-first
  home (C).
- Thresholds in "Needs attention rules" below are confirmed as written.

## Unchanged

- Room database, Firestore sync, roles, permissions and project assignment.
- Every field and validation rule in the forms. Only their layout changes.
- Navigation stays state-based in `MainActivity` (no project selected = portfolio,
  selected = project; Back clears the selection). `navigation-compose` stays unused.
- Release process: version bump, debug-signed APK, GitHub release, mandatory update via
  `update.json`.

## Stages

| Release | Contents |
| --- | --- |
| v1.7.0 | Design system and theme guard. Portfolio home. Top bar and shared components moved to the theme. Old dashboard card removed. |
| v1.8.0 | Project detail in 4 sections. Two-pane tablet layout. Payments as cards. |
| v1.9.0 | Login, all add/edit dialogs, Subdivision Plan and Weather moved to the theme. Long forms full-screen with steps. Old `Dark*` constants deleted. |

Each release ships on its own and passes the testing below.

## Design system

Found: `ui/theme/Theme.kt` defines light and dark Material 3 schemes and the account menu
has a System/Light/Dark switch (default System). The `DarkBackground`/`DarkSurface`/... names
used by 26 files are theme-aware getters over `LocalNHAColors`, so those surfaces already
follow the switch. What does not adapt is the 413 hand-written `Color(0x...)` values outside
`ui/theme`.

- Screens take colours only from `MaterialTheme.colorScheme` and status roles added to the
  existing `NHAColors` (`LocalNHAColors`): on track, watch, behind, neutral, each with
  light and dark values.
- Light palette:

  | Role | Light | Use |
  | --- | --- | --- |
  | Background / surface | `#F8FAFC` / `#FFFFFF` | page, cards |
  | Text / secondary | `#0F172A` / `#475569` | body, labels |
  | Outline | `#E2E8F0` | borders, dividers |
  | Primary | `#0369A1` | buttons, links, billed bar |
  | On track | `#15803D` | positive variance |
  | Watch | `#B45309` | billing ahead, due soon, pending approval |
  | Behind | `#B91C1C` | critical slippage, overdue |
  | Neutral | `#64748B` | suspended, stale updates |

  Dark mode uses matching dark values for each role.
- Cards are white with a 1dp outline. Status is a small badge plus bar colour, not a fully
  tinted card with a 2dp coloured border.
- Type: system font; a scale for large count numbers, titles, body and labels. Money and
  percentages use tabular digits (`fontFeatureSettings = "tnum"`).
- Spacing on a 4dp grid; 12dp card corners, 8dp control corners.
- Accessibility: WCAG AA text contrast in both themes, 48dp minimum touch targets, status
  always paired with an icon or text (never colour alone).
- The old `Dark*` constants stay until each screen is moved in its stage, then are deleted
  in v1.9.0.

## Portfolio home (v1.7.0)

Layout, top to bottom:

1. Header: office name and project count. (The app keeps no Firestore last-sync time; the
   top bar already shows sync status.)
2. Four counts: Behind schedule, Billing ahead of work, Waiting approval, Due in 30 days.
   Tapping a count filters the project list to those projects.
3. Needs attention: up to 5 items, red then amber then grey, each opening its project;
   "See all" shows the rest.
4. All projects: compact rows with name, location, status badge, variance, work-done bar
   and billed bar side by side, completion date and pending-document count. Search,
   filters and sort stay; the default sort is "Needs attention first".

Phones: single column, counts in a 2×2 grid, the two bars stacked. Engineers see the same
screen limited to their assigned projects, as today.

The billed bar uses `BillingProgress.billedFraction` with `ContractCost.current`, as in
v1.6.0.

## Needs attention rules

One pure function, `ProjectAttention`, takes a project, its pending documents, its
payments total, its latest weekly report time and today's date, and returns its flags.

| Signal | Rule | Level |
| --- | --- | --- |
| Behind schedule | −15% < slippage (actual − target) ≤ −10%, or status "Behind Schedule" | amber (warning) |
| Behind schedule | slippage ≤ −15% | red (critical) |
| Billing ahead of work | billed % − actual % > 10 percentage points; skipped when contract cost is 0 | amber |
| Waiting approval | a Variation Order, Time Extension, Work Suspension or Work Resumption document not Approved | amber |
| Due soon | 0 ≤ days until effective completion date (revised if set, `ProjectSchedule.effectiveCompletion`) ≤ 30 and actual < 100% | amber |
| Overdue | effective completion date before today and actual < 100% | red |
| No recent update | 14 or more days since the latest weekly report (since the project start when there is none); suspended projects skipped | grey |

- Slippage above −10% (e.g. −9.9%) is not flagged; it shows as the variance on the row.
- Boundaries follow RA 9184's "10% or more" / "15% or more" slippage wording.
- The "Waiting approval" count includes every document not Approved; the attention item
  only covers the four schedule- and cost-changing order types.
- There is no "Completed" status; done means actual accomplishment of 100%.
- "Last field update" is the newest `WeeklyReport.createdAtTimestamp`. It replaces the
  card's "Last Update", which shows `Project.lastUpdatedDate`; that field is never
  updated and always shows its 2026-08-01 default.
- Thresholds are constants in `ProjectAttention`.

## Navigation and tablet layout (v1.8.0)

| Width | Layout |
| --- | --- |
| 840dp and up (Phone A sideways) | Two panes: portfolio list (about 380dp) and the selected project. Selecting another project swaps the right pane. With no selection, the right pane shows Needs attention. |
| 600–840dp (Phone A upright) | One pane at a time; screens use the width (4 counts in a row, project detail in 2 columns). |
| Under 600dp (phones) | One pane at a time, single column. |

- Top bar keeps sync status, notifications and the account menu. Sort and filter move into
  the portfolio list header.
- Back from a project returns to the portfolio; on two panes it clears the selection.
- The project's 4 sections sit in a fixed bar with no sideways scrolling. Inside a section,
  chips switch views.
- The last used section is remembered when switching projects.
- "New Project" shows on the portfolio only.

## Project detail (v1.8.0)

Header on every section: name, location, status badge, variance, work-done and billed
bars; collapses to one line on scroll.

| Section | Contents | From |
| --- | --- | --- |
| Summary | this project's attention flags; key dates (start, original and revised completion, days left); original and current contract cost; latest weekly report snapshot; open issues count; master profile (office, contractor, scope, type, land area, units) | Overview top half |
| Progress | chips: Weekly, Monthly, Lots (only when the project has a lot plan), Photos, Weather, Issues | Weekly, Monthly, Subdivision Plan, Gallery, Weather 24h, Issues |
| Money | billing summary (current contract cost, total billed, % billed vs % work done, balance); payments as cards; variation orders log with Add and Additive/Deductive type | Payments; VO log from Overview |
| Documents | chips: Checklist (waiting approval first), Schedule orders (time extensions, suspensions, resumptions), Files, Audit log | Pending Docs; logs and Related Files from Overview; Audit Log |

Payment cards: billing name and DV number; date and period; gross amount with % of
contract; balance after this billing; paperclip when a scanned DV is attached; edit and
delete in a ⋮ menu. Two columns at 600dp and up. The grand-total row becomes the billing
summary.

Every action keeps today's role checks, including that only an Engineer Admin deletes a
non-core document. Add/edit forms keep their current dialogs until v1.9.0.
`OverviewTab.kt` (1,134 lines) is split across the sections.

## Phones and forms (v1.9.0)

- Main actions sit within thumb reach at the bottom ("Add weekly report" on Progress,
  "Add billing" on Money).
- Long forms open full-screen on phones: weekly report, monthly report, create/edit
  project, lot inspection. The weekly report becomes steps: week and progress; manpower
  and equipment; activities and issues; photos and documents; review and save.
- Short forms (add billing, add document, variation order) stay as dialogs or bottom
  sheets.
- Date pickers replace typed YYYY-MM-DD; number keyboards for amounts; errors next to
  the field; a fixed Save button.
- Closing a form with unsaved changes asks "Discard changes?".

## Testing

- Unit tests for `ProjectAttention`, including each edge: slippage −9.9%, −10% (amber),
  −15% (red); billing gap exactly 10 points (not flagged); exactly 30 days to completion
  (flagged); exactly 14 days since the last report (flagged); zero contract cost;
  suspended projects. Existing `BillingProgressTest` and
  `ContractCostTest` keep passing.
- Theme guard test: fails if `Color(0x` appears in a moved screen outside `ui/theme`; the
  list of exempt files shrinks each stage and is empty in v1.9.0.
- Screenshot tests with Roborazzi (already a test dependency): portfolio home, each
  project section and payment cards, in light and dark, at phone (411dp) and tablet
  (743dp and 1185dp) widths, from fixed sample data.
- Existing test tags (`project_tile_…`, `create_project_fab`, `search_project_input`, ...)
  are kept.
- `./gradlew -q testDebugUnitTest lintDebug assembleDebug` passes.
- Device check: Phone A (Super Admin) upright and sideways, light and dark; Phone B
  (Engineer Admin); update install over the previous release.

## Out of scope

- Timeline (Gantt) view; may come later as a view inside the portfolio.
- A cross-project approvals inbox.
- New data fields, sync changes, or permission changes.
- Release signing changes (APKs stay signed with the repo debug key, as v1.5.1 and v1.6.0).
