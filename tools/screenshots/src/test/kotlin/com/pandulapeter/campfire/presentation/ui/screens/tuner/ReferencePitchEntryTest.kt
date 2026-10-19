/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.tuner

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextRange
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.tools
import com.pandulapeter.campfire.presentation.resources.tuner
import com.pandulapeter.campfire.presentation.resources.tuner_reference_pitch_increase
import com.pandulapeter.campfire.presentation.resources.tuner_reference_pitch_value
import com.pandulapeter.campfire.screenshots.AppTestScope
import com.pandulapeter.campfire.screenshots.runAppTest
import com.pandulapeter.campfire.screenshots.tap
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
internal class ReferencePitchEntryTest {

    @Test
    fun `focusing the reference pitch selects its number and not its unit`() = runAppTest {
        openReferencePitch()
        tap(hasSetTextAction())
        val field = onNode(hasSetTextAction()).fetchSemanticsNode().config
        val text = field.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()
        assertEquals(TextRange(0, text.length), field.getOrNull(SemanticsProperties.TextSelectionRange))
        assertEquals(text.filter(Char::isDigit), text)
    }

    @Test
    fun `a reference pitch typed over the selected number is the one shown`() = runAppTest {
        openReferencePitch()
        tap(hasSetTextAction())
        onNode(hasSetTextAction()).performTextInput("432")
        waitForIdle()
        // The stepper's buttons carry what it reads as their state, for a screen reader.
        val state = onNode(hasContentDescription(string(Res.string.tuner_reference_pitch_increase)))
            .fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)
        assertEquals(string(Res.string.tuner_reference_pitch_value, 432), state)
    }

    private fun AppTestScope.openReferencePitch() {
        tap(hasText(string(Res.string.tools)) and hasClickAction())
        tap(hasText(string(Res.string.tuner)) and hasClickAction())
        // The tools' tab row above the tuner scrolls too, sideways, where its tabs are wider than the window.
        onNode(hasScrollAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).performScrollToNode(hasSetTextAction())
        waitForIdle()
    }
}
