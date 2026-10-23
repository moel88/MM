"""Where things are on Azure Garden v1.3, as boolean maps over the island's columns (index [z - z0, x - x0]).

The island is a plateau at y=80 on a sea floor at 48 (sea level 62). Round the town lay a lawn and, on the edge, a grey
stone wall up to y~100; the rework turns that outer belt into brown rock (cliffs.py). This module decides what is
town (kept and paved), what is a park or a forest (replanted), what is the belt, and which of the belt is rock (`cliff`):
the old wall's ring by the sea, and everything behind the houses on the town's edge, right up against their backs (the
wall stands there, and behind it the rock slopes down to the sea, cliffs.py). The rest of the belt (round the forests and
an open corner) stays a floor of rock to run on.
"""
import numpy as np
from scipy import ndimage

WALL = {'stone', 'andesite', 'calcite', 'brown_terracotta', 'terracotta', 'moss_block', 'gray_terracotta'}
PLANTS = ('fern', 'grass', 'daisy', 'orchid', 'valley', 'cornflower', 'flower', 'tulip', 'poppy', 'dandelion', 'allium',
          'bluet', 'carpet', 'petals', 'bush', 'azalea', 'sapling', 'vine', 'lichen')
TREE = ('leaves', '_log', '_wood')

# hand-drawn zones (world x0, x1, z0, z1, inclusive), traced from the v1.3 blocks and the Roblox reference map
FOREST_NW = (-130, -60, -162, -91)        # reference 'Forests' (green), north-west
FOREST_S = (-4, 116, 97, 178)             # reference 'Forests' (green), south: the boss spawn, the cave, the grail ruins
RUINS = (38, 177, 10)                     # 'Holy Grail War': the ring of ruins south of the forest (x, z, radius)
PARK_W = (-110, -72, -79, -25)            # reference 'Parks' (lime), west: the formal garden inside the sidewalks
GARDEN_S = (15, 61, 0, 89)                # reference 'Parks' (lime), south: the formal garden east of the car park
ARENA = (3, 64, -71, -25)                 # reference 'Battle Field' (red), with its dark frame


def short(n):
    return n.split(':')[-1]


def morph(op, m, iterations):
    """A 3x3 binary opening or closing that treats the world's edge as more of the same (no erosion there)."""
    k = iterations + 1
    return op(np.pad(m, k, mode='edge'), structure=np.ones((3, 3)), iterations=iterations)[k:-k, k:-k]


