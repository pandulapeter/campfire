# Announce the tuner's notices and its input issues when they appear

**Kind:** accessibility  ·  **Severity:** low  ·  **Platforms:** all
**Challenged:** amended — the fallback wraps the AnimatedSettingsRow/AnimatedContent from outside with a plain liveRegion, not clearAndSetSemantics, which would delete the notice and issue rows' buttons
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/tuner/TunerNoticeCard.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/tuner/TunerOptions.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/tuner/CLAUDE.md`

## Problem

What stops the tuner from hearing arrives without a tap of the user's: the microphone refused in the system prompt,
disconnected, taken by a call (`TunerNotice.REFUSED`, `NO_MICROPHONE`, `BUSY`, `FAILED`), or an open input that hears
nothing (`TunerInputIssue.SILENT`) or waits for a gesture on the web (`WAITING_FOR_GESTURE`). A screen reader user is
told none of it: the sentences are plain `Text`s.

`tuner/TunerNoticeCard.kt:55-68` at b5c8ed3b5:

```kotlin
Text(
    text = stringResource(
        when (notice) {
            TunerNotice.NOT_ASKED -> Res.string.tuner_notice_not_asked
            …
        }
    ),
    style = MaterialTheme.typography.bodyLarge,
    color = MaterialTheme.colorScheme.onSurface,
)
```

`tuner/TunerOptions.kt:56-65`:

```kotlin
AnimatedSettingsRow(value = hearing?.issue) { issue ->
    Column {
        SettingsMessage(
            text = stringResource(
                when (issue) {
                    TunerInputIssue.SILENT -> Res.string.tuner_notice_silent
                    TunerInputIssue.WAITING_FOR_GESTURE -> Res.string.tuner_notice_waiting
                }
            ),
        )
        …
```

The display these notices replace was a live region, so its focus is lost too.

## Fix

Make each sentence a polite live region.

- `TunerNoticeCard`: give the sentence `Text` `modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }`
  (imports `androidx.compose.ui.semantics.LiveRegionMode`, `liveRegion`, `semantics`). The buttons keep their own
  semantics.
- `TunerOptions`: uses `SettingsMessage`'s announce parameter from lane A's plan 41 if present (e.g.
  `SettingsMessage(text = …, isAnnounced = true)`, whatever name plan 41 gave it); otherwise add the semantics locally
  through the modifier `SettingsMessage` already takes:
  `SettingsMessage(modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }, text = …)`.

Both sentences appear by being composed (through `AnimatedSettingsRow` / `AnimatedContent`) rather than by a text
changing in place. Try the simple form above first; if the manual check finds a newly composed live region is not
announced on a platform, move the `liveRegion` to a node that stays composed — a `Column(Modifier.semantics { liveRegion =
LiveRegionMode.Polite })` wrapped *around* the `AnimatedSettingsRow` call that holds the input issue (its
`AnimatedVisibility` composes nothing while hidden, and `AnimatedSettingsRow` takes no modifier), and for the notice
around the `AnimatedSettingsRow` on the tab (`TunerScreen`) and the `AnimatedContent` in the sheet (`TunerSheet`).
**Challenged:** not with the sentence under `clearAndSetSemantics` on that wrapper — the notice card and the issue row
hold buttons (Use the microphone, Open settings), whose semantics it would delete; the plain `semantics { liveRegion }`
lets the reader speak the subtree and keeps the buttons. Use the same form plan 41 settles on.

`NOT_ASKED` is the page's first state rather than news; it is announced once as the page opens, which is harmless and
helpful (it says what the button is for).

Docs: `ui/tuner/CLAUDE.md`, the `TunerNoticeCard` bullet: "… its sentence a polite live region, as is the input issue
line of `TunerOptions`, since both arrive without a tap."

## Tests

None: semantics of Composables.

## Manual check

TalkBack and VoiceOver, Tuner tab:
1. Tap "Use the microphone" and refuse in the system prompt: the refusal sentence is read without moving focus.
2. With the microphone allowed and listening, mute it (Android: the quick setting's microphone access off; iOS: none
   possible, skip): "Nothing at all is heard…" is read.
3. Web (Chrome + a screen reader): open the tab without a click on the page after a reload: "Tap anywhere to let the
   browser listen." is read.
