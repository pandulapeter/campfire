/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state.editor

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.text.TextRange
import com.pandulapeter.campfire.chordpro.convert.PageTitle
import com.pandulapeter.campfire.chordpro.edit.ChordProImages
import com.pandulapeter.campfire.data.model.image.ImageIntake
import com.pandulapeter.campfire.data.model.image.ImageRefusal
import com.pandulapeter.campfire.data.model.importing.ImportPlan
import com.pandulapeter.campfire.data.model.importing.ImportedFile
import com.pandulapeter.campfire.data.model.importing.ImportedImage
import com.pandulapeter.campfire.data.model.importing.IncomingContent
import com.pandulapeter.campfire.domain.api.images.DownloadPictureUseCase
import com.pandulapeter.campfire.domain.api.images.AddDraftImageUseCase
import com.pandulapeter.campfire.domain.api.images.ExamineImageUseCase
import com.pandulapeter.campfire.domain.api.images.ClearDraftImagesUseCase
import com.pandulapeter.campfire.domain.api.images.HoldEditorImagesUseCase
import com.pandulapeter.campfire.domain.api.images.SaveWithDraftImagesUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import com.pandulapeter.campfire.data.model.song.Song
import com.pandulapeter.campfire.data.model.song.SongContent
import com.pandulapeter.campfire.data.model.preferences.AppLanguage
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.domain.api.songs.CreateSongFromTextUseCase
import com.pandulapeter.campfire.domain.api.songs.DraftSongFromTextUseCase
import com.pandulapeter.campfire.domain.api.songs.GetEditorDraftUseCase
import com.pandulapeter.campfire.domain.api.songs.GetSongContentInvalidationsUseCase
import com.pandulapeter.campfire.domain.api.songs.GetSongContentUseCase
import com.pandulapeter.campfire.domain.api.songs.SaveEditorDraftUseCase
import com.pandulapeter.campfire.domain.api.images.SaveSongImageUseCase
import com.pandulapeter.campfire.domain.api.songs.SaveSongContentUseCase
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogHost
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.messages.Message
import com.pandulapeter.campfire.presentation.ui.messages.MessageSink
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.navigation.followNewSongCreated
import com.pandulapeter.campfire.presentation.ui.rendering.testSongRenderer
import com.pandulapeter.campfire.presentation.ui.pictures.PendingMissingImagePick
import com.pandulapeter.campfire.presentation.ui.pictures.PictureFitter
import com.pandulapeter.campfire.domain.api.images.PictureDownload
import com.pandulapeter.campfire.presentation.ui.pictures.PictureLinkDownloader
import com.pandulapeter.campfire.presentation.ui.pictures.PictureWork
import com.pandulapeter.campfire.presentation.ui.state.SavedStateStore
import androidx.lifecycle.SavedStateHandle
import com.pandulapeter.campfire.presentation.ui.state.songText.InferredSongKeys
import com.pandulapeter.campfire.presentation.ui.state.songText.SongImageAdding
import com.pandulapeter.campfire.presentation.ui.state.songText.SongTextStore
import kotlinx.coroutines.Dispatchers
import com.pandulapeter.campfire.chordpro.model.ChordProAttachment
import com.pandulapeter.campfire.domain.api.images.AddDraftAttachmentUseCase
import com.pandulapeter.campfire.presentation.ui.pictures.AttachmentIntake
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The editor's session over a library of [files] held in memory, read and written through small stand-ins for the use cases. */
class EditorSessionTest {

    private val files = mutableMapOf<String, String>()
    private val linkedPictures = mutableMapOf<String, PictureDownload>()
    private var storedDraft: SongContent? = null
    private var isWriteFailing = false
    private val backStack = mutableStateListOf<CampfireDestination>(CampfireDestination.Songs)

    /** The pictures on the draft shelf, the ones Save moved into the library, and what the pruning was last told to hold. */
    private val draftImages = mutableSetOf<String>()
    private val libraryImages = mutableSetOf<String>()
    private var heldImages = emptySet<String>()

