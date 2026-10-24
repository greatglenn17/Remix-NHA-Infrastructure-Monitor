package com.example.ui.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.model.UserAccount
import com.example.data.model.UserRole
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkTextPrimary

/** Client-side elevation is intentionally disabled. Roles are backend claims. */
@Composable
fun AdminElevationDialog(
    currentUser: UserAccount,
    targetRole: UserRole,
    onDismiss: () -> Unit,
    onElevateSuccess: (UserRole, position: String, office: String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = { Text("Role changes are managed centrally", color = DarkTextPrimary) },
        text = {
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(
                    "This device cannot change roles or privileges. An authorized backend administrator must update the user's server-controlled role.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = DarkTextPrimary
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
