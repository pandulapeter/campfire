<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# 47 — Version each web file by its own content rather than by the whole distribution's

| | |
|---|---|
| Lane | F |
| Impact | low–medium (every returning visitor, once per web release) |
| Confidence | high |
| Platforms | web |
| Files | `app/web/build.gradle.kts`, `app/web/src/wasmJsMain/resources/index.html`, `app/web/CLAUDE.md` |
| Depends on / conflicts with | Conflicts with 46 (same `index.html` and `app/web/CLAUDE.md`, different blocks). |
| Commit message | `Version every file of the web distribution by its own content so that a release leaves unchanged binaries cached.` |

## Problem
**Why the versioning exists** (`app/web/CLAUDE.md:56–64`, `index.html:67–77`). GitHub Pages lets a browser keep any file for ten minutes by its address, and a deployment replaces files one at a time. With fixed names, a fresh page could start an old `campfire.js` whose binaries were already deleted, or a new binary with old resources. So every file but the page is fetched as `<file>?v=<version>`, and each release becomes a set of addresses only its own page knows. That property has to survive this change.

**How it is done today.**
- `app/web/build.gradle.kts:95–109` computes **one** version for the whole distribution:

  ```kotlin
  deployed.map { … }.filter { (path, _) -> path != page }.sortedBy { … }.forEach { (path, file) ->
      digest.update("$path\u0000".toByteArray())
      digest.update(file.readBytes())
  }
  val version = digest.digest().take(8).joinToString("") { "%02x".format(it) }
  ```

- `index.html:89–96` puts that version on every address inside the page's folder:

  ```js
  resolved.searchParams.set('v', build.version);
  ```

**What that costs.** Any change to any file, including a one-line Kotlin change that only alters the app's own `.wasm` and `campfire.js`, gives *every* file a new address. That includes skiko's WebAssembly binary, which only changes with a Compose upgrade, plus the fonts and the drawables. So after each release every returning visitor downloads the whole distribution again: 16 MB, or 5.5 MB over the wire with GitHub Pages' gzip (`app/web/CLAUDE.md:103`). Unchanged files would otherwise need at most a `304` revalidation against their ETag.

## Fix
1. **Build manifest**, in `finishWebDistribution` (`app/web/build.gradle.kts`):
   - Hash each deployed file except the page on its own: SHA-256 of its bytes, first 8 bytes as hex.
   - Emit a map keyed by path relative to the distribution root, with invariant separators, exactly as the current `path` value is built:

     ```kotlin
     val versions = deployed.map { it.relativeTo(directory).invariantSeparatorsPath to it }
         .filter { (path, _) -> path != page }
         .sortedBy { (path, _) -> path }
         .associate { (path, file) -> path to sha256(file.readBytes()).take(8).joinToString("") { "%02x".format(it) } }
     ```

   - The manifest becomes `{"binaryCount":…,"binaryBytes":…,"files":{"campfire.js":"…","composeResources/…/font/inter_regular.ttf":"…",…}}`. Write it as JSON with proper escaping (`"` and `\` in a path), not string concatenation. Compose resource paths are plain today, but nothing guarantees it.
   - Keep the log line, logging the number of files. Drop the single `version` field, or keep it as a hash of the map purely for the log.
2. **`index.html`, `versioned(url)`** (lines 89–96): look the file up by its path relative to `folder`, and append its own hash.

   ```js
   function versioned(url) {
       var resolved = new URL(url, document.baseURI);
       if (!build || resolved.href.indexOf(folder) !== 0) return url;
       var path = decodeURIComponent(resolved.pathname.substring(new URL(folder).pathname.length));
       var hash = build.files[path];
       if (!hash) return url;
       resolved.searchParams.set('v', hash);
       return resolved.href;
   }
   ```

   A file missing from the map is left unversioned, as on the development server today. That covers the favicons, which `setFavicon` sets directly rather than through `fetch` and which are named by color anyway (`app/web/CLAUDE.md:64`). The font preloads, `window.campfireVersioned('campfire.js')` and the storage worker (`FileStorage.wasmJs.kt:271`, `campfireVersioned('opfs-writer.js')`) all go through `versioned` and need no change.
3. **Why consistency still holds.** A page only ever names addresses whose query is the hash of the exact content it was built with. An old `campfire.js` can therefore never be paired with a new binary, because the new binary's hash is different and the old page asks for the old one. Unchanged files keep their addresses, which is the point. The deployment race (a page asking before the file is copied, with GitHub Pages ignoring the query) is exactly as it is today, neither better nor worse.
4. **First, check whether webpack already content-hashes the `.wasm` names.** Look for names like `8a1b…wasm` in `build/dist/wasmJs/productionExecutable`. If it does, their names already change with their content, and the per-file hash is harmless on them. The win is then all the files with fixed names, including skiko's if its name is fixed. Say which in the commit's notes.
5. **Documentation.**
   - `app/web/CLAUDE.md:56–64`: `?v=<hash>` is now each file's own hash.
   - `app/web/CLAUDE.md:94–96`: the build manifest paragraph now describes a map of paths to hashes rather than "a hash of every other file of the distribution".
   - The comment block at `index.html:67–77`, to match.

Must not change: that `index.html` itself is never versioned, that addresses outside the folder (the sync service) are untouched, the byte total the progress bar uses, the development server's behavior, or the `precompress` path.

## Verification
- `./gradlew :app:web:wasmJsBrowserDistribution` twice on the same sources: the page and the manifest must be byte-identical. The task must stay reproducible (`app/web/CLAUDE.md:96–97`).
- Make a trivial Kotlin change in `:presentation` and build again. Only the app's `.wasm` and `campfire.js` entries (and whatever else really changed) get new hashes; skiko's binary, the fonts and the drawables keep theirs. Diff the two manifests.
- Serve the distribution as in the memory note `web-target-verification`. In DevTools → Network, every request of the page, the fonts, the drawables, `campfire.js`, the `.wasm` files and the worker (after a first save) carries `?v=<its hash>`. The app starts, and a song can be saved.
- Deploy the second build over the first on the local imitation. The unchanged binaries are served from the disk cache (or answered `304`) rather than downloaded again.
