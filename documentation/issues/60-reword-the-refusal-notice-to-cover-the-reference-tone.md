# Reword the tuner's refusal notice, which promises strings that chromatic mode does not show

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/composeResources/values/strings.xml`,
`presentation/src/commonMain/composeResources/values-hu/strings.xml`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/tuner/TunerScreen.kt` (KDoc)

## Problem

`strings.xml:415-416` at b5c8ed3b5:

```xml
<string name="tuner_notice_refused">The microphone is switched off for Campfire. The strings below still play their notes.</string>
<string name="tuner_notice_refused_web">The microphone is switched off for Campfire. It is allowed in the browser\'s site settings, next to the address. The strings below still play their notes.</string>
```

and `values-hu/strings.xml:415-416`: "… Az alábbi húrok hangja így is lejátszható."

The default instrument is chromatic (`TunerSettings.instrumentId = "chromatic"`), and in chromatic mode `TunerOptions`
shows no strings at all (`AnimatedSettingsRow(value = config.tuning)`, `tuning` being null for chromatic,
`TunerOptions.kt:80-93`). What does still play is the reference pitch's A chip (`ReferencePitchSetting`'s
`tuner_play_reference`), which every instrument shows. So the first thing most people who refuse read points at
something that is not on the page.

## Fix

Options:
- **A (recommended): one sentence true in every mode** — the tones below, which are the strings' chips where there are
  strings and the reference A always:
  - en: `The microphone is switched off for Campfire. The tones below can still be played.`
  - en web: `The microphone is switched off for Campfire. It is allowed in the browser\'s site settings, next to the
    address. The tones below can still be played.`
  - hu: `A mikrofon ki van kapcsolva a Campfire számára. Az alábbi hangok így is lejátszhatók.`
  - hu web: `A mikrofon ki van kapcsolva a Campfire számára. A böngésző webhelybeállításaiban engedélyezhető, a cím
    mellett. Az alábbi hangok így is lejátszhatók.`
- B: choose the sentence by tuning (strings vs the reference A), which needs the tuning passed into `TunerNoticeCard`
  and two more keys per language, for a distinction the sentence does not need.

Change only the values of the two keys, in both files (no key changes, so no code changes). Update the
`TunerScreen` KDoc's "the strings still play their notes then" (`TunerScreen.kt:59-60`) to "its tones still play then".

No CLAUDE.md changes: no documented behavior changes (`ui/tuner/CLAUDE.md` does not quote the sentence).

## Tests

None: strings.

## Manual check

Refuse the microphone (Android or iOS system prompt; on the web, block it in the site settings) with the instrument on
Chromatic: the notice says "The tones below can still be played", and the reference A chip below plays. Switch to
Guitar: the same sentence, with the string chips playing too. Repeat in Hungarian.
