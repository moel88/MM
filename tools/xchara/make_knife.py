"""엑스 차라's knife for GeckoLib (2.111.0, user request: "이제 신규 스탠드 만들자 스탠드 이름은 엑스 차라 검 모델링부터
보내줄게 대충 이걸 참고해", with a photo of the knife in a hand; asked, the user answered: the red-and-white swirled blocks
at its left are the hand that holds it, not the knife; its player holds it in person, as Kageno and Lee So-sang hold
their swords; then "검 크기 ㅈㄴ 키워야할듯 그리고 손잡이 가운데 부분 잡아야하고": held twice the photo's size, 3 blocks in all,
chosen among in-game sizes, the fist round the rod's middle): its geometry, its texture and its sprite, made
together so that every face is painted for the place it takes on the knife (per-face UV, as tools/isosang/make_sword.py
does).

usage: python3 tools/xchara/make_knife.py [OUT]
Writes, under src/main/resources/assets/celestialfall (or under OUT, in the same folders, to look at a change without
touching the game's files):
  geo/item/x_chara_knife.geo.json                  the model
  textures/item/x_chara_knife_model.png            its texture
  textures/item/x_chara_knife.png                  a 16 px sprite (the item's particles)
  animations/item/x_chara_knife.animation.json     its idle (nothing moves)

Its shape, measured off the photo (the knife seen side on, its spine turned level; in the photo's pixels the blade is
1345 long and 325 deep at its heel, the rod 438 long and about 24 across, its middle 33 below the spine's line):
  the blade black, its spine (its back) straight from the heel to the point; its heel square across the blade but for a
  lean of 7 degrees, its lower corner a little ahead; its edge running from the heel's lower corner almost straight,
  closing in on the spine a little (to 0.88 of the heel's depth halfway along), then its belly curving up ever faster
  to a sharp point on the spine's line (PROFILE: the depth along the blade, as measured); 4.1 times as long as it is
  deep at the heel; its edge ground to a thin bevel along its length, a faint bright line at its very edge;
  the handle a thin black rod, level with the spine a tenth of the blade's depth below it, coming out of the heel near
  its upper corner (the fist holds it at its middle, the model's origin).
The model's size is the photo's, its hand taken for a player's (as wide as a player's hand, four sixteenths of a
block): 24 units (1.5 blocks) from the rod's end to the point, the blade 18.1 units long and 4.38 deep, the rod 5.9
long and 0.36 across (a hair thicker than the photo's 0.32, so that it reads). A hand holds it twice that (its display,
models/item/x_chara_knife.json): 3 blocks in all, as the user chose.
Its colours are the photo's: the blade near black (#0e1216 in its middle), a little lighter by its spine and its edge;
the rod near black, a little warm. (The violet near the point in the photo is the purple block behind it seen through
and reflected: the knife is black.)

Units are sixteenths of a block. The knife runs along +y with the middle of its rod at the origin (GeckoLib's item
renderer puts the model's origin at the middle of the item's space, so the hand holds it there), its spine towards +x,
its edge towards -x, its flats towards +-z. GeckoLib mirrors x as it bakes a model; the item's display
(models/item/x_chara_knife.json) turns it in the hand.

The blade is cut in thin slices across it, so that its outline follows the photo's: by its heel, where the heel's line
runs across the blade, in slices along x (each slice's end on the heel's line: steps a hair long); beyond, in slices
along y (each its depth at its middle: steps a hair deep, finer where the belly curves fast), each slice a box for the
blade's body (as thick as the blade) and a thinner one for its bevel along the edge. Each slice reaches a hair past its
neighbours (the renderer leaves hair-wide gaps between boxes that only meet). The flats are painted for where each texel
is on the blade (so that the slices' flats run on into one another); the slices' narrow faces (the spine's top, the
edge, the heel, and the faces between slices) all of one colour each, sharing a little place on the atlas.

How GeckoLib lays a face's texture (per-face UV), in the cube's own frame (from make_star_platinum.py):
  north (z min)  x = x0 + u*sx   y = y1 - v*sy        south (z max)  x = x1 - u*sx   y = y1 - v*sy
  east (x min)   z = z1 - u*sz   y = y1 - v*sy        west (x max)   z = z0 + u*sz   y = y1 - v*sy
  up (y max)     x = x0 + u*sx   z = z1 - v*sz        down (y min)   x = x0 + u*sx   z = z0 + v*sz
"""
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parents[2] / 'src/main/resources/assets/celestialfall'
DENS = 24       # texels a unit
SS = 3          # samples a texel's side (lines drawn smooth)
ATLAS_W = 512


