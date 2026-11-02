/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.theme

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.chords
import com.pandulapeter.campfire.presentation.resources.settings
import com.pandulapeter.campfire.presentation.resources.setlists
import com.pandulapeter.campfire.presentation.resources.song_details_chord_diagram
import com.pandulapeter.campfire.presentation.resources.songs
import com.pandulapeter.campfire.presentation.resources.tools
import com.pandulapeter.campfire.screenshots.AppTestScope
import com.pandulapeter.campfire.screenshots.RISING_SUN_TITLE
import com.pandulapeter.campfire.screenshots.TestAppearance
import com.pandulapeter.campfire.screenshots.TestWindow
import com.pandulapeter.campfire.screenshots.closestTo
import com.pandulapeter.campfire.screenshots.contrastBetween
import com.pandulapeter.campfire.screenshots.drawnColors
import com.pandulapeter.campfire.screenshots.openEditor
import com.pandulapeter.campfire.screenshots.openNewSong
import com.pandulapeter.campfire.screenshots.openSong
import com.pandulapeter.campfire.screenshots.runAppTest
import com.pandulapeter.campfire.screenshots.textLayout
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.fail

/**
 * A color of the dark theme lost or unreadable, which only shows on the dark theme's own backgrounds: on the songs list,
 * a song's screen, the editor, and the New song sheet over the keyboard, each text checked (and the chord diagrams) is
 * drawn at a WCAG contrast of at least [MIN_CONTRAST] against what is behind it, read from the drawn window (see
 * `DrawnColors`). 3:1 is WCAG's floor for large text and for the parts of a control that have to be seen; the app's
 * texts are well over it, and a color meant for the light theme falls far under it.
 */
@OptIn(ExperimentalTestApi::class)
internal class DarkThemeContrastTest {

    /** The tags, the key, the bar: every text of the first song's card, and the navigation bar's labels. */
    @Test
    fun `the songs list is readable in the dark theme`() = runAppTest(appearance = DARK) {
        val card = node(hasText(RISING_SUN_TITLE, substring = true) and hasClickAction(), useUnmergedTree = false)
        val bar = listOf(Res.string.songs, Res.string.setlists, Res.string.tools, Res.string.settings).map { string(it) }
        assertReadable(textsIn(card) + bar.map { label(it) to node(hasText(it)).boundsInRoot })
    }

    /**
     * The chord diagrams and the song's text drawn in colors meant for the light theme: the first screen's texts and
     * first row of diagrams, and on the page that holds the first verse, its first line's chords and letters apart.
     */
    @Test
    fun `a song is readable in the dark theme`() = runAppTest(appearance = DARK) {
        openSong()
        val firstDiagram = text(Res.string.song_details_chord_diagram, "Am", PLACEHOLDER).substringBefore(PLACEHOLDER)
        val firstScreen = listOf(
            RISING_SUN_TITLE to node(hasText(RISING_SUN_TITLE)).boundsInRoot,
            string(Res.string.chords) to node(hasText(string(Res.string.chords))).boundsInRoot,
            "The diagrams of Am, C, and D" to node(hasContentDescription(firstDiagram, substring = true)).boundsInRoot,
        )
        assertReadable(firstScreen)
        val firstLine = SemanticsMatcher("reads \"$FIRST_LYRIC\"") { it.text.replace(ZERO_WIDTH_SPACE, "").replace(WHITESPACE, " ").trim() == FIRST_LYRIC }
        repeat(MAX_PAGES) {
            if (!isShown(firstLine)) {
                onRoot().performKeyInput { pressKey(Key.DirectionDown) }
                waitForIdle()
            }
        }
        val line = node(firstLine)
        val layout = textLayout(firstLine)
        // A line is set as tall as three of its letters, its chords drawn in the room above them.
        val lettersTop = line.boundsInRoot.top + layout.getLineBaseline(0) - with(layout.layoutInput.density) { layout.layoutInput.style.fontSize.toPx() }
        assertReadable(
            listOf(
                "The chords over \"$FIRST_LYRIC\"" to line.boundsInRoot.copy(bottom = lettersTop),
                "\"$FIRST_LYRIC\"" to line.boundsInRoot.copy(top = lettersTop),
            ),
        )
    }

