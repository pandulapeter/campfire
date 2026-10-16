/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
import React from "react";
import { AbsoluteFill, Easing, Img, interpolate, Sequence, spring, staticFile, useCurrentFrame } from "remotion";
import {
  Accent,
  Backdrop,
  BAR,
  BEAT,
  beat,
  C,
  Chip,
  CircleReveal,
  Clip,
  ease,
  fontFaces,
  Headline,
  MONO,
  Phone,
  pulse,
  SANS,
  Slate,
  Stage,
  Still,
  useFormat,
} from "./kit";

// The cue sheet: every scene starts on a bar or halfway through one (90 frames = 1.5 s at 160 BPM).
const S = {
  intro: { from: 0, bars: 2 },
  // The iPad, its song already open, and the setlist both come in on a third beat: bar 4's D and bar 6's D/F♯.
  songs: { from: 2 * BAR, bars: 1.5 },
  song: { from: 3.5 * BAR, bars: 2 },
  setlist: { from: 5.5 * BAR, bars: 1.5 },
  transpose: { from: 7 * BAR, bars: 2 },
  metronome: { from: 9 * BAR, bars: 2 },
  // The tuner leaves as soon as its string is in tune, on bar 13's downbeat, and the Chords tab comes in on that bar's G.
  tuner: { from: 11 * BAR, bars: 1.5 },
  chords: { from: 12.5 * BAR, bars: 1.5 },
  // The export rides the riser into bar 16, where the laptop comes in; the light sweeps across it a beat later.
  export: { from: 14 * BAR, bars: 1 },
  everywhere: { from: 15 * BAR, bars: 2 },
  values: { from: 17 * BAR, bars: 1 },
  outro: { from: 18 * BAR, bars: 2 },
};

/**
 * The video is the soundtrack's 20 bars and nothing else: the first frame is bar 1's downbeat and the last chord rings
 * through bar 20 to the end, 1800 frames, 30 s. A beat is 22.5 frames, so every other beat falls halfway between two
 * frames.
 */
export const LEAD_IN = 0;
export const DURATION = 20 * BAR;

export const Promo: React.FC = () => (
  <AbsoluteFill style={{ background: C.bg }}>
    <Backdrop />
    <Sequence from={LEAD_IN}>
      <Bars />
    </Sequence>
  </AbsoluteFill>
);

