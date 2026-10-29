/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.sync.implementation.shared

import com.pandulapeter.campfire.data.sync.implementation.testEnvironment
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Reads the shared links mirror and journal Campfire 5.2.0 left behind (`resources/upgrade-5.2.0`, written by that
 * version's own mirror and journal), so that an upgrade that forgets a share or a pending rename or stop fails here.
 */
class SharedLinksUpgradeFrom520Test {

    private val fixture = File(requireNotNull(javaClass.getResource("/upgrade-5.2.0")).toURI())

    @Test
    fun `the shared links mirror 5_2_0 left behind keeps its revision and every link`() = runTest {
        val localSource = FakeSharedLinksLocalSource(mirror = File(fixture, "preferences/shared-links.json").readText())

        val mirrored = SharedLinksMirror(localSource, testEnvironment()).load()

        assertEquals("015f3a9c2e40000000002b1", mirrored.revision)
        assertEquals(
            listOf(
                SharedLinksEntry(
                    shareId = "k3J9xQ2mVt",
                    kind = "song",
                    fileName = "leonard_cohen-hallelujah.cho",
                    title = "Hallelujah",
                    fileId = "id:AbCdEfGhIjAAAAAAAAAAQw",
                    rlkey = "q8w7e6r5t4y3u2i1o0p9",
                    sharedAt = "2026-10-02",
                    built = SharedCopyBuild(inputs = "9f2c41d7", build = 412, hash = "5be0c1a8"),
                ),
                SharedLinksEntry(
                    shareId = "Zp4LwN8rYc",
                    kind = "setlist",
                    fileName = "wedding.setlist.json",
                    title = "Wedding",
                    fileId = "id:AbCdEfGhIjAAAAAAAAAARg",
                    rlkey = "a1s2d3f4g5h6j7k8l9z0",
                    sharedAt = "2026-10-05",
                    built = SharedCopyBuild(inputs = "77ab03e1", build = 412, hash = "c0ffee12"),
                ),
            ),
            mirrored.document.links,
        )
    }

    @Test
    fun `the shared links journal 5_2_0 left behind keeps its pending rename and stop`() = runTest {
        val localSource = FakeSharedLinksLocalSource(journal = File(fixture, "preferences/shared-links-journal.json").readText())

        assertEquals(
            listOf(
                SharedLinksJournal.Entry.Rename(from = "summer_camp.setlist.json", to = "summer_camp_2024.setlist.json", shareId = "Hq7TmB3xKd"),
                SharedLinksJournal.Entry.StopItem(kind = "song", fileName = "beatles-yesterday.cho"),
            ),
            SharedLinksJournal(localSource, testEnvironment()).load(),
        )
    }
}
