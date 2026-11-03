"""Lee So-sang's G (2.83.0): the giant golden tablet's textures and the whirl of cloud it comes out of, drawn here (fixed
seeds), never by hand.

usage: python3 tools/isosang/make_tablet.py [OUT_DIR]

The user's videos of G show a giant tablet like a shield, rounded at the top and coming to a point at the bottom, carved all
over: a raised rim with a groove inside it, a second band, and inside them a spine down its middle with hooked branches and
columns of key-shaped meanders either side. It is amber gold, lit warm. Its outline here is the one JadeTablet draws
(half(y): the same numbers), its face mapped so that across it is u (0 its left edge, 1 its right) and down it v (0 its top,
1 its point).
- isosang_tablet.png: the carved face (its outline cut out), lit from the upper left, faint grain;
- isosang_tablet_glow.png: what shines when it glows (the raised edges catching the light most, the carving's ridges);
- isosang_tablet_side.png: its sides (amber, banded);
- isosang_vortex.png: the whirl of cloud seen from below, a bright eye in its middle, white and grey-blue arms winding in,
  its rim breaking up into nothing.
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

OUT = Path(__file__).resolve().parents[2] / 'src/main/resources/assets/celestialfall/textures/effect'
# the tablet (blocks): as JadeTablet / IsosangRules (TABLET_HEIGHT, TABLET_WIDTH)
H, W = 15.0, 7.6
YB, YM = .40 * H, .70 * H
TW, TH = 512, 1024


def half(y):
    """The tablet's half width at y blocks up from its point (0 below it and over its top)."""
    y = np.asarray(y, dtype=np.float64)
    r = W / 2
    u = np.clip(y / YB, 0, 1)
    low = r * (1 - (1 - u) ** 1.8)
    v = np.clip((y - YM) / (H - YM), 0, 1)
    top = r * np.sqrt(np.maximum(0, 1 - v * v))
    out = np.where(y < YB, low, np.where(y < YM, r, top))
    return np.where((y < 0) | (y > H), 0, out)


def grid():
    """Each texel's place on the face: x across from the middle, y up from the point (blocks)."""
    px = (np.arange(TW) + .5) / TW
    py = (np.arange(TH) + .5) / TH
    x = (px - .5) * W
    y = (1 - py) * H
    return np.meshgrid(x, y)


def inset(x, y):
    """How far inside the outline a point is (blocks; negative outside), close enough for bands a block wide."""
    # the distance to the outline sideways and, near the top and the point, along a few directions
    best = half(y) - np.abs(x)
    for ang in np.linspace(0, np.pi, 13)[1:-1]:
        dx, dy = np.cos(ang), np.sin(ang)
        # march out from the point both ways until outside
        for sgn in (1, -1):
            d = np.zeros_like(x)
            step = .05
            inside = np.ones_like(x, dtype=bool)
            for k in range(1, 60):
                xx = x + sgn * dx * k * step
                yy = y + sgn * dy * k * step
                out = np.abs(xx) > half(yy)
                newly = inside & out
                d = np.where(newly, k * step, d)
                inside &= ~out
            d = np.where(inside, 99, d)
            best = np.minimum(best, d)
    return best


def noise(shape, scale, seed):
    """Smooth value noise (bilinear on a coarse random grid)."""
    rng = np.random.default_rng(seed)
    gh, gw = int(shape[0] / scale) + 3, int(shape[1] / scale) + 3
    g = rng.random((gh, gw))
    yy = np.arange(shape[0]) / scale
    xx = np.arange(shape[1]) / scale
    y0 = np.floor(yy).astype(int); x0 = np.floor(xx).astype(int)
    fy = (yy - y0)[:, None]; fx = (xx - x0)[None, :]
    fy = fy * fy * (3 - 2 * fy); fx = fx * fx * (3 - 2 * fx)
    a = g[y0][:, x0]; b = g[y0][:, x0 + 1]; c = g[y0 + 1][:, x0]; d = g[y0 + 1][:, x0 + 1]
    return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy


def fbm(shape, scale, seed, octaves=4):
    out = np.zeros(shape); amp = 1; tot = 0
    for o in range(octaves):
        out += amp * noise(shape, scale / (2 ** o), seed + o * 17)
        tot += amp; amp *= .5
    return out / tot


