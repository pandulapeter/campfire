/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state.songs

/**
 * The songs the search opened over, and the card at the top left of them and where it was (`rememberSearchOpening`).
 *
 * The card kept is the top left one unless that is one of the random picks, which an open search does not show
 * (`cardKeptAcrossSearch`); closing goes back to the top left one ([returnKey]) all the same.
 *
 * @property cardOffset Where the card was laid out, in the grid's content coordinates.
 * @property returnKey The card at the top left as the search opened, which closing puts back at [returnOffset].
 * @property hasBeenScrolled Whether the list has been scrolled since, which leaves the card's old place nobody's.
 * @property hasShownOtherSongs Whether the search has shown other songs since, which sent the list to the top: neither
 *   the gap that kept the card in its column ([slotFor]) nor the card's old place is brought back by a query cleared
 *   again, and closing keeps whichever card is at the top left then.
 */
internal class SearchOpening(
    val songGroups: SongGroups,
    val slot: SongCardSlot,
    val cardOffset: Int,
    val returnKey: Any,
    val returnOffset: Int,
) {
    var hasBeenScrolled = false
    var hasShownOtherSongs = false

    /** The card the results of [current] keep in its column, if they are still the ones it was picked for. */
    fun slotFor(current: SongGroups, columnCount: Int) = slot.takeIf {
        !hasShownOtherSongs && current.filterKey == songGroups.filterKey && it.columnCount == columnCount
    }
}
