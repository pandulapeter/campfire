# Stop the release on an instruction comment the description parser cannot read, instead of taking it as absent

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** CI (all stores)
**Files:** `.github/scripts/release_description.py`, `.github/scripts/test_release_description.py`,
`.github/workflows/publish-all.yml` (header comment only), `.claude/skills/prepare-release/SKILL.md` (the "Nothing
misspelt is taken as absent" bullet only)

## Problem

`release_description.py`'s docstring (lines 12-17) and the root `CLAUDE.md` ("Publishing a GitHub release is the
release": "nothing it cannot read is taken as absent, since every default is the stronger action") promise that a
comment the parser cannot read stops the release. That only holds for a wrong store name or a wrong value. A slip in
the *key*, or the closing `-->` on its own line, falls through all three patterns at 8ee010b36 and the default is
applied silently:

```python
# release_description.py:58, 67, 77
match = re.search(r"<!--[ \t]*whats-new[ \t]+en-US[ \t]*\n(.*?)-->", body, re.S | re.I)
for words, value in re.findall(r"<!--[ \t]*([^\n]*?)[ \t]+update-priority:[ \t]*([^\n]*?)[ \t]*-->", body):
for store, value in re.findall(r"<!--[ \t]*([^\n]*?)[ \t]+submit:[ \t]*([^\n]*?)[ \t]*-->", body):
```

Run against the script at HEAD, each of these bodies returns every `*_submit` as `"true"` and `update_priority` as
`"0"`, with no error and no warning:

- `<!-- play-store submit: false\n-->` — the closing on its own line, the shape the `whats-new` block right above has
- `<!-- app-store sumbit: false -->`, `<!-- play-store submit : false -->`, `<!-- microsoft-store: submit false -->`
- `<!-- play-store update_priority: 5 -->` — the underscore is how the workflow input and the output are spelled
- `Vis\n<!-- whats-new en-US - one line -->` and `## Heading\nVisible\n<!-- whats-new en\n- note\n-->` — the block is
  ignored and the visible description (`## Heading` included) becomes every store's "What's new"

So a release meant to wait as a draft in App Store Connect or Partner Center for new screenshots is submitted for
review, or a release meant to block the old Android version (priority 4-5) goes out at 0 — exactly what the docstring
says the parser exists to prevent.

## Fix

In `release_description.py`:

1. Name the three patterns once, at module level, and let the two single-line ones accept a closing on the next line
   (`\s*-->` instead of `[ \t]*-->`):

   ```python
   WHATS_NEW = re.compile(r"<!--[ \t]*whats-new[ \t]+(\S+)[ \t]*\n.*-->", re.S | re.I)
   PRIORITY = re.compile(r"<!--[ \t]*([^\n]*?)[ \t]+update-priority:[ \t]*([^\n]*?)\s*-->")
   SUBMIT = re.compile(r"<!--[ \t]*([^\n]*?)[ \t]+submit:[ \t]*([^\n]*?)\s*-->")
   ```

   and use `PRIORITY.findall(body)` / `SUBMIT.findall(body)` in the two existing loops (the `whats-new en-US` search at
   line 58 stays as it is).

2. Add a pass, run in `read()` before the priority loop and appending to the same `errors` list, that walks every
   comment of the body and reports one that looks like an instruction but is not one of the three well-formed shapes:

   ```python
   def unreadable_instructions(body):
       """Every comment that looks like one of the instructions but is not written the way one is read."""
       messages = []
       for comment in re.finditer(r"<!--.*?-->", body, re.S):
           text = comment.group(0)
           whats_new = WHATS_NEW.fullmatch(text)
           if whats_new:
               language = whats_new.group(1).lower()
               # A block in another language is left alone; one that is almost en-US was meant to be read.
               if language != "en-us" and (language == "en" or language.startswith(("en-", "en_"))):
                   messages.append(f"The release has a \"whats-new {whats_new.group(1)}\" block; the stores read \"whats-new en-US\".")
               continue
           if PRIORITY.fullmatch(text) or SUBMIT.fullmatch(text):
               continue
           inner = text[4:-3].strip().lower().replace("_", "-")
           first = inner.split()[0].rstrip(":") if inner.split() else ""
           if first in STORES or first in {"whats-new", "whatsnew"} or "submit" in inner or "priority" in inner:
               messages.append(f"The release has a comment I cannot read: \"{text}\". The format is in publish-all.yml's header.")
       return messages
   ```

   (`errors += unreadable_instructions(body)`). A well-formed comment is matched by the `fullmatch` and skipped, so
   the existing errors (unknown store, bad value) are still reported once, by the loops. A comment of the author's
   own that mentions "submit" or "priority" also stops the release; that errs on the side the docstring asks for, and
   the message says which comment it was.

3. Extend the docstring's sentence ("a store name it does not know, a submit value other than true or false, a
   priority outside 0-5 stop the release") with "and so does a comment that looks like one of them but is not written
   the way one is read".

The `<!-- whats-new hu-HU … -->` case stays as it is (`test_whats_new_in_another_language_is_left_alone`). In
`publish-all.yml`'s header (lines 34-36), add the same clause after "a priority outside 0-5 stop the release". In the
prepare-release skill's "Nothing misspelt is taken as absent" bullet, add "or a comment that names a store, `submit`,
`priority` or `whats-new` in any other shape". The root `CLAUDE.md` sentence already promises this behaviour and needs
no change.

This sketch was run against the existing 19 tests and the bodies above in a scratch copy: all tests pass, every body
above raises, `<!-- play-store submit: false\n-->` reads as `false`, and the prepare-release skill's own template block
reads unchanged.

## Tests

In `test_release_description.py`:
- `test_a_slip_in_an_instruction_stops_the_release`: each of `<!-- app-store sumbit: false -->`,
  `<!-- play-store submit : false -->`, `<!-- microsoft-store: submit false -->`,
  `<!-- play-store update_priority: 5 -->`, `Vis\n<!-- whats-new en-US - one line -->` and
  `Vis\n<!-- whats-new en\n- n\n-->` raises `ReleaseDescriptionError`.
- `test_a_closing_on_its_own_line_reads`: `<!-- play-store submit: false\n-->` gives `play_store_submit == "false"`,
  `<!-- play-store update-priority: 4\n-->` gives `"4"`.
- `test_other_comments_are_left_alone`: `<!-- TODO polish -->\nVisible.` reads with notes `Visible.` and no error.
- `test_the_prepare_release_template_reads`: the five-comment block from the prepare-release skill reads without error.

Run with `python3 -m unittest discover -s .github/scripts -p 'test_*.py'`.

## Manual check

None beyond the tests: the parser is the whole of the change. Optionally, edit a draft release's description to one
of the bodies above and run the script locally with `RELEASE_BODY` set.
