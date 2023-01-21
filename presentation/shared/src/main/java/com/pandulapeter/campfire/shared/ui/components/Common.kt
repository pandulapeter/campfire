package com.pandulapeter.campfire.shared.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
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
    modifier = modifier.padding(horizontal = 8.dp, vertical = 16.dp).fillMaxWidth(),
    text = text,
    style = TextStyle.Default.copy(fontWeight = FontWeight.Bold),
    color = MaterialTheme.colors.primary
)

@Composable
fun RoundedCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) = Column(
    modifier = modifier
) {
    Surface(
        modifier = Modifier.padding(horizontal = 8.dp),
        elevation = 4.dp,
        shape = RoundedCornerShape(8.dp),
        content = content
    )
    Spacer(Modifier.height(8.dp))
}