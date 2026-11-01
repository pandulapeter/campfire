/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state.songs

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * The seed the songs list's random picks are drawn with ([randomPicks]). A new one is drawn by [reshuffle], and by
 * [catchUp] once an hour has gone by since the last draw - which is only called as the songs screen is opened, so the
 * picks never change under somebody reading the list, and never on a song closed over it.
 *
 * Time is read on the wall clock rather than a monotonic one: Android's, iOS's, and macOS's monotonic clocks stop
 * while the device sleeps, and a phone put down for hours would count minutes. A clock set back reads as negative
 * time and draws nothing until it catches up; one set forward at worst draws once early.
 *
 * The seed is this device's own, never synced or written to disk, and kept only in the saved state of a process the
 * system may restore ([restored], [persist]): two devices of one synced library pick differently, and a launch is still
 * a new draw, but a process killed in the background while the user was away in a file picker comes back with the same
 * picks. The time the app stopped is kept with it, so that a restored process's first start is judged as a return
 * ([onAppStarted]) like any other.
 */
internal class RandomPicksDraw(
    private val clock: Clock,
    private val random: Random,
    restored: SavedDraw?,
    private val persist: (SavedDraw) -> Unit,
) {
    private var drawnAt = restored?.let { Instant.fromEpochMilliseconds(it.drawnAtMillis) } ?: clock.now()
    private var stoppedAt: Instant? = restored?.stoppedAtMillis?.let(Instant::fromEpochMilliseconds)
    private val _seed = MutableStateFlow(restored?.seed ?: random.nextLong())
    val seed = _seed.asStateFlow()

    init {
        save()
    }

    /** For the songs screen being opened: the app coming back to the front on it, or another tab left for it. */
    fun catchUp() {
        if (clock.now() - drawnAt >= REDRAW_INTERVAL) reshuffle()
    }

    fun onAppStopped() {
        stoppedAt = clock.now()
        save()
    }

    /**
     * Counts as the songs screen being opened only after a real stay away ([RETURN_AWAY]): on Android a start also
     * follows the system file picker or share sheet opened from the list, and an activity recreation, and neither is
     * the list opened again. The first start, with no stop before it, is the launch, which drew the picks already.
     */
    fun onAppStarted(isSongsOnTop: Boolean) {
        val stoppedAt = stoppedAt ?: return
        this.stoppedAt = null
        save()
        if (isSongsOnTop && clock.now() - stoppedAt >= RETURN_AWAY) catchUp()
    }

    fun reshuffle() {
        drawnAt = clock.now()
        _seed.value = random.nextLong()
        save()
    }

    private fun save() = persist(
        SavedDraw(seed = _seed.value, drawnAtMillis = drawnAt.toEpochMilliseconds(), stoppedAtMillis = stoppedAt?.toEpochMilliseconds()),
    )

    /** What a restored process starts from, the times on the wall clock in epoch milliseconds. */
    @Serializable
    data class SavedDraw(
        val seed: Long,
        val drawnAtMillis: Long,
        val stoppedAtMillis: Long?,
    )

    private companion object {
        val REDRAW_INTERVAL = 1.hours
        val RETURN_AWAY = 5.minutes
    }
}
