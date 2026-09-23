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

interface DeleteSongUseCase {

    /**
     * Deletes the file and removes the song from every setlist and from the saved transpositions. Throws when the file
     * could not be deleted, and nothing else has been touched then. Once it is gone the rest is attempted whatever
     * fails, and the answer is whether every reference could be removed: false leaves a setlist or the saved
     * transposition naming a file that is gone - a setlist then shows the song as missing.
     */
    suspend operator fun invoke(fileName: String): Boolean
}
