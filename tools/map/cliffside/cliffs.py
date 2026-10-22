"""The brown rock rim of the Roblox map (map v1.4): the lawn round the town and the grey wall on its edge become
rock, built of tilted cube-like boulders as the reference's are.

The belt keeps the lawn's use: inside it lies a floor of rock at the town's level (y=80), big flush slabs of calm
browns with broad plates a block up, as open to run and fight on as the lawn was. Only its last four blocks before the
sea, where the grey wall stood, rise as a cliff of boulders stacking up to the edge (9 to 16 blocks high, higher where
the belt is wider). A boulder is a rectangle turned by a random angle with a tilted top, one block kind each; a column
is solid up to the highest top over it (no overhangs, nothing floating), its top takes the boulder's kind and a tall
side is big piled blocks of mixed browns. Boulders also stand in the sea along the edge, from the sea floor up to the
waterline or up the cliff, and the island's old grey face under the belt is clad the same way.
"""
import math, random
import numpy as np
from scipy import ndimage

# the reference's warm brown rock, from light (packed mud) to dark (brown terracotta); weights in percent
ROCK = [('terracotta', 34), ('red_terracotta', 13), ('brown_terracotta', 18), ('orange_terracotta', 3), ('packed_mud', 18),
        ('granite', 7), ('dripstone_block', 7)]
BASE = 'terracotta'
# the floor inside the cliff in calm browns (a flat floor in every colour reads busy)
FLOOR = [('terracotta', 56), ('packed_mud', 22), ('granite', 8), ('brown_terracotta', 8), ('red_terracotta', 4),
         ('dripstone_block', 2)]
# a tall face in fewer dark and red blocks than the boulders' tops
FACE = [('terracotta', 40), ('packed_mud', 20), ('granite', 12), ('brown_terracotta', 12), ('red_terracotta', 8),
        ('dripstone_block', 8)]
SEA_FLOOR = 49   # the first water block over the sea's sand (46..48)
GROUND = 80
CLEAR_TO = 112   # the old wall reaches y=101; trees a little higher


def smoothstep(a, b, t):
    t = np.clip((t - a) / (b - a), 0.0, 1.0)
    return t * t * (3 - 2 * t)


def pick(rng, table=ROCK):
    total = sum(wt for _, wt in table); r = rng.uniform(0, total)
    for name, wt in table:
        r -= wt
        if r <= 0: return name
    return table[-1][0]


