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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.importing.ImportConflictResolution
import com.pandulapeter.campfire.data.model.importing.ImportEntryKey
import com.pandulapeter.campfire.data.model.importing.ImportPlan
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.import_conflicts_keep_both
import com.pandulapeter.campfire.presentation.resources.import_conflicts_replace
import com.pandulapeter.campfire.presentation.resources.import_preview_already_there
import com.pandulapeter.campfire.presentation.resources.import_preview_include
import com.pandulapeter.campfire.presentation.resources.import_preview_left_out
import com.pandulapeter.campfire.presentation.resources.import_preview_name_taken
import com.pandulapeter.campfire.presentation.resources.import_preview_name_taken_choice
import com.pandulapeter.campfire.presentation.resources.import_preview_shared_update
import com.pandulapeter.campfire.presentation.resources.import_preview_title
import com.pandulapeter.campfire.presentation.ui.components.controls.segmentShape
import com.pandulapeter.campfire.presentation.ui.components.SHORT_WINDOW_HEIGHT
import com.pandulapeter.campfire.presentation.ui.state.importing.ImportPreviewAction
import com.pandulapeter.campfire.presentation.ui.state.importing.ImportPreviewPage
import com.pandulapeter.campfire.presentation.ui.state.importing.ImportPreviewPageChoice
import com.pandulapeter.campfire.presentation.ui.state.importing.importPreviewPageChoiceOf
import kotlin.math.max

/**
 * What says, at the bottom of the pages and for as long as the screen is a preview, that the song on it is not in the
 * library: a card in the secondary container colour of the app's other notices (`WarningCard`), holding the message,
 * over the review the page's own choices (the same ones its row in the review offers), and the buttons that import.
 * The buttons sit at the card's end beside the message where the message keeps a readable width, and under it, still at
 * the end, where it would not. It goes away, sliding down, once the preview of one song has been imported and the screen
 * is the song's own. In a short window ([SHORT_WINDOW_HEIGHT]) it is compact: one line and the buttons, over the review
 * with the page's choices in [ImportPreviewChoicesMenu].
 *
 * @param importPreview Null once the screen is not a preview any more; the last one is drawn while the card leaves.
 * @param isAddingToReview Whether a batch is being read into the review, whose Import waits for it.
 */
@Composable
internal fun ImportPreviewBar(
    modifier: Modifier = Modifier,
    importPreview: ImportPreviewState?,
    currentPage: Int,
    isAddingToReview: Boolean,
    onAction: (ImportPreviewAction) -> Unit,
    onChosenChange: (ImportEntryKey, Boolean) -> Unit,
    onResolutionSelected: (ImportEntryKey, ImportConflictResolution) -> Unit,
) {
    val last = rememberLast(importPreview)
    // A short window leaves the song a slot a few lines tall above the full card, which gives most of it back as one line
    // and its buttons, the page's choices going behind a button of their own.
    val isCompact = LocalWindowInfo.current.containerDpSize.height < SHORT_WINDOW_HEIGHT
    AnimatedVisibility(
        modifier = modifier,
        visible = importPreview != null,
        enter = fadeIn() + slideInVertically { it },
        exit = fadeOut() + slideOutVertically { it },
    ) {
        val shown = last ?: return@AnimatedVisibility
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            shape = MaterialTheme.shapes.large,
        ) {
            MessageBesideOrAboveButtons(
                modifier = Modifier
                    .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                message = {
                    AnimatedContent(
                        targetState = isCompact,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                    ) { compact ->
                        if (compact) {
                            CompactPreviewMessage(
                                importPreview = shown,
                                currentPage = currentPage,
                                onChosenChange = onChosenChange,
                                onResolutionSelected = onResolutionSelected,
                            )
                        } else {
                            PreviewMessage(
                                importPreview = shown,
                                currentPage = currentPage,
                                onChosenChange = onChosenChange,
                                onResolutionSelected = onResolutionSelected,
                            )
                        }
                    }
                },
                buttons = {
                    ImportPreviewButtons(
                        importPreview = shown,
                        isAddingToReview = isAddingToReview,
                        onAction = onAction,
                    )
                },
            )
        }
    }
}

