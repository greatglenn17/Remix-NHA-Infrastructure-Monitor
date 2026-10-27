# Oversight UI v1.8.0 (project detail and tablet layout) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the 10-tab project screen with 4 fixed sections (Summary, Progress, Money, Documents) under a collapsing project header, show payments as cards, and add the two-pane tablet layout.

**Architecture:** `ProjectDetailScreen` stays the stateful screen (collects the view model, owns the dialogs) and renders a stateless `ProjectHeader`, a fixed `TabRow` of `ProjectSection`s, and one section composable per section under `ui/detail/sections/`. Progress and Documents are chip rows over the existing tab composables. `OverviewTab.kt` is split: its cards move into Summary, Money and Documents, and only its two dialogs remain. The chosen section lives in `ProjectViewModel` so it survives switching projects. `MainActivity` picks one or two panes from `LocalConfiguration.current.screenWidthDp`.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Robolectric, Roborazzi 1.59.0.

**Spec:** `docs/superpowers/specs/2026-10-26-oversight-ui-redesign-design.md` (sections "Stages", "Navigation and tablet layout (v1.8.0)", "Project detail (v1.8.0)", "Design system", "Testing", "Out of scope").

## Global Constraints

- No Room schema change, no new data fields, no sync or permission changes (spec "Out of scope").
- Every role check stays exactly as today. In particular: deleting a non-core checklist document needs `!doc.isCoreChecklist && currentUserRole == UserRole.ENGINEER_ADMIN` (`PendingDocumentsTab.kt:303`); long-press delete of a related file needs `Permission.UPLOAD_DOCUMENT`; Add Variation Order needs `Permission.UPLOAD_DOCUMENT`; Edit project needs `Permission.EDIT_PROJECT`; Audit log needs `Permission.VIEW_AUDIT_LOGS`; weekly/monthly/issue checks stay inside their tabs. Payments have no role check today and get none.
- Add/edit forms keep their current dialogs (`AddPaymentDialog`, `ViewDvScanDialog`, `AddRelatedDocumentDialog`, `ViewScannedCopyDialog`, and everything in `ui/dialogs/`). They move to the theme in v1.9.0.
- Navigation stays state-based in `MainActivity`; `navigation-compose` stays unused. `BackHandler(enabled = selectedProjectId != null) { viewModel.selectProject(null) }` (`MainActivity.kt:87`) stays and covers both layouts.
- Widths: two panes at `screenWidthDp >= 840` (list pane 380dp); "use the width" at `600 <= width < 840`; single column under 600.
- Theme default stays System. Colours in moved files only from `MaterialTheme.colorScheme` and `LocalNHAColors`; money and percentages use `TabularNumbers`; 12dp card corners, 8dp control corners, 1dp outline; touch targets ≥ 48dp; status always with text or an icon.
- Keep test tags: `edit_project_button`, `add_related_file_button`, `add_variation_button`, `add_custom_document_button`, `audit_*`, and `detail_tab_<index>` (now on the 4 section tabs, index 0–3).
- Gate for every task: `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"; ./gradlew -q testDebugUnitTest lintDebug assembleDebug` passes. Do not set `MSYS_NO_PATHCONV`. The worktree needs `local.properties`, `debug.keystore` and `.env` copied from the main checkout first.
- Commit messages end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. No version bump, push, tag or release.

## Decisions to confirm

