"""Gold Experience's tree of life for GeckoLib (2.152.0, user request: "이번에는 H스킬 나무를 소환해서 광역으로 주변에 데미지를
주고 높게 점프하는 스킬", on how it grows: "내리치지 말고 스탠드가 땅을 만지고 나무가 자라나게 원작 같은 굵은 나무로 만들기",
and the eight pictures they sent of it, "나무는 이렇게 생겼어": the manga's and the games'): its geometry and its texture,
made together the way the stands' are (tools/stand/rig.py), and what the game needs to grow it and to light it.

usage: python3 tools/stand/make_gold_tree.py [OUT]
Writes assets/celestialfall/geo/entity/gold_tree.geo.json, textures/entity/gold_tree.png and
stands/gold_tree_shape.json under src/main/resources, or under OUT (the same folders) to look at a change without
touching the game's files.

What the pictures show, and so what is built: a young tree that has shot up in a moment, all branches and hardly a leaf.
A trunk a little thicker than a leg at its foot, flaring into five short roots, that forks at about a body's height into
four limbs: one going on straight up (under whom it carried up), the others up and out to the sides and behind; each
limb forks again and again, its branches thinning to twigs, the whole crown about three times a body's height and
nearly as wide, every branch reaching upward. A few small pale green leaves on the twigs' ends. Bark a warm grey brown,
darker at the foot, its grain running along the wood. Units are sixteenths of a block, up from the ground, its front
(where its maker faced, the way it leans a little) towards -z. The game draws it at its own size.

The game grows it (client/stand/GoldTreeModel): each branch out of the one before it, from the trunk out to the twigs
(gold_tree_shape.json lists the branches' first bones and how deep each is in the tree: "growers"), and back into the
ground as it withers.
"""
import json
import math
import random
import sys
from pathlib import Path

import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import rig  # noqa: E402
from rig import bone, box, strand, hexc, ramp, norm, pillow  # noqa: E402

ROOT = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parents[2] / 'src/main/resources/assets/celestialfall'

# ---------------------------------------------------------------- palette (the pictures' bark and leaves)
BARK = [hexc(c) for c in ('#2b1d12', '#45301e', '#5e432b', '#785a3b', '#92734f', '#ad9068')]
LEAF = [hexc(c) for c in ('#3f6f1e', '#5a9128', '#77ae36', '#97c84c', '#bde06e')]
LIGHT = norm((.3, .85, -.4))           # baked light: from above, a little in front and to its left
WOOD_DENS, LEAF_DENS = 3, 6            # texels a unit (a block is 48 on the wood)

rng = random.Random(152)
GROWERS = []                           # (the branch's first bone, its depth: 0 the trunk .. 3 a twig, its order)
LEAVES = []                            # where the leaves are (rest pose), for their light and their fall
TIPS = []                              # the twigs' ends


def turned(a, r, y):
    return np.array([math.sin(a) * r, y, math.cos(a) * r])


def spread(d, angle, azimuth):
    """A way out of d: turned off it by angle (degrees) round towards azimuth (degrees about d)."""
    d = norm(d)
    side = norm(np.cross(d, (0, 0, 1)) if abs(d[2]) < .9 else np.cross(d, (1, 0, 0)))
    side = rig.rotate_about(side, d, azimuth)
    return norm(rig.rotate_about(d, side, angle))


def twin(name):
    """The piece doubled by a twin turned 45 degrees about its length: it reads round (an eight-sided log), the twin a
    hair shorter at its top so their ends never draw the same square."""
    c = next(c for c in rig.CUBES if c.bone == name)
    o, s = c.origin, c.size
    piv = (o[0] + s[0] / 2, o[1], o[2] + s[2] / 2)
    box(name, (piv[0] - s[0] * .47, o[1] + .05, piv[2] - s[2] * .47), (s[0] * .94, s[1] - .3, s[2] * .94), c.mat,
        rot=(0, 45, 0), pivot=piv, **c.opts)


def leaf(parent, tip_local, length, twist, lean, roll):
    """A small leaf on a twig's end: a thin plate standing out of the tip, splayed."""
    w, l = length * .62, length
    box(parent, (tip_local[0] - w / 2, tip_local[1] - .2, tip_local[2] - .12), (w, l, .24), 'leaf',
        rot=(lean, twist, roll), pivot=tuple(tip_local), dens=LEAF_DENS, faces=('north', 'south', 'east', 'west', 'up'))


