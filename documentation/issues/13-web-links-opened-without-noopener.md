# Open song links on the web without handing the opened page `window.opener`

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** web
**Files:** presentation/src/wasmJsMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireWebApp.kt, app/web/CLAUDE.md

## Problem
The web URL opener is `urlOpener = { url -> window.open(url, "_blank") }`
(`CampfireWebApp.kt:84`). Until 5181aab8 it only ever opened the app's own links (Settings → About). Now it also
opens every `{meta: link …}` of a song (`SongDetailsScreen` link chips → `urlOpener`), and those addresses come from
files other people wrote: imported archives, shared `.cho` files, a synced folder. Root CLAUDE.md: "Any page is taken,
whatever site it is on".

Unlike an `<a target="_blank">`, `window.open(url, "_blank")` does **not** imply `noopener` in any browser: the opened
page gets a live `window.opener` reference to the Campfire tab. A hostile page linked from a song can then run
`window.opener.location = "https://pandulapeter-github.io.example/campfire/…"` and replace the Campfire tab with a
look-alike while the user reads the link (reverse tabnabbing) — e.g. a fake "Connect Dropbox again" page. It also keeps
the two tabs in one browsing context group, so the opened page can share the Campfire tab's process on browsers that
group by opener. GitHub Pages sends no `Cross-Origin-Opener-Policy`, so nothing else severs it.

## Fix
1. `CampfireWebApp.kt:84`: `urlOpener = { url -> window.open(url, "_blank", "noopener,noreferrer") }`. With
   `noopener` `window.open` returns null, which the lambda already ignores. `noreferrer` additionally keeps the
   Campfire address — which names the song file, `song/{song}` — out of the linked site's `Referer` on browsers
   whose default policy would send more than the origin.
2. Optionally hoist the lambda out of composition (a top-level `private fun openInNewTab(url: String)`) so it is not
   a new instance on every recomposition of `CampfireWebApp`.
3. Mention in `app/web/CLAUDE.md` that links open with `noopener,noreferrer` and why.

## Verification
Web dev server (`./gradlew :app:web:wasmJsBrowserDevelopmentRun`): add a link to a song pointing at a page that runs
`document.body.textContent = String(window.opener)`; tap the link chip. Expected after the fix: `null`, and the new
tab still opens (user activation is kept, since the call is still made from the click). Settings → About links still
open.

## Conflicts
None known (the chordpro lane's link plans touch parsing and the iOS opener, not this file).
