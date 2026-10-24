"""Star Platinum's 3D model for GeckoLib (user request: "일단 스타플래티나 모델링만 뽑자 신경써서", after the official
art the user sent): its geometry and its texture, made together so that every face is painted for the place it takes on
the body.

usage: python3 tools/stand/make_star_platinum.py [OUT]
Writes assets/celestialfall/geo/entity/star_platinum.geo.json, textures/entity/star_platinum.png and
stands/star_platinum_poses.json under src/main/resources, or under OUT (the same folders) to look at a change without
touching the game's files (a running dev client swaps changed resources in at once).

Units are sixteenths of a block, y up from the soles, the front towards -z (north) and the right side towards -x, as
in every Bedrock/GeckoLib model (the game mirrors x when it bakes it). The texture has DENS texels a unit (the face
FACE_DENS), four times a vanilla skin, so the face, the muscles and the gold lines can be drawn. Every face has its own
place on it (per-face UV); the painters get each texel's place on the body in the rest pose (and, for hair, cloth and
the scarf, its place along them).

How GeckoLib lays a face's texture (per-face UV, from its BakedModelFactory and GeoQuad), in model space:
  north (z min)  x = x0 + u*sx   y = y1 - v*sy        south (z max)  x = x1 - u*sx   y = y1 - v*sy
  east (x min)   z = z1 - u*sz   y = y1 - v*sy        west (x max)   z = z0 + u*sz   y = y1 - v*sy
  up (y max)     x = x0 + u*sx   z = z1 - v*sz        down (y min)   x = x0 + u*sx   z = z0 + v*sz
and it turns a bone or cube by Rz(-rz) . Ry(ry) . Rx(-rx) about its pivot (degrees, model space), so a positive rx
tips something standing up towards the front and a positive rz towards the left (+x).

The hair, the scarf's ends and the loincloth are chains of bones, one a piece, so the game can sway them.
"""
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parents[2] / 'src/main/resources/assets/celestialfall'
DENS = 4
FACE_DENS = 8
ATLAS_W = 1024


def hexc(h):
    h = h.lstrip('#')
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], dtype=np.float64) / 255


# ---------------------------------------------------------------- palette (from the art the user sent)
SKIN = [hexc(c) for c in ('#2a2860', '#403f8c', '#5657ae', '#6f73c8', '#8d92dc', '#adb1ec', '#cfd0f6')]  # line .. shine
SKIN_RIM = hexc('#93cfe6')            # the cyan light along shaded edges in the art
HAIR = [hexc(c) for c in ('#08070c', '#121119', '#1c1a26', '#2a2836', '#3f3d4f', '#5f5d72')]   # black, cool grey sheen
GOLD = [hexc(c) for c in ('#4f3610', '#8f6a22', '#c39a36', '#e1bf4c', '#f3dc78', '#fff4b8')]
CREAM = [hexc(c) for c in ('#7a6a3c', '#b9aa78', '#dccfa0', '#eee5c2', '#faf5e2')]
SCARF = [hexc(c) for c in ('#5a160b', '#8a2513', '#b9391d', '#dc5426', '#f07b3c', '#ffad6a')]
GAUNT = [hexc(c) for c in ('#0b140e', '#16241a', '#223829', '#33523b', '#4b7253', '#6a9270')]
CLOTH = [hexc(c) for c in ('#6f74aa', '#9399cc', '#b9bfe6', '#d9ddf5', '#eef0fc', '#fbfbff')]
SHORTS = [hexc(c) for c in ('#0a0a14', '#131225', '#1e1c38', '#2a2850')]
EYE_WHITE, EYE_IRIS, EYE_DARK = hexc('#e2f8fb'), hexc('#57c6d8'), hexc('#123440')
EYE_PALE = hexc('#cde4ec')             # the white of the eye, a little dimmed
LINE = hexc('#1a1838')                 # the ink line of the face
GEM = [hexc(c) for c in ('#4f8790', '#9fd6dc', '#e6fbff')]
BUCKLE_FACE = [hexc(c) for c in ('#a9b9a0', '#d3e0cc', '#eef5ea')]


def mix(a, b, t):
    return a + (b - a) * t


def ramp(cols, t):
    """A cel-shaded tone: t in 0..1 picks one of the colours (no blending: crisp bands like the art)."""
    i = int(np.clip(t, 0, .9999) * len(cols))
    return cols[i]


def noise(x, y, z, seed=0):
    """Small deterministic value noise in -1..1 (grain)."""
    v = math.sin(x * 12.9898 + y * 78.233 + z * 37.719 + seed * 4.1) * 43758.5453
    return (v - math.floor(v)) * 2 - 1


def norm(v):
    v = np.asarray(v, dtype=np.float64)
    return v / np.linalg.norm(v)


# ---------------------------------------------------------------- the rig
class Bone:
    def __init__(self, name, parent, pivot, rot=(0, 0, 0)):
        self.name, self.parent, self.pivot, self.rot = name, parent, list(pivot), list(rot)


class Cube:
    def __init__(self, bone, origin, size, mat, rot=None, pivot=None, inflate=0.0, opts=None, faces=None):
        self.bone, self.origin, self.size, self.mat = bone, list(origin), list(size), mat
        self.rot, self.pivot, self.inflate = rot, pivot, inflate
        self.opts = dict(opts or {})
        self.faces = faces or ('north', 'south', 'east', 'west', 'up', 'down')


BONES, CUBES = {}, []


def bone(name, parent, pivot, rot=(0, 0, 0)):
    BONES[name] = Bone(name, parent, pivot, rot)
    return name


def box(b, origin, size, mat, **opts):
    kw = {k: opts.pop(k) for k in ('rot', 'pivot', 'inflate', 'faces') if k in opts}
    c = Cube(b, origin, size, mat, opts=opts, **kw)
    CUBES.append(c)
    return c


def rounded(b, origin, size, mat, notch, **opts):
    """A box with its four long edges stepped in (two crossed boxes, one narrower and one shallower): a limb or a body
    that reads round, the steps drawn like the art's line work. notch is how far each edge steps in (x, z)."""
    (x, y, z), (w, h, d) = origin, size
    nx, nz = (notch, notch) if np.isscalar(notch) else notch
    box(b, (x, y, z + nz), (w, h, d - 2 * nz), mat, **opts)
    # its ends a hair inside the other's, so the two never draw the same square
    return box(b, (x + nx, y + .02, z), (w - 2 * nx, h - .04, d), mat, **opts)


def mirror_name(n):
    return n.replace('right', 'left') if 'right' in n else n.replace('left', 'right')


def mirrored(build):
    """Builds a part on the right side, then its mirror on the left: x -> -x, rotations (rx, -ry, -rz)."""
    first = len(CUBES)
    names_before = set(BONES)
    build()
    new_bones = [n for n in BONES if n not in names_before]
    for n in new_bones:
        b = BONES[n]
        m = mirror_name(n)
        BONES[m] = Bone(m, mirror_name(b.parent) if b.parent in new_bones else b.parent,
                        [-b.pivot[0], b.pivot[1], b.pivot[2]], [b.rot[0], -b.rot[1], -b.rot[2]])
    for c in CUBES[first:len(CUBES)]:
        o, s = c.origin, c.size
        opts = dict(c.opts)
        opts['side'] = -opts.get('side', -1)
        mc = Cube(mirror_name(c.bone) if c.bone in new_bones else c.bone, [-(o[0] + s[0]), o[1], o[2]], s, c.mat,
                  rot=None if c.rot is None else [c.rot[0], -c.rot[1], -c.rot[2]],
                  pivot=None if c.pivot is None else [-c.pivot[0], c.pivot[1], c.pivot[2]],
                  inflate=c.inflate, opts=opts, faces=c.faces)
        CUBES.append(mc)


def rot_matrix(r):
    rx, ry, rz = (math.radians(a) for a in r)
    cx, sx = math.cos(-rx), math.sin(-rx)
    cy, sy = math.cos(ry), math.sin(ry)
    cz, sz = math.cos(-rz), math.sin(-rz)
    Rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    Ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    Rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return Rz @ Ry @ Rx


