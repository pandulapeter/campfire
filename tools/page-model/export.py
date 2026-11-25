# This file is part of Campfire.
# Copyright (c) Pandula Péter 2017-2026.
# https://github.com/pandulapeter/campfire
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://mozilla.org/MPL/2.0/.

"""
Trains the page model the app ships on every labelled page and document (no fold held out: the gate has already judged
the recipe) and writes it in the format `:libs:pagesong` reads, then the fixtures its tests check the Kotlin forward
pass and line features against. The app ships two: one trained with the pages' structure marks, for a page it read
itself, and one with `--no-marks`, for a paste and a document, which the marked one crops worse.

    .venv/bin/python -I tools/page-model/export.py <corpus folder> --synthetic <import corpus> --weights <file> [--no-marks]
    .venv/bin/python -I tools/page-model/export.py --fixtures <folder>

The weights file, little-endian: the magic `CFPM`, the format version, then the shape (buckets, characters a line,
embedding, channels, line width, features, hidden, labels) as 32-bit integers, then every tensor in the forward pass's
order. A matrix is int8 with one float32 scale a row (the row's largest magnitude over 127), a bias is float32. A
convolution's kernel is a matrix of its output channels by input channel × tap. `--fixtures` writes, from text written
here and a tiny model of random weights, `tiny.bin` and `golden.json` (each line's hashed characters at the app's bucket count, its features, and the
logits PyTorch gives for the tiny model's quantized weights), so the tests never hold a page of the private corpus.
"""

import argparse
import json
import os
import struct
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import torch  # noqa: E402

from lines import FEATURES, LABELS, features  # noqa: E402
from model import MAX_CHARS, PageModel, hashed  # noqa: E402

MAGIC = b"CFPM"
VERSION = 2


def quantized(matrix):
    scales = matrix.abs().max(dim=1).values.clamp(min=1e-8) / 127.0
    values = torch.round(matrix / scales.unsqueeze(1)).clamp(-127, 127).to(torch.int8)
    return values, scales


def tensors(model):
    """The forward pass's tensors in the order the file holds them, each `("matrix" | "bias", tensor)`."""
    conv1, conv2 = model.convolutions[0], model.convolutions[2]
    items = [
        ("matrix", model.embedding.weight.detach()),
        ("matrix", conv1.weight.detach().reshape(conv1.out_channels, -1)),
        ("bias", conv1.bias.detach()),
        ("matrix", conv2.weight.detach().reshape(conv2.out_channels, -1)),
        ("bias", conv2.bias.detach()),
        ("matrix", model.line[0].weight.detach()),
        ("bias", model.line[0].bias.detach()),
    ]
    for layer in range(2):
        for suffix in ("", "_reverse"):
            for name in ("weight_ih", "weight_hh"):
                items.append(("matrix", getattr(model.sequence, f"{name}_l{layer}{suffix}").detach()))
            for name in ("bias_ih", "bias_hh"):
                items.append(("bias", getattr(model.sequence, f"{name}_l{layer}{suffix}").detach()))
    items += [("matrix", model.output.weight.detach()), ("bias", model.output.bias.detach())]
    return items


def shape(model):
    conv1 = model.convolutions[0]
    return [model.embedding.num_embeddings - 1, MAX_CHARS, conv1.in_channels, conv1.out_channels, model.line[0].out_features, FEATURES,
            model.sequence.hidden_size, len(LABELS)]


def write(model, path):
    """Writes the weights and loads the quantized values back into `model`, so what it answers is what the app answers."""
    with open(path, "wb") as file:
        file.write(MAGIC + struct.pack("<I", VERSION))
        file.write(struct.pack(f"<{8}i", *shape(model)))
        for kind, tensor in tensors(model):
            if kind == "matrix":
                values, scales = quantized(tensor)
                file.write(struct.pack(f"<{scales.numel()}f", *scales.tolist()))
                file.write(values.numpy().tobytes())
                # A convolution's matrix is a view of its kernel, so this also writes the kernel back.
                tensor.copy_(values.float() * scales.unsqueeze(1))
            else:
                file.write(struct.pack(f"<{tensor.numel()}f", *tensor.tolist()))


