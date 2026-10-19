"""The Rokakaka's 3D model for GeckoLib (2.41.0, user request): geometry, its texture and an idle animation, generated
together so every cube's box-UV region is painted to fit it. Same palette as the 2D sprite (make_fruit.py).
usage: python3 tools/commerce/make_fruit_model.py
Writes assets/celestialfall/{geo/item/rokakaka.geo.json, textures/item/rokakaka_model.png,
animations/item/rokakaka.animation.json}. Units are sixteenths of a block; the model's origin sits at the middle of the
item's space (GeckoLib's item renderer), the fruit a little below it so stem and leaves stay inside.
"""
import json, math, random
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[2] / 'src/main/resources/assets/celestialfall'
TW, TH = 128, 128
random.seed(7)

# the 2D sprite's palette
OUTLINE, BODY, LIGHT, BRIGHT, SHADOW = '#55274e', '#c7528e', '#f087b1', '#ffd1d5', '#a93475'
THORN, TIP = '#9e315f', '#ffcfb4'
STEM, STEM_LIGHT, LEAF, LEAF_DARK, VEIN = '#638953', '#8cad68', '#4f7a58', '#3a5c45', '#b1d180'


def rgb(h):
    h = h.lstrip('#'); return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def mix(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


# ---- the cubes: (bone, origin, size, painter) ----
CY = -1.5  # the fruit's middle
HALF = 6   # half its height
cubes = []


def radius(t):
    """The fruit's radius at height t (-1 bottom, 1 top): round, a little fuller below its middle like the sprite's."""
    return 5.6 * math.sqrt(max(0, 1 - t * t)) * (1 - .1 * t)


# a round fruit built of one-pixel slices, each slice a disc made of a long, a cross and a square box (an octagon)
for j in range(2 * HALF):
    t = (j + .5 - HALF) / HALF
    r = radius(t)
    y0 = CY - HALF + j
    long = max(1, round(2 * r))
    short = max(1, round(2 * r * .62))
    square = max(1, round(2 * r * .86))
    shapes = [(long, short), (short, long)] if long > 2 else [(long, long)]
    if long >= 7:
        shapes.append((square, square))
    for w, d in shapes:
        cubes.append(('fruit', [-w / 2, y0, -d / 2], [w, 1, d], 'body'))
# bumps over it: little light-tipped thorns standing a pixel out of the skin, never on the very top or bottom
for k in range(34):
    a = k * 2.3999632  # the golden angle spreads them evenly round
    t = -.78 + 1.56 * ((k + .5) / 34)
    r = radius(t)
    px, py, pz = math.cos(a) * r, CY + t * HALF, math.sin(a) * r
    # half in the skin, on the half pixel
    px, py, pz = round(px * 2) / 2 - .5, round(py * 2) / 2 - .5, round(pz * 2) / 2 - .5
    cubes.append(('fruit', [px, py, pz], [1, 1, 1], 'thorn'))
# the calyx: a small dark green cross on top, the stem out of it
top = CY + HALF
cubes.append(('crown', [-2.5, top - .5, -.5], [5, 1, 1], 'calyx'))
cubes.append(('crown', [-.5, top - .5, -2.5], [1, 1, 5], 'calyx'))
cubes.append(('crown', [-.5, top, -.5], [1, 3, 1], 'stem'))
# two leaves from the stem's top (their bones turn them)
cubes.append(('leaf_a', [0, top + 2, -1], [5, 1, 2], 'leaf'))
cubes.append(('leaf_b', [-5, top + 2, -1], [4, 1, 2], 'leaf'))

# ---- box UV packing (shelves) ----
def region(size):
    w, h, d = size
    return 2 * (d + w), d + h


placed, x, y, row = [], 0, 0, 0
order = sorted(range(len(cubes)), key=lambda i: -region(cubes[i][2])[1])
uv = {}
for i in order:
    rw, rh = region(cubes[i][2])
    if x + rw > TW:
        x, y, row = 0, y + row, 0
    uv[i] = (x, y)
    x += rw; row = max(row, rh)
assert y + row <= TH, f'texture too small: {y + row}'

# ---- painting ----
img = Image.new('RGBA', (TW, TH), (0, 0, 0, 0))
px = img.load()


def paint_face(u0, v0, fw, fh, colour_at):
    for j in range(int(fh)):
        for i in range(int(fw)):
            px[u0 + i, v0 + j] = colour_at(i, j, fw, fh) + (255,)


def body_colour(face, y_world):
    """The skin: lighter up top, deeper below, speckled; tops bright, bottoms in shadow."""
    t = (y_world - (CY - HALF)) / (2 * HALF)  # 0 at the bottom, 1 at the top
    base = mix(rgb(SHADOW), rgb(BODY), min(1, t * 1.6))
    base = mix(base, rgb(LIGHT), max(0, (t - .55) / .45) * .85)
    if face == 'up':
        base = mix(base, rgb(LIGHT), .55)
    if face == 'down':
        base = mix(base, rgb(OUTLINE), .45)
    r = random.random()
    if r < .07:
        base = mix(base, rgb(BRIGHT), .7)
    elif r < .16:
        base = mix(base, rgb(SHADOW), .6)
    return base


for i, (bone, origin, size, kind) in enumerate(cubes):
    w, h, d = size
    u, v = uv[i]
    y0 = origin[1]

    if kind == 'body':
        for face, (fu, fv, fw, fh) in {'up': (u + d, v, w, d), 'down': (u + d + w, v, w, d)}.items():
            paint_face(fu, fv, fw, fh, lambda a, b, fw_, fh_, face=face: body_colour(face, y0 + h if face == 'up' else y0))
        for fu, fw in [(u, d), (u + d, w), (u + d + w, d), (u + 2 * d + w, w)]:
            paint_face(fu, v + d, fw, h, lambda a, b, fw_, fh_: body_colour('side', y0 + h - b - .5))
    elif kind == 'thorn':
        for fu, fv, fw, fh, c in [(u + d, v, w, d, TIP), (u + d + w, v, w, d, SHADOW)]:
            paint_face(fu, fv, fw, fh, lambda *a, c=c: rgb(c))
        for fu, fw in [(u, d), (u + d, w), (u + d + w, d), (u + 2 * d + w, w)]:
            paint_face(fu, v + d, fw, h, lambda *a: rgb(THORN))
    elif kind in ('calyx', 'stem'):
        c0, c1 = (LEAF_DARK, LEAF) if kind == 'calyx' else (STEM, STEM_LIGHT)
        paint_face(u + d, v, w, d, lambda *a: rgb(c1))
        paint_face(u + d + w, v, w, d, lambda *a: rgb(c0))
        for fu, fw in [(u, d), (u + d, w), (u + d + w, d), (u + 2 * d + w, w)]:
            paint_face(fu, v + d, fw, h, lambda a, b, fw_, fh_: mix(rgb(c0), rgb(c1), b / max(1, fh_ - 1) * .0 + .35))
    elif kind == 'leaf':
        # top: green with a pale vein along its length; underside darker; edges dark
        paint_face(u + d, v, w, d, lambda a, b, fw_, fh_: rgb(VEIN) if b == int(fh_ // 2) and 0 < a < fw_ - 1 else rgb(LEAF))
        paint_face(u + d + w, v, w, d, lambda *a: rgb(LEAF_DARK))
        for fu, fw in [(u, d), (u + d, w), (u + d + w, d), (u + 2 * d + w, w)]:
            paint_face(fu, v + d, fw, h, lambda *a: rgb(LEAF_DARK))

(ROOT / 'textures/item').mkdir(parents=True, exist_ok=True)
img.save(ROOT / 'textures/item/rokakaka_model.png')

# ---- geometry ----
bones = {
    'root': {'name': 'root', 'pivot': [0, 0, 0]},
    'fruit': {'name': 'fruit', 'parent': 'root', 'pivot': [0, CY - HALF, 0]},
    'crown': {'name': 'crown', 'parent': 'root', 'pivot': [0, top, 0]},
    'leaf_a': {'name': 'leaf_a', 'parent': 'crown', 'pivot': [0, top + 2.5, 0], 'rotation': [0, -20, 28]},
    'leaf_b': {'name': 'leaf_b', 'parent': 'crown', 'pivot': [0, top + 2.5, 0], 'rotation': [0, 35, -22]},
}
for b in bones.values():
    b['cubes'] = []
for i, (bone, origin, size, kind) in enumerate(cubes):
    bones[bone]['cubes'].append({'origin': [round(c, 3) for c in origin], 'size': size, 'uv': list(uv[i])})
for b in bones.values():
    if not b['cubes']:
        del b['cubes']
geo = {'format_version': '1.12.0', 'minecraft:geometry': [{
    'description': {'identifier': 'geometry.rokakaka', 'texture_width': TW, 'texture_height': TH,
                    'visible_bounds_width': 2, 'visible_bounds_height': 2, 'visible_bounds_offset': [0, .5, 0]},
    'bones': list(bones.values())}]}
(ROOT / 'geo/item').mkdir(parents=True, exist_ok=True)
(ROOT / 'geo/item/rokakaka.geo.json').write_text(json.dumps(geo, indent=1))

# ---- idle: the leaves stir, the fruit breathes a little ----
anim = {'format_version': '1.8.0', 'animations': {'animation.rokakaka.idle': {
    'loop': True, 'animation_length': 4.0,
    'bones': {
        'leaf_a': {'rotation': {'0.0': [0, 0, 0], '2.0': [0, 4, -7], '4.0': [0, 0, 0]}},
        'leaf_b': {'rotation': {'0.0': [0, 0, 0], '2.0': [0, -3, 6], '4.0': [0, 0, 0]}},
        'fruit': {'scale': {'0.0': [1, 1, 1], '2.0': [1.02, .985, 1.02], '4.0': [1, 1, 1]}},
    }}}}
(ROOT / 'animations/item').mkdir(parents=True, exist_ok=True)
(ROOT / 'animations/item/rokakaka.animation.json').write_text(json.dumps(anim, indent=1))
print('cubes', len(cubes), 'texture', TW, 'x', TH, 'used rows to', y + row)
