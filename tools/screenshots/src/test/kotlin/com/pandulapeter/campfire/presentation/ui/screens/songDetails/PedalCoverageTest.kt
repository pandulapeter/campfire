/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.song_details_next_page
import com.pandulapeter.campfire.presentation.resources.song_details_next_section
import com.pandulapeter.campfire.presentation.resources.song_details_previous_page
import com.pandulapeter.campfire.presentation.resources.song_details_previous_section
import com.pandulapeter.campfire.presentation.resources.song_details_scroll_down
import com.pandulapeter.campfire.presentation.resources.song_details_scroll_up
import com.pandulapeter.campfire.presentation.ui.components.EDGE_FADE_SIZE
import com.pandulapeter.campfire.screenshots.AppTestScope
import com.pandulapeter.campfire.screenshots.TestWindow
import com.pandulapeter.campfire.screenshots.runAppTest
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The promise the song details screen is built around: with nothing but a page turner pedal, which sends Up and Down,
 * every line of a song can be read, at any window size and text size. Each test opens a song and walks it with the
 * pedal to its end and back, at the default and at the largest text size, and asks of every line whether it was on
 * screen whole at a rest, going down and going up, and whether the walks end on the first and the last line.
 *
 * It asks only what a reader sees: where the lines are, from semantics, against the song's viewport, and where that
 * is not enough, the pixels. It never asks about the rows, cuts, or columns the screen lays the song out in, so another
 * layout can pass it only by keeping the promise. A chorus repeats its lines word for word, so a line is told apart by
 * how many times its words have come before it, not by what it says.
 */
@OptIn(ExperimentalTestApi::class)
internal class PedalCoverageTest {

    @Test
    fun `every line of House of the Rising Sun is read with the pedal on a phone`() = walkAtEveryTextSize(TestWindow.PHONE, RISING_SUN)

    @Test
    fun `every line of House of the Rising Sun is read with the pedal on a phone on its side`() = walkAtEveryTextSize(TestWindow.PHONE_ON_ITS_SIDE, RISING_SUN)

    @Test
    fun `every line of House of the Rising Sun is read with the pedal on a desktop`() = walkAtEveryTextSize(DESKTOP, RISING_SUN)

    @Test
    fun `every line of a long song with tabs and a grid is read with the pedal on a phone`() = walkAtEveryTextSize(TestWindow.PHONE, LONG_SONG)

    @Test
    fun `every line of a long song with tabs and a grid is read with the pedal on a phone on its side`() = walkAtEveryTextSize(TestWindow.PHONE_ON_ITS_SIDE, LONG_SONG)

    @Test
    fun `every line of a long song with tabs and a grid is read with the pedal on a desktop`() = walkAtEveryTextSize(DESKTOP, LONG_SONG)

    private fun walkAtEveryTextSize(window: TestWindow, song: TestSong) = listOf(UserPreferences.DEFAULT_FONT_SCALE, UserPreferences.MAX_FONT_SCALE).forEach { fontScale ->
        runAppTest(window = window, songs = listOfNotNull(song.file), fontScale = fontScale) {
            openSong(song.title)
            val case = "${song.title} in a ${window.width.value.toInt()} x ${window.height.value.toInt()} dp window at text size $fontScale"
            val down = walk(Key.DirectionDown, "$case, going down")
            val up = walk(Key.DirectionUp, "$case, going up")
            // Looking closer scrolls the song, so it waits until both walks are over.
            val reader = Reader(this, case)
            assertEveryLineRead(reader, down, "$case, going down")
            assertEveryLineRead(reader, up, "$case, going up")
            down.last().let { end -> assertTrue(reader.reads(end, end.pieces.maxBy { it.bounds.bottom }), "$case: the walk down does not end on the last line.") }
            up.last().let { start -> assertTrue(reader.reads(start, start.pieces.minBy { it.bounds.top }), "$case: the walk up does not end on the first line.") }
        }
    }

