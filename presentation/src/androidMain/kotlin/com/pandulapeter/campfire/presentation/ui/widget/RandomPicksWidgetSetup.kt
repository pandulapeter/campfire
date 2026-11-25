/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.widget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.song.tagKey
import com.pandulapeter.campfire.presentation.ui.components.MAX_TITLE_LENGTH
import com.pandulapeter.campfire.presentation.ui.components.rememberClearTextButton
import kotlin.math.roundToInt

/**
 * The random picks widget's setup screen: its own title (empty for the snapshot's Random picks, which the field shows as
 * its placeholder), how many songs it draws, and the tags and languages of [snapshot]'s library it draws from,
 * [initialTitle], [initialCount], and [initialFilter] at first. Done calls [onDone] with them. A group the library has
 * none of is left out.
 *
 * The title is typed as the app's setlist titles are (`dialogs/forms`): selected whole the first time it takes focus, so
 * the first key typed replaces a title rather than adding to it, cut at their length, with a clear button, and the
 * keyboard's Done putting the keyboard away.
 */
@Composable
fun RandomPicksWidgetSetup(
    snapshot: WidgetSnapshot,
    initialTitle: String,
    initialCount: Int,
    initialFilter: RandomPicksFilter,
    onDone: (title: String, count: Int, filter: RandomPicksFilter) -> Unit,
) {
    val strings = snapshot.strings
    var title by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(text = initialTitle, selection = TextRange(initialTitle.length)))
    }
    var hasTitleBeenFocused by rememberSaveable { mutableStateOf(false) }
    // A tap focuses the field and places its caret in the same pass, which would undo the selection made as the focus
    // arrives, so the field's selection changes are let through only after that frame (as `AddressTextField` does).
    var isFocusArriving by remember { mutableStateOf(false) }
    LaunchedEffect(isFocusArriving) {
        if (isFocusArriving) {
            withFrameNanos { }
            isFocusArriving = false
        }
    }
    var count by rememberSaveable { mutableIntStateOf(initialCount.coerceIn(RANDOM_PICKS_COUNTS)) }
    val focusManager = LocalFocusManager.current
    // Lists rather than sets, which a saved state can hold. A tag is kept as the library writes it, matched as the filter
    // matches it, so one saved in another case is still found selected.
    var tags by rememberSaveable {
        val initialKeys = initialFilter.tags.mapTo(mutableSetOf(), ::tagKey)
        mutableStateOf(snapshot.tags.filter { tagKey(it) in initialKeys })
    }
    var languages by rememberSaveable { mutableStateOf(snapshot.languages.map { it.code }.filter { it in initialFilter.languages }) }
    WidgetSetupScaffold(
        snapshot = snapshot,
        onDone = { onDone(title.text, count, RandomPicksFilter(tags = tags.toSet(), languages = languages.toSet())) },
    ) {
        item(key = TITLE_KEY) {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).onFocusChanged {
                    if (it.isFocused && !hasTitleBeenFocused) {
                        hasTitleBeenFocused = true
                        title = title.copy(selection = TextRange(0, title.text.length))
                        isFocusArriving = true
                    }
                },
                value = title,
                onValueChange = { newValue ->
                    if (isFocusArriving && newValue.text == title.text) return@OutlinedTextField
                    val text = newValue.text.replace("\n", "").take(MAX_TITLE_LENGTH)
                    // Rebuilt only where the text had to be cut, so that the field's own selection (the caret, a dragged
                    // run of text) survives every other keystroke.
                    title = if (text == newValue.text) {
                        newValue
                    } else {
                        TextFieldValue(text = text, selection = TextRange(newValue.selection.end.coerceAtMost(text.length)))
                    }
                },
                label = { Text(text = strings.title) },
                placeholder = { Text(text = strings.randomPicks) },
                trailingIcon = rememberClearTextButton(isVisible = title.text.isNotEmpty(), onClear = { title = TextFieldValue() }),
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            )
        }
        item(key = COUNT_KEY) {
            PickCount(
                label = strings.pickCount,
                count = count,
                onCountChange = { count = it },
            )
        }
        item(key = DESCRIPTION_KEY) {
            Text(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                text = strings.randomPicksFilterDescription,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (snapshot.tags.isNotEmpty()) {
            item(key = TAGS_KEY) {
                ChoiceGroup(
                    title = strings.tags,
                    choices = snapshot.tags.map { it to it },
                    selected = tags,
                    onToggled = { tag -> tags = if (tag in tags) tags - tag else tags + tag },
                )
            }
        }
        if (snapshot.languages.isNotEmpty()) {
            item(key = LANGUAGES_KEY) {
                ChoiceGroup(
                    title = strings.languages,
                    choices = snapshot.languages.map { it.code to it.name },
                    selected = languages,
                    onToggled = { code -> languages = if (code in languages) languages - code else languages + code },
                )
            }
        }
    }
}

/** [label] and the [count] beside it, over a slider across [RANDOM_PICKS_COUNTS]. */
@Composable
private fun PickCount(
    label: String,
    count: Int,
    onCountChange: (Int) -> Unit,
) = Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(
            modifier = Modifier.weight(1f),
            text = label,
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    Slider(
        modifier = Modifier.fillMaxWidth().semantics {
            contentDescription = label
            // The range alone would be read as a percentage of it, which says nothing about a number of songs.
            stateDescription = count.toString()
        },
        value = count.toFloat(),
        onValueChange = { onCountChange(it.roundToInt()) },
        valueRange = RANDOM_PICKS_COUNTS.first.toFloat()..RANDOM_PICKS_COUNTS.last.toFloat(),
        steps = RANDOM_PICKS_COUNTS.last - RANDOM_PICKS_COUNTS.first - 1,
    )
}

/** A group's [title] over a chip for each of its [choices] (a value and its name), the [selected] ones ticked. */
@Composable
private fun ChoiceGroup(
    title: String,
    choices: List<Pair<String, String>>,
    selected: List<String>,
    onToggled: (String) -> Unit,
) = Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
    Text(
        modifier = Modifier.padding(bottom = 8.dp),
        text = title,
        style = MaterialTheme.typography.titleSmall,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEach { (value, name) ->
            // No tick: one appearing would widen the chip and push its neighbours along at once, where the selected
            // colors already change in an animation of their own.
            FilterChip(
                selected = value in selected,
                onClick = { onToggled(value) },
                label = { Text(text = name) },
            )
        }
    }
}

/** The list's own rows' keys. */
private const val TITLE_KEY = "title_field"
private const val COUNT_KEY = "count"
private const val DESCRIPTION_KEY = "description"
private const val TAGS_KEY = "tags"
private const val LANGUAGES_KEY = "languages"