    private inner class Fixture(scope: CoroutineScope, notation: UserPreferences.Notation) {
        val messageSink = MessageSink(scope)
        val dialogHost = DialogHost(scope)
        private val getSongContent = object : GetSongContentUseCase {
            override suspend fun invoke(fileName: String) = files[fileName]?.let { SongContent(fileName = fileName, text = it) }
        }
        val useCases = EditorUseCases(
            getEditorDraft = object : GetEditorDraftUseCase {
                override suspend fun invoke() = storedDraft
            },
            saveEditorDraft = object : SaveEditorDraftUseCase {
                override suspend fun invoke(draft: SongContent?) {
                    storedDraft = draft
                }
            },
            draftSongFromText = object : DraftSongFromTextUseCase {
                override fun invoke(
                    text: String,
                    placeholderTitle: String,
                    placeholderArtist: String,
                    suggestedTitle: String?,
                    suggestedArtist: String?,
                ) = if ("{title" in text) text else "{title: ${suggestedTitle ?: placeholderTitle}}\n{artist: ${suggestedArtist ?: placeholderArtist}}\n$text"
            },
            createSongFromText = object : CreateSongFromTextUseCase {
                override suspend fun invoke(text: String): Song {
                    val title = Regex("""\{title: (.*)\}""").find(text)?.groupValues?.get(1).orEmpty()
                    val fileName = title.lowercase().replace(' ', '_') + ".cho"
                    files[fileName] = text
                    return Song(
                        fileName = fileName, title = title, artist = "", key = null, transpose = 0, tags = emptyList(), languages = emptyList(),
                        coverArtUrl = null, hasChords = true, canUpdateFileName = false, lastModified = 0, size = 0,
                    )
                }
            },
            addDraftImage = object : AddDraftImageUseCase {
                override suspend fun invoke(bytes: ByteArray): ImageIntake = intakeOf(bytes).also { if (it is ImageIntake.Accepted) draftImages += it.name }
            },
            examineImage = object : ExamineImageUseCase {
                override suspend fun invoke(bytes: ByteArray) = intakeOf(bytes)
            },
            holdEditorImages = object : HoldEditorImagesUseCase {
                override fun invoke(names: Set<String>) {
                    heldImages = names
                }
            },
            clearDraftImages = object : ClearDraftImagesUseCase {
                override suspend fun invoke() = draftImages.clear()
            },
            saveWithDraftImages = object : SaveWithDraftImagesUseCase {
                override suspend fun <T> invoke(text: String, save: suspend (text: String) -> T): T {
                    val names = ChordProImages.referencedSources(text).filter { it in draftImages }
                    libraryImages += names
                    return save(text)
                }
            },
            downloadPicture = object : DownloadPictureUseCase {
                override suspend fun invoke(url: String) = linkedPictures[url] ?: PictureDownload.Unreachable
            },
            addDraftAttachment = object : AddDraftAttachmentUseCase {
                override suspend fun invoke(bytes: ByteArray, extension: String): ImageIntake =
                    ImageIntake.Accepted("draft-${bytes.first()}.$extension").also { draftImages += it.name }
            },
        )

        /** Shrinks a picture to its last byte alone, as the platform's re-encoder would to a smaller copy. */
        private val pictureFitter = PictureFitter(examine = useCases.examineImage::invoke, reencode = { bytes, _, _ -> byteArrayOf(bytes.last()) }, work = PictureWork())

        val session: EditorSession
        val songTextStore = SongTextStore(
            scope = scope,
            backStack = backStack,
            editorDraftFileName = { session.editorDraft.value?.fileName },
            messageSink = messageSink,
            getSongContent = getSongContent,
            saveSongContent = object : SaveSongContentUseCase {
                override suspend fun invoke(content: SongContent, expectedText: String?): Boolean {
                    if (isWriteFailing) throw IllegalStateException("The disk is full.")
                    files[content.fileName] = content.text
                    return true
                }
            },
            getSongContentInvalidations = object : GetSongContentInvalidationsUseCase {
                override fun invoke() = emptyFlow<Long>()
            },
            inferredKeys = InferredSongKeys(inferKey = { null }, dispatcher = Dispatchers.Unconfined),
            readImageSizes = {},
            imageAdding = SongImageAdding(
                fitter = pictureFitter,
                saveSongImage = object : SaveSongImageUseCase {
                    override suspend fun invoke(fileName: String, expectedText: String, missingName: String, bytes: ByteArray) = throw NotImplementedError()
                },
                pendingPick = PendingMissingImagePick(SavedStateStore(SavedStateHandle(), scope)),
                linkDownloader = PictureLinkDownloader(download = { PictureDownload.Unreachable }, work = PictureWork(), messageSink = messageSink),
            ),
            addOnBackStackChanged = {},
            currentSongFileName = { null },
        )

        init {
            session = EditorSession(
                scope = scope,
                dialogHost = dialogHost,
                messageSink = messageSink,
                songTextStore = songTextStore,
                songRenderer = testSongRenderer(),
                backStack = backStack,
                userPreferences = MutableStateFlow(preferences(notation)),
                arePreferencesLoaded = MutableStateFlow(true),
                getSongContent = getSongContent,
                useCases = useCases,
                images = EditorImages(
                    scope = scope,
                    messageSink = messageSink,
                    addDraftImage = useCases.addDraftImage,
                    pictureFitter = pictureFitter,
                    linkDownloader = PictureLinkDownloader(download = useCases.downloadPicture::invoke, work = PictureWork(), messageSink = messageSink),
                    attachmentIntake = AttachmentIntake(addDraftAttachment = useCases.addDraftAttachment::invoke, messageSink = messageSink, work = PictureWork()),
                    holdEditorImages = useCases.holdEditorImages,
                    clearDraftImages = useCases.clearDraftImages,
                    computation = Dispatchers.Unconfined,
                    work = PictureWork(),
                ),
                updateBackStack = { update ->
                    val top = backStack.lastOrNull()
                    backStack.update()
                    session.onBackStackChanged(top)
                },
                isSongShownOnTop = { fileName -> (backStack.lastOrNull() as? CampfireDestination.SongDetails)?.songFileNames?.singleOrNull() == fileName },
                popBackStack = { session.onBackStackChanged(backStack.removeAt(backStack.lastIndex)) },
                takePendingExit = { null },
                requestExit = { _, _ -> },
                followNewSongCreated = { fileName -> backStack.followNewSongCreated(fileName) },
            )
        }

        val messages get() = messageSink.messageQueue.value.map { it.value }
    }

