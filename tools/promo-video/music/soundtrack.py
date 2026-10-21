# This file is part of Campfire.
# Copyright (c) Pandula Péter 2017-2026.
# https://github.com/pandulapeter/campfire
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://mozilla.org/MPL/2.0/.

"""
The Campfire promo's soundtrack: a backing track for a lead guitar, 20 bars at 160 BPM in 4/4, in A major, from bar
1's downbeat to the end of bar 20, through which the last chord rings out (30 s, the video's 1800 frames), sequenced against the video's cue sheet so every hit lands on the frame its animation does. A recorded acoustic drum kit and a
recorded bass guitar (both from music/samples/, which fetch_samples.py downloads), and synthesizers: a pad, brassy stabs
on the hits, and a plucked arpeggio. The guitar is the owner's, played over this; nothing here sits where a lead does
for long, and the synths are cut around 800 Hz–2 kHz to leave it room.

    python3 soundtrack.py out.wav                    the mix
    python3 soundtrack.py out.wav --stems DIR        also writes drums, bass, synths, fx and chips to DIR, at the mix's scale

The chords, one per beat where a bar has several:

     1 A (ringing)        6 A A# B C        11 A A A G        16 A
     2 A G D/F# G         7 C#              12 D              17 A G D/F# G
     3 A                  8 D (metronome)   13 D D D G        18 A
     4 A A A D            9 D D D G         14 E              19 A G D/F# G
     5 A                 10 A (tuner)       15 E E E G        20 A (ringing)

The owner wrote this chart and records guitars (and likely the bass) over it: the scenes follow the music, never the
other way round. Bars 16–19 bring back the opening's A–G–D/F♯–G, the last of them as bar 2's four stabs.
"""
import glob
import os
import re
import sys
import wave

import numpy as np
from scipy.signal import butter, fftconvolve, lfilter, sosfilt

SR = 48_000
BPM = 160
BEAT = 60 / BPM          # 0.375 s
BAR = 4 * BEAT           # 1.5 s
LEAD_IN = 0.0            # bar 1's downbeat is the video's first frame
LENGTH = 1800 / 60       # the video's length: 20 bars
N = int(LENGTH * SR)
TAIL = SR * 6
SAMPLES = os.path.join(os.path.dirname(os.path.abspath(__file__)), "samples")
rng = np.random.default_rng(7)


def t_of(bar, beat=0.0):
    """Seconds at [beat] (0-based, may be fractional) of 1-based [bar], counted from the start of the track."""
    return LEAD_IN + (bar - 1) * BAR + beat * BEAT


def hz(note):
    return 440.0 * 2 ** ((note - 69) / 12)


def seconds(x):
    return int(round(x * SR))


# ─── Signal helpers ───────────────────────────────────────────────────────────────────────────

def lowpass(sig, cutoff, order=2):
    return sosfilt(butter(order, min(cutoff, SR * 0.45), "low", fs=SR, output="sos"), sig)


def highpass(sig, cutoff, order=2):
    return sosfilt(butter(order, cutoff, "high", fs=SR, output="sos"), sig)


def bandpass(sig, lo, hi, order=2):
    return sosfilt(butter(order, [lo, min(hi, SR * 0.45)], "band", fs=SR, output="sos"), sig)


def peak(sig, freq, gain_db, q=1.0):
    """A peaking EQ band (RBJ biquad)."""
    a = 10 ** (gain_db / 40)
    w = 2 * np.pi * freq / SR
    alpha = np.sin(w) / (2 * q)
    b = np.array([1 + alpha * a, -2 * np.cos(w), 1 - alpha * a])
    den = np.array([1 + alpha / a, -2 * np.cos(w), 1 - alpha / a])
    return lfilter(b / den[0], den / den[0], sig, axis=-1)


def shelf(sig, freq, gain_db, high=True):
    """A shelving EQ (RBJ, slope 1)."""
    a = 10 ** (gain_db / 40)
    w = 2 * np.pi * freq / SR
    cw, alpha = np.cos(w), np.sin(w) / 2 * np.sqrt(2)
    s = 1 if high else -1
    b = np.array([a * ((a + 1) + s * (a - 1) * cw + 2 * np.sqrt(a) * alpha), -2 * s * a * ((a - 1) + s * (a + 1) * cw),
                  a * ((a + 1) + s * (a - 1) * cw - 2 * np.sqrt(a) * alpha)])
    den = np.array([(a + 1) - s * (a - 1) * cw + 2 * np.sqrt(a) * alpha, 2 * s * ((a - 1) - s * (a + 1) * cw),
                    (a + 1) - s * (a - 1) * cw - 2 * np.sqrt(a) * alpha])
    return lfilter(b / den[0], den / den[0], sig, axis=-1)


def sweep(sig, cutoff, q=0.7, block=64):
    """
    A resonant lowpass whose cutoff moves: [cutoff] is a function of the time in seconds from the start of [sig], read
    once per block of 64 samples, the way an analog synth's filter follows its envelope.
    """
    out = np.empty_like(sig)
    z = np.zeros(2)
    for i in range(0, len(sig), block):
        w = 2 * np.pi * min(cutoff(i / SR), SR * 0.45) / SR
        alpha = np.sin(w) / (2 * q)
        cw = np.cos(w)
        b = np.array([(1 - cw) / 2, 1 - cw, (1 - cw) / 2])
        a = np.array([1 + alpha, -2 * cw, 1 - alpha])
        out[i:i + block], z = lfilter(b / a[0], a / a[0], sig[i:i + block], zi=z)
    return out


def saw(freq, length, detune=0.0, phase=None):
    """A band-limited saw (PolyBLEP)."""
    t = np.arange(seconds(length)) / SR
    f = freq * 2 ** (detune / 1200)
    dt = f / SR
    p = (f * t + (rng.random() if phase is None else phase)) % 1.0
    out = 2 * p - 1
    lo = p < dt
    x = p[lo] / dt
    out[lo] -= x + x - x * x - 1
    hi = p > 1 - dt
    x = (p[hi] - 1) / dt
    out[hi] -= x * x + x + x + 1
    return out


def pulse(freq, length, width=0.5, detune=0.0):
    phase = rng.random()
    return saw(freq, length, detune, phase) - saw(freq, length, detune, (phase + width) % 1.0)


