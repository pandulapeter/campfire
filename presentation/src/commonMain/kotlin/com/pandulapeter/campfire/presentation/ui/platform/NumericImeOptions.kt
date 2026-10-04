/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.platform

import androidx.compose.ui.text.input.PlatformImeOptions

/**
 * What a field typed in digits passes as its platform IME options: on iOS the numbers page of the full keyboard,
 * since the iPhone number pad `KeyboardType.Number` maps to has no return key, and a form walked with Next could not
 * leave the field. Null everywhere else, where the number keyboard has its action key.
 */
internal expect val numericPlatformImeOptions: PlatformImeOptions?
