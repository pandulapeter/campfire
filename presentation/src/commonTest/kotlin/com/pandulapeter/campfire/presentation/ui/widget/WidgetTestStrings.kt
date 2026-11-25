/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.widget

/** English-like widget texts for the tests, every count but 1 read as the plural. */
internal object WidgetTestStrings {

    private fun plural(one: String, other: String) = WidgetSnapshot.Plural(List(WidgetSnapshot.Plural.SIZE) { if (it == 1) one else other })

    val strings = WidgetSnapshot.Strings(
        isRightToLeft = false,
        randomPicks = "Random picks",
        drawNewPicks = "Draw new picks",
        emptyLibrary = "Add songs",
        songsOff = "Songs off",
        setlistsOff = "Setlists off",
        nextGig = "Next gig",
        nextGigDescription = "",
        noUpcomingSetlist = "No upcoming setlist",
        setlistMissing = "Gone",
        chooseSetlist = "",
        today = "Today",
        tomorrow = "Tomorrow",
        yesterday = "Yesterday",
        inDays = plural("In %1\$d day", "In %1\$d days"),
        daysAgo = plural("%1\$d day ago", "%1\$d days ago"),
        noMatchingSongs = "No match",
    )
}
