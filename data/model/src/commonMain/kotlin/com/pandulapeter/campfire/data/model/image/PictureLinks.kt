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

/**
 * A picture a song names by a web address (`{image: src=https://…}`) rather than by a library file. ChordPro never says
 * `src` may be an address, so no such picture is ever drawn from the web: it is downloaded once, when the user asks or an
 * import brings it, and kept as an ordinary library file, the directive then naming that file.
 */
object PictureLinks {

    /** Whether [source], an image directive's `src`, is an `https` address: the only kind the app ever downloads. */
    fun isLink(source: String) =
        source.length > SCHEME_PREFIX.length && source.startsWith(SCHEME_PREFIX, ignoreCase = true) && source.none { it.isWhitespace() }

    /** What every link starts with, in lowercase. */
    const val SCHEME_PREFIX = "https://"
}
