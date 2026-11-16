/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model.library

/**
 * One file of an archive being packed: [name] is its path inside the archive. An archive is packed from functions that
 * answer one each, read only when it is its turn, so that the archive is never held whole; a null leaves it out.
 */
class ArchiveEntry(
    val name: String,
    val bytes: ByteArray,
)
