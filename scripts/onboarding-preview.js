// The browser half of the onboarding preview. Loaded verbatim into
// build/art/onboarding-preview.html by scripts/gen-onboarding-scenes.mjs,
// which hands it SPEC (design/onboarding.json) and C (the design tokens).
//
// Deliberately the same arithmetic, in the same order, as OnboardingArt.swift
// and OnboardingArt.kt, so what this shows is what the phones draw.

/** Cubic-bezier easing, solved for x then read for y, as CSS does it. */
function bezier(x1, y1, x2, y2, t) {
  const cx = 3 * x1, bx = 3 * (x2 - x1) - cx, ax = 1 - cx - bx;
  const cy = 3 * y1, by = 3 * (y2 - y1) - cy, ay = 1 - cy - by;
  let u = t;
  for (let i = 0; i < 8; i++) {
    const x = ((ax * u + bx) * u + cx) * u - t;
    const d = (3 * ax * u + 2 * bx) * u + cx;
    if (Math.abs(x) < 1e-5 || d === 0) break;
    u -= x / d;
  }
  u = Math.min(Math.max(u, 0), 1);
  return ((ay * u + by) * u + cy) * u;
}

/** The four curves the spec is allowed to name. */
function ease(name, t) {
  const c = Math.min(Math.max(t, 0), 1);
  if (name === "inout") return bezier(0.42, 0, 0.58, 1, c);
  if (name === "out") return bezier(0, 0, 0.58, 1, c);
  if (name === "back") return bezier(0.3, 1.3, 0.4, 1, c);
  return c;
}

const PROPS = ["dx", "dy", "dist", "scale", "rot", "opacity", "dash"];

/** The piece's state at `phase`, its own delay already taken off. */
function sample(piece, phase) {
  let t = (phase - (piece.delay || 0)) % 1;
  if (t < 0) t += 1;
  const keys = piece.track;
  let i = 0;
  while (i < keys.length - 2 && keys[i + 1].at <= t) i++;
  const a = keys[i], b = keys[i + 1] ?? a;
  const span = b.at - a.at;
  const k = span > 0 ? ease(b.ease || "linear", (t - a.at) / span) : 0;
  const out = {};
  for (const p of PROPS) {
    const av = a[p] ?? (p === "scale" || p === "opacity" ? 1 : 0);
    const bv = b[p] ?? (p === "scale" || p === "opacity" ? 1 : 0);
    out[p] = av + (bv - av) * k;
  }
  return out;
}

const S = SPEC.style;
const FACE = (size) => `900 ${size}px Unbounded, system-ui, sans-serif`;

/** The rounded rect the app draws everywhere, with per-corner radii. */
function rectPath(ctx, x, y, w, h, r) {
  ctx.beginPath();
  ctx.roundRect(x, y, w, h, r);
}

/**
 * The recap's star, so the intro's stars and the recap's stars are one shape.
 * `space` is the unit box the spec draws it in; the outline scales with it.
 */
function starPath(ctx, cx, cy, outer) {
  const WAIST = 0.392, TIP = 0.0665, VALLEY = 0.031;
  const pts = [...Array(10)].map((_, i) => {
    const r = i % 2 === 0 ? outer : outer * WAIST;
    const a = ((-90 + i * 36) * Math.PI) / 180;
    return [cx + r * Math.cos(a), cy + r * Math.sin(a)];
  });
  ctx.beginPath();
  for (let i = 0; i < 10; i++) {
    const p = pts[i], prev = pts[(i + 9) % 10], next = pts[(i + 1) % 10];
    const len = (a, b) => Math.hypot(b[0] - a[0], b[1] - a[1]);
    const unit = (a, b) => { const l = len(a, b); return [(b[0] - a[0]) / l, (b[1] - a[1]) / l]; };
    const u = unit(p, prev), v = unit(p, next);
    const half = Math.acos(Math.max(-1, Math.min(1, u[0] * v[0] + u[1] * v[1]))) / 2;
    const corner = outer * (i % 2 === 0 ? TIP : VALLEY);
    const trim = Math.min(corner / Math.tan(half), Math.min(len(p, prev), len(p, next)) / 2);
    const a = [p[0] + u[0] * trim, p[1] + u[1] * trim];
    const b = [p[0] + v[0] * trim, p[1] + v[1] * trim];
    const k = (4 / 3) * Math.tan((Math.PI - 2 * half) / 4) * Math.tan(half);
    if (i === 0) ctx.moveTo(a[0], a[1]); else ctx.lineTo(a[0], a[1]);
    ctx.bezierCurveTo(a[0] + (p[0] - a[0]) * k, a[1] + (p[1] - a[1]) * k,
                      b[0] + (p[0] - b[0]) * k, b[1] + (p[1] - b[1]) * k, b[0], b[1]);
  }
  ctx.closePath();
}

