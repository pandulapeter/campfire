/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songView

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import com.pandulapeter.campfire.data.model.importing.ImportedFile
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.close
import com.pandulapeter.campfire.presentation.resources.picture_source_photos
import com.pandulapeter.campfire.presentation.resources.song_image
import com.pandulapeter.campfire.presentation.resources.song_image_add
import com.pandulapeter.campfire.presentation.resources.song_image_not_found
import com.pandulapeter.campfire.presentation.ui.platform.ImpersonatedPlatform
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.openSong
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.songViewport
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.unclippedBounds
import com.pandulapeter.campfire.screenshots.AppTestScope
import com.pandulapeter.campfire.screenshots.TestWindow
import com.pandulapeter.campfire.screenshots.runAppTest
import com.pandulapeter.campfire.screenshots.tap
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pictures in a song (`{image}`): drawn in their room from the song's first layout, fitted to the column and the page,
 * opened over the whole window, and a missing one standing in as a placeholder whose Add image stores a picked file.
 * The pictures are made here, named by their content as the library names them.
 */
@OptIn(ExperimentalTestApi::class)
internal class SongImagesTest {

    @Test
    fun `a song that is only a picture shows it fitted to the column, and a long press opens it over the window`() {
        val picture = png(width = 800, height = 600)
        runAppTest(window = TestWindow.PHONE, libraryFiles = library(song("Only A Picture", "{image: src=${picture.name}}"), picture)) {
            openSong("Only A Picture")
            val bounds = pictureBounds()
            assertTrue(bounds.width > TestWindow.PHONE.width.value / 2 && bounds.right <= TestWindow.PHONE.width.value, "The picture is $bounds.")
            assertEquals(800f / 600f, bounds.width / bounds.height, ASPECT_TOLERANCE)
            // A tap is the song's, to step it, and opens nothing.
            tap(hasContentDescription(string(Res.string.song_image)) and hasClickAction())
            assertTrue(onAllNodes(hasContentDescription(string(Res.string.close))).fetchSemanticsNodes().isEmpty(), "A tap opened the viewer.")
            onNode(hasContentDescription(string(Res.string.song_image)) and hasClickAction()).performTouchInput { longClick() }
            waitForIdle()
            tap(hasContentDescription(string(Res.string.close)) and hasClickAction())
            assertTrue(onAllNodes(hasContentDescription(string(Res.string.close))).fetchSemanticsNodes().isEmpty(), "The viewer did not close.")
        }
    }

    @Test
    fun `a picture between two sections stands between them`() {
        val picture = png(width = 300, height = 100)
        val text = "{start_of_verse}\n$FIRST_VERSE\n{end_of_verse}\n{image: src=${picture.name}}\n{start_of_verse}\n$SECOND_VERSE\n{end_of_verse}"
        runAppTest(window = TestWindow.PHONE, libraryFiles = library(song("Between Verses", text), picture)) {
            openSong("Between Verses")
            val bounds = pictureBounds()
            assertTrue(textBounds(FIRST_VERSE).bottom <= bounds.top, "The first verse ends under the picture's top.")
            assertTrue(bounds.bottom <= textBounds(SECOND_VERSE).top, "The second verse starts above the picture's bottom.")
        }
    }

    @Test
    fun `a portrait photo on a phone is scaled down to a page of its own, seen whole`() {
        val picture = png(width = 1200, height = 3000)
        runAppTest(window = TestWindow.PHONE, libraryFiles = library(song("A Tall Photo", "{image: src=${picture.name}}"), picture)) {
            openSong("A Tall Photo")
            val bounds = pictureBounds()
            val viewport = checkNotNull(songViewport()).unclippedBounds()
            assertTrue(bounds.top >= viewport.top && bounds.bottom <= viewport.bottom, "The picture is $bounds, past the page's $viewport.")
            assertEquals(1200f / 3000f, bounds.width / bounds.height, ASPECT_TOLERANCE)
        }
    }

    @Test
    fun `a portrait photo between verses on a desktop is seen whole wherever the columns put it`() {
        val picture = png(width = 1200, height = 3000)
        val text = "{start_of_verse}\n$FIRST_VERSE\n{end_of_verse}\n{image: src=${picture.name}}\n{start_of_verse}\n$SECOND_VERSE\n{end_of_verse}"
        runAppTest(window = TestWindow.DESKTOP, platform = ImpersonatedPlatform.WINDOWS, libraryFiles = library(song("A Tall Photo", text), picture)) {
            openSong("A Tall Photo")
            val bounds = pictureBounds()
            val viewport = checkNotNull(songViewport()).unclippedBounds()
            assertTrue(bounds.top >= viewport.top && bounds.bottom <= viewport.bottom, "The picture is $bounds, past the page's $viewport.")
        }
    }

