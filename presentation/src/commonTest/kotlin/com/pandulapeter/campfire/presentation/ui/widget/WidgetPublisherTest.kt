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

import com.pandulapeter.campfire.data.model.catalogue.CoverArtResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WidgetPublisherTest {

    /** A folder in memory that checks, at every write of a snapshot, that each cover it names is there. */
    private class FakeWidgetFiles(covers: Set<String> = emptySet()) : WidgetFiles {
        val covers = covers.toMutableSet()
        val snapshots = mutableListOf<WidgetSnapshot>()

        override suspend fun writeSnapshot(snapshot: WidgetSnapshot) {
            snapshot.songs.mapNotNull { it.cover }.forEach { assertTrue(it in covers, "The snapshot names a cover that is not there") }
            snapshots += snapshot
        }

        override suspend fun coverNames() = covers.toSet()

        override suspend fun writeCover(name: String, bytes: ByteArray) = covers.add(name).let { true }

        override suspend fun deleteCover(name: String) {
            assertTrue(snapshots.lastOrNull()?.songs.orEmpty().none { it.cover == name }, "A cover the written snapshot names is deleted")
            covers.remove(name)
        }
    }

    private val snapshot = WidgetSnapshot(
        songs = listOf("a", "b", "c").map { WidgetSnapshot.Song(fileName = "$it.cho", title = it, artist = "", link = "campfire://song/$it") },
        setlists = emptyList(),
        areSongsEnabled = true,
        areSetlistsEnabled = true,
        homeLink = "campfire://",
        setlistsLink = "campfire://setlists",
        strings = WidgetTestStrings.strings,
    )

    private val addresses = mapOf("a.cho" to "https://covers/a.jpg", "b.cho" to "https://covers/missing.jpg")

    private fun publisher(files: WidgetFiles, failingUrls: Set<String> = emptySet()) = WidgetPublisher(
        files = files,
        getCoverArt = { url -> if (url in failingUrls) CoverArtResult.Missing else CoverArtResult.Image(url.encodeToByteArray()) },
        makeThumbnail = { it },
    )

    private val everyWidget = PlacedWidgets(hasRandomPicks = true, hasSetlist = true)

    private fun WidgetSnapshot.coverOf(fileName: String) = songs.single { it.fileName == fileName }.cover

    @Test
    fun `the snapshot reaches the widgets first with the covers already made and again once the missing ones are`() = runTest {
        val files = FakeWidgetFiles(covers = setOf(widgetCoverName("https://covers/a.jpg")))
        publisher(files).publish(snapshot = snapshot, allCoverAddresses = addresses + ("c.cho" to "https://covers/c.jpg"), placed = everyWidget)
        assertEquals(2, files.snapshots.size)
        assertEquals(widgetCoverName("https://covers/a.jpg"), files.snapshots.first().coverOf("a.cho"))
        assertNull(files.snapshots.first().coverOf("c.cho"))
        assertEquals(widgetCoverName("https://covers/c.jpg"), files.snapshots.last().coverOf("c.cho"))
    }

    @Test
    fun `a cover that cannot be had leaves its song without one`() = runTest {
        val files = FakeWidgetFiles()
        publisher(files, failingUrls = setOf("https://covers/missing.jpg")).publish(snapshot = snapshot, allCoverAddresses = addresses, placed = everyWidget)
        val last = files.snapshots.last()
        assertEquals(widgetCoverName("https://covers/a.jpg"), last.coverOf("a.cho"))
        assertNull(last.coverOf("b.cho"))
        assertNull(last.coverOf("c.cho"))
    }

    @Test
    fun `covers no song names any more are deleted after the snapshot that stops naming them`() = runTest {
        val files = FakeWidgetFiles()
        publisher(files).publish(snapshot = snapshot, allCoverAddresses = addresses, placed = everyWidget)
        publisher(files).publish(snapshot = snapshot, allCoverAddresses = emptyMap(), placed = everyWidget)
        assertTrue(files.covers.isEmpty())
        assertTrue(files.snapshots.last().songs.all { it.cover == null })
    }

    @Test
    fun `nothing is made again and the snapshot is written once where every cover is there`() = runTest {
        val files = FakeWidgetFiles()
        publisher(files).publish(snapshot = snapshot, allCoverAddresses = addresses, placed = everyWidget)
        files.snapshots.clear()
        publisher(files).publish(snapshot = snapshot, allCoverAddresses = addresses, placed = everyWidget)
        assertEquals(1, files.snapshots.size)
    }

    @Test
    fun `with no widget placed nothing is fetched and every thumbnail is deleted`() = runTest {
        val files = FakeWidgetFiles()
        publisher(files).publish(snapshot = snapshot, allCoverAddresses = addresses, placed = everyWidget)
        val fetched = mutableListOf<String>()
        WidgetPublisher(files = files, getCoverArt = { fetched += it; CoverArtResult.Missing }, makeThumbnail = { it })
            .publish(snapshot = snapshot, allCoverAddresses = addresses + ("c.cho" to "https://covers/c.jpg"), placed = PlacedWidgets(hasRandomPicks = false, hasSetlist = false))
        assertTrue(fetched.isEmpty())
        assertTrue(files.covers.isEmpty())
        assertTrue(files.snapshots.last().songs.all { it.cover == null })
    }
}
