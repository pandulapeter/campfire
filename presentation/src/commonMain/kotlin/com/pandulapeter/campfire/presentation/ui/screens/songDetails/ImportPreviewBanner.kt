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
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.importing.ImportConflictResolution
import com.pandulapeter.campfire.data.model.importing.ImportEntryKey
import com.pandulapeter.campfire.data.model.importing.ImportPlan
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_import
import com.pandulapeter.campfire.presentation.resources.import_conflicts_keep_both
import com.pandulapeter.campfire.presentation.resources.import_conflicts_replace
import com.pandulapeter.campfire.presentation.resources.import_preview_already_there
import com.pandulapeter.campfire.presentation.resources.import_preview_include
import com.pandulapeter.campfire.presentation.resources.import_preview_left_out
import com.pandulapeter.campfire.presentation.resources.import_preview_name_taken
import com.pandulapeter.campfire.presentation.resources.import_preview_shared_update
import com.pandulapeter.campfire.presentation.resources.import_preview_title
import com.pandulapeter.campfire.presentation.ui.state.importing.ImportPreviewPage
import com.pandulapeter.campfire.presentation.ui.state.importing.ImportPreviewPageChoice
import com.pandulapeter.campfire.presentation.ui.state.importing.importPreviewPageChoiceOf
import org.jetbrains.compose.resources.painterResource

/**
 * What says, under the app bar and for as long as the screen is a preview, that the song on it is not in the library:
 * a card in the tertiary container colour, which nothing else on the screen is drawn in, and in a preview over the
 * review the page's own choices, the same ones its row in the review offers. It goes away, shrinking, once the preview
 * of one song has been imported and the screen is the song's own.
 *
 * @param importPreview Null once the screen is not a preview any more; the last one is drawn while the card leaves.
 */
@Composable
internal fun ImportPreviewBanner(
    importPreview: ImportPreviewState?,
    currentPage: Int,
    contentPadding: PaddingValues,
    onChosenChange: (ImportEntryKey, Boolean) -> Unit,
    onResolutionSelected: (ImportEntryKey, ImportConflictResolution) -> Unit,
) {
    val last = rememberLast(importPreview)
    AnimatedVisibility(
        visible = importPreview != null,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        val shown = last ?: return@AnimatedVisibility
        val layoutDirection = LocalLayoutDirection.current
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = contentPadding.calculateStartPadding(layoutDirection) + BANNER_MARGIN,
                    end = contentPadding.calculateEndPadding(layoutDirection) + BANNER_MARGIN,
                    bottom = BANNER_GAP,
                ),
            color = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            shape = MaterialTheme.shapes.large,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                BannerTitle()
                // The page's own line and choices change with the page, as the title in the bar above it does.
                AnimatedContent(
                    targetState = shown.pages.getOrNull(currentPage),
                    contentKey = { it?.index },
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                ) { page ->
                    if (page != null) {
                        PageChoice(
                            page = page,
                            importPreview = shown,
                            onChosenChange = onChosenChange,
                            onResolutionSelected = onResolutionSelected,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BannerTitle() = Row(
    verticalAlignment = Alignment.CenterVertically,
) {
    Icon(
        modifier = Modifier.padding(end = 8.dp).size(20.dp),
        painter = painterResource(Res.drawable.ic_import),
        contentDescription = null,
    )
    Text(
        text = stringResource(Res.string.import_preview_title),
        style = MaterialTheme.typography.titleSmall,
    )
}

/**
 * The line under the title and, over the review, the tick and the taken name's choice of the page's entry, bound to the
 * choices its row reads. The preview of one song has its choice in its buttons instead, both ways at once.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PageChoice(
    page: ImportPreviewPage,
    importPreview: ImportPreviewState,
    onChosenChange: (ImportEntryKey, Boolean) -> Unit,
    onResolutionSelected: (ImportEntryKey, ImportConflictResolution) -> Unit,
) = Column(
    // Polite, so that a screen reader says what became of the song as its tick or its choice changes.
    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    verticalArrangement = Arrangement.spacedBy(4.dp),
) {
    if (!importPreview.preview.isReviewPager) {
        if (page.entry.status == ImportPlan.Status.CONFLICTING) {
            BannerLine(stringResource(if (importPreview.review.sharedLinkUpdateCount > 0) Res.string.import_preview_shared_update else Res.string.import_preview_name_taken))
        }
        return@Column
    }
    when (val choice = importPreviewPageChoiceOf(page.entry, page.key, importPreview.review.choices)) {
        ImportPreviewPageChoice.AlreadyInLibrary -> BannerLine(stringResource(Res.string.import_preview_already_there))
        is ImportPreviewPageChoice.New -> {
            if (!choice.isChosen) BannerLine(stringResource(Res.string.import_preview_left_out))
            IncludeToggle(isChosen = choice.isChosen, onChosenChange = { onChosenChange(page.key, it) })
        }

        is ImportPreviewPageChoice.NameTaken -> {
            BannerLine(stringResource(if (choice.isChosen) Res.string.import_preview_name_taken else Res.string.import_preview_left_out))
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

@Composable
private fun BannerLine(text: String) = Text(
    text = text,
    style = MaterialTheme.typography.bodySmall,
)

/** The page's tick: whether the review's Import writes the song, as the box of its row says. */
@Composable
private fun IncludeToggle(
    modifier: Modifier = Modifier,
    isChosen: Boolean,
    onChosenChange: (Boolean) -> Unit,
) = Row(
    modifier = modifier.toggleable(value = isChosen, role = Role.Checkbox, onValueChange = onChosenChange),
    verticalAlignment = Alignment.CenterVertically,
) {
    Checkbox(checked = isChosen, onCheckedChange = null)
    Text(
        modifier = Modifier.padding(start = 8.dp, end = 4.dp),
        text = stringResource(Res.string.import_preview_include),
        style = MaterialTheme.typography.labelLarge,
    )
}

/** Keep both and Replace side by side, so that which one the review's Import does is plain without opening anything. */
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
            shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            label = { Text(text = label, maxLines = 1) },
        )
    }
}

private val BANNER_MARGIN = 16.dp
private val BANNER_GAP = 8.dp
