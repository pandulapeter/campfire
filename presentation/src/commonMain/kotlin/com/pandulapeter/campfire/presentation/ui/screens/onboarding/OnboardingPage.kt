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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.ui.components.fadingVerticalEdges
import com.pandulapeter.campfire.presentation.ui.platform.bounceVerticalScroll

/**
 * One step of the onboarding: a mark, a question and a line saying why it is asked, centered, and the answers under
 * them. The page is centered in the room the screen's bars leave it while it fits there, and scrolls while it does not -
 * a phone on its side, the smallest phone, or a large font - its edges fading into the bars as everywhere else. It is
 * never wider than a comfortable line of text, or than every theme color in one row, so that a tablet or a desktop
 * window shows a column in the middle rather than cards stretched across the screen.
 *
 * @param isCompact Leaves the mark out, in a window too short to give a decoration the room the answers need
 *   ([ONBOARDING_MARK_MIN_HEIGHT]).
 */
@Composable
internal fun OnboardingPage(
    modifier: Modifier = Modifier,
    icon: Painter,
    title: String,
    message: String,
    isCompact: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollState = rememberScrollState()
    BoxWithConstraints(
        modifier = modifier.fillMaxSize().fadingVerticalEdges(scrollState),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .bounceVerticalScroll(scrollState)
                .heightIn(min = maxHeight)
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Column(
                modifier = Modifier.widthIn(max = ONBOARDING_CONTENT_WIDTH).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                OnboardingPageHeader(
                    icon = icon,
                    title = title,
                    message = message,
                    isCompact = isCompact,
                )
                content()
            }
        }
    }
}

@Composable
private fun OnboardingPageHeader(
    icon: Painter,
    title: String,
    message: String,
    isCompact: Boolean,
) = Column(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(12.dp),
) {
    if (!isCompact) {
        Surface(
            modifier = Modifier.size(72.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    modifier = Modifier.size(36.dp),
                    painter = icon,
                    contentDescription = null,
                )
            }
        }
    }
    Text(
        modifier = Modifier.semantics { heading() },
        text = title,
        style = MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center,
    )
    Text(
        text = message,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}
