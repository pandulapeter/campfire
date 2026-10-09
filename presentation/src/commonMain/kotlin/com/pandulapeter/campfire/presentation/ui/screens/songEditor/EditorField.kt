/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songEditor

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.text.TextRange
import com.pandulapeter.campfire.presentation.resources.save

/**
 * The editor's field, and what became of it on the way back from a saved state.
 *
 * @param isDraftLost True where the field held unsaved text too long to be saved and the process that held it
 * is gone: the field starts from the file then, and the user is told so rather than left to find out.
 */
internal class EditorField(
    val textFieldState: TextFieldState,
    val isDraftLost: Boolean = false,
)

/**
 * Saves the field as its text and its selection, and the text only while it is not long.
 *
 * What is saved here crosses to the system in one Binder transaction, together with everything else the Activity
 * saves and out of about a megabyte for the whole process; past that Android kills the app as it goes to the
 * background. The field's own saver also writes the undo history, in which every rewrite of the whole document (a
 * transposition, a revert) is the whole text twice, so a dozen taps on a long song add up to that megabyte.
 *
 * What this gives up is only ever wanted after a configuration change - a change of language or dark mode restores
 * through here as well - and the view model lives through those, so the field itself is handed to it ([retain]) and
 * taken back as it is, undo history included and however long. A new process gets the text and the caret, or for a
 * long document the draft the pause put on disk ([recovered]), or failing that the file.
 */
internal class EditorFieldSaver(
    private val retain: (TextFieldState) -> Unit,
    private val retained: () -> TextFieldState?,
    private val recovered: () -> TextFieldState?,
    private val fileText: () -> String,
) : Saver<EditorField, Any> {

    override fun SaverScope.save(value: EditorField): Any {
        val textFieldState = value.textFieldState
        retain(textFieldState)
        val text = textFieldState.text.toString()
        return if (text.length <= LONG_DOCUMENT_LENGTH) {
            listOf(text, textFieldState.selection.start, textFieldState.selection.end)
        } else {
            // Whether there was anything to lose, so that an untouched long file does not come back with an apology.
            listOf(text != fileText())
        }
    }

    override fun restore(value: Any): EditorField {
        retained()?.let { return EditorField(it) }
        val saved = value as List<*>
        return if (saved.size == 1) {
            recovered()?.let { EditorField(it) }
                ?: EditorField(textFieldState = TextFieldState(initialText = fileText()), isDraftLost = saved[0] as Boolean)
        } else {
            EditorField(
                TextFieldState(
                    initialText = saved[0] as String,
                    initialSelection = TextRange(start = saved[1] as Int, end = saved[2] as Int),
                )
            )
        }
    }
}
