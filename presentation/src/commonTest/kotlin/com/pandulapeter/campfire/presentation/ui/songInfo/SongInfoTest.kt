/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songInfo

import com.pandulapeter.campfire.chordpro.ChordProParser
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SongInfoTest {

    @Test
    fun `a song whose only info is attachments has none to show while they are hidden`() {
        val metadata = ChordProParser.parseMetadata("{title: A}\n{meta: attachment 3fa9c2e81b7d4f06.pdf Sheet.pdf}")
        assertTrue(metadata.hasSongInfo(areAttachmentsShown = true))
        assertFalse(metadata.hasSongInfo(areAttachmentsShown = false))
    }

    @Test
    fun `a song with a link has info to show whether attachments are shown or not`() {
        val metadata = ChordProParser.parseMetadata("{title: A}\n{meta: link https://example.com}")
        assertTrue(metadata.hasSongInfo(areAttachmentsShown = false))
    }
}
