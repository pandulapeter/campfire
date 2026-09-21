# 35 · Two setlists (or songs) that tie in the sort order trade places whenever one of them is changed

**Severity:** minor (all platforms; certain once two items tie — two setlists created offline on two devices get the
same priority, every hand-written setlist has priority 0, and two arrangements of one song share title and artist)
· **Area:** `:domain:implementation` (`GetScreenDataUseCaseImpl`), `:presentation` (`SongPicker` in `Dialogs.kt`)

## Symptom

1. Two devices each create a setlist while offline. Both get `priority = highest + 1`, which is the same number.
   After a sync both devices hold both. (Or: copy two hand-written `*.setlist.json` files into the library; neither
   carries a priority, so both are 0.)
2. On the Setlists screen, sorted **Newest first**, tap a transposition inside the upper one, reorder it, or add a
   song to it. The two setlists swap places under the finger. Do the same to the other one: they swap back. A refresh
   or the next sync run puts them in file name order again, whichever was on top.
3. The same with **By title** for two setlists whose titles fold to the same text (`Summer set` / `Summer Set!`), on
   the Songs screen for two songs with the same title and artist (`…-amazing_grace.cho` / `…-amazing_grace_2.cho`)
   whenever either is saved or tagged, and in the song picker of a setlist.
4. A lesser relative: a tag spelled `rock` in one song and `Rock` in another is one chip, labelled the way "the
   first song that carries it" spells it — and which song is first changes the same way, so the chip changes its
   capitals after an edit to a song and changes back after the next rescan.

## Cause

The repositories' cached lists are in no particular order. A scan leaves them in file name order (every
`FileStorage.list` sorts by name), but every write moves the item it wrote to the end
(`SongRepositoryImpl.kt:52`, `SetlistRepositoryImpl.kt:50`, and the same shape in `renameSetlist`, `renameSong`):

```kotlin
updateData { current -> current.orEmpty().filterNot { it.fileName == setlist.fileName } + setlist }
```

`sortedWith` is stable, so wherever a comparator ties, that order shows through.
`domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/GetScreenDataUseCaseImpl.kt:118-123`:

```kotlin
UserPreferences.SetlistSortingMode.NEWEST_FIRST -> compareBy<Setlist> { it.isArchived }.thenByDescending { it.priority }
UserPreferences.SetlistSortingMode.BY_TITLE -> compareBy<Setlist> { it.isArchived }.thenBy { normalizeText(it.title) }
```

`:208-216` (as of `29820b93`; plan 03 replaces these two with one `SortableSong.ORDER`, which ties the same way):

```kotlin
UserPreferences.SortingMode.BY_ARTIST -> compareBy<SortableSong>({ it.artist }, { it.title })
UserPreferences.SortingMode.BY_TITLE -> compareBy<SortableSong>({ it.title }, { it.artist })
```

`:194-201`, the tags: `tagsByName[name] = … ?: Tag(name = tag, songCount = 1)` keeps the spelling of whichever song
the list has first, and `compareByDescending<Tag> { it.songCount }.thenBy { normalizeText(it.name) }` ties for two
tags that differ only by an accent (`Ének` / `Enek` are two tags — tags fold case, not accents — with one sort key).

`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/Dialogs.kt:930`, the song
picker, sorts `viewModel.allSongs` — `ScreenData.unfilteredSongs`, the repository's list as it is:

```kotlin
pickable.filterNot { it.song.fileName in initial }.sortedWith(compareBy({ it.title }, { it.artist }))
```

Checked and already total, so that nobody goes looking again: the setlist picker (`Dialogs.kt:824`, which
`29820b93` gave a `fileName` tie-breaker), `toLanguages()` (ends in `{ it.code }`, which is unique), the language
picker (`Languages.kt:85`, sorted from a fixed list of codes), and the search ranking
(`CampfireViewModel.filterAndRank`, a stable sort over the list the domain layer sorted, so it is exactly as
deterministic as that list is). None of the comparators can violate its contract: every key is a precomputed or pure
`String`, `Int` or `Boolean`.

