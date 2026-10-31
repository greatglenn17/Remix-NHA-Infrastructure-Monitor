package com.example.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.Project
import com.example.ui.theme.LocalNHAColors
import com.example.ui.viewmodel.PortfolioUiState
import com.example.util.AttentionFlag
import com.example.util.AttentionLevel
import com.example.util.Portfolio
import com.example.util.PortfolioFilter
import com.example.util.PortfolioItem

private const val ATTENTION_PREVIEW = 5
private val SORT_OPTIONS = listOf(Portfolio.NEEDS_ATTENTION_SORT, "Variance (Worst First)", "Variance (Best First)", "Completion Date", "Name")
private val STATUS_OPTIONS = listOf("All", "On-going", "Behind Schedule", "Suspended")

/**
 * Portfolio home: header, four counts, needs-attention queue and the project list (spec "Portfolio home").
 * [projectsFirst] puts the list above the counts and queue, for a field engineer who opens the app to reach their own sites.
 */
@Composable
fun PortfolioContent(
    state: PortfolioUiState,
    officeName: String,
    searchQuery: String,
    sortOrder: String,
    statusFilter: String,
    typeFilter: String,
    typeOptions: List<String>,
    emptyMessage: Pair<String, String>,
    onFilter: (PortfolioFilter?) -> Unit,
    onSearch: (String) -> Unit,
    onSort: (String) -> Unit,
    onStatus: (String) -> Unit,
    onType: (String) -> Unit,
    onOpen: (Project) -> Unit,
    onEdit: ((Project) -> Unit)?,
    onDelete: ((Project) -> Unit)?,
    modifier: Modifier = Modifier,
    projectsFirst: Boolean = false,
    emptyActions: @Composable () -> Unit = {}
) {
    val scheme = MaterialTheme.colorScheme
    var showAllAttention by rememberSaveable { mutableStateOf(false) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item(key = "header") {
                Column {
                    Text(officeName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = scheme.onSurface)
                    Text(
                        if (state.totalProjects == 1) "1 project" else "${state.totalProjects} projects",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant
                    )
                }
            }

            if (state.totalProjects == 0) {
                item(key = "empty") { EmptyState(emptyMessage, emptyActions) }
                return@LazyColumn
            }

            if (!projectsFirst) overview(state, wide, showAllAttention, { showAllAttention = !showAllAttention }, onFilter, onOpen)

            item(key = "list_header") {
                ListHeader(
                    shown = state.rows.size,
                    filter = state.filter,
                    onClearFilter = { onFilter(null) },
                    searchQuery = searchQuery, sortOrder = sortOrder, statusFilter = statusFilter, typeFilter = typeFilter,
                    typeOptions = typeOptions, wide = wide,
                    onSearch = onSearch, onSort = onSort, onStatus = onStatus, onType = onType
                )
            }

            if (state.rows.isEmpty()) {
                item(key = "no_match") { EmptyState(emptyMessage) {} }
            } else {
                items(state.rows, key = { it.project.id }) { item ->
                    PortfolioRow(item, wide, onOpen, onEdit, onDelete)
                }
            }

            if (projectsFirst) {
                item(key = "overview_header") {
                    Text(
                        "Overview",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurface,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
                overview(state, wide, showAllAttention, { showAllAttention = !showAllAttention }, onFilter, onOpen)
            }
        }
    }
}

private fun LazyListScope.overview(
    state: PortfolioUiState,
    wide: Boolean,
    showAllAttention: Boolean,
    onToggleAll: () -> Unit,
    onFilter: (PortfolioFilter?) -> Unit,
    onOpen: (Project) -> Unit
) {
    item(key = "counts") { PortfolioCounts(state.summary, state.filter, wide, onFilter) }

    // Hidden when nothing needs attention, so the overview has no empty box.
    if (state.attention.isNotEmpty()) item(key = "attention") {
        AttentionCard(queue = state.attention, showAll = showAllAttention, onToggleAll = onToggleAll, onOpen = onOpen)
    }
}

@Composable
internal fun AttentionCard(
    queue: List<Pair<PortfolioItem, AttentionFlag>>,
    showAll: Boolean,
    onToggleAll: () -> Unit,
    onOpen: (Project) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val colors = LocalNHAColors.current
    Surface(shape = RoundedCornerShape(12.dp), color = scheme.surface, border = BorderStroke(1.dp, scheme.outlineVariant), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                Text("Needs attention", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
                if (queue.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Badge("${queue.size}", colors.behind, colors.behindContainer)
                }
            }
            if (queue.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = colors.onTrack, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Nothing needs attention", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                }
                return@Column
            }
            val shown = if (showAll) queue else queue.take(ATTENTION_PREVIEW)
            shown.forEach { (item, flag) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(item.project) }
                        .heightIn(min = 48.dp)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(levelIcon(flag.level), contentDescription = levelName(flag.level), tint = levelColor(flag.level, colors), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            item.project.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = scheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(flag.message, style = MaterialTheme.typography.bodySmall, color = levelColor(flag.level, colors))
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = scheme.onSurfaceVariant)
                }
            }
            if (queue.size > ATTENTION_PREVIEW) {
                TextButton(onClick = onToggleAll, modifier = Modifier.padding(horizontal = 4.dp)) {
                    Text(if (showAll) "Show fewer" else "See all ${queue.size}")
                }
            }
        }
    }
}

