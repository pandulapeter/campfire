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
import { AbsoluteFill, Easing, Img, interpolate, OffthreadVideo, spring, staticFile, useCurrentFrame, useVideoConfig } from "remotion";

// 160 BPM at 60 fps: a beat is 22.5 frames, a bar of 4/4 is 90.
export const BEAT = 22.5;
export const BAR = 90;
export const beat = (n: number) => Math.round(n * BEAT);

export const C = {
  bg: "#15121C",
  ink: "#E3E1EA",
  lilac: "#D0BCFF",
  orange: "#FE851E",
  white: "#FFFFFF",
  // The lines under the name and the subtitle, a step back from the white of everything else.
  softWhite: "rgba(255,255,255,0.8)",
};

/** The store listings' backdrop: the app's purple into its icon's orange. */
export const GRADIENT = "linear-gradient(135deg, #5A49CA, #F57C00)";

export const fontFaces = `
@font-face { font-family: Inter; font-weight: 400; src: url(${staticFile("fonts/inter_regular.ttf")}) format("truetype"); }
@font-face { font-family: Inter; font-weight: 500; src: url(${staticFile("fonts/inter_medium.ttf")}) format("truetype"); }
@font-face { font-family: Inter; font-weight: 700; src: url(${staticFile("fonts/inter_bold.ttf")}) format("truetype"); }
@font-face { font-family: JBM; font-weight: 700; src: url(${staticFile("fonts/jetbrains_mono_bold.ttf")}) format("truetype"); }
`;

// No fallback: a glyph the bundled fonts lack would be drawn in a system font, so the chips draw icons instead of symbols.
export const SANS = "Inter";
export const MONO = "JBM";

/** A spring that starts at [from] frames, 0 → 1. */
export const useSpring = (from: number, config: { damping?: number; stiffness?: number; mass?: number } = {}) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  return spring({ frame: frame - from, fps, config: { damping: 14, stiffness: 140, mass: 0.8, ...config } });
};

/** A punch that peaks on [at] and decays: for things that pulse on the beat. */
export const pulse = (frame: number, at: number, length = 18) => {
  const t = frame - at;
  if (t < 0 || t > length) return 0;
  return Math.pow(1 - t / length, 2.2);
};

export const ease = (frame: number, range: [number, number], out: [number, number], easing = Easing.bezier(0.22, 1, 0.36, 1)) =>
  interpolate(frame, range, out, { extrapolateLeft: "clamp", extrapolateRight: "clamp", easing });

/** The backdrop of every scene, the light theme's included: static, the same in every format. */
export const Backdrop: React.FC = () => <AbsoluteFill style={{ background: GRADIENT }} />;

/** A generic phone frame, with no camera notch or island, around a screen of the given aspect ratio, sized by its [height]. */
export const Phone: React.FC<{ height: number; children: React.ReactNode; light?: boolean }> = ({ height, children, light }) => {
  const aspect = 1320 / 2868;
  const bezel = height * 0.018;
  const width = height * aspect;
  return (
    <div
      style={{
        width: width + bezel * 2,
        height: height + bezel * 2,
        padding: bezel,
        borderRadius: height * 0.075,
        background: light ? "#D9D5E2" : "#0A090D",
        boxShadow: `0 0 0 3px ${light ? "#BDB7C9" : "#2E2A38"}`,
        position: "relative",
      }}
    >
      <div style={{ width, height, borderRadius: height * 0.062, overflow: "hidden", position: "relative", background: "#000" }}>
        {children}
      </div>
    </div>
  );
};

/** A tablet or laptop frame around a landscape screen. */
export const Slate: React.FC<{ width: number; aspect: number; children: React.ReactNode; laptop?: boolean; light?: boolean }> = ({
  width,
  aspect,
  children,
  laptop,
  light,
}) => {
  const bezel = width * (laptop ? 0.022 : 0.03);
  const height = width / aspect;
  return (
    <div style={{ position: "relative" }}>
      <div
        style={{
          width: width + bezel * 2,
          height: height + bezel * 2,
          padding: bezel,
          borderRadius: laptop ? `${width * 0.03}px ${width * 0.03}px ${width * 0.008}px ${width * 0.008}px` : width * 0.045,
          background: light ? "#D9D5E2" : "#0A090D",
          boxShadow: `0 0 0 3px ${light ? "#BDB7C9" : "#2E2A38"}`,
        }}
      >
        <div style={{ width, height, borderRadius: laptop ? width * 0.006 : width * 0.022, overflow: "hidden", position: "relative" }}>
          {children}
        </div>
      </div>
      {laptop && (
        <div
          style={{
            width: (width + bezel * 2) * 1.12,
            height: width * 0.035,
            marginLeft: -(width + bezel * 2) * 0.06,
            background: "linear-gradient(#C9C5D3, #8E899B)",
            borderRadius: `0 0 ${width * 0.03}px ${width * 0.03}px`,
          }}
        />
      )}
    </div>
  );
};

