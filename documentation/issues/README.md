# The 58th review

Reviewed at `aa5daa860` on `main`: the 57 commits since the 57th review's plans were removed (`cb7e26db2..aa5daa860`),
mostly user-facing work (the page model and its crop, pages read from their links, the tab chrome swaps, the iOS share
notification) and the nit-pick rounds on top of it. Angle (the owner's): code quality of what was delivered. Three Opus
area reviewers (import crop; pages from links and the share extension; the presentation changes), a Sonnet docs check
(no false claims), two Opus fixers, one regression read (clean but one nit, fixed), two plan writers, two challengers.

## Headlines

- Privacy: a page read on Android kept its cookies, storage, and cache, and a page's service worker reached hosts the
  page itself may not (both fixed). On Linux a page answering with an attachment is still saved to Downloads (05).
- The page crop counts blank lines as song lines, which the model was never trained on (01).
- The tab chrome's swap paired two leaving copies when a third tab interrupted it, and clipped the travelling icon.

## Fixed during the review

Interleaved with the owner's own commits from another session.

| Commit | Message |
|---|---|
| `74f78c1b6` | Keep two tab copies on their way out from matching each other when a third tab is opened during a swap. |
| `3fbfeefaf` | Drop the tab chrome's duplicate crossfade animation and share one helper for its swap animations. |
| `72bfe3479` | Look up a tab chrome part's counterpart once per draw instead of on every value it decides. |
| `1f3e92d3c` | Stop clipping a tab chrome part's icon at its own button while it travels to the other tab's spot. |
| `97bfba36e` | Share one rule for the container a tab chrome part's own button leaves transparent. |
| `95b71aac4` | Share one hidden scale between the tab chrome parts and the bar's detached actions. |
| `f53787096` | Name on the import report which of its stages draw their own progress, rather than repeating them where the window's bar is hidden. |
| `3ad7b9f3c` | Sort the pinned list header imports into place. |
| `7bd013568` | Correct the page model's docs on which lines past the read limit a crop keeps. |
| `f53a655d4` | Correct the page model's docs on which layout values it caps. |
| `429a16b8d` | Remove the page crop's unused metadata lines. |
| `35807a958` | Rename the page crop's title and artist lines apart from its header lines. |
| `1ee0b4929` | Name the page model's library among the modules with an explicit public API. |
| `17a6d0652` | Clear the cookies, storage, and cache a page read on Android leaves behind. |
| `a4d2388ee` | Refuse a page's service worker requests off public https hosts on Android, as its own are. |
| `6c37379e8` | Answer a page as unreadable rather than fail when no web view can be made to read it. |
| `a347fea00` | Treat a page whose web content process ended on iOS as a failed read. |
| `30113eb03` | Hold the share extension's notification to its one second wait, the settings check included. |
| `ea1d94182` | Log a page crop that fails while a page read from its link is imported. |
| `79bb02af9` | Write Android's cookie jar to disk only once a page read's cookies are removed. |
| `8dab1debe` | Stop offering Campfire in the share sheet for property list files on iOS, which it cannot import. |

## Index

| # | Plan | Severity | Lane |
|---|---|---|---|
| 01 | Leave blank lines out of the page crop's song count and its first song line | medium | C |
| 02 | Keep a chord row the model drops only inside a document's song or straight after it | low | C |
| 03 | Keep a document's unsure title and artist lines only where they head the song | low | C |
| 04 | Score the page corpus through PageTextCrop itself, not a copy of it | low | C |
| 05 | Refuse downloads in the Linux page reader | medium | W |
| 06 | Load the desktop web view helper off the main thread | low | D |
| 07 | Read an http page address by its https form | low | I |
| 08 | Block pictures, media, and plain http in the iOS page reader | low | W |
| 09 | Run the page model once per page read from a link | low | C |

## Lanes

| Lane | Area | Plans, in order | Files owned |
|---|---|---|---|
| C | the page crop | 01, 02, 03, 04, 09 | `libs/pagesong`, `libs/chordsheet/.../convert/ChordSheetSongCrop*`, `domain/implementation` importing, `PageCropperImpl`, `PageCorpusTest`, their build files and `CLAUDE.md`s |
| W | native page readers | 05, 08 | `desktopMain/native/linux`, iOS `WebKitBrowser.kt`, a comment in `desktopMain/native/macos` |
| D | desktop helper load | 06 | `DesktopPageSource.kt`, `CampfireViewModel.kt`, `PastedSongController.kt` and its test |
| I | http addresses | 07 | `OffscreenPageSource.kt` and its test |

09 was written for lane I and moved into C, after 04, since both rewrite `PageCropperImpl.songLinesOf`.

**Merge order: C, W, I, D.** C is the largest and touches the most docs; W and I share only the module `CLAUDE.md`;
D is one plan with nothing in common with the others.

**Shared file:** `data/source/remote/implementation/CLAUDE.md` (Pages section) is edited by W (05, 08) and I (07):
each lane edits only its own bullets, and the merge keeps every sentence of both.

## Decisions

- 02: option (b), a closing chord row straight after the song stays, a diagram block's names go (the owner, as
  recommended).
- 07: option (a), an http address is read by its https form (the owner, as recommended).
- The iOS share extension's `com.apple.property-list` activation clause, which offers Campfire for any shared `.plist`
  file: removed (the owner, against the first recommendation to accept it). Proven on the iOS 27.2 simulator first:
  Safari's page share still lists Campfire and delivers PageText.js's result, a link share and `.cho`/`.pdf` files
  still do, a `.plist` from Files no longer does. A one-line fix, so it landed during the review instead of a plan.

## Challenge

Ran on all nine. Sound: 02, 05, 07. Amended: 01 (blank lines are never the title or artist line either, and the
document crop's header ignores them), 03 (every line labelled title or artist kept wherever it is; only the 2% rule is
bound to above the song), 04 (artist-only headers count as read; pages whose line count changes on a re-split are
skipped), 06 (a daemon thread, KDocs named), 08 (one main-thread `CompletableDeferred` compile no read can cancel), 09
(the memo under a lock of its own, never held while the model runs). Dropped: none.

## Checked and found solid

The Kotlin page model against PyTorch (GRU, encoder, weights format, features, rules; golden tests); the index alignment
of document lines and scores; `PageCropperImpl`'s weight loading; the offscreen read's bounds and closing; the macOS,
Windows, and Linux helpers' memory and threading; `CompileWebViewHelper`'s checksum and CI failure; the share
extension's `Once`; the tab chrome reading its rectangles only in draw; the back gesture rework; the
`SearchableTopAppBar` split; `WholePageOffer` and its tests; every sticky list item's `pinnedOverList`.

## Dropped after verification

- `predictiveBackSpec` living in `TabFadeSpecs.kt`: legal under the layers, a rename only; left.
- `WholePageBanner` restating `WarningCard`'s look: the banner is meant to stand out from it; left.
- `ImportReportScreen` using the new `drawsItsOwnProgress`: its branches draw each stage's content, so folding them
  would read worse.
- `setServiceWorkerClient` throwing leaking one view: not a failure any Android version shows.
