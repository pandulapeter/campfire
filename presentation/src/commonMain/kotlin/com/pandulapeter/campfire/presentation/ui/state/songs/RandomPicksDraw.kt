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

import com.pandulapeter.campfire.data.model.preferences.RandomPicksSeed
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.ui.state.SavedStateStore
import com.pandulapeter.campfire.presentation.ui.state.SavedStateStore.Companion.RANDOM_PICKS_KEY
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * The seed the songs list's random picks are drawn with ([randomPicks]), null until it is known. A new one is drawn by
 * [reshuffle], and by [catchUp] once an hour has gone by since the last draw - which is only called as the songs screen
 * is opened (the launch counting as one), so the picks never change under somebody reading the list, and never on a
 * song closed over it.
 *
 * Time is read on the wall clock rather than a monotonic one: Android's, iOS's, and macOS's monotonic clocks stop
 * while the device sleeps, and a phone put down for hours would count minutes. A clock set back reads as negative
 * time and draws nothing until it catches up; one set forward at worst draws once early.
 *
 * The seed is this device's own, never synced: two devices of one synced library pick differently. Every draw is
 * stored in the preferences ([start]), so that a launch within the hour of the last draw shows the same picks, and
 * kept in the saved state of a process the system may restore ([restored], [persist]) with the time the app stopped,
 * so that a restored process's first start is judged as a return ([onAppStarted]) like any other.
 */
internal class RandomPicksDraw(
    private val clock: Clock,
    private val random: Random,
    restored: SavedDraw?,
    private val persist: (SavedDraw) -> Unit,
) {
    private var drawnAt = restored?.let { Instant.fromEpochMilliseconds(it.drawnAtMillis) }
    private var stoppedAt: Instant? = restored?.stoppedAtMillis?.let(Instant::fromEpochMilliseconds)
    private var store: (RandomPicksSeed) -> Unit = {}
    private val _seed = MutableStateFlow(restored?.seed)
    val seed: StateFlow<Long?> = _seed.asStateFlow()

    /**
     * Stores every later draw in the preferences and, unless a restored process brought its own, takes the one the
     * last run stored once they are read: the launch opens the songs screen, so a stored draw an hour old is replaced.
     * Called once the preferences' holder exists, which is built after the navigator that calls [catchUp].
     */
    fun start(scope: CoroutineScope, userPreferences: StateFlow<UserPreferences?>, changeUserPreferences: (UserPreferences.() -> UserPreferences) -> Unit) {
        store = { drawn -> changeUserPreferences { copy(randomPicksSeed = drawn) } }
        if (_seed.value != null) return
        scope.launch {
            val stored = userPreferences.filterNotNull().first().randomPicksSeed
            // A refresh pressed before the preferences arrived is a draw of its own already.
            if (_seed.value != null) return@launch
            if (stored == null) return@launch reshuffle()
            drawnAt = Instant.fromEpochMilliseconds(stored.drawnAtMillis)
            _seed.value = stored.value
            save()
            catchUp()
        }
    }

    /** For the songs screen being opened: the app coming back to the front on it, or another tab left for it. */
    fun catchUp() {
        val drawnAt = drawnAt ?: return
        if (clock.now() - drawnAt >= REDRAW_INTERVAL) reshuffle()
    }

    fun onAppStopped() {
        stoppedAt = clock.now()
        save()
    }

    /**
     * Counts as the songs screen being opened only after a real stay away ([RETURN_AWAY]): on Android a start also
     * follows the system file picker or share sheet opened from the list, and an activity recreation, and neither is
     * the list opened again. The first start, with no stop before it, is the launch, which [start] has judged already.
     */
    fun onAppStarted(isSongsOnTop: Boolean) {
        val stoppedAt = stoppedAt ?: return
        this.stoppedAt = null
        save()
        if (isSongsOnTop && clock.now() - stoppedAt >= RETURN_AWAY) catchUp()
    }

    fun reshuffle() {
        val now = clock.now()
        val seed = random.nextLong()
        drawnAt = now
        _seed.value = seed
        save()
        store(RandomPicksSeed(value = seed, drawnAtMillis = now.toEpochMilliseconds()))
    }

    private fun save() {
        val seed = _seed.value ?: return
        val drawnAt = drawnAt ?: return
        persist(SavedDraw(seed = seed, drawnAtMillis = drawnAt.toEpochMilliseconds(), stoppedAtMillis = stoppedAt?.toEpochMilliseconds()))
    }

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

/** The draw the app runs with: the system's wall clock and randomness, kept in [savedStateStore]. */
internal fun randomPicksDraw(savedStateStore: SavedStateStore) = RandomPicksDraw(
    clock = Clock.System,
    random = Random.Default,
    restored = savedStateStore.restore<RandomPicksDraw.SavedDraw>(RANDOM_PICKS_KEY),
    persist = { savedStateStore.persist(RANDOM_PICKS_KEY, it) },
)