def euler_of(R):
    """The (rx, ry, rz) for which rot_matrix gives R (R = Rz(a) Ry(b) Rx(c) with a = -rz, b = ry, c = -rx)."""
    b = math.asin(float(np.clip(-R[2, 0], -1, 1)))
    a = math.atan2(R[1, 0], R[0, 0])
    c = math.atan2(R[2, 1], R[2, 2])
    return [-math.degrees(c), math.degrees(b), -math.degrees(a)]


def rest_transform(name):
    """A bone's rest transform as (R, t): a point p of its unturned frame sits at R p + t."""
    R, t = np.eye(3), np.zeros(3)
    chain = []
    b = BONES[name]
    while b is not None:
        chain.append(b)
        b = BONES[b.parent] if b.parent else None
    for b in reversed(chain):          # root first: world = T_root(T_child(p))
        Rb = rot_matrix(b.rot)
        piv = np.array(b.pivot, dtype=np.float64)
        # T_b(p) = piv + Rb (p - piv); compose world = R (T_b(p)) + t
        t = R @ (piv - Rb @ piv) + t
        R = R @ Rb
    return R, t


def strand(prefix, parent, points, widths, thicks, normal_hint, mat, **opts):
    """A chain of bones, one a piece, through points (rest pose, model space): each piece a box standing along its
    bone's +y from the bone's pivot, its width across x and its thickness along z, z turned towards normal_hint.
    normal_hint is a vector or a function of the piece's index."""
    R_prev, t_prev = rest_transform(parent)
    names = []
    par = parent
    total = sum(np.linalg.norm(np.subtract(points[k + 1], points[k])) for k in range(len(points) - 1))
    along = 0.0
    for k in range(len(points) - 1):
        P0, P1 = np.array(points[k], dtype=np.float64), np.array(points[k + 1], dtype=np.float64)
        d = P1 - P0
        L = float(np.linalg.norm(d))
        y = d / L
        nh = norm(normal_hint(k) if callable(normal_hint) else normal_hint)
        z = nh - (nh @ y) * y
        if np.linalg.norm(z) < 1e-6:
            z = np.array([0, 0, 1.0]) - y[2] * y
        z = norm(z)
        x = np.cross(y, z)
        Rk = np.column_stack([x, y, z])
        Rrel = R_prev.T @ Rk
        q = R_prev.T @ (P0 - t_prev)
        name = f'{prefix}_{k}' if len(points) > 2 else prefix
        bone(name, par, q, euler_of(Rrel))
        w, th = widths[k], thicks[k]
        sink = .35 if k > 0 else .6     # each piece starts a little inside the one before (or the head)
        over = .2 if k < len(points) - 2 else 0
        box(name, (q[0] - w / 2, q[1] - sink, q[2] - th / 2), (w, L + sink + over, th), mat,
            piece=k, pieces=len(points) - 1, start=along / total, length=total, seg=L, sink=sink, **opts)
        along += L
        t_prev = R_prev @ (q - Rrel @ q) + t_prev
        R_prev = R_prev @ Rrel
        par = name
        names.append(name)
    return names


def slerp(a, b, t):
    a, b = norm(a), norm(b)
    om = math.acos(float(np.clip(a @ b, -1, 1)))
    if om < 1e-4:
        return a
    return (math.sin((1 - t) * om) * a + math.sin(t * om) * b) / math.sin(om)


def rotate_about(v, axis, deg):
    axis = norm(axis)
    a = math.radians(deg)
    return v * math.cos(a) + np.cross(axis, v) * math.sin(a) + axis * (axis @ v) * (1 - math.cos(a))


# ---------------------------------------------------------------- geometry
KNEE_Y, ANKLE_Y, SOLE = 9.15, 0.25, -2.0
LIFT = -SOLE
ROOT_BONE = bone('root', None, (0, 0, 0))
HIPS = bone('hips', 'root', (0, 18, 0))
TORSO = bone('torso', 'hips', (0, 21, 0))
CHEST = bone('chest', 'torso', (0, 26.5, 0))
NECK = bone('neck', 'chest', (0, 32.5, -0.1))
HEAD = bone('head', 'neck', (0, 35.0, -0.25))

# torso: a narrow waist under a broad, deep chest, the lats flaring out behind
rounded(TORSO, (-4.0, 20.25, -2.4), (8.0, 3.75, 4.8), 'skin', .5, part='abdomen')
rounded(TORSO, (-4.5, 23.5, -2.55), (9.0, 2.75, 5.1), 'skin', .55, part='lowribs')
rounded(CHEST, (-5.0, 25.75, -2.75), (10.0, 3.0, 5.5), 'skin', .6, part='ribs')
rounded(CHEST, (-5.5, 28.5, -3.0), (11.0, 4.25, 6.0), 'skin', .7, part='chest')
rounded(CHEST, (-6.0, 26.25, -1.5), (12.0, 6.0, 4.75), 'skin', .6, part='lats')
box(CHEST, (-3.0, 32.5, -1.25), (6.0, 1.5, 4.1), 'skin', part='traps')
# the pecs, and an eight-pack standing out of the belly
for side in (-1, 1):
    box(CHEST, (0.25 if side > 0 else -5.25, 28.75, -3.85), (5.0, 3.75, 1.6), 'skin', part='pec', side=side)
ABS = [  # bottom, top, width, front, back
    (21.4, 22.6, 1.6, -2.85, -2.3),
    (22.9, 24.1, 1.75, -3.0, -2.45),
    (24.4, 25.7, 1.85, -3.0, -2.45),
    (26.0, 27.4, 1.9, -3.2, -2.65),
]
for row, (y0, y1, w, z0, z1) in enumerate(ABS):
    for side in (-1, 1):
        x0 = 0.2 if side > 0 else -0.2 - w
        box(TORSO if row < 3 else CHEST, (x0, y0, z0), (w, y1 - y0, z1 - z0), 'skin', part='ab', side=side, row=row)
rounded(NECK, (-1.75, 32.0, -1.75), (3.5, 3.5, 3.75), 'skin', .45, part='neck')

# head: a long face with a strong jaw and heavy brows, the headband and its medallion (painted at FACE_DENS)
rounded(HEAD, (-2.75, 35.0, -3.0), (5.5, 6.25, 5.75), 'skin', .45, part='head', dens=FACE_DENS)
box(HEAD, (-2.25, 34.25, -3.25), (4.5, 1.75, 4.0), 'skin', part='jaw', dens=FACE_DENS)
box(HEAD, (-0.5, 36.6, -3.5), (1.0, 1.6, 0.5), 'skin', part='nose', dens=FACE_DENS)
box(HEAD, (-2.5, 38.6, -3.3), (5.0, 0.6, 0.3), 'skin', part='brow', dens=FACE_DENS, faces=('north', 'up', 'down', 'east', 'west'))
for side in (-1, 1):
    box(HEAD, (2.75 if side > 0 else -3.2, 36.75, -0.6), (0.45, 1.75, 1.25), 'skin', part='ear', side=side, dens=FACE_DENS)
box(HEAD, (-3.15, 39.4, -3.3), (6.3, 1.3, 6.95), 'gold', part='headband')
box(HEAD, (-0.9, 39.15, -3.65), (1.8, 1.8, 0.4), 'gem', part='medallion', dens=FACE_DENS)
box(HEAD, (-0.65, 38.9, -3.6), (1.3, 2.3, 0.32), 'gem', part='medallion', dens=FACE_DENS)

# hair: a cap over the top, back and sides of the head, then a wild mane thrown up and back, swept to the left as in
# the art, and a few locks falling down the right side past the ear
box(HEAD, (-3.05, 40.6, -3.1), (6.1, 1.95, 6.45), 'hair', part='cap')
box(HEAD, (-3.0, 35.4, 2.45), (6.0, 5.25, 1.0), 'hair', part='capback')
for side in (-1, 1):
    box(HEAD, (2.7 if side > 0 else -3.1, 38.0, -1.6), (0.4, 2.9, 4.2), 'hair', part='capside', side=side)

