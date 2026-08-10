package com.example.ui.login

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.R
import com.example.data.model.UserAccount
import com.example.data.model.UserRole
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: (email: String, password: String, displayName: String?, role: UserRole, isSignUp: Boolean, position: String?, office: String?) -> Unit,
    onSendPasswordReset: (email: String, newPassword: String?) -> Unit = { _, _ -> },
    isLoading: Boolean = false,
    errorMessage: String? = null,
    successMessage: String? = null,
    onClearError: () -> Unit = {},
    onClearSuccessMessage: () -> Unit = {}
) {
    var isSignUpMode by remember { mutableStateOf(false) }

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var positionTitle by remember { mutableStateOf("") }
    var officeRegion by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var selectedRole by remember { mutableStateOf(UserRole.ENGINEER_ADMIN) }
    var adminPin by remember { mutableStateOf("") }
    var adminPinError by remember { mutableStateOf<String?>(null) }
    var showForgotPasswordDialog by remember { mutableStateOf(false) }
    var resetEmailInput by remember { mutableStateOf("") }

    val keyboardController = LocalSoftwareKeyboardController.current
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Decorative background gradient circles
        Box(
            modifier = Modifier
                .size(300.dp)
                .offset(x = (-80).dp, y = (-50).dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            NhaBlue.copy(alpha = 0.25f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )
        Box(
            modifier = Modifier
                .size(250.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 60.dp, y = 60.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            GoldAccent.copy(alpha = 0.15f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Logo & Header
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(DarkSurfaceVariant, shape = CircleShape)
                    .border(2.dp, GoldAccent.copy(alpha = 0.8f), CircleShape)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_nha_logo),
                    contentDescription = "NHA Emblem",
                    tint = Color.Unspecified,
                    modifier = Modifier.size(64.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "National Housing Authority",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Black,
                    color = DarkTextPrimary,
                    fontSize = 22.sp,
                    letterSpacing = 0.5.sp
                ),
                textAlign = TextAlign.Center
            )

            Text(
                text = "Infrastructure Monitoring System",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    color = GoldAccent,
                    fontSize = 14.sp
                ),
                textAlign = TextAlign.Center
            )

            Surface(
                modifier = Modifier.padding(top = 8.dp),
                color = NhaBlue.copy(alpha = 0.2f),
                border = BorderStroke(1.dp, NhaBlue.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(
                    text = "REGION III • BULACAN DISTRICT OFFICE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = DarkTextSecondary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        fontSize = 10.sp
                    ),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Main Authentication Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = BorderStroke(1.dp, DarkBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Sign In / Register Mode Switcher
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DarkSurfaceVariant, RoundedCornerShape(12.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    isSignUpMode = false
                                    onClearError()
                                },
                            shape = RoundedCornerShape(10.dp),
                            color = if (!isSignUpMode) DarkCardHeader else Color.Transparent,
                            border = if (!isSignUpMode) BorderStroke(1.dp, DarkBorder) else null
                        ) {
                            Text(
                                text = "Firebase Sign In",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = if (!isSignUpMode) FontWeight.Bold else FontWeight.Medium,
                                    color = if (!isSignUpMode) DarkTextPrimary else DarkTextSecondary
                                ),
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .padding(vertical = 10.dp)
                                    .testTag("tab_sign_in")
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    isSignUpMode = true
                                    onClearError()
                                },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSignUpMode) DarkCardHeader else Color.Transparent,
                            border = if (isSignUpMode) BorderStroke(1.dp, DarkBorder) else null
                        ) {
                            Text(
                                text = "Create Account",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = if (isSignUpMode) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSignUpMode) DarkTextPrimary else DarkTextSecondary
                                ),
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .padding(vertical = 10.dp)
                                    .testTag("tab_create_account")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Success Message Banner (e.g. after Registration)
                    if (successMessage != null) {
                        LaunchedEffect(successMessage) {
                            isSignUpMode = false
                            password = ""
                        }
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            color = GreenAccent.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, GreenAccent.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = GreenAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = successMessage,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = DarkTextPrimary,
                                        fontSize = 12.sp
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = onClearSuccessMessage,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Dismiss",
                                        tint = DarkTextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Error Message Banner
                    if (errorMessage != null) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            color = ErrorRed.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = ErrorRed,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = errorMessage,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = DarkTextPrimary,
                                        fontSize = 12.sp
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = onClearError,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Dismiss Error",
                                        tint = DarkTextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Registration Specific Fields (If SignUp)
                    AnimatedVisibility(
                        visible = isSignUpMode,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column {
                            // Full Name Field
                            OutlinedTextField(
                                value = fullName,
                                onValueChange = { fullName = it },
                                label = { Text("Full Name") },
                                placeholder = { Text("e.g. Engr. Juan Dela Cruz") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = DarkTextSecondary
                                    )
                                },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_full_name"),
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
                            Spacer(modifier = Modifier.height(14.dp))

                            // Position / Title Field
                            OutlinedTextField(
                                value = positionTitle,
                                onValueChange = { positionTitle = it },
                                label = { Text("Position / Designation") },
                                placeholder = { Text("e.g. Supervising Project Engineer") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Badge,
                                        contentDescription = null,
                                        tint = DarkTextSecondary
                                    )
                                },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_position_title"),
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
                            Spacer(modifier = Modifier.height(14.dp))

                            // Office / Department / Region Field
                            OutlinedTextField(
                                value = officeRegion,
                                onValueChange = { officeRegion = it },
                                label = { Text("Office / Department / Region") },
                                placeholder = { Text("e.g. NHA Regional Office III") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Business,
                                        contentDescription = null,
                                        tint = DarkTextSecondary
                                    )
                                },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_office_region"),
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
                            // Admin Secret PIN Field (If registering as Super Admin or Engineer Admin)
                            if (selectedRole == UserRole.SUPER_ADMIN || selectedRole == UserRole.ENGINEER_ADMIN) {
                                OutlinedTextField(
                                    value = adminPin,
                                    onValueChange = {
                                        adminPin = it
                                        adminPinError = null
                                    },
                                    label = { Text("Admin Secret PIN (Required for ${selectedRole.label})") },
                                    placeholder = { Text("Enter 6-digit Secret PIN") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Key,
                                            contentDescription = null,
                                            tint = GoldAccent
                                        )
                                    },
                                    isError = adminPinError != null,
                                    supportingText = {
                                        if (adminPinError != null) {
                                            Text(text = adminPinError!!, color = ErrorRed, fontWeight = FontWeight.Bold)
                                        } else {
                                            Text(text = "Secret PIN required to register Admin role", color = DarkTextSecondary)
                                        }
                                    },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("input_admin_pin"),
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
                                Spacer(modifier = Modifier.height(14.dp))
                            }
                        }
                    }

                    // Email Field
                    OutlinedTextField(
                        value = email,
                        onValueChange = {
                            email = it
                            if (errorMessage != null) onClearError()
                        },
                        label = { Text("Email Address") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = null,
                                tint = DarkTextSecondary
                            )
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_email"),
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

                    Spacer(modifier = Modifier.height(14.dp))

                    // Password Field
                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            if (errorMessage != null) onClearError()
                        },
                        label = { Text("Password") },
                        placeholder = { Text("••••••••") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = DarkTextSecondary
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Toggle Password Visibility",
                                    tint = DarkTextSecondary
                                )
                            }
                        },
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = {
                            keyboardController?.hide()
                            if (email.isNotBlank() && password.isNotBlank()) {
                                onLoginSuccess(
                                    email.trim(),
                                    password.trim(),
                                    fullName.trim().ifBlank { null },
                                    selectedRole,
                                    isSignUpMode,
                                    positionTitle.trim().ifBlank { null },
                                    officeRegion.trim().ifBlank { null }
                                )
                            }
                        }),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_password"),
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

                    if (!isSignUpMode) {
                        TextButton(
                            onClick = {
                                resetEmailInput = email.ifBlank { "" }
                                showForgotPasswordDialog = true
                            },
                            modifier = Modifier
                                .align(Alignment.End)
                                .testTag("btn_forgot_password")
                        ) {
                            Text(
                                text = "Forgot password?",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = GoldAccent,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    // Select User Role Selection
                    Text(
                        text = if (isSignUpMode) "Initial User Role:" else "Account Role Level:",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = DarkTextSecondary,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier
                            .align(Alignment.Start)
                            .padding(bottom = 8.dp)
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            UserRoleChoiceChip(
                                role = UserRole.SUPER_ADMIN,
                                title = "Super Admin",
                                subtitle = "Full System Control",
                                icon = Icons.Default.AdminPanelSettings,
                                isSelected = selectedRole == UserRole.SUPER_ADMIN,
                                onClick = { selectedRole = UserRole.SUPER_ADMIN },
                                modifier = Modifier.weight(1f)
                            )

                            UserRoleChoiceChip(
                                role = UserRole.ENGINEER_ADMIN,
                                title = "Engineer Admin",
                                subtitle = "Project Management",
                                icon = Icons.Default.VerifiedUser,
                                isSelected = selectedRole == UserRole.ENGINEER_ADMIN,
                                onClick = { selectedRole = UserRole.ENGINEER_ADMIN },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            UserRoleChoiceChip(
                                role = UserRole.FIELD_ENGINEER,
                                title = "Field Engineer",
                                subtitle = "Reports, Payments, Weather & Issues",
                                icon = Icons.Default.Engineering,
                                isSelected = selectedRole == UserRole.FIELD_ENGINEER,
                                onClick = { selectedRole = UserRole.FIELD_ENGINEER },
                                modifier = Modifier.weight(1f)
                            )

                            UserRoleChoiceChip(
                                role = UserRole.VIEWER,
                                title = "Viewer",
                                subtitle = "Read-Only Dashboard",
                                icon = Icons.Default.Visibility,
                                isSelected = selectedRole == UserRole.VIEWER,
                                onClick = { selectedRole = UserRole.VIEWER },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Primary Submit Button
                    Button(
                        onClick = {
                            keyboardController?.hide()
                            if (isSignUpMode && (selectedRole == UserRole.SUPER_ADMIN || selectedRole == UserRole.ENGINEER_ADMIN)) {
                                if (adminPin.trim() != "021793") {
                                    adminPinError = "Invalid Secret PIN! Contact your Principal Engineer or Super Admin for the authorized PIN."
                                    return@Button
                                }
                            }
                            onLoginSuccess(
                                email.trim(),
                                password.trim(),
                                fullName.trim().ifBlank { null },
                                selectedRole,
                                isSignUpMode,
                                positionTitle.trim().ifBlank { null },
                                officeRegion.trim().ifBlank { null }
                            )
                        },
                        enabled = !isLoading && email.isNotBlank() && password.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("login_submit_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GoldAccent,
                            contentColor = Color.Black,
                            disabledContainerColor = DarkSurfaceVariant,
                            disabledContentColor = DarkTextSecondary
                        )
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = Color.Black,
                                strokeWidth = 2.5.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Authenticating Account...",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        } else {
                            Icon(
                                imageVector = if (isSignUpMode) Icons.Default.PersonAdd else Icons.Default.Login,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isSignUpMode) "Register New Account" else "Sign In",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Firebase & Encryption Security Footer
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = DarkTextSecondary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Firebase Auth Secured • AES-256 Encrypted Storage & Backup",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = DarkTextSecondary,
                        fontSize = 11.sp
                    )
                )
            }
        }

        if (showForgotPasswordDialog) {
            var newPassInput by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showForgotPasswordDialog = false },
                title = {
                    Text(
                        text = "Password Recovery & Reset",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = DarkTextPrimary)
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Enter your registered email below to send a Firebase email link OR type a new password for instant update.",
                            style = MaterialTheme.typography.bodySmall.copy(color = DarkTextSecondary)
                        )
                        OutlinedTextField(
                            value = resetEmailInput,
                            onValueChange = { resetEmailInput = it },
                            label = { Text("Registered Email Address") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GoldAccent,
                                unfocusedBorderColor = DarkBorder,
                                focusedTextColor = DarkTextPrimary,
                                unfocusedTextColor = DarkTextPrimary,
                                focusedContainerColor = DarkSurfaceVariant,
                                unfocusedContainerColor = DarkSurfaceVariant
                            )
                        )

                        OutlinedTextField(
                            value = newPassInput,
                            onValueChange = { newPassInput = it },
                            label = { Text("New Password (Instant In-App Reset)") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GoldAccent,
                                unfocusedBorderColor = DarkBorder,
                                focusedTextColor = DarkTextPrimary,
                                unfocusedTextColor = DarkTextPrimary,
                                focusedContainerColor = DarkSurfaceVariant,
                                unfocusedContainerColor = DarkSurfaceVariant
                            )
                        )
                    }
                },
                confirmButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                if (resetEmailInput.isNotBlank()) {
                                    onSendPasswordReset(resetEmailInput.trim(), null)
                                    showForgotPasswordDialog = false
                                }
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldAccent),
                            border = BorderStroke(1.dp, GoldAccent)
                        ) {
                            Text("Send Email Link", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                if (resetEmailInput.isNotBlank() && newPassInput.isNotBlank()) {
                                    onSendPasswordReset(resetEmailInput.trim(), newPassInput.trim())
                                    showForgotPasswordDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = Color.Black)
                        ) {
                            Text("Update Password", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showForgotPasswordDialog = false }) {
                        Text("Cancel", color = DarkTextSecondary)
                    }
                },
                containerColor = DarkSurface,
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}

@Composable
private fun UserRoleChoiceChip(
    role: UserRole,
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) GoldAccent.copy(alpha = 0.15f) else DarkSurfaceVariant,
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) GoldAccent else DarkBorder
        ),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) GoldAccent else DarkTextSecondary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) DarkTextPrimary else DarkTextSecondary,
                        fontSize = 12.sp
                    )
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = DarkTextSecondary,
                        fontSize = 9.5.sp
                    )
                )
            }
        }
    }
}
