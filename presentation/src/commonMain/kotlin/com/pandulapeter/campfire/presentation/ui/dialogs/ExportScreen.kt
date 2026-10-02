/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.animateBounds
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.pandulapeter.campfire.data.model.domain.PrintSettings
import com.pandulapeter.campfire.presentation.localization.currentLanguage
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.cancel
import com.pandulapeter.campfire.presentation.resources.close
import com.pandulapeter.campfire.presentation.resources.ic_add
import com.pandulapeter.campfire.presentation.resources.ic_clear
import com.pandulapeter.campfire.presentation.resources.ic_next
import com.pandulapeter.campfire.presentation.resources.ic_previous
import com.pandulapeter.campfire.presentation.resources.ic_save
import com.pandulapeter.campfire.presentation.resources.ic_setlists
import com.pandulapeter.campfire.presentation.resources.ic_share
import com.pandulapeter.campfire.presentation.resources.ic_songs
import com.pandulapeter.campfire.presentation.resources.ic_subtract
import com.pandulapeter.campfire.presentation.resources.ic_text_decrease
import com.pandulapeter.campfire.presentation.resources.ic_text_increase
import com.pandulapeter.campfire.presentation.resources.print_a4
import com.pandulapeter.campfire.presentation.resources.print_chords
import com.pandulapeter.campfire.presentation.resources.print_columns
import com.pandulapeter.campfire.presentation.resources.print_comments
import com.pandulapeter.campfire.presentation.resources.print_export
import com.pandulapeter.campfire.presentation.resources.print_font_size
import com.pandulapeter.campfire.presentation.resources.print_format
import com.pandulapeter.campfire.presentation.resources.print_format_chordpro
import com.pandulapeter.campfire.presentation.resources.print_format_chordpro_song_description
import com.pandulapeter.campfire.presentation.resources.print_format_pdf
import com.pandulapeter.campfire.presentation.resources.print_format_pdf_description
import com.pandulapeter.campfire.presentation.resources.print_format_zip
import com.pandulapeter.campfire.presentation.resources.print_format_zip_description
import com.pandulapeter.campfire.presentation.resources.print_landscape
import com.pandulapeter.campfire.presentation.resources.print_letter
import com.pandulapeter.campfire.presentation.resources.print_load_failed
import com.pandulapeter.campfire.presentation.resources.print_margin
import com.pandulapeter.campfire.presentation.resources.print_margin_decrease
import com.pandulapeter.campfire.presentation.resources.print_margin_increase
import com.pandulapeter.campfire.presentation.resources.print_margins
import com.pandulapeter.campfire.presentation.resources.print_metadata
import com.pandulapeter.campfire.presentation.resources.print_missing
import com.pandulapeter.campfire.presentation.resources.print_new_page
import com.pandulapeter.campfire.presentation.resources.print_next
import com.pandulapeter.campfire.presentation.resources.print_no_songs
import com.pandulapeter.campfire.presentation.resources.print_overview
import com.pandulapeter.campfire.presentation.resources.print_page
import com.pandulapeter.campfire.presentation.resources.print_page_numbers
import com.pandulapeter.campfire.presentation.resources.print_paper
import com.pandulapeter.campfire.presentation.resources.print_portrait
import com.pandulapeter.campfire.presentation.resources.print_previous
import com.pandulapeter.campfire.presentation.resources.print_running_order
import com.pandulapeter.campfire.presentation.resources.print_save
import com.pandulapeter.campfire.presentation.resources.print_select_all
import com.pandulapeter.campfire.presentation.resources.print_setlist_content
import com.pandulapeter.campfire.presentation.resources.print_setlist_empty
import com.pandulapeter.campfire.presentation.resources.print_share
import com.pandulapeter.campfire.presentation.resources.print_song_sheets
import com.pandulapeter.campfire.presentation.resources.print_songs
import com.pandulapeter.campfire.presentation.resources.print_time
import com.pandulapeter.campfire.presentation.resources.print_zip_contents
import com.pandulapeter.campfire.presentation.resources.print_zip_manifest
import com.pandulapeter.campfire.presentation.resources.print_zip_manifest_description
import com.pandulapeter.campfire.presentation.resources.print_zip_missing
import com.pandulapeter.campfire.presentation.resources.print_zip_song_description
import com.pandulapeter.campfire.presentation.resources.retry
import com.pandulapeter.campfire.presentation.resources.song_details_text_size
import com.pandulapeter.campfire.presentation.resources.song_details_text_size_decrease
import com.pandulapeter.campfire.presentation.resources.song_details_text_size_increase
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_capo
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_key
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_tempo
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.CampfireTopAppBar
import com.pandulapeter.campfire.presentation.ui.components.CheckboxListItem
import com.pandulapeter.campfire.presentation.ui.components.DelayedLoadingIndicator
import com.pandulapeter.campfire.presentation.ui.components.SegmentedChoice
import com.pandulapeter.campfire.presentation.ui.components.SettingsSectionTitle
import com.pandulapeter.campfire.presentation.ui.components.fadingTopEdge
import com.pandulapeter.campfire.presentation.ui.components.fadingUnderStartOverlay
import com.pandulapeter.campfire.presentation.ui.components.fadingVerticalEdges
import com.pandulapeter.campfire.presentation.ui.components.textResource
import com.pandulapeter.campfire.presentation.ui.contentEdges
import com.pandulapeter.campfire.presentation.ui.platform.LocalFilePicker
import com.pandulapeter.campfire.presentation.ui.platform.calendarLocale
import com.pandulapeter.campfire.presentation.ui.platform.isDesktopPlatform
import com.pandulapeter.campfire.presentation.ui.platform.isLaunchScreenWholeStartup
import com.pandulapeter.campfire.presentation.ui.platform.verticalWheelNotches
import com.pandulapeter.campfire.presentation.ui.print.PrintDocument
import com.pandulapeter.campfire.presentation.ui.print.PrintLabels
import com.pandulapeter.campfire.presentation.ui.print.PrintPage
import com.pandulapeter.campfire.presentation.ui.print.PrintRenderer
import com.pandulapeter.campfire.presentation.ui.print.PrintSong
import com.pandulapeter.campfire.presentation.ui.print.PrintSource
import com.pandulapeter.campfire.presentation.ui.print.layoutPrintDocument
import com.pandulapeter.campfire.presentation.ui.print.pdfFileName
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.Stepper
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.rememberDefaultSectionLabels
import com.pandulapeter.campfire.presentation.ui.slideFractionSpec
import com.pandulapeter.campfire.presentation.ui.theme.LocalMonospaceFontFamily
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import org.jetbrains.compose.resources.painterResource

/**
 * How far [ExportHost] has dealt the export screen over the app, which the screens under it follow the way they
 * follow a destination being pushed over them: 0 while it is away, 1 while it covers them.
 */
@Stable
internal class ExportTransition {

    val progress = Animatable(0f)
}

/**
 * Puts [ExportScreen] over the whole app for as long as the view model's dialog is an export, dealing it in from
 * the right edge and taking it away again with the spring, the direction and the background slide of the app's own
 * navigation, and following a predictive back gesture as a destination's pop does. It is drawn inside the app's own
 * layout rather than in a window of its own, so that the snackbars of a failed export still show over it; it is still a
 * dialog to the view model, which is what lets the desktop's Escape, the web's Back and another dialog put up in its
 * place close it the way they close a sheet.
 */
