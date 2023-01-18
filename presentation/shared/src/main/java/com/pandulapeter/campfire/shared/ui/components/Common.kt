package com.pandulapeter.campfire.shared.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun Header(
    modifier: Modifier = Modifier,
    text: String
) = Text(
    modifier = modifier.padding(8.dp).fillMaxWidth(),
    text = text,
    style = TextStyle.Default.copy(fontWeight = FontWeight.Bold)
)