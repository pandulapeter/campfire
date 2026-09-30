# Bring the presentation docs up to date with the website and the tags dialog

**Challenged:** amended — the KDoc's `[tags]` does not resolve: `DialogType.SongTags` has only `song`, and the `tags` property near it belongs to `LabelsOnEverySong`; the new KDoc sentence must not link it.

**Kind:** docs  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/CLAUDE.md` (line 50), `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`
(the `DialogType.SongTags` KDoc)

## Problem

Two sentences describe what the code no longer does (at 9ab7ca54e).

`presentation/CLAUDE.md` line 50, the Settings paragraph, ends:

> **GitHub is the app's website**, and the author row is the link to the author's own site.

The root `CLAUDE.md`'s About section (edited in this range) says campfire-songbook.com is the project's website and
GitHub is the source code and the issue tracker, and `SettingsScreen.kt` has `WEBSITE_URL = "https://campfire-songbook.com/"`.

`CampfireViewModel.kt` lines 2907–2911:

```kotlin
/**
 * Opened from the tag header of the song details screen or from a row of the song list; what it offers besides
 * the song's own tags is [tags].
 */
data class SongTags(val song: Song) : DialogType
```

The row of the song list no longer opens it: the list rows' tag chips became filter taps in this range, and the one
caller is `SongDetailsScreen.kt` (the tag header's Manage tags chip). The dialog is also "Manage tags" now, not the
"Add tag" it was.

## Fix

In `presentation/CLAUDE.md` line 50, make the sentence "**campfire-songbook.com is the app's website** and GitHub its
source code and issue tracker; the author row is the link to the author's own site", keeping the rest of the line
as it is (the file's paragraphs are one line each; edit in place, do not re-wrap). In the KDoc, "Opened from the tag
header of the song details screen; what it offers besides the song's own tags is [tags]." `[tags]` names nothing on `SongTags` (it has only `song`), so drop the clause that links it: "Opened from the tag header of the song details screen (the Manage tags chip), and offers the song's own tags and the rest of the library's."

## Tests

None; documentation only.

## Manual check

None.
