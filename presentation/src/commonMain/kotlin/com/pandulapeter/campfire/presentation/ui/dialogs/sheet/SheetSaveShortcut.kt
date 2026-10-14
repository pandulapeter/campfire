/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs.sheet

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * What Ctrl / Cmd + S presses in a [CampfireBottomSheet]: the [BottomSheetConfirmButton] in its header that writes
 * what the sheet is for, which sets itself here while it is there. Null where the sheet has no such button, or one the
 * key should not press.
 */
internal class SheetSaveShortcut {
    var action: (() -> Unit)? = null
}

/** Whether anything in a [CampfireBottomSheet] has the focus, read once from an effect rather than drawn from. */
internal class SheetFocusState {
    var hasFocus = false
}

/** The [SheetSaveShortcut] of the [CampfireBottomSheet] around it, null outside one. */
internal val LocalSheetSaveShortcut = staticCompositionLocalOf<SheetSaveShortcut?> { null }
