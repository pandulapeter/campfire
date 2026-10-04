# Emit the song's info section under one key whatever its metadata, so the editor preview's cover waits for the typing to pause and crossfades

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongLyrics.kt, presentation/CLAUDE.md

**Challenged:** sound — checked: nothing else keys off the info section's key (`animations` and `SectionMeasurements` are indexed by unit position, sizes are pooled by content, the editor preview uses `SectionMotion.NONE`, so no glide or `animateBounds` there; on the details screen the card now springs to its new size after a sheet's Save, which is a change the user made).

## Problem

The editor preview's About the song card shows the cover through `rememberSettledCoverArtUrl` (SongMetadata.kt:242):

```kotlin
coverArtUrl = rememberSettledCoverArtUrl(metadata.coverArt.takeIf { editing?.onEditCoverArt != null }),
```

which (CoverArt.kt:140) starts at the address it is first given and only follows later ones after a 500 ms pause:

```kotlin
var settledUrl by remember { mutableStateOf(url) }
LaunchedEffect(url) {
    if (url != null) delay(COVER_ART_SETTLE_DELAY)
    settledUrl = url
}
```

But the card is composed inside a `key` that changes with every metadata edit. `SongLyrics` keys each section by its content hash (SongLyrics.kt:274–282, used at :347 as `key(unitKeys[unit])`):

```kotlin
val sectionKeys = remember(sections) {
    val occurrences = HashMap<RenderSection, Int>()
    sections.map { section ->
        val occurrence = (occurrences[section] ?: 0) + 1
        occurrences[section] = occurrence
        SectionKey(hash = section.hashCode(), occurrence = occurrence)
    }
}
```

and the info section is `data class Metadata(val metadata: ChordProMetadata)` (SongLyrics.kt:2125), so typing in `{meta: cover …}` (or any header directive) changes its hash, and the whole `SongMetadataSection` is torn down and composed afresh on every preview rebuild — every 150 ms typing pause (`PREVIEW_DELAY_MILLIS`, SongEditorScreen.kt:839/1136). Each fresh composition's `rememberSettledCoverArtUrl` starts *at* the half-typed address, so:

- every pause on a syntactically valid partial address (`https://e`, `https://ex.com/a`, …) is a cover download, and the failed ones are blocked from retrying for the session or a minute (`CoverArtRepository`), defeating the settle delay that exists exactly for this;
- `SongInfoCover`'s `AnimatedContent` (SongMetadata.kt:367) never animates in the preview: it is always on its first composition, so a cover appearing or being replaced snaps in, against the "every visible change is animated" convention.

The details screen is affected the same way whenever its metadata changes (after a sheet's Save), but there it happens once per save.

## Fix

Give the info section a key that does not depend on its content — a song has at most one, at the top. In `SongLyrics.kt` where `sectionKeys` is built:

```kotlin
val sectionKeys = remember(sections) {
    val occurrences = HashMap<Any, Int>()
    sections.map { section ->
        // The info section is the song's one header card: keyed by its content, every keystroke in a header directive
        // would compose it afresh, and whatever it remembers (the cover waiting for the typing to pause, the cover's
        // crossfade) would start over each time.
        val identity: Any = if (section is RenderSection.Metadata) RenderSection.Metadata::class else section
        val occurrence = (occurrences[identity] ?: 0) + 1
        occurrences[identity] = occurrence
        SectionKey(hash = identity.hashCode(), occurrence = occurrence)
    }
}
```

Nothing else needs to change: the section sizes are pooled by content separately (`SectionSizesPool`/`UnitContent`), so a metadata change is still measured; `SongMetadataSection` reads everything from its parameters (its only `remember` is keyed on `metadata.tags`). On the details screen the card now keeps its node across a save, so `animateBounds` moves it rather than it being replaced — the intended behaviour for an edited section.

Alternative considered: hoisting `rememberSettledCoverArtUrl` out into `SongPreview`. It fixes the downloads but not the crossfade, and the keyed unit would still recompose the card from scratch on each edit; the constant key fixes both.

Add one clause to the presentation/CLAUDE.md `SongLyrics.kt` bullet where it describes sections being emitted under keys of their content (search "key of its content" / "Equal sections are told apart"; if no such sentence exists, add it after the sentence about the song's first grid section being its info section): "the info section is emitted under one key whatever it says, so the cover in the editor preview's card waits for the typing to pause rather than following every half-typed address".

## Tests

None in code: the key is computed inside a composable, and the effect is a composition lifecycle. (If the executor prefers, extract the key computation into an `internal fun sectionKeysOf(sections: List<RenderSection>): List<Any>` and add a `RenderSectionsTest` case asserting two `Metadata` sections with different metadata get equal keys while two different `Lines` get different ones.)

## Manual check

Open a song in the editor, Preview or Split pane, with the About the song card visible and Cover art on. Type `{meta: cover https://coverartarchive.org/release-group/…/front-250}` one character at a time with short pauses: no cover request is made until you pause for half a second after the last keystroke (watch the desktop log or a proxy), and when the address is complete the cover fades in rather than appearing in one frame. Deleting the directive makes the cover fade out.