    private fun TestScope.fixture(notation: UserPreferences.Notation = UserPreferences.Notation.STANDARD) = Fixture(backgroundScope, notation)

    @Test
    fun `the editor opened from a list has the song's details screen under it`() = runTest {
        fixture().session.openEditor("a.cho")
        val details = backStack[1] as CampfireDestination.SongDetails
        assertEquals(listOf("a.cho"), details.songFileNames)
        assertEquals(listOf(CampfireDestination.Songs, details, CampfireDestination.SongEditor(fileName = "a.cho")), backStack.toList())
    }

    @Test
    fun `the editor opened from the song's details screen goes straight on top of it`() = runTest {
        val details = CampfireDestination.SongDetails(songFileNames = listOf("a.cho"), setlistFileName = null, initialIndex = 0)
        backStack += details
        fixture().session.openEditor("a.cho")
        assertEquals(listOf(CampfireDestination.Songs, details, CampfireDestination.SongEditor(fileName = "a.cho")), backStack.toList())
    }

    @Test
    fun `a stored draft the file already holds is thrown away rather than reopened`() = runTest {
        files["a.cho"] = "{key: Bb}\n[Bb]One"
        storedDraft = SongContent(fileName = "a.cho", text = "{key: Bb}\n[Bb]One")
        val fixture = fixture(UserPreferences.Notation.GERMAN)
        fixture.session.startRecovery()
        assertFalse(fixture.session.editorDraftRecovery.await())
        runCurrent()
        assertEquals(listOf<CampfireDestination>(CampfireDestination.Songs), backStack.toList())
        assertNull(storedDraft)
    }

    @Test
    fun `a stored draft of a file that is gone reopens the editor and says both`() = runTest {
        storedDraft = SongContent(fileName = "a.cho", text = "[C]One")
        val fixture = fixture()
        fixture.session.startRecovery()
        assertTrue(fixture.session.editorDraftRecovery.await())
        assertEquals(CampfireDestination.SongEditor(fileName = "a.cho"), backStack.last())
        assertEquals("[C]One", fixture.session.editorDraft.value?.text)
        assertTrue(fixture.session.hasUnsavedEditorText())
        assertEquals(listOf(Message.EditorDraftRestored, Message.EditedSongFileGone), fixture.messages)
    }

    @Test
    fun `a stored draft is not recovered over an editor the stack already has`() = runTest {
        files["a.cho"] = "[C]One"
        storedDraft = SongContent(fileName = "a.cho", text = "[C]Two")
        backStack += CampfireDestination.SongEditor(fileName = "b.cho")
        val fixture = fixture()
        fixture.session.startRecovery()
        assertFalse(fixture.session.editorDraftRecovery.await())
        assertEquals(CampfireDestination.SongEditor(fileName = "b.cho"), backStack.last())
        assertNull(fixture.session.recoveredEditorField("a.cho"))
        assertNull(fixture.session.recoveredEditorField("b.cho"))
        assertEquals(emptyList(), fixture.messages)
    }

    @Test
    fun `a stored draft is handed to an editor the restored stack has for its file and kept`() = runTest {
        files["a.cho"] = "[C]One"
        storedDraft = SongContent(fileName = "a.cho", text = "[C]Two")
        backStack += CampfireDestination.SongEditor(fileName = "a.cho")
        val fixture = fixture()
        fixture.session.startRecovery()
        assertFalse(fixture.session.editorDraftRecovery.await())
        runCurrent()
        assertEquals("[C]Two", fixture.session.recoveredEditorField("a.cho")?.text?.toString())
        assertEquals("[C]Two", fixture.session.editorDraft.value?.text)
        assertEquals(SongContent(fileName = "a.cho", text = "[C]Two"), storedDraft)
        assertEquals(emptyList(), fixture.messages)
    }

    @Test
    fun `a chord typed in the reader's notation is not unsaved against the file's spelling of it`() = runTest {
        files["a.cho"] = "[Bb]One"
        backStack += CampfireDestination.SongEditor(fileName = "a.cho")
        val fixture = fixture(UserPreferences.Notation.GERMAN)
        fixture.songTextStore.loadSongContent("a.cho")
        runCurrent()
        fixture.session.onEditorTextChanged(fileName = "a.cho", text = "[B]One")
        assertFalse(fixture.session.hasUnsavedEditorText())
        fixture.session.onEditorTextChanged(fileName = "a.cho", text = "[H]One")
        assertTrue(fixture.session.hasUnsavedEditorText())
    }

    @Test
    fun `a save from the unsaved changes question that fails keeps the editor open with its text`() = runTest {
        files["a.cho"] = "[C]One"
        backStack += CampfireDestination.SongEditor(fileName = "a.cho")
        isWriteFailing = true
        val fixture = fixture()
        fixture.session.onEditorTextChanged(fileName = "a.cho", text = "[C]Two")
        fixture.dialogHost.showDialog(DialogType.UnsavedChanges)
        fixture.session.saveEditorChangesAndLeave()
        runCurrent()
        assertEquals(CampfireDestination.SongEditor(fileName = "a.cho"), backStack.last())
        assertEquals("[C]Two", fixture.session.editorDraft.value?.text)
        assertNull(fixture.dialogHost.visibleDialog.value)
        assertEquals(listOf<Message>(Message.SaveFailed), fixture.messages)
        assertEquals("[C]One", files["a.cho"])
    }

