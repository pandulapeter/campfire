/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Reads and writes the value of `{duration}`, which ChordPro documents as seconds (`268`) or minutes and seconds
 * (`4:28`); hours, minutes and seconds (`1:02:03`) are taken as well, for a medley or a whole set written as one file.
 *
 * The file is the user's and anything may be written in it, so reading is lenient in the only way that keeps a sum of
 * durations honest: a value that is one of those shapes is used, and anything else (`about 4 min`, `4:5`, `0`) is
 * treated as no duration at all, never guessed at. The text itself is left in the file as it is.
 */
public object ChordProDuration {

    private val SECONDS = Regex("""(\d{1,6})""")
    private val MINUTES_SECONDS = Regex("""(\d{1,4}):([0-5]\d)""")
    private val HOURS_MINUTES_SECONDS = Regex("""(\d{1,3}):([0-5]\d):([0-5]\d)""")

    /** The duration [text] declares, or null where it is missing, zero or not one of the shapes above. */
    public fun parse(text: String?): Duration? {
        val value = text?.trim() ?: return null
        val duration = SECONDS.matchEntire(value)?.let { match ->
            match.groupValues[1].toInt().seconds
        } ?: MINUTES_SECONDS.matchEntire(value)?.let { match ->
            match.groupValues[1].toInt().minutes + match.groupValues[2].toInt().seconds
        } ?: HOURS_MINUTES_SECONDS.matchEntire(value)?.let { match ->
            match.groupValues[1].toInt().hours + match.groupValues[2].toInt().minutes + match.groupValues[3].toInt().seconds
        }
        return duration?.takeIf { it.isPositive() }
    }

    /** [duration] as `m:ss`, or `h:mm:ss` from an hour on, whole seconds only; what the app writes and shows. */
    public fun format(duration: Duration): String {
        val totalSeconds = duration.inWholeSeconds.coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = totalSeconds % 3600 / 60
        val seconds = (totalSeconds % 60).toString().padStart(2, '0')
        return if (hours > 0) "$hours:${minutes.toString().padStart(2, '0')}:$seconds" else "$minutes:$seconds"
    }
}
