/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs.pageLink

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

/**
 * One numbered step of [PageLinkSheet]: its number in a circle beside what to do, and under the text the button that does
 * it, where the step has one. The number is left out for screen readers, which read the steps in their order anyway.
 */
@Composable
internal fun PageLinkStep(
    number: Int,
    text: String,
    action: (@Composable ColumnScope.() -> Unit)? = null,
) = Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    Surface(
        modifier = Modifier.size(28.dp).clearAndSetSemantics { },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = number.toString(), style = MaterialTheme.typography.labelLarge)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            modifier = Modifier.padding(top = 3.dp),
            text = text,
            style = MaterialTheme.typography.bodyLarge,
        )
        action?.invoke(this)
    }
}