    @Test
    fun `a save from the unsaved changes question that succeeds leaves the editor`() = runTest {
        files["a.cho"] = "[C]One"
        backStack += CampfireDestination.SongEditor(fileName = "a.cho")
        val fixture = fixture()
        fixture.session.onEditorTextChanged(fileName = "a.cho", text = "[C]Two")
        fixture.dialogHost.showDialog(DialogType.UnsavedChanges)
        fixture.session.saveEditorChangesAndLeave()
        runCurrent()
        assertEquals(listOf<CampfireDestination>(CampfireDestination.Songs), backStack.toList())
        assertEquals("[C]Two", files["a.cho"])
    }

    @Test
    fun `a new song opens its editor alone and with a placeholder title and unsaved and its draft on disk at once`() = runTest {
        val fixture = fixture()
        fixture.session.openNewSongEditor("[G]Hello", null)
        val editor = backStack.last() as CampfireDestination.SongEditor
        assertTrue(editor.isNewSong)
        assertEquals(listOf(CampfireDestination.Songs, editor), backStack.toList())
        assertEquals("{title: New song}\n{artist: Unknown artist}\n{key: G}\n[G]Hello", fixture.session.editorDraft.value?.text)
        assertTrue(fixture.session.hasUnsavedEditorText())
        assertEquals(fixture.session.editorDraft.value, storedDraft)
        assertTrue(files.isEmpty())
    }

    @Test
    fun `a new song naming no key opens with the key its chords are in under its title and artist and so does its whole page`() = runTest {
        val fixture = fixture()
        val song = ImportPlan.SongEntry(
            fileName = "new.cho",
            text = "[G]one [C]two [D]three [G]four",
            status = ImportPlan.Status.NEW,
            sourceFileName = "Pasted.txt",
            wholePageText = "Menu\n[G]one [C]two [D]three [G]four",
        )
        fixture.session.openNewSongEditor(song, null)
        runCurrent()
        assertEquals("{title: New song}\n{artist: Unknown artist}\n{key: G}\n[G]one [C]two [D]three [G]four", fixture.session.editorDraft.value?.text)
        assertEquals("{title: New song}\n{artist: Unknown artist}\n{key: G}\nMenu\n[G]one [C]two [D]three [G]four", fixture.session.wholePage.wholePageDraft.value)
        assertTrue(files.isEmpty())
    }

    @Test
    fun `a cropped page offers its whole page until the text is edited`() = runTest {
        val fixture = fixture()
        fixture.session.openNewSongEditor(croppedPage(), null)
        runCurrent()
        assertEquals("{title: New song}\n{artist: Unknown artist}\n{key: G}\nMenu\n[G]Hello", fixture.session.wholePage.wholePageDraft.value)
        fixture.session.onEditorTextChanged(CampfireDestination.SongEditor.NEW_SONG_FILE_NAME, "{title: Riverlight}\n[G]Hello")
        runCurrent()
        assertNull(fixture.session.wholePage.wholePageDraft.value)
        fixture.session.onEditorTextChanged(CampfireDestination.SongEditor.NEW_SONG_FILE_NAME, "{title: New song}\n{artist: Unknown artist}\n{key: G}\n[G]Hello")
        runCurrent()
        assertNull(fixture.session.wholePage.wholePageDraft.value)
    }

    @Test
    fun `using the whole page hands it to the editor and ends the offer`() = runTest {
        val fixture = fixture()
        val edits = mutableListOf<EditorTextEdit>()
        backgroundScope.launch { fixture.session.editorTextEdits.collect { edits += it } }
        fixture.session.openNewSongEditor(croppedPage(), null)
        runCurrent()
        fixture.session.wholePage.use()
        runCurrent()
        val draft = fixture.session.editorDraft.value?.text.orEmpty()
        assertEquals("{title: New song}\n{artist: Unknown artist}\n{key: G}\nMenu\n[G]Hello", edits.single().edit(draft))
        assertNull(fixture.session.wholePage.wholePageDraft.value)
    }

    @Test
    fun `a dismissed offer is gone and a page that was not cropped offers nothing`() = runTest {
        val fixture = fixture()
        fixture.session.openNewSongEditor(croppedPage(), null)
        runCurrent()
        fixture.session.wholePage.dismiss()
        runCurrent()
        assertNull(fixture.session.wholePage.wholePageDraft.value)
        fixture.session.onEditorClosed(CampfireDestination.SongEditor.NEW_SONG_FILE_NAME)
        fixture.session.openNewSongEditor("[G]Hello", null)
        runCurrent()
        assertNull(fixture.session.wholePage.wholePageDraft.value)
    }

    @Test
    fun `a suggested title is selected in a new song's editor`() = runTest {
        val fixture = fixture()
        fixture.session.openNewSongEditor("[G]Hello", PageTitle(title = "Riverlight", artist = "The Invented Band"))
        assertEquals("{title: Riverlight}\n{artist: The Invented Band}\n{key: G}\n[G]Hello", fixture.session.editorDraft.value?.text)
        val field = fixture.session.retainedEditorField(CampfireDestination.SongEditor.NEW_SONG_FILE_NAME)
        assertEquals(TextRange(8, 18), field?.selection)
    }