    private fun AppTestScope.openSong(title: String) {
        onNode(hasScrollToNodeAction()).performScrollToNode(hasText(title))
        onNode(hasText(title) and hasClickAction()).performClick()
        waitUntil(timeoutMillis = OPEN_TIMEOUT_MILLIS) { songViewport() != null }
        waitForIdle()
    }

    /** Presses [key] until a press moves the song no further, keeping what was on screen at every rest, the first one included. */
    private fun AppTestScope.walk(key: Key, case: String): List<Rest> {
        val rests = mutableListOf(rest())
        while (true) {
            if (rests.size > MAX_STEPS) fail("$case: the song still moves after $MAX_STEPS presses.")
            onRoot().performKeyInput { pressKey(key) }
            waitForIdle()
            val rest = rest()
            if (rest.scroll == rests.last().scroll) return rests
            rests += rest
        }
    }

    private fun AppTestScope.rest(): Rest {
        val viewport = checkNotNull(songViewport())
        val bounds = viewport.unclippedBounds()
        val scroll = viewport.scroll()
        // Everything is kept where it is in the song, the top of the viewport at no scroll being nought, so that a line
        // is compared with itself at every rest.
        val songTop = bounds.top - scroll
        val image = onRoot().captureToImage()
        val edge = IntArray(image.width * EDGE_ROWS).also { image.readPixels(it, startX = 0, startY = bounds.top.toInt(), width = image.width, height = EDGE_ROWS) }
        return Rest(
            pieces = songPieces(viewport, songTop),
            viewport = Rect(bounds.left, scroll, bounds.right, scroll + bounds.height),
            buttons = onAllNodes(isStepButton(), useUnmergedTree = true).fetchSemanticsNodes().map { it.drawnBounds().translate(0f, -songTop) },
            scroll = scroll,
            edge = Pixels(edge, image.width, top = scroll),
        )
    }

    private fun assertEveryLineRead(reader: Reader, rests: List<Rest>, case: String) {
        val pieces = rests.first().pieces
        val unread = pieces.filter { piece ->
            val placed = rests.map { rest -> rest to rest.pieces.single { it.key == piece.key } }
            !placed.any { (rest, placedPiece) -> reader.reads(rest, placedPiece) } && !reader.readsInPages(placed)
        }
        assertTrue(
            unread.isEmpty(),
            "$case: ${unread.size} of ${pieces.size} lines are never on screen whole at a rest.\n" + unread.joinToString("\n") { piece ->
                "\"${piece.text}\" at ${piece.bounds.top}..${piece.bounds.bottom}, rests at " + rests.joinToString { "${it.scroll}" }
            },
        )
    }
}

/** A line of text, or a block drawn rather than written (tablature, chord diagrams), where it is in the song. */
private data class Piece(val text: String, val occurrence: Int, val bounds: Rect) {
    val key get() = text to occurrence
}

/**
 * What was on screen where a press of the pedal left the song: its [viewport] in the song's coordinates, scrolled to
 * [scroll], and the pixels of its [edge], the top of the viewport that fades what is scrolled under it.
 */
private class Rest(val pieces: List<Piece>, val viewport: Rect, val buttons: List<Rect>, val scroll: Float, val edge: Pixels)

/** Rows of a window's pixels, [width] wide, the first of them [top] down the song. */
private class Pixels(private val argb: IntArray, private val width: Int, val top: Float) {
    val rows get() = argb.size / width

    /** The pixel [x] across and [y] down the song, the nearest of these rows and columns where it is a fraction past them. */
    operator fun get(x: Int, y: Int) = Color(argb[(y - top.toInt()).coerceIn(0, rows - 1) * width + x.coerceIn(0, width - 1)])
}