MANE = [
    # root, first direction, last direction, length, width, thickness, flat side faces (normal), wave (deg), phase.
    # As in the user's pictures (round 2: "머리카락도 지금 너무 이상해"): long black hair swept back off the forehead
    # and streaming out behind in the wind, waving up and down along its length, the tips flicking; locks fall beside
    # the face. Nothing stands up off the head.
    # the top, rising a little off the head and then back
    ((-1.8, 42.3, -1.8), (-.2, .3, 1), (-.3, .25, 1), 12.5, 3.4, 1.6, (0, 1, -.2), 9, 0.0),
    ((0.1, 42.5, -1.9), (0, .32, 1), (.05, .3, 1), 13.5, 3.6, 1.7, (0, 1, -.2), 10, 1.3),
    ((1.9, 42.3, -1.7), (.2, .3, 1), (.35, .25, 1), 12.5, 3.4, 1.6, (0, 1, -.2), 9, 2.4),
    ((-1.2, 42.6, 0.4), (-.15, .5, 1), (-.25, .35, 1), 13.0, 3.4, 1.6, (0, 1, -.1), 10, .7),
    ((1.2, 42.6, 0.5), (.15, .5, 1), (.3, .35, 1), 13.0, 3.4, 1.6, (0, 1, -.1), 10, 3.0),
    # the back of the head, streaming out behind
    ((-2.2, 41.4, 2.9), (-.3, .5, 1), (-.4, .25, 1), 12.0, 3.3, 1.5, (0, 1, 0), 10, .4),
    ((0.0, 41.7, 3.1), (0, .55, 1), (.05, .3, 1), 12.5, 3.5, 1.6, (0, 1, 0), 11, 2.0),
    ((2.2, 41.4, 2.9), (.3, .5, 1), (.45, .25, 1), 12.0, 3.3, 1.5, (0, 1, 0), 10, 1.1),
    ((-2.0, 39.4, 3.3), (-.3, .1, 1), (-.4, -.15, 1), 11.0, 3.2, 1.5, (0, 1, 0), 10, 2.7),
    ((0.1, 39.6, 3.4), (0, .15, 1), (.05, -.1, 1), 11.5, 3.4, 1.5, (0, 1, 0), 11, .2),
    ((2.1, 39.4, 3.3), (.3, .1, 1), (.45, -.15, 1), 11.0, 3.2, 1.5, (0, 1, 0), 10, 1.6),
    ((-1.6, 37.2, 3.1), (-.25, -.45, 1), (-.3, -.75, .7), 9.0, 2.9, 1.3, (0, 1, 0), 9, 1.0),
    ((0.3, 37.0, 3.2), (0, -.5, 1), (.05, -.8, .65), 9.5, 3.0, 1.3, (0, 1, 0), 9, 2.9),
    ((2.0, 37.2, 3.0), (.25, -.45, 1), (.35, -.75, .7), 9.0, 2.9, 1.3, (0, 1, 0), 9, .5),
    # the sides, swept back past the ears
    ((-3.0, 40.4, -0.8), (-.9, .2, .45), (-.55, .1, 1), 11.0, 3.0, 1.4, (-1, .4, 0), 10, 1.8),
    ((3.0, 40.4, -0.8), (.9, .2, .45), (.6, .1, 1), 11.0, 3.0, 1.4, (1, .4, 0), 10, 2.3),
    ((-3.0, 38.6, 0.6), (-.8, -.2, .6), (-.4, -.45, 1), 9.5, 2.7, 1.3, (-1, .3, 0), 9, .9),
    ((3.0, 38.6, 0.6), (.8, -.2, .6), (.45, -.45, 1), 9.5, 2.7, 1.3, (1, .3, 0), 9, 2.6),
    # locks falling beside the face
    ((-3.0, 39.0, -1.5), (-.3, -1, -.15), (-.35, -1, .3), 6.5, 1.9, 1.0, (-1, 0, -.3), 8, 2.0),
    ((3.0, 39.0, -1.4), (.3, -1, -.1), (.35, -1, .35), 5.5, 1.7, 1.0, (1, 0, -.3), 8, .5),
]
# two thick cores along the flow fill the mane, so the locks read as the ragged edge of one mass of hair
MANE_CORES = [
    ((0.0, 41.7, 0.8), (0, .4, 1), (0, .25, 1), 10.5, 6.2, 3.4, (0, 1, 0), 0, 0),
    ((0.0, 39.5, 2.7), (0, .1, 1), (0, -.2, 1), 8.5, 6.0, 3.2, (0, 1, 0), 0, 0),
]
MANE = MANE_CORES + MANE
SPLIT = (.3, .27, .24, .19)
for i, (root, d0, d1, length, width, thick, nrm, wave, phase) in enumerate(MANE):
    pts = [np.array(root, dtype=np.float64)]
    for k, part in enumerate(SPLIT):
        d = slerp(d0, d1, ((k + .5) / len(SPLIT)) ** .8)
        # the hair ripples up and down along its length (about its own width), the way hair streams in a wind
        across = np.cross(d, norm(nrm))
        if np.linalg.norm(across) > 1e-6:
            d = rotate_about(d, across, wave * math.sin(k * 2.1 + phase))
        pts.append(pts[-1] + d * length * part)
    strand(f'hair_{i:02d}', HEAD, pts, [width * f for f in (1, .85, .62, .32)], [thick * f for f in (1, .85, .65, .45)],
           nrm, 'hair', part='lock', lock=i)

# scarf: wound thick round the neck in two rolls, the upper one high in front over the chin (as in the user's second
# round of pictures), knotted at the back on the right with its end flying out behind
rounded(NECK, (-3.15, 31.85, -3.15), (6.3, 1.95, 6.3), 'scarf', .6, part='wrap', roll=0)
rounded(NECK, (-2.9, 33.5, -3.72), (5.8, 2.35, 6.42), 'scarf', .55, part='wrap', roll=1)
rounded(NECK, (-2.3, 33.1, -4.05), (4.6, 2.2, 1.1), 'scarf', (.4, .2), part='wrap', roll=2)
box(NECK, (-3.45, 32.55, 1.45), (1.75, 1.9, 1.95), 'scarf', part='knot')
strand('scarf_tail', NECK, [(-2.9, 33.4, 2.6), (-3.8, 32.6, 5.5), (-4.6, 31.4, 8.2), (-5.0, 30.0, 10.6), (-5.2, 28.7, 12.4)],
       [2.6, 2.3, 1.9, 1.3], [.45, .42, .4, .36], (1, 0, 0), 'scarf', part='tail')


# shoulder pads: three cream plates stacked into a shell, each edged in gold, rising towards the neck; a gold stud
# at the lower front where the gold line of the body begins
def right_pad():
    # tilted so that it rises towards the neck and its striped top turns a little to the front, as in the art
    pad = bone('right_pad', CHEST, (-6.5, 33.5, 0.0), (10, 0, -28))
    for k in range(5):
        box(pad, (-12.0 + .55 * k, 32.75 + .62 * k, -4.3 + .5 * k), (9.75 - 1.1 * k, 1.0 if k == 0 else .72, 8.6 - 1.0 * k),
            'cream', part='pad', layer=k, layers=5)
    box(pad, (-12.1, 30.25, -4.05), (1.2, 2.65, 8.1), 'cream', part='lip')
    box(pad, (-5.75, 32.1, -4.75), (1.7, 1.7, 0.55), 'gold', part='stud')


mirrored(right_pad)


