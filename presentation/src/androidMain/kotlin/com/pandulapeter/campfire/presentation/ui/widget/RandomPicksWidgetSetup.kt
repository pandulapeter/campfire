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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.song.tagKey

/**
 * The random picks widget's setup screen: its own title (empty for the snapshot's Random picks, which the field shows as
 * its placeholder), and the tags and languages of [snapshot]'s library it draws from, [initialTitle] and [initialFilter]
 * at first. Done calls [onDone] with them. A group the library has none of is left out.
 */
@Composable
fun RandomPicksWidgetSetup(
    snapshot: WidgetSnapshot,
    initialTitle: String,
    initialFilter: RandomPicksFilter,
    onDone: (title: String, filter: RandomPicksFilter) -> Unit,
) {
    val strings = snapshot.strings
    var title by rememberSaveable { mutableStateOf(initialTitle) }
    // Lists rather than sets, which a saved state can hold. A tag is kept as the library writes it, matched as the filter
    // matches it, so one saved in another case is still found selected.
    var tags by rememberSaveable {
        val initialKeys = initialFilter.tags.mapTo(mutableSetOf(), ::tagKey)
        mutableStateOf(snapshot.tags.filter { tagKey(it) in initialKeys })
    }
    var languages by rememberSaveable { mutableStateOf(snapshot.languages.map { it.code }.filter { it in initialFilter.languages }) }
    WidgetSetupScaffold(
        snapshot = snapshot,
        onDone = { onDone(title, RandomPicksFilter(tags = tags.toSet(), languages = languages.toSet())) },
    ) {
        item(key = TITLE_KEY) {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                value = title,
                onValueChange = { title = it },
                label = { Text(text = strings.title) },
                placeholder = { Text(text = strings.randomPicks) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
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
private const val DESCRIPTION_KEY = "description"
private const val TAGS_KEY = "tags"
private const val LANGUAGES_KEY = "languages"
