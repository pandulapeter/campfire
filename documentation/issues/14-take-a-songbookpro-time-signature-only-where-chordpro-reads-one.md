# Take a SongbookPro time signature only where `ChordProTime` reads one

**Kind:** bug · **Severity:** low · **Platforms:** all
**Challenged:** sound
**Files:**
- `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/backup/SongbookProBackup.kt`
- `data/source/local/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/backup/SongbookProBackupTest.kt`

## Problem

The SongbookPro reader keeps its own idea of a time signature (`SongbookProBackup.kt:122` and `:187` at 1c52e5347):

```kotlin
directive("time", song.text("timeSig")?.takeIf { TIME_SIGNATURE.matches(it) })
…
private val TIME_SIGNATURE = Regex("""\d{1,2}/\d{1,2}""")
```

The app's rule is `ChordProTime.parse` (`chordpro/…/ChordProTime.kt`): 1–16 beats over a unit of 1, 2, 4, 8 or 16, and
`C`, `C|`, `¢`. The regex lets through `0/0`, `4/0`, `4/3`, `17/8`, which the rest of the app treats as no time
signature: the editor highlights the line as invalid, the Song defaults sheet and the click fall back to 4/4, and
`ChordProPrettifier` treats it as an invalid value. Probed at 1c52e5347: `"timeSig": "4/0"` is written as
`{time: 4/0}`. It also rejects `C`, which the app reads as 4/4. Small, but it imports a value the app itself would
never write.

## Fix

Replace the regex with the app's own parser (`:chordpro` is already an `implementation` dependency of this module, and
`ChordProDuration` is imported from it in the same file):

```kotlin
directive("time", song.text("timeSig")?.takeIf { ChordProTime.parse(it) != null })
```

and delete `TIME_SIGNATURE`. Write the value as SongbookPro stored it (trimmed), so `C` stays `C`; a value that
`ChordProTime.parse` accepts with spaces (`3 / 4`) may be written as `"${beats}/${unit}"` instead if the executor
prefers a canonical form — either is fine, since the parser reads both.

## Tests

In `SongbookProBackupTest`: songs with `timeSig` `"4/0"`, `"17/8"`, `"0/0"` get no `{time}` line; `"C"` gets `{time: C}`;
`"6/8"` gets `{time: 6/8}`. The existing header test (`3/4`) is unchanged.

## Manual check

None beyond the tests; the value only decides whether one header line is written.