1. **Payment edit.** The spec puts "edit and delete in a ⋮ menu", but there is no edit-payment form today (`onUpdatePayment` only attaches a scan). Plan: the ⋮ menu has "Attach scan"/"Replace scan" and "Delete"; editing a billing waits for v1.9.0's forms, since it needs a new form mode and recomputing later balances.
2. **Lots chip** shows for Housing projects (today's Subdivision Plan rule), not only when a lot plan exists, because the first plan is uploaded from that chip.
3. **Theme guard.** `ThemeGuardTest` keeps a list of migrated files (`MIGRATED`) that grows, rather than an exempt list that shrinks; same effect. This stage adds the new section files, `ProjectDetailScreen.kt`, and the six small tabs shown inside sections (Weekly, Monthly, Issues, Gallery, Pending Docs, Audit Log: 32 hand-set colours in total). Subdivision Plan, Weather and the dialogs stay for v1.9.0, as the spec's Stages table says.

## Review Focus

1. Switching from a Housing project with the Lots chip open to a non-Housing one: chip falls back to Weekly, no crash. Pinned in Task 1 (`chipOrFirst`).
2. Contract cost 0 or no payments: billing summary shows "No billings yet", no division by zero, no NaN%. Pinned in Task 5 screenshot `money_empty`.
3. A Field Engineer: no Add Variation Order, no long-press delete on Files, no Audit log chip; only an Engineer Admin sees Delete on a non-core checklist document. Pinned by keeping the existing condition code moved verbatim (Tasks 3–5) and the Task 1 chip test.
4. 90-character project name on a 411dp phone with the header collapsed: one line, ellipsized. Pinned in Task 8 screenshot `header_collapsed`.
5. Tablet rotated 1185 → 743 with a project open: single pane on the project; Back returns to the portfolio. On two panes Back clears the selection and the right pane shows Needs attention. Pinned in Task 7 device check and screenshot `two_pane_empty`.

---

### Task 1: Section model and revised completion

**Files:**
- Create: `app/src/main/java/com/example/ui/detail/ProjectSection.kt`
- Modify: `app/src/main/java/com/example/util/ProjectSchedule.kt`
- Modify: `app/src/main/java/com/example/ui/viewmodel/ProjectViewModel.kt` (next to `_selectedProjectId`, line 123)
- Test: `app/src/test/java/com/example/ProjectSectionTest.kt`, `app/src/test/java/com/example/ProjectScheduleTest.kt`

**Interfaces:**
- Produces:

```kotlin
enum class ProjectSection(val label: String) { SUMMARY("Summary"), PROGRESS("Progress"), MONEY("Money"), DOCUMENTS("Documents") }

object SectionChips {
    fun progress(isHousing: Boolean): List<String>         // Weekly, Monthly, [Lots], Photos, Weather, Issues
    fun documents(canViewAuditLogs: Boolean): List<String> // Checklist, Schedule orders, Files, [Audit log]
    fun chipOrFirst(chips: List<String>, chip: String?): String
}

// ProjectSchedule: approved Time Extension days + approved Work Suspension days added to the contract duration
fun revisedCompletion(dateStarted: String, contractDurationDays: Int, documents: List<PendingDocument>): String?  // null = no approved extension or unparseable start

// ProjectViewModel
val selectedSection: StateFlow<ProjectSection>   // default SUMMARY; not reset by selectProject
fun setSection(section: ProjectSection)
```

- [ ] **Step 1: Write the failing tests.** `progress_housingAddsLots` (`progress(true)` == `["Weekly","Monthly","Lots","Photos","Weather","Issues"]`, `progress(false)` has no "Lots"); `documents_auditOnlyWithPermission`; `chipOrFirst_unknownChipFallsBackToFirst` (`chipOrFirst(progress(false), "Lots") == "Weekly"`, `null` → "Weekly"). In `ProjectScheduleTest`: `revisedCompletion_addsApprovedExtensionAndSuspensionDays` (start 2026-01-01, 100 days, approved "Time Extension No. 1 - 30 Days" and approved Work Suspension with remarks "Duration: 10 Days" → `"2026-05-21"`); `revisedCompletion_ignoresPending` (→ `null`); `revisedCompletion_blankStart_isNull`.
- [ ] **Step 2: Run** `./gradlew -q testDebugUnitTest --tests com.example.ProjectSectionTest --tests com.example.ProjectScheduleTest`. Expected: FAIL (unresolved).
- [ ] **Step 3: Implement.** `revisedCompletion` uses the two regexes from `OverviewTab.kt:241-249` unchanged. Replace both copies (Overview and the `ProjectDetailScreen` header) with calls to it.
- [ ] **Step 4: Run** the gate. Expected: PASS.
- [ ] **Step 5: Commit** `feat: project sections model; one revised-completion helper`.

---

### Task 2: Detail shell: collapsing header, fixed section bar, chips

**Files:**
- Create: `app/src/main/java/com/example/ui/detail/sections/ProjectHeader.kt`
- Create: `app/src/main/java/com/example/ui/detail/sections/ChipRow.kt`
- Modify: `app/src/main/java/com/example/ui/detail/ProjectDetailScreen.kt`

**Interfaces:**
- Consumes: Task 1.
- Produces:

```kotlin
@Composable fun ProjectHeader(project: Project, billedFraction: Float?, collapsed: Boolean, modifier: Modifier = Modifier)
@Composable fun ChipRow(chips: List<String>, selected: String, onSelect: (String) -> Unit)  // FilterChips, wraps with FlowRow, never scrolls sideways
// ProjectDetailScreen gains: embedded: Boolean = false   (true = no Scaffold top bar; used by two panes in Task 7)
```

- [ ] **Step 1: Build `ProjectHeader`.** Expanded: status badge, variance badge (`+1.2%` / `−12.4%`, colours as `PortfolioRow`), name (2 lines max), location, "Work done" and "Billed" bars. Collapsed: one row with name (1 line, ellipsis), status badge and variance. Reuse `Badge`, `levelColor` and the bar from `ui/dashboard/PortfolioRow.kt`: make `ProgressLine`, `StatusBadge`, `varianceColor`, `formatVariance` `internal` instead of copying them. `billedFraction` = `BillingProgress.billedFraction(payments.sumOf { it.grossAmount }, ContractCost.current(...))`.
- [ ] **Step 2: Rebuild the screen body.** Header, then a non-scrolling `TabRow` of `ProjectSection.entries` (tags `detail_tab_<ordinal>`, selected from `viewModel.selectedSection`), then the section. Collapse with a `NestedScrollConnection` on the section box: `onPreScroll` with `available.y < 0` sets `collapsed = true`; `onPostScroll` with `available.y > 0` (child at top) sets it false; reset to expanded on project or section change. Progress and Documents keep their chip in `rememberSaveable(currentProject.id)` and pass it through `chipOrFirst`. Wire chips to the existing tab composables with today's arguments; Summary temporarily shows `OverviewTab`, Money `PaymentsTab` (replaced in Tasks 3–5); Documents' Checklist gets `pendingDocuments.sortedBy { it.status.equals("Approved", true) }`; Audit log chip only with `VIEW_AUDIT_LOGS`. Loading spinner uses `colorScheme.primary`; scaffold uses `colorScheme.background`.
- [ ] **Step 3: Run** the gate. Expected: PASS.
- [ ] **Step 4: Commit** `feat(ui): project detail in 4 sections with a collapsing header`.

---

### Task 3: Summary section (Overview top half)

**Files:**
- Create: `app/src/main/java/com/example/ui/detail/sections/SummarySection.kt`
- Modify: `app/src/main/java/com/example/ui/detail/tabs/OverviewTab.kt` (remove the master profile card, lines 114–292), `ProjectDetailScreen.kt`

**Interfaces:**
- Consumes: `ProjectAttention.flags`, `ProjectSchedule.revisedCompletion`, `ProjectSchedule.elapsedAndRemaining`, `ContractCost.original` / `current`.
- Produces:

```kotlin
@Composable fun SummarySection(
    project: Project,
    flags: List<AttentionFlag>,
    documents: List<PendingDocument>,
    latestReport: WeeklyReport?,       // newest by createdAtTimestamp
    openIssues: Int,                   // status not Resolved/Closed, as IssuesTab's filter
    canEditProject: Boolean,
    onEditProject: () -> Unit
)
```

- [ ] **Step 1: Build `SummarySection`** as a `LazyColumn` of cards: "Needs attention" (each flag with level icon and message; "Nothing needs attention" when empty); "Key dates" (Date started, Original completion, Revised completion or "-", Days left from `elapsedAndRemaining` against the effective completion); "Contract cost" (Original, Current); "Latest weekly report" (reporting week, target vs actual %, submitted by; "No weekly reports yet"); "Open issues" count; "Project profile" (office, contractor, scope of works list, type, land area and units for Housing, duration) with the Edit icon tagged `edit_project_button` when `canEditProject`. At `maxWidth >= 600.dp` cards sit in 2 columns (`BoxWithConstraints`, pairs per row). Flags come from `ProjectAttention.flags(project, documents, billedTotal, latestReport date in ZoneId.systemDefault(), LocalDate.now())` computed in the screen.
- [ ] **Step 2: Delete** the master profile card from `OverviewTab`; Summary replaces `OverviewTab` in the screen. `DetailItem` moves to `SummarySection.kt`.
- [ ] **Step 3: Run** the gate. Expected: PASS.
- [ ] **Step 4: Commit** `feat(ui): Summary section`.

---

### Task 4: Documents section (logs and related files)

**Files:**
- Create: `app/src/main/java/com/example/ui/detail/sections/DocumentsSection.kt` (`ScheduleOrdersList`, `RelatedFilesList`)
- Modify: `OverviewTab.kt` → `git mv` to `app/src/main/java/com/example/ui/detail/tabs/DocumentDialogs.kt`, keeping only `AddRelatedDocumentDialog` and `ViewScannedCopyDialog`
- Modify: `ProjectDetailScreen.kt`

**Interfaces:**
- Produces:

```kotlin
@Composable fun ScheduleOrdersList(documents: List<PendingDocument>)   // Time extension, Work suspension, Work resumption logs
@Composable fun RelatedFilesList(
    documents: List<PendingDocument>,
    currentUserRole: UserRole,
    onAddRelatedDocument: (String, String, String, String) -> Unit,
    onUpdateDocumentFileUrl: (PendingDocument, String) -> Unit,
    onDeleteDocument: (Long) -> Unit
)
```

- [ ] **Step 1: Move** the Time Extension, Work Suspension and Work Resumption log cards (same filters: name contains the type and status Approved or Submitted) into `ScheduleOrdersList`, and the Related Files card with its add dialog, scan viewer and delete confirmation into `RelatedFilesList`. Related files become rows of title, status badge, details and a paperclip/upload button (no 4-column table); long-press delete stays gated by `UPLOAD_DOCUMENT`; tag `add_related_file_button` kept. Theme colours only.
- [ ] **Step 2: Wire** the Schedule orders and Files chips. The Variation Order log stays in `OverviewTab` until Task 5.
- [ ] **Step 3: Run** the gate. Expected: PASS.
- [ ] **Step 4: Commit** `feat(ui): Documents section takes the order logs and related files`.

---

### Task 5: Money section and payment cards

**Files:**
- Create: `app/src/main/java/com/example/ui/detail/sections/MoneySection.kt` (`MoneySection`, `BillingSummary`, `VariationOrdersLog`)
- Create: `app/src/main/java/com/example/ui/detail/sections/PaymentCard.kt`
- Modify: `app/src/main/java/com/example/ui/detail/tabs/PaymentsTab.kt` (delete `PaymentsTab`; keep `AddPaymentDialog`, `ViewDvScanDialog`, `currentMonthPeriod`); rename to `PaymentDialogs.kt` with `git mv`
- Modify: `DocumentDialogs.kt` (the VO log leaves; file is dialogs only), `ProjectDetailScreen.kt`

**Interfaces:**
- Produces:

```kotlin
@Composable fun MoneySection(
    payments: List<ProjectPayment>,
    contractCost: Double,                         // ContractCost.current(...)
    actualPercent: Double,                        // project.actualAccomplishment
    documents: List<PendingDocument>,
    canAddVariationOrder: Boolean,                // UPLOAD_DOCUMENT, as today
    onAddVariationOrder: () -> Unit,
    onAddPayment: (String, String, String, String, Double, Double, Double, Double, String) -> Unit,
    onUpdatePayment: (ProjectPayment) -> Unit,
    onDeletePayment: (ProjectPayment) -> Unit
)
@Composable fun PaymentCard(payment: ProjectPayment, onViewScan: () -> Unit, onAttachScan: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier)
```

- [ ] **Step 1: Build `BillingSummary`:** Current contract cost, Total billed, "% billed" vs "% work done" bars, Balance (`contractCost − totalBilled`). Percent billed only when `contractCost > 0`; with no payments show "No billings yet". One "Add billing" button (no FAB, no second button). Add-payment maths stays exactly as `PaymentsTab.kt:360-365`.
- [ ] **Step 2: Build `PaymentCard`:** name and DV number ("N/A" when blank); date and period; gross amount with `"%.2f%% of contract"`; "Balance after: …" with its %; a paperclip `IconButton` (48dp, "View scanned DV") when `fileUrl` is set; ⋮ menu with "Attach scan" or "Replace scan" and "Delete" (see Decisions 1). Cards in 2 columns at `maxWidth >= 600.dp`.
- [ ] **Step 3: Move** the Variation Orders log from `OverviewTab` into `VariationOrdersLog` (same filter, Pending listed too; each entry shows Additive/Deductive from its name, status and date approved; Add button tag `add_variation_button` when `canAddVariationOrder`). The file left from `OverviewTab` now holds only the two dialogs.
- [ ] **Step 4: Run** the gate. Expected: PASS. `grep -rn "820.dp\|OverviewTab\|PaymentsTab(" app/src/main` returns nothing.
- [ ] **Step 5: Commit** `feat(ui): Money section with payment cards and billing summary`.

---

### Task 6: Tabs shown inside sections move to the theme

**Files:**
- Modify: `ui/detail/tabs/WeeklyReportsTab.kt`, `MonthlyReportsTab.kt`, `IssuesTab.kt`, `PhotoGalleryTab.kt`, `PendingDocumentsTab.kt`, `AuditLogTab.kt`
- Modify: `app/src/test/java/com/example/ThemeGuardTest.kt`

- [ ] **Step 1: Add to `MIGRATED`:** `ui/detail/ProjectDetailScreen.kt`, `ui/detail/ProjectSection.kt`, every `ui/detail/sections/*.kt`, and the six tab files above. Run `./gradlew -q testDebugUnitTest --tests com.example.ThemeGuardTest`. Expected: FAIL listing the 32 tab lines.
- [ ] **Step 2: Replace each `Color(0x…)`** with a role: sky blue → `colorScheme.primary`, amber → `watch`, green → `onTrack`, red → `behind`, grey → `neutral`, translucent fills → the matching `…Container`. Only colours change; role checks, layout and tags stay.
- [ ] **Step 3: Run** the gate. Expected: PASS.
- [ ] **Step 4: Commit** `refactor(ui): project tabs take colours from the theme`.

---

### Task 7: Two panes and width layout

**Files:**
- Modify: `app/src/main/java/com/example/MainActivity.kt:85-113`
- Modify: `app/src/main/java/com/example/ui/dashboard/DashboardScreen.kt`, `PortfolioContent.kt` (make `AttentionCard` `internal`)
- Create: `app/src/main/java/com/example/ui/dashboard/NeedsAttentionPane.kt`

**Interfaces:**
- Produces: `DashboardScreen(..., detailPane: (@Composable () -> Unit)? = null)`; `@Composable fun NeedsAttentionPane(queue: List<Pair<PortfolioItem, AttentionFlag>>, onOpen: (Project) -> Unit)`.

- [ ] **Step 1: Wire `MainActivity`.** `val twoPane = LocalConfiguration.current.screenWidthDp >= 840`. Two panes: always `DashboardScreen(detailPane = { if (selectedProjectId != null) ProjectDetailScreen(embedded = true, …) else NeedsAttentionPane(…) })`. Otherwise today's `if/else`. The `BackHandler` line is unchanged.
- [ ] **Step 2: Two-pane `DashboardScreen`.** With `detailPane != null`: one top bar across the width; content is `Row { list column 380dp (banner, `PortfolioContent`, "New Project" FAB at its bottom end, tag `create_project_fab`) ; VerticalDivider ; Box(weight 1) { detailPane() } }`; no Scaffold FAB. `NeedsAttentionPane` shows `AttentionCard` with all items expanded. `embedded` detail drops its own Scaffold top bar and keeps its dialogs.
- [ ] **Step 3: Run** the gate. Expected: PASS.
- [ ] **Step 4: Device check** (Phone A sideways and upright, Light and Dark): selecting another project swaps the right pane and keeps the section; Back on two panes clears the selection; Back upright returns to the portfolio; rotating with a project open keeps it open.
- [ ] **Step 5: Commit** `feat(ui): two-pane layout at 840dp and wider`.

---

### Task 8: Screenshot tests

**Files:**
- Create: `app/src/test/java/com/example/ProjectDetailScreenshotTest.kt`; baselines under `app/src/test/screenshots/`

- [ ] **Step 1: Write the test** like `PortfolioScreenshotTest` (Robolectric, `@GraphicsMode(NATIVE)`, `NHATheme(themeMode = …)`). Fixed sample data in the file: one Housing project with a 90-character name, 4 payments (one with a scan), 1 approved and 1 pending variation order, an approved time extension, 3 checklist documents in different states, 2 weekly reports, 2 issues. For each width (`RuntimeEnvironment.setQualifiers` with `w411dp-h891dp`, `w743dp-h1185dp`, `w1185dp-h743dp`) and each of Light and Dark, capture `ProjectHeader` plus each section (`summary`, `progress`, `money`, `documents`) to `<section>_<width>_<theme>.png` (24 images). Plus `header_collapsed` (411dp, Light), `money_empty` (no payments, contract cost 0, 411dp, Light), and `two_pane_empty` (`NeedsAttentionPane` at 1185dp, Light).
- [ ] **Step 2: Record** `./gradlew recordRoborazziDebug`; open each PNG and check: no sideways-cut content, payment cards 2 columns at 743/1185, collapsed header one line, dark text readable.
- [ ] **Step 3: Run** `./gradlew verifyRoborazziDebug` and the gate. Expected: PASS.
- [ ] **Step 4: Commit** `test: screenshots for project sections and payment cards` (with the PNGs).
