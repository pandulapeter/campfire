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

interface EditSetlistUseCase {

    /**
     * Writes what the user can say about a setlist: its title, and the description that may be blank. Only the title
     * reaches the file name, which is why this is a move as well as a write - nothing in the library points at a
     * setlist by file name, so the move costs nothing to follow, but the returned setlist may have a `fileName` the
     * caller has not seen before, and sync will carry it across as a deletion and a new file.
     */
    suspend operator fun invoke(setlist: Setlist, title: String, description: String): Setlist
}
