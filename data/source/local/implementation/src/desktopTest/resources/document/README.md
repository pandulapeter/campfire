<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Document regression fixtures

The text is the opening line of John Newton's public-domain Amazing Grace, with deliberately simple chord
positions. Fixtures are actual independent-producer files, not the test-only Kotlin PDF writer. Adjacent `.cho`
files pin the conversion; `DocumentGoldenTest` reads the bytes and also checks deterministic repeat conversion.
The test does not regenerate them or require any producer application.

| Files | Producer and purpose |
| --- | --- |
| `python-docx.docx` | python-docx, ordinary styled paragraphs and a monospaced chord line |
| `libreoffice.docx`, `libreoffice.pdf` | Actual LibreOffice resave/PDF export of that Word document |
| `chrome.pdf` | Chrome headless print of `chrome.html`, embedded browser fonts and ToUnicode maps |
| `reportlab.pdf` | ReportLab, compressed content and built-in fonts |
| `reportlab-unicode.pdf` | ReportLab with embedded Arial: Hungarian double accents, Greek and Cyrillic |
| `two-column.pdf` | ReportLab, two verses side by side below a title |
| `songbook.pdf` | ReportLab, two separately titled song pages |
| `protected.pdf` | pypdf encryption of the ReportLab sheet with an empty password; must be unreadable |
| `campfire.pdf` | Campfire `PrintRendererTest`, page image plus invisible glyphless Type 3 text; Hungarian accents, styled title and proportional chord placement |
| `campfire-columns.pdf` | Campfire `PrintRendererTest`, thirty numbered lyric/chord pairs flowing through both columns; song reading order and no page footer in the imported lyrics |

`generate_fixtures.py` creates the python-docx, ReportLab and pypdf files using those Python libraries. It sets
stable ZIP entry dates and ReportLab's invariant mode. The Unicode fixture uses macOS's supplemental Arial font;
the existing binary fixture is what the test reads on every host.

Regenerate LibreOffice exports in a separate output directory, so the source is not overwritten:

```sh
soffice --headless --convert-to docx --outdir /tmp/campfire-word python-docx.docx
soffice --headless --convert-to pdf --outdir /tmp/campfire-pdf python-docx.docx
```

Name the actual resulting exports `libreoffice.docx` and `libreoffice.pdf`. Chrome uses the checked-in HTML:

```sh
"/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" --headless \
  --user-data-dir=/tmp/campfire-fixture-chrome --no-pdf-header-footer \
  --print-to-pdf=chrome.pdf "file://$(pwd)/chrome.html"
```

The Word sheet and the PDF column/songbook layouts were rendered and visually inspected when the fixtures were
added. Changes to a golden must be justified by inspecting the producer's actual page, not by accepting whatever
the parser happened to emit. The protected file deliberately has no golden.

The Campfire fixture is produced by the real exporter rather than an independent PDF library. Regenerate it from
the renderer test, then inspect the result before replacing its binary (the golden is intentionally hand-written):

```sh
CAMPFIRE_PRINT_QA_DIR=/tmp/campfire-print-qa ./gradlew :presentation:desktopTest \
  --tests '*PrintRendererTest' --rerun-tasks
cp /tmp/campfire-print-qa/campfire.pdf \
  data/source/local/implementation/src/desktopTest/resources/document/campfire.pdf
cp /tmp/campfire-print-qa/campfire-columns.pdf \
  data/source/local/implementation/src/desktopTest/resources/document/campfire-columns.pdf
```

`DocumentGoldenTest.campfireExportImportsAccentsAndChordsAtTheirPrintedPositions` checks the exported bytes through
the real document local source and chord-sheet converter, including omission of the printed page-count footer.

Still missing from the planned producer matrix: real Microsoft Word, Google Docs, Apple Pages, Safari print and
LaTeX exports. These fixtures do not claim to cover those producers; add their original bytes and goldens when
available. Platform picker/drop/share and snackbar behavior is checked separately in
`documentation/release-check.md`.
