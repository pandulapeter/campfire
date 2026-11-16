/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.firstRun

import com.pandulapeter.campfire.domain.api.library.IsFirstRunUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async

/**
 * Whether this is the first launch of this installation, asked once and shared. It stops being true the moment the
 * preferences are written, which is the last thing the demo library planting does, so a second caller asking the
 * question again would be answered by whichever coroutine got there first. Two of them need it: the demo library, and
 * the sync connection a reinstalled app must not inherit (`ForgetSyncConnectionUseCase`).
 *
 * An answer that could not be read is false: neither caller may act on a guess.
 */
internal fun CoroutineScope.isFirstLaunchAsync(isFirstRun: IsFirstRunUseCase) = async {
    try {
        isFirstRun()
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        println("Could not tell whether this is a first run: ${exception.message}")
        false
    }
}
