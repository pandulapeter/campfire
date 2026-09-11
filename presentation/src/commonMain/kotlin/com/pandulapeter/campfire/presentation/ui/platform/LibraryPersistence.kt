/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.platform

/**
 * Whether anything but the user can take the library away.
 *
 * Three of the four platforms keep it in a file system of their own, where it stays until the app is uninstalled.
 * The web build keeps it in the browser's storage for the origin, which a browser short of space is free to evict
 * unless it has been asked not to - and where the library is the only copy of the user's own work, that answer is
 * worth having and worth showing.
 */
internal enum class LibraryPersistence {

    /** The platform's own storage, which nothing clears behind the app's back. */
    GUARANTEED,

    /** The browser agreed to keep the library until the user clears it. */
    GRANTED,

    /** The browser made no such promise, so the library may be evicted when the device runs low on space. */
    BEST_EFFORT,
}

/**
 * Asks the platform to hold on to the library, and reports what it said. Called once as the app starts, and again by
 * the settings screen, which only reads the answer - asking twice must therefore be free of side effects beyond the
 * first, and must not put a second question to the user.
 */
internal expect suspend fun requestLibraryPersistence(): LibraryPersistence
