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

interface RememberDemoLibraryFilesUseCase {

    /** See `SyncRepository.rememberDemoLibraryFiles`: what the demo files just planted under these names hold. */
    suspend operator fun invoke(songFileNames: Collection<String>, setlistFileNames: Collection<String>)
}