def branch(name, parent, pts, widths, depth, order):
    """A branch through pts, then its own branches out of its end (and the twigs' leaves)."""
    names = strand(name, parent, [tuple(p) for p in pts], widths, [w * .9 for w in widths],
                   (0, 1, 0), 'wood', dens=WOOD_DENS, depth=depth)
    if depth <= 1:
        twin(names[0])
    GROWERS.append((names[0], depth, order))
    end, before = np.array(pts[-1], dtype=np.float64), np.array(pts[-2], dtype=np.float64)
    d = norm(end - before)
    length = float(sum(np.linalg.norm(np.subtract(pts[k + 1], pts[k])) for k in range(len(pts) - 1)))
    last = names[-1]
    if depth >= 3:
        # a twig's end: two or three small leaves on it, splayed
        R, t = rig.rest_transform(last)
        tip_local = R.T @ (end - t)
        TIPS.append([round(float(v), 2) for v in end])
        for k in range(rng.choice((2, 2, 3))):
            ln = rng.uniform(3.4, 4.6)
            leaf(last, tip_local, ln, rng.uniform(0, 360), rng.uniform(-55, 55), rng.uniform(-40, 40))
            LEAVES.append([round(float(v), 2) for v in end])
        return names
    kids = 3 if depth == 1 and rng.random() < .55 else 2
    base = rng.uniform(0, 360)
    for k in range(kids):
        way = spread(d, rng.uniform(24, 40), base + k * 360 / kids + rng.uniform(-25, 25))
        way = norm(way + np.array([0, .35 if depth < 2 else .25, 0]))        # every branch reaching up
        ln = length * rng.uniform(.5, .62)
        p0 = end - d * 1.2
        bend = spread(way, 90, rng.uniform(0, 360)) * ln * .08
        p1 = end + way * ln * .5 + bend
        p2 = end + way * ln
        w0 = widths[-1] * .78
        # (a child is named with a letter: its parent's pieces are named _0, _1)
        child = name + 'abc'[k]
        if depth + 1 >= 3:
            branch(child, last, [p0, p2], [max(1.3, w0 * .8)], depth + 1, order * 4 + k)
        else:
            branch(child, last, [p0, p1, p2], [w0, w0 * .72], depth + 1, order * 4 + k)
    return names


# ---------------------------------------------------------------- the build
bone('tree', None, (0, 0, 0))
TRUNK = [(0, -6, 0), (.4, 12, -.6), (.8, 26, -1.6)]
# the trunk (its forks are the limbs): a strand of two pieces, round
trunk = strand('trunk', 'tree', TRUNK, [12.0, 10.0], [11.0, 9.0], (0, 0, 1), 'wood', dens=WOOD_DENS, depth=0)
for n in trunk:
    twin(n)
GROWERS.append((trunk[0], 0, 0))
# its foot, flared into the ground (most of it under it), and its roots running out over it
for k, (w, h) in enumerate(((17, 9), (14.5, 13))):
    piv = (0.0, -4.0, 0.0)
    for j, turn in enumerate((0, 45)):
        box('trunk_0', (-w / 2 * (1 - .03 * j), -4.0 + .1 * k + .03 * j, -w / 2 * (1 - .03 * j)),
            (w * (1 - .03 * j), h - .2 * k - .06 * j, w * (1 - .03 * j)), 'wood', rot=(0, turn + 22.5 * k, 0), pivot=piv,
            dens=WOOD_DENS, depth=0, start=0.0, length=40.0, piece=0, pieces=1, seg=h, sink=0)
bone('roots', 'tree', (0, 0, 0))
ROOT_ENDS = []
for i in range(5):
    a = math.radians(i * 72 + 25 + rng.uniform(-12, 12))
    reach = rng.uniform(16, 21)
    pts = [turned(a, 3.6, 4.0), turned(a + .15, reach * .6, 1.2), turned(a + .25, reach, -2.4)]
    strand(f'root{i}', 'roots', [tuple(p) for p in pts], [5.2, 3.4], [4.2, 2.8], (0, 1, 0), 'wood', dens=WOOD_DENS, depth=1)
    ROOT_ENDS.append([round(float(v), 2) for v in pts[2]])

top = np.array(TRUNK[2], dtype=np.float64)
LIMBS = [
    # the leader, going on straight up (leaning a little forward): it carries its maker up
    [top + (0, -2, 0), (1.0, 42, -4.5), (.5, 57, -7.5), (-.4, 70, -10.0)],
    [top + (0, -2, 0), (10, 38, .5), (17.5, 49, 2.5)],
    [top + (0, -2, 0), (-11, 37, -2.5), (-19, 48, -4.0)],
    [top + (0, -2.5, 0), (-.5, 36, 8.0), (-2.0, 47, 13.5)],
]
LIMB_W = [[7.6, 6.2, 4.9], [6.0, 4.6], [6.0, 4.6], [5.2, 4.0]]
for i, (pts, w) in enumerate(zip(LIMBS, LIMB_W)):
    names = branch(f'limb{i}', trunk[-1], [np.array(p, dtype=np.float64) for p in pts], w, 1, i + 1)
    if i == 0:
        # the leader's own boughs out of its sides on the way up
        for k, (piece, frm, to, toward) in enumerate(((names[0], 1, 2, 70), (names[1], 2, 3, 250))):
            at = np.array(pts[frm], dtype=np.float64)
            d = norm(np.array(pts[to], dtype=np.float64) - at)
            way = norm(spread(d, 52, toward) + np.array([0, .3, 0]))
            ln = 17.0
            branch('limb0' + 'xy'[k], piece, [at - d * 1.0, at + way * ln * .5 + (0, 1.5, 0), at + way * ln], [4.4, 3.2], 2, 20 + k)