def adsr(length, attack, decay, sustain, release):
    """An envelope [length] seconds long, whose release starts [release] seconds before its end."""
    n = seconds(length)
    t = np.arange(n) / SR
    env = np.where(t < attack, t / max(attack, 1e-4), sustain + (1 - sustain) * np.exp(-(t - attack) / max(decay, 1e-4)))
    r = min(seconds(release), n)
    if r:
        env[-r:] *= np.linspace(1, 0, r) ** 2
    return env


def compress(sig, threshold, ratio, attack=0.005, release=0.12, makeup=1.0):
    """A feed-forward compressor on the louder channel, with a peak follower that rises fast and falls slowly."""
    level = np.max(np.abs(sig), axis=0) if sig.ndim == 2 else np.abs(sig)
    # Rise with [attack], fall with [release]: smooth with the slow one, then let any faster rise through.
    slow = lfilter([1 - np.exp(-1 / (release * SR))], [1, -np.exp(-1 / (release * SR))], level)
    fast = lfilter([1 - np.exp(-1 / (attack * SR))], [1, -np.exp(-1 / (attack * SR))], level)
    env = np.maximum(slow, fast) + 1e-9
    gain = np.where(env > threshold, (env / threshold) ** (1 / ratio - 1), 1.0)
    return sig * gain * makeup


def reverb_ir(t60_low, t60_high, length, seed, predelay=0.012, early=True):
    """
    A stereo room: decorrelated noise in three bands, each dying away at its own rate (highs first, as in a real room),
    after a few early reflections and a short predelay.
    """
    r = np.random.default_rng(seed)
    n = seconds(length)
    t = np.arange(n) / SR
    out = np.zeros((2, n))
    for ch in range(2):
        noise = r.standard_normal(n)
        low = lowpass(noise, 500) * np.exp(-6.9 * t / t60_low)
        mid = bandpass(noise, 500, 4000) * np.exp(-6.9 * t / ((t60_low + t60_high) / 2))
        high = highpass(noise, 4000) * np.exp(-6.9 * t / t60_high) * 0.6
        out[ch] = (low + mid + high) * np.minimum(1, t / 0.02)
        if early:
            for k in range(7):
                at = seconds(0.004 + r.random() * 0.045)
                out[ch, at] += (0.9 - k * 0.1) * (1 if r.random() > 0.5 else -1) * 3
    out = np.concatenate([np.zeros((2, seconds(predelay))), out], axis=1)
    return out / np.sqrt(np.sum(out ** 2) / 2)


def convolve(sig, ir):
    return np.stack([fftconvolve(sig[ch], ir[ch])[: sig.shape[1]] for ch in range(2)])


def chorus(sig, depth=0.003, rate=0.6, mix=0.5):
    """Two modulated delays, one per side, a quarter-turn apart: the width of an analog ensemble."""
    t = np.arange(sig.shape[1]) / SR
    out = sig.copy()
    for ch, phase in ((0, 0.0), (1, np.pi / 2)):
        delay = (0.012 + depth * np.sin(2 * np.pi * rate * t + phase)) * SR
        idx = np.arange(sig.shape[1]) - delay
        i0 = np.clip(np.floor(idx).astype(int), 0, sig.shape[1] - 1)
        frac = idx - np.floor(idx)
        mono = sig.mean(axis=0)
        wet = mono[i0] * (1 - frac) + mono[np.clip(i0 + 1, 0, sig.shape[1] - 1)] * frac
        out[ch] = sig[ch] * (1 - mix) + wet * mix
    return out


def ping_pong(sig, delay, feedback=0.38, taps=6, damp=3500):
    """A dotted-eighth ping-pong delay, each repeat duller than the last."""
    out = np.zeros_like(sig)
    mono = sig.mean(axis=0)
    echo = mono
    for k in range(1, taps + 1):
        echo = lowpass(echo, damp, 1)
        i = seconds(delay * k)
        if i >= len(mono):
            break
        out[k % 2, i:] += echo[: len(mono) - i] * feedback ** k
    return out


class Bus:
    def __init__(self):
        self.sig = np.zeros((2, N + TAIL))

    def add(self, start, sig, gain=1.0, pan=0.0):
        """Adds a mono or stereo [sig] at [start] seconds; a mono one is placed at [pan] with a constant-power law."""
        if sig.ndim == 1:
            sig = np.stack([sig * np.sqrt(0.5 * (1 - pan)), sig * np.sqrt(0.5 * (1 + pan))])
        elif pan:
            sig = np.stack([sig[0] * min(1, 1 - pan), sig[1] * min(1, 1 + pan)])
        i = int(round(start * SR))
        if i < 0:
            sig, i = sig[:, -i:], 0
        end = min(i + sig.shape[1], self.sig.shape[1])
        self.sig[:, i:end] += sig[:, : end - i] * gain


# ─── The drum kit (recorded) ──────────────────────────────────────────────────────────────────

def read_wav(path, max_seconds=None):
    with wave.open(path) as w:
        ch, width, n = w.getnchannels(), w.getsampwidth(), w.getnframes()
        if max_seconds:
            n = min(n, seconds(max_seconds))
        raw = np.frombuffer(w.readframes(n), dtype=np.uint8)
    if width == 3:
        b = raw.reshape(-1, 3).astype(np.int32)
        x = (b[:, 0] | (b[:, 1] << 8) | (b[:, 2] << 16)).astype(np.int32)
        x = np.where(x >= 1 << 23, x - (1 << 24), x) / float(1 << 23)
    else:
        x = np.frombuffer(raw.tobytes(), dtype="<i2") / 32768.0
    return x.reshape(-1, ch).T


