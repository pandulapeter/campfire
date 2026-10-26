<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Plan: an audience screen the app controls

**Status:** not started; step 1 is a spike that decides how the Chromecast receiver is drawn. The goal is a second screen
(a projector, a TV, a monitor) that everyone sees, showing the song's lyrics (or lyrics and chords) in big text, while
the app stays the controller. `ui/` is `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/`,
as in the root `CLAUDE.md`.

## Decisions

- **The audience follows the page.** It shows the lines on the performer's current page, fitted to its own screen. The
  pedal and the step buttons drive both screens; there is no second thing to steer while playing.
- **Three modes:** Lyrics (default), Lyrics and chords (a stage monitor for the band), and Blackout. Blackout is one tap
  or one key away. Outside song details the audience screen is black.
- **One frame, many routes.** Common code turns what the performer sees into an `AudienceFrame`, a small serializable
  value: the visible sections, already transposed, notated, and filtered (lyrics only drops chord-only sections, as the
  song page does). Its receivers never parse ChordPro, read preferences, or touch the library. Each route only carries
  the frame to a screen:

  | Route | Where | Carried by | Drawn by |
  |---|---|---|---|
  | A second window on any screen (AirPlay "separate display" and Miracast "extend" included) | desktop | the same process | `AudienceScreen` |
  | An external display scene (AirPlay mirroring, HDMI, USB-C) | iOS, iPadOS | the same process | `AudienceScreen` |
  | `android.app.Presentation` on a wired display | Android | the same process | `AudienceScreen` |
  | A popup window | web | `BroadcastChannel` | `AudienceScreen` in the web build's display mode |
  | Google Cast | Android, iOS (and Chrome on the web, step 9) | a Cast custom message namespace | the receiver (step 1) |

- **Chromecast is in v1.** It is the only wireless route from an Android phone. It costs a $5 Cast developer
  registration, the Google Cast sender SDKs (proprietary, like Play's in-app updates already are), and a receiver page
  served over HTTPS: the web build's deployment on GitHub Pages, so there is still no Campfire server.
- **Discovery only on request.** Cast devices are searched for (mDNS on the local network) only while the route picker
  is open or a session is live, never in the background. A Settings → Features switch, "Audience screen", removes the
  feature (and with it every Cast request), like Cover art. It only hides.
- **The audience screen is dark.** Black background, white lyrics, the accent colour for chords and headings, in
  Inter, whatever the app's theme; projectors wash out light backgrounds. Text is fitted (the largest size at which
  the frame fits, capped), never the performer's text size.
- **A frame carries a protocol version.** The receiver on GitHub Pages is always the latest build, and the phone may be
  older; the receiver draws every older version it knows and shows "Update Campfire" for a newer one.

## Where it hooks in today

- `ui/songView/SongLyrics.kt` draws from a `SongLyricsModel` and plain values, no view model; `prepareSongLyrics`
  (`ui/songView/model/SongLyricsModel.kt`) turns `SongLyricsInputs` into it. The frame is built from that model.
- The reading position is screen-local: `pageScrollStates` (`SongDetailsScreen.kt`), the `SongStepper`
  (`screens/songDetails/stepping/`), and the pager's state. Only the settled song reaches shared state
  (`Navigator.onSongDetailsPageSettled`). Step 2 publishes the visible line range too.
- The keys: `songKeyboardShortcuts` (`SongKeyboardShortcuts.kt`); Blackout gets `B`, as in presentation software.
- Feature switches: `UserPreferences`, `UserPreferencesDocument` and its mappers, `PreferencesController`,
  `FeaturesSections.kt`, `FeatureSwitches.kt`, with `isTunerEnabled` as the template.
- Desktop: one `SwingWindow` in `CampfireDesktopApplication.kt`; the window owns the `ViewModelStore`, so the second
  window takes the main window's view model rather than its own.
- iOS: a SwiftUI `App` with one `WindowGroup`; `Info.plist` declares `UIApplicationSupportsMultipleScenes = false` and no
  scene configurations.
- Android: one `ComponentActivity`; no MediaRouter yet.
- Web: `index.html` takes the `campfire-library` Web Lock before it starts the app; a display page must branch before
  `claimLibrary` and never take it. `campfire-shared-links` is the existing `BroadcastChannel` precedent.

## Steps

### 1. Spike (decides how Cast is drawn and confirms iOS)

