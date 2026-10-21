/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.backup

import com.pandulapeter.campfire.data.model.JsonBounds
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Parses the JSON of a foreign document, or returns null where it is not JSON or is past either of [JsonBounds]' bounds.
 *
 * The depth is bounded because of the stack: the tree parser recurses once per array level with no limit of its own, so
 * a few kilobytes of `[[[[` overflow it. The tokens are bounded because of the heap: a small text of many values builds
 * a tree of hundreds of megabytes. Both fail with an `Error` (`StackOverflowError`, `OutOfMemoryError`) that no
 * `catch (Exception)` on the import path stops, so both are counted before parsing, outside strings.
 */
internal object ShallowJson {

    fun parse(text: String): JsonElement? = if (!JsonBounds.isWithin(text, JsonBounds.MAX_TOKENS)) null else try {
        Json.parseToJsonElement(text)
    } catch (_: SerializationException) {
        null
    }
}
