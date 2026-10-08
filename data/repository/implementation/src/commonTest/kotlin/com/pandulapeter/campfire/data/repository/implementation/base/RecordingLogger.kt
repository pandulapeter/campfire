/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.repository.implementation.base

import com.pandulapeter.campfire.data.model.domain.Logger

/** Keeps every line instead of printing it, so that a test can check what a failure that was not thrown said. */
internal class RecordingLogger : Logger {
    val lines = mutableListOf<String>()

    override fun log(message: String) {
        lines += message
    }
}
