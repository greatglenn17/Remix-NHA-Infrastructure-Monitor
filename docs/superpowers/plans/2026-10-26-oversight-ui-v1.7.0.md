# Oversight UI v1.7.0 (foundation and Portfolio home) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the light design system, the theme guard, the attention rules and the new Portfolio home that replaces the tinted project-card grid.

**Architecture:** Colours come from the existing `NHAColors`/`LocalNHAColors` plus new status roles. Attention rules and portfolio aggregation are pure Kotlin objects in `com.example.util`, unit tested. `ProjectViewModel` exposes one `PortfolioUiState`; `DashboardScreen` becomes a thin stateful wrapper around a stateless `PortfolioContent` that the Roborazzi screenshot tests render with sample data.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Room, Robolectric, Roborazzi 1.59.0 (already in `app/build.gradle.kts`).

**Spec:** `docs/superpowers/specs/2026-10-26-oversight-ui-redesign-design.md` (sections "Design system", "Portfolio home (v1.7.0)", "Needs attention rules", "Testing").

## Global Constraints

- No Room schema change (database stays version 23); queries may be added.
- Data, sync, roles, permissions and assignment unchanged. Engineers see only `userScopedProjects`, as today.
- Navigation stays state-based in `MainActivity`; tablet two-pane is v1.8.0, not here.
- Thresholds (constants in `ProjectAttention`): warning slippage −10.0, critical slippage −15.0, billing gap 10.0 points, due soon 30 days, stale 14 days. Boundaries: `−15 < slippage ≤ −10` amber, `slippage ≤ −15` red, `billed% − actual% > 10` amber, `0 ≤ daysToCompletion ≤ 30` amber, `daysToCompletion < 0` red, `daysSinceUpdate ≥ 14` grey.
- Billed share uses `ContractCost.current(scopeOfWork, contractCostOriginal, documents)` as the denominator, as in v1.6.0.
- Light palette: background `#F8FAFC`, surface `#FFFFFF`, text `#0F172A`, secondary text `#475569`, outline `#E2E8F0`, primary `#0369A1`, on track `#15803D`, watch `#B45309`, behind `#B91C1C`, neutral `#64748B`.
- Touch targets ≥ 48dp; status always shown with text or an icon, never colour alone.
- Keep test tags `project_tile_<id>`, `create_project_fab`, `search_project_input`, `sort_button`, `status_dropdown_button`, `type_dropdown_button`, `edit_project_button_<id>`.
- Gate for every task: `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"; ./gradlew -q testDebugUnitTest lintDebug assembleDebug` passes. Do not set `MSYS_NO_PATHCONV`.
- Commit messages end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Do not push.

## Review Focus

1. Blank or placeholder dates (`""`, `"-"`) on a project: no due, overdue or stale flag, and no crash. Pinned in Task 3.
2. Contract cost of 0 with payments recorded: no "billing ahead" flag, billed bar shows its empty state. Pinned in Task 3.
3. A finished project (actual ≥ 100%) past its completion date with no recent report: not overdue, not stale. Pinned in Task 3.
4. Start date in the future and no reports yet: not stale. Pinned in Task 3.
5. Long project names and locations on a 411dp phone, and an empty portfolio: text ellipsizes, empty-state messages stay as today. Pinned in Task 6 screenshots.

---

### Task 1: Theme foundation

**Files:**
- Modify: `app/src/main/java/com/example/ui/theme/Color.kt` (`NHAColors`, `LightNHAColors`, `DarkNHAColors`)
- Modify: `app/src/main/java/com/example/ui/theme/Theme.kt` (`LightColorScheme`)
- Modify: `app/src/main/java/com/example/ui/theme/Type.kt`
- Modify: `app/src/main/java/com/example/data/auth/AuthManager.kt:360-366` (`getSavedAppThemeMode`)
- Test: `app/src/test/java/com/example/ThemeDefaultTest.kt`

**Interfaces:**
- Produces: `NHAColors` gains `onTrack`, `watch`, `behind`, `neutral` and `onTrackContainer`, `watchContainer`, `behindContainer`, `neutralContainer` (all `Color`). `val TabularNumbers: TextStyle` in `Type.kt` (`fontFeatureSettings = "tnum"`). `AuthManager.getSavedAppThemeMode()` returns `LIGHT` when no theme was ever saved.

- [ ] **Step 1: Write the failing test** (Robolectric, like `AssignedUidsTest`)