    /**
     * The editor's syntax colors unreadable on the dark theme's background: each color the editor gives a part of the
     * song's text on the lines it shows (a directive's name and its value, a chord), found among the drawn pixels and set
     * against the field's background.
     */
    @Test
    fun `the editor's syntax colors are readable in the dark theme`() = runAppTest(appearance = DARK) {
        openEditor()
        val field = node(hasSetTextAction())
        val layout = textLayout(hasSetTextAction())
        val shown = field.boundsInRoot.intersect(WINDOW)
        val shownLines = (shown.height / (layout.getLineBottom(0) - layout.getLineTop(0))).toInt() - 1
        val colors = layout.layoutInput.text.spanStyles
            .filter { it.item.color != Color.Unspecified && layout.getLineForOffset(it.start) < shownLines }
            .map { it.item.color }
            .distinct()
        val image = captureWindow()
        val background = image.drawnColors(shown).background
        val failures = mutableListOf<String>()
        if (colors.isEmpty()) failures += "The editor colors none of the song's text it shows."
        colors.forEach { color ->
            val drawn = image.closestTo(shown, color)
            if (!drawn.isNear(color)) failures += "The editor's $color is not drawn; the closest pixel is $drawn."
            val contrast = contrastBetween(drawn, background)
            if (contrast < MIN_CONTRAST) failures += "The editor's $color is drawn at a contrast of ${"%.2f".format(contrast)} against $background."
        }
        if (failures.isNotEmpty()) fail(failures.joinToString(separator = "\n"))
    }

    /** The sheet's fields and header in the dark theme, over the keyboard: every text of the sheet the keyboard leaves. */
    @Test
    fun `the new song sheet is readable in the dark theme`() = runAppTest(appearance = DARK) {
        openNewSong()
        waitForIdle()
        val room = WINDOW.copy(bottom = WINDOW.bottom - keyboardHeight.value)
        val texts = onAllNodes(isText and hasAnyAncestor(isDialog()), useUnmergedTree = true).fetchSemanticsNodes()
            .filter { it.boundsInRoot.isShownIn(room) && !it.isDisabled }
            .map { label(it.text) to it.boundsInRoot }
        if (texts.isEmpty()) fail("The New song sheet shows no text.")
        assertReadable(texts)
    }

    /** Checks each of [texts], a name and where it is drawn, against [MIN_CONTRAST] in the window as it is drawn now. */
    private fun AppTestScope.assertReadable(texts: List<Pair<String, Rect>>) {
        waitForIdle()
        val image: ImageBitmap = captureWindow()
        val failures = texts.mapNotNull { (name, bounds) ->
            val colors = image.drawnColors(bounds)
            if (colors.contrast >= MIN_CONTRAST) {
                null
            } else {
                "$name ($bounds) is drawn in ${colors.ink} on ${colors.background}, a contrast of ${"%.2f".format(colors.contrast)}."
            }
        }
        if (failures.isNotEmpty()) fail(failures.joinToString(separator = "\n"))
    }
}

private val DARK = TestAppearance(isDark = true)
private val WINDOW = Rect(0f, 0f, TestWindow.PHONE.width.value, TestWindow.PHONE.height.value)
private val isText = SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)

@OptIn(ExperimentalTestApi::class)
private fun AppTestScope.node(matcher: SemanticsMatcher, useUnmergedTree: Boolean = true) =
    onNode(matcher, useUnmergedTree = useUnmergedTree).fetchSemanticsNode()

@OptIn(ExperimentalTestApi::class)
private fun AppTestScope.isShown(matcher: SemanticsMatcher) =
    onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().any { it.boundsInRoot.isShownIn(WINDOW) }

/** The texts drawn inside the node of [card], as the unmerged tree has them, each with where it is. */
@OptIn(ExperimentalTestApi::class)
private fun AppTestScope.textsIn(card: SemanticsNode): List<Pair<String, Rect>> {
    val unmerged = node(SemanticsMatcher("is the node ${card.id}") { it.id == card.id })
    fun visit(node: SemanticsNode): List<Pair<String, Rect>> = node.children.flatMap { child ->
        listOfNotNull(child.text.takeIf { it.isNotBlank() && child.boundsInRoot.isShownIn(WINDOW) }?.let { label(it) to child.boundsInRoot }) + visit(child)
    }
    return visit(unmerged)
}

private val SemanticsNode.text get() = config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }.orEmpty()

private val SemanticsNode.isDisabled get() = generateSequence(this) { it.parent }.any { SemanticsProperties.Disabled in it.config }

private fun Rect.isShownIn(room: Rect) = width > 0f && height > 0f && top >= room.top && bottom <= room.bottom

private fun label(text: String) = "\"$text\""

/** Whether [other] is this color as a stroke drawn in it shows, allowing for a channel rounded differently. */
private fun Color.isNear(other: Color) = maxOf(abs(red - other.red), abs(green - other.green), abs(blue - other.blue)) <= COLOR_TOLERANCE

private const val MIN_CONTRAST = 3f
private const val COLOR_TOLERANCE = 4f / 255f

/** More pages than House of the Rising Sun takes to reach its first verse on a phone. */
private const val MAX_PAGES = 8
private const val FIRST_LYRIC = "There is a house in New Orleans"

/** What a line of the song is broken by where a chord stands inside a word, so that a line is read without it. */
private const val ZERO_WIDTH_SPACE = "\u200B"
private val WHITESPACE = Regex("\\s+")

/** Stands in for a diagram's shape, so that the text before it is how a chord's description starts. */
private const val PLACEHOLDER = "\u0000"