# arms: deltoid and upper arm, the gauntleted forearm from the elbow with its gold rings, the bare fist with gold
# studs on its knuckles
def right_arm():
    arm = bone('right_arm', CHEST, (-7.25, 31.25, 0.25))
    rounded(arm, (-9.75, 28.25, -2.5), (4.25, 4.25, 5.1), 'skin', .6, part='deltoid')
    rounded(arm, (-9.4, 24.0, -1.95), (3.75, 5.0, 4.0), 'skin', .5, part='upperarm')
    box(arm, (-8.8, 25.0, -2.4), (2.55, 3.5, 0.5), 'skin', part='bicep')
    box(arm, (-9.0, 25.0, 2.0), (3.0, 3.75, 0.45), 'skin', part='tricep')
    fore = bone('right_forearm', arm, (-7.5, 24.25, 0.1))
    rounded(fore, (-9.8, 20.75, -2.25), (4.6, 3.6, 4.7), 'gaunt', .55, part='gauntlet', piece=0)
    rounded(fore, (-9.5, 18.3, -1.95), (4.0, 2.75, 4.1), 'gaunt', .5, part='gauntlet', piece=1)
    box(fore, (-9.95, 23.35, -2.4), (4.9, 0.65, 5.0), 'gold', part='band')
    box(fore, (-9.7, 18.05, -2.15), (4.4, 0.7, 4.5), 'gold', part='band')
    hand = bone('right_hand', fore, (-7.5, 18.25, 0.1))
    rounded(hand, (-9.2, 15.0, -1.85), (3.4, 3.3, 3.7), 'skin', .35, part='fist')
    box(hand, (-9.0, 15.4, -2.25), (2.2, 1.9, 0.45), 'skin', part='thumb')
    for k in range(4):
        box(hand, (-8.0, 14.72, -1.55 + k * 0.82), (0.75, 0.3, 0.62), 'gold', part='knuckle')


mirrored(right_arm)


# legs (long, as the art draws them: the model is built with its soles at SOLE and written LIFT higher, soles at 0):
# thigh with its quad, its outer sweep and hamstring, the knee, shin and calf, the gold rings below the knee and
# at the ankle, bare feet
def right_leg():
    leg = bone('right_leg', HIPS, (-2.5, 18.25, 0.0))
    thigh = 18.75 - KNEE_Y
    rounded(leg, (-5.15, KNEE_Y, -2.75), (5.05, thigh, 5.5), 'skin', .65, part='thigh')
    box(leg, (-4.65, KNEE_Y + 1.0, -3.15), (4.1, thigh * .735, 1.15), 'skin', part='quad')
    box(leg, (-5.55, KNEE_Y + 1.25, -2.0), (0.5, thigh * .68, 3.8), 'skin', part='sweep')
    box(leg, (-4.8, KNEE_Y + 1.5, 2.0), (4.3, thigh * .65, 1.05), 'skin', part='hamstring')
    shin = bone('right_shin', leg, (-2.5, KNEE_Y, 0.0))
    rounded(shin, (-4.6, KNEE_Y - 1.5, -2.35), (4.15, 2.25, 4.6), 'skin', .5, part='knee')
    rounded(shin, (-4.3, ANKLE_Y, -2.0), (3.6, KNEE_Y - 1.0 - ANKLE_Y, 4.0), 'skin', .45, part='shin')
    box(shin, (-4.5, ANKLE_Y + 2.5, 0.2), (4.0, KNEE_Y - 3.75 - ANKLE_Y, 2.1), 'skin', part='calf')
    box(shin, (-4.75, KNEE_Y - 2.1, -2.55), (4.45, 0.8, 5.0), 'gold', part='band')
    box(shin, (-4.5, ANKLE_Y - .15, -2.2), (4.0, 0.6, 4.4), 'gold', part='band')
    foot = bone('right_foot', shin, (-2.5, ANKLE_Y, -0.25))
    box(foot, (-4.35, SOLE, -3.4), (3.7, 2.4, 5.0), 'skin', part='foot')
    box(foot, (-4.25, SOLE + .02, -4.5), (3.5, 1.08, 1.15), 'skin', part='toes')


mirrored(right_leg)

# hips: shorts under a gold belt with its round buckles; the loincloth hangs from the belt in front, behind and at the
# sides, in pieces so that it can flutter
rounded(HIPS, (-4.6, 16.75, -2.85), (9.2, 4.5, 5.7), 'shorts', .55, part='pelvis')
box(HIPS, (-4.85, 20.3, -3.0), (9.7, 0.8, 6.1), 'gold', part='belt')
for (cx, cy, r) in ((3.55, 19.7, 1.55), (-3.75, 20.1, 1.0)):
    box(HIPS, (cx - r * .8, cy - r * .55, -3.85), (r * 1.6, r * 1.1, 0.9), 'buckle', part='buckle', r=r, cx=cx, cy=cy)
    box(HIPS, (cx - r * .55, cy - r * .8, -3.8), (r * 1.1, r * 1.6, 0.83), 'buckle', part='buckle', r=r, cx=cx, cy=cy)


def flap(name, parent, pivot, tilt, pieces, z0, hang):
    """A hanging cloth: pieces (width, length, tilt) one under the other, each its own bone."""
    x, y, z = pivot
    par = parent
    for k, (w, L, t) in enumerate(pieces):
        b = bone(name if k == 0 else f'{name}_{k}', par, (x, y, z), tilt(k, t))
        box(b, (x - w / 2 if hang != 'side' else z0[0], y - L, z0 if hang != 'side' else z - w / 2),
            (w, L + (.15 if k else 0), .3) if hang != 'side' else (.3, L + (.15 if k else 0), w),
            'cloth', part='flap', piece=k, pieces=len(pieces), flap=name, w=w, hang=hang)
        y -= L
        par = b


flap('cloth_front', HIPS, (0, 20.5, -3.45), lambda k, t: (t, 0, 0), ((6.6, 4.2, -5), (7.4, 3.95, -4), (8.2, 4.35, -5)), -3.6, 'front')
flap('cloth_back', HIPS, (0, 20.5, 3.35), lambda k, t: (t, 0, 0), ((7.2, 4.2, 5), (7.8, 4.15, 4), (8.4, 4.55, 5)), 3.2, 'back')


def right_side_flap():
    flap('cloth_right', HIPS, (-4.95, 20.6, 0.3), lambda k, t: (0, 0, t), ((3.4, 5.6, 12), (3.8, 6.4, -4)), (-5.25,), 'side')


mirrored(right_side_flap)


# ---------------------------------------------------------------- where each texel sits
def extents(c):
    o, s, i = np.array(c.origin, dtype=np.float64), np.array(c.size, dtype=np.float64), c.inflate
    return o - i, o + s + i


FACE_DIMS = {'north': (0, 1), 'south': (0, 1), 'east': (2, 1), 'west': (2, 1), 'up': (0, 2), 'down': (0, 2)}
NORMALS = {'north': (0, 0, -1), 'south': (0, 0, 1), 'east': (-1, 0, 0), 'west': (1, 0, 0), 'up': (0, 1, 0), 'down': (0, -1, 0)}


def face_point(c, face, u, v):
    """The point at (u, v) of a face (0..1 each) in the cube's own unturned frame."""
    lo, hi = extents(c)
    x0, y0, z0 = lo
    x1, y1, z1 = hi
    sx, sy, sz = hi - lo
    if face == 'north':
        return np.array([x0 + u * sx, y1 - v * sy, z0])
    if face == 'south':
        return np.array([x1 - u * sx, y1 - v * sy, z1])
    if face == 'east':
        return np.array([x0, y1 - v * sy, z1 - u * sz])
    if face == 'west':
        return np.array([x1, y1 - v * sy, z0 + u * sz])
    if face == 'up':
        return np.array([x0 + u * sx, y1, z1 - v * sz])
    return np.array([x0 + u * sx, y0, z0 + v * sz])


def to_rest(c, p, n):
    """A point and normal of a cube in the model's rest pose (its own turn, then its bones' up to the root)."""
    if c.rot is not None:
        R = rot_matrix(c.rot)
        piv = np.array(c.pivot)
        p, n = piv + R @ (p - piv), R @ n
    R, t = rest_transform(c.bone)
    return R @ p + t, R @ n


