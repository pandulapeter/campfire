# This file is part of Campfire.
# Copyright (c) Pandula Péter 2017-2026.
# https://github.com/pandulapeter/campfire
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://mozilla.org/MPL/2.0/.

"""
The deterministic half of the add-language skill: everything about a new app language that a script can decide, so
that the model doing the translation only has to translate.

    python3 .claude/skills/add-language/scripts/language.py preflight <code> --sample "<alphabet and punctuation>"
    python3 .claude/skills/add-language/scripts/language.py split <dir>
    python3 .claude/skills/add-language/scripts/language.py join <code> <dir>
    python3 .claude/skills/add-language/scripts/language.py strings <code>
    python3 .claude/skills/add-language/scripts/language.py wiring <code> <EnglishName>

`preflight` exits 2 when the language needs groundwork the skill cannot do (a script no bundled face covers, a plural
rule the app does not know, a regional variant). A language written from the right is no blocker: it is a checklist,
which `preflight` prints and `wiring` checks. `strings` and `wiring` exit 1 on an error. Standard library only: the
bundled fonts' character maps are read directly, and the tables this script follows (the script faces, the plural
rules, the right-to-left languages, the retired codes) are read from the Kotlin that holds them.
"""

import re
import struct
import subprocess
import sys
import unicodedata
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
RESOURCES = ROOT / "presentation/src/commonMain/composeResources"
ENGLISH = RESOURCES / "values/strings.xml"
FONTS = sorted(p for p in (RESOURCES / "font").glob("*.ttf") if p.name.startswith(("inter_", "jetbrains_mono_")))

PRESENTATION = ROOT / "presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation"
SCRIPT_FACES = PRESENTATION / "ui/theme/ScriptFaces.kt"
PLURAL_RULES = PRESENTATION / "localization/PluralRules.kt"
LANGUAGES = ROOT / "chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/language"
APP_LANGUAGE = "data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/preferences/AppLanguage.kt"
STORE_LISTINGS = ROOT / ".github/scripts/store_listings.py"
CATEGORIES = ("zero", "one", "two", "few", "many", "other")


def retired_codes():
    """{retired code: current code}, from :chordpro's LanguageCodes, which every code the app compares goes through."""
    return dict(re.findall(r'"([a-z]+)" to "([a-z]+)"', (LANGUAGES / "LanguageCodes.kt").read_text()))


def right_to_left_codes():
    """The languages written from the right, from :chordpro's RightToLeftLanguages, which decides a song's direction."""
    return set(re.findall(r'"([a-z]+)"', re.search(r"setOf\((.*?)\)", (LANGUAGES / "RightToLeftLanguages.kt").read_text()).group(1)))


def is_right_to_left(code):
    return retired_codes().get(code, code) in right_to_left_codes()


def script_faces():
    """Every row of the script-face table as (script, code point ranges, languages, font file), read as
    tools/fonts/script_faces.py reads it."""
    for row in re.findall(r"ScriptFace\((.*?)\n    \)", SCRIPT_FACES.read_text(), re.DOTALL):
        script = re.search(r'script = "([^"]+)"', row).group(1)
        ranges = [(int(a, 16), int(b, 16)) for a, b in re.findall(r"0x([0-9A-Fa-f]+)\.\.0x([0-9A-Fa-f]+)", row)]
        languages = set(re.findall(r'"([a-z]+)"', re.search(r"languages = setOf\((.*?)\)", row, re.DOTALL).group(1)))
        font = RESOURCES / "font" / (re.search(r"Res\.font\.(\w+)", row).group(1) + ".ttf")
        yield script, ranges, languages, font


def face_covering(character):
    """The row of the script-face table that draws [character], or None."""
    point = ord(character)
    for face in script_faces():
        if any(start <= point <= end for start, end in face[1]) and point in font_map(face[3]):
            return face
    return None