```kotlin
@RunWith(RobolectricTestRunner::class)
class ThemeDefaultTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun unsetTheme_defaultsToLight() {
        assertEquals(AppThemeMode.LIGHT, AuthManager(context).getSavedAppThemeMode())
    }

    @Test fun savedChoice_isKept() {
        AuthManager(context).saveAppThemeMode(AppThemeMode.SYSTEM)
        assertEquals(AppThemeMode.SYSTEM, AuthManager(context).getSavedAppThemeMode())
    }
}
```

- [ ] **Step 2: Run** `./gradlew -q testDebugUnitTest --tests com.example.ThemeDefaultTest`. Expected: `unsetTheme_defaultsToLight` FAILS (gets SYSTEM).

- [ ] **Step 3: Implement.** In `getSavedAppThemeMode`, return `LIGHT` when `!prefs.contains("app_theme_mode")`; an unreadable value still falls back to `SYSTEM`. Set the light palette from Global Constraints in `LightNHAColors` and `LightColorScheme` (`primary = 0xFF0369A1`, `background`/`surface`/`onSurface`/`onSurfaceVariant`/`outline` from the table). Status roles, light: `onTrack 0xFF15803D`, `watch 0xFFB45309`, `behind 0xFFB91C1C`, `neutral 0xFF64748B`; containers `0xFFDCFCE7`, `0xFFFEF3C7`, `0xFFFEE2E2`, `0xFFF1F5F9`. Dark: `0xFF4ADE80`, `0xFFFBBF24`, `0xFFFCA5A5`, `0xFF94A3B8`; containers `0xFF062C1E`, `0xFF331D08`, `0xFF371215`, `0xFF1E293B`. Dark scheme otherwise unchanged.

- [ ] **Step 4: Run** the gate. Expected: PASS.

- [ ] **Step 5: Commit** `feat(theme): light palette, status colours and light default`.

---

### Task 2: Theme guard and top bar

**Files:**
- Create: `app/src/test/java/com/example/ThemeGuardTest.kt`
- Modify: `app/src/main/java/com/example/ui/components/TopAppBarHeader.kt` (22 `Color(0x…)` uses)

**Interfaces:**
- Produces: `ThemeGuardTest.MIGRATED: List<String>`, paths relative to `app/src/main/java/com/example/`. Later tasks append to it.

- [ ] **Step 1: Write the failing test.** Plain JUnit; reads files from `File("src/main/java/com/example")` (Gradle runs unit tests with the module as working directory).

```kotlin
class ThemeGuardTest {
    companion object {
        val MIGRATED = listOf("ui/components/TopAppBarHeader.kt")
    }

    @Test fun migratedScreens_takeColoursFromTheTheme() {
        val offenders = MIGRATED.flatMap { path ->
            File("src/main/java/com/example", path).readLines().mapIndexedNotNull { i, line ->
                if ("Color(0x" in line) "$path:${i + 1}" else null
            }
        }
        assertEquals("Hand-set colours outside ui/theme: $offenders", emptyList<String>(), offenders)
    }
}
```

- [ ] **Step 2: Run** `./gradlew -q testDebugUnitTest --tests com.example.ThemeGuardTest`. Expected: FAIL listing 22 `TopAppBarHeader.kt` lines.

- [ ] **Step 3: Replace each `Color(0x…)` in `TopAppBarHeader.kt`** with a theme role: sky/blue accents → `MaterialTheme.colorScheme.primary`, amber badge → `LocalNHAColors.current.watch` on `watchContainer`, green → `onTrack`, translucent fills → the matching `…Container`. Behaviour and layout unchanged.

- [ ] **Step 4: Run** the gate. Expected: PASS. Install on Phone A (`adb -s PDNP06J000012674 install -r app/build/outputs/apk/debug/app-debug.apk`) and check the top bar in Light and Dark from the theme menu.

- [ ] **Step 5: Commit** `refactor(ui): top bar takes colours from the theme; add theme guard`.

---

### Task 3: Attention rules

**Files:**
- Create: `app/src/main/java/com/example/util/ProjectAttention.kt`
- Test: `app/src/test/java/com/example/ProjectAttentionTest.kt` (Robolectric runner, because `ContractCost` uses `org.json`)

**Interfaces:**
- Consumes: `ContractCost.current(String, Double, List<PendingDocument>): Double`, `ProjectSchedule.effectiveCompletion(original: String, revised: String): String`, `Project.variance`.
- Produces:

