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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.domain.api.library.ScreenData
import com.pandulapeter.campfire.presentation.localization.AppLocale
import com.pandulapeter.campfire.presentation.localization.currentLanguage
import com.pandulapeter.campfire.presentation.localization.layoutDirection
import com.pandulapeter.campfire.presentation.localization.pluralTemplate
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.setlists_countdown_days_ago
import com.pandulapeter.campfire.presentation.resources.setlists_countdown_in_days
import com.pandulapeter.campfire.presentation.resources.setlists_countdown_today
import com.pandulapeter.campfire.presentation.resources.setlists_countdown_tomorrow
import com.pandulapeter.campfire.presentation.resources.setlists_countdown_yesterday
import com.pandulapeter.campfire.presentation.resources.songs_random_picks
import com.pandulapeter.campfire.presentation.resources.songs_random_picks_refresh
import com.pandulapeter.campfire.presentation.resources.widget_choose_setlist
import com.pandulapeter.campfire.presentation.resources.widget_empty_library
import com.pandulapeter.campfire.presentation.resources.widget_next_gig
import com.pandulapeter.campfire.presentation.resources.widget_next_gig_description
import com.pandulapeter.campfire.presentation.resources.widget_no_upcoming_setlist
import com.pandulapeter.campfire.presentation.resources.widget_setlist_missing
import com.pandulapeter.campfire.presentation.resources.widget_setlists_off
import com.pandulapeter.campfire.presentation.resources.widget_song_count
import com.pandulapeter.campfire.presentation.resources.widget_songs_off
import com.pandulapeter.campfire.presentation.ui.navigation.FeatureSwitches
import com.pandulapeter.campfire.presentation.ui.platform.calendarLocale
import com.pandulapeter.campfire.presentation.ui.state.PreferencesController
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import org.jetbrains.compose.resources.PluralStringResource

/**
 * Hands [onChanged] the home screen widgets' snapshot whenever what it holds changes: the library, the Songs and
 * Setlists switches, the app's language, or its theme. Only a library read whole counts, never a first read's batches or a failed
 * read standing in as empty, which would show the widgets a library shrinking and growing back. A change is handed over
 * once it has held for a moment, since a sync run or an import changes the library file by file; an unchanged snapshot is
 * not handed over again.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WidgetSnapshotEffect(
    screenData: StateFlow<DataState<ScreenData>>,
    preferencesController: PreferencesController,
    onChanged: (WidgetSnapshot) -> Unit,
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
    val snapshot = remember(library, features, strings, theme, locale) {
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
        )
    }
    val latestOnChanged by rememberUpdatedState(onChanged)
    LaunchedEffect(snapshot) {
        delay(SETTLE_MILLISECONDS)
        println("Widget snapshot published: ${snapshot.songs.size} songs, ${snapshot.setlists.size} setlists")
        latestOnChanged(snapshot)
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
        songCount = remember(locale) { widgetPlural(Res.plurals.widget_song_count, locale) },
    )
}

/** [key] read for every count [WidgetSnapshot.Plural] holds, by the language's own rule. */
internal fun widgetPlural(key: PluralStringResource, locale: AppLocale) =
    WidgetSnapshot.Plural(byCount = List(WidgetSnapshot.Plural.SIZE) { count -> pluralTemplate(key, count, locale) })

private const val SETTLE_MILLISECONDS = 1000L
