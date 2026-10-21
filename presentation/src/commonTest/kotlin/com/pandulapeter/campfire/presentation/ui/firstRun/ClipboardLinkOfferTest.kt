/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.firstRun

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClipboardLinkOfferTest {

    @Test
    fun `an onboarding skipped before its last page offers the copied link once more`() {
        assertTrue(isClipboardLinkOfferedAgain(isOffered = true, isLeftFromLastPage = false))
    }

    @Test
    fun `an onboarding left from its last page, where the card was, offers nothing more`() {
        assertFalse(isClipboardLinkOfferedAgain(isOffered = true, isLeftFromLastPage = true))
    }

    @Test
    fun `nothing is offered where no link was`() {
        assertFalse(isClipboardLinkOfferedAgain(isOffered = false, isLeftFromLastPage = false))
        assertFalse(isClipboardLinkOfferedAgain(isOffered = false, isLeftFromLastPage = true))
    }
}