# Lines written for the fixtures: a chord sheet with its page's clutter, every script the app's languages use, chord
# rows, a tab staff, headings, labelled details, structure marks for a page (by line, as the app's page read writes
# them: several combinations, a blank line's `{}`, and an unaligned line), and layout for a document.
FIXTURE_PAGE = [
    "Home  Songs  Artists  Log in", "Accept all cookies", "", "Amazing Grace chords by John Newton", "Key: G   Capo: 2",
    "[Verse 1]", "G           G7        C        G", "Amazing grace, how sweet the sound", "      G               D",
    "That saved a wretch like me", "", "e|-----0-----3-----|", "B|---1---1-------3-|", "Chorus:", "Refrén", "Ref.",
    "Strofa 2", "C x32010   Am x02210", "Árvíztűrő tükörfúrógép", "שלום עולם", "Žluťoučký kůň úpěl ďábelské ódy",
    "Zażółć gęślą jaźń", "⁠G   ⁠D", "Em7b5  D#maj7  Bb/F", "https://example.com/song", "Report a correction",
    "Words & Music: John Newton", "Tempo: 90 bpm", "    indented line", "😀 emoji line 🎸", "x" * 200,
    "||: G / / D :|| (x2) N.C. nc % . 4x |", "Do Re Mi Fa Sol La Si Ré/Sol", "Cis Fes Hmaj7 Asus4 Bbadd9 C° Bø7 D+ F#m7(b5) A/C#",
    "G  Dm  keyboard", "VERSE 2:", "(Pre-Chorus)", "[PRECHORUS 2]", "Ritornello:", "R.", "V.1", "\u0131ntro", "\u0130NTRO",
    "[Chorus                                      ]", "Intro: G D", "CAPOTRASTE: 3", "Kapodaszter = 1", "keyboard: Yamaha",
    "key2: x", "Written by - Someone", "Słowa: ktoś", "Keyé: x", "Tonalité : Ré", "\u212aey: A", "www.campfire.org",
    "site.community", "visit example.com.", "example.comé", "HTTP://EXAMPLE", "A|--2--2--0--2--|", "D#|-------5h7p5--|",
    "|-0-0-0-0-0-0-|", "e|0 1 2 3 4 5 6 7|", "Ve\u200brse 1", "G\u00a0\u00a0D", "\u3000Chorus", "G\u2003Am", "\u001fG D",
    "\U00020000\U00020001 CJK", "\U0001f3f4\U000e0067\U000e0062\U000e0073\U000e0063\U000e0074\U000e007f flag", "\u00ad",
    "   ", "\t\tG\tD",
]
FIXTURE_PAGE_MARKS = {
    0: {"link": 1, "newBlock": 1, "landmark": 1, "size": 0.88, "bold": 0, "italic": 0, "monospace": 0, "heading": 0},
    1: {"link": 1, "newBlock": 1, "landmark": 0, "size": 1, "bold": 1, "italic": 0, "monospace": 0, "heading": 0},
    2: {},
    3: {"link": 0, "newBlock": 1, "landmark": 0, "size": 2.13, "bold": 1, "italic": 0, "monospace": 0, "heading": 1},
    4: {"link": 0, "newBlock": 0, "landmark": 0, "size": 1, "bold": 0, "italic": 1, "monospace": 0, "heading": 0},
    5: {"link": 0, "newBlock": 1, "landmark": 0, "size": 1, "bold": 1, "italic": 0, "monospace": 1, "heading": 0},
    6: {"link": 0, "newBlock": 0, "landmark": 0, "size": 1.07, "bold": 1, "italic": 0, "monospace": 1, "heading": 0},
    7: {"link": 0, "newBlock": 0, "landmark": 0, "size": 1.07, "bold": 0, "italic": 0, "monospace": 1, "heading": 0},
    8: {"unaligned": 1, "covered": 0.42},
    9: {"link": 0, "newBlock": 0, "landmark": 0, "size": 4.5, "bold": 0, "italic": 1, "monospace": 1, "heading": 1},
    24: {"link": 1, "newBlock": 0, "landmark": 1, "size": 0.75, "bold": 0, "italic": 0, "monospace": 0, "heading": 0},
    25: {"link": 1, "newBlock": 1, "landmark": 0, "size": 0.9, "bold": 1, "italic": 1, "monospace": 0, "heading": 0},
}
FIXTURE_DOCUMENT = [
    {"text": "AMAZING GRACE", "page": 0, "column": -1, "size": 1.8, "bold": True, "italic": False, "monospace": False, "indent": 0.3, "heading": True},
    {"text": "John Newton", "page": 0, "column": -1, "size": 1.1, "bold": False, "italic": True, "monospace": False, "indent": 0.35, "heading": False},
    {"text": "G     C    G", "page": 0, "column": 0, "size": 1.0, "bold": True, "italic": False, "monospace": True, "indent": 0.0, "heading": False},
    {"text": "Amazing grace, how sweet", "page": 0, "column": 0, "size": 1.0, "bold": False, "italic": False, "monospace": False, "indent": 0.0, "heading": False},
    {"text": "", "page": 0, "column": 0, "size": 1.0, "bold": False, "italic": False, "monospace": False, "indent": 0.0, "heading": False},
    {"text": "Page 1 of 2", "page": 1, "column": 1, "size": 0.7, "bold": False, "italic": False, "monospace": False, "indent": 0.9, "heading": False},
    {"text": "printed 2026-11-23", "page": 25, "column": 3, "size": 4.0, "bold": False, "italic": False, "monospace": False, "indent": 1.4, "heading": False},
]


