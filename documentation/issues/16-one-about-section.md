<!--
 This file is part of Campfire.
 Copyright (c) Pandula Péter 2017-2026.
 https://github.com/pandulapeter/campfire

 This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one at
 https://mozilla.org/MPL/2.0/.
-->
# 16 — One About section, one link to the README and one rating row

## The user's decision

Quoted in full, because the whole plan is an implementation of it:

> *"Let's rethink this section and make it consistent across platforms. Get rid of the individual links and simply
> have a single, unified About section that hints at the existence of other apps, but hides them behind a link to the
> Readme. I'll update that manually once all the store listings are ready. The only other link that should survive
> this change is the link to the store of the current distribution so that users can leave a rating (this should be
> missing from Web and Linux)."*

And the follow-up that settles what "the store of the current distribution" means in practice:

> The rating row is **not** limited to builds that came from a store. It appears on every build, including the direct
> `.dmg`/`.msi` download and a sideloaded APK, and it points at the store of **the platform the app is running on**,
> not the one it was installed from: Android → Google Play, iOS → the App Store, macOS (any desktop build) → the Mac
> App Store, Windows (any desktop build) → the Microsoft Store, Linux → hidden, web → hidden (it runs on every
> operating system, so any single choice would be a guess). The row is hidden whenever that platform's listing does
> not exist yet, and an Apple build links only to Apple's own store. Use the `https` listing URL rather than
> `market://`, and Apple's `?action=write-review` form, so the link also works when it is opened in a desktop
> browser.

The user will update the README and the store listings by hand.

## What the user sees

**Today**, Settings → About is two sections. The first, "Campfire on your other devices", is six rows — Android,
iPhone and iPad, Mac, Windows, Linux, Web browser — of which four are disabled "Coming soon to the App Store" style
placeholders, with the build's own row marked "· this version", and the whole list silently cut short on Apple builds
by a store-policy filter. The second is "About": the author, GitHub, Report a problem, the privacy policy and,
sometimes, the coffee.

**After**, it is one untitled section (the tab names it), the same rows on every platform:

1. *Created by Pandula Péter* / *Version 4.3.0* → the author's own site
2. *Campfire on GitHub*
3. *Report a problem*
4. *Every version of Campfire* / *Listed on the project's GitHub page* → the README's "Get Campfire" section
5. *Rate Campfire* / *Leave a review on Google Play* — only where that platform's store listing exists
6. *Privacy Policy*
7. *Buy me a coffee* — only where `canAskForDonations` (see plan 15)

Row 4 is the one that "hints at the existence of other apps": it says there are other versions and hands the question
to a page the user can keep up to date without a release. Nothing in the app claims a build exists before it does, so
App Review's guideline 2.1 (no placeholder content) and 2.3.10 (no other platform's name) stop being anything the
code has to reason about — the store filtering goes away with the list it filtered.

Row 5 is worded with care. **Tapping through to a store page shows that store's install button**, and a user who
came to Campfire through the direct `.dmg` download could install a second, sandboxed copy from the Mac App Store
with a library of its own, quietly separate from the one they have been using. So the row has to read as *rate*, not
as *get the app from the store*: the title is an invitation to leave a review and the description names the store as
the place the review goes, in both English and Hungarian. On a device whose store listing does not exist yet — which
today is everything except Android — the row is simply absent.

## Cause

Not a bug: a design that has been outgrown. Verified at HEAD `984861e4`.

`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/Platform.kt:68-98`:

```kotlin
internal enum class Distribution(
    val url: String?,
    val isApple: Boolean = false,
) {
    PLAY_STORE(url = "https://play.google.com/store/apps/details?id=com.pandulapeter.campfire"),
    APP_STORE(url = null, isApple = true),
    MAC_APP_STORE(url = null, isApple = true),
    MICROSOFT_STORE(url = null),

    /** No store at all: the package the release workflow attaches to every GitHub release. */
    LINUX(url = "https://github.com/pandulapeter/campfire/releases/latest"),
    WEB(url = "https://pandulapeter.com/campfire"),
}

internal fun visibleDistributions(current: Distribution? = currentDistribution) = Distribution.entries.filter { distribution ->
    current?.isApple != true || distribution == Distribution.WEB || (distribution.isApple && distribution.url != null)
}
```

`presentation/src/commonMain/.../ui/screens/settings/SettingsScreen.kt:546-583`:

