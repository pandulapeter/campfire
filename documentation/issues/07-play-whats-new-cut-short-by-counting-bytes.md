# 07 · Play's "what's new" loses its last bullets well under 500 characters, most of all the Hungarian one

**Severity:** store listing text (Android / Play. Likely for every Hungarian block the release-notes skill writes near
the limit, and for English text with typographic dashes or quotes; silent) · **Area:** `.github/workflows/android-publish.yml`

## Symptom
The release-notes skill writes each `<!-- play-store … -->` block to fit Play's 500 characters, counted with Python's
`len()`. Play then shows the Hungarian text without its last one or two bullets, and nothing in the run's log says so.
An English text using `—`, `’` or `“` loses bullets the same way. When the English block is missing and the visible
description stands in for it, a first paragraph longer than the limit leaves the listing text empty.

## Cause
`android-publish.yml:107` cuts each locale with awk:

```bash
trim() { awk '{ total += length($0) + 1; if (total > 500) exit; print }' "$1" > "$2"; }
```

On `ubuntu-latest` (24.04) `awk` is mawk: the runner image's toolset (`actions/runner-images`,
`images/ubuntu/toolsets/toolset-2404.json`) installs no gawk, and mawk's `length()` counts bytes. Every `á é í ó ö ő
ú ü ű` counts twice and every `—` three times, so a Hungarian block of about 470 characters (roughly 550 bytes) is
cut. Reproduced locally with macOS awk, which also counts bytes here: a six-line, 473-character Hungarian text came out
as five lines. The limit is checked in two places in two units, and the workflow's is stricter than Play's. A first
line over 500 makes awk exit before printing anything, so the file is empty.

## Fix
`android-publish.yml`, replace the comment and `trim` definition at `:105-107` with a Python version counting
characters (`python3` is on the runner; `release.yml`'s `prepare` job already relies on it):

```bash
          # The Play Store counts every locale against the same 500 character limit, and rejects the whole release
          # when one of them is over it, so each one is cut at a line boundary rather than mid-sentence. Characters,
          # not bytes: awk on the runner is mawk, whose length() counts bytes, and cut a Hungarian text with a few
          # dozen accented letters bullets short of what the release-notes skill had fitted to the limit.
          trim() {
            python3 - "$1" "$2" <<'PYTHON'
          import sys

          LIMIT = 500
          lines = open(sys.argv[1], encoding="utf-8").read().splitlines()
          kept, total = [], 0
          for line in lines:
              total += len(line) + 1
              if total > LIMIT:
                  break
              kept.append(line)
          if not kept and lines:
              # A first line over the limit on its own, which a paragraph of the visible description can be, is cut at
              # the last word that fits rather than leaving the listing with nothing to say.
              first = lines[0][:LIMIT - 1]
              kept = [first.rsplit(" ", 1)[0] if " " in first else first]
          if len(kept) < len(lines):
              print(f"::warning::{sys.argv[2]} was cut to {LIMIT} characters.")
          with open(sys.argv[2], "w", encoding="utf-8") as output:
              output.write("\n".join(kept) + "\n")
          PYTHON
          }
```

Indentation: the whole `run: |` block is indented 10 spaces, and YAML strips exactly that, so the Python lines (and
the closing `PYTHON` delimiter) sit at 10 spaces in the file and at column 0 in the script, as in `release.yml:66-87`.
The quoted `'PYTHON'` delimiter keeps the shell from expanding anything inside. The two call sites (`:108`, `:117`)
stay as they are. `python3 - a b` gives `sys.argv == ["-", a, b]`.

The snippet was run locally against a 473-character (551-byte) Hungarian text (all six lines kept) and a 1000-character
single line (cut at a word to 495 characters, with the warning).

## Tests
None (CI workflow; not a tested module).

## Verify
1. `actionlint .github/workflows/android-publish.yml` if available, or at least `python3 -c 'import yaml;
   yaml.safe_load(open(".github/workflows/android-publish.yml"))'`.
2. Extract the step's script body and run it locally in a scratch folder with a Hungarian `release_notes_hu.txt`
   of ~480 characters: `whatsnew/whatsnew-hu-HU` keeps every line.
3. On the next release (or a hand dispatch with `release_notes_hu` filled in), the step's log prints the whole
   Hungarian text and no warning.

## Docs
None become untrue: `.claude/skills/release-notes/SKILL.md:122` already counts with Python's `len()`, which is now what
the workflow does. The root `CLAUDE.md` does not describe the cut.

## Touches
- `.github/workflows/android-publish.yml`

## Depends on
None.