export const Clip: React.FC<{ name: string; startFrom?: number }> = ({ name, startFrom = 0 }) => (
  <OffthreadVideo src={staticFile(`clips/${name}.mp4`)} startFrom={startFrom} muted style={{ width: "100%", height: "100%", objectFit: "cover" }} />
);

export const Still: React.FC<{ name: string; style?: React.CSSProperties }> = ({ name, style }) => (
  <Img src={staticFile(`stills/${name}.jpg`)} style={{ width: "100%", height: "100%", objectFit: "cover", ...style }} />
);

/** A headline whose lines rise into place one after another, each masked by its own line box. */
export type Format = "portrait" | "square" | "landscape";

export const useFormat = (): Format => {
  const { width, height } = useVideoConfig();
  return width < height ? "portrait" : width === height ? "square" : "landscape";
};

/**
 * The scenes are laid out once, in the portrait frame's 1080 × 1920 coordinates. In the other formats the part of a
 * scene between [band]'s two heights is scaled into a region of the frame: the whole frame for a scene that is all
 * type, and for a scene with devices the space the headline leaves, below it in a square and beside it in landscape.
 */
export const Stage: React.FC<{ band: [number, number]; full?: boolean; children: React.ReactNode }> = ({ band, full, children }) => {
  const format = useFormat();
  const { width, height } = useVideoConfig();
  if (format === "portrait") return <AbsoluteFill>{children}</AbsoluteFill>;
  const { x, y, scale } = stageTransform(format, width, height, band, full);
  return (
    <div style={{ position: "absolute", left: 0, top: 0, width: 1080, height: 1920, transformOrigin: "0 0", transform: `translate(${x}px, ${y}px) scale(${scale})` }}>
      {children}
    </div>
  );
};

/** Where [Stage] puts its portrait coordinates in a square or landscape frame: a scale, and the offset after it. */
const stageTransform = (format: Format, width: number, height: number, band: [number, number], full?: boolean) => {
  const region = full
    ? { x: 0, y: 0, w: width, h: height }
    : format === "square"
      ? { x: 0, y: 250, w: 1080, h: 815 }
      : { x: 910, y: 20, w: 990, h: 1040 };
  const [y0, y1] = band;
  const scale = Math.min(region.w / 1080, region.h / (y1 - y0), 1.25);
  return {
    scale,
    x: region.x + (region.w - 1080 * scale) / 2,
    y: region.y + (region.h - (y1 - y0) * scale) / 2 - y0 * scale,
  };
};

/**
 * A headline whose lines rise a short way into place one after another, fading in as they move, and drift up as they
 * fade out on the way out, never cut off by a mask. In the portrait and square frames it is centred in the gap between
 * the frame's top edge and the top of the scene's highest device, [deviceTop] in the [band] of the scene's [Stage]
 * (in portrait, with [deviceBottom], between the device's bottom and the frame's bottom edge instead); in a landscape
 * frame it has a column of its own on the left, where a line too long for the column wraps. It is set as the store
 * listings' headlines are: Inter Regular in white, letters 5% of the size closer, lines 0.9 apart.
 */