# ---------------------------------------------------------------- painting
def lattice(i, j, k, seed):
    v = math.sin(i * 127.1 + j * 311.7 + k * 74.7 + seed * 19.19) * 43758.5453
    return (v - math.floor(v)) * 2 - 1


def smooth(x, y, z, seed=0):
    """Smooth value noise in -1..1 (the hash at the lattice's corners, eased between them)."""
    i, j, k = math.floor(x), math.floor(y), math.floor(z)
    fx, fy, fz = x - i, y - j, z - k
    ux, uy, uz = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy), fz * fz * (3 - 2 * fz)
    out = 0.0
    for a in (0, 1):
        for b in (0, 1):
            for c in (0, 1):
                w = (ux if a else 1 - ux) * (uy if b else 1 - uy) * (uz if c else 1 - uz)
                out += w * lattice(i + a, j + b, k + c, seed)
    return out


def wood_paint(c, face, u, v, p, n):
    """Bark, painted along the wood (its own y runs along the piece): fine grain running along it, the side the light
    falls on lit, darker towards the foot and lighter out on the twigs."""
    o, s = np.array(c.origin), np.array(c.size)
    if face in ('up', 'down'):
        return ramp(BARK, .45)
    mid = o + s / 2
    ang = math.atan2(p[0] - mid[0], p[2] - mid[2])
    along = c.opts['start'] * c.opts['length'] + (p[1] - o[1])
    seed = sum(map(ord, c.bone)) % 97
    depth = c.opts.get('depth', 1)
    grain = math.sin(ang * 5 + 1.3 * smooth(ang * .8, along * .07, seed, 5))
    t = .4 + .3 * max(0.0, float(n @ LIGHT)) + .1 * pillow(u, v, .5, 0) + .07 * depth + .05 * smooth(ang * 2, along * .1, seed, 7)
    if grain > .83:
        t -= .2
    elif grain < -.8:
        t += .06
    if c.bone.startswith('trunk') and depth == 0:
        rest_y = rig.to_rest(c, p, np.zeros(3))[0][1]
        if rest_y < 8:
            t -= .14 * (8 - rest_y) / 12   # darker where it comes out of the ground
    if c.bone.startswith('root'):
        t -= .08
    return ramp(BARK, t)


def leaf_paint(c, face, u, v, p, n):
    """A small leaf: pointed at its end (the top of the plate, away from the twig), round at its foot, a pale vein."""
    if face not in ('north', 'south'):
        return LEAF[1]
    along = 1 - v                      # 0 at the twig, 1 at its point
    across = abs(u - .5) * 2
    half = math.sin(math.pi * min(1.0, along) ** .75) * .92
    if along < .06 or across > half:
        return None
    t = .35 + .35 * along + .18 * (1 - across) - (.18 if face == 'south' else 0)
    if across < .1 and along < .85:
        t += .25                       # its vein
    elif across > half - .14:
        t -= .15                       # its rim
    return ramp(LEAF, t)


PAINT = {'wood': wood_paint, 'leaf': leaf_paint}
OWN = ('wood', 'leaf')


def main():
    for b in rig.check_coplanar():
        print('coplanar overlap (would flicker):', b)
    place, height = rig.pack()
    model = rig.geo(place, height, 'geometry.botand.gold_tree', bounds=(7, 7, (0, 3, 0)))
    img = rig.paint(place, height, PAINT, OWN)
    # how high and wide it stands (every cube's corners in the rest pose)
    pts = []
    for c in rig.CUBES:
        lo, hi = rig.extents(c)
        for d in np.ndindex(2, 2, 2):
            pts.append(rig.to_rest(c, np.where(np.array(d) == 1, hi, lo), np.zeros(3))[0])
    pts = np.array(pts)
    shape = {
        'trunk': [[round(float(v), 2) for v in p] for p in TRUNK],
        'leader': [[round(float(v), 2) for v in p] for p in LIMBS[0]],
        'growers': [[n, d, o] for n, d, o in GROWERS],
        'tips': TIPS, 'leaves': LEAVES, 'roots': ROOT_ENDS,
        'height': round(float(pts[:, 1].max()), 2), 'across': round(float(max(abs(pts[:, 0]).max(), abs(pts[:, 2]).max())), 2),
    }
    for d in ('geo/entity', 'textures/entity', 'stands'):
        (ROOT / d).mkdir(parents=True, exist_ok=True)
    (ROOT / 'geo/entity/gold_tree.geo.json').write_text(json.dumps(model, indent=1))
    Image.fromarray(img, 'RGBA').save(ROOT / 'textures/entity/gold_tree.png', optimize=True)
    (ROOT / 'stands/gold_tree_shape.json').write_text(json.dumps(shape, indent=1))
    print(f'{len(rig.BONES)} bones, {len(rig.CUBES)} cubes, texture {rig.ATLAS_W}x{height}, {shape["height"]:.1f} units high, '
          f'{shape["across"]:.1f} out from its middle, {len(TIPS)} twigs, {len(LEAVES)} leaves')


if __name__ == '__main__':
    main()