```kotlin
enum class AttentionLevel { CRITICAL, WARNING, STALE }          // red, amber, grey; declaration order = rank
enum class AttentionKind { BEHIND, BILLING_AHEAD, WAITING_APPROVAL, DUE_SOON, OVERDUE, NO_RECENT_UPDATE }
data class AttentionFlag(val kind: AttentionKind, val level: AttentionLevel, val message: String)

object ProjectAttention {
    const val WARNING_SLIPPAGE = -10.0
    const val CRITICAL_SLIPPAGE = -15.0
    const val BILLING_GAP_POINTS = 10.0
    const val DUE_SOON_DAYS = 30
    const val STALE_DAYS = 14
    val ORDER_TYPES = listOf("Variation Order", "Time Extension", "Work Suspension", "Work Resumption")

    fun flags(
        project: Project,
        documents: List<PendingDocument>,   // this project's documents
        billedTotal: Double,
        latestReportAt: LocalDate?,
        today: LocalDate
    ): List<AttentionFlag>                  // sorted by level
}
```

- [ ] **Step 1: Write the failing tests.** Helper `project(target, actual, status = "On-going", start = "2026-01-01", completion = "2026-12-31", revised = "-", cost = 1_000_000.0)` with `scopeOfWork = "[]"`; `today = 2026-10-26`.

| Test | Input | Assertion |
| --- | --- | --- |
| `slippageJustAboveWarning_notFlagged` | target 50, actual 40.1 | no `BEHIND` |
| `slippageAtTen_isWarning` | target 50, actual 40 | `BEHIND` / `WARNING` |
| `slippageAtFifteen_isCritical` | target 50, actual 35 | `BEHIND` / `CRITICAL` |
| `behindScheduleStatus_isWarningEvenWithSmallSlippage` | status "Behind Schedule", slippage −3 | `BEHIND` / `WARNING` |
| `billingGapOfExactlyTen_notFlagged` | actual 40, billedTotal 500,000 | no `BILLING_AHEAD` |
| `billingGapAboveTen_isWarning` | actual 40, billedTotal 501,000 | `BILLING_AHEAD` / `WARNING`, message "Billed 50.1%, work done 40.0%" |
| `zeroContractCost_noBillingFlag` | cost 0, billedTotal 100,000 | no `BILLING_AHEAD` |
| `pendingOrder_isWarning_checklistIsNot` | docs: "Variation Order No. 1 (Additive) - PHP 5" Pending, "Contractor Notice to Proceed (NTP)" Pending | exactly one `WAITING_APPROVAL`, message "1 order waiting approval" |
| `dueInThirtyDays_isWarning` | completion 2026-11-25 | `DUE_SOON` / `WARNING`, message "Due in 30 days" |
| `dueInThirtyOneDays_notFlagged` | completion 2026-11-26 | no `DUE_SOON` |
| `revisedCompletion_wins` | completion 2026-10-30, revised 2027-03-01 | no `DUE_SOON` |
| `pastCompletion_isOverdueCritical` | completion 2026-10-20, actual 80 | `OVERDUE` / `CRITICAL`, message "Overdue by 6 days" |
| `finishedProject_notOverdueNorStale` | completion 2026-10-20, actual 100, no reports | no `OVERDUE`, no `NO_RECENT_UPDATE` |
| `reportFourteenDaysAgo_isStale` | latestReportAt 2026-10-12 | `NO_RECENT_UPDATE` / `STALE`, message "No field update in 14 days" |
| `reportThirteenDaysAgo_notStale` | latestReportAt 2026-10-13 | no `NO_RECENT_UPDATE` |
| `noReports_countsFromStart` | start 2026-10-01, latestReportAt null | `NO_RECENT_UPDATE` |
| `futureStart_notStale` | start 2026-11-15, latestReportAt null | no `NO_RECENT_UPDATE` |
| `suspended_notStale` | status "Suspended", start 2026-01-01 | no `NO_RECENT_UPDATE` |
| `blankDates_noDateFlags` | start "", completion "-", revised "-" | no `DUE_SOON`, `OVERDUE`, `NO_RECENT_UPDATE` |
| `flags_areSortedByLevel` | slippage −20 and stale | first flag `CRITICAL`, last `STALE` |

Other messages: `BEHIND` → `"%.1f%% behind schedule".format(-variance)` (for status-only cases with no negative slippage: `"Marked behind schedule"`).

- [ ] **Step 2: Run** `./gradlew -q testDebugUnitTest --tests com.example.ProjectAttentionTest`. Expected: FAIL (unresolved `ProjectAttention`).

