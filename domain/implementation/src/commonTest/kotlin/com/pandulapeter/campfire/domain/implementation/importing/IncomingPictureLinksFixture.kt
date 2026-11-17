/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.implementation.importing

import com.pandulapeter.campfire.domain.implementation.FakeImageRepository
import com.pandulapeter.campfire.domain.implementation.FakeImportDownloadRepository
import com.pandulapeter.campfire.domain.implementation.TEST_PREFERENCES
import com.pandulapeter.campfire.domain.implementation.UserPreferencesRepositoryStub

/** [IncomingPictureLinks] over [downloads] and [images], its Features switch [isEnabled]. */
internal fun incomingPictureLinks(
    downloads: FakeImportDownloadRepository = FakeImportDownloadRepository(),
    images: FakeImageRepository = FakeImageRepository(),
    isEnabled: Boolean = true,
) = IncomingPictureLinks(
    importDownloadRepository = downloads,
    userPreferencesRepository = object : UserPreferencesRepositoryStub() {
        override suspend fun loadUserPreferencesIfNeeded() = TEST_PREFERENCES.copy(isPictureLinkDownloadEnabled = isEnabled)
    },
    imageRepository = images,
)