@Composable
internal fun ExportHost(
    viewModel: CampfireViewModel,
    transition: ExportTransition,
) {
    val visibleDialog by viewModel.visibleDialog.collectAsStateWithLifecycle()
    val target = visibleDialog as? CampfireViewModel.DialogType.Export
    // The export on screen, which outlives the dialog for as long as the screen takes to slide away. One export
    // replacing another is the same screen with what it reads starting over, not a second one sliding in.
    var shown by remember { mutableStateOf<CampfireViewModel.DialogType.Export?>(null) }
    // The export a back gesture has just closed: the gesture ends a moment before the dialog does, and the screen
    // slides on away from wherever the finger left it rather than starting back towards the open position first.
    var closedByGesture by remember { mutableStateOf<CampfireViewModel.DialogType.Export?>(null) }
    val backGesture = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
    // Composed after the app, so that the dispatcher reaches this handler before the back stack's, and registered
    // whether or not the screen is up, since a handler that comes and goes changes the order the dispatcher picks in.
    NavigationBackHandler(
        state = backGesture,
        isBackEnabled = target != null,
        onBackCompleted = {
            closedByGesture = target
            target?.let(viewModel::dismissSheet)
        },
    )
    val spec = MaterialTheme.motionScheme.slideFractionSpec()
    LaunchedEffect(transition) {
        snapshotFlow {
            val gestureProgress = (backGesture.transitionState as? NavigationEventTransitionState.InProgress)
                ?.takeIf { it.direction == NavigationEventTransitionState.TRANSITIONING_BACK }
                ?.latestEvent
                ?.progress
            (visibleDialog as? CampfireViewModel.DialogType.Export)?.takeIf { it !== closedByGesture } to gestureProgress
        }.collectLatest { (open, gestureProgress) ->
            when {
                open == null -> {
                    transition.progress.animateTo(0f, spec)
                    shown = null
                    closedByGesture = null
                }
                // The finger moves the screen itself, the way it moves a destination, rather than a spring chasing it.
                gestureProgress != null -> transition.progress.snapTo(1f - gestureProgress)
                else -> {
                    shown = open
                    transition.progress.animateTo(1f, spec)
                }
            }
        }
    }
    shown?.let { dialog ->
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationX = ((1f - transition.progress.value) * size.width).roundToInt().toFloat() },
        ) {
            ExportScreen(viewModel, dialog)
        }
    }
}