    @Test
    fun `the title a page's header names comes before the subject's and is selected while its whole page keeps the subject's`() = runTest {
        val fixture = fixture()
        val song = ImportPlan.SongEntry(
            fileName = "new.cho",
            text = "[G]Hello",
            status = ImportPlan.Status.NEW,
            sourceFileName = "Pasted.txt",
            wholePageText = "Riverlight Chords\n[G]Hello",
            pageTitle = "Riverlight",
        )
        fixture.session.openNewSongEditor(song, PageTitle(title = "Riverlight Chords @ Site", artist = "The Invented Band"))
        runCurrent()
        assertEquals("{title: Riverlight}\n{artist: The Invented Band}\n{key: G}\n[G]Hello", fixture.session.editorDraft.value?.text)
        assertEquals(TextRange(8, 18), fixture.session.retainedEditorField(CampfireDestination.SongEditor.NEW_SONG_FILE_NAME)?.selection)
        assertEquals(true, fixture.session.wholePage.wholePageDraft.value?.startsWith("{title: Riverlight Chords @ Site}"))
    }

    @Test
    fun `a new song replaces an editor on top that has nothing unsaved`() = runTest {
        files["a.cho"] = "[C]One"
        backStack += CampfireDestination.SongDetails(songFileNames = listOf("a.cho"), setlistFileName = null, initialIndex = 0)
        backStack += CampfireDestination.SongEditor(fileName = "a.cho")
        fixture().session.openNewSongEditor("[G]Hello", null)
        assertEquals(3, backStack.size)
        assertTrue((backStack.last() as CampfireDestination.SongEditor).isNewSong)
    }

    @Test
    fun `a new song with no title is not saved`() = runTest {
        val fixture = fixture()
        fixture.session.openNewSongEditor("[G]Hello", null)
        fixture.session.saveSongContent(CampfireDestination.SongEditor.NEW_SONG_FILE_NAME, "{title: }\n[G]Hello")
        runCurrent()
        assertTrue(files.isEmpty())
        assertEquals(listOf<Message>(Message.NewSongUntitled), fixture.messages)
        assertTrue((backStack.last() as CampfireDestination.SongEditor).isNewSong)
    }

    @Test
    fun `a new song's first save writes it and its editor goes on over the song's details screen`() = runTest {
        val fixture = fixture()
        fixture.session.openNewSongEditor("[G]Hello", null)
        val entryKey = (backStack.last() as CampfireDestination.SongEditor).entryKey
        val text = "{title: Song}\n[G]Hello"
        fixture.session.onEditorTextChanged(CampfireDestination.SongEditor.NEW_SONG_FILE_NAME, text)
        fixture.session.saveSongContent(CampfireDestination.SongEditor.NEW_SONG_FILE_NAME, text)
        runCurrent()
        assertEquals(text, files["song.cho"])
        val details = backStack[1] as CampfireDestination.SongDetails
        assertEquals(listOf("song.cho"), details.songFileNames)
        assertEquals(CampfireDestination.SongEditor(fileName = "song.cho", entryKey = entryKey), backStack.last())
        assertEquals("song.cho", fixture.session.editorDraft.value?.fileName)
        assertFalse(fixture.session.hasUnsavedEditorText())
    }

    @Test
    fun `a new song saved from the unsaved changes question leaves for its details screen`() = runTest {
        val fixture = fixture()
        fixture.session.openNewSongEditor("{title: Song}\n[G]Hello", null)
        fixture.dialogHost.showDialog(DialogType.UnsavedChanges)
        fixture.session.saveEditorChangesAndLeave()
        runCurrent()
        assertEquals(CampfireDestination.Songs, backStack.first())
        assertEquals(listOf("song.cho"), (backStack.last() as CampfireDestination.SongDetails).songFileNames)
        assertEquals(2, backStack.size)
    }

    @Test
    fun `a new song discarded leaves nothing behind`() = runTest {
        val fixture = fixture()
        // What lets go of the stored draft once nothing is unsaved, as every start does.
        fixture.session.startRecovery()
        fixture.session.editorDraftRecovery.await()
        fixture.session.openNewSongEditor("[G]Hello", null)
        runCurrent()
        assertTrue(storedDraft != null)
        fixture.session.leaveEditorWithoutSaving()
        runCurrent()
        assertEquals(listOf<CampfireDestination>(CampfireDestination.Songs), backStack.toList())
        assertTrue(files.isEmpty())
        assertNull(storedDraft)
    }

    @Test
    fun `a stored draft of a new song reopens its editor alone - with no word of a missing file`() = runTest {
        storedDraft = SongContent(fileName = CampfireDestination.SongEditor.NEW_SONG_FILE_NAME, text = "{title: Song}\n[G]Hello")
        val fixture = fixture()
        fixture.session.startRecovery()
        assertTrue(fixture.session.editorDraftRecovery.await())
        assertEquals(2, backStack.size)
        assertTrue((backStack.last() as CampfireDestination.SongEditor).isNewSong)
        assertEquals(listOf<Message>(Message.EditorDraftRestored), fixture.messages)
    }

