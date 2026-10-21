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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_link
import com.pandulapeter.campfire.presentation.resources.onboarding_clipboard_link_open
import com.pandulapeter.campfire.presentation.resources.onboarding_clipboard_link_question
import com.pandulapeter.campfire.presentation.ui.firstRun.FirstRunController
import org.jetbrains.compose.resources.painterResource

/**
 * The first run's offer, under the last page's answers, to open the song or setlist link the clipboard seems to hold
 * (`FirstRunController.offerClipboardLink`): what an iPhone has instead of an install referrer, the landing page having
 * copied the link before sending the user to the App Store. It leaves once taken, whatever the clipboard turns out to hold.
 */
@Composable
internal fun OnboardingClipboardLinkCard(
    modifier: Modifier = Modifier,
    isVisible: Boolean,
    onOpen: () -> Unit,
) = AnimatedVisibility(
    modifier = modifier,
    visible = isVisible,
    enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
    exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_link),
                contentDescription = null,
            )
            Text(
                modifier = Modifier.weight(1f),
                text = stringResource(Res.string.onboarding_clipboard_link_question),
                style = MaterialTheme.typography.bodyLarge,
            )
            FilledTonalButton(onClick = onOpen) {
                Text(stringResource(Res.string.onboarding_clipboard_link_open))
            }
        }
    }
}

/**
 * The last page's footer ([LocalOnboardingPageFooter]): the card, shown while [firstRunController] offers the link. One
 * lambda for the screen's life, reading the offer as it is drawn, so a page turn does not hand the pages a new one.
 */
@Composable
internal fun rememberOnboardingClipboardLinkFooter(firstRunController: FirstRunController): @Composable () -> Unit {
    val isOffered by firstRunController.isClipboardLinkOffered.collectAsStateWithLifecycle()
    return remember(firstRunController) {
        @Composable {
            OnboardingClipboardLinkCard(
                isVisible = isOffered,
                onOpen = firstRunController::openClipboardLink,
            )
        }
    }
}
