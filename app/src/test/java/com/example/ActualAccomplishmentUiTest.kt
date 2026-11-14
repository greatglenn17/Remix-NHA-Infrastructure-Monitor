package com.example

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.text.TextLayoutResult
import com.example.data.model.AppThemeMode
import com.example.data.model.Project
import com.example.data.model.ProjectAbstract
import com.example.data.model.WeeklyReport
import com.example.ui.detail.ProjectSection
import com.example.ui.detail.ProjectSectionTabs
import com.example.ui.detail.sections.ActualAccomplishmentSection
import com.example.ui.dialogs.AddWeeklyReportDialog
import com.example.ui.dialogs.WeeklyDrafts
import com.example.ui.theme.NHATheme
import com.example.util.BillingAbstract
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// The Actual Accomplishment tab, and the weekly report form of a week that has an uploaded abstract.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ActualAccomplishmentUiTest {
    @get:Rule val rule = createComposeRule()

    private val project = Project(
        id = 1, name = "P", location = "L", implementingOffice = "O", contractor = "C",
        scopeOfWork = """[{"name":"Land Acquisition","amount":1000000,"weightPct":10},{"name":"Land Development","amount":2000000,"weightPct":20},
            {"name":"Construction of Housing Units","amount":7000000,"weightPct":70}]""",
        contractCostOriginal = 10_000_000.0, contractCostRevised = 0.0, contractDurationDays = 360,
        dateStarted = "2025-01-06", completionDateOriginal = "2025-12-31", completionDateRevised = "", targetAccomplishment = 12.0
    )

    // As the website saves it: Land Development 25.21%, Housing Construction 1%, no Land Acquisition part.
    private val abstract = ProjectAbstract(
        id = 1, projectId = 1, asOf = "2025-03-14", overallPct = 10.73, fileName = "abstract.xlsx", uploadedBy = "Engr. Sample",
        rowsJson = """[{"kind":"part","no":"2","name":"LAND DEVELOPMENT","qty":null,"unit":"","unitCost":null,"amount":null,"prev":[null,null,null],"period":[null,null,null],"toDate":[null,null,null],"balance":[null,null]},
            {"kind":"item","no":"2.1.1","name":"Mobilization/Demobilization","qty":1,"unit":"l.s.","unitCost":400000,"amount":400000,"prev":[0,0,0],"period":[0.25,100000,25],"toDate":[0.25,100000,25],"balance":[0.75,300000]},
            {"kind":"subtotal","no":"","name":"Subtotal (Land Development)","qty":null,"unit":"","unitCost":null,"amount":2000000,"prev":[null,null,null],"period":[null,null,null],"toDate":[null,504000,25.21],"balance":[null,null]},
            {"kind":"part","no":"3","name":"HOUSING CONSTRUCTION","qty":null,"unit":"","unitCost":null,"amount":null,"prev":[null,null,null],"period":[null,null,null],"toDate":[null,null,null],"balance":[null,null]},
            {"kind":"subtotal","no":"","name":"Subtotal (Housing Construction)","qty":null,"unit":"","unitCost":null,"amount":7000000,"prev":[null,null,null],"period":[null,null,null],"toDate":[null,70000,1],"balance":[null,null]},
            {"kind":"total","no":"","name":"GRAND TOTAL","qty":null,"unit":"","unitCost":null,"amount":10000000,"prev":[null,null,null],"period":[null,null,null],"toDate":[null,1074000,10.73],"balance":[null,null]}]"""
    )

    private val typed = SemanticsMatcher.keyIsDefined(SemanticsActions.SetText)

    @Config(qualifiers = "w360dp-h640dp")
    @Test fun allFiveSectionTabs_fitA360dpPhone() {
        rule.setContent { NHATheme(themeMode = AppThemeMode.LIGHT) { ProjectSectionTabs(ProjectSection.ACTUAL) {} } }
        val cut = ProjectSection.entries.mapNotNull { s ->
            val layout = mutableListOf<TextLayoutResult>()
            rule.onNodeWithText(s.label, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!(layout)
            // The label's own width against the width it was given.
            val text = layout.single()
            "${s.label}: ${text.multiParagraph.maxIntrinsicWidth}px in ${text.size.width}px".takeIf { text.multiParagraph.maxIntrinsicWidth > text.size.width }
        }
        assertEquals(emptyList<String>(), cut)
    }

    @Config(qualifiers = "w411dp-h891dp")
    @Test fun tab_showsTheLatestAbstractWithItsPayItems_andPartsFold() {
        rule.setContent {
            NHATheme(themeMode = AppThemeMode.LIGHT) {
                ActualAccomplishmentSection(project, emptyList(), listOf(abstract.copy(id = 2, asOf = "2025-02-14", overallPct = 5.0, uuid = "older"), abstract), "Engr. Sample", {}, {}, {}, { _, _ -> })
            }
        }
        rule.onNodeWithText("10.73%").assertExists() // the latest "As of" date opens first
        // Target, actual and advance/slippage by line; without a bar chart only the overall has a target, the project's.
        rule.onNodeWithTag("standing_Land Development").assert(hasText("-")).assert(hasText("25.21%"))
        rule.onNodeWithTag("standing_Overall").assert(hasText("12.00%")).assert(hasText("10.73%")).assert(hasText("-1.27%"))
        rule.onNodeWithText("abstract.xlsx").assertExists()
        rule.onNodeWithText("2  LAND DEVELOPMENT").assertExists()
        rule.onNodeWithText("Amount 2,000,000.00 · To date 25.21%").assertExists()
        rule.onNodeWithText("2.1.1  Mobilization/Demobilization").assertExists()
        rule.onNodeWithText("Amount 400,000.00 · To date 100,000.00 (25.00%)").assertExists()
        rule.onNodeWithTag("abstract_part_0").performClick()
        rule.onNodeWithText("2.1.1  Mobilization/Demobilization").assertDoesNotExist()
        rule.onNodeWithText("GRAND TOTAL").assertExists()
        rule.onNodeWithTag("upload_abstract").assertExists()
        rule.onNodeWithTag("delete_abstract").assertExists()
        // An earlier date can be picked.
        rule.onNodeWithTag("section_chip_Feb 14, 2025").performClick()
        rule.onNodeWithText("5.00%").assertExists()
    }

    @Test fun tab_withoutAnAbstract_saysSo_andViewersCannotUpload() {
        rule.setContent { NHATheme(themeMode = AppThemeMode.LIGHT) { ActualAccomplishmentSection(project, emptyList(), emptyList(), "", null, null, {}, { _, _ -> }) } }
        rule.onNodeWithText("No abstract uploaded yet. Upload the Abstract of Physical Accomplishment of the actual work (the \"As of\" one), saved as an Excel workbook (.xlsx).").assertExists()
        rule.onNodeWithTag("upload_abstract").assertDoesNotExist()
    }

    // "Print billing abstract": this billing's figures are typed, land development starting from the abstract's own this-period amount.
    @Test fun tab_printsTheBillingAbstractFromTheTypedFigures() {
        val withPeriod = abstract.copy(rowsJson = abstract.rowsJson.replace(""""period":[null,null,null],"toDate":[null,504000,25.21]""", """"period":[null,104000,5.2],"toDate":[null,504000,25.21]"""))
        var printed: Pair<ProjectAbstract, BillingAbstract.Period>? = null
        rule.setContent {
            NHATheme(themeMode = AppThemeMode.LIGHT) {
                ActualAccomplishmentSection(project, emptyList(), listOf(withPeriod), "", null, null, {}, { a, period -> printed = a to period }) // a viewer can print too
            }
        }
        rule.onNodeWithTag("print_billing_abstract").performClick()
        rule.onNodeWithText("This billing").assertExists()
        rule.onNodeWithText("Previous is what the billings saved up to Mar 14, 2025 came to. Nothing typed here is saved.").assertExists()
        rule.onNodeWithTag("billing_lots").assertDoesNotExist() // the abstract has no land acquisition
        rule.onNodeWithTag("billing_development").assert(hasText("104000.00"))
        rule.onNodeWithTag("billing_covered").performTextReplacement("March 1 - 14, 2025")
        rule.onNodeWithTag("billing_m1").performTextReplacement("2")
        rule.onNodeWithTag("billing_m2").performTextReplacement("two") // unreadable: 0
        rule.onNodeWithTag("confirm_print_billing_abstract").performClick()
        assertEquals(withPeriod to BillingAbstract.Period("March 1 - 14, 2025", 0.0, 104000.0, 2.0, 0.0), printed)
        rule.onNodeWithText("This billing").assertDoesNotExist()
    }

    private fun report(week: String, acquisition: Double, development: Double, housing: Double) = WeeklyReport(
        projectId = 1, reportingWeek = week, daysElapsed = 0, remainingDays = 0, targetAccomplishmentPct = 0.0, actualAccomplishmentPct = 0.0,
        manpowerJson = "[]", equipmentJson = "[]", activitiesJson = "[]", issuesJson = "[]", documentsIssuedReceivedJson = "[]", attachedPhotoUrlsJson = "[]", submittedByStaff = "",
        accomplishmentItemsJson = listOf("Land Acquisition" to acquisition, "Land Development" to development, "Housing Construction" to housing)
            .joinToString(",", "[", "]") { (name, actual) -> """{"itemDescription":"$name","targetPct":0,"actualPct":$actual,"weightPct":10}""" }
    )
    private fun actuals(accItemsJson: String) = org.json.JSONArray(accItemsJson).let { a -> (0 until a.length()).map { a.getJSONObject(it).getDouble("actualPct") } }

    /** Opens the form in the abstract's week and saves it as it is: the overall actual and the lines' as stored. */
    private fun savedAsOpened(existing: WeeklyReport?): Pair<Double, List<Double>> {
        var saved: Pair<Double, List<Double>>? = null
        rule.setContent {
            NHATheme(themeMode = AppThemeMode.LIGHT) {
                AddWeeklyReportDialog(project = project, projectId = 1, existingReport = existing, onDismiss = {},
                    onSubmit = { _, _, actual, _, _, _, _, _, _, _, accItemsJson, _ -> saved = actual to actuals(accItemsJson) },
                    today = LocalDate.of(2025, 3, 12), abstracts = listOf(abstract))
            }
        }
        rule.onNodeWithTag("submit_weekly_report_button").performClick()
        return saved!!
    }

    // Saving stores the abstract's three figures: 25.21 x 20% + 1 x 70% overall.
    @Test fun weeklyForm_savesTheAbstractsFigures() {
        val (overall, lines) = savedAsOpened(null)
        assertEquals(listOf(0.0, 25.21, 1.0), lines)
        assertEquals(5.742, overall, 1e-9)
    }

    // Also in a saved report that had other figures, though nothing was typed.
    @Test fun weeklyForm_savesTheAbstractsFigures_whenEditingASavedReport() {
        val (overall, lines) = savedAsOpened(report("Mar 8, 2025 - Mar 14, 2025", 10.0, 20.0, 30.0))
        assertEquals(listOf(0.0, 25.21, 1.0), lines)
        assertEquals(5.742, overall, 1e-9)
    }

    // A draft keeps what was typed (or carried) under the abstract's figures, so the boxes return to it in a week without an abstract.
    @Test fun weeklyForm_draftKeepsWhatWasTypedUnderTheAbstract() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        WeeklyDrafts.clear(context, 1)
        rule.setContent {
            NHATheme(themeMode = AppThemeMode.LIGHT) {
                AddWeeklyReportDialog(project = project, projectId = 1, onDismiss = {}, onSubmit = { _, _, _, _, _, _, _, _, _, _, _, _ -> },
                    today = LocalDate.of(2025, 3, 12), reports = listOf(report("Mar 1, 2025 - Mar 7, 2025", 50.0, 20.0, 0.5)), abstracts = listOf(abstract))
            }
        }
        rule.onNodeWithTag("weekly_actual_Land Development").assert(hasText("25.21")) // the abstract's, over the 20.00 carried from the last report
        rule.onNodeWithTag("weekly_target_Land Development").performTextReplacement("30")
        rule.mainClock.advanceTimeBy(1000)
        rule.waitForIdle()
        assertEquals(listOf(50.0, 20.0, 0.5), actuals(WeeklyDrafts.load(context, 1)!!.report.accomplishmentItemsJson))
        WeeklyDrafts.clear(context, 1)
    }

    @Test fun weeklyForm_takesTheActualsOfTheAbstractOfItsWeek() {
        rule.setContent {
            NHATheme(themeMode = AppThemeMode.LIGHT) {
                // Wed Mar 12, 2025 is in the week of Sat Mar 8 to Fri Mar 14.
                AddWeeklyReportDialog(project = project, projectId = 1, onDismiss = {}, onSubmit = { _, _, _, _, _, _, _, _, _, _, _, _ -> },
                    today = LocalDate.of(2025, 3, 12), abstracts = listOf(abstract))
            }
        }
        rule.onNodeWithText("Actual % is from the abstract as of Mar 14, 2025 (Actual Accomplishment tab).").assertExists()
        rule.onNodeWithTag("weekly_actual_Land Development").assert(hasText("25.21")).assert(!typed)
        rule.onNodeWithTag("weekly_actual_Housing Construction").assert(hasText("1.00"))
        rule.onNodeWithTag("weekly_actual_Land Acquisition").assert(hasText("0.00")) // the abstract has no part for it
        rule.onNodeWithText("Actual: 5.74%").assertExists() // 25.21 x 20% + 1 x 70%
    }

    @Test fun weeklyForm_ofAWeekWithoutAnAbstract_isTyped() {
        rule.setContent {
            NHATheme(themeMode = AppThemeMode.LIGHT) {
                AddWeeklyReportDialog(project = project, projectId = 1, onDismiss = {}, onSubmit = { _, _, _, _, _, _, _, _, _, _, _, _ -> },
                    today = LocalDate.of(2025, 3, 19), abstracts = listOf(abstract))
            }
        }
        rule.onNodeWithText("Actual % is from the abstract", substring = true).assertDoesNotExist()
        rule.onNodeWithTag("weekly_actual_Land Development").assert(hasText("0.00")).assert(typed)
    }
}
