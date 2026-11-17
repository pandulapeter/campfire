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

/**
 * The cause a failed OPFS call is reported with: an [OutOfSpaceException] around it where the browser refused it for the
 * origin's quota, which reaches Kotlin only as a message naming the JavaScript error, `QuotaExceededError`.
 */
internal fun Throwable.asStorageCause(): Throwable = if (message.orEmpty().contains(QUOTA_EXCEEDED)) OutOfSpaceException(cause = this) else this

private const val QUOTA_EXCEEDED = "QuotaExceededError"
