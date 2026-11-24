/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.widget

import com.pandulapeter.campfire.presentation.ui.components.RelativeDay
import com.pandulapeter.campfire.presentation.ui.components.relativeDay
import kotlinx.datetime.LocalDate

/** What the random picks widget shows: the songs drawn, or a sentence where there is nothing to draw from. */
sealed interface RandomPicksContent {

    /** The [songs] drawn, which a widget lists in this order. */
    data class Picks(val songs: List<WidgetSnapshot.Song>) : RandomPicksContent

    /** [text] instead of songs, the whole widget opening the app. */
    data class Message(val text: String) : RandomPicksContent
}

/** A song of the setlist a setlist widget shows: its [title], and the app link that opens the setlist at it. */
data class SetlistWidgetSong(
    val title: String,
    val link: String,
)

/** What the setlist widget shows: a setlist, or a sentence saying why there is none. Either opens [link]. */
sealed interface SetlistWidgetContent {

    val link: String

    /**
     * The setlist [title], on [date] ([WidgetSnapshot.Setlist.formattedDate]), [relativeDay] from today ("In 3 days"),
     * with [songCount] ("12 songs") and its [songs] in order, each opening the setlist at that song.
     */
    data class Shown(
        val title: String,
        val date: String,
        val relativeDay: String,
        val songCount: String,
        val songs: List<SetlistWidgetSong>,
        override val link: String,
    ) : SetlistWidgetContent

    /** [text] instead of a setlist. */
    data class Message(
        val text: String,
        override val link: String,
    ) : SetlistWidgetContent
}

/**
 * Up to [count] songs drawn from the library by [seed]: the same seed draws the same songs from the same library, in
 * whatever order it is sorted, so a widget keeps its picks by keeping the seed, and draws again by taking a new one.
 * Every song can be drawn, however few: a library of three shows all three.
 */
fun WidgetSnapshot.randomPicksContent(count: Int, seed: Long): RandomPicksContent = when {
    !areSongsEnabled -> RandomPicksContent.Message(strings.songsOff)
    songs.isEmpty() -> RandomPicksContent.Message(strings.emptyLibrary)
    else -> RandomPicksContent.Picks(pickWidgetSongs(songs = songs, count = count, seed = seed))
}

/**
 * The setlist the widget shows: the one pinned by its file name ([pinnedFileName], archived or not), else the next gig
 * ([nextGig]) on [todayEpochDay], the day as the system's calendar has it (days since 1970-01-01).
 */
fun WidgetSnapshot.setlistContent(pinnedFileName: String?, todayEpochDay: Long): SetlistWidgetContent {
    if (!areSongsEnabled) return SetlistWidgetContent.Message(text = strings.songsOff, link = homeLink)
    if (!areSetlistsEnabled) return SetlistWidgetContent.Message(text = strings.setlistsOff, link = homeLink)
    val today = LocalDate.fromEpochDays(todayEpochDay)
    val setlist = if (pinnedFileName == null) {
        nextGig(setlists = setlists, today = today) ?: return SetlistWidgetContent.Message(text = strings.noUpcomingSetlist, link = setlistsLink)
    } else {
        setlists.firstOrNull { it.fileName == pinnedFileName } ?: return SetlistWidgetContent.Message(text = strings.setlistMissing, link = setlistsLink)
    }
    val titles = songs.associate { it.fileName to it.title }
    return SetlistWidgetContent.Shown(
        title = setlist.title,
        date = setlist.formattedDate,
        relativeDay = LocalDate.parseOrNull(setlist.date)?.let { strings.relativeDayText(date = it, today = today) }.orEmpty(),
        songCount = strings.songCount.format(setlist.songFileNames.size),
        songs = setlist.songFileNames.mapIndexedNotNull { index, fileName ->
            // A snapshot from before the songs had links of their own opens the setlist at its first.
            titles[fileName]?.let { SetlistWidgetSong(title = it, link = setlist.songLinks.getOrNull(index) ?: setlist.link) }
        },
        link = setlist.link,
    )
}

/**
 * Up to [count] of [songs]: those whose file names, mixed with [seed], rank first. A song's rank is its own name's alone,
 * so the library's order (its sorting, a rename elsewhere) leaves the picks as they are, and a song added or removed
 * changes one of them at most. The iOS widget ranks the same way (`RandomPicksWidget.swift`).
 */
internal fun pickWidgetSongs(songs: List<WidgetSnapshot.Song>, count: Int, seed: Long) =
    songs.sortedBy { drawRank(seed = seed, fileName = it.fileName) }.take(count.coerceAtLeast(0))

/** The file name's 64-bit FNV-1a hash, mixed with [seed] by SplitMix64's finalizer. */
private fun drawRank(seed: Long, fileName: String): ULong {
    var hash = 0xCBF29CE484222325uL
    fileName.encodeToByteArray().forEach { byte -> hash = (hash xor byte.toUByte().toULong()) * 0x100000001B3uL }
    var mixed = hash xor seed.toULong()
    mixed = (mixed xor (mixed shr 30)) * 0xBF58476D1CE4E5B9uL
    mixed = (mixed xor (mixed shr 27)) * 0x94D049BB133111EBuL
    return mixed xor (mixed shr 31)
}

/**
 * The next gig on [today]: the setlist not archived with the earliest day that is today or later, the first of a day's
 * in [setlists]' order, which is the setlists screen's.
 */
internal fun nextGig(setlists: List<WidgetSnapshot.Setlist>, today: LocalDate) = setlists
    .filter { !it.isArchived }
    .mapNotNull { setlist -> LocalDate.parseOrNull(setlist.date)?.takeIf { it >= today }?.let { setlist to it } }
    .minByOrNull { it.second }
    ?.first

private fun WidgetSnapshot.Strings.relativeDayText(date: LocalDate, today: LocalDate) = when (val day = relativeDay(date = date, today = today)) {
    RelativeDay.Today -> this.today
    RelativeDay.Tomorrow -> tomorrow
    RelativeDay.Yesterday -> yesterday
    is RelativeDay.InDays -> inDays.format(day.days)
    is RelativeDay.DaysAgo -> daysAgo.format(day.days)
}

private fun LocalDate.Companion.parseOrNull(text: String) = try {
    parse(text)
} catch (_: IllegalArgumentException) {
    null
}
