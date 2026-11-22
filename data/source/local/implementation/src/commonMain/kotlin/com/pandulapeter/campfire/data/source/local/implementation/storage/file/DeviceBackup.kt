/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.storage.file

import com.pandulapeter.campfire.data.model.library.LibraryFiles

/** The name of the preferences document, the one file of [StorageDirectory.PREFERENCES] that belongs in a device backup. */
internal const val PREFERENCES_DOCUMENT_NAME = "preferences.json"

/**
 * Whether the file called [name] in [directory] goes into the device backup, as the root `CLAUDE.md`'s privacy section
 * lists it: the songs, the setlists, the pictures songs show, and the preferences document. Everything else describes
 * this installation rather than the user's work (the sync index, the shared links' mirror and journal, the editor's
 * draft, an unreadable preferences document's copy), can be downloaded again (the covers), or comes and goes (the
 * imports and exports), and a file nobody listed here is left out, the safe way to fail. An attachment is left out too,
 * since a few recordings would pass what a backup holds and sync is how they move between devices; a name both a
 * picture and an attachment could have counts as a picture. Only iOS asks (`FoundationFileStorage`): Android's backup is
 * the allow-list in `:app:android`'s XML, which has to agree with this one, and the desktop and the web have no backup.
 */
internal fun isInDeviceBackup(directory: StorageDirectory, name: String) = when (directory) {
    StorageDirectory.SONGS, StorageDirectory.SETLISTS -> true
    StorageDirectory.FILES -> LibraryFiles.isLibraryImageName(name) || !LibraryFiles.isLibraryAttachmentName(name)
    StorageDirectory.PREFERENCES -> name == PREFERENCES_DOCUMENT_NAME
    StorageDirectory.DRAFT_IMAGES, StorageDirectory.COVERS, StorageDirectory.EXPORTS, StorageDirectory.IMPORTS -> false
}
