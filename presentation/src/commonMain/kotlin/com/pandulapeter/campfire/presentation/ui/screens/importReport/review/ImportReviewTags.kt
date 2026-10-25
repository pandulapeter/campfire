/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.importReport.review

import com.pandulapeter.campfire.presentation.ui.songInfo.SongInfoGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_label
import com.pandulapeter.campfire.presentation.resources.import_review_tags
import com.pandulapeter.campfire.presentation.resources.song_details_tags_add
import com.pandulapeter.campfire.presentation.resources.song_details_tags_manage
import com.pandulapeter.campfire.presentation.ui.components.labels.TagFlowRow
import com.pandulapeter.campfire.presentation.ui.components.labels.TagPill
import com.pandulapeter.campfire.presentation.ui.components.labels.sortedAlphabeticallyBy
import com.pandulapeter.campfire.presentation.ui.components.labels.tagColor
import org.jetbrains.compose.resources.painterResource

/**
 * The tags the import puts on every song it writes, under the counts: a tile like a song's own Tags group
 * ([SongInfoGroup]), the whole of which opens the tag sheet, so that tagging a batch reads as tagging one song does.
 */
@Composable
internal fun ImportReviewTags(
    modifier: Modifier = Modifier,
    tags: List<String>,
    onEdit: () -> Unit,
) = SongInfoGroup(
    modifier = modifier,
    title = stringResource(Res.string.import_review_tags),
    count = tags.size,
    fontScale = 1f,
    horizontalPadding = 0.dp,
    tileShape = MaterialTheme.shapes.medium,
    isCompact = true,
    onClick = onEdit,
    onClickLabel = stringResource(Res.string.song_details_tags_manage),
    emptyLabel = stringResource(Res.string.song_details_tags_add),
    isContentShown = tags.isNotEmpty(),
) {
    val sortedTags = remember(tags) { tags.sortedAlphabeticallyBy { it } }
    TagFlowRow {
        sortedTags.forEach { tag ->
            TagPill(
                text = tag,
                leadingIcon = painterResource(Res.drawable.ic_label),
                leadingIconFill = tagColor(tag)?.color() ?: Color.Unspecified,
            )
        }
    }
}
