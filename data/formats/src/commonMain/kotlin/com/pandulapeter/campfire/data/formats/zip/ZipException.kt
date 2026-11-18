/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.formats.zip

/**
 * Thrown when an archive is malformed, truncated, encrypted, split, or uses a feature this minimal implementation does
 * not support (compression methods other than STORED and DEFLATE), or when the writer gets entries it cannot write. Only this module throws one; it is public so that a caller in another module
 * can tell a damaged archive from whatever else failed around it, and a [WrappedArchiveException] from the rest.
 */
open class ZipException internal constructor(message: String) : Exception(message)
