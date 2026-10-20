#!/usr/bin/env python3
"""Draws the seaside town's block textures (64x) and writes their models, block states, item models, loot tables,
tool tags and names: python3 tools/map/blocks/make_blocks.py

The blocks are dev.sangwoo.celestialfall.town.TownBlocks; the list below names them in the same order. Every picture is
drawn from seeded noise (no image is copied from anywhere), so a run always gives the same files. The materials follow
the two photos the seaside map is built after (tools/map/town/PHOTO_SCENES.md): fine dark asphalt with thin paint,
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
    """The lot's old asphalt: light, coarse and brownish, its stones standing out, cracks with weeds in some."""
    g = rng(seed)
    img = shade(flat((124, 118, 108)), 9 * g.normal(size=(S, S)) + 7 * field(seed + 1, 2.5) + 6 * field(seed + 2, 12))
    img = stones(img, seed + 3, 0.11, [(158, 152, 142), (98, 94, 88), (176, 166, 150), (86, 84, 82), (140, 128, 112)], big=0.5)
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

def vending_front_lower(seed):
    """The machine's lower front: the row of buttons under the display, coin and note slots, the dark tray."""
    g = rng(seed)
    img = shade(flat((186, 26, 32)), 4 * g.normal(size=(S, S)))
    img[0:10, 4:60] = (220, 220, 216)
    for k in range(8):
        x = 6 + k * 7; img[3:7, x:x + 4] = (70, 150, 230)
    img[14:34, 40:58] = (196, 198, 200); img[18:22, 44:54] = (40, 40, 42); img[26:29, 44:54] = (40, 40, 42)
    img[40:58, 6:46] = (28, 28, 30); img[40:43, 6:46] = (60, 60, 62)
    img[60:64] = (60, 16, 20)
    return img

def vending_front_upper(seed):
    """The lit display: four shelves of cans and bottles of many colours, price tags, a bright header."""
    g = rng(seed)
    img = flat((244, 244, 240))
    img[0:8] = (186, 26, 32)
    cols = [(210, 40, 40), (40, 90, 200), (240, 200, 40), (60, 160, 70), (240, 240, 240), (230, 120, 40), (120, 60, 30), (30, 30, 30)]
    for r in range(4):
        y0 = 10 + r * 12
        for k in range(7):
            x0 = 4 + k * 8
            c = cols[g.integers(0, len(cols))]
            img[y0:y0 + 8, x0:x0 + 6] = c
            img[y0 + 2:y0 + 4, x0:x0 + 6] = np.clip(np.array(c) + 40, 0, 255)
            img[y0 + 9:y0 + 11, x0:x0 + 6] = (250, 250, 250)
            img[y0 + 9:y0 + 11, x0 + 1:x0 + 4] = (40, 40, 40)
    img[:, 0:3] = (186, 26, 32); img[:, 61:64] = (186, 26, 32)
    return img

def vending_side(seed):
    g = rng(seed)
    img = shade(flat((184, 26, 32)), 4 * g.normal(size=(S, S)) + 5 * field(seed + 1, 10))
    img[:, 28:32] = (232, 232, 230)
    return img

def ac_front(seed):
    """An outdoor unit's face: a round fan guard on the left, a louvred panel on the right."""
    g = rng(seed)
    img = shade(flat((214, 210, 200)), 2.5 * g.normal(size=(S, S)))
    y, x = np.mgrid[0:S, 0:S]
    d = np.hypot(x - 24, y - 32)
    img[d < 20] = (70, 72, 74)
    ring = (d < 20) & ((np.round(d) % 4) == 0)
    img[ring] = (200, 200, 196)
    spoke = (d < 20) & ((np.abs(x - 24) < 1) | (np.abs(y - 32) < 1))
    img[spoke] = (190, 190, 186)
    for yy in range(14, 52, 4): img[yy, 48:60] = (150, 148, 142)
    return img

def ac_side(seed):
    img = steel(seed, (210, 206, 196))
    img[::5, :] -= 14
    return img

def letterbox_front(seed):
    img = steel(seed, (206, 206, 200))
    img[22:26, 14:50] = (40, 40, 42)
    return img

def pane_slats(seed):
    """Dark brown horizontal slats with a gap of sky between each."""
    g = rng(seed)
    img = np.zeros((S, S, 3)); alpha = np.zeros((S, S))
    grain = 9 * field(seed + 1, 10, 0.6) + 4 * g.normal(size=(S, S))
    for k in range(8):
        y0 = k * 8
        img[y0:y0 + 7] = (90, 60, 41) + np.array([g.normal(0, 7)] * 3)
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
TEXTURES['worn_asphalt_line'] = (lambda s: paint(worn_asphalt(s), s, wobble(rows(28, 35), s + 9, 1), (214, 210, 198), 0.45), (250,))
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
VFL = tex('vending_front_lower', vending_front_lower, 1100)
VFU = tex('vending_front_upper', vending_front_upper, 1101)
VS = tex('vending_side', vending_side, 1102)
VT = tex('vending_top', steel, 1103, (70, 72, 74))
ACF = tex('ac_front', ac_front, 1200)
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
block('vending_machine', 'vending', {}, '자판기', 'Vending Machine')

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
    d = {'textures': {'particle': particle}, 'elements': elements}
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
        TEXTURES[i] = (lambda s, fn=fn: fn(s), (1400 + len(TEXTURES),))
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
        a, b = d['from'], d['to']
        m = custom(i, [element([a, 0, a], [b, 16, b], box_faces(d['tex'], [a, 0, a], [b, 16, b]))], d['tex'])
        STATES[i] = {'variants': {'': {'model': m}}}
        ITEMS[i] = m
    elif kind == 'crossarm':
        els = []
        if d['pole']: els.append(element([5, 0, 5], [11, 16, 11], box_faces(POLE, [5, 0, 5], [11, 16, 11])))
        els.append(element([0, 11, 7], [16, 13.5, 9], box_faces(STEEL, [0, 11, 7], [16, 13.5, 9])))
        for x0 in ((1.5, 12.5) if d['pole'] else (1.5, 7, 12.5)):
            els.append(element([x0, 13.5, 7.25], [x0 + 2, 16, 8.75], box_faces(POR, [x0, 13.5, 7.25], [x0 + 2, 16, 8.75])))
        m = custom(i, els, STEEL)
        STATES[i] = {'variants': {'axis=x': {'model': m}, 'axis=z': {'model': m, 'y': 90}}}
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
        STATES[i] = {'variants': {f'facing={f}': v for f, v in rotations(m).items()}}
        ITEMS[i] = m
    elif kind == 'vending':
        lf = box_faces(VS, [0, 0, 2], [16, 16, 16]); lf['north'] = face(VFL, [0, 0, 16, 16]); lf['down'] = face(VT)
        lf.pop('up')
        uf = box_faces(VS, [0, 0, 2], [16, 13, 16]); uf['north'] = face(VFU, [0, 0, 16, 13]); uf['up'] = face(VT); uf.pop('down')
        lo = custom(i + '_lower', [element([0, 0, 2], [16, 16, 16], lf)], VS)
        up = custom(i + '_upper', [element([0, 0, 2], [16, 13, 16], uf)], VS)
        v = {}
        for half, m in (('lower', lo), ('upper', up)):
            for f, r in rotations(m).items(): v[f'facing={f},half={half}'] = r
        STATES[i] = {'variants': v}
        ITEMS[i] = lo

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
    for tool in ('pickaxe', 'axe'):
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
