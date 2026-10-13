/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.tabs

import com.pandulapeter.campfire.chordpro.model.TabNotice

/**
 * The words a [TabNotice] is said in, resolved in the app's language while composing, since a song is moved for its
 * reader off the composition, where no string resource can be read. The sentences that name an instrument hold
 * [INSTRUMENT] in its place, and [instruments] is every instrument's name by the id a notice names it by.
 */
internal data class TabNoticeLabels(
    val octaveUp: String,
    val octaveDown: String,
    val otherInstrument: String,
    val notMoved: String,
    val notMovedInstrument: String,
    val hidden: String,
    val hiddenInstrument: String,
    val instruments: Map<String, String>,
) {

    /**
     * What [notice] says. A tab moved from another instrument says that and nothing about octaves, which another
     * instrument's strings have no say in; a tab shown as written says that and nothing about the transposition's
     * octave, which is the one thing that moved in it.
     */
    fun describe(notice: TabNotice): String {
        val instrument = notice.instrument?.let(instruments::get)
        return when (notice.kind) {
            TabNotice.Kind.MOVED -> when {
                instrument != null -> otherInstrument.replace(INSTRUMENT, instrument)
                notice.octaves < 0 -> octaveDown
                else -> octaveUp
            }
            TabNotice.Kind.NOT_MOVED -> instrument?.let { notMovedInstrument.replace(INSTRUMENT, it) } ?: notMoved
            TabNotice.Kind.HIDDEN -> instrument?.let { hiddenInstrument.replace(INSTRUMENT, it) } ?: hidden
        }
    }
}

/** What the sentences of [TabNoticeLabels] hold in place of the instrument's name. */
internal const val INSTRUMENT = "\u0001"
