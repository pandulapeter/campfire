# This file is part of Campfire.
# Copyright (c) Pandula Péter 2017-2026.
# https://github.com/pandulapeter/campfire
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://mozilla.org/MPL/2.0/.

"""
Downloads the recorded instruments the soundtrack plays into music/samples/, once: an acoustic drum kit and a bass
guitar, both by Karoryfer Samples and both CC0 (public domain, no attribution needed), from the SFZ Instruments
repositories on GitHub at fixed commits, so a regeneration always plays the same recordings.

    python3 fetch_samples.py            downloads what is missing (about 950 MB), converted to 48 kHz WAV by ffmpeg

Only the folders the arrangement uses are fetched, not the whole libraries (several gigabytes between them).
"""
import json
import os
import subprocess
import sys
import urllib.request
from concurrent.futures import ThreadPoolExecutor

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "samples")

DRUMS = ("karoryfer.big-rusty-drums", "f07ce00df34a46b6b08375be56fe116cf15782bc")
BASS = ("karoryfer.black-and-blue-basses", "6e7d674cdb41be7a54dbccb15472401ad01099b9")

# Close and overhead microphones of every drum the arrangement plays.
DRUM_DIRS = [
    f"Samples/{d}"
    for d in (
        "kick_24/kick/kick", "kick_24/kick/oh",
        "snare_14/center/top", "snare_14/center/btm", "snare_14/center/oh",
        "snare_14/rimshot/top", "snare_14/rimshot/btm", "snare_14/rimshot/oh",
        "hihat_14/tc/cl", "hihat_14/tc/oh", "hihat_14/qo/cl", "hihat_14/qo/oh", "hihat_14/open/cl", "hihat_14/open/oh",
        "crash_17/cr/cl", "crash_17/cr/oh", "crash_sizzle_17/cr/cl", "crash_sizzle_17/cr/oh",
        "ride_22/rd/cl", "ride_22/rd/oh", "ride_22/bl/cl", "ride_22/bl/oh",
        "tom_14/center/cl", "tom_14/center/oh", "tom_18/center/cl", "tom_18/center/oh", "tom_22/center/cl", "tom_22/center/oh",
    )
]

# The library names its files an octave above the pitch they sound at: `darkblack_a2_*` is A1, 55 Hz. E1 to E3 at
# two dynamics covers every note the bass line plays.
FLATS = ["c", "db", "d", "eb", "e", "f", "gb", "g", "ab", "a", "bb", "b"]
BASS_NOTES = {f"{FLATS[m % 12]}{m // 12}" for m in range(28, 52 + 1)}
BASS_DYNAMICS = ("f", "mf")


def tree(repo, sha):
    with urllib.request.urlopen(f"https://api.github.com/repos/sfzinstruments/{repo}/git/trees/{sha}?recursive=1") as r:
        return [x["path"] for x in json.load(r)["tree"] if x["type"] == "blob"]


def wanted(repo, path):
    if repo == DRUMS[0]:
        return any(path.startswith(d + "/") for d in DRUM_DIRS)
    if not path.startswith("Samples/darkblack/reg/"):
        return False
    _, note, dynamic, _ = os.path.basename(path).split("_")
    return note in BASS_NOTES and dynamic in BASS_DYNAMICS


def fetch(job):
    repo, sha, path = job
    kind = "drums" if repo == DRUMS[0] else "bass"
    target = os.path.join(OUT, kind, path.removeprefix("Samples/").rsplit(".", 1)[0] + ".wav")
    if os.path.exists(target):
        return False
    os.makedirs(os.path.dirname(target), exist_ok=True)
    url = f"https://raw.githubusercontent.com/sfzinstruments/{repo}/{sha}/{urllib.request.quote(path)}"
    with urllib.request.urlopen(url) as r:
        data = r.read()
    # A bass note never needs its last seconds of decay; the drums keep theirs.
    trim = ["-t", "3.5"] if kind == "bass" else []
    subprocess.run(
        ["ffmpeg", "-loglevel", "error", "-y", "-i", "pipe:0", *trim, "-ar", "48000", "-c:a", "pcm_s24le", target + ".tmp.wav"],
        input=data,
        check=True,
    )
    os.replace(target + ".tmp.wav", target)
    return True


def main():
    jobs = [(repo, sha, p) for repo, sha in (DRUMS, BASS) for p in tree(repo, sha) if wanted(repo, p)]
    with ThreadPoolExecutor(8) as pool:
        fetched = sum(pool.map(fetch, jobs))
    print(f"samples: {len(jobs)} files, {fetched} downloaded", file=sys.stderr)


if __name__ == "__main__":
    main()