def fixtures(folder):
    torch.manual_seed(3)
    buckets = 61
    tiny = PageModel(embedding=4, channels=6, line=8, hidden=5, buckets=buckets)
    for parameter in tiny.parameters():
        torch.nn.init.uniform_(parameter, -0.6, 0.6)
    os.makedirs(folder, exist_ok=True)
    with torch.no_grad():
        write(tiny, os.path.join(folder, "tiny.bin"))
    tiny.eval()
    examples = []
    page = [{"text": text, **({"marks": FIXTURE_PAGE_MARKS[index]} if index in FIXTURE_PAGE_MARKS else {})} for index, text in enumerate(FIXTURE_PAGE)]
    for source, lines in (("page", page), ("document", FIXTURE_DOCUMENT)):
        values = [features(line, source) for line in lines]
        with torch.no_grad():
            logits = tiny(torch.tensor([hashed(line["text"], buckets) for line in lines]), torch.tensor(values, dtype=torch.float32))
        codes = [[code for code in hashed(line["text"]) if code] for line in lines]
        examples.append({"source": source, "lines": lines, "codes": codes, "features": values, "logits": logits.tolist()})
    with open(os.path.join(folder, "golden.json"), "w", encoding="utf-8") as file:
        json.dump({"labels": LABELS, "examples": examples}, file, ensure_ascii=False, indent=1)
    print(f"fixtures written to {folder}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("corpus", nargs="?")
    parser.add_argument("--synthetic", metavar="IMPORT_CORPUS")
    parser.add_argument("--weights", metavar="FILE")
    parser.add_argument("--epochs", type=int, default=12)
    parser.add_argument("--seed", type=int, default=7)
    parser.add_argument("--fixtures", metavar="FOLDER")
    parser.add_argument("--no-marks", action="store_true", help="leave the pages' structure marks out: the model for pastes and documents")
    arguments = parser.parse_args()
    if arguments.fixtures:
        fixtures(arguments.fixtures)
        return
    from gate import load, train, training_set  # noqa: E402

    torch.set_num_threads(max(1, (os.cpu_count() or 2) - 2))
    examples = training_set(load(arguments.corpus, use_marks=not arguments.no_marks), arguments.synthetic, set(), arguments.seed)
    model = train(examples, arguments.epochs, arguments.seed)
    model.eval()
    with torch.no_grad():
        write(model, arguments.weights)
    print(f"trained on {len(examples)} examples; weights written to {arguments.weights} ({os.path.getsize(arguments.weights)} bytes)")


if __name__ == "__main__":
    main()