/**
 * Tells whether a piece of the song could be read at a rest: all of it on screen and none of it under the step buttons
 * or faded by the top edge. Where the piece's bounds alone do not tell - the end of its line reaches past the screen,
 * it is in the faded edge, or it is taller than the screen - it looks at the pixels of the piece itself, which it sees
 * by scrolling the song to where that part of the piece is on screen away from the edge. How far the edge fades is no
 * semantics' to say: it fades as far as the song is scrolled into the row it rests in, so a row rested on is not faded.
 */
@OptIn(ExperimentalTestApi::class)
private class Reader(private val test: AppTestScope, private val case: String) {

    /** What is drawn of each piece, relative to its top left corner, null where nothing is. */
    private val inks = mutableMapOf<Pair<String, Int>, Rect?>()

    /** The pixels of each piece short enough to be seen whole, which every question about it is answered from. */
    private val looks = mutableMapOf<Pair<String, Int>, PiecePixels>()

    fun reads(rest: Rest, piece: Piece): Boolean {
        if (rest.shows(piece.bounds) && (rest.scroll == 0f || piece.bounds.top >= rest.edgeBottom)) return true
        if (!rest.viewport.overlaps(piece.bounds) || piece.bounds.height > rest.viewport.height - EDGE_FADE_SIZE.value) return false
        val ink = inks.getOrPut(piece.key) { inkOf(piece, 0f, piece.bounds.height) }?.translate(piece.bounds.left, piece.bounds.top) ?: return true
        return rest.shows(ink) && (rest.scroll == 0f || ink.top >= rest.edgeBottom || isUnfaded(rest, piece, ink))
    }

    /**
     * Whether a piece taller than what can be read at once, a run of tablature at a large text size, is read a page at
     * a time across the rests it is [placed] at: every row of it with anything drawn in it on screen and unfaded at one.
     */
    fun readsInPages(placed: List<Pair<Rest, Piece>>): Boolean {
        val piece = placed.first().second
        if (placed.any { (rest, placedPiece) -> placedPiece.bounds.height <= rest.viewport.height - EDGE_FADE_SIZE.value }) return false
        val seen = placed.filter { (rest, placedPiece) -> rest.viewport.overlaps(placedPiece.bounds) }.map { (rest, placedPiece) ->
            val top = if (rest.scroll == 0f || isUnfaded(rest, placedPiece, placedPiece.bounds)) rest.viewport.top else rest.edgeBottom
            top - placedPiece.bounds.top to rest.viewport.bottom - placedPiece.bounds.top
        }
        return unseenSpans(seen, piece.bounds.height).all { (start, end) -> inkOf(piece, start, end) == null }
    }

    /** Whether what of [area] of [piece] is in the edge at [rest] looks there as it does away from the edge. */
    private fun isUnfaded(rest: Rest, piece: Piece, area: Rect): Boolean {
        val edge = rest.viewport.copy(bottom = rest.edgeBottom).intersect(area)
        if (edge.isEmpty) return true
        val pixels = lookWhole(piece) ?: look(piece, edge.top - piece.bounds.top, edge.bottom - piece.bounds.top)
        return rows(edge.top, edge.bottom).all { y ->
            columns(edge).all { x -> pixels[x - piece.bounds.left, y - piece.bounds.top]?.let { rest.edge[x, y].isCloseTo(it, FADED_DIFFERENCE) } ?: true }
        }
    }