def stacked(x, y, z, seed):
    """The kind of a block in a tall rock face: the face is cut into strips four blocks wide, each strip into blocks
    five high from its own starting height, one kind each, so it reads as big blocks of rock piled up, not as stripes
    or a pattern."""
    strip = ((x // 4) * 92821 + (z // 4) * 68917 + seed) & 0xFFFFFFFF
    off = strip % 5
    return pick(random.Random((strip * 31 + (y + off) // 5 * 1237) & 0xFFFFFFFF), FACE)


def poisson(cells, spacing, rng, fixed=()):
    """Points from `cells` ((x, z) floats) no closer than spacing(x, z) to each other (greedy, in random order), after
    the `fixed` points, which are all kept."""
    cells = list(cells); rng.shuffle(cells)
    grid = {}; out = []
    g = 4.0
    for x, z in fixed:
        grid.setdefault((int(x // g), int(z // g)), []).append((x, z, spacing(x, z)))
    for x, z in cells:
        r = spacing(x, z)
        gx, gz = int(x // g), int(z // g); k = int(math.ceil(r / g)) + 1
        ok = True
        for i in range(gx - k, gx + k + 1):
            for j in range(gz - k, gz + k + 1):
                for (px, pz, pr) in grid.get((i, j), ()):
                    if (px - x) ** 2 + (pz - z) ** 2 < max(r, pr) ** 2:
                        ok = False; break
                if not ok: break
            if not ok: break
        if ok:
            grid.setdefault((gx, gz), []).append((x, z, r)); out.append((x, z))
    return out


class Boulder:
    """A turned rectangle (half sizes a, b) with a tilted flat top through (cx, top, cz)."""
    def __init__(self, cx, cz, a, b, yaw, gu, gv, top, kind):
        self.cx, self.cz, self.a, self.b = cx, cz, a, b
        self.c, self.s = math.cos(yaw), math.sin(yaw)
        self.gu, self.gv, self.top, self.kind = gu, gv, top, kind

    def columns(self):
        r = math.hypot(self.a, self.b) + 1
        for z in range(math.floor(self.cz - r), math.ceil(self.cz + r) + 1):
            for x in range(math.floor(self.cx - r), math.ceil(self.cx + r) + 1):
                dx, dz = x + 0.5 - self.cx, z + 0.5 - self.cz
                u = dx * self.c + dz * self.s; v = -dx * self.s + dz * self.c
                if abs(u) <= self.a and abs(v) <= self.b:
                    yield x, z, self.top + self.gu * u + self.gv * v


def rim(p, Z, seed=1931):
    """Build the rock rim into plan p over the belt of Zones Z."""
    w = Z.w; rng = random.Random(seed)
    band, land, sea = Z.band, Z.land, Z.sea
    shape = band.shape
    d_town, d_sea = Z.d_town, Z.d_sea
    width = d_town + d_sea
    # the cliff is the belt's last few blocks before the sea; inside it lies a floor of rock at the town's level, as
    # open to run and fight on as the lawn it replaces
    cw = np.full(shape, 4.0)                 # where the old wall stood: its four blocks next to the sea
    cliff = band & (d_sea <= cw)
    floor_ = band & ~cliff
    u = np.where(cliff, np.clip((cw - d_sea) / np.maximum(cw - 1.0, 1e-6), 0.0, 1.0), 0.0)   # 0 at its foot, 1 at the edge
    height = np.clip(7.0 + 0.22 * width, 9.0, 16.0)
    hb = np.where(cliff, GROUND + height * smoothstep(0.0, 0.85, u), GROUND)
    d_land = ndimage.distance_transform_edt(sea)                        # sea columns: distance to the island
    # the crest by each sea column (to size the boulders that stand in the water)
    near = ndimage.distance_transform_edt(~band, return_distances=False, return_indices=True)
    H = np.where(band, np.floor(hb).astype(int), -1)
    kind = np.full(shape, '', object)
    kind[band] = BASE

    def offer(x, z, top, k, only=None):
        zi, xi = z - w.z0, x - w.x0
        if not (0 <= zi < shape[0] and 0 <= xi < shape[1]): return
        if not (band[zi, xi] or (sea[zi, xi] and d_land[zi, xi] <= 3.6)): return
        if only is not None and not (only[zi, xi] or sea[zi, xi]): return
        top = int(math.floor(top))
        if top > H[zi, xi]:
            H[zi, xi] = top; kind[zi, xi] = k

    def paint(x, z, k):
        zi, xi = z - w.z0, x - w.x0
        if 0 <= zi < shape[0] and 0 <= xi < shape[1] and floor_[zi, xi] and H[zi, xi] == GROUND:
            kind[zi, xi] = k

    def at(m, x, z):
        return m[int(z) - w.z0, int(x) - w.x0]

    fz, fx = np.nonzero(floor_)
    floor_cells = [(x + w.x0 + rng.random(), z + w.z0 + rng.random()) for z, x in zip(fz.tolist(), fx.tolist())]
    # the floor: flush slabs of rock in calm browns ...
    for x, z in poisson(floor_cells, lambda x, z: 4.6, rng):
        b = Boulder(x, z, rng.uniform(2.2, 4.0), rng.uniform(2.2, 4.0), rng.uniform(0, math.pi / 2), 0, 0, GROUND, pick(rng, FLOOR))
        for cx, cz, _ in b.columns():
            paint(cx, cz, b.kind)
    # ... and broad plates a block up: a step, not a wall, so the floor stays as open as the lawn was
    for x, z in poisson(floor_cells, lambda x, z: 11.0, rng):
        if at(d_town, x, z) < 3 or rng.random() < 0.35: continue
        offer_all(Boulder(x, z, rng.uniform(2.5, 4.6), rng.uniform(2.5, 4.6), rng.uniform(0, math.pi / 2), 0, 0, GROUND + 1.2,
                          pick(rng, FLOOR)), offer)
    # the cliff: boulders that stack up from its foot to the edge
    cz_, cx_ = np.nonzero(cliff)
    cells = [(x + w.x0 + rng.random(), z + w.z0 + rng.random()) for z, x in zip(cz_.tolist(), cx_.tolist())]
    for x, z in poisson(cells, lambda x, z: 3.4 + 1.6 * float(at(u, x, z)), rng):
        uv = float(at(u, x, z)); base = float(at(hb, x, z))
        a = rng.uniform(1.8, 3.0) + 2.0 * uv; b = rng.uniform(1.8, 3.0) + 2.0 * uv
        tilt = math.radians(rng.uniform(0, 10 + 14 * uv)); dirn = rng.uniform(0, 2 * math.pi)
        gu, gv = math.tan(tilt) * math.cos(dirn), math.tan(tilt) * math.sin(dirn)
        offer_all(Boulder(x, z, a, b, rng.uniform(0, math.pi / 2), gu, gv, base + rng.uniform(0.4, 1.2 + 3.0 * uv), pick(rng)),
                  lambda x, z, top, k: offer(x, z, top, k, only=cliff))
    # boulders in the sea along the edge: at the waterline, half way up, a few up to the crest
    sz, sx = np.nonzero(sea & (d_land >= 0.9) & (d_land <= 2.8))
    cells = [(x + w.x0 + rng.random(), z + w.z0 + rng.random()) for z, x in zip(sz.tolist(), sx.tolist())]
    for x, z in poisson(cells, lambda x, z: 3.8, rng):
        zi, xi = int(z) - w.z0, int(x) - w.x0
        nzi, nxi = near[0][zi, xi], near[1][zi, xi]
        crest_here = max(H[nzi, nxi], 70)
        r = rng.random()
        top = rng.uniform(62.5, 65.5) if r < 0.5 else rng.uniform(65, 74) if r < 0.85 else rng.uniform(73, crest_here - 0.5)
        a, b = rng.uniform(1.4, 2.8), rng.uniform(1.4, 2.8)
        tilt = math.radians(rng.uniform(0, 14)); dirn = rng.uniform(0, 2 * math.pi)
        offer_all(Boulder(x, z, a, b, rng.uniform(0, math.pi / 2), math.tan(tilt) * math.cos(dirn), math.tan(tilt) * math.sin(dirn),
                          top, pick(rng)), offer)
    # a few stray rocks a little way out
    far = sea & (d_land > 4.5) & (d_land < 9)
    fz, fx = np.nonzero(far)
    for i in rng.sample(range(len(fz)), min(len(fz), 14)):
        x, z = fx[i] + w.x0 + 0.5, fz[i] + w.z0 + 0.5
        b = Boulder(x, z, rng.uniform(1.2, 2.4), rng.uniform(1.2, 2.4), rng.uniform(0, math.pi / 2), 0.1, -0.08,
                    rng.uniform(62.2, 64.6), pick(rng))
        for cx, cz, top in b.columns():
            zi, xi = cz - w.z0, cx - w.x0
            if 0 <= zi < shape[0] and 0 <= xi < shape[1] and sea[zi, xi] and top >= 62:
                H[zi, xi] = max(H[zi, xi], int(top)); kind[zi, xi] = b.kind

    # write the columns: the visible side in the boulder's kind, what is hidden in the base rock
    lowest = np.where(H >= 0, H, np.where(land, GROUND, 62))
    nb = np.minimum.reduce([np.roll(lowest, 1, 0), np.roll(lowest, -1, 0), np.roll(lowest, 1, 1), np.roll(lowest, -1, 1)])
    old_top = w.top()
    is_tree = np.array([any(k in n for k in ('leaves', '_log', '_wood')) for n in w.names])
    zs, xs = np.nonzero(H >= 0)
    for zi, xi in zip(zs.tolist(), xs.tolist()):
        x, z = xi + w.x0, zi + w.z0
        top = int(H[zi, xi]); k = kind[zi, xi]
        cap = top - (x * 5 + z * 11) % 2              # the boulder's own kind on its top block or two
        if band[zi, xi]:
            seen_from = max(GROUND, min(top, int(nb[zi, xi])) - 1)
            tall = top - seen_from >= 4
            for y in range(GROUND, top + 1):
                p.put(x, y, z, BASE if y < seen_from else k if (y >= cap or not tall) else stacked(x, y, z, seed))
            # above the rock: the old wall, lamps and flowers go; the crowns of the town's trees may reach over it
            for y in range(top + 1, min(int(old_top[zi, xi]), CLEAR_TO) + 1):
                cur = p.cells.get((x, y, z))
                if cur is None:
                    i = w.v[y - w.y0, zi, xi]
                    if i != 0 and not is_tree[i]: p.put(x, y, z, 'air')
                elif cur != 'minecraft:air' and not any(t in cur for t in ('leaves', '_log')):
                    p.put(x, y, z, 'air')
        else:   # in the sea: from the floor up
            for y in range(SEA_FLOOR, top + 1):
                p.put(x, y, z, k if y >= cap or top - SEA_FLOOR < 16 else stacked(x, y, z, seed))
    # the island's old grey face under the belt, clad in brown blocks of about four
    face = band & (d_sea <= 1.6)
    zs, xs = np.nonzero(face)
    for zi, xi in zip(zs.tolist(), xs.tolist()):
        x, z = xi + w.x0, zi + w.z0
        for y in range(SEA_FLOOR + 5, GROUND):
            p.put(x, y, z, stacked(x, y, z, seed))
    return H


def offer_all(b, offer):
    for x, z, top in b.columns():
        offer(x, z, top, b.kind)