class Piece:
    """
    One drum of the kit, as recorded: every velocity layer, every round robin, each a mix of its close and overhead
    microphones in the proportions the kit's own SFZ program uses. A hit picks the layer its velocity falls in (now
    and then the one next to it, as a drummer's hands do), the next round robin of that layer, and moves a few
    milliseconds off the grid.
    """

    def __init__(self, folder, close_mic, close_gain, oh_gain, pan, level, max_seconds=5.0, jitter=0.0035):
        layers = {}
        for path in glob.glob(os.path.join(SAMPLES, "drums", folder, close_mic, "*.wav")):
            vl, rr = map(int, re.search(r"_vl(\d+)_rr(\d+)", path).groups())
            close = read_wav(path, max_seconds)[0]
            oh = read_wav(os.path.join(os.path.dirname(os.path.dirname(path)), "oh", os.path.basename(path)), max_seconds)
            n = min(len(close), oh.shape[1])
            fade = np.ones(n)
            fade[-seconds(0.3):] = np.linspace(1, 0, seconds(0.3)) ** 2
            mix = np.stack([close[:n] * close_gain * np.sqrt(0.5 * (1 - pan)), close[:n] * close_gain * np.sqrt(0.5 * (1 + pan))])
            mix += oh[:, :n] * oh_gain
            layers.setdefault(vl, []).append(mix * fade)
        if not layers:
            sys.exit(f"No samples in {SAMPLES}/drums/{folder}: run fetch_samples.py first.")
        self.layers = [layers[k] for k in sorted(layers)]
        loudest = max(np.max(np.abs(s)) for s in self.layers[-1])
        self.scale = level / loudest
        self.next = [0] * len(self.layers)
        self.jitter = jitter

    def render(self, velocity):
        top = len(self.layers) - 1
        layer = int(round(np.clip(velocity, 0, 1) * top))
        if 0 < layer < top and rng.random() < 0.25:
            layer += 1 if rng.random() < 0.5 else -1
        rr = self.layers[layer]
        sig = rr[self.next[layer] % len(rr)]
        self.next[layer] += 1
        # The layers themselves get louder; this only spreads the hits within one.
        return sig * self.scale * (0.82 + 0.18 * velocity) * (1 + rng.normal(0, 0.04))


KIT = {
    "kick": Piece("kick_24/kick", "kick", 1.0, 0.35, 0.0, 1.0, max_seconds=1.2, jitter=0.0025),
    "snare": Piece("snare_14/center", "top", 1.0, 0.55, 0.0, 0.85, max_seconds=1.6, jitter=0.0025),
    "rim": Piece("snare_14/rimshot", "top", 1.0, 0.55, 0.0, 0.95, max_seconds=1.6, jitter=0.0025),
    "hat": Piece("hihat_14/tc", "cl", 0.8, 0.7, -0.35, 0.22, max_seconds=1.0),
    "hat_q": Piece("hihat_14/qo", "cl", 0.8, 0.7, -0.35, 0.26, max_seconds=1.6),
    "hat_open": Piece("hihat_14/open", "cl", 0.8, 0.7, -0.35, 0.3, max_seconds=2.5),
    "crash": Piece("crash_17/cr", "cl", 0.3, 0.8, -0.45, 0.5, max_seconds=5.0),
    "crash2": Piece("crash_sizzle_17/cr", "cl", 0.3, 0.8, 0.45, 0.45, max_seconds=5.0),
    "ride": Piece("ride_22/rd", "cl", 0.3, 0.8, 0.5, 0.24, max_seconds=4.0),
    "bell": Piece("ride_22/bl", "cl", 0.3, 0.8, 0.5, 0.22, max_seconds=4.0),
    "tom_hi": Piece("tom_14/center", "cl", 0.8, 0.55, -0.4, 0.7, max_seconds=2.0),
    "tom_mid": Piece("tom_18/center", "cl", 0.8, 0.55, 0.2, 0.75, max_seconds=2.0),
    "tom_lo": Piece("tom_22/center", "cl", 0.8, 0.55, 0.5, 0.8, max_seconds=2.5),
}
ROOM_SEND = {"kick": 0.1, "snare": 0.5, "rim": 0.5, "tom_hi": 0.4, "tom_mid": 0.4, "tom_lo": 0.4}

drums, room = Bus(), Bus()
kicks = []


def hit(name, at, velocity=0.8):
    piece = KIT[name]
    at = max(0.0, at + rng.normal(0, piece.jitter))
    sig = piece.render(velocity)
    drums.add(at, sig)
    if name in ROOM_SEND:
        room.add(at, sig, ROOM_SEND[name])
    if name == "kick":
        kicks.append(at)


def groove(bar, cymbal="hat", kick_beats=(0, 2, 2.5), snare_beats=(1, 3), open_last=False, ghosts=True, intensity=1.0):
    """
    A rock beat: eighths on the hi-hat (or the ride) leaning on the quarters, the snare on two and four, and a ghost
    note or two in between, softer than anything else in the kit.
    """
    for b in kick_beats:
        hit("kick", t_of(bar, b), 0.85 * intensity + rng.normal(0, 0.03))
    for b in snare_beats:
        hit("snare", t_of(bar, b), 0.88 * intensity + rng.normal(0, 0.03))
    for e in range(8):
        at = t_of(bar, e / 2)
        if cymbal == "ride":
            hit("bell" if e % 4 == 0 else "ride", at, (0.75 if e % 2 == 0 else 0.5) * intensity)
        elif open_last and e == 7:
            hit("hat_q", at, 0.7 * intensity)
        else:
            hit("hat", at, (0.8 if e % 2 == 0 else 0.45) * intensity + rng.normal(0, 0.05))
    if ghosts:
        for g in (1.75, 3.75):
            if rng.random() < 0.6:
                hit("snare", t_of(bar, g), 0.12 + rng.random() * 0.08)


def fill(bar, start_beat, pattern, velocity=0.8, crescendo=0.0):
    """[pattern] is (beat offset, drum) from [start_beat]; [crescendo] adds that much velocity by the last hit."""
    for i, (offset, drum) in enumerate(pattern):
        v = velocity + crescendo * i / max(1, len(pattern) - 1)
        hit(drum, t_of(bar, start_beat + offset), min(1.0, v))


def crash(at, velocity=0.9, second=False):
    hit("crash2" if second else "crash", at, velocity)


def reverse_crash(end, length):
    """A crash played backwards, swelling into the beat it ends on."""
    sig = KIT["crash"].layers[-2][0][:, : seconds(length * 1.4)][:, : seconds(length)][:, ::-1]
    sig = sig * np.linspace(0.05, 1, sig.shape[1]) ** 2 * KIT["crash"].scale * 1.1
    fx.add(end - length, sig)


# ─── The bass (recorded) ──────────────────────────────────────────────────────────────────────

FLATS = ["c", "db", "d", "eb", "e", "f", "gb", "g", "ab", "a", "bb", "b"]
_bass_cache = {}
_bass_rr = {}


