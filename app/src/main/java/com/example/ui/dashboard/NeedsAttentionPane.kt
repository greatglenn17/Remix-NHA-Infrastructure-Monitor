package com.example.ui.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.model.Project
import com.example.util.AttentionFlag
import com.example.util.PortfolioItem

/** Right pane on two panes when no project is selected: the whole Needs attention queue. */
@Composable
fun NeedsAttentionPane(queue: List<Pair<PortfolioItem, AttentionFlag>>, onOpen: (Project) -> Unit) {
    var showAll by remember { mutableStateOf(true) }
    Box(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        AttentionCard(queue = queue, showAll = showAll, onToggleAll = { showAll = !showAll }, onOpen = onOpen)
    }
}