Needs a Chromecast with Google TV (2020) and a Google TV Streamer, or whichever the owner has; a device must be
registered by serial number in the Cast console for development.

- **Receiver: Kotlin/Wasm or plain JS.** Load a throwaway receiver that starts the Cast Application Framework
  (`cast_receiver_framework.js`, which Google requires the page to load from its own address) and a minimal Compose for
  Web page drawing a hard-coded frame. Record whether WasmGC is supported, the time to the first frame, memory, and
  whether a page change animates smoothly at 1080p. If it passes, the receiver is the web build's display mode
  (step 6) and the Cast route shares `AudienceScreen`. If not, the receiver is a small HTML/JS page drawing the same
  frame with the DOM (sections, lines, chords over lyrics); `AudienceScreen`'s fit and style rules are then written down
  for both, and a frame test fixture is shared so both render the same frames.
- **iOS: a second Compose window.** In the simulator (I/O → External Displays), host a `ComposeUIViewController` in a
  `UIWindow` on a `windowExternalDisplayNonInteractive` scene. Check the frame rate of that screen's display link, the
  density (external screens are often scale 1), and that nothing in Compose waits for the scene to become key. On the
  iOS 27 SDK, register it with `registerSceneAccessory(.externalNonInteractive(...))` from a hidden representable;
  keep the Info.plist role for iOS 26 and earlier (`INFOPLIST_KEY_UIApplicationSceneManifest_Generation = NO` if Xcode
  rewrites the manifest).
- **iOS: the Cast SDK in this Xcode project.** Which distribution of the Google Cast iOS SDK fits beside the Kotlin
  framework (Swift Package Manager, if offered, over CocoaPods), its size, and the permissions it needs now: Local
  Network with `NSBonjourServices` `_googlecast._tcp` and `_<APPID>._googlecast._tcp`; whether Location is still asked.

The spike ends in a note added here: the receiver verdict, versions, sizes, and times.

### 2. The frame (`:presentation`, common)

- `AudienceFrame` (kotlinx.serialization): protocol version, mode, song title and artist, and a list of sections, each
  a heading and lines of text with chords placed by character offset and the `<b>`/`<i>`/`<u>` markup kept. No file
  names, no tags, no links: only what the screen shows.
- `AudienceController` on `CampfireViewModel`: the mode, the route, and a `StateFlow<AudienceFrame?>` built from the
  song on top of song details, its `SongLyricsModel`, and the visible line range.
- Song details reports its visible line range (first and last line on screen) when a step lands or a scroll settles,
  not on every frame. With a page cut mid-section, the frame carries that section's visible lines under its heading.
- Tests (`commonTest`): the frame for a page cut mid-section, for lyrics only (chord-only sections dropped, as on the
  song page), for a transposed and capoed song (names are what sounds, per the root rule), and a serialization round
  trip including an older version.

### 3. `AudienceScreen` (`:presentation`, common)

- Draws a frame: the fitted text, the sections in as many columns as the screen's shape fits best, the title small on
  the song's first page only. Reuse the song view's line drawing wherever it takes plain values.
- Motion: a new page or song is a whole-page change (leave → glide → arrive, `staged-motion` rules), Blackout fades,
  a mode change crossfades. The first frame on a newly connected screen is drawn right, never animated in.
- Tests: the fit (largest size that fits, the cap, a single line too long for the width wraps instead of shrinking
  everything).

### 4. The controller UI (`:presentation`, common)

- Song details' overflow menu gains **Audience screen…**, opening the route picker: this platform's targets (a screen
  or "New window" on desktop, "New window" on the web, a connected display on iOS and Android) and the Cast devices
  found, with "Searching…" while discovery runs. With one connected display and no Cast device, picking it is one tap.
- While presenting, a pill in the song details bar names the target and the mode, and opens a dropdown: Lyrics, Lyrics
  and chords, Blackout, Stop. `B` toggles Blackout. The pill fits the bar within its existing height (the bar budget in
  `ui/chrome`); on a short window it shrinks to an icon.
- A display unplugged or a Cast session dropped ends presenting with a snackbar; nothing reconnects by itself.
- The Features switch, strings in English and Hungarian (plurals where counted), and the "Audience screen" entry in the
  onboarding's features page only if the onboarding lists every switch.

### 5. Desktop route

- A second `Window` in the same `application {}`, taking the main window's `AudienceController`, undecorated and sized
  to the chosen `GraphicsDevice`'s bounds (not macOS's native fullscreen Space, which blanks other screens when
  "Displays have separate Spaces" is off). "New window" opens a decorated window to drag anywhere, with a fullscreen
  toggle on double-click.
