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
 * Reads the shared links mirror and journal Campfire 5.4.0 left behind (`resources/upgrade-5.4.0`, written by that
 * version's own mirror and journal), from before an entry counted the attachments its copy leaves out.
 */
class SharedLinksUpgradeFrom540Test {

    private val fixture = File(requireNotNull(javaClass.getResource("/upgrade-5.4.0")).toURI())

    @Test
    fun `the shared links mirror 5_4_0 left behind keeps its revision and every link, none leaving an attachment out`() = runTest {
        val localSource = FakeSharedLinksLocalSource(mirror = File(fixture, "preferences/shared-links.json").readText())

        val mirrored = SharedLinksMirror(localSource, testEnvironment()).load()

        assertEquals("0160a7c3d5e00000000003c4", mirrored.revision)
        assertEquals(
            listOf(
                SharedLinksEntry(
                    shareId = "Rb5XwK2nPq",
                    kind = "song",
                    fileName = "leonard_cohen-hallelujah.cho",
                    title = "Hallelujah",
                    fileId = "id:AbCdEfGhIjAAAAAAAAAAQw",
                    rlkey = "q8w7e6r5t4y3u2i1o0p9",
                    sharedAt = "2026-11-02",
                    built = SharedCopyBuild(inputs = "4e1a9c07", build = 540, hash = "9d3b77e0"),
                ),
                SharedLinksEntry(
                    shareId = "Tm8QzH4vLs",
                    kind = "setlist",
                    fileName = "wedding.setlist.json",
                    title = "Wedding",
                    fileId = "id:AbCdEfGhIjAAAAAAAAAARg",
                    rlkey = "a1s2d3f4g5h6j7k8l9z0",
                    sharedAt = "2026-11-05",
                    state = SharedLinksEntry.STATE_TOO_LARGE,
                    built = SharedCopyBuild(inputs = "b20c5f18", build = 540),
                ),
                SharedLinksEntry(
                    shareId = "Yc3NdW7kGa",
                    kind = "song",
                    fileName = "oasis-wonderwall.cho",
                    title = "Wonderwall",
                    fileId = "id:AbCdEfGhIjAAAAAAAAAASw",
                    rlkey = "z9x8c7v6b5n4m3l2k1j0",
                    sharedAt = "2026-11-07",
                    state = SharedLinksEntry.STATE_ORPHANED,
                    copyMissing = true,
                ),
            ),
            mirrored.document.links,
        )
        assertEquals(listOf(0, 0, 0), mirrored.document.links.map { it.leftOutAttachments })
    }

    @Test
    fun `the shared links journal 5_4_0 left behind keeps its pending rename and stops`() = runTest {
        val localSource = FakeSharedLinksLocalSource(journal = File(fixture, "preferences/shared-links-journal.json").readText())

        assertEquals(
            listOf(
                SharedLinksJournal.Entry.Rename(from = "wedding.setlist.json", to = "wedding_2026.setlist.json", shareId = "Tm8QzH4vLs"),
                SharedLinksJournal.Entry.Stop(shareId = "Rb5XwK2nPq"),
                SharedLinksJournal.Entry.StopItem(kind = "song", fileName = "beatles-yesterday.cho"),
            ),
            SharedLinksJournal(localSource, testEnvironment()).load(),
        )
    }
}
