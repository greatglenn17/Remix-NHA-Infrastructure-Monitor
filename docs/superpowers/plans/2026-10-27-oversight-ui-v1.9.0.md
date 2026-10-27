# Oversight UI v1.9.0 (phones and forms) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Long forms open full-screen on phones (weekly report in five steps), every form gets date pickers, number keyboards, errors next to the field, a fixed Save and a "Discard changes?" guard, the main Progress/Money actions move to the bottom, and every UI file takes its colours from the theme so the old `Dark*` constants can be deleted.

**Architecture:** One small form kit in `ui/components/FormKit.kt` (`LongForm`, `DiscardChangesDialog`, `DateField`) and pure field checks in `util/FormValidation.kt`. Each form keeps its public composable name and parameters, so `MainActivity`, `DashboardScreen` and `ProjectDetailScreen` callers do not change; only the body changes. The colour move is mechanical: a fixed mapping from old constants to `MaterialTheme.colorScheme` / `LocalNHAColors`, then the constants are deleted so the compiler finds any leftover.

**Tech Stack:** Kotlin, Jetpack Compose Material 3 (`DatePickerDialog`, `rememberDatePickerState`), Robolectric 4.16.1, Roborazzi 1.59.0.

**Spec:** `docs/superpowers/specs/2026-10-26-oversight-ui-redesign-design.md` (sections "Stages" v1.9.0 row, "Phones and forms (v1.9.0)", "Design system", "Testing", "Out of scope").

## Global Constraints

- No Room schema change, no new data fields, no sync, role-check or permission change (spec "Out of scope"). Role conditions are moved verbatim.
- Form composables keep their names and parameter lists: `AddWeeklyReportDialog`, `AddMonthlyReportDialog`, `CreateProjectDialog`, `EditProjectDialog`, `RecordInspectionSnapshotDialog`, `AddPaymentDialog`, `AddRelatedDocumentDialog`, `AddCustomDocumentDialog`, `AddTimeExtensionDialog`, `AddWorkSuspensionDialog`, `AddWorkResumptionDialog`, `AddIssueDialog`. Values passed to `onSubmit` keep today's formats (dates stay `yyyy-MM-dd` strings).
- Keep every existing test tag, including `input_weekly_reporting_week`, `submit_weekly_report_button`, `add_issue_button`, `add_photo_button`, `input_project_*`, `submit_create_project`, `edit_project_*`, `submit_edit_project`, `add_billing_button`, `add_weekly_report_top_button`, `add_weekly_report_empty_button`, `add_variation_button`, `add_related_file_button`, `add_custom_document_button`, `add_issue_fab`, `create_project_fab`.
- Theme default stays System. Colours only from `MaterialTheme.colorScheme` and `LocalNHAColors` (plus the `Weather*` category colours, which stay in `ui/theme/Color.kt` as chart data colours). `Color.White`/`Color.Black` only for content drawn over photos or the map image. Touch targets ≥ 48dp.
- Phone = `LocalConfiguration.current.screenWidthDp < 600`.
- Gate for every task: `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"; ./gradlew -q testDebugUnitTest lintDebug assembleDebug` in Git Bash (do not set `MSYS_NO_PATHCONV`). Task 8 adds `verifyRoborazziDebug`. The worktree needs `local.properties`, `debug.keystore` and `.env` copied from the main checkout first.
- Never commit `.env`, `users.json`, `functions/node_modules` or any `.jks`. Commit messages end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. No version bump, merge, push, tag or release.

## Decisions to confirm