@Composable
private fun PreviewMessage(
    importPreview: ImportPreviewState,
    currentPage: Int,
    onChosenChange: (ImportEntryKey, Boolean) -> Unit,
    onResolutionSelected: (ImportEntryKey, ImportConflictResolution) -> Unit,
) = Column {
    MessageTitle(isLeftOut = importPreview.pages.getOrNull(currentPage)?.let { isLeftOut(it, importPreview) } == true)
    // The page's own line and choices change with the page, as the title in the bar above does.
    AnimatedContent(
        targetState = importPreview.pages.getOrNull(currentPage),
        contentKey = { it?.index },
        transitionSpec = { fadeIn() togetherWith fadeOut() },
    ) { page ->
        if (page != null) {
            PageChoice(
                page = page,
                importPreview = importPreview,
                onChosenChange = onChosenChange,
                onResolutionSelected = onResolutionSelected,
            )
        }
    }
}

/**
 * The title, saying what Import does with the page's song: that it is not in the library yet, or, its box unticked over
 * the review, that it is left out. Both are laid out at once, the one not said invisible, so that the card keeps the
 * height of the taller one: a tick changing it never moves the box just ticked, which a line coming and going would.
 */
@Composable
private fun MessageTitle(isLeftOut: Boolean) = Box(
    // Polite, so that a screen reader says what became of the song as its tick changes.
    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
) {
    val leftOutAlpha by animateFloatAsState(
        targetValue = if (isLeftOut) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
    )
    TitleText(modifier = Modifier.graphicsLayer { alpha = 1f - leftOutAlpha }, text = stringResource(Res.string.import_preview_title), isSaid = !isLeftOut)
    TitleText(modifier = Modifier.graphicsLayer { alpha = leftOutAlpha }, text = stringResource(Res.string.import_preview_left_out), isSaid = isLeftOut)
}

@Composable
private fun TitleText(
    modifier: Modifier = Modifier,
    text: String,
    isSaid: Boolean,
) = Text(
    modifier = if (isSaid) modifier else modifier.clearAndSetSemantics {},
    text = text,
    style = MaterialTheme.typography.titleSmall,
)

/** Whether the page's song is unticked over the review, which the title says in place of its own. */
private fun isLeftOut(page: ImportPreviewPage, importPreview: ImportPreviewState) = importPreview.preview.isReviewPager &&
    when (val choice = importPreviewPageChoiceOf(page.entry, page.key, importPreview.review.choices)) {
        is ImportPreviewPageChoice.New -> !choice.isChosen
        is ImportPreviewPageChoice.NameTaken -> !choice.isChosen
        ImportPreviewPageChoice.AlreadyInLibrary -> false
    }

/**
 * The compact card's message: the page's line with, over the review, the button of its choices. It has no icon of its
 * own, the Import button beside it carrying the same one.
 */
@Composable
private fun CompactPreviewMessage(
    importPreview: ImportPreviewState,
    currentPage: Int,
    onChosenChange: (ImportEntryKey, Boolean) -> Unit,
    onResolutionSelected: (ImportEntryKey, ImportConflictResolution) -> Unit,
) = AnimatedContent(
    // The choices button changes with the page too, so that its menu is bound to the page it was opened on.
    targetState = importPreview.pages.getOrNull(currentPage),
    contentKey = { it?.index },
    transitionSpec = { fadeIn() togetherWith fadeOut() },
) { page ->
    if (page != null) {
        CompactPageChoice(
            page = page,
            importPreview = importPreview,
            onChosenChange = onChosenChange,
            onResolutionSelected = onResolutionSelected,
        )
    }
}

