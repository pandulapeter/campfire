# Record what each setlist held when the selection's Choose setlists sheet is first tapped on it, so a setlist created or synced in while it is open is not emptied by two taps

**Challenged:** amended — the record is taken at a setlist's first tap rather than when it is first listed: `createSetlistWithSongs` writes the new setlist empty (`createSetlist`, which puts it in the list) and only then with the songs (`saveSetlist`), so "first listed" could record the empty intermediate and leave Scenario A as broken as before. Until its first tap a setlist's box follows the library; the opening snapshot and the `LaunchedEffect` are gone.

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/SelectionSetlistsSheet.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/setlists/SetlistSongToggle.kt`, `presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/screens/setlists/SetlistSongToggleTest.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/CLAUDE.md`

## Problem

`SelectionSetlistsSheet` records what every setlist held of the selection once, as the sheet opens, and judges every
box and every tap by that record (`SelectionSetlistsSheet.kt` at d650f3cb0):

```kotlin
val heldAtOpening by rememberSaveable(stateSaver = fileNamesBySetlistSaver) {
    mutableStateOf(setlists.associate { setlist -> setlist.fileName to setlist.entries.map { it.songFileName }.filter { it in selection }.toSet() })
}
...
val held = heldAtOpening[setlist.fileName].orEmpty()
...
isEnabled = !setlist.isArchived && held.size < selection.size,
onClick = {
    if (addedHere.isEmpty()) {
        viewModel.addSongsToSetlist(songFileNames = selection, setlistFileName = setlist.fileName)
        added = added + (setlist.fileName to selection.filterNot { it in held }.toSet())
    } else {
        viewModel.takeSongsOutOfSetlist(songFileNames = addedHere, setlistFileName = setlist.fileName)
        added = added - setlist.fileName
    }
},
```

(L62–64, L112, L118–126.) A setlist that is not in `heldAtOpening` is treated as holding none of the songs, whatever it
holds.

Scenario A (no sync needed): tick three songs, open Choose setlists, tap New setlist, name it, Create. The naming
sheet closes back onto Choose setlists (`closeNamingDialog` only sets `isNamingNewSetlist = false`), and the new
setlist — created by `createSetlistWithSongs` holding all three — is listed with an empty, enabled box. A tap records
all three as added by this sheet (`withSongsAdded` writes nothing, since they are all there) and ticks it; a second tap
calls `takeSongsOutOfSetlist` with all three and empties the setlist the user just made. The KDoc's promise, "A song
the setlist held already stays whatever is tapped", is broken for it.

Scenario B: a sync run brings in a setlist that already holds some of the ticked songs while the sheet is open: same
outcome for those songs. A sync run that adds one of the ticked songs to a setlist the sheet already listed, before the
user taps it, is the same again: the snapshot from the opening still says the setlist lacks it.

Why not "record a setlist when the sheet first lists it": `SetlistsController.createSetlistWithSongs` is two writes —
`createSetlist` writes the setlist empty and puts it into the list (`SetlistRepositoryImpl.createSetlist`:
`putInCache(created)`), then `saveSetlist` writes it with the songs. Between them is a file write, during which a frame
can show the new setlist holding nothing (most likely on the web, where OPFS writes are slowest); a record taken on that
frame would freeze "holds none" and Scenario A would be back.

## Fix

The record only has to be frozen once the sheet itself changes a setlist — from then on the library lags behind the
sheet's own writes, which is what the record is for. Before the first tap, the library *is* what the setlist holds.

1. In `SetlistSongToggle.kt`, next to `selectionBoxState`, add two pure helpers:

   ```kotlin
   /**
    * What [setlist] holds of [selection] for the selection's Choose setlists sheet: what it held when the sheet was first
    * tapped on it, as [recorded] keeps it, or what it holds now where the sheet has not touched it yet - a setlist the
    * sheet has not written to is what the library says it is, however it got there (created from the sheet's own New
    * setlist, or synced in or changed by a sync run while the sheet is open).
    */
   internal fun heldOf(recorded: Map<String, Set<String>>, setlist: Setlist, selection: Collection<String>): Set<String> =
       recorded[setlist.fileName] ?: setlist.entries.mapNotNullTo(hashSetOf()) { entry -> entry.songFileName.takeIf { it in selection } }

   /** [recorded] with [setlist] in it, as [heldOf] reads it now, where it is not in it yet: called by the sheet's tap. */
   internal fun withHeldRecorded(recorded: Map<String, Set<String>>, setlist: Setlist, selection: Collection<String>): Map<String, Set<String>> =
       if (setlist.fileName in recorded) recorded else recorded + (setlist.fileName to heldOf(recorded, setlist, selection))
   ```

2. In `SelectionSetlistsSheet`, replace `heldAtOpening` with
   `var heldWhenTapped by rememberSaveable(stateSaver = fileNamesBySetlistSaver) { mutableStateOf(emptyMap<String, Set<String>>()) }`.
   Read it everywhere through `heldOf(heldWhenTapped, setlist, selection)`: `stateOf` takes the `Setlist` rather than its
   file name, `pickableSetlists` filters on `heldOf(...).isNotEmpty()` for an archived one (its `remember` keys become
   `setlists, heldWhenTapped`), and the row's `held`. In the row's `onClick`, record first and decide on the recorded
   value:

   ```kotlin
   onClick = {
       heldWhenTapped = withHeldRecorded(heldWhenTapped, setlist, selection)
       val heldHere = heldWhenTapped.getValue(setlist.fileName)
       if (addedHere.isEmpty()) {
           viewModel.addSongsToSetlist(songFileNames = selection, setlistFileName = setlist.fileName)
           added = added + (setlist.fileName to selection.filterNot { it in heldHere }.toSet())
       } else {
           viewModel.takeSongsOutOfSetlist(songFileNames = addedHere, setlistFileName = setlist.fileName)
           added = added - setlist.fileName
       }
   },
   ```

   The record of a setlist is never dropped (not when its added songs are taken out either), so the quick
   untick-then-tick, whose take-out has not round-tripped yet, re-adds exactly what the sheet took out, as today. With
   this, Scenario A's setlist is ticked and disabled as soon as it holds the songs (it cannot be tapped while it does),
   and Scenario B's is judged by what it holds when tapped. A setlist created empty and filled a moment later may show an
   empty box for the frame or two in between; that is the library's own two writes, and nothing can be recorded wrong
   from it unless it is tapped within those milliseconds.
3. Update the KDoc of `SelectionSetlistsSheet` ("A setlist that held all of the songs as the sheet opened is ticked and
   disabled…": "A setlist that holds all of the songs, and that the sheet has not added to, is ticked and disabled…";
   "The boxes follow the sheet's own record…": the record is of what each setlist held when the sheet was first tapped on
   it, and a setlist not tapped yet follows the library) and of `selectionBoxState` ("[held] is what it held when the
   sheet opened" becomes "[held] is what [heldOf] says it holds").
4. `ui/dialogs/CLAUDE.md`, section "The selection mode's sheets", first paragraph only: change "following the sheet's
   own record of what each setlist held as it opened and what the sheet added since rather than the library" to
   "following what each setlist holds until the sheet is first tapped on it, and from then on the sheet's own record of
   what it held then and what the sheet added since rather than the library, which lags behind the sheet's writes", and
   "A setlist that held them all is ticked and disabled" to "A setlist that holds them all, untouched by the sheet, is
   ticked and disabled".

Left as it is, deliberately: a song that a sync run adds to a setlist *after* the sheet's first tap on it is still
counted as the sheet's own, so unticking takes it out. Two devices editing one setlist within the seconds the sheet is
open is the rarer case.

## Tests

In `SetlistSongToggleTest` (selection `[a, b, c]`):

- `a setlist the sheet has not tapped is read as it is now`: `heldOf(emptyMap(), setlist with entries a, b, c, x, selection)`
  is `{a, b, c}`, and `selectionBoxState(selection, it, emptySet())` is `On` — the new setlist of Scenario A.
- `a recorded setlist is not read again`: `heldOf(mapOf("s" to setOf("a")), setlist "s" with entries a, b, selection)` is `{a}`.
- `a tap records what the setlist holds then, once`: `withHeldRecorded(emptyMap(), "s" with entries a)` is
  `mapOf("s" to setOf("a"))`; the same call on that result with "s" now holding a, b, c returns the same instance
  (`assertSame`) — the untick-then-tick case, whose re-tick must not take the songs the sheet put in for held ones.

The first fails to compile at d650f3cb0 (no helper) and, in substance, encodes what the sheet got wrong: a setlist it
had no record of read as holding none.

## Manual check

Tick three songs in the Songs screen's selection mode, Choose setlists, New setlist, Create: the new setlist is listed
ticked and disabled, and no tap on it changes it. Open the setlist afterwards: all three songs are in it. Then tick a
setlist holding one of the three, untick it, and tick it again quickly: it ends holding the three, its first song still
in its old place.
