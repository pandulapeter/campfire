/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.welcome_get_started
import com.pandulapeter.campfire.presentation.resources.whats_new_title
import com.pandulapeter.campfire.presentation.resources.whats_new_message
import com.pandulapeter.campfire.presentation.CAMPFIRE_VERSION_NAME
import com.pandulapeter.campfire.presentation.ui.components.fadingVerticalEdges
import com.pandulapeter.campfire.presentation.ui.platform.bounceVerticalScroll

/**
 * Each change gets its own row so wrapped lines stay aligned with the words rather than the bullet, a bold headline
 * with its description under it (`• **Headline** Description` in the resource; a row without the bold part is all
 * description). Only the list scrolls: the title and the way back to the songbook stay visible, even with a long
 * release or larger interface text.
 */
@Composable
internal fun WhatsNewDialog(
    onDismiss: () -> Unit,
) = AlertDialog(
    modifier = Modifier.widthIn(max = 480.dp),
    onDismissRequest = onDismiss,
    title = { Text(stringResource(Res.string.whats_new_title, CAMPFIRE_VERSION_NAME)) },
    text = {
        val scrollState = rememberScrollState()
        val message = stringResource(Res.string.whats_new_message)
        val changes = remember(message) {
            message.lineSequence().map { it.trim().removePrefix("•").trim() }.filter { it.isNotBlank() }.map { change ->
                val headline = WHATS_NEW_HEADLINE.matchEntire(change)
                if (headline == null) null to change else headline.groupValues[1] to headline.groupValues[2]
            }.toList()
        }
        val bulletColor = MaterialTheme.colorScheme.primary
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .fadingVerticalEdges(scrollState)
                .bounceVerticalScroll(scrollState)
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            changes.forEach { (headline, description) ->
                Row(
                    modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Canvas(modifier = Modifier.padding(top = 10.dp).size(5.dp)) {
                        drawCircle(color = bulletColor)
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        if (headline != null) {
                            Text(
                                text = headline,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        if (description.isNotEmpty()) {
                            Text(
                                text = description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    },
    confirmButton = {
        Button(onClick = onDismiss) { Text(stringResource(Res.string.welcome_get_started)) }
    },
)

private val WHATS_NEW_HEADLINE = Regex("""^\*\*(.+?)\*\*\s*(.*)$""")
