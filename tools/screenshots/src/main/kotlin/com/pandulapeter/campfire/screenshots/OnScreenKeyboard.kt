/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.screenshots

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The on-screen keyboard of a clip that types: the desktop build the clips are drawn from has none, so a shot raises
 * this one by [height] (reported to the app as the platform's keyboard inset, which moves what sits above it) and
 * lights [pressedKey] while a finger is on it. Generic rather than any platform's, like the video's devices.
 */
internal class OnScreenKeyboard {
    var height by mutableStateOf(0.dp)
    var pressedKey by mutableStateOf<Char?>(null)

    companion object {
        /** Four rows of keys and, under them, the room a phone keeps for its home indicator. */
        val FULL_HEIGHT = 300.dp
    }
}

private val ROWS = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
private val BACKGROUND = Color(0xFF221F28)
private val KEY = Color(0xFF4A4653)
private val SPECIAL_KEY = Color(0xFF34313B)
private val PRESSED_KEY = Color(0xFF8C8698)
private val KEY_SHAPE = RoundedCornerShape(6.dp)

/**
 * The keyboard as tall as [OnScreenKeyboard.height] lets it be, along the bottom of the screen: drawn at its full height
 * from its top edge, so that it slides up from under the screen's edge as the height grows rather than being squeezed.
 */
@Composable
internal fun OnScreenKeyboardView(keyboard: OnScreenKeyboard, appearance: ChromeAppearance) {
    if (keyboard.height <= 0.dp) return
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Box(Modifier.fillMaxWidth().height(keyboard.height).clipToBounds()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .requiredHeight(OnScreenKeyboard.FULL_HEIGHT)
                    .align(Alignment.TopCenter)
                    .background(BACKGROUND)
                    .padding(start = 4.dp, end = 4.dp, top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                KeyRow { ROWS[0].forEach { Letter(it, keyboard.pressedKey, appearance) } }
                KeyRow {
                    Spacer(Modifier.weight(0.5f))
                    ROWS[1].forEach { Letter(it, keyboard.pressedKey, appearance) }
                    Spacer(Modifier.weight(0.5f))
                }
                KeyRow {
                    SpecialKey(weight = 1.35f) { Canvas(Modifier.size(18.dp)) { drawPath(shiftArrow(size.width, size.height), appearance.content, style = Stroke(1.6.dp.toPx(), join = StrokeJoin.Round)) } }
                    Spacer(Modifier.weight(0.15f))
                    ROWS[2].forEach { Letter(it, keyboard.pressedKey, appearance) }
                    Spacer(Modifier.weight(0.15f))
                    SpecialKey(weight = 1.35f) { Canvas(Modifier.size(width = 22.dp, height = 16.dp)) { drawPath(deleteKey(size.width, size.height), appearance.content, style = Stroke(1.6.dp.toPx(), join = StrokeJoin.Round)) } }
                }
                KeyRow {
                    SpecialKey(weight = 2.5f) { KeyLabel("123", appearance, 16) }
                    Key(weight = 5f, color = KEY) { KeyLabel("space", appearance, 16) }
                    SpecialKey(weight = 2.5f) { KeyLabel("search", appearance, 16) }
                }
            }
        }
    }
}

@Composable
private fun KeyRow(content: @Composable RowScope.() -> Unit) = Row(
    modifier = Modifier.fillMaxWidth().height(KEY_HEIGHT),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    content = content,
)

@Composable
private fun RowScope.Letter(letter: Char, pressedKey: Char?, appearance: ChromeAppearance) =
    Key(weight = 1f, color = if (letter == pressedKey) PRESSED_KEY else KEY) { KeyLabel(letter.toString(), appearance, 24) }

@Composable
private fun RowScope.SpecialKey(weight: Float, content: @Composable () -> Unit) = Key(weight = weight, color = SPECIAL_KEY, content = content)

@Composable
private fun RowScope.Key(weight: Float, color: Color, content: @Composable () -> Unit) = Box(
    modifier = Modifier.weight(weight).height(KEY_HEIGHT).background(color, KEY_SHAPE),
    contentAlignment = Alignment.Center,
) {
    content()
}

@Composable
private fun KeyLabel(text: String, appearance: ChromeAppearance, size: Int) = BasicText(
    text = text,
    style = TextStyle(
        color = appearance.content,
        fontFamily = appearance.fontFamily,
        fontSize = size.sp,
        fontWeight = FontWeight.Normal,
        textAlign = TextAlign.Center,
    ),
)

/** An upward arrow with a stem, the shift key's, drawn rather than set in a font that may not have the glyph. */
private fun shiftArrow(width: Float, height: Float) = Path().apply {
    moveTo(width / 2, 0f)
    lineTo(width, height * 0.5f)
    lineTo(width * 0.72f, height * 0.5f)
    lineTo(width * 0.72f, height)
    lineTo(width * 0.28f, height)
    lineTo(width * 0.28f, height * 0.5f)
    lineTo(0f, height * 0.5f)
    close()
}

/** The delete key's label pointing left, with its cross. */
private fun deleteKey(width: Float, height: Float) = Path().apply {
    moveTo(0f, height / 2)
    lineTo(width * 0.3f, 0f)
    lineTo(width, 0f)
    lineTo(width, height)
    lineTo(width * 0.3f, height)
    close()
    val center = Offset(width * 0.64f, height / 2)
    val arm = height * 0.2f
    moveTo(center.x - arm, center.y - arm)
    lineTo(center.x + arm, center.y + arm)
    moveTo(center.x + arm, center.y - arm)
    lineTo(center.x - arm, center.y + arm)
}

private val KEY_HEIGHT: Dp = 46.dp
