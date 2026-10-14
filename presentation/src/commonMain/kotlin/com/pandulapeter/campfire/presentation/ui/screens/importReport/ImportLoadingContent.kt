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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.importing.ImportProgress
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.cancel
import com.pandulapeter.campfire.presentation.ui.dialogs.ImportProgressContent
import com.pandulapeter.campfire.presentation.ui.dialogs.isCancellable

/**
 * How far an import is, centered, as the files are read and as they are written. [onCancel] is offered under it for as
 * long as the phase only reads, and fades away once anything is written: an import is never left half written.
 */
@Composable
internal fun ImportLoadingContent(
    modifier: Modifier = Modifier,
    progress: ImportProgress,
    onCancel: (() -> Unit)?,
) = Column(
    modifier = modifier.widthIn(max = PROGRESS_MAX_WIDTH).fillMaxWidth().padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(8.dp),
) {
    ImportProgressContent(
        modifier = Modifier.fillMaxWidth(),
        progress = progress,
    )
    AnimatedVisibility(
        visible = onCancel != null && progress.phase.isCancellable,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        TextButton(onClick = { onCancel?.invoke() }) { Text(stringResource(Res.string.cancel)) }
    }
}

private val PROGRESS_MAX_WIDTH = 480.dp
