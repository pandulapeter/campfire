/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro

import com.pandulapeter.campfire.chordpro.model.ChordInstrument
import com.pandulapeter.campfire.chordpro.model.ChordVoicing
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * Draws every shape of the tables into `CAMPFIRE_CHORD_QA_DIR`, where that is set, one sheet per instrument. A test can
 * say a shape sounds the right chord; only a player looking at it can say it is the shape they expect, which is what
 * this is for. Without the variable it does nothing.
 */
class ChordVoicingContactSheetTest {

    @Test
    fun `draw the tables`() {
        val directory = System.getenv("CAMPFIRE_CHORD_QA_DIR")?.takeIf { it.isNotBlank() }?.let(::File) ?: return
        directory.mkdirs()
        mapOf(ChordInstrument.GUITAR to ChordVoicingTables.guitar, ChordInstrument.UKULELE to ChordVoicingTables.ukulele).forEach { (instrument, lines) ->
            val shapes = lines.map { ChordProDefinitions.read(it) as ChordProDefinitions.Reading.Shape }
            val rows = (shapes.size + COLUMNS - 1) / COLUMNS
            val image = BufferedImage(COLUMNS * CELL, rows * CELL, BufferedImage.TYPE_INT_RGB)
            val graphics = image.createGraphics()
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            graphics.color = Color.WHITE
            graphics.fillRect(0, 0, image.width, image.height)
            shapes.forEachIndexed { index, shape ->
                val frets = (shape.voicing as ChordVoicing.Fretted).frets
                val fingers = shape.voicing.fingers
                val left = (index % COLUMNS) * CELL + 30
                val top = (index / COLUMNS) * CELL + 50
                val strings = frets.size
                val gap = 100 / (strings - 1)
                val base = ChordVoicings.baseFret(frets)
                graphics.color = Color.BLACK
                graphics.font = Font(Font.SANS_SERIF, Font.BOLD, 18)
                graphics.drawString(shape.name, left, top - 28)
                graphics.font = Font(Font.SANS_SERIF, Font.PLAIN, 12)
                graphics.stroke = BasicStroke(if (base == 1) 4f else 1f)
                graphics.drawLine(left, top, left + gap * (strings - 1), top)
                graphics.stroke = BasicStroke(1f)
                if (base > 1) graphics.drawString("$base", left - 24, top + 18)
                (1..FRETS).forEach { fret -> graphics.drawLine(left, top + fret * FRET, left + gap * (strings - 1), top + fret * FRET) }
                (0 until strings).forEach { string ->
                    val x = left + string * gap
                    graphics.drawLine(x, top, x, top + FRETS * FRET)
                    when (val fret = frets[string]) {
                        null -> graphics.drawString("×", x - 4, top - 6)
                        0 -> graphics.drawOval(x - 5, top - 16, 10, 10)
                        else -> {
                            val y = top + (fret - base) * FRET + FRET / 2
                            graphics.fillOval(x - 8, y - 8, 16, 16)
                            fingers?.getOrNull(string)?.takeIf { it > 0 }?.let { finger ->
                                graphics.color = Color.WHITE
                                graphics.drawString("$finger", x - 3, y + 5)
                                graphics.color = Color.BLACK
                            }
                        }
                    }
                }
            }
            graphics.dispose()
            ImageIO.write(image, "png", File(directory, "chord-shapes-${instrument.id}.png"))
        }
    }

    private companion object {
        const val COLUMNS = 10
        const val CELL = 170
        const val FRETS = 5
        const val FRET = 22
    }
}
