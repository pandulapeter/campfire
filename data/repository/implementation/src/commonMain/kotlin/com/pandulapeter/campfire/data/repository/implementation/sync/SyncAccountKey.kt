/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.repository.implementation.sync

import com.pandulapeter.campfire.data.model.domain.SyncAccount

/**
 * What `sync-index.json` is filed under: the provider and the id the service itself gives the account. Nothing the
 * user can change is part of it, because an index that stops matching is an index that is ignored, and a run
 * without one brings back every file that was deleted since the last.
 *
 * An account whose id was never learnt falls back on what the key was made of before there was one.
 */
internal fun SyncAccount.indexKey() = "${providerId.id}:${id.ifEmpty { email ?: displayName }}"

/**
 * The key an index of this account was filed under before [indexKey] used the service's id: its e-mail address.
 *
 * Only the address, although the old key fell back on the display name where there was none. A display name is
 * not unique, so an index found under one proves nothing about whose it is - and the one time that fallback was
 * ever taken for a Dropbox account, the name stood in for the very id the key is made of now, so that index
 * matches as it is.
 */
internal fun SyncAccount.legacyIndexKey() = email?.takeIf(String::isNotEmpty)?.let { "${providerId.id}:$it" }
