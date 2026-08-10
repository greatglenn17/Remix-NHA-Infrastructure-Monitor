package com.example.ui.dialogs
import androidx.compose.material3.*
import androidx.compose.runtime.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestDRP() {
    val state = rememberDateRangePickerState()
    DateRangePicker(state = state)
}
