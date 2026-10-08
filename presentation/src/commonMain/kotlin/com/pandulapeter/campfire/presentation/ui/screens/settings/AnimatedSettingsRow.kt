/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * A row of a [SettingsSection] that is only there while there is a [value] to draw it from, expanding into the section
 * and shrinking out of it so that the rows and the section under it move rather than jump.
 *
 * The last value is kept after it has gone, since the row still has to be drawn while it shrinks away and what it
 * showed - an account, a progress - is exactly what is no longer there. A row whose value is there from the start is
 * composed at its full height, with nothing animating: only a change is narrated, never an arrival.
 */
@Composable
internal fun <T : Any> ColumnScope.AnimatedSettingsRow(
    value: T?,
    content: @Composable (T) -> Unit,
) {
    val lastValue = remember { LastValue(value) }
    value?.let { lastValue.value = it }
    AnimatedVisibility(
        visible = value != null,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        lastValue.value?.let { content(it) }
    }
}

/** The [AnimatedSettingsRow] of a row that needs nothing but to know whether it is there. */
@Composable
internal fun ColumnScope.AnimatedSettingsRow(
    isVisible: Boolean,
    content: @Composable () -> Unit,
) = AnimatedSettingsRow(value = Unit.takeIf { isVisible }) { content() }

/** What an [AnimatedSettingsRow] draws while it leaves. Not a state, since nothing is ever redrawn because of it. */
private class LastValue<T>(var value: T?)