    /**
     * The bounds of what is drawn of [piece] from [top] to [bottom] below its top, relative to its top left corner, or
     * null where nothing is: anything that differs from the color most of it has.
     */
    private fun inkOf(piece: Piece, top: Float, bottom: Float): Rect? {
        val pixels = lookWhole(piece) ?: look(piece, top, bottom)
        val area = Rect(0f, top, piece.bounds.width, bottom)
        val background = rows(area.top, area.bottom).flatMap { y -> columns(area).mapNotNull { x -> pixels[x.toFloat(), y.toFloat()] } }
            .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: return null
        var ink: Rect? = null
        rows(area.top, area.bottom).forEach { y ->
            val drawn = columns(area).filter { x -> pixels[x.toFloat(), y.toFloat()]?.isCloseTo(background, INK_DIFFERENCE) == false }
            // A rule across all of it is the divider between two rows, which may be drawn over the end of a line.
            if (drawn.isEmpty() || drawn.size >= RULE_COVERAGE * columns(area).count()) return@forEach
            val row = Rect(drawn.first().toFloat(), y.toFloat(), drawn.last() + 1f, y + 1f)
            ink = ink?.let { Rect(minOf(it.left, row.left), minOf(it.top, row.top), maxOf(it.right, row.right), maxOf(it.bottom, row.bottom)) } ?: row
        }
        return ink
    }

    private fun lookWhole(piece: Piece) = if (piece.bounds.height > shortestViewport() - EDGE_FADE_SIZE.value) {
        null
    } else {
        looks.getOrPut(piece.key) { look(piece, 0f, piece.bounds.height) }
    }

    private fun shortestViewport() = checkNotNull(test.songViewport()).unclippedBounds().height

    /**
     * The pixels of [piece] from [top] to [bottom] below its top, seen on screen below the faded edge. A short window
     * hides its app bar as the song is scrolled down, which the pedal's steps never do, and lays the song out again for
     * the room that leaves, so the piece is found by its words wherever that puts it, and the song is brought there from
     * further below than the bar is tall, which shows the bar again.
     */
    private fun look(piece: Piece, top: Float, bottom: Float): PiecePixels {
        repeat(MAX_LOOK_ATTEMPTS) {
            val placed = placedPiece(piece)
            val maxValue = checkNotNull(test.songViewport()).config[SemanticsProperties.VerticalScrollAxisRange].maxValue()
            val target = floor(placed.bounds.top + top - EDGE_FADE_SIZE.value).coerceIn(0f, maxValue)
            scrollTo((target + APPROACH).coerceAtMost(maxValue))
            val node = scrollTo(target)
            val bounds = node.unclippedBounds()
            val shown = placedPiece(piece)
            // Nothing fades the top edge of a song that is not scrolled at all.
            val edge = if (node.scroll() == 0f) 0f else EDGE_FADE_SIZE.value
            if (shown.bounds.top + top >= node.scroll() + edge && shown.bounds.top + bottom <= node.scroll() + bounds.height) {
                val image = test.onRoot().captureToImage()
                val songTop = bounds.top - node.scroll()
                return PiecePixels(
                    pixels = Pixels(image.rows(bounds.top.toInt(), bounds.height.toInt()), image.width, top = node.scroll()),
                    left = shown.bounds.left,
                    top = shown.bounds.top,
                    hidden = test.onAllNodes(test.isStepControl(), useUnmergedTree = true).fetchSemanticsNodes().map { it.drawnBounds().translate(0f, -songTop) },
                )
            }
        }
        fail("$case: \"${piece.text}\" cannot be brought on screen from $top to $bottom below its top.")
    }

    private fun placedPiece(piece: Piece): Piece {
        val node = checkNotNull(test.songViewport())
        return songPieces(node, node.unclippedBounds().top - node.scroll()).singleOrNull { it.key == piece.key }
            ?: fail("$case: \"${piece.text}\" is gone once the song is scrolled by other means than the pedal.")
    }

