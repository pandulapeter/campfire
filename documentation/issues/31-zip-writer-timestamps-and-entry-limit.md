# 31 · Files unpacked from an exported archive are dated 1979 (or not at all), and an archive of more than 65,534 files would be written truncated

**Severity:** minor (all platforms; the dates show for every export that is unpacked outside the app, the entry count
needs a library no one has) · **Area:** `:data:source:local:implementation` (`zip/ZipWriter`, `ArchiveLocalSourceImpl`)

## Symptom

1. Settings -> Export library (or export a setlist, which is a zip too). Unpack `campfire_library.zip` with Archive
   Utility, Windows Explorer, `unzip` or Android's Files: every song and setlist comes out dated **30 November
   1979** (`unzip -l`, the JDK), **1 January 1980** or with no date at all, depending on the tool. In a folder sorted
   by date they sink below everything the user has, a backup tool sees them as older than any copy it holds, and
   copied into the desktop library folder by hand that is the `lastModified` the app reads back for each song.
2. A library of 65,535 files or more exports "successfully" into an archive whose end record says
   `count mod 65,536` entries. The app's own reader, and every other one, then sees a fraction of the library (or
   refuses the archive: a count of exactly `0xFFFF` is the ZIP64 marker). Not reachable with a real library today;
   it is a silent wrap in a format writer, which is why it is closed rather than left.

## Cause

`data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/zip/ZipWriter.kt`.
Both headers of every entry carry a zero date and time (`:29-30` local, `:46-47` central):

```kotlin
builder.u16(0) // Modification time.
builder.u16(0) // Modification date.
```

An MS-DOS date is `((year - 1980) shl 9) or (month shl 5) or day` with months and days counted from 1, so `0` is
month 0, day 0 of 1980 — not a date. Tools disagree on what to make of it, which is the spread above.

The end of central directory record takes the count as it comes (`:64-65`), and `ByteArrayBuilder.u16` writes the
low 16 bits of whatever it is given:

```kotlin
builder.u16(entries.size)
builder.u16(entries.size)
```

The name length has the same shape (`:34`, `:51`: `builder.u16(names[index].size)`), for a name over 65,535 bytes.

Everything else in the writer was checked and is right: the UTF-8 flag (bit 11) is set in both headers, the CRC and
both sizes are correct, there are no data descriptors, and "version made by" 20 / MS-DOS is fine with the UTF-8 flag.

## Fix

The `zip/` package stays what its `CLAUDE.md` calls it — dependency-free and pure. The timestamp enters it as two
integers; the clock and the calendar stay in the one caller.

1. **New file** `…/implementation/zip/DosTimestamp.kt` (MPL header copied from `ZipEntry.kt`):

   ```kotlin
   package com.pandulapeter.campfire.data.source.local.implementation.zip

   /**
    * A moment the way a zip archive stores it: the two 16 bit fields of an MS-DOS date and time. They are the local
    * time of whoever wrote the archive, with no zone, to the two seconds, and the years run from 1980 to 2107.
    */
   internal data class DosTimestamp(
       val date: Int,
       val time: Int,
   ) {

       companion object {

           /**
            * Midnight on 1 January 1980, the earliest moment the format can name. It is what an archive written without
            * a clock is dated rather than all zeroes: months and days are counted from one, so a zero date is no date,
            * and every tool that unpacks one makes up something else for it.
            */
           val EARLIEST = DosTimestamp(date = (1 shl 5) or 1, time = 0)

           private val LATEST = DosTimestamp(
               date = ((LAST_YEAR - FIRST_YEAR) shl 9) or (12 shl 5) or 31,
               time = (23 shl 11) or (59 shl 5) or 29,
           )

           /** A moment outside the years the format has becomes the nearest one it does have. Months and days count from 1. */
           fun of(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int) = when {
               year < FIRST_YEAR -> EARLIEST
               year > LAST_YEAR -> LATEST
               else -> DosTimestamp(
                   date = ((year - FIRST_YEAR) shl 9) or (month shl 5) or day,
                   // Five bits hold the seconds halved, and a leap second would otherwise be a thirtieth pair.
                   time = (hour shl 11) or (minute shl 5) or (second / 2).coerceAtMost(29),
               )
           }

           private const val FIRST_YEAR = 1980
           private const val LAST_YEAR = 2107
       }
   }
   ```

   (`FIRST_YEAR` / `LAST_YEAR` are `const val`s, so `LATEST` may use them before their declaration.)

