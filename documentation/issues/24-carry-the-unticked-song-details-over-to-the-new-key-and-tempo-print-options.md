# Carry a user's unticked "Artist and song details" over to the new Key and Tempo print options instead of defaulting them to ticked

**Challenged:** amended — the knock-on also covers a later `{transpose}`'s key line (`keyChangeRows`), which 4.6.1 printed with `showChords` alone; and `PrintSettings.withinFeatures` is unaffected (it masks at use, the stored choice migrates once).

**Kind:** bug (settings migration)  ·  **Severity:** low  ·  **Platforms:** all
**Files:**
- `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/model/UserPreferencesDocument.kt`
- `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/mapper/UserPreferencesMappers.kt`
- `data/source/local/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/mapper/UserPreferencesMappersTest.kt`

## Problem

Before 1894ec1d5 (unreleased; 4.6.1 is the last tag), the PDF printed the key/transposition/capo line under a song's
heading and the tempo/time line (and every later tempo or time change) **only with `showMetadata`** — the export
screen's box labelled "Artist and song details" in 4.6.1 (`print_metadata`, now just "Artist"):

```kotlin
// PrintLayout.kt at 1894ec1d5^
if (options.showMetadata) {
    entry.artist?.takeIf { it.isNotBlank() }?.let { heading += wrapped(it) }
    entry.song?.let { metadata(it).takeIf(String::isNotBlank)?.let { heading += wrapped(it, detailStyle) } }
}
...
private fun timingRows(timing: ChordProBlock.Timing, width: Float): List<Row> = if (options.showMetadata) {
```

1894ec1d5 split them into `showKey` and `showTempo`, and the stored document defaults both to `true`:

```kotlin
internal data class PrintSettingsDocument(
    ...
    val showKey: Boolean = true,
    val showTempo: Boolean = true,
    ...
    val showMetadata: Boolean = true,
```

`PrintSettingsDocument.toModel()` copies them straight through (`showKey = showKey, showTempo = showTempo`). A 4.6.1
user who had unticked the details box — to print bare lyric sheets — has `"showMetadata": false` and no `showKey` /
`showTempo` in `preferences.json`, so after the update their PDFs suddenly carry key, capo, tempo and time lines again,
against a choice they made and that is still visibly unticked on the screen.

## Fix

Make the two new fields absent-aware and inherit the old box where they were never written:

```kotlin
// PrintSettingsDocument
/** Null in a document from before the key and the tempo had boxes of their own, when [showMetadata] printed them. */
val showKey: Boolean? = null,
/** As [showKey]. */
val showTempo: Boolean? = null,
```

```kotlin
// PrintSettingsDocument.toModel()
showKey = showKey ?: showMetadata,
showTempo = showTempo ?: showMetadata,
```

`toDocument()` keeps writing `settings.showKey` / `settings.showTempo` (non-null, so always present from the first save
on — `UserPreferencesDocumentFormat`'s `Json` does not encode defaults, and `null` is now the default, so a `true` is
written explicitly). `coerceInputValues = true` already reads a `null` in the file as absent. A new user gets `true` for
both through `showMetadata`'s own default, so "every box starts ticked" still holds.

One knock-on to be aware of: the setlist overview's `(Key: G)` after each title, and the `Key: X` line of a later
`{transpose}` (`keyChangeRows`), used to follow `showChords` alone (not `showMetadata`) and now follow `showKey`, so for
the same user the overview and the key-change lines lose their keys (they have them with today's default). In 4.6.1 the
heading's key/capo needed `showMetadata && showChords`; today's `withinFeatures` (`showKey && areChordsEnabled`) keeps
the feature gate, and the migrated value is written explicitly on the first preferences save, so it is decided once. Inheriting
`showMetadata` is still the right default — the heading lines are the bigger, more visible change — and the user can
tick Key again; no separate migration for the overview is worth a field.

## Tests

`UserPreferencesMappersTest`:
- A `PrintSettingsDocument` decoded from `{"showMetadata": false}` (through `UserPreferencesDocumentFormat.decode` of a
  preferences text holding that `printSettings`) maps to `showKey == false && showTempo == false`.
- `{"showMetadata": false, "showKey": true}` → `showKey == true`, `showTempo == false`.
- An empty `printSettings` → both `true`.
- The existing round-trip test (`printSettingsSurviveSavingAndReloadingPreferences`) still passes, and a round trip of
  `showMetadata = false, showKey = true, showTempo = true` keeps both `true`.

## Manual check

Install 4.6.1, untick "Artist and song details" on the export screen, export once; update to this build; open Export song: Key,
transposition and capo and Tempo and time signature are unticked and the PDF preview shows no key/tempo line under the
heading.
