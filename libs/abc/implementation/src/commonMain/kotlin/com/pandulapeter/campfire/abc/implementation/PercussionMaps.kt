/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.abc.implementation

/**
 * [tune] with the percussion maps notation programs export (`I:percmap ^g g 42 x`: the note, the step it is shown at,
 * the MIDI drum, and the head) in abc2svg's form (`%%percmap ^g 42 x`: the note, the drum, and the head). abc2svg
 * takes the third word for the head's name, and a head named after no glyph is not drawn, so every note mapped that way
 * lost its head. xml2abc's `normal` head is abc2svg's default, which has no name of its own, so it is left out.
 */
internal fun withAbc2svgPercussionMaps(tune: String): String = EXPORTED_PERCUSSION_MAP.replace(tune) { match ->
    val (prefix, note, drum, head) = match.destructured
    "${prefix}percmap $note $drum" + if (head.isEmpty() || head == DEFAULT_HEAD) "" else " $head"
}

private val EXPORTED_PERCUSSION_MAP = Regex("""(?m)^(I:|%%)percmap[ \t]+(\S+)[ \t]+\S+[ \t]+(\d+)(?:[ \t]+(\S+))?[ \t]*$""")
private const val DEFAULT_HEAD = "normal"
