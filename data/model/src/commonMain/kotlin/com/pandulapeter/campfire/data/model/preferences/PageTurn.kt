/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model.preferences

/**
 * How a song laid out in [SongLayout.PAGES] is turned ([UserPreferences.pageTurn]) where several of its columns are on
 * screen: [PAGE] turns every column at once, [COLUMN] slides the song one column along, so that the column being read
 * moves to where the eye expects it and the next one is always in view.
 */
enum class PageTurn(val id: String) {
    PAGE("page"),
    COLUMN("column"),
}
