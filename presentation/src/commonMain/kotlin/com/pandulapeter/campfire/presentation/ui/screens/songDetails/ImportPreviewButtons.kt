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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumExtendedFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
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
import com.pandulapeter.campfire.presentation.ui.components.SHORT_WINDOW_HEIGHT
import com.pandulapeter.campfire.presentation.ui.screens.importReport.review.importReviewSummary
import com.pandulapeter.campfire.presentation.ui.state.importing.ImportPreviewAction
import org.jetbrains.compose.resources.painterResource
import kotlin.math.max

/**
 * The floating buttons of a preview, at the bottom of the pages, centred so that they keep clear of the step buttons at
 * the end edge: one for a name that is free, and for a taken one both ways side by side where the width has room for
 * them, one over the other where it has not, travelling from one to the other as the window changes. The primary one is
 * the larger, but in a short window, which has no height to spare for it. While what a press started is being written
 * the primary one shows it and nothing takes a press. They leave, scaling away, once the preview has been imported.
 *
 * @param isAddingToReview Whether a batch is being read into the review, whose Import waits for it.
 * @param onHeightChanged The height the buttons take from the bottom of the pages, none once they are gone, which the
 *   pages leave free under their last line so that every line can still be read above them.
 */
@Composable
internal fun ImportPreviewButtons(
    modifier: Modifier = Modifier,
    importPreview: ImportPreviewState?,
    isAddingToReview: Boolean,
    onAction: (ImportPreviewAction) -> Unit,
    onHeightChanged: (Dp) -> Unit,
) {
    val density = LocalDensity.current
    val last = rememberLast(importPreview?.takeIf { it.actions != null })
    AnimatedVisibility(
        modifier = modifier.onSizeChanged { onHeightChanged(with(density) { it.height.toDp() }) },
        visible = importPreview?.actions != null,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut(),
    ) {
        val shown = last ?: return@AnimatedVisibility
        val actions = shown.actions ?: return@AnimatedVisibility
        val isShort = LocalWindowInfo.current.containerDpSize.height < SHORT_WINDOW_HEIGHT
        val isEnabled = !shown.isWriting && when (actions.primary) {
            ImportPreviewAction.IMPORT_REVIEW -> !isAddingToReview && importReviewSummary(shown.review.plan, shown.review.choices).importCount > 0
            else -> true
        }
        val placement = Modifier.animatePlacement(MaterialTheme.motionScheme.defaultSpatialSpec())
        SideBySideOrStacked {
            actions.secondary?.let { action ->
                PreviewButton(
                    modifier = placement,
                    action = action,
                    importPreview = shown,
                    isPrimary = false,
                    isLarge = false,
                    isEnabled = isEnabled,
                    onClick = { onAction(action) },
                )
            }
            PreviewButton(
                modifier = placement,
                action = actions.primary,
                importPreview = shown,
                isPrimary = true,
                isLarge = !isShort,
                isEnabled = isEnabled,
                onClick = { onAction(actions.primary) },
            )
        }
    }
}

/**
 * One of the buttons. A floating button has no disabled state of its own, so it is drawn and announced as one and
 * ignores presses, as the import screen's Import is; each is described by its own label, since Material's extended
 * floating button clears the semantics of the text it draws.
 */
@Composable
private fun PreviewButton(
    modifier: Modifier = Modifier,
    action: ImportPreviewAction,
    importPreview: ImportPreviewState,
    isPrimary: Boolean,
    isLarge: Boolean,
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
    val description = if (isWriting) stringResource(Res.string.import_preview_importing) else label
    val enabledContainer = if (isPrimary) FloatingActionButtonDefaults.containerColor else MaterialTheme.colorScheme.secondaryContainer
    val enabledContent = if (isPrimary) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
    val containerColor by animateColorAsState(if (isEnabled || isWriting) enabledContainer else MaterialTheme.colorScheme.surfaceContainerHighest)
    val contentColor by animateColorAsState(if (isEnabled || isWriting) enabledContent else MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_CONTENT_ALPHA))
    val buttonModifier = modifier.semantics {
        contentDescription = description
        if (!isEnabled) disabled()
    }
    val icon: @Composable () -> Unit = {
        if (isWriting) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = LocalContentColor.current, strokeWidth = 3.dp)
        } else {
            Icon(painter = painterResource(action.icon), contentDescription = null)
        }
    }
    val text: @Composable () -> Unit = { Text(label, maxLines = 1) }
    if (isLarge) {
        MediumExtendedFloatingActionButton(
            modifier = buttonModifier,
            text = text,
            icon = icon,
            onClick = { if (isEnabled) onClick() },
            containerColor = containerColor,
            contentColor = contentColor,
        )
    } else {
        ExtendedFloatingActionButton(
            modifier = buttonModifier,
            text = text,
            icon = icon,
            onClick = { if (isEnabled) onClick() },
            containerColor = containerColor,
            contentColor = contentColor,
            elevation = if (isPrimary) FloatingActionButtonDefaults.elevation() else FloatingActionButtonDefaults.loweredElevation(),
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
 * Its children in a row, bottoms aligned and centred, where the width has room for all of them, and otherwise in a
 * column, centred, the last at the bottom: what decides it is the width they take, so a window resized across it moves
 * them, and their placement animation is what makes that a journey rather than a jump.
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
                placeable.place(x, height - placeable.height)
                x += placeable.width + gap
            } else {
                placeable.place((width - placeable.width) / 2, y)
                y += placeable.height + gap
            }
        }
    }
}

private val BUTTON_GAP = 12.dp
private const val DISABLED_CONTENT_ALPHA = 0.38f
