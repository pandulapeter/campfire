# 48 — The store publishing guides ask for work that is already done and name a field that does not exist

**Severity:** stale docs (a release manager following them redoes finished work or searches for a field in vain) ·
**Area:** `documentation/publishing/ios-app-store.md`, `mac-app-store.md`, `microsoft-store.md`

**Read, not run.** Found by comparing the guides with the repository at HEAD. Nothing was run.

## What the user sees

The "user" is whoever takes the app to the Apple and Microsoft stores:

1. `ios-app-store.md` opens "None of the steps below has been done yet", then asks them to add
   `ITSAppUsesNonExemptEncryption` to `Info.plist` and to create a privacy manifest, "There is none today". Both are
   in the project and already covered by the release test script (REL-006). Following the guide adds a duplicate key
   or a second manifest to the target.
2. All three guides end with "Fill in `Distribution.<STORE>`'s `url`". The property is `listingUrl`.
3. `microsoft-store.md` builds the store package from the debug image (`createDistributable`), with no distribution
   property, and says Settings "lists them all" — the other platforms, which Settings stopped doing.
4. `mac-app-store.md` §2 asks them to "turn the donation link off in the store build" by detecting
   `APP_SANDBOX_CONTAINER_ID` at runtime, and says `canAskForDonations` "is `true` for the whole desktop target". That
   was replaced by the build-time `campfire.desktop.distribution` property — as the same guide says forty lines later,
   in its "Already taken care of in the code" paragraph. The two passages contradict each other; the one in §2 is the
   stale one. §5 (the automated `packagePkg`) meanwhile never mentions passing that property, which is the step that
   actually matters.

## Cause

**1.** `documentation/publishing/ios-app-store.md:12-15`:

> What is left between the iOS build as it is today and a listing on the App Store that every GitHub release updates
> by itself. The app builds and runs; `ios-publish.yml` already produces an unsigned `.ipa` for every release. None of
> the steps below has been done yet, …

and `:34-39`:

> - [ ] Add `ITSAppUsesNonExemptEncryption` = `NO` to `app/ios/iosApp/iosApp/Info.plist`. …
> - [ ] Add a privacy manifest (`PrivacyInfo.xcprivacy`) to the app target. There is none today, and Kotlin/Native and
>       Compose Multiplatform touch "required reason" APIs (file timestamps among them), …

Against the repository:

- `app/ios/iosApp/iosApp/Info.plist:59-60` — `<key>ITSAppUsesNonExemptEncryption</key>` / `<false/>`.
- `app/ios/iosApp/iosApp/PrivacyInfo.xcprivacy` exists, declares `NSPrivacyTracking` false, no collected data, and
  `NSPrivacyAccessedAPICategoryFileTimestamp` with reasons `C617.1` and `3B52.1`; the Xcode project copies it
  (`project.pbxproj:14`, "PrivacyInfo.xcprivacy in Resources"). `documentation/testing/08-release-and-stores.md`
  REL-006 ("iOS project identity and privacy manifest") checks both.
- Still true in §2: there is no **shared** scheme (only a per-user one under
  `iosApp.xcodeproj/xcuserdata/…/xcschemes/iosApp.xcscheme`), and the build-number item.

**2.** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/Platform.kt:81-89`:

```kotlin
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

against `ios-app-store.md:94`, `mac-app-store.md:121` and `microsoft-store.md:102`, which all say "`url`".

**3.** `documentation/publishing/mac-app-store.md:59-61`:

> - [ ] **Turn the donation link off in the store build.** `canAskForDonations` is `true` for the whole desktop target,
>       and guideline 3.1.1 applies on the Mac as it does on iOS. One jar serves the `.dmg` and the store alike, so the
>       decision has to be made at runtime: a sandboxed process has `APP_SANDBOX_CONTAINER_ID` in its environment.