- [ ] **Step 3: Implement `ProjectAttention.flags`.** Billed % = `billedTotal / cost * 100` (uncapped; skip when cost ≤ 0). Dates parse with `LocalDate.parse` inside `runCatching`; unparseable means that flag is skipped. Days with `ChronoUnit.DAYS.between`. "Finished" = `actualAccomplishment >= 100`: skips `DUE_SOON`, `OVERDUE`, `NO_RECENT_UPDATE`.

- [ ] **Step 4: Run** the gate. Expected: PASS.

- [ ] **Step 5: Commit** `feat: attention rules for the portfolio`.

---

### Task 4: Portfolio aggregation

**Files:**
- Create: `app/src/main/java/com/example/util/Portfolio.kt`
- Test: `app/src/test/java/com/example/PortfolioTest.kt`

**Interfaces:**
- Consumes: Task 3 types.
- Produces:

```kotlin
data class PortfolioItem(
    val project: Project,
    val flags: List<AttentionFlag>,
    val billedFraction: Float?,      // BillingProgress.billedFraction(billedTotal, contractCost)
    val billedTotal: Double,
    val contractCost: Double,
    val pendingDocsCount: Int,       // this project's documents not "Approved"
    val lastFieldUpdate: LocalDate?
)
data class PortfolioSummary(val behind: Int, val billingAhead: Int, val waitingApproval: Int, val dueSoon: Int)
enum class PortfolioFilter { BEHIND, BILLING_AHEAD, WAITING_APPROVAL, DUE_SOON }

object Portfolio {
    const val NEEDS_ATTENTION_SORT = "Needs attention first"
    fun summarize(items: List<PortfolioItem>): PortfolioSummary
    fun matches(item: PortfolioItem, filter: PortfolioFilter?): Boolean
    fun attentionQueue(items: List<PortfolioItem>): List<Pair<PortfolioItem, AttentionFlag>>
    fun sortByAttention(items: List<PortfolioItem>): List<PortfolioItem>
}
```

- [ ] **Step 1: Write the failing tests** (plain JUnit; build `PortfolioItem`s directly with hand-made flags).

| Test | Assertion |
| --- | --- |
| `summarize_countsProjectsPerFlagAndDocuments` | 2 items with `BEHIND` (one WARNING, one CRITICAL) → `behind == 2`; `waitingApproval` = sum of `pendingDocsCount`; `dueSoon` counts `DUE_SOON` only (an `OVERDUE` item is not counted) |
| `matches_nullFilter_matchesAll` | `matches(item, null)` is true |
| `matches_waitingApproval_usesPendingDocs` | item with `pendingDocsCount = 2` and no flags matches `WAITING_APPROVAL` |
| `attentionQueue_ordersCriticalThenWarningThenStale` | levels come out CRITICAL, WARNING, STALE regardless of input order |
| `attentionQueue_breaksTiesByWorseVariance` | two CRITICAL `BEHIND` items, variance −20 before −16 |
| `sortByAttention_flaggedFirst_thenByName` | unflagged items keep alphabetical order after all flagged ones |

- [ ] **Step 2: Run** `./gradlew -q testDebugUnitTest --tests com.example.PortfolioTest`. Expected: FAIL.

- [ ] **Step 3: Implement `Portfolio`.** `sortByAttention` key: best (lowest ordinal) level, then variance ascending, then name.

- [ ] **Step 4: Run** the gate. Expected: PASS.

- [ ] **Step 5: Commit** `feat: portfolio summary, filters and attention queue`.

---

### Task 5: Portfolio state in the view model

**Files:**
- Create: `app/src/main/java/com/example/data/model/ProjectLatestReport.kt`
- Modify: `app/src/main/java/com/example/data/local/ReportDao.kt`
- Modify: `app/src/main/java/com/example/ui/viewmodel/ProjectViewModel.kt` (sort default at line 97, sort `when` near line 305, new flows next to `billedTotalsByProject`)
- Test: `app/src/test/java/com/example/ReportDaoLatestTest.kt` (in-memory Room, Robolectric; follow `DatabaseTest.kt` for setup)

**Interfaces:**
- Consumes: Tasks 3–4.
- Produces:
  - `data class ProjectLatestReport(val projectId: Long, val latestAt: Long)`
  - `ReportDao.getLatestWeeklyReportTimes(): Flow<List<ProjectLatestReport>>` with `@Query("SELECT projectId, MAX(createdAtTimestamp) AS latestAt FROM weekly_reports GROUP BY projectId")`
  - In `ProjectViewModel`:

