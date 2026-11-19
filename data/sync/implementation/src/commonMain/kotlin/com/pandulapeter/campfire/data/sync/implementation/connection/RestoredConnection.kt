/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.sync.implementation.connection

/**
 * What [SyncConnectionManager.restore] found: whether a connection is restored, and whether the run the last launch left
 * going was one the user should hear was interrupted. The repository answers it as its own `RestoreResult`.
 */
internal data class RestoredConnection(
    val isConnected: Boolean,
    val wasInterrupted: Boolean,
)
