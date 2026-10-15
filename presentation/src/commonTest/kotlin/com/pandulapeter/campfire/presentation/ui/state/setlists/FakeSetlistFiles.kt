/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state.setlists

import com.pandulapeter.campfire.data.model.setlist.Setlist
import com.pandulapeter.campfire.domain.api.setlists.CreateSetlistUseCase
import com.pandulapeter.campfire.domain.api.setlists.DeleteSetlistUseCase
import com.pandulapeter.campfire.domain.api.setlists.EditSetlistUseCase
import com.pandulapeter.campfire.domain.api.setlists.SaveSetlistUseCase
import com.pandulapeter.campfire.domain.api.setlists.UpdateSetlistUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.LocalDate

/**
 * The setlist files the use cases read and write, by name, published on [setlists] the way the library state follows
 * the repository. A created setlist is named after its title, as an empty file.
 */
internal class FakeSetlistFiles(vararg initial: Setlist) :
    CreateSetlistUseCase,
    SaveSetlistUseCase,
    EditSetlistUseCase,
    UpdateSetlistUseCase,
    DeleteSetlistUseCase {

    val setlists = MutableStateFlow(initial.toList())

    /** The setlist named [fileName] as its file holds it now. */
    operator fun get(fileName: String) = setlists.value.firstOrNull { it.fileName == fileName }

    override suspend fun invoke(title: String, description: String, date: LocalDate, isCountdownShown: Boolean) = Setlist(
        fileName = "${title.lowercase()}.setlist.json",
        title = title,
        description = description,
        date = date,
        isCountdownShown = isCountdownShown,
        isArchived = false,
        entries = emptyList(),
        size = 0L,
    ).also(::write)

    override suspend fun invoke(setlist: Setlist) = write(setlist)

    override suspend fun invoke(fileName: String, title: String, description: String, date: LocalDate, isCountdownShown: Boolean) =
        get(fileName)?.copy(title = title, description = description, date = date, isCountdownShown = isCountdownShown)?.also(::write)

    override suspend fun invoke(fileName: String, transform: (Setlist) -> Setlist) = get(fileName)?.let(transform)?.also(::write)

    override suspend fun invoke(fileName: String) {
        setlists.value = setlists.value.filterNot { it.fileName == fileName }
    }

    private fun write(setlist: Setlist) {
        setlists.value = setlists.value.filterNot { it.fileName == setlist.fileName } + setlist
    }
}