def hexc(h):
    h = h.lstrip('#')
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], dtype=np.float64) / 255


def mix(a, b, t):
    t = np.asarray(t, dtype=np.float64)[..., None] if np.ndim(t) else t
    return a + (b - a) * t


def smooth(t):
    t = np.clip(t, 0, 1)
    return t * t * (3 - 2 * t)


# ---------------------------------------------------------------- palette (sampled from the photo)
BODY = hexc('#0f1217')          # the blade's middle (the photo: #0e1216)
BODY_DEEP = hexc('#0a0c10')
SPINE_LIT = hexc('#22252c')     # by the spine (the photo: #1a1a1f), its top lighter still
SPINE_TOP = hexc('#2a2d35')
SHEEN = hexc('#171a20')         # a faint cool sheen along the blade, as on dark steel
SHOULDER = hexc('#07080b')      # where the bevel is ground in
BEVEL_HI, BEVEL_LO = hexc('#2a2e37'), hexc('#191c22')   # the bevel, lighter towards the edge (the photo: #1b1b19 by it)
EDGE = hexc('#555b67')          # the very edge, a faint bright line
HEEL = hexc('#15171c')
ROD = [hexc('#110e12'), hexc('#1b171c')]   # the rod's end and its sides (the photo: #1d171c)

# ---------------------------------------------------------------- the measures (off the photo, in its pixels)
PHOTO_BLADE, PHOTO_DEPTH, PHOTO_ROD, PHOTO_ROD_ACROSS, PHOTO_ROD_BELOW = 1345, 325, 438, 24, 33
TOTAL = 24.0                              # the knife from the rod's end to the point (units: 1.5 blocks; held twice that)
K = TOTAL / (PHOTO_BLADE + PHOTO_ROD)     # units a pixel of the photo
BLADE = PHOTO_BLADE * K                   # the blade's length, heel to point
DEPTH = PHOTO_DEPTH * K                   # its depth at the heel, spine to edge
ROD_LEN = PHOTO_ROD * K
ROD_R = max(PHOTO_ROD_ACROSS * K, .36) / 2
SPINE_X = PHOTO_ROD_BELOW * K             # the spine above the rod's middle
LEAN = 7.0                                # the heel's lean (degrees): its lower corner this far ahead of its upper
HEEL_RUN = DEPTH * math.tan(math.radians(LEAN))
Y_HEEL = ROD_LEN / 2                      # the heel's upper corner (the rod's middle at the origin)
Y_TIP = Y_HEEL + BLADE
HEEL_ZONE = .62                           # by the heel, slices along x; beyond it, along y
T_BODY = .32                              # the blade's thickness
T_EDGE = .13                              # its bevel's
BEVEL = .40                               # the bevel's width along most of the edge (narrower towards the point)
# the blade's depth along it (of its depth at the heel), measured off the photo every so far along (0 the heel, 1 the
# point): straight to a quarter of the way, closing in a little to halfway, then the belly
PROFILE = [(.22, 1.0), (.30, .98), (.37, .94), (.45, .912), (.52, .88), (.595, .80), (.67, .74), (.74, .63), (.82, .51),
           (.89, .36), (.93, .26), (.967, .12), (.985, .06)]