def carving():
    """The carving's strokes (blocks: polylines on the face, x from the middle, y up from the point)."""
    lines = []
    # the spine down its middle
    lines.append([(0, 1.7), (0, 13.2)])
    # hooked branches off it, either side, every 1.25 blocks
    for k in range(9):
        y = 2.6 + k * 1.25
        reach = min(1.15, .55 + .1 * k)
        for s in (-1, 1):
            lines.append([(0, y), (s * reach, y), (s * reach, y + .55), (s * (reach - .35), y + .55)])
    # columns of key-shaped meanders either side
    for s in (-1, 1):
        cx = s * 2.05
        y = 2.7
        while y < 12.4:
            w = .52; h = .85
            if np.abs(cx) + w > half(y) - .95 or np.abs(cx) + w > half(y + h) - .95:
                y += h + .3
                continue
            lines.append([(cx - s * w, y), (cx + s * w, y), (cx + s * w, y + h), (cx - s * w * .2, y + h), (cx - s * w * .2, y + h * .45), (cx + s * w * .45, y + h * .45)])
            y += h + .3
    # flames licking up the band inside the rim, either side (short hooked strokes)
    for s in (-1, 1):
        for k in range(7):
            y = 4.2 + k * 1.05
            if y > YM + 1.2:
                break
            x0 = s * (half(y) - 1.25)
            lines.append([(x0, y), (x0 + s * .35, y + .45), (x0, y + .85)])
    # two arcs following the round top
    for k, r in enumerate((2.5, 1.7)):
        pts = []
        for a in np.linspace(.18 * np.pi, .82 * np.pi, 24):
            pts.append((np.cos(a) * r * 1.05, YM + .35 + np.sin(a) * r * 1.1))
        lines.append(pts)
    return lines


def to_px(x, y):
    return ((x / W + .5) * TW, (1 - y / H) * TH)


