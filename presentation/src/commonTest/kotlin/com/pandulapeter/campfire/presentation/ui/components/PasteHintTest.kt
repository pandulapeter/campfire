/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components

import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.add_songs_tip_paste_hint
import com.pandulapeter.campfire.presentation.resources.add_songs_tip_paste_hint_with_links
import com.pandulapeter.campfire.presentation.resources.add_songs_tip_paste_hint_with_links_without_sharing
import kotlin.test.Test
import kotlin.test.assertEquals

class PasteHintTest {

    @Test
    fun `a platform that reads no links never mentions them`() {
        assertEquals(Res.string.add_songs_tip_paste_hint, pasteHint(canReadLinks = false, canReceiveShares = true))
        assertEquals(Res.string.add_songs_tip_paste_hint, pasteHint(canReadLinks = false, canReceiveShares = false))
    }

    @Test
    fun `a platform that reads links offers sharing only where pages are shared to the app`() {
        assertEquals(Res.string.add_songs_tip_paste_hint_with_links, pasteHint(canReadLinks = true, canReceiveShares = true))
        assertEquals(
            Res.string.add_songs_tip_paste_hint_with_links_without_sharing,
            pasteHint(canReadLinks = true, canReceiveShares = false),
        )
    }
}
