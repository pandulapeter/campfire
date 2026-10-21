/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.sync

import com.pandulapeter.campfire.data.model.library.LibraryFiles
import com.pandulapeter.campfire.data.model.share.Share
import com.pandulapeter.campfire.data.model.share.SharedLinkKind

/**
 * Whether these shares name the library file [fileName] of [kind], under any case or Unicode form of the name, as the
 * account's list matches it.
 */
internal fun List<Share>.isShared(kind: SharedLinkKind, fileName: String) =
    any { it.kind == kind && LibraryFiles.isSameLibraryName(it.fileName, fileName) }
