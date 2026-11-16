/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.spreadTheWord

import com.pandulapeter.campfire.data.model.preferences.SongOpens
import com.pandulapeter.campfire.presentation.ui.firstRun.canShowWhatsNew

/** The songs of their own someone needs before they are asked: a library of five is one being built, not tried. */
internal const val MIN_OWN_SONGS = 5

/** The song details screens opened, all songs together, that make Campfire part of how someone plays. */
internal const val MIN_SONG_OPENS = 10

/**
 * The different days those opens fall on. It asks of a new user what "installed three days ago" would (the third day
 * is two days after the first at the earliest) and more of everyone else, and needs no install date, which the app
 * does not keep.
 */
internal const val MIN_SONG_OPEN_DAYS = 3

/** Whether these opens are enough to ask, after which nothing more is counted. */
internal val SongOpens.areEnough get() = count >= MIN_SONG_OPENS && dayCount >= MIN_SONG_OPEN_DAYS

/**
 * These opens with one more on [today] (the local date in days since 1970-01-01), a new day whenever it differs from
 * the last one's: a clock moved backwards counts another day rather than taking one away, which only asks a little
 * sooner. Unchanged once [areEnough], so a song open writes nothing after that.
 */
internal fun SongOpens.counted(today: Long) = if (areEnough) {
    this
} else {
    SongOpens(count = count + 1, dayCount = if (today == lastEpochDay) dayCount else dayCount + 1, lastEpochDay = today)
}

/** The songs among [songFileNames] that are the user's own: the demo's are not, edited or not. */
internal fun ownSongCount(songFileNames: Iterable<String>, isDemoSong: (String) -> Boolean) = songFileNames.count { !isDemoSong(it) }

/** Whether this device owes the request to spread the word, see the package's `CLAUDE.md`. */
internal fun isSpreadTheWordDue(ownSongCount: Int, songOpens: SongOpens, isShown: Boolean) =
    !isShown && ownSongCount >= MIN_OWN_SONGS && songOpens.areEnough

/**
 * Whether the request may go up now: where What's new may ([canShowWhatsNew]), and with no other question still to
 * come or waiting for an answer - the onboarding, What's new for this version, or a sync run stopped before deleting
 * most of the library - so that it never stacks on one.
 */
internal fun canShowSpreadTheWord(
    hasDialog: Boolean,
    isImporting: Boolean,
    hasImportReport: Boolean,
    queuedImportCount: Int,
    isOnboardingPending: Boolean,
    isWhatsNewOwed: Boolean,
    areSyncDeletionsAwaitingAnswer: Boolean,
) = canShowWhatsNew(
    hasDialog = hasDialog,
    isImporting = isImporting,
    hasImportReport = hasImportReport,
    queuedImportCount = queuedImportCount,
) && !isOnboardingPending && !isWhatsNewOwed && !areSyncDeletionsAwaitingAnswer
