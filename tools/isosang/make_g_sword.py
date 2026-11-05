"""Lee So-sang's G (2.83.1): the giant sword's blade (a real model's: GStage draws it), its colour and its carving's
normals, drawn here (fixed seeds), never by hand.

usage: python3 -I tools/isosang/make_g_sword.py [OUT_DIR]

User request (2.83.1: "이게 ㅈㄴ 큰 검이 내려오는건데 구름에 가려져서 칼 끝 부분밖에 안 보이는거야", "저거 아직 그림이지? 실제 모델
아니지?", "존나 삐뚫빼뚫하잖아", "그냥 저거 내가 선으로 표시한거 없애고 너가 저 원본 사진에서 선 따면 되는 거 아님?", "내가 그려준거랑
비교해서 하면 되잖아"): a giant sword, a broad blade with a point, its edges bevelled, coming down point first. Its carving
(CARVING) is the videos' own: every frame of their sky shot (1.63 to 2.70 s) carried onto the blade's face through the sky
shot's camera and the sword's pose in it (CAM, POSE: FallingSword's) and stacked, its grooves' centre lines found there and
followed; which grooves, and how they join, are the ones the user drew over it (their red lines, kept beside this file:
g_slab_lines.png, the videos' frame at 1.917 s over x .38 to .69 and y .1 to .7). Each is cut in as a
straight-sided channel of one width, its corners sharp. Past where the videos show it (up the blade toward its guard), the
same carving goes on: the spine's lines up its middle and hooked strokes either side.
- isosang_g_blade.png: its colour (a muted terracotta, darker in the channels, the rim a little lighter); its alpha is
  where it glows when hot (its point most);
- isosang_g_blade_n.png: the carving's normals (red across to the right, green up the blade, blue out of the face).
Its face is mapped so that across it is u (0 its left edge, 1 its right) and down it v (0 its top, 1 its point).
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

OUT = Path(__file__).resolve().parents[2] / 'src/main/resources/assets/celestialfall/textures/effect'
# the blade (blocks): as FallingSword / IsosangRules (BLADE_LENGTH, BLADE_WIDTH), its point's length and roundness, its
# bevel's width, how far in from its edge (square to it) the panel's border groove is, and its carving's channels' width
L, W = 24.0, 7.6
P = 6.0
N = 2.2
BEVEL = .45
RIM = .78
CHANNEL = .16
TW, TH = 512, 1536
# the camera the carving was traced through (FallingSword's sky shot: SKY_EYE_FW, SKY_EYE_SIDE, SKY_EYE_UP, SKY_PITCH,
# SKY_PAN, SKY_ROLL, SKY_FOV) and the sword's pose in it at 1.95 s (SKY_TIP_DEPTH, SKY_AXIS_UP, SKY_AXIS_RIGHT)
CAM = (1.114, -.023, -.326, 60, 41.92, 27.61, 84.42)
POSE = (4.23, 44.92, -50.63)
# the carving, traced from the videos (blocks: x across, its right +; y up the blade from its point): each a channel's
# centre line, corner to corner
CARVING = [
    # the left meander: down the spine's outer line, stepping out, down, hooked, along the bottom, back up and over
    [(-.85, 6.42), (-.91, 5.81), (-.66, 5.84), (-.67, 5.44), (-1.04, 5.39), (-1.10, 4.55), (-1.25, 4.36), (-1.45, 4.24),
     (-1.70, 4.12), (-1.47, 3.84), (-2.07, 3.64), (-2.34, 4.16), (-2.00, 4.28), (-1.78, 4.40), (-1.65, 4.62),
     (-1.58, 5.12), (-2.45, 5.03), (-2.12, 5.56)],
    # the spine's lines, each stepping out once and hooked at its foot
    [(-.555, 6.40), (-.555, 6.06), (-.39, 6.04), (-.39, 5.38), (-.60, 5.34), (-.66, 3.95), (-.86, 3.98), (-.87, 4.12)],
    [(-.15, 6.35), (-.15, 5.24), (-.28, 5.17), (-.29, 3.81), (-.47, 3.84), (-.48, 4.04)],
    [(-.06, 4.26), (-.07, 3.67)],
    [(.20, 4.20), (.21, 3.61)],
    [(.20, 6.22), (.22, 5.15), (.37, 5.06), (.41, 3.65), (.54, 3.64), (.55, 3.80)],
    [(.56, 6.10), (.56, 5.72), (.41, 5.74), (.43, 5.17), (.61, 5.06), (.73, 3.66), (.87, 3.64), (.88, 3.76)],
    # the right meander: down, stepping in, out down the side, hooked twice and back up
    [(.76, 6.05), (.84, 5.78), (.85, 5.37), (.67, 5.38), (.70, 5.14), (.99, 4.82), (1.04, 4.18), (1.50, 3.46),
     (1.38, 3.31), (1.73, 2.92), (1.93, 3.29), (1.53, 3.76), (1.40, 4.32), (1.62, 4.20)],
    # the fan below the spine, left: each line down and sweeping out, its end lifting
    [(-1.05, 3.76), (-1.05, 3.46), (-1.12, 3.22), (-1.30, 3.06), (-1.55, 2.98), (-1.78, 3.00), (-1.98, 3.12),
     (-2.10, 3.25)],
    [(-.67, 3.77), (-.71, 3.40), (-.80, 3.00), (-.95, 2.80), (-1.15, 2.68), (-1.45, 2.60), (-1.62, 2.65), (-1.72, 2.74)],
    [(-.34, 3.64), (-.38, 3.30), (-.46, 2.95), (-.57, 2.65), (-.72, 2.42), (-.92, 2.30), (-1.12, 2.28), (-1.26, 2.37)],
    # its innermost lines, joined below by the arrowhead
    [(-.12, 3.54), (-.15, 2.76), (-.20, 2.35), (-.30, 2.15), (-.50, 2.02), (-.70, 1.97), (-.86, 1.97), (0, 1.38),
     (.80, 1.80), (.58, 1.93), (.40, 2.12), (.29, 2.40), (.25, 2.95), (.23, 3.46)],
    # and right
    [(.45, 3.48), (.47, 3.00), (.55, 2.55), (.68, 2.25), (.86, 2.08), (1.05, 1.97), (1.14, 2.01)],
    [(.92, 3.50), (.77, 3.46), (.79, 3.00), (.90, 2.60), (1.08, 2.41), (1.32, 2.28), (1.45, 2.28)],
    [(1.00, 3.34), (1.03, 3.00), (1.12, 2.76), (1.30, 2.60), (1.55, 2.52), (1.70, 2.56)],
]


def half(y):
    """The blade's half width at y blocks up from its point."""
    y = np.asarray(y, dtype=np.float64)
    u = np.clip(y / P, 0, 1)
    return np.where(y < 0, 0, W / 2 * (1 - (1 - u) ** N))


