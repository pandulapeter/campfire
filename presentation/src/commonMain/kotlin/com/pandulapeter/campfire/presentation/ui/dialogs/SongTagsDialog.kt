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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.done
import com.pandulapeter.campfire.presentation.resources.ic_add
import com.pandulapeter.campfire.presentation.resources.save
import com.pandulapeter.campfire.presentation.resources.song_details_tag_create
import com.pandulapeter.campfire.presentation.resources.song_details_tags_manage
import com.pandulapeter.campfire.presentation.resources.song_details_tags_search
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.ActionListItem
import com.pandulapeter.campfire.presentation.ui.components.CheckboxListItem
import com.pandulapeter.campfire.presentation.ui.components.HideKeyboardWhenScrolledDown
import com.pandulapeter.campfire.presentation.ui.components.LabelSortingToggle
import com.pandulapeter.campfire.presentation.ui.components.KeepChecklistRowsInPlace
import com.pandulapeter.campfire.presentation.ui.components.checklistItems
import com.pandulapeter.campfire.presentation.ui.components.rememberChecklistOrder
import com.pandulapeter.campfire.presentation.ui.components.sortedAlphabeticallyBy
import com.pandulapeter.campfire.presentation.ui.components.ScrollToStartWhenChanged
import com.pandulapeter.campfire.presentation.ui.components.fadingTopEdge
import com.pandulapeter.campfire.presentation.ui.components.listItemAnimation
import com.pandulapeter.campfire.presentation.ui.components.orderedBy
import com.pandulapeter.campfire.presentation.ui.components.rememberClearTextButton
import com.pandulapeter.campfire.presentation.ui.components.textResource
import com.pandulapeter.campfire.presentation.ui.platform.bounceScrollableContent
import org.jetbrains.compose.resources.painterResource

/**
 * Every tag of a song, managed in one place: the library's tags as a checklist with the song's own ticked and at the
 * top, and a field that narrows the list and creates a tag the library does not have yet. As with the languages
 * ([SongLanguagesDialog]), the whole set is written when the dialog is confirmed, so a file the user owns is rewritten
 * once rather than once per checkbox. The checked tags lead the list as a selected group ([ChecklistOrder]), which a
 * tag checked later joins as a copy while the row itself stays where it was tapped.
 *
 * Offering the library's tags before anything is typed is the point of the list, because a library where the same idea
 * is filed under "christmas", "Christmas" and "xmas" is a library whose tags filter nothing. For the same reason what is
 * typed ticks the tag it spells, whatever its case, and a tag is only created where there is none to tick.
 *
 * It names the song it tags under its title ([SheetHeader]) wherever it was opened from: a tag put on the row next
 * to the one that was meant is a file quietly rewritten, and saying it over the song details screen as well keeps the
 * dialog reading the same from both places.
 */
