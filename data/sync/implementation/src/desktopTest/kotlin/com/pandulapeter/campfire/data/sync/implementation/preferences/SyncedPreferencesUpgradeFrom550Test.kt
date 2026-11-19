/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.sync.implementation.preferences

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Reads the cloud folder's `preferences.json` Campfire 5.5.0 wrote (`resources/upgrade-5.5.0/sync-folder`, encoded by
 * that version's own document code), with every field it owned set for some song, chord, or tag.
 */
class SyncedPreferencesUpgradeFrom550Test {

    private val bytes = File(requireNotNull(javaClass.getResource("/upgrade-5.5.0/sync-folder/preferences.json")).toURI()).readBytes()

    private val preferences = SyncedPreferences(
        transpositions = mapOf("oasis-wonderwall.cho" to 2, "leonard_cohen-hallelujah.cho" to -3),
        tempos = mapOf("oasis-wonderwall.cho" to 87),
        capos = mapOf("oasis-wonderwall.cho" to 2, "john_newton-amazing_grace.cho" to 1),
        autoScrollSpeeds = mapOf("leonard_cohen-hallelujah.cho" to 40),
        beatLevels = mapOf(
            "leonard_cohen-hallelujah.cho" to mapOf(
                "12/8" to listOf("accent", "normal", "normal", "muted", "normal", "normal", "accent", "normal", "normal", "muted", "normal", "normal"),
            ),
        ),
        chords = mapOf("guitar" to mapOf("F:0.4.7" to "x x 3 2 1 1"), "ukulele" to mapOf("D:0.4.7" to "2 2 2 0")),
        tags = mapOf("christmas" to "red", "worship" to "blue"),
    )

    @Test
    fun `the folder preferences 5_5_0 wrote read back as every value it held`() {
        val document = requireNotNull(SyncedPreferencesDocument.decode(bytes))

        assertEquals(true, SyncedPreferencesDocument.isReadable(document))
        assertFalse(SyncedPreferencesDocument.isNewerFormat(document))
        assertEquals(preferences, SyncedPreferencesDocument.preferencesOf(document))
    }

    @Test
    fun `the folder preferences 5_5_0 wrote are the same bytes again when nothing changed, so no run uploads them`() {
        val document = requireNotNull(SyncedPreferencesDocument.decode(bytes))

        assertContentEquals(bytes, SyncedPreferencesDocument.encode(SyncedPreferencesDocument.localDocument(document, preferences)))
    }
}
