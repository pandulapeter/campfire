package com.pandulapeter.campfire.shared.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun Controls(
    modifier: Modifier = Modifier,
    state: String,
    onForceRefreshPressed: () -> Unit,
    onDeleteLocalDataPressed: () -> Unit
) = Column(modifier = modifier) {
    Text(
        modifier = modifier.padding(8.dp),
        style = TextStyle.Default.copy(fontWeight = FontWeight.Bold),
        text = "State: $state"
    )
    Text(
        modifier = modifier.clickable { onForceRefreshPressed() }.padding(8.dp),
        text = "Force refresh"
    )
    Text(
        modifier = modifier.clickable { onDeleteLocalDataPressed() }.padding(8.dp),
        text = "Delete local data"
    )
}