```kotlin
data class PortfolioUiState(
    val summary: PortfolioSummary,
    val attention: List<Pair<PortfolioItem, AttentionFlag>>,  // from all scoped projects
    val rows: List<PortfolioItem>,                            // search/status/type/filter/sort applied
    val filter: PortfolioFilter?,
    val totalProjects: Int
)
val portfolioState: StateFlow<PortfolioUiState>
fun setPortfolioFilter(filter: PortfolioFilter?)   // tapping the active count again clears it
```

- [ ] **Step 1: Write the failing DAO test** `latestTimes_arePerProjectMaximum`: insert reports for project 1 at 100 and 300 and project 2 at 200; assert `[ProjectLatestReport(1, 300), ProjectLatestReport(2, 200)]` (sorted by `projectId`).

- [ ] **Step 2: Run** `./gradlew -q testDebugUnitTest --tests com.example.ReportDaoLatestTest`. Expected: FAIL.

- [ ] **Step 3: Add the query and data class.** No entity or version change.

- [ ] **Step 4: Build `portfolioState`.** Items from `userScopedProjects`, `allPendingDocuments`, `billedTotalsByProject`, `reportDao.getLatestWeeklyReportTimes()`; `today = LocalDate.now()`; report time → `LocalDate` in `ZoneId.systemDefault()`. `rows` = `filteredProjects` order mapped to items, then `Portfolio.matches(filter)`, then `Portfolio.sortByAttention` when the sort is `Portfolio.NEEDS_ATTENTION_SORT`. Make that sort the default (line 97) and add it to the sort `when` (`else -> list` already leaves order unchanged). Remove `dashboardSummaryStats` and `data/model/DashboardSummaryStats.kt` once nothing reads them (Task 6 removes the last reader; delete them in Task 6 if the build still needs them here).

- [ ] **Step 5: Run** the gate. Expected: PASS.

- [ ] **Step 6: Commit** `feat: portfolio state with latest field update per project`.

---

### Task 6: Portfolio home UI and screenshots

**Files:**
- Create: `app/src/main/java/com/example/ui/dashboard/PortfolioContent.kt` (stateless screen: header, counts, attention list, project list header with search/sort/filters, rows, empty states)
- Create: `app/src/main/java/com/example/ui/dashboard/PortfolioRow.kt` (one project row)
- Create: `app/src/main/java/com/example/ui/dashboard/PortfolioCounts.kt` (the four count tiles)
- Modify: `app/src/main/java/com/example/ui/dashboard/DashboardScreen.kt` (collects state, keeps its dialogs, calls `PortfolioContent`)
- Delete: `ProjectTileCard.kt`, `DashboardSummaryCard.kt`, `data/model/DashboardSummaryStats.kt`
- Modify: `app/build.gradle.kts` (add `roborazzi { outputDir.set(file("src/test/screenshots")) }`)
- Modify: `app/src/test/java/com/example/ThemeGuardTest.kt` (`MIGRATED` += the four `ui/dashboard/*.kt` files)
- Test: `app/src/test/java/com/example/PortfolioScreenshotTest.kt`, baselines under `app/src/test/screenshots/`

**Interfaces:**
- Consumes: `PortfolioUiState`, `setPortfolioFilter`, existing `setSearchQuery`, `setSortOrder`, `setStatusFilter`, `setTypeFilter`, permissions via `Permission.EDIT_PROJECT` / `DELETE_PROJECT` / `CREATE_PROJECT`.
- Produces:

```kotlin
@Composable fun PortfolioContent(
    state: PortfolioUiState,
    officeName: String,
    searchQuery: String, sortOrder: String, statusFilter: String, typeFilter: String,
    typeOptions: List<String>,
    emptyMessage: Pair<String, String>,          // title, body; today's texts from DashboardScreen
    onFilter: (PortfolioFilter?) -> Unit,
    onSearch: (String) -> Unit, onSort: (String) -> Unit,
    onStatus: (String) -> Unit, onType: (String) -> Unit,
    onOpen: (Project) -> Unit,
    onEdit: ((Project) -> Unit)?, onDelete: ((Project) -> Unit)?,
    modifier: Modifier = Modifier
)
```

