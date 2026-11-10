/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.abc.api

/**
 * Something the renderer reported about the tune it was given: an error or a warning, in its own words ([text], English,
 * for the log and the developer, never shown as the app's own text).
 *
 * [line] and [column] are where in that tune the renderer found it, both counted from zero, the lines split at `\n`:
 * what the caller passed, never the format lines the renderer puts in front of it. Both are null where the renderer
 * cannot tell, and [column] may name the line break that ends [line] or, for a renderer counting from the next line's
 * start, be -1, so a caller placing it on the text clamps it.
 */
public data class AbcMessage(
    val text: String,
    val line: Int?,
    val column: Int?,
)
