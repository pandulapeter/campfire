/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.api.useCases

import com.pandulapeter.campfire.data.model.domain.Setlist

interface UpdateSetlistUseCase {

    /**
     * Changes the setlist called [fileName] as a single step: the latest version of it is read, [transform] is applied
     * and the result is written, before the next change is let in. It is how a setlist that is already in the library
     * is changed, since a screen's copy of it lags a write behind and two quick changes built on that copy would
     * build on the same snapshot, the second one undoing the first.
     *
     * @return The setlist as it was written, or null if there is no setlist by that name.
     */
    suspend operator fun invoke(fileName: String, transform: (Setlist) -> Setlist): Setlist?
}
