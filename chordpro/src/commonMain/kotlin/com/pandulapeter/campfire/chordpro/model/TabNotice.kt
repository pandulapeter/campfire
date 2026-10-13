/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.model

/**
 * What a reader is told above a run of tablature that was not simply moved to their instrument: only where something
 * was decided for them that they would otherwise not know about. A tab moved string by string to the tuning they play in,
 * with every fret still on the neck, says nothing, since nothing about it could have been done differently.
 *
 * @property instrument The instrument the tab is written for, where it is not the one it is read on: what a tab's
 *   selector or its number of strings names it by ([TabTuning.instrument]). Null for a tab of the reader's own instrument.
 * @property octaves How many octaves the tab was moved by on top of what moving it asked for, up where positive: the
 *   one way a tab that would have left the neck stays on it, which makes it sound an octave away from what was written.
 */
public data class TabNotice(
    val kind: Kind,
    val instrument: String? = null,
    val octaves: Int = 0,
) {

    public enum class Kind {

        /** The tab was moved, but by octaves, from another instrument, or both. */
        MOVED,

        /** The tab could not be moved to the reader's strings, and is shown as it is written, with its own string names. */
        NOT_MOVED,

        /** The reader's instrument has nothing a tab could be played on, so the tab is left out and this said in its place. */
        HIDDEN,
    }
}
