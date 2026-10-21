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

import android.content.ClipData
import android.os.Build
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.toClipEntry

internal actual fun plainTextClipEntry(text: String): ClipEntry = ClipData.newPlainText(null, text).toClipEntry()

internal actual val isCopyConfirmedBySystem get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
