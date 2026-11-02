/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state

import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * A mode ends with the screen it belongs to: the setlists' reordering and the songs' selection. The view model registers
 * [onBackStackChanged] with the navigator and [onBeforeDialogChange] with the dialog host, and calls [start].
 */
internal class ModeEndings(
    private val endSetlistReordering: () -> Unit,
    private val endSongSelection: () -> Unit,
) {

    fun onBackStackChanged(stack: List<CampfireDestination>) {
        if (stack.lastOrNull() != CampfireDestination.Setlists) endSetlistReordering()
        // A tab tapped, a song opened by an import, the import screen: none of them may stand over a mode nobody can
        // see, so leaving the songs drops the selection, as the reorder mode is dropped with its screen.
        if (stack.lastOrNull() != CampfireDestination.Songs) endSongSelection()
    }

    fun onBeforeDialogChange(dialogType: DialogType?) {
        // Editing can rename the setlist's file, which is what the mode is keyed by, so it ends the mode as the other
        // ways of leaving the rows do rather than losing it to a name that has stopped existing.
        if (dialogType is DialogType.Export || dialogType is DialogType.DuplicateSetlist || dialogType is DialogType.EditSetlist) {
            endSetlistReordering()
        }
    }

    fun start(scope: CoroutineScope, isPerformanceModeEnabled: Flow<Boolean>) {
        scope.launch {
            // Performance mode leaves no row menu to start the mode from, and no way to act on what is ticked.
            isPerformanceModeEnabled.collect { if (it) endSongSelection() }
        }
    }
}
