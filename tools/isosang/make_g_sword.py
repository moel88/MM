"""Lee So-sang's G (2.83.0): the giant sword's carved blade, drawn here (fixed seeds), never by hand.

usage: python3 -I tools/isosang/make_g_sword.py [OUT_DIR]

The user's videos of G show only the lower part of a giant sword's blade as it comes down (user: "그리고 저거 거대한 검
떨어지는거임"): a broad blade coming to a point like a shield's, amber gold lit warm, its edges beveled, carved deep all over:
a raised rim inside the bevel with a groove and a second band inside it that follow the point, and in the field inside them
channels cut deep and angular: a spine of three down its middle, crossed by bars near the point, and either side climbing
the blade slanting strokes that turn up and hook back down, with stepped keys by the border. It is lit as it is drawn (the isosang_blade program: the carving by its normals), so its colour here is
its colour only, with the hollows' shade. Its outline is the one FallingSword draws (half(y): the same numbers), its face
mapped so that across it is u (0 its left edge, 1 its right) and down it v (0 its top, 1 its point).
- isosang_g_blade.png: its colour (amber, darker deep in the carving); its alpha is where it glows when hot (its point
  most, its edges and the carving near the point);
- isosang_g_blade_n.png: the carving's normals (red across to the right, green up the blade, blue out of the face).
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

OUT = Path(__file__).resolve().parents[2] / 'src/main/resources/assets/celestialfall/textures/effect'
# the blade (blocks): as FallingSword / IsosangRules (BLADE_LENGTH, BLADE_WIDTH), the point's length and the bevel's width
L, W = 24.0, 7.6
P = 6.0
BEVEL = 0.45
TW, TH = 512, 1536


def half(y):
    """The blade's half width at y blocks up from its point (it goes on past its top: the texture's top is no edge)."""
    y = np.asarray(y, dtype=np.float64)
    r = W / 2
    u = np.clip(y / P, 0, 1)
    out = np.where(y < P, r * (1 - (1 - u) ** 1.8), r)
    return np.where(y < 0, 0, out)


def grid():
    """Each texel's place on the face: x across from the middle, y up from the point (blocks)."""
    px = (np.arange(TW) + .5) / TW
    py = (np.arange(TH) + .5) / TH
    return np.meshgrid((px - .5) * W, (1 - py) * L)


def inset(x, y):
    """How far inside the outline a point is (blocks; negative outside)."""
    best = half(y) - np.abs(x)
    for ang in np.linspace(0, np.pi, 15)[1:-1]:
        dx, dy = np.cos(ang), np.sin(ang)
        for sgn in (1, -1):
            d = np.zeros_like(x)
            step = .05
            inside = np.ones_like(x, dtype=bool)
            for k in range(1, 50):
                xx = x + sgn * dx * k * step
                yy = y + sgn * dy * k * step
                out = np.abs(xx) > half(yy)
                d = np.where(inside & out, k * step, d)
                inside &= ~out
            best = np.minimum(best, np.where(inside, 99, d))
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


def spline(points, n=12):
    """A smooth curve through the points (Catmull-Rom)."""
    p = [points[0]] + list(points) + [points[-1]]
    out = []
    for i in range(1, len(p) - 2):
        p0, p1, p2, p3 = (np.array(q, dtype=float) for q in p[i - 1:i + 3])
        for t in np.linspace(0, 1, n, endpoint=False):
            t2, t3 = t * t, t * t * t
            out.append(tuple(.5 * (2 * p1 + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3)))
    out.append(tuple(points[-1]))
    return out


def channels():
    """The channels cut into the field (blocks: polylines on the face, x from the middle, y up from the point): as the
    videos' carving, angular: straight cuts with sharp turns and slanting strokes, not round scrolls."""
    lines = []
    # the spine: three down the middle, coming to points over the bars
    for x, y0 in ((0, 1.55), (-.44, 2.05), (.44, 2.05)):
        lines.append([(x, y0), (x, L + .5)])
    # bars across it near the point
    for y in (2.7, 3.2, 3.7):
        lines.append([(-.78, y), (.78, y)])
    # either side, climbing the blade: a slanting stroke out from the spine that turns up and hooks back down at its
    # top (a claw), a stepped key by the border, and a short slash between
    k = 0
    while True:
        y0 = 4.2 + k * 3.1
        if y0 > L - .5:
            break
        for s in (-1, 1):
            lines.append([(s * .8, y0), (s * 1.55, y0 + .85), (s * 1.55, y0 + 1.95), (s * 2.25, y0 + 2.55), (s * 2.25, y0 + 1.7)])
            lines.append([(s * 1.05, y0 - .15), (s * 2.42, y0 - .15), (s * 2.42, y0 + .75), (s * 1.98, y0 + .75), (s * 1.98, y0 + .35)])
            lines.append([(s * .8, y0 + 2.2), (s * 1.18, y0 + 2.62)])
        k += 1
    return lines


