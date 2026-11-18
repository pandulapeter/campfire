/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.source

import com.pandulapeter.campfire.data.formats.zip.ZipEntry
import com.pandulapeter.campfire.data.formats.zip.ZipWriter
import com.pandulapeter.campfire.data.model.hashing.Sha256
import com.pandulapeter.campfire.data.model.importing.ImportLimits
import com.pandulapeter.campfire.data.model.library.LibraryFiles
import java.io.File
import java.lang.reflect.InvocationTargetException
import kotlin.random.Random

/** An archive to import, under the [name] it is picked as, and the same content without ZIP64 where it has one. */
internal class ParityArchive(val label: String, val name: String, val bytes: ByteArray, val twin: ByteArray? = null)

/**
 * Every archive `ArchivePathParityTest` imports: the archives the unpack tests build, the committed fixtures, and the
 * private corpus.
 */
internal object ParityArchives {

    /**
     * Every archive the unpack tests hand an [ArchiveUnpacks], recorded while each of their tests runs, so that a case
     * added there is imported whole here without a second list to keep in step. Run by reflection, each in
     * a new instance with its `@BeforeTest` and `@AfterTest`, as the test runner runs them.
     */
    fun recorded(): List<ParityArchive> {
        val archives = linkedMapOf<String, ByteArray>()
        archiveRecorder = { archive -> archives.putIfAbsent(Sha256.hashToHex(archive), archive.copyOf()) }
        try {
            listOf(ArchiveLocalSourceTest::class.java, ArchiveImagesTest::class.java, StreamingArchiveUnpackTest::class.java, OpenSongSetImportTest::class.java).forEach(::runEveryTest)
        } finally {
            archiveRecorder = null
        }
        return archives.values.mapIndexed { index, bytes -> ParityArchive(label = "recorded #$index", name = "archive.zip", bytes = bytes) }
    }

    private fun runEveryTest(testClass: Class<*>) {
        fun annotated(vararg names: String) = testClass.methods.filter { method -> method.annotations.any { it.annotationClass.simpleName in names } }
        val before = annotated("Before", "BeforeEach", "BeforeTest")
        val after = annotated("After", "AfterEach", "AfterTest")
        annotated("Test").sortedBy { it.name }.forEach { test ->
            val instance = testClass.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
            try {
                before.forEach { it.invoke(instance) }
                test.invoke(instance)
            } catch (exception: InvocationTargetException) {
                throw AssertionError("${testClass.simpleName}.${test.name} fails on its own", exception.targetException)
            } finally {
                after.forEach { it.invoke(instance) }
            }
        }
    }

    /**
     * Archives as people bring them, which the unpack tests build only in pieces: libraries exported with pictures,
     * attachments, setlists, and picture links; other apps' backups beside them; and archives of many megabytes of
     * pictures and attachments, built here since no unpack test may hold that much.
     */
    suspend fun built(): List<ParityArchive> {
        val pictures = (0 until 4).map { TestPictures.png(seed = it, size = 3_000 + it * 500) }
        val text = { index: Int, extra: String -> "{title: Song $index}\n{artist: Band}\n{image: src=images/p$index.png}\n$extra[C]La la [G]la\n" }
        val library = ZipWriter.write(
            (0 until 4).flatMap { index ->
                listOf(
                    ZipEntry("songs/band-song_$index.cho", text(index, if (index == 1) "{meta: attachment sheet.pdf Sheet}\n" else "").encodeToByteArray()),
                    ZipEntry("images/p$index.png", pictures[index]),
                )
            } + listOf(
                ZipEntry("images/sheet.pdf", "%PDF-1.7\n%fake\n".encodeToByteArray()),
                ZipEntry("setlists/gig.setlist.json", SETLIST.encodeToByteArray()),
                // A picture two songs share, a picture named by address, and one no song names.
                ZipEntry("songs/shared.cho", "{title: Shared}\n{image: src=p0.png}\n{image: src=https://example.com/cover.png}\n".encodeToByteArray()),
                ZipEntry("images/unused.png", TestPictures.png(seed = 9, size = 2_000)),
            ),
        )
        val songbookPro = ZipWriter.write(
            listOf(
                ZipEntry("dataFile.txt", SONGBOOK_PRO.encodeToByteArray()),
                ZipEntry("dataFile.hash", "9ed4cb9934f75a0e".encodeToByteArray()),
            ),
        )
        val nested = ZipWriter.write(
            listOf(
                ZipEntry("Library.sbpbackup", songbookPro),
                ZipEntry("export.zip", library),
                ZipEntry("loose.cho", "{title: Loose}\n{image: src=p1.png}\n".encodeToByteArray()),
                ZipEntry("p1.png", pictures[1]),
            ),
        )
        return listOf(
            ParityArchive("a library export", "library.zip", library),
            ParityArchive("a SongbookPro backup", "Library.sbpbackup", songbookPro),
            ParityArchive("a library beside a backup in an archive", "everything.zip", nested),
            ParityArchive("pictures past 64 MiB", "photos.zip", pastPictureBudget()),
            ParityArchive("attachments past 50 MiB", "recordings.zip", pastAttachmentBudget()),
            ParityArchive("a nested archive lying about its size", "lie.zip", nestedArchiveLyingAboutItsSize()),
            ParityArchive("pictures lying about their size", "lies.zip", picturesLyingAboutTheirSize()),
        ) + upgradeFixtures()
    }

