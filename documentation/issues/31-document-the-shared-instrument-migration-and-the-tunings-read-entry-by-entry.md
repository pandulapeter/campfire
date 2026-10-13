# Document how the preferences move to the one shared instrument, and that the tunings are read entry by entry

**Kind:** docs  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `data/source/local/implementation/CLAUDE.md`

## Problem

`data/source/local/implementation/CLAUDE.md`'s `model/` + `mapper/` paragraph describes every compatibility rule of
`UserPreferencesDocument` except the newest one, at 72d48deb7:

1. It says nothing of the migration from the documents 4.8.0 wrote (and the development builds after it) to one
   instrument shared by the tuner and the chord diagrams, which `UserPreferencesMappers.kt` and
   `UserPreferencesDocument.kt` implement:
   - `instrument()`: a document with no `instrument` takes it from `tunerSettings.instrument` first (the tuner's tuning
     id, through `Instrument.ofTuningId`, the more specific answer: a seven-string player told the tuner so and the
     diagrams Other), then from `chordInstrument`, then the guitar;
   - `tunings()`: such a document's tunings are `chordTunings`, plus the tuner's tuning for its instrument where that is
     not the standard one;
   - `TunerSettingsDocument.toModel()`: `isChromatic` is the document's own where it has one, and otherwise true only
     where the old `tunerSettings.instrument` was explicitly `"chromatic"` (its default is blank now, since the old
     `"chromatic"` default was never written);
   - the old keys (`chordInstrument`, `chordTunings`, `tunerSettings.instrument`) are only read, and never written again
     (the test `a document written now carries none of the legacy instrument keys`).
2. Its list of what `UserPreferencesDocumentFormat` reads entry by entry ("one transposition, tempo, or capo …, one
   song's accents …, one folded section key …, and one tag color that is not text costs that tag") omits the tunings and
   the chord tunings, which `readableField` filters the same way:
   ```kotlin
   (key == TAG_COLORS_KEY || key == TUNINGS_KEY || key == CHORD_TUNINGS_KEY) && value is JsonObject -> JsonObject(value.filterValues { it is JsonPrimitive && it.isString })
   ```

## Fix

In that paragraph:

1. Extend the entry-by-entry sentence: "… and one tag color that is not text costs that tag, as one tuning (or one of the
   chord tunings they replaced) that is not text costs that instrument's —".
2. Add, after the sentence about `demoLibraryContentHashes`, one sentence on the migration, for example: "A document
   with no `instrument` key was written before the tuner and the chord diagrams shared one instrument, and is read into
   it once: the instrument from the tuner's old tuning id (`tunerSettings.instrument`, the more specific answer) and
   otherwise the diagrams' `chordInstrument`, the tunings from `chordTunings` plus the tuner's tuning where it was not
   its instrument's standard one, and the chromatic switch on only where the tuner was explicitly `chromatic`; those old
   keys are only read and never written again, and a document that has an `instrument` key, one this version does not
   know included, is never read through them." Keep the last clause in step with plan 30, which makes the code match it;
   if plan 30 is rejected, drop that clause.

Use the Oxford comma in the new lists.

## Tests

None: documentation only. The file's MPL header stays, so the license header check is unaffected.

## Manual check

None.