@Composable
internal fun SongTagsDialog(
    viewModel: CampfireViewModel,
    dialog: DialogType.SongTags,
) {
    val libraryTags by viewModel.tags.collectAsStateWithLifecycle()
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val sortingMode = userPreferences?.tagSortingMode ?: UserPreferences.LabelSortingMode.BY_USAGE
    var query by rememberSaveable { mutableStateOf("") }
    // Saved, since the dialog outlives a recreated Activity and Save writes whatever is ticked at that moment.
    var selectedTags by rememberSaveable(dialog.song.fileName, stateSaver = stringListSaver) { mutableStateOf(dialog.song.tags) }
    var createdTags by rememberSaveable(dialog.song.fileName, stateSaver = stringListSaver) { mutableStateOf(emptyList()) }
    // Keep the library's sorting order, using the song's spelling where the same tag differs only by case.
    val offeredTags = remember(dialog.song, createdTags, libraryTags, sortingMode) {
        val ownTags = dialog.song.tags + createdTags
        val tags = (libraryTags.orderedBy(sortingMode).map { tag ->
            ownTags.firstOrNull { it.equals(tag.name, ignoreCase = true) } ?: tag.name
        } + ownTags).distinctBy { it.lowercase() }
        if (sortingMode == UserPreferences.LabelSortingMode.ALPHABETICAL) tags.sortedAlphabeticallyBy { it } else tags
    }
    val searchableTags = remember(offeredTags) { offeredTags.map { it to viewModel.normalizeForSearch(it) } }
    val matches = remember(searchableTags, query) {
        val normalizedQuery = viewModel.normalizeForSearch(query)
        searchableTags.mapNotNull { (tag, name) -> tag.takeIf { normalizedQuery in name } }
    }
    val checkedTagKeys = selectedTags.toSet()
    val tagOrder = rememberChecklistOrder(checkedTagKeys, sortingMode to query)
    val tagLayout = remember(matches, tagOrder) { tagOrder.layout(matches) { it } }
    val typedTag = query.trim()
    val spelledTag = offeredTags.firstOrNull { it.equals(typedTag, ignoreCase = true) }
    // Include a tag still in the field, since Save commits it too.
    val tagsToSave = when {
        typedTag.isEmpty() -> selectedTags
        spelledTag != null -> if (spelledTag in selectedTags) selectedTags else selectedTags + spelledTag
        else -> selectedTags + typedTag
    }
    val hasTagChanges = tagsToSave.map { it.lowercase() }.toSet() != dialog.song.tags.map { it.lowercase() }.toSet()
    val focusRequester = rememberFirstFieldFocusRequester()
    val keyboardController = LocalSoftwareKeyboardController.current
    // The keyboard is put away two frames after Done rather than straight from it: the web build focuses its text input
    // again whenever the field's text changes, which entering a tag does, once at once and once more on the next frame,
    // and either would bring the keyboard back up the moment it had gone.
    var keyboardHideRequests by remember { mutableIntStateOf(0) }
    LaunchedEffect(keyboardHideRequests) {
        if (keyboardHideRequests > 0) {
            repeat(2) { withFrameNanos { } }
            keyboardController?.hide()
        }
    }
    val enterTypedTag = {
        when {
            typedTag.isEmpty() -> Unit
            spelledTag != null -> if (spelledTag !in selectedTags) selectedTags = selectedTags + spelledTag
            else -> {
                createdTags = createdTags + typedTag
                selectedTags = selectedTags + typedTag
            }
        }
        query = ""
    }
    TextFieldBottomSheet(
        onDismissRequest = { viewModel.dismissSheet(dialog) },
        title = stringResource(Res.string.song_details_tags_manage),
        subtitle = songLabel(dialog.song),
        retainHeight = true,
        startButton = {
            LabelSortingToggle(
                sortingMode = sortingMode,
                onSortingModeSelected = viewModel::setTagSortingMode,
            )
        },
        text = { contentPadding ->
            Column {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    value = query,
                    onValueChange = { query = it.asSingleLine().take(MAX_TAG_LENGTH) },
                    label = { Text(stringResource(Res.string.song_details_tags_search)) },
                    trailingIcon = rememberClearTextButton(isVisible = query.isNotEmpty(), onClear = { query = "" }),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    // Done enters what is typed and puts the keyboard away, so that the list it narrowed is in view to
                    // be ticked; the next tag is a tap on the field away.
                    keyboardActions = KeyboardActions(
                        onDone = {
                            enterTypedTag()
                            keyboardHideRequests++
                        },
                    ),
                )
                val isCreatable = typedTag.isNotEmpty() && spelledTag == null
                if (isCreatable || matches.isNotEmpty()) {
                    val listState = rememberLazyListState()
                    HideKeyboardWhenScrolledDown(listState)
                    ScrollToStartWhenChanged(
                        listState = listState,
                        key = sortingMode to query,
                        contents = matches,
                    )
                    KeepChecklistRowsInPlace(listState, tagLayout)
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
                        if (isCreatable) {
                            item(key = CREATE_TAG_KEY) {
                                ActionListItem(
                                    modifier = listItemAnimation(listState),
                                    title = textResource(Res.string.song_details_tag_create, typedTag),
                                    icon = painterResource(Res.drawable.ic_add),
                                    horizontalInset = DIALOG_CHECKLIST_ROW_INSET,
                                    onClick = enterTypedTag,
                                )
                            }
                        }
                        checklistItems(
                            items = matches,
                            order = tagOrder,
                            key = { it },
                            listState = listState,
                            horizontalInset = DIALOG_CHECKLIST_ROW_INSET,
                        ) { tag ->
                            CheckboxListItem(
                                modifier = listItemAnimation(listState),
                                title = tag,
                                isChecked = tag in selectedTags,
                                horizontalInset = DIALOG_CHECKLIST_ROW_INSET,
                                onCheckedChange = { isChecked ->
                                    selectedTags = if (isChecked) selectedTags + tag else selectedTags - tag
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { close ->
            BottomSheetConfirmButton(
                enabled = hasTagChanges,
                onClick = {
                    viewModel.setSongTags(fileName = dialog.song.fileName, isEditorDraft = dialog.isEditorDraft, tags = tagsToSave, offeredTags = offeredTags)
                    close()
                },
            ) { Text(stringResource(if (dialog.isEditorDraft) Res.string.done else Res.string.save)) }
        },
    )
}

/** The tags ticked in the tag dialog, and the ones created there, as a Bundle can hold them. */
private val stringListSaver = listSaver<List<String>, String>(save = { it }, restore = { it })

/** A tag is a label to filter by, a word or two, and it sits in a pill next to others under a song's title. */
private const val MAX_TAG_LENGTH = 30

/**
 * The key of the tag dialog's create row. A tag row's key is its tag behind a `tag:` prefix, since a tag is text anybody
 * may have written, and only the prefix keeps one spelled like this key from being listed under the same key as the row.
 */
private const val CREATE_TAG_KEY = "create"