def unit(v):
    return v / np.linalg.norm(v)


def to_px(x, y, k=1):
    return ((x / W + .5) * TW * k, (1 - y / L) * TH * k)


def continued():
    """Past where the videos show it (from 6.4 blocks up the blade to its guard): the spine's four middle lines go on up
    it, and either side hooked strokes climb it (the videos' fall shot carves them so)."""
    lines = [[s[0], (s[0][0], L - .8)] for s in CARVING[1:3] + CARVING[5:7]]
    k = 0
    while True:
        y0 = 7.4 + k * 3.4
        if y0 > L - 2.5:
            break
        for s in (-1, 1):
            lines.append([(s * .95, y0), (s * 1.7, y0 + .9), (s * 1.7, y0 + 2.0), (s * 2.5, y0 + 2.6), (s * 2.5, y0 + 1.7)])
            lines.append([(s * 1.1, y0 - .3), (s * 2.6, y0 - .3), (s * 2.6, y0 + .7)])
        k += 1
    return lines


def channel(draw, pts, width, k):
    """One channel: straight-sided, of one width, its ends square, its corners sharp (mitred; past 3 half-widths, cut)."""
    h = width / 2
    b = [np.array(p, dtype=np.float64) for p in pts]
    d = [(b[i + 1] - b[i]) / np.linalg.norm(b[i + 1] - b[i]) for i in range(len(b) - 1)]
    n = [np.array([-v[1], v[0]]) for v in d]
    def fill(poly):
        draw.polygon([to_px(c[0], c[1], k) for c in poly], fill=255)
    for i in range(len(d)):
        a0 = b[i] - d[i] * h if i == 0 else b[i]
        a1 = b[i + 1] + d[i] * h if i == len(d) - 1 else b[i + 1]
        fill([a0 + n[i] * h, a1 + n[i] * h, a1 - n[i] * h, a0 - n[i] * h])
    for i in range(1, len(d)):
        # the corner's outer side: a mitre (or, very sharp, a cut)
        side = -1 if d[i - 1][0] * d[i][1] - d[i - 1][1] * d[i][0] > 0 else 1
        m = n[i - 1] + n[i]
        if np.linalg.norm(m) < 1e-9:
            continue
        m = m / np.linalg.norm(m)
        reach = h / max(1e-9, m @ n[i])
        p0 = b[i] + side * n[i - 1] * h
        p1 = b[i] + side * n[i] * h
        fill([b[i], p0, b[i] + side * m * reach, p1] if reach <= 3 * h else [b[i], p0, p1])


