/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.source

import com.pandulapeter.campfire.data.model.library.LibraryFiles

/**
 * Whether the file of `library/files` called [name] is kept out of the device backup after every write, by whichever
 * source writes it (the app's or sync's): an attachment, since a few recordings would pass what a backup holds and sync
 * is how they move between devices. A picture goes wherever the library goes, a name both could have counting as one.
 */
internal fun isLibraryFileKeptOutOfDeviceBackup(name: String) = !LibraryFiles.isLibraryImageName(name) && LibraryFiles.isLibraryAttachmentName(name)
