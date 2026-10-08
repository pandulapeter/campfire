/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.repository.api

/** The demo songs and setlist the app plants, and what it remembers about them. */
interface DemoLibraryRepository {

    /**
     * Writes down what the demo files the app has just planted hold, so that a run which finds a different version of
     * one of them in the cloud folder - planted there by an older version of the app - takes that version instead of
     * keeping both, for as long as this device's file still holds exactly what was planted. Names of files that are not
     * there are ignored. Never fails: a record that could not be taken only means a conflict copy later.
     */
    suspend fun rememberDemoLibraryFiles(songFileNames: Collection<String>, setlistFileNames: Collection<String>)
}
