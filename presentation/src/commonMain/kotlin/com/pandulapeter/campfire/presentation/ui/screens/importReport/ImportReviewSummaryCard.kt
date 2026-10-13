/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.importReport

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.pluralStringResource
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.import_oversized
import com.pandulapeter.campfire.presentation.resources.import_review_already_there
import com.pandulapeter.campfire.presentation.resources.import_review_left_out
import com.pandulapeter.campfire.presentation.resources.import_review_numbered
import com.pandulapeter.campfire.presentation.resources.import_review_replacing
import com.pandulapeter.campfire.presentation.resources.import_review_skipped
import com.pandulapeter.campfire.presentation.resources.import_review_summary_counts
import com.pandulapeter.campfire.presentation.resources.import_unreadable_documents
import org.jetbrains.compose.resources.PluralStringResource

/**
 * What Import will do with the ticks as they stand, at the top of the review: how many songs and setlists it writes and
 * how the names already taken are treated, then what it leaves alone and what it cannot import. A line that would count
 * nothing is left out, and the card grows and shrinks with them as the boxes are ticked.
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
        modifier = Modifier.fillMaxWidth().animateContentSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(Res.string.import_review_summary_counts, summary.songCount, summary.setlistCount),
            style = MaterialTheme.typography.bodyLarge,
        )
        SummaryLine(Res.plurals.import_review_replacing, summary.replacedCount)
        SummaryLine(Res.plurals.import_review_numbered, summary.numberedCount)
        SummaryLine(Res.plurals.import_review_already_there, summary.alreadyThereCount)
        SummaryLine(Res.plurals.import_review_left_out, summary.leftOutCount)
        SummaryNote(Res.plurals.import_review_skipped, summary.skippedCount)
        SummaryNote(Res.plurals.import_oversized, summary.oversizedCount)
        SummaryNote(Res.plurals.import_unreadable_documents, summary.unreadableCount)
    }
}

@Composable
private fun SummaryLine(text: PluralStringResource, count: Int) {
    if (count > 0) Text(text = pluralStringResource(text, count, count), style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun SummaryNote(text: PluralStringResource, count: Int) {
    if (count > 0) ReportNote(modifier = Modifier.padding(top = 4.dp), text = pluralStringResource(text, count, count))
}