    /**
     * Nine pictures of 7.5 MB, each within its own limit and together past the 64 MiB the pictures downloaded by address
     * may take ([ImportLimits.MAX_IMPORT_IMAGES_SIZE]), which they never count against, and a picture by address after them.
     */
    private suspend fun pastPictureBudget(): ByteArray {
        val count = 9
        val song = (listOf("{title: Photos}") + (0 until count).map { "{image: src=photo$it.png}" } + "{image: src=https://example.com/late.png}").joinToString("\n")
        return ZipWriter.write(listOf(ZipEntry("photos.cho", song.encodeToByteArray())) + (0 until count).map { ZipEntry("photo$it.png", TestPictures.png(seed = 100 + it, size = 7_500_000)) })
    }

    /** Two recordings of 27 MB, each within the attachment limit and together past it. */
    private suspend fun pastAttachmentBudget(): ByteArray {
        val names = listOf("0123456789abcdef.mp3", "fedcba9876543210.mp3")
        val song = (listOf("{title: Takes}", "{image: src=cover.png}") + names.map { "{meta: attachment $it Take}" }).joinToString("\n")
        return ZipWriter.write(
            listOf(ZipEntry("takes.cho", song.encodeToByteArray()), ZipEntry("cover.png", TestPictures.png(seed = 7, size = 5_000))) +
                names.mapIndexed { index, name -> ZipEntry("images/$name", Random(200 + index).nextBytes(27_000_000)) },
        )
    }

    /**
     * A few hundred bytes: a song, and a nested archive whose central directory declares nearly 4 GB, which its 118
     * bytes cannot hold, and which is more than the phone-like free space [ImportPathApp] reports.
     */
    private suspend fun nestedArchiveLyingAboutItsSize(): ByteArray {
        val inner = ZipWriter.write(listOf(ZipEntry("b.cho", "{title: B}".encodeToByteArray())))
        return ZipWriter.write(listOf(ZipEntry("a.cho", "{title: A}".encodeToByteArray()), ZipEntry("inner.zip", inner)))
            .apply { declareUncompressedSize("inner.zip", 0xF0000000L) }
    }

    /** A song naming 150 pictures of a hundred bytes that each declare the most a picture may be, 1.2 GB together. */
    private suspend fun picturesLyingAboutTheirSize(): ByteArray {
        val count = 150
        val song = (listOf("{title: Lies}") + List(count) { "{image: src=p$it.png}" }).joinToString("\n")
        return ZipWriter.write(listOf(ZipEntry("lies.cho", song.encodeToByteArray())) + List(count) { ZipEntry("p$it.png", TestPictures.png(seed = it, size = 100)) })
            .apply { repeat(count) { declareUncompressedSize("p$it.png", ImportLimits.MAX_IMAGE_FILE_SIZE) } }
    }

