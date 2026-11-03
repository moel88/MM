"""Lee So-sang's G (2.83.1): the giant sword's blade (a real model's: GStage draws it), its colour and its carving's
normals, drawn here (fixed seeds), never by hand.

usage: python3 -I tools/isosang/make_g_sword.py [OUT_DIR]

User request (2.83.1: "이게 ㅈㄴ 큰 검이 내려오는건데 구름에 가려져서 칼 끝 부분밖에 안 보이는거야", "저거 아직 그림이지? 실제 모델
아니지?"): a giant sword, a broad blade with a point, its edges bevelled, coming down point first; its carving is the one
the user drew over the videos' sky shot (their red lines, g_slab_lines.png beside this file, in the videos' frame at 1.917 s
over LX0..LX1 and LY0..LY1), carried onto its face as the sky shot's camera sees it there at 1.95 s (FallingSword's sky
camera and the sword's pose in it, fitted to the edges they drew: the same numbers below), cut in as channels; past where
they drew, up the blade toward its guard, the same carving goes on: the spine's lines up its middle and hooked strokes
either side.
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
LINES = Path(__file__).resolve().parent / 'g_slab_lines.png'
# the blade (blocks): as FallingSword / IsosangRules (BLADE_LENGTH, BLADE_WIDTH), its point's length and roundness, its
# bevel's width, how far in from its edge the panel's border groove is, and its carving's channels' width
L, W = 24.0, 7.6
P = 6.0
N = 2.2
BEVEL = .45
RIM = .8
CHANNEL = .17
TW, TH = 512, 1536
# the sky shot's camera (FallingSword: SKY_EYE_FW, SKY_EYE_SIDE, SKY_EYE_UP, SKY_PITCH, SKY_PAN, SKY_ROLL, SKY_FOV) and the
# sword's pose in it at 1.95 s (FallingSword: its point's place in the frame, SKY_TIP_DEPTH, SKY_AXIS_UP, SKY_AXIS_RIGHT)
CAM = (1.114, -.023, -.326, 60, 41.92, 27.61, 84.42)
TIP = (.648, .653)
POSE = (4.23, 44.92, -50.63)
ASPECT = 1920 / 886
# where the user's lines are (the slab picture's place: the frame at 1.917 s), and the frame at 1.95 s against it
LX0, LX1, LY0, LY1 = .38, .69, .1, .7
S130, TX130, TY130 = 1.00271, -.00122, -.00036


def half(y):
    """The blade's half width at y blocks up from its point."""
    y = np.asarray(y, dtype=np.float64)
    u = np.clip(y / P, 0, 1)
    return np.where(y < 0, 0, W / 2 * (1 - (1 - u) ** N))


def unit(v):
    return v / np.linalg.norm(v)


def sky_camera():
    """The sky shot's camera (as FallingSword.skyView): its eye, its look, its right and its up (world: x her left, y up, z her front)."""
    fw, side, up, pitch, pan, roll, fov = CAM
    f = np.array([0, 0, 1.0]); r = np.array([-1.0, 0, 0]); Y = np.array([0, 1.0, 0])
    eye = f * fw - r * side + Y * up
    pa, pi = np.radians(pan), np.radians(pitch)
    level = unit(-f * np.cos(pa) - r * np.sin(pa))
    look = unit(level * np.cos(pi) + Y * np.sin(pi))
    cr = unit(np.cross(look, Y)); cu = unit(np.cross(cr, look))
    a = np.radians(-roll)
    cr, cu = cr * np.cos(a) + cu * np.sin(a), -cr * np.sin(a) + cu * np.cos(a)
    return eye, look, cr, cu, np.tan(np.radians(fov / 2))


