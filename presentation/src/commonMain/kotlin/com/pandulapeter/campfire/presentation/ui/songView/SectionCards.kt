/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songView

import com.pandulapeter.campfire.presentation.ui.songView.model.CardKey
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/** The cards [cardKeys] name, each placed by the modifier [cardModifier] gives the card at its index. */
@Composable
internal fun SectionCards(
    cardKeys: List<CardKey>,
    cardModifier: (card: Int, cardKey: CardKey) -> Modifier,
) {
    val cardShape = MaterialTheme.shapes.large
    val cardColor = MaterialTheme.colorScheme.surfaceContainerHigh
    cardKeys.forEachIndexed { card, cardKey ->
        key(cardKey) {
            Box(
                modifier = cardModifier(card, cardKey)
                    .graphicsLayer {
                        shadowElevation = CARD_ELEVATION.toPx()
                        shape = cardShape
                        clip = false
                    }
                    .background(color = cardColor, shape = cardShape),
            )
        }
    }
}

private val CARD_ELEVATION = 1.dp
