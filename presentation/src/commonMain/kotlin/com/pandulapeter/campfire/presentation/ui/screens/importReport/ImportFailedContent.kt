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

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.import_failed_interrupted
import com.pandulapeter.campfire.presentation.resources.import_failed_link_gone
import com.pandulapeter.campfire.presentation.resources.import_failed_link_unreachable
import com.pandulapeter.campfire.presentation.resources.import_failed_link_unreachable_untitled
import com.pandulapeter.campfire.presentation.resources.import_failed_no_space
import com.pandulapeter.campfire.presentation.resources.import_failed_no_space_import_again
import com.pandulapeter.campfire.presentation.resources.import_failed_no_space_unknown
import com.pandulapeter.campfire.presentation.resources.import_failed_no_space_unknown_import_again
import com.pandulapeter.campfire.presentation.resources.import_failed_not_found
import com.pandulapeter.campfire.presentation.resources.import_failed_too_large
import com.pandulapeter.campfire.presentation.resources.import_failed_unreachable
import com.pandulapeter.campfire.presentation.resources.import_failed_unreadable
import com.pandulapeter.campfire.presentation.resources.import_failed_wrapped_archive
import com.pandulapeter.campfire.presentation.localization.textResource
import com.pandulapeter.campfire.presentation.ui.components.formattedSize
import com.pandulapeter.campfire.presentation.ui.state.importing.ImportFailure
import com.pandulapeter.campfire.presentation.ui.state.importing.ImportReport

/**
 * Why nothing could be read, in one sentence, centered where the progress was; Try again is the floating button, or Done
 * where the batch cannot be read again ([ImportReport.Failed.canRetry]), whose sentence then says to start again.
 */
@Composable
internal fun ImportFailedContent(
    modifier: Modifier = Modifier,
    failure: ImportReport.Failed,
) = Text(
    modifier = modifier.widthIn(max = MESSAGE_MAX_WIDTH).fillMaxWidth().padding(24.dp),
    text = when (failure.reason) {
        ImportFailure.UNREACHABLE -> textResource(Res.string.import_failed_unreachable, failure.sourceName.orEmpty())
        ImportFailure.NOT_FOUND -> stringResource(Res.string.import_failed_not_found)
        ImportFailure.TOO_LARGE -> stringResource(Res.string.import_failed_too_large)
        ImportFailure.UNREADABLE -> stringResource(Res.string.import_failed_unreadable)
        ImportFailure.LINK_GONE -> stringResource(Res.string.import_failed_link_gone)
        ImportFailure.LINK_UNREACHABLE -> failure.sourceName?.let { textResource(Res.string.import_failed_link_unreachable, it) }
            ?: stringResource(Res.string.import_failed_link_unreachable_untitled)
        ImportFailure.NO_SPACE -> if (failure.canRetry) {
            failure.neededBytes?.let { stringResource(Res.string.import_failed_no_space, formattedSize(it)) }
                ?: stringResource(Res.string.import_failed_no_space_unknown)
        } else {
            failure.neededBytes?.let { stringResource(Res.string.import_failed_no_space_import_again, formattedSize(it)) }
                ?: stringResource(Res.string.import_failed_no_space_unknown_import_again)
        }
        ImportFailure.WRAPPED_ARCHIVE -> stringResource(Res.string.import_failed_wrapped_archive)
        ImportFailure.INTERRUPTED -> stringResource(Res.string.import_failed_interrupted)
    },
    style = MaterialTheme.typography.bodyLarge,
    textAlign = TextAlign.Center,
)

private val MESSAGE_MAX_WIDTH = 480.dp
