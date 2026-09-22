# 17 · Android: a song opened from a setlist of thousands of songs crashes the app every time it is sent to the background

**Severity:** crash (Android only. Unlikely: it takes a setlist of roughly 10,000 songs. But the crash repeats every time the app goes to the background while that state is held, and it has already happened once for the editor, see ff569ee1) · **Area:** `:presentation` (`CampfireViewModel.kt`: `persistBackStack`)

## Symptom
1. Android. Have a setlist of about 10,000 songs (easy to make by importing a generated `.setlist.json` next to
   the `.cho` files it names, the kind of thing a stress test does).
2. Open any song from that setlist.
3. Press Home. The app crashes at once (`java.lang.RuntimeException: android.os.TransactionTooLargeException: data
   parcel size … bytes` from `ActivityClient.activityStopped`). Reopening it starts fresh; opening a song from the
   setlist and leaving again crashes again.

(A paste of a few hundred KB into a search field crashes the same way; that half is plan 18.)

## Cause
The view model writes an unbounded string into its `SavedStateHandle`, which Android puts into the activity's saved
state `Bundle`. That bundle crosses to the system in one Binder transaction of at most 1 MB, and strings are written
as UTF-16, so two bytes per character. Above the limit, Android (target SDK 24 and later) throws instead of dropping
the state.

`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt:745`, on every back stack change:

```kotlin
private fun persistBackStack() = persist(BACK_STACK_KEY, backStack.toList())
```

where a song opened from a setlist carries every file name of that setlist
(`openSongInSetlist`, `:766-772`: `songFileNames = setlistWithSongs.songs.map { it.fileName }`). With file names of
about 40 characters, 10,000 of them are about 450,000 characters of JSON, roughly 900 KB as UTF-16. On top of that
come Navigation 3's saved state for each entry and everything else in the activity's bundle.

The editor's text was capped for the same reason (`EditorFieldSaver`, 50,000 characters, commit ff569ee1). The back
stack was not (the searches are 18).

## Fix
Cap what is written, not what the user can do. The cap is far past anything that is used in practice, so it only
ever applies to the pathological case.

1. The search query half of this crash is fixed by plan 18, which
   caps the query where it is typed, which also covers the second copy of the query saved by
   `ScrollToTopWhenChanged`'s `rememberSaveable` key that capping only the persisted value would miss. Do not add a
   second cap here; this plan only bounds the back stack.

2. `persistBackStack` (`:745`):

   ```kotlin
   /**
    * ... (existing KDoc) ...
    *
    * A stack whose JSON would not fit [MAX_SAVED_BACK_STACK_LENGTH] is saved only up to the screen that makes it too
    * long - in practice a song opened from a setlist of thousands, which names every one of them. A restored process
    * then comes back one screen short, which beats one that crashes as it is sent to the background: the saved state
    * crosses to the system in a single transaction of at most a megabyte.
    */
   private fun persistBackStack() {
       val stack = backStack.toList()
       val savable = (stack.size downTo 1).asSequence()
           .map { stack.subList(0, it) }
           .firstOrNull { Json.encodeToString(it).length <= MAX_SAVED_BACK_STACK_LENGTH }
           ?: listOf(CampfireDestination.Songs)
       persist(BACK_STACK_KEY, savable)
   }
   ```

   The stack is at most four entries deep, so encoding it up to four times is nothing. In the usual case the first
   encoding fits and the loop stops. If you want to avoid encoding it twice (once here, once in `persist`), give
   `persist` an overload that takes the already encoded string.

3. Companion constant: `private const val MAX_SAVED_BACK_STACK_LENGTH = 100_000` (about 200 KB as UTF-16, a fifth
   of the transaction), with a short comment saying what it is measured in (characters of JSON).

4. Do **not**:
   - drop `songFileNames` from `SongDetails`, or re-derive it from the setlist on restore. The pager's list is a
     snapshot on purpose, and the destination is the Navigation 3 key.
   - trim the list inside the `SongDetails` that is persisted. A restored pager holding a prefix of the setlist would
     open on the wrong songs.

## Tests
None (UI is untested).

## Verify
1. Android (`.debug` build): generate a setlist of 12,000 songs. Import a zip of 12,000 small `.cho` files plus a
   `.setlist.json` naming them all, for example with a short shell loop. Open a song from it, press Home: no crash.
   `adb shell am kill com.pandulapeter.campfire.debug` while in the background and reopen: the app comes back on the
   setlists tab rather than on the song.
2. Ordinary use: open a song from a normal setlist, kill the process in the background, reopen. It comes back on the
   song, as before (the stack fits).

## Docs
`presentation/CLAUDE.md`, the `ui/navigation/CampfireDestination.kt` bullet, after "…the field's own saver writes an
undo history that holds the whole document twice per transposition.": add "For the same reason the view model saves a
back stack only as far as it fits 100,000 characters of JSON (a song opened from a setlist of thousands names every
one of them, and a restored process then comes back one screen short)."

## Touches
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`
- `presentation/CLAUDE.md`

## Depends on
Nothing. 18 is the search half of the same crash; neither needs the other to land first. 34 rewrites every
`_messages` line of the same file, none of these.
