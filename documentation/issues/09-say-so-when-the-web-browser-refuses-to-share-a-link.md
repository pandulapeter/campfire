<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# Say so when the web browser refuses to share a link, instead of doing nothing

**Kind:** bug · **Severity:** low · **Platforms:** web
**Files:** `presentation/src/wasmJsMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/FilePicker.wasmJs.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/FilePicker.kt` (only if the
contract's KDoc changes), `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/export/ExportController.kt`,
`presentation/src/wasmJsMain/CLAUDE.md`, `presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/screens/export/ExportControllerTest.kt`
**Challenged:** amended — the contract wording is pinned so it stays true of iOS (which quietly returns false while
another sheet is up) and Android (whose `startActivity` can already throw); a common test of the catch is added. The
only caller of `shareText` is `ExportController.shareLink` (both overloads, the export screen and Settings), which
already catches, so throwing breaks no caller.

## Problem

```kotlin
override suspend fun shareText(text: String, subject: String) = shareUrl(url = text, title = subject).await<JsString>().toString() == SHARED
```

`shareUrl` resolves `refused` for anything but the user closing the sheet (`NotAllowedError` after a lost user
activation, `InvalidStateError` while another share is open, `TypeError` for an unsupported payload), and `shareText`
returns false for it exactly as for a dismissal. `ExportController.shareLink` then shows nothing: Settings → Share
Campfire and the export screen's link Share button silently do nothing. Files fall back to a download (`shareFile`);
a link has no fallback.

## Fix

Make a refused text share throw (an `IllegalStateException` naming the error) rather than return false, so
`ExportController.shareLink`'s existing `catch (exception: Exception)` shows `Message.ExportFailed`, as a failed share
does on every other platform; a dismissal stays a quiet false. Have `shareUrl`'s rejection resolve with the error's
name (`'refused:' + error.name`) so the log line says why. Rewrite the end of `FilePicker.shareText`'s KDoc (today "false where nothing was shown") as: false where the user
dismissed the sheet or nothing could be shown for a reason not worth telling (another sheet already up); an exception
where the platform refused it for a reason the user should hear of, which the caller reports. Leave `shareFile`'s web
fallback to a download as it is. (Copying the link to the clipboard instead was
considered: the clipboard needs the same user activation the share lost, so it would fail in the same case.)

## Tests

`ExportControllerTest` (commonTest): with a fake `FilePicker` whose `shareText` throws, `shareLink` sends
`Message.ExportFailed`; with one returning false, no message. The browser's share sheet itself cannot be driven; the
`app/web` Node tests do not cover the Kotlin side. Compile `:app:web:compileKotlinWasmJs`.