    @Test
    fun `a picked picture is kept as a draft picture and inserted and Save moves it into the library before the draft shelf is emptied`() = runTest {
        files["a.cho"] = "{title: A}"
        val fixture = fixture()
        // Where the holding starts following the editor, once no stored draft is left to reopen.
        fixture.session.startRecovery()
        fixture.session.editorDraftRecovery.await()
        fixture.openEditor("a.cho")
        val insertions = mutableListOf<EditorImageInsertion>()
        backgroundScope.launch { fixture.session.images.insertions.collect { insertions += it } }
        fixture.session.images.pickImages("a.cho") { listOf(ImportedFile(name = "photo.jpg", bytes = byteArrayOf(7))) }
        runCurrent()
        assertEquals(listOf(listOf("draft-7.png")), insertions.map { it.names })
        assertEquals(setOf("draft-7.png"), draftImages)
        val text = "{title: A}\n{image: src=draft-7.png}"
        fixture.session.onEditorTextChanged("a.cho", text)
        advanceTimeBy(1_000)
        assertEquals(setOf("draft-7.png"), heldImages)
        fixture.session.saveSongContent("a.cho", text)
        runCurrent()
        assertEquals(text, files["a.cho"])
        assertEquals(setOf("draft-7.png"), libraryImages)
        // Still there while the editor is: an undo may bring back a line naming one.
        assertEquals(setOf("draft-7.png"), draftImages)
        backStack.removeAt(backStack.lastIndex).let { fixture.session.onBackStackChanged(it) }
        runCurrent()
        assertTrue(draftImages.isEmpty())
        assertEquals(emptySet(), heldImages)
    }

    @Test
    fun `a picture picked into an editor that is then discarded reaches neither the library nor the draft shelf`() = runTest {
        files["a.cho"] = "{title: A}"
        val fixture = fixture()
        fixture.openEditor("a.cho")
        fixture.session.images.pickImages("a.cho") { listOf(ImportedFile(name = "photo.jpg", bytes = byteArrayOf(7))) }
        runCurrent()
        fixture.session.onEditorTextChanged("a.cho", "{title: A}\n{image: src=draft-7.png}")
        fixture.session.leaveEditorWithoutSaving()
        runCurrent()
        assertEquals(listOf<CampfireDestination>(CampfireDestination.Songs), backStack.toList())
        assertTrue(libraryImages.isEmpty())
        assertTrue(draftImages.isEmpty())
        assertEquals("{title: A}", files["a.cho"])
    }

    @Test
    fun `a picture from a link is kept as a draft picture and inserted and one that does not arrive is said why`() = runTest {
        files["a.cho"] = "{title: A}"
        linkedPictures["https://example.com/chart.png"] = PictureDownload.Picture(byteArrayOf(7))
        val fixture = fixture()
        fixture.openEditor("a.cho")
        val insertions = mutableListOf<EditorImageInsertion>()
        backgroundScope.launch { fixture.session.images.insertions.collect { insertions += it } }
        fixture.session.images.downloadImage("a.cho", "https://example.com/chart.png")
        fixture.session.images.downloadImage("a.cho", "https://example.com/gone.png")
        runCurrent()
        assertEquals(listOf(listOf("draft-7.png")), insertions.map { it.names })
        assertEquals(setOf("draft-7.png"), draftImages)
        assertEquals(listOf<Message>(Message.PictureLinkUnreachable), fixture.messages)
    }

    @Test
    fun `the address of a picture that does not arrive is kept for a retry and forgotten once one arrives`() = runTest {
        files["a.cho"] = "{title: A}"
        val fixture = fixture()
        fixture.openEditor("a.cho")
        backgroundScope.launch { fixture.session.images.insertions.collect {} }
        fixture.session.images.downloadImage("a.cho", "https://example.com/chart.png")
        runCurrent()
        assertEquals("https://example.com/chart.png", fixture.session.images.failedLinkAddress)
        linkedPictures["https://example.com/chart.png"] = PictureDownload.Picture(byteArrayOf(7))
        fixture.session.images.downloadImage("a.cho", "https://example.com/chart.png")
        runCurrent()
        assertEquals(null, fixture.session.images.failedLinkAddress)
    }

    @Test
    fun `a refused picture is said why and inserts nothing`() = runTest {
        files["a.cho"] = "{title: A}"
        val fixture = fixture()
        fixture.openEditor("a.cho")
        val insertions = mutableListOf<EditorImageInsertion>()
        backgroundScope.launch { fixture.session.images.insertions.collect { insertions += it } }
        fixture.session.images.pickImages("a.cho") { listOf(ImportedFile(name = "photo.heic", bytes = byteArrayOf(HEIC))) }
        fixture.session.images.pickImages("a.cho") { listOf(ImportedFile.unread("huge.jpg", isTooLarge = true)) }
        runCurrent()
        assertTrue(insertions.isEmpty())
        assertEquals(
            listOf<Message>(Message.ImageNotAdded(ImageRefusal.UNSUPPORTED_FORMAT), Message.ImageTooLargeToRead),
            fixture.messages,
        )
    }