/**
 * The Export screen of a song or a setlist: the options on one side, the preview of what they make on the other, and Save
 * as its floating action button (Share, where the platform has it, in the app bar). The first option is the format: a
 * PDF, whose preview and file are drawn from one layout by one [PrintRenderer], so what is saved is the preview at print
 * resolution, or the library's own files - a song's ChordPro file, or a setlist's zip of its manifest and its songs as
 * ChordPro files - which take no option but the songs a setlist's zip holds, and are previewed as what is written. That
 * one choice is what the menus offer as their single export entry, rather than one entry for every format and every way
 * out of the app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportScreen(
    viewModel: CampfireViewModel,
    dialog: CampfireViewModel.DialogType.Export,
) {
    val preferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val initialSettings = preferences?.printSettings ?: return
    val state = rememberExportState(dialog) { viewModel.pendingPrintSettings.value ?: initialSettings }
    val exportProgress by viewModel.pdfExportProgress.collectAsStateWithLifecycle()
    val isFileTransferActive by viewModel.isFileTransferActive.collectAsStateWithLifecycle()
    val filePicker = LocalFilePicker.current
    val fontResolver = LocalFontFamilyResolver.current
    val fontFamily = LocalMonospaceFontFamily.current
    // The face the viewer sets lyrics in, which on the web is the preloaded Inter rather than FontFamily.Default.
    val textFontFamily = MaterialTheme.typography.bodyLarge.fontFamily ?: FontFamily.Default
    fun newRenderer() = PrintRenderer(
        measurer = TextMeasurer(fontResolver, Density(1f), LayoutDirection.Ltr, cacheSize = 256),
        monospaceFontFamily = fontFamily,
        textFontFamily = textFontFamily,
    )
    val renderer = remember(fontResolver, fontFamily, textFontFamily) { newRenderer() }
    val labels = PrintLabels(
        key = stringResource(Res.string.song_editor_insert_key),
        capo = stringResource(Res.string.song_editor_insert_capo),
        tempo = stringResource(Res.string.song_editor_insert_tempo),
        time = stringResource(Res.string.print_time),
        missing = stringResource(Res.string.print_missing),
        sections = rememberDefaultSectionLabels(),
    )
    LaunchedEffect(dialog, state.attempt) {
        state.failed = false
        try {
            state.source = viewModel.preparePrintSource(dialog)
            if (state.selected == null) state.selected = state.source!!.songs.indices.toSet()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            state.failed = true
        }
    }
    // The setlist's day as the setlist dialog shows it, in the app's language. The formatter counts in UTC midnights, so
    // the day goes in as one: any other zone would print the day before or after it on one side of UTC.
    val languageCode = currentLanguage.value.code
    val locale = remember(languageCode) { calendarLocale(languageCode) }
    val dateFormatter = remember { DatePickerDefaults.dateFormatter() }
    val date = dialog.setlist?.date?.let { day ->
        dateFormatter.formatDate(day.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds(), locale) ?: day.toString()
    }
    val chosenSource = remember(state.source, state.selected, date) {
        state.source?.let { source ->
            source.copy(date = date, songs = source.songs.filterIndexed { index, _ -> index in state.selected.orEmpty() })
        }
    }
    // The last document stays on screen while the next one is laid out, so that a step of an option fades from one
    // page to the next instead of blanking the preview to a spinner each time.
    // Retry is keyed too: the source it reads again equals the one that failed to lay out, so without it nothing would.
    val laidOut by produceState<LaidOutDocument?>(null, chosenSource, state.settings, labels, renderer, state.attempt) {
        state.layoutFailed = false
        val input = chosenSource
        val inputSettings = state.settings
        if (input == null || input.songs.isEmpty()) {
            value = null
            return@produceState
        }
        // An export of the library's own files has no pages, and the ones laid out for the PDF are kept for the format being switched back,
        // which then lays out nothing again.
        if (inputSettings.format != PrintSettings.Format.PDF) return@produceState
        if (value.let { it != null && it.source === input && it.settings == inputSettings && it.labels == labels }) return@produceState
        // A burst of steps is one layout, but the first one, with nothing on screen yet, starts at once.
        if (value != null) delay(LAYOUT_DEBOUNCE)
        try {
            val document = withContext(Dispatchers.Default) {
                val measurements = newRenderer()
                layoutPrintDocument(
                    source = input,
                    settings = state.settings,
                    labels = labels,
                    measureText = measurements::width,
                )
            }
            value = LaidOutDocument(
                document = document,
                source = input,
                settings = inputSettings,
                labels = labels,
                generation = (value?.generation ?: 0) + 1,
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            state.layoutFailed = true
        }
    }
    // Only a document laid out from what the screen shows now may be saved: the producer restarts a frame after an option
    // changes, and a Save in that frame would otherwise export the old options. The source is compared by identity,
    // since it is remembered and an equality check would walk every song in it.
    val isCurrent = laidOut.let { it != null && it.source === chosenSource && it.settings == state.settings && it.labels == labels }
    // The screen stays composed while it slides away, after its settings were flushed and its drawing cancelled, so
    // nothing tapped in that moment may change an option or start an export. Equality, since an equal export opened
    // again during the slide is this one.
    val visibleDialog by viewModel.visibleDialog.collectAsStateWithLifecycle()
    val isOpen = visibleDialog == dialog
    val update: (PrintSettings) -> Unit = update@{
        if (!isOpen) return@update
        val normalized = it.normalized()
        if (normalized != state.settings) {
            state.settings = normalized
            viewModel.setPrintSettings(normalized)
        }
    }
    val isFiles = state.settings.format == PrintSettings.Format.FILES
    // The songs a setlist's zip is narrowed to, by the same ticks a PDF is: null where every one of them is chosen, which
    // hands the setlist's manifest out exactly as it is stored.
    val chosenSongFileNames = state.source?.takeIf { it.isSetlist }?.let { source ->
        val selected = state.selected.orEmpty()
        if (source.songs.indices.all { it in selected }) null else source.songs.filterIndexed { index, _ -> index in selected }.mapTo(mutableSetOf()) { it.fileName }
    }
    // A song whose file could not be read has nothing to write, and a setlist's zip wants a song chosen unless it has
    // none at all, in which case its manifest is all there is to share.
    val canExportFiles = state.source.let { source ->
        when {
            source == null -> false
            source.isSetlist -> source.songs.isEmpty() || state.selected.orEmpty().isNotEmpty()
            else -> source.songs.firstOrNull()?.text != null
        }
    }
    val hasPages = laidOut?.document?.pages?.isNotEmpty() == true
    val canSave = isCurrent && hasPages && !isFileTransferActive
    // A tap while the pages are being laid out again for an option just changed is kept until they are, rather than
    // being refused: a floating action button has no disabled look to say it would be, and the layout takes a moment.
    var requestedExport by remember(dialog) { mutableStateOf<ExportRequest?>(null) }
    LaunchedEffect(requestedExport, canSave, hasPages, isFiles) {
        val request = requestedExport ?: return@LaunchedEffect
        // A PDF asked for before the format was switched away is not one to come up once it is switched back.
        if (!hasPages || isFiles) {
            requestedExport = null
        } else if (canSave) {
            requestedExport = null
            laidOut?.document?.let { snapshot ->
                // From the screen's own settings, which the saved preferences may not have caught up with yet.
                viewModel.exportPdf(
                    filePicker = filePicker,
                    fileName = pdfFileName(state.source!!, state.settings),
                    dialog = dialog,
                    pageCount = snapshot.pages.size,
                    isShare = request == ExportRequest.SHARE,
                ) { onPage ->
                    newRenderer().pdf(snapshot, state.source!!.title, onPage = onPage)
                }
            }
        }
    }
    // Any import or export running would make the view model ignore the tap, so it is not even asked.
    val requestExport = { request: ExportRequest ->
        if (isOpen && !isFileTransferActive) {
            if (isFiles) viewModel.exportFiles(filePicker, dialog, chosenSongFileNames, isShare = request == ExportRequest.SHARE) else requestedExport = request
        }
    }
    val close = { viewModel.dismissSheet(dialog) }
    LaunchedEffect(dialog) { viewModel.exportSaved.collect { if (it == dialog) close() } }
    val content = when {
        state.failed || state.layoutFailed && !isFiles -> PrintScreenContent.FAILED
        state.source == null -> PrintScreenContent.LOADING
        else -> PrintScreenContent.LOADED
    }
    // Drawn in the same window as the screen under it, so it does not take the focus by being there, and a key would
    // otherwise go on reaching that screen - a pedal stepping the song being exported. A bare focus target draws no
    // indication and does not hand the focus on to the first button, so it is taken on every platform, whatever the
    // screen shows; the preview moves it further in where it takes it for its own arrows.
    val rootFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { rootFocus.requestFocus() }
    // A Surface, so that nothing of the app under it can be pressed through it.
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(rootFocus)
            .focusTarget(),
        color = MaterialTheme.colorScheme.background,
    ) {
        val bottomInset = WindowInsets.contentEdges.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()
        var saveButtonSize by remember { mutableStateOf(DpSize.Zero) }
        val density = LocalDensity.current
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                PrintTopAppBar(
                    title = stringResource(Res.string.print_export),
                    subtitle = dialog.setlist?.title ?: dialog.song?.let { songLabel(it) }.orEmpty(),
                    canShare = filePicker.canShare && content == PrintScreenContent.LOADED && exportProgress == null &&
                        if (isFiles) canExportFiles else hasPages,
                    onShare = { requestExport(ExportRequest.SHARE) },
                    onClose = close,
                )
                BoxWithConstraints(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .windowInsetsPadding(WindowInsets.contentEdges.only(WindowInsetsSides.Horizontal)),
                ) {
                    val bodyWidth = maxWidth
                    val bodyHeight = maxHeight
                    AnimatedContent(content, Modifier.fillMaxSize(), transitionSpec = { fadeIn() togetherWith fadeOut() }) { shown ->
                        when (shown) {
                            PrintScreenContent.FAILED -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(stringResource(Res.string.print_load_failed))
                                    TextButton(onClick = { state.attempt++ }) { Text(stringResource(Res.string.retry)) }
                                }
                            }
                            PrintScreenContent.LOADING -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                DelayedLoadingIndicator()
                            }
                            PrintScreenContent.LOADED -> if (state.source != null) {
                                val optionsWidth = when {
                                    bodyWidth >= 760.dp && bodyHeight >= 480.dp -> 330.dp
                                    // A phone on its side: too short to stack the two, wide enough to put them side by side.
                                    bodyWidth >= 600.dp -> 300.dp
                                    else -> null
                                }
                                val isSideBySide = optionsWidth != null
                                val options: @Composable (Modifier) -> Unit = { modifier ->
                                    PrintOptions(
                                        modifier = modifier,
                                        source = state.source!!,
                                        settings = state.settings,
                                        selected = state.selected.orEmpty(),
                                        // Stacked under the preview, the list ends under the button, so its last row
                                        // can be scrolled clear of it; beside the preview the button is not over it.
                                        bottomPadding = bottomInset + if (isSideBySide) 8.dp else SAVE_BUTTON_CLEARANCE,
                                        onSelected = { if (isOpen) state.selected = it },
                                        onSettings = update,
                                    )
                                }
                                val preview: @Composable (Modifier) -> Unit = { modifier ->
                                    AnimatedContent(isFiles, modifier, transitionSpec = { fadeIn() togetherWith fadeOut() }) { showsFiles ->
                                        if (showsFiles) {
                                            FilesPreview(
                                                modifier = Modifier.fillMaxSize(),
                                                source = state.source!!,
                                                setlistFileName = dialog.setlist?.fileName,
                                                selected = state.selected.orEmpty(),
                                                // Beside the options the save button floats over the end of this pane.
                                                bottomPadding = if (isSideBySide) bottomInset + SAVE_BUTTON_CLEARANCE else 8.dp,
                                            )
                                        } else {
                                            val emptyMessage = when {
                                                state.source!!.songs.isEmpty() -> stringResource(Res.string.print_setlist_empty)
                                                state.selected.orEmpty().isEmpty() -> stringResource(Res.string.print_no_songs)
                                                else -> null
                                            }
                                            PrintPreview(
                                                modifier = Modifier.fillMaxSize(),
                                                laidOut = laidOut,
                                                isCurrent = isCurrent,
                                                renderer = renderer,
                                                emptyMessage = emptyMessage,
                                                // Beside the options the preview reaches down under the navigation bar and
                                                // the save button floats over it with the page buttons; stacked, it ends
                                                // above the options, which the save button floats over instead.
                                                bottomInset = if (isSideBySide) bottomInset else 0.dp,
                                                areOptionsBelow = !isSideBySide,
                                                saveButtonSize = if (isSideBySide) saveButtonSize else DpSize.Zero,
                                                magnifications = viewModel.printPreviewMagnifications,
                                                page = state.page,
                                                onPageSettled = { state.page = it },
                                                pageView = { state.pageView },
                                                onPageViewChanged = { state.pageView = it },
                                            )
                                        }
                                    }
                                }
                                PrintPanes(
                                    optionsWidth = optionsWidth,
                                    previewHeight = bodyHeight * PHONE_PREVIEW_HEIGHT_FRACTION,
                                    options = options,
                                    preview = preview,
                                )
                            }
                        }
                    }
                }
            }
            SaveButton(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .windowInsetsPadding(WindowInsets.contentEdges.only(WindowInsetsSides.Bottom + WindowInsetsSides.End))
                    .padding(16.dp)
                    .onSizeChanged { saveButtonSize = with(density) { DpSize(it.width.toDp(), it.height.toDp()) } },
                isVisible = content == PrintScreenContent.LOADED && (exportProgress != null || if (isFiles) canExportFiles else hasPages),
                progress = exportProgress,
                onSave = { requestExport(ExportRequest.SAVE) },
                onCancel = viewModel::cancelPdfExport,
            )
        }
    }
}

/**
 * The options beside the preview, [optionsWidth] wide at its start, or under it where that is null, the preview then
 * [previewHeight] tall. Both panes stay where they are in the composition whichever way they are arranged, so the zoom,
 * the scroll of the options and everything else they remember is kept across a window crossing from one arrangement to
 * the other, and they travel to their new places on the spatial spring rather than being laid out again from scratch. A
 * window being resized within one arrangement is followed as it comes, since a spring after every frame of it would only
 * make the panes trail behind the edge being dragged.
 */
@Composable
private fun PrintPanes(
    optionsWidth: Dp?,
    previewHeight: Dp,
    options: @Composable (Modifier) -> Unit,
    preview: @Composable (Modifier) -> Unit,
) {
    val arrangement = updateTransition(targetState = optionsWidth, label = "PrintPanes")
    val motionScheme = MaterialTheme.motionScheme
    // A child animation on the same spring keeps the transition running for as long as the panes travel, which is what
    // tells a change of arrangement from a resize in the frames after the first one.
    arrangement.animateFloat(transitionSpec = { motionScheme.defaultSpatialSpec() }, label = "PrintPanesProgress") {
        it?.value ?: 0f
    }
    val spec = motionScheme.paneBoundsSpec()
    val boundsTransform = remember(arrangement, spec) {
        BoundsTransform { _, _ -> if (arrangement.currentState != arrangement.targetState) spec else snap() }
    }
    LookaheadScope {
        val paneBounds = Modifier.animateBounds(lookaheadScope = this, boundsTransform = boundsTransform)
        // One call of each pane whatever the arrangement, since a call in another branch would be another pane.
        Box(Modifier.fillMaxSize()) {
            options(
                if (optionsWidth != null) {
                    Modifier.width(optionsWidth).fillMaxHeight()
                } else {
                    Modifier.fillMaxSize().padding(top = previewHeight)
                }.then(paneBounds),
            )
            preview(
                if (optionsWidth != null) {
                    Modifier.fillMaxSize().padding(start = optionsWidth)
                } else {
                    Modifier.fillMaxWidth().height(previewHeight)
                }.then(paneBounds),
            )
        }
    }
}