def dens(c):
    return c.opts.get('dens', DENS)


def face_size(c, face):
    lo, hi = extents(c)
    a, b = FACE_DIMS[face]
    return (hi - lo)[a], (hi - lo)[b]


def edge_texels(c, face, u, v):
    """How many texels a point lies from the nearest edge of its face."""
    w, h = face_size(c, face)
    return min(u * w, (1 - u) * w, v * h, (1 - v) * h) * dens(c)


def outline(c, face, u, v, col, strength=.75, width=1.0, ink=None):
    if edge_texels(c, face, u, v) < width:
        return mix(col, LINE if ink is None else ink, strength)
    return col


# ---------------------------------------------------------------- painters
LIGHT = norm((0.0, .8, -.6))           # baked light: from above and in front, the same on both sides


def gold_line_torso(x, y):
    """The two wavy gold lines down the front of the body, outside the abs, curving up to the pads' studs."""
    if y < 20.3 or y > 33.0:
        return 0
    k = 0
    for side in (-1, 1):
        cx = 3.3 + .42 * math.sin((y - 21.2) * 1.0 + .4 * side) + max(0, y - 29.8) * .55
        dd = abs(x - side * cx)
        if dd < .3:
            k = max(k, 2 if dd < .14 else 1)
    return k


def gold_line_leg(z, y, side):
    """The gold lines down the outer side of the legs, from the hip to the knee ring and on to the ankle."""
    if y > 18.6 or y < ANKLE_Y + .35 or KNEE_Y - 2.15 < y < KNEE_Y - 1.25:
        return 0
    cz = -.1 + .55 * math.sin((y - 1) * .8 + (side > 0) * 1.3)
    dd = abs(z - cz)
    return 2 if dd < .14 else (1 if dd < .3 else 0)


def gold_line_arm(z, y, side):
    """A gold line down the outside of the upper arm, from under the pad to above the elbow."""
    if y > 30.5 or y < 24.6:
        return 0
    cz = .3 + .45 * math.sin((y - 25) * 1.1)
    dd = abs(z - cz)
    return 2 if dd < .13 else (1 if dd < .28 else 0)


def pillow(u, v, kx=1.0, ky=1.0):
    """1 in the middle of a face falling towards its edges: a muscle's roundness."""
    return 1 - (kx * (2 * u - 1) ** 2 + ky * (2 * v - 1) ** 2) * .5


def skin(c, face, u, v, p, n):
    part = c.opts.get('part')
    side = c.opts.get('side', -1)
    x, y, z = p
    lit = max(0.0, float(n @ LIGHT))
    t = .36 + .3 * lit + .2 * pillow(u, v, .8, .6)
    if face == 'down':
        t -= .2
    if face == 'south':
        t -= .05
    ink = 0.0                            # how much of the line colour to lay over the tone
    if face == 'north':
        if part == 'pec':
            # a full pec: light high on it, a hard shadow under it, the line under the outer edge
            t += .16 * (1 - v) - .5 * max(0, v - .72) * 3.6
            if v > .9:
                ink = .55
        elif part == 'ab':
            row = c.opts['row']
            t += .2 - .42 * v + (.06 if row == 3 else 0)
            if v > .86:
                ink = .45
        elif part in ('abdomen', 'lowribs', 'ribs'):
            # the belly round the abs sits back in shade; the obliques' edge runs down beside them
            t -= .12 if abs(x) < 2.3 else .02
            ob = abs(abs(x) - (2.45 + (26.0 - y) * .28))
            if part != 'ribs' and ob < .18 and y < 25.8:
                ink = .45
            if part == 'ribs' and face == 'north':
                # the ribs under the pecs: three short serratus fingers on each side
                k = ((y - 25.75) * 1.4 + abs(x) * .55) % 1
                if abs(x) > 3.0 and k < .22:
                    ink = .4
        elif part == 'chest':
            if abs(x) < .18 and y > 28.6:
                ink = .55                # the sternum between the pecs
        elif part == 'deltoid':
            t += .08 * (1 - v)
        elif part == 'bicep':
            t += .18 - .3 * v
        elif part == 'quad':
            # the teardrop of the inner quad above the knee
            t += .14 * math.sin(u * math.pi) - .2 * v
            if v > .88:
                ink = .4
        elif part == 'knee':
            t += .1 if (u - .5) ** 2 + (v - .45) ** 2 < .08 else -.06
    if face == 'south':
        if part in ('lats', 'chest', 'ribs', 'traps'):
            if abs(x) < .2:
                ink = .5                 # the spine
            if 28.4 < y < 31.6 and abs(abs(x) - (1.7 + (31.6 - y) * .18)) < .1:
                ink = max(ink, .35)      # the inner edges of the shoulder blades
            if part == 'lats' and abs(abs(x) - (5.2 - (32.0 - y) * .32)) < .12 and y < 30.5:
                ink = max(ink, .3)       # the lats sweeping in to the waist
        if part in ('abdomen', 'lowribs'):
            if abs(x) < .2:
                ink = .5
            t -= .06
        if part == 'calf':
            t += .1 * math.sin(u * math.pi) - .1 * v
            if abs(u - .5) < .04:
                ink = .3
        if part == 'hamstring' and abs(u - .5) < .05:
            ink = .3
    if face in ('east', 'west') and part in ('lowribs', 'ribs', 'lats'):
        # serratus and obliques on the flanks
        k = (y * 1.25 + (z if face == 'west' else -z) * .5) % 1
        if k < .16 and 23.6 < y < 28.5:
            ink = max(ink, .35)
    t += .02 * noise(x, y, z)
    col = ramp(SKIN[1:], t)
    # every box's edges a little darker, like the art's line work round the muscles
    if edge_texels(c, face, u, v) < 1 and part not in ('neck', 'head', 'jaw'):
        ink = max(ink, .35)
    if ink:
        col = mix(col, SKIN[0], ink)
    # the cyan bounce light under the arms and on the shaded edges
    if face == 'down' and part in ('deltoid', 'pec', 'upperarm') and edge_texels(c, face, u, v) < 2:
        col = mix(col, SKIN_RIM, .3)
    # gold lines
    g = 0
    if face == 'north' and part in ('abdomen', 'lowribs', 'ribs', 'chest', 'pec'):
        g = gold_line_torso(x, y)
    outer = (side < 0 and face == 'east') or (side > 0 and face == 'west')
    if part in ('thigh', 'shin', 'knee', 'sweep', 'calf') and outer:
        g = gold_line_leg(z, y, side)
    if part in ('upperarm', 'deltoid') and outer:
        g = gold_line_arm(z, y, side)
    if g:
        col = GOLD[4] if g == 2 else GOLD[2]
    if part in ('head', 'jaw', 'nose', 'brow', 'ear'):
        col = face_paint(c, face, u, v, p, n, col)
    if part == 'toes' and face == 'north' and (u * 4) % 1 < .12:
        col = mix(col, SKIN[0], .7)
    if part == 'fist':
        col = fist_paint(c, face, u, v, p, col)
    return col


def fist_paint(c, face, u, v, p, col):
    """A right fist hanging, palm in: the back of the hand outside, the curled fingers inside, knuckles below."""
    side = c.opts.get('side', -1)
    inner = (side < 0 and face == 'west') or (side > 0 and face == 'east')
    if inner:
        # four curled fingers stacked front to back, the thumb crossing over them at the front
        k = (p[2] + 1.85) / 3.7 * 4
        if k % 1 < .14 or (1 - v) < .1:
            return mix(col, SKIN[0], .6)
        return ramp(SKIN[2:], .55 - .3 * v)
    if face == 'down':
        k = (p[2] + 1.85) / 3.7 * 4
        return mix(col, SKIN[0], .55) if k % 1 < .12 else col
    if face in ('east', 'west'):
        # the back of the hand: tendons towards the knuckles
        if v > .78:
            return mix(col, SKIN[0], .4)
    return col


