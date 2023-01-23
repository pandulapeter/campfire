package com.pandulapeter.campfire.shared.ui.catalogue.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun RoundedCard(
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