/** The motion scheme's spatial spring for the bounds of a pane, settling within a pixel. */
private fun MotionScheme.paneBoundsSpec() = when (val spec = defaultSpatialSpec<Rect>()) {
    is SpringSpec -> spring(dampingRatio = spec.dampingRatio, stiffness = spec.stiffness, visibilityThreshold = Rect.VisibilityThreshold)
    else -> spec
}

/** What a tap on Save or Share asked for, kept until the pages it is to export are laid out. */
private enum class ExportRequest { SAVE, SHARE }

/** Close, the title and what is exported under it, and Share where the platform has one. */
@Composable
private fun PrintTopAppBar(
    title: String,
    subtitle: String,
    canShare: Boolean,
    onShare: () -> Unit,
    onClose: () -> Unit,
) = CampfireTopAppBar(
    navigationIcon = {
        IconButton(onClick = onClose) {
            Icon(painter = painterResource(Res.drawable.ic_clear), contentDescription = stringResource(Res.string.close))
        }
    },
    title = {
        Column {
            Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    },
    actions = {
        // One Cancel, on the save button, stands for both while the pages are drawn, whichever of the two started it.
        AnimatedVisibility(canShare, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
            IconButton(onClick = onShare) {
                Icon(painter = painterResource(Res.drawable.ic_share), contentDescription = stringResource(Res.string.print_share))
            }
        }
    },
)

/**
 * Save, which while the pages of a PDF are drawn counts them in a ring in place of its icon and cancels; once the picker
 * is up there is nothing to cancel, and a tap waits for it to answer. It leaves while there is nothing to save: no pages,
 * or a song file that could not be read.
 */
@Composable
private fun SaveButton(
    modifier: Modifier,
    isVisible: Boolean,
    progress: CampfireViewModel.PdfExportProgress?,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) = AnimatedVisibility(isVisible, modifier = modifier, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
    ExtendedFloatingActionButton(
        onClick = if (progress != null) onCancel else onSave,
        icon = {
            // Keyed by whether there is any, so that the ring fades out showing the last count rather than an empty one.
            AnimatedContent(progress, transitionSpec = { fadeIn() togetherWith fadeOut() }, contentKey = { it != null }) { shown ->
                if (shown == null) {
                    Icon(painter = painterResource(Res.drawable.ic_save), contentDescription = null)
                } else {
                    CircularProgressIndicator(
                        progress = { if (shown.total == 0) 0f else shown.done.toFloat() / shown.total },
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 3.dp,
                    )
                }
            }
        },
        text = { CrossfadedLabel(stringResource(Res.string.print_save), stringResource(Res.string.cancel), isSecondShown = progress != null) },
    )
}

/** What the screen's area shows, which it fades between; a new document is not one of these, see [PrintPreview]. */
private enum class PrintScreenContent { FAILED, LOADING, LOADED }

/**
 * What the screen keeps while it is open, built by [rememberExportState]. Each field is remembered on its own, as
 * saveable or not as it has to be, rather than the holder being remembered whole, which could not be saved field by
 * field: the selection, the page and how it is zoomed outlive a rotation, while the options, the source and the attempt
 * are read again.
 */
private class ExportState(
    settings: MutableState<PrintSettings>,
    source: MutableState<PrintSource?>,
    failed: MutableState<Boolean>,
    attempt: MutableIntState,
    selected: MutableState<Set<Int>?>,
    page: MutableIntState,
    pageView: MutableState<PageView>,
    layoutFailed: MutableState<Boolean>,
) {
    /** The options the preview is laid out with, ahead of the saved preferences until those catch up. */
    var settings by settings

    /** The snapshot the PDF is made of, null until it has been read. */
    var source by source

    /** Whether reading [source] failed, which the screen offers to try again. */
    var failed by failed

    /** Counts the tries of Retry, which both the read of the source and the layout are keyed by. */
    var attempt by attempt

    /** The songs to export, by their place in [source]; null until the source has been read. */
    var selected by selected

    /** The page of the preview that is open. */
    var page by page

    /** How that page is zoomed and panned, which the preview's pane outlives being laid out again in another place. */
    var pageView by pageView

    /** Whether the last layout failed, which the screen offers to try again the way a failed read is. */
    var layoutFailed by layoutFailed
}

@Composable
private fun rememberExportState(
    dialog: CampfireViewModel.DialogType.Export,
    initialSettings: () -> PrintSettings,
) = ExportState(
    settings = remember(dialog) { mutableStateOf(initialSettings()) },
    source = remember(dialog) { mutableStateOf<PrintSource?>(null) },
    failed = remember(dialog) { mutableStateOf(false) },
    attempt = remember(dialog) { mutableIntStateOf(0) },
    // Saved, or a rotation would put back every song somebody had unticked; null until the source has been read.
    selected = rememberSaveable(dialog, stateSaver = SELECTION_SAVER) { mutableStateOf<Set<Int>?>(null) },
    page = rememberSaveable(dialog) { mutableIntStateOf(0) },
    pageView = rememberSaveable(dialog, stateSaver = PAGE_VIEW_SAVER) { mutableStateOf(PageView()) },
    layoutFailed = remember { mutableStateOf(false) },
)

/**
 * A laid out document together with what it was laid out from, which is what tells a document that stands in for the
 * next one while that is laid out from one that may be exported. [generation] tells one document from the next without
 * comparing them, which would walk every text on every page.
 */
private class LaidOutDocument(
    val document: PrintDocument,
    val source: PrintSource,
    val settings: PrintSettings,
    val labels: PrintLabels,
    val generation: Int,
)

/** Two labels of one button, faded between, the button keeping the size of the wider so that it does not jump. */
@Composable
private fun CrossfadedLabel(
    first: String,
    second: String,
    isSecondShown: Boolean,
) = Box(contentAlignment = Alignment.Center) {
    val secondAlpha by animateFloatAsState(if (isSecondShown) 1f else 0f)
    Text(first, Modifier.alpha(1f - secondAlpha).semantics { if (isSecondShown) invisibleToUser() })
    Text(second, Modifier.alpha(secondAlpha).semantics { if (!isSecondShown) invisibleToUser() })
}

/**
 * The options, in one list that scrolls on its own: the format and what it is good for, then for a PDF the paper, the
 * text and what is printed, and for a setlist what it exports, then for a setlist in either format which of its songs.
 * The library's own files are the songs as the library holds them, so the PDF's options fold away under the format while
 * it is chosen. [onSettings] takes every change, normalized and saved by the screen.
 */
@Composable
private fun PrintOptions(
    modifier: Modifier,
    source: PrintSource,
    settings: PrintSettings,
    selected: Set<Int>,
    bottomPadding: Dp,
    onSelected: (Set<Int>) -> Unit,
    onSettings: (PrintSettings) -> Unit,
) {
    val state = rememberLazyListState()
    val isPdf = settings.format == PrintSettings.Format.PDF
    LazyColumn(modifier.fadingVerticalEdges(state), state = state, contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding)) {
        item {
            FormatChoice(
                format = settings.format,
                isSetlist = source.isSetlist,
                onSelected = { onSettings(settings.copy(format = it)) },
            )
        }
        item {
            PdfOption(isPdf) {
                SettingsSectionTitle(text = stringResource(Res.string.print_paper))
                SegmentedChoice(
                    options = PrintSettings.Paper.entries.map { paper ->
                        paper to stringResource(if (paper == PrintSettings.Paper.A4) Res.string.print_a4 else Res.string.print_letter)
                    },
                    selected = settings.paper,
                    onSelected = { onSettings(settings.copy(paper = it)) },
                )
                Spacer(Modifier.height(8.dp))
                SegmentedChoice(
                    options = listOf(false to stringResource(Res.string.print_portrait), true to stringResource(Res.string.print_landscape)),
                    selected = settings.isLandscape,
                    onSelected = { onSettings(settings.copy(isLandscape = it)) },
                )
                Spacer(Modifier.height(8.dp))
                PrintStepperRow(stringResource(Res.string.song_details_text_size)) {
                    Stepper(
                        value = stringResource(Res.string.print_font_size, settings.fontSize),
                        isDefault = true,
                        decreaseIcon = painterResource(Res.drawable.ic_text_decrease),
                        decreaseLabel = stringResource(Res.string.song_details_text_size_decrease),
                        canDecrease = settings.fontSize > MIN_FONT_SIZE,
                        onDecrease = { onSettings(settings.copy(fontSize = settings.fontSize - 1)) },
                        increaseIcon = painterResource(Res.drawable.ic_text_increase),
                        increaseLabel = stringResource(Res.string.song_details_text_size_increase),
                        canIncrease = settings.fontSize < MAX_FONT_SIZE,
                        onIncrease = { onSettings(settings.copy(fontSize = settings.fontSize + 1)) },
                        resetLabel = null,
                        onReset = null,
                    )
                }
                PrintStepperRow(stringResource(Res.string.print_margins)) {
                    Stepper(
                        value = stringResource(Res.string.print_margin, settings.marginMm),
                        isDefault = true,
                        decreaseIcon = painterResource(Res.drawable.ic_subtract),
                        decreaseLabel = stringResource(Res.string.print_margin_decrease),
                        canDecrease = settings.marginMm > MIN_MARGIN_MM,
                        // To the next multiple of the step either way, so that a stored value off the steps joins them
                        // rather than keeping its offset (12 goes to 10 or 15, never to 7 or 17); normalized() clamps it.
                        onDecrease = { onSettings(settings.copy(marginMm = (settings.marginMm - 1) / MARGIN_STEP_MM * MARGIN_STEP_MM)) },
                        increaseIcon = painterResource(Res.drawable.ic_add),
                        increaseLabel = stringResource(Res.string.print_margin_increase),
                        canIncrease = settings.marginMm < MAX_MARGIN_MM,
                        onIncrease = { onSettings(settings.copy(marginMm = (settings.marginMm / MARGIN_STEP_MM + 1) * MARGIN_STEP_MM)) },
                        resetLabel = null,
                        onReset = null,
                    )
                }
                SettingsSectionTitle(text = stringResource(Res.string.print_columns))
                SegmentedChoice(
                    options = (1..2).map { it to it.toString() },
                    selected = settings.columns,
                    onSelected = { onSettings(settings.copy(columns = it)) },
                )
                Spacer(Modifier.height(8.dp))
                CheckboxListItem(
                    title = stringResource(Res.string.print_chords),
                    isChecked = settings.showChords,
                    onCheckedChange = { onSettings(settings.copy(showChords = it)) },
                )
                CheckboxListItem(
                    title = stringResource(Res.string.print_comments),
                    isChecked = settings.showComments,
                    onCheckedChange = { onSettings(settings.copy(showComments = it)) },
                )
                CheckboxListItem(
                    title = stringResource(Res.string.print_metadata),
                    isChecked = settings.showMetadata,
                    onCheckedChange = { onSettings(settings.copy(showMetadata = it)) },
                )
                CheckboxListItem(
                    title = stringResource(Res.string.print_page_numbers),
                    isChecked = settings.showPageNumbers,
                    onCheckedChange = { onSettings(settings.copy(showPageNumbers = it)) },
                )
            }
        }
        if (source.isSetlist) {
            item {
                PdfOption(isPdf) {
                    SettingsSectionTitle(text = stringResource(Res.string.print_setlist_content))
                    SegmentedChoice(
                        options = PrintSettings.SetlistMode.entries.map { mode ->
                            mode to stringResource(
                                if (mode == PrintSettings.SetlistMode.SONG_SHEETS) {
                                    Res.string.print_song_sheets
                                } else {
                                    Res.string.print_running_order
                                },
                            )
                        },
                        selected = settings.setlistMode,
                        onSelected = { onSettings(settings.copy(setlistMode = it)) },
                    )
                    if (settings.setlistMode == PrintSettings.SetlistMode.SONG_SHEETS) {
                        Spacer(Modifier.height(8.dp))
                        CheckboxListItem(
                            title = stringResource(Res.string.print_overview),
                            isChecked = settings.includeSetlistOverview,
                            onCheckedChange = { onSettings(settings.copy(includeSetlistOverview = it)) },
                        )
                        CheckboxListItem(
                            title = stringResource(Res.string.print_new_page),
                            isChecked = settings.startSongsOnNewPage,
                            onCheckedChange = { onSettings(settings.copy(startSongsOnNewPage = it)) },
                        )
                    }
                }
            }
            // Which songs go out is a question for either format, and one answer to both: the same ticks narrow the PDF and
            // the zip, so switching between the two does not quietly put back the songs that were left out.
            if (source.songs.isNotEmpty()) {
                item {
                    SettingsSectionTitle(text = stringResource(Res.string.print_songs))
                    SelectAllListItem(
                        state = when (selected.size) {
                            0 -> ToggleableState.Off
                            source.songs.size -> ToggleableState.On
                            else -> ToggleableState.Indeterminate
                        },
                        onClick = { onSelected(if (selected.size == source.songs.size) emptySet() else source.songs.indices.toSet()) },
                    )
                }
            }
            itemsIndexed(source.songs) { index, entry ->
                CheckboxListItem(
                    title = "${entry.index}. ${entry.title}",
                    description = if (entry.song == null) stringResource(Res.string.print_missing) else entry.artist,
                    isChecked = index in selected,
                    onCheckedChange = { onSelected(if (it) selected + index else selected - index) },
                )
            }
        } else if (source.songs.any { it.song == null }) {
            item {
                Text(
                    text = stringResource(Res.string.print_missing),
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/**
 * The first of the options, PDF or the library's own files - ChordPro for a song, a zip for a setlist - and a line under
 * it saying what the chosen one is for and, for the zip, what is in it, since the two are told apart by what the person
 * receiving the file can do with it rather than by anything the names say.
 */
@Composable
private fun FormatChoice(
    format: PrintSettings.Format,
    isSetlist: Boolean,
    onSelected: (PrintSettings.Format) -> Unit,
) = Column {
    SettingsSectionTitle(text = stringResource(Res.string.print_format))
    SegmentedChoice(
        options = PrintSettings.Format.entries.map { option ->
            option to stringResource(
                when {
                    option == PrintSettings.Format.PDF -> Res.string.print_format_pdf
                    isSetlist -> Res.string.print_format_zip
                    else -> Res.string.print_format_chordpro
                },
            )
        },
        selected = format,
        onSelected = onSelected,
    )
    val description = when {
        format == PrintSettings.Format.PDF -> stringResource(Res.string.print_format_pdf_description)
        isSetlist -> stringResource(Res.string.print_format_zip_description)
        else -> stringResource(Res.string.print_format_chordpro_song_description)
    }
    AnimatedContent(description, transitionSpec = { fadeIn() togetherWith fadeOut() }) { shown ->
        Text(
            text = shown,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** One group of the options that only a PDF has, folding away while the library's own files are the format. */
@Composable
private fun PdfOption(
    isPdf: Boolean,
    content: @Composable () -> Unit,
) = AnimatedVisibility(isPdf, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
    Column { content() }
}

/**
 * One row standing for every song under it, its checkbox ticked when all of them are, empty when none is and a dash in
 * between, and a tap ticking them all or, when they all are, none: a row like the ones it acts on rather than a pair of
 * text buttons, which in the heading's color and size read as a second heading.
 */
@Composable
private fun SelectAllListItem(
    state: ToggleableState,
    onClick: () -> Unit,
) = ListItem(
    modifier = Modifier.triStateToggleable(state = state, role = Role.Checkbox, onClick = onClick),
    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    headlineContent = { Text(stringResource(Res.string.print_select_all)) },
    leadingContent = { TriStateCheckbox(state = state, onClick = null) },
)

/** A label and a stepper on one line, built like the overflow menus' `MenuStepperRow`, at the screen's own keyline. */
@Composable
private fun PrintStepperRow(
    label: String,
    stepper: @Composable () -> Unit,
) = Row(
    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Text(label, Modifier.weight(1f).padding(end = 16.dp), style = MaterialTheme.typography.bodyLarge)
    stepper()
}

/**
 * What a song's ChordPro or a setlist's zip writes, in the preview's place: a song's file as the library holds it,
 * unwrapped and in the monospaced font the editor shows it in, since only that keeps the columns of a tab lined up, or the
 * files the zip holds (see [ZipContents]). A song whose file could not be read has nothing to show, and nothing to save
 * either, and a setlist with none of its songs chosen says so, as the PDF's preview does.
 */
@Composable
private fun FilesPreview(
    modifier: Modifier,
    source: PrintSource,
    setlistFileName: String?,
    selected: Set<Int>,
    bottomPadding: Dp,
) {
    val text = source.songs.firstOrNull()?.text
    when {
        source.isSetlist && source.songs.isNotEmpty() && selected.isEmpty() -> FilesPreviewMessage(modifier, stringResource(Res.string.print_no_songs))
        source.isSetlist -> ZipContents(
            modifier = modifier,
            setlistFileName = setlistFileName.orEmpty(),
            songs = source.songs.filterIndexed { index, _ -> index in selected },
            bottomPadding = bottomPadding,
        )
        text == null -> FilesPreviewMessage(modifier, stringResource(Res.string.print_missing))
        else -> {
            val scrollState = rememberScrollState()
            Text(
                text = text,
                modifier = modifier
                    .fadingTopEdge(scrollState, MaterialTheme.colorScheme.background)
                    .verticalScroll(scrollState)
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPadding),
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = LocalMonospaceFontFamily.current),
                softWrap = false,
            )
        }
    }
}

@Composable
private fun FilesPreviewMessage(
    modifier: Modifier,
    message: String,
) = Box(modifier.padding(PAGE_MARGIN), contentAlignment = Alignment.Center) { Text(message) }

/**
 * The files of a setlist's zip, as an import will find them: the setlist manifest under its own file name, which keeps
 * the running order and every song's key and names only the [songs] that were chosen, and each of those as the ChordPro
 * file it is in the library. A chosen song whose file is missing stays named by the manifest but has no file of its own,
 * which its dimmed row says.
 */
@Composable
private fun ZipContents(
    modifier: Modifier,
    setlistFileName: String,
    songs: List<PrintSong>,
    bottomPadding: Dp,
) {
    val state = rememberLazyListState()
    LazyColumn(modifier.fadingVerticalEdges(state), state = state, contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding)) {
        item {
            SettingsSectionTitle(text = stringResource(Res.string.print_zip_contents))
            ZipFileRow(
                icon = painterResource(Res.drawable.ic_setlists),
                title = stringResource(Res.string.print_zip_manifest),
                description = textResource(Res.string.print_zip_manifest_description, setlistFileName),
            )
        }
        // One list under one heading: the songs are in the zip as much as the manifest is, and a heading of their own
        // read as if the first one held only the manifest.
        items(songs) { entry ->
            ZipFileRow(
                icon = painterResource(Res.drawable.ic_songs),
                title = "${entry.index}. ${entry.title}",
                description = if (entry.text == null) {
                    stringResource(Res.string.print_zip_missing)
                } else {
                    textResource(Res.string.print_zip_song_description, entry.fileName)
                },
                isIncluded = entry.text != null,
            )
        }
    }
}

/** One file of a setlist's zip, dimmed where it is named by the setlist but left out of the zip, its file missing. */
@Composable
private fun ZipFileRow(
    icon: Painter,
    title: String,
    description: String?,
    isIncluded: Boolean = true,
) = ListItem(
    modifier = Modifier.alpha(if (isIncluded) 1f else 0.5f),
    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    headlineContent = { Text(title) },
    supportingContent = description?.let { { Text(it) } },
    leadingContent = { Icon(painter = icon, contentDescription = null) },
)

/** What the preview shows, which it fades between, keyed by its kind so that a new document does not count as a change. */
private sealed interface PreviewContent {
    data class Empty(val message: String) : PreviewContent
    data object Loading : PreviewContent
    data class Pages(val laidOut: LaidOutDocument) : PreviewContent
}

/**
 * The pages, or what stands in for them, over the whole of [modifier]'s pane: nothing around them takes a band of it, so
 * a zoomed page reaches out to the pane's edges and what controls it floats over it.
 *
 * @param emptyMessage What to say instead of the pages where there are none to show: a setlist with no songs, or none
 *   of them chosen.
 * @param bottomInset What of the bottom of the pane the system bars take.
 * @param areOptionsBelow Whether the options are under the pane rather than beside it, at its start.
 * @param saveButtonSize The save button's, where it floats over the bottom of the pane too; zero where it does not.
 * @param magnifications The touchpad pinches the window hears, see [CampfireViewModel.magnifyByTouchpad].
 * @param page The page asked for, which the screen keeps rather than the pager: after a rotation the layout starts over and
 *   there are no pages for a while, and a pager state restored on its own would clamp the saved page to the first one.
 * @param pageView How that page is zoomed and panned, kept by the screen for the same reason, and read where it is used,
 *   since the gestures that change it read it again before the next composition.
 */
@Composable
private fun PrintPreview(
    modifier: Modifier,
    laidOut: LaidOutDocument?,
    isCurrent: Boolean,
    renderer: PrintRenderer,
    emptyMessage: String?,
    bottomInset: Dp,
    areOptionsBelow: Boolean,
    saveButtonSize: DpSize,
    magnifications: Flow<Float>,
    page: Int,
    onPageSettled: (Int) -> Unit,
    pageView: () -> PageView,
    onPageViewChanged: (PageView) -> Unit,
) {
    val content = when {
        emptyMessage != null -> PreviewContent.Empty(emptyMessage)
        laidOut == null || laidOut.document.pages.isEmpty() -> PreviewContent.Loading
        else -> PreviewContent.Pages(laidOut)
    }
    AnimatedContent(
        targetState = content,
        modifier = modifier,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        contentKey = { it::class },
    ) { shown ->
        when (shown) {
            is PreviewContent.Empty -> Box(Modifier.fillMaxSize().padding(PAGE_MARGIN), contentAlignment = Alignment.Center) {
                Text(shown.message)
            }
            PreviewContent.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { DelayedLoadingIndicator() }
            is PreviewContent.Pages -> PrintPages(
                laidOut = shown.laidOut,
                isCurrent = isCurrent,
                renderer = renderer,
                bottomInset = bottomInset,
                areOptionsBelow = areOptionsBelow,
                saveButtonSize = saveButtonSize,
                magnifications = magnifications,
                page = page,
                onPageSettled = onPageSettled,
                pageView = pageView,
                onPageViewChanged = onPageViewChanged,
            )
        }
    }
}

/**
 * The pages side by side in a pager, turned by a swipe, by the buttons floating under them or, once the pane has the
 * focus, by the arrow, Page Up / Page Down, Home and End keys, and zoomed by a pinch, a double tap, a pinch on a touchpad
 * or, on the desktop, Ctrl / Cmd and the scroll wheel. It is the whole sheet of paper that grows, edges and all, past
 * the pane that cuts it off and under the buttons floating over it, the way a document viewer zooms, rather than its
 * content growing inside a page that keeps its size, which read as the text being enlarged for the file. At a zoom of 1
 * the page fits what the floating buttons leave of the pane. A zoomed page is drawn again at its new size rather than
 * scaled up, so it stays sharp, and the pager does not take a swipe while it is zoomed, since the swipe is the pan.
 * The zoom and the pan are [pageView], a point of the page rather than an offset in pixels, so that a pane laid out at
 * another size keeps the same part of the page in its middle.
 */
@Composable
private fun PrintPages(
    laidOut: LaidOutDocument,
    isCurrent: Boolean,
    renderer: PrintRenderer,
    bottomInset: Dp,
    areOptionsBelow: Boolean,
    saveButtonSize: DpSize,
    magnifications: Flow<Float>,
    page: Int,
    onPageSettled: (Int) -> Unit,
    pageView: () -> PageView,
    onPageViewChanged: (PageView) -> Unit,
) = BoxWithConstraints(Modifier.fillMaxSize()) {
    val pageCount = laidOut.document.pages.size
    val pagerState = rememberPagerState(initialPage = page.coerceIn(0, pageCount - 1)) { pageCount }
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    var viewportSize by remember { mutableStateOf(Size.Zero) }
    var pointerPosition by remember { mutableStateOf<Offset?>(null) }
    val aspectRatio = laidOut.document.width / laidOut.document.height
    // The buttons are centered under the page. Where the pane is too narrow for them beside the save button, they are
    // lifted over it rather than pushed off the middle; beside it they are level with it.
    val hasSaveButton = saveButtonSize.width > 0.dp
    val isBesideSaveButton = maxWidth / 2 + PAGE_BUTTONS_HALF_WIDTH + 8.dp <= maxWidth - PAGE_MARGIN - saveButtonSize.width
    val pageButtonsBottom by animateDpAsState(
        targetValue = bottomInset + PAGE_MARGIN + when {
            !hasSaveButton -> 0.dp
            isBesideSaveButton -> (saveButtonSize.height - PAGE_BUTTONS_HEIGHT) / 2
            else -> saveButtonSize.height + PAGE_MARGIN
        },
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
    )
    val fitBottom = pageButtonsBottom + PAGE_BUTTONS_HEIGHT + PAGE_MARGIN
    fun fitArea() = with(density) { fitArea(viewportSize, PAGE_MARGIN.toPx(), fitBottom.toPx()) }
    // A new document keeps the page that was open, clamped to the pages it has.
    LaunchedEffect(pageCount) { if (pageCount > 0) pagerState.scrollToPage(page.coerceIn(0, pageCount - 1)) }
    // A page turned to opens whole. The page the pager starts on is the one the screen kept, zoomed as it was left, which
    // is what the pager being composed again in another pane, or after a rotation, comes back to.
    LaunchedEffect(pagerState) {
        var previous = pagerState.settledPage
        snapshotFlow { pagerState.settledPage }.collect { settled ->
            if (pagerState.pageCount > 0) onPageSettled(settled)
            if (settled != previous) onPageViewChanged(PageView())
            previous = settled
        }
    }
    // Only where a keyboard is the way the app is driven: on a touch screen it would bring the keyboard's focus ring up.
    LaunchedEffect(Unit) { if (isDesktopPlatform) focusRequester.requestFocus() }
    fun shownPageBounds() = pageBounds(viewportSize, fitArea(), aspectRatio, pageView())
    fun zoomTo(target: Float, pivot: Offset = fitArea().center) {
        val view = pageView()
        val clampedZoom = target.coerceIn(1f, MAX_ZOOM)
        val area = fitArea()
        // The point of the page under the pivot stays under it, until the page is back at the size it fits at.
        val topLeft = pivot - (pivot - shownPageBounds().topLeft) * (clampedZoom / view.zoom)
        val zoomedSize = fittedPageSize(area.size, aspectRatio) * clampedZoom
        onPageViewChanged(pageViewOf(clampedZoom, topLeft - centeredTopLeft(zoomedSize, area), area, zoomedSize, viewportSize))
    }
    // How far a page has been moved past where it rests towards an edge of the pane, which is as strong as the fade
    // there is: a page at rest is drawn whole, and one zoomed, panned or turned fades out as it goes under the app bar
    // or towards the options, the way anything scrolled out of a pane of the app does, instead of being cut off there.
    fun pastTop() = if (viewportSize == Size.Zero) 0 else with(density) { PAGE_MARGIN.toPx() - shownPageBounds().top }.roundToInt()
    fun pastBottom() = if (viewportSize == Size.Zero) 0 else with(density) {
        shownPageBounds().bottom - viewportSize.height + PAGE_MARGIN.toPx()
    }.roundToInt()
    fun pastStart(): Int {
        if (viewportSize == Size.Zero) return 0
        val margin = with(density) { PAGE_MARGIN.toPx() }
        return pagerState.layoutInfo.visiblePagesInfo.maxOfOrNull { info ->
            val bounds = if (info.index == pagerState.currentPage) {
                shownPageBounds()
            } else {
                pageBounds(viewportSize, fitArea(), aspectRatio, PageView())
            }
            margin - info.offset - if (layoutDirection == LayoutDirection.Rtl) viewportSize.width - bounds.right else bounds.left
        }?.roundToInt() ?: 0
    }
    fun turnTo(target: Int) {
        coroutineScope.launch { pagerState.animateScrollToPage(target.coerceIn(0, pageCount - 1)) }
    }
    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        zoomTo(pageView().zoom * zoomChange)
        val view = pageView()
        if (view.zoom > 1f) {
            val area = fitArea()
            val zoomedSize = fittedPageSize(area.size, aspectRatio) * view.zoom
            val pan = clampPan(view.panOf(zoomedSize), area, zoomedSize, viewportSize)
            onPageViewChanged(pageViewOf(view.zoom, pan + panChange, area, zoomedSize, viewportSize))
        }
    }
    // Around the pointer, where it is over the page, as the scroll wheel zooms; a pinch on a touchpad says nowhere.
    LaunchedEffect(magnifications) {
        magnifications.collect { factor -> zoomTo(pageView().zoom * factor, pointerPosition ?: fitArea().center) }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                // Alt + Left is the browser's Back, which on the web closes this screen, and Cmd + Left and Right are Back
                // and Forward on a Mac, so a press with any of those down is left to whoever sent it.
                if (event.type != KeyEventType.KeyDown || event.isAltPressed || event.isCtrlPressed || event.isMetaPressed) {
                    return@onKeyEvent false
                }
                val target = when (event.key) {
                    Key.DirectionLeft, Key.PageUp -> pagerState.currentPage - 1
                    Key.DirectionRight, Key.PageDown -> pagerState.currentPage + 1
                    Key.MoveHome -> 0
                    Key.MoveEnd -> pageCount - 1
                    else -> return@onKeyEvent false
                }
                turnTo(target)
                true
            },
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .fadingVerticalEdges(
                    scrolledFromTop = ::pastTop,
                    scrolledFromBottom = { if (areOptionsBelow) pastBottom() else 0 },
                )
                .then(if (areOptionsBelow) Modifier else Modifier.fadingUnderStartOverlay(scrolledFromStart = ::pastStart, overlayWidth = 0.dp)),
            pageSpacing = PAGE_MARGIN,
            userScrollEnabled = pageView().zoom == 1f,
        ) { index ->
            val isShownPage = index == pagerState.currentPage
            val pageLabel = stringResource(Res.string.print_page, index + 1, pageCount)
            // Keyed by the generation, not by the document, whose equality would compare every text on every page.
            AnimatedContent(
                targetState = laidOut,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                contentKey = { it.generation },
            ) { faded ->
                val document = faded.document
                val shownPage = document.pages.getOrNull(index) ?: return@AnimatedContent
                PrintPageCanvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (isShownPage) {
                                Modifier
                                    .onSizeChanged { viewportSize = it.toSize() }
                                    .trackPointer { pointerPosition = it }
                                    .transformable(transformableState, canPan = { pageView().zoom > 1f })
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onDoubleTap = { zoomTo(if (pageView().zoom > 1f) 1f else DOUBLE_TAP_ZOOM, it) },
                                        )
                                    }
                                    .wheelZoom { notches, position ->
                                        zoomTo(pageView().zoom * WHEEL_ZOOM_BASE.pow(-notches), position)
                                    }
                            } else {
                                Modifier
                            },
                        ),
                    document = document,
                    page = shownPage,
                    renderer = renderer,
                    fitBottom = fitBottom,
                    pageView = if (isShownPage) pageView() else PageView(),
                    description = pageLabel,
                )
            }
        }
        LayoutIndicator(isVisible = !isCurrent)
        PageButtons(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = pageButtonsBottom),
            page = pagerState.currentPage,
            pageCount = pageCount,
            onTurn = ::turnTo,
        )
    }
}

