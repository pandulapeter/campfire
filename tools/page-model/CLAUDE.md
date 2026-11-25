<!--
 This file is part of Campfire.
 Copyright (c) Pandula Péter 2017-2026.
 https://github.com/pandulapeter/campfire

 This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one at
 https://mozilla.org/MPL/2.0/.
-->

# tools/page-model

The trainer of the page model `:libs:pagesong` runs: which lines of a web page or a document are the song, its title,
its artist, its details, or clutter. Python over a private corpus folder outside git, run by hand, never in CI, and
not a Gradle module. Of what it makes, only the shipped weights and the golden test fixtures reach the repository. Each
script's docstring says what it reads and writes; this file is the order they run in and how a model is judged.

## Setup

`requirements.txt` (numpy, Playwright, and PyTorch) in a virtual environment at the repository root, where the
docstrings' commands run from (`.venv/bin/python -I tools/page-model/…`), and Playwright's Chromium:

```
python3 -m venv .venv
.venv/bin/pip install -r tools/page-model/requirements.txt
.venv/bin/python -m playwright install chromium
```

Git does not ignore `.venv`: never stage it.

Two folders feed it, both named in `local.properties`:

- **The page corpus** (`campfire.pageCorpus`): `urls.tsv`, written by hand, and everything the steps below add to it
  (`pages/`, `printed/`, `teacher/`, `labels/`, `synthetic/`, `documents/`, and `.runs/`).
- **The import corpus** (`campfire.importCorpus`, `ImportCorpusTest`'s): its documents, each beside a hand-corrected
  `.cho`, become labelled documents, and its songs are set as made-up pages and documents.

## Steps, in order

1. **Collect** (`collect.py <corpus>`): every page of `urls.tsv` rendered in headless Chrome into `pages/<id>.json`
   (its text as the app's web views read it, by the app's own `PAGE_SNAPSHOT_SCRIPT`, and that script's structure marks,
   one per line) and `printed/<id>.pdf`; `collect.tsv` says what each came
   to. A rerun picks up where the last stopped; a page that turns the browser away is left out.
2. **Label the pages** (`teacher.py prepare <corpus>`, then Claude, then `teacher.py merge <corpus>`): `prepare` writes
   each page as numbered lines and `teacher/INSTRUCTIONS.md`; Claude labels every page twice, independently, by those
   instructions, answering line numbers only (`teacher/<id>.run1.json` and `.run2.json`); `merge` keeps a page whose
   runs agree on 95% of its printed lines as `labels/<id>.json`, the lines they disagree on `IGNORE`.
3. **Make up documents** (`synthetic_documents.py <corpus> <import corpus> [--per-song N]`): each song of the import
   corpus laid out several ways, wrapped in clutter, and printed offline into `synthetic/`. Its clutter comes from the
   agreed pages' `JUNK` lines, so it runs after step 2.
4. **Dump the documents' lines** (`PageCorpusDocumentsDump`, a desktop test of `:data:source:local:implementation`):
   the import corpus's PDFs and Word files, `printed/`, and `synthetic/`, read by the app's own document reader and
   `ChordSheetPageLines` into `documents/<id>.json`, so training sees what the app hands the model. It runs with every
   `desktopTest` while `campfire.pageCorpus` is set, or alone:
   `./gradlew :data:source:local:implementation:desktopTest --tests '*PageCorpusDocumentsDump'`.
5. **Label the documents** (`align_documents.py <corpus>`): import documents by their `.cho`, printed pages by their
   page's agreed labels, and made-up ones by the lines they were made from, into `labels/<document id>.json`, each
   naming its song's group; one aligned too poorly is left out, never fixed by hand.
6. **Gate** (`gate.py <corpus> --synthetic <import corpus>`): five-fold cross-validation grouped by song, for pages
   only, documents only, and both, with each fold's training widened by `synthetic.py`'s made-up pages (built in
   memory; that script has no command). A staff string after a song's first line labelled clutter trains as `IGNORE`,
   since the crop keeps it anyway (`tab_lines`, `CropRule.kt`'s staff rule, which the gate scores with). Writes `.runs/gate.txt`, `gate.tsv`, `lost.txt` (each song line a crop lost),
   and `predictions.json`, which `--rescore` scores again without training.
7. **Export** (`export.py <corpus> --synthetic <import corpus> --weights
   presentation/src/commonMain/composeResources/files/page-model.bin`): the gate's recipe trained once on every
   labelled page and document, its weights quantized and written where the app reads them (`PageCropperImpl`, read once
   on the first page cropped).
8. **Check the shipped weights** (`./gradlew :domain:implementation:desktopTest --tests '*PageCorpusTest'`, also part
   of `desktopTest` while the property is set): the import's own crop (`PageTextCrop`), over the labelled pages, against
   keeping every line. `ImportCorpusTest` crops
   documents with the same weights.

## How a model is judged

- **The gate decides** (`libs/pagesong/CLAUDE.md`): a retrain ships only when its held-out scores on pages and on
  documents hold up against the last model's: how many song pages the crop beats keeping everything on, mean line F1,
  pages and lines of the song lost, pages with no song told as such, and title and artist hits. Its `unsure` crop is
  the rule the app runs (`CropRule.kt`). Each run overwrites `.runs/`, so keep the last report aside to compare.
- `PageCorpusTest` (`:domain:implementation`) shows what the shipped model does, held out only for pages added after
  it was trained, and fails only on loose sanity bounds.
- The golden tests in `:libs:pagesong` prove only that Kotlin computes what PyTorch does.

## Changing the model

`lines.py` (`visible`, `features`, and their patterns) and `model.py` (`hashed`, `PageModel`) are the definition the app ports. A
change there is ported to `:libs:pagesong` in the same change, the fixtures rewritten
(`export.py --fixtures <folder>`, its `tiny.bin` and `golden.json` copied into `libs/pagesong/src/desktopTest/resources`),
and the model gated and exported again, since the shipped weights no longer match. A change to the weights' layout
also bumps `VERSION` in `export.py` and what `PageModelWeights` accepts.