2. **`…/zip/ZipWriter.kt`** — take the timestamp, and refuse what the format cannot say before any work is done:

   ```kotlin
   /**
    * Every entry is dated [modifiedAt]: the app keeps no modification time of its own for a song (the web has none
    * to keep), and what an export can honestly say about each file is when it was handed out.
    *
    * Throws [ZipException] for entries a plain archive cannot hold. ZIP64, which can, is deliberately not written:
    * the reader of this very app rejects it, and so do enough of the tools an export is unpacked with.
    */
   fun write(entries: List<ZipEntry>, modifiedAt: DosTimestamp = DosTimestamp.EARLIEST): ByteArray {
       // One short of what sixteen bits count to, because 0xFFFF in that field is what announces a ZIP64 archive.
       if (entries.size >= ZIP64_MARKER) {
           throw ZipException("An archive cannot hold ${entries.size} entries, the most is ${ZIP64_MARKER - 1}.")
       }
       val names = entries.map { it.name.encodeToByteArray() }
       names.forEachIndexed { index, name ->
           if (name.size > MAX_NAME_SIZE) {
               throw ZipException("The name of entry \"${entries[index].name.take(64)}…\" is too long for an archive.")
           }
       }
       val builder = ByteArrayBuilder(entries.sumOf { it.bytes.size + it.name.length * 2 + 128 })
       …
   ```

   The `val names` line moves up from where it is (it must not be computed twice); the rest of the body is unchanged
   except that the four zero fields become

   ```kotlin
   builder.u16(modifiedAt.time)
   builder.u16(modifiedAt.date)
   ```

   in the local header and again in the central one — **time first, then date**, which is the order the format has
   and the order the two comments being replaced are already in. Add to the constants:

   ```kotlin
   private const val ZIP64_MARKER = 0xFFFF
   private const val MAX_NAME_SIZE = 0xFFFF
   ```

   The default argument is there for the tests and keeps what they write deterministic; it is a *valid* date, so even
   a caller with no clock no longer writes a zero. Do not use `require(…)` as the report suggests: that is an
   `IllegalArgumentException`, which says "the caller has a bug", while this is a property of the data.

   **Why not ZIP64:** (1) `ZipReader.kt:31-33, 57-59` rejects it, so the app could not import its own export until the
   reader learned it too; (2) the other half of ZIP64 — sizes over 4 GiB — cannot happen here at all, since the whole
   archive is one `ByteArray` behind an `Int`-indexed `ByteArrayBuilder` and would be out of memory long before;
   (3) nothing else in the app is built for a library of that size (the scan, sync's listing, the lists), so the
   honest outcome is an export that says it failed, not an archive format that half the unpacking tools mishandle.

3. **`…/zip/ZipException.kt`** — the KDoc only speaks of reading. Make it: "Thrown when an archive is malformed,
   truncated, encrypted or uses a feature this minimal implementation does not support (ZIP64, compression methods
   other than STORED and DEFLATE) — and by the writer for entries that would need one of those features: more of them
   than sixteen bits count, or a name longer than that."

4. **`data/source/local/implementation/build.gradle.kts`** — `commonMain.dependencies` gains, in alphabetical place
   after `libs.kotlin.coroutines`:

   ```kotlin
   // The one place this module needs a calendar: an exported archive is dated in the local time of the device.
   implementation(libs.kotlin.datetime)
   ```

   `kotlinx-datetime` is already in the version catalog (`kotlin-datetime = "0.8.0"`) and already in every build
   through `:presentation` (`SyncSettings.kt` calls `TimeZone.currentSystemDefault()` on all four platforms, the web
   included), so this adds nothing to any binary. It is **not** a dependency of this module today — the report's
   "the caller can pass `Clock.System.now()`" is not enough on its own, since an `Instant` has no year or hour until
   a time zone is applied, and DOS time is local time. A hand-written UTC calendar was weighed and rejected: the
   files would come out hours in the future for everyone west of Greenwich.

5. **`…/source/ArchiveLocalSourceImpl.kt`** — `pack` (`:32-34`) supplies the moment. Add
   `@file:OptIn(ExperimentalTime::class)` above the `package` line (as `SyncRepositoryImpl.kt:10` has it) and the
   imports `kotlin.time.Clock`, `kotlin.time.ExperimentalTime`, `kotlinx.datetime.LocalDateTime`,
   `kotlinx.datetime.TimeZone`, `kotlinx.datetime.number`, `kotlinx.datetime.toLocalDateTime` and
   `…implementation.zip.DosTimestamp`:

   ```kotlin
   override suspend fun pack(files: Map<String, ByteArray>): ByteArray = withContext(Dispatchers.Default) {
       ZipWriter.write(
           entries = files.map { (name, bytes) -> ZipEntry(name = name, bytes = bytes) },
           modifiedAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).toDosTimestamp(),
       )
   }

   private fun LocalDateTime.toDosTimestamp() = DosTimestamp.of(
       year = year,
       month = month.number,
       day = day,
       hour = hour,
       minute = minute,
       second = second,
   )
   ```

   (`day` is the 0.8 name of what used to be `dayOfMonth`; `SyncSettings.kt` already uses it.) Do not touch `unpack`
   or the hidden-file filter in the same file: plans 15 and 27 own those.

6. **`data/source/local/api/…/ArchiveLocalSource.kt`** — the KDoc of `pack` becomes:

   ```kotlin
   /**
    * Packs the given entries (name to content, the names may contain directories) into a zip archive, each of them
    * dated now. Throws when there are more entries than a zip archive counts (65,534): a second archive format is
    * not worth a library nothing else in the app could hold either.
    */
   ```

   `ArchiveRepository.pack` ("See `ArchiveLocalSource.pack`") and the three export use cases need no change: the
   exception travels up as it is to `CampfireViewModel.save` (`:1228-1236`), whose `catch (exception: Exception)`
   sends `Message.ExportFailed` — "Export failed" / "Az exportálás nem sikerült". That *is* the report: the export
   says it failed instead of handing out a truncated archive. A message of its own was weighed and left out — it
   would cost an exception type in `:data:model`, a `Message`, a branch in `CampfireApp` and two strings, all in files
   half the other plans edit, for a library of 65,535 files. No new strings.

## Tests

All in `:data:source:local:implementation`.

- `commonTest`, new class `zip/DosTimestampTest` (camelCase names, `internal class`, as its neighbours):
  - `encodesADateAndTime` — `of(2026, 9, 21, 14, 37, 58)` → `date == 0x5D35`, `time == 0x74BD`.
  - `halvesTheSecondsRoundingDown` — seconds `0`, `1`, `59` → the low five bits of `time` are `0`, `0`, `29`.
  - `aLeapSecondStaysInsideTheField` — second `60` → low five bits `29`, minute bits unchanged.
  - `theEarliestMomentIsARealDate` — `EARLIEST.date == 0x21`, `EARLIEST.time == 0`, and
    `of(1980, 1, 1, 0, 0, 0) == EARLIEST`.
  - `aYearBeforeTheFormatBecomesItsFirstMoment` — `of(1970, 1, 1, 12, 0, 0) == EARLIEST`.
  - `aYearAfterTheFormatBecomesItsLastMoment` — `of(2108, 1, 1, 0, 0, 0)` → `date == 0xFF9F`, `time == 0xBF7D`; and
    `of(2107, 12, 31, 23, 59, 59)` equals it.
- `commonTest`, `zip/ZipRoundTripTest` — add (the three existing cases stay as they are, which pins that the default
  argument keeps them compiling). `archive.u16(position)` / `u32(position)` from `LittleEndian.kt` are visible here;
  the central directory starts at `archive.u32(archive.size - 22 + 16).toInt()`:
  - `datesBothHeadersOfEveryEntry` — two entries, `modifiedAt = DosTimestamp.of(2026, 9, 21, 14, 37, 58)`. Walk the
    local headers (time at `+10`, date at `+12`, next header at `+30 + nameLength + size`) and the central ones (time
    at `+12`, date at `+14`, next at `+46 + nameLength`): all four pairs are `0x74BD` / `0x5D35`. `ZipReader.read` of
    the same archive still returns both entries.
  - `datesAnArchiveWrittenWithoutAClock` — `ZipWriter.write(entries)` → every date field is `0x21`, none is `0`.
  - `writesAndReadsBackAsManyEntriesAsTheFormatCounts` — 65,534 entries named `"$index.cho"` with empty content →
    `ZipReader.read(archive).size == 65_534`, first and last names match.
  - `refusesMoreEntriesThanTheFormatCounts` — 65,535 such entries → `assertFailsWith<ZipException>`; and 70,000
    (which would wrap to 4,464) fails the same way.
  - `refusesANameTheFormatCannotHold` — one entry named `"a".repeat(65_536) + ".cho"` → `ZipException`; a name of
    exactly 65,535 bytes is written and read back.
- `desktopTest`, `zip/ZipReaderJvmTest` — the JDK as the second opinion, zone-free because `getTimeLocal` reads the
  DOS fields as they are:
  - `jvmReadsTheDateTheZipWriterWrote` — write `contents` with `DosTimestamp.of(2026, 9, 21, 14, 37, 58)`; through
    `ZipInputStream` (local headers) every `entry.timeLocal == java.time.LocalDateTime.of(2026, 9, 21, 14, 37, 58)`;
    then write the bytes to a temp file and assert the same through `java.util.zip.ZipFile(file).entries()` (central
    directory). Delete the file in a `finally`.
- `desktopTest`, `source/ArchiveLocalSourceTest`:
  - `` `a packed archive is dated when it was packed` `` — take `java.time.LocalDateTime.now()` before and after
    `ArchiveLocalSourceImpl().pack(mapOf("songs/a.cho" to "{title: A}".encodeToByteArray()))`; the entry's
    `timeLocal` is not before `before.minusSeconds(2)` (the format rounds down to two seconds) and not after `after`.
    Follow the naming style that file already uses.

## Verify

1. `./gradlew :data:source:local:implementation:desktopTest`, then the three compile checks — the new dependency has
   to resolve for Android, both iOS targets and wasmJs, so none of them may be skipped.
2. `./gradlew :app:desktop:run`, Settings -> Export library, then `unzip -l campfire_library.zip`: every line carries
   today's date and the current local time (to two seconds), not `11-30-1979 00:00`. Unpack it in Finder / Explorer
   and check the dates there too.
3. Import that archive into an empty library (or a second desktop data folder): everything comes back, which shows
   the reader is unbothered by the new fields.
4. Repeat step 2 on the web build (`:app:web:wasmJsBrowserDevelopmentRun`), where the time zone comes from the
   browser: the time in the listing must be the local one, not UTC. Export a single setlist on Android or iOS and open
   the zip in the Files app: dated today.
5. The entry limit has no manual check; the tests are the check.

## Docs

- `data/source/local/implementation/CLAUDE.md`, the **`zip/`** bullet: after "`ZipWriter` (STORED only — song text
  compresses badly enough not to be worth it)" add: ", every entry dated with the `DosTimestamp` it is given — the
  moment of the export in the device's local time, which `ArchiveLocalSourceImpl` takes from kotlinx-datetime so
  that the package itself stays dependency-free and pure — and more than 65,534 entries refused with a
  `ZipException` rather than written as ZIP64, which the reader rejects too". The "Tested with" sentence gains "the
  DOS timestamp, the entry limit" in its `commonTest` list.
