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
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.animateBounds
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.data.model.domain.PrintSettings
import com.pandulapeter.campfire.presentation.localization.currentLanguage
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.print_load_failed
import com.pandulapeter.campfire.presentation.resources.print_missing
import com.pandulapeter.campfire.presentation.resources.print_no_songs
import com.pandulapeter.campfire.presentation.resources.print_setlist_empty
import com.pandulapeter.campfire.presentation.resources.print_time
import com.pandulapeter.campfire.presentation.resources.retry
import com.pandulapeter.campfire.presentation.resources.setlists_export
import com.pandulapeter.campfire.presentation.resources.song_details_tempo
import com.pandulapeter.campfire.presentation.resources.song_details_transposition
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_capo
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_key
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_tempo
import com.pandulapeter.campfire.presentation.resources.songs_export_song
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.DelayedLoadingIndicator
import com.pandulapeter.campfire.presentation.ui.components.saveShortcut
import com.pandulapeter.campfire.presentation.ui.components.textResource
import com.pandulapeter.campfire.presentation.ui.contentEdges
import com.pandulapeter.campfire.presentation.ui.platform.LocalFilePicker
import com.pandulapeter.campfire.presentation.ui.platform.calendarLocale
import com.pandulapeter.campfire.presentation.ui.print.PrintLabels
import com.pandulapeter.campfire.presentation.ui.print.PrintRenderer
import com.pandulapeter.campfire.presentation.ui.print.TEMPO_VALUE
import com.pandulapeter.campfire.presentation.ui.print.layoutPrintDocument
import com.pandulapeter.campfire.presentation.ui.print.pdfFileName
import com.pandulapeter.campfire.presentation.ui.print.withinFeatures
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.rememberDefaultSectionLabels
import com.pandulapeter.campfire.presentation.ui.theme.LocalMonospaceFontFamily
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn

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
internal fun ExportScreen(
    viewModel: CampfireViewModel,
    dialog: DialogType.Export,
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
        transposition = stringResource(Res.string.song_details_transposition),
        capo = stringResource(Res.string.song_editor_insert_capo),
        tempo = stringResource(Res.string.song_editor_insert_tempo),
        tempoValue = textResource(Res.string.song_details_tempo, TEMPO_VALUE),
        time = stringResource(Res.string.print_time),
        missing = stringResource(Res.string.print_missing),
        sections = rememberDefaultSectionLabels(shouldNumberSections = preferences?.shouldNumberSections == true),
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
    // What the Features tab switched off is left out of the file whatever the options say, the options themselves being
    // kept for the switch to be turned back on.
    val areChordsEnabled = preferences?.areChordsEnabled != false
    val isMetronomeEnabled = preferences?.isMetronomeEnabled != false
    val printedSettings = state.settings.withinFeatures(areChordsEnabled = areChordsEnabled, isMetronomeEnabled = isMetronomeEnabled)
    val laidOut by produceState<LaidOutDocument?>(null, chosenSource, printedSettings, labels, renderer, state.attempt) {
        state.layoutFailed = false
        val input = chosenSource
        val inputSettings = printedSettings
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
                    settings = inputSettings,
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
    val isCurrent = laidOut.let { it != null && it.source === chosenSource && it.settings == printedSettings && it.labels == labels }
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
    val pageCount = laidOut?.document?.pages?.size ?: 0
    val pagerState = rememberPagerState(initialPage = state.page.coerceIn(0, maxOf(0, pageCount - 1))) { pageCount }
    val pageScope = rememberCoroutineScope()
    val pageResetSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    var pageResetAnimation by remember { mutableStateOf<Job?>(null) }
    // Keep paging alive even when the preview scrolls out of the portrait list.
    LaunchedEffect(pageCount) {
        if (pageCount > 0) pagerState.scrollToPage(state.page.coerceIn(0, pageCount - 1))
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { settled ->
            if (pagerState.pageCount > 0) {
                if (state.page != settled) {
                    pageResetAnimation?.cancel()
                    val initialView = state.pageView
                    pageResetAnimation = pageScope.launch {
                        // One spring moves zoom and focus together, returning the page smoothly to its fitted position.
                        animate(0f, 1f, animationSpec = pageResetSpec) { value, _ ->
                            val progress = value.coerceIn(0f, 1f)
                            state.pageView = PageView(
                                zoom = initialView.zoom + (1f - initialView.zoom) * progress,
                                focus = initialView.focus + (Offset(0.5f, 0.5f) - initialView.focus) * progress,
                            )
                        }
                    }
                }
                state.page = settled
            }
        }
    }
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
    val content = when {
        state.failed || state.layoutFailed && !isFiles -> PrintScreenContent.FAILED
        state.source == null -> PrintScreenContent.LOADING
        else -> PrintScreenContent.LOADED
    }
    val canSaveNow = content == PrintScreenContent.LOADED && if (isFiles) canExportFiles else hasPages
    // Drawn in the same window as the screen under it, so it does not take the focus by being there, and a key would
    // otherwise go on reaching that screen - a pedal stepping the song being exported. A bare focus target draws no
    // indication and does not hand the focus on to the first button, so it is taken on every platform, whatever the
    // screen shows; the preview moves it further in where it takes it for its own arrows. Holding it is also what lets
    // Ctrl / Cmd + S press Save, which it only does while the button is there and is not counting pages.
    val rootFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { rootFocus.requestFocus() }
    // A Surface, so that nothing of the app under it can be pressed through it.
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .saveShortcut { if (canSaveNow && exportProgress == null) requestExport(ExportRequest.SAVE) }
            .focusRequester(rootFocus)
            .focusTarget(),
        color = MaterialTheme.colorScheme.background,
    ) {
        val bottomInset = WindowInsets.contentEdges.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                PrintTopAppBar(
                    title = stringResource(if (dialog.setlist != null) Res.string.setlists_export else Res.string.songs_export_song),
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
                    val optionsWidth = when {
                        bodyWidth >= 760.dp && bodyHeight >= 480.dp -> 330.dp
                        // A phone on its side: too short to stack the two, wide enough to put them side by side.
                        bodyWidth >= 520.dp -> 260.dp
                        else -> null
                    }
                    val isSideBySide = optionsWidth != null
                    val turnPage: (Int) -> Unit = { target -> pageScope.launch { pagerState.animateScrollToPage(target.coerceIn(0, pageCount - 1)) } }
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
                                                isBottomAnchored = isSideBySide,
                                            )
                                        } else {
                                            val emptyMessage = when {
                                                state.source!!.songs.isEmpty() -> stringResource(Res.string.print_setlist_empty)
                                                state.selected.orEmpty().isEmpty() -> stringResource(Res.string.print_no_songs)
                                                else -> null
                                            }
                                            // Stacked, the preview is the first item of the options' list, so the pill turning its pages
                                            // is drawn inside it and scrolls away with it rather than staying over the options.
                                            Box(Modifier.fillMaxSize()) {
                                                PrintPreview(
                                                    modifier = Modifier.fillMaxSize(),
                                                    laidOut = laidOut,
                                                    isCurrent = isCurrent,
                                                    renderer = renderer,
                                                    emptyMessage = emptyMessage,
                                                    // Only the full-height preview needs room to fit a page above the floating controls.
                                                    bottomInset = if (isSideBySide) bottomInset else 0.dp,
                                                    areOptionsBelow = !isSideBySide,
                                                    pagerState = pagerState,
                                                    magnifications = viewModel.printPreviewMagnifications,
                                                    pageView = { state.pageView },
                                                    onPageViewChanged = {
                                                        // A new zoom or pan gesture takes over from the automatic reset.
                                                        pageResetAnimation?.cancel()
                                                        state.pageView = it
                                                    },
                                                )
                                                if (!isSideBySide) {
                                                    PageButtons(
                                                        modifier = Modifier.align(Alignment.TopEnd).padding(PAGE_MARGIN),
                                                        isVisible = hasPages,
                                                        page = pagerState.currentPage,
                                                        pageCount = pageCount,
                                                        onTurn = turnPage,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                val options: @Composable (Modifier) -> Unit = { modifier ->
                                    PrintOptions(
                                        modifier = modifier,
                                        source = state.source!!,
                                        settings = state.settings,
                                        areChordsEnabled = areChordsEnabled,
                                        isMetronomeEnabled = isMetronomeEnabled,
                                        selected = state.selected.orEmpty(),
                                        // Scroll under the controls, with enough trailing space to bring the last option above them.
                                        bottomPadding = bottomInset + if (isSideBySide) 8.dp else SAVE_BUTTON_CLEARANCE,
                                        header = if (isSideBySide) null else { { preview(Modifier.fillMaxWidth().height(360.dp)) } },
                                        onSelected = { if (isOpen) state.selected = it },
                                        onSettings = update,
                                    )
                                }
                                PrintPanes(
                                    optionsWidth = optionsWidth,
                                    options = options,
                                    preview = preview,
                                )
                            }
                        }
                    }
                    PageButtons(
                        modifier = Modifier.align(Alignment.TopEnd).padding(PAGE_MARGIN),
                        isVisible = isSideBySide && content == PrintScreenContent.LOADED && !isFiles && hasPages,
                        page = pagerState.currentPage,
                        pageCount = pageCount,
                        onTurn = turnPage,
                    )
                }
            }
            SaveButton(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .windowInsetsPadding(WindowInsets.contentEdges.only(WindowInsetsSides.Bottom + WindowInsetsSides.End))
                    .padding(16.dp),
                isVisible = exportProgress != null && content == PrintScreenContent.LOADED || canSaveNow,
                progress = exportProgress,
                onSave = { requestExport(ExportRequest.SAVE) },
                onCancel = viewModel::cancelPdfExport,
            )
        }
    }
}

/**
 * The options beside the preview, [optionsWidth] wide at its start, or filling the width where that is null,
 * with the preview as their first scrolling item. The options keep their scroll across arrangements and their bounds
 * animate when the arrangement changes, while a resize within one arrangement is followed as it comes.
 */
@Composable
private fun PrintPanes(
    optionsWidth: Dp?,
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
        Box(Modifier.fillMaxSize()) {
            options(
                if (optionsWidth != null) {
                    Modifier.width(optionsWidth).fillMaxHeight()
                } else {
                    Modifier.fillMaxSize()
                }.then(paneBounds),
            )
            if (optionsWidth != null) {
                preview(Modifier.fillMaxSize().padding(start = optionsWidth).then(paneBounds))
            }
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

/** What the screen's area shows, which it fades between; a new document is not one of these, see [PrintPreview]. */
private enum class PrintScreenContent { FAILED, LOADING, LOADED }

/** How long the options have to hold still before the pages are laid out again, so that a burst of steps is one layout. */
private val LAYOUT_DEBOUNCE = 120.milliseconds