def face_paint(c, face, u, v, p, n, col):
    """The face, after the user's second round of pictures: no mouth at all ("입은 아예 없애줘"); almond eyes of a good
    size ("눈이 너무 작다고": not the thin slits tried first), a little angry, under heavy brows drawn down to the nose."""
    x, y, z = p
    part = c.opts['part']
    ax = abs(x)
    if face != 'north':
        if part == 'head' and face in ('east', 'west') and z < -1.0 and 35.5 < y < 37.6:
            return mix(col, SKIN[0], .3)   # the hollow under the cheekbone, round the side
        if part == 'ear':
            return mix(col, SKIN[0], .45) if (u - .5) ** 2 + (v - .5) ** 2 < .06 else col
        return col
    if part == 'head':
        base = ramp(SKIN[2:], .6 - .25 * (ax / 2.75) ** 2)
        # the eyes: almond shaped and of a good size, as in the pictures, pale with a small cyan iris under a heavy lid,
        # the brow low over them and a little shade round them
        if .3 < ax < 2.35 and 37.45 < y < 38.62:
            a = (ax - .4) / 1.75                     # along the eye: 0 at its inner corner, 1 at its outer corner
            if 0 <= a <= 1:
                k = math.sin(math.pi * a)
                centre = 37.93 + .16 * a
                # a little angry ("눈이 조금 화나있어야함"): the upper lid comes down towards the nose over the iris
                top, bottom = centre + .3 * k ** .4 - .17 * (1 - a) * k ** .3, centre - .2 * k ** .7
                if top <= y < top + .13:
                    return LINE                      # the heavy upper lid
                if bottom - .07 <= y < bottom:
                    return mix(LINE, base, .45)      # the lower lid, lighter
                if bottom <= y < top:
                    # a big iris and pupil ("눈알이 너무 작아"), cut by the lids like the art's glare
                    ix, iy = (a - .47) * 1.75, y - (centre + .03)
                    r = math.hypot(ix, iy * 1.1)
                    if r < .14:
                        return EYE_WHITE if -.1 < ix < -.03 and .03 < iy < .1 else EYE_DARK
                    if r < .29:
                        return EYE_IRIS
                    return EYE_PALE
            return mix(base, SKIN[0], .3)            # a little shade round the eyes, under the brow
        if ax < .32 and y > 38.1:
            return mix(base, SKIN[0], .35)           # the frown between the brows
        if ax > 2.1 and y < 38.4:
            return mix(base, SKIN[1], .5)            # the side of the face turns away into shade
        if 1.3 < ax < 2.1 and 35.9 < y < 37.2:
            return mix(base, SKIN[1], .4)            # cheek hollows under the cheekbones
        return base
    if part == 'brow':
        # heavy brows coming down low over the nose, rising outwards: a hard look
        if .2 < ax < 2.4:
            low = 38.56 + .2 * (ax - .2)            # brows drawn down to the nose: a frown
            if low - .02 < y < low + .34 + .08 * max(0, 1 - ax):
                return LINE
        return mix(ramp(SKIN[2:], .5), SKIN[0], .25)
    if part == 'nose':
        if v > .8:
            return ramp(SKIN[2:], .5)                # no dark nostrils: nothing under the nose may read as a mouth
        return ramp(SKIN[2:], .78 - .25 * v) if u > .35 else ramp(SKIN[2:], .45)
    if part == 'jaw':
        # no mouth (the scarf comes up over it anyway)
        base = ramp(SKIN[2:], .5 - .3 * (ax / 2.25) ** 2)
        return mix(base, SKIN[1], .45) if ax > 1.75 else base
    return col


def hair(c, face, u, v, p, n):
    part = c.opts.get('part')
    up = float(n[1])
    if part == 'lock':
        lo, hi = extents(c)
        k = c.opts['piece']
        # along the whole lock: 0 at the root, 1 at the tip (each piece stands along +y in its own frame)
        local = (p[1] - lo[1] - c.opts['sink']) / c.opts['seg'] if face not in ('up', 'down') else (1.0 if face == 'up' else 0.0)
        along = c.opts['start'] + np.clip(local, 0, 1) * c.opts['seg'] / c.opts['length']
        across = u
        streak = (across * 2.6 + along * 2.1 + c.opts['lock'] * .37) % 1
        t = .3 + .35 * along + .15 * up + (.22 if streak < .18 else 0) - (.12 if face in ('down',) else 0)
        t += .04 * noise(p[0], p[1], p[2], 3)
        col = ramp(HAIR[1:], t)
        if along > .93:
            col = mix(col, HAIR[0], .5)
        return outline(c, face, u, v, col, .6)
    x, y, z = p
    s = (x * .9 + .3 * math.sin((z if face == 'up' else y) * 1.2)) % 1.1
    t = .35 + (.22 if s < .25 else 0) + .1 * up
    return outline(c, face, u, v, ramp(HAIR[1:], t), .45)


def gold(c, face, u, v, p, n):
    """Gold: a bright band high on each ring, dark at its lower edge, outlined."""
    part = c.opts.get('part')
    if face in ('up', 'down'):
        col = GOLD[3] if face == 'up' else GOLD[1]
    else:
        t = .82 - .6 * v + (.3 if .15 < v < .38 else 0)
        col = ramp(GOLD[1:], t)
    if part in ('knuckle', 'stud'):
        r = (u - .5) ** 2 + (v - .5) ** 2
        col = GOLD[5] if r < .03 else (GOLD[4] if r < .12 else GOLD[2])
        if part == 'knuckle' and face == 'down' and r < .05:
            col = GOLD[1]                 # each knuckle ring has a hollow
    return outline(c, face, u, v, col, .65, ink=GOLD[0])


def gem(c, face, u, v, p, n):
    """The headband's medallion: a gold ring round a pale cyan stone."""
    x, y, z = p
    r = math.hypot(x / .9, (y - 40.05) / 1.05)
    if face == 'north':
        if r < .46:
            return GEM[2] if (x + .15) ** 2 + (y - 40.3) ** 2 < .03 else GEM[1]
        if r < .58:
            return GEM[0]
        if r < .66:
            return GOLD[1]
        col = ramp(GOLD[2:], .85 - .5 * (y - 39.0) / 2.25)
        return outline(c, face, u, v, col, .7, ink=GOLD[0])
    return outline(c, face, u, v, GOLD[2], .7, ink=GOLD[0])


def cream(c, face, u, v, p, n):
    """The shoulder pads: thin cream plates stacked into a shell, each with a gold edge, so from any side the stack reads
    as the art's concentric stripes of cream and gold."""
    part = c.opts.get('part')
    layer = c.opts.get('layer', 0)
    up = float(n[1])
    if face == 'down':
        return outline(c, face, u, v, CREAM[0], .7, ink=GOLD[0])
    if part == 'lip':
        col = ramp(CREAM[1:], .55 - .3 * v) if face in ('east', 'west') or .2 < v < .8 else GOLD[3]
        return outline(c, face, u, v, col, .7, ink=GOLD[0])
    if face == 'up':
        col = ramp(CREAM[1:], .5 + .1 * layer + .15 * (1 - v))
        if layer == c.opts.get('layers', 1) - 1 and edge_texels(c, face, u, v) < 1.2:
            col = GOLD[4]
        return col
    # a plate's edge: cream under a thin gold line, dark along its foot
    w, h = face_size(c, face)
    col = ramp(CREAM[1:], .62 + .08 * layer - .2 * v)
    if v * h * DENS < 1.0:
        col = GOLD[4]
    elif (1 - v) * h * DENS < 1.0:
        col = GOLD[1]
    return col


