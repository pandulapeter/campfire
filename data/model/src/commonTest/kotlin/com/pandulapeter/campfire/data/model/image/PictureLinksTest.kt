/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model.image

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PictureLinksTest {

    @Test
    fun `an https address is a link whatever its case`() {
        assertTrue(PictureLinks.isLink("https://example.com/chart.png"))
        assertTrue(PictureLinks.isLink("HTTPS://example.com/chart"))
    }

    @Test
    fun `a library name or a plain http address or a bare scheme is not a link`() {
        assertFalse(PictureLinks.isLink("0f1e2d.png"))
        assertFalse(PictureLinks.isLink("http://example.com/chart.png"))
        assertFalse(PictureLinks.isLink("https://"))
        assertFalse(PictureLinks.isLink("https://example.com/my chart.png"))
    }
}