/** Draw one piece at one instant. Shadow, then face, then type. */
function drawPiece(ctx, p, st) {
  if (st.opacity <= 0.001 || st.scale <= 0.001) return;
  const a = ((p.angle || 0) * Math.PI) / 180;
  const cx = p.x + (p.w || 0) / 2 + st.dx + Math.cos(a) * st.dist;
  const cy = p.y + (p.h || 0) / 2 + st.dy + Math.sin(a) * st.dist;

  ctx.save();
  ctx.globalAlpha = st.opacity;
  ctx.translate(cx, cy);
  ctx.rotate((st.rot * Math.PI) / 180);
  ctx.scale(st.scale, st.scale);

  if (p.kind === "text") {
    ctx.font = FACE(p.size);
    ctx.textAlign = p.align === "left" ? "left" : "center";
    ctx.textBaseline = "middle";
    ctx.letterSpacing = `${(p.tracking || 0) * p.size}px`;
    const tx = p.align === "left" ? -(p.w || 0) / 2 : 0;
    ctx.fillStyle = C.ink;
    ctx.fillText(p.value, tx + S.depth, S.depth);
    ctx.fillStyle = C[p.fill];
    ctx.fillText(p.value, tx, 0);
    ctx.restore();
    return;
  }

  if (p.kind === "star") {
    // The outline is stated in the piece's own unit box, so it thickens with
    // the star rather than staying a hairline on the big one.
    const outer = p.w / 1.631;
    const line = (p.border * p.w) / p.space;
    starPath(ctx, 0, 0, outer);
    ctx.fillStyle = C[p.fill];
    ctx.fill();
    ctx.lineWidth = line;
    ctx.lineJoin = "round";
    ctx.strokeStyle = C[p.stroke];
    ctx.stroke();
    ctx.restore();
    return;
  }

  if (p.kind === "polyline") {
    const k = p.w / p.space;
    ctx.lineWidth = p.width * k;
    ctx.lineCap = "round";
    ctx.lineJoin = "round";
    ctx.strokeStyle = C[p.stroke];
    ctx.setLineDash([p.length * k, p.length * k]);
    ctx.lineDashOffset = st.dash * k;
    ctx.beginPath();
    p.points.forEach((q, i) => {
      const x = q[0] * k - p.w / 2, y = q[1] * k - p.h / 2;
      if (i === 0) ctx.moveTo(x, y); else ctx.lineTo(x, y);
    });
    ctx.stroke();
    ctx.setLineDash([]);
    ctx.restore();
    return;
  }

  // rect
  const hw = p.w / 2, hh = p.h / 2;
  if (p.shadow) {
    rectPath(ctx, -hw + S.depth, -hh + S.depth, p.w, p.h, p.radii ?? p.r);
    ctx.fillStyle = C.ink; ctx.fill();
  }
  const r = p.r;
  if (p.border > 0) {
    rectPath(ctx, -hw, -hh, p.w, p.h, r);
    ctx.fillStyle = C.ink; ctx.fill();
    const e = p.border;
    rectPath(ctx, -hw + e, -hh + e, p.w - 2 * e, p.h - 2 * e, r.map((v) => Math.max(0, v - e)));
    ctx.fillStyle = C[p.fill]; ctx.fill();
  } else {
    rectPath(ctx, -hw, -hh, p.w, p.h, r);
    ctx.fillStyle = C[p.fill]; ctx.fill();
  }
  if (p.text) {
    ctx.font = FACE(p.text.size);
    ctx.textAlign = "center";
    ctx.textBaseline = "middle";
    ctx.fillStyle = C[p.text.fill];
    ctx.fillText(p.text.value, 0, 0);
  }
  ctx.restore();
}

function drawScene(ctx, pieces, phase) {
  const { w, h } = SPEC.canvas;
  ctx.clearRect(0, 0, w, h);
  ctx.fillStyle = C["blue-deep"];
  ctx.fillRect(0, 0, w, h);
  for (const p of pieces) drawPiece(ctx, p, sample(p, phase));
}

// --- Page -------------------------------------------------------------------

const row = document.getElementById("row");
const views = SPEC.scenes.map((s) => {
  const fig = document.createElement("figure");
  const cv = document.createElement("canvas");
  const dpr = window.devicePixelRatio || 1;
  cv.width = SPEC.canvas.w * dpr; cv.height = SPEC.canvas.h * dpr;
  cv.style.width = SPEC.canvas.w + "px"; cv.style.height = SPEC.canvas.h + "px";
  const ctx = cv.getContext("2d"); ctx.scale(dpr, dpr);
  const cap = document.createElement("figcaption"); cap.textContent = s.id;
  fig.append(cv, cap); row.append(fig);
  return { pieces: s.pieces, ctx };
});

let playing = true, t0 = performance.now(), held = 0;
const scrub = document.getElementById("scrub");
const read = document.getElementById("read");
const play = document.getElementById("play");

play.onclick = () => {
  playing = !playing;
  play.textContent = playing ? "pause" : "play";
  if (playing) t0 = performance.now() - held * SPEC.loop.durationMs;
};
scrub.oninput = () => {
  playing = false; play.textContent = "play";
  held = scrub.value / 1000;
};

function frame(now) {
  const phase = playing ? ((now - t0) % SPEC.loop.durationMs) / SPEC.loop.durationMs : held;
  if (playing) scrub.value = String(Math.round(phase * 1000));
  read.textContent = phase.toFixed(3);
  for (const v of views) drawScene(v.ctx, v.pieces, phase);
  requestAnimationFrame(frame);
}
requestAnimationFrame(frame);
