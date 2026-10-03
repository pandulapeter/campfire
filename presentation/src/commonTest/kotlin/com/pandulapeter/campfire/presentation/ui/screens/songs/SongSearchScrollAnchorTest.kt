/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.fail

class SongSearchScrollAnchorTest {
    private val contents = "unchanged sections"
    private val originalPosition = SongSearchScrollAnchor.Position(8, 19)
    private val snapshot = SongSearchScrollAnchor.Snapshot(
        cardIndex = 9,
        cardTop = -19,
        position = originalPosition,
        trailingHeaderCount = 3,
    )

    @Test
    fun openingDuringAnExistingScrollNeverCapturesOrRestoresAfterItStops() {
        val anchor = SongSearchScrollAnchor(isOpen = false)
        anchor.update(open = true, contents = contents, isScrolling = true, inset = 0) { fail("Must not capture during scrolling") }
        assertNull(anchor.positionFor(24, isScrolling = true))
        anchor.update(open = true, contents = contents, isScrolling = false, inset = 56) { fail("Idle must not rearm this transition") }
        assertNull(anchor.positionFor(56, isScrolling = false))
        assertEquals(0, anchor.trailingHeaderCount)
    }

    @Test
    fun scrollStartingBetweenCompositionAndMeasurementPermanentlyReleasesTheAnchor() {
        val anchor = SongSearchScrollAnchor(isOpen = false)
        anchor.update(open = true, contents = contents, isScrolling = false, inset = 0) { snapshot }
        assertEquals(SongSearchScrollAnchor.Position(9, 29), anchor.positionFor(10, isScrolling = false))
        assertNull(anchor.positionFor(20, isScrolling = true))
        assertNull(anchor.positionFor(56, isScrolling = false))
        assertEquals(0, anchor.trailingHeaderCount)
    }

    @Test
    fun closingDuringAnExistingScrollCannotRestoreTheOriginalPositionAtTheEnd() {
        val anchor = SongSearchScrollAnchor(isOpen = false)
        anchor.update(open = true, contents = contents, isScrolling = false, inset = 0) { snapshot }
        anchor.positionFor(56, isScrolling = false)
        anchor.update(open = false, contents = contents, isScrolling = true, inset = 56) { fail("Must not recapture during scrolling") }
        assertNull(anchor.positionFor(30, isScrolling = true))
        anchor.update(open = false, contents = contents, isScrolling = false, inset = 0) { fail("Idle must not rearm closing") }
        assertNull(anchor.positionFor(0, isScrolling = false))
    }

    @Test
    fun scrollInterruptingTheReturnTransitionCancelsItsFinalRestore() {
        val anchor = SongSearchScrollAnchor(isOpen = false)
        anchor.update(open = true, contents = contents, isScrolling = false, inset = 0) { snapshot }
        anchor.positionFor(56, isScrolling = false)
        anchor.update(open = false, contents = contents, isScrolling = false, inset = 56) { fail("Reuse the original anchor") }
        anchor.positionFor(40, isScrolling = false)
        assertNull(anchor.positionFor(20, isScrolling = true))
        assertNull(anchor.positionFor(0, isScrolling = false))
    }

    @Test
    fun unchangedRoundTripKeepsTheCardFixedAndRestoresTheExactOriginalPosition() {
        val anchor = SongSearchScrollAnchor(isOpen = false)
        anchor.update(open = true, contents = contents, isScrolling = false, inset = 0) { snapshot }
        for (inset in listOf(0, 10, 28, 55, 56)) {
            val position = anchor.positionFor(inset, isScrolling = false) ?: fail("Missing opening position")
            assertEquals(snapshot.cardTop, inset - position.offset)
        }
        anchor.update(open = false, contents = contents, isScrolling = false, inset = 56) { fail("Reuse the original anchor") }
        for (inset in listOf(56, 40, 10, 1)) {
            val position = anchor.positionFor(inset, isScrolling = false) ?: fail("Missing return position")
            assertEquals(snapshot.cardTop, inset - position.offset)
        }
        assertEquals(originalPosition, anchor.positionFor(0, isScrolling = false))
        assertNull(anchor.positionFor(0, isScrolling = false))
    }

    @Test
    fun changedSearchResultsReleaseTheOriginalAnchor() {
        val anchor = SongSearchScrollAnchor(isOpen = false)
        anchor.update(open = true, contents = contents, isScrolling = false, inset = 0) { snapshot }
        anchor.update(open = true, contents = "filtered results", isScrolling = false, inset = 25) { fail("Must not rearm for changed contents") }
        assertNull(anchor.positionFor(56, isScrolling = false))
        assertEquals(0, anchor.trailingHeaderCount)
    }
}
