# Step 12: verification guide

Use this after every step and as the final acceptance run. It is written for an agent working from a terminal on
macOS with the Android SDK, Xcode and Chrome installed.

## Build everything

```
./gradlew :chordpro:desktopTest :data:source:local:implementation:desktopTest \
          :app:android:assembleDebug :app:desktop:build \
          :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:wasmJsBrowserDistribution
```

The iOS *app* (not just the framework) builds with
```
xcodebuild -project app/ios/iosApp/iosApp.xcodeproj -target iosApp -sdk iphonesimulator -arch arm64 \
           SYMROOT=/tmp/campfire-ios OBJROOT=/tmp/campfire-ios build
```
(there is no scheme in the project, so `-target` plus the two roots is the working recipe; a full build takes ~2.5 min).

## Where the library lives (for pushing sample files in)

| Platform | Songs directory |
| --- | --- |
| Desktop (macOS) | `~/Library/Application Support/Campfire/library/songs` |
| Android (debug build) | `/data/data/com.pandulapeter.campfire.debug/files/library/songs` (`adb shell run-as com.pandulapeter.campfire.debug ls files/library/songs`; on the emulator `adb root` also works) |
| iOS simulator | `$(xcrun simctl get_app_container booted com.pandulapeter.campfire data)/Documents/library/songs` |
| Web | OPFS: Chrome DevTools → Application → Storage → Origin Private File System (or use the app's own import) |

Sample files: `docs/rewrite-plan/samples/*.cho` (created in step 06).

## Desktop

- Run: `./gradlew :app:desktop:run --quiet > /tmp/campfire-desktop.log 2>&1 &`; the process is `pgrep -f CampfireDesktopApplicationKt`.
- Screenshot only the app window (never the full screen): find the window id with a tiny Swift helper using
  `CGWindowListCopyWindowInfo` (owner pid = the JVM, layer 0, name "Campfire"), then `screencapture -x -o -l <id> out.png`.
  The image is 2× logical size and includes the 28 pt title bar.
- Clicks: `osascript` System Events `click at {x, y}` after making the process frontmost; restore the previous app
  afterwards. Escape is `key code 53`. These stop working when the display sleeps or the screen is locked.
- Scrolling: CGEvent scroll-wheel events work (a Swift helper posting `scrollWheelEvent2Source` after warping the
  cursor over the window). Synthetic mouse *drags* on desktop are unreliable: verify drag gestures on Android.
- Deterministic alternative: a temporary `LaunchedEffect` in `app/desktop`'s `main` that drives the view model
  (`openSong`, `openEditor`, `navigateBack`, `selectTopLevelDestination`) with `delay`s and `println`s; remove it
  before committing.

## Android

- Emulator: `~/Library/Android/sdk/emulator/emulator -avd Resizable_Experimental -no-snapshot-load -no-boot-anim &`,
  wait for `adb shell getprop sys.boot_completed` to print `1`. Kill with `adb emu kill`.
- Install: `adb install -r app/android/build/outputs/apk/debug/*.apk`; launch
  `adb shell monkey -p com.pandulapeter.campfire.debug -c android.intent.category.LAUNCHER 1`. (The `.debug` suffix
  matters: a release build may also be installed.)
- Screenshots: `adb shell screencap -p /sdcard/x.png && adb pull /sdcard/x.png` (`exec-out screencap` fails on this AVD).
- Input: `adb shell input tap x y`, `adb shell input swipe x1 y1 x2 y2 ms` (real touch gesture), `adb shell input text`.
  Pinch needs `sendevent` on `/dev/input/event1` after `adb root` (protocol B multi-touch, coordinates scaled to
  0..32767); only needed for the font-scale gesture.
- Logs: `adb logcat -s System.out` shows `println`s from shared code.
- Import test: `adb push docs/rewrite-plan/samples/simple.cho /sdcard/Download/` then pick it from the in-app picker
  (Downloads appears in the system picker).

## iOS

- Simulator: `xcrun simctl boot "iPhone 16"`, install with `xcrun simctl install booted /tmp/campfire-ios/Debug-iphonesimulator/iosApp.app`,
  launch `xcrun simctl launch booted com.pandulapeter.campfire`.
- Screenshots: `xcrun simctl io booted screenshot out.png`.
- Touches: `simctl` cannot tap. Post CGEvents from a small Swift helper **only when**
  `NSWorkspace.shared.frontmostApplication?.localizedName == "Simulator"` and take the window rect from that PID,
  otherwise clicks land in other apps. Rotation: AppleScript `click menu item "Rotate Left" of menu 1 of menu bar item "Device"`.
- Files app check (step 10): `open` the Files app in the simulator via `xcrun simctl launch booted com.apple.DocumentsApp`.
- Import test: `xcrun simctl addmedia` does not handle text files; copy the sample into the app container's
  `Documents/library/songs` (path above) and rescan, or after step 10 into `Documents` of the Files app.

## Web

- Dev server: `./gradlew :app:web:wasmJsBrowserDevelopmentRun` (prints the localhost URL). Production bundle:
  `./gradlew :app:web:wasmJsBrowserDistribution` then serve `app/web/build/dist/wasmJs/productionExecutable` with
  `python3 -m http.server 8080` (OPFS needs `localhost` or https).
- Drive it with the Chrome browser automation tools if available, otherwise manually. Check the DevTools console for
  wasm trap errors (`struct.set`-style errors mean a library that the wasm compiler cannot handle was pulled in).
- Test in Chrome, Safari and Firefox: OPFS, the file input and the download link behave differently in each.

## Manual acceptance matrix (run at the end of steps 09 and 11)

| Check | Desktop | Android | iOS | Web |
| --- | --- | --- | --- | --- |
| Fresh start shows the empty state with New song / Import | | | | |
| New song → editor → typing → preview updates → back → song in list with title/artist | | | | |
| Import `samples.zip` (3 songs) → 3 songs; import again → 3 " (2)" copies | | | | |
| `everything.cho` renders tab (monospace), grid, three comment styles, chorus recall, header chips | | | | |
| Transpose +1 in a song in E → chords use flats, key chip shows F; restart → remembered | | | | |
| Setlist: create, add two songs, reorder, per-setlist transposition, rename, export, delete | | | | |
| Delete a song that is in a setlist → setlist shows it no more | | | | |
| Export library zip → `unzip -l` lists `songs/` and `setlists/` → import into a wiped install → identical | | | | |
| Lyrics-only mode hides chords/tabs/grids; horizontal flow; font scale (pinch on touch) | | | | |
| Theme light/dark/system; language en/hu (every screen, no `???`) | | | | |
| Rescan picks up a file added outside the app (desktop/iOS) | | | n/a | n/a |
| "Open with" a `.cho` (after step 10) | | | | n/a |
| No network requests at all (Android: `adb shell dumpsys netstats` or Charles; web: DevTools Network tab shows only the site's own assets) | | | | |

Fill the cells with ✅ / ❌ + a note, and put the table into the execution notes of the step you are verifying.
