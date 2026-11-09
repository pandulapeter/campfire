/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.abc

/**
 * The annotations that name a part of a song: notation programs export their rehearsal marks as annotations above the
 * staff (`"^Verse"`, `"^chorus 2"`). Only the parts of a song count, since an annotation is far more often a direction
 * (`"^Fine"`, `"^rit."`, `"^Coda"`, which also marks a jump), and a direction taken for a part would cut the grid apart.
 */
internal object AbcPartNames {

    private val parts = setOf(
        "intro", "verse", "pre-chorus", "pre chorus", "prechorus", "chorus", "refrain", "bridge", "solo", "interlude",
        "instrumental", "outro",
    )
    private val numbered = Regex("""^(.*?)\s*\d*\.?$""")

    /** The label [annotation] (its text, the placement mark left out) gives its part, its first letter raised, or null. */
    fun label(annotation: String): String? {
        val text = annotation.trim()
        val name = numbered.find(text)?.groupValues?.get(1)?.lowercase() ?: return null
        return if (name in parts) text.replaceFirstChar { it.uppercaseChar() } else null
    }
}
