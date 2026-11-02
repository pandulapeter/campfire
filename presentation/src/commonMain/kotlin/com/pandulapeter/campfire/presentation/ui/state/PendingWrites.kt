/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state

import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.ui.playing.PendingOverrides
import kotlinx.coroutines.CoroutineScope

/**
 * Every value of one view model that waits for its debounce before it is written, so that none can be made that the way
 * out does not write: a debounced preference exists only through [preference], and the tempo and capo overrides register
 * themselves ([register]). [flushAll] and [takeAll] are what leaving the app writes.
 *
 * @param delayMillis How long a value has to hold still before it is written.
 * @param writePreferences The write a debounced preference saves itself with, `UpdateUserPreferencesUseCase`.
 */
internal class PendingWrites(
    private val scope: CoroutineScope,
    val delayMillis: Long,
    private val writePreferences: PreferencesWrite,
) {
    private val preferences = mutableListOf<DebouncedPreference<*>>()
    private val overrides = mutableListOf<PendingOverrides<*>>()

    /** A debounced preference, saving itself once it holds still, and flushed by [flushAll]. */
    fun <T : Any> preference(apply: UserPreferences.(T) -> UserPreferences) = DebouncedPreference(apply).also {
        it.start(scope, delayMillis, writePreferences)
        preferences += it
    }

    /** Registers the tempo or capo overrides, whose writes [flushAll] takes too. */
    fun register(overrides: PendingOverrides<*>) {
        this.overrides += overrides
    }

    /**
     * Takes the overrides' waiting writes at once, cancelling their debounces, and returns what writes everything that
     * is waiting: the preferences in one read-modify-write (`DebouncedPreference.flushAll`), then the overrides. For a
     * scope that is being cancelled, which has to take the overrides before it goes and write them on another.
     */
    fun takeAll(): suspend () -> Unit {
        val waitingOverrides = overrides.map { it.takeWaiting() }
        return {
            DebouncedPreference.flushAll(*preferences.toTypedArray(), write = writePreferences)
            waitingOverrides.forEach { it() }
        }
    }

    /** Writes everything that is waiting, see [takeAll]. */
    suspend fun flushAll() = takeAll()()
}
