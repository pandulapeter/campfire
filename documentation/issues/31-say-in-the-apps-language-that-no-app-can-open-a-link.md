# Say in the app's language that no app can open a link, instead of toasting Android's exception text

**Kind:** bug · **Severity:** low · **Platforms:** Android
**Challenged:** amended — the app already has this message: the desktop answers a link nothing opens with
`CampfireViewModel.onLinkNotOpened(url)`, a localized snackbar naming the address (`Message.LinkNotOpened`,
`error_link_not_opened`, read with `textResource`). Android now reports to the same place by returning whether the link
was opened, instead of handing a new string to the activity for a toast; no new string keys.
**Files:** `app/android/src/main/java/com/pandulapeter/campfire/CampfireMainActivity.kt`,
`presentation/src/androidMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireAndroidApp.kt`,
`presentation/CLAUDE.md` (the `CampfireAndroidApp.kt` entry), `app/android/CLAUDE.md` (the `CampfireMainActivity`
entry)

## Problem

`CampfireMainActivity.openInCustomTab`:

```kotlin
    private fun openInCustomTab(uri: Uri, isDarkTheme: Boolean) = try {
        CustomTabsIntent.Builder()
            ...
            .launchUrl(this, uri)
    } catch (exception: ActivityNotFoundException) {
        Toast.makeText(this, exception.message, Toast.LENGTH_SHORT).show()
    }
```

On a device with no browser (de-Googled phone, kiosk or work profile), tapping a song's link chip, the ChordPro
reference in the editor, Help and support, the website, GitHub or the author row toasts the framework's English text —
"No Activity found to handle Intent { act=android.intent.action.VIEW dat=https://… }" — whatever the app's language.
Pre-existing since 4.6.1, not a crash.

The desktop already handles the same case in the app's language: `CampfireDesktopApp.kt` passes
`urlOpener = { url -> if (!openUrl(url)) viewModel.onLinkNotOpened(url) }`, and `CampfireApp` shows
`Message.LinkNotOpened` as `textResource(Res.string.error_link_not_opened, url)` ("Could not open %1$s" /
"Nem sikerült megnyitni: %1$s").

## Fix

Do the same on Android: the activity says whether the link was opened, the UI reports it.

1. `CampfireMainActivity.kt`:
   - `openUrl(url: String, isDarkTheme: Boolean): Boolean` — the Play Store branch returns `true` after its
     `startActivity` (in place of the bare `return`), and the function ends with `return openInCustomTab(uri, isDarkTheme)`.
     Extend its KDoc: "Returns false where nothing could open it, for the UI to say so in the app's language."
   - `openInCustomTab(uri: Uri, isDarkTheme: Boolean): Boolean = try { …launchUrl(this, uri); true } catch (_:
     ActivityNotFoundException) { false }`.
   - Remove the `android.widget.Toast` import if nothing else uses it (nothing does at `3eaa29e24`).
   - `urlOpener = ::openUrl` stays as it is.
2. `CampfireAndroidApp.kt`: the parameter becomes `urlOpener: (url: String, isDarkTheme: Boolean) -> Boolean`, its
   KDoc `@param urlOpener` "Opens the given URL, styled for the given theme, and says whether anything could open it."
   and the call `urlOpener = { url -> if (!urlOpener(url, isDarkTheme)) viewModel.onLinkNotOpened(url) }`, the
   desktop's form.
3. Docs: in `presentation/CLAUDE.md`'s `CampfireAndroidApp.kt` entry, after "The `urlOpener` receives whether the dark
   theme is active so Custom Tabs can match": ", and returns whether anything opened the link — one nothing opens is
   the desktop's `Message.LinkNotOpened` snackbar". In `app/android/CLAUDE.md`'s `CampfireMainActivity` entry, after the
   Play Store fallback sentence: "A link nothing can open (no browser at all) is reported back to the UI, which says so
   in the app's language."

The iOS `urlOpener` (`(String) -> Unit`, `CampfireIosApp.kt` / `CampfireViewController.kt`) and the common
`CampfireApp` signature are untouched; the only callers of the Android signature are `CampfireMainActivity` and
`CampfireAndroidApp` (grep of the whole repository, `:app:baselineprofile` included).

## Tests

None (platform shell). Compile: `./gradlew :app:android:compileDebugKotlin :app:desktop:compileKotlin`.

## Manual check

On an emulator, disable Chrome (`adb shell pm disable-user com.android.chrome`, and any other browser), switch the app
to Hungarian, tap Settings → Help and support → a snackbar reads "Nem sikerült megnyitni: https://campfire-songbook.com/support/".
The Rate Campfire row still opens the Play Store app where there is one. Re-enable Chrome afterwards.
