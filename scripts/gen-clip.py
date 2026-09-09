"""Vertical social clip, built from the transfer dataset.

The repo already holds 1,900 real transfers, the brand fonts and the design
tokens. That is a year of "guess the player" videos sitting unused, and the
format is a proven one on TikTok and Shorts. This turns any five transfers
into a 1080x1920 clip with no hand work.

Frames are stills rather than animation, and ffmpeg holds each for its own
duration through the concat demuxer. A quiz clip is read, not watched: the
thing that has to be right is that the clubs are legible for long enough to
think, and that the answer lands after the viewer has committed to a guess.

Brand rules come from store/marketing/README.md: the wordmark is flat here,
never carrying the in-app ink offset; cards keep the design system's ink
border and solid shadow; the icon is placed as is under the platform's own
rounded mask.

Run:
  python3 scripts/gen-clip.py --out build/clips --count 7
  python3 scripts/gen-clip.py --out build/clips --transfers 42,101,7,88,9
"""

import argparse
import csv
import json
import os
import random
import subprocess
import sys
import tempfile

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
W, H = 1080, 1920
FPS = 30

TOK = json.load(open(os.path.join(ROOT, "design/tokens.json")))["color"]
INK = TOK["ink"]
YELLOW = TOK["yellow"]
IVORY = TOK["ivory"]
BLUE_TOP = TOK["blue-top"]
BLUE = TOK["blue"]
BLUE_DEEP = TOK["blue-deep"]
CLUB_GREY = TOK["club-grey"]

UNBOUNDED = os.path.join(ROOT, "design/fonts/Unbounded-Black.ttf")
FIGTREE_BLACK = os.path.join(ROOT, "design/fonts/Figtree-Black.ttf")
FIGTREE_XBOLD = os.path.join(ROOT, "design/fonts/Figtree-ExtraBold.ttf")
ICON = os.path.join(ROOT, "store/play-assets/icon-512.png")


def hex_rgb(value):
    value = value.lstrip("#")
    return tuple(int(value[i : i + 2], 16) for i in (0, 2, 4))


def font(path, size):
    return ImageFont.truetype(path, size)


