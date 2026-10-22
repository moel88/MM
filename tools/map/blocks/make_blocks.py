#!/usr/bin/env python3
"""Draws the seaside town's block textures (64x) and writes their models, block states, item models, loot tables,
tool tags and names: python3 tools/map/blocks/make_blocks.py

The blocks are dev.sangwoo.celestialfall.town.TownBlocks; the list below names them in the same order. Every picture is
drawn from seeded noise (no image is copied from anywhere), so a run always gives the same files. The materials follow
the two photos the seaside map was first built after (map v1.x; v2.0 rebuilt the town wide and simple with the same
blocks): fine dark asphalt with thin paint,
the light coarse old asphalt of the lot, pale pavers, split-face concrete blocks, dark rough stone, plaster, kawara,
white corrugated iron streaked with rust, and the street's furniture.
"""
import json
from pathlib import Path
import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[3]
RES = ROOT / 'src/main/resources'
ASSETS = RES / 'assets/celestialfall'
TEX = ASSETS / 'textures/block/town'
S = 64
NS = 'celestialfall'

# ---------------------------------------------------------------- noise and paint

def field(seed, sx, sy=None):
    """Tileable smooth noise (zero mean, unit spread): white noise blurred by sx across and sy down, in pixels."""
    sy = sx if sy is None else sy
    w = np.random.default_rng(seed).normal(size=(S, S))
    fy = np.fft.fftfreq(S)[:, None]; fx = np.fft.fftfreq(S)[None, :]
    f = np.real(np.fft.ifft2(np.fft.fft2(w) * np.exp(-2 * np.pi ** 2 * ((sx * fx) ** 2 + (sy * fy) ** 2))))
    f -= f.mean(); s = f.std()
    return f / (s if s > 0 else 1)

def rng(seed): return np.random.default_rng(seed)

def flat(color):
    img = np.zeros((S, S, 3)); img[:] = color; return img

def shade(img, v):
    """Adds a brightness field to every channel."""
    return img + v[..., None]

def save(name, img, alpha=None):
    TEX.mkdir(parents=True, exist_ok=True)
    a = np.clip(img, 0, 255).astype(np.uint8)
    if alpha is not None:
        rgba = np.dstack([a, np.clip(alpha, 0, 255).astype(np.uint8)])
        Image.fromarray(rgba, 'RGBA').save(TEX / f'{name}.png')
    else:
        Image.fromarray(a, 'RGB').save(TEX / f'{name}.png')

def stones(img, seed, rate, palette, big=0.3):
    """Aggregate showing through: specks of one or two pixels in colours from the palette."""
    g = rng(seed)
    m = g.random((S, S)) < rate
    m |= np.roll(m & (g.random((S, S)) < big), 1, axis=1)
    pick = g.integers(0, len(palette), (S, S))
    col = np.array(palette, float)[pick] * g.uniform(0.85, 1.1, (S, S))[..., None]
    img[m] = col[m]
    return img

def paint(img, seed, mask, color=(232, 231, 224), wear=0.10):
    """Road paint over the mask: a little dirty, worn through in places so the surface shows."""
    g = rng(seed)
    n = field(seed + 7, 1.2) * 0.6 + g.normal(size=(S, S)) * 0.5
    keep = mask & (n > -2.2 + wear * 5)
    tint = np.array(color, float) + (field(seed + 8, 4) * 5)[..., None] + g.normal(0, 3, (S, S))[..., None]
    out = img.copy(); out[keep] = tint[keep]
    half = mask & ~keep
    out[half] = 0.55 * img[half] + 0.45 * tint[half]
    return out

def rows(lo, hi):
    m = np.zeros((S, S), bool); m[lo:hi, :] = True; return m

def wobble(mask, seed, amount=1):
    """Makes a stripe's straight edges a little uneven, as brushed paint."""
    out = mask.copy()
    off = np.round(field(seed, 6, 0.5)[0] * amount).astype(int)
    for x in range(S): out[:, x] = np.roll(mask[:, x], off[x])
    return out

# ---------------------------------------------------------------- materials

def asphalt(seed):
    """Fine dark asphalt of the streets: a grey grain with pale chips of stone and black pores."""
    g = rng(seed)
    img = shade(flat((63, 63, 65)), 6 * g.normal(size=(S, S)) + 3.5 * field(seed + 1, 1.4) + 3 * field(seed + 2, 7))
    img = stones(img, seed + 3, 0.05, [(112, 110, 104), (96, 95, 92), (128, 124, 116)])
    img[g.random((S, S)) < 0.035] -= 16
    return img

def worn_asphalt(seed, cracked=False):
    """The lot's old asphalt: coarse and grey (photo 2's, cool rather than brown in the sun), its stones standing
    out, cracks with weeds in some."""
    g = rng(seed)
    img = shade(flat((101, 98, 97)), 9 * g.normal(size=(S, S)) + 7 * field(seed + 1, 2.5) + 7 * field(seed + 2, 12))
    img = stones(img, seed + 3, 0.11, [(140, 136, 132), (84, 82, 80), (152, 147, 141), (72, 71, 70), (120, 114, 108)], big=0.5)
    img[g.random((S, S)) < 0.04] -= 22
    if cracked:
        for k in range(3):
            x, y = g.uniform(0, S, 2); a = g.uniform(0, 2 * np.pi)
            for step in range(int(g.uniform(40, 90))):
                a += g.normal(0, 0.35); x = (x + np.cos(a)) % S; y = (y + np.sin(a)) % S
                ix, iy = int(x), int(y)
                img[iy, ix] = (58, 54, 50)
                if g.random() < 0.35: img[iy, (ix + 1) % S] = (78, 74, 68)
                if g.random() < 0.10:
                    for dx, dy in ((0, 1), (1, 0), (0, -1)):
                        img[(iy + dy) % S, (ix + dx) % S] = (74 + g.integers(-8, 8), 104 + g.integers(-10, 12), 50)
    return img

def patched(seed):
    g = rng(seed)
    img = shade(flat((84, 83, 82)), 4 * g.normal(size=(S, S)) + 3 * field(seed + 1, 3))
    return stones(img, seed + 2, 0.02, [(120, 118, 112)])

def pavers(seed):
    """Pale pavers in a stretcher bond, each a shade of its own, thin dark joints."""
    g = rng(seed)
    img = np.zeros((S, S, 3)); tone = np.zeros((S, S))
    for r in range(4):
        off = 16 if r % 2 else 0
        for c in range(3):
            x0 = (c * 32 + off) % S
            t = g.normal(0, 6)
            for x in range(x0, x0 + 32): tone[r * 16:(r + 1) * 16, x % S] = t
    img[:] = (178, 174, 165)
    img = shade(img, tone + 2.5 * g.normal(size=(S, S)) + 2 * field(seed + 1, 5))
    img[g.random((S, S)) < 0.03] -= 14
    for r in range(4):
        img[r * 16, :] = (128, 124, 116)
        off = 16 if r % 2 else 0
        for c in range(2): img[r * 16:(r + 1) * 16, (c * 32 + off) % S] = (128, 124, 116)
    return img

