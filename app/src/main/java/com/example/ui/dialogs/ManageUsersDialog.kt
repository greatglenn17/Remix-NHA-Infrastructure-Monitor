package com.example.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.RegisteredUser
import com.example.data.model.UserRole

/** Roles the Super Admin can give; Super Admin itself stays with the designated account. */
private val ASSIGNABLE_ROLES = listOf(UserRole.ENGINEER_ADMIN, UserRole.FIELD_ENGINEER, UserRole.VIEWER)

/** Super Admin: every registered user with their role; tapping a role changes it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ManageUsersDialog(
    users: List<RegisteredUser>,
    loaded: Boolean,
    onSetRole: (RegisteredUser, UserRole, (String) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    var message by remember { mutableStateOf<String?>(null) }
    val others = users.filter { it.role != UserRole.SUPER_ADMIN.name }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        icon = { Icon(Icons.Default.ManageAccounts, contentDescription = null, tint = MaterialTheme.colorScheme.secondary) },
        title = { Text("Manage users & roles", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "A new role applies the next time that person opens the app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                message?.let { Text(it, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.secondary) }
                when {
                    !loaded -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Loading users… (needs internet)", style = MaterialTheme.typography.bodySmall)
                    }
                    others.isEmpty() -> Text("No other users have registered yet.", style = MaterialTheme.typography.bodyMedium)
                    else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.heightIn(max = 420.dp)) {
                        items(others, key = { it.uid }) { user ->
                            Column(modifier = Modifier.testTag("manage_user_${user.uid}")) {
                                Text(user.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                if (user.email.isNotBlank() && user.email != user.name) {
                                    Text(user.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    ASSIGNABLE_ROLES.forEach { role ->
                                        // A profile without a role counts as Field Engineer, as the claims function does.
                                        val current = user.role.ifBlank { UserRole.FIELD_ENGINEER.name } == role.name
                                        FilterChip(
                                            selected = current,
                                            onClick = { if (!current) onSetRole(user, role) { message = it } },
                                            label = { Text(role.label) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}
