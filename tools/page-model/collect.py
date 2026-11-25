# This file is part of Campfire.
# Copyright (c) Pandula Péter 2017-2026.
# https://github.com/pandulapeter/campfire
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://mozilla.org/MPL/2.0/.

"""
Renders each page `urls.tsv` names in headless Chrome and saves what a share or a copy of it would hand the app: its
text as the app's own web views read it (`PAGE_SNAPSHOT_SCRIPT`, taken from `PageSnapshotScript.kt` so the two never
differ: `innerText` with every element drawn and a raised inline chord set as a chord row), split into lines, the
structure marks that script gives each line (`marks`, one per line), its title, and its final address, as
`pages/<id>.json`, and the page
printed the way a browser prints it, as `printed/<id>.pdf` (Letter for English, A4 for the rest, the print stylesheet
applied). A page already saved is skipped, so a run picks up where the last one stopped.

    .venv/bin/python -I tools/page-model/collect.py <corpus folder>

`urls.tsv` in the corpus folder holds one page a row: `kind` (`song`, or `none` for a page with no song), language, and
address, tab-separated. What each page came to is written to `collect.tsv`: `ok`, `blocked` (a bot challenge, or next
to no text), or the error. The browser says it is headless Chrome and a page that turns it away is left out, never
worked around.
"""

import asyncio
import hashlib
import json
import os
import sys

from playwright.async_api import async_playwright

PARALLEL = 4
TIMEOUT_MS = 30_000
SETTLE_MS = 2_500
SCRIPT_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "data", "source", "remote", "implementation", "src", "commonMain", "kotlin", "com", "pandulapeter", "campfire", "data", "source", "remote", "implementation", "page", "PageSnapshotScript.kt")
CHALLENGES = ("just a moment", "attention required", "access denied", "are you a robot", "verify you are human", "captcha")


def snapshot_script():
    with open(SCRIPT_FILE, encoding="utf-8") as file:
        source = file.read()
    return source[source.index('"""') + 3 : source.rindex('"""')]


SCRIPT = snapshot_script()


def page_id(url):
    return hashlib.sha1(url.encode("utf-8")).hexdigest()[:12]


def read_urls(corpus):
    rows = []
    with open(os.path.join(corpus, "urls.tsv"), encoding="utf-8") as file:
        for line in file:
            parts = line.rstrip("\n").split("\t")
            if len(parts) == 3 and parts[2].startswith("http"):
                rows.append({"kind": parts[0], "language": parts[1], "url": parts[2]})
    return rows


def is_blocked(title, lines):
    text = (title + "\n" + "\n".join(lines[:20])).lower()
    return any(challenge in text for challenge in CHALLENGES) or sum(len(line) for line in lines) < 200


async def collect(browser, corpus, row, statuses):
    identity = page_id(row["url"])
    target = os.path.join(corpus, "pages", identity + ".json")
    if os.path.exists(target):
        statuses[identity] = "ok"
        return
    context = await browser.new_context(locale=row["language"] if row["language"] != "xx" else "en", viewport={"width": 1280, "height": 900})
    page = await context.new_page()
    try:
        await page.goto(row["url"], timeout=TIMEOUT_MS, wait_until="domcontentloaded")
        try:
            await page.wait_for_load_state("networkidle", timeout=10_000)
        except Exception:
            pass
        await page.wait_for_timeout(SETTLE_MS)
        title = await page.title()
        snapshot = json.loads(await page.evaluate(SCRIPT))
        lines = snapshot["text"].replace("\r\n", "\n").split("\n")
        marks = snapshot["marks"]
        if len(marks) != len(lines):
            raise ValueError(f"{len(marks)} marks for {len(lines)} lines")
        if is_blocked(title, lines):
            statuses[identity] = "blocked"
            return
        os.makedirs(os.path.dirname(target), exist_ok=True)
        printed = os.path.join(corpus, "printed", identity + ".pdf")
        os.makedirs(os.path.dirname(printed), exist_ok=True)
        await page.emulate_media(media="print")
        await page.pdf(path=printed, format="Letter" if row["language"] == "en" else "A4", print_background=False)
        document = {"id": identity, "kind": row["kind"], "language": row["language"], "url": row["url"], "finalUrl": page.url, "title": title, "lines": lines, "marks": marks}
        with open(target, "w", encoding="utf-8") as file:
            json.dump(document, file, ensure_ascii=False, indent=0)
        statuses[identity] = "ok"
    except Exception as exception:
        statuses[identity] = "error: " + str(exception).splitlines()[0][:200]
    finally:
        await context.close()


async def main(corpus):
    rows = read_urls(corpus)
    statuses = {}
    async with async_playwright() as playwright:
        browser = await playwright.chromium.launch(headless=True)
        queue = asyncio.Queue()
        for row in rows:
            queue.put_nowait(row)

        async def worker():
            while not queue.empty():
                row = queue.get_nowait()
                await collect(browser, corpus, row, statuses)
                print(statuses.get(page_id(row["url"]), "?"), row["url"], flush=True)

        await asyncio.gather(*(worker() for _ in range(PARALLEL)))
        await browser.close()
    with open(os.path.join(corpus, "collect.tsv"), "w", encoding="utf-8") as file:
        for row in rows:
            file.write(f"{page_id(row['url'])}\t{statuses.get(page_id(row['url']), '?')}\t{row['url']}\n")
    counts = {}
    for status in statuses.values():
        key = status.split(":")[0]
        counts[key] = counts.get(key, 0) + 1
    print(counts)


if __name__ == "__main__":
    asyncio.run(main(sys.argv[1]))
