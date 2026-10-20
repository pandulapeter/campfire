/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.sync.implementation.preferences

import kotlinx.serialization.json.JsonObject

/**
 * The document a settle of [SyncedPreferencesSync] left as the last synced one, and the folder's revision of it, null
 * where the folder does not hold that document.
 */
internal data class SettledPreferences(
    val document: JsonObject,
    val revision: String?,
)
