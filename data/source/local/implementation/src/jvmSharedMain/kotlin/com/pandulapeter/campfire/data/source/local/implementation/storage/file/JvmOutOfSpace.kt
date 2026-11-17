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

import com.pandulapeter.campfire.data.model.OutOfSpaceException
import java.io.IOException

/**
 * What a failed write is reported with as its cause: an [OutOfSpaceException] around it where the file system ran out
 * of room, else the failure itself. The JVM has no exception of its own for that, so it is told by the message the
 * operating system gives (Android's `ErrnoException` names `ENOSPC` in its own), anywhere along the causes.
 */
internal fun IOException.asStorageCause(): Throwable =
    if (generateSequence<Throwable>(this) { it.cause }.take(MAX_CAUSE_DEPTH).any { it.message.isOutOfSpaceMessage() }) OutOfSpaceException(cause = this) else this

private fun String?.isOutOfSpaceMessage() = this != null && OUT_OF_SPACE_MESSAGES.any { contains(it, ignoreCase = true) }

private val OUT_OF_SPACE_MESSAGES = listOf("No space left on device", "ENOSPC", "There is not enough space on the disk")

private const val MAX_CAUSE_DEPTH = 16
