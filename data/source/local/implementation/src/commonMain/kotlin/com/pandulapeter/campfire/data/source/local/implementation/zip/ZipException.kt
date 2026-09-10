/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.zip

/**
 * Thrown when an archive is malformed, truncated, encrypted or uses a feature this minimal implementation does not
 * support (ZIP64, compression methods other than STORED and DEFLATE).
 */
internal class ZipException(message: String) : Exception(message)
