/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.convert

/**
 * The geometry Campfire's PDF export lays a song's rows out by, which its import ([ChordSheetCampfireLayout]) reads them
 * back by, in font sizes: a font's line box differs from one platform to the next, but where the export places each row
 * does not. Change a value here and both sides follow.
 */
public object CampfireExportLayout {

    /** How far under the top of a row of chords the text of its lyric starts. */
    public const val LYRIC_UNDER_CHORDS: Double = 1.3

    /** How tall a row of text is: how far under one row's text the next row's starts, where nothing stands between. */
    public const val ROW_HEIGHT: Double = 1.45
}