# and the smooth curve through them the knife takes (least squares; within 0.02 of every measure): level to T0, then
# 1 - C1 w - (1 - C1) w^P with w running 0 to 1 from T0 to the point (the edge's straight run and its belly in one),
# its start rounded over BLEND so that the edge bends in without a corner
T0, C1, P, BLEND = .20, .36, 4.3, .16


def depth_frac(t):
    """The blade's depth at t along it (0 the heel, 1 the point), of its depth at the heel (numbers or arrays)."""
    w = np.clip((np.asarray(t, dtype=np.float64) - T0) / (1 - T0), 0.0, 1.0)
    run = np.where(w < BLEND, w * w / (2 * BLEND), w - BLEND / 2) / (1 - BLEND / 2)
    v = np.maximum(1 - C1 * run - (1 - C1) * w ** P, 0.0)
    return float(v) if v.ndim == 0 else v


def depth_at(y):
    """The blade's depth (units, spine to edge) at y (numbers or arrays)."""
    return DEPTH * depth_frac((np.asarray(y, dtype=np.float64) - Y_HEEL) / BLADE)


def bevel_at(y):
    """The bevel's width at y: as wide as BEVEL, narrower as the blade grows shallow towards the point."""
    return np.minimum(BEVEL, .3 * depth_at(y))


def heel_y(x):
    """Where the heel's line crosses x (its upper corner at the spine, leaning forward going down)."""
    return Y_HEEL + (SPINE_X - x) * math.tan(math.radians(LEAN))


# ---------------------------------------------------------------- the cubes
class Cube:
    def __init__(self, bone, part, origin, size, faces, rot=None, pivot=None, solid=None, **opts):
        self.bone, self.part = bone, part
        self.origin, self.size = np.array(origin, dtype=np.float64), np.array(size, dtype=np.float64)
        self.faces = faces
        self.rot = None if rot is None else tuple(float(r) for r in rot)
        self.pivot = None if pivot is None else np.array(pivot, dtype=np.float64)
        self.solid = solid or {}              # face -> the colour that face is all of (its place shared on the atlas)
        self.opts = opts


CUBES = []
SEAM = .0015                              # how far a slice reaches past its neighbours (units)


def cube(bone, part, origin, size, faces, **kw):
    CUBES.append(Cube(bone, part, origin, size, list(faces), **kw))


def blade():
    """The blade's slices: by the heel along x, beyond it along y (each a body box and, by the edge, a bevel box)."""
    edge_x = SPINE_X - DEPTH
    y_zone = Y_HEEL + HEEL_ZONE
    # by the heel: slices along x, a sixteenth of a unit high, each from the heel's line to the zone's end
    n = int(math.ceil(DEPTH / (1 / 16)))
    xs = np.linspace(edge_x, SPINE_X, n + 1)
    for k in range(n):
        x0, x1 = float(xs[k]), float(xs[k + 1])
        y0 = heel_y((x0 + x1) / 2)            # its end on the heel's line at its middle
        bevel = x1 <= edge_x + BEVEL + 1e-9
        t = T_EDGE if bevel else T_BODY
        first_body = not bevel and xs[k] <= edge_x + BEVEL + 1e-9
        solid = {'down': HEEL, 'east': EDGE if k == 0 else SHOULDER if first_body else BEVEL_LO if bevel else HEEL,
                 'west': SPINE_TOP if k == n - 1 else HEEL}
        cube('blade', 'bevel' if bevel else 'body', [x0 - SEAM, y0, -t / 2], [x1 - x0 + 2 * SEAM, y_zone - y0 + SEAM, t],
             ['north', 'south', 'east', 'west', 'down'], solid=solid)
    # beyond: slices along y, each as deep as the blade at its middle; long where the edge runs straight, short where the
    # belly curves fast (no step deeper than a sixtieth of a unit, none shorter than a thirty-second)
    y = y_zone
    while y < Y_TIP - 1e-6:
        slope = abs(depth_at(y + .01) - depth_at(y)) / .01
        step = min(1 / 8, max(1 / 32, (1 / 60) / max(slope, 1e-6)))
        y1 = min(Y_TIP, y + step)
        mid = (y + y1) / 2
        d, b = depth_at(mid), bevel_at(mid)
        if d < .02:
            break
        last = y1 >= Y_TIP - 1e-6 or depth_at(y1 + .02) < .02
        x_edge, x_shoulder = SPINE_X - d, SPINE_X - d + b
        # (its end towards the point too, all of one colour: where the next slice is shallower a sliver of it shows)
        cube('blade', 'body', [x_shoulder, y - SEAM, -T_BODY / 2], [SPINE_X - x_shoulder, y1 - y + 2 * SEAM, T_BODY],
             ['north', 'south', 'east', 'west', 'up'], solid={'east': SHOULDER, 'west': SPINE_TOP, 'up': BODY_DEEP})
        cube('blade', 'bevel', [x_edge, y - SEAM, -T_EDGE / 2], [b + SEAM, y1 - y + 2 * SEAM, T_EDGE],
             ['north', 'south', 'east', 'up'], solid={'east': EDGE, 'up': BEVEL_LO})
        if last:
            break
        y = y1


