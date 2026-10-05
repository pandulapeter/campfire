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

interface DeleteLibraryUseCase {

    /**
     * Deletes every song and every setlist file of the library, and what the preferences remember about the songs by
     * their file names (the transpositions, the tempos, the capos and the folded sections). Nothing else is touched: the preferences, the
     * sync connection and the covers stay. Where an account is connected a sync run then starts at once with the
     * deletions allowed ([com.pandulapeter.campfire.data.model.domain.SyncDeletionPolicy.DELETE_REMOTELY]): the
     * confirmation the user typed is the answer the run's guard would otherwise stop to ask for, so the cloud folder,
     * and every device synced with it, is emptied as well. A file changed on another device since the last run still
     * comes back, since an edit beats a deletion. A run that is already going is stopped before anything is deleted,
     * since it would keep this one from starting; what it had moved stays moved. Every file is attempted even after one fails, the first failure being
     * thrown at the end.
     */
    suspend operator fun invoke()
}