1. **Weekly steps.** 1 *Week and progress*: reporting week, accomplishment by scope item, daily weather log (it is per day of the chosen week). 2 *Manpower and equipment*. 3 *Activities and issues*. 4 *Photos and documents*: photos only, because the weekly report has no document field and adding one is a data change. 5 *Review and save*: read-only totals plus any errors, each tappable to jump to its step. Steps are a tappable step bar at the top; Back/Next at the bottom.
2. **Fixed Save.** Long forms: a bottom bar that is always visible, with Save (filled, right, carries the form's existing submit tag) and, on the weekly report, Back/Next to its left. Save is enabled whenever there are no errors, on any step, so editing one number in an old report does not need five Next taps. Short dialogs keep Save in the dialog's confirm slot (already fixed).
3. **Tablets.** At ≥ 600dp a long form is a dialog 640dp wide and 90% of the height with the same top bar and bottom bar, not full-screen.
4. **Date pickers everywhere.** Create/Edit project already use the platform `android.app.DatePickerDialog`; they switch to the same `DateField` as every other form, so all pickers look alike and follow the app theme.
5. **Old status/accent constants.** Besides the seven `Dark*` getters, the `Geo*`, `*Light`, `Status*`, `NavyPrimary`, `NavySecondary`, `GoldAccent`, `ErrorRed`, `NhaBlue`, `GreenAccent` and `StaticDark*`/`StaticLight*` names are deleted too (their values move into the private colour schemes in `Theme.kt` / `NHAColors`). `DarkNHAColors`/`LightNHAColors` stay; they are the palette.
6. **Scratch files** `ui/detail/tabs/VicoTest.kt` and `ui/dialogs/TestDateRange.kt` are unused and deleted instead of migrated.

## Review Focus

1. Back gesture on a full-screen form with nothing typed closes at once; with one changed field shows "Discard changes?"; Keep editing returns to the same step with the data intact. Pinned in Task 2 `FormKitTest`.
2. Editing an existing weekly report and pressing Save without changes still saves the same values (no field reset by the step split, photos kept). Pinned in Task 3 `WeeklyReportStepsTest.editUnchanged_submitsSameValues`.
3. Amount typed as `15,000,000.50` or with spaces: accepted, sent as `15000000.5`; `abc`, `-5` or empty (when required) shows the error under that field and disables Save. Pinned in Task 2 `FormValidationTest`.
4. A bottom "Add billing"/"Add weekly report" button covering the last payment card or report: lists get bottom padding so the last item can scroll clear. Pinned in Task 8 screenshots `money_fab_411_*` / `progress_fab_411_*`.
5. Field Engineer: no "Add weekly report" button where today there is none, Engineer-Admin-only actions still hidden. Pinned by moving the existing `canAdd…` conditions verbatim (Task 6) and the device check on Phone B (Task 9).

---

### Task 1: Revised duration from the stored revised date

**Files:**
- Modify: `app/src/main/java/com/example/util/ProjectSchedule.kt`, `app/src/main/java/com/example/ui/detail/sections/SummarySection.kt:81`
- Test: `app/src/test/java/com/example/ProjectScheduleTest.kt`

**Interfaces:**
- Produces: `fun ProjectSchedule.revisedDurationDays(dateStarted: String, contractDurationDays: Int, addedDays: Int, completionDateRevised: String): Int?` — `contractDurationDays + addedDays` when `addedDays > 0`; else `DAYS.between(dateStarted, completionDateRevised)` when both parse and the result differs from `contractDurationDays`; else `null`.

- [ ] **Step 1: Tests** in `ProjectScheduleTest`: `revisedDuration_fromApprovedOrders` (`("2026-01-05", 300, 30, "-")` → `330`); `revisedDuration_fromStoredDate` (`("2026-01-05", 300, 0, "2026-12-01")` → `330`); `revisedDuration_noneWhenSameOrBlank` (`("2026-01-05", 300, 0, "-")` → `null`, `("2026-01-05", 300, 0, "2026-11-01")` → `null`); `revisedDuration_badDate` (`("", 300, 0, "2026-12-01")` → `null`).
- [ ] **Step 2: Run** `./gradlew -q testDebugUnitTest --tests com.example.ProjectScheduleTest`. Expected: FAIL (unresolved reference).
- [ ] **Step 3: Implement**, and in `KeyDatesCard` show `revisedDurationDays(...)?.let { "${CurrencyFormatter.formatNumber(it)} days" } ?: "-"`.
- [ ] **Step 4: Run** the gate. Expected: PASS (`ProjectDetailScreenshotTest` baselines unchanged: sample project has `completionDateRevised = "-"`).
- [ ] **Step 5: Commit** `fix(ui): revised duration uses the stored revised date when no orders`.

---

### Task 2: Form kit and field checks

**Files:**
- Create: `app/src/main/java/com/example/util/FormValidation.kt`, `app/src/main/java/com/example/ui/components/FormKit.kt`
- Test: `app/src/test/java/com/example/FormValidationTest.kt`, `app/src/test/java/com/example/FormKitTest.kt` (Robolectric + `createComposeRule`)

**Interfaces:**
- Produces:

```kotlin
object FormValidation {
    fun parseAmount(text: String): Double?                       // strips commas/spaces; null if not a number or < 0
    fun amountError(text: String, required: Boolean): String?    // "Required" | "Enter an amount" | null
    fun percentError(text: String): String?                      // blank ok; "0 to 100" when outside or not a number
    fun requiredError(text: String): String?                     // "Required" | null
}

@Composable fun LongForm(
    title: String, dirty: Boolean, saveEnabled: Boolean, onSave: () -> Unit, onDismiss: () -> Unit,
    saveTag: String, bottomStart: @Composable RowScope.() -> Unit = {}, topContent: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
)   // Dialog(usePlatformDefaultWidth = false); full-screen < 600dp, else 640dp × 90%; Scaffold: top bar (Close icon, title),
    // topContent under it, scrolling content, fixed bottom bar [bottomStart … Save]; Close and Back call guard below
@Composable fun DiscardChangesDialog(onDiscard: () -> Unit, onKeepEditing: () -> Unit)
    // title "Discard changes?", text "Your changes will be lost.", buttons "Discard" / "Keep editing"
@Composable fun rememberDiscardGuard(dirty: Boolean, onDismiss: () -> Unit): () -> Unit
    // returns the close action for short dialogs: calls onDismiss when clean, else shows DiscardChangesDialog
@Composable fun DateField(label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier,
    error: String? = null, clearable: Boolean = false)
    // read-only OutlinedTextField + trailing calendar icon; tap opens M3 DatePickerDialog; value in/out "yyyy-MM-dd" (UTC millis);
    // clearable adds a "Clear" button for optional dates (VO Date Approved)
```

- Dirty rule used by every form: `val initial = remember { snapshot() }; val dirty = snapshot() != initial`, where `snapshot()` is a `listOf(...)` of the form's field values (lists copied with `.toList()`).

- [ ] **Step 1: `FormValidationTest`**: `parseAmount("15,000,000.50") == 15000000.5`; `parseAmount(" 1 200 ") == 1200.0`; `parseAmount("-5") == null`; `parseAmount("abc") == null`; `amountError("", true) == "Required"`; `amountError("", false) == null`; `amountError("abc", false) == "Enter an amount"`; `percentError("100") == null`; `percentError("100.5") == "0 to 100"`; `percentError("") == null`; `requiredError("  ") == "Required"`.
- [ ] **Step 2: `FormKitTest`**: `clean_closeDismissesAtOnce` (LongForm with `dirty = false`, click the Close icon → `onDismiss` called once, no "Discard changes?" node); `dirty_closeAsks` (`dirty = true` → "Discard changes?" shown, `onDismiss` not called); `dirty_keepEditing_staysOpen` (click "Keep editing" → dialog gone, `onDismiss` not called); `dirty_discard_dismisses` (click "Discard" → `onDismiss` called); `save_disabled_whenNotEnabled` (node with `saveTag` is not enabled).
- [ ] **Step 3: Run** `./gradlew -q testDebugUnitTest --tests "com.example.Form*"`. Expected: FAIL (unresolved references).
- [ ] **Step 4: Implement** both files. `LongForm` uses `BackHandler` inside the dialog plus `onDismissRequest` for the guard; `Modifier.imePadding()` on the scaffold so the bottom bar rides above the keyboard.
- [ ] **Step 5: Run** the gate. Expected: PASS.
- [ ] **Step 6: Commit** `feat(ui): form kit with date field, discard guard and field checks`.

---

### Task 3: Weekly report full-screen in five steps

**Files:**
- Modify: `app/src/main/java/com/example/ui/dialogs/AddWeeklyReportDialog.kt`
- Create: `app/src/main/java/com/example/ui/dialogs/WeeklyReportSteps.kt`
- Test: `app/src/test/java/com/example/WeeklyReportStepsTest.kt`

**Interfaces:**
- Consumes: `LongForm`, `FormValidation.percentError` (Task 2).
- Produces: `enum class WeeklyStep(val label: String) { WEEK("Week and progress"), CREW("Manpower and equipment"), WORK("Activities and issues"), PHOTOS("Photos and documents"), REVIEW("Review and save") }` and `fun weeklyErrors(accomplishments: Map<String, Pair<String, String>>): List<Pair<WeeklyStep, String>>` (one entry per scope item whose target or actual fails `percentError`, message `"<item>: target 0 to 100"` / `"<item>: actual 0 to 100"`).

- [ ] **Step 1: Tests**: `weeklyErrors_flagsOutOfRange` (`mapOf("Drainage" to ("50" to "120"))` → `[(WEEK, "Drainage: actual 0 to 100")]`); `weeklyErrors_blankIsFine`; `editUnchanged_submitsSameValues` (Robolectric: render `AddWeeklyReportDialog` with an `existingReport` fixture holding 2 scope items, 1 manpower row, 1 issue, 1 photo; click `submit_weekly_report_button`; assert every `onSubmit` argument equals the fixture's stored value).
- [ ] **Step 2: Run** `./gradlew -q testDebugUnitTest --tests com.example.WeeklyReportStepsTest`. Expected: FAIL.
- [ ] **Step 3: Restructure** `AddWeeklyReportDialog`: state stays where it is (top of the function, so switching steps loses nothing); the `AlertDialog` becomes `LongForm(title = if (existingReport == null) "New weekly report" else "Edit weekly report", saveTag = "submit_weekly_report_button", ...)`; `topContent` is a `ScrollableTabRow` of the five `WeeklyStep` labels; `bottomStart` holds Back / Next `TextButton`s; the existing blocks move by their comment headers into `when (step)`: WEEK = reporting week + accomplishment table + daily weather log; CREW = manpower + equipment; WORK = activities + issues; PHOTOS = photo card; REVIEW = week, overall target/actual/variance, manpower total, counts of equipment, activities, issues, photos, then `weeklyErrors` as tappable rows that set `step`. Percent fields get `KeyboardType.Decimal`, `isError` and `supportingText` from `percentError`. Save enabled when `weeklyErrors(...)` is empty. The photo-source dialog stays a dialog. Colours per the Task 7 mapping; `darkTextFieldColors` is removed (default field colours).
- [ ] **Step 4: Run** the gate. Expected: PASS.
- [ ] **Step 5: Commit** `feat(ui): weekly report full-screen in five steps`.

---

### Task 4: Monthly report, create/edit project and lot inspection full-screen

**Files:**
- Modify: `app/src/main/java/com/example/ui/dialogs/AddMonthlyReportDialog.kt`, `CreateProjectDialog.kt`, `EditProjectDialog.kt`, `AssignStaffField.kt`; `app/src/main/java/com/example/ui/detail/tabs/SubdivisionPlanTab.kt` (`RecordInspectionSnapshotDialog`, line 1673)

**Interfaces:**
- Consumes: `LongForm`, `DateField`, `FormValidation` (Task 2).

- [ ] **Step 1: Convert each** `AlertDialog` to `LongForm` (titles: "New monthly report"/"Edit monthly report", "New project", "Edit project", "Record inspection: <lot label>"; `saveTag` = the existing submit tag, or `submit_monthly_report`/`submit_lot_inspection` where none exists). The platform `DatePickerDialog` blocks in Create/Edit project become `DateField`s. Amount fields (contract cost, scope amounts) use `KeyboardType.Decimal` and `amountError(required = true)`; counts keep `KeyboardType.Number`; name/location/contractor show `requiredError` under the field. Save enabled when no field has an error. Validation that today lives in the confirm button's `onClick` (toasts or silent returns) moves to these field errors; the checks themselves stay the same.
- [ ] **Step 2: Colours** in these files per the Task 7 mapping.
- [ ] **Step 3: Run** the gate. Expected: PASS.
- [ ] **Step 4: Commit** `feat(ui): long forms open full-screen on phones`.

---

### Task 5: Short forms (billing, documents, variation order, schedule orders, issue)

**Files:**
- Modify: `app/src/main/java/com/example/ui/detail/tabs/PaymentDialogs.kt`, `DocumentDialogs.kt`; `app/src/main/java/com/example/ui/dialogs/AddSubLogDialogs.kt`, `AddIssueDialog.kt`

**Interfaces:**
- Consumes: `DateField`, `rememberDiscardGuard`, `FormValidation` (Task 2).

- [ ] **Step 1: Convert** every typed `(YYYY-MM-DD)` field to `DateField` (labels without the format hint; VO "Date Approved" `clearable = true`, supporting text "Leave blank if not yet approved"). Amounts (`AddPaymentDialog` amount, VO amount, time-extension days) use `KeyboardType.Decimal`/`Number` and show `amountError` under the field; `onSubmit` receives `parseAmount(...)`. Dismiss (outside tap, Back, Cancel) goes through `rememberDiscardGuard(dirty, onDismiss)`. These stay `AlertDialog`s.
- [ ] **Step 2: Colours** in these files per the Task 7 mapping.
- [ ] **Step 3: Run** the gate. Expected: PASS.
- [ ] **Step 4: Commit** `feat(ui): short forms get date pickers, number keyboards and field errors`.

---

### Task 6: Main actions at the bottom

**Files:**
- Modify: `app/src/main/java/com/example/ui/detail/tabs/WeeklyReportsTab.kt:90-100`, `app/src/main/java/com/example/ui/detail/sections/MoneySection.kt:57,117`

- [ ] **Step 1: Progress.** The top "Add" button (tag `add_weekly_report_top_button`, same `canAdd…` condition) becomes an `ExtendedFloatingActionButton` ("Add weekly report", `Icons.Default.Add`) aligned bottom-end in a `Box` over the list; the list gets `contentPadding(bottom = 88.dp)`. The empty-state button stays.
- [ ] **Step 2: Money.** The `TextButton` "Add billing" (tag `add_billing_button`) leaves `BillingSummary` and becomes the same kind of bottom-end `ExtendedFloatingActionButton` over `MoneySection`'s list, with the same bottom padding. Payments have no role check today and get none.
- [ ] **Step 3: Run** the gate. Expected: PASS; `ProjectDetailScreenshotTest` diffs on money/progress are expected and are re-recorded in Task 8.
- [ ] **Step 4: Commit** `feat(ui): add weekly report and add billing within thumb reach`.

---

### Task 7: Every UI file on the theme; old constants deleted

**Files:**
- Modify: `app/src/main/java/com/example/MainActivity.kt`; `ui/login/LoginScreen.kt`; `ui/components/TopAppBarHeader.kt`; `ui/dashboard/DashboardScreen.kt`; `ui/detail/ProjectDetailScreen.kt`; `ui/detail/tabs/{AuditLogTab,IssuesTab,MonthlyReportsTab,PendingDocumentsTab,PhotoGalleryTab,SubdivisionPlanTab,WeatherChartTab,WeeklyReportsTab}.kt`; `ui/dialogs/{AdminElevationDialog,EditProfileDialog,GoogleDriveSyncDialog,NotificationsDialog,UpdateAvailableDialog}.kt`; `ui/theme/Color.kt`, `ui/theme/Theme.kt`
- Delete: `ui/detail/tabs/VicoTest.kt`, `ui/dialogs/TestDateRange.kt`
- Modify: `app/src/test/java/com/example/ThemeGuardTest.kt`

**Mapping** (`cs` = `MaterialTheme.colorScheme`, `nha` = `LocalNHAColors.current`):
`DarkBackground`/`GeoBackground`/`BackgroundLight` → `cs.background`; `DarkSurface`/`SurfaceLight` → `cs.surface`; `DarkSurfaceVariant`/`GeoNeutralBg`/`SurfaceVariantLight` → `cs.surfaceVariant`; `DarkTextPrimary`/`GeoTextPrimary` and `Color.White` text on surfaces → `cs.onSurface`; `DarkTextSecondary`/`GeoTextSecondary` → `cs.onSurfaceVariant`; `DarkBorder`/`GeoNeutralBorder` → `cs.outline`; `DarkCardHeader` → `nha.cardHeader`; `NavyPrimary`/`NhaBlue`/sky-blue hex → `cs.primary` (text on it `cs.onPrimary`); `GoldAccent` → `cs.secondary`; `ErrorRed` → `cs.error`; `StatusGreen*`/`GreenAccent`/green hex → `nha.onTrack` (`…Bg`, `…BarBg` → `nha.onTrackContainer`; `…Border` → `nha.onTrack.copy(alpha = 0.4f)`); `StatusOrange*`/amber hex → `nha.watch` family; `StatusRed*`/red hex → `nha.behind` family; `StatusGray*`/grey hex → `nha.neutral` family; translucent black scrims → `cs.scrim.copy(alpha = …)`. Lot-map fills in `SubdivisionPlanTab` (`getLotProgressFillColor` etc.) use the same four status roles plus `cs.primary` for "in progress". `Weather*` stay.

- [ ] **Step 1: Guard first.** Replace the hand-kept list check with `everyUiFile_isMigrated`: `MIGRATED` must equal every `.kt` under `src/main/java/com/example/ui` except `ui/theme/**`, plus `MainActivity.kt` (sorted list compare, message lists the missing files). Set `MIGRATED` to that full list. Run `./gradlew -q testDebugUnitTest --tests com.example.ThemeGuardTest`. Expected: FAIL listing the `Color(0x` lines still in the files above.
- [ ] **Step 2: Replace colours** file by file per the mapping. Only colours change; layout, text, tags and role checks stay.
- [ ] **Step 3: Delete constants.** In `Color.kt` delete the `Dark*` getters, `Geo*`, `BackgroundLight`, `SurfaceLight`, `SurfaceVariantLight`, all `Status*`, and the accent vals; inline the `StaticDark*`/`StaticLight*` values into `DarkNHAColors`/`LightNHAColors` and have `Theme.kt`'s schemes read `DarkNHAColors.background` etc. Delete the two scratch files. `./gradlew -q compileDebugKotlin` must compile; any unresolved reference is a missed use, fixed with the mapping.
- [ ] **Step 4: Run** the gate. Expected: PASS; `grep -rnE "\bDark(Background|Surface|SurfaceVariant|TextPrimary|TextSecondary|Border|CardHeader)\b|Status[A-Z]" app/src/main` prints nothing.
- [ ] **Step 5: Commit** `refactor(ui): every screen takes colours from the theme; old constants removed`.

---

### Task 8: Screenshot tests for the forms

**Files:**
- Create: `app/src/test/java/com/example/FormsScreenshotTest.kt`; baselines under `app/src/test/screenshots/`
- Modify: re-record `ProjectDetailScreenshotTest` baselines changed by Task 6

- [ ] **Step 1: Write the test** like `ProjectDetailScreenshotTest` (fixed sample data in the file, `NHATheme(themeMode = …)`, `RuntimeEnvironment.setQualifiers` `w411dp-h891dp` / `w743dp-h1185dp` / `w1185dp-h743dp`). Forms are dialog windows, so render with `createComposeRule()` and capture with `captureScreenRoboImage(path)`. For each width × Light/Dark: `form_weekly_week`, `form_weekly_review` (with one out-of-range actual so an error shows), `form_monthly`, `form_create_project` (name blank so "Required" shows), `form_lot_inspection`, `form_add_billing`, `form_variation_order`, `login` → `<name>_<width>_<theme>.png` (48 images). Plus `form_discard_411_light` and, in `ProjectDetailScreenshotTest`, `progress_fab_411_light`/`_dark` and `money_fab_411_light`/`_dark` scrolled to the end.
- [ ] **Step 2: Record** `./gradlew recordRoborazziDebug`; open each PNG and check: full-screen at 411, 640dp dialog at 743/1185, bottom bar visible, error text under its field, dark text readable, last list item clear of the bottom button.
- [ ] **Step 3: Run** `./gradlew -q testDebugUnitTest lintDebug assembleDebug verifyRoborazziDebug`. Expected: PASS.
- [ ] **Step 4: Commit** `test: screenshots for forms, login and bottom actions` (with the PNGs).

---

### Task 9: Device check

- [ ] **Step 1:** Note Phone A's (`PDNP06J000012674`) current dark mode (`adb shell cmd uimode night`) and auto-rotate (`adb shell settings get system accelerometer_rotation`).
- [ ] **Step 2:** `adb -s PDNP06J000012674 install -r app/build/outputs/apk/debug/app-debug.apk` (update over v1.8.1, data kept).
- [ ] **Step 3:** Upright and sideways, Light and Dark: login; open a project; Progress → "Add weekly report" at the bottom → walk the five steps, back gesture with a change → "Discard changes?"; edit an existing report and save unchanged; Money → "Add billing" with `1,500.50`, a date from the picker, and an empty amount error; variation order date picker; create project full-screen; lot inspection full-screen; keyboard does not cover the Save bar.
- [ ] **Step 4:** If Phone B is connected (`adb devices`), sign in as Engineer Admin and repeat the weekly report and billing checks.
- [ ] **Step 5:** Put dark mode and auto-rotate back to the values from Step 1. Record results in the shared notes folder.