```kotlin
private fun DistributionsSection(
    urlOpener: (String) -> Unit,
) = SettingsSection(title = stringResource(Res.string.settings_distributions)) {
    SettingsMessage(text = stringResource(Res.string.settings_distributions_description))
    val distributions = visibleDistributions()
    distributions.forEach { distribution ->
        …
            description = when {
                distribution.url == null -> stringResource(Res.string.settings_distribution_coming_soon, storeName)
                distribution == currentDistribution -> stringResource(Res.string.settings_distribution_current, storeName)
                else -> storeName
            },
```

and the tab that hosts both sections, `SettingsScreen.kt:276-282`:

```kotlin
                SettingsTab.ABOUT -> SettingsPage(
                    settledWidth = pageWidth,
                    scrollState = scrollStates[page],
                    contentPadding = contentPadding,
                    section = { DistributionsSection(urlOpener = urlOpener) },
                    secondSection = { AboutSection(urlOpener = urlOpener) },
                )
```

## The change

### 1. `Distribution` becomes the four app stores

Nothing else reads the enum — a grep for `Distribution` over `presentation/src` finds only `Platform.kt`, the four
platform actuals and `SettingsScreen.kt` — so `LINUX` and `WEB` can go, and a build that came from no store answers
`currentDistribution = null`, which the type already allows.

Replace `Platform.kt:61-98` with:

```kotlin
/**
 * The app stores Campfire is published on.
 *
 * @property listingUrl The page a rating is left on, or null for as long as the app is not on that store: it is
 *   filled in on the day the listing goes live, which is the whole of what publishing costs the app. It is an https
 *   address rather than a store's own scheme (`market://`, `ms-windows-store://`) so that it also opens in a
 *   browser, on a desktop where the store app may not be installed at all.
 * @property isApple Whether the store is one of Apple's, which is what decides [canAskForDonations].
 */
internal enum class Distribution(
    val listingUrl: String?,
    val isApple: Boolean = false,
) {
    PLAY_STORE(listingUrl = "https://play.google.com/store/apps/details?id=com.pandulapeter.campfire"),
    APP_STORE(listingUrl = null, isApple = true),
    MAC_APP_STORE(listingUrl = null, isApple = true),
    MICROSOFT_STORE(listingUrl = null),
}
```

`visibleDistributions` is deleted outright.

`currentDistribution`'s KDoc (`Platform.kt:37-43`) loses its second half, which described the list that is going
away:

```kotlin
/**
 * Which store this build was published on, or null where it came from none of them: the Linux package, the unsigned
 * desktop installers the GitHub release carries, and the web build. It decides nothing the user sees directly - what
 * it answers is [canAskForDonations], since a build that goes through App Review may not ask for money at all.
 */
internal expect val currentDistribution: Distribution?
```

Add, next to it:

```kotlin
/**
 * The store whose listing the settings screen's rating row opens, decided by the platform the app is **running on**
 * rather than by where the build came from: somebody who downloaded the .dmg is still a Mac user, and the Mac App
 * Store listing is still where a review of Campfire on a Mac goes. Null where the platform has no store to rate the
 * app on - Linux has none, and the web build runs on all of them, so any one choice would be a guess.
 *
 * A build that goes through App Review only ever runs on an Apple platform, so this never names another company's
 * store to one (guideline 2.3.10) without a rule of its own having to say so.
 */
internal expect val storeForRating: Distribution?
```

The four actuals:

- `Platform.android.kt`: `internal actual val storeForRating: Distribution? = Distribution.PLAY_STORE`
- `Platform.ios.kt`: `internal actual val storeForRating: Distribution? = Distribution.APP_STORE`
- `Platform.wasmJs.kt`, with the reason as a comment: the page runs on every operating system and knows none of them
  well enough to pick a store → `internal actual val storeForRating: Distribution? = null`
- `Platform.desktop.kt`, the one place where the operating system is still the right question to ask:

  ```kotlin
  // The store of the machine, not of the build: the Mac App Store listing is where a review of Campfire on a Mac
  // goes whether the app arrived from that store or as the .dmg the GitHub release carries. Linux has no store.
  internal actual val storeForRating: Distribution? = when {
      isMacOs -> Distribution.MAC_APP_STORE
      isWindows -> Distribution.MICROSOFT_STORE
      else -> null
  }
  ```

Plan 15 left `currentDistribution` on desktop reading the generated `CAMPFIRE_DESKTOP_DISTRIBUTION`; keep that, and
change its two now-deleted branches — `"linux"` and the `download` fallback both map to `null`:

```kotlin
internal actual val currentDistribution: Distribution? = when (CAMPFIRE_DESKTOP_DISTRIBUTION) {
    "mac-app-store" -> Distribution.MAC_APP_STORE
    "microsoft-store" -> Distribution.MICROSOFT_STORE
    // "linux" and "download" answer to no store's rules, which is what a null says.
    else -> null
}
```

`Platform.wasmJs.kt`'s `currentDistribution` becomes `null` for the same reason (the web build is on no store), with
its comment updated.

### 2. The About tab is one section

`SettingsScreen.kt`, the ABOUT branch:

```kotlin
                SettingsTab.ABOUT -> SettingsPage(
                    settledWidth = pageWidth,
                    scrollState = scrollStates[page],
                    contentPadding = contentPadding,
                    section = { AboutSection(urlOpener = urlOpener) },
                )