def scarf(c, face, u, v, p, n):
    """The scarf: red-orange; folds slanting round the rolls at the neck, creases running down its flying end."""
    part = c.opts.get('part')
    up = float(n[1])
    t = .55 + .15 * up - (.15 if face in ('south', 'down') else 0)
    if part == 'wrap':
        t += .08 * c.opts.get('roll', 0) + .14 * math.sin(p[1] * 2.6 + (p[0] + p[2]) * .8)
        if (p[1] * 2.6 + (p[0] + p[2]) * .8) % (2 * math.pi) < .3:
            t -= .25                                  # a fold's crease
    elif part == 'knot':
        t -= .1
    else:
        t += .12 * (1 - c.opts.get('start', 0)) - .1 * v
        for cu in (.3, .68):
            if abs(u - cu - .06 * math.sin(v * 3 + c.opts['piece'])) < .045:
                t -= .25
    col = ramp(SCARF[1:], t)
    return outline(c, face, u, v, col, .55, ink=SCARF[0])


def gaunt(c, face, u, v, p, n):
    """The gauntlets: dark green, a sheen down the middle of each side, dark edges."""
    up = float(n[1])
    t = .4 + .3 * math.sin(u * math.pi) ** 2 * (face in ('north', 'south', 'east', 'west')) + .1 * up - (.15 if face == 'down' else 0)
    if face in ('north', 'south', 'east', 'west') and c.opts.get('piece') == 0 and v > .85:
        t -= .15                          # the flare at the elbow throws a shade under it
    col = ramp(GAUNT[1:], t)
    return outline(c, face, u, v, col, .6, ink=GAUNT[0])


def cloth(c, face, u, v, p, n):
    """The loincloth: white with a lavender cast, soft folds, shaded under the belt, torn at the hem."""
    piece, pieces = c.opts['piece'], c.opts['pieces']
    hang = c.opts['hang']
    w, h = face_size(c, face)
    across = u * w
    if face in ('up', 'down'):
        return CLOTH[2]
    inward = (face == 'south' and hang == 'front') or (face == 'north' and hang == 'back') or \
             (hang == 'side' and ((c.opts.get('side', -1) < 0 and face == 'west') or (c.opts.get('side', -1) > 0 and face == 'east')))
    fold = math.sin(across * 1.25 + piece * .9 + (hang == 'back') * 1.7)
    t = .62 + .2 * fold - .3 * max(0, .3 - v) * (piece == 0) * 3 - .06 * piece
    if inward:
        t -= .25
    col = ramp(CLOTH, t)
    if (across * 1.25 + piece * .9 + (hang == 'back') * 1.7) % (2 * math.pi) < .25:
        col = mix(col, CLOTH[0], .55)     # a crease
    if piece == 0 and hang == 'front' and face == 'north':
        for sx in (-2.2, 2.2):
            if (p[0] - sx) ** 2 + (p[1] - 19.9) ** 2 < .1:
                return GOLD[4] if (p[0] - sx + .07) ** 2 + (p[1] - 19.97) ** 2 < .02 else GOLD[2]
    if piece == pieces - 1:
        cut = .8 + .13 * abs(math.sin(across * 1.35 + 1.3)) + .06 * math.sin(across * 4.3)
        if v > cut:
            return None                   # the torn hem
        if v > cut - 1.2 / (h * DENS):
            col = mix(col, CLOTH[0], .7)
    if edge_texels(c, face, u, v) < 1 and v < .98:
        col = mix(col, CLOTH[0], .45)
    return col


def shorts(c, face, u, v, p, n):
    t = .35 + .35 * math.sin(u * math.pi) * (face == 'north') + .1 * float(n[1])
    return ramp(SHORTS, t)


def buckle(c, face, u, v, p, n):
    """A round buckle: a pale green-white face in a gold ring."""
    r, cx, cy = c.opts['r'], c.opts['cx'], c.opts['cy']
    x, y, z = p
    d = math.hypot(x - cx, y - cy) / r
    if face == 'north':
        if d < .6:
            hl = (x - cx + .25 * r) ** 2 + (y - cy - .25 * r) ** 2 < (.18 * r) ** 2
            return BUCKLE_FACE[2] if hl else ramp(BUCKLE_FACE, .75 - .5 * (d / .6) ** 2)
        if d < .68:
            return GOLD[1]
        if d < .9:
            return ramp(GOLD[2:], .8 - .5 * (y - cy + r) / (2 * r))
        return GOLD[0]
    return GOLD[2]


PAINT = {'skin': skin, 'hair': hair, 'gold': gold, 'gem': gem, 'cream': cream, 'scarf': scarf, 'gaunt': gaunt,
         'cloth': cloth, 'shorts': shorts, 'buckle': buckle}


# ---------------------------------------------------------------- the atlas
def tex_size(c, face):
    w, h = face_size(c, face)
    d = dens(c)
    return max(1, int(math.ceil(w * d - 1e-6))), max(1, int(math.ceil(h * d - 1e-6)))


def pack():
    items = []
    for ci, c in enumerate(CUBES):
        for f in c.faces:
            tw, th = tex_size(c, f)
            items.append((th, tw, ci, f))
    items.sort(key=lambda t: (-t[0], -t[1]))
    x = y = shelf = 0
    place = {}
    for th, tw, ci, f in items:
        if x + tw + 1 > ATLAS_W:
            x, y, shelf = 0, y + shelf + 1, 0
        place[(ci, f)] = (x, y, tw, th)
        x += tw + 1
        shelf = max(shelf, th)
    height = y + shelf + 1
    height = int(math.ceil(height / 64) * 64)
    return place, height


def paint(place, height):
    img = np.zeros((height, ATLAS_W, 4), dtype=np.float64)
    for (ci, f), (x0, y0, tw, th) in place.items():
        c = CUBES[ci]
        painter = PAINT[c.mat]
        n0 = np.array(NORMALS[f], dtype=np.float64)
        for j in range(th):
            for i in range(tw):
                u, v = (i + .5) / tw, (j + .5) / th
                p_local = face_point(c, f, u, v)
                p_rest, n_rest = to_rest(c, p_local, n0)
                # hair, cloth and the scarf are painted along themselves, everything else where it sits on the body
                col = painter(c, f, u, v, p_local if c.mat in ('hair', 'cloth', 'scarf') else p_rest, n_rest)
                if col is None:
                    img[y0 + j, x0 + i] = (0, 0, 0, 0)
                else:
                    img[y0 + j, x0 + i, :3] = np.clip(col, 0, 1)
                    img[y0 + j, x0 + i, 3] = 1
    return (img * 255 + .5).astype(np.uint8)


def check_coplanar():
    """Faces of different cubes lying in the same plane, facing the same way and overlapping would flicker."""
    rects = []
    for ci, c in enumerate(CUBES):
        if c.rot is not None or any(abs(a) > 1e-9 for a in rest_transform(c.bone)[0].flatten() - np.eye(3).flatten()):
            continue
        lo, hi = extents(c)
        for f in c.faces:
            axis = {'north': 2, 'south': 2, 'east': 0, 'west': 0, 'up': 1, 'down': 1}[f]
            val = lo[axis] if f in ('north', 'east', 'down') else hi[axis]
            others = [a for a in range(3) if a != axis]
            rects.append((axis, f, round(float(val), 4), [(lo[a], hi[a]) for a in others], ci, c.bone))
    bad = []
    for i in range(len(rects)):
        for j in range(i + 1, len(rects)):
            a, b = rects[i], rects[j]
            if a[0] != b[0] or a[1] != b[1] or a[2] != b[2] or a[4] == b[4]:
                continue
            if all(min(a[3][k][1], b[3][k][1]) - max(a[3][k][0], b[3][k][0]) > 1e-3 for k in range(2)):
                bad.append((a[1], a[2], CUBES[a[4]].opts.get('part'), CUBES[b[4]].opts.get('part')))
    return bad


