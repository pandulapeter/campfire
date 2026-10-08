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

import com.pandulapeter.campfire.data.model.domain.UserPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A change to the preferences as they are when it runs, which is what `UpdateUserPreferencesUseCase` takes. */
internal typealias PreferencesWrite = suspend (update: (UserPreferences) -> UserPreferences) -> Unit

/**
 * A preference that changes every frame while it is being set - a pinch's text size, a step of the export screen's
 * options, a dragged slider - and is saved once it has held still: saving each value would publish the preferences to
 * every screen once a frame. Until it is saved, [pending] holds it, and the screens reading it let it win over the
 * stored value.
 *
 * A value is let go of after its write only if nothing newer arrived while it was being written, or that one would
 * never be saved. Whatever is still waiting when the owner goes is written by [flushAll], which every such preference
 * has to be passed to, or its last value is lost on quit.
 *
 * @param apply What the value changes in the preferences.
 */
internal class DebouncedPreference<T : Any>(
    private val apply: UserPreferences.(T) -> UserPreferences,
) {
    private val _pending = MutableStateFlow<T?>(null)

    /** The value set and not saved yet, or null once it is. */
    val pending: StateFlow<T?> = _pending.asStateFlow()

    fun set(value: T) {
        _pending.value = value
    }

    fun update(change: (T?) -> T) = _pending.update(change)

    /** Saves every value that has held still for [delayMillis], for as long as [scope] is alive. */
    @OptIn(FlowPreview::class)
    fun start(scope: CoroutineScope, delayMillis: Long, write: PreferencesWrite) = scope.launch {
        _pending.filterNotNull().debounce(delayMillis).collect { save(it, write) }
    }

    /** Saves the waiting value now, without waiting for it to hold still. */
    suspend fun flush(write: PreferencesWrite) {
        _pending.value?.let { save(it, write) }
    }

    private suspend fun save(value: T, write: PreferencesWrite) {
        write { it.apply(value) }
        clearIfStill(value)
    }

    private fun clearIfStill(value: T) {
        _pending.compareAndSet(value, null)
    }

    private fun snapshot() = _pending.value?.let { value -> Snapshot(this, value) }

    /** A waiting value as it was read, so that one written meanwhile is not the one let go of. */
    private class Snapshot<T : Any>(
        private val preference: DebouncedPreference<T>,
        private val value: T,
    ) {
        fun foldInto(preferences: UserPreferences) = preference.apply(preferences, value)

        fun clearIfStill() = preference.clearIfStill(value)
    }

    companion object {

        /**
         * Writes the values of [preferences] still waiting for their debounce in one read-modify-write, for an owner or
         * a process that is about to go: one publish, not one per preference. A write that fails only loses what was
         * already being lost, so it is reported rather than thrown, and leaves the values waiting.
         */
        suspend fun flushAll(vararg preferences: DebouncedPreference<*>, write: PreferencesWrite) {
            val snapshots = preferences.mapNotNull { it.snapshot() }
            if (snapshots.isEmpty()) return
            try {
                write { stored -> snapshots.fold(stored) { folded, snapshot -> snapshot.foldInto(folded) } }
                snapshots.forEach { it.clearIfStill() }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                println("Could not write the waiting preferences: ${exception.message}")
            }
        }
    }
}