## Fix

**Plan 03 lands first.** It replaces `sortSongs` and the two song comparators with `sortIntoSections` and a single
`SortableSong.ORDER`, precisely so that there is one place to append to. Step 2 is written against that.

The rule: every comparator that orders library items ends in the file name, which is the identity of a song and of
a setlist and therefore never ties. Do **not** fix this in the repositories by keeping their lists sorted: the order
of a cache is nobody's contract, and the next writer would break it again.

1. `GetScreenDataUseCaseImpl.sortSetlists` — extend the KDoc and both branches:

   ```kotlin
       /**
        * The setlists in the order the screen lists them. The archived ones come last whichever order that is: they are
        * only on the screen at all because the user asked to see what has been put away, and mixing them in among the
        * setlists still in use would undo the putting away.
        *
        * Both orders end in the file name, which never ties. Priorities do - two devices that each made a setlist
        * while offline gave them the same one, and a hand written file has none - and so do titles, and what decides a
        * tie otherwise is the order of the repository's list, where a setlist moves to the end every time it is
        * written: the two would trade places on the screen whenever one of them was touched.
        */
       private fun List<Setlist>.sortSetlists(listPreferences: ListPreferences) = sortedWith(
           when (listPreferences.setlistSortingMode) {
               UserPreferences.SetlistSortingMode.NEWEST_FIRST -> compareBy<Setlist> { it.isArchived }.thenByDescending { it.priority }
               UserPreferences.SetlistSortingMode.BY_TITLE -> compareBy<Setlist> { it.isArchived }.thenBy { normalizeText(it.title) }
           }.thenBy { it.fileName }
       )
   ```

2. `GetScreenDataUseCaseImpl`, `SortableSong.ORDER` (plan 03's step 3) — one more selector, and one more sentence at
   the end of its KDoc:

   ```kotlin
               /**
                * … (plan 03's paragraph, unchanged)
                *
                * The file name comes last because it is the one thing two songs cannot share: two arrangements of a
                * song tie on everything before it, and would otherwise be listed in the order of the repository's
                * list, where a song moves to the end every time it is saved.
                */
               val ORDER = compareBy<SortableSong>({ it.initial != null }, { it.sectionKey }, { it.primaryText }, { it.secondaryText }, { it.song.fileName })
   ```

   If this plan is nevertheless applied to the file as it is at `29820b93`, the same selector goes at the end of both
   comparators in `sortSongs` (`compareBy<SortableSong>({ it.artist }, { it.title }, { it.song.fileName })` and its
   mirror image), and plan 03 then carries it over into `ORDER`.

3. `GetScreenDataUseCaseImpl.toTags` — the spelling is that of the first song *by file name*, which is what "the
   first song" has meant after every scan, and the order ends in the name itself:

   ```kotlin
       /**
        * The tags of the library with the number of songs carrying each, most used first. Two spellings of the same word
        * are one tag, shown the way the song that comes first by file name spells it - by file name rather than by where
        * the song is in the list, because the repository's list is in no particular order (a song that was just saved is
        * at its end), and the chip would change its capitals after an edit to a song and back after the next rescan.
        */
       private fun List<Song>.toTags(): List<Tag> {
           val tagsByName = linkedMapOf<String, SpelledTag>()
           forEach { song ->
               song.tags.forEach { tag ->
                   val spelled = tagsByName.getOrPut(tag.lowercase()) { SpelledTag(name = tag, fileName = song.fileName) }
                   spelled.songCount++
                   if (song.fileName < spelled.fileName) {
                       spelled.name = tag
                       spelled.fileName = song.fileName
                   }
               }
           }
           return tagsByName.values
               .map { Tag(name = it.name, songCount = it.songCount) }
               // Tags fold case but not accents, so two of them can share the text they are sorted by.
               .sortedWith(compareByDescending<Tag> { it.songCount }.thenBy { normalizeText(it.name) }.thenBy { it.name })
       }

       /**
        * A tag while it is being counted.
        *
        * @param fileName The song [name] is spelled after: the first by file name of the ones counted so far.
        */
       private class SpelledTag(
           var name: String,
           var fileName: String,
           var songCount: Int = 0,
       )
   ```

   `Tag` is `:data:model`'s (`data/model/.../domain/Tag.kt`) and does not change. The counting is unchanged too (a
   song that carries both spellings still counts twice, as today).