against `mac-app-store.md:91-93` ("A build made with `-Pcampfire.desktop.distribution=mac-app-store` has no donation
link (guideline 3.1.1, `canAskForDonations`).") and the code: `Platform.kt:54`
`internal val canAskForDonations get() = currentDistribution?.isApple != true`, with
`Platform.desktop.kt:32-38` mapping `"mac-app-store"` to `Distribution.MAC_APP_STORE` from the generated
`CAMPFIRE_DESKTOP_DISTRIBUTION` (`presentation/build.gradle.kts:44-60`). §5, `:102-105`, runs
"`./gradlew :app:desktop:packagePkg` with the signing properties" and nothing else.

**Microsoft.** `documentation/publishing/microsoft-store.md:56` — "Build the package from the unpacked application:
`./gradlew :app:desktop:createDistributable`, copy the manifest and the images next to `Campfire.exe`…"; `:66-67` —
"the Microsoft Store has no rule against naming other platforms, so Settings lists them all". Every published desktop
artifact is a release (ProGuard) build started once before it ships (`desktop-publish.yml`), and Settings → About
names no other build since commit `96e24dfd`.

The Mac guide's own opening ("None of the steps below has been done yet", `:14`) is otherwise still true: `bundleID`,
`appStore`, `signing`, the entitlements, `TargetFormat.Pkg` and the Mac `ITSAppUsesNonExemptEncryption` are all absent
from `app/desktop/build.gradle.kts`. The Microsoft guide's opening is true.

## The change

Docs only.

**`documentation/publishing/ios-app-store.md`**

- `:13-14`: "None of the steps below has been done yet, and the ones marked…" → "The boxes that are ticked are done in
  the repository; nothing else has been started. The ones marked…"
- `:34-35` → tick it and say where it is:
  `- [x] \`ITSAppUsesNonExemptEncryption\` = \`NO\` is in \`app/ios/iosApp/iosApp/Info.plist\` (the app only uses the system's HTTPS, which is exempt; without the key every upload stops to ask).`
- `:36-39` → tick it, keep the link and the *(verify)*:
  `- [x] The privacy manifest (\`app/ios/iosApp/iosApp/PrivacyInfo.xcprivacy\`, in the target's resources) declares no tracking, no collected data and the file-timestamp reasons Kotlin/Native and Compose need (\`C617.1\`, \`3B52.1\`). Check it against JetBrains' list again before the first upload, since App Store Connect rejects a missing reason: https://kotlinlang.org/docs/apple-privacy-manifest.html *(verify)*`
- `:94` → "Fill in `Distribution.APP_STORE`'s `listingUrl` in `presentation/…/ui/platform/Platform.kt`".

**`documentation/publishing/mac-app-store.md`**

- `:59-61` → delete the item. The "Already taken care of in the code" paragraph at `:91-93` already says the right
  thing.
- `:102-103`, §5 → "…run `./gradlew :app:desktop:packagePkg -Pcampfire.desktop.distribution=mac-app-store` with the
  signing properties — the distribution property is what takes the donation link out (guideline 3.1.1), and a leg
  that forgets it uploads a build App Review turns down — and upload with…"
- `:121` → "Fill in `Distribution.MAC_APP_STORE`'s `listingUrl` in `presentation/…/ui/platform/Platform.kt`."

**`documentation/publishing/microsoft-store.md`**

- `:102` → "Fill in `Distribution.MICROSOFT_STORE`'s `listingUrl` in `presentation/…/ui/platform/Platform.kt`".
- `:56`, the build step, says `./gradlew :app:desktop:createDistributable` — the debug image, without ProGuard and
  without the distribution property. Replace with `./gradlew :app:desktop:createReleaseDistributable
  -Pcampfire.desktop.distribution=microsoft-store` ("copy the manifest and the images next to `Campfire.exe` in
  `app/desktop/build/compose/binaries/main-release/app/Campfire/`"), and add: "Start that image once before packing
  it: ProGuard breaks things only a start shows (see `app/desktop/CLAUDE.md`)." Today the property changes nothing
  visible on Windows (`canAskForDonations` only reads `isApple`), but it is what the build is for and what any later
  store-specific behaviour will read.
- `:66-67`, "the Microsoft Store has no rule against naming other platforms, so Settings lists them all" — Settings
  lists no builds at all any more, only the README link (root `CLAUDE.md`, "The app says nothing about the other
  builds…"). → "Nothing to change for the store's rules: Settings names no other build (only a link to the README),
  and the donation link is allowed (policy 10.8) as long as …".

## Tests

None: documentation only.

## Verification

1. `grep -n "\`url\`" documentation/publishing/*.md` prints nothing; `grep -n listingUrl documentation/publishing/*.md`
   prints the three edited lines.
2. `grep -n APP_SANDBOX_CONTAINER_ID documentation/publishing/mac-app-store.md` prints nothing.
3. Open `app/ios/iosApp/iosApp.xcodeproj` in Xcode: `PrivacyInfo.xcprivacy` is in the target's Copy Bundle Resources,
   and `Info.plist` has the encryption key — the two ticked items describe what is there.

## Docs

This plan is the docs change. Root `CLAUDE.md` describes `Distribution` with `listingUrl` correctly already; no change.

## Files touched

- `documentation/publishing/ios-app-store.md`
- `documentation/publishing/mac-app-store.md`
- `documentation/publishing/microsoft-store.md`

## Depends on

Nothing. Plan 49 also edits `documentation/publishing/ios-app-store.md` (the build-number item, lines 42-43) — a
different line, either order.
