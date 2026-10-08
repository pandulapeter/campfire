/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.playing

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The overrides of one kind set here whose writes are waiting or still on their way back from the repository, by song
 * as it was opened. A held stepper sets one on every step and writes once it lets go - a setlist write is a sync run,
 * and a held stepper is a step every few frames - so for as long as one is here it is what the stepper, the page and
 * the click read, which is also why a value can be set absolutely: while it is pending nothing reads the store's
 * instead.
 *
 * @param stored The overrides as the preferences and the setlists hold them.
 * @param name What the overrides are, for the log line of a write that failed.
 * @param write Writes one override, null removing it, and answers whether it was written and is expected to come back
 *   from the store; one that was not is let go of and reported through [onFailed].
 */
internal class PendingOverrides<T : Any>(
    private val scope: CoroutineScope,
    private val delayMillis: Long,
    private val stored: StateFlow<SongOverrides<T>>,
    private val name: String,
    private val write: suspend (place: SongPlace, value: T?) -> Boolean,
    private val onFailed: () -> Unit,
) {
    private val pending = MutableStateFlow<Map<SongPlace, Pending<T>>>(emptyMap())

    /** The debounced writes of [pending] that have not started yet; one that has started is out of reach of a cancel. */
    private val writeJobs = mutableMapOf<SongPlace, Job>()

    /** Every song's override, the pending ones over the stored ones. */
    val effective: Flow<SongOverrides<T>> = combine(stored, pending) { stored, pending ->
        pending.entries.fold(stored) { overrides, (place, value) -> overrides.with(place, value.value) }
    }

    /**
     * Lets a pending value go once its write has landed and the store says the same, so that the value on screen never
     * steps back to the stored one for the frames in between.
     */
    fun start() = scope.launch {
        combine(stored, pending) { stored, pending ->
            pending.filter { (place, value) -> value.isWritten && stored[place] == value.value }.keys
        }.collect { settled -> if (settled.isNotEmpty()) pending.update { it - settled } }
    }

    /** Sets the override of [place] to [value], null removing it, and writes it once it has held still. */
    fun set(place: SongPlace, value: T?) {
        pending.update { it + (place to Pending(value)) }
        writeJobs.remove(place)?.cancel()
        writeJobs[place] = scope.launch {
            delay(delayMillis)
            writeJobs.remove(place)
            writeNow(place, value)
        }
    }

    /** Removes the override of [place] at once, dropping a value still waiting to be written so that it cannot land after. */
    fun reset(place: SongPlace) {
        writeJobs.remove(place)?.cancel()
        pending.update { it + (place to Pending(null)) }
        scope.launch { writeNow(place, null) }
    }

    /**
     * Takes the overrides still waiting for their debounce out of it - their jobs cancelled, which only ever catches one
     * in its delay, since a job leaves its map the moment the delay ends - and returns their writes, for an owner or a
     * process that is about to go. The writes do not go through [scope], which may be cancelled by the time they run.
     */
    fun takeWaiting(): suspend () -> Unit {
        val waiting = writeJobs.mapNotNull { (place, job) ->
            job.cancel()
            pending.value[place]?.let { place to it.value }
        }
        writeJobs.clear()
        return { waiting.forEach { (place, value) -> writeNow(place, value) } }
    }

    private suspend fun writeNow(place: SongPlace, value: T?) {
        try {
            if (write(place, value)) {
                pending.update { pending ->
                    pending[place]?.takeIf { it.value == value && !it.isWritten }?.let { pending + (place to it.copy(isWritten = true)) } ?: pending
                }
            } else {
                pending.update { it - place }
                onFailed()
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            println("The $name could not be written: ${exception.message}")
            pending.update { it - place }
            onFailed()
        }
    }

    /**
     * An override set on this device. [value] null removes the override.
     *
     * @param isWritten Whether its write has landed, after which it is let go of as soon as the store says the same.
     */
    private data class Pending<T : Any>(
        val value: T?,
        val isWritten: Boolean = false,
    )
}
