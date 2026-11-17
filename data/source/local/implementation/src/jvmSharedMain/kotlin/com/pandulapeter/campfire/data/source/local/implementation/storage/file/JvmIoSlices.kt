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

/**
 * The most bytes the JVM's storage hands an NIO channel or stream in one call. A channel moves a heap array through a
 * temporary direct buffer of the request's size, which Android allocates on the Java heap and caches per thread for
 * good, so a 50 MB attachment read or written in one call would cost 100 MB and leave 50 MB held for the rest of the
 * process. Slices of this size bound that buffer.
 */
internal const val JVM_IO_SLICE_SIZE = 64 * 1024