    @Test
    fun `an attached file is kept on the draft shelf under its own extension and named as it was picked`() = runTest {
        files["a.cho"] = "{title: A}"
        val fixture = fixture()
        fixture.openEditor("a.cho")
        val insertions = mutableListOf<EditorAttachmentInsertion>()
        backgroundScope.launch { fixture.session.images.attachmentInsertions.collect { insertions += it } }
        fixture.session.images.attach("a.cho") { ImportedFile(name = "Rehearsal take.M4A", bytes = byteArrayOf(7)) }
        runCurrent()
        assertEquals(listOf(ChordProAttachment(source = "draft-7.m4a", name = "Rehearsal take.M4A")), insertions.map { it.attachment })
        assertEquals(setOf("draft-7.m4a"), draftImages)
    }

    @Test
    fun `a file too large or of a type no song takes is said why and attaches nothing`() = runTest {
        files["a.cho"] = "{title: A}"
        val fixture = fixture()
        fixture.openEditor("a.cho")
        val insertions = mutableListOf<EditorAttachmentInsertion>()
        backgroundScope.launch { fixture.session.images.attachmentInsertions.collect { insertions += it } }
        fixture.session.images.attach("a.cho") { ImportedFile.unread("concert.mp4", isTooLarge = true) }
        fixture.session.images.attach("a.cho") { ImportedFile.unread("setup.exe") }
        runCurrent()
        assertTrue(insertions.isEmpty())
        assertTrue(draftImages.isEmpty())
        assertEquals(
            listOf<Message>(Message.AttachmentNotAdded(ImageRefusal.TOO_LARGE), Message.AttachmentNotAdded(ImageRefusal.UNSUPPORTED_FORMAT)),
            fixture.messages,
        )
    }

    @Test
    fun `a picture too large for a song is kept as a smaller copy which a snackbar says`() = runTest {
        files["a.cho"] = "{title: A}"
        val fixture = fixture()
        fixture.openEditor("a.cho")
        val insertions = mutableListOf<EditorImageInsertion>()
        backgroundScope.launch { fixture.session.images.insertions.collect { insertions += it } }
        fixture.session.images.pickImages("a.cho") { listOf(ImportedFile(name = "photo.jpg", bytes = LARGE_PHOTO)) }
        runCurrent()
        assertEquals(listOf(listOf("draft-5.png")), insertions.map { it.names })
        assertEquals(listOf<Message>(Message.ImageShrunk), fixture.messages)
    }

    @Test
    fun `a refusal is said rather than a smaller copy among pictures picked together`() = runTest {
        files["a.cho"] = "{title: A}"
        val fixture = fixture()
        fixture.openEditor("a.cho")
        val insertions = mutableListOf<EditorImageInsertion>()
        backgroundScope.launch { fixture.session.images.insertions.collect { insertions += it } }
        fixture.session.images.pickImages("a.cho") {
            listOf(ImportedFile(name = "photo.jpg", bytes = LARGE_PHOTO), ImportedFile(name = "photo.heic", bytes = byteArrayOf(HEIC)))
        }
        runCurrent()
        assertEquals(listOf(listOf("draft-5.png")), insertions.map { it.names })
        assertEquals(listOf<Message>(Message.ImageNotAdded(ImageRefusal.UNSUPPORTED_FORMAT)), fixture.messages)
    }

    @Test
    fun `a picture picked for an editor that closed meanwhile is not kept`() = runTest {
        files["a.cho"] = "{title: A}"
        val fixture = fixture()
        fixture.openEditor("a.cho")
        val picked = CompletableDeferred<List<ImportedFile>>()
        fixture.session.images.pickImages("a.cho") { picked.await() }
        runCurrent()
        fixture.session.leaveEditorWithoutSaving()
        runCurrent()
        picked.complete(listOf(ImportedFile(name = "photo.jpg", bytes = byteArrayOf(7))))
        runCurrent()
        assertTrue(draftImages.isEmpty())
    }

    @Test
    fun `pictures that arrived alone open a new song titled by the first one - showing each one kept`() = runTest {
        val fixture = fixture()
        val isOpened = fixture.session.openNewSongEditor(pictureSong(byteArrayOf(1), byteArrayOf(HEIC), byteArrayOf(2)), null)
        assertTrue(isOpened)
        assertEquals(
            "{title: page 1}\n{artist: Unknown artist}\n{image: src=draft-1.png}\n{image: src=draft-2.png}",
            fixture.session.editorDraft.value?.text,
        )
        assertEquals(fixture.session.editorDraft.value, storedDraft)
        assertEquals(listOf<Message>(Message.ImageNotAdded(ImageRefusal.UNSUPPORTED_FORMAT)), fixture.messages)
        assertTrue(files.isEmpty())
        assertTrue(libraryImages.isEmpty())
    }

    @Test
    fun `a pasted text that brings pictures from its links opens as it is - its pictures kept as draft pictures`() = runTest {
        val fixture = fixture()
        val text = "{title: A}\n{artist: B}\n{image: src=draft-3.png}\n{image: src=https://example.com/gone.png}"
        val song = ImportPlan.SongEntry(
            fileName = "a.cho",
            text = text,
            status = ImportPlan.Status.NEW,
            sourceFileName = "Pasted.txt",
            images = listOf(ImportPlan.SongImage(source = "https://example.com/chart.png", name = "draft-3.png", content = IncomingContent.Bytes(byteArrayOf(3)), problem = null)),
        )
        assertTrue(fixture.session.openNewSongEditor(song, null))
        assertEquals(text, fixture.session.editorDraft.value?.text)
        assertEquals(setOf("draft-3.png"), draftImages)
    }

