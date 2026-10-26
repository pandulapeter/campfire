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

import com.pandulapeter.campfire.presentation.ui.screens.importReport.ReportNote
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.pluralStringResource
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.import_oversized
import com.pandulapeter.campfire.presentation.resources.import_review_already_there
import com.pandulapeter.campfire.presentation.resources.import_review_left_out_count
import com.pandulapeter.campfire.presentation.resources.import_review_name_taken_counts
import com.pandulapeter.campfire.presentation.resources.import_review_skipped
import com.pandulapeter.campfire.presentation.resources.import_review_summary_counts
import com.pandulapeter.campfire.presentation.resources.import_unreadable_documents
import org.jetbrains.compose.resources.PluralStringResource

/**
 * What Import will do with the ticks as they stand, at the top of the review: how many songs and setlists it writes and
 * how the names already taken are treated, then what it leaves alone and what it cannot import. Which lines it has is
 * the review's alone: one the ticks move is there, at zero too, wherever the review has something it counts, so that a
 * box ticked under the card never moves (`ImportReviewSummary.hasNameTaken`); one they cannot move is left out at zero.
 * A count changing crossfades, and a line a joining batch brings or takes away unfolds or folds.
 */
@Composable
internal fun ImportReviewSummaryCard(
    modifier: Modifier = Modifier,
    summary: ImportReviewSummary,
) = Card(
    modifier = modifier,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    ) {
        SummaryText(stringResource(Res.string.import_review_summary_counts, summary.songCount, summary.setlistCount))
        SummaryLine(isVisible = summary.hasNameTaken) {
            SummaryText(stringResource(Res.string.import_review_name_taken_counts, summary.replacedCount, summary.numberedCount))
        }
        SummaryLine(isVisible = summary.alreadyThereCount > 0) {
            SummaryText(pluralStringResource(Res.plurals.import_review_already_there, summary.alreadyThereCount, summary.alreadyThereCount))
        }
        SummaryLine(isVisible = summary.hasEntriesToChoose) {
            SummaryText(stringResource(Res.string.import_review_left_out_count, summary.leftOutCount))
        }
        SummaryNote(Res.plurals.import_review_skipped, summary.skippedCount)
        SummaryNote(Res.plurals.import_oversized, summary.oversizedCount)
        SummaryNote(Res.plurals.import_unreadable_documents, summary.unreadableCount)
    }
}

/** A line under the first, its gap above it folding away with it so that nothing jumps once it is gone. */
@Composable
private fun SummaryLine(
    isVisible: Boolean,
    topPadding: Dp = 4.dp,
    content: @Composable () -> Unit,
) = AnimatedVisibility(
    visible = isVisible,
    enter = fadeIn() + expandVertically(),
    exit = fadeOut() + shrinkVertically(),
) {
    Box(modifier = Modifier.padding(top = topPadding)) { content() }
}

@Composable
private fun SummaryText(text: String) = AnimatedContent(
    targetState = text,
    transitionSpec = { fadeIn() togetherWith fadeOut() },
) {
    Text(text = it, style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun SummaryNote(text: PluralStringResource, count: Int) = SummaryLine(isVisible = count > 0, topPadding = 8.dp) {
    ReportNote(text = pluralStringResource(text, count, count))
}
