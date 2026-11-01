package com.example.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.model.Project
import com.example.ui.theme.LocalNHAColors
import com.example.util.AttentionFlag
import com.example.util.PortfolioItem

/** Right pane on two panes when no project is selected: the whole Needs attention queue, or a quiet prompt when it is empty. */
@Composable
fun NeedsAttentionPane(queue: List<Pair<PortfolioItem, AttentionFlag>>, onOpen: (Project) -> Unit) {
    if (queue.isEmpty()) {
        // No card: a box stretched across half the screen around one line read as something big and empty.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            modifier = Modifier.fillMaxSize().padding(32.dp)
        ) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = LocalNHAColors.current.onTrack, modifier = Modifier.size(32.dp))
            Text("Nothing needs attention", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(
                "Select a project to see its details.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        return
    }
    var showAll by remember { mutableStateOf(true) }
    Box(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        AttentionCard(queue = queue, showAll = showAll, onToggleAll = { showAll = !showAll }, onOpen = onOpen)
    }
}
