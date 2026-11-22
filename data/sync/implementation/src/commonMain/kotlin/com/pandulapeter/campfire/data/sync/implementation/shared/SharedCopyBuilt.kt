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

/**
 * A shared copy as [SharedCopyBuilder] built it: the packed [copy], null where it is too large to share, and the parts
 * of its inputs fingerprint ([sharedCopyFingerprint]) the build alone knows: a setlist's [songs] with the hashes of what
 * was read for them, and the library files it carries or meant to ([images]). [leftOut] holds the attachments left out
 * for their size, whose lines its songs no longer have.
 */
internal class SharedCopyBuilt(
    val copy: ByteArray?,
    val songs: List<Pair<String, String?>>?,
    val images: List<String>,
    val leftOut: Set<String>,
)