/** The page buttons and the count between them, on a pill of their own floating over the bottom of the page. */
@Composable
private fun PageButtons(
    modifier: Modifier,
    page: Int,
    pageCount: Int,
    onTurn: (Int) -> Unit,
) = Surface(
    modifier = modifier.height(PAGE_BUTTONS_HEIGHT),
    shape = CircleShape,
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(enabled = page > 0, onClick = { onTurn(page - 1) }) {
            Icon(painterResource(Res.drawable.ic_previous), contentDescription = stringResource(Res.string.print_previous))
        }
        Text(stringResource(Res.string.print_page, page + 1, pageCount), style = MaterialTheme.typography.bodySmall)
        IconButton(enabled = page + 1 < pageCount, onClick = { onTurn(page + 1) }) {
            Icon(painterResource(Res.drawable.ic_next), contentDescription = stringResource(Res.string.print_next))
        }
    }
}

/** Reports where the pointer is over the element while it hovers or presses, and null once it has left. */
private fun Modifier.trackPointer(onPosition: (Offset?) -> Unit) = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            onPosition(if (event.type == PointerEventType.Exit) null else event.changes.first().position)
        }
    }
}

/**
 * One page, white under a hairline border, as large as fits in the canvas less [PAGE_MARGIN] around it and [fitBottom]
 * under it at a zoom of 1 and the zoom of [pageView] times that otherwise, moved to the point of it [pageView] looks at;
 * the canvas cuts off whatever of it reaches past its edges.
 */