@Composable
private fun ListHeader(
    shown: Int,
    filter: PortfolioFilter?,
    onClearFilter: () -> Unit,
    searchQuery: String,
    sortOrder: String,
    statusFilter: String,
    typeFilter: String,
    typeOptions: List<String>,
    wide: Boolean,
    onSearch: (String) -> Unit,
    onSort: (String) -> Unit,
    onStatus: (String) -> Unit,
    onType: (String) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("All projects", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
            Spacer(Modifier.width(8.dp))
            Text("$shown shown", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            if (filter != null) {
                InputChip(
                    selected = true,
                    onClick = onClearFilter,
                    label = { Text(filterLabel(filter)) },
                    trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Clear filter", modifier = Modifier.size(16.dp)) }
                )
            }
        }
        val search: @Composable (Modifier) -> Unit = { m -> SearchField(searchQuery, onSearch, m) }
        val pickers: @Composable (Modifier) -> Unit = { m ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = m) {
                Picker("Status: $statusFilter", "status_dropdown_button", "Filter by project status", STATUS_OPTIONS, statusFilter, onStatus, Modifier.weight(1f))
                Picker("Type: $typeFilter", "type_dropdown_button", "Filter by project type", typeOptions, typeFilter, onType, Modifier.weight(1f))
                SortButton(sortOrder, onSort)
            }
        }
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                search(Modifier.weight(1f))
                pickers(Modifier.weight(1.2f))
            }
        } else {
            search(Modifier.fillMaxWidth())
            pickers(Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SearchField(query: String, onSearch: (String) -> Unit, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    BasicTextField(
        value = query,
        onValueChange = onSearch,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = scheme.onSurface),
        cursorBrush = SolidColor(scheme.primary),
        modifier = modifier.height(48.dp).testTag("search_project_input"),
        decorationBox = { inner ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = scheme.surface,
                border = BorderStroke(1.dp, if (query.isNotEmpty()) scheme.primary else scheme.outline),
                modifier = Modifier.fillMaxSize()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp)) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text("Search name, location, contractor...", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, maxLines = 1)
                        }
                        inner()
                    }
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onSearch("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search", tint = scheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun Picker(
    label: String,
    tag: String,
    menuTitle: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier
) {
    val scheme = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { open = true },
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, if (selected != "All") scheme.primary else scheme.outlineVariant),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = scheme.surface, contentColor = scheme.onSurface),
            contentPadding = PaddingValues(start = 12.dp, end = 4.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag(tag)
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        OptionsMenu(open, { open = false }, menuTitle, options, selected, onSelect)
    }
}

@Composable
private fun SortButton(sortOrder: String, onSort: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { open = true },
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, scheme.outlineVariant),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = scheme.surface, contentColor = scheme.onSurface),
            contentPadding = PaddingValues(horizontal = 12.dp),
            modifier = Modifier.height(48.dp).testTag("sort_button")
        ) {
            Icon(Icons.Default.Sort, contentDescription = "Sort: $sortOrder", modifier = Modifier.size(18.dp))
        }
        OptionsMenu(open, { open = false }, "Sort by", SORT_OPTIONS, sortOrder, onSort)
    }
}

@Composable
private fun OptionsMenu(
    open: Boolean,
    onDismiss: () -> Unit,
    title: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    DropdownMenu(expanded = open, onDismissRequest = onDismiss) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        HorizontalDivider(color = scheme.outlineVariant)
        options.forEach { option ->
            DropdownMenuItem(
                text = { Text(option, fontWeight = if (option == selected) FontWeight.Bold else FontWeight.Normal) },
                onClick = { onSelect(option); onDismiss() },
                leadingIcon = { if (option == selected) Icon(Icons.Default.Check, contentDescription = "Selected", tint = scheme.primary) }
            )
        }
    }
}

@Composable
private fun EmptyState(message: Pair<String, String>, actions: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 16.dp)
    ) {
        Icon(Icons.Default.FolderOff, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(16.dp))
        Text(message.first, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = scheme.onSurface, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(message.second, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        actions()
    }
}

private fun filterLabel(filter: PortfolioFilter) = when (filter) {
    PortfolioFilter.BEHIND -> "Behind schedule"
    PortfolioFilter.BILLING_AHEAD -> "Billing ahead of work"
    PortfolioFilter.WAITING_APPROVAL -> "Waiting approval"
    PortfolioFilter.DUE_SOON -> "Due in 30 days"
}

internal fun levelIcon(level: AttentionLevel): ImageVector = when (level) {
    AttentionLevel.CRITICAL -> Icons.Default.Error
    AttentionLevel.WARNING -> Icons.Default.Warning
    AttentionLevel.STALE -> Icons.Default.Schedule
}

internal fun levelName(level: AttentionLevel) = when (level) {
    AttentionLevel.CRITICAL -> "Critical"
    AttentionLevel.WARNING -> "Warning"
    AttentionLevel.STALE -> "No recent update"
}
