package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.*
import com.example.data.model.AppThemeMode
import com.example.data.model.RegisteredUser
import com.example.ui.dialogs.ManageUsersDialog
import com.example.ui.theme.NHATheme
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AccessScreenshotTest {
    @get:Rule val rule = createComposeRule()

    // A Super Viewer has no "See all projects" switch: the role already sees everything.
    @Test fun manage_users_super_viewer_and_see_all_light() {
        rule.setContent {
            NHATheme(themeMode = AppThemeMode.LIGHT) {
                ManageUsersDialog(
                    users = listOf(
                        RegisteredUser("a", "Engr. Ana Cruz", "ana@nha.gov.ph", "FIELD_ENGINEER", seesAllProjects = true),
                        RegisteredUser("b", "Dir. Ben Santos", "ben@nha.gov.ph", "SUPER_VIEWER"),
                        RegisteredUser("c", "Engr. Carlo Reyes", "carlo@nha.gov.ph", "ENGINEER_ADMIN")
                    ),
                    loaded = true,
                    onSetRole = { _, _, _ -> },
                    onSetSeesAll = { _, _, _ -> },
                    onApprove = { _, _ -> },
                    onDismiss = {}
                )
            }
        }
        rule.onNodeWithText("Dir. Ben Santos").assertExists()
        captureScreenRoboImage("src/test/screenshots/manage_users_super_viewer_light.png")
    }

    // A new account waits for approval at the top of the list, with an Approve button that sends it.
    @Test fun manage_users_pending_account_listed_first_with_approve() {
        var approved: RegisteredUser? = null
        val pending = RegisteredUser("d", "Engr. Dana Lim", "dana@nha.gov.ph", "FIELD_ENGINEER", approved = false)
        rule.setContent {
            NHATheme(themeMode = AppThemeMode.LIGHT) {
                ManageUsersDialog(
                    users = listOf(RegisteredUser("a", "Engr. Ana Cruz", "ana@nha.gov.ph", "FIELD_ENGINEER"), pending),
                    loaded = true,
                    onSetRole = { _, _, _ -> },
                    onSetSeesAll = { _, _, _ -> },
                    onApprove = { user, _ -> approved = user },
                    onDismiss = {}
                )
            }
        }
        rule.onAllNodesWithText("Waiting for approval: can only view").assertCountEquals(1)
        rule.onNodeWithTag("approve_d").performClick()
        org.junit.Assert.assertEquals(pending, approved)
    }
}
