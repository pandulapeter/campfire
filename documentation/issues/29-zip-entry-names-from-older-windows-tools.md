# 29 · Archives from older Windows tools show garbled file names, and backslash paths are not stripped

**Severity:** minor (all platforms. Only for archives made by Windows 10's "Send to > Compressed folder" or other
tools that store names in a DOS code page without the UTF-8 flag, and by Windows PowerShell 5.1's `Compress-Archive`,
which writes `\` separators. The song texts are read correctly either way. What goes wrong is the name, which is only
used as the title of a song that declares none, in the import report, and to match the setlist references of an
archive whose songs are renamed on the way in) · **Area:** `:data:source:local:implementation` (`zip/ZipReader.kt`,
`source/ArchiveLocalSourceImpl.kt`)

## Symptom
1. On Windows 10, select `Tükörfúrógép.cho` (a song with no `{title}`) and "Send to > Compressed (zipped) folder".
   Import the zip. The song is titled `t_k_rf_r_g_p`. Explorer stored the name in the OEM code page without the
   UTF-8 flag, it decoded to `T�k�rf�r�g�p`, and that was normalized as the fallback title. The import report names
   the file the same way.
2. Zip a `songs` folder with Windows PowerShell 5.1 (`Compress-Archive songs songs.zip`). Its entries are called
   `songs\a.cho`. The path is not stripped, so the song arrives as `songs\a.cho`, a titleless one is titled `songs_a`,
   and a setlist in the archive whose songs are renamed on import (`storedSongFileNames` is keyed by the arriving
   name) keeps pointing at the old names. A directory entry `songs\` is reported as a skipped file.

## Cause
- `zip/ZipReader.kt:67` decodes every name as UTF-8 whatever the general purpose flags say
  (`archive.utf8(position + 46, nameLength)`, `:161-166`, `decodeToString()`, which replaces invalid bytes with
  U+FFFD). APPNOTE 4.4.4 says a name without bit 11 set is in IBM code page 437. In practice macOS's Archive Utility
  writes UTF-8 *without* the flag, so UTF-8 still has to be tried first.
- `zip/ZipReader.kt:68` recognises a directory only by `name.endsWith("/")`, and
  `ArchiveLocalSourceImpl.kt:92` strips the path only at `/`:

  ```kotlin
  private val String.fileName get() = substringAfterLast('/')
  ```

  APPNOTE 4.4.17 requires forward slashes, but PowerShell 5.1 on .NET Framework writes the platform separator.

## Fix
1. `ZipReader.kt`: replace `utf8` (`:161-166`) with a name decoder that honours the flag and falls back to CP437:

   ```kotlin
   /**
    * The name of an entry. UTF-8 where the entry says so (bit 11) and wherever the bytes are valid UTF-8 anyway, since
    * macOS writes UTF-8 names without setting the bit. Anything else is code page 437, which is what the format
    * specifies for a name without the bit and what the DOS-era Windows tools wrote: the accented letters of Western
    * and most Central European names come out right, instead of as a row of replacement characters.
    */
   private fun ByteArray.entryName(offset: Int, length: Int, flags: Int): String {
       if (length < 0 || offset < 0 || offset + length > size) {
           throw ZipException("Truncated archive: a $length byte name at offset $offset is outside the $size byte input.")
       }
       val bytes = copyOfRange(offset, offset + length)
       if (flags and FLAG_UTF8_NAME != 0) return bytes.decodeToString()
       return try {
           bytes.decodeToString(throwOnInvalidSequence = true)
       } catch (_: CharacterCodingException) {
           buildString(length) {
               for (byte in bytes) {
                   val value = byte.toInt() and 0xFF
                   append(if (value < 0x80) value.toChar() else CP437_CHARACTERS[value - 0x80])
               }
           }
       }
   }
   ```

   Call it as `val name = archive.entryName(position + 46, nameLength, flags)` at `:67`. `flags` is already read at
   `:58`. Add to the constants:

   ```kotlin
   private const val FLAG_UTF8_NAME = 0x0800

   /** 0x80-0xFF in IBM code page 437, the character set a zip entry name is in when it does not say otherwise. */
   private const val CP437_CHARACTERS =
       "ÇüéâäàåçêëèïîìÄÅÉæÆôöòûùÿÖÜ¢£¥₧ƒ" +
           "áíóúñÑªº¿⌐¬½¼¡«»░▒▓│┤╡╢╖╕╣║╗╝╜╛┐" +
           "└┴┬├─┼╞╟╚╔╩╦╠═╬╧╨╤╥╙╘╒╓╫╪┘┌█▄▌▐▀" +
           "αßΓπΣσµτΦΘΩδ∞φε∩≡±≥≤⌠⌡÷≈°∙·√ⁿ²■\u00A0"
   ```

   (four rows of 32, generated from Python's `cp437` codec). `CharacterCodingException` is in `kotlin.text`, so it
   needs no import in common code. Update the `read` KDoc sentence "Names are returned as stored (forward slashes,
   possibly with sub-directories), decoded as UTF-8." to "Names are returned as stored, sub-directories included,
   decoded as UTF-8 or, where they are not UTF-8 and do not claim to be, as code page 437 (see [entryName])."

   Hungarian names made on a Hungarian Windows are in CP852, not CP437. The two agree on `á é í ó ö ú ü` but not on
   `ő ű`, which come out as `ï √`. There is no telling the OEM code page from the bytes, and CP437 is what the format
   names. It is still far better than replacement characters, and the name is only a fallback.

2. `ZipReader.kt:68`: a directory is also a name ending in a backslash:

   ```kotlin
   // A backslash is a separator too: Windows PowerShell 5.1 writes the platform's own, against the format.
   if (!name.endsWith("/") && !name.endsWith("\\")) {
   ```

3. `ArchiveLocalSourceImpl.kt:91-92`:

   ```kotlin
   /**
    * The library is flat, so "songs/x.cho" and "x.cho" are the same file as far as an import is concerned. A
    * backslash separates as well, since Windows PowerShell 5.1 writes one; no file name the library holds has one.
    */
   private val String.fileName get() = substringAfterLast('/').substringAfterLast('\\')
   ```

   This also makes the hidden-file checks (`limitOf`, the unread filter), which already go through `fileName`, see
   `__MACOSX\._a.cho` as hidden.

## Tests
- `data/source/local/implementation/src/desktopTest/.../zip/ZipReaderJvmTest.kt`: an archive written by the JVM in
  CP437 (which leaves bit 11 clear) reads back under its real name:

  ```kotlin
  @Test
  fun readsNamesStoredInTheDosCodePage() {
      val archive = ByteArrayOutputStream().also { stream ->
          ZipOutputStream(stream, java.nio.charset.Charset.forName("IBM437")).use { zip ->
              zip.putNextEntry(java.util.zip.ZipEntry("Tükörfúrógép.cho"))
              zip.write(contents.getValue("hello.cho"))
              zip.closeEntry()
          }
      }.toByteArray()

      // Written without the UTF-8 flag, which is what makes the name code page 437 rather than UTF-8.
      assertEquals(0, archive.u16(4 + 2) and 0x0800)
      assertEquals(listOf("Tükörfúrógép.cho"), ZipReader.read(archive).entries.map { it.name })
  }
  ```

  A UTF-8 name without the flag (what macOS writes) is checked by hand, see Verify: the JVM and `ZipWriter` both set
  the flag for UTF-8 names.
- `data/source/local/implementation/src/desktopTest/.../source/ArchiveLocalSourceTest.kt`:

  ```kotlin
  @Test
  fun `strips backslash paths the way Windows PowerShell writes them`() = runBlocking {
      val archive = ZipWriter.write(
          listOf(
              ZipEntry("songs\\", ByteArray(0)),
              ZipEntry("songs\\a.cho", "{title: A}".encodeToByteArray()),
          ),
      )

      val files = archiveLocalSource.unpack(archive = archive, maxSize = ImportLimits.MAX_IMPORT_SIZE)

      assertEquals(listOf("a.cho"), files.map { it.name })
  }
  ```

Run `./gradlew :data:source:local:implementation:desktopTest` (in a worktree).

## Verify
1. Desktop: import an archive made by Windows 10's "Send to > Compressed (zipped) folder" holding a titleless
   `Tükörfúrógép.cho`. Or write the archive the new test builds to a file and import that. The song is titled
   `tukorfurogep` and the import report names `Tükörfúrógép.cho`.
2. Import an archive made by macOS Finder with accented names (UTF-8 without the flag): the names are unchanged.
3. Import an export made by Campfire: unchanged.
4. Compile `:app:desktop:run`, `:app:android:assembleDebug`, `:app:ios:linkDebugFrameworkIosSimulatorArm64`,
   `:app:web:wasmJsBrowserDevelopmentRun`.

## Docs
`data/source/local/implementation/CLAUDE.md:105`, the `zip/` bullet: add at its end
"Entry names are UTF-8 where they say so or are valid UTF-8 (macOS does not say so), and code page 437 otherwise, as
the format specifies. A backslash separates paths as well as a slash, since Windows PowerShell 5.1 writes one."

## Touches
- `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/zip/ZipReader.kt`
- `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/source/ArchiveLocalSourceImpl.kt`
- `data/source/local/implementation/src/desktopTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/zip/ZipReaderJvmTest.kt`
- `data/source/local/implementation/src/desktopTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/source/ArchiveLocalSourceTest.kt`
- `data/source/local/implementation/CLAUDE.md`

## Depends on
None.
