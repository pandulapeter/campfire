<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Plan: a song from any page

What remains of turning any website's song into a clean pre-filled editor. The pipeline itself (`:libs:pagesong`'s
page model, the crop of pastes, drops, shares, and documents, pages read from their address, and the share doors) is
in the code and its `CLAUDE.md` files (`libs/pagesong`, `presentation`, `data/source/remote`).

## 1. Optional: browser extensions

- One WebExtension for Chrome, Edge, and Firefox: a toolbar button that reads the page's `innerText` and title and
  hands them to Campfire, which treats them as a shared page's text, so nothing differs after the hand-over.
- Never needed for anything a share or a paste can do. Each store's listing and review is the cost; nothing else
  depends on it.

### Research (2026-11-23)

**The common door.** Every platform ends in `PastedSongController.importSharedText(SharedTextPayload(texts, subject))`,
which already crops a page's text (`isPageText`) and suggests the title from `subject` (`PageTitle.of`). Android and iOS
pass a `sharedTexts` flow to `CampfireApp`; the desktop (`CampfireDesktopApp`) and the web (`CampfireWebApp`) pass none
yet, so both need one.

**iOS: no Safari Web Extension.** The share extension's `PageText.js` already hands over `innerText`, title, and
address from Safari's share sheet into the share inbox; a Web Extension would be a second button for the same text, and
it could not open the app either. Android's Chrome has no extensions, and Firefox for Android shares instead.

**Desktop app: native messaging, per install.**

- Extension: `activeTab`, `scripting`, `nativeMessaging` (the least review friction on every store; no install-time host
  warning). `sendNativeMessage` from the background script starts the host once per click; a page's text is far under
  the 1 MB limit.
- The app writes a host manifest per browser when the user turns the feature on (per user: the `NativeMessagingHosts`
  folders on macOS and Linux, `HKCU\SOFTWARE\{Google\Chrome,Microsoft\Edge,Mozilla}\NativeMessagingHosts` on Windows),
  listing the Chrome Web Store, Edge Add-ons, and AMO IDs (Chrome and Edge give the same extension two IDs).
- The host is the app's launcher in a host mode: it reads the message from stdin and forwards it to the running
  instance over `instance.endpoint`, or starts the app. The endpoint protocol (`SingleInstance.kt`) carries only file
  paths and share links, so it gains a text argument kind (text and title) routed to the new `sharedTexts` flow; a cold
  start cannot carry the text in argv (Windows' ~32K limit, macOS passes no argv through LaunchServices), so the host
  starts the app and sends once the endpoint appears.
- **Mac App Store:** the sandbox redirects the manifest writes; it needs the
  `temporary-exception.files.home-relative-path.read-write` entitlement for the browsers' folders (Bitwarden's Mac App
  Store build has it) and a sandboxed, separately signed host. App Review may refuse it.
- **Microsoft Store (MSIX):** no declaration for a host exists, and HKCU writes are virtualized away from the browser;
  the workaround is a restricted capability (`unvirtualizedResources`), and Bitwarden and 1Password both ship without
  browser integration there. Treat as unsupported.
- Fallback where no host answers: the extension copies the text and says to paste it into Campfire.
- Rejected: a localhost listener (Chrome's and Firefox's Local Network Access prompts, port discovery, and a listening
  port on the privacy list), a `campfire:` link carrying the text (Windows truncates at ~2,083 characters, and Chrome
  asks first).

**Web build tab.** One host permission for the deployed origin; the extension finds the Campfire tab
(`tabs.query`) or opens one, and `scripting.executeScript` posts the text with `window.postMessage`; `index.html` gains a
`message` listener checking origin, source, and shape, feeding the web's new `sharedTexts` flow. Works in Chrome, Edge,
Firefox, and Safari. Not `externally_connectable` (no Firefox) nor a URL fragment (the text would land in history). A
second tab meets the Web Lock's "already open" page, so the extension posts to the owning tab.

**Stores.** Chrome Web Store: $5 once, MV3, a privacy tab declaring "website content" read, a few days' review (faster
with `activeTab`). Edge Add-ons: free. AMO: free, signing mandatory, source submission only if the code is bundled or
minified (plain hand-written JS avoids it); one manifest carries both `background.service_worker` and
`background.scripts` plus `browser_specific_settings.gecko.id`.

**Unverified, for the first spike:** whether jpackage's Windows GUI launcher has working stdin and stdout as a host (a
tiny console relay otherwise); Firefox's Local Network Access for extensions (moot unless localhost returns).

**Still to decide:** whether the step is worth it at all, given that the fallback (copy, then paste) is close to what
Ctrl+A, Ctrl+C already does and the app reads a shared page's address; whether the Mac App Store build asks App Review
for the entitlement or stays on the fallback; and whether the web build tab is in scope.

## 2. Documentation

- At release time: the website's import page, the store listings, the privacy policy, and what's new.