def tablet():
    x, y = grid()
    ins = inset(x, y)
    inside = ins > 0
    # the height of the face: the raised rim, the groove inside it, the second band, the field and its carving
    h = np.zeros_like(x)
    h = np.where(ins < .32, .55 + .45 * np.clip(ins / .12, 0, 1), h)            # the rim (beveled at its edge)
    h = np.where((ins >= .32) & (ins < .44), .18, h)                          # the groove
    h = np.where((ins >= .44) & (ins < .74), .8, h)                           # the second band
    h = np.where(ins >= .74, .32, h)                                          # the field
    carve = Image.new('L', (TW, TH), 0)
    d = ImageDraw.Draw(carve)
    for pts in carving():
        d.line([to_px(px, py) for px, py in pts], fill=255, width=int(.3 / W * TW), joint='curve')
    c = np.asarray(carve.filter(ImageFilter.GaussianBlur(1.6)), dtype=np.float64) / 255
    field = ins >= .9
    h = np.where(field, np.maximum(h, .26 + .7 * c), h)
    h = h + .03 * (fbm(h.shape, 40, 3) - .5)
    hs = np.asarray(Image.fromarray((np.clip(h, 0, 1) * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(1.2)), dtype=np.float64) / 255
    gy, gx = np.gradient(hs * 26)
    n = np.stack([-gx, gy, np.ones_like(gx)], -1)
    n /= np.linalg.norm(n, axis=-1, keepdims=True)
    light = np.array([-.55, .65, .55]); light /= np.linalg.norm(light)
    diff = np.clip((n * light).sum(-1), 0, 1)
    rim = np.clip((n[..., 0] * -.6 + n[..., 1] * .8), 0, 1)
    # amber gold, lighter toward its top, darker into the carving's hollows
    top = np.clip(y / H, 0, 1)[..., None]
    base = np.array([196, 116, 64]) * (1 - top) + np.array([232, 158, 96]) * top
    shade = (.42 + .72 * diff)[..., None] * (.72 + .42 * hs[..., None])
    col = base * shade + np.array([255, 214, 150]) * (.38 * rim ** 6)[..., None]
    grain = fbm(h.shape, 6, 11, 3)
    col *= (.93 + .1 * grain)[..., None]
    alpha = np.clip(ins / .03, 0, 1) * 255
    img = np.dstack([np.clip(col, 0, 255), alpha]).astype(np.uint8)
    Image.fromarray(img, 'RGBA').save(OUT / 'isosang_tablet.png')
    # what shines: the edges catching the light, the carving's ridges, a soft warmth over all
    glow = np.clip(.6 * rim ** 3 + .35 * c * (diff > .5) + .14 * hs, 0, 1)
    gcol = np.array([255, 196, 120])[None, None, :] * glow[..., None]
    gimg = np.dstack([gcol, alpha]).astype(np.uint8)
    Image.fromarray(gimg, 'RGBA').save(OUT / 'isosang_tablet_glow.png')


def side():
    w, h = 64, 64
    y = np.arange(h)[:, None] / h
    band = .5 + .5 * np.sin(y * np.pi * 6)
    grain = fbm((h, w), 8, 21, 3)
    col = np.array([150, 86, 46]) * (.85 + .2 * band)[..., None] * (.9 + .15 * grain)[..., None]
    col = np.broadcast_to(col, (h, w, 3))
    img = np.dstack([np.clip(col, 0, 255), np.full((h, w), 255)]).astype(np.uint8)
    Image.fromarray(img, 'RGBA').save(OUT / 'isosang_tablet_side.png')


def vortex():
    n = 1024
    c = (np.arange(n) + .5) / n * 2 - 1
    xx, yy = np.meshgrid(c, c)
    r = np.sqrt(xx * xx + yy * yy)
    ang = np.arctan2(yy, xx)
    # the arms wind in toward the eye: the angle twisted by how far out it is
    tw = ang + 2.3 * np.log(np.maximum(r, .02))
    # cloud in the twisted frame: noise sampled along the arms (stretched along them)
    u = (tw / (2 * np.pi)) % 1.0
    k = 6
    arms = .5 + .5 * np.cos(tw * 3)
    # noise on a polar strip (u around, r out), then mapped
    strip = fbm((512, 1024), 60, 31, 5)
    iu = (u * 1023).astype(int) % 1024
    ir = np.clip((r * 511).astype(int), 0, 511)
    cloud = strip[ir, iu]
    fine = fbm((512, 1024), 14, 41, 4)[ir, iu]
    dens = np.clip(.4 * arms + .95 * cloud + .3 * fine - .3, 0, 1)
    # the eye: open and bright in the middle; the rim breaking up into nothing
    eye = np.clip((r - .1) / .12, 0, 1)
    edge = np.clip((1 - r) / .28, 0, 1) * np.clip(.7 + .6 * cloud - .25, 0, 1)
    alpha = np.clip(dens * 1.9, 0, 1) * eye * edge
    alpha = np.maximum(alpha, eye * edge * .6)
    # white where the light from the eye catches it, grey-blue in the arms' shadows, stormy at the rim
    lit = np.clip(1.1 - r * 1.1, 0, 1)
    shadow = np.clip(1 - dens, 0, 1)
    white = np.array([238, 242, 248]); grey = np.array([128, 146, 172]); storm = np.array([70, 86, 112])
    col = white * (lit * (1 - .5 * shadow))[..., None] + grey * ((1 - lit) * (1 - shadow))[..., None] + storm * (shadow * (1 - lit))[..., None]
    col = np.clip(col * (.85 + .3 * fine)[..., None] + np.array([255, 236, 200]) * (np.clip(.3 - r, 0, .3) / .3 * .6)[..., None], 0, 255)
    img = np.dstack([col, alpha * 255]).astype(np.uint8)
    Image.fromarray(img, 'RGBA').save(OUT / 'isosang_vortex.png')


def main():
    global OUT
    if len(sys.argv) > 1:
        OUT = Path(sys.argv[1])
    OUT.mkdir(parents=True, exist_ok=True)
    tablet()
    side()
    vortex()
    for name in ('isosang_tablet.png', 'isosang_tablet_glow.png', 'isosang_tablet_side.png', 'isosang_vortex.png'):
        print(name, Image.open(OUT / name).size)


if __name__ == '__main__':
    main()
