<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Plan: app shortcuts on Android and iOS

**Status:** not started. Long-pressing the app icon offers shortcuts straight into the app: its main screens, New
song, and Paste a song. Android calls these launcher shortcuts; iOS calls them Home Screen quick actions
(`UIApplicationShortcutItem`). `ui/` is
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/`, as in the root `CLAUDE.md`.

## Decisions

- **The shortcuts follow the app.** They're dynamic on both platforms (Android's `ShortcutManagerCompat` dynamic
  shortcuts, iOS's `UIApplication.shortcutItems`), set by the app whenever what they depend on changes. Static
  shortcuts couldn't follow the feature switches, read only mode, or the in-app language.
- **What's offered, in this order:**
  1. **New song**: the editor on a new song, as the songs screen's New button opens it.
  2. **Paste a song**: what the songs screen's "Paste from clipboard" does, reading the clipboard as the app opens.
  3. **The main screens**: Songs, Setlists, Metronome, Tuner, Chords, and Scales, in the navigation's order, each only
     while its feature is switched on. The home screen (the first one switched on, which the app icon opens anyway)
     and Settings are left out.
- **Hidden where the app hides them.** New song and Paste a song need Songs switched on and read only mode off, as
  their buttons do. Features and read only mode only hide, so a shortcut that goes away is a shortcut no longer
  offered, never a disabled one.
- **Fewer are shown than offered.** Launchers usually show four, and iOS shows at most four of an app's own. Both
  show them in the order given, so New song and Paste a song always fit and the screens fill the rest. That's fine,
  since the screens are a tap away in the app.
- **Labels in the app's language.** Dynamic shortcuts take their labels at runtime, so they're the app's own strings
  (`app_shortcut_*`, in every app language) and are set again when the language changes.
- **Icons are the app's own.** Each shortcut uses the icon its screen or button has in the app. Android needs them as
  vector drawable resources in `:app:android` (the Compose `ImageVector`s can't be handed to a launcher); iOS takes SF
  Symbols as close to them as there are, which is what quick actions are designed for.
- **Every shortcut is an app link**, `campfire://` followed by a web address, as the widgets' plan defines
  ([home-screen-widgets.md](home-screen-widgets.md), step 2). The screens use the addresses the web build already has
  (`setlists`, `metronome`, and so on). New song and Paste a song get two addresses of their own, `new` and `paste`,
  which only the shortcuts use. On the web, they open the home screen, so a typed address does nothing surprising.
- **Pasting asks the system.** On iOS 16 and later, reading the clipboard shows the system's "Allow Paste" question,
  as the in-app button does. Android 12 and later show their "pasted from your clipboard" notice. An empty clipboard,
  or one holding only a link, says so the way the button does (`paste_clipboard_empty`, `paste_only_links`).
- **Only Android and iOS.** A macOS Dock menu (`java.awt.Taskbar.setMenu`) and the web manifest's `shortcuts` could
  follow later. The Windows jump list needs native code. None of them is in this plan.

## Where it hooks in today

- `navigation/FeatureSwitches.kt` holds the switches, and read only mode is a preference.
  `components/shortcuts`' `ShortcutFeatures` already decides which keyboard shortcuts read only mode hides; New song
  and Paste a song follow the same rule.
- The songs screen's New and "Paste from clipboard" buttons go through `CampfireViewModel`. The shortcuts call the
  same functions rather than new ones.
- `navigation/BrowserRoutes.kt` parses the web addresses into a back stack.
- Android: `CampfireMainActivity.handle` and `.CampfireIntentActivity`'s filters (`app/android/CLAUDE.md`).
- iOS: a SwiftUI `App` hosting the Compose controller; `IosFileImport.openUrl` receives `campfire://` links.

## Steps

### 1. What the shortcuts are (common code)

- `ui/shortcuts/`: `AppShortcut` (`id`, the label string, the icon's name, and the link), and
  `appShortcuts(featureSwitches, isReadOnly)`, which gives the list in its order.
- `AppShortcutPublisher` is a platform interface with one `publish(shortcuts)`. Android and iOS implement it, and
  desktop and web get a no-op. `CampfireViewModel` (or a small collector it owns) publishes when the list or the app's
  language changes, skipping a publish equal to the last one.
- The `new` and `paste` addresses in `BrowserRoutes`: `new` opens the editor on a new song over the home screen, and
  `paste` runs the clipboard paste once the app is on screen. Neither is ever written back into an address, and the web
  build reads both as the home screen.
- Tests in `commonTest`: the list for every switch combination and read only mode, its order, and the two addresses
  parsed (and ignored on the web).

### 2. Links (shared with the widgets)

- If the widgets haven't landed first, this plan lands `campfire://<web address>` handling on both platforms, as the
  widgets' plan describes in its step 2. Whichever lands first does it, once.

### 3. Android

- The publisher sets the dynamic shortcuts with `ShortcutManagerCompat.setDynamicShortcuts`, each an `ACTION_VIEW`
  intent for its link, aimed at `.CampfireIntentActivity`, ranked in the list's order. `androidx.core` is already
  shipped, so no new dependency or credit.
- A shortcut that's been pinned to the home screen and is no longer offered is disabled with a message saying why
  ("Switched off in Campfire's settings"), since Android doesn't remove a pinned one.
- Vector drawables for each shortcut's icon, and the labels in every app language.
- Report each use (`reportShortcutUsed`), so the launcher can learn what is used.

### 4. iOS

- The publisher sets `UIApplication.shared.shortcutItems` from Kotlin (UIKit's API is Objective-C, so no Swift seam is
  needed), each item's `userInfo` holding its link.
- A shortcut that launches the app arrives in the scene's connection options, and one pressed while the app is running
  arrives in `windowScene(_:performActionFor:completionHandler:)`. A SwiftUI app gets both through a scene delegate
  set up from an `UIApplicationDelegateAdaptor`. Both hand the link to `openUrl`.

### 5. Documentation

- Root `CLAUDE.md`: a product rule line (`ui/shortcuts`, `app/android`, `app/ios`).
- New `ui/shortcuts/CLAUDE.md`. Update `navigation/CLAUDE.md` (the `new` and `paste` addresses), `app/android/CLAUDE.md`
  (the dynamic shortcuts and pinned ones), and `app/ios/CLAUDE.md` (the scene delegate).
- The `add-language` skill needs no change: the `app_shortcut_*` strings live in `strings.xml`, which a new language
  translates whole.
