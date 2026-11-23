/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.pagesong

/** What the model says a line is. The order is the model's output order (`LABELS` in `tools/page-model/lines.py`). */
public enum class PageLineLabel {

    /** The page's clutter: menus, ads, comments, other songs, the site's calls to action. */
    JUNK,

    /**
     * A line of the song: lyrics, chords, tabs, section headings. Blank lines are never labelled: the model was not
     * trained on them, so its label for one means nothing.
     */
    SONG,

    /** The song's title. */
    TITLE,

    /** The song's artist. */
    ARTIST,

    /** One of the song's details (key, capo, tempo, time, credits), still read by the converter's own rules. */
    METADATA,
}