def sky_lines(xs, ys):
    """The user's lines where the face's points (xs across, ys up the blade) are seen in the sky shot at 1.95 s (0 to 1)."""
    eye, look, cr, cu, t = sky_camera()
    depth, el, az = POSE
    tip = eye + (look + cr * (2 * TIP[0] - 1) * t * ASPECT + cu * (1 - 2 * TIP[1]) * t) * depth
    e, z = np.radians(el), np.radians(az)
    axis = unit(look * np.cos(e) * np.cos(z) + cr * np.cos(e) * np.sin(z) + cu * np.sin(e))
    nn = eye - tip; nn = unit(nn - axis * (nn @ axis)); across = np.cross(axis, nn)
    pw = tip + across * xs[..., None] + axis * ys[..., None] + nn * .45
    d = pw - eye; zz = d @ look
    sx = ((d @ cr) / zz / (t * ASPECT) + 1) / 2
    sy = (1 - (d @ cu) / zz / t) / 2
    us = ((sx - TX130) / S130 - LX0) / (LX1 - LX0)
    vs = ((sy - TY130) / S130 - LY0) / (LY1 - LY0)
    img = np.asarray(Image.open(LINES).convert('L'), dtype=np.float64) / 255
    h, w = img.shape
    fx = np.clip(us * w - .5, 0, w - 1.001); fy = np.clip(vs * h - .5, 0, h - 1.001)
    x0 = np.floor(fx).astype(int); y0 = np.floor(fy).astype(int); ax = fx - x0; ay = fy - y0
    val = (img[y0, x0] * (1 - ax) + img[y0, x0 + 1] * ax) * (1 - ay) + (img[y0 + 1, x0] * (1 - ax) + img[y0 + 1, x0 + 1] * ax) * ay
    ok = (zz > 0) & (us > 0) & (us < 1) & (vs > 0) & (vs < 1)
    return np.where(ok, val, 0)


def to_px(x, y):
    return ((x / W + .5) * TW, (1 - y / L) * TH)


def continued():
    """Past where the user drew (from 6.5 blocks up the blade to its guard): the spine's three lines up its middle, and
    either side hooked strokes climbing it (the videos' fall shot carves them so)."""
    lines = []
    for x in (-.55, 0, .55):
        lines.append([(x, 6.2), (x, L - .8)])
    k = 0
    while True:
        y0 = 7.0 + k * 3.4
        if y0 > L - 2.5:
            break
        for s in (-1, 1):
            lines.append([(s * .9, y0), (s * 1.7, y0 + .9), (s * 1.7, y0 + 2.0), (s * 2.5, y0 + 2.6), (s * 2.5, y0 + 1.7)])
            lines.append([(s * 1.1, y0 - .3), (s * 2.6, y0 - .3), (s * 2.6, y0 + .7)])
        k += 1
    return lines


def blade():
    k = 2
    u = (np.arange(TW * k) + .5) / (TW * k)
    v = (np.arange(TH * k) + .5) / (TH * k)
    U, V = np.meshgrid(u, v)
    x = (U - .5) * W
    y = (1 - V) * L
    hw = half(y)
    # how far in from its edge each place on its face is (blocks, across)
    ins = hw - np.abs(x)
    # the user's lines, carried onto it, made into channels of an even width
    drawn = sky_lines(x, y)
    line = Image.fromarray((np.clip(drawn * 1.6, 0, 1) * 255).astype(np.uint8))
    line = line.point(lambda q: 255 if q > 90 else 0)
    px = TW * k / W
    line = line.filter(ImageFilter.MaxFilter(2 * int(CHANNEL * px / 2) + 1))
    cut = np.asarray(line.filter(ImageFilter.GaussianBlur(.06 * px)), dtype=np.float64) / 255
    # its carving going on up the blade, in the same channels
    more = Image.new('L', (TW * k, TH * k), 0)
    d = ImageDraw.Draw(more)
    for pts in continued():
        d.line([(a * k, b * k) for a, b in (to_px(px_, py_) for px_, py_ in pts)], fill=255, width=max(3, int(CHANNEL * px)), joint='curve')
    cut = np.maximum(cut, np.asarray(more.filter(ImageFilter.GaussianBlur(.06 * px)), dtype=np.float64) / 255)
    # its height (0 deep, 1 high): the bevel is the mesh's own (flat here), the rim, the panel's border groove, the panel
    # with its channels cut in (rounded at their bottoms)
    def band(a, b, soft=.04):
        return np.clip((ins - a) / soft, 0, 1) * np.clip((b - ins) / soft, 0, 1)
    h = np.full_like(x, .8)
    panel = np.clip((ins - (RIM + .14)) / .05, 0, 1)
    h = h + .06 * band(BEVEL, RIM) - .5 * band(RIM, RIM + .12, .025)
    h = h - .5 * cut * panel
    h = h + .015 * (fbm(h.shape, 60, 3) - .5)
    hs = np.asarray(Image.fromarray((np.clip(h, 0, 1) * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(1.2)), dtype=np.float64) / 255
    gy, gx = np.gradient(hs)
    s = 6.0
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
    col = base * (.92 + .12 * grain[..., None]) * (1 - .38 * hollow[..., None])
    col = col * (1 + .05 * band(BEVEL, RIM)[..., None])
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
