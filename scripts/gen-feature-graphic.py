"""Play Store feature graphic, 1024x500, built from the design tokens.

Text is converted to outlines rather than referenced by family name. rsvg
resolves fonts through fontconfig, which does not see a font that is merely
sitting in the repo, and every attempt to point it at design/fonts silently
fell back to a generic sans: the wordmark rendered in the wrong face while
reporting success. Outlines remove the question entirely, and they are also
what a store asset should be, since nothing downstream needs the text to be
selectable.
"""
import json, subprocess
from fontTools.ttLib import TTFont
from fontTools.pens.svgPathPen import SVGPathPen

ROOT = "/Users/Nico/mercato/"
tok = json.load(open(ROOT + "design/tokens.json"))["color"]

def run(text, font_path, size, tracking=0.0):
    """Outline `text`, returning (svg_path_data, advance_width) at `size` px."""
    f = TTFont(ROOT + font_path, fontNumber=0)
    upem = f["head"].unitsPerEm
    cmap = f.getBestCmap()
    gs = f.getGlyphSet()
    scale = size / upem
    out, x = [], 0.0
    for ch in text:
        name = cmap.get(ord(ch))
        if name is None:
            x += size * 0.3
            continue
        pen = SVGPathPen(gs)
        gs[name].draw(pen)
        d = pen.getCommands()
        if d:
            out.append(f'<path d="{d}" transform="translate({x/scale:.2f},0)"/>')
        x += gs[name].width * scale + tracking
    return "".join(out), x

def block(text, font, size, tracking, cx, y, fill, shadow, dx, dy):
    d, w = run(text, font, size, tracking)
    scale = size / TTFont(ROOT + font, fontNumber=0)["head"].unitsPerEm
    # The glyph outlines are y-up; flip them and place the baseline at y.
    def g(fill, ox, oy):
        return (f'<g transform="translate({cx - w/2 + ox:.2f},{y + oy:.2f}) '
                f'scale({scale:.5f},{-scale:.5f})" fill="{fill}">{d}</g>')
    return g(shadow, dx, dy) + g(fill, 0, 0), w

UNB = "design/fonts/Unbounded-Black.ttf"
FIG = "design/fonts/Figtree-ExtraBold.ttf"

word, _ = block("MERCATO", UNB, 118, -5, 512, 248, "#FFFFFF", tok["ink"], 8, 9)
# "CATO" in yellow: drawn as a second pass clipped to its own advance.
mer_d, mer_w = run("MER", UNB, 118, -5)
cato_d, cato_w = run("CATO", UNB, 118, -5)
full_w = mer_w + cato_w
s = 118 / TTFont(ROOT + UNB, fontNumber=0)["head"].unitsPerEm
x0 = 512 - full_w / 2

def grp(d, ox, oy, fill):
    return (f'<g transform="translate({ox:.2f},{oy:.2f}) scale({s:.5f},{-s:.5f})" '
            f'fill="{fill}">{d}</g>')

tag_d, tag_w = run("TWO CLUBS, ONE YEAR. NAME THE PLAYER.", FIG, 31, 1.5)
st = 31 / TTFont(ROOT + FIG, fontNumber=0)["head"].unitsPerEm
tx = 512 - tag_w / 2

def tgrp(ox, oy, fill):
    return (f'<g transform="translate({ox:.2f},{oy:.2f}) scale({st:.5f},{-st:.5f})" '
            f'fill="{fill}">{tag_d}</g>')

svg = f'''<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="500">
 <defs><linearGradient id="s" x1="0" y1="0" x2="0" y2="1">
  <stop offset="0" stop-color="{tok['blue-top']}"/><stop offset="1" stop-color="{tok['blue-deep']}"/>
 </linearGradient></defs>
 <rect width="1024" height="500" fill="url(#s)"/>
 {grp(mer_d, x0 + 8, 257, tok['ink'])}{grp(cato_d, x0 + mer_w + 8, 257, tok['ink'])}
 {grp(mer_d, x0, 248, "#FFFFFF")}{grp(cato_d, x0 + mer_w, 248, tok['yellow'])}
 {tgrp(tx + 3, 353, tok['ink'])}{tgrp(tx, 350, tok['yellow'])}
</svg>'''
open("/tmp/feature.svg", "w").write(svg)
subprocess.run(["rsvg-convert", "-w", "1024", "-h", "500",
                "-o", ROOT + "store/play-assets/feature-1024x500.png",
                "/tmp/feature.svg"], check=True)
print("feature graphic written, text as outlines")
