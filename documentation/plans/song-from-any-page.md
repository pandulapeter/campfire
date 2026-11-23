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

- One WebExtension for Chrome, Edge, and Firefox, and its Safari Web Extension build inside the iOS app: a toolbar
  button that reads the page's `innerText` and title and hands them to Campfire, which treats them as a shared page's
  text (`ImportedFile.isPageText`, through `PastedSongController`), so nothing differs after the hand-over.
- On iOS it hands over through the app's own inbox, as the share extension does.
- On the desktop, how it reaches the running app (native messaging, or the single-instance endpoint
  `instance.endpoint` already accepts) and an open web build tab is a spike of its own, settled before this step starts.
- Never needed for anything a share or a paste can do. Each store's listing and review is the cost; nothing else
  depends on it.

## 2. Documentation

- At release time: the website's import page, the store listings, the privacy policy, and what's new.
