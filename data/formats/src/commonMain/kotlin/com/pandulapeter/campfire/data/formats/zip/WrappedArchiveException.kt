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
 * Thrown by [ZipReader.open] for an archive longer than `0xFFFFFFFF` bytes with no ZIP64 locator before its end record:
 * a tool that writes no ZIP64 (some versions of macOS Archive Utility and `ditto`, some Windows tools) wraps every size
 * and offset past 4 GiB around, so nothing in such an archive can be trusted, and it is refused rather than recovered.
 *
 * It is a [ZipException], so a caller that knows only damaged archives refuses it as one; a caller with a sentence of its
 * own for it (the archive is larger than 4 GB and was written by a tool that damages archives of that size, so zip it
 * in smaller parts or with another tool) catches it first.
 */
class WrappedArchiveException internal constructor(size: Long) :
    ZipException("An archive of $size bytes, past 4 GiB, has no ZIP64 records, so its sizes and offsets have wrapped around.")