@Composable
private fun PrintPageCanvas(
    modifier: Modifier,
    document: PrintDocument,
    page: PrintPage,
    renderer: PrintRenderer,
    fitBottom: Dp,
    pageView: PageView,
    description: String,
) {
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    Canvas(
        modifier
            .clipToBounds()
            // A picture of a page, named by its number: its texts are in layout order, chord after lyric fragment after
            // padding, which read aloud is noise, and the song itself is readable in the viewer.
            .semantics {
                contentDescription = description
                role = Role.Image
            },
    ) {
        val area = fitArea(size, PAGE_MARGIN.toPx(), fitBottom.toPx())
        val bounds = pageBounds(size, area, document.width / document.height, pageView)
        clipRect(bounds.left, bounds.top, bounds.right, bounds.bottom) {
            drawRect(Color.White, bounds.topLeft, bounds.size)
            translate(bounds.left, bounds.top) { renderer.draw(this, page, bounds.width / document.width) }
        }
        val borderWidth = 1.dp.toPx()
        drawRect(
            color = borderColor,
            topLeft = bounds.topLeft + Offset(borderWidth / 2f, borderWidth / 2f),
            size = Size(bounds.width - borderWidth, bounds.height - borderWidth),
            style = Stroke(borderWidth),
        )
    }
}