```

`DistributionsSection` is deleted, and `AboutSection` loses its `title` argument — `presentation/CLAUDE.md` already
states the rule: "a tab holding one section leaves it untitled, since the tab names it". Its KDoc and body become:

```kotlin
/**
 * What the app is and where it lives: GitHub is both its home page and where a problem is reported, and it is also
 * where every build of Campfire is listed, which is the whole of what the app says about the other platforms - a
 * page can be kept up to date without a release, and it is the one place App Review has nothing to say about.
 *
 * The rating row leads to the store of the platform the app is running on, and only once that listing exists. It
 * says "rate", never "get it from the store": a store page has an install button on it, and somebody who has the
 * direct download would end up with a second copy of the app, sandboxed, with a library of its own.
 */
@Composable
private fun AboutSection(
    urlOpener: (String) -> Unit,
) = SettingsSection {
    LinkListItem(
        title = stringResource(Res.string.settings_created_by),
        description = stringResource(Res.string.settings_version, CAMPFIRE_VERSION_NAME),
        icon = painterResource(Res.drawable.ic_campfire),
        onClick = { urlOpener("https://pandulapeter.com/") },
    )
    LinkListItem(
        title = stringResource(Res.string.settings_git_hub),
        icon = painterResource(Res.drawable.ic_git_hub),
        onClick = { urlOpener(GIT_HUB_URL) },
    )
    LinkListItem(
        title = stringResource(Res.string.settings_report_issue),
        icon = painterResource(Res.drawable.ic_bug),
        onClick = { urlOpener("$GIT_HUB_URL/issues") },
    )
    // The README's "Get Campfire" section, whose anchor GitHub derives from the heading, so renaming that heading
    // means changing it here.
    LinkListItem(
        title = stringResource(Res.string.settings_distributions_all),
        description = stringResource(Res.string.settings_distributions_all_description),
        icon = painterResource(Res.drawable.ic_phone),
        onClick = { urlOpener("$GIT_HUB_URL#get-campfire") },
    )
    storeForRating?.let { store ->
        store.listingUrl?.let { listingUrl ->
            LinkListItem(
                title = stringResource(Res.string.settings_rate),
                description = stringResource(Res.string.settings_rate_description, stringResource(store.storeName)),
                icon = painterResource(Res.drawable.ic_star),
                onClick = { urlOpener(listingUrl) },
            )
        }
    }
    LinkListItem(
        title = stringResource(Res.string.settings_privacy_policy),
        icon = painterResource(Res.drawable.ic_privacy_policy),
        onClick = { urlOpener("https://pandulapeter.com/legal/privacy_policy-campfire.html") },
    )
    if (canAskForDonations) {
        LinkListItem(
            title = stringResource(Res.string.settings_support),
            icon = painterResource(Res.drawable.ic_coffee),
            onClick = { urlOpener("https://buymeacoffee.com/pandulapeter") },
        )
    }
}
```

`platformName` and `icon` (`SettingsScreen.kt:635-669`) are deleted; `storeName` shrinks to the four stores and stays:

```kotlin
/** The name a rating is left under, which is the one thing the app still has to call a store. */
private val Distribution.storeName
    get() = when (this) {
        Distribution.PLAY_STORE -> Res.string.settings_distribution_play_store
        Distribution.APP_STORE -> Res.string.settings_distribution_app_store
        Distribution.MAC_APP_STORE -> Res.string.settings_distribution_mac_app_store
        Distribution.MICROSOFT_STORE -> Res.string.settings_distribution_microsoft_store
    }