def background():
    """The app's radial gradient, drawn once and reused for every frame."""
    top, mid, deep = hex_rgb(BLUE_TOP), hex_rgb(BLUE), hex_rgb(BLUE_DEEP)
    img = Image.new("RGB", (W, H), deep)
    px = img.load()
    cx, cy = W / 2, 0.0
    # 130% width, 85% height, matching gradient.app-background in the tokens.
    rx, ry = W * 1.30, H * 0.85
    for y in range(H):
        for x in range(0, W, 4):
            t = (((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2) ** 0.5
            t = min(1.0, t)
            if t < 0.44:
                k = t / 0.44
                c = tuple(round(top[i] + (mid[i] - top[i]) * k) for i in range(3))
            else:
                k = (t - 0.44) / 0.56
                c = tuple(round(mid[i] + (deep[i] - mid[i]) * k) for i in range(3))
            for dx in range(4):
                if x + dx < W:
                    px[x + dx, y] = c
    return img


def text_width(draw, s, f, tracking=0):
    if not s:
        return 0
    w = sum(draw.textlength(ch, font=f) for ch in s)
    return w + tracking * (len(s) - 1)


def draw_tracked(draw, xy, s, f, fill, tracking=0):
    x, y = xy
    for ch in s:
        draw.text((x, y), ch, font=f, fill=fill)
        x += draw.textlength(ch, font=f) + tracking


def centered(draw, y, s, f, fill, tracking=0):
    w = text_width(draw, s, f, tracking)
    draw_tracked(draw, ((W - w) / 2, y), s, f, fill, tracking)
    return w


def fitted(draw, y, s, path, size, fill, tracking, max_width):
    """Centre `s`, shrinking the face until it fits inside `max_width`.

    Club names run from PSG to Borussia Moenchengladbach. One fixed size
    either wastes half the card or runs the long ones off the edge, and a clip
    whose text is clipped is worse than no clip.
    """
    while size > 20:
        f = font(path, size)
        if text_width(draw, s, f, tracking) <= max_width:
            break
        size -= 2
    centered(draw, y, s, font(path, size), fill, tracking)


def wordmark(draw, y, size):
    """MER in white, CATO in yellow, flat. Tracking is -5px measured at 118."""
    f = font(UNBOUNDED, size)
    tracking = -5 * size / 118
    total = text_width(draw, "MER", f, tracking) + tracking + text_width(draw, "CATO", f, tracking)
    x = (W - total) / 2
    draw_tracked(draw, (x, y), "MER", f, "#FFFFFF", tracking)
    x += text_width(draw, "MER", f, tracking) + tracking
    draw_tracked(draw, (x, y), "CATO", f, YELLOW, tracking)


def solid_card(img, box, fill, radius=44, depth=14):
    """Design-system surface: ink border, solid ink shadow, no blur."""
    x0, y0, x1, y1 = box
    d = ImageDraw.Draw(img)
    d.rounded_rectangle((x0 + depth, y0 + depth, x1 + depth, y1 + depth), radius, fill=INK)
    d.rounded_rectangle((x0, y0, x1, y1), radius, fill=fill, outline=INK, width=6)


def header(img, line):
    d = ImageDraw.Draw(img)
    centered(d, 150, line, font(FIGTREE_BLACK, 52), YELLOW, tracking=3)


def footer(img):
    d = ImageDraw.Draw(img)
    wordmark(d, H - 250, 64)
    centered(d, H - 160, "FREE ON GOOGLE PLAY", font(FIGTREE_XBOLD, 34), IVORY, tracking=4)


def question_frame(bg, from_club, to_club, year, countdown=None, answer=None):
    img = bg.copy()
    header(img, "GUESS THE PLAYER")

    solid_card(img, (90, 470, W - 90, 1180), hex_rgb(IVORY))
    d = ImageDraw.Draw(img)
    centered(d, 530, "TRANSFER", font(FIGTREE_XBOLD, 34), CLUB_GREY, tracking=8)

    inner = W - 2 * 90 - 80
    fitted(d, 630, from_club.upper(), UNBOUNDED, 62, INK, -2, inner)
    centered(d, 760, "TO", font(FIGTREE_XBOLD, 40), CLUB_GREY, tracking=6)
    fitted(d, 830, to_club.upper(), UNBOUNDED, 62, INK, -2, inner)
    centered(d, 1000, str(year), font(UNBOUNDED, 96), YELLOW, tracking=-3)

    if answer is not None:
        solid_card(img, (90, 1290, W - 90, 1520), hex_rgb(YELLOW))
        fitted(d, 1380, answer.upper(), UNBOUNDED, 58, INK, -2, W - 2 * 90 - 80)
    elif countdown is not None:
        centered(d, 1330, str(countdown), font(UNBOUNDED, 150), IVORY, tracking=0)

    footer(img)
    return img


def intro_frame(bg):
    img = bg.copy()
    d = ImageDraw.Draw(img)
    centered(d, 520, "A FLEGM GAME", font(FIGTREE_XBOLD, 34), IVORY, tracking=8)

    icon = Image.open(ICON).convert("RGBA").resize((260, 260))
    mask = Image.new("L", (260, 260), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, 259, 259), 57, fill=255)
    img.paste(icon, (int((W - 260) / 2), 600), mask)

    wordmark(d, 940, 110)
    centered(d, 1130, "HOW WELL DO YOU REALLY", font(FIGTREE_BLACK, 54), YELLOW, tracking=1)
    centered(d, 1200, "KNOW FOOTBALL?", font(FIGTREE_BLACK, 54), YELLOW, tracking=1)
    centered(d, 1400, "FIVE REAL TRANSFERS", font(FIGTREE_XBOLD, 44), IVORY, tracking=4)
    centered(d, 1470, "NAME THE PLAYER", font(FIGTREE_XBOLD, 44), IVORY, tracking=4)
    return img


def outro_frame(bg):
    img = bg.copy()
    d = ImageDraw.Draw(img)
    icon = Image.open(ICON).convert("RGBA").resize((300, 300))
    mask = Image.new("L", (300, 300), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, 299, 299), 66, fill=255)
    img.paste(icon, (int((W - 300) / 2), 560), mask)

    wordmark(d, 950, 120)
    centered(d, 1150, "1,900 REAL TRANSFERS", font(FIGTREE_BLACK, 50), YELLOW, tracking=2)
    centered(d, 1230, "PLAY THE DAILY CHALLENGE", font(FIGTREE_XBOLD, 44), IVORY, tracking=3)

    solid_card(img, (200, 1400, W - 200, 1560), hex_rgb(YELLOW), radius=40, depth=12)
    centered(d, 1455, "DOWNLOAD", font(UNBOUNDED, 56), INK, tracking=-2)
    return img


