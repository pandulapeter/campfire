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
import com.pandulapeter.campfire.presentation.resources.import_failed_not_found
import com.pandulapeter.campfire.presentation.resources.import_failed_too_large
import com.pandulapeter.campfire.presentation.resources.import_failed_unreachable
import com.pandulapeter.campfire.presentation.resources.import_failed_unreadable
import com.pandulapeter.campfire.presentation.localization.textResource

/** Why nothing could be read, in one sentence, centered where the progress was; Try again is the floating button. */
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
    },
    style = MaterialTheme.typography.bodyLarge,
    textAlign = TextAlign.Center,
)

private val MESSAGE_MAX_WIDTH = 480.dp