    /** The `upgrade-*` libraries as an export packs a library: its songs and setlists, by the paths they have there. */
    private suspend fun upgradeFixtures() = listOf("upgrade-5.2.0", "upgrade-5.4.0").mapNotNull { fixture ->
        val library = File(File(checkNotNull(javaClass.classLoader.getResource(fixture)).toURI()), "library").takeIf { it.isDirectory } ?: return@mapNotNull null
        val entries = library.walkTopDown().filter { it.isFile }.sortedBy { it.path }.map { ZipEntry(it.relativeTo(library).invariantSeparatorsPath, it.readBytes()) }.toList()
        ParityArchive("the $fixture library", "$fixture.zip", ZipWriter.write(entries))
    }

    /**
     * The small ZIP64 archives real tools wrote (`RealWriterArchives` in `:data:formats`' tests, whose source the build
     * names), each beside its twin without ZIP64; the archives past 4 GiB there are sparse descriptions, not archives.
     */
    fun realWriters(): List<ParityArchive> {
        val source = File(checkNotNull(System.getProperty("realWriterArchives")) { "The build names RealWriterArchives.kt" }).readText()
        fun hex(name: String): ByteArray {
            val body = Regex("const val $name =\\s*((?:\"[0-9a-f]*\"\\s*\\+?\\s*)+)").find(source)?.groupValues?.get(1) ?: error("No $name in RealWriterArchives.kt")
            val digits = Regex("\"([0-9a-f]*)\"").findAll(body).joinToString("") { it.groupValues[1] }
            return ByteArray(digits.length / 2) { digits.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        }
        val pairs = listOf("INFO_ZIP" to "INFO_ZIP_ZIP64", "INFO_ZIP_STREAMED" to "INFO_ZIP_ZIP64_STREAMED", "PYTHON" to "PYTHON_ZIP64")
        return pairs.flatMap { (twin, zip64) ->
            listOf(
                ParityArchive(label = twin, name = "${twin.lowercase()}.zip", bytes = hex(twin)),
                ParityArchive(label = zip64, name = "${twin.lowercase()}.zip", bytes = hex(zip64), twin = hex(twin)),
            )
        }
    }

    /**
     * Every archive-like file under the private corpus (`.zip` and the other apps' backups by name, any other file but a
     * document's by its zip signature, as the import tells archives), and, since the corpus is mostly loose documents,
     * each of its folders packed into an archive and every folder's archive packed into one more, so that real documents
     * and their songs are imported inside archives and nested ones too.
     */
    suspend fun corpus(corpus: File): List<ParityArchive> {
        fun visible(file: File) = !file.name.startsWith(".")
        val files = corpus.walkTopDown().onEnter(::visible).filter { it.isFile && visible(it) }.sortedBy { it.path }.toList()
        val archiveLike = files.filter { file ->
            LibraryFiles.isArchiveFileName(file.name) || (!LibraryFiles.isDocumentFileName(file.name) && LibraryFiles.isZipArchive(file.readBytes()))
        }.map { ParityArchive(label = "corpus ${it.relativeTo(corpus).invariantSeparatorsPath}", name = it.name, bytes = it.readBytes()) }
        val folders = corpus.listFiles().orEmpty().filter { it.isDirectory && visible(it) }.sortedBy { it.name }.map { folder ->
            val entries = files.filter { it.startsWith(folder) }.map { ZipEntry(it.relativeTo(corpus).invariantSeparatorsPath, it.readBytes()) }
            ParityArchive(label = "corpus folder ${folder.name} packed", name = "${folder.name}.zip", bytes = ZipWriter.write(entries))
        }
        val whole = ZipWriter.write(folders.map { ZipEntry("corpus/${it.name}", it.bytes) })
        return archiveLike + folders + listOfNotNull(
            ParityArchive(label = "corpus folders packed into one", name = "corpus.zip", bytes = whole).takeIf { whole.size <= ImportLimits.MAX_ARCHIVE_SIZE },
        )
    }

    private const val SETLIST = """{"title": "Gig", "songs": [{"fileName": "band-song_0.cho"}, {"fileName": "shared.cho"}]}"""

    private const val SONGBOOK_PRO = """1.0
{"songs": [{"Id": 1, "name": "A", "content": "[C]La"}, {"Id": 2, "name": "B", "content": "{image: src=x.png}\n[G]Lo"}],
"sets": [{"details": {"Id": 1, "name": "Gig"}, "contents": [{"Order": 0, "SongId": 1, "ItemType": 1}, {"Order": 1, "SongId": 2, "ItemType": 1}]}], "folders": []}"""
}
