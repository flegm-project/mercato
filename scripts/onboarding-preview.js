// The browser half of the onboarding preview. Loaded verbatim into
// build/art/onboarding-preview.html by scripts/gen-onboarding-scenes.mjs,
// which hands it SCENE (design/onboarding.json) and C (the design tokens).
//
// Deliberately the same arithmetic as OnboardingArt.swift and OnboardingArt.kt,
// in the same order, so that what this shows is what the phones draw.

const ease = (t) => { const c = Math.min(Math.max(t, 0), 1); return c * c * (3 - 2 * c); };
const pop = (t) => { const c = Math.min(Math.max(t, 0), 1) - 1; return 1 + 2.7 * c ** 3 + 1.7 * c ** 2; };

function flatten(p) {
  const pts = [];
  for (let i = 0; i <= SCENE.style.samples; i++) {
    const t = i / SCENE.style.samples, u = 1 - t;
    const w = [u ** 3, 3 * u * u * t, 3 * u * t * t, t ** 3];
    const at = (k) => w[0] * p.path.from[k] + w[1] * p.path.c1[k] + w[2] * p.path.c2[k] + w[3] * p.path.to[k];
    pts.push([at(0), at(1)]);
  }
  const len = [0];
  for (let i = 1; i < pts.length; i++) len.push(len[i - 1] + Math.hypot(pts[i][0] - pts[i - 1][0], pts[i][1] - pts[i - 1][1]));
  return [pts, len];
}

/** The polyline up to `f` of its length, and where that leaves the ball. */
function trail(ctx, p, f) {
  const [pts, len] = flatten(p);
  const target = len[len.length - 1] * Math.min(Math.max(f, 0), 1);
  ctx.beginPath();
  ctx.moveTo(pts[0][0], pts[0][1]);
  let head = pts[0];
  for (let i = 1; i < pts.length; i++) {
    if (len[i] >= target) {
      const span = len[i] - len[i - 1];
      const k = span > 0 ? (target - len[i - 1]) / span : 0;
      head = [pts[i - 1][0] + (pts[i][0] - pts[i - 1][0]) * k, pts[i - 1][1] + (pts[i][1] - pts[i - 1][1]) * k];
      ctx.lineTo(head[0], head[1]);
      break;
    }
    ctx.lineTo(pts[i][0], pts[i][1]);
    head = pts[i];
  }
  return head;
}

const S = SCENE.style;

function roundRect(ctx, x, y, w, h, r) {
  ctx.beginPath();
  ctx.roundRect(x, y, w, h, r);
}

function raised(ctx, draw, fill, alpha) {
  ctx.globalAlpha = alpha;
  ctx.save(); ctx.translate(S.depth, S.depth); draw(0); ctx.fillStyle = C.ink; ctx.fill(); ctx.restore();
  draw(0); ctx.fillStyle = C.ink; ctx.fill();
  draw(1); ctx.fillStyle = fill; ctx.fill();
  ctx.globalAlpha = 1;
}

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

function star(ctx, cx, cy, r, fill, shade, alpha) {
  ctx.globalAlpha = alpha;
  ctx.save(); ctx.translate(S.depth, S.depth); starPath(ctx, cx, cy, r); ctx.fillStyle = shade; ctx.fill(); ctx.restore();
  starPath(ctx, cx, cy, r); ctx.fillStyle = fill; ctx.fill();
  ctx.globalAlpha = 1;
}

function drawScene(ctx, p, phase) {
  const { w, h } = SCENE.canvas;
  ctx.clearRect(0, 0, w, h);
  ctx.fillStyle = C.ground; ctx.fillRect(0, 0, w, h);

  const travel = ease(phase / SCENE.loop.travelEnd);
  const land = (phase - SCENE.loop.travelEnd) / (SCENE.loop.accentEnd - SCENE.loop.travelEnd);
  const alpha = 1 - ease((phase - SCENE.loop.holdEnd) / (1 - SCENE.loop.holdEnd));
  const lit = ease(land) * alpha;

  const accent = p.accentColor === "green" ? C.green : C.yellow;
  (p.blocks || []).forEach((b, i) => {
    const draw = (inset) => {
      const e = inset ? S.border : 0;
      roundRect(ctx, b.x + e, b.y + e, b.w - 2 * e, b.h - 2 * e, b.r - e);
    };
    raised(ctx, draw, C.grey, alpha);
    if (i === p.accent) raised(ctx, draw, accent, lit);
  });

  (p.stars || []).forEach((s, i) => {
    star(ctx, s.cx, s.cy, s.r, "rgba(255,255,255,.15)", "rgba(11,16,48,.15)", alpha);
    const rank = Math.max(0, (p.order || []).indexOf(i));
    const grow = pop((land - rank * 0.17) / 0.66);
    if (grow > 0.01) star(ctx, s.cx, s.cy, s.r * grow, C.yellow, C.ink, alpha);
  });

  ctx.globalAlpha = alpha;
  const head = trail(ctx, p, travel);
  ctx.lineCap = "round"; ctx.lineJoin = "round"; ctx.lineWidth = S.trail;
  ctx.save(); ctx.translate(S.depth, S.depth); ctx.strokeStyle = C.ink;
  trail(ctx, p, travel); ctx.stroke(); ctx.restore();
  trail(ctx, p, travel); ctx.strokeStyle = C.yellow; ctx.stroke();
  ctx.globalAlpha = 1;

  const shrink = (p.stars || []).length ? 1 - ease(land / 0.4) : 1;
  if (shrink > 0.01) {
    raised(ctx, (inset) => {
      ctx.beginPath();
      ctx.arc(head[0], head[1], (S.ballRadius - (inset ? S.ballBorder : 0)) * shrink, 0, 7);
    }, C.ivory, alpha);
  }
}

// --- Page -------------------------------------------------------------------

const row = document.getElementById("row");
const views = SCENE.panes.map((p) => {
  const fig = document.createElement("figure");
  const cv = document.createElement("canvas");
  const dpr = window.devicePixelRatio || 1;
  cv.width = SCENE.canvas.w * dpr; cv.height = SCENE.canvas.h * dpr;
  cv.style.width = SCENE.canvas.w + "px"; cv.style.height = SCENE.canvas.h + "px";
  const ctx = cv.getContext("2d"); ctx.scale(dpr, dpr);
  const cap = document.createElement("figcaption"); cap.textContent = p.id;
  fig.append(cv, cap); row.append(fig);
  return { p, ctx };
});

let playing = true, t0 = performance.now(), held = 0;
const scrub = document.getElementById("scrub");
const read = document.getElementById("read");
const play = document.getElementById("play");

play.onclick = () => {
  playing = !playing;
  play.textContent = playing ? "pause" : "play";
  if (playing) t0 = performance.now() - held * SCENE.loop.durationMs;
};
scrub.oninput = () => {
  playing = false; play.textContent = "play";
  held = scrub.value / 1000;
};

function frame(now) {
  const phase = playing ? ((now - t0) % SCENE.loop.durationMs) / SCENE.loop.durationMs : held;
  if (playing) scrub.value = String(Math.round(phase * 1000));
  read.textContent = phase.toFixed(3);
  for (const v of views) drawScene(v.ctx, v.p, phase);
  requestAnimationFrame(frame);
}
requestAnimationFrame(frame);