4. `Dialogs.kt:930`, `SongPicker`:

   ```kotlin
               pickable.filterNot { it.song.fileName in initial }.sortedWith(compareBy({ it.title }, { it.artist }, { it.song.fileName }))
   ```

   Leave the picker's order otherwise as it is — plain alphabetical over the folded text, symbols where the string
   order puts them. It has no section headers, so plan 03's "whatever starts with no letter comes first" is not needed
   there, and a list to find a song in is not obliged to agree with the Songs screen about where `¿` goes.

## Tests

None: `:domain:implementation`'s use cases and the UI are untested, and `GetScreenDataUseCaseImpl` takes three
repositories to construct. (Plan 02 gives the module a `commonTest` source set for `ImportPlanner`; if the sorting is
ever extracted into something pure the way 02 extracts the planning, the cases are: two setlists with equal priority
in both input orders give one output order; two songs with equal title and artist likewise; songs `b.cho` tagged
`rock` and `a.cho` tagged `Rock` in both input orders give one `Tag("Rock", 2)`.)

## Verify

1. `./gradlew :app:desktop:run`. Create two setlists, close the app, and edit both files under
   `library/setlists/` so that they carry the same `"priority"`. Start the app, sort **Newest first**: the two are in
   file name order. Add a song to the upper one, tap a transposition in it, archive and unarchive it: it stays where
   it was. Before the fix it drops below the other on the first change.
2. Name them `Summer set` and `Summer Set!`, sort **By title**, repeat: no swap.
3. Create two songs with the same title and artist (the second becomes `…_2.cho`), in either sorting mode toggle a
   tag on the first from the song details header: the rows do not swap. Open the song picker of a setlist that holds
   neither: the two are in the same order there as on the Songs screen, and stay so after the tag change.
4. Tag `a.cho` with `Rock` and `b.cho` with `rock`. The filter chip says `Rock (2)`. Save `a.cho` from the editor:
   still `Rock`. Before the fix it turns into `rock` until the next refresh.
5. Compile checks for all four targets.

## Docs

- `domain/implementation/CLAUDE.md`, the `GetScreenDataUseCaseImpl` bullet: after "orders the setlists (newest first
  or by title, the archived ones after the rest either way)" add "— every order it produces ends in the file name,
  because the repositories' lists are in no particular order (a written item moves to the end) and a tie would
  otherwise be decided by it".
- `chordpro/CLAUDE.md`, the tags paragraph: "the library shows the spelling of the first song that carries it"
  becomes "the library shows the spelling of the song that comes first by file name".
- `data/repository/implementation/CLAUDE.md`, the paragraph that starts "Writing is deliberately **not** part of that
  shape": after the sentence about `updateData` add "The cached lists are in no particular order — a scan leaves them
  by file name, a write moves its item to the end — and ordering them is the domain layer's business."

## Touches

- `domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/GetScreenDataUseCaseImpl.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/Dialogs.kt`
- `domain/implementation/CLAUDE.md`
- `chordpro/CLAUDE.md`
- `data/repository/implementation/CLAUDE.md`

## Depends on

03 (it rewrites the song comparators into `SortableSong.ORDER`, which step 2 appends to; 03 names this plan as
landing after it). Shares `Dialogs.kt` with 33 and 36, which edit other dialogs in that file (`EditSetlist`'s
`onConfirm`, the confirm buttons); no overlap in lines.
