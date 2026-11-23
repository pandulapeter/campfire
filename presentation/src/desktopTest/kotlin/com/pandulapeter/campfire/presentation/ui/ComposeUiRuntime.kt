/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.platform.registerSkikoComposeImplementation

/**
 * Hands Compose's text and graphics their Skia implementation, which a window or a scene registers as it is made, for a
 * test that measures or draws with neither. Registering the same implementation again changes nothing.
 */
@OptIn(InternalComposeUiApi::class)
internal fun registerComposeUiRuntime() = registerSkikoComposeImplementation()