class Zones:
    def __init__(self, w, keep_points=(), margin=4):
        self.w = w
        names = np.array([short(n) for n in w.names])
        self.names = names
        y = lambda yy: yy - w.y0
        n80 = names[w.v[y(80)]]
        above = w.v[y(81):y(110)]
        is_plant = np.array([n == 'air' or any(k in n for k in PLANTS) for n in names])
        is_tree = np.array([any(k in n for k in TREE) for n in names])
        is_wall = np.array([n in WALL for n in names])
        # land: a solid block anywhere from the sea's surface up (the island's columns; the sea's top is water or sand at 62)
        solid = ~np.isin(names, ['air', 'water'])
        self.land = solid[w.v[y(63):y(81)]].any(0)
        self.sea = ~self.land
        self.d_sea = ndimage.distance_transform_edt(np.pad(self.land, 1))[1:-1, 1:-1]   # beyond the world: sea
        self.road = n80 == 'black_concrete'
        self.sidewalk = n80 == 'polished_andesite'
        self.grass = n80 == 'grass_block'
        wallish = (self.d_sea <= 12) & (is_wall[above].any(0) | np.isin(n80, list(WALL)))
        lab, n = ndimage.label(wallish)
        touch = np.unique(lab[(self.d_sea <= 3) & wallish])
        self.wall = np.isin(lab, touch[touch > 0])
        self.treecol = is_tree[above].any(0)
        tall = (~is_plant[above] & ~is_tree[above])
        # standing on the ground and two blocks tall or more: houses, venue walls, lamp posts, kiosks (not a lantern
        # hanging in a crown)
        self.building = tall[:2].any(0) & tall[1:].any(0) & ~self.wall
        floor = self.land & ~self.grass & ~np.isin(n80, ['air', 'water', 'dirt', 'grass_block'])
        self.feature = (self.road | self.sidewalk | (floor & ~self.wall) | self.building) & ~self.wall
        self.forest_nw = self.rect(*FOREST_NW)
        self.forest_s = self.rect(*FOREST_S) | self.disc(*RUINS)
        self.park_w = self.rect(*PARK_W)
        self.garden_s = self.rect(*GARDEN_S)
        self.arena = self.rect(*ARENA)
        keep = np.zeros_like(self.land)
        for kx, kz, r in keep_points:
            keep |= self.disc(kx, kz, r)
        d_feat = ndimage.distance_transform_edt(~self.feature)
        # the belt follows a forest's own edge (four blocks round its trees), not the rectangle drawn round it
        trees_near = ndimage.binary_dilation(self.treecol, iterations=4)
        woods = ((self.forest_nw | self.forest_s) & (trees_near | self.feature)) | self.disc(*RUINS)
        zones = (woods | self.park_w | self.garden_s) & ~self.wall
        self.greens = zones
        town = (((d_feat <= margin) & ~self.wall) | zones | keep) & self.land
        cand = self.land & ~town
        lab, n = ndimage.label(cand)
        edge = np.unique(lab[cand & ((self.d_sea <= 2.5) | self.wall)])
        band = np.isin(lab, edge[edge > 0])
        # smooth its inner edge: no slivers of rock between houses, never closer than `margin` to the town's features
        band = morph(ndimage.binary_opening, band, 2) | (band & (self.d_sea <= 2.5))
        band = morph(ndimage.binary_closing, band, 2) & self.land & (d_feat > margin) & ~keep
        band |= self.wall & ~keep          # the old wall is rock wherever it stood, even against a house
        # the town bridges the gaps between neighbouring houses (up to about sixteen blocks), so the wall behind a row
        # of houses runs straight along their backs instead of reaching in between them
        town0 = self.land & ~band
        band &= ~(morph(ndimage.binary_closing, town0, 8) & ~self.wall)
        self.cliff = self.walls(band, keep)
        self.band = band | self.cliff
        self.town = self.land & ~self.band
        self.d_town = ndimage.distance_transform_edt(~self.town)

    def houses(self):
        """Footprints of the buildings, doors closed and insides filled (lamp posts, benches and statues left out, and
        the cave, the boss spawn and the grail ruins in the forests)."""
        lab, n = ndimage.label(self.building)
        sizes = ndimage.sum(np.ones_like(lab), lab, range(1, n + 1))
        woods = self.forest_nw | self.forest_s
        out = np.zeros(self.land.shape, bool)
        for i, size in enumerate(sizes, 1):
            if size < 30: continue
            m = lab == i
            if (m & woods).sum() > size / 2: continue
            out |= ndimage.binary_fill_holes(morph(ndimage.binary_closing, m, 2))
        return out

    def walls(self, band, keep, reach=12, gap=32):
        """The huge wall: the ring by the sea where the old wall stood, and wherever the belt lies behind a house on the
        town's edge, filled right up to its back. Looking straight out from each face of a house over the paving (no
        road, park, forest or other house), the belt is met within `reach` blocks: that face is a back, and the wall
        covers the paving behind it and the belt beyond it out to the sea. Between neighbouring houses (up to `gap`
        blocks apart) it runs on along their backs; their sides and fronts stay clear."""
        houses = self.houses()
        town = self.land & ~band
        # the paving a back looks out over (a lamp post on it is buried)
        over = town & ~houses & ~self.road & ~keep & ~self.greens
        ring = band & (self.d_sea <= 4.5) | (self.wall & band)
        wall = ring.copy()
        fill = np.zeros_like(band)
        inf = 1 << 20
        # each direction in turn, turned so that it points along +x (axis 1)
        turns = [(lambda a: a, lambda a: a), (lambda a: a[:, ::-1], lambda a: a[:, ::-1]),
                 (lambda a: a.T, lambda a: a.T), (lambda a: a.T[:, ::-1], lambda a: a[:, ::-1].T)]
        for fwd, back in turns:
            H, O, B = fwd(houses), fwd(over), fwd(band)
            rows, cols = H.shape
            face = H.copy(); face[:, :-1] &= ~H[:, 1:]
            # steps from the last face behind, over the paving only
            d0 = np.full((rows, cols), inf)
            for x in range(1, cols):
                d0[:, x] = np.where(O[:, x], np.where(face[:, x - 1], 1, np.minimum(d0[:, x - 1] + 1, inf)), inf)
            # steps on to the belt ahead, over the paving only
            tb = np.full((rows, cols), inf)
            tb[:, -1] = np.where(B[:, -1], 0, inf)
            for x in range(cols - 2, -1, -1):
                tb[:, x] = np.where(B[:, x], 0, np.where(O[:, x], np.minimum(tb[:, x + 1] + 1, inf), inf))
            F = O & (d0 + tb <= reach)
            # into the belt where a back's view reaches it, and on through the belt to the sea
            S = np.zeros_like(B)
            for x in range(1, cols):
                came = (face[:, x - 1] | F[:, x - 1] | S[:, x - 1])
                S[:, x] = B[:, x] & came
            # along the backs: close gaps between neighbouring houses' walls, across the line they look out on
            R = F | S
            ok = O | B
            up = np.full((rows, cols), inf); down = np.full((rows, cols), inf)
            for z in range(1, rows):
                up[z] = np.where(ok[z] & ~R[z], np.where(R[z - 1], 1, np.minimum(up[z - 1] + 1, inf)), inf)
            for z in range(rows - 2, -1, -1):
                down[z] = np.where(ok[z] & ~R[z], np.where(R[z + 1], 1, np.minimum(down[z + 1] + 1, inf)), inf)
            R = R | (ok & ~R & (up + down - 1 <= gap))
            fill |= back(R & O)
            wall |= back(R)
        wall &= ~keep & self.land
        # no thin strips of floor between walls, no thin fins of wall, and no floor shut in by the wall
        floor = band & ~wall
        wall |= floor & ~morph(ndimage.binary_opening, floor, 2)
        wall = (morph(ndimage.binary_opening, wall, 1) | ring) & ~keep & self.land
        floor = band & ~wall
        lab, n = ndimage.label(floor)
        reached = np.unique(lab[floor & ndimage.binary_dilation(town & ~wall)])
        wall |= floor & ~np.isin(lab, reached[reached > 0]) & ~keep
        self.backfill = wall & fill            # paving behind a house that the wall now covers
        return wall

    # --- helpers over the column grid ---
    def idx(self, x, z):
        return z - self.w.z0, x - self.w.x0

    def at(self, mask, x, z):
        zi, xi = self.idx(x, z)
        return 0 <= zi < mask.shape[0] and 0 <= xi < mask.shape[1] and bool(mask[zi, xi])

    def rect(self, x0, x1, z0, z1):
        m = np.zeros(self.land.shape, bool)
        zi0, xi0 = self.idx(x0, z0); zi1, xi1 = self.idx(x1, z1)
        m[max(zi0, 0):zi1 + 1, max(xi0, 0):xi1 + 1] = True
        return m

    def disc(self, cx, cz, r):
        zz, xx = np.indices(self.land.shape)
        return (xx + self.w.x0 + 0.5 - cx - 0.5) ** 2 + (zz + self.w.z0 + 0.5 - cz - 0.5) ** 2 <= r * r

    def cells(self, mask):
        """World (x, z) of every True column."""
        zi, xi = np.nonzero(mask)
        return list(zip((xi + self.w.x0).tolist(), (zi + self.w.z0).tolist()))
