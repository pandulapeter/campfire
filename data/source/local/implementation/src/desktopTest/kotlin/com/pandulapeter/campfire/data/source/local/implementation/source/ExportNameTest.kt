/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.source

import com.pandulapeter.campfire.data.source.local.implementation.storage.file.JvmFileStorage
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The rule an exported song is named by is the import's own, so that it comes back under the name it left under:
 * named by its header, whatever its library file is called.
 */
class ExportNameTest {

    private val root: File = Files.createTempDirectory("campfire-export-name").toFile()
    private val songLocalSource = SongLocalSourceImpl(JvmFileStorage(root))

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun `a numbered sibling is named by its header`() {
        assertEquals("bar-foo.cho", songLocalSource.importFileName("foo_2", "{title: Foo}\n{artist: Bar}\n"))
    }

    @Test
    fun `a subtitle joins the title half`() {
        assertEquals("foo_live.cho", songLocalSource.importFileName("foo_2", "{title: Foo}\n{subtitle: Live}\n"))
    }
}