const Bars: React.FC = () => {
  const frame = useCurrentFrame();
  const light = ease(frame, [S.everywhere.from + LIGHT_FROM, S.everywhere.from + LIGHT_TO], [0, 1], Easing.out(Easing.cubic));
  const dark = interpolate(frame, [S.values.from - 4, S.values.from + 6], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  return (
    <AbsoluteFill style={{ background: C.bg }}>
      <style>{fontFaces}</style>
      <Backdrop light={light * (1 - dark)} />
      {scene(S.intro, <Intro />)}
      {scene(S.songs, <Songs />)}
      {scene(S.song, <Song />)}
      {scene(S.setlist, <Setlist />)}
      {scene(S.transpose, <Transpose />)}
      {scene(S.metronome, <Metronome />)}
      {scene(S.tuner, <Tuner />)}
      {scene(S.chords, <Chords />)}
      {scene(S.export, <Export />)}
      {scene(S.everywhere, <Everywhere />)}
      {scene(S.values, <Values />, false)}
      {scene(S.outro, <Outro />, false)}
    </AbsoluteFill>
  );
};

/** How far into the next scene a scene's exit runs, under the next scene's devices coming in. */
const OVERLAP = 14;

const scene = (s: { from: number; bars: number }, content: React.ReactNode, exits = true) => (
  <Sequence from={s.from} durationInFrames={Math.round(s.bars * BAR) + (exits ? OVERLAP : 0)}>
    <Exit duration={Math.round(s.bars * BAR)} enabled={exits}>
      {content}
    </Exit>
  </Sequence>
);

/**
 * Every scene leaves from just before its end into the start of the next, pushed toward the viewer, blurred and faded,
 * while the next one's devices are already on their way in over it.
 */
const Exit: React.FC<{ duration: number; enabled: boolean; children: React.ReactNode }> = ({ duration, enabled, children }) => {
  const frame = useCurrentFrame();
  const t = enabled ? ease(frame, [duration - 6, duration + OVERLAP], [0, 1], Easing.in(Easing.quad)) : 0;
  return (
    <AbsoluteFill style={{ transform: `scale(${1 + t * 0.12})`, opacity: 1 - t, filter: t > 0 ? `blur(${t * 14}px)` : undefined }}>
      {children}
    </AbsoluteFill>
  );
};

type Enter = "bottom" | "top" | "left" | "right" | "zoom";

/**
 * Places a device centred on (x, y), springing in at [at] from the side [enter] names, or growing out of the middle,
 * with a slight turn as it lands. An [at] below zero has the device already on its way when the scene starts.
 */
const Place: React.FC<{ x?: number; y: number; at: number; rotate?: number; from?: number; scale?: number; enter?: Enter; children: React.ReactNode }> = ({
  x = 540,
  y,
  at,
  rotate = 0,
  from = 1100,
  scale = 1,
  enter = "bottom",
  children,
}) => {
  const frame = useCurrentFrame();
  const p = spring({ frame: frame - at, fps: 60, config: { damping: 15, stiffness: 90, mass: 0.9 } });
  const away = (1 - p) * from;
  const move = {
    bottom: `translateY(${away}px) rotate(${rotate + (1 - p) * 10}deg)`,
    top: `translateY(${-away}px) rotate(${rotate - (1 - p) * 10}deg)`,
    left: `translateX(${-away}px) rotate(${rotate - (1 - p) * 12}deg)`,
    right: `translateX(${away}px) rotate(${rotate + (1 - p) * 12}deg)`,
    zoom: `rotate(${rotate}deg) scale(${0.55 + 0.45 * p})`,
  }[enter];
  return (
    <div
      style={{
        position: "absolute",
        left: x,
        top: y,
        transform: `translate(-50%, -50%) ${move} scale(${scale})`,
        opacity: enter === "zoom" ? Math.min(1, p * 1.6) : p > 0.01 ? 1 : 0,
      }}
    >
      {children}
    </div>
  );
};

// ─── Bars 1–2: the icon, the name, the promise ────────────────────────────────────────────────

const Intro: React.FC = () => {
  const frame = useCurrentFrame();
  const icon = spring({ frame, fps: 60, config: { damping: 9, stiffness: 120, mass: 0.7 } });
  const ring = ease(frame, [0, 40], [0, 1], Easing.out(Easing.cubic));
  const word = "Campfire";
  const lift = ease(frame, [BAR - 10, BAR + 12], [0, -240]);
  const iconScale = ease(frame, [BAR - 10, BAR + 12], [1, 0.62]);
  const lines = ["Lyrics.", "Chords.", "Wherever you play."];
  return (
    <Stage band={[300, 1500]} full>
      <div
        style={{
          position: "absolute",
          left: 540,
          top: 760 + lift,
          width: 760,
          height: 760,
          transform: "translate(-50%, -50%)",
          borderRadius: 999,
          border: `6px solid ${C.orange}`,
          opacity: (1 - ring) * 0.8,
          scale: `${0.3 + ring * 0.9}`,
        }}
      />
      <Img
        src={staticFile("icons/app_icon.png")}
        style={{
          position: "absolute",
          left: 540,
          top: 760 + lift,
          width: 330,
          height: 330,
          transform: `translate(-50%, -50%) scale(${icon * iconScale}) rotate(${(1 - icon) * -40}deg)`,
        }}
      />
      <div style={{ position: "absolute", top: 990 + lift * 1.35, left: 0, right: 0, textAlign: "center", fontFamily: SANS, fontWeight: 700, fontSize: 150, letterSpacing: -5, color: C.ink }}>
        {word.split("").map((ch, i) => {
          // Started early by the frames the spring takes to all but arrive, so the first letter is there on beat 2.
          const p = spring({ frame: frame - (BEAT - 6) - i * 2.4, fps: 60, config: { damping: 12, stiffness: 160, mass: 0.6 } });
          return (
            <span key={i} style={{ display: "inline-block", transform: `translateY(${(1 - p) * 80}px) scale(${0.6 + p * 0.4})`, opacity: p }}>
              {ch}
            </span>
          );
        })}
      </div>
      {lines.map((line, i) => {
        // A stiff spring started four frames early, so each line is at full size on its stab (bar 2, beats 1–3) and
        // overshoots just after it, the punch of the hit; bar 2's fourth hit, which has no line, leaves them still.
        const p = spring({ frame: frame - (BAR + i * BEAT - 4), fps: 60, config: { damping: 13, stiffness: 380, mass: 0.5 } });
        return (
          <div
            key={line}
            style={{
              position: "absolute",
              top: 1000 + i * 130,
              left: 0,
              right: 0,
              textAlign: "center",
              fontFamily: SANS,
              // Lighter than the name above them, so they read as its subtitle rather than as three more titles; the
              // orange line, the smallest, a step heavier to hold its own.
              fontWeight: i === 2 ? 500 : 400,
              fontSize: i === 2 ? 92 : 112,
              letterSpacing: -3,
              color: i === 2 ? C.orange : C.ink,
              transform: `scale(${p}) translateY(${(1 - p) * 40}px)`,
              opacity: Math.min(1, p * 2),
            }}
          >
            {line}
          </div>
        );
      })}
    </Stage>
  );
};

// ─── Bars 3–4½: the library, with its colored tags ─────────────────────────────────────────────

const TAGS: [string, string][] = [
  ["Rock", "#EF7A75"],
  ["Pop", "#CE93D8"],
  ["Folk", "#DCE775"],
  ["Blues", "#7BB8F5"],
  ["Soul", "#81C995"],
  ["Disco", "#F48FB1"],
];
const TAG_SPOTS: [number, number, number][] = [
  [170, 760, -6],
  [915, 860, 5],
  [150, 1150, 4],
  [930, 1290, -5],
  [190, 1520, -3],
  [890, 1640, 6],
];

const Songs: React.FC = () => (
  <AbsoluteFill>
    <Headline top={230} at={0} lines={["Your whole songbook,", <Accent key="a">beautifully organized.</Accent>]} size={84} exitAt={1.5 * BAR - 14} />
    <Stage band={[590, 1830]}>
      <Place y={1210} at={2}>
        <Phone height={1180}>
          <Clip name="songs-scroll" />
        </Phone>
      </Place>
      {/* One a beat from the third, as many as the scene has beats for. */}
      {TAGS.slice(0, 4).map(([text, color], i) => (
        <Chip key={text} at={beat(2 + i)} x={TAG_SPOTS[i][0]} y={TAG_SPOTS[i][1]} rotate={TAG_SPOTS[i][2]} color={color} text={text} />
      ))}
    </Stage>
  </AbsoluteFill>
);

// ─── Bars 4½–6½: a song opened on the iPad, its page turned on the riser ──────────────────────

const IPAD_ASPECT = 2752 / 2064;

/** A chip's spring takes about five frames to be mostly there, so a chip meant to land on [n] starts that early. */
const onBeat = (n: number) => n * BEAT - 5;

// The clip starts with the song open and turns to its second page on its 90th frame, bar 5's third beat; the page stays
// up, with its chip, until the scene leaves.
const Song: React.FC = () => (
  <AbsoluteFill>
    <Stage band={[590, 1830]}>
      <Place y={1150} at={-4} enter="zoom">
        <Slate width={980} aspect={IPAD_ASPECT}>
          <Clip name="open-song" />
        </Slate>
      </Place>
      <Chip at={onBeat(2)} x={530} y={1650} color={C.orange} icon="♪" text="Every chord, with diagrams" size={46} rotate={-2} />
      <Chip at={onBeat(4)} x={760} y={735} color={C.lilac} icon="⇅" text="Pedal & tap to turn pages" size={44} rotate={4} />
    </Stage>
    <Headline top={230} at={0} lines={["Lyrics and chords,", <Accent key="a">fit to any screen.</Accent>]} size={84} exitAt={2 * BAR - 14} />
  </AbsoluteFill>
);

// ─── Bars 6½–7: a setlist rearranged by a drag ────────────────────────────────────────────────

const Setlist: React.FC = () => (
  <AbsoluteFill>
    <Stage band={[590, 1830]}>
      {/* The clip lifts Ring of Fire by its grip on its 20th frame and drops it in second place on its 113th, the sixth
          beat, inside the soundtrack's tom fill. */}
      <Place y={1215} at={-6} rotate={-3} enter="right">
        <Phone height={1180}>
          <Clip name="setlist-reorder" />
        </Phone>
      </Place>
      <Chip at={onBeat(1)} x={850} y={1500} color={C.lilac} icon="⇅" text="Drag to reorder" size={44} rotate={5} />
      <Chip at={onBeat(2)} x={225} y={645} color={C.orange} icon="⏳" text="Countdown" size={44} rotate={-5} />
    </Stage>
    <Headline top={230} at={0} lines={["Setlists,", <Accent key="a">in the order you play.</Accent>]} size={84} exitAt={1.5 * BAR - 14} />
  </AbsoluteFill>
);

// ─── Bars 8–9: a song transposed and then renotated live, a change a beat ─────────────────────

/**
 * What the song's first chord is on each of the scene's eight beats, and what the badge says under it: the soundtrack
 * plays A, A♯, B, and C, then C♯ four times, and the clip taps + on the second to fifth beats and switches the
 * notation on the sixth to eighth.
 */
const STEPS: [string, string][] = [
  ["A", "key"],
  ["A♯", "+1"],
  ["B", "+2"],
  ["C", "+3"],
  ["C♯", "+4"],
  ["Do♯", "Latin"],
  ["I", "Roman"],
  ["1", "Nashville"],
];

const Transpose: React.FC = () => {
  const frame = useCurrentFrame();
  const index = Math.min(7, Math.floor(frame / BEAT));
  const kick = pulse(frame, beat(index), 16);
  const p = spring({ frame: frame - beat(index) + 3, fps: 60, config: { damping: 10, stiffness: 220, mass: 0.5 } });
  // The phone is drawn 1.4 times larger about the point 30% down its screen, so the stepper, the chord diagrams and
  // the first verse fill the frame; the rest of it runs off the bottom.
  const originY = 1215 - 590 + 0.3 * 1180;
  const [chord, label] = STEPS[index];
  return (
    <AbsoluteFill>
      <Stage band={[440, 1890]}>
        <div style={{ position: "absolute", inset: 0, transform: "scale(1.4)", transformOrigin: `540px ${originY}px` }}>
          <Place y={1215} at={-6} from={900} rotate={2} enter="left">
            <Phone height={1180}>
              <Clip name="transpose" />
            </Phone>
          </Place>
        </div>
        <div
          style={{
            position: "absolute",
            left: 820,
            top: 1530,
            transform: `translate(-50%, -50%) rotate(-4deg) scale(${(0.7 + 0.3 * p) * (1 + 0.06 * kick)})`,
            opacity: Math.min(1, p * 2),
            display: "flex",
            alignItems: "baseline",
            gap: 22,
            padding: "18px 44px",
            borderRadius: 999,
            background: "rgba(33,30,41,0.94)",
            border: `2px solid ${C.lilac}55`,
            fontFamily: SANS,
            fontWeight: 500,
            whiteSpace: "nowrap",
          }}
        >
          <span style={{ fontFamily: MONO, fontWeight: 700, fontSize: 120, color: C.lilac, letterSpacing: -4 }}>{chord}</span>
          <span style={{ fontSize: 52, color: C.lilac }}>{label}</span>
        </div>
      </Stage>
      <Headline top={230} at={0} step={BEAT / 2} lines={["Transpose in a tap.", <Accent key="a">Every chord follows.</Accent>]} size={84} exitAt={BAR - 14} />
      <Headline top={230} at={BAR} step={BEAT / 2} lines={["Read chords", <Accent key="a">your way.</Accent>]} size={84} exitAt={2 * BAR - 14} />
    </AbsoluteFill>
  );
};

// ─── Bars 10–11: the metronome, playing the soundtrack's own tempo ────────────────────────────

const Metronome: React.FC = () => {
  const frame = useCurrentFrame();
  const n = Math.floor(frame / BEAT);
  const kick = pulse(frame, beat(n), 16);
  const downbeat = n % 4 === 0;
  return (
    <AbsoluteFill>
      <Stage band={[520, 1890]}>
      <div
        style={{
          position: "absolute",
          top: 650,
          left: 0,
          right: 0,
          textAlign: "center",
          fontFamily: SANS,
          fontWeight: 700,
          fontSize: 230,
          letterSpacing: -8,
          color: "transparent",
          WebkitTextStroke: `4px ${downbeat ? C.orange : C.lilac}`,
          opacity: 0.25 + 0.5 * kick,
          transform: `translateY(-50%) scale(${1 + 0.06 * kick})`,
        }}
      >
        160 BPM
      </div>
      <Place y={1360} at={-6} rotate={-3}>
        <div style={{ transform: `scale(${1 + 0.012 * kick})` }}>
          <Phone height={1000}>
            <Clip name="metronome" />
          </Phone>
        </div>
      </Place>
      <Chip at={onBeat(3)} x={190} y={1720} color={C.orange} icon="♩" text="Tap tempo" size={40} rotate={-4} />
      <Chip at={onBeat(5)} x={880} y={1600} color={C.lilac} icon="◔" text="Count-ins" size={40} rotate={4} />
      </Stage>
      <Headline top={230} at={0} lines={["A metronome that", <Accent key="a">knows every song.</Accent>]} size={84} exitAt={2 * BAR - 14} />
    </AbsoluteFill>
  );
};

// ─── Bars 12–13: the tuner, until its string is in tune ───────────────────────────────────────

const Tuner: React.FC = () => {
  const frame = useCurrentFrame();
  // The A string closes into tune around the clip's 90th frame, on the second bar's downbeat.
  const inTune = pulse(frame, BAR, 40);
  return (
    <AbsoluteFill>
      <Stage band={[580, 1880]}>
      <div
        style={{
          position: "absolute",
          left: 540,
          top: 820,
          width: 900,
          height: 900,
          transform: `translate(-50%, -50%) scale(${0.4 + (1 - inTune) * 1.2})`,
          borderRadius: 999,
          border: `8px solid ${C.orange}`,
          opacity: inTune,
        }}
      />
      <Place y={1280} at={-6} from={1300} enter="top">
        <Phone height={1180}>
          <Clip name="tuner" />
        </Phone>
      </Place>
      <Chip at={onBeat(1)} x={205} y={1010} color={C.lilac} icon="♪" text="Instrument tuner" size={38} rotate={-5} />
      <Chip at={onBeat(2)} x={880} y={1260} color={C.orange} icon="◎" text="Chromatic tuner" size={38} rotate={4} />
      <Chip at={onBeat(3)} x={210} y={1660} color="#81C995" icon="✓" text="Alternate tunings" size={38} rotate={-4} />
      </Stage>
      <Headline
        top={230}
        at={0}
        lines={["A tuner,", <Accent key="a">built right in.</Accent>]}
        size={84}
        exitAt={1.5 * BAR - 14}
      />
    </AbsoluteFill>
  );
};

// ─── Bars 13½–14: the Chords tab, from bar 13's third beat: every instrument, then a shape picked ─

// Every instrument the diagrams are drawn for (the twelve-string is drawn as a guitar), one a beat on alternate sides of the phone, the keyboard, the ukulele and
// the guitar as the clip switches to each; they stay until the scene leaves: text, x, y, rotation, frame.
const INSTRUMENTS: [string, number, number, number, number][] = [
  ["Keyboard", 185, 790, -6, 6],
  ["Mandolin", 915, 900, 5, 23],
  ["Ukulele", 170, 1120, 4, 45],
  ["Guitar", 920, 1250, -5, 68],
  ["Banjo", 190, 1460, -3, 90],
];

/** The clip opens the guitar's A on its 79th frame, half a beat before bar 14's third, and picks another shape on its 113th. */
const SHEET_AT = 79;

const Chords: React.FC = () => (
  <AbsoluteFill>
    <Stage band={[590, 1830]}>
      <Place y={1215} at={-6} enter="zoom">
        <Phone height={1180}>
          <Clip name="chords" />
        </Phone>
      </Place>
      {INSTRUMENTS.map(([text, x, y, rotate, at], i) => (
        <Chip key={text} at={at - 4} x={x} y={y} rotate={rotate} color={i % 2 ? C.orange : C.lilac} text={text} size={44} />
      ))}
    </Stage>
    <Headline top={230} at={0} step={BEAT / 2} lines={["Every chord,", <Accent key="a">for every instrument.</Accent>]} size={84} exitAt={SHEET_AT - 14} />
    <Headline top={230} at={SHEET_AT} step={BEAT / 2} lines={["Your favorite shape,", <Accent key="a">in every song.</Accent>]} size={84} exitAt={1.5 * BAR - 14} />
  </AbsoluteFill>
);

// ─── Bar 15, its first three beats: a PDF for the music stand, over the riser into the light theme ──

const Export: React.FC = () => {
  const frame = useCurrentFrame();
  // A slow push in that rises with the riser.
  const push = ease(frame, [0, S.export.bars * BAR], [1, 1.06], Easing.in(Easing.quad));
  return (
    <AbsoluteFill>
      <Stage band={[590, 1830]}>
        <Place y={1215} at={-6} rotate={3} enter="left">
          <div style={{ transform: `scale(${push})` }}>
            <Phone height={1180}>
              <Clip name="export" />
            </Phone>
          </div>
        </Place>
        <Chip at={onBeat(1)} x={210} y={900} color={C.orange} icon="☰" text="Whole setlists" size={44} rotate={-5} />
        <Chip at={onBeat(2)} x={870} y={1560} color={C.lilac} icon="♪" text="Individual songs" size={44} rotate={4} />
      </Stage>
      <Headline top={230} at={0} step={BEAT / 2} lines={["Print-ready PDFs,", <Accent key="a">for the music stand.</Accent>]} size={84} exitAt={S.export.bars * BAR - 14} />
    </AbsoluteFill>
  );
};

// ─── Bars 16–17: every platform, and the light theme ──────────────────────────────────────────

// One thing a beat from bar 16's downbeat, where the soundtrack changes to E: the laptop comes in, the light theme sweeps
// across it with the whoosh, opening fastest at first and covering the screen over a beat and a half, then the iPad,
// then the phone.
const DOWNBEAT = 0;
const LIGHT_FROM = DOWNBEAT + BEAT;
const LIGHT_TO = LIGHT_FROM + 1.6 * BEAT;

const LAPTOP_ASPECT = 2000 / 1255;

const PLATFORMS = ["Android", "iOS", "iPadOS", "macOS", "Windows", "Linux", "Web"];

const Everywhere: React.FC = () => {
  const frame = useCurrentFrame();
  const t = ease(frame, [LIGHT_FROM, LIGHT_TO], [0, 1], Easing.out(Easing.cubic));
  const ink = t > 0.5 ? C.inkLight : C.ink;
  return (
    <AbsoluteFill>
      <Stage band={[450, 1850]}>
      {/* The laptop lands dark on bar 16's downbeat and takes the light theme on its second beat; the iPad, upright and
          near its real size beside it, and the phone land over its lower edge on the third and fourth, the two of them
          centred under it. */}
      <Place x={540} y={760} at={-4} enter="top">
        <Slate width={860} aspect={LAPTOP_ASPECT} laptop light={t > 0.5}>
          <Still name="laptop-song-dark" />
          <CircleReveal from={LIGHT_FROM} to={LIGHT_TO + 6} x={430} y={270} easing={Easing.out(Easing.cubic)}>
            <Still name="laptop-song-light" />
          </CircleReveal>
        </Slate>
      </Place>
      <Place x={414} y={1250} at={DOWNBEAT + beat(2)} rotate={-4} enter="left">
        <Slate width={520} aspect={2064 / 2752} light>
          <Still name="ipad-portrait-song-light" />
        </Slate>
      </Place>
      <Place x={804} y={1270} at={DOWNBEAT + beat(3)} rotate={5} enter="right">
        <Phone height={580} light>
          <Still name="android-songs-light" />
        </Phone>
      </Place>
      <div style={{ position: "absolute", top: 1760, left: 20, right: 20, display: "flex", justifyContent: "center", gap: 22 }}>
        {PLATFORMS.map((name, i) => {
          const p = spring({ frame: frame - DOWNBEAT - beat(4) - i * 4, fps: 60, config: { damping: 12, stiffness: 180, mass: 0.6 } });
          return (
            <span key={name} style={{ fontFamily: SANS, fontWeight: 500, fontSize: 40, color: i % 2 ? C.purple : "#C25E00", opacity: p, transform: `translateY(${(1 - p) * 30}px)`, display: "inline-block" }}>
              {name}
            </span>
          );
        })}
      </div>
      </Stage>
      <Headline top={230} at={0} lines={["On every device", <Accent key="a" light={t > 0.5}>you own.</Accent>]} size={84} color={ink} exitAt={S.everywhere.bars * BAR - 14} />
    </AbsoluteFill>
  );
};

// ─── Bar 18: four promises, one a beat ────────────────────────────────────────────────────────

const Values: React.FC = () => {
  const frame = useCurrentFrame();
  const words = ["Free.", "No ads.", "No account.", "Offline."];
  return (
    <Stage band={[830, 1090]} full>
    <AbsoluteFill style={{ alignItems: "center", justifyContent: "center" }}>
      {words.map((word, i) => {
        const at = beat(i);
        const p = spring({ frame: frame - at, fps: 60, config: { damping: 9, stiffness: 240, mass: 0.5 } });
        const out = i < 3 ? ease(frame, [beat(i + 1) - 3, beat(i + 1) + 3], [0, 1]) : 0;
        return (
          <div
            key={word}
            style={{
              position: "absolute",
              fontFamily: SANS,
              fontWeight: 700,
              fontSize: 170,
              letterSpacing: -6,
              color: i % 2 ? C.orange : C.ink,
              transform: `scale(${(0.5 + 0.5 * p) * (1 + out * 0.5)})`,
              opacity: frame >= at ? (1 - out) : 0,
              filter: out ? `blur(${out * 10}px)` : undefined,
            }}
          >
            {word}
          </div>
        );
      })}
    </AbsoluteFill>
    </Stage>
  );
};

// ─── Bars 19–20: the icon in every color, the name and the address ────────────────────────────

const ICONS = ["orange", "blue", "green", "pink", "teal", "purple", "red", "yellow"];

const Outro: React.FC = () => {
  const frame = useCurrentFrame();
  const enter = spring({ frame, fps: 60, config: { damping: 10, stiffness: 130, mass: 0.7 } });
  // Half a beat per color for the first bar, then the icon settles on its own gradient.
  const step = Math.floor(frame / (BEAT / 2));
  const icon = step < ICONS.length ? `app_icon_${ICONS[step]}.png` : "app_icon.png";
  const kick = step <= ICONS.length ? pulse(frame, step * (BEAT / 2), 10) : 0;
  const name = spring({ frame: frame - BAR, fps: 60, config: { damping: 13, stiffness: 150, mass: 0.7 } });
  const url = spring({ frame: frame - BAR - beat(1), fps: 60, config: { damping: 13, stiffness: 150, mass: 0.7 } });
  const sub = spring({ frame: frame - BAR - beat(2), fps: 60, config: { damping: 13, stiffness: 150, mass: 0.7 } });
  return (
    <Stage band={[530, 1410]} full>
      <Img
        src={staticFile(`icons/${icon}`)}
        style={{
          position: "absolute",
          left: 540,
          top: 720,
          width: 340,
          height: 340,
          transform: `translate(-50%, -50%) scale(${enter * (1 + 0.07 * kick)})`,
        }}
      />
      <div style={{ position: "absolute", top: 950, left: 0, right: 0, textAlign: "center", fontFamily: SANS, fontWeight: 700, fontSize: 150, letterSpacing: -5, color: C.ink, opacity: name, transform: `translateY(${(1 - name) * 50}px)` }}>
        Campfire
      </div>
      <div style={{ position: "absolute", top: 1150, left: 0, right: 0, textAlign: "center", fontFamily: SANS, fontWeight: 400, fontSize: 50, color: C.muted, opacity: sub, transform: `translateY(${(1 - sub) * 30}px)` }}>
        Free and open source songbook
      </div>
      <div style={{ position: "absolute", top: 1290, left: 0, right: 0, display: "flex", justifyContent: "center" }}>
        <div
          style={{
            padding: "26px 54px",
            fontFamily: SANS,
            fontWeight: 700,
            fontSize: 56,
            color: C.lilac,
            transform: `scale(${url})`,
          }}
        >
          campfire-songbook.com
        </div>
      </div>
    </Stage>
  );
};
