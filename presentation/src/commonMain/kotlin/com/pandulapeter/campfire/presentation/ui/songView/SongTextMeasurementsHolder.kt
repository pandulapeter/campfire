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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalLocaleList
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * Everything a width [SongTextMeasurements] measures depends on: what a `TextMeasurer` is built with (the font resolver,
 * compared by identity, one per window and on Android per activity; the locales, since a language switch changes line
 * breaking and shaping; the density and the layout direction) and the three styles, which carry the text size.
 */
internal data class SongTextMeasurementsKey(
    val fontFamilyResolver: FontFamily.Resolver,
    val localeList: LocaleList,
    val density: Density,
    val layoutDirection: LayoutDirection,
    val lyricsStyle: TextStyle,
    val chordStyle: TextStyle,
    val annotationStyle: TextStyle,
)

/**
 * The [SongTextMeasurements] of the last few [SongTextMeasurementsKey]s, app-wide, so that a song opened after another at
 * the same text size starts with every chord name and lyric fragment any page measured before rather than measuring them
 * again on its push frame. Main thread only: it is read from composition, and what it hands out is used from composition,
 * measurement, and drawing, never from the notation's painting, which has a measurer of its own.
 *
 * It keeps [MAX_KEPT_STYLE_SETS] of them, least recently asked for leaving first: a pinch makes a new style set every
 * frame, and only the size it settles at is worth keeping. Each keeps its own bounded maps (4,096 chord, annotation, and
 * grid layouts, each a native paragraph off Android, and 64 tab runs), the bounds the editor's preview, which shares this,
 * already needed; in use a library's chord names are a few hundred, a few MB at most. An Android activity recreated by a
 * configuration change the app does not handle brings a new font resolver, so the old entries can never be asked for
 * again and simply age out (a resolver holds the application's context, not the activity's).
 */
internal object SongTextMeasurementsHolder {

    /** In the order they were last asked for, the least recent first: one asked for is taken out and put back last. */
    private val kept = LinkedHashMap<SongTextMeasurementsKey, SongTextMeasurements>()

    /** The measurements of [key], the same instance for an equal key while it is kept. */
    fun of(key: SongTextMeasurementsKey): SongTextMeasurements = (kept.remove(key) ?: measurementsOf(key)).also {
        kept[key] = it
        if (kept.size > MAX_KEPT_STYLE_SETS) kept.remove(kept.keys.first())
    }

    private fun measurementsOf(key: SongTextMeasurementsKey) = SongTextMeasurements(
        // Everything measured through it is kept by whoever asked for it (see [SongTextMeasurements] and [TabRows]), so a
        // cache of its own would only hold every layout a second time.
        textMeasurer = TextMeasurer(key.fontFamilyResolver, key.localeList, key.density, key.layoutDirection, cacheSize = 0),
        lyricsStyle = key.lyricsStyle,
        chordStyle = key.chordStyle,
        annotationStyle = key.annotationStyle,
    )
}

/**
 * The app-wide [SongTextMeasurements] of the three styles under what the screen measures text with here: exactly what
 * `rememberTextMeasurer` reads, the layout direction the screen's rather than a song's own.
 */
@Composable
internal fun rememberSongTextMeasurements(lyricsStyle: TextStyle, chordStyle: TextStyle, annotationStyle: TextStyle): SongTextMeasurements {
    val key = SongTextMeasurementsKey(
        fontFamilyResolver = LocalFontFamilyResolver.current,
        localeList = LocalLocaleList.current,
        density = LocalDensity.current,
        layoutDirection = LocalLayoutDirection.current,
        lyricsStyle = lyricsStyle,
        chordStyle = chordStyle,
        annotationStyle = annotationStyle,
    )
    return remember(key) { SongTextMeasurementsHolder.of(key) }
}

internal const val MAX_KEPT_STYLE_SETS = 3
