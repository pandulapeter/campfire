# This file is part of Campfire.
# Copyright (c) Pandula Péter 2017-2026.
# https://github.com/pandulapeter/campfire
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://mozilla.org/MPL/2.0/.

"""
Rebuilds the abc2svg scripts the app bundles (`libs/abc/implementation/src/jvmSharedMain/resources/abc2svg`) and the
music glyphs drawn from its font (`AbcMusicGlyphs.kt`, through `tools/fonts/abc_music_glyphs.py`) from a release tag of
the author's repository, the only current source: npm and the GitHub mirror are stale, and the repository's tarball
endpoint fails, so every file is fetched through its "raw" link at the tag.

    python3 -I tools/abc2svg/update.py v1.23.7

Each bundled file is built as the tag's `build.ninja` builds it (the license, each source's first line, the sources
minified by the tag's own `jsmin.js`, and for the core the release's `version.txt`), with `jsmin.js` run under node and
given the three QuickJS globals it uses. The set of bundled files is the one already in the folder: a module is added or
dropped by hand, never by the script. Run at the bundled tag, it rewrites every file byte for byte, which is how a
change to the script is checked. The version named in the documentation and the license notice is changed by hand.
Needs node.
"""

import os
import re
import subprocess
import sys
import tempfile
import urllib.error
import urllib.request

REPOSITORY = "https://chiselapp.com/user/moinejf/repository/abc2svg"
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")
BUNDLED = os.path.join(ROOT, "libs", "abc", "implementation", "src", "jvmSharedMain", "resources", "abc2svg")
GLYPHS_SCRIPT = os.path.join(ROOT, "tools", "fonts", "abc_music_glyphs.py")

# QuickJS's print adds a newline per call; std and scriptArgs are the rest of what jsmin.js touches.
JSMIN_UNDER_NODE = """
const fs = require('fs'), vm = require('vm')
const out = []
vm.runInNewContext(fs.readFileSync(process.argv[1], 'utf8'), {
    std: {
        in: { readAsString: () => fs.readFileSync(0, 'utf8') },
        err: { printf: (s) => process.stderr.write(String(s)) },
        exit: (code) => process.exit(code),
    },
    print: (...words) => out.push(words.join(' ') + '\\n'),
    scriptArgs: ['jsmin.js'],
})
process.stdout.write(out.join(''))
"""


class _NoRedirect(urllib.request.HTTPRedirectHandler):
    # The repository answers a missing file with a redirect to an error page, never a 404.
    def redirect_request(self, *arguments):
        return None


_opener = urllib.request.build_opener(_NoRedirect)


def fetch(path, tag):
    url = f"{REPOSITORY}/raw/{path}?ci={tag}"
    try:
        with _opener.open(urllib.request.Request(url, headers={"User-Agent": "campfire-abc2svg-update"})) as response:
            return response.read().decode("utf-8")
    except urllib.error.HTTPError as error:
        sys.exit(f"{path} is not in abc2svg {tag} ({error.code}): {url}")


def build_rules(ninja):
    """Each output of build.ninja's `jsc` and `jscu` rules, with its rule and its inputs."""
    variables = dict(re.findall(r"^(\w+)=(\S+)$", ninja, re.MULTILINE))
    ninja = ninja.replace("$\n", " ")
    rules = {}
    for output, rule, inputs in re.findall(r"^build (\S+): (jsc|jscu) (.+)$", ninja, re.MULTILINE):
        output = re.sub(r"\$(\w+)", lambda match: variables[match.group(1)], output)
        rules[output] = (rule, inputs.split())
    return rules


def minify(text, jsmin_path):
    return subprocess.run(
        ["node", "-e", JSMIN_UNDER_NODE, jsmin_path], input=text, capture_output=True, text=True, check=True,
    ).stdout


def main():
    if len(sys.argv) != 2 or not re.fullmatch(r"v\d+\.\d+\.\d+", sys.argv[1]):
        sys.exit("usage: python3 -I tools/abc2svg/update.py v<major>.<minor>.<patch>")
    tag = sys.argv[1]
    version = fetch("version.txt", tag).strip()
    if f'"{tag}"' not in version:
        sys.exit(f"version.txt at {tag} names another release: {version}")
    rules = build_rules(fetch("build.ninja", tag))
    license_text = fetch("core/license", tag)
    sources = {}
    with tempfile.TemporaryDirectory() as scratch:
        jsmin_path = os.path.join(scratch, "jsmin.js")
        with open(jsmin_path, "w", encoding="utf-8") as jsmin:
            jsmin.write(fetch("jsmin.js", tag))
        for name in sorted(file for file in os.listdir(BUNDLED) if file.endswith(".js")):
            if name not in rules:
                sys.exit(f"abc2svg {tag} no longer builds {name}; drop it from the bundle by hand")
            rule, inputs = rules[name]
            texts = [sources.setdefault(path, fetch(path, tag)) for path in inputs]
            built = license_text + "".join(text.split("\n", 1)[0] + "\n" for text in texts)
            built += minify("".join(texts), jsmin_path)
            if rule == "jsc":
                built += version + "\n"
            with open(os.path.join(BUNDLED, name), "w", encoding="utf-8", newline="") as output:
                output.write(built)
            print(f"{name} built from {', '.join(inputs)}")
        font_path = os.path.join(scratch, "abc2svg.sfd")
        with open(font_path, "w", encoding="utf-8") as font:
            font.write(fetch("font/abc2svg.sfd", tag))
        environment = dict(os.environ, ABC2SVG_VERSION=f"abc2svg {tag}")
        subprocess.run([sys.executable, "-I", GLYPHS_SCRIPT, font_path], env=environment, check=True)


if __name__ == "__main__":
    main()