export const Headline: React.FC<{
  lines: React.ReactNode[];
  at: number;
  step?: number;
  band: [number, number];
  deviceTop: number;
  deviceBottom?: number;
  size?: number;
  exitAt?: number;
}> = ({ lines, at, step = BEAT, band, deviceTop, deviceBottom, size = 92, exitAt }) => {
  const frame = useCurrentFrame();
  const format = useFormat();
  const { width, height } = useVideoConfig();
  const exit = exitAt === undefined ? 0 : ease(frame, [exitAt, exitAt + 14], [0, 1], Easing.in(Easing.cubic));
  const isLandscape = format === "landscape";
  const fontSize = format === "portrait" ? size : isLandscape ? size * 0.98 : size * 0.76;
  const center =
    format === "portrait"
      ? deviceBottom === undefined
        ? deviceTop / 2
        : (deviceBottom + height) / 2
      : (() => {
          const stage = stageTransform(format, width, height, band);
          return (stage.y + deviceTop * stage.scale) / 2;
        })();
  const box: React.CSSProperties = isLandscape
    ? { position: "absolute", left: 100, width: 800, top: 0, bottom: 0, display: "flex", flexDirection: "column", justifyContent: "center", alignItems: "flex-start", textAlign: "left" }
    : { position: "absolute", top: center, left: 0, right: 0, transform: "translateY(-50%)", display: "flex", flexDirection: "column", alignItems: "center", textAlign: "center" };
  return (
    <div style={{ ...box, fontFamily: SANS, fontWeight: 400 }}>
      {lines.map((line, i) => {
        const p = spring({ frame: frame - at - i * step, fps: 60, config: { damping: 16, stiffness: 150, mass: 0.7 } });
        return (
          <div key={i} style={{ padding: isLandscape ? 0 : "0 40px", lineHeight: 0.9, alignSelf: "stretch" }}>
            <div
              style={{
                fontSize,
                letterSpacing: -fontSize * 0.05,
                textWrap: "balance",
                color: C.white,
                transform: `translateY(${(1 - p) * 0.55 * fontSize - exit * 0.35 * fontSize}px)`,
                opacity: Math.min(1, p * 1.3) * (1 - exit),
              }}
            >
              {line}
            </div>
          </div>
        );
      })}
    </div>
  );
};

/**
 * One of the app's own icons (`public/icons/<name>.svg`, converted from its vector drawables), painted in [color]
 * through a mask, since an SVG drawn as an image keeps its own fill.
 */
export const Icon: React.FC<{ name: string; size: number; color: string }> = ({ name, size, color }) => {
  const mask = `url(${staticFile(`icons/${name}.svg`)}) center / contain no-repeat`;
  return <span style={{ width: size, height: size, flexShrink: 0, background: color, mask, WebkitMask: mask }} />;
};

/** A rounded label that springs in at [at], led by one of the app's icons ([icon], see [Icon]) or a dot of its color. */
export const Chip: React.FC<{
  at: number;
  x: number;
  y: number;
  color: string;
  text: string;
  icon?: string;
  size?: number;
  rotate?: number;
  exitAt?: number;
}> = ({ at, x, y, color, text, icon, size = 40, rotate = 0, exitAt }) => {
  const frame = useCurrentFrame();
  const p = spring({ frame: frame - at, fps: 60, config: { damping: 11, stiffness: 180, mass: 0.6 } });
  const exit = exitAt === undefined ? 0 : ease(frame, [exitAt, exitAt + 12], [0, 1], Easing.in(Easing.cubic));
  const float = Math.sin((frame - at) / 26 + x) * 8;
  return (
    <div
      style={{
        position: "absolute",
        left: x,
        top: y + float,
        transform: `translate(-50%, -50%) scale(${p * (1 - exit)}) rotate(${rotate * p}deg)`,
        display: "flex",
        alignItems: "center",
        gap: size * 0.35,
        padding: `${size * 0.32}px ${size * 0.6}px`,
        borderRadius: 999,
        background: "rgba(33,30,41,0.92)",
        border: `2px solid ${color}55`,
        fontFamily: SANS,
        fontWeight: 500,
        fontSize: size,
        color: C.ink,
        whiteSpace: "nowrap",
      }}
    >
      {icon ? (
        <Icon name={icon} size={size * 1.05} color={color} />
      ) : (
        <span style={{ width: size * 0.5, height: size * 0.5, borderRadius: 999, background: color, }} />
      )}
      {text}
    </div>
  );
};

/** A circle grown from ([x], [y]) over the whole frame between [from] and [to], filled with [children]. */
export const CircleReveal: React.FC<{ from: number; to: number; x: number; y: number; easing?: (t: number) => number; children: React.ReactNode }> = ({
  from,
  to,
  x,
  y,
  easing = Easing.bezier(0.65, 0, 0.35, 1),
  children,
}) => {
  const frame = useCurrentFrame();
  const r = ease(frame, [from, to], [0, 2300], easing);
  return <AbsoluteFill style={{ clipPath: `circle(${r}px at ${x}px ${y}px)` }}>{children}</AbsoluteFill>;
};
