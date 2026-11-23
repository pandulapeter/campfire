/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.platform

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Hands a shell the words an import's background notification is written in: the twin of [SyncNotifier]. The import's
 * work belongs to the app (`ImportWork`), and each shell watches its state itself, since the composition stops with the
 * scene on iOS and goes with the activity on Android while the work carries on; what only the composition knows is the
 * language chosen in the app, so it hands over the words, already translated, whenever they change.
 *
 * Android keeps the process alive with a foreground service while an import works, iOS with a continued processing task
 * or background time; the desktop asks before quitting instead, and the web warns before the tab closes, neither of
 * which needs these words.
 */
fun interface ImportNotifier {

    fun onImportNotificationChanged(notification: ImportNotification)
}

/**
 * The words of an import's notification, each sentence that carries a number taken apart the way
 * [SyncNotification.progressBodyFormat] is: `{completed}` and `{total}` stand for sizes, `{size}` for a size's number,
 * filled in by the shell long after the composition that resolved them may be gone (`ImportNotificationText.kt`).
 *
 * @param writing What to say while the review's choice is written, which is never cancelled and counts no bytes.
 * @param downloadingPictures What to say while the pictures an import's songs name by address are downloaded, which,
 *   like [reading] and [comparing], is part of the unpacking's work and named as the import screen names it.
 * @param finishing What to say once the write's files are in, as the import screen does.
 * @param readingPage What to say while a page shared by its address is read, which is the download's work.
 * @param sizeUnits A size's number in bytes, kilobytes, megabytes, and gigabytes, in that order, as the screen writes them.
 * @param decimalSeparator What goes between a size's whole number and its tenths, in the app's language.
 * @param cancelLabel The label of the action that cancels an import still being read, where the platform can offer one.
 */
data class ImportNotification(
    val channelName: String,
    val title: String,
    val receiving: Phase,
    val downloading: Phase,
    val unpacking: Phase,
    val writing: String,
    val downloadingPictures: String,
    val reading: String,
    val comparing: String,
    val finishing: String,
    val readingPage: String,
    val sizeUnits: List<String>,
    val decimalSeparator: String,
    val cancelLabel: String,
) {
    /**
     * One phase that counts bytes.
     *
     * @param line What to say before there is anything to count, or where the phase reports no bytes.
     * @param bytesOfTotal The sentence for `{completed}` of `{total}`.
     * @param bytesSoFar The sentence for `{completed}` alone, where the total is unknown (a download without a length).
     */
    data class Phase(
        val line: String,
        val bytesOfTotal: String,
        val bytesSoFar: String,
    )

    /**
     * Every word, in a fixed order, for a shell that has to send them somewhere only strings go (an Android intent).
     * [fromWords] reads them back.
     */
    fun toWords(): List<String> = listOf(channelName, title) +
        listOf(receiving, downloading, unpacking).flatMap { listOf(it.line, it.bytesOfTotal, it.bytesSoFar) } +
        listOf(writing, decimalSeparator, cancelLabel, downloadingPictures, reading, comparing, finishing, readingPage) + sizeUnits

    companion object {

        /** What [toWords] wrote, or null for anything else (an intent from a version that wrote fewer words). */
        fun fromWords(words: List<String>): ImportNotification? {
            if (words.size != WORD_COUNT) return null
            fun phaseAt(index: Int) = Phase(line = words[index], bytesOfTotal = words[index + 1], bytesSoFar = words[index + 2])
            return ImportNotification(
                channelName = words[0],
                title = words[1],
                receiving = phaseAt(2),
                downloading = phaseAt(5),
                unpacking = phaseAt(8),
                writing = words[11],
                decimalSeparator = words[12],
                cancelLabel = words[13],
                downloadingPictures = words[14],
                reading = words[15],
                comparing = words[16],
                finishing = words[17],
                readingPage = words[18],
                sizeUnits = words.subList(19, WORD_COUNT),
            )
        }

        /** Nineteen words before the four size units. */
        private const val WORD_COUNT = 23
    }
}

/** No-op by default, which is exactly right for the desktop and the web. */
val LocalImportNotifier = staticCompositionLocalOf { ImportNotifier { } }