    private fun scrollTo(target: Float): SemanticsNode {
        var node = checkNotNull(test.songViewport())
        // A scroll asked of the semantics can stop short of where it was asked to go.
        repeat(MAX_SCROLL_ATTEMPTS) {
            if (node.scroll() == target) return node
            test.onNode(SemanticsMatcher("is the song") { it.id == node.id }).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, target - node.scroll()) }
            test.waitForIdle()
            node = checkNotNull(test.songViewport())
        }
        return node
    }

    /**
     * The [pixels] of the song with a piece's top left corner at [left] and [top], read relative to that corner, none
     * where the step buttons or the dots between them are drawn over it.
     */
    private class PiecePixels(private val pixels: Pixels, private val left: Float, private val top: Float, private val hidden: List<Rect>) {
        operator fun get(x: Float, y: Float): Color? {
            val songX = floor(left + x)
            val songY = floor(top + y)
            return if (hidden.any { songX + 1 > it.left && songX < it.right && songY + 1 > it.top && songY < it.bottom }) null else pixels[songX.toInt(), songY.toInt()]
        }
    }
}

private class TestSong(val title: String, val file: String?)

/** Where the top edge of the viewport stops fading what is under it. */
private val Rest.edgeBottom get() = viewport.top + EDGE_FADE_SIZE.value

/** Whether all of [bounds] is on screen at this rest, clear of the step buttons. */
private fun Rest.shows(bounds: Rect) = bounds.top >= viewport.top - TOLERANCE && bounds.bottom <= viewport.bottom + TOLERANCE &&
    bounds.left >= viewport.left - TOLERANCE && bounds.right <= viewport.right + TOLERANCE && buttons.none { it.overlaps(bounds) }

private fun rows(top: Float, bottom: Float) = floor(top).toInt() until ceil(bottom).toInt()

private fun columns(bounds: Rect) = floor(bounds.left).toInt() until ceil(bounds.right).toInt()

private fun ImageBitmap.rows(top: Int, count: Int) = IntArray(width * count).also { readPixels(it, startX = 0, startY = top, width = width, height = count) }

/** The stretches of something [height] tall that none of the [spans] seen of it, each relative to its top, reached. */
private fun unseenSpans(spans: List<Pair<Float, Float>>, height: Float): List<Pair<Float, Float>> {
    val unseen = mutableListOf<Pair<Float, Float>>()
    var covered = 0f
    spans.sortedBy { it.first }.forEach { (start, end) ->
        if (start > covered + TOLERANCE) unseen += covered to minOf(start, height)
        covered = maxOf(covered, end)
    }
    if (covered < height - TOLERANCE) unseen += covered to height
    return unseen.filter { (start, end) -> end > start }
}

/** The buttons that step through the song, which are drawn over its end edge. */
private fun AppTestScope.isStepButton() = listOf(
    Res.string.song_details_previous_page,
    Res.string.song_details_next_page,
    Res.string.song_details_previous_section,
    Res.string.song_details_next_section,
    Res.string.song_details_scroll_up,
    Res.string.song_details_scroll_down,
).map { hasContentDescription(string(it)) }.reduce { matcher, other -> matcher or other }

/** The step buttons and the dots between them. */
private fun AppTestScope.isStepControl() = isStepButton() or SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)

private val isSongViewport = SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)

private fun AppTestScope.songViewport() = onAllNodes(isSongViewport, useUnmergedTree = true).fetchSemanticsNodes().firstOrNull { node ->
    node.children.isNotEmpty() && songPieces(node, 0f).isNotEmpty()
}

private fun SemanticsNode.scroll() = config[SemanticsProperties.VerticalScrollAxisRange].value()

/**
 * The pieces of the song in [viewport], [songTop] being where the top of the song is on screen, in the order they are
 * read: every line of every text, and every block drawn rather than written. A card drawn behind a chorus has a shape
 * and nothing to read, and an icon has a role. A line reaches from its top, where chords are drawn over it, to as far
 * as its letters can reach below it, not over the leading its line height adds, where a row's divider may be drawn.
 */
