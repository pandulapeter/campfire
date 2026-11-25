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

import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.data.model.song.SongLanguage
import com.pandulapeter.campfire.domain.api.catalogue.GetCoverArtUseCase
import com.pandulapeter.campfire.domain.api.library.ScreenData
import com.pandulapeter.campfire.presentation.localization.AppLocale
import com.pandulapeter.campfire.presentation.localization.currentLanguage
import com.pandulapeter.campfire.presentation.localization.layoutDirection
import com.pandulapeter.campfire.presentation.localization.pluralTemplate
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.done
import com.pandulapeter.campfire.presentation.resources.setlists_countdown_days_ago
import com.pandulapeter.campfire.presentation.resources.setlists_countdown_in_days
import com.pandulapeter.campfire.presentation.resources.setlists_countdown_today
import com.pandulapeter.campfire.presentation.resources.setlists_countdown_tomorrow
import com.pandulapeter.campfire.presentation.resources.setlists_countdown_yesterday
import com.pandulapeter.campfire.presentation.resources.songs_languages
import com.pandulapeter.campfire.presentation.resources.songs_languages_unknown
import com.pandulapeter.campfire.presentation.resources.songs_new_song_title
import com.pandulapeter.campfire.presentation.resources.songs_random_picks
import com.pandulapeter.campfire.presentation.resources.songs_random_picks_refresh
import com.pandulapeter.campfire.presentation.resources.songs_tags
import com.pandulapeter.campfire.presentation.resources.widget_choose_setlist
import com.pandulapeter.campfire.presentation.resources.widget_empty_library
import com.pandulapeter.campfire.presentation.resources.widget_next_gig
import com.pandulapeter.campfire.presentation.resources.widget_next_gig_description
import com.pandulapeter.campfire.presentation.resources.widget_no_matching_songs
import com.pandulapeter.campfire.presentation.resources.widget_no_upcoming_setlist
import com.pandulapeter.campfire.presentation.resources.widget_random_picks_count
import com.pandulapeter.campfire.presentation.resources.widget_random_picks_filter_description
import com.pandulapeter.campfire.presentation.resources.widget_set_up
import com.pandulapeter.campfire.presentation.resources.widget_setlist_missing
import com.pandulapeter.campfire.presentation.resources.widget_setlists_off
import com.pandulapeter.campfire.presentation.resources.widget_songs_off
import com.pandulapeter.campfire.presentation.ui.components.labels.namedLanguageLabel
import com.pandulapeter.campfire.presentation.ui.navigation.FeatureSwitches
import com.pandulapeter.campfire.presentation.ui.platform.calendarLocale
import com.pandulapeter.campfire.presentation.ui.state.PreferencesController
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import org.jetbrains.compose.resources.PluralStringResource