/** What a page fits into at a zoom of 1: the [viewport] less [margin] at its top and sides and [bottom] under it. */
private fun fitArea(viewport: Size, margin: Float, bottom: Float) = Rect(
    left = margin,
    top = margin,
    right = maxOf(margin, viewport.width - margin),
    bottom = maxOf(margin, viewport.height - bottom),
)

/** The largest page of [aspectRatio] that fits in [area]. */
private fun fittedPageSize(area: Size, aspectRatio: Float): Size {
    val width = minOf(area.width, area.height * aspectRatio)
    return Size(width, width / aspectRatio)
}

/** Where a page as large as [pageSize] starts when it is centered in [area]. */
private fun centeredTopLeft(pageSize: Size, area: Rect) =
    Offset(area.center.x - pageSize.width / 2f, area.center.y - pageSize.height / 2f)

/**
 * How a page of the preview is zoomed, and the point of it, as a fraction of its width and its height, that is in the
 * middle of what it fits into: a pan in pixels would be another part of the page in a pane of another size.
 */
@Immutable
private data class PageView(
    val zoom: Float = 1f,
    val focus: Offset = Offset(0.5f, 0.5f),
) {
    /** How far a page as large as [pageSize] is moved from the middle by looking at [focus]. */
    fun panOf(pageSize: Size) = Offset(pageSize.width * (0.5f - focus.x), pageSize.height * (0.5f - focus.y))
}

