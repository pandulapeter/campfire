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
import kotlin.random.Random

/** What the random picks widget shows: the songs drawn, or a sentence where there is nothing to draw from. */
sealed interface RandomPicksContent {

    /** The [songs] drawn, which a widget lists in this order. */
    data class Picks(val songs: List<WidgetSnapshot.Song>) : RandomPicksContent

    /** [text] instead of songs, the whole widget opening the app. */
    data class Message(val text: String) : RandomPicksContent
}

/** What the setlist widget shows: a setlist, or a sentence saying why there is none. Either opens [link]. */
sealed interface SetlistWidgetContent {

    val link: String

    /**
     * The setlist [title], on [date] ([WidgetSnapshot.Setlist.formattedDate]), [relativeDay] from today ("In 3 days"),
     * with [songCount] ("12 songs") and its [songTitles] in order, for as many as a widget has room for.
     */
    data class Shown(
        val title: String,
        val date: String,
        val relativeDay: String,
        val songCount: String,
        val songTitles: List<String>,
        override val link: String,
    ) : SetlistWidgetContent

    /** [text] instead of a setlist. */
    data class Message(
        val text: String,
        override val link: String,
    ) : SetlistWidgetContent
}

/**
 * Up to [count] songs drawn from the library by [seed]: the same seed draws the same songs from the same library, so a
 * widget keeps its picks by keeping the seed, and draws again by taking a new one. Every song can be drawn, however few:
 * a library of three shows all three.
 */
fun WidgetSnapshot.randomPicksContent(count: Int, seed: Long): RandomPicksContent = when {
    !areSongsEnabled -> RandomPicksContent.Message(strings.songsOff)
    songs.isEmpty() -> RandomPicksContent.Message(strings.emptyLibrary)
    else -> RandomPicksContent.Picks(pickWidgetSongs(songs = songs, count = count, random = Random(seed)))
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
        songTitles = setlist.songFileNames.mapNotNull(titles::get),
        link = setlist.link,
    )
}

/** Up to [count] of [songs], drawn by [random]. */
internal fun pickWidgetSongs(songs: List<WidgetSnapshot.Song>, count: Int, random: Random) = songs.shuffled(random).take(count.coerceAtLeast(0))

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