def geo(place, height):
    bones = []
    for name, b in BONES.items():
        entry = {'name': name, 'pivot': [round(float(v + (LIFT if i == 1 else 0)), 4) for i, v in enumerate(b.pivot)]}
        if b.parent:
            entry['parent'] = b.parent
        if any(abs(a) > 1e-6 for a in b.rot):
            entry['rotation'] = [round(float(v), 3) for v in b.rot]
        cubes = []
        for ci, c in enumerate(CUBES):
            if c.bone != name:
                continue
            cube = {'origin': [round(float(v + (LIFT if i == 1 else 0)), 4) for i, v in enumerate(c.origin)], 'size': [round(float(v), 4) for v in c.size]}
            if c.inflate:
                cube['inflate'] = c.inflate
            if c.rot is not None:
                cube['rotation'] = [round(float(v), 3) for v in c.rot]
                cube['pivot'] = [round(float(v + (LIFT if i == 1 else 0)), 4) for i, v in enumerate(c.pivot)]
            cube['uv'] = {f: {'uv': [place[(ci, f)][0], place[(ci, f)][1]], 'uv_size': [place[(ci, f)][2], place[(ci, f)][3]]} for f in c.faces}
            cubes.append(cube)
        if cubes:
            entry['cubes'] = cubes
        bones.append(entry)
    return {'format_version': '1.12.0', 'minecraft:geometry': [{
        'description': {'identifier': 'geometry.botand.star_platinum', 'texture_width': ATLAS_W, 'texture_height': height,
                        'visible_bounds_width': 5, 'visible_bounds_height': 4, 'visible_bounds_offset': [0, 1.5, 0]},
        'bones': bones}]}


# ---------------------------------------------------------------- poses
# The figure's stances, written with the model for the game (assets/celestialfall/stands/star_platinum_poses.json):
# each bone's turn in degrees as Blockbench writes it (added to the bone's own turn) and its offset in units. A limb can
# be aimed instead: "aim" gives the way its length should run (from the joint to the next) and the way its front should
# face, and the turn that does it is worked out here from the bones above it.
POSES = {
    # a stand floating at rest: a little off the ground, toes pointing down, arms loose, knees bent
    'idle': {
        'root': {'pos': (0, 2.5, 0)},
        'torso': {'rot': (2, 0, 0)},
        'head': {'rot': (4, 0, 0)},
        'right_arm': {'rot': (-6, 0, 7)}, 'right_forearm': {'rot': (-14, 0, 0)},
        'left_arm': {'rot': (-6, 0, -7)}, 'left_forearm': {'rot': (-14, 0, 0)},
        'right_leg': {'rot': (-6, 0, 3)}, 'right_shin': {'rot': (14, 0, 0)}, 'right_foot': {'rot': (28, 0, 0)},
        'left_leg': {'rot': (8, 0, -3)}, 'left_shin': {'rot': (20, 0, 0)}, 'left_foot': {'rot': (32, 0, 0)},
    },
    # the art the user sent: the left fist raised out to the side, the right fist on the hip, the hips turned to the
    # left and the chest back to the front, the loincloth thrown to the left by the wind
    'hero': {
        'hips': {'rot': (0, -10, 0)},
        'torso': {'rot': (-3, 5, 0)},
        'chest': {'rot': (-4, 9, 2)},
        'neck': {'rot': (3, 3, 0)},
        'head': {'rot': (3, 9, -3)},
        'left_arm': {'aim': ((.8, .12, -.55), (0, .7, -.7))},
        'left_forearm': {'aim': ((-.15, .95, -.3), (-1, 0, -.2))},
        'left_hand': {'rot': (15, 0, 0)},
        'left_pad': {'rot': (0, 0, -14)},
        'right_arm': {'aim': ((-.45, -.82, .38), (0, 0, -1))},
        'right_forearm': {'aim': ((.62, -.64, -.41), (0, 1, 0))},
        'right_leg': {'rot': (-10, 0, 3)}, 'right_shin': {'rot': (6, 0, 0)},
        'left_leg': {'rot': (6, 0, -5)}, 'left_shin': {'rot': (10, 0, 0)}, 'left_foot': {'rot': (-4, 0, 0)},
        'cloth_front': {'rot': (-12, 0, -14)}, 'cloth_front_1': {'rot': (-6, 0, -8)}, 'cloth_front_2': {'rot': (-8, 0, -10)},
        'cloth_left': {'rot': (0, 0, -10)}, 'cloth_left_1': {'rot': (0, 0, -8)},
        'cloth_right': {'rot': (8, 0, -4)},
    },
    # coming out of its user (2.51.0): curled up behind crossed forearms, knees drawn up, head down; it bursts open from
    # here into the hero stance (client/stand/StandSummonTimeline)
    'summon': {
        'root': {'pos': (0, 2.5, 0)},
        'hips': {'rot': (6, 0, 0)},
        'torso': {'rot': (9, 0, 0)},
        'chest': {'rot': (8, 0, 0)},
        'neck': {'rot': (6, 0, 0)},
        'head': {'rot': (14, 0, 0)},
        'left_arm': {'aim': ((-.22, -.5, -.84), (0, .85, -.5))},
        'left_forearm': {'aim': ((-.82, .52, -.24), (0, -.2, -1))},
        'left_hand': {'rot': (0, 0, 10)},
        'right_arm': {'aim': ((.24, -.56, -.79), (0, .85, -.5))},
        'right_forearm': {'aim': ((.84, .5, -.12), (0, -.2, -1))},
        'right_hand': {'rot': (0, 0, -10)},
        'left_pad': {'rot': (0, 0, 9)}, 'right_pad': {'rot': (0, 0, -9)},
        'right_leg': {'rot': (-58, 0, 5)}, 'right_shin': {'rot': (80, 0, 0)}, 'right_foot': {'rot': (26, 0, 0)},
        'left_leg': {'rot': (-42, 0, -5)}, 'left_shin': {'rot': (70, 0, 0)}, 'left_foot': {'rot': (22, 0, 0)},
        'cloth_front': {'rot': (-24, 0, 0)}, 'cloth_front_1': {'rot': (-10, 0, 0)}, 'cloth_front_2': {'rot': (-6, 0, 0)},
        'cloth_back': {'rot': (6, 0, 0)},
    },
}


def frame(down, front):
    """A bone's turn in the model that sends its -y along down and its -z along front (made square to down)."""
    y = -norm(down)
    f = norm(front)
    z = -(f - (f @ -y) * -y)
    z = norm(z)
    x = np.cross(y, z)
    return np.column_stack([x, y, z])


def pose_table(spec):
    """The stance as each bone's turn (degrees, Blockbench's way) and offset, aims worked out down the rig."""
    world = {}
    out = {}
    for name, b in BONES.items():
        parent = world[b.parent] if b.parent else np.eye(3)
        entry = spec.get(name, {})
        if 'aim' in entry:
            Rrel = parent.T @ frame(*entry['aim'])
            delta = euler_of(Rrel)
            delta = [d - r for d, r in zip(delta, b.rot)]
        else:
            delta = list(entry.get('rot', (0, 0, 0)))
        world[name] = parent @ rot_matrix([r + d for r, d in zip(b.rot, delta)])
        if any(abs(d) > 1e-6 for d in delta) or 'pos' in entry:
            out[name] = {'rot': [round(float(d), 3) for d in delta]}
            if 'pos' in entry:
                out[name]['pos'] = [float(v) for v in entry['pos']]
    return out


def main():
    for b in check_coplanar():
        print('coplanar overlap (would flicker):', b)
    place, height = pack()
    img = paint(place, height)
    (ROOT / 'geo/entity').mkdir(parents=True, exist_ok=True)
    (ROOT / 'textures/entity').mkdir(parents=True, exist_ok=True)
    (ROOT / 'geo/entity/star_platinum.geo.json').write_text(json.dumps(geo(place, height), indent=1))
    Image.fromarray(img, 'RGBA').save(ROOT / 'textures/entity/star_platinum.png', optimize=True)
    (ROOT / 'stands').mkdir(parents=True, exist_ok=True)
    (ROOT / 'stands/star_platinum_poses.json').write_text(json.dumps({k: pose_table(v) for k, v in POSES.items()}, indent=1))
    print(f'{len(BONES)} bones, {len(CUBES)} cubes, texture {ATLAS_W}x{height}')


if __name__ == '__main__':
    main()