    @Test
    fun `a missing picture is a placeholder whose Add image stores a picked file under the song`() {
        val picked = png(width = 400, height = 300)
        runAppTest(
            window = TestWindow.PHONE,
            libraryFiles = library(song("A Missing Photo", "{image: src=photo.jpg}")),
            pickedFiles = listOf(ImportedFile("holiday.png", picked.bytes)),
        ) {
            openSong("A Missing Photo")
            onNode(hasText(text(Res.string.song_image_not_found, "photo.jpg")), useUnmergedTree = true).assertExists()
            tap(hasText(string(Res.string.song_image_add)) and hasClickAction())
            val songFile = songsFolder.resolve(fileName("A Missing Photo"))
            waitForLibrary { songFile.readText().contains(picked.name) }
            assertTrue(library.resolve("files/${picked.name}").readBytes().contentEquals(picked.bytes), "The picked file was not stored by its content.")
            waitUntil(timeoutMillis = SHOW_TIMEOUT_MILLIS) { onAllNodes(hasContentDescription(string(Res.string.song_image)), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        }
    }

    @Test
    fun `where there is a photo library Add image asks whether to pick from Photos or Files and stores the photo picked`() {
        val photo = png(width = 300, height = 400)
        runAppTest(
            window = TestWindow.PHONE,
            libraryFiles = library(song("A Missing Photo", "{image: src=photo.jpg}")),
            pickedFiles = listOf(ImportedFile("holiday.png", png(width = 400, height = 300).bytes)),
            pickedPhotos = listOf(ImportedFile("IMG_0001.png", photo.bytes)),
        ) {
            openSong("A Missing Photo")
            tap(hasText(string(Res.string.song_image_add)) and hasClickAction())
            tap(hasText(string(Res.string.picture_source_photos)) and hasClickAction())
            val songFile = songsFolder.resolve(fileName("A Missing Photo"))
            waitForLibrary { songFile.readText().contains(photo.name) }
            assertTrue(library.resolve("files/${photo.name}").readBytes().contentEquals(photo.bytes), "The picked photo was not stored by its content.")
        }
    }

    private fun AppTestScope.pictureBounds(): Rect = onAllNodes(hasContentDescription(string(Res.string.song_image)), useUnmergedTree = true)
        .fetchSemanticsNodes().first().unclippedBounds()

    private fun AppTestScope.textBounds(text: String): Rect = onAllNodes(hasText(text, substring = true), useUnmergedTree = true)
        .fetchSemanticsNodes().first().unclippedBounds()

    /** A picture made here, and the name the library keeps it under: 16 hex digits of its SHA-256 and its extension. */
    private class Picture(val bytes: ByteArray) {
        val name = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }.take(16) + ".png"
    }

    /** A PNG [width] by [height] pixels, a gradient so that it compresses into a file of some size, as a photo does. */
    private fun png(width: Int, height: Int): Picture {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until height) for (x in 0 until width) image.setRGB(x, y, Color(x * 255 / width, y * 255 / height, 128).rgb)
        return Picture(ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray())
    }

    private fun fileName(title: String) = "campfire_tests-" + title.lowercase().replace(' ', '_') + ".cho"

    private fun song(title: String, body: String) = fileName(title) to "{title: $title}\n{artist: Campfire Tests}\n$body\n"

    private fun library(song: Pair<String, String>, vararg pictures: Picture) =
        mapOf("songs/${song.first}" to song.second.encodeToByteArray()) + pictures.associate { "files/${it.name}" to it.bytes }

    private fun assertEquals(expected: Float, actual: Float, tolerance: Float) =
        assertTrue(abs(expected - actual) <= tolerance, "Expected a ratio of $expected, but it was $actual.")

    private companion object {
        const val FIRST_VERSE = "The first verse stands above the picture"
        const val SECOND_VERSE = "The second verse stands under it"
        const val ASPECT_TOLERANCE = 0.03f
        const val SHOW_TIMEOUT_MILLIS = 10_000L
    }
}
