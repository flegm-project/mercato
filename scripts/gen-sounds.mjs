// Generate the game's sound effects, from a description rather than from files.
//
// The app ships no recorded audio. Three cues is all the game needs, they are
// a few hundred milliseconds each, and synthesising them keeps them in the
// repo as the twenty lines that describe them instead of as binaries nobody
// can diff or adjust. Changing the wrong-answer note is a number here, not a
// trip through an audio editor and a new blob in git.
//
// The timbre is a band-limited pulse: the odd-heavy harmonic series of a
// square, summed as sines and stopped below Nyquist so nothing aliases. It is
// the audible equivalent of the app's flat fills and hard ink borders, where a
// sampled orchestral sting would not be.
//
// Run: node scripts/gen-sounds.mjs
// Emits: build/sounds/{correct,wrong,roundover}.wav
//        16-bit PCM, mono, 44.1kHz, which both AVAudioPlayer and SoundPool
//        play without a decoder.

import fs from "fs";
import path from "path";

const ROOT = new URL("..", import.meta.url).pathname;
const OUT = path.join(ROOT, "build/sounds");

const RATE = 44100;
const PEAK = 0.72; // headroom, so the mix never clips on a phone speaker

// Equal temperament from A4. Notes are written as names so the intervals stay
// readable: a fifth up is a fifth up, not 1.4983.
const SEMITONE = { C: -9, D: -7, E: -5, F: -4, G: -2, A: 0, B: 2 };
function freq(name) {
  const m = /^([A-G])(#|b)?(\d)$/.exec(name);
  if (!m) throw new Error(`bad note ${name}`);
  const semis = SEMITONE[m[1]] + (m[2] === "#" ? 1 : m[2] === "b" ? -1 : 0)
    + (Number(m[3]) - 4) * 12;
  return 440 * Math.pow(2, semis / 12);
}

/**
 * One band-limited pulse partial set. `duty` shapes the harmonic weights: 0.5
 * is a square (odd harmonics only), lower values thin it out and add the even
 * ones, which is what gives the wrong-answer cue its harder edge.
 *
 * `cut` is a one-pole tilt over the harmonics, in Hz. Without it a bare square
 * at 880 keeps every partial up to Nyquist at full weight and comes out of a
 * phone speaker as a shriek; rolling off above a couple of kHz leaves it
 * unmistakably square without the ice pick.
 */
function pulse(phase, duty, f, cut) {
  let sum = 0;
  for (let h = 1; h <= 40; h++) {
    const hz = h * f;
    if (hz > RATE / 2) break; // never write a partial that would alias
    const amp = Math.sin(Math.PI * h * duty) / (Math.PI * h);
    const tilt = 1 / Math.sqrt(1 + (hz / cut) ** 2);
    sum += amp * tilt * Math.cos(2 * Math.PI * h * phase);
  }
  return sum * 2;
}

/** Attack, decay to a sustain level, then release. All times in seconds. */
function envelope(t, dur, { a = 0.006, d = 0.05, s = 0.7, r = 0.08 }) {
  if (t < a) return t / a;
  if (t < a + d) return 1 - (1 - s) * ((t - a) / d);
  if (t < dur - r) return s;
  if (t < dur) return s * (dur - t) / r;
  return 0;
}

/**
 * A cue is a list of notes, each with a start, a length and a pitch. They are
 * summed rather than concatenated so the tail of one rings under the next,
 * which is what stops a two-note answer cue sounding like two separate beeps.
 */
function render(notes, { duty = 0.5, glide = 0, cut = 2600, env = {} } = {}) {
  const end = Math.max(...notes.map((n) => n.at + n.dur)) + 0.05;
  const buf = new Float64Array(Math.ceil(end * RATE));
  for (const n of notes) {
    const f0 = freq(n.note);
    const f1 = n.to ? freq(n.to) : f0 * Math.pow(2, glide / 12);
    let phase = 0;
    const start = Math.round(n.at * RATE);
    const len = Math.round(n.dur * RATE);
    for (let i = 0; i < len; i++) {
      const t = i / RATE;
      const k = i / len;
      const f = f0 + (f1 - f0) * k;
      phase += f / RATE;
      const e = envelope(t, n.dur, { ...env, ...(n.env || {}) });
      buf[start + i] += pulse(phase, n.duty ?? duty, f, n.cut ?? cut) * e * (n.gain ?? 1);
    }
  }
  return buf;
}

/** Normalise to PEAK and write a mono 16-bit WAV. */
function writeWav(file, samples) {
  let max = 0;
  for (const v of samples) max = Math.max(max, Math.abs(v));
  const scale = max > 0 ? PEAK / max : 0;

  const n = samples.length;
  const data = Buffer.alloc(n * 2);
  for (let i = 0; i < n; i++) {
    // A short fade at both ends: a waveform that starts or stops off zero
    // clicks through a phone speaker louder than the cue itself.
    const fade = Math.min(1, i / 64, (n - 1 - i) / 256);
    const v = Math.max(-1, Math.min(1, samples[i] * scale * fade));
    data.writeInt16LE(Math.round(v * 32767), i * 2);
  }

  const header = Buffer.alloc(44);
  header.write("RIFF", 0);
  header.writeUInt32LE(36 + data.length, 4);
  header.write("WAVE", 8);
  header.write("fmt ", 12);
  header.writeUInt32LE(16, 16);
  header.writeUInt16LE(1, 20); // PCM
  header.writeUInt16LE(1, 22); // mono
  header.writeUInt32LE(RATE, 24);
  header.writeUInt32LE(RATE * 2, 28);
  header.writeUInt16LE(2, 32);
  header.writeUInt16LE(16, 34);
  header.write("data", 36);
  header.writeUInt32LE(data.length, 40);

  fs.writeFileSync(file, Buffer.concat([header, data]));
  return 44 + data.length;
}

// --- The three cues -------------------------------------------------------

const CUES = {
  // Right answer: a rising fourth, the second note held. Up means yes in
  // every game ever made, and the interval is consonant enough to hear ten
  // times a round without wearing out.
  correct: () =>
    render(
      [
        { note: "E5", at: 0, dur: 0.09, gain: 0.85 },
        { note: "A5", at: 0.07, dur: 0.30, env: { d: 0.09, s: 0.55, r: 0.2 } },
      ],
      { duty: 0.5, env: { a: 0.004, d: 0.05, s: 0.7, r: 0.06 } },
    ),

  // Wrong answer: the same shape inverted and dropped two octaves, with the
  // duty pushed off square so it buzzes. Short, because a long negative cue
  // is a punishment and this game hands out nine more questions.
  wrong: () =>
    render(
      [
        { note: "A3", at: 0, dur: 0.10, gain: 0.9 },
        { note: "D#3", at: 0.08, dur: 0.26, to: "D3", env: { d: 0.1, s: 0.5, r: 0.14 } },
      ],
      { duty: 0.28, cut: 1400, env: { a: 0.003, d: 0.04, s: 0.75, r: 0.06 } },
    ),

  // End of round: a major arpeggio to the octave, the last note twice as long.
  // It plays once per round, so it can afford to be the only cue that sounds
  // like a small event rather than a reply.
  roundover: () =>
    render(
      [
        { note: "A4", at: 0.00, dur: 0.10 },
        { note: "C#5", at: 0.09, dur: 0.10 },
        { note: "E5", at: 0.18, dur: 0.10 },
        { note: "A5", at: 0.27, dur: 0.46, env: { d: 0.14, s: 0.5, r: 0.3 } },
      ],
      { duty: 0.5, env: { a: 0.004, d: 0.04, s: 0.72, r: 0.05 } },
    ),
};

fs.mkdirSync(OUT, { recursive: true });
for (const [name, make] of Object.entries(CUES)) {
  const file = path.join(OUT, `${name}.wav`);
  const bytes = writeWav(file, make());
  console.log(`  ${name}.wav  ${(bytes / 1024).toFixed(1)} kB`);
}
console.log(`sounds written to build/sounds`);