def plural_categories(code):
    """
    The categories the language's row of PluralRules.kt can give a count, `other` always among them, or None for a
    language with no row. Read from the row's own text and that of the private functions it calls.
    """
    text = PLURAL_RULES.read_text()
    table = text[text.index("PLURAL_RULES"):]
    table = table[:table.index("\n)\n")]
    rows = dict(re.findall(r'\n    "([a-z]+)" to (.*?)(?=\n    "[a-z]+" to |\Z)', table, re.DOTALL))
    retired = retired_codes()
    row = rows.get(retired.get(code, code))
    if row is None:
        return None
    for function in re.findall(r"(\w+)\(n\)", row):
        found = re.search(rf"fun {function}\(.*", text)
        row += found.group(0) if found else ""
    return [category for category in CATEGORIES if category == "other" or re.search(rf"\b{category.upper()}\b", row)]


CASING_NOTES = {
    "tr": "Kotlin's lowercase()/titlecase() are locale-insensitive: a scale name or section label starting with i/ı/İ "
          "is cased the English way (standaloneScaleName, SectionNumbering). Avoid such leading letters where a "
          "synonym exists and check the Scales tab and section headings in the app.",
    "az": "Same dotted/dotless i casing caveat as Turkish (standaloneScaleName, SectionNumbering).",
}

SPECIFIER = re.compile(r"%\d+\$[sd]|\{[a-z_]+\}|\\n|</?[biu]>")


def font_map(path):
    """The code points a TrueType file's `cmap` maps (formats 4 and 12, which is all the bundled fonts use)."""
    data = path.read_bytes()
    count = struct.unpack(">H", data[4:6])[0]
    tables = {data[12 + 16 * i:16 + 16 * i].decode("latin-1"): struct.unpack(">II", data[20 + 16 * i:28 + 16 * i]) for i in range(count)}
    offset = tables["cmap"][0]
    points = set()
    for i in range(struct.unpack(">H", data[offset + 2:offset + 4])[0]):
        _, _, sub = struct.unpack(">HHI", data[offset + 4 + 8 * i:offset + 12 + 8 * i])
        start = offset + sub
        kind = struct.unpack(">H", data[start:start + 2])[0]
        if kind == 4:
            segments = struct.unpack(">H", data[start + 6:start + 8])[0] // 2
            ends = struct.unpack(f">{segments}H", data[start + 14:start + 14 + 2 * segments])
            starts = struct.unpack(f">{segments}H", data[start + 16 + 2 * segments:start + 16 + 4 * segments])
            for first, last in zip(starts, ends):
                points.update(range(first, last + 1))
        elif kind == 12:
            for group in range(struct.unpack(">I", data[start + 12:start + 16])[0]):
                first, last, _ = struct.unpack(">III", data[start + 16 + 12 * group:start + 28 + 12 * group])
                points.update(range(first, last + 1))
    points.discard(0xFFFF)
    return points


def missing_glyphs(text):
    """
    Characters of [text] Inter or JetBrains Mono cannot draw and no row of the script-face table covers either, by font.
    The app draws only its bundled faces where the platform has none of its own (the web, Linux), so these would be
    boxes there.
    """
    characters = {c for c in text if not c.isspace() and unicodedata.category(c)[0] != "C"}
    characters = {c for c in characters if face_covering(c) is None}
    return {path.name: sorted(c for c in characters if ord(c) not in font_map(path)) for path in FONTS if any(ord(c) not in font_map(path) for c in characters)}


def faces_for(text):
    """The rows of the script-face table that draw some character of [text]."""
    faces = {}
    for character in set(text):
        face = face_covering(character) if ord(character) > 0x7F else None
        if face is not None:
            faces[face[0]] = face
    return list(faces.values())


def right_to_left(text):
    return sorted({c for c in text if unicodedata.bidirectional(c) in ("R", "AL")})