def load_corpus():
    players = {p["id"]: p for p in csv.DictReader(open(os.path.join(ROOT, "data/players.csv")))}
    clubs = {c["id"]: c for c in csv.DictReader(open(os.path.join(ROOT, "data/clubs.csv")))}
    transfers = list(csv.DictReader(open(os.path.join(ROOT, "data/transfers.csv"))))
    return players, clubs, transfers


def render_clip(bg, picks, players, clubs, out_path):
    """One clip: intro, five questions with a countdown and a reveal, outro."""
    frames = []  # (PIL image, seconds on screen)
    frames.append((intro_frame(bg), 2.2))
    for t in picks:
        player = players[t["player_id"]]["name_en"]
        a = clubs[t["from_club"]]["name_en"]
        b = clubs[t["to_club"]]["name_en"]
        year = t["year"]
        for n in (3, 2, 1):
            frames.append((question_frame(bg, a, b, year, countdown=n), 1.0))
        frames.append((question_frame(bg, a, b, year, answer=player), 1.6))
    frames.append((outro_frame(bg), 2.8))

    with tempfile.TemporaryDirectory() as tmp:
        listing = []
        for i, (img, seconds) in enumerate(frames):
            p = os.path.join(tmp, f"{i:03d}.png")
            img.save(p)
            listing.append(f"file '{p}'\nduration {seconds}")
        # The concat demuxer ignores the last entry's duration, so the final
        # frame is repeated to give the outro the time it asks for.
        listing.append(f"file '{os.path.join(tmp, f'{len(frames) - 1:03d}.png')}'")
        list_path = os.path.join(tmp, "list.txt")
        open(list_path, "w").write("\n".join(listing) + "\n")
        subprocess.run(
            [
                "ffmpeg", "-y", "-loglevel", "error",
                "-f", "concat", "-safe", "0", "-i", list_path,
                "-vf", f"fps={FPS},format=yuv420p",
                "-c:v", "libx264", "-preset", "medium", "-crf", "20",
                "-movflags", "+faststart",
                out_path,
            ],
            check=True,
        )


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=os.path.join(ROOT, "build/clips"))
    ap.add_argument("--count", type=int, default=1, help="how many clips to render")
    ap.add_argument("--seed", type=int, default=None)
    ap.add_argument("--transfers", default="", help="explicit comma-separated transfer ids")
    args = ap.parse_args()

    players, clubs, transfers = load_corpus()
    os.makedirs(args.out, exist_ok=True)

    # Tier 1 only. A social clip has three seconds to be recognised, so it
    # draws from the transfers everyone argues about, not from the long tail
    # the game keeps for its harder modes.
    pool = [t for t in transfers if t["tier"] == "1"]
    rng = random.Random(args.seed if args.seed is not None else 20260910)

    print("rendering the background once")
    bg = background()

    if args.transfers:
        wanted = set(args.transfers.split(","))
        picks_list = [[t for t in transfers if t["id"] in wanted]]
    else:
        # One transfer per player. The pool holds several moves for the same
        # famous career, and a clip that asks for Cavani three times reads as
        # broken rather than hard.
        rng.shuffle(pool)
        seen, unique = set(), []
        for t in pool:
            if t["player_id"] in seen:
                continue
            seen.add(t["player_id"])
            unique.append(t)
        picks_list = [unique[i * 5 : i * 5 + 5] for i in range(args.count)]

    for i, picks in enumerate(picks_list, start=1):
        if len(picks) < 5:
            print(f"skipping clip {i}: only {len(picks)} transfers", file=sys.stderr)
            continue
        out = os.path.join(args.out, f"clip-{i:02d}.mp4")
        render_clip(bg, picks, players, clubs, out)
        names = ", ".join(players[t["player_id"]]["name_en"] for t in picks)
        print(f"{out}  ({names})")


if __name__ == "__main__":
    main()
