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

import com.pandulapeter.campfire.presentation.ui.songView.model.ScaleSuggestions
import com.pandulapeter.campfire.presentation.ui.songView.firstSection.SongPlayingControls
import com.pandulapeter.campfire.presentation.ui.songView.firstSection.SongPlayingTools
import com.pandulapeter.campfire.presentation.ui.songView.model.CardKey
import com.pandulapeter.campfire.presentation.ui.songView.model.ChordDiagrams
import com.pandulapeter.campfire.presentation.ui.songView.model.FoldedRuns
import com.pandulapeter.campfire.presentation.ui.songView.model.RenderSection
import com.pandulapeter.campfire.presentation.ui.songView.model.SongUnits
import com.pandulapeter.campfire.presentation.ui.songView.model.UnitKey
import com.pandulapeter.campfire.presentation.ui.songInfo.SongInfoEditing
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.animateBounds
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.TextStyle
import com.pandulapeter.campfire.chordpro.chords.ChordNotation
import com.pandulapeter.campfire.presentation.ui.songLayout.DefaultSectionLabels

/**
 * The children of [SongLyrics]' [SongSectionsLayout], in the order its measure slices them by: the chunks of the
 * sections, then the cards behind them. The dividers between the pages are drawn by the layout itself.
 *
 * A composable of its own rather than the layout's content lambda, which captures what [SongLyrics] computes and so is
 * a new lambda on every composition: [SongLyrics] is composed again on every frame of an app bar collapsing, the
 * metronome panel moving or a navigation rail animating, and the whole song would be composed again with it. Every
 * parameter here is remembered by [SongLyrics] or its callers, so it keeps its identity over those frames and this
 * skips; a lambda or an object built in [SongLyrics]' body would make it compose again on every one of them.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun LookaheadScope.SongSectionChildren(
    sections: List<RenderSection>,
    units: SongUnits,
    unitKeys: List<UnitKey>,
    cardKeys: List<CardKey>,
    animations: List<SectionAnimation>,
    sectionGlides: SectionGlides?,
    sectionMotion: SectionMotion,
    isResizing: Boolean,
    isTextScaling: Boolean,
    notation: ChordNotation,
    isSongInfoShown: Boolean,
    songInfoEditing: SongInfoEditing?,
    playingControls: SongPlayingControls?,
    playingTools: SongPlayingTools?,
    chordDiagrams: ChordDiagrams?,
    scaleSuggestions: ScaleSuggestions?,
    tuningVisibility: MutableTransitionState<Boolean>,
    chordLayouts: ChordCellLayouts?,
    onChordFoldToggled: (() -> Unit)?,
    hasChordFoldBeenToggled: Boolean,
    hasScaleFoldBeenToggled: Boolean,
    headerStyle: TextStyle,
    lyricsStyle: TextStyle,
    chordStyle: TextStyle,
    textMeasurements: SongTextMeasurements,
    foldedRuns: FoldedRuns?,
    defaultLabels: DefaultSectionLabels,
    fontScale: Float,
) {
    // Whether a chunk or a card is placed by animateBounds: not while the layout follows a change that keeps coming or a
    // navigation transition, not where it is too tall for it (see maxAnimatedSectionHeight), and not while it is still
    // gliding from a change that kept coming, since the spring would start at the place the glide has not reached yet.
    fun Modifier.movedBy(key: Any, animation: SectionAnimation): Modifier {
        val isGliding = sectionGlides?.isGliding(key) == true
        val isHeldStill = sectionMotion != SectionMotion.SPRING || isResizing
        return if (isHeldStill || animation.isTooTallToAnimate || isGliding) {
            this
        } else {
            animateBounds(this@SongSectionChildren).layoutId(AnimatedSectionLayoutId)
        }
    }
    units.itemRanges.forEachIndexed { unit, items ->
        val section = sections[units.unitSections[unit]]
        key(unitKeys[unit]) {
            // Each chunk is read as a whole and in the order the song declares, whatever column it was put in: the reading
            // order is otherwise worked out from the geometry, line by line across the page, which with two columns reads
            // the first line of each, then the second line of each.
            val unitModifier = Modifier.movedBy(unitKeys[unit], animations[unit]).semantics {
                isTraversalGroup = true
                traversalIndex = unit.toFloat()
            }
            SongSectionItem(
                modifier = unitModifier,
                section = section,
                items = items,
                notation = notation,
                isSongInfoShown = isSongInfoShown,
                songInfoEditing = songInfoEditing,
                playingControls = playingControls,
                playingTools = playingTools,
                animatesControls = !isResizing && (sectionMotion == SectionMotion.SPRING || sectionMotion == SectionMotion.GLIDE && !isTextScaling),
                chordDiagrams = chordDiagrams,
                scaleSuggestions = scaleSuggestions,
                tuningVisibility = tuningVisibility,
                chordLayouts = chordLayouts,
                onChordFoldToggled = onChordFoldToggled,
                isChordsSectionFadingIn = hasChordFoldBeenToggled,
                isScalesSectionFadingIn = hasScaleFoldBeenToggled,
                headerStyle = headerStyle,
                lyricsStyle = lyricsStyle,
                chordStyle = chordStyle,
                textMeasurements = textMeasurements,
                foldedRuns = foldedRuns,
                defaultLabels = defaultLabels,
                fontScale = fontScale,
            )
        }
    }
    // The cards the pieces of the sections on a card are drawn on, as many as each could be cut into; the ones a layout
    // has no piece for stay unmeasured.
    SectionCards(cardKeys = cardKeys) { card, cardKey -> Modifier.movedBy(cardKey, animations[units.unitSections.size + card]) }
}
