package com.example.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.RegisteredUser

/**
 * The project's assigned accounts. The Super Admin ([editable]) picks from [users] once
 * they have loaded; everyone else, or a Super Admin whose list failed to load, sees the
 * [assignedStaff] text and cannot change it, so an empty list can never clear the project.
 * A uid in [selectedUids] that is not among [users] stays selected and is shown by uid.
 */
@Composable
fun AssignStaffField(
    editable: Boolean,
    usersLoaded: Boolean,
    users: List<RegisteredUser>,
    selectedUids: List<String>,
    assignedStaff: String,
    onSelectedChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    val label = "Assigned Field Staff / Manager"
    if (!editable || !usersLoaded) {
        OutlinedTextField(
            value = assignedStaff,
            onValueChange = {},
            label = { Text(label) },
            readOnly = true,
            enabled = false,
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledBorderColor = MaterialTheme.colorScheme.outline
            ),
            supportingText = if (editable) ({ Text("Account list unavailable; assignment can't be edited right now.") }) else null,
            modifier = modifier.fillMaxWidth().testTag("assigned_staff_readonly"),
            singleLine = true
        )
        return
    }

    val knownUids = users.map { it.uid }.toSet()
    val options = users + selectedUids.filter { it !in knownUids }.distinct().map { RegisteredUser(it, it) }

    Column(modifier = modifier.fillMaxWidth().testTag("assigned_staff_picker")) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(
            color = Color.Transparent,
            shape = RoundedCornerShape(4.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        ) {
            Column(modifier = Modifier.heightIn(max = 200.dp).verticalScroll(rememberScrollState())) {
                if (options.isEmpty()) {
                    Text("No registered accounts", modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                options.forEach { user ->
                    val checked = user.uid in selectedUids
                    val toggle = {
                        onSelectedChange(if (checked) selectedUids - user.uid else (selectedUids + user.uid).distinct())
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { toggle() }.padding(horizontal = 8.dp)
                    ) {
                        Checkbox(checked = checked, onCheckedChange = { toggle() })
                        Text(user.name, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}