/** The [PageView] of a page as large as [pageSize] at [zoom], moved by [pan] from the middle of [area] as far as it may be. */
private fun pageViewOf(zoom: Float, pan: Offset, area: Rect, pageSize: Size, viewport: Size): PageView {
    val clamped = clampPan(pan, area, pageSize, viewport)
    return PageView(
        zoom = zoom,
        focus = Offset(0.5f - clamped.x / pageSize.width, 0.5f - clamped.y / pageSize.height),
    )
}

/** Where in [viewport] a page of [aspectRatio] lies, zoomed and moved by [view] from the middle of [area]. */
private fun pageBounds(viewport: Size, area: Rect, aspectRatio: Float, view: PageView): Rect {
    val pageSize = fittedPageSize(area.size, aspectRatio) * view.zoom
    return Rect(centeredTopLeft(pageSize, area) + clampPan(view.panOf(pageSize), area, pageSize, viewport), pageSize)
}

/**
 * Keeps a page as large as [pageSize], moved by [pan] from the middle of [area], where nothing is lost from sight along
 * each axis: centered in [area] while it fits it, covering [area] and inside [viewport] while it is between the two, and
 * covering [viewport] once it is larger, so that no pan shows anything beyond its edges. Each range runs into the next,
 * so a page zoomed out drifts back to the middle as it shrinks rather than jumping there at the end.
 */
private fun clampPan(pan: Offset, area: Rect, pageSize: Size, viewport: Size): Offset {
    val centered = centeredTopLeft(pageSize, area)
    fun clamp(value: Float, size: Float, areaStart: Float, areaEnd: Float, room: Float, start: Float): Float {
        val range = when {
            size <= areaEnd - areaStart -> start..start
            size <= room -> maxOf(areaEnd - size, 0f)..minOf(areaStart, room - size)
            else -> (room - size)..0f
        }
        return (start + value).coerceIn(range) - start
    }
    return Offset(
        x = clamp(pan.x, pageSize.width, area.left, area.right, viewport.width, centered.x),
        y = clamp(pan.y, pageSize.height, area.top, area.bottom, viewport.height, centered.y),
    )
}

/**
 * Ctrl or Cmd and the scroll wheel, in the desktop application only: in a browser that chord is the page's own zoom,
 * which the app leaves alone. [isLaunchScreenWholeStartup] is the one platform flag that is true there and nowhere else.
 */
private fun Modifier.wheelZoom(onZoom: (notches: Float, position: Offset) -> Unit) = if (!isLaunchScreenWholeStartup) {
    this
} else {
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                val isZoomChord = event.keyboardModifiers.isCtrlPressed || event.keyboardModifiers.isMetaPressed
                if (event.type == PointerEventType.Scroll && isZoomChord) {
                    onZoom(event.verticalWheelNotches(), event.changes.first().position)
                    event.changes.forEach { it.consume() }
                }
            }
        }
    }
}

/** Shown over a page that stands in for the next one, once the layout of that has taken a moment (it fades in on its own). */
@Composable
private fun LayoutIndicator(isVisible: Boolean) = AnimatedVisibility(isVisible, enter = EnterTransition.None, exit = fadeOut()) {
    DelayedLoadingIndicator()
}

/** The selected songs by their place in the source, with null (nothing read yet) saved as nothing at all. */
private val SELECTION_SAVER = Saver<Set<Int>?, List<Int>>(save = { it?.toList() }, restore = { it.toSet() })

private val PAGE_VIEW_SAVER = Saver<PageView, List<Float>>(
    save = { listOf(it.zoom, it.focus.x, it.focus.y) },
    restore = { PageView(zoom = it[0], focus = Offset(it[1], it[2])) },
)

private const val MAX_ZOOM = 4f
private const val DOUBLE_TAP_ZOOM = 2.5f

/** The zoom of one notch of the scroll wheel, towards the user zooming out. */
private const val WHEEL_ZOOM_BASE = 1.15f

/** The room around a page at a zoom of 1, and between two pages of the pager. */
private val PAGE_MARGIN = 16.dp

/** The height of the floating page buttons, fixed so that the page fits what they leave from the first frame. */
private val PAGE_BUTTONS_HEIGHT = 48.dp

/** Half the widest the page buttons get, two buttons around "Page 88 of 88", which they are kept that clear of the save button by. */
private val PAGE_BUTTONS_HALF_WIDTH = 110.dp

/** The room a list under the save button leaves after its last row, so that the row can be scrolled clear of it. */
private val SAVE_BUTTON_CLEARANCE = 88.dp

/** The share of a phone's screen the preview takes over the options, enough to read a page by and to leave the options a list. */
private const val PHONE_PREVIEW_HEIGHT_FRACTION = 0.42f

/** How long the options have to hold still before the pages are laid out again, so that a burst of steps is one layout. */
private val LAYOUT_DEBOUNCE = 120.milliseconds
private const val MIN_FONT_SIZE = 8
private const val MAX_FONT_SIZE = 20
private const val MIN_MARGIN_MM = 10
private const val MAX_MARGIN_MM = 25
private const val MARGIN_STEP_MM = 5