def rod():
    """The rod: an eight-sided prism along y (four bars a side wide, turned an eighth apart), from its end to just inside
    the heel."""
    y0, y1 = -ROD_LEN / 2, heel_y(0.0) + .06
    sides, r = 8, ROD_R
    n, side = sides // 2, 2 * r * math.tan(math.pi / sides)
    for k in range(n):
        lo = y0 + .002 * k                    # the end faces staggered a hair, so that they never fight
        cube('rod', 'rod', [-r, lo, -side / 2], [2 * r, y1 - lo, side], ['east', 'west', 'down'],
             rot=(0, 180 / n * k, 0), pivot=[0, (y0 + y1) / 2, 0], solid={'east': ROD[1], 'west': ROD[1], 'down': ROD[0]})


blade()
rod()


# ---------------------------------------------------------------- painting
FACE_DIMS = {'north': (0, 1), 'south': (0, 1), 'east': (2, 1), 'west': (2, 1), 'up': (0, 2), 'down': (0, 2)}


def face_point(c, face, u, v):
    """Points (arrays) at (u, v) on a face, in the cube's own unturned frame."""
    x0, y0, z0 = c.origin
    sx, sy, sz = c.size
    x1, y1, z1 = c.origin + c.size
    one = np.ones_like(u)
    if face == 'north':
        return x0 + u * sx, y1 - v * sy, z0 * one
    if face == 'south':
        return x1 - u * sx, y1 - v * sy, z1 * one
    if face == 'east':
        return x0 * one, y1 - v * sy, z1 - u * sz
    if face == 'west':
        return x1 * one, y1 - v * sy, z0 + u * sz
    if face == 'up':
        return x0 + u * sx, y1 * one, z1 - v * sz
    return x0 + u * sx, y0 * one, z0 + v * sz


def flat(x, y):
    """A flat of the blade at (x, y): lighter by the spine, a faint sheen a third of the way down, near black in the
    middle, a dark line where the bevel is ground in, the bevel lighter towards the edge, a faint bright line at the edge."""
    d = depth_at(y)
    b = np.minimum(BEVEL, .3 * d)
    s = SPINE_X - x                           # how far down from the spine
    col = mix(BODY, BODY_DEEP, smooth((s - .25 * d) / np.maximum(.6 * d, 1e-6)) * .6)
    col = mix(col, SHEEN, np.exp(-((s - .3 * d) / np.maximum(.16 * d, 1e-3)) ** 2) * (.55 + .45 * np.sin(y * .7) ** 2))
    col = mix(col, SPINE_LIT, 1 - smooth(s / .22))
    col = np.where((s < .035)[..., None], SPINE_TOP, col)
    shoulder = d - b                          # where the bevel begins
    bev = mix(BEVEL_LO, BEVEL_HI, smooth((s - shoulder) / np.maximum(b, 1e-6)))
    col = np.where((s > shoulder)[..., None], bev, col)
    col = np.where((np.abs(s - shoulder) < .03)[..., None], SHOULDER, col)
    col = np.where((s > d - .035)[..., None], EDGE, col)
    # by the heel the heel's own line, a hair darker
    col = np.where(((y - heel_y(x)) < .03)[..., None], HEEL, col)
    return col