/**
 * Writes the home screen widgets' snapshot into [files] whenever what it holds changes: the library, the Songs and
 * Setlists switches, the Cover art switch, the app's language, or its theme. Only a library read whole counts, never a
 * first read's batches or a failed read standing in as empty, which would show the widgets a library shrinking and growing
 * back. A change is handed over once it has held for a moment, since a sync run or an import changes the library file by
 * file; an unchanged snapshot is not handed over again. Its songs' covers come through [getCoverArt] ([WidgetPublisher]),
 * only for the widgets [placement] says are placed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WidgetSnapshotEffect(
    screenData: StateFlow<DataState<ScreenData>>,
    preferencesController: PreferencesController,
    files: WidgetFiles,
    placement: WidgetPlacement,
    getCoverArt: GetCoverArtUseCase,
) {
    val state by screenData.collectAsStateWithLifecycle()
    val userPreferences by preferencesController.userPreferences.collectAsStateWithLifecycle()
    val preferences = userPreferences ?: return
    val library = (state as? DataState.Idle)?.data?.takeIf { it.isWholeLibrary } ?: return
    val strings = widgetStrings()
    val languageCode = currentLanguage.value.code
    val locale = remember(languageCode) { calendarLocale(languageCode) }
    val dateFormatter = remember { DatePickerDefaults.dateFormatter() }
    // The switches alone, not every preference: a transposition or a counted song open would rebuild the whole library's
    // snapshot otherwise.
    val features = FeatureSwitches.of(preferences)
    val theme = rememberWidgetTheme(preferences)
    val unknownLanguage = stringResource(Res.string.songs_languages_unknown)
    val snapshot = remember(library, features, strings, theme, locale, unknownLanguage) {
        widgetSnapshot(
            screenData = library,
            features = features,
            strings = strings,
            theme = theme,
            formatDate = { day ->
                // The formatter counts in UTC midnights, as the setlist dialog's does: any other zone would print the day
                // before or after it on one side of UTC.
                dateFormatter.formatDate(day.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds(), locale) ?: day.toString()
            },
            languageName = { code -> if (code == SongLanguage.UNKNOWN) unknownLanguage else namedLanguageLabel(code = code, appLanguageCode = languageCode) },
        )
    }
    val coverAddresses = remember(library, preferences.isCoverArtEnabled) {
        widgetCoverAddresses(songs = library.unfilteredSongs, isCoverArtEnabled = preferences.isCoverArtEnabled)
    }
    val publisher = remember(files, getCoverArt) {
        WidgetPublisher(files = files, getCoverArt = getCoverArt::invoke, makeThumbnail = ::widgetCoverThumbnail)
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    // A newer snapshot cancels the covers still being made for the one before it: what was written stays, and the next
    // publish names it. Published again at every return to the front and whenever the platform says the widgets changed,
    // which is how a widget placed while the app was away gets its covers; an unchanged snapshot is not written again.
    LaunchedEffect(snapshot, coverAddresses, placement, lifecycleOwner) {
        delay(SETTLE_MILLISECONDS)
        println("Widget snapshot published: ${snapshot.songs.size} songs, ${snapshot.setlists.size} setlists, ${coverAddresses.size} covers")
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            placement.changes.onStart { emit(Unit) }.collectLatest {
                publisher.publish(snapshot = snapshot, allCoverAddresses = coverAddresses, placed = placement.placedWidgets())
            }
        }
    }
}

@Composable
private fun widgetStrings(): WidgetSnapshot.Strings {
    val locale = currentLanguage.value
    return WidgetSnapshot.Strings(
        isRightToLeft = locale.layoutDirection == LayoutDirection.Rtl,
        randomPicks = stringResource(Res.string.songs_random_picks),
        drawNewPicks = stringResource(Res.string.songs_random_picks_refresh),
        emptyLibrary = stringResource(Res.string.widget_empty_library),
        songsOff = stringResource(Res.string.widget_songs_off),
        setlistsOff = stringResource(Res.string.widget_setlists_off),
        nextGig = stringResource(Res.string.widget_next_gig),
        nextGigDescription = stringResource(Res.string.widget_next_gig_description),
        noUpcomingSetlist = stringResource(Res.string.widget_no_upcoming_setlist),
        setlistMissing = stringResource(Res.string.widget_setlist_missing),
        chooseSetlist = stringResource(Res.string.widget_choose_setlist),
        today = stringResource(Res.string.setlists_countdown_today),
        tomorrow = stringResource(Res.string.setlists_countdown_tomorrow),
        yesterday = stringResource(Res.string.setlists_countdown_yesterday),
        inDays = remember(locale) { widgetPlural(Res.plurals.setlists_countdown_in_days, locale) },
        daysAgo = remember(locale) { widgetPlural(Res.plurals.setlists_countdown_days_ago, locale) },
        noMatchingSongs = stringResource(Res.string.widget_no_matching_songs),
        setUpWidget = stringResource(Res.string.widget_set_up),
        done = stringResource(Res.string.done),
        title = stringResource(Res.string.songs_new_song_title),
        tags = stringResource(Res.string.songs_tags),
        languages = stringResource(Res.string.songs_languages),
        randomPicksFilterDescription = stringResource(Res.string.widget_random_picks_filter_description),
        pickCount = stringResource(Res.string.widget_random_picks_count),
    )
}

/** [key] read for every count [WidgetSnapshot.Plural] holds, by the language's own rule. */
internal fun widgetPlural(key: PluralStringResource, locale: AppLocale) =
    WidgetSnapshot.Plural(byCount = List(WidgetSnapshot.Plural.SIZE) { count -> pluralTemplate(key, count, locale) })

private const val SETTLE_MILLISECONDS = 1000L
