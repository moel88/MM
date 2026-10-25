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
TIARA_LIGHT, TIARA_DARK = hexc('#f2d8ca'), hexc('#141019')      # the tiara's cream wedges and its dark cut-outs
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

# head: a long face with a strong jaw and its nose, painted at FACE_DENS. After the user's third picture
# ("스타플래티나 얼굴과 머리카락을 이걸 참고해서", 2.53.0): no brows standing out of it, and a tiara over the eyes: a gold
# band whose lower edge sits right on them, its oval stone hanging a little lower in the middle between them. The nose
# is the one from before it ("코는 롤백해": a drawn line between two pale diamonds was tried first).
rounded(HEAD, (-2.75, 35.0, -3.0), (5.5, 6.25, 5.75), 'skin', .45, part='head', dens=FACE_DENS)
box(HEAD, (-2.25, 34.25, -3.25), (4.5, 1.75, 4.0), 'skin', part='jaw', dens=FACE_DENS)
box(HEAD, (-0.5, 36.6, -3.5), (1.0, 1.6, 0.5), 'skin', part='nose', dens=FACE_DENS)
for side in (-1, 1):
    box(HEAD, (2.75 if side > 0 else -3.2, 36.75, -0.6), (0.45, 1.75, 1.25), 'skin', part='ear', side=side, dens=FACE_DENS)
BAND_Y0, BAND_Y1 = 39.0, 40.45
box(HEAD, (-3.15, BAND_Y0, -3.3), (6.3, BAND_Y1 - BAND_Y0, 6.95), 'gold', part='headband', dens=FACE_DENS)
box(HEAD, (-0.95, 38.55, -3.62), (1.9, 2.4, 0.36), 'gem', part='medallion', dens=FACE_DENS)

# hair: a cap over the top, back and sides of the head, and on it a great mane rising straight up off the band and
# fanning out (the user's third picture): thick wavy locks in three rows, front, middle and back, the outer ones leaning
# further out the higher they go, their pointed tips curling outwards, and a lock puffing out over each temple. It
# replaces the mane streaming back of 2.50.0 to 2.52.0.
box(HEAD, (-3.05, BAND_Y1 - .05, -3.1), (6.1, 1.95, 6.45), 'hair', part='cap')
box(HEAD, (-3.0, 35.4, 2.45), (6.0, 5.25, 1.0), 'hair', part='capback')
for side in (-1, 1):
    box(HEAD, (2.7 if side > 0 else -3.1, 38.0, -1.6), (0.4, 2.9, 4.2), 'hair', part='capside', side=side)