def bass_sample(note, dynamic):
    """The library names a note an octave above the pitch it sounds at: `darkblack_a2_*` is A1, MIDI 33."""
    key = (note, dynamic)
    if key not in _bass_cache:
        name = f"{FLATS[note % 12]}{note // 12}"
        paths = sorted(glob.glob(os.path.join(SAMPLES, "bass", "darkblack", "reg", f"darkblack_{name}_{dynamic}_rr*.wav")))
        if not paths:
            sys.exit(f"No bass samples for MIDI {note}: run fetch_samples.py first.")
        _bass_cache[key] = [prepared(read_wav(p)[0], note) for p in paths]
    return _bass_cache[key]


def prepared(sig, note):
    """
    A take made ready to play on the grid and in tune with a guitar: started 3 ms before its attack (the recordings
    begin about 25 ms early), and resampled to the exact pitch of [note] (they are up to 10 cents sharp, as a bass
    string is just after it is plucked). The pitch is read with a harmonic product spectrum over its first second.
    """
    start = max(0, int(np.argmax(np.abs(sig) > 0.1 * np.max(np.abs(sig)))) - seconds(0.003))
    sig = sig[start:]
    part = sig[seconds(0.12):seconds(1.12)]
    spectrum = np.abs(np.fft.rfft(part * np.hanning(len(part)), 2 ** 20))
    freqs = np.fft.rfftfreq(2 ** 20, 1 / SR)
    product = spectrum.copy()
    for k in (2, 3, 4):
        product[: len(spectrum) // k] *= spectrum[::k][: len(spectrum) // k]
    expected = hz(note)
    near = (freqs > expected * 0.9) & (freqs < expected * 1.1)
    ratio = freqs[near][np.argmax(product[near])] / expected
    # Playing [ratio] slower brings it down to [note]: read the take at 1/ratio of a sample per sample.
    positions = np.arange(0, len(sig) - 1, 1 / ratio)
    return np.interp(positions, np.arange(len(sig)), sig)


bass = Bus()


def b(at, note, length, velocity=0.85):
    """
    One plucked note, cut off after [length] seconds the way a bassist's fretting hand lets go: a short fade, never a
    click. Played a hair behind the beat, as bass players do in rock.
    """
    takes = bass_sample(note, "f" if velocity > 0.75 else "mf")
    i = _bass_rr.get(note, 0)
    _bass_rr[note] = i + 1
    sig = takes[i % len(takes)][: seconds(length + 0.04)].copy()
    release = min(seconds(0.04), len(sig))
    sig[-release:] *= np.linspace(1, 0, release) ** 2
    bass.add(at + 0.004 + rng.normal(0, 0.003), sig * velocity * (1 + rng.normal(0, 0.05)))


# A1 is the open A string; the bass plays the chord's root, F♯ under D/F♯.
ROOT = {"A": 33, "A#": 34, "B": 35, "C": 36, "C#": 37, "D": 38, "E": 28, "G": 31, "D/F#": 30}


def bass_eighths(bar, chords, velocity=0.85, octaves=False):
    """Driving eighths, the bar's chords one per beat ([chords] may be a single chord for the whole bar)."""
    chords = [chords] * 4 if isinstance(chords, str) else chords
    for e in range(8):
        note = ROOT[chords[e // 2]] + (12 if octaves and e % 2 else 0)
        b(t_of(bar, e / 2), note, BEAT / 2 * 0.9, velocity * (1.0 if e % 2 == 0 else 0.86))


# ─── The synthesizers ─────────────────────────────────────────────────────────────────────────

# Voicings in the octave around middle C, voice-led from one chord to the next.
PAD = {
    "A": [57, 61, 64, 69], "G": [55, 59, 62, 67], "D/F#": [54, 57, 62, 66], "D": [54, 57, 62, 66],
    "A#": [58, 62, 65, 70], "B": [59, 63, 66, 71], "C": [60, 64, 67, 72], "C#": [61, 65, 68, 73], "E": [56, 59, 64, 68],
}
# The stabs: root, fifth, octave and the major tenth, so the chromatic hits of bar 8 move in parallel and rise.
STAB_ROOT = {"A": 57, "A#": 58, "B": 59, "C": 60, "C#": 61, "D": 50, "E": 52, "G": 55, "D/F#": 54}


def stab_notes(chord):
    if chord == "D/F#":
        return [54, 57, 62, 66, 69]
    r = STAB_ROOT[chord]
    return [r, r + 7, r + 12, r + 16]


pad, stabs, arp, fx, chips = Bus(), Bus(), Bus(), Bus(), Bus()


def pad_chord(at, chord, length, gain=1.0, attack=0.04):
    """Three detuned saws a note, spread across the stereo field; the bus's filter does the shaping."""
    total = length + 0.6
    env = adsr(total, attack, 0.6, 0.8, 0.6)
    for note in PAD[chord]:
        for detune, side in ((-9, -0.7), (0, 0.0), (8, 0.7)):
            pad.add(at, saw(hz(note), total, detune + rng.normal(0, 1.5)) * env * 0.07 * gain, pan=side)


def stab(at, chord, length, gain=1.0, bright=1.0):
    """
    A brassy synth hit: two saws a note through a resonant filter that snaps open and closes within a beat, on top of
    a quick pitch scoop up into the note.
    """
    total = length + 0.35
    env = adsr(total, 0.004, 0.25, 0.45, 0.35)
    sig = np.zeros(seconds(total))
    for note in stab_notes(chord):
        for detune in (-6, 7):
            sig += saw(hz(note), total, detune)
    opened = 5200 * bright
    sig = sweep(sig, lambda t: 500 + opened * np.exp(-t / 0.09) + 900 * np.exp(-t / 0.6), q=1.4)
    sig = np.tanh(sig * env * 0.5) * 0.5
    side = rng.normal(0, 0.15)
    stabs.add(at, sig * gain, pan=side)


def pluck(at, note, length=BEAT / 2, gain=1.0, bright=1.0, bus=None):
    """A plucked square through a closing filter: the arpeggio's voice, and the chips' on [bus]."""
    total = length + 0.25
    sig = pulse(hz(note), total, 0.32) + 0.4 * saw(hz(note) * 2 ** (7 / 1200), total)
    sig = sweep(sig, lambda t: 350 + 3200 * bright * np.exp(-t / 0.07), q=2.0)
    env = adsr(total, 0.002, 0.12, 0.25, 0.2)
    (bus or arp).add(at, sig * env * 0.16 * gain, pan=rng.uniform(-0.25, 0.25))


def arpeggio(bar, chords, octave=0, gain=1.0, bright=1.0):
    """Eighths up and down the chord, its root, fifth, octave and tenth, one chord per beat."""
    chords = [chords] * 4 if isinstance(chords, str) else chords
    shape = [0, 7, 12, 16, 12, 7, 12, 16]
    for e in range(8):
        chord = chords[e // 2]
        root = (50 if chord == "D/F#" else STAB_ROOT[chord]) + 12 * octave
        pluck(t_of(bar, e / 2), root + shape[(e + bar * 8) % 8], gain=gain * (1.0 if e % 2 == 0 else 0.8), bright=bright)


def impact(at, gain=1.0):
    """A low boom under a downbeat: a sine dropping in pitch, and a burst of dark noise."""
    L = 1.6
    t = np.arange(seconds(L)) / SR
    f = 38 + 60 * np.exp(-t * 9)
    boom = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 2.6)
    noise = lowpass(rng.standard_normal(len(t)), 900) * np.exp(-t * 7) * 0.4
    sig = np.tanh((boom + noise) * 1.5) * np.minimum(1, t / 0.002)
    fx.add(at, highpass(sig, 28) * 0.55 * gain)


def riser(end, length, gain=1.0):
    """Noise through a band that sweeps up into the beat at [end], growing louder all the way."""
    n = seconds(length)
    t = np.arange(n) / SR
    noise = rng.standard_normal(n)
    sig = sweep(noise, lambda s: 300 * (40 ** (s / length)), q=3.0)
    sig = highpass(sig, 200) * (t / length) ** 2.2
    l, r = sig, np.roll(sig, seconds(0.011))
    fx.add(end - length, np.stack([l, r]) * 0.22 * gain)


def pink(n, r):
    """Noise falling 3 dB an octave, as air does, rather than white noise's flat hiss."""
    spectrum = np.fft.rfft(r.standard_normal(n))
    spectrum /= np.sqrt(np.maximum(np.arange(len(spectrum)), 1))
    sig = np.fft.irfft(spectrum, n)
    return sig / np.std(sig)


def whoosh(at, length, gain=1.0):
    """
    Air rushing past as the light theme washes over the screen. The reveal opens fastest at first, so the sound swells
    to its peak a fifth of the way in and then falls away for the rest, past [length] into a tail. Three layers of pink
    noise, each through a gentle band of its own sweeping down at its own rate (a body around 1 kHz to 200 Hz, the air
    above it, and a thin sparkle on top that is gone first), with a soft E gliding down an octave under them for weight.
    Each side has noise of its own, so it is wide rather than a copy, and it travels from left of centre across to the
    right as the circle grows out of the laptop's upper left; a short room of its own blurs the end into the groove.
    """
    r = np.random.default_rng(16)
    total = length + 0.7
    n = seconds(total)
    t = np.arange(n) / SR
    rise = length * 0.2
    env = np.where(t < rise, np.sin(np.pi / 2 * np.minimum(t / rise, 1)) ** 2, np.exp(-(t - rise) * 3.2 / length))
    sides = []
    for ch in range(2):
        body = sweep(pink(n, r), lambda s: 1100 * (0.18 ** (s / total)), q=1.1)
        air = highpass(sweep(pink(n, r), lambda s: 7500 * (0.12 ** (s / total)), q=0.8), 700)
        sparkle = highpass(r.standard_normal(n), 7000) * np.exp(-t * 6 / length)
        sides.append(body * 0.9 + air * 0.75 + sparkle * 0.12)
    tone = np.sin(2 * np.pi * np.cumsum(164.81 * 2 ** (-np.minimum(t / length, 1))) / SR)
    tone = lowpass(tone, 400) * 0.25
    sig = np.stack([sides[0] + tone, sides[1] + tone]) * env
    sig *= np.minimum(1, t / 0.005) * np.minimum(1, (total - t) / 0.05)
    position = -0.45 + 0.85 * np.minimum(t / length, 1) ** 0.6
    sig = np.stack([sig[0] * np.sqrt(0.5 * (1 - position)), sig[1] * np.sqrt(0.5 * (1 + position))]) * np.sqrt(2)
    sig = sig + convolve(sig, reverb_ir(1.1, 0.6, 1.4, 16, predelay=0.015, early=False)) * 0.25
    fx.add(at, sig * 0.115 * gain)


def click(at, accent):
    """The app's own metronome, its CLICK sound (ClickSynthesizer.kt): a 2.5 kHz tick on one, 1.8 kHz on the others."""
    L = 0.025
    t = np.arange(seconds(L)) / SR
    env = np.minimum(1, t / 0.0005) * np.minimum(1, (L - t) / 0.002) * np.exp(-t / 0.004)
    fx.add(at, np.sin(2 * np.pi * (2500 if accent else 1800) * t) * env * (0.9 if accent else 0.65) * 0.55)


# ─── The arrangement ──────────────────────────────────────────────────────────────────────────

# Bar 1: the icon lands on a ringing A with an impact and both crashes; the name spells itself in over a quick rising
# arpeggio from beat 2.
impact(t_of(1), 1.0)
hit("kick", t_of(1), 1.0)
crash(t_of(1), 1.0)
crash(t_of(1), 0.85, second=True)
stab(t_of(1), "A", BAR * 0.9, 1.0)
pad_chord(t_of(1), "A", BAR * 0.95, 0.8)
b(t_of(1), ROOT["A"], BAR * 0.95, 0.95)
for i, note in enumerate([69, 73, 76, 81, 76, 81, 85, 88, 81, 85]):
    pluck(t_of(1, 1 + i * 0.25), note, BEAT / 4, gain=0.35 + 0.04 * i, bright=0.8)
reverse_crash(t_of(2), BEAT * 1.5)

# Bar 2: "Lyrics." "Chords." "Wherever you play." — A, G, D/F♯ and G, one hit each; the third line, the big one,
# lands on the third hit with a crash, and the fourth beat is held under a snare build into the groove.
for i, chord in enumerate(["A", "G", "D/F#", "G"]):
    at = t_of(2, i)
    stab(at, chord, BEAT * (0.8 if i < 3 else 0.95), 1.0 if i != 2 else 1.15, bright=1.0 + 0.1 * i)
    b(at, ROOT[chord], BEAT * (0.85 if i < 3 else 0.95), 0.95)
    hit("kick", at, 0.9)
    if i < 3:
        hit("snare" if i != 2 else "rim", at, 0.8 + 0.08 * i)
crash(t_of(2, 2), 1.0)
fill(2, 3, [(0, "snare"), (0.25, "snare"), (0.5, "snare"), (0.75, "snare")], 0.45, 0.45)

# Bars 3–5: the groove under the library's search and the song. Palm-muted eighths on the bass and a pad that holds the
# chord; the search opens on bar 3's downbeat and takes a letter on each of its last three beats, each with a high pluck
# climbing A major's pentatonic under the library's chips.
VERSE = {3: "A", 4: ["A", "A", "A", "D"], 5: "A"}
for bar, chords in VERSE.items():
    groove(bar, open_last=bar == 4)
    bass_eighths(bar, chords)
    for i, chord in enumerate([chords] if isinstance(chords, str) else chords):
        span = 4 if isinstance(chords, str) else 1
        pad_chord(t_of(bar, i), chord, BEAT * span * 0.98, 0.9 if bar < 5 else 1.0)
    if bar == 5:
        arpeggio(bar, chords, gain=0.8)
crash(t_of(3), 0.9)
# "h", "o", "u", one a beat from bar 3's second, each with a chip and a pluck climbing A major's pentatonic, on the
# chips' own bus.
for i, note in enumerate([76, 78, 81]):
    pluck(t_of(3, 1 + i), note, BEAT * 0.6, gain=0.55, bright=1.2, bus=chips)
fill(4, 3, [(0, "snare"), (0.5, "snare"), (0.75, "snare")], 0.6, 0.25)
# The page turns on bar 5's third beat; the toms run down from there into the transposition's first hit.
fill(5, 2, [(0, "snare"), (0.25, "snare"), (0.5, "tom_hi"), (0.75, "tom_hi"), (1.0, "tom_mid"), (1.25, "tom_mid"),
            (1.5, "tom_lo"), (1.75, "tom_lo")], 0.7, 0.25)

# Bar 6: "Transpose in a tap." A, A♯, B and C, one hit a beat, every chord a semitone above the last.
for i, chord in enumerate(["A", "A#", "B", "C"]):
    at = t_of(6, i)
    stab(at, chord, BEAT * 0.8, 1.0, bright=1.0 + 0.12 * i)
    b(at, ROOT[chord], BEAT * 0.85, 0.95)
    hit("kick", at, 0.9)
    hit("snare" if i % 2 else "hat_q", at, 0.85 if i % 2 else 0.6)
crash(t_of(6), 1.0)

# Bar 7: "Read chords your way." C♯ in four notations, four hits on C♯ building with the crashes.
for i in range(4):
    at = t_of(7, i)
    stab(at, "C#", BEAT * 0.8, 0.9 + 0.06 * i, bright=1.0 + 0.15 * i)
    b(at, ROOT["C#"] + (12 if i == 3 else 0), BEAT * 0.85, 0.9)
    hit("kick", at, 0.9)
    hit("snare", at, 0.55 + 0.12 * i)
    crash(at, 0.4 + 0.15 * i, second=bool(i % 2))
fill(7, 3.5, [(0, "snare"), (0.25, "snare")], 0.7, 0.2)

# Bars 8–9: the metronome on screen plays the beat itself. The band drops to a half-time feel on D, the pad held,
# the bass on the downbeat and the push of beat 3: the most room a solo gets in the video. G on bar 9's last beat.
for bar in (8, 9):
    for i in range(4):
        click(t_of(bar, i), i == 0)
    hit("kick", t_of(bar, 0), 0.85)
    hit("kick", t_of(bar, 2.5), 0.6)
    hit("snare", t_of(bar, 2), 0.75)
    for e in range(4):
        hit("ride", t_of(bar, e), 0.4)
    pad_chord(t_of(bar), "D", (BEAT * 3 if bar == 9 else BAR) * 0.98, 0.9, attack=0.08)
    b(t_of(bar), ROOT["D"], BEAT * 1.9, 0.85)
    b(t_of(bar, 2.5), ROOT["D"], BEAT * (0.45 if bar == 9 else 1.4), 0.75)
crash(t_of(8), 0.7)
pad_chord(t_of(9, 3), "G", BEAT * 0.98, 0.9)
b(t_of(9, 3), ROOT["G"], BEAT * 0.9, 0.85)
fill(9, 3, [(0, "tom_mid"), (0.5, "tom_lo"), (0.75, "tom_lo")], 0.7, 0.15)

# Bar 10: everything but the bass, a soft pad and the ride drops away for the tuner; the owner's guitar plays its A.
for i in range(4):
    hit("ride", t_of(10, i), 0.55 if i == 0 else 0.35)
pad_chord(t_of(10), "A", BAR * 0.98, 0.55, attack=0.3)
b(t_of(10), ROOT["A"], BAR * 0.95, 0.7)
reverse_crash(t_of(11), BEAT * 2)

# Bar 11: in tune on the downbeat, where the ring opens: the band comes back in, half-time, A and then G on the last
# beat. The Chords tab comes in on the third beat, on a crash, and the snare runs sixteenths into bar 12.
impact(t_of(11), 0.7)
hit("kick", t_of(11), 1.0)
crash(t_of(11), 0.95)
hit("snare", t_of(11, 2), 0.85)
crash(t_of(11, 2), 0.8, second=True)
hit("kick", t_of(11, 2.5), 0.6)
for i in range(3):
    hit("ride", t_of(11, i + 0.5), 0.4)
pad_chord(t_of(11), "A", BEAT * 2.95, 1.0)
pad_chord(t_of(11, 3), "G", BEAT * 0.98, 1.0)
stab(t_of(11), "A", BEAT * 1.5, 0.75)
b(t_of(11), ROOT["A"], BEAT * 1.9, 0.9)
b(t_of(11, 2), ROOT["A"], BEAT * 0.9, 0.8)
b(t_of(11, 3), ROOT["G"], BEAT * 0.9, 0.85)
fill(11, 3, [(0, "snare"), (0.25, "snare"), (0.5, "snare"), (0.75, "snare")], 0.5, 0.4)

# Bars 12–13: the Chords tab and the Scales tab, the full groove back on D; a crash on bar 12's downbeat, where the
# diagrams turn to the ukulele, one on bar 13's and one on its second, where a scale's sheet opens, and a fill into the E.
for bar, chords in ((12, "D"), (13, ["D", "D", "D", "G"])):
    groove(bar, open_last=bar == 12)
    bass_eighths(bar, chords)
    arpeggio(bar, chords)
    pad_chord(t_of(bar), "D", (BEAT * 3 if bar == 13 else BAR) * 0.98)
pad_chord(t_of(13, 3), "G", BEAT * 0.98)
crash(t_of(12), 0.95)
crash(t_of(13), 0.9)
crash(t_of(13, 1), 0.7, second=True)
fill(13, 3, [(0, "snare"), (0.5, "tom_hi"), (0.75, "tom_mid")], 0.7, 0.2)

# Bar 14: the laptop comes in on the downbeat's crash, and a whoosh on the second beat, the only effect here, as the
# light theme washes over it for a beat and a half. Bars 14–15 are the brightest part: E with the bass jumping octaves,
# the arpeggio an octave up, the groove on the ride.
crash(t_of(14), 1.0)
whoosh(t_of(14, 1), BEAT * 1.6, 3.0)
for bar, chords in ((14, "E"), (15, ["E", "E", "E", "G"])):
    groove(bar, cymbal="ride", intensity=1.05)
    bass_eighths(bar, chords, 0.9, octaves=True)
    arpeggio(bar, chords, octave=1, gain=0.85, bright=1.3)
    pad_chord(t_of(bar), "E", (BAR if bar == 14 else BEAT * 3) * 0.98, 1.1)
pad_chord(t_of(15, 3), "G", BEAT * 0.98, 1.1)
# The iPad lands on bar 14's third beat and the Android phone on its fourth; the platform names cascade in from bar 15.
crash(t_of(14, 2), 0.75, second=True)
hit("rim", t_of(14, 3), 0.9)
crash(t_of(15), 0.9, second=True)
fill(15, 3, [(0, "snare"), (0.25, "snare"), (0.5, "tom_hi"), (0.75, "tom_mid")], 0.6, 0.35)

# Bars 16–18: home on A: the setlist, the import, and the export over the opening's groove and its A–G–D/F♯–G, the
# arpeggio back in its own octave. The setlist's song is lifted on bar 16's second beat and dropped on its fourth, at the
# end of a tom run; a chord sheet is pasted on bar 17's downbeat, a chip on each of its chords; the export rides the
# ride, and the snare builds into the four promises.
for bar, chords in ((16, "A"), (17, ["A", "G", "D/F#", "G"]), (18, "A")):
    groove(bar, cymbal="ride" if bar == 18 else "hat", kick_beats=(0, 1.5, 2, 2.5) if bar == 17 else (0, 2, 2.5))
    bass_eighths(bar, chords)
    arpeggio(bar, chords)
    for i, chord in enumerate([chords] if isinstance(chords, str) else chords):
        span = 4 if isinstance(chords, str) else 1
        pad_chord(t_of(bar, i), chord, BEAT * span * 0.98)
impact(t_of(16), 0.6)
crash(t_of(16), 1.0)
crash(t_of(16), 0.8, second=True)
fill(16, 2, [(0, "snare"), (0.5, "tom_hi"), (0.75, "tom_mid")], 0.65, 0.2)
crash(t_of(16, 3), 0.6, second=True)
crash(t_of(17), 0.85, second=True)
crash(t_of(18), 0.8)
fill(18, 3, [(0, "snare"), (0.25, "snare"), (0.5, "snare"), (0.75, "snare")], 0.55, 0.4)

# Bar 19: "Free." "No ads." "No account." "Offline." — bar 2's stops again, A, G, D/F♯ and G, a word a hit, the third
# the big one, with a line climbing over them into the last chord's A.
for i, chord in enumerate(["A", "G", "D/F#", "G"]):
    at = t_of(19, i)
    stab(at, chord, BEAT * (0.8 if i < 3 else 0.95), 1.0 if i != 2 else 1.15, bright=1.0 + 0.1 * i)
    b(at, ROOT[chord], BEAT * (0.85 if i < 3 else 0.95), 0.95)
    hit("kick", at, 0.95)
    if i < 3:
        hit("snare" if i != 2 else "rim", at, 0.8 + 0.08 * i)
    pluck(at, [73, 74, 78, 79][i], BEAT * 0.8, gain=0.7, bright=1.3)
crash(t_of(19, 2), 1.0)
fill(19, 3, [(0, "snare"), (0.25, "snare"), (0.5, "snare"), (0.75, "snare")], 0.5, 0.5)

# Bar 20: the icon and the name on the last hit, A with an impact and two crashes, ringing out to the end.
impact(t_of(20), 1.0)
hit("kick", t_of(20), 1.0)
hit("snare", t_of(20), 0.95)
crash(t_of(20), 1.0)
crash(t_of(20), 0.9, second=True)
stab(t_of(20), "A", BAR * 1.4, 1.0)
pad_chord(t_of(20), "A", BAR * 1.4, 1.1)
b(t_of(20), ROOT["A"], BAR * 1.4, 0.95)

# A scene that pushes out without a fill under it gets a reversed crash into the next scene's first beat: the metronome
# and the Chords tab.
for bar, beat in ((8, 0), (11, 2)):
    reverse_crash(t_of(bar, beat), BEAT)

# Every feature chip that pops onto the screen gets a pluck of a note of the chord under it, on the beat it lands, on
# a bus of its own, so that a mix can set them apart.
CHIPS = [
    (5, 0, 81), (5, 1, 85), (5, 2, 88),      # Every chord, with diagrams; Scale suggestions; Pedal & tap (A)
    (8, 3, 86), (9, 1, 90),                  # Tap tempo; Count-ins (D)
    (10, 1, 81), (10, 2, 85), (10, 3, 88),   # Instrument tuner; Chromatic tuner; Alternate tunings (A)
    (11, 2, 81),                             # Keyboard (A)
    (12, 0, 86), (12, 1, 90), (12, 2, 93),   # Ukulele; Guitar; Mandolin (D)
    (12, 3, 90),                             # Banjo (D)
    (13, 2, 90), (13, 3, 91),                # Any instrument & tuning (D); Notes & intervals (G)
    (16, 1, 85), (16, 3, 88),                # Drag to reorder; Countdown (A)
    (17, 0, 85), (17, 1, 83),                # Paste from websites (A); PDF & Word (G)
    (17, 2, 86), (17, 3, 91),                # OnSong & SongbookPro (D/F♯); OpenSong (G)
    (18, 1, 85), (18, 2, 88),                # Whole setlists; Individual songs (A)
]
for bar, beat, note in CHIPS:
    pluck(t_of(bar, beat), note, BEAT * 0.35, gain=0.6, bright=1.4, bus=chips)

# ─── Mix ──────────────────────────────────────────────────────────────────────────────────────

def sidechain(length, times, depth, release=0.16):
    """The pad and the arpeggio dip under every kick and swell back, which is what makes them pump with the beat."""
    g = np.ones(length)
    t = np.arange(seconds(release)) / SR
    curve = 1 - depth * (1 - t / release) ** 2
    for at in times:
        i = int(at * SR)
        j = min(i + len(curve), length)
        g[i:j] = np.minimum(g[i:j], curve[: j - i])
    return g


def pad_cutoff(t):
    """The pad bus's filter over the whole track: closed in the tuner break, open after the page turn and for the light theme."""
    points = [(0, 2600), (t_of(3), 1800), (t_of(5, 1), 1800), (t_of(5, 3.5), 4200), (t_of(6), 4200), (t_of(7), 2200),
              (t_of(8), 1700), (t_of(10), 900), (t_of(11), 2200), (t_of(13, 2), 2400), (t_of(14), 4800), (t_of(16), 4800),
              (t_of(16, 1), 3400), (t_of(19), 3800), (t_of(20), 3600), (LENGTH + 6, 1500)]
    xs, ys = zip(*points)
    return float(np.exp(np.interp(t, xs, np.log(ys))))


total = N + TAIL
duck = sidechain(total, kicks, 0.45)

# Drums: a little glue, a room for the snare and toms, and a lift on top so the cymbals shine.
drum_bus = compress(drums.sig, 0.5, 3.0, 0.004, 0.1) + convolve(room.sig, reverb_ir(0.7, 0.35, 1.0, 1)) * 0.22
drum_bus = shelf(highpass(drum_bus, 30), 8000, 2.0)

# The bass: tamed peaks, a little warmth from the tube-like curve, and its upper mids pulled back under the guitar.
bass_bus = compress(bass.sig, 0.35, 4.0, 0.003, 0.15, makeup=1.6)
bass_bus = np.tanh(bass_bus * 1.6) / 1.6
bass_bus = peak(highpass(bass_bus, 32), 1200, -4, 0.8)
bass_bus = peak(bass_bus, 90, 2.5, 0.9) * (1 - 0.25 * (1 - duck))

# Synths: the pad filtered by its automation, widened and put in a hall; the stabs and arpeggio in the same hall, the
# arpeggio echoing a dotted eighth across the sides. All of them leave 800 Hz–2 kHz to the guitar.
pad_bus = np.stack([sweep(pad.sig[ch], pad_cutoff, q=0.9) for ch in range(2)])
pad_bus = chorus(highpass(pad_bus, 160), 0.0025, 0.5, 0.45) * duck
stab_bus = highpass(stabs.sig, 120)
arp_bus = highpass(arp.sig, 220) * duck
arp_bus = arp_bus + ping_pong(arp_bus, BEAT * 0.75, 0.35) * 0.55
hall = reverb_ir(2.4, 1.2, 3.2, 3, predelay=0.03, early=False)
synth_dry = pad_bus * 0.9 + stab_bus * 0.55 + arp_bus * 0.55
synth_bus = synth_dry + convolve(pad_bus * 0.25 + stab_bus * 0.35 + arp_bus * 0.3, hall) * 0.6
synth_bus = peak(synth_bus, 1300, -5, 0.7)

fx_bus = fx.sig + convolve(fx.sig, reverb_ir(2.0, 1.0, 2.6, 4, predelay=0.02, early=False)) * 0.35

# The synths stay well under the band: the owner's guitar is the lead, and they only fill around it.
# The chips: the arpeggio's echo and hall, at twice the level the synths would give them.
chips_bus = highpass(chips.sig, 300)
chips_bus = chips_bus + ping_pong(chips_bus, BEAT * 0.75, 0.3) * 0.4
chips_bus = chips_bus * 0.55 + convolve(chips_bus * 0.3, hall) * 0.6

stems = {"drums": drum_bus * 0.95, "bass": bass_bus * 0.8, "synths": synth_bus * 0.7, "fx": fx_bus * 0.8, "chips": chips_bus * 1.4}
mix = sum(stems.values())[:, :N]
fade = np.ones(N)
fade[-seconds(1.0):] = np.linspace(1, 0, seconds(1.0)) ** 1.5

# Master: gentle glue, then only the loudest peaks reach the soft clip — the mix is brought to where its 99.9th
# percentile sits at 0.75 first. ffmpeg's loudnorm sets the final loudness (regenerate.sh).
mix = compress(mix, 0.6, 2.0, 0.01, 0.2) * fade
scale = 0.75 / np.percentile(np.abs(mix), 99.9)
mix = np.tanh(mix * scale)
mix /= np.max(np.abs(mix)) / 0.89

for bar in range(1, 21):
    seg = mix[:, int(t_of(bar) * SR):int(t_of(bar + 1) * SR)]
    print(f"bar {bar:2d}: {20 * np.log10(np.sqrt(np.mean(seg ** 2)) + 1e-9):6.1f} dB")


def write(path, sig, width=2):
    with wave.open(path, "wb") as w:
        w.setnchannels(2)
        w.setsampwidth(width)
        w.setframerate(SR)
        if width == 3:
            x = (np.clip(sig.T, -1, 1) * 8388607).astype("<i4").reshape(-1)
            w.writeframes(np.stack([x & 0xFF, (x >> 8) & 0xFF, (x >> 16) & 0xFF], axis=1).astype(np.uint8).tobytes())
        else:
            w.writeframes((np.clip(sig.T, -1, 1) * 32767).astype("<i2").tobytes())


args = [a for a in sys.argv[1:]]
if "--stems" in args:
    folder = args[args.index("--stems") + 1]
    os.makedirs(folder, exist_ok=True)
    # The stems at one common scale, before the master's compression and clip: summed, they are the backing track.
    for name, sig in stems.items():
        write(os.path.join(folder, f"{name}.wav"), sig[:, :N] * fade * scale * 0.7, width=3)
    args.remove(folder)
out = next((a for a in args if not a.startswith("--")), "music.wav")
write(out, mix)
print("wrote", out, mix.shape[1] / SR, "s")
