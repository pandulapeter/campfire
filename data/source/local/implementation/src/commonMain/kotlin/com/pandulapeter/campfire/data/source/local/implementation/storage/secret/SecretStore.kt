/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.storage.secret

/**
 * A small string kept where the platform keeps secrets. Null when there is none.
 *
 * Android encrypts it with a key held by the Keystore and iOS hands it to the Keychain. Desktop and the web keep a
 * plain file in the preferences directory instead: a desktop keychain is a different native dependency on every
 * operating system, which is not worth what it would protect against an attacker who can already read the user's
 * files, and the browser has no equivalent at all.
 */
internal interface SecretStore {

    suspend fun load(key: String): String?

    /** Null removes the value. */
    suspend fun save(key: String, value: String?)
}
