# Title a shared or pasted song that declares no title by the page title it was shared with

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** Android, iOS (shares carry a subject); every platform for the
editor path
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/state/importing/PastedSongController.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/state/importing/ImportController.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/state/importing/ImportRequest.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/SharedTextName.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/EditorSession.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt` (the wiring of
`openNewSongEditor`),
`domain/api/src/commonMain/kotlin/com/pandulapeter/campfire/domain/api/songs/DraftSongFromTextUseCase.kt`,
`domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/songs/DraftSongFromTextUseCaseImpl.kt`,
`domain/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/domain/implementation/songs/DraftSongFromTextUseCaseImplTest.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/state/importing/PastedSongControllerTest.kt`,
`.../commonTest/.../ui/state/importing/ImportControllerQueueTest.kt` (its `openNewSongEditor` stub),
`.../commonTest/.../ui/screens/songEditor/EditorSessionTest.kt` (its `DraftSongFromTextUseCase` fake),
`.../commonTest/.../ui/platform/SharedTextNameTest.kt`,
`presentation/CLAUDE.md` (the "Text and document imports" bullet)

**Challenged:** amended — the suggestion travels through `PastedSongController`'s `enqueueImport` lambda and
`ImportController.enqueueImport` (the draft had the controller set `ImportRequest` directly, which it never sees); a
bare " by " is not split ("Stand by Me Lyrics" would give the title "Stand" and the artist "Me"): only the
`<title> <page kind> by <artist>` shape is; the whitespace the page-kind removal leaves is collapsed; the stub the test
was to use lives in `ImportControllerQueueTest`, not `PastedSongControllerTest`; the two test doubles the signature
changes break are named; no default on the use case parameters (one caller, one fake). Both platforms carry a subject:
Android's `EXTRA_SUBJECT` (`CampfireMainActivity`) and iOS's `attributedTitle` (`ShareInboxWriter.subject`, through
`share.json`).

## Problem

A browser's share carries the page title as the subject ("Riverlight Chords by The Invented Band @ Ultimate-Guitar.Com").
`sharedTextName(subject)` in `SharedTextName.kt` already strips the site, the version and the page kind from it, but
the result is only used as the `.txt` *file name* of the import (normalized to `riverlight`, lowercase with
underscores), and the editor path throws it away:

```kotlin
// PastedSongController
fun newSongFromText(text: String, subject: String? = null): Deferred<Unit> = when {
    …
    else -> enqueueImport(sharedTextsToImportedFiles(listOf(text), subject), true)
}
// ImportController.showNewSongEditor
openNewSongEditor(song.text)
// EditorSession.openNewSongEditor
val draft = editorTextOf(draftSongFromText(text = text, placeholderTitle = placeholderTitle, placeholderArtist = …))
```

So a lyrics-only share, or any share whose text the converter finds no title in (plan 03 narrows this), opens the
editor with the placeholder title "New song" selected, while the sender told us the title. The same for a paste whose
text names nothing (no subject there; nothing to do).

## Fix

1. `SharedTextName.kt`: add `internal fun sharedTextTitle(subject: String?): SharedTextTitle?` (`data class
   SharedTextTitle(val title: String, val artist: String?)`), which does what `sharedTextName` does up to the `trim`
   and then collapses the runs of spaces the removed words leave (`"Riverlight   by The Invented Band"`), keeping the
   case. `sharedTextName` calls it and `cleanedForFileName`s the title, so the file name is unchanged. The artist is
   split off **only** where the subject reads `<title> <page kind> by <artist>` before the page kind is removed (match
   `^(.+?)\s+$PAGE_KIND\s+by\s+(.+)$` on the part before ` @ ` / ` | `): "Stand by Me Chords by Ben E. King" →
   "Stand by Me" / "Ben E. King", while "Stand by Me Lyrics" or "Stand by Me" keeps its whole title and no artist.
   Never split on a dash: both orders are common.
2. Carry it to the import: `PastedSongController.enqueueImport` becomes `(files, isNewSongFromText, suggestion:
   SharedTextTitle?) -> Deferred<Unit>`; `newSongFromText(text, subject)` passes `sharedTextTitle(subject)` (null for a
   paste, which has no subject); `importSharedText`'s other branches pass null. `ImportController.enqueueImport` gains
   `suggestion: SharedTextTitle? = null` (a plain class, no Koin rule) and stores it on `ImportRequest` as `val
   suggestion: SharedTextTitle? = null`; update the lambda in `CampfireViewModel` (line ~503). Two short strings on a
   request are fine against the `ImportController` note about not keeping bytes reachable.
3. `ImportController.showNewSongEditor` passes `request.suggestion` along: the constructor's
   `openNewSongEditor: suspend (text: String) -> Unit` becomes `suspend (text: String, suggestion: SharedTextTitle?) ->
   Unit`, wired in `CampfireViewModel` (line ~485).
4. `EditorSession.openNewSongEditor(text, suggestion)` hands the title and artist to the use case, and selects the
   title the draft ends up with: `placeholderTitleSelection(draft, suggestion?.title ?: placeholderTitle)` (it finds
   `{title: <that>}`, or answers `TextRange.Zero` where the text declared its own), so a suggested title is typed over
   in one go as the placeholder is.
5. `DraftSongFromTextUseCase.invoke(text, placeholderTitle, placeholderArtist, suggestedTitle: String?,
   suggestedArtist: String?)`, no defaults (one caller and one fake; a default on the interface would be allowed, a
   constructor default on the `@Factory` class would not): a suggested value is written in place of the placeholder for
   a field the text does not declare; what the text declares wins, as today.

## Tests

- `DraftSongFromTextUseCaseImplTest`: `a shared page title titles a song that declares none` (title and artist
  suggested → both written; text declaring `{title}` → the suggestion is ignored for the title, used for the artist).
- `SharedTextNameTest`: `sharedTextTitle` keeps the title's case and single spaces, splits `X Chords by Y`, and does
  not split `Stand by Me Lyrics`; `sharedTextName`'s existing cases are unchanged.
- `PastedSongControllerTest`: `a share's subject is queued as the suggestion` (the UG-shaped subject reaches the
  `enqueueImport` lambda as "Riverlight" / "The Invented Band"; a paste queues null).
- `ImportControllerQueueTest`: `a new song from a share opens the editor with its suggestion` (the `openNewSongEditor`
  stub receives it).
- `EditorSessionTest`: update the fake to the new signature; `a suggested title is selected in a new song's editor`.
