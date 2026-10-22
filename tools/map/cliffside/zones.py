"""Where things are on Azure Garden v1.3, as boolean maps over the island's columns (index [z - z0, x - x0]).

The island is a plateau at y=80 on a sea floor at 48 (sea level 62). Round the town lay a lawn and, on the edge, a grey
stone wall up to y~100; the rework turns that outer belt into brown rock (cliffs.py). This module decides what is town
(kept and paved), what is a park or a forest (replanted), and what is the belt.
"""
import numpy as np
from scipy import ndimage

WALL = {'stone', 'andesite', 'calcite', 'brown_terracotta', 'terracotta', 'moss_block', 'gray_terracotta'}
PLANTS = ('fern', 'grass', 'daisy', 'orchid', 'valley', 'cornflower', 'flower', 'tulip', 'poppy', 'dandelion', 'allium',
          'bluet', 'carpet', 'petals', 'bush', 'azalea', 'sapling', 'vine', 'lichen')
TREE = ('leaves', '_log', '_wood')

# hand-drawn zones (world x0, x1, z0, z1, inclusive), traced from the v1.3 blocks and the Roblox reference map
FOREST_NW = (-126, -60, -152, -91)        # reference 'Forests' (green), north-west
FOREST_S = (-2, 113, 97, 174)             # reference 'Forests' (green), south: the boss spawn, the cave, the grail ruins
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
        zones = (self.forest_nw | self.forest_s | self.park_w | self.garden_s) & ~self.wall
        town = (((d_feat <= margin) & ~self.wall) | zones | keep) & self.land
        cand = self.land & ~town
        lab, n = ndimage.label(cand)
        edge = np.unique(lab[cand & ((self.d_sea <= 2.5) | self.wall)])
        band = np.isin(lab, edge[edge > 0])
        # smooth its inner edge: no slivers of rock between houses, never closer than two blocks to the town's features
        band = morph(ndimage.binary_opening, band, 2) | (band & (self.d_sea <= 2.5))
        band = morph(ndimage.binary_closing, band, 2) & self.land & (d_feat > 2) & ~keep
        band |= self.wall & ~keep          # the old wall is rock wherever it stood, even against a house
        self.band = band
        self.town = self.land & ~band
        self.d_town = ndimage.distance_transform_edt(~self.town)

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
