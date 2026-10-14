/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state.importing

import com.pandulapeter.campfire.presentation.ui.firstRun.DemoLibrary
import kotlinx.coroutines.CompletableDeferred

/**
 * One batch in the [ImportController]'s queue.
 *
 * @param source Emptied once a preparation has succeeded: the plan carries everything the rest of the import needs,
 *   while the request lives for as long as the review does. Kept while a preparation failed, for Try again, and an
 *   address that was downloaded becomes the file it downloaded, so a read that failed is not downloaded again.
 * @param shouldAnnounceResult False for the import nobody asked for: the demo library planted on a first run is
 *   the library the user is about to be shown, and a snackbar or a result counting the files of it would be the app
 *   reporting on something that, as far as anyone can tell, simply came with it.
 * @param shouldOpenSong True for files the system handed over and for an address, see [ImportController.importFiles].
 * @param shouldReview See [ImportController.enqueueImport].
 * @param isDemoLibrary The files are [DemoLibrary]'s, and what of them is written is remembered for sync.
 */
internal class ImportRequest(
    var source: ImportSource,
    val shouldAnnounceResult: Boolean,
    val shouldOpenSong: Boolean,
    val shouldReview: Boolean = true,
    val isDemoLibrary: Boolean = false,
    val settled: CompletableDeferred<Unit> = CompletableDeferred(),
)
