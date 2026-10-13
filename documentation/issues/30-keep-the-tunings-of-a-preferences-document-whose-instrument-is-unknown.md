# Tell a pre-instrument preferences document by its missing instrument key, so an instrument this version does not know keeps every tuning

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Challenged:** amended — corrected what the new test sees before the fix (the tunings are `{banjo: banjo_open_d}`,
not empty); the fix itself holds.
**Files:** `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/mapper/UserPreferencesMappers.kt`,
`data/source/local/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/mapper/UserPreferencesMappersTest.kt`,
`data/source/local/implementation/CLAUDE.md` (only if plan 31 has not already described the migration)

## Problem

`UserPreferencesDocument.toModel()` decides whether a document predates the one shared instrument by asking whether its
`instrument` is an id *this* version knows (`UserPreferencesMappers.kt:199-210` at 72d48deb7):

```kotlin
private fun UserPreferencesDocument.instrument() = UserPreferences.Instrument.fromId(instrument)
    ?: UserPreferences.Instrument.ofTuningId(tunerSettings.instrument)
    ?: UserPreferences.Instrument.fromId(chordInstrument)
    ?: UserPreferences.Instrument.GUITAR

/** The tunings picked for each instrument; on a document from before [instrument] the diagrams', plus the tuner's own. */
private fun UserPreferencesDocument.tunings(): Map<String, String> {
    if (UserPreferences.Instrument.fromId(instrument) != null) return tunings
    val tunerTuning = tunerSettings.instrument
    val tunerInstrument = UserPreferences.Instrument.ofTuningId(tunerTuning)
    return if (tunerInstrument == null || tunerTuning == tunerInstrument.id) chordTunings else chordTunings + (tunerInstrument.id to tunerTuning)
}
```

A document written by a later version with an instrument this one lacks (`"instrument": "mandola"`) takes the legacy
branch: `tunings()` returns `chordTunings`, which no document since the migration carries, so every tuning the player
picked, for every instrument, is dropped, and the next save writes `"tunings": {}` over them. A probe confirmed it:
`{"instrument": "mandola", "tunings": {"guitar": "guitar_drop_d", "mandola": "mandola_x"}}` reads as instrument GUITAR
with `tunings = {}`, and saves back with no tunings. This contradicts `UserPreferences.tunings`' KDoc and
`data/model/CLAUDE.md` ("an unknown id kept as it is"). Likewise `instrument()` would consult the legacy keys for such a
document, so a stale `tunerSettings.instrument` or `chordInstrument` could pick the instrument rather than the guitar the
test `an instrument this version does not know is the guitar` promises.

Reachable only where one `preferences.json` is read by an older build after a newer one (the preferences are kept on the
device, not synced): a desktop or sideloaded downgrade, or a restore of a newer device backup.

## Fix

Decide "from before the shared instrument" by the key being absent, which is what the migration means: every document
written since writes `instrument` (`toDocument` writes `instrument.id`, never blank), and no document before had it.

```kotlin
/** Whether this document was written before the tuner and the chord diagrams shared one instrument: it names none. */
private val UserPreferencesDocument.predatesInstrument get() = instrument.isBlank()

private fun UserPreferencesDocument.instrument() = if (predatesInstrument) {
    UserPreferences.Instrument.ofTuningId(tunerSettings.instrument)
        ?: UserPreferences.Instrument.fromId(chordInstrument)
        ?: UserPreferences.Instrument.GUITAR
} else {
    UserPreferences.Instrument.fromId(instrument) ?: UserPreferences.Instrument.GUITAR
}

private fun UserPreferencesDocument.tunings(): Map<String, String> {
    if (!predatesInstrument) return tunings
    … // the legacy branch as it is
}
```

Update the KDoc of `instrument()` to say an instrument this version does not know is the guitar, its tunings kept. (The
unknown instrument id itself cannot be kept, since the model's `Instrument` is an enum; the next save writes `guitar`.
That is existing, tested behaviour and is not changed here.)

If plan 31 lands first, its migration paragraph in `data/source/local/implementation/CLAUDE.md` already says "a document
with no `instrument` key"; make sure the wording matches this rule. Otherwise add that clause there.

## Tests

In `UserPreferencesMappersTest`:

```kotlin
@Test
fun `an instrument this version does not know keeps every tuning and ignores the legacy keys`() {
    val model = UserPreferencesDocumentFormat.decode(
        """{"instrument": "mandola", "tunings": {"guitar": "guitar_drop_d", "mandola": "mandola_gdae"}, "chordInstrument": "ukulele", "tunerSettings": {"instrument": "banjo_open_d"}}""",
    ).document.toModel()
    assertEquals(UserPreferences.Instrument.GUITAR, model.instrument)
    assertEquals(mapOf("guitar" to "guitar_drop_d", "mandola" to "mandola_gdae"), model.tunings)
    assertEquals(model.tunings, UserPreferencesDocumentFormat.decode(UserPreferencesDocumentFormat.encode(model.toDocument())).document.toModel().tunings)
}
```

Before the fix the instrument is BANJO and the tunings are only the legacy tuner's `{banjo: banjo_open_d}` (the
document's own `tunings` dropped); either assertion fails. The existing legacy tests (`a legacy tuner instrument
wins over the legacy chord instrument`, the chromatic, five-string bass, seven-string, and unknown-legacy-tuner ones) and
`an instrument this version does not know is the guitar` must still pass unchanged.

## Manual check

On the desktop build: quit the app, edit `preferences/preferences.json` in the app's data directory, setting
`"instrument": "mandola"` and keeping a `"tunings"` entry such as `"guitar": "guitar_drop_d"`; launch, pick the guitar in
Settings (the guitar is already picked): its tuning is drop D. Quit and confirm the file still has the `tunings` entry.