def to_px(x, y):
    return ((x / W + .5) * TW, (1 - y / L) * TH)


def blade():
    x, y = grid()
    ins = inset(x, y)
    # the carving's height (0 deep, 1 high): the bevel is the mesh's own (flat here), then the rim, a groove, a band, a
    # groove, and the field, cut by its channels (rounded at their bottoms)
    def band(a, b, soft=.05):
        return np.clip((ins - a) / soft, 0, 1) * np.clip((b - ins) / soft, 0, 1)
    h = np.full_like(x, .8)
    h = np.where(ins < BEVEL, .8, h)
    h = h + .2 * band(BEVEL, BEVEL + .18) - .45 * band(BEVEL + .18, BEVEL + .29, .03) + .12 * band(BEVEL + .29, BEVEL + .62) \
        - .45 * band(BEVEL + .62, BEVEL + .73, .03)
    cut = Image.new('L', (TW, TH), 0)
    d = ImageDraw.Draw(cut)
    for pts in channels():
        if len(pts) > 1:
            d.line([to_px(px, py) for px, py in pts], fill=255, width=max(3, int(.24 / W * TW)))
            for px, py in pts[1:-1]:
                cx, cy = to_px(px, py); r = .12 / W * TW
                d.rectangle([cx - r, cy - r, cx + r, cy + r], fill=255)
            for px, py in (pts[0], pts[-1]):
                cx, cy = to_px(px, py); r = .12 / W * TW
                d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=255)
    c = np.asarray(cut.filter(ImageFilter.GaussianBlur(2.2)), dtype=np.float64) / 255
    field = np.clip((ins - (BEVEL + .85)) / .08, 0, 1)
    h = h - .55 * c * field
    h = h + .015 * (fbm(h.shape, 30, 3) - .5)
    hs = np.asarray(Image.fromarray((np.clip(h, 0, 1) * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(1.0)), dtype=np.float64) / 255
    # normals: the carving's slopes, across (x) and up the blade (y) in texels, made steep enough to read
    gy, gx = np.gradient(hs)
    k = 7.0
    nx, ny, nz = -gx * k, gy * k, np.ones_like(gx)
    n = np.stack([nx, ny, nz], -1)
    n /= np.linalg.norm(n, axis=-1, keepdims=True)
    nimg = np.dstack([(n + 1) * 127.5, np.full(h.shape, 255)]).astype(np.uint8)
    Image.fromarray(nimg, 'RGBA').save(OUT / 'isosang_g_blade_n.png')
    # colour: amber, warmer toward the point, a little uneven, darker deep in the channels and grooves (their hollows'
    # shade), the high places a touch lighter
    near = np.exp(-y / 7)[..., None]
    base = np.array([212, 124, 90]) * (1 - near) + np.array([226, 138, 92]) * near
    grain = fbm(h.shape, 50, 11, 4)
    hollow = np.clip((.8 - hs) / .5, 0, 1)
    col = base * (.92 + .12 * grain[..., None]) * (1 - .42 * hollow[..., None]) * (.94 + .1 * np.clip(hs - .8, 0, .2)[..., None] / .2)
    col = np.clip(col + np.array([20, 8, 0]) * (np.clip(.3 - ins, 0, .3) / .3)[..., None], 0, 255)
    # where it glows when hot: its point most, its edges and the carving near the point
    edge = np.clip(1 - ins / .6, 0, 1)
    glow = np.exp(-y / 1.4) + .3 * np.exp(-y / 4) * edge + .15 * np.exp(-y / 4) * c + .03
    img = np.dstack([col, np.clip(glow, 0, 1) * 255]).astype(np.uint8)
    Image.fromarray(img, 'RGBA').save(OUT / 'isosang_g_blade.png')


def main():
    global OUT
    if len(sys.argv) > 1:
        OUT = Path(sys.argv[1])
    OUT.mkdir(parents=True, exist_ok=True)
    blade()
    for name in ('isosang_g_blade.png', 'isosang_g_blade_n.png'):
        print(name, Image.open(OUT / name).size)


if __name__ == '__main__':
    main()