def edge_distance(k):
    """How far in from its edge, square to it, each place on its face is (blocks), on the texture's grid."""
    s = 160.0
    nx = int(W * s); ny = int(L * s)
    xs = (np.arange(nx) + .5) / s - W / 2
    ys = (1 - (np.arange(ny) + .5) / ny) * L
    inside = (np.abs(xs)[None, :] <= half(ys)[:, None]).astype(np.uint8)
    import cv2
    dist = cv2.distanceTransform(inside, cv2.DIST_L2, 5) / s
    # onto the texture's grid
    u = ((np.arange(TW * k) + .5) / (TW * k)) * nx - .5
    v = ((np.arange(TH * k) + .5) / (TH * k)) * ny - .5
    U, V = np.meshgrid(u.astype(np.float32), v.astype(np.float32))
    return cv2.remap(dist.astype(np.float32), U, V, cv2.INTER_LINEAR).astype(np.float64)


def blade():
    k = 2
    u = (np.arange(TW * k) + .5) / (TW * k)
    v = (np.arange(TH * k) + .5) / (TH * k)
    U, V = np.meshgrid(u, v)
    x = (U - .5) * W
    y = (1 - V) * L
    hw = half(y)
    # how far in from its edge each place on its face is (blocks): across (the bevel, as the mesh cuts it) and square to
    # the edge (the panel's border groove)
    ins = hw - np.abs(x)
    sq = edge_distance(k)
    px = TW * k / W
    # the carving: each traced line, and its going on up the blade, cut as channels of one width (their bottoms rounded)
    more = Image.new('L', (TW * k, TH * k), 0)
    d = ImageDraw.Draw(more)
    for pts in CARVING + continued():
        channel(d, pts, CHANNEL, k)
    cut = np.asarray(more.filter(ImageFilter.GaussianBlur(.016 * px)), dtype=np.float64) / 255
    # its height (0 deep, 1 high): the bevel is the mesh's own (flat here), the rim, the panel's border groove, the panel
    # with its channels cut in (rounded at their bottoms)
    def band(a, b, soft=.04, by=None):
        by = ins if by is None else by
        return np.clip((by - a) / soft, 0, 1) * np.clip((b - by) / soft, 0, 1)
    h = np.full_like(x, .8)
    panel = np.clip((sq - (RIM + .14)) / .05, 0, 1)
    h = h + .06 * band(BEVEL, RIM, by=sq) - .5 * band(RIM, RIM + .12, .025, by=sq)
    h = h - .5 * cut * panel
    h = h + .015 * (fbm(h.shape, 60, 3) - .5)
    hs = np.asarray(Image.fromarray((np.clip(h, 0, 1) * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(.8)), dtype=np.float64) / 255
    gy, gx = np.gradient(hs)
    s = 9.0
    n = np.stack([-gx * s, gy * s, np.ones_like(gx)], -1)
    n /= np.linalg.norm(n, axis=-1, keepdims=True)
    nimg = ((n + 1) * 127.5).astype(np.uint8)
    nrgb = Image.fromarray(nimg, 'RGB').resize((TW, TH), Image.LANCZOS)
    Image.merge('RGBA', (*nrgb.split(), Image.new('L', (TW, TH), 255))).save(OUT / 'isosang_g_blade_n.png')
    # its colour: a muted terracotta (the videos'), warmer toward its point, a little uneven, darker deep in the channels,
    # the rim a touch lighter
    near = np.exp(-y / 7)[..., None]
    base = np.array([186, 120, 96]) * (1 - near) + np.array([206, 136, 100]) * near
    grain = fbm(h.shape, 70, 11, 4)
    hollow = np.clip((.8 - hs) / .5, 0, 1)
    col = base * (.92 + .12 * grain[..., None]) * (1 - .55 * hollow[..., None])
    col = col * (1 + .05 * band(BEVEL, RIM, by=sq)[..., None])
    col = np.clip(col, 0, 255)
    # where it glows when hot: its point most, its edges and the carving near it a little
    edge = np.clip(1 - ins / .6, 0, 1)
    glow = np.exp(-y / 1.6) + .3 * np.exp(-y / 4) * edge + .12 * np.exp(-y / 4) * cut + .03
    # (its colour and its glow made small apart: shrunk together, the faint glow would crush the colour)
    rgb = Image.fromarray(col.astype(np.uint8), 'RGB').resize((TW, TH), Image.LANCZOS)
    a = Image.fromarray((np.clip(glow, 0, 1) * 255).astype(np.uint8), 'L').resize((TW, TH), Image.LANCZOS)
    Image.merge('RGBA', (*rgb.split(), a)).save(OUT / 'isosang_g_blade.png')


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
