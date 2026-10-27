/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.abc.implementation

/** The engine of a platform that has none yet: every render fails, which shows the tune as its text. */
internal object UnavailableAbcScriptEngine : AbcScriptEngine {
    override suspend fun render(source: String) = throw UnsupportedOperationException("ABC notation is not rendered on this platform yet")
}
