# Treat a setlist tempo or capo step whose song is no longer in the setlist as not written, instead of a pending value that never settles

**Challenged:** amended — a reset (`bpm` / `fret` null: the stepper's value tapped, a step back to the file's value, the Song defaults card's Reset) of a song whose entry is gone has nothing left to clear and settles on its own (`null == null`), so it now still counts as written instead of reporting a failure that did not happen.

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt` (`writeTempo`, `writeCapo`)

Lane D: apply after 30 and 31 (same file, different functions).

## Problem

A song details screen opened from a setlist stays open when a sync run (or another device's edit arriving) takes that
song out of the setlist: the screen resolves its pages from `destination.songFileNames` against the library, not against
the setlist's entries. Stepping its tempo or capo then writes into the setlist through:

```kotlin
updateEditableSetlist(setlistFileName) { setlist ->
    setlist.copy(entries = setlist.entries.map { entry -> if (entry.songFileName == key.songFileName) entry.copy(tempo = bpm) else entry })
}?.takeUnless { it.isArchived } != null
```

No entry matches, the setlist comes back unchanged and non-null, so `isWritten` is true and the pending value is marked
written:

```kotlin
pending[key]?.takeIf { it.bpm == bpm && !it.isWritten }?.let { pending + (key to it.copy(isWritten = true)) } ?: pending
```

The settling collector only lets go of a written pending value once the store says the same:

```kotlin
pending.filter { (key, value) -> value.isWritten && stored[key.songFileName, key.setlistFileName] == value.bpm }.keys
```

`Tempos.get` for a setlist that has no entry for the song is null, which never equals a non-null bpm, so the pending
tempo stays for the rest of the session. The stepper and the click show a tempo that was saved nowhere, without the
`OperationFailed` message a deleted setlist gets; and if the song is put back into the setlist later in the session, the
stale pending value overrides the entry's real (null) tempo. `writeCapo` has the identical shape (`entry.copy(capo = fret)`,
`pendingCapos`, the capo settling collector).

## Fix

In both `writeTempo` and `writeCapo`, count a setlist write as written only when the setlist still holds the song:

```kotlin
var hasEntry = false
val isWritten = if (setlistFileName == null) {
    ...
} else {
    updateEditableSetlist(setlistFileName) { setlist ->
        hasEntry = setlist.entries.any { it.songFileName == key.songFileName }
        if (hasEntry) setlist.copy(entries = …) else setlist
    }?.takeUnless { it.isArchived } != null && (hasEntry || bpm == null)
}
```

A **null** value (a reset: `resetTempo`, the stepper's value tapped, a step back onto the file's own value, the Song
defaults card's Reset) is still written when the entry is gone: there is no override left anywhere to clear, the
setlist was not changed, and the settling collector lets the pending null go at once (`stored[…] == null`). Reporting
`OperationFailed` for it would tell the user a reset failed that did exactly what they asked. (`writeCapo`: `fret == null`.)

(the transform may run more than once under a compare-and-set; assigning the flag each time is fine). The existing
not-written branch then drops the pending value and sends `Message.OperationFailed`, exactly as for a setlist that is
gone. Returning `setlist` unchanged for a missing entry also keeps the repository from writing (and syncing) an equal
document.

## Tests

None: the logic lives in the view model's write path, which root `CLAUDE.md` excludes from unit tests.

## Manual check

With sync connected on two devices: open a setlist's song on device A, remove that song from the setlist on device B and
let A sync. On A, step the tempo (and the capo): after the debounce a "could not be saved" message appears and the stepper
returns to the file's value; adding the song back to the setlist shows no leftover override. A reset there (the stepper's value tapped, or a step back
onto the file's value) shows no message.
