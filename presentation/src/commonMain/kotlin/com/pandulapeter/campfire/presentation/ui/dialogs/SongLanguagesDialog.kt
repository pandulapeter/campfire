/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.data.model.domain.SongLanguage
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.localization.currentLanguage
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.done
import com.pandulapeter.campfire.presentation.resources.save
import com.pandulapeter.campfire.presentation.resources.song_details_languages_edit
import com.pandulapeter.campfire.presentation.resources.song_details_language_no_search_results
import com.pandulapeter.campfire.presentation.resources.song_details_language_search
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.CheckboxListItem
import com.pandulapeter.campfire.presentation.ui.components.HideKeyboardWhenScrolledDown
import com.pandulapeter.campfire.presentation.ui.components.LabelSortingToggle
import com.pandulapeter.campfire.presentation.ui.components.MAX_SEARCH_QUERY_LENGTH
import com.pandulapeter.campfire.presentation.ui.components.KeepChecklistRowsInPlace
import com.pandulapeter.campfire.presentation.ui.components.checklistItems
import com.pandulapeter.campfire.presentation.ui.components.rememberChecklistOrder
import com.pandulapeter.campfire.presentation.ui.components.ScrollToStartWhenChanged
import com.pandulapeter.campfire.presentation.ui.components.fadingTopEdge
import com.pandulapeter.campfire.presentation.ui.components.listItemAnimation
import com.pandulapeter.campfire.presentation.ui.components.pickableLanguages
import com.pandulapeter.campfire.presentation.ui.components.rememberClearTextButton
import com.pandulapeter.campfire.presentation.ui.platform.bounceScrollableContent

/**
 * The languages of one song, asked about all at once: the whole set is written when the dialog is confirmed, so a
 * file the user owns is rewritten once rather than once per checkbox.
 *
 * The list is ordered by the name the platform gives each language in the language the app is set to (see
 * [languageName]), under a selected group of the checked ones ([ChecklistOrder]), which a language checked later joins
 * as a copy while the row itself stays where it was tapped.
 *
 * What is listed before anything is typed is what can be named, plus the languages the song and the library already
 * use. Ordered by usage, the library's come first, most used first: the next song to be filed is far likelier to be in
 * one of them than in any of the six hundred the library has never held. Ordered alphabetically, they are among the
 * rest, since that order is asked for by somebody who looks a language up by its name. Everything else — on the web that is most languages, see
 * [pickableLanguages] — is found by typing its code, which is also what such a row is labelled with. A language
 * nobody can name is still a language the file can be filed under, and the code is the one thing the app always
 * knows about it.
 */