def preflight(code, sample):
    blockers, notes = [], []
    if not re.fullmatch(r"[a-z]{2,3}", code):
        blockers.append(f"\"{code}\" is not a plain ISO 639 language code. Regional or script variants (pt-BR, zh-Hans, "
                        "sr-Latn) need groundwork: AppLocale.findByCode matches Locale.current.language alone, and the "
                        "localization plugin's handling of values-xx-rYY folders is untested here.")
    if code in retired_codes():
        blockers.append(f"\"{code}\" is a retired code; add the language as \"{retired_codes()[code]}\", which "
                        "LanguageCodes folds it to on every platform.")
    if (RESOURCES / f"values-{code}").exists():
        blockers.append(f"values-{code} already exists: the language is already supported.")
    if is_right_to_left(code) or right_to_left(sample):
        notes.append("Right to left: the app lays itself out from the language's AppLanguage.layoutDirection, so the "
                     "checklist is: the entry with Direction.RIGHT_TO_LEFT, its index.html TEXTS block with "
                     "dir: 'rtl', and each store's own code for the language (step 8). `wiring` checks the first two.")
        if not is_right_to_left(code):
            blockers.append("The sample is written from the right but :chordpro's RightToLeftLanguages does not list "
                            f"\"{code}\"; add it there (CLDR's list) first, so a song in the language reads from the right.")
    categories = plural_categories(code)
    if categories is None:
        blockers.append("PluralRules.kt has no row for the language: add its CLDR cardinal rule to PLURAL_RULES (with a "
                        "test of the counts that pick each category) first, or every count would read like English.")
    else:
        notes.append(f"Plurals: the language's rule picks {', '.join(categories)}; give every <plurals> an item for each "
                     "(only `one` and `other` are required: a missing category reads `other`), and a `zero` item where "
                     "0 reads apart (\"No songs\").")
    if code in CASING_NOTES:
        notes.append("Casing: " + CASING_NOTES[code])
    for script, _, languages, font in faces_for(sample):
        notes.append(f"Fonts: the {script} row of ScriptFaces.kt ({font.name}, Regular only; Android, Apple, and Windows "
                     "draw their own face) draws the script." +
                     ("" if code in languages else f" Add \"{code}\" to its languages, so the web loads it before any text."))
    missing = missing_glyphs(sample)
    if missing:
        blockers.append("No bundled face can draw: " +
                        "; ".join(f"{font}: {' '.join(chars)}" for font, chars in missing.items()) +
                        ". Inter and JetBrains Mono hold Latin, Greek, and Cyrillic; another script needs a row in "
                        "ScriptFaces.kt, a run of tools/fonts/script_faces.py, and the face credited first.")
    for note in notes:
        print("NOTE  " + note)
    for blocker in blockers:
        print("ABORT " + blocker)
    if blockers:
        sys.exit(2)
    print("OK    No fundamental blocker found.")


def chunk_ranges(lines):
    """Line ranges (1-based, inclusive) of English groups, cut at group comments into about 250 lines each."""
    first = next(i for i, line in enumerate(lines, 1) if line.startswith("    <!--"))
    last = max(i for i, line in enumerate(lines, 1) if line.strip() == "</resources>") - 1
    starts = [i for i, line in enumerate(lines, 1) if line.startswith("    <!--") and first <= i <= last]
    ranges, start = [], first
    for cut in starts:
        # A chunk starts at the blank line above its group comment, so none ends in a blank line a writer would drop.
        cut -= 1 if not lines[cut - 2].strip() else 0
        if cut - start >= 250:
            ranges.append((start, cut - 1))
            start = cut
    ranges.append((start, last))
    return ranges, first, last


def split(directory):
    lines = ENGLISH.read_text().split("\n")
    ranges, _, _ = chunk_ranges(lines)
    out = Path(directory)
    out.mkdir(parents=True, exist_ok=True)
    for number, (start, end) in enumerate(ranges, 1):
        (out / f"en_{number}.xml").write_text("\n".join(lines[start - 1:end]) + "\n")
        print(f"en_{number}.xml  lines {start}-{end}  ->  write the translation to {out / f'xx_{number}.xml'}")


