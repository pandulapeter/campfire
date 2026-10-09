/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.tuner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.tuner_ask_again
import com.pandulapeter.campfire.presentation.resources.tuner_notice_busy
import com.pandulapeter.campfire.presentation.resources.tuner_notice_failed
import com.pandulapeter.campfire.presentation.resources.tuner_notice_no_microphone
import com.pandulapeter.campfire.presentation.resources.tuner_notice_not_asked
import com.pandulapeter.campfire.presentation.resources.tuner_notice_not_supported
import com.pandulapeter.campfire.presentation.resources.tuner_notice_refused
import com.pandulapeter.campfire.presentation.resources.tuner_notice_refused_web
import com.pandulapeter.campfire.presentation.resources.tuner_open_settings
import com.pandulapeter.campfire.presentation.resources.tuner_try_again
import com.pandulapeter.campfire.presentation.resources.tuner_use_microphone
import com.pandulapeter.campfire.presentation.ui.platform.MicrophonePermission

/**
 * What the page says in place of its display while the microphone cannot be listened to: one sentence, and at most two
 * buttons. The first time it is the only place the microphone is ever asked for; a refusal offers the system's settings
 * where the platform has a page to open, and asking again where the system would still show its question.
 *
 * @param onListen Opens the microphone, which on the desktop and the web is also what asks.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TunerNoticeCard(
    modifier: Modifier = Modifier,
    notice: TunerNotice,
    permission: MicrophonePermission,
    onListen: () -> Unit,
) = Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
    Text(
        // What stops the tuner hearing mostly arrives without a tap: a refusal in the system's prompt, a call, a
        // microphone pulled out.
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        text = stringResource(
            when (notice) {
                TunerNotice.NOT_ASKED -> Res.string.tuner_notice_not_asked
                TunerNotice.REFUSED -> if (permission.isAllowedInBrowser) Res.string.tuner_notice_refused_web else Res.string.tuner_notice_refused
                TunerNotice.NO_MICROPHONE -> Res.string.tuner_notice_no_microphone
                TunerNotice.BUSY -> Res.string.tuner_notice_busy
                TunerNotice.NOT_SUPPORTED -> Res.string.tuner_notice_not_supported
                TunerNotice.FAILED -> Res.string.tuner_notice_failed
            }
        ),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
    val ask = permission.request ?: onListen
    val buttons = when (notice) {
        TunerNotice.NOT_ASKED -> listOf(Res.string.tuner_use_microphone to ask)
        TunerNotice.REFUSED -> listOfNotNull(
            permission.openSettings?.let { Res.string.tuner_open_settings to it },
            // On the web asking again is opening the input again, which a browser that remembers the refusal answers
            // at once and one that does not asks about.
            if (permission.canAskAgain || permission.request == null) Res.string.tuner_ask_again to ask else null,
        )
        TunerNotice.NO_MICROPHONE, TunerNotice.BUSY, TunerNotice.FAILED -> listOf(Res.string.tuner_try_again to onListen)
        TunerNotice.NOT_SUPPORTED -> emptyList()
    }
    if (buttons.isNotEmpty()) {
        FlowRow(
            modifier = Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            buttons.forEachIndexed { index, (label, onClick) ->
                if (index == 0) {
                    Button(onClick = onClick) { Text(stringResource(label)) }
                } else {
                    OutlinedButton(onClick = onClick) { Text(stringResource(label)) }
                }
            }
        }
    }
}
