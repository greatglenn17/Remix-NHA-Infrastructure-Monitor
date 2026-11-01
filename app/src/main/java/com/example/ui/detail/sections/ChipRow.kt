package com.example.ui.detail.sections

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * Views inside a section, on one line that scrolls sideways: wrapped onto two lines they read as filters.
 * The next chip peeks at the edge, and the open view is scrolled into sight.
 */
@Composable
fun ChipRow(chips: List<String>, selected: String, onSelect: (String) -> Unit) {
    val state = rememberLazyListState()
    LaunchedEffect(selected) {
        val i = chips.indexOf(selected).takeIf { it >= 0 } ?: return@LaunchedEffect
        val info = state.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == i }
        if (item == null || item.offset < 0 || item.offset + item.size > info.viewportEndOffset - info.afterContentPadding) {
            state.animateScrollToItem(i)
        }
    }
    LazyRow(
        state = state,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        items(chips) { chip ->
            FilterChip(
                selected = chip == selected,
                onClick = { onSelect(chip) },
                label = { Text(chip) },
                leadingIcon = if (chip == selected) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                } else null,
                modifier = Modifier.testTag("section_chip_$chip")
            )
        }
    }
}