def join(code, directory):
    lines = ENGLISH.read_text().split("\n")
    ranges, first, last = chunk_ranges(lines)
    parts = []
    for number in range(1, len(ranges) + 1):
        part = (Path(directory) / f"xx_{number}.xml").read_text().rstrip("\n").split("\n")
        expected = ranges[number - 1][1] - ranges[number - 1][0] + 1
        # A plural item of a quantity the English has none of (`zero`, or a category of the language's own) is a line
        # of its own, the only kind a translation adds.
        added = sum(1 for line in part if re.search(r'<item quantity="(zero|two|few|many)"', line))
        if len(part) - added != expected:
            sys.exit(f"xx_{number}.xml has {len(part)} lines, en_{number}.xml {expected}.")
        parts += part
    target = RESOURCES / f"values-{code}/strings.xml"
    target.parent.mkdir(exist_ok=True)
    target.write_text("\n".join(lines[:first - 1] + parts + lines[last:]))
    print(f"Wrote {target.relative_to(ROOT)}")


def entries(path):
    values = {}
    for element in ET.parse(path).getroot():
        if element.tag == "string":
            values[element.get("name")] = element.text or ""
        elif element.tag == "plurals":
            for item in element:
                values[f"{element.get('name')}:{item.get('quantity')}"] = item.text or ""
    return values


def check_strings(code):
    target = RESOURCES / f"values-{code}/strings.xml"
    english, translated = entries(ENGLISH), entries(target)
    categories = plural_categories(code) or ["one", "other"]
    # Plural items the English lacks (a `zero`, or a category of the language's own) are the only lines it may add.
    added = {key: text for key, text in translated.items() if ":" in key and key not in english}
    translated = {key: text for key, text in translated.items() if key not in added}
    errors, warnings = [], []
    if list(english) != list(translated):
        missing = [k for k in english if k not in translated]
        extra = [k for k in translated if k not in english]
        errors.append(f"Keys differ from English (missing {missing[:10]}, extra {extra[:10]}, or the order changed).")
    english_comments = re.findall(r"<!--.*?-->", ENGLISH.read_text(), re.S)
    if english_comments != re.findall(r"<!--.*?-->", target.read_text(), re.S):
        errors.append("The XML comments differ from English; they stay verbatim, in English.")
    for key, text in translated.items():
        source = english.get(key, "")
        if sorted(SPECIFIER.findall(source)) != sorted(SPECIFIER.findall(text)):
            message = f"{key}: placeholders {SPECIFIER.findall(source)} became {SPECIFIER.findall(text)}"
            # A `one` item may spell its count out ("one song"), dropping the number but nothing else.
            dropped = sorted(SPECIFIER.findall(source))
            for kept in SPECIFIER.findall(text):
                if kept in dropped:
                    dropped.remove(kept)
            is_count_spelled = key.endswith(":one") and len(SPECIFIER.findall(text)) + len(dropped) == len(SPECIFIER.findall(source)) and all(d.endswith("$d") for d in dropped)
            (warnings if is_count_spelled else errors).append(message)
        if source.count("\u00a0") > text.count("\u00a0"):
            errors.append(f"{key}: the English keeps a count on its word's line with a no-break space (U+00A0); keep each.")
        if text == source and len(source) > 12:
            warnings.append(f"{key}: identical to English ({source[:50]!r}); fine only for names and symbols.")
        if right_to_left(text) and not is_right_to_left(code):
            errors.append(f"{key}: right-to-left characters in a language written from the left.")
    for key, text in added.items():
        name, quantity = key.split(":")
        other = english.get(f"{name}:other")
        if other is None:
            errors.append(f"{key}: a plural item in a key that is not a plural.")
        elif quantity != "zero" and quantity not in categories:
            errors.append(f"{key}: the language's plural rule never picks `{quantity}` (it picks {', '.join(categories)}).")
        elif not set(SPECIFIER.findall(text)) <= set(SPECIFIER.findall(other)):
            errors.append(f"{key}: placeholders {SPECIFIER.findall(text)} that `other` lacks.")
    for category in categories:
        if category not in ("one", "other"):
            plurals = {key.split(":")[0] for key in english if ":" in key}
            lacking = sorted(name for name in plurals if f"{name}:{category}" not in added)
            if lacking:
                warnings.append(f"{len(lacking)} plurals have no `{category}` item ({', '.join(lacking[:5])}, ...): fine "
                                f"only where `other` reads the counts the rule calls {category}.")
    everything = "".join(translated.values()) + "".join(added.values())
    missing = missing_glyphs(everything)
    if missing:
        errors.append("Characters no bundled face has: " + "; ".join(f"{f}: {' '.join(c)}" for f, c in missing.items()))
    for script, _, languages, _ in faces_for(everything):
        if code not in languages:
            errors.append(f"ScriptFaces.kt: the {script} row draws the strings but does not list \"{code}\" among its "
                          "languages, so the web would draw the interface before loading it.")
    if ENGLISH.read_text().count("\n") != target.read_text().count("\n") - len(added):
        errors.append("Line count differs from English: the file must mirror it line for line, plural items aside.")
    report(errors, warnings)


