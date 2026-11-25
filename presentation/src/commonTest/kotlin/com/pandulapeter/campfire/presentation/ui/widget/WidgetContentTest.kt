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

import com.pandulapeter.campfire.data.model.song.SongLanguage
import com.pandulapeter.campfire.presentation.localization.AppLocale
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.import_review_songs
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WidgetContentTest {

    private val today = LocalDate(2026, 11, 24)

    private val strings = WidgetTestStrings.strings

    private fun song(name: String, tags: List<String> = emptyList(), languages: List<String> = emptyList()) =
        WidgetSnapshot.Song(fileName = "$name.cho", title = name.uppercase(), artist = "", link = "campfire://song/$name", tags = tags, languages = languages)

    private fun setlist(name: String, date: LocalDate, isArchived: Boolean = false, songs: List<String> = emptyList()) = WidgetSnapshot.Setlist(
        fileName = "$name.setlist.json",
        title = name,
        date = date.toString(),
        formattedDate = "",
        isArchived = isArchived,
        songFileNames = songs.map { "$it.cho" },
        link = "campfire://setlist/$name",
    )

    private fun snapshot(
        songs: List<WidgetSnapshot.Song> = emptyList(),
        setlists: List<WidgetSnapshot.Setlist> = emptyList(),
        areSongsEnabled: Boolean = true,
        areSetlistsEnabled: Boolean = true,
    ) = WidgetSnapshot(
        songs = songs,
        setlists = setlists,
        areSongsEnabled = areSongsEnabled,
        areSetlistsEnabled = areSetlistsEnabled,
        homeLink = "campfire://",
        setlistsLink = "campfire://setlists",
        strings = strings,
    )

    @Test
    fun `the draw takes up to the count asked for and every song of a smaller library`() {
        val songs = listOf("a", "b", "c").map(::song)
        assertEquals(songs.toSet(), pickWidgetSongs(songs, count = 5, seed = 1).toSet())
        val drawn = pickWidgetSongs(songs, count = 2, seed = 1)
        assertEquals(2, drawn.toSet().size)
    }

    @Test
    fun `the library's order leaves the draw as it is`() {
        val songs = (1..30).map { song("s$it") }
        assertEquals(pickWidgetSongs(songs, count = 5, seed = 7), pickWidgetSongs(songs.reversed(), count = 5, seed = 7))
    }

    @Test
    fun `a song added to the library changes one pick at most`() {
        val songs = (1..30).map { song("s$it") }
        (0L..20L).forEach { seed ->
            val before = pickWidgetSongs(songs, count = 5, seed = seed)
            val after = pickWidgetSongs(songs + song("new"), count = 5, seed = seed)
            assertTrue((before - after.toSet()).size <= 1)
        }
    }

    @Test
    fun `different seeds draw different songs`() {
        val songs = (1..30).map { song("s$it") }
        assertTrue((0L..9L).map { pickWidgetSongs(songs, count = 5, seed = it).toSet() }.toSet().size > 1)
    }

    @Test
    fun `the same seed draws the same songs`() {
        val library = snapshot(songs = (1..30).map { song("s$it") })
        assertEquals(library.randomPicksContent(count = 5, seed = 7), library.randomPicksContent(count = 5, seed = 7))
    }

    @Test
    fun `random picks say so for an empty library and for the songs switched off`() {
        assertEquals(RandomPicksContent.Message("Add songs"), snapshot().randomPicksContent(count = 5, seed = 0))
        assertEquals(RandomPicksContent.Message("Songs off"), snapshot(songs = listOf(song("a")), areSongsEnabled = false).randomPicksContent(count = 5, seed = 0))
    }

    @Test
    fun `random picks draw only from the songs with a selected tag in a selected language`() {
        val library = snapshot(
            songs = listOf(song("a", tags = listOf("Folk"), languages = listOf("en")), song("b", tags = listOf("folk")), song("c", languages = listOf("en"))),
        ).copy(tags = listOf("Folk"), languages = listOf(WidgetSnapshot.Language("en", "English"), WidgetSnapshot.Language(SongLanguage.UNKNOWN, "Unknown")))
        fun drawn(filter: RandomPicksFilter) = assertIs<RandomPicksContent.Picks>(library.randomPicksContent(count = 5, seed = 0, filter = filter)).songs.map { it.title }.toSet()
        assertEquals(setOf("A", "B"), drawn(RandomPicksFilter(tags = setOf("FOLK"))))
        assertEquals(setOf("A"), drawn(RandomPicksFilter(tags = setOf("Folk"), languages = setOf("en"))))
        assertEquals(setOf("B"), drawn(RandomPicksFilter(languages = setOf(SongLanguage.UNKNOWN))))
        assertEquals(setOf("A", "B", "C"), drawn(RandomPicksFilter(tags = setOf("Gone"))))
    }

    @Test
    fun `random picks say so where the filter leaves no song`() {
        val library = snapshot(songs = listOf(song("a", languages = listOf("en")))).copy(tags = listOf("Folk"), languages = listOf(WidgetSnapshot.Language("en", "English")))
        assertEquals(RandomPicksContent.Message("No match"), library.randomPicksContent(count = 5, seed = 0, filter = RandomPicksFilter(tags = setOf("Folk"))))
    }

    @Test
    fun `random picks can be drawn again only from more songs than the widget draws`() {
        fun canDrawAgain(songCount: Int) = assertIs<RandomPicksContent.Picks>(
            snapshot(songs = (1..songCount).map { song("s$it") }).randomPicksContent(count = 3, seed = 0),
        ).canDrawAgain
        assertEquals(false, canDrawAgain(3))
        assertEquals(true, canDrawAgain(4))
    }

    @Test
    fun `a widget showing fewer picks than it draws shows the first of the same draw`() {
        val library = snapshot(songs = (1..30).map { song("s$it") })
        val drawn = assertIs<RandomPicksContent.Picks>(library.randomPicksContent(count = 10, seed = 3)).songs
        assertEquals(assertIs<RandomPicksContent.Picks>(library.randomPicksContent(count = 4, seed = 3)).songs, drawn.take(4))
    }

    @Test
    fun `the next gig is the earliest day from today on`() {
        val past = setlist("past", today.minusDays(1))
        val later = setlist("later", today.plusDays(10))
        val soon = setlist("soon", today.plusDays(3))
        assertEquals(soon, nextGig(listOf(past, later, soon), today))
        val onTheDay = setlist("today", today)
        assertEquals(onTheDay, nextGig(listOf(later, onTheDay), today))
    }

    @Test
    fun `an archived setlist is never the next gig`() =
        assertNull(nextGig(listOf(setlist("archived", today.plusDays(1), isArchived = true)), today))

    @Test
    fun `of two setlists on one day the first in the screen's order is the next gig`() {
        val first = setlist("first", today.plusDays(2))
        val second = setlist("second", today.plusDays(2))
        assertEquals(first, nextGig(listOf(first, second), today))
    }

    @Test
    fun `with no setlist ahead the widget says so and opens the setlists`() = assertEquals(
        SetlistWidgetContent.Message(text = "No upcoming setlist", link = "campfire://setlists"),
        snapshot(setlists = listOf(setlist("past", today.minusDays(1)))).setlistContent(pinnedFileName = null, todayEpochDay = today.toEpochDays()),
    )

    @Test
    fun `the next gig shows how far its day is and its songs in order`() {
        val library = snapshot(songs = listOf(song("a"), song("b")), setlists = listOf(setlist("gig", today.plusDays(3), songs = listOf("b", "a"))))
        val content = assertIs<SetlistWidgetContent.Shown>(library.setlistContent(pinnedFileName = null, todayEpochDay = today.toEpochDays()))
        assertEquals("In 3 days", content.relativeDay)
        assertEquals(listOf("B", "A"), content.songs.map { it.title })
        assertEquals(listOf(1, 2), content.songs.map { it.number })
        assertEquals("campfire://setlist/gig", content.link)
    }

    @Test
    fun `a song shows its artist and keeps its number in the setlist past one the library lacks`() {
        val library = snapshot(songs = listOf(song("a").copy(artist = "Artist")), setlists = listOf(setlist("gig", today, songs = listOf("gone", "a"))))
        val song = assertIs<SetlistWidgetContent.Shown>(library.setlistContent(pinnedFileName = null, todayEpochDay = today.toEpochDays())).songs.single()
        assertEquals(2, song.number)
        assertEquals("Artist", song.artist)
    }

    @Test
    fun `each song opens the setlist at it or at its first in a snapshot from before songs had links`() {
        val gig = setlist("gig", today, songs = listOf("a", "b"))
        val linked = gig.copy(songLinks = listOf("campfire://setlist/gig/a", "campfire://setlist/gig/b"))
        fun links(setlist: WidgetSnapshot.Setlist) = assertIs<SetlistWidgetContent.Shown>(
            snapshot(songs = listOf(song("a"), song("b")), setlists = listOf(setlist)).setlistContent(pinnedFileName = null, todayEpochDay = today.toEpochDays()),
        ).songs.map { it.link }
        assertEquals(listOf("campfire://setlist/gig/a", "campfire://setlist/gig/b"), links(linked))
        assertEquals(listOf("campfire://setlist/gig", "campfire://setlist/gig"), links(gig))
    }

    @Test
    fun `the relative day turns over with the day`() {
        val library = snapshot(setlists = listOf(setlist("gig", today.plusDays(1))))
        fun relativeDay(day: LocalDate) = (library.setlistContent(pinnedFileName = null, todayEpochDay = day.toEpochDays()) as SetlistWidgetContent.Shown).relativeDay
        assertEquals("Tomorrow", relativeDay(today))
        assertEquals("Today", relativeDay(today.plusDays(1)))
    }

    @Test
    fun `a pinned setlist is shown archived and past too`() {
        val archived = setlist("old", today.minusDays(5), isArchived = true)
        val content = assertIs<SetlistWidgetContent.Shown>(snapshot(setlists = listOf(archived)).setlistContent(archived.fileName, today.toEpochDays()))
        assertEquals("5 days ago", content.relativeDay)
    }

    @Test
    fun `a pinned setlist the library no longer holds says so and opens the setlists`() = assertEquals(
        SetlistWidgetContent.Message(text = "Gone", link = "campfire://setlists"),
        snapshot().setlistContent(pinnedFileName = "renamed.setlist.json", todayEpochDay = today.toEpochDays()),
    )

    @Test
    fun `the setlist widget says when the songs or the setlists are switched off`() {
        assertEquals("Songs off", (snapshot(areSongsEnabled = false).setlistContent(null, today.toEpochDays()) as SetlistWidgetContent.Message).text)
        assertEquals("Setlists off", (snapshot(areSetlistsEnabled = false).setlistContent(null, today.toEpochDays()) as SetlistWidgetContent.Message).text)
    }

    @Test
    fun `a counted text follows the language's rule past the counts it holds`() {
        val polish = widgetPlural(Res.plurals.import_review_songs, AppLocale.PL)
        assertEquals("22 piosenki", polish.format(22))
        assertEquals("25 piosenek", polish.format(25))
        assertEquals("1 piosenka", polish.format(1))
        assertEquals("1022 piosenki", polish.format(1022))
        assertEquals("1012 piosenek", polish.format(1012))
    }

    @Test
    fun `a snapshot reads back as written`() {
        val written = snapshot(songs = listOf(song("a")), setlists = listOf(setlist("gig", today, songs = listOf("a"))))
        assertEquals(written, WidgetSnapshot.decode(written.encode()))
        assertNull(WidgetSnapshot.decode("{"))
    }

    private fun LocalDate.plusDays(days: Int) = LocalDate.fromEpochDays(toEpochDays() + days)

    private fun LocalDate.minusDays(days: Int) = plusDays(-days)
}