    @Test
    fun `pictures that are all refused open no editor`() = runTest {
        val fixture = fixture()
        assertFalse(fixture.session.openNewSongEditor(pictureSong(byteArrayOf(HEIC)), null))
        assertEquals(listOf<CampfireDestination>(CampfireDestination.Songs), backStack.toList())
        assertNull(fixture.session.editorDraft.value)
    }

    @Test
    fun `a picture too large to read that arrived alone opens no editor and is said to be too large`() = runTest {
        val fixture = fixture()
        val song = ImportPlan.SongEntry(
            fileName = "photo.webp",
            text = "",
            status = ImportPlan.Status.NEW,
            sourceFileName = "photo.webp",
            title = "photo",
            images = listOf(ImportPlan.SongImage(source = "photo.webp", name = null, content = null, problem = ImportedImage.Problem.TOO_LARGE)),
        )
        assertFalse(fixture.session.openNewSongEditor(song, null))
        assertEquals(listOf<Message>(Message.ImageTooLargeToRead), fixture.messages)
        assertNull(fixture.session.editorDraft.value)
    }

    @Test
    fun `a new song made of a picture moves it into the library at its first save`() = runTest {
        val fixture = fixture()
        fixture.session.openNewSongEditor(pictureSong(byteArrayOf(1)), null)
        val text = fixture.session.editorDraft.value!!.text
        fixture.session.saveSongContent(CampfireDestination.SongEditor.NEW_SONG_FILE_NAME, text)
        runCurrent()
        assertEquals(text, files["page_1.cho"])
        assertEquals(setOf("draft-1.png"), libraryImages)
    }

    private fun Fixture.openEditor(fileName: String) {
        backStack += CampfireDestination.SongEditor(fileName = fileName)
        session.onBackStackChanged(CampfireDestination.Songs)
        session.onEditorTextChanged(fileName, files.getValue(fileName))
    }

    private fun pictureSong(vararg pictures: ByteArray) = ImportPlan.SongEntry(
        fileName = "page 1.jpg",
        text = "",
        status = ImportPlan.Status.NEW,
        sourceFileName = "page 1.jpg",
        title = "page 1",
        images = pictures.mapIndexed { index, bytes -> ImportPlan.SongImage(source = "page ${index + 1}.jpg", name = null, content = IncomingContent.Bytes(bytes), problem = null) },
    )

    /** A page whose clutter the import cropped, as it hands it over. */
    private fun croppedPage() = ImportPlan.SongEntry(
        fileName = "new.cho",
        text = "[G]Hello",
        status = ImportPlan.Status.NEW,
        sourceFileName = "Pasted.txt",
        wholePageText = "Menu\n[G]Hello",
    )

    /** A pasted [text], as the import hands it over. */
    private suspend fun EditorSession.openNewSongEditor(text: String, suggestion: PageTitle?) =
        openNewSongEditor(ImportPlan.SongEntry(fileName = "new.cho", text = text, status = ImportPlan.Status.NEW, sourceFileName = "Pasted.txt"), suggestion)

    private fun preferences(notation: UserPreferences.Notation) = UserPreferences(
        isPerformanceModeEnabled = false,
        shouldShowArchivedSetlists = false,
        areChordsEnabled = true,
        areSetlistsEnabled = true,
        isMetronomeEnabled = true,
        fontScale = UserPreferences.DEFAULT_FONT_SCALE,
        sortingMode = UserPreferences.SortingMode.BY_TITLE,
        setlistSortingMode = UserPreferences.SetlistSortingMode.BY_DATE,
        uiMode = UserPreferences.UiMode.SYSTEM_DEFAULT,
        themeColor = UserPreferences.ThemeColor.CAMPFIRE,
        isAppIconThemed = true,
        isCoverArtEnabled = true,
        shouldNumberSections = true,
        language = AppLanguage.SYSTEM_DEFAULT,
        chordSpelling = UserPreferences.ChordSpelling(accidentals = UserPreferences.Accidentals.ORIGINAL, notation = notation),
        transpositions = emptyMap(),
        foldedSections = emptyMap(),
        tagMatchMode = UserPreferences.MatchMode.ANY,
        languageMatchMode = UserPreferences.MatchMode.ANY,
        tagSortingMode = UserPreferences.LabelSortingMode.BY_USAGE,
        languageSortingMode = UserPreferences.LabelSortingMode.BY_USAGE,
    )

    /** Refused as [addDraftImage] refuses: nothing, an unsupported format ([HEIC] first), over three bytes, else kept. */
    private fun intakeOf(bytes: ByteArray): ImageIntake = when {
        bytes.isEmpty() -> ImageIntake.Refused(ImageRefusal.NOT_AN_IMAGE)
        bytes.first() == HEIC -> ImageIntake.Refused(ImageRefusal.UNSUPPORTED_FORMAT)
        bytes.size > 3 -> ImageIntake.Refused(ImageRefusal.TOO_LARGE)
        else -> ImageIntake.Accepted("draft-${bytes.first()}.png")
    }

    private companion object {
        const val HEIC: Byte = 99

        /** Too large, with a JPEG's signature, and shrunk to its last byte. */
        val LARGE_PHOTO = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 5)
    }
}