FRONT, BACK = (0, 0, -1), (0, 0, 1)
MANE = [
    # root, first direction, last direction, length, width, thickness, flat side faces (normal), wave (deg), phase
    # the front row, rising off the band
    ((0.0, 41.4, -2.1), (0, 1, .05), (0, .75, .65), 7.8, 3.8, 2.0, FRONT, 7, 0.0),
    ((-1.9, 41.3, -2.0), (-.3, 1, .05), (-.75, .7, .4), 8.0, 3.9, 1.9, FRONT, 7, 1.1),
    ((1.9, 41.3, -2.0), (.3, 1, .05), (.75, .7, .4), 8.0, 3.9, 1.9, FRONT, 7, 2.3),
    ((-3.0, 40.9, -1.6), (-.8, 1, 0), (-1, .35, .2), 7.6, 3.6, 1.8, FRONT, 8, .6),
    ((3.0, 40.9, -1.6), (.8, 1, 0), (1, .35, .2), 7.6, 3.6, 1.8, FRONT, 8, 1.9),
    # the middle row
    ((0.0, 42.4, 0.2), (0, 1, .25), (0, .6, .8), 8.6, 4.2, 2.2, FRONT, 8, 2.8),
    ((-2.3, 42.2, 0.3), (-.4, 1, .2), (-.85, .6, .55), 8.8, 4.1, 2.0, FRONT, 8, .4),
    ((2.3, 42.2, 0.3), (.4, 1, .2), (.85, .6, .55), 8.8, 4.1, 2.0, FRONT, 8, 1.6),
    ((-3.4, 41.4, 0.4), (-.9, .9, .15), (-1, .25, .45), 8.6, 3.8, 1.9, FRONT, 9, 2.1),
    ((3.4, 41.4, 0.4), (.9, .9, .15), (1, .25, .45), 8.6, 3.8, 1.9, FRONT, 9, .9),
    # the back row, rising and leaning back
    ((0.0, 41.8, 2.6), (0, .85, .55), (0, .55, .95), 8.6, 4.2, 2.1, BACK, 8, 1.4),
    ((-2.2, 41.5, 2.5), (-.4, .85, .5), (-.75, .45, .85), 8.4, 3.8, 2.0, BACK, 8, 2.6),
    ((2.2, 41.5, 2.5), (.4, .85, .5), (.75, .45, .85), 8.4, 3.8, 2.0, BACK, 8, .2),
    ((-3.3, 40.6, 2.1), (-.85, .7, .45), (-.9, .25, .8), 7.6, 3.2, 1.8, BACK, 9, 1.2),
    ((3.3, 40.6, 2.1), (.85, .7, .45), (.9, .25, .8), 7.6, 3.2, 1.8, BACK, 9, 2.4),
    # a lock puffing out over each temple and up
    ((-3.0, 39.9, -0.6), (-1, .4, 0), (-.5, 1, .1), 6.4, 2.8, 1.6, FRONT, 6, .3),
    ((3.0, 39.9, -0.6), (1, .4, 0), (.5, 1, .1), 6.4, 2.8, 1.6, FRONT, 6, 1.7),
    # the back of the head, below the mane
    ((-1.5, 38.6, 3.2), (-.25, -.3, 1), (-.35, -.7, .7), 6.0, 3.2, 1.4, (0, 1, 0), 7, 2.2),
    ((1.5, 38.6, 3.2), (.25, -.3, 1), (.35, -.7, .7), 6.0, 3.2, 1.4, (0, 1, 0), 7, .8),
]
# two thick cores fill the mane, so the locks read as the ragged edge of one mass of hair
MANE_CORES = [
    ((0.0, 41.5, -0.8), (0, 1, .2), (0, .8, .55), 7.5, 8.4, 4.6, FRONT, 0, 0),
    ((0.0, 41.3, 1.6), (0, .9, .5), (0, .65, .8), 7.2, 8.0, 4.2, BACK, 0, 0),
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
    strand(f'hair_{i:02d}', HEAD, pts, [width * f for f in (1, .9, .72, .45)], [thick * f for f in (1, .88, .7, .5)],
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
    # the Star Finger (2.60.0): the first two fingers held straight out of the fist from their knuckles, side by side.
    # Hidden but for the Star Finger, when the game stretches the bone along them (from the knuckles) out to whoever
    # they strike and turns it to them (client/stand/StarPlatinumRenderer): no face across them but the tips, and
    # nothing painted along them that a stretch would smear.
    finger = bone('right_finger', hand, FINGER_ROOT)
    for k in range(2):
        box(finger, (FINGER_ROOT[0] - FINGER_W / 2, FINGER_ROOT[1] - FINGER_LEN, -1.62 + .82 * k), (FINGER_W, FINGER_LEN, .76),
            'skin', part='finger', piece=k, faces=('north', 'south', 'east', 'west', 'down'))


# where the Star Finger's fingers leave the fist (between the first two knuckles, on its face) and their size
FINGER_ROOT, FINGER_LEN, FINGER_W = (-7.62, 15.0, -.85), 3.0, .86
# where its mouth would be, in the head's own space (model units): under the nose, just out from the jaw (2.61.0)
MOUTH = (0.0, 35.6, -3.7)
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
    if part == 'finger':
        return finger_paint(c, face, u, v, n)
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
    if part in ('head', 'jaw', 'nose', 'ear'):
        col = face_paint(c, face, u, v, p, n, col)
    if part == 'toes' and face == 'north' and (u * 4) % 1 < .12:
        col = mix(col, SKIN[0], .7)
    if part == 'fist':
        col = fist_paint(c, face, u, v, p, col)
    return col


def finger_paint(c, face, u, v, n):
    """The Star Finger's fingers (2.60.0): lit across them and the same all along them (the game stretches them out
    along their length to whoever they strike), dark down their long edges like the art's line work; the tips a
    little paler, rimmed dark."""
    lit = max(0.0, float(n @ LIGHT))
    if face == 'down':
        col = ramp(SKIN[1:], .5 + .25 * lit + .12 * pillow(u, v))
        return mix(col, SKIN[0], .45) if edge_texels(c, face, u, v) < .6 else col
    col = ramp(SKIN[1:], .38 + .3 * lit + .18 * (1 - (2 * u - 1) ** 2))
    w, _ = face_size(c, face)
    return mix(col, SKIN[0], .4) if min(u, 1 - u) * w * dens(c) < .7 else col


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


EYE_IN, EYE_OUT = (.55, 37.97), (2.27, 38.88)   # each eye's inner corner (low, by the nose) and outer corner (high)
EYE_LOOK = .4                                     # where along the eye the iris sits: a little in, glaring ahead


def eye_paint(ax, y, base):
    """One eye at (|x|, y) on the face, or None off it: glaring as in the user's third picture ("사진에는 얼굴이
    화났는데 너꺼는 왜 화가 안 남?"). It slants steeply down to the nose under a heavy dark lid that fills up to the tiara's
    band and meets the other one's over the bridge in a frown; the opening is a wide crescent under the lid's straight
    edge, the big iris pressed up under it, its top cut off, with white below it. Not a thin slit (round 2 of the user's
    pictures: "눈이 너무 작다고", "눈알이 너무 작아")."""
    (x0, y0), (x1, y1) = EYE_IN, EYE_OUT
    if y >= BAND_Y0:
        return None
    a = (ax - x0) / (x1 - x0)                      # along the eye: 0 at its inner corner, 1 at its outer corner
    if a < 0:
        return LINE if y > 38.2 else None           # the frown over the bridge, above the nose
    if a > 1:
        return None
    s = math.sin(math.pi * a)
    chord = y0 + (y1 - y0) * a
    top = chord + .1 * s                           # the lid's edge, almost straight
    bottom = chord - .64 * s ** .8 * (1 - .25 * a)  # the lower lid, sagging most on the inner side
    if y >= top:
        return LINE                                # the heavy lid, dark up to the band
    if y >= bottom:
        cx = x0 + (x1 - x0) * EYE_LOOK
        cy = y0 + (y1 - y0) * EYE_LOOK + .1 * math.sin(math.pi * EYE_LOOK) - .14
        r = math.hypot(ax - cx, (y - cy) * 1.05)
        if r < .15:
            return EYE_DARK
        if r < .37:
            return EYE_IRIS if r < .31 else mix(EYE_IRIS, EYE_DARK, .55)
        return EYE_PALE if y > top - .09 else EYE_WHITE   # the lid's shadow on the white under it
    if y >= bottom - .09:
        return mix(LINE, base, .35)                # the lower lid
    if y >= bottom - .3 and .08 < a < .92:
        return mix(base, SKIN[1], .4)              # shade under the eye
    return None


def face_paint(c, face, u, v, p, n, col):
    """The face: no mouth (the scarf is over it), the nose standing out as before 2.53.0, and under the tiara's band the
    eyes glaring out from under heavy lids (eye_paint)."""
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
        eye = eye_paint(ax, y, base)
        if eye is not None:
            return eye
        if ax > 2.1 and y < 38.4:
            return mix(base, SKIN[1], .5)            # the side of the face turns away into shade
        if 1.3 < ax < 2.1 and 35.9 < y < 37.2:
            return mix(base, SKIN[1], .4)            # cheek hollows under the cheekbones
        return base
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
    """Gold: a bright band high on each ring, dark at its lower edge, outlined. The headband's front is a tiara (2.53.0,
    the user's third picture): on each side of the stone a cream wedge pointing in at it and a dark cut-out pointing out."""
    part = c.opts.get('part')
    if part == 'headband' and face == 'north':
        x, y = p[0], p[1]
        yc, hh = (BAND_Y0 + BAND_Y1) / 2, (BAND_Y1 - BAND_Y0) / 2 - .2
        a = (abs(x) - 1.0) / 2.15
        if 0 <= a <= 1:
            dy = abs(y - yc)
            if .03 <= a <= .5 and dy < (hh + .08) * (a - .03) / .47:
                return mix(TIARA_LIGHT, GOLD[5], .3) if y > yc else TIARA_LIGHT
            if .58 <= a <= .93 and dy < hh * (.93 - a) / .35:
                return TIARA_DARK
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
    """The tiara's stone (2.53.0, after the user's third picture): an upright oval of pale blue-white with a dark crescent
    of reflection across its upper half, in an oval gold setting that hangs a little below the band between the eyes.
    Outside the setting the plate is see-through."""
    x, y, z = p
    if face != 'north':
        return None
    cy = 39.75
    r = math.hypot(x / .55, (y - cy) / .82)
    if r < 1:
        cr = math.hypot(x / .44, (y - cy - .16) / .58)
        if .76 < cr < 1.0 and y > cy + .02:
            return GEM[0]                            # the crescent
        return GEM[2] if (x - .16) ** 2 + (y - cy + .28) ** 2 < .05 else GEM[1]
    if r < 1.1:
        return GOLD[1]
    if r < 1.3:
        return GOLD[4] if y > cy else GOLD[3]
    return None


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
    # ---- the basic attack (2.54.0; 2.55.0 made fiercer, user request: "스타플래티나 평타 모션을 더 격하게 만들어주고";
    # 2.56.0 fiercer again and faster, user request: "더 격하게 때리게 모션을"):
    # four punches thrown from a boxer's stance, the left side forward (an orthodox guard) -- a left jab, a right
    # cross, a left hook and a lunging right that throws whoever it strikes. Each punch has its loading pose and its
    # strike; client/stand/StandPunch moves through them. The arms are aimed in the model's own space (front -z, its
    # right -x), so they meet the target in front of it whatever the body's turn. Every load is deep (coiled far round,
    # the fist drawn back, the body low) and every strike drives the whole body after the fist (the hips and shoulders
    # wheeled through, leaning far over the front leg, the back leg kicked out straight), the arm thrown out straight.
    # Since 2.56.0 the guard hunts lower and further forward, the loads coil further round and lower, and the strikes
    # throw the body further through: the hips wheel some ten degrees further, the body lunges a quarter further on
    # and leans further over, the back leg flies out straighter and the head drives in behind the fist.
    # floating in the guard between punches: crouched low and leaning hard in, turned to its right so the left
    # shoulder leads, the head held up to the front, both fists up at the eyes, the left knee forward
    'guard': {
        'root': {'pos': (0, .6, -1.0)},
        'hips': {'rot': (16, 26, 0)}, 'torso': {'rot': (10, 4, 0)}, 'chest': {'rot': (8, 4, 0)},
        'neck': {'rot': (-11, -12, 0)}, 'head': {'rot': (-14, -19, 0)},
        'left_arm': {'reach': ((4.5, 38.5, -15.5), (0.6, -1, 0.2))},
        'right_arm': {'reach': ((-2.5, 38.0, -6.5), (-0.4, -1, 0.1))},
        'left_pad': {'rot': (0, 0, -8)}, 'right_pad': {'rot': (0, 0, 6)},
        'left_leg': {'rot': (-38, 0, -4)}, 'left_shin': {'rot': (46, 0, 0)}, 'left_foot': {'rot': (18, 0, 0)},
        'right_leg': {'rot': (24, 0, 6)}, 'right_shin': {'rot': (48, 0, 0)}, 'right_foot': {'rot': (36, 0, 0)},
        'cloth_front': {'rot': (-12, 0, 0)},
    },
    # 1, the jab: loaded (dropped low and back, wound further round, the left fist snapped back to the chin) ...
    'wind_l': {
        'root': {'pos': (0, 0, 1.3)},
        'hips': {'rot': (14, 42, 0)}, 'torso': {'rot': (10, 9, 0)}, 'chest': {'rot': (8, 10, 0)},
        'neck': {'rot': (-11, -20, 0)}, 'head': {'rot': (-14, -30, 0)},
        'left_arm': {'reach': ((3.0, 37.5, -9.0), (0.6, -1, 0.3))},
        'right_arm': {'reach': ((-2.5, 38.0, -5.5), (-0.4, -1, 0.1))},
        'left_pad': {'rot': (0, 0, -8)}, 'right_pad': {'rot': (0, 0, 6)},
        'left_leg': {'rot': (-34, 0, -4)}, 'left_shin': {'rot': (56, 0, 0)}, 'left_foot': {'rot': (18, 0, 0)},
        'right_leg': {'rot': (26, 0, 6)}, 'right_shin': {'rot': (56, 0, 0)}, 'right_foot': {'rot': (36, 0, 0)},
        'cloth_front': {'rot': (-12, 0, 0)},
    },
    # ... and thrown: the left arm shot out straight at the target, the lead shoulder rammed far forward, the whole
    # body lunging in over the front leg after it, the back leg kicked out straight behind
    'punch_l': {
        'root': {'pos': (0, .8, -4.8)},
        'hips': {'rot': (22, 46, 0)}, 'torso': {'rot': (12, 14, 0)}, 'chest': {'rot': (8, 16, 0)},
        'neck': {'rot': (-12, -24, 0)}, 'head': {'rot': (-16, -38, 0)},
        'left_arm': {'reach': ((0.2, 34.6, -34.0), (0.5, -1, 0))},
        'left_pad': {'rot': (-18, 0, -24)},
        'right_arm': {'reach': ((-2.0, 37.5, -7.0), (-0.4, -1, 0.1))},
        'right_pad': {'rot': (0, 0, 6)},
        'left_leg': {'rot': (-48, 0, -4)}, 'left_shin': {'rot': (30, 0, 0)}, 'left_foot': {'rot': (10, 0, 0)},
        'right_leg': {'rot': (42, 0, 6)}, 'right_shin': {'rot': (14, 0, 0)}, 'right_foot': {'rot': (50, 0, 0)},
        'cloth_front': {'rot': (0, 0, 0)}, 'cloth_back': {'rot': (20, 0, 0)},
    },
    # 2, the cross: loaded (wound hard round to the right and low, the right fist cocked back past the ear, the left
    # still out at the target) ...
    'wind_r': {
        'root': {'pos': (0, 0, 1.8)},
        'hips': {'rot': (14, 52, 0)}, 'torso': {'rot': (10, 14, 0)}, 'chest': {'rot': (8, 12, 0)},
        'neck': {'rot': (-11, -24, 0)}, 'head': {'rot': (-14, -38, 0)},
        'left_arm': {'reach': ((3.0, 37.0, -18.5), (0.6, -1, 0.1))},
        'right_arm': {'reach': ((-4.5, 39.0, 3.0), (-0.7, -1, 0.5))},
        'left_pad': {'rot': (0, 0, -10)}, 'right_pad': {'rot': (0, 0, 12)},
        'left_leg': {'rot': (-32, 0, -4)}, 'left_shin': {'rot': (56, 0, 0)}, 'left_foot': {'rot': (18, 0, 0)},
        'right_leg': {'rot': (26, 0, 8)}, 'right_shin': {'rot': (58, 0, 0)}, 'right_foot': {'rot': (36, 0, 0)},
        'cloth_front': {'rot': (-12, 0, 0)},
    },
    # ... and thrown: the hips and shoulders wrenched round to the left, the right arm shot out straight at the
    # target, the left fist ripped back to the chest, the body flung on over the front leg, the back leg straight
    'punch_r': {
        'root': {'pos': (0, .6, -5.2)},
        'hips': {'rot': (22, -34, 0)}, 'torso': {'rot': (12, -16, 0)}, 'chest': {'rot': (8, -16, 0)},
        'neck': {'rot': (-12, 22, 0)}, 'head': {'rot': (-16, 34, 0)},
        'right_arm': {'reach': ((-0.5, 34.4, -34.5), (-0.5, -1, 0))},
        'right_pad': {'rot': (-18, 0, 24)},
        'left_arm': {'reach': ((3.0, 36.5, -3.5), (0.6, -1, 0.3))},
        'left_pad': {'rot': (0, 0, -8)},
        'left_leg': {'rot': (-46, 0, -4)}, 'left_shin': {'rot': (26, 0, 0)}, 'left_foot': {'rot': (10, 0, 0)},
        'right_leg': {'rot': (46, 0, 6)}, 'right_shin': {'rot': (12, 0, 0)}, 'right_foot': {'rot': (54, 0, 0)},
        'cloth_front': {'rot': (0, 0, 0)}, 'cloth_back': {'rot': (22, 0, 0)},
    },
    # 3, the left hook: loaded (dropped low and back, the weight on the left leg, the body flung open to the left, the
    # left elbow cocked out at the side and the fist out wide behind it) ...
    'wind_hook': {
        'root': {'pos': (0, -.8, 1.8)},
        'hips': {'rot': (16, -24, 8)}, 'torso': {'rot': (12, -16, 8)}, 'chest': {'rot': (8, -10, 5)},
        'neck': {'rot': (-9, 18, -7)}, 'head': {'rot': (-14, 22, -7)},
        'left_arm': {'reach': ((19.0, 32.0, -3.0), (1, 0.1, 0.6))},
        'left_pad': {'rot': (0, 0, -28)},
        'right_arm': {'reach': ((-2.5, 37.5, -6.0), (-0.4, -1, 0.1))},
        'right_pad': {'rot': (0, 0, 6)},
        'left_leg': {'rot': (-28, 0, -10)}, 'left_shin': {'rot': (64, 0, 0)}, 'left_foot': {'rot': (16, 0, 0)},
        'right_leg': {'rot': (22, 0, 10)}, 'right_shin': {'rot': (54, 0, 0)}, 'right_foot': {'rot': (36, 0, 0)},
        'cloth_front': {'rot': (-12, 0, 10)}, 'cloth_left': {'rot': (0, 0, -16)},
    },
    # ... and thrown: the body wrenched violently round to the right, the left arm whipping round level with the
    # elbow bent square, the fist smashing in across the front at the target, the head turned on through after it
    'hook': {
        'root': {'pos': (0, .6, -3.8)},
        'hips': {'rot': (18, 60, -6)}, 'torso': {'rot': (10, 22, -6)}, 'chest': {'rot': (8, 22, 0)},
        'neck': {'rot': (-9, -36, 0)}, 'head': {'rot': (-12, -56, 0)},
        'left_arm': {'reach': ((-8.0, 34.5, -19.5), (1, 0.2, -0.3))},
        'left_pad': {'rot': (-10, 0, -34)},
        'right_arm': {'reach': ((-3.0, 37.5, -2.5), (-0.4, -1, 0))},
        'right_pad': {'rot': (0, 0, 6)},
        'left_leg': {'rot': (-36, 0, -4)}, 'left_shin': {'rot': (28, 0, 0)}, 'left_foot': {'rot': (10, 0, 0)},
        'right_leg': {'rot': (32, 0, 14)}, 'right_shin': {'rot': (30, 0, 0)}, 'right_foot': {'rot': (44, 0, 0)},
        'cloth_front': {'rot': (-6, 0, -20)}, 'cloth_right': {'rot': (8, 0, 18)},
    },
    # 4, the finisher: coiled (rearing up and back, wound right round to the right, the right fist drawn back
    # behind the head, the left arm out at the target taking aim, the knees drawn right up to spring) ...
    'wind_big': {
        'root': {'pos': (0, 4.0, 3.2)},
        'hips': {'rot': (-12, 60, 0)}, 'torso': {'rot': (-10, 19, 0)}, 'chest': {'rot': (-8, 14, 0)},
        'neck': {'rot': (5, -34, 0)}, 'head': {'rot': (9, -48, 0)},
        'right_arm': {'reach': ((-3.0, 41.0, 12.0), (-1, -0.2, 0.6))},
        'right_pad': {'rot': (0, 0, 20)},
        'left_arm': {'reach': ((3.0, 36.0, -23.0), (0.6, -1, 0))},
        'left_pad': {'rot': (-14, 0, -20)},
        'left_leg': {'rot': (-56, 0, -6)}, 'left_shin': {'rot': (78, 0, 0)}, 'left_foot': {'rot': (16, 0, 0)},
        'right_leg': {'rot': (10, 0, 8)}, 'right_shin': {'rot': (80, 0, 0)}, 'right_foot': {'rot': (36, 0, 0)},
        'cloth_front': {'rot': (-22, 0, 0)}, 'cloth_back': {'rot': (-10, 0, 0)},
    },
    # ... and thrown: the whole body explodes forward, wheeling round to the left and flung far over the front leg,
    # the right arm driven straight through the target, the left fist torn back to the hip, the back leg flung out
    # straight behind
    'punch_big': {
        'root': {'pos': (0, .2, -8.6)},
        'hips': {'rot': (30, -38, 0)}, 'torso': {'rot': (14, -20, 0)}, 'chest': {'rot': (10, -18, 0)},
        'neck': {'rot': (-15, 30, 0)}, 'head': {'rot': (-21, 38, 0)},
        'right_arm': {'reach': ((0.0, 32.5, -41.0), (-0.5, -1, 0))},
        'right_pad': {'rot': (-20, 0, 26)},
        'left_arm': {'reach': ((6.5, 27.0, 3.0), (1, -0.5, 1))},
        'left_pad': {'rot': (0, 0, -8)},
        'left_leg': {'rot': (-58, 0, -4)}, 'left_shin': {'rot': (46, 0, 0)}, 'left_foot': {'rot': (0, 0, 0)},
        'right_leg': {'rot': (60, 0, 6)}, 'right_shin': {'rot': (0, 0, 0)}, 'right_foot': {'rot': (50, 0, 0)},
        'cloth_front': {'rot': (-26, 0, 0)}, 'cloth_back': {'rot': (34, 0, 0)}, 'cloth_back_1': {'rot': (16, 0, 0)},
    },
    # ---- R, the heavy punch (2.59.0, user request: "이번에는 R 강펀치 맞으면 날아가고 가드 브레이커야"): one right
    # straight thrown with everything behind it. It coils deeper than the finisher: dropped low and back on the rear
    # leg, wound right round to the right with the right fist cocked back past the hip and the elbow out behind, the
    # left arm reaching out at the target to take aim, the head held on the target; then the whole body explodes
    # forward, wheeling round to the left and flung far out over the front leg, the right arm driven straight through
    # the target and the left fist torn back to the hip, the back leg kicked out straight behind.
    'wind_heavy': {
        'root': {'pos': (0, -1.2, 3.6)},
        'hips': {'rot': (6, 64, 0)}, 'torso': {'rot': (4, 21, 0)}, 'chest': {'rot': (2, 17, 0)},
        'neck': {'rot': (-6, -38, 0)}, 'head': {'rot': (-9, -56, 0)},
        'right_arm': {'reach': ((-5.5, 30.0, 13.0), (-1, -0.3, 0.5))},
        'right_pad': {'rot': (0, 0, 22)},
        'left_arm': {'reach': ((6.5, 37.0, -22.0), (0.7, -1, 0))},
        'left_pad': {'rot': (-12, 0, -18)},
        'left_leg': {'rot': (-52, 0, -8)}, 'left_shin': {'rot': (72, 0, 0)}, 'left_foot': {'rot': (14, 0, 0)},
        'right_leg': {'rot': (14, 0, 10)}, 'right_shin': {'rot': (80, 0, 0)}, 'right_foot': {'rot': (34, 0, 0)},
        'cloth_front': {'rot': (-20, 0, 0)}, 'cloth_back': {'rot': (-12, 0, 0)},
    },
    'punch_heavy': {
        'root': {'pos': (0, -0.4, -9.8)},
        'hips': {'rot': (32, -42, 0)}, 'torso': {'rot': (15, -21, 0)}, 'chest': {'rot': (10, -19, 0)},
        'neck': {'rot': (-16, 33, 0)}, 'head': {'rot': (-22, 41, 0)},
        'right_arm': {'reach': ((0.0, 31.5, -44.0), (-0.5, -1, 0))},
        'right_pad': {'rot': (-22, 0, 28)},
        'left_arm': {'reach': ((7.0, 26.0, 4.0), (1, -0.5, 1))},
        'left_pad': {'rot': (0, 0, -8)},
        'left_leg': {'rot': (-62, 0, -4)}, 'left_shin': {'rot': (50, 0, 0)}, 'left_foot': {'rot': (0, 0, 0)},
        'right_leg': {'rot': (64, 0, 6)}, 'right_shin': {'rot': (0, 0, 0)}, 'right_foot': {'rot': (52, 0, 0)},
        'cloth_front': {'rot': (-28, 0, 0)}, 'cloth_back': {'rot': (36, 0, 0)}, 'cloth_back_1': {'rot': (18, 0, 0)},
    },
    # ---- T, the Star Finger (2.60.0, user request: "이번에는 스타 핑거 손가락 2개를 엄청 늘려서 5블록까지 적을 찌를 수
    # 있음"): the first two fingers held out straight like a spear. It takes aim: wound round to the right, the right
    # hand drawn back level beside the head with the elbow out behind it so the fingers point at the target, the left
    # arm reaching out at the target as a sight, the weight sitting back; then it drives the hand out straight at the
    # target, the hips and shoulders wheeling round to the left behind it, the left fist torn back to the chest, the
    # body leaning in over the front leg, while the fingers shoot out (the game stretches them, client/stand).
    'wind_finger': {
        'root': {'pos': (0, -.4, 2.2)},
        'hips': {'rot': (8, 40, 0)}, 'torso': {'rot': (6, 13, 0)}, 'chest': {'rot': (4, 11, 0)},
        'neck': {'rot': (-8, -25, 0)}, 'head': {'rot': (-10, -36, 0)},
        'right_arm': {'reach': ((-6.0, 31.5, -6.0), (-.3, -.2, 1))},
        'right_pad': {'rot': (0, 0, 18)},
        'left_arm': {'reach': ((5.5, 36.0, -21.0), (.7, -1, 0))},
        'left_pad': {'rot': (-10, 0, -16)},
        'left_leg': {'rot': (-36, 0, -6)}, 'left_shin': {'rot': (54, 0, 0)}, 'left_foot': {'rot': (16, 0, 0)},
        'right_leg': {'rot': (18, 0, 8)}, 'right_shin': {'rot': (62, 0, 0)}, 'right_foot': {'rot': (34, 0, 0)},
        'cloth_front': {'rot': (-16, 0, 0)}, 'cloth_back': {'rot': (-6, 0, 0)},
    },
    'star_finger': {
        'root': {'pos': (0, .4, -4.6)},
        'hips': {'rot': (18, -30, 0)}, 'torso': {'rot': (10, -12, 0)}, 'chest': {'rot': (7, -12, 0)},
        'neck': {'rot': (-12, 22, 0)}, 'head': {'rot': (-15, 30, 0)},
        'right_arm': {'reach': ((-1.0, 29.0, -40.0), (-.5, -1, 0))},
        'right_pad': {'rot': (-16, 0, 22)},
        'left_arm': {'reach': ((5.5, 30.5, 1.5), (1, -.5, 1))},
        'left_pad': {'rot': (0, 0, -8)},
        'left_leg': {'rot': (-44, 0, -4)}, 'left_shin': {'rot': (32, 0, 0)}, 'left_foot': {'rot': (8, 0, 0)},
        'right_leg': {'rot': (40, 0, 6)}, 'right_shin': {'rot': (12, 0, 0)}, 'right_foot': {'rot': (48, 0, 0)},
        'cloth_front': {'rot': (-4, 0, 0)}, 'cloth_back': {'rot': (24, 0, 0)},
    },
    # ---- Y, the inhale (2.61.0, user request: "스타플래티나가 최대 3블록까지 앞에 있는 플레이어를 입으로 빨아들여서 5초동안
    # 끌고오게 하는 스킬"; 2.62.0: "모션도 더 격하게"; 2.63.0, with the user's picture of its breath drawn in: "빨아 들이는
    # 모션을 이걸로 하면 안되나?", "사실 원작에서도 들이쉬는거랑 비슷해서 빨아드리는 모션이 사실 저게 맞음"): it sucks as it
    # breathes in, as in the original -- reared right back and up, the back arched, the chest thrown out, the head flung
    # back to the sky, the arms flung out wide and high, the knees drawn up ('suction': the pose in the user's picture,
    # the breath of 2.61.0 and 2.62.0 as it was, held now all the while it sucks; no lunge forward). It rears back into it
    # with a gasp ('inhale': the arch deeper still, the chest higher, the arms up in a V, the knees drawn up tighter) that
    # each heave and each catch's gulp go back toward, and as it sucks it trembles with the strain ('strain': the
    # shoulders hunched up and the arms drawn in tight, the head a little forward against the pull, the knees drawn up),
    # client/stand/StandInhale shuddering between the two. It has no mouth (the scarf covers where it would be: "입은 아예
    # 없애줘"), so the air rushes in under the scarf (client/stand/StandInhaleFx at the 'mouth' point stance_points writes).
    'inhale': {
        'root': {'pos': (0, 3.4, 1.6)},
        'hips': {'rot': (-11, 0, 0)}, 'torso': {'rot': (-13, 0, 0)}, 'chest': {'rot': (-23, 0, 0)},
        'neck': {'rot': (-11, 0, 0)}, 'head': {'rot': (-17, 0, 0)},
        'left_arm': {'reach': ((22.5, 39.5, 13.0), (.3, -1, .8))},
        'right_arm': {'reach': ((-22.5, 39.5, 13.0), (-.3, -1, .8))},
        'left_pad': {'rot': (9, 0, -34)}, 'right_pad': {'rot': (9, 0, 34)},
        'left_leg': {'rot': (-32, 0, -16)}, 'left_shin': {'rot': (64, 0, 0)}, 'left_foot': {'rot': (28, 0, 0)},
        'right_leg': {'rot': (14, 0, 16)}, 'right_shin': {'rot': (58, 0, 0)}, 'right_foot': {'rot': (34, 0, 0)},
        'cloth_front': {'rot': (-21, 0, 0)}, 'cloth_back': {'rot': (23, 0, 0)},
    },
    'suction': {
        'root': {'pos': (0, 2.4, 1.6)},
        'hips': {'rot': (-10, 0, 0)}, 'torso': {'rot': (-11, 0, 0)}, 'chest': {'rot': (-17, 0, 0)},
        'neck': {'rot': (-10, 0, 0)}, 'head': {'rot': (-16, 0, 0)},
        'left_arm': {'reach': ((21.0, 36.5, 12.0), (.3, -1, .8))},
        'right_arm': {'reach': ((-21.0, 36.5, 12.0), (-.3, -1, .8))},
        'left_pad': {'rot': (6, 0, -28)}, 'right_pad': {'rot': (6, 0, 28)},
        'left_leg': {'rot': (-26, 0, -14)}, 'left_shin': {'rot': (56, 0, 0)}, 'left_foot': {'rot': (24, 0, 0)},
        'right_leg': {'rot': (10, 0, 14)}, 'right_shin': {'rot': (50, 0, 0)}, 'right_foot': {'rot': (30, 0, 0)},
        'cloth_front': {'rot': (-16, 0, 0)}, 'cloth_back': {'rot': (18, 0, 0)},
    },
    'strain': {
        'root': {'pos': (0, 2.0, 1.4)},
        'hips': {'rot': (-9, 0, 0)}, 'torso': {'rot': (-12, 0, 0)}, 'chest': {'rot': (-21, 0, 0)},
        'neck': {'rot': (-7, 0, 0)}, 'head': {'rot': (-13, 0, 0)},
        'left_arm': {'reach': ((19.0, 33.5, 15.5), (.5, -1, .6))},
        'right_arm': {'reach': ((-19.0, 33.5, 15.5), (-.5, -1, .6))},
        'left_pad': {'rot': (0, 0, -36)}, 'right_pad': {'rot': (0, 0, 36)},
        'left_leg': {'rot': (-31, 0, -15)}, 'left_shin': {'rot': (64, 0, 0)}, 'left_foot': {'rot': (20, 0, 0)},
        'right_leg': {'rot': (15, 0, 15)}, 'right_shin': {'rot': (58, 0, 0)}, 'right_foot': {'rot': (26, 0, 0)},
        'cloth_front': {'rot': (-19, 0, 0)}, 'cloth_back': {'rot': (21, 0, 0)},
    },
    # ---- G, the beatdown (2.64.0, user request: "비트다운 꽂을때는 플레이어의 멱살을 잡고 올려치고 그대로 오라러쉬를
    # 꽂아줘 찰지게 때리고 날려야해"): in its cutscene it grabs the one it struck by the collar ('beat_grab': the left arm
    # out straight at their collar and gripping it, the body leaning in and wound round to the right, the right fist
    # cocked low back by the hip, the weight sitting back on the rear leg) and throws the uppercut ('beat_upper': rising
    # off the front leg as the hips wheel round to the left, the right fist driven straight up in front through their
    # chin, the body arched a little back after it, the left hand let go and torn back to the chest, the head up after
    # them), then rushes them (the rush's stances) and throws its last blow (the heavy punch's), client/stand/StandBeatdown.
    'beat_grab': {
        'root': {'pos': (0, -.8, -3.6)},
        'hips': {'rot': (10, 30, 0)}, 'torso': {'rot': (7, 10, 0)}, 'chest': {'rot': (5, 8, 0)},
        'neck': {'rot': (-9, -16, 0)}, 'head': {'rot': (-12, -24, 0)},
        'left_arm': {'reach': ((2.0, 34.5, -27.0), (.6, -1, .1))},
        'right_arm': {'reach': ((-7.0, 22.0, 4.5), (-1, -.3, .6))},
        'left_pad': {'rot': (-12, 0, -18)}, 'right_pad': {'rot': (0, 0, 14)},
        'left_leg': {'rot': (-30, 0, -4)}, 'left_shin': {'rot': (40, 0, 0)}, 'left_foot': {'rot': (14, 0, 0)},
        'right_leg': {'rot': (24, 0, 8)}, 'right_shin': {'rot': (58, 0, 0)}, 'right_foot': {'rot': (34, 0, 0)},
        'cloth_front': {'rot': (-14, 0, 0)}, 'cloth_back': {'rot': (8, 0, 0)},
    },
    'beat_upper': {
        'root': {'pos': (0, 2.6, -4.2)},
        'hips': {'rot': (2, -34, 0)}, 'torso': {'rot': (-6, -12, 0)}, 'chest': {'rot': (-11, -12, 0)},
        'neck': {'rot': (-8, 18, 0)}, 'head': {'rot': (-17, 22, 0)},
        'right_arm': {'reach': ((-1.5, 48.0, -19.0), (-.5, -.4, 1))},
        'right_pad': {'rot': (-16, 0, 30)},
        'left_arm': {'reach': ((6.0, 30.0, -2.0), (.8, -1, .4))},
        'left_pad': {'rot': (0, 0, -10)},
        'left_leg': {'rot': (-52, 0, -4)}, 'left_shin': {'rot': (36, 0, 0)}, 'left_foot': {'rot': (8, 0, 0)},
        'right_leg': {'rot': (40, 0, 6)}, 'right_shin': {'rot': (16, 0, 0)}, 'right_foot': {'rot': (46, 0, 0)},
        'cloth_front': {'rot': (-24, 0, 0)}, 'cloth_back': {'rot': (26, 0, 0)},
    },
    # The beatdown's own blow, G (2.65.0, user request: "G 꽂을때 파훼 할 수 있게 모션이 더 크게 보여야하고"): one huge
    # overhand right that whoever it comes at can see coming half a second off. It rears right up and back as if
    # springing, wound round to the right, the right fist cocked high up behind its head with the elbow out, the left
    # arm thrust out straight at the target to take aim, the knees drawn right up ('beat_wind'); then it smashes the fist
    # down and on through the target with everything behind it, wheeling round to the left and flung out over the front
    # leg, the left fist torn back to the hip, the back leg kicked out straight ('beat_strike').
    'beat_wind': {
        'root': {'pos': (0, 5.0, 4.0)},
        'hips': {'rot': (-14, 66, 0)}, 'torso': {'rot': (-12, 22, 0)}, 'chest': {'rot': (-10, 16, 0)},
        'neck': {'rot': (6, -38, 0)}, 'head': {'rot': (10, -54, 0)},
        'right_arm': {'reach': ((-5.0, 46.0, 11.0), (-1, 0.3, 0.2))},
        'right_pad': {'rot': (0, 0, 26)},
        'left_arm': {'reach': ((4.0, 38.0, -25.0), (0.6, -1, 0))},
        'left_pad': {'rot': (-14, 0, -20)},
        'left_leg': {'rot': (-62, 0, -6)}, 'left_shin': {'rot': (82, 0, 0)}, 'left_foot': {'rot': (16, 0, 0)},
        'right_leg': {'rot': (8, 0, 8)}, 'right_shin': {'rot': (84, 0, 0)}, 'right_foot': {'rot': (34, 0, 0)},
        'cloth_front': {'rot': (-24, 0, 0)}, 'cloth_back': {'rot': (-12, 0, 0)},
    },
    'beat_strike': {
        'root': {'pos': (0, -0.8, -10.5)},
        'hips': {'rot': (34, -46, 0)}, 'torso': {'rot': (17, -22, 0)}, 'chest': {'rot': (12, -20, 0)},
        'neck': {'rot': (-17, 34, 0)}, 'head': {'rot': (-23, 43, 0)},
        'right_arm': {'reach': ((0.0, 29.5, -45.0), (-0.5, -1, 0))},
        'right_pad': {'rot': (-24, 0, 30)},
        'left_arm': {'reach': ((7.5, 25.0, 5.0), (1, -0.5, 1))},
        'left_pad': {'rot': (0, 0, -8)},
        'left_leg': {'rot': (-64, 0, -4)}, 'left_shin': {'rot': (52, 0, 0)}, 'left_foot': {'rot': (0, 0, 0)},
        'right_leg': {'rot': (66, 0, 6)}, 'right_shin': {'rot': (0, 0, 0)}, 'right_foot': {'rot': (54, 0, 0)},
        'cloth_front': {'rot': (-30, 0, 0)}, 'cloth_back': {'rot': (38, 0, 0)}, 'cloth_back_1': {'rot': (20, 0, 0)},
    },
    # ---- H, the time stop (2.67.0, user request: "이제 H시간 정지를 만들자 시간 정지 시간은 5초고"): as its user shouts
    # "Star Platinum: The World!" the stand comes round in front of them and gathers itself, hunched, the fists clenched
    # together in front of its belly and the elbows flared out wide, the shoulders hunched up, the head down, the knees
    # drawn up ('world_gather'); as the shout ends it flings itself open, the arms out wide to either side and down from
    # the shoulders, the fists clenched, the chest thrown out and the head up, the legs apart, and holds it as time stops
    # round it ('world'), client/stand/StandTimeStop. Both read from behind, where the game's camera is.
    'world_gather': {
        'root': {'pos': (0, 2.4, 0.6)},
        'hips': {'rot': (6, 0, 0)}, 'torso': {'rot': (10, 0, 0)}, 'chest': {'rot': (9, 0, 0)},
        'neck': {'rot': (3, 0, 0)}, 'head': {'rot': (11, 0, 0)},
        'left_arm': {'reach': ((4.5, 27.0, -9.0), (1, -.25, .1))},
        'right_arm': {'reach': ((-4.5, 27.0, -9.0), (-1, -.25, .1))},
        'left_pad': {'rot': (0, 0, -18)}, 'right_pad': {'rot': (0, 0, 18)},
        'left_leg': {'rot': (-22, 0, -8)}, 'left_shin': {'rot': (44, 0, 0)}, 'left_foot': {'rot': (26, 0, 0)},
        'right_leg': {'rot': (-8, 0, 8)}, 'right_shin': {'rot': (36, 0, 0)}, 'right_foot': {'rot': (24, 0, 0)},
        'cloth_front': {'rot': (-8, 0, 0)}, 'cloth_back': {'rot': (10, 0, 0)},
    },
    'world': {
        'root': {'pos': (0, 3.2, 0.4)},
        'hips': {'rot': (-5, 0, 0)}, 'torso': {'rot': (-7, 0, 0)}, 'chest': {'rot': (-12, 0, 0)},
        'neck': {'rot': (-5, 0, 0)}, 'head': {'rot': (-12, 0, 0)},
        'left_arm': {'reach': ((21.5, 28.5, -5.5), (0, -1, .45))},
        'right_arm': {'reach': ((-21.5, 28.5, -5.5), (0, -1, .45))},
        'left_pad': {'rot': (6, 0, -26)}, 'right_pad': {'rot': (6, 0, 26)},
        'left_leg': {'rot': (-12, 0, -20)}, 'left_shin': {'rot': (26, 0, 0)}, 'left_foot': {'rot': (22, 0, 0)},
        'right_leg': {'rot': (6, 0, 20)}, 'right_shin': {'rot': (30, 0, 0)}, 'right_foot': {'rot': (24, 0, 0)},
        'cloth_front': {'rot': (-22, 0, 0)}, 'cloth_back': {'rot': (24, 0, 0)},
    },
}


# ---- E, the rush (2.57.0, user request: "이번에는 e스킬 만들자 e홀드로 사용 할 수 있고 도중에 끊으면 바로 끊겨 러시고
# 연타를 빠르게 꽂는거야"): the barrage. Squared up to the front, the chest thrust forward and the head up to the target,
# floating in a fighting stance, it drives its fists out straight one after the other, the shoulder behind each, while
# the other comes back to the guard. Each stance has one fist out -- at the middle, high or low, so that the blows land
# all over whoever stands in front -- and client/stand/StandRush goes from one to the next, left and right in turn, a
# dozen times a second. The punching arm, the shoulders' turn and how far the body dips change from one stance to the
# next; the legs stay, so the body holds its stance behind the flurry. The arm that comes back is held where the guard
# holds it (and bent the same way), so that the rush and the guard are close. Since 2.58.0 (user request: "오라 러시가
# 더 격했으면 좋겠어") it is fiercer: it leans further in, the hips, waist and shoulders wrench round further behind
# every fist (the chest swings some 64 degrees from a left to a right), the head drives in behind the fists, the high
# fists go higher and the low ones lower with the body rising and dropping after them, and the drawn fist comes back
# further.
RUSH_OUT = {'mid': ((1.5, 32.0, -30.0), 0.0), 'hi': ((2.5, 38.0, -30.0), -5.0), 'lo': ((0.5, 25.5, -29.0), 8.0)}
RUSH_BACK = {'left': ((4.5, 37.0, -11.5), (0.6, -1, 0.2)), 'right': ((-4.5, 37.0, -11.5), (-0.6, -1, 0.2))}
RUSH_RISE = {'mid': 0.0, 'hi': 0.4, 'lo': -1.0}


def rush_stance(side, where):
    """The barrage with the {side} fist thrown out straight {where} (mid, hi or lo), the other one back in the guard."""
    target, dip = RUSH_OUT[where]
    s = 1 if side == 'left' else -1          # the left side is +x, and turning +y brings the left shoulder forward
    other = 'right' if side == 'left' else 'left'
    return {
        'root': {'pos': (0, .6 + RUSH_RISE[where], -1.6)},
        'hips': {'rot': (11, 6 * s, 0)}, 'torso': {'rot': (8 + .3 * dip, 10 * s, 0)}, 'chest': {'rot': (6 + .6 * dip, 16 * s, 0)},
        'neck': {'rot': (-9 - .3 * dip, -12 * s, 0)}, 'head': {'rot': (-12 - .5 * dip, -16 * s, 0)},
        side + '_arm': {'reach': ((target[0] * s, target[1], target[2]), (.5 * s, -1, 0))},
        other + '_arm': {'reach': RUSH_BACK[other]},
        side + '_pad': {'rot': (-14, 0, -20 * s)}, other + '_pad': {'rot': (0, 0, 8 * s)},
        'left_leg': {'rot': (-24, 0, -4)}, 'left_shin': {'rot': (34, 0, 0)}, 'left_foot': {'rot': (16, 0, 0)},
        'right_leg': {'rot': (16, 0, 6)}, 'right_shin': {'rot': (40, 0, 0)}, 'right_foot': {'rot': (30, 0, 0)},
        'cloth_front': {'rot': (-14, 0, 0)}, 'cloth_back': {'rot': (18, 0, 0)},
    }


for _side in ('left', 'right'):
    for _where in ('mid', 'hi', 'lo'):
        POSES['rush_' + _side[0] + ('' if _where == 'mid' else '_' + _where)] = rush_stance(_side, _where)


def frame(down, front):
    """A bone's turn in the model that sends its -y along down and its -z along front (made square to down)."""
    y = -norm(down)
    f = norm(front)
    z = -(f - (f @ -y) * -y)
    z = norm(z)
    x = np.cross(y, z)
    return np.column_stack([x, y, z])


# the middle of each fist's four gold knuckles in the rest pose (model units, before LIFT): what a punch strikes with
KNUCKLES = {'right': (-7.62, 14.87, -0.32), 'left': (7.62, 14.87, -0.32)}


def bone_matrix(b, entry):
    """A bone's own move in a stance (4x4): its offset, then its turn about its pivot."""
    turn = np.eye(4)
    turn[:3, :3] = rot_matrix([r + d for r, d in zip(b.rot, entry.get('rot', (0, 0, 0)))])
    move, to, back = np.eye(4), np.eye(4), np.eye(4)
    move[:3, 3] = entry.get('pos', (0, 0, 0))
    to[:3, 3] = b.pivot
    back[:3, 3] = -np.array(b.pivot, dtype=np.float64)
    return move @ to @ turn @ back


def reach(spec, side, placed, target, pole):
    """An arm reaching its fist's knuckles to target (model units, soles at 0) with its elbow out towards pole: the upper
    arm aimed at the elbow, folding towards the fist, and the forearm bent at the elbow as a hinge (no twist), so that
    two stances' arms differ by how the shoulder turns and how far the elbow bends, never by a forearm spun round."""
    arm, fore, hand = BONES[side + '_arm'], BONES[side + '_forearm'], BONES[side + '_hand']
    parent = placed[arm.parent]
    shoulder = (parent @ np.array([*arm.pivot, 1.0]))[:3]
    upper = float(np.linalg.norm(np.subtract(fore.pivot, arm.pivot)))
    lower = float(np.linalg.norm(np.subtract(KNUCKLES[side], fore.pivot)))
    goal = np.array(target, dtype=np.float64) - np.array([0, LIFT, 0])
    d = goal - shoulder
    dist = float(np.clip(np.linalg.norm(d), abs(upper - lower) + 1e-3, upper + lower - 1e-3))
    u = norm(d)
    side_way = np.array(pole, dtype=np.float64) - (np.array(pole, dtype=np.float64) @ u) * u
    v = norm(side_way) if np.linalg.norm(side_way) > 1e-6 else norm(np.cross(u, (1, 0, 0)))
    a = (upper * upper - lower * lower + dist * dist) / (2 * dist)
    h = math.sqrt(max(0.0, upper * upper - a * a))
    elbow = shoulder + u * a + v * h
    down = norm(elbow - shoulder)
    to_fist = shoulder + u * dist - elbow
    fold = to_fist - (to_fist @ down) * down
    front = norm(fold) if np.linalg.norm(fold) > 1e-6 else v
    bend = math.degrees(math.acos(float(np.clip(norm(to_fist) @ down, -1, 1))))
    spec[side + '_arm'] = {**{k: v2 for k, v2 in spec.get(side + '_arm', {}).items() if k not in ('reach',)}, 'aim': (tuple(down), tuple(front))}
    spec[side + '_forearm'] = {'rot': (-bend, 0, 0)}


def pose_table(spec):
    """The stance as each bone's turn (degrees, Blockbench's way) and offset, aims and reaches worked out down the rig."""
    spec = dict(spec)
    world = {}
    placed = {}
    out = {}
    for name, b in BONES.items():
        parent = world[b.parent] if b.parent else np.eye(3)
        if name.endswith('_arm') and 'reach' in spec.get(name, {}):
            reach(spec, name[:-4], placed, *spec[name]['reach'])
        entry = spec.get(name, {})
        if 'aim' in entry:
            Rrel = parent.T @ frame(*entry['aim'])
            delta = euler_of(Rrel)
            delta = [d - r for d, r in zip(delta, b.rot)]
        else:
            delta = list(entry.get('rot', (0, 0, 0)))
        world[name] = parent @ rot_matrix([r + d for r, d in zip(b.rot, delta)])
        placed[name] = (placed[b.parent] if b.parent else np.eye(4)) @ bone_matrix(b, {**entry, 'rot': delta})
        if any(abs(d) > 1e-6 for d in delta) or 'pos' in entry:
            out[name] = {'rot': [round(float(d), 3) for d in delta]}
            if 'pos' in entry:
                out[name]['pos'] = [float(v) for v in entry['pos']]
    return out


def stance_points(spec):
    """Where each fist's knuckles are in a stance (model units, soles at 0): every bone moved as the game's model moves
    it, by its offset, then turned about its pivot, down from the root. And where each shoulder is (the upper arm's
    pivot, 2.58.0): what the rush's bigger arms grow from (client/stand/StandRush)."""
    table = pose_table(spec)
    world = {}
    for name, b in BONES.items():
        world[name] = (world[b.parent] if b.parent else np.eye(4)) @ bone_matrix(b, table.get(name, {}))
    out = {}
    for side, p in KNUCKLES.items():
        q = world[side + '_hand'] @ np.array([*p, 1.0])
        out[side] = [round(float(q[0]), 3), round(float(q[1] + LIFT), 3), round(float(q[2]), 3)]
        q = world[side + '_arm'] @ np.array([*BONES[side + '_arm'].pivot, 1.0])
        out[side + '_shoulder'] = [round(float(q[0]), 3), round(float(q[1] + LIFT), 3), round(float(q[2]), 3)]
    # where the Star Finger's fingers leave the right fist and the way they point out of it, along the hand (2.60.0)
    q = world['right_hand'] @ np.array([*FINGER_ROOT, 1.0])
    out['right_finger'] = [round(float(q[0]), 3), round(float(q[1] + LIFT), 3), round(float(q[2]), 3)]
    out['right_finger_way'] = [round(float(v), 4) for v in world['right_hand'][:3, :3] @ np.array([0, -1.0, 0])]
    # where its mouth would be (2.61.0, the inhale): under the nose, just in front of the jaw (the scarf covers it)
    q = world['head'] @ np.array([*MOUTH, 1.0])
    out['mouth'] = [round(float(q[0]), 3), round(float(q[1] + LIFT), 3), round(float(q[2]), 3)]
    return out


# How far in front of it and behind it the stand reaches at each height near its middle (2.63.0, the inhale, held now in
# the breath drawn in with its head flung back): its head flung back must stay clear of its user's head behind it, and
# whom it holds clear of it in front (client/stand/StandInhaleTest). Model units, soles at 0, in bands PROFILE_STEP high:
# 'front' the furthest forward any of it reaches (least z: its front is -z) within PROFILE_FRONT of its middle across (a
# body's width with its arms, 0.47 blocks at the size the game draws it, StarPlatinumRenderer.SCALE .72), 'back' the
# furthest back (most z) within PROFILE_BACK (a head's width, 0.3 blocks); None where nothing of it is.
PROFILE_STANCES = ('guard', 'inhale', 'suction', 'strain')
PROFILE_STEP, PROFILE_FRONT, PROFILE_BACK = 2.0, 10.5, 6.7


def profile(spec):
    table = pose_table(spec)
    world = {}
    for name, b in BONES.items():
        world[name] = (world[b.parent] if b.parent else np.eye(4)) @ bone_matrix(b, table.get(name, {}))
    g = np.linspace(0, 1, 5)
    lattice = np.array([(a, b, d) for a in g for b in g for d in g])
    pts = []
    for c in CUBES:
        if c.bone.endswith('_finger'):      # the Star Finger's fingers show only as it stabs (StarPlatinumModel)
            continue
        p = np.array(c.origin, dtype=np.float64) - c.inflate + lattice * (np.array(c.size, dtype=np.float64) + 2 * c.inflate)
        if c.rot is not None:
            pivot = np.array(c.pivot, dtype=np.float64)
            p = (p - pivot) @ rot_matrix(c.rot).T + pivot
        w = world[c.bone]
        pts.append(p @ w[:3, :3].T + w[:3, 3])
    pts = np.concatenate(pts)
    pts[:, 1] += LIFT
    front, back = [], []
    for k in range(int(math.ceil(pts[:, 1].max() / PROFILE_STEP))):
        band = pts[(pts[:, 1] >= k * PROFILE_STEP) & (pts[:, 1] < (k + 1) * PROFILE_STEP)]
        f, b = band[np.abs(band[:, 0]) <= PROFILE_FRONT], band[np.abs(band[:, 0]) <= PROFILE_BACK]
        front.append(round(float(f[:, 2].min()), 2) if len(f) else None)
        back.append(round(float(b[:, 2].max()), 2) if len(b) else None)
    return {'step': PROFILE_STEP, 'front': front, 'back': back}


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
    # where the fists are in every stance, for the effects of the punches (client/stand/StandPunch), and how far the
    # inhale's stances reach in front and behind (2.63.0)
    points = {k: stance_points(v) for k, v in POSES.items()}
    for k in PROFILE_STANCES:
        points[k]['profile'] = profile(POSES[k])
    (ROOT / 'stands/star_platinum_fists.json').write_text(json.dumps(points, indent=1))
    print(f'{len(BONES)} bones, {len(CUBES)} cubes, texture {ATLAS_W}x{height}')


if __name__ == '__main__':
    main()
