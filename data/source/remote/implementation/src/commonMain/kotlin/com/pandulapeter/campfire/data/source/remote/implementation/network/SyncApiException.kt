/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.remote.implementation.network

/**
 * A call a sync service answered with something other than success, failing that one call rather than the run. It is
 * an answer, so [syncTransport] passes it on as it is instead of taking it for the network failing.
 */
internal open class SyncApiException(message: String) : Exception(message)