@Composable
private fun CompactPageChoice(
    page: ImportPreviewPage,
    importPreview: ImportPreviewState,
    onChosenChange: (ImportEntryKey, Boolean) -> Unit,
    onResolutionSelected: (ImportEntryKey, ImportConflictResolution) -> Unit,
) = Row(
    verticalAlignment = Alignment.CenterVertically,
) {
    val choice = if (importPreview.preview.isReviewPager) importPreviewPageChoiceOf(page.entry, page.key, importPreview.review.choices) else null
    Crossfade(
        // Polite, so that a screen reader says what became of the song as its tick or its choice changes.
        modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
        targetState = compactLineOf(page = page, importPreview = importPreview, choice = choice),
    ) { line ->
        Text(
            text = line,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
    when (choice) {
        is ImportPreviewPageChoice.New -> ImportPreviewChoicesMenu(
            isChosen = choice.isChosen,
            resolution = null,
            onChosenChange = { onChosenChange(page.key, it) },
            onResolutionSelected = { onResolutionSelected(page.key, it) },
        )

        is ImportPreviewPageChoice.NameTaken -> ImportPreviewChoicesMenu(
            isChosen = choice.isChosen,
            resolution = choice.resolution,
            onChosenChange = { onChosenChange(page.key, it) },
            onResolutionSelected = { onResolutionSelected(page.key, it) },
        )

        ImportPreviewPageChoice.AlreadyInLibrary, null -> Unit
    }
}

/**
 * What the compact card's line says, as the full card's message line would: for the preview of one song its taken name
 * where it has one, else the title; over the review, what the page's choice comes to, a taken name's choice named.
 */
@Composable
private fun compactLineOf(page: ImportPreviewPage, importPreview: ImportPreviewState, choice: ImportPreviewPageChoice?) = when (choice) {
    null -> stringResource(
        when {
            page.entry.status != ImportPlan.Status.CONFLICTING -> Res.string.import_preview_title
            importPreview.review.sharedLinkUpdateCount > 0 -> Res.string.import_preview_shared_update
            else -> Res.string.import_preview_name_taken
        },
    )

    ImportPreviewPageChoice.AlreadyInLibrary -> stringResource(Res.string.import_preview_already_there)
    is ImportPreviewPageChoice.New -> stringResource(if (choice.isChosen) Res.string.import_preview_title else Res.string.import_preview_left_out)
    is ImportPreviewPageChoice.NameTaken -> if (choice.isChosen) {
        stringResource(
            Res.string.import_preview_name_taken_choice,
            stringResource(if (choice.resolution == ImportConflictResolution.REPLACE) Res.string.import_conflicts_replace else Res.string.import_conflicts_keep_both),
        )
    } else {
        stringResource(Res.string.import_preview_left_out)
    }
}

/**
 * The message, and the buttons beside it at the end, centred on it, where the width left to the message is at least
 * [MIN_MESSAGE_WIDTH], else under it at the end. The buttons travel between the two places as the window changes width,
 * rather than jumping. Decided by width alone, not by the message's intrinsic width: the review's choices under it are
 * drawn by layouts that cannot be asked for one.
 */
@Composable
private fun MessageBesideOrAboveButtons(
    modifier: Modifier = Modifier,
    message: @Composable () -> Unit,
    buttons: @Composable () -> Unit,
) {
    val placement = Modifier.animatePlacement(MaterialTheme.motionScheme.defaultSpatialSpec())
    Layout(
        modifier = modifier,
        contents = listOf(message, { Box(placement) { buttons() } }),
    ) { (messageMeasurables, buttonMeasurables), constraints ->
        val gap = MESSAGE_GAP.roundToPx()
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val buttonPlaceable = buttonMeasurables.single().measure(loose)
        val hasButtons = buttonPlaceable.width > 0
        val besideWidth = constraints.maxWidth - buttonPlaceable.width - gap
        val isBeside = !hasButtons || besideWidth >= MIN_MESSAGE_WIDTH.roundToPx()
        val messagePlaceable = messageMeasurables.single().measure(loose.copy(maxWidth = if (hasButtons && isBeside) besideWidth else constraints.maxWidth))
        val width = constraints.maxWidth
        val height = when {
            isBeside -> max(messagePlaceable.height, buttonPlaceable.height)
            else -> messagePlaceable.height + gap + buttonPlaceable.height
        }
        layout(width, max(height, constraints.minHeight)) {
            if (isBeside) {
                messagePlaceable.placeRelative(0, (height - messagePlaceable.height) / 2)
                buttonPlaceable.placeRelative(width - buttonPlaceable.width, (height - buttonPlaceable.height) / 2)
            } else {
                messagePlaceable.placeRelative(0, 0)
                buttonPlaceable.placeRelative(width - buttonPlaceable.width, messagePlaceable.height + gap)
            }
        }
    }
}

/**
 * The line under the title and, over the review, the tick and the taken name's choice of the page's entry, bound to the
 * choices its row reads. The preview of one song has its choice in its buttons instead, both ways at once. Where there
 * is nothing to say it draws nothing, not even the gap under the title, which would push the title off the centre of
 * the buttons beside it. Nothing here comes or goes with the tick, which the title says instead ([MessageTitle]).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PageChoice(
    page: ImportPreviewPage,
    importPreview: ImportPreviewState,
    onChosenChange: (ImportEntryKey, Boolean) -> Unit,
    onResolutionSelected: (ImportEntryKey, ImportConflictResolution) -> Unit,
) {
    if (!importPreview.preview.isReviewPager && page.entry.status != ImportPlan.Status.CONFLICTING) return
    Column(
        modifier = Modifier.padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (!importPreview.preview.isReviewPager) {
            MessageLine(stringResource(if (importPreview.review.sharedLinkUpdateCount > 0) Res.string.import_preview_shared_update else Res.string.import_preview_name_taken))
            return@Column
        }
        when (val choice = importPreviewPageChoiceOf(page.entry, page.key, importPreview.review.choices)) {
            ImportPreviewPageChoice.AlreadyInLibrary -> MessageLine(stringResource(Res.string.import_preview_already_there))
            is ImportPreviewPageChoice.New -> IncludeToggle(isChosen = choice.isChosen, onChosenChange = { onChosenChange(page.key, it) })

            is ImportPreviewPageChoice.NameTaken -> {
                MessageLine(stringResource(Res.string.import_preview_name_taken))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    // A row too narrow for both goes onto a second line, and travels there rather than jumping.
                    val placement = Modifier.animatePlacement(MaterialTheme.motionScheme.defaultSpatialSpec())
                    IncludeToggle(modifier = placement, isChosen = choice.isChosen, onChosenChange = { onChosenChange(page.key, it) })
                    ResolutionChoice(modifier = placement, resolution = choice.resolution, onResolutionSelected = { onResolutionSelected(page.key, it) })
                }
            }
        }
    }
}

@Composable
private fun MessageLine(text: String) = Text(
    text = text,
    style = MaterialTheme.typography.bodySmall,
)

/**
 * The page's tick: whether the review's Import writes the song, as the box of its row says. The box and its label are one
 * pill as tall as the Keep both / Replace buttons beside it, pressed and rippling as a whole, reaching out into the card's
 * padding so that the box itself stays in line with the title above it.
 */
@Composable
private fun IncludeToggle(
    modifier: Modifier = Modifier,
    isChosen: Boolean,
    onChosenChange: (Boolean) -> Unit,
) = Row(
    modifier = modifier
        .offset(x = -INCLUDE_TOGGLE_PADDING)
        .heightIn(min = 40.dp)
        .clip(CircleShape)
        .toggleable(value = isChosen, role = Role.Checkbox, onValueChange = onChosenChange)
        .padding(start = INCLUDE_TOGGLE_PADDING, end = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Checkbox(checked = isChosen, onCheckedChange = null)
    Text(
        modifier = Modifier.padding(start = 8.dp),
        text = stringResource(Res.string.import_preview_include),
        style = MaterialTheme.typography.labelLarge,
    )
}

/**
 * Keep both and Replace side by side, so that which one the review's Import does is plain without opening anything. The
 * chosen one is filled with the primary colour, since the segmented buttons' own fill is the card's colour.
 */
@Composable
private fun ResolutionChoice(
    modifier: Modifier = Modifier,
    resolution: ImportConflictResolution,
    onResolutionSelected: (ImportConflictResolution) -> Unit,
) = SingleChoiceSegmentedButtonRow(modifier = modifier) {
    val options = listOf(
        ImportConflictResolution.KEEP_BOTH to stringResource(Res.string.import_conflicts_keep_both),
        ImportConflictResolution.REPLACE to stringResource(Res.string.import_conflicts_replace),
    )
    options.forEachIndexed { index, (value, label) ->
        SegmentedButton(
            selected = value == resolution,
            onClick = { onResolutionSelected(value) },
            shape = segmentShape(index = index, count = options.size),
            colors = SegmentedButtonDefaults.colors(
                activeContainerColor = MaterialTheme.colorScheme.primary,
                activeContentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            label = { Text(text = label, maxLines = 1) },
        )
    }
}

private val MESSAGE_GAP = 12.dp

/** The pill's room before the box, which the pill takes out of the card's own padding (16 dp) to keep the box aligned. */
private val INCLUDE_TOGGLE_PADDING = 12.dp

/**
 * The narrowest the message may be beside the buttons: on a 360 dp phone the lone Import still fits beside it, the
 * title taking two lines, where Import as a copy and Replace together send the buttons under it.
 */
private val MIN_MESSAGE_WIDTH = 140.dp
