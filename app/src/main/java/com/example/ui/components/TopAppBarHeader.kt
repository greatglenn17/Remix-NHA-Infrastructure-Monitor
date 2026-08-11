package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.*

import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.launch
import com.example.BuildConfig
import com.example.R
import com.example.data.model.AppThemeMode
import com.example.data.model.UserAccount
import com.example.data.model.UserRole
import com.example.ui.dialogs.AdminElevationDialog
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeaderBadge(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    textColor: Color,
    backgroundColor: Color,
    borderColor: Color,
    onClick: (() -> Unit)? = null,
    isSpinning: Boolean = false
) {
    val content = @Composable {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (isSpinning) {
                val infiniteTransition = rememberInfiniteTransition(label = "spin_trans")
                val angle by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "spin_angle"
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier
                        .size(10.dp)
                        .graphicsLayer { rotationZ = angle }
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(10.dp)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = textColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 8.5.sp,
                    letterSpacing = 0.4.sp
                ),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    if (onClick != null) {
        Surface(
            onClick = onClick,
            color = backgroundColor,
            border = BorderStroke(1.dp, borderColor),
            shape = RoundedCornerShape(50.dp)
        ) {
            content()
        }
    } else {
        Surface(
            color = backgroundColor,
            border = BorderStroke(1.dp, borderColor),
            shape = RoundedCornerShape(50.dp)
        ) {
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopAppBarHeader(
    currentUser: UserAccount,
    onSwitchUser: (UserAccount) -> Unit,
    onBackClick: (() -> Unit)? = null,
    isSyncing: Boolean = false,
    onOpenDriveSync: (() -> Unit)? = null,
    onEditProfileClick: (() -> Unit)? = null,
    onSignOut: (() -> Unit)? = null,
    unreadNotificationCount: Int = 0,
    onOpenNotifications: (() -> Unit)? = null,
    appThemeMode: AppThemeMode = AppThemeMode.SYSTEM,
    onSetThemeMode: ((AppThemeMode) -> Unit)? = null,
    onElevateRole: ((UserRole) -> Unit)? = null,
    onElevateRoleWithProfile: ((UserRole, String?, String?) -> Unit)? = null
) {
    var showUserMenu by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var pendingElevationRole by remember { mutableStateOf<UserRole?>(null) }
    var availableUpdate by remember { mutableStateOf<com.example.util.AppUpdateInfo?>(null) }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    Surface(
        color = DarkBackground,
        tonalElevation = 0.dp,
        border = BorderStroke(1.dp, DarkBorder.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (onBackClick != null) {
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("top_bar_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = DarkTextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .background(Color.Transparent, shape = CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_nha_logo),
                            contentDescription = "NHA Logo",
                            tint = Color.Unspecified,
                            modifier = Modifier.size(46.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "NHA Monitoring Application",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = DarkTextPrimary,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp,
                                lineHeight = 18.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "BULACAN DISTRICT OFFICE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = DarkTextSecondary,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.6.sp,
                                fontSize = 9.5.sp,
                                lineHeight = 11.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HeaderBadge(
                                text = if (isSyncing) "SYNCING" else "ONLINE",
                                icon = if (isSyncing) Icons.Default.Sync else Icons.Default.CloudDone,
                                iconTint = if (isSyncing) Color(0xFFFBBF24) else Color(0xFF4ADE80),
                                textColor = if (isSyncing) Color(0xFFFBBF24) else Color(0xFF4ADE80),
                                backgroundColor = if (isSyncing) Color(0xFFF59E0B).copy(alpha = 0.2f) else Color(0xFF16A34A).copy(alpha = 0.2f),
                                borderColor = if (isSyncing) Color(0xFFF59E0B).copy(alpha = 0.8f) else Color(0xFF16A34A).copy(alpha = 0.8f),
                                isSpinning = isSyncing
                            )

                            if (onOpenDriveSync != null) {
                                HeaderBadge(
                                    text = "CLOUD SYNC",
                                    icon = Icons.Default.CloudSync,
                                    iconTint = Color(0xFF38BDF8),
                                    textColor = Color(0xFF38BDF8),
                                    backgroundColor = Color(0xFF0F766E).copy(alpha = 0.25f),
                                    borderColor = Color(0xFF38BDF8).copy(alpha = 0.8f),
                                    onClick = onOpenDriveSync
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Notification Bell for Super Admin & Engineer Admin
                    if ((currentUser.role == UserRole.SUPER_ADMIN || currentUser.role == UserRole.ENGINEER_ADMIN) && onOpenNotifications != null) {
                        IconButton(
                            onClick = onOpenNotifications,
                            modifier = Modifier
                                .size(38.dp)
                                .testTag("admin_notifications_bell")
                        ) {
                            BadgedBox(
                                badge = {
                                    if (unreadNotificationCount > 0) {
                                        Badge(
                                            containerColor = StatusRedText,
                                            contentColor = Color.White
                                        ) {
                                            Text(text = if (unreadNotificationCount > 9) "9+" else "$unreadNotificationCount")
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Notifications,
                                    contentDescription = "Notifications",
                                    tint = if (unreadNotificationCount > 0) Color(0xFF38BDF8) else DarkTextSecondary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    // Profile / Role Switcher Button Original Form
                    Box {
                    OutlinedButton(
                        onClick = { showUserMenu = true },
                        shape = CircleShape,
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = DarkSurfaceVariant,
                            contentColor = DarkTextPrimary
                        ),
                        border = BorderStroke(1.dp, DarkBorder),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("role_switcher_button")
                    ) {
                        Icon(
                            imageVector = when (currentUser.role) {
                                UserRole.SUPER_ADMIN -> Icons.Default.AdminPanelSettings
                                UserRole.ENGINEER_ADMIN -> Icons.Default.VerifiedUser
                                UserRole.FIELD_ENGINEER -> Icons.Default.Engineering
                                UserRole.VIEWER -> Icons.Default.Visibility
                            },
                            contentDescription = "Role Icon",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when (currentUser.role) {
                                UserRole.SUPER_ADMIN -> "SA"
                                UserRole.ENGINEER_ADMIN -> "EA"
                                UserRole.FIELD_ENGINEER -> "FE"
                                UserRole.VIEWER -> "VW"
                            },
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = DarkTextPrimary
                            )
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Dropdown",
                            tint = DarkTextPrimary
                        )
                    }

                    DropdownMenu(
                        expanded = showUserMenu,
                        onDismissRequest = { showUserMenu = false },
                        modifier = Modifier.background(DarkSurface)
                    ) {
                        Text(
                            text = "LOGGED IN ACCOUNT",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = DarkTextSecondary,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            Text(
                                text = currentUser.name,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = DarkTextPrimary
                                )
                            )
                            Text(
                                text = "${currentUser.title} • ${currentUser.office}",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF38BDF8), fontWeight = FontWeight.SemiBold)
                            )
                        }

                        if (onEditProfileClick != null) {
                            DropdownMenuItem(
                                text = { Text("Update Position & Office", color = GoldAccent, fontWeight = FontWeight.Bold) },
                                leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null, tint = GoldAccent) },
                                onClick = {
                                    showUserMenu = false
                                    onEditProfileClick()
                                }
                            )
                        }

                        HorizontalDivider(color = DarkBorder)
                        Text(
                            text = "APPEARANCE / THEME",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = DarkTextSecondary,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AppThemeMode.values().forEach { mode ->
                                val isSelected = appThemeMode == mode
                                Surface(
                                    onClick = { onSetThemeMode?.invoke(mode) },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) Color(0xFF38BDF8).copy(alpha = 0.25f) else DarkSurfaceVariant,
                                    border = BorderStroke(1.dp, if (isSelected) Color(0xFF38BDF8) else DarkBorder),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(vertical = 6.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            imageVector = when (mode) {
                                                AppThemeMode.SYSTEM -> Icons.Default.SettingsSuggest
                                                AppThemeMode.LIGHT -> Icons.Default.LightMode
                                                AppThemeMode.DARK -> Icons.Default.DarkMode
                                            },
                                            contentDescription = mode.displayName,
                                            tint = if (isSelected) Color(0xFF38BDF8) else DarkTextSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = when (mode) {
                                                AppThemeMode.SYSTEM -> "System"
                                                AppThemeMode.LIGHT -> "Light"
                                                AppThemeMode.DARK -> "Dark"
                                            },
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = if (isSelected) Color(0xFF38BDF8) else DarkTextPrimary,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 10.5.sp
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        // Role Elevation Section (Super Admin Power & Active Role Switcher)
                        if (onElevateRole != null) {
                            HorizontalDivider(color = DarkBorder)
                            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                                Text(
                                    text = "Active Role Elevation / Switcher",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = GoldAccent
                                    )
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    UserRole.entries.forEach { roleOption ->
                                        val isCurrent = currentUser.role == roleOption
                                        FilterChip(
                                            selected = isCurrent,
                                            onClick = {
                                                if (roleOption == UserRole.SUPER_ADMIN || roleOption == UserRole.ENGINEER_ADMIN) {
                                                    pendingElevationRole = roleOption
                                                } else {
                                                    if (onElevateRoleWithProfile != null) {
                                                        onElevateRoleWithProfile(roleOption, null, null)
                                                    } else {
                                                        onElevateRole?.invoke(roleOption)
                                                    }
                                                }
                                                showUserMenu = false
                                            },
                                            label = {
                                                Text(
                                                    text = roleOption.label,
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                                        fontSize = 10.sp
                                                    )
                                                )
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = GoldAccent.copy(alpha = 0.25f),
                                                selectedLabelColor = GoldAccent,
                                                containerColor = DarkSurfaceVariant,
                                                labelColor = DarkTextSecondary
                                            ),
                                            border = FilterChipDefaults.filterChipBorder(
                                                enabled = true,
                                                selected = isCurrent,
                                                borderColor = DarkBorder,
                                                selectedBorderColor = GoldAccent
                                            ),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = DarkBorder)
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "About NHA Monitor",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF38BDF8)
                                    )
                                )
                            },
                            onClick = {
                                showUserMenu = false
                                showAboutDialog = true
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "About App",
                                    tint = Color(0xFF38BDF8)
                                )
                            }
                        )

                        if (onSignOut != null) {
                            HorizontalDivider(color = DarkBorder)
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Sign Out",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = ErrorRed
                                        )
                                    )
                                },
                                onClick = {
                                    showUserMenu = false
                                    onSignOut()
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Logout,
                                        contentDescription = "Sign Out",
                                        tint = ErrorRed
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("CLOSE", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "About NHA Monitor",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = DarkTextPrimary
                        )
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        color = DarkSurfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, DarkBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "APPLICATION CREATOR",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color(0xFF38BDF8),
                                    fontWeight = FontWeight.ExtraBold
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "ENGR. GLENN C. APROVECHADO",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = DarkTextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = "Principal Engineer C • Bulacan District Office",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = DarkTextSecondary
                                )
                            )
                        }
                    }

                    Surface(
                        color = DarkSurfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, DarkBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "BUILD & SYSTEM INFO",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color(0xFF4ADE80),
                                    fontWeight = FontWeight.ExtraBold
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Version: v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = DarkTextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = "National Housing Authority (NHA) • Region III",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = DarkTextSecondary
                                )
                            )
                            Text(
                                text = "Engine: Android Jetpack Compose + In-App Auto Updater",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = DarkTextSecondary
                                )
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    isCheckingUpdate = true
                                    kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                                        val info = com.example.util.AppUpdateManager.checkForUpdates()
                                        isCheckingUpdate = false
                                        if (info.isUpdateAvailable) {
                                            availableUpdate = info
                                            showAboutDialog = false
                                        } else {
                                            android.widget.Toast.makeText(
                                                context,
                                                "NHA Infrastructure Monitor is up to date!\nVersion: v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE}) is the latest release.",
                                                android.widget.Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF0284C7),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SystemUpdate,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isCheckingUpdate) "CHECKING FOR UPDATES..." else "CHECK FOR LIVE UPDATES",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (availableUpdate != null) {
        com.example.ui.dialogs.UpdateAvailableDialog(
            updateInfo = availableUpdate!!,
            onDismiss = { availableUpdate = null }
        )
    }

    if (pendingElevationRole != null) {
        AdminElevationDialog(
            currentUser = currentUser,
            targetRole = pendingElevationRole!!,
            onDismiss = { pendingElevationRole = null },
            onElevateSuccess = { role, position, office ->
                if (onElevateRoleWithProfile != null) {
                    onElevateRoleWithProfile(role, position, office)
                } else {
                    onElevateRole?.invoke(role)
                }
                pendingElevationRole = null
            }
        )
    }
}

}




