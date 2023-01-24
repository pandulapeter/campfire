package com.pandulapeter.campfire.shared.ui.screenComponents.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.shared.ui.catalogue.components.RoundedCard
import com.pandulapeter.campfire.shared.ui.catalogue.resources.CampfireStrings


@Composable
fun SettingsPlaceholder(
    modifier: Modifier = Modifier,
    uiStrings: CampfireStrings
) = RoundedCard(
    modifier = modifier.padding(8.dp)
) {
    Text(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        textAlign = TextAlign.Center,
        text = uiStrings.settings
    )
}