# Announce a settings-style notice that appears on its own, starting with the Metronome tab's audio notice

**Kind:** accessibility  ·  **Severity:** low  ·  **Platforms:** all (screen readers)
**Challenged:** amended — the fallback live-region node must wrap the AnimatedSettingsRow call (its AnimatedVisibility composes nothing while hidden), never clearAndSetSemantics over a row with buttons
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/settings/SettingsMessage.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/metronome/MetronomeScreen.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/settings/SyncSettings.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CLAUDE.md` (`## Accessibility`)

**Shared with lane P:** `SettingsMessage.kt` gains the parameter here; lane P passes it for the tuner's notices in
`tuner/TunerOptions.kt` and must land after this plan.

## Problem

When Start is pressed on the Metronome tab and there is no sound output, or the browser waits for a gesture, a notice
expands in above the tempo
(`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/metronome/MetronomeScreen.kt:143`
at b5c8ed3b5):

```kotlin
    AnimatedSettingsRow(value = (playback as? MetronomePlayback.Playing)?.audioIssue) { issue ->
        SettingsMessage(
            text = stringResource(
                when (issue) {
                    MetronomeAudioIssue.UNAVAILABLE -> Res.string.metronome_audio_unavailable
                    MetronomeAudioIssue.WAITING_FOR_GESTURE -> Res.string.metronome_audio_waiting
                }
            ),
        )
    }
```

`SettingsMessage` (`screens/settings/SettingsMessage.kt`) is a plain `Text`. A screen reader user who pressed Start
hears nothing — and, with no sound output, there is nothing else to hear either: the one thing that explains the
silence is never read unless they explore down to it. The same holds for Settings → Sync's connection failure
(`SyncSettings.kt:166`, `AnimatedSettingsRow(value = (syncState as? SyncState.ConnectionFailed)?.reason)`), which
appears after the browser or the Dropbox app hands control back.

## Fix

Give `SettingsMessage` an opt-in live region, rather than putting the modifier at each call site:

```kotlin
/**
 * ...
 * @param isAnnounced Whether a screen reader reads the message out as it appears, for one that comes up on its own,
 *   in answer to something the user did elsewhere or to nothing at all (a sound output that is missing, a connection
 *   that failed), rather than being part of the section from the start.
 */
@Composable
internal fun SettingsMessage(
    modifier: Modifier = Modifier,
    text: String,
    isAnnounced: Boolean = false,
) = Text(
    modifier = modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 8.dp)
        .then(if (isAnnounced) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier),
    ...
```

(`androidx.compose.ui.semantics.liveRegion`, `LiveRegionMode`, as `tuner/TunerDisplay.kt:104` already uses them.)
Pass `isAnnounced = true` from the metronome notice above and from the sync connection-failure message in
`SyncSettings.kt`; the always-there descriptions (`settings_sync_description`, `settings_sync_unavailable`, the language
note) stay as they are. Polite, not assertive: it waits for whatever TalkBack is reading (the Start button's new
"Stop" label) to finish. A node that appears with a live region is announced as Material's snackbars are (their
container carries `liveRegion = Polite` and is new each time); if TalkBack on the test device does not read it on
appearance, move the semantics to a node that is there before the message and say so in this plan's commit.
**Challenged:** that node has to be *outside* `AnimatedSettingsRow` — a `Column(Modifier.semantics { liveRegion =
LiveRegionMode.Polite })` around the `AnimatedSettingsRow` call — not around its content: its `AnimatedVisibility`
composes nothing at all while hidden, so a wrapper inside it is as new as the message. (On Android Compose only sets
the node's live-region attribute, `AndroidComposeViewAccessibilityDelegateCompat.android.kt:814`, and an appearing node
is reported as a subtree change of its nearest semantics parent, so a persistent live-region parent is the form that is
sure to be the event's source.) Do not use `clearAndSetSemantics` on such a wrapper where the row also holds a button.

Lane P passes `isAnnounced = true` for the tuner's `tuner_notice_silent` / `tuner_notice_waiting` in
`tuner/TunerOptions.kt`.

Add to `ui/CLAUDE.md`'s `## Accessibility`: "A notice that appears on its own is a polite live region
(`SettingsMessage(isAnnounced = true)`); one that is there from the start is not."

## Tests

None: semantics only.

## Manual check

- Android with TalkBack, an emulator with no audio output (or the web build in Chrome with ChromeVox before any
  click): Metronome tab → Start — "There is no sound output, so the beat is only shown." is read after "Stop".
- Settings → Sync → Connect, cancel the authorization in the browser: the failure message is read when the app comes
  back.
- iOS VoiceOver: the same notices are read (Compose maps a live region to a layout-changed notification there; note
  in the commit if it does not).
- Nothing is announced on opening the Metronome tab while no issue is present.