def check_wiring(code, english_name):
    enum = english_name.upper().replace(" ", "_")
    key = "settings_user_interface_language_" + english_name.lower().replace(" ", "_")
    strings_object = "Strings" + code.capitalize()
    presentation = str((PRESENTATION / "ui/components/preferences").relative_to(ROOT))
    ios = "app/ios/iosApp"
    checks = [
        (f"{presentation}/AppLanguageNames.kt", rf"import com\.pandulapeter\.campfire\.presentation\.resources\.{key}\n"),
        (f"{presentation}/AppLanguageNames.kt", rf"AppLanguage\.{enum} -> Res\.string\.{key}\n"),
        ("tools/screenshots/src/main/kotlin/com/pandulapeter/campfire/screenshots/Shot.kt", rf"import com\.pandulapeter\.campfire\.presentation\.localization\.{strings_object}\n"),
        ("tools/screenshots/src/main/kotlin/com/pandulapeter/campfire/screenshots/Shot.kt", rf"AppLanguage\.{enum} -> {strings_object}\.strings\[key\]"),
        (f"{ios}/iosApp/Info.plist", rf"<key>CFBundleLocalizations</key>\s*<array>(\s*<string>[a-z-]+</string>)*\s*<string>{code}</string>"),
        (f"{ios}/ShareExtension/Info.plist", rf"<key>CFBundleLocalizations</key>\s*<array>(\s*<string>[a-z-]+</string>)*\s*<string>{code}</string>"),
        (f"{ios}/iosApp/{code}.lproj/InfoPlist.strings", r'"NSMicrophoneUsageDescription" = ".+";'),
        (f"{ios}/iosApp.xcodeproj/project.pbxproj", rf'name = {code}; path = {code}\.lproj/Localizable\.strings;'),
        (f"{ios}/iosApp.xcodeproj/project.pbxproj", rf'name = {code}; path = {code}\.lproj/InfoPlist\.strings;'),
        (f"{ios}/iosApp.xcodeproj/project.pbxproj", rf"knownRegions = \([^)]*\n\t\t\t\t{code},"),
        (f"{ios}/ShareExtension/en.lproj/Localizable.strings", rf"{code}\.lproj"),
        ("app/web/src/wasmJsMain/resources/index.html", rf"\n            {code}: \{{"),
        ("app/web/src/wasmJsMain/resources/index.html", rf"/\^\([a-z|]*\b{code}\b[a-z|]*\)\\b/i"),
        ("CLAUDE.md", rf"values\[[^\]]*-{code}\b[^\]]*\]/strings\.xml"),
        ("presentation/CLAUDE.md", english_name),
        ("app/web/CLAUDE.md", rf"navigator\.language` \([^)]*{english_name}"),
        ("app/ios/CLAUDE.md", rf"`Localizable\.strings` \([^)]*`{code}`"),
        ("app/ios/CLAUDE.md", rf"`CFBundleLocalizations` \([^)]*`{code}`"),
    ]
    stores = sorted(p.name for p in (ROOT / "documentation/store-listings").iterdir() if p.is_dir())
    # The screenshots' texts are the one folder that is no store's listing, with no description to name the languages in.
    checks += [(f"documentation/store-listings/{store}/en.md", rf"\b{english_name}\b[^\n]*, switchable inside the app") for store in stores if store != "screenshot-texts"]
    checks += [(f"documentation/store-listings/{store}/{code}.md", r"(?m)^## ") for store in stores]
    checks += [("app/desktop/AppxManifest.xml", rf'<Resource Language="{code}" />')]
    checks += [(str((PRESENTATION / "localization/PluralTables.kt").relative_to(ROOT)), rf"AppLocale\.{code.upper()} -> {strings_object}\.plurals")]
    direction = "RIGHT_TO_LEFT" if is_right_to_left(code) else "LEFT_TO_RIGHT"
    checks += [(APP_LANGUAGE, rf'\b{enum}\("{code}", Direction\.{direction}\),')]
    if direction == "RIGHT_TO_LEFT":
        checks += [("app/web/src/wasmJsMain/resources/index.html", rf"\n            {code}: \{{\n                dir: 'rtl',")]
    checks += [(str(STORE_LISTINGS.relative_to(ROOT)), rf'"{old}": "{code}"') for old, new in retired_codes().items() if new == code]
    checks += [(str(p.relative_to(ROOT)), rf'<string name="{key}">[^<]+</string>') for p in sorted(RESOURCES.glob("values*/strings.xml"))]
    errors = []
    for relative, pattern in checks:
        path = ROOT / relative
        if not path.exists():
            errors.append(f"{relative}: missing file")
        elif not re.search(pattern, path.read_text()):
            errors.append(f"{relative}: nothing matches {pattern}")
    pbxproj = (ROOT / f"{ios}/iosApp.xcodeproj/project.pbxproj").read_text()
    if pbxproj.count(f"/* {code} */") != 4:
        errors.append(f"project.pbxproj: expected 4 `/* {code} */` (two file references, two variant group children), "
                      f"found {pbxproj.count(f'/* {code} */')}")
    shares = lambda path: set(re.findall(r'^"(\w+)" =', path.read_text(), re.M))
    english_share = ROOT / f"{ios}/ShareExtension/en.lproj/Localizable.strings"
    new_share = ROOT / f"{ios}/ShareExtension/{code}.lproj/Localizable.strings"
    if not new_share.exists() or shares(english_share) != shares(new_share):
        errors.append(f"{new_share.relative_to(ROOT)}: must hold exactly en.lproj's keys")
    generated = ROOT / f"presentation/build/generated/compose/resourceGenerator/kotlin/commonCustomResClass/com/pandulapeter/campfire/presentation/localization/{strings_object}.kt"
    if not generated.exists():
        errors.append(f"{strings_object}.kt is not generated yet: run ./gradlew :presentation:generateTranslateFile --rerun-tasks")
    listings = subprocess.run([sys.executable, str(ROOT / ".github/scripts/check_store_listings.py")], capture_output=True, text=True)
    errors += listings.stdout.splitlines()
    report(errors, [])


def report(errors, warnings):
    for warning in warnings:
        print("WARN  " + warning)
    for error in errors:
        print("ERROR " + error)
    if errors:
        sys.exit(1)
    print("OK")


if __name__ == "__main__":
    arguments = sys.argv[1:]
    if arguments[:1] == ["preflight"] and len(arguments) == 4 and arguments[2] == "--sample":
        preflight(arguments[1], arguments[3])
    elif arguments[:1] == ["split"] and len(arguments) == 2:
        split(arguments[1])
    elif arguments[:1] == ["join"] and len(arguments) == 3:
        join(arguments[1], arguments[2])
    elif arguments[:1] == ["strings"] and len(arguments) == 2:
        check_strings(arguments[1])
    elif arguments[:1] == ["wiring"] and len(arguments) == 3:
        check_wiring(arguments[1], arguments[2])
    else:
        sys.exit(__doc__)