def paint(c, face, u, v):
    """A painted face's colours at points (u, v) of it (the blade's flats)."""
    x, y, z = face_point(c, face, u, v)
    return flat(x, y)


# ---------------------------------------------------------------- the atlas
def texels(c, face):
    a, b = FACE_DIMS[face]
    return max(1, int(math.ceil(c.size[a] * DENS - 1e-6))), max(1, int(math.ceil(c.size[b] * DENS - 1e-6)))


def solid_colours():
    """Every colour a face is all of, once each."""
    out = []
    for c in CUBES:
        for f in c.faces:
            col = c.solid.get(f)
            if col is not None and not any(np.allclose(col, o) for o in out):
                out.append(col)
    return out


def pack():
    """Every painted face its own place on the atlas (shelves, tallest first), with a texel round it of its own (draw fills
    it with the face's edge, so that a texel sampled a hair outside a face is still the face's colour); every face that
    is all one colour the middle of a 4x4 patch of that colour, shared."""
    solids = solid_colours()
    place, x, y, row = {}, 0, 0, 0
    patches = {}
    for i, col in enumerate(solids):
        if x + 4 > ATLAS_W:
            x, y, row = 0, y + row, 0
        patches[i] = (x, y)
        x += 4
        row = max(row, 4)
    x, y, row = 0, y + row, 0
    items = [(ci, f) + texels(c, f) for ci, c in enumerate(CUBES) for f in c.faces if f not in c.solid]
    items.sort(key=lambda it: (-it[3], -it[2]))
    for ci, f, w, h in items:
        if x + w + 2 > ATLAS_W:
            x, y, row = 0, y + row, 0
        place[(ci, f)] = (x + 1, y + 1, w, h)
        x += w + 2
        row = max(row, h + 2)
    height = int(math.ceil((y + row) / 16) * 16)
    for ci, c in enumerate(CUBES):
        for f in c.faces:
            if f in c.solid:
                i = next(k for k, col in enumerate(solids) if np.allclose(col, c.solid[f]))
                px, py = patches[i]
                place[(ci, f)] = (px + 1, py + 1, 2, 2)
    return place, height, solids, patches


def draw(place, height, solids, patches):
    img = np.zeros((height, ATLAS_W, 4), dtype=np.float64)
    for i, col in enumerate(solids):
        px, py = patches[i]
        img[py:py + 4, px:px + 4, :3] = col
        img[py:py + 4, px:px + 4, 3] = 1
    for (ci, f), (px, py, w, h) in place.items():
        c = CUBES[ci]
        if f in c.solid:
            continue
        su = (np.arange(w * SS) + .5) / (w * SS)
        sv = (np.arange(h * SS) + .5) / (h * SS)
        U, V = np.meshgrid(su, sv)
        col = paint(c, f, U, V).reshape(h, SS, w, SS, 3).mean(axis=(1, 3))
        img[py:py + h, px:px + w, :3] = col
        img[py:py + h, px:px + w, 3] = 1
        img[py - 1, px:px + w], img[py + h, px:px + w] = img[py, px:px + w], img[py + h - 1, px:px + w]   # its border
        img[py - 1:py + h + 1, px - 1], img[py - 1:py + h + 1, px + w] = img[py - 1:py + h + 1, px], img[py - 1:py + h + 1, px + w - 1]
    return img