@Composable
internal fun SongLanguagesDialog(
    viewModel: CampfireViewModel,
    dialog: CampfireViewModel.DialogType.SongLanguages,
) {
    val appLanguageCode = currentLanguage.value.code
    val libraryLanguages by viewModel.languages.collectAsStateWithLifecycle()
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val sortingMode = userPreferences?.languageSortingMode ?: UserPreferences.LabelSortingMode.BY_USAGE
    var query by rememberSaveable { mutableStateOf("") }
    // Saved, since the dialog outlives a recreated Activity and Save writes whatever is ticked at that moment.
    var selectedCodes by rememberSaveable(
        dialog.song.fileName,
        stateSaver = listSaver<Set<String>, String>(save = { it.toList() }, restore = { it.toSet() }),
    ) { mutableStateOf(dialog.song.languages.toSet()) }
    val languages = remember(dialog.song, libraryLanguages, appLanguageCode, sortingMode) {
        val declared = dialog.song.languages
        val libraryCodes = libraryLanguages.map { it.code }.filterNot { it == SongLanguage.UNKNOWN }
        val pickable = pickableLanguages(appLanguageCode = appLanguageCode, alsoOffer = declared + libraryCodes, normalize = viewModel::normalize)
        val leading = when (sortingMode) {
            UserPreferences.LabelSortingMode.BY_USAGE -> libraryCodes.distinct()
            UserPreferences.LabelSortingMode.ALPHABETICAL -> emptyList()
        }
        leading.mapNotNull { code -> pickable.firstOrNull { it.code == code } } + pickable.filterNot { it.code in leading }
    }
    val focusRequester = rememberFirstFieldFocusRequester()
    val keyboardController = LocalSoftwareKeyboardController.current
    val matches = remember(languages, query) {
        val normalizedQuery = viewModel.normalize(query)
        // What was typed may be a code, and not the one the library files the language under: `HUN`, `hu-HU` and
        // `hu` all name Hungarian, and whichever of them a reader knows has to find the single row that is.
        val queryCode = viewModel.languageCode(query)
        if (normalizedQuery.isEmpty()) {
            languages.filter { it.isListed }
        } else {
            languages.filter { it.code == queryCode || normalizedQuery in it.sortKey || it.code.startsWith(normalizedQuery) }
        }
    }
    val languageOrder = rememberChecklistOrder(selectedCodes, listOf(sortingMode, query, appLanguageCode))
    val languageLayout = remember(matches, languageOrder) { languageOrder.layout(matches) { it.code } }
    TextFieldBottomSheet(
        onDismissRequest = { viewModel.dismissSheet(dialog) },
        title = stringResource(Res.string.song_details_languages_edit),
        subtitle = songLabel(dialog.song),
        retainHeight = true,
        startButton = {
            LabelSortingToggle(
                sortingMode = sortingMode,
                onSortingModeSelected = viewModel::setLanguageSortingMode,
            )
        },
        text = { contentPadding ->
            Column {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    value = query,
                    onValueChange = { query = it.replace("\n", "").take(MAX_SEARCH_QUERY_LENGTH) },
                    label = { Text(stringResource(Res.string.song_details_language_search)) },
                    trailingIcon = rememberClearTextButton(isVisible = query.isNotEmpty(), onClear = { query = "" }),
                    singleLine = true,
                    // What is typed here is as often a code as a name, and autocorrect would make a word of either.
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
                )
                if (matches.isEmpty()) {
                    Text(
                        modifier = Modifier.padding(top = 16.dp),
                        text = stringResource(Res.string.song_details_language_no_search_results),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    val listState = rememberLazyListState()
                    HideKeyboardWhenScrolledDown(listState)
                    ScrollToStartWhenChanged(
                        listState = listState,
                        key = sortingMode to query,
                        contents = matches,
                    )
                    KeepChecklistRowsInPlace(listState, languageLayout)
                    LazyColumn(
                        modifier = Modifier.bounceScrollableContent(listState)
                            .padding(top = 8.dp)
                            .reachingDialogEdges()
                            .weight(1f, fill = false)
                            .fadingTopEdge(listState, sheetContainerColor())
                            .followSheetGrowth(),
                        contentPadding = contentPadding,
                        state = listState,
                    ) {
                        checklistItems(
                            items = matches,
                            order = languageOrder,
                            key = { it.code },
                            listState = listState,
                            horizontalInset = DIALOG_CHECKLIST_ROW_INSET,
                        ) { language ->
                            CheckboxListItem(
                                modifier = listItemAnimation(listState),
                                title = language.label,
                                // The code is under the name, and is the name itself where there is none to put above it.
                                description = language.name?.let { language.code.uppercase() },
                                isChecked = language.code in selectedCodes,
                                horizontalInset = DIALOG_CHECKLIST_ROW_INSET,
                                onCheckedChange = { isChecked ->
                                    selectedCodes = if (isChecked) selectedCodes + language.code else selectedCodes - language.code
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { close ->
            BottomSheetConfirmButton(
                enabled = selectedCodes != dialog.song.languages.toSet(),
                onClick = {
                    viewModel.setSongLanguages(fileName = dialog.song.fileName, isEditorDraft = dialog.isEditorDraft, codes = selectedCodes.toList())
                    close()
                },
            ) { Text(stringResource(if (dialog.isEditorDraft) Res.string.done else Res.string.save)) }
        },
    )
}