- Screens come and go: AWT has no hot-plug event, so the picker reads `screenDevices` when opened, and a presenting
  window whose screen is gone falls back to a window on the main screen.
- Linux/Wayland cannot place a window on a chosen monitor; there the picker offers only "New window".
- Key events that land on the audience window go to the main window's handler, so the pedal keeps working whichever
  has focus.

### 6. Web route and display mode

- `index.html` branches on `?display` before `claimLibrary`: the display page never takes the Web Lock, never opens
  OPFS or registers the service worker, and starts the app in a display-only entry that draws `AudienceScreen` from
  frames on a `campfire-audience` `BroadcastChannel` (the controller posts the current frame whenever a display says
  hello, so a reload recovers).
- The controller opens it with `window.open` (no `noopener` handle is needed, the channel is same-origin). On Chrome and
  Edge the Window Management API (`getScreenDetails`, behind its permission prompt) lets the picker list screens and
  open the popup fullscreen on one; elsewhere it opens a window and shows "Drag it to the other screen, then press
  F" with a fullscreen button inside (fullscreen needs a click in that window).
- Node tests beside the existing ones for the branch: a display page never requests the lock.

### 7. Android route

- `androidx.mediarouter` (`CATEGORY_LIVE_VIDEO` routes with a `presentationDisplay`), credited as a shipped dependency.
- A `Presentation` holding a `ComposeView` with the activity's lifecycle, saved state, and view model store owners
  set before `setContent`, drawing `AudienceScreen` from the shared controller. It is dismissed when the activity is
  destroyed and recreated after a configuration change.
- Check Android 16's connected displays on a Pixel: whether a monitor in a desktop session is still offered as a
  presentation display.

### 8. iOS route

- What the spike settled: the external-display scene with a `UIWindow` holding a second `ComposeUIViewController` that
  draws `AudienceScreen` from the same Kotlin controller (a `CampfireAudienceViewController()` entry beside
  `CampfireViewController()`), the iOS 27 scene accessory with its `isEnabled` following Stop, and the Info.plist role.
- Until the user picks the screen in the app, iOS mirrors as it does today; mirroring stays the behaviour when the
  feature is switched off.

### 9. Cast

- **Registration:** a Cast developer account and a Custom Web Receiver app whose URL is the web deployment's
  `/app/?display=cast`. Its ID is `campfire.cast.appId` in `gradle.properties`, empty by default (no Cast route, as the
  empty Dropbox key leaves Sync unconfigured), set in `local.properties` and CI secrets (`.github/CLAUDE.md`).
- **Receiver:** the display mode from step 6 (or the JS page, if the spike said so), which also loads the Cast
  Application Framework, listens on `urn:x-cast:com.pandulapeter.campfire.audience`, and keeps the TV awake while a
  sender is connected.
- **Senders** live in `:data:source:remote:implementation`, the module every network call belongs to, behind a
  `CastSource` in `:data:source:remote:api`: discovery as a `Flow` of devices, connect, send a frame, disconnect.
  Android uses Play Services `cast-framework` with its own picker (not the SDK's dialog, so the route picker stays one
  list); iOS the Google Cast SDK. A frame is sent when it changes, throttled to the frame rate of page changes.
- **Chrome on the web** (cheap once the receiver exists): the Cast Web Sender SDK from the same `CastSource` on
  wasmJs, offered only where `chrome.cast` loads.
- Credits: the Cast SDKs' terms under `composeResources/files/licenses` of the shipping source sets,
  `OpenSourceComponents.kt`, and `THIRD_PARTY_SOFTWARE.md`.

### 10. Docs

- Root `CLAUDE.md` → **Privacy: what reaches the network** gains a bullet: Cast discovery on the local network only
  while the picker is open or a session is live, frames sent to the chosen device, and the TV loading the receiver from
  the web deployment and Google's framework; the Features switch removes it. The root's product rules gain one line,
  and the new package gets its `CLAUDE.md`.
- Release-time, not part of this plan: the privacy policy, the store listings' feature lists, and the website.

## Not in v1

- A title card between songs, per-section slides chosen by tapping, a "next" preview, backgrounds and logos.
- Casting from the desktop JVM app (no Cast SDK; the open CASTV2 protocol would need TLS and mDNS of our own).
- More than one audience screen at a time.