```

The row order above is a judgement call: the identity rows first, then the two that lead away from the app (the other
versions, the rating), then the legal and the optional one. Keep whatever order review settles on, but keep the
version row first — `presentation/CLAUDE.md` calls it "the link to the author's own site" and it is the section's
header in everything but name.

### 3. Strings — both files, same commented group, in the same order

`presentation/src/commonMain/composeResources/values/strings.xml` and `values-hu/strings.xml`.

**Delete** (both files), lines 199-216 of the English file except the two kept below:

`settings_distributions`, `settings_distributions_description`, `settings_distribution_android`,
`settings_distribution_ios`, `settings_distribution_mac`, `settings_distribution_windows`,
`settings_distribution_linux`, `settings_distribution_web`, `settings_distribution_git_hub_releases`,
`settings_distribution_web_address`, `settings_distribution_current`, `settings_distribution_coming_soon`.

**Keep unchanged**: `settings_distributions_all`, `settings_distributions_all_description`,
`settings_distribution_play_store`, `settings_distribution_app_store`, `settings_distribution_mac_app_store`,
`settings_distribution_microsoft_store`. The first two already read exactly right for their new role as an ordinary
row ("Every version of Campfire" / "Listed on the project's GitHub page", "A Campfire összes változata" / "A projekt
GitHub-oldalán"), and reusing them keeps the translations that are already there.

**Add**, in the same group in both files:

```xml
    <string name="settings_rate">Rate Campfire</string>
    <string name="settings_rate_description">Leave a review on %1$s</string>
```

```xml
    <string name="settings_rate">Értékeld a Campfire-t</string>
    <string name="settings_rate_description">Írj véleményt itt: %1$s</string>
```

Both are read with `com.pandulapeter.campfire.presentation.localization.stringResource`, never the
`org.jetbrains.compose.resources` overload, and the formatted one is always called with its argument. The argument is
a store's name out of this same file — the app's own text, not something a user wrote — so `stringResource` is
correct here and `textResource` is not.

Neither wording may be turned into "Get Campfire on %1$s": the row must not read as a way to install the app.

### 4. Drawables

Delete `ic_tablet.xml`, `ic_laptop.xml`, `ic_desktop.xml`, `ic_terminal.xml`, `ic_website.xml` from
`presentation/src/commonMain/composeResources/drawable/` — grep confirms `SettingsScreen.kt` is their only user and
`DistributionsSection` was the only place in it. `ic_phone.xml` stays (it is also
`UserPreferences.ThemeColor.SYSTEM`'s icon, `SettingsScreen.kt:698`) and `ic_git_hub.xml` stays.

Add `ic_star.xml` for the rating row, in the shape of the existing drawables (24dp viewport, `#FF000000` fill, the
MPL header as an XML comment) — the Material Symbols "star" path is the natural choice.

## Tests

None; the UI is untested and nothing here is pure logic. Run the standard suite to confirm the shared modules are
untouched:

```bash
./gradlew :chordpro:desktopTest :domain:implementation:desktopTest :data:source:local:implementation:desktopTest :data:source:remote:api:desktopTest :data:source:remote:implementation:desktopTest :data:repository:implementation:desktopTest
```

## Verification

```bash
# Every target still compiles after an expect/actual pair was added and one removed.
./gradlew :app:android:assembleDebug :app:desktop:run :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:wasmJsBrowserDevelopmentRun

# No string key or drawable was left behind.
grep -rn "settings_distribution_android\|settings_distribution_coming_soon\|visibleDistributions\|platformName" presentation/src app
grep -rn "ic_tablet\|ic_laptop\|ic_desktop\|ic_terminal\|ic_website" presentation/src
```

By hand, Settings → About:

- **Desktop** (`./gradlew :app:desktop:run`, on a Mac): one untitled section, seven rows minus the rating one, since
  the Mac App Store listing URL is still null. Fill `MAC_APP_STORE`'s `listingUrl` in temporarily to see the row
  appear and open. On Linux and in the web build the row must never appear, whatever the enum says.
- **Android** (`./gradlew :app:android:assembleDebug`, then install): the rating row is there and opens the Play
  listing in a Custom Tab; a sideloaded debug APK shows it exactly as a Play install does, which is the point.
- **Hungarian**: switch the in-app language in Settings → General and confirm both new strings are translated and
  that nothing reads "???" (a missing key).
- Needs a Mac for the macOS and iOS halves; the Microsoft Store half needs a Windows PC. Neither store listing exists
  yet, so the only row that can be seen for real today is Play's.

## Docs

Root `CLAUDE.md`, Conventions. This bullet becomes untrue in full and is replaced:

