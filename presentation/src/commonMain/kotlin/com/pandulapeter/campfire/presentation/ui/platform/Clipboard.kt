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

import androidx.compose.ui.platform.ClipEntry

/** [text] as a clip for `Clipboard.setClipEntry`, which every platform builds its own way. */
internal expect fun plainTextClipEntry(text: String): ClipEntry

/**
 * Whether the platform says by itself that something was copied: Android from 13 on shows what it holds, and a
 * snackbar saying it again would only cover it.
 */
internal expect val isCopyConfirmedBySystem: Boolean
