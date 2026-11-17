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

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.importing.ImportProgress
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.import_progress_bytes_downloading
import com.pandulapeter.campfire.presentation.resources.import_progress_bytes_downloading_so_far
import com.pandulapeter.campfire.presentation.resources.import_progress_bytes_receiving
import com.pandulapeter.campfire.presentation.resources.import_progress_bytes_receiving_so_far
import com.pandulapeter.campfire.presentation.resources.import_progress_bytes_unpacking
import com.pandulapeter.campfire.presentation.resources.import_progress_bytes_unpacking_so_far
import com.pandulapeter.campfire.presentation.resources.import_progress_comparing
import com.pandulapeter.campfire.presentation.resources.import_progress_count
import com.pandulapeter.campfire.presentation.resources.import_progress_downloading
import com.pandulapeter.campfire.presentation.resources.import_progress_downloading_pictures
import com.pandulapeter.campfire.presentation.resources.import_progress_finishing
import com.pandulapeter.campfire.presentation.resources.import_progress_importing
import com.pandulapeter.campfire.presentation.resources.import_progress_reading
import com.pandulapeter.campfire.presentation.resources.import_progress_receiving
import com.pandulapeter.campfire.presentation.resources.import_progress_unpacking
import com.pandulapeter.campfire.presentation.resources.import_progress_wait
import com.pandulapeter.campfire.presentation.ui.components.formattedSize

/**
 * How far an import is, which the import screen shows both while the files are read and while they are written. The
 * screen's loading stage offers Cancel only for the phases that are [isCancellable].
 */
@Composable
internal fun ImportProgressContent(
    modifier: Modifier = Modifier,
    progress: ImportProgress,
) = Column(
    modifier = modifier.animateContentSize(),
    verticalArrangement = Arrangement.spacedBy(12.dp),
) {
    Crossfade(progress.phase) { phase -> Text(stringResource(phase.label)) }
    // Keyed by the phase so that the bar of a new phase starts where that phase does, rather than sliding back from
    // where the previous one ended.
    key(progress.phase) {
        val line = importProgressLineOf(progress)
        val lineFraction = line?.fraction
        if (lineFraction != null) {
            val fraction by animateFloatAsState(lineFraction)
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        if (line != null) {
            Text(
                text = line.text(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    progress.fileName?.let { fileName ->
        Text(
            text = fileName,
            maxLines = 1,
            overflow = TextOverflow.MiddleEllipsis,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    Text(
        text = stringResource(Res.string.import_progress_wait),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ImportProgressLine.text() = when (this) {
    is ImportProgressLine.Files -> stringResource(Res.string.import_progress_count, completed, total)
    is ImportProgressLine.Bytes -> if (total == null) {
        stringResource(phase.bytesSoFarLabel, formattedSize(completed))
    } else {
        stringResource(phase.bytesOfTotalLabel, formattedSize(completed), formattedSize(total))
    }
}

/** Only the phases that count bytes (`countsBytes`) reach these; any other says how far it is in the unpacking's words. */
private val ImportProgress.Phase.bytesOfTotalLabel
    get() = when (this) {
        ImportProgress.Phase.RECEIVING -> Res.string.import_progress_bytes_receiving
        ImportProgress.Phase.DOWNLOADING -> Res.string.import_progress_bytes_downloading
        else -> Res.string.import_progress_bytes_unpacking
    }

private val ImportProgress.Phase.bytesSoFarLabel
    get() = when (this) {
        ImportProgress.Phase.RECEIVING -> Res.string.import_progress_bytes_receiving_so_far
        ImportProgress.Phase.DOWNLOADING -> Res.string.import_progress_bytes_downloading_so_far
        else -> Res.string.import_progress_bytes_unpacking_so_far
    }

private val ImportProgress.Phase.label
    get() = when (this) {
        ImportProgress.Phase.RECEIVING -> Res.string.import_progress_receiving
        ImportProgress.Phase.DOWNLOADING -> Res.string.import_progress_downloading
        ImportProgress.Phase.UNPACKING -> Res.string.import_progress_unpacking
        ImportProgress.Phase.DOWNLOADING_PICTURES -> Res.string.import_progress_downloading_pictures
        ImportProgress.Phase.READING -> Res.string.import_progress_reading
        ImportProgress.Phase.COMPARING -> Res.string.import_progress_comparing
        ImportProgress.Phase.IMPORTING -> Res.string.import_progress_importing
        ImportProgress.Phase.FINISHING -> Res.string.import_progress_finishing
    }

/** The phases that only download, read and compare: the ones after them write, and an import is never left half written. */
internal val ImportProgress.Phase.isCancellable
    get() = this == ImportProgress.Phase.RECEIVING || this == ImportProgress.Phase.DOWNLOADING || this == ImportProgress.Phase.UNPACKING ||
        this == ImportProgress.Phase.DOWNLOADING_PICTURES || this == ImportProgress.Phase.READING || this == ImportProgress.Phase.COMPARING