Layout and copy follow the spec's "Portfolio home": header (office name and project count; sync status stays in the top bar, see Task 7); four counts (labels "Behind schedule", "Billing ahead of work", "Waiting approval", "Due in 30 days"; 4 in a row at ≥ 600dp width, 2×2 below, via `BoxWithConstraints`); "Needs attention" with up to 5 items and "See all" expanding in place; "All projects" rows. Row: name (1 line, ellipsis), location (1 line), status badge, variance chip (`+1.2%` / `−12.4%`), "Work done" and "Billed" bars with percentages (Billed shows "No billings yet" when `billedFraction == null || billedTotal <= 0`), completion date, pending-documents count, ⋮ menu with Edit/Delete per permission (tag `edit_project_button_<id>` on Edit). Row tag `project_tile_<id>`. Keep the FAB and the Field Engineer "Assigned View" banner. Colours only from `MaterialTheme.colorScheme` and `LocalNHAColors`.

- [ ] **Step 1: Write the screenshot test** with Robolectric `@GraphicsMode(GraphicsMode.Mode.NATIVE)` and `captureRoboImage`, rendering `NHATheme(themeMode = …) { PortfolioContent(sampleState, …) }`. Sample data in the test file: 6 projects covering every flag kind, one with a 90-character name and long location, one with no billings. Cases (`@Config(qualifiers = …)`):
  - `phone_light` (`w411dp-h891dp`), `phone_dark`
  - `tablet_portrait_light` (`w743dp-h1185dp`)
  - `tablet_landscape_light` (`w1185dp-h743dp`)
  - `empty_light` (`w411dp-h891dp`, no projects, Super Admin empty message)

- [ ] **Step 2: Build `PortfolioCounts`, `PortfolioRow`, `PortfolioContent`; rewire `DashboardScreen`; delete the old card files.**

- [ ] **Step 3: Record baselines** `./gradlew recordRoborazziDebug`, then open each PNG under `app/src/test/screenshots/` and check: long name ellipsized, counts 2×2 on phone and 4 across on tablet, dark text readable.

- [ ] **Step 4: Run** `./gradlew verifyRoborazziDebug` and the gate. Expected: PASS (`ThemeGuardTest` now covers `ui/dashboard`).

- [ ] **Step 5: Device check** on Phone A (Super Admin, upright, Light and Dark) and Phone B (Engineer Admin): counts match the projects, tapping a count filters and tapping it again clears, attention items open their project, Edit and Delete still work for allowed roles only.

- [ ] **Step 6: Commit** `feat(ui): Portfolio home replaces the project card grid` (include the baseline PNGs).

---

### Task 7: Spec correction and release prep

**Files:**
- Modify: `docs/superpowers/specs/2026-10-26-oversight-ui-redesign-design.md` ("Design system" finding)
- Modify: `app/build.gradle.kts` (versionCode 170, versionName "1.7.0"), `README.md` (badge, download steps, history row), `update.json`

- [ ] **Step 1: Correct the spec.** Replace the "Found:" paragraph with: the `Dark*` names are theme-aware getters over `LocalNHAColors` and a Light/Dark/System switch exists (default System); what does not adapt is the 413 hand-written `Color(0x…)` values outside `ui/theme`. Replace "a small `NhaStatusColors` set provided through a `CompositionLocal`" with "status roles added to the existing `NHAColors` (`LocalNHAColors`)". In "Portfolio home", change the header line to "office name and project count"; the app keeps no Firestore last-sync time, and the top bar already shows sync status.

- [ ] **Step 2: Bump the version** in the three files as in the v1.6.0 release commit (`git show 35efc71`). README history row: "📊 Portfolio Home: a light oversight dashboard with counts for slippage, billing ahead of work, pending approvals and deadlines, a ranked Needs attention list, and work-done vs billed bars for every project".

- [ ] **Step 3: Build and record the APK hash.** Run the gate; copy `app-debug.apk` to `NHA_Monitor_v1.7.0_Build170.apk`; confirm `aapt2 dump badging` shows versionCode 170 and `apksigner` shows certificate SHA-256 `66de9c79…5339459c` (same as v1.6.0); put its SHA-256 in `update.json`.

- [ ] **Step 4: Upgrade check.** Install the APK over v1.6.0 on Phone B; app opens signed in, Portfolio home shows, no entries in `adb logcat -b crash`.

- [ ] **Step 5: Commit** `Release v1.7.0: Portfolio home` (spec correction in its own `docs:` commit). Stop here: tagging, pushing and publishing need the owner's go-ahead.
