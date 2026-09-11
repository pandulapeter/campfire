/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
@file:OptIn(ExperimentalWasmJsInterop::class)

package com.pandulapeter.campfire.presentation.ui.platform

import kotlinx.coroutines.await
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsBoolean
import kotlin.js.Promise

/**
 * The answer of the first request, kept because the settings screen asks the same question again and the browsers
 * that decide by asking the user must only ask once. It is also the only state the web build has of its own, which
 * is what a cache in a file of platform declarations is worth.
 */
private var persistence: LibraryPersistence? = null

/**
 * The library lives in the browser's storage for this origin, which is cleared along with the site's data and, unless
 * the origin is marked persistent, may be evicted on its own when the device runs short of space. Campfire's library
 * is the user's own work and often its only copy, so the promise is worth asking for.
 *
 * What the answer depends on is the browser: Chromium grants it silently to a site the user has engaged with or
 * installed, Firefox asks the user, and Safari grants it on its own terms. A refusal costs nothing beyond the
 * promise, so it is asked for once and never insisted on.
 */
internal actual suspend fun requestLibraryPersistence(): LibraryPersistence = persistence ?: when {
    !isStoragePersistenceSupported() -> LibraryPersistence.BEST_EFFORT
    isStoragePersisted().await<JsBoolean?>()?.toBoolean() == true -> LibraryPersistence.GRANTED
    requestStoragePersistence().await<JsBoolean?>()?.toBoolean() == true -> LibraryPersistence.GRANTED
    else -> LibraryPersistence.BEST_EFFORT
}.also { persistence = it }

private fun isStoragePersistenceSupported(): Boolean =
    js("typeof navigator !== 'undefined' && navigator.storage != null && typeof navigator.storage.persist === 'function'")

/** Both calls are wrapped, because a browser that has the methods may still refuse the question in a private window. */
private fun isStoragePersisted(): Promise<JsBoolean?> = js("navigator.storage.persisted().catch(function () { return false; })")

private fun requestStoragePersistence(): Promise<JsBoolean?> = js("navigator.storage.persist().catch(function () { return false; })")