> - **Every build links to the others** from Settings, so the app can be found for each device its user has:
>   `Distribution` (in `:presentation`'s `ui/platform/Platform.kt`) lists Play, the App Store, the Mac App Store, the
>   Microsoft Store, the Linux package on the latest GitHub release and the web build, a null `url` marking one that
>   is not published yet — it is drawn as a disabled "Coming soon" row, and publishing it is filling that URL in.
>   `visibleDistributions` is where the store rules are kept: a build that goes through App Review names only Apple's
>   stores and the web, and shows no placeholders — what it has instead is a row naming no platform that leads to the
>   README's "Get Campfire" section, where every build is listed. **GitHub is the project's website and its issue
>   tracker**; the About section links nothing else but the author's own site, the privacy policy and the donation
>   page.

Replacement:

> - **The app says nothing about the other builds but where to find them.** Settings → About is one section on every
>   platform, and the row that names no platform — "Every version of Campfire" — leads to the README's "Get Campfire"
>   section, which is a page that can be kept up to date without a release and the one place a store has nothing to
>   say about. `Distribution` (in `:presentation`'s `ui/platform/Platform.kt`) is now just the four app stores and
>   their listing URLs, a null `listingUrl` marking one the app is not on yet; publishing is filling it in.
>   `currentDistribution` says which store the build was published on, and is what decides whether it may ask for
>   money at all (`canAskForDonations`: never on Apple's stores, guideline 3.1.1). `storeForRating` is a different
>   question — the store of the platform the app is **running** on, so the direct `.dmg` and the Mac App Store build
>   both send a review to the Mac App Store — and it is what the one "Rate Campfire" row opens, absent on Linux, on
>   the web, and wherever that listing does not exist yet. The row says *rate* and never *install*: a store page
>   carries an install button, and a second copy of the app would come with a library of its own. **GitHub is the
>   project's website and its issue tracker**; the About section links nothing else but the author's own site, the
>   privacy policy and the donation page.

`presentation/CLAUDE.md`, the `ui/screens/settings/` bullet. This sentence goes:

> **The other builds** (`DistributionsSection`) are `Platform.kt`'s `Distribution` entries filtered by
> `visibleDistributions`: every store and the web, the build's own included (that is the listing to rate or to send
> on), an unpublished one as a disabled "Coming soon" row that gets its URL in the enum on the day it ships. A build
> that goes through App Review (iOS, and the desktop build on macOS, which is headed for the Mac App Store) names
> only Apple's stores and the web (guideline 2.3.10) and leaves unpublished ones out altogether (2.1 wants no
> placeholder content), and wherever the list was cut short that way it ends in an "Every version of Campfire" row
> that names no platform and opens the README at its "Get Campfire" section, which lists them all; the icons are
> kinds of device rather than anybody's logo for the same reason.

replaced by:

> **The About tab is one section** (`AboutSection`, untitled, since the tab names it) and the same rows on every
> platform: the author and the version, GitHub, Report a problem, "Every version of Campfire" (the README's "Get
> Campfire" section, which is the whole of what the app says about the other builds), the rating row, the privacy
> policy and, where `canAskForDonations`, the coffee. The rating row opens `storeForRating`'s `listingUrl` — the store
> of the platform the app runs on rather than the one it came from — and is absent on Linux, in the web build and
> wherever that listing does not exist yet; its wording says *rate*, since a store page also offers to install a
> second copy of the app.

Also fix the same bullet's earlier line, which describes the tab: "About (the other builds, then the app and its
links)" becomes "About (the app, its links and where its other builds are listed)".

The README's "Get Campfire" heading and its `#get-campfire` anchor are unchanged, so the link keeps working; the
user updates that section's contents by hand as the listings go live. No README edit belongs in this change.

## Files touched

- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/Platform.kt`
- `presentation/src/{androidMain,iosMain,desktopMain,wasmJsMain}/.../ui/platform/Platform.<platform>.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/settings/SettingsScreen.kt`
- `presentation/src/commonMain/composeResources/values/strings.xml`
- `presentation/src/commonMain/composeResources/values-hu/strings.xml`
- `presentation/src/commonMain/composeResources/drawable/ic_star.xml` (new), and five deleted drawables
- `CLAUDE.md`, `presentation/CLAUDE.md`

## Depends on

**Plan 15**, which introduces `campfire.desktop.distribution` and makes `canAskForDonations` derive from
`currentDistribution`. This plan changes the desktop mapping that plan 15 writes, so land 15 first.

## Rules

- Load the `code-style` skill before the first edit.
- Every new string goes into **both** `values/strings.xml` and `values-hu/strings.xml`, in the same commented group,
  and is read with `com.pandulapeter.campfire.presentation.localization.stringResource`.
- Deleting the last use of a string key deletes it from **both** locale files, and the last use of a drawable deletes
  the drawable.
- A new `expect` (`storeForRating`) needs all four `actual`s in the same change.
- `commonMain` stays JVM-free.
- Material 3 Expressive only; `modifier: Modifier = Modifier` first where a Composable takes one; trailing commas.