def blocks(seed, base, w, h, joint, rough=10, bond=True, stain=6, speck=None):
    """Masonry of w x h pixel units: a tone per unit, a rough face, darker edges below and to the right, joints."""
    g = rng(seed)
    img = np.zeros((S, S, 3)); tone = np.zeros((S, S)); bev = np.zeros((S, S))
    for r in range(S // h + 1):
        off = (w // 2) if (bond and r % 2) else 0
        for c in range(S // w + 1):
            t = g.normal(0, rough * 0.9)
            for yy in range(r * h, min(S, (r + 1) * h)):
                for k in range(w):
                    x = (c * w + off + k) % S
                    tone[yy, x] = t
                    if k >= w - 2 or yy >= (r + 1) * h - 2: bev[yy, x] = -9
                    elif k < 1 or yy < r * h + 1: bev[yy, x] = 7
    img[:] = base
    img = shade(img, tone + bev + rough * 0.8 * g.normal(size=(S, S)) + rough * 0.7 * field(seed + 1, 1.3) + 5 * field(seed + 2, 6))
    img = shade(img, -np.clip(field(seed + 3, 1.0, 14), 0, None) * stain)
    if speck: img[g.random((S, S)) < speck[0]] = speck[1]
    for r in range(S // h + 1):
        y = r * h
        if y < S: img[y:y + 2, :] = joint
        off = (w // 2) if (bond and r % 2) else 0
        for c in range(S // w + 1):
            x = (c * w + off) % S
            img[y:min(S, y + h), x:x + 2] = joint
    return img

def plaster(seed):
    g = rng(seed)
    img = shade(flat((228, 224, 213)), 2.5 * g.normal(size=(S, S)) + 2.5 * field(seed + 1, 2.5) + 3.5 * field(seed + 2, 16))
    return shade(img, -np.clip(field(seed + 3, 1.2, 18), 0.6, None) * 4)

def weathered(seed):
    """Old concrete: rain streaks from above, a crack or two, patches of newer render."""
    g = rng(seed)
    img = shade(flat((176, 170, 160)), 5 * g.normal(size=(S, S)) + 6 * field(seed + 1, 2) + 6 * field(seed + 2, 12))
    img = shade(img, -np.clip(field(seed + 3, 1.2, 16), 0, None) * 14)
    x, y = g.uniform(0, S, 2)
    for step in range(60):
        x = (x + g.normal(0, 0.6)) % S; y = (y + 1) % S
        img[int(y), int(x)] = (96, 92, 86)
    return img

def kawara(seed):
    """Kawara for a slope running along the picture's width: long rounded channels, faint overlaps every quarter metre."""
    g = rng(seed)
    v = np.arange(S) % 16
    c = np.cos((v - 7.5) / 7.5 * np.pi / 2)
    prof = 36 * c ** 1.5 - 15
    img = shade(flat((72, 76, 84)), prof[:, None] + np.zeros((S, S)))
    u = np.arange(S) % 16
    lap = np.where(u == 15, -9, np.where(u == 0, 4, 0))
    img = shade(img, lap[None, :] * (0.5 + c[:, None]))
    img[np.isin(v, (15, 0))] -= 12
    return shade(img, 2.5 * g.normal(size=(S, S)) + 3 * field(seed + 1, 3))

def corrugated(seed, base, rust):
    """Painted corrugated sheet, its ribs running down the picture; rust spreads softly from blotches and runs down in
    streaks, taking the ribs' light and shade with it. It tiles up and down, so a wall's rows do not repeat a band."""
    g = rng(seed)
    u = np.arange(S)
    rib = 15 * np.sin(u / 6.0 * 2 * np.pi)
    img = shade(flat(base), rib[None, :] + 3 * g.normal(size=(S, S)) + 4 * field(seed + 1, 6))
    img[:, 0] -= 30; img[:, 1] += 8
    m = 0.6 * field(seed + 2, 5) + 0.8 * field(seed + 3, 0.8, 12)
    a = np.clip((m - (1.3 - rust * 2.3)) / 0.7, 0, 1)
    hue = field(seed + 4, 3)
    rc = np.dstack([118 + 26 * hue, 64 + 14 * hue, 36 + 6 * hue]) + (rib[None, :] * 0.7)[..., None] + (4 * g.normal(size=(S, S)))[..., None]
    return img * (1 - a[..., None]) + rc * a[..., None]

def kawara_ridge(seed):
    """The ridge's cap of kawara: round tiles laid end to end along the picture's width, a little darker than the slopes."""
    g = rng(seed)
    v = np.arange(S)
    c = np.cos((v % 32 - 15.5) / 15.5 * np.pi / 2)
    img = shade(flat((58, 61, 68)), (30 * c ** 1.2 - 12)[:, None] + np.zeros((S, S)))
    u = np.arange(S) % 21
    img = shade(img, np.where(u == 20, -14, np.where(u == 0, 6, 0))[None, :] + 0 * img[..., 0])
    return shade(img, 2.5 * g.normal(size=(S, S)) + 3 * field(seed + 1, 3))

def flashing(seed):
    """The bent sheet over a metal roof's ridge: plain grey steel with a crease along it."""
    img = steel(seed, (92, 94, 98))
    img[30:34] += 18; img[34:36] -= 14
    return img

def roof_sheet(seed):
    g = rng(seed)
    v = np.arange(S)
    rib = 13 * np.sin(v / 6.0 * 2 * np.pi)
    img = shade(flat((70, 72, 77)), rib[:, None] + 3 * g.normal(size=(S, S)) + 4 * field(seed + 1, 8))
    img[g.random((S, S)) < 0.03] = (96, 64, 44)
    return img

def concrete_pole(seed):
    g = rng(seed)
    img = shade(flat((152, 151, 146)), 3 * g.normal(size=(S, S)) + 6 * field(seed + 1, 0.8, 12) + 4 * field(seed + 2, 6))
    for y in (8, 40):
        img[y:y + 2, 30:32] = (70, 70, 68)
    return img

def sleeve(seed):
    g = rng(seed)
    y, x = np.mgrid[0:S, 0:S]
    band = ((x + y) // 11) % 2 == 0
    img = np.where(band[..., None], np.array((228, 186, 34), float), np.array((32, 32, 30), float))
    return shade(img, 3 * g.normal(size=(S, S)) - np.clip(field(seed + 1, 3), 1.2, None) * 16)

def steel(seed, base=(152, 156, 160)):
    g = rng(seed)
    return shade(flat(base), 4 * g.normal(size=(S, S)) + 4 * field(seed + 1, 5))

def porcelain(seed):
    img = steel(seed, (226, 226, 221))
    img[np.arange(S) % 6 == 0] -= 30
    return img

def transformer(seed):
    g = rng(seed)
    u = np.arange(S)
    img = shade(flat((168, 170, 172)), (10 * np.sin(u / 5.0 * 2 * np.pi))[None, :] + 3 * g.normal(size=(S, S)))
    img[:6] -= 22
    return img

def led(seed):
    img = flat((250, 248, 238)); img[::8] -= 10; img[:, ::8] -= 10
    return img

def ac_side(seed):
    img = steel(seed, (210, 206, 196))
    img[::5, :] -= 14
    return img

def letterbox_front(seed):
    img = steel(seed, (206, 206, 200))
    img[22:26, 14:50] = (40, 40, 42)
    return img

def pane_slats(seed, base=(90, 60, 41)):
    """Dark brown horizontal slats with a gap of sky between each (2.48.0: black or orange-brown too)."""
    g = rng(seed)
    img = np.zeros((S, S, 3)); alpha = np.zeros((S, S))
    grain = 9 * field(seed + 1, 10, 0.6) + 4 * g.normal(size=(S, S))
    for k in range(8):
        y0 = k * 8
        img[y0:y0 + 7] = base + np.array([g.normal(0, 7)] * 3)
        alpha[y0:y0 + 7] = 255
    img = shade(img, grain)
    for k in range(8): img[k * 8] += 10; img[k * 8 + 6] -= 12
    return img, alpha

def pane_chain(seed):
    g = rng(seed)
    y, x = np.mgrid[0:S, 0:S]
    wire = ((x + y) % 8 == 0) | ((x - y) % 8 == 0)
    img = shade(flat((158, 160, 158)), 10 * g.normal(size=(S, S)))
    img[wire & (field(seed + 1, 3) > 1.2)] = (124, 96, 78)
    return img, np.where(wire, 255, 0)

def pane_gate(seed):
    img = flat((32, 32, 34)); alpha = np.zeros((S, S))
    for x in range(0, S, 8): alpha[:, x + 3:x + 5] = 255; img[:, x + 3] += 22
    for y0, y1 in ((0, 4), (30, 33), (60, 64)): alpha[y0:y1] = 255; img[y0] += 18
    return img, alpha

def pane_guardrail(seed):
    img = flat((236, 236, 232)); alpha = np.zeros((S, S))
    for top in (2, 30):
        for i, c in enumerate((248, 238, 222, 190, 168)): img[top + i] = (c, c, c - 3)
        alpha[top:top + 5] = 255
    return img, alpha

def pane_balcony(seed):
    img = flat((40, 42, 46)); alpha = np.zeros((S, S))
    alpha[0:5] = 255; img[0:2] += 30
    alpha[57:61] = 255
    for x in range(2, S, 7): alpha[:, x:x + 2] = 255; img[:, x] += 16
    return img, alpha

def pane_net(seed):
    img = flat((232, 232, 226)); alpha = np.zeros((S, S))
    alpha[::6, :] = 230; alpha[:, ::6] = 230
    return img, alpha

def pane_window(seed):
    """A window from outside: dark timber frame, white curtains drawn to the sides, glass between."""
    g = rng(seed)
    img = np.zeros((S, S, 3)); alpha = np.zeros((S, S))
    img[:] = (150, 172, 186); alpha[:] = 110
    y, x = np.mgrid[0:S, 0:S]
    streak = np.abs((x - y) - 6) < 5
    img[streak] = (196, 212, 222); alpha[streak] = 130
    for x0, x1 in ((3, 23), (41, 61)):
        xs = np.arange(x0, x1)
        fold = 12 * np.sin((xs - x0) / 5.0 * 2 * np.pi)
        img[3:61, x0:x1] = (236, 232, 224) + fold[None, :, None] * np.array([1, 1, 1])
        alpha[3:61, x0:x1] = 250
    img[58:61, 3:61] -= 20
    frame = (x < 3) | (x >= 61) | (y < 3) | (y >= 61) | ((x >= 31) & (x < 33))
    img[frame] = (66, 48, 36); alpha[frame] = 255
    return img, alpha

def petals(seed):
    """Fallen cherry petals: small pale pink ovals scattered over nothing, a few still in twos and threes."""
    g = rng(seed)
    img = np.zeros((S, S, 3)); alpha = np.zeros((S, S))
    y, x = np.mgrid[0:S, 0:S]
    cols = [(246, 196, 212), (250, 214, 226), (252, 232, 238), (232, 168, 192), (240, 186, 204)]
    for k in range(34):
        cx, cy = g.uniform(0, S, 2); a = g.uniform(0, np.pi); rl = g.uniform(1.6, 2.6); rs = rl * g.uniform(0.45, 0.65)
        dx = (x - cx + S / 2) % S - S / 2; dy = (y - cy + S / 2) % S - S / 2
        u = dx * np.cos(a) + dy * np.sin(a); v = -dx * np.sin(a) + dy * np.cos(a)
        m = (u / rl) ** 2 + (v / rs) ** 2 <= 1
        c = np.array(cols[g.integers(0, len(cols))], float)
        img[m] = c - 14 * ((u[m] / rl) > 0.4)[:, None]
        alpha[m] = 255
    return img, alpha

# ---------------------------------------------------------------- leaves on cards, windows, the houses' materials (2.48.0)

def leaf_shape(x, y, cx, cy, a, length, width):
    """A leaf: a pointed ellipse of the given length and width, turned by a, round (cx, cy); and how far along it each point is."""
    dx = x - cx; dy = y - cy
    u = dx * np.cos(a) + dy * np.sin(a); v = -dx * np.sin(a) + dy * np.cos(a)
    t = u / (length / 2)
    half = (width / 2) * np.clip(1 - np.abs(t) ** 1.6, 0, 1) * (1.0 - 0.35 * np.clip(t, 0, 1))
    inside = (np.abs(t) <= 1) & (np.abs(v) <= half)
    return inside, t, v / np.maximum(half, 1e-6)

def leaf_cards(seed, hue=(84, 136, 52), count=64, spread=24, sizes=(11, 17), wrap=False):
    """A cluster of leaves on nothing: pointed ovals every way round, each a shade of its own, lit from above, a
    darker edge and a pale midrib. The cluster thins out toward the card's edge, so the card's outline is ragged."""
    g = rng(seed)
    img = np.zeros((S, S, 3)); alpha = np.zeros((S, S))
    y, x = np.mgrid[0:S, 0:S]
    base = np.array(hue, float)
    for k in range(count):
        if wrap:
            cx, cy = g.uniform(0, S, 2)
        else:
            # thick in the middle, thinning to the edge: the card's outline is a ragged round
            rr = spread * np.sqrt(g.random()) * 1.05; aa = g.uniform(0, 2 * np.pi)
            cx = 32 + rr * np.cos(aa); cy = 32 + rr * np.sin(aa)
        a = g.uniform(0, np.pi); length = g.uniform(*sizes); width = length * g.uniform(0.42, 0.6)
        xs, ys = x, y
        if wrap:
            xs = (x - cx + S / 2) % S + cx - S / 2; ys = (y - cy + S / 2) % S + cy - S / 2
        inside, t, v = leaf_shape(xs, ys, cx, cy, a, length, width)
        tone = g.uniform(0.62, 1.18)
        lit = 1 + 0.18 * (-np.sin(a)) * t  # the tip toward the top is lit
        col = base * tone * lit[..., None]
        col = col + np.array([18, 22, 8]) * np.clip(-v, 0, 1)[..., None] * 0.35
        rim = inside & ((np.abs(v) > 0.72) | (np.abs(t) > 0.86))
        rib = inside & (np.abs(v) < 0.09) & (np.abs(t) < 0.9)
        img[inside] = col[inside]
        img[rim] = img[rim] * 0.62
        img[rib] = np.clip(img[rib] * 1.25 + 12, 0, 255)
        alpha[inside] = 255
    return img, alpha

def leaf_mass(seed):
    """The dark mass inside a hedge or a crown, on the block's faces: leaves over leaves, tiling, a few gaps."""
    img, alpha = leaf_cards(seed, (54, 96, 38), 110, 0, (9, 14), True)
    img = img * 0.85
    g = rng(seed + 5)
    holes = (field(seed + 6, 2.2) > 1.55)
    alpha[holes] = 0
    alpha[(alpha == 0) & ~holes] = 255; img[(img.sum(axis=2) == 0)] = (40, 70, 28)
    return img, alpha

def blossom_cards(seed, count=60, spread=24, wrap=False, mass=False):
    """Cherry blossom: five-petalled flowers in pale pinks and near whites over one another, each with a darker rim
    so the flowers part from one another, a deep pink eye with stamens, a few dark twigs behind."""
    g = rng(seed)
    img = np.zeros((S, S, 3)); alpha = np.zeros((S, S))
    y, x = np.mgrid[0:S, 0:S]
    # twigs first, behind the flowers
    for k in range(3 if not mass else 6):
        px, py = (g.uniform(0, S, 2) if wrap else (32 + g.normal(0, spread * 0.5), 32 + g.normal(0, spread * 0.5)))
        a = g.uniform(0, 2 * np.pi)
        for step in range(int(g.uniform(14, 30))):
            a += g.normal(0, 0.25); px += np.cos(a); py += np.sin(a)
            ix, iy = int(px) % S, int(py) % S
            if not wrap and not (0 <= px < S and 0 <= py < S): break
            img[iy, ix] = (70, 46, 44); alpha[iy, ix] = 255
    pinks = [(250, 206, 222), (252, 222, 232), (246, 192, 214), (255, 238, 244), (242, 182, 206), (250, 214, 228)]
    for k in range(count):
        if wrap: cx, cy = g.uniform(0, S, 2)
        else:
            rr = spread * np.sqrt(g.random()) * 1.05; aa = g.uniform(0, 2 * np.pi)
            cx = 32 + rr * np.cos(aa); cy = 32 + rr * np.sin(aa)
        r = g.uniform(4.2, 6.4); rot = g.uniform(0, 2 * np.pi)
        dx = x - cx; dy = y - cy
        if wrap: dx = (dx + S / 2) % S - S / 2; dy = (dy + S / 2) % S - S / 2
        d = np.hypot(dx, dy); ang = np.arctan2(dy, dx) - rot
        petal = r * (0.74 + 0.26 * np.cos(5 * ang)) * (1 - 0.08 * np.cos(10 * ang + 1))
        inside = d <= petal
        c = np.array(pinks[g.integers(0, len(pinks))], float) * g.uniform(0.93, 1.03)
        # lit from above: the flower's upper side a little brighter
        lit = 1 + 0.06 * np.clip(-dy / max(r, 1), -1, 1)
        shade_ = (1 - 0.16 * np.clip(d / np.maximum(petal, 1e-6), 0, 1) ** 2) * lit
        col = c[None, None, :] * shade_[..., None]
        rim = inside & (d > petal * 0.86)
        img[inside] = col[inside]
        img[rim] = img[rim] * 0.82
        mid = d <= r * 0.24
        img[mid] = (226, 104, 146)
        for st in range(6):
            sa = rot + st * np.pi / 3 + 0.3
            sx, sy = int(cx + np.cos(sa) * r * 0.38) % S, int(cy + np.sin(sa) * r * 0.38) % S
            img[sy, sx] = (240, 168, 72)
        alpha[inside] = 255
    return np.clip(img, 0, 255), alpha

def blossom_mass(seed):
    """The blossom inside a crown, on the block's faces: flowers packed over a pink shade, with gaps of shadow and sky
    so a crown's faces do not read as flat squares."""
    img, alpha = blossom_cards(seed, 120, 0, True, True)
    holes = (field(seed + 6, 2.4) > 0.85)
    fill = (alpha == 0) & ~holes
    img[fill] = (206, 146, 172)
    alpha[fill] = 255
    alpha[holes & (field(seed + 7, 1.2) > -0.3)] = 0
    return img, alpha

def ivy_cards(seed):
    """Ivy hanging down a wall: three strands of thin stem with heart-shaped leaves along them, darker than a hedge's."""
    g = rng(seed)
    img = np.zeros((S, S, 3)); alpha = np.zeros((S, S))
    y, x = np.mgrid[0:S, 0:S]
    for strand in range(4):
        px = g.uniform(6, 58); py = g.uniform(-6, 8); drift = g.normal(0, 0.12)
        pts = []
        for step in range(int(g.uniform(44, 64))):
            px += drift + g.normal(0, 0.35); py += 1
            if 0 <= px < S and 0 <= py < S:
                img[int(py), int(px)] = (64, 72, 40); alpha[int(py), int(px)] = 255
            pts.append((px, py))
        for (lx, ly) in pts[3::7]:
            r = g.uniform(4.4, 6.2); a = g.uniform(-0.9, 0.9) + (np.pi / 2 if g.random() < 0.5 else -np.pi / 2)
            dx = x - lx; dy = y - ly
            u = dx * np.cos(a) + dy * np.sin(a); v = -dx * np.sin(a) + dy * np.cos(a)
            # a heart: two lobes at the stem end, a point at the tip
            lobes = np.minimum(np.hypot(u + r * 0.2, v - r * 0.45), np.hypot(u + r * 0.2, v + r * 0.45)) <= r * 0.58
            tip = (u > -r * 0.2) & (u <= r) & (np.abs(v) <= r * 0.95 * (1 - (u + r * 0.2) / (r * 1.2)))
            inside = lobes | tip
            tone = g.uniform(0.7, 1.15)
            col = np.array((56, 104, 40), float) * tone
            d = np.hypot(u, v) / r
            img[inside] = (col[None, None, :] * (1.08 - 0.25 * d[..., None]))[inside]
            vein = inside & (np.abs(v) < 0.6) & (u > -r * 0.2)
            img[vein] = np.clip(img[vein] * 1.3 + 10, 0, 255)
            rim = inside & ~np.roll(inside, 1, 0) | inside & ~np.roll(inside, -1, 1)
            img[rim] = img[rim] * 0.6
            alpha[inside] = 255
    return img, alpha

def pine_cards(seed, count=46, spread=24):
    """A black pine's pad of needles: tufts of fine dark needles fanning out from short twigs, thinning to the edge."""
    g = rng(seed)
    img = np.zeros((S, S, 3)); alpha = np.zeros((S, S))
    for k in range(count):
        rr = spread * np.sqrt(g.random()); aa = g.uniform(0, 2 * np.pi)
        cx = 32 + rr * np.cos(aa); cy = 32 + rr * np.sin(aa)
        base = np.array((44, 74, 40), float) * g.uniform(0.8, 1.25)
        for n in range(int(g.uniform(10, 18))):
            a = g.uniform(0, 2 * np.pi); length = g.uniform(4, 8)
            for t in np.linspace(0, 1, int(length * 2)):
                x = int(round(cx + np.cos(a) * length * t)); y = int(round(cy + np.sin(a) * length * t))
                if 0 <= x < S and 0 <= y < S:
                    img[y, x] = base * (0.85 + 0.35 * t) * (1.1 if np.sin(a) < 0 else 0.95)
                    alpha[y, x] = 255
        ix, iy = int(cx), int(cy)
        if 0 <= ix < S and 0 <= iy < S: img[iy, ix] = (70, 52, 40); alpha[iy, ix] = 255
    return np.clip(img, 0, 255), alpha

def frame_tex(seed, base):
    """An aluminium window frame: a flat tone with the faintest brushing along it."""
    g = rng(seed)
    return shade(flat(base), 2 * g.normal(size=(S, S)) + 3 * field(seed + 1, 14, 1.2))

def window_glass(seed):
    """Glass seen from outside: a cool tint, mostly see-through, with a soft slanting streak of sky."""
    img = np.zeros((S, S, 3)); alpha = np.zeros((S, S))
    img[:] = (148, 172, 192); alpha[:] = 110
    y, x = np.mgrid[0:S, 0:S]
    streak = np.clip(1 - np.abs((x - y) - 8) / 9.0, 0, 1)
    img += (np.array((62, 54, 44), float)[None, None, :] * streak[..., None])
    alpha += 50 * streak
    img += (field(seed, 9) * 6)[..., None]
    return img, alpha

def window_frosted(seed):
    g = rng(seed)
    img = shade(flat((226, 230, 232)), 3 * g.normal(size=(S, S)) + 4 * field(seed + 1, 3))
    return img, np.full((S, S), 215.0)

def window_curtain(seed):
    """Curtains drawn to the sides behind the glass: two cream panels in soft folds, the middle open."""
    g = rng(seed)
    img = np.zeros((S, S, 3)); alpha = np.zeros((S, S))
    for x0, x1 in ((6, 26), (38, 58)):
        xs = np.arange(x0, x1)
        fold = 14 * np.sin((xs - x0) / 4.2 * 2 * np.pi) - 6 * np.cos((xs - x0) / 9.0 * 2 * np.pi)
        img[:, x0:x1] = (238, 232, 220) + fold[None, :, None] * np.array([1, 1, 0.9])
        alpha[:, x0:x1] = 255
    img = shade(img, 2 * g.normal(size=(S, S)))
    img[58:64] -= 16
    return img, alpha

def stucco(seed, base):
    """Sprayed stucco: a fine stipple, a soft unevenness, the faintest run of grime."""
    g = rng(seed)
    img = shade(flat(base), 4.5 * g.normal(size=(S, S)) + 3 * field(seed + 1, 1.1) + 3 * field(seed + 2, 12))
    return shade(img, -np.clip(field(seed + 3, 1.2, 20), 0.8, None) * 3)

def siding(seed):
    """Lap siding in a warm white: boards two sixteenths tall, a shadow under each board's lower edge."""
    g = rng(seed)
    img = shade(flat((222, 218, 208)), 2.5 * g.normal(size=(S, S)) + 2 * field(seed + 1, 10, 1.0))
    for y0 in range(0, S, 8):
        img[y0] += 10; img[y0 + 6] -= 10; img[y0 + 7] -= 26
    return img

def tile_wall(seed):
    """Small wall tiles of a light grey in rows, each a tone of its own, in pale joints."""
    g = rng(seed)
    img = np.zeros((S, S, 3)); tone = np.zeros((S, S))
    for r in range(8):
        off = 8 if r % 2 else 0
        for c in range(5):
            x0 = (c * 16 + off) % S
            t = g.normal(0, 5)
            for x in range(x0, x0 + 16): tone[r * 8:(r + 1) * 8, x % S] = t
    img[:] = (198, 200, 198)
    img = shade(img, tone + 2 * g.normal(size=(S, S)) + 2 * field(seed + 1, 6))
    for r in range(8):
        img[r * 8, :] = (160, 162, 160)
        off = 8 if r % 2 else 0
        for c in range(5): img[r * 8:(r + 1) * 8, (c * 16 + off) % S] = (160, 162, 160)
    return img

def trim(seed, base):
    g = rng(seed)
    return shade(flat(base), 2 * g.normal(size=(S, S)) + 3 * field(seed + 1, 14, 1.0))

def concrete_smooth(seed):
    g = rng(seed)
    img = shade(flat((180, 178, 172)), 3 * g.normal(size=(S, S)) + 4 * field(seed + 1, 3) + 5 * field(seed + 2, 14))
    img[31:33] -= 10
    return img

def block_wall_foot(seed, k):
    """The lowest course of a block wall: the blocks with the splash of the road on them, grime and a little moss."""
    img = blocks(seed, (140, 138, 132), 32, 16, (110, 108, 102), 10, True, 6 + 4 * k)
    g = rng(seed + 9)
    y = np.arange(S)[:, None]
    grime = np.clip((y - 22) / 42.0, 0, 1) ** 1.4 * (26 + 10 * field(seed + 10, 3))
    img = img - grime[..., None] * np.array([1, 0.95, 0.9])
    moss = (field(seed + 11, 2.5) > 0.9) & (y > 34)
    img[moss] = img[moss] * 0.7 + np.array((70, 92, 44)) * 0.3
    return img

def paint_green(seed):
    """The green walking strip along a road's edge: green paint over the asphalt with its white line along one side."""
    img = asphalt(seed)
    all_ = np.ones((S, S), bool)
    img = paint(img, seed + 3, all_, (74, 140, 112), 0.12)
    return paint(img, seed, wobble(rows(4, 14), seed + 9), (232, 231, 224), 0.08)

def vending_lower(seed, base, dark=(38, 38, 40)):
    """A drinks machine's lower front round the tray: the row of lit buttons and the price labels under the display, the tray's dark recess."""
    g = rng(seed)
    img = shade(flat(base), 3 * g.normal(size=(S, S)))
    img[20:36, 6:40] = (226, 226, 222)
    for k in range(7):
        x = 8 + k * 4
        img[22:26, x:x + 3] = (60, 140, 230) if k % 3 else (230, 80, 70)
        img[28:33, x:x + 3] = (40, 40, 40); img[29:32, x + 1:x + 2] = (250, 250, 250)
    img[36:56, 8:56] = dark
    img[36:38, 8:56] = (70, 70, 72)
    img[56:64] = np.array(base) * 0.8
    return img

def vending_upper(seed, base):
    """The upper front round the display: the header band over it, white, with a run of coloured marks (no brand)."""
    g = rng(seed)
    img = shade(flat(base), 3 * g.normal(size=(S, S)))
    img[0:8, 6:58] = (240, 240, 236)
    for k, c in enumerate(((230, 60, 60), (60, 120, 230), (250, 200, 40), (60, 170, 80))):
        img[2:6, 10 + k * 6:13 + k * 6] = c
    img[2:6, 40:54] = (90, 90, 94)
    return img

def vending_side_c(seed, base, stripe):
    g = rng(seed)
    img = shade(flat(base), 4 * g.normal(size=(S, S)) + 5 * field(seed + 1, 10))
    img[:, 28:32] = stripe
    return img

def vending_display(seed):
    """The lit display: four shelves of cans and bottles of many colours on a dark back, a white lip with price tags."""
    g = rng(seed)
    img = flat((26, 26, 30))
    cols = [(210, 40, 40), (40, 90, 200), (240, 200, 40), (60, 160, 70), (240, 240, 240), (230, 120, 40), (120, 60, 30), (30, 160, 180), (200, 200, 90)]
    for r in range(4):
        y0 = r * 16 + 1
        for k in range(7):
            x0 = 7 + k * 7
            c = np.array(cols[g.integers(0, len(cols))], float)
            bottle = g.random() < 0.4
            img[y0 + (2 if bottle else 0):y0 + 11, x0:x0 + 5] = c
            if bottle: img[y0:y0 + 2, x0 + 1:x0 + 4] = c * 0.8
            img[y0 + 3:y0 + 5, x0:x0 + 5] = np.clip(c + 45, 0, 255)
            img[y0 + 5:y0 + 7, x0 + 1:x0 + 4] = np.clip(c * 0.7, 0, 255)
        img[y0 + 11:y0 + 15, 6:58] = (236, 236, 232)
        for k in range(7):
            x0 = 8 + k * 7
            img[y0 + 12:y0 + 14, x0:x0 + 3] = (40, 40, 40)
    img[:, 0:6] = (26, 26, 30); img[:, 58:64] = (26, 26, 30)
    return img

def vending_glass(seed):
    img = np.zeros((S, S, 3)); alpha = np.zeros((S, S))
    img[:] = (205, 218, 232); alpha[:] = 48
    y, x = np.mgrid[0:S, 0:S]
    streak = np.clip(1 - np.abs((x - y) - 14) / 7.0, 0, 1)
    alpha += 70 * streak
    return img, alpha

def ac_front(seed):
    """An outdoor unit's face, drawn where the unit's front sits on the block: the dark well of the fan at the left
    (its guard stands proud of it), a louvred panel at the right, the maker's plate under it."""
    g = rng(seed)
    img = shade(flat((214, 212, 204)), 2.5 * g.normal(size=(S, S)))
    y, x = np.mgrid[0:S, 0:S]
    d = np.hypot(x - 22, y - 44)
    img[d < 17] = (54, 56, 58)
    img[d < 4] = (120, 122, 124)
    img[(d >= 17) & (d < 18.5)] = (170, 170, 166)
    for yy in range(28, 60, 3): img[yy, 42:58] = (150, 148, 142)
    img[48:52, 44:56] = (236, 236, 232)
    img[24:26, 4:60] -= 18
    return img

def ac_guard(seed):
    """The fan guard: rings and spokes of pale steel on nothing."""
    img = np.zeros((S, S, 3)); alpha = np.zeros((S, S))
    y, x = np.mgrid[0:S, 0:S]
    d = np.hypot(x - 31.5, y - 31.5); a = np.arctan2(y - 31.5, x - 31.5)
    ring = (d < 31) & ((d % 5) < 1.6)
    spoke = (d < 31) & (np.abs(np.sin(4 * a)) < 0.07)
    hub = d < 5
    m = ring | spoke | hub
    img[m] = (206, 206, 200); img[hub] = (160, 160, 156)
    alpha[m] = 255
    return img, alpha

def mirror_orange(seed):
    g = rng(seed)
    return shade(flat((234, 112, 30)), 3 * g.normal(size=(S, S)) + 4 * field(seed + 1, 6))

def mirror_face(seed):
    """A convex traffic mirror: a sky-grey glass darkening to its rim, the sky's light caught high on the left."""
    y, x = np.mgrid[0:S, 0:S]
    d = np.hypot(x - 31.5, y - 31.5) / 31.5
    img = np.zeros((S, S, 3))
    sky = np.array((176, 196, 210), float); ground = np.array((96, 104, 100), float)
    t = np.clip((y - 10) / 44.0, 0, 1)[..., None]
    img[:] = sky * (1 - t) + ground * t
    img = img * (1 - 0.5 * np.clip(d, 0, 1) ** 3)[..., None]
    hi = np.exp(-(((x - 20) / 9.0) ** 2 + ((y - 18) / 6.0) ** 2))
    img += (np.array((70, 60, 50), float)[None, None, :] * hi[..., None])
    img[d > 0.96] = (40, 40, 44)
    return img

def boards(seed, base, battens=True):
    """Vertical wooden boards of an old house: eight boards a block, a tone and grain each, dark joints, a batten
    over every other joint (焼杉 when dark)."""
    g = rng(seed)
    img = np.zeros((S, S, 3))
    grain = 7 * field(seed + 1, 0.6, 10) + 4 * g.normal(size=(S, S))
    for k in range(8):
        x0 = k * 8
        img[:, x0:x0 + 8] = np.array(base, float) + g.normal(0, 6)
    img = shade(img, grain)
    for k in range(8):
        img[:, k * 8] -= 22
        img[:, k * 8 + 7] -= 8
    if battens:
        for k in range(0, 8, 2):
            x0 = k * 8
            img[:, x0 - 1 if x0 else 63] = np.array(base, float) * 1.25
            img[:, x0:x0 + 2] = np.array(base, float) * 1.18
    return img

def timber(seed, base):
    """A squared post: a dark wood with its grain running up."""
    g = rng(seed)
    img = shade(flat(base), 8 * field(seed + 1, 0.5, 12) + 3 * g.normal(size=(S, S)))
    img[:, 0:2] -= 14; img[:, 62:64] -= 14
    return img

def edge(color):
    img = flat(color); return img

# ---------------------------------------------------------------- the block list (same order as TownBlocks)

TEXTURES = {}

def tex(name, fn, *args):
    TEXTURES[name] = (fn, args)
    return f'{NS}:block/town/{name}'

BLOCKS = []  # (id, kind, data, korean, english, tool)

def block(i, kind, data, ko, en, tool='pickaxe'):
    BLOCKS.append((i, kind, data, ko, en, tool))

A = [tex(f'asphalt_{k}', asphalt, 100 + k) for k in range(4)]
WA = [tex(f'worn_asphalt_{k}', worn_asphalt, 200 + k) for k in range(4)]
WAC = [tex(f'worn_asphalt_cracked_{k}', worn_asphalt, 220 + k, True) for k in range(2)]
PA = tex('patched_asphalt', patched, 240)
TEXTURES['asphalt_edge_line'] = (lambda s: paint(asphalt(s), s, wobble(rows(4, 14), s + 9)), (130,))
TEXTURES['asphalt_center_line'] = (lambda s: paint(asphalt(s), s, wobble(rows(27, 37), s + 9)), (131,))
TEXTURES['asphalt_zebra'] = (lambda s: paint(asphalt(s), s, wobble(rows(18, 46), s + 9)), (132,))
TEXTURES['asphalt_stop_line'] = (lambda s: paint(asphalt(s), s, wobble(rows(22, 42), s + 9)), (133,))
TEXTURES['worn_asphalt_line'] = (lambda s: paint(worn_asphalt(s), s, wobble(rows(26, 37), s + 9, 1), (222, 219, 208), 0.25), (250,))
PAV = [tex(f'paving_{k}', pavers, 300 + k) for k in range(2)]
BW = [tex(f'block_wall_{k}', blocks, 400 + k, (140, 138, 132), 32, 16, (110, 108, 102), 10, True, 6 + 4 * k) for k in range(3)]
CAP = tex('block_wall_cap', steel, 430, (150, 148, 142))
SP = [tex(f'stone_pillar_{k}', blocks, 500 + k, (86, 84, 82), 32, 21, (50, 48, 46), 12, True, 4, (0.05, (138, 136, 132))) for k in range(2)]
PL = [tex(f'plaster_{k}', plaster, 600 + k) for k in range(3)]
WC = [tex(f'weathered_concrete_{k}', weathered, 700 + k) for k in range(3)]
KW = tex('kawara', kawara, 800)
KWS = tex('kawara_side', steel, 801, (62, 65, 72))
CI = [tex(f'corrugated_iron_{k}', corrugated, 900 + k, (204, 204, 198), 0.15 + 0.1 * k) for k in range(3)]
RCI = [tex(f'rusty_corrugated_iron_{k}', corrugated, 920 + k, (198, 196, 188), 0.55 + 0.1 * k) for k in range(2)]
CR = tex('corrugated_roof', roof_sheet, 940)
POLE = tex('concrete_pole', concrete_pole, 1000)
SLV = tex('pole_sleeve', sleeve, 1001)
STEEL = tex('steel', steel, 1002)
WSTEEL = tex('white_steel', steel, 1003, (232, 232, 228))
POR = tex('porcelain', porcelain, 1004)
TRF = tex('transformer', transformer, 1005)
LAMP = tex('lamp_housing', steel, 1006, (92, 94, 98))
LED = tex('lamp_led', led, 1007)
VT = tex('vending_top', steel, 1103, (70, 72, 74))
ACS = tex('ac_side', ac_side, 1201)
LBF = tex('letterbox_front', letterbox_front, 1300)
LBS = tex('letterbox_side', steel, 1301, (206, 206, 200))
PANES = {'slat_fence': (pane_slats, (82, 55, 38)), 'chain_link_fence': (pane_chain, (150, 152, 150)), 'metal_gate': (pane_gate, (32, 32, 34)),
         'guardrail': (pane_guardrail, (236, 236, 232)), 'balcony_rail': (pane_balcony, (40, 42, 46)), 'net': (pane_net, (232, 232, 226)),
         'curtain_window': (pane_window, (66, 48, 36))}

def tl(k): return f'{NS}:block/town/{k}'

block('asphalt', 'cube', {'tex': A, 'spin': True}, '아스팔트', 'Asphalt')
block('asphalt_slab', 'slab', {'tex': A, 'full': 'asphalt'}, '아스팔트 반 블록', 'Asphalt Slab')
block('asphalt_edge_line', 'facing_top', {'top': tl('asphalt_edge_line'), 'side': A[0]}, '아스팔트 (가장자리 선)', 'Asphalt with Edge Line')
block('asphalt_edge_line_slab', 'facing_slab', {'top': tl('asphalt_edge_line'), 'side': A[0]}, '아스팔트 반 블록 (가장자리 선)', 'Asphalt Slab with Edge Line')
block('asphalt_center_line', 'axis_top', {'top': tl('asphalt_center_line'), 'side': A[1]}, '아스팔트 (중앙선)', 'Asphalt with Centre Line')
block('asphalt_zebra', 'axis_top', {'top': tl('asphalt_zebra'), 'side': A[2]}, '아스팔트 (횡단보도)', 'Asphalt with Zebra Bar')
block('asphalt_stop_line', 'axis_top', {'top': tl('asphalt_stop_line'), 'side': A[3]}, '아스팔트 (정지선)', 'Asphalt with Stop Line')
block('worn_asphalt', 'cube', {'tex': WA, 'spin': True}, '낡은 아스팔트', 'Worn Asphalt')
block('worn_asphalt_cracked', 'cube', {'tex': WAC, 'spin': True}, '갈라진 아스팔트', 'Cracked Worn Asphalt')
block('patched_asphalt', 'cube', {'tex': [PA], 'spin': True}, '보수한 아스팔트', 'Patched Asphalt')
block('worn_asphalt_line', 'axis_top', {'top': tl('worn_asphalt_line'), 'side': WA[0]}, '낡은 아스팔트 (흐린 선)', 'Worn Asphalt with Faded Line')
block('paving', 'cube', {'tex': PAV, 'spin': False}, '보도블록', 'Paving')
block('paving_slab', 'slab', {'tex': PAV, 'full': 'paving'}, '보도블록 반 블록', 'Paving Slab')
block('block_wall', 'cube', {'tex': BW, 'spin': False}, '콘크리트 블록 담', 'Concrete Block Wall')
block('block_wall_slab', 'slab', {'tex': BW, 'top': CAP, 'full': 'block_wall'}, '블록 담 갓돌', 'Block Wall Coping')
block('stone_pillar', 'cube', {'tex': SP, 'spin': False}, '거친 돌기둥', 'Rough Stone Pillar')
block('plaster', 'cube', {'tex': PL, 'spin': False}, '회벽', 'Plaster')
block('weathered_concrete', 'cube', {'tex': WC, 'spin': False}, '낡은 콘크리트', 'Weathered Concrete')
block('kawara', 'axis_cube', {'top': KW, 'side': KWS}, '기와', 'Kawara')
block('kawara_slab', 'axis_slab', {'top': KW, 'side': KWS, 'full': 'kawara'}, '기와 반 블록', 'Kawara Slab')
block('corrugated_iron', 'cube', {'tex': CI, 'spin': False}, '함석', 'Corrugated Iron')
block('rusty_corrugated_iron', 'cube', {'tex': RCI, 'spin': False}, '녹슨 함석', 'Rusty Corrugated Iron')
block('corrugated_roof', 'axis_cube', {'top': CR, 'side': CR}, '함석 지붕', 'Corrugated Roof')
block('corrugated_roof_slab', 'axis_slab', {'top': CR, 'side': CR, 'full': 'corrugated_roof'}, '함석 지붕 반 블록', 'Corrugated Roof Slab')
block('slat_fence', 'pane', {}, '나무 가로살 울타리', 'Slat Fence', 'axe')
block('chain_link_fence', 'pane', {}, '철망', 'Chain-Link Fence')
block('metal_gate', 'pane', {}, '철문', 'Metal Gate')
block('guardrail', 'pane', {}, '가드레일', 'Guardrail')
block('balcony_rail', 'pane', {}, '베란다 난간', 'Balcony Rail')
block('net', 'pane', {}, '그물', 'Net', None)
block('curtain_window', 'pane', {}, '커튼 창문', 'Curtained Window', None)
block('utility_pole', 'post', {'from': 5, 'to': 11, 'tex': POLE}, '전주', 'Utility Pole')
block('pole_sleeve', 'post', {'from': 4.5, 'to': 11.5, 'tex': SLV}, '전주 보호대', 'Pole Sleeve')
block('pipe_post', 'post', {'from': 7, 'to': 9, 'tex': WSTEEL}, '쇠기둥', 'Pipe Post')
block('pole_crossarm', 'crossarm', {'pole': True}, '전주 완금', 'Pole Crossarm')
block('pole_arm', 'crossarm', {'pole': False}, '완금 끝', 'Crossarm End')
block('pole_transformer', 'shaped', {}, '변압기', 'Pole Transformer')
block('street_lamp_arm', 'shaped', {}, '가로등 팔', 'Street Lamp Arm')
block('street_lamp', 'shaped', {}, '가로등', 'Street Lamp')
block('air_conditioner', 'shaped', {}, '에어컨 실외기', 'Air Conditioner')
block('letterbox', 'shaped', {}, '우편함', 'Letterbox')
block('fallen_petals', 'litter', {}, '떨어진 꽃잎', 'Fallen Petals', None)
block('vending_machine', 'vending', {'colour': 'red'}, '자판기', 'Vending Machine')
# roofs at their true pitch: drawn in code (RoofModel), so they get an item model and no block state file here
def mc(name): return f'minecraft:block/{name}'
block('roof_kawara', 'roof', {'top': KW, 'side': KWS, 'under': PL[0]}, '기와 지붕', 'Kawara Roof')
block('roof_sheet', 'roof', {'top': CR, 'side': CR, 'under': CI[0]}, '함석 지붕', 'Sheet Roof')
block('roof_black', 'roof', {'top': mc('polished_blackstone'), 'side': mc('polished_blackstone'), 'under': PL[0]}, '검은 지붕', 'Black Roof')
block('roof_blue', 'roof', {'top': mc('dark_prismarine'), 'side': mc('dark_prismarine'), 'under': PL[1]}, '파란 지붕', 'Blue Roof')
block('roof_slate', 'roof', {'top': mc('stone_bricks'), 'side': mc('stone_bricks'), 'under': PL[2]}, '석판 지붕', 'Slate Roof')
block('roof_red', 'roof', {'top': mc('bricks'), 'side': mc('bricks'), 'under': PL[0]}, '붉은 지붕', 'Red Roof')
block('roof_brown', 'roof', {'top': mc('mud_bricks'), 'side': mc('mud_bricks'), 'under': PL[1]}, '갈색 지붕', 'Brown Roof')
block('roof_copper', 'roof', {'top': mc('oxidized_cut_copper'), 'side': mc('oxidized_cut_copper'), 'under': PL[0]}, '청동 지붕', 'Copper Roof')
block('roof_rust', 'roof', {'top': mc('exposed_cut_copper'), 'side': mc('exposed_cut_copper'), 'under': PL[2]}, '구리 지붕', 'Rusty Copper Roof')
block('roof_teal', 'roof', {'top': mc('weathered_cut_copper'), 'side': mc('weathered_cut_copper'), 'under': PL[1]}, '녹청 지붕', 'Teal Copper Roof')

# 2.48.0: leaves on cards, windows with sash frames, the houses' materials, the green strip, more fences and furniture.
# (The textures above keep their seeds; these take seeds of their own.)
LEAF = [tex(f'leaf_cards_{k}', leaf_cards, 2000 + k) for k in range(3)]
LMASS = tex('leaf_mass', leaf_mass, 2010)
BLOS = [tex(f'blossom_cards_{k}', blossom_cards, 2020 + k) for k in range(3)]
BMASS = tex('blossom_mass', blossom_mass, 2030)
IVY = [tex(f'ivy_cards_{k}', ivy_cards, 2040 + k) for k in range(2)]
PINE = [tex(f'pine_cards_{k}', pine_cards, 2050 + k) for k in range(2)]
WFD = tex('window_frame_dark', frame_tex, 2100, (44, 46, 50))
WFW = tex('window_frame_white', frame_tex, 2101, (232, 232, 228))
WG = tex('window_glass', window_glass, 2102)
WFR = tex('window_frosted', window_frosted, 2103)
WCU = tex('window_curtain', window_curtain, 2104)
STW = [tex(f'stucco_white_{k}', stucco, 2200 + k, (238, 236, 230)) for k in range(2)]
STC = [tex(f'stucco_cream_{k}', stucco, 2210 + k, (228, 216, 192)) for k in range(2)]
STP = [tex(f'stucco_pink_{k}', stucco, 2220 + k, (224, 198, 182)) for k in range(2)]
STG = [tex(f'stucco_gray_{k}', stucco, 2230 + k, (186, 186, 182)) for k in range(2)]
SID = [tex(f'siding_{k}', siding, 2240 + k) for k in range(2)]
TIW = [tex(f'tile_wall_{k}', tile_wall, 2250 + k) for k in range(2)]
TRD = tex('trim_dark', trim, 2260, (48, 50, 54))
TRW = tex('trim_white', trim, 2261, (236, 236, 232))
CSM = [tex(f'concrete_smooth_{k}', concrete_smooth, 2270 + k) for k in range(2)]
BWF = [tex(f'block_wall_foot_{k}', block_wall_foot, 400 + k, k) for k in range(3)]
BWD = [tex(f'block_wall_dark_{k}', blocks, 2300 + k, (74, 74, 76), 32, 16, (118, 118, 116), 9, True, 5 + 3 * k) for k in range(2)]
BWDC = tex('block_wall_dark_cap', steel, 2310, (70, 70, 72))
TEXTURES['asphalt_green'] = (paint_green, (2320,))
VLR = tex('vending_lower_red', vending_lower, 2400, (186, 26, 32))
VLW = tex('vending_lower_white', vending_lower, 2401, (236, 236, 232))
VUR = tex('vending_upper_red', vending_upper, 2402, (186, 26, 32))
VUW = tex('vending_upper_white', vending_upper, 2403, (236, 236, 232))
VSR = tex('vending_side_red', vending_side_c, 2404, (184, 26, 32), (232, 232, 230))
VSW = tex('vending_side_white', vending_side_c, 2405, (232, 232, 228), (196, 196, 192))
VDI = tex('vending_display', vending_display, 2406)
VGL = tex('vending_glass', vending_glass, 2407)
VDK = tex('vending_dark', steel, 2408, (38, 38, 40))
ACF = tex('ac_front', ac_front, 1200)
ACG = tex('ac_guard', ac_guard, 2420)
MOR = tex('mirror_orange', mirror_orange, 2430)
MFC = tex('mirror_face', mirror_face, 2431)
PANES['slat_fence_dark'] = (lambda s: pane_slats(s, (40, 36, 34)), (40, 36, 34))
PANES['slat_fence_light'] = (lambda s: pane_slats(s, (176, 112, 56)), (176, 112, 56))
# the panes' seeds as they were when they were numbered by their place (2.46.0), so their pictures stay the same
PANE_SEEDS = {'slat_fence': 1454, 'chain_link_fence': 1456, 'metal_gate': 1458, 'guardrail': 1460, 'balcony_rail': 1462, 'net': 1464,
              'curtain_window': 1466, 'slat_fence_dark': 1470, 'slat_fence_light': 1472}
for _old in ('vending_front_lower', 'vending_front_upper', 'vending_side'): (TEX / f'{_old}.png').unlink(missing_ok=True)

block('hedge', 'foliage', {'mass': LMASS}, '생울타리', 'Hedge', 'hoe')
block('canopy', 'foliage', {'mass': LMASS}, '나뭇잎 (수관)', 'Tree Canopy', 'hoe')
block('cherry_blossom', 'foliage', {'mass': BMASS}, '벚꽃 무리', 'Cherry Blossom', 'hoe')
block('ivy', 'ivy', {'tex': IVY[0]}, '담쟁이', 'Ivy', 'hoe')
block('cherry_branch', 'branch', {}, '벚나무 가지', 'Cherry Branch', 'axe')
block('pine_needles', 'foliage', {'mass': PINE[0]}, '솔잎', 'Pine Needles', 'hoe')
block('window', 'window', {'frame': WFD, 'glass': WG}, '창문 (검은 틀)', 'Window (Dark Frame)', None)
block('window_curtain', 'window', {'frame': WFD, 'glass': WG}, '커튼 창문 (검은 틀)', 'Curtained Window (Dark Frame)', None)
block('window_frosted', 'window', {'frame': WFD, 'glass': WFR}, '불투명 창문 (검은 틀)', 'Frosted Window (Dark Frame)', None)
block('window_white', 'window', {'frame': WFW, 'glass': WG}, '창문 (흰 틀)', 'Window (White Frame)', None)
block('window_white_curtain', 'window', {'frame': WFW, 'glass': WG}, '커튼 창문 (흰 틀)', 'Curtained Window (White Frame)', None)
block('stucco_white', 'cube', {'tex': STW, 'spin': False}, '흰 스터코 벽', 'White Stucco')
block('stucco_cream', 'cube', {'tex': STC, 'spin': False}, '크림색 스터코 벽', 'Cream Stucco')
block('stucco_pink', 'cube', {'tex': STP, 'spin': False}, '분홍빛 스터코 벽', 'Pink Stucco')
block('stucco_gray', 'cube', {'tex': STG, 'spin': False}, '회색 스터코 벽', 'Grey Stucco')
block('siding', 'cube', {'tex': SID, 'spin': False}, '사이딩 벽', 'Lap Siding')
block('tile_wall', 'cube', {'tex': TIW, 'spin': False}, '타일 벽', 'Tile Wall')
block('trim_dark', 'cube', {'tex': [TRD], 'spin': False}, '검은 몰딩', 'Dark Trim')
block('trim_white', 'cube', {'tex': [TRW], 'spin': False}, '흰 몰딩', 'White Trim')
block('concrete_smooth', 'cube', {'tex': CSM, 'spin': False}, '매끈한 콘크리트', 'Smooth Concrete')
block('block_wall_foot', 'cube', {'tex': BWF, 'spin': False}, '블록 담 아랫단', 'Block Wall Foot')
block('block_wall_dark', 'cube', {'tex': BWD, 'spin': False}, '검은 블록 담', 'Dark Block Wall')
block('block_wall_dark_slab', 'slab', {'tex': BWD, 'top': BWDC, 'full': 'block_wall_dark'}, '검은 블록 담 갓돌', 'Dark Block Wall Coping')
block('asphalt_green', 'facing_top', {'top': tl('asphalt_green'), 'side': A[2]}, '아스팔트 (녹색 보행로)', 'Asphalt with Green Strip')
block('slat_fence_dark', 'pane', {}, '검은 가로살 울타리', 'Dark Slat Fence', 'axe')
block('slat_fence_light', 'pane', {}, '밝은 가로살 울타리', 'Light Slat Fence', 'axe')
WBD = [tex(f'wood_boards_dark_{k}', boards, 2500 + k, (46, 38, 34)) for k in range(2)]
WBB = [tex(f'wood_boards_{k}', boards, 2510 + k, (112, 84, 60)) for k in range(2)]
TMD = tex('timber_dark', timber, 2520, (70, 50, 38))
block('wood_boards_dark', 'cube', {'tex': WBD, 'spin': False}, '검은 판자벽', 'Charred Wood Boards', 'axe')
block('wood_boards', 'cube', {'tex': WBB, 'spin': False}, '갈색 판자벽', 'Wood Boards', 'axe')
block('timber_dark', 'cube', {'tex': [TMD], 'spin': False}, '짙은 목재 기둥', 'Dark Timber', 'axe')
block('vending_machine_white', 'vending', {'colour': 'white'}, '흰 자판기', 'White Vending Machine')
block('mirror_post', 'post', {'from': 7.1, 'to': 8.9, 'tex': MOR}, '반사경 기둥', 'Mirror Post')
block('traffic_mirror', 'mirror', {}, '도로 반사경', 'Traffic Mirror')

# ---------------------------------------------------------------- models

MODELS = {}; STATES = {}; ITEMS = {}

def model(name, data): MODELS[name] = data; return f'{NS}:block/town/{name}'
def face(t, uv=None, cull=None):
    f = {'texture': t}
    if uv: f['uv'] = uv
    if cull: f['cullface'] = cull
    return f

def cube(name, t): return model(name, {'parent': 'minecraft:block/cube_all', 'textures': {'all': t}})
def top_cube(name, top, side, bottom=None): return model(name, {'parent': 'minecraft:block/cube_bottom_top', 'textures': {'top': top, 'side': side, 'bottom': bottom or side}})
def slab(name, top, side, bottom=None):
    b = bottom or top
    return (model(name, {'parent': 'minecraft:block/slab', 'textures': {'top': top, 'side': side, 'bottom': b}}),
            model(name + '_top', {'parent': 'minecraft:block/slab_top', 'textures': {'top': top, 'side': side, 'bottom': b}}))

def element(frm, to, faces, rot=None):
    e = {'from': frm, 'to': to, 'faces': faces}
    if rot: e['rotation'] = rot
    return e

def box_faces(t, frm, to, skip=()):
    """All six faces of a box with the texture mapped to the box's own extent."""
    x0, y0, z0 = frm; x1, y1, z1 = to
    out = {'north': face(t, [16 - x1, 16 - y1, 16 - x0, 16 - y0]), 'south': face(t, [x0, 16 - y1, x1, 16 - y0]),
           'west': face(t, [z0, 16 - y1, z1, 16 - y0]), 'east': face(t, [16 - z1, 16 - y1, 16 - z0, 16 - y0]),
           'up': face(t, [x0, z0, x1, z1]), 'down': face(t, [x0, 16 - z1, x1, 16 - z0])}
    for k in skip: out.pop(k)
    return out

def custom(name, elements, particle, ao=True):
    """A model of its own elements. A face names a texture by a variable ('#t0'), never by its path: the game reads
    a face's texture as the name of an entry in the model's textures, so each path used gets an entry."""
    textures = {'particle': particle}; names = {}
    for e in elements:
        for f in e['faces'].values():
            t = f['texture']
            if t.startswith('#'): continue
            if t not in names:
                names[t] = f't{len(names)}'; textures[names[t]] = t
            f['texture'] = '#' + names[t]
    d = {'textures': textures, 'elements': elements}
    if not ao: d['ambientocclusion'] = False
    return model(name, d)

def rotations(m, uv=False):
    return {'north': {'model': m}, 'east': {'model': m, 'y': 90}, 'south': {'model': m, 'y': 180}, 'west': {'model': m, 'y': 270}}

for (i, kind, d, ko, en, tool) in BLOCKS:
    if kind == 'cube':
        ms = [cube(f'{i}_{k}', t) for k, t in enumerate(d['tex'])]
        ys = (0, 90, 180, 270) if d['spin'] else (0,)
        STATES[i] = {'variants': {'': [dict({'model': m}, **({'y': y} if y else {})) for m in ms for y in ys]}}
        ITEMS[i] = ms[0]
    elif kind == 'slab':
        top = d.get('top')
        pairs = [slab(f'{i}_{k}', top or t, t) for k, t in enumerate(d['tex'])]
        full = [f'{NS}:block/town/{d["full"]}_{k}' for k in range(len(d['tex']))]
        STATES[i] = {'variants': {'type=bottom': [{'model': b} for b, _ in pairs], 'type=top': [{'model': t} for _, t in pairs],
                                  'type=double': [{'model': f} for f in full]}}
        ITEMS[i] = pairs[0][0]
    elif kind in ('facing_top', 'axis_top', 'axis_cube'):
        m = top_cube(i, d['top'], d['side'])
        if kind == 'facing_top':
            STATES[i] = {'variants': {f'facing={f}': v for f, v in rotations(m).items()}}
        else:
            STATES[i] = {'variants': {'axis=x': {'model': m}, 'axis=z': {'model': m, 'y': 90}}}
        ITEMS[i] = m
    elif kind == 'facing_slab':
        b, t = slab(i, d['top'], d['side'], d['side'])
        full = model(i + '_double', {'parent': 'minecraft:block/cube_bottom_top', 'textures': {'top': d['top'], 'side': d['side'], 'bottom': d['side']}})
        v = {}
        for typ, m in (('bottom', b), ('top', t), ('double', full)):
            for f, r in rotations(m).items(): v[f'facing={f},type={typ}'] = r
        STATES[i] = {'variants': v}
        ITEMS[i] = b
    elif kind == 'axis_slab':
        b, t = slab(i, d['top'], d['side'], d['side'])
        full = f'{NS}:block/town/{d["full"]}'
        v = {}
        for typ, m in (('bottom', b), ('top', t), ('double', full)):
            v[f'axis=x,type={typ}'] = {'model': m}; v[f'axis=z,type={typ}'] = {'model': m, 'y': 90}
        STATES[i] = {'variants': v}
        ITEMS[i] = b
    elif kind == 'pane':
        fn, ec = PANES[i]
        TEXTURES[i] = (lambda s, fn=fn: fn(s), (PANE_SEEDS[i],))
        TEXTURES[i + '_edge'] = (lambda s, c=ec: edge(c), (0,))
        t = {'pane': tl(i), 'edge': tl(i + '_edge')}
        names = {}
        for part in ('post', 'side', 'side_alt', 'noside', 'noside_alt'):
            names[part] = model(f'{i}_{part}', {'parent': f'minecraft:block/template_glass_pane_{part}', 'textures': t})
        mp = [{'apply': {'model': names['post']}},
              {'apply': {'model': names['side']}, 'when': {'north': 'true'}}, {'apply': {'model': names['side'], 'y': 90}, 'when': {'east': 'true'}},
              {'apply': {'model': names['side_alt']}, 'when': {'south': 'true'}}, {'apply': {'model': names['side_alt'], 'y': 90}, 'when': {'west': 'true'}},
              {'apply': {'model': names['noside']}, 'when': {'north': 'false'}}, {'apply': {'model': names['noside_alt']}, 'when': {'east': 'false'}},
              {'apply': {'model': names['noside_alt'], 'y': 90}, 'when': {'south': 'false'}}, {'apply': {'model': names['noside'], 'y': 270}, 'when': {'west': 'false'}}]
        STATES[i] = {'multipart': mp}
        ITEMS[i] = ('generated', tl(i))
    elif kind == 'post':
        # drawn round in code (client/town/PoleModel); this square one is only the item's
        a, b = d['from'], d['to']
        m = custom(i, [element([a, 0, a], [b, 16, b], box_faces(d['tex'], [a, 0, a], [b, 16, b]))], d['tex'])
        ITEMS[i] = m
    elif kind == 'crossarm':
        els = []
        if d['pole']: els.append(element([5, 0, 5], [11, 16, 11], box_faces(POLE, [5, 0, 5], [11, 16, 11])))
        els.append(element([0, 11, 7], [16, 13.5, 9], box_faces(STEEL, [0, 11, 7], [16, 13.5, 9])))
        for x0 in ((1.5, 12.5) if d['pole'] else (1.5, 7, 12.5)):
            els.append(element([x0, 13.5, 7.25], [x0 + 2, 16, 8.75], box_faces(POR, [x0, 13.5, 7.25], [x0 + 2, 16, 8.75])))
        m = custom(i, els, STEEL)
        # the arm through a pole is drawn in code with the pole round (PoleModel); the arm's end keeps this model
        if not d['pole']: STATES[i] = {'variants': {'axis=x': {'model': m}, 'axis=z': {'model': m, 'y': 90}}}
        ITEMS[i] = m
    elif kind == 'shaped':
        if i == 'pole_transformer':
            els = [element([4, 1, 7], [12, 14, 15], box_faces(TRF, [4, 1, 7], [12, 14, 15])),
                   element([4, 1, 7], [12, 14, 15], box_faces(TRF, [4, 1, 7], [12, 14, 15]), {'angle': 45, 'axis': 'y', 'origin': [8, 7, 11]}),
                   element([3.5, 14, 6.5], [12.5, 15, 15.5], box_faces(LAMP, [3.5, 14, 6.5], [12.5, 15, 15.5])),
                   element([6, 15, 9], [7, 16.5, 10], box_faces(POR, [6, 15, 9], [7, 16.5, 10])),
                   element([9, 15, 12], [10, 16.5, 13], box_faces(POR, [9, 15, 12], [10, 16.5, 13])),
                   element([7, 6, 15], [9, 9, 16], box_faces(STEEL, [7, 6, 15], [9, 9, 16]))]
            p = TRF
        elif i == 'street_lamp_arm':
            els = [element([7, 10, 0], [9, 12, 16], box_faces(STEEL, [7, 10, 0], [9, 12, 16]))]; p = STEEL
        elif i == 'street_lamp':
            f = box_faces(LAMP, [4, 9, 2], [12, 12, 11]); f['down'] = face(LED, [4, 5, 12, 14])
            els = [element([7, 10, 10], [9, 12, 16], box_faces(STEEL, [7, 10, 10], [9, 12, 16])), element([4, 9, 2], [12, 12, 11], f)]; p = LAMP
        elif i == 'air_conditioner':
            f = box_faces(ACS, [1, 0, 5], [15, 10, 16]); f['north'] = face(ACF, [1, 3, 15, 13])
            els = [element([1, 0, 5], [15, 10, 16], f)]; p = ACS
        elif i == 'letterbox':
            f = box_faces(LBS, [4, 5, 12], [12, 11, 16]); f['north'] = face(LBF, [4, 5, 12, 11])
            els = [element([4, 5, 12], [12, 11, 16], f)]; p = LBS
        m = custom(i, els, p)
        # the air conditioner is drawn in code since 2.48.0 (FurnitureModel); this model is only its item's
        if i != 'air_conditioner': STATES[i] = {'variants': {f'facing={f}': v for f, v in rotations(m).items()}}
        ITEMS[i] = m
    elif kind == 'litter':
        f = {'up': face(tl(i)), 'down': face(tl(i))}
        m = custom(i, [element([0, 0.25, 0], [16, 0.25, 16], f)], tl(i), ao=False)
        STATES[i] = {'variants': {'': [dict({'model': m}, **({'y': y} if y else {})) for y in (0, 90, 180, 270)]}}
        ITEMS[i] = ('generated', tl(i))
    elif kind == 'roof':
        # in the hand and the inventory, a plate tipped at the roof's slope
        f = box_faces(d['top'], [0, 4, 0], [16, 7, 16]); f['up'] = face(d['top']); f['down'] = face(d['under'])
        for k in ('north', 'south', 'east', 'west'): f[k] = face(d['side'], [0, 9, 16, 12])
        ITEMS[i] = custom(i + '_item', [element([0, 4, 0], [16, 7, 16], f, {'angle': -22.5, 'axis': 'x', 'origin': [8, 6, 8], 'rescale': True})], d['top'])
    elif kind == 'vending':
        # drawn in code since 2.48.0 (FurnitureModel); the item is the machine's lower half as a box
        vs, vl = tl('vending_side_' + d['colour']), tl('vending_lower_' + d['colour'])
        lf = box_faces(vs, [0, 0, 2], [16, 16, 16]); lf['north'] = face(vl, [0, 0, 16, 16]); lf['down'] = face(VT); lf['up'] = face(VT)
        ITEMS[i] = custom(i + '_item', [element([0, 0, 2], [16, 16, 16], lf)], vs)
        for old_m in ('_lower', '_upper'):
            (ASSETS / 'models/block/town' / f'{i}{old_m}.json').unlink(missing_ok=True)
    elif kind == 'foliage':
        # drawn in code (FoliageModel); in the hand, a block of the leaves' mass
        ITEMS[i] = cube(i + '_item', d['mass'])
    elif kind == 'ivy':
        ITEMS[i] = ('generated', d['tex'])
    elif kind == 'branch':
        ITEMS[i] = custom(i + '_item', [element([5.5, 0, 5.5], [10.5, 16, 10.5], box_faces(mc('cherry_log'), [5.5, 0, 5.5], [10.5, 16, 10.5]))], mc('cherry_log'))
    elif kind == 'window':
        # drawn in code (WindowModel); in the hand, the frame with its glass
        fr = box_faces(d['frame'], [0, 0, 0], [16, 16, 2.5])
        gl = {'north': face(d['glass'], [1.5, 1.5, 14.5, 14.5]), 'south': face(d['glass'], [1.5, 1.5, 14.5, 14.5])}
        ITEMS[i] = custom(i + '_item', [element([0, 0, 0], [16, 16, 2.5], fr), element([1.5, 1.5, 1.2], [14.5, 14.5, 1.2], gl)], d['frame'])
    elif kind == 'mirror':
        f = box_faces(MOR, [1, 2.5, 6], [15, 16, 7.5]); f['north'] = face(MFC, [1, 0, 15, 13.5])
        ITEMS[i] = custom(i + '_item', [element([7.1, 0, 7.1], [8.9, 8, 8.9], box_faces(MOR, [7.1, 0, 7.1], [8.9, 8, 8.9])), element([1, 2.5, 6], [15, 16, 7.5], f)], MOR)

# the roofs' ridge caps (named by TownBlocks) and the fallen petals, added after the panes so their seeds stay as they were
KWR = tex('kawara_ridge', kawara_ridge, 802)
FLS = tex('roof_flashing', flashing, 941)
PET = tex('fallen_petals', petals, 1500)

# ---------------------------------------------------------------- write everything

def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=1) + '\n', encoding='utf-8')

def main():
    for name, (fn, args) in TEXTURES.items():
        out = fn(*args)
        if isinstance(out, tuple): save(name, out[0], out[1])
        else: save(name, out)
    for name, data in MODELS.items(): write(ASSETS / 'models/block/town' / f'{name}.json', data)
    for i, data in STATES.items(): write(ASSETS / 'blockstates' / f'{i}.json', data)
    # blocks whose models are built in code have no block state file
    for (i, kind, d, ko, en, tool) in BLOCKS:
        if i not in STATES: (ASSETS / 'blockstates' / f'{i}.json').unlink(missing_ok=True)
    for i, m in ITEMS.items():
        data = {'parent': 'minecraft:item/generated', 'textures': {'layer0': m[1]}} if isinstance(m, tuple) else {'parent': m}
        write(ASSETS / 'models/item' / f'{i}.json', data)
    data = RES / f'data/{NS}'
    for (i, kind, d, ko, en, tool) in BLOCKS:
        entry = {'type': 'minecraft:item', 'name': f'{NS}:{i}'}
        pool = {'rolls': 1, 'entries': [entry], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}
        if kind == 'slab' or kind in ('axis_slab', 'facing_slab'):
            entry['functions'] = [{'function': 'minecraft:set_count', 'count': 2, 'conditions': [{'condition': 'minecraft:block_state_property', 'block': f'{NS}:{i}', 'properties': {'type': 'double'}}]}]
        if kind == 'vending':
            pool['conditions'].append({'condition': 'minecraft:block_state_property', 'block': f'{NS}:{i}', 'properties': {'half': 'lower'}})
        write(data / 'loot_tables/blocks' / f'{i}.json', {'type': 'minecraft:block', 'pools': [pool]})
    for tool in ('pickaxe', 'axe', 'hoe'):
        write(RES / f'data/minecraft/tags/blocks/mineable/{tool}.json',
              {'replace': False, 'values': [f'{NS}:{i}' for (i, k, d, ko, en, t) in BLOCKS if t == tool]})
    for lang, col in (('ko_kr', 3), ('en_us', 4)):
        p = ASSETS / f'lang/{lang}.json'
        names = json.loads(p.read_text(encoding='utf-8'))
        for b in BLOCKS: names[f'block.{NS}.{b[0]}'] = b[col]
        names[f'itemGroup.{NS}.town'] = '보탠드 마을' if lang == 'ko_kr' else 'Botand Town'
        p.write_text(json.dumps(names, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(f'{len(BLOCKS)} blocks, {len(TEXTURES)} textures, {len(MODELS)} models')

if __name__ == '__main__':
    main()