def geo(place, height):
    bones = [{'name': 'root', 'pivot': [0, 0, 0]}]
    for name in ('rod', 'blade'):
        entry = {'name': name, 'parent': 'root', 'pivot': [0, 0, 0]}
        cubes = []
        for ci, c in enumerate(CUBES):
            if c.bone != name:
                continue
            cb = {'origin': [round(float(v), 4) for v in c.origin], 'size': [round(float(v), 4) for v in c.size]}
            if c.rot is not None:
                cb['rotation'] = [round(r, 4) for r in c.rot]
                cb['pivot'] = [round(float(v), 4) for v in c.pivot]
            cb['uv'] = {f: {'uv': [place[(ci, f)][0], place[(ci, f)][1]], 'uv_size': [place[(ci, f)][2], place[(ci, f)][3]]} for f in c.faces}
            cubes.append(cb)
        entry['cubes'] = cubes
        bones.append(entry)
    return {'format_version': '1.12.0', 'minecraft:geometry': [{
        'description': {'identifier': 'geometry.botand.x_chara_knife', 'texture_width': ATLAS_W, 'texture_height': height,
                        'visible_bounds_width': 3, 'visible_bounds_height': 3, 'visible_bounds_offset': [0, 1, 0]},
        'bones': bones}]}


def sprite():
    """The item's 16 px sprite (its particles): the knife on the diagonal, black with its edge's faint line."""
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    p = img.load()
    c = lambda a: tuple(int(round(v * 255)) for v in a) + (255,)
    for k in range(11):                       # the blade, from its heel up to its point
        x, y = 4 + k, 11 - k
        p[x, y] = c(BODY)
        if k < 9 and y + 1 < 16:
            p[x, y + 1] = c(BODY_DEEP)
        if k < 7 and y + 2 < 16 and x - 1 >= 0:
            p[x - 1, y + 2 if y + 2 < 16 else 15] = c(BEVEL_LO)
        if x + 1 < 16 and k < 10:
            p[x + 1, y] = c(SPINE_LIT)
    for x, y in ((3, 13), (5, 13), (6, 12), (7, 11)):
        p[x, y] = c(EDGE)
    for x, y in ((1, 14), (2, 13), (3, 12)):  # the rod
        p[x, y] = c(ROD[1])
    p[0, 15] = c(ROD[0])
    return img


def main():
    place, height, solids, patches = pack()
    img = draw(place, height, solids, patches)
    rgba = (np.clip(img, 0, 1) * 255).round().astype(np.uint8)
    for sub in ('geo/item', 'textures/item', 'animations/item'):
        (ROOT / sub).mkdir(parents=True, exist_ok=True)
    Image.fromarray(rgba, 'RGBA').save(ROOT / 'textures/item/x_chara_knife_model.png')
    sprite().save(ROOT / 'textures/item/x_chara_knife.png')
    (ROOT / 'geo/item/x_chara_knife.geo.json').write_text(json.dumps(geo(place, height), indent=1) + '\n')
    anim = {'format_version': '1.8.0', 'animations': {'animation.x_chara_knife.idle': {
        'loop': True, 'animation_length': 1.0, 'bones': {'root': {'rotation': {'0.0': [0, 0, 0]}}}}}}
    (ROOT / 'animations/item/x_chara_knife.animation.json').write_text(json.dumps(anim, indent=1) + '\n')
    print(f'{len(CUBES)} cubes, {len(place)} faces, atlas {ATLAS_W}x{height}; blade {BLADE:.2f} x {DEPTH:.2f} units, '
          f'rod {ROD_LEN:.2f} x {2 * ROD_R:.2f}, knife {ROD_LEN + BLADE:.2f} units')


if __name__ == '__main__':
    main()
