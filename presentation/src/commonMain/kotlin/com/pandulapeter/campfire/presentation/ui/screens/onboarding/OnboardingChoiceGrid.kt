/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The cards of an onboarding page in rows of [columns], every card of a row as tall as the tallest one, so that a label
 * that wraps in one language does not leave its neighbours short. A row that is not full keeps its empty places rather
 * than stretching its cards, so that every card of a page is one width.
 *
 * @param item Draws the card at an index, with the modifier that sizes it into its place.
 */
@Composable
internal fun OnboardingChoiceGrid(
    modifier: Modifier = Modifier,
    columns: Int,
    count: Int,
    item: @Composable (index: Int, modifier: Modifier) -> Unit,
) = Column(
    modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
    verticalArrangement = Arrangement.spacedBy(CHOICE_GAP),
) {
    (0 until count).chunked(columns).forEach { row ->
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(CHOICE_GAP),
        ) {
            row.forEach { index -> item(index, Modifier.weight(1f).fillMaxHeight()) }
            repeat(columns - row.size) { Spacer(modifier = Modifier.weight(1f)) }
        }
    }
}

private val CHOICE_GAP = 12.dp
