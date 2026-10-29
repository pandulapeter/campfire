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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import com.pandulapeter.campfire.presentation.localization.currentLanguage
import com.pandulapeter.campfire.presentation.ui.theme.ScriptFace
import com.pandulapeter.campfire.presentation.ui.theme.scriptFacesNeededBy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * The faces of other scripts the app needs, see `ProvideScriptFaces`: those of the interface's language, and those of
 * the library's text - the names, artists, tags, and declared languages of its songs, the names and descriptions of its
 * setlists, and the full text of the songs opened. Built but not collected anywhere but on the web, the one platform
 * that fetches a face only once it is needed.
 */
@Composable
internal fun rememberNeededScriptFaces(viewModel: CampfireViewModel): Flow<Set<ScriptFace>> = remember(viewModel) {
    combine(
        snapshotFlow { currentLanguage.value.code },
        viewModel.libraryState.allSongs,
        viewModel.libraryState.setlists,
        viewModel.songTextStore.songTexts,
    ) { languageCode, songs, setlists, songTexts ->
        scriptFacesNeededBy(
            languages = songs.flatMapTo(mutableSetOf(languageCode)) { it.languages },
            texts = songs.asSequence().flatMap { sequenceOf(it.title, it.artist) + it.tags } +
                setlists.asSequence().flatMap { sequenceOf(it.title, it.description) } +
                songTexts.values.asSequence(),
        )
    }.distinctUntilChanged()
}
