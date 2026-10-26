/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails

import com.pandulapeter.campfire.presentation.ui.components.animatePlacement
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.pluralStringResource
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_duplicate
import com.pandulapeter.campfire.presentation.resources.ic_import
import com.pandulapeter.campfire.presentation.resources.ic_update
import com.pandulapeter.campfire.presentation.resources.import_conflicts_replace
import com.pandulapeter.campfire.presentation.resources.import_preview_import
import com.pandulapeter.campfire.presentation.resources.import_preview_import_as_copy
import com.pandulapeter.campfire.presentation.resources.import_preview_importing
import com.pandulapeter.campfire.presentation.resources.import_review_confirm
import com.pandulapeter.campfire.presentation.ui.screens.importReport.review.importReviewSummary
import com.pandulapeter.campfire.presentation.ui.state.importing.ImportPreviewAction
import org.jetbrains.compose.resources.painterResource
import kotlin.math.max

/**
 * The buttons of a preview's card ([ImportPreviewBar]): one for a name that is free, and for a taken one both ways at
 * once, side by side where the card has room for them, one over the other at its end where it has not, travelling from
 * one to the other as the window changes. The primary one is filled, the other outlined. While what a press started is
 * being written the primary one shows it and nothing takes a press. They leave, scaling away, where the preview has
 * nothing to import.
 *
 * @param isAddingToReview Whether a batch is being read into the review, whose Import waits for it.
 */
@Composable
internal fun ImportPreviewButtons(
    modifier: Modifier = Modifier,
    importPreview: ImportPreviewState,
    isAddingToReview: Boolean,
    onAction: (ImportPreviewAction) -> Unit,
) {
    val last = rememberLast(importPreview.actions)
    AnimatedVisibility(
        modifier = modifier,
        visible = importPreview.actions != null,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut(),
    ) {
        val actions = last ?: return@AnimatedVisibility
        val isEnabled = !importPreview.isWriting && when (actions.primary) {
            ImportPreviewAction.IMPORT_REVIEW -> !isAddingToReview && importReviewSummary(importPreview.review.plan, importPreview.review.choices).importCount > 0
            else -> true
        }
        val placement = Modifier.animatePlacement(MaterialTheme.motionScheme.defaultSpatialSpec())
        SideBySideOrStacked {
            actions.secondary?.let { action ->
                PreviewButton(
                    modifier = placement,
                    action = action,
                    importPreview = importPreview,
                    isPrimary = false,
                    isEnabled = isEnabled,
                    onClick = { onAction(action) },
                )
            }
            PreviewButton(
                modifier = placement,
                action = actions.primary,
                importPreview = importPreview,
                isPrimary = true,
                isEnabled = isEnabled,
                onClick = { onAction(actions.primary) },
            )
        }
    }
}

/** One of the buttons, described by what it does, or while it writes by that, since its label stays as it was. */
@Composable
private fun PreviewButton(
    modifier: Modifier = Modifier,
    action: ImportPreviewAction,
    importPreview: ImportPreviewState,
    isPrimary: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit,
) {
    val label = when (action) {
        ImportPreviewAction.IMPORT -> stringResource(Res.string.import_preview_import)
        ImportPreviewAction.IMPORT_AS_COPY -> stringResource(Res.string.import_preview_import_as_copy)
        ImportPreviewAction.REPLACE -> stringResource(Res.string.import_conflicts_replace)
        ImportPreviewAction.IMPORT_REVIEW -> importReviewSummary(importPreview.review.plan, importPreview.review.choices).importCount.let { count ->
            pluralStringResource(Res.plurals.import_review_confirm, count, count)
        }
    }
    val isWriting = isPrimary && importPreview.isWriting
    val writingDescription = stringResource(Res.string.import_preview_importing)
    val buttonModifier = if (isWriting) modifier.semantics { contentDescription = writingDescription } else modifier
    val content: @Composable RowScope.() -> Unit = {
        if (isWriting) {
            CircularProgressIndicator(modifier = Modifier.size(ButtonDefaults.IconSize), color = LocalContentColor.current, strokeWidth = 2.dp)
        } else {
            Icon(modifier = Modifier.size(ButtonDefaults.IconSize), painter = painterResource(action.icon), contentDescription = null)
        }
        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        Text(label, maxLines = 1)
    }
    if (isPrimary) {
        Button(
            modifier = buttonModifier,
            onClick = onClick,
            enabled = isEnabled,
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            content = content,
        )
    } else {
        OutlinedButton(
            modifier = buttonModifier,
            onClick = onClick,
            enabled = isEnabled,
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            content = content,
        )
    }
}

private val ImportPreviewAction.icon
    get() = when (this) {
        ImportPreviewAction.IMPORT, ImportPreviewAction.IMPORT_REVIEW -> Res.drawable.ic_import
        ImportPreviewAction.IMPORT_AS_COPY -> Res.drawable.ic_duplicate
        ImportPreviewAction.REPLACE -> Res.drawable.ic_update
    }

/**
 * Its children in a row, centred on each other, where the width has room for all of them, and otherwise in a column at
 * the end, the last at the bottom: what decides it is the width they take, so a window resized across it moves them,
 * and their placement animation is what makes that a journey rather than a jump.
 */
@Composable
private fun SideBySideOrStacked(content: @Composable () -> Unit) = Layout(content = content) { measurables, constraints ->
    val gap = BUTTON_GAP.roundToPx()
    val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
    val rowWidth = placeables.sumOf { it.width } + gap * (placeables.size - 1).coerceAtLeast(0)
    val isRow = rowWidth <= constraints.maxWidth
    val width = if (isRow) rowWidth else placeables.maxOf { it.width }
    val height = if (isRow) placeables.maxOf { it.height } else placeables.sumOf { it.height } + gap * (placeables.size - 1).coerceAtLeast(0)
    layout(max(width, constraints.minWidth), max(height, constraints.minHeight)) {
        var x = 0
        var y = 0
        placeables.forEach { placeable ->
            if (isRow) {
                placeable.placeRelative(x, (height - placeable.height) / 2)
                x += placeable.width + gap
            } else {
                placeable.placeRelative(width - placeable.width, y)
                y += placeable.height + gap
            }
        }
    }
}

private val BUTTON_GAP = 8.dp
