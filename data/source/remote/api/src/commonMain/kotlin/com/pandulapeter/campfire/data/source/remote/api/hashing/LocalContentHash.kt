/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.remote.api.hashing

/**
 * The fingerprint the sync index remembers for every local file, and the only thing that decides whether a file has
 * changed since the last run.
 *
 * Deliberately not a timestamp. The four platforms disagree about modification times and the web reports none at
 * all, so a run that trusted a clock would sooner or later decide that an untouched song was the newer version and
 * overwrite the one the user actually edited.
 *
 * It is Campfire's own hash, in Campfire's own format, and never compared against anything a service reports - that
 * comparison belongs to `SyncProvider.contentHashOf`, in whatever dialect the service happens to speak.
 */
fun localContentHash(bytes: ByteArray) = Sha256.hashToHex(bytes)