private fun songPieces(viewport: SemanticsNode, songTop: Float): List<Piece> {
    val pieces = mutableListOf<Pair<String, Rect>>()
    fun visit(node: SemanticsNode) {
        val bounds = node.unclippedBounds().translate(0f, -songTop)
        val layout = node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.let { action ->
            mutableListOf<TextLayoutResult>().also { action(it) }.firstOrNull()
        }
        when {
            layout != null -> {
                val descent = layout.descent()
                (0 until layout.lineCount).forEach { line ->
                    pieces += layout.layoutInput.text.text.substring(layout.getLineStart(line), layout.getLineEnd(line)) to Rect(
                        left = bounds.left,
                        top = bounds.top + layout.getLineTop(line).coerceAtLeast(0f),
                        right = bounds.right,
                        bottom = bounds.top + minOf(layout.getLineBottom(line), layout.getLineBaseline(line) + descent, bounds.height),
                    )
                }
            }
            node.children.isEmpty() && bounds.height > 0f && node.config.getOrNull(SemanticsProperties.Role) == null &&
                node.config.getOrNull(SemanticsProperties.Shape) == null ->
                pieces += node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString().orEmpty() to bounds
            else -> node.children.forEach(::visit)
        }
    }
    viewport.children.forEach(::visit)
    val occurrences = mutableMapOf<String, Int>()
    return pieces.map { (text, bounds) -> Piece(text = text, occurrence = occurrences.merge(text, 1, Int::plus)!!, bounds = bounds) }
}

/** How far the letters of this text's style can reach below their baseline, without the leading its line height adds. */
private fun TextLayoutResult.descent() = descents.getOrPut(layoutInput.style) {
    TextMeasurer(layoutInput.fontFamilyResolver, layoutInput.density, layoutInput.layoutDirection)
        .measure(DESCENT_SAMPLE, layoutInput.style.copy(lineHeight = TextUnit.Unspecified))
        .let { it.size.height - it.firstBaseline }
}

private val descents = mutableMapOf<TextStyle, Float>()

/**
 * What a step control covers: a button's icon is described, but the button around it is drawn over the song too, and
 * its shadow around that, which read as part of a line would make the line reach under the button.
 */
private fun SemanticsNode.drawnBounds() = unclippedBounds().let { bounds ->
    if (config.getOrNull(SemanticsProperties.ProgressBarRangeInfo) != null) {
        bounds
    } else {
        Rect(center = bounds.center, radius = STEP_BUTTON_SIZE.value / 2 + STEP_BUTTON_SHADOW.value)
    }
}

private fun SemanticsNode.unclippedBounds() = Rect(offset = positionInRoot, size = Size(size.width.toFloat(), size.height.toFloat()))

private fun Color.isCloseTo(other: Color, tolerance: Float) = abs(red - other.red) <= tolerance && abs(green - other.green) <= tolerance && abs(blue - other.blue) <= tolerance

private val RISING_SUN = TestSong(title = "House of the Rising Sun", file = null)
private val LONG_SONG = TestSong(title = "The Long Road North", file = "campfire_tests-the_long_road_north.cho")
private val DESKTOP = TestWindow(width = 1280.dp, height = 800.dp)
private val EDGE_ROWS = EDGE_FADE_SIZE.value.toInt()

/** How far a step button's shadow reaches past the button: a little further than its elevation's, measured. */
private val STEP_BUTTON_SHADOW = 8.dp
private const val MAX_STEPS = 500
private const val MAX_SCROLL_ATTEMPTS = 10
private const val MAX_LOOK_ATTEMPTS = 3

/** Further than a short window's app bar is tall. */
private const val APPROACH = 200f
private const val OPEN_TIMEOUT_MILLIS = 10_000L
private const val TOLERANCE = 0.5f
private const val DESCENT_SAMPLE = "gjpqy"

/** How far a pixel of the edge may be from the same pixel unfaded: further than antialiasing moves it, much less than a fade. */
private const val FADED_DIFFERENCE = 0.2f

/** How far a pixel may be from the color around it and still count as nothing drawn. */
private const val INK_DIFFERENCE = 0.1f

/** How much of a row of pixels a rule drawn across it covers, which no line of text does. */
private const val RULE_COVERAGE = 0.9f
