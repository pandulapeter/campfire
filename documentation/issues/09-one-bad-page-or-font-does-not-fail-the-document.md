# Let a malformed page, font, form or stream cost only itself, and let the readability rule decide the rest
**Challenged:** amended — the rotation rule said "nearest" but wrote truncation (`/Rotate 359` became 270); the cyclic-form check must stay outside the form's `try`/`finally` or it removes the outer invocation's entry; encryption found by a lazy recovery scan inside a page or font must stay fatal; and the 2,000-page and other budget checks must sit where the page catch rethrows them.

**Kind:** robustness  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/document/PdfTextExtractor.kt`, `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/document/PdfFile.kt`, `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/document/PdfFonts.kt`, `data/source/local/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/document/PdfTextExtractorTest.kt`, `data/source/local/implementation/CLAUDE.md`

## Problem
Anything that `require`s or `error`s anywhere in `PdfTextExtractor.extract` fails the whole document, and
`DocumentLocalSourceImpl` turns that into "no readable text". For a songbook of 300 pages one odd page loses every song.
Verified on HEAD (probes): a two-page PDF whose second page has `/Rotate 45` throws `Failed requirement` and the first page's
text is lost; a content stream with a stray `)` (`... Tj ET ) q`) throws "Unexpected PDF delimiter"; 2,100 pages throws. Other
sites in the same spirit: `page()`'s `require(x1 > x0 && y1 > y0 && rotate % 90 == 0)` and the 1,000,000 MediaBox bound,
`matrix()`'s `abs <= 1_000_000` check, `PdfFont`/`cmap`/`cidWidths` `require`s reached from `Tf`, a `Do` form that fails to decode,
a `/Contents` stream that fails to inflate.

## Fix
Depends on `07-content-work-budget-shared-by-pages-and-forms.md` (`PdfLimitException`) and benefits from
`08-cid-width-ranges-are-bounded-by-what-the-font-can-hold.md`.

Catch at three levels, always as
`catch (e: CancellationException) { throw e } catch (e: PdfLimitException) { throw e } catch (e: Exception) { ... }`:

1. **Page** (around the body of `page()`, i.e. bounds, contents assembly and `interpret`): on a non-limit failure add
   `Page(emptyList(), 792.0)` to `pages` (the slot must exist so `isPageCount` and the running-line statistics keep their
   numbering), discard that page's partial glyphs, `shown += 1; unreadable += 1` (so a one-page document that fails still ends as
   "no readable font mapping", exactly as today) and `failedPages++`. Page-level failures of a form or content stream are
   covered here.
2. **Font** (the `Tf` branch): on a non-limit failure set `state.font = null` (text shown with no font is already counted as
   unreadable bytes) and remember the dictionary in a `failedFonts` set so the construction is not retried per `Tf`.
3. **Form** (the `Do` branch): on a non-limit failure skip the form, `shown++; unreadable++`, `activeForms.remove(form)` in a
   `finally`.

Precise placement, because a catch that is a few lines too wide changes the meaning:
- The `require(pages.size < 2000)` at the top of `page()` is a `PdfLimitException` after plan 07, so the page catch
  rethrows it; keep it that way (a plain `require` there would turn every page past 2,000 into one more empty page).
- **Form**: keep the cycle test outside the `try`: `if (!activeForms.add(form)) { shown++; unreadable++ } else try { ... } finally { activeForms.remove(form) }`.
  Inside the `try`/`finally` as the plan first read, a cyclic `Do` would fail the `add` and then the `finally` would
  remove the entry that the outer, still running invocation of the same form owns. A cyclic form is skipped like any
  other failed form rather than failing the page.
- **Encryption stays fatal wherever it is found.** `PdfFile.scan()` runs lazily (from `resolve` on a missing object,
  from `catalog()`), and when it meets an `/Encrypt` trailer it throws a plain `require(!encrypted) { "Encrypted PDF" }`
  from inside whatever page or font asked. Expose `val isEncrypted` on `PdfFile` and, in all three catch blocks,
  rethrow when `file.isEncrypted` is true, so ciphertext is never read as text page by page.

Final rule: keep `require(shown > 0 && unreadable * 2 <= shown)` and add `failedPages * 2 <= pages.size` (a document most of whose
pages failed is not a document we read). Fatal and still thrown: encryption, every `PdfLimitException`, "PDF has no
catalog", a broken page tree (`walk`'s cycle and depth checks, and a kid that does not resolve to a dictionary), a
document with more than 2,000 pages. **Decision on the 2,000-page limit:** keep rejecting, do not read "the first
2,000": a songbook silently losing its tail is worse than telling the user it cannot be read; say so in a comment at
`require(pages.size < 2000)`.

A `/Rotate` that is not a multiple of 90 is rounded to the **nearest** multiple instead of failing the page: after the
existing `((rotation % 360) + 360) % 360`, take `((rotate + 45) / 90 * 90) % 360` (so 359 and 44 read as 0, 45 and 134 as 90).
A producer that writes `/Rotate 359` meant upright, not 270, and is not hostile.

Also update the `document/` paragraph of `data/source/local/implementation/CLAUDE.md`: "a malformed page, font or form costs only itself, and the document
is unreadable only when more than half of its glyphs or pages are".

## Tests
`data/source/local/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/document/PdfTextExtractorTest.kt`: (a) `aMalformedSecondPageDoesNotCostTheFirst`: the two-page file of the probe (page 2 `/Rotate 45`
becomes rounded and reads, so use a MediaBox of `[0 0 0 0]` for the bad page) returns a document whose first page holds "hi";
(b) `aStrayParenthesisInOnePageStreamOnlyLosesThatPage`; (c) a font whose `/W` fails (plan 08's per-font cap) loses only the text in that font while another font on the
page still reads (show more glyphs in the good font than in the bad one: text shown with no font counts as unreadable); (d) a one-page document with a bad page is still rejected; (e) a 2,100-page document is still rejected; (f)
`PdfLimitException` (use the plan-07 hostile form file) is not swallowed per page or per form; (g) a form that
`Do`es itself is skipped and the page's other text still reads; (h) `/Rotate 359` reads like `/Rotate 0`. Cancellation: the existing test
`oversizedShownStringsStopAtTheGlyphBudgetAndCancellationPropagates` must keep passing.

## Manual check
Import a songbook PDF in which one page was replaced by garbage (hex-edit one content stream): the other songs import.
