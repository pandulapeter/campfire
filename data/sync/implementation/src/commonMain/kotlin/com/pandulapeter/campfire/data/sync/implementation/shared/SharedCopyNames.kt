/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.sync.implementation.shared

import com.pandulapeter.campfire.data.model.library.SyncCollection

/**
 * What [SharedCopyBuilder] knows of the library's names: the run's index ([IndexedNames]) for a copy a run builds, the
 * library's listing ([ListedNames]) for a share's first one. Both answer by the identity of a name, since the registry
 * and setlists may spell a file differently, and both know only the files `SyncCollection.matches` admits, so that the
 * two build the same copy from the same files.
 */
internal interface SharedCopyNames {

    /** This device's spelling of [name] in [kind], where the library holds the file under another one. */
    fun localNameOf(kind: SyncCollection, name: String): String

    /** Whether the library holds the picture or attachment [name]. */
    fun holdsFile(name: String): Boolean
}
