/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs.lookup

import com.pandulapeter.campfire.presentation.ui.dialogs.forms.label
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.chordpro.ChordProDuration
import com.pandulapeter.campfire.chordpro.edit.ChordProMetadataFields.Field
import com.pandulapeter.campfire.data.model.catalogue.SongCreditsState
import com.pandulapeter.campfire.data.model.catalogue.SongDetailsCandidate
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.retry
import com.pandulapeter.campfire.presentation.resources.song_details_lookup_credits_failed
import com.pandulapeter.campfire.presentation.resources.song_details_lookup_credits_loading
import com.pandulapeter.campfire.presentation.resources.song_details_lookup_credits_none
import com.pandulapeter.campfire.presentation.resources.song_details_lookup_nothing_new
import com.pandulapeter.campfire.presentation.resources.song_details_lookup_now
import com.pandulapeter.campfire.presentation.ui.components.checklist.CheckboxListItem
import com.pandulapeter.campfire.presentation.localization.textResource

/**
 * One recording the song details lookup found: its title, then on one line its artist, album, year, and duration; the
 * catalogues are credited once, under the list, rather than on every row. Selected, it is outlined in the primary color and opens in place into its review — a box per
 * value it would fill into the form, the value the form holds now under it, and on MusicBrainz a line saying where
 * asking for the songwriters is — rather than onto a screen of its own, so that Back and the close button mean what
 * they mean on every other sheet.
 */
@Composable
internal fun SongDetailsCandidateRow(
    modifier: Modifier = Modifier,
    candidate: SongDetailsCandidate,
    isSelected: Boolean,
    rows: List<SongDetailsReviewRow>,
    tickedFields: Set<Field>,
    creditsState: SongCreditsState?,
    onClick: () -> Unit,
    onToggle: (Field) -> Unit,
    onRetryCredits: () -> Unit,
) = Surface(
    modifier = modifier
        .fillMaxWidth()
        .then(if (isSelected) Modifier.border(width = 2.dp, color = MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.medium) else Modifier),
    shape = MaterialTheme.shapes.medium,
    color = MaterialTheme.colorScheme.surfaceContainer,
) {
    // What a row being closed shows while it shrinks away, rather than an empty review: the last of it it was handed.
    var shownReview by remember { mutableStateOf(Review(rows, tickedFields, creditsState)) }
    if (isSelected) shownReview = Review(rows, tickedFields, creditsState)
    Column {
        CandidateSummary(
            modifier = Modifier.selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick),
            candidate = candidate,
        )
        AnimatedVisibility(
            visible = isSelected,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            CandidateReview(
                review = shownReview,
                onToggle = onToggle,
                onRetryCredits = onRetryCredits,
            )
        }
    }
}

private data class Review(
    val rows: List<SongDetailsReviewRow>,
    val tickedFields: Set<Field>,
    val creditsState: SongCreditsState?,
)

@Composable
private fun CandidateSummary(
    modifier: Modifier = Modifier,
    candidate: SongDetailsCandidate,
) = Column(
    modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
) {
    Text(
        text = candidate.title,
        style = MaterialTheme.typography.bodyLarge,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
    Text(
        text = listOfNotNull(
            candidate.artist.takeIf { it.isNotBlank() },
            candidate.album,
            candidate.year,
            candidate.duration?.let(ChordProDuration::format),
        ).joinToString(" · "),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun CandidateReview(
    review: Review,
    onToggle: (Field) -> Unit,
    onRetryCredits: () -> Unit,
) = Column(
    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
) {
    review.rows.forEach { row ->
        val label = stringResource(row.field.label)
        CheckboxListItem(
            title = label,
            description = if (row.current.isBlank()) row.suggested else row.suggested + "\n" + textResource(Res.string.song_details_lookup_now, row.current),
            isChecked = row.field in review.tickedFields,
            horizontalInset = 4.dp,
            onCheckedChange = { onToggle(row.field) },
        )
    }
    val isWaitingForCredits = review.creditsState == SongCreditsState.Loading || review.creditsState == SongCreditsState.Busy
    if (review.rows.isEmpty() && !isWaitingForCredits) {
        ReviewNote(text = stringResource(Res.string.song_details_lookup_nothing_new))
    }
    when (val state = review.creditsState) {
        SongCreditsState.Loading, SongCreditsState.Busy -> Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(
                text = stringResource(Res.string.song_details_lookup_credits_loading),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        is SongCreditsState.Loaded -> if (state.credits.composer == null && state.credits.lyricist == null) {
            ReviewNote(text = stringResource(Res.string.song_details_lookup_credits_none))
        }

        SongCreditsState.Failed -> Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = stringResource(Res.string.song_details_lookup_credits_failed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onRetryCredits) {
                Text(stringResource(Res.string.retry))
            }
        }

        // A catalogue that never names songwriters is not worth a line under every one of its rows.
        SongCreditsState.Unavailable, null -> Unit
    }
}

@Composable
private fun ReviewNote(
    text: String,
) = Text(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    text = text,
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
)
