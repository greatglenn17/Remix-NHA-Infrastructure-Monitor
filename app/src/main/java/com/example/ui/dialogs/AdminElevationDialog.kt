package com.example.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.UserAccount
import com.example.data.model.UserRole
import com.example.ui.theme.*

@Composable
fun AdminElevationDialog(
    currentUser: UserAccount,
    targetRole: UserRole,
    onDismiss: () -> Unit,
    onElevateSuccess: (UserRole, position: String, office: String) -> Unit
) {
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    var isPinVisible by remember { mutableStateOf(false) }

    var positionTitle by remember { mutableStateOf(currentUser.title.ifBlank { "Principal Engineer C" }) }
    var officeRegion by remember { mutableStateOf(currentUser.office.ifBlank { "Bulacan District Office" }) }

    val secretPin = "021793"

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AdminPanelSettings,
                    contentDescription = null,
                    tint = GoldAccent,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Admin Elevation & Security PIN",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = DarkTextPrimary
                    )
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = GoldAccent.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Elevating to ${targetRole.label} requires Secret Admin PIN. Authorized personnel receive full administrative & auditing powers.",
                        style = MaterialTheme.typography.bodySmall.copy(color = GoldAccent, fontSize = 12.sp),
                        modifier = Modifier.padding(10.dp)
                    )
                }

                // PIN Input Field
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = {
                        pinInput = it
                        if (pinError != null) pinError = null
                    },
                    label = { Text("Enter 6-Digit Secret Admin PIN") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = GoldAccent) },
                    trailingIcon = {
                        IconButton(onClick = { isPinVisible = !isPinVisible }) {
                            Icon(
                                imageVector = if (isPinVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle PIN Visibility",
                                tint = DarkTextSecondary
                            )
                        }
                    },
                    visualTransformation = if (isPinVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    isError = pinError != null,
                    modifier = Modifier.fillMaxWidth().testTag("admin_pin_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldAccent,
                        unfocusedBorderColor = DarkBorder,
                        focusedLabelColor = GoldAccent,
                        unfocusedLabelColor = DarkTextSecondary,
                        focusedTextColor = DarkTextPrimary,
                        unfocusedTextColor = DarkTextPrimary,
                        focusedContainerColor = DarkSurfaceVariant,
                        unfocusedContainerColor = DarkSurfaceVariant
                    )
                )

                if (pinError != null) {
                    Text(
                        text = pinError!!,
                        style = MaterialTheme.typography.bodySmall.copy(color = StatusRedText, fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }

                HorizontalDivider(color = DarkBorder, modifier = Modifier.padding(vertical = 4.dp))

                Text(
                    text = "Role Customization & Position Title",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = DarkTextSecondary)
                )

                // Position Title
                OutlinedTextField(
                    value = positionTitle,
                    onValueChange = { positionTitle = it },
                    label = { Text("Position / Title") },
                    leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null, tint = DarkTextSecondary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = DarkBorder,
                        focusedLabelColor = Color(0xFF38BDF8),
                        unfocusedLabelColor = DarkTextSecondary,
                        focusedTextColor = DarkTextPrimary,
                        unfocusedTextColor = DarkTextPrimary,
                        focusedContainerColor = DarkSurfaceVariant,
                        unfocusedContainerColor = DarkSurfaceVariant
                    )
                )

                // Office Region
                OutlinedTextField(
                    value = officeRegion,
                    onValueChange = { officeRegion = it },
                    label = { Text("Office / Department") },
                    leadingIcon = { Icon(Icons.Default.Business, contentDescription = null, tint = DarkTextSecondary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = DarkBorder,
                        focusedLabelColor = Color(0xFF38BDF8),
                        unfocusedLabelColor = DarkTextSecondary,
                        focusedTextColor = DarkTextPrimary,
                        unfocusedTextColor = DarkTextPrimary,
                        focusedContainerColor = DarkSurfaceVariant,
                        unfocusedContainerColor = DarkSurfaceVariant
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (pinInput.trim() == secretPin) {
                        onElevateSuccess(targetRole, positionTitle.trim(), officeRegion.trim())
                    } else {
                        pinError = "Invalid Secret PIN! Contact your Principal Engineer or Super Admin for authorization."
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = Color.Black),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("admin_pin_submit")
            ) {
                Text("Authorize & Elevate", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = DarkTextSecondary)
            }
        }
    )
}
