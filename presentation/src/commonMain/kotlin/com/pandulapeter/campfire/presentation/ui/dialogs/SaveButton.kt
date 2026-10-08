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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.cancel
import com.pandulapeter.campfire.presentation.resources.ic_save
import com.pandulapeter.campfire.presentation.resources.print_save
import com.pandulapeter.campfire.presentation.ui.print.PdfExportProgress
import org.jetbrains.compose.resources.painterResource

/**
 * Save, which while the pages of a PDF are drawn counts them in a ring in place of its icon and cancels; once the picker
 * is up there is nothing to cancel, and a tap waits for it to answer. It leaves while there is nothing to save: no pages,
 * or a song file that could not be read.
 */
@Composable
internal fun SaveButton(
    modifier: Modifier,
    isVisible: Boolean,
    progress: PdfExportProgress?,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) = AnimatedVisibility(isVisible, modifier = modifier, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
    ExtendedFloatingActionButton(
        onClick = if (progress != null) onCancel else onSave,
        icon = {
            // Keyed by whether there is any, so that the ring fades out showing the last count rather than an empty one.
            AnimatedContent(progress, transitionSpec = { fadeIn() togetherWith fadeOut() }, contentKey = { it != null }) { shown ->
                if (shown == null) {
                    Icon(painter = painterResource(Res.drawable.ic_save), contentDescription = null)
                } else {
                    CircularProgressIndicator(
                        progress = { if (shown.total == 0) 0f else shown.done.toFloat() / shown.total },
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 3.dp,
                    )
                }
            }
        },
        text = { CrossfadedLabel(stringResource(Res.string.print_save), stringResource(Res.string.cancel), isSecondShown = progress != null) },
    )
}

/** Two labels of one button, faded between, the button keeping the size of the wider so that it does not jump. */
@Composable
private fun CrossfadedLabel(
    first: String,
    second: String,
    isSecondShown: Boolean,
) = Box(contentAlignment = Alignment.Center) {
    val secondAlpha by animateFloatAsState(if (isSecondShown) 1f else 0f)
    Text(first, Modifier.alpha(1f - secondAlpha).semantics { if (isSecondShown) hideFromAccessibility() })
    Text(second, Modifier.alpha(secondAlpha).semantics { if (!isSecondShown) hideFromAccessibility() })
}