- `data/source/local/api/CLAUDE.md`, the `ArchiveLocalSource` bullet: add "Packing dates every entry now and throws for
  more entries than a plain zip counts."
- Root `CLAUDE.md`: nothing (it says only that the module "holds the pure-Kotlin zip reader/writer", still true).

## Touches

- `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/zip/DosTimestamp.kt` (new)
- `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/zip/ZipWriter.kt`
- `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/zip/ZipException.kt`
- `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/source/ArchiveLocalSourceImpl.kt`
- `data/source/local/implementation/build.gradle.kts`
- `data/source/local/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/api/ArchiveLocalSource.kt`
- `data/source/local/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/zip/DosTimestampTest.kt` (new)
- `data/source/local/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/zip/ZipRoundTripTest.kt`
- `data/source/local/implementation/src/desktopTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/zip/ZipReaderJvmTest.kt`
- `data/source/local/implementation/src/desktopTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/source/ArchiveLocalSourceTest.kt`
- `data/source/local/implementation/CLAUDE.md`
- `data/source/local/api/CLAUDE.md`

## Depends on

Nothing. Shares `ArchiveLocalSourceImpl.kt`, `ArchiveLocalSource.kt` and `ArchiveLocalSourceTest.kt` with plans 15 and
27, which both rework `unpack` and its KDoc; this plan only edits `pack`, its KDoc and the imports, so the three can
land in any order, one after the other. Plan 15 also edits `ZipReader.kt` and `ZipReaderTest.kt`, which this plan
leaves alone.
