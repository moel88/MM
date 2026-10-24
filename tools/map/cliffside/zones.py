"""Where things are on Azure Garden v1.3, as boolean maps over the island's columns (index [z - z0, x - x0]).

The island is a plateau at y=80 on a sea floor at 48 (sea level 62). Round the town lay a lawn and, on the edge, a grey
stone wall up to y~100; the rework turns that outer belt into brown rock (cliffs.py). This module decides what is
town (kept and paved), what is a park or a forest (replanted), what is the belt, and which of the belt is rock (`cliff`):
the old wall's ring by the sea, and everything behind the houses on the town's edge, right up against their backs (the
wall stands there, and behind it the rock slopes down to the sea, cliffs.py). A house has one back, the face the street
does not see; its sides and front keep four blocks of pavement clear of the rock. The rest of the belt (round the forests
and an open corner) stays a floor of rock to run on.
"""
import numpy as np
from scipy import ndimage

WALL = {'stone', 'andesite', 'calcite', 'brown_terracotta', 'terracotta', 'moss_block', 'gray_terracotta'}
PLANTS = ('fern', 'grass', 'daisy', 'orchid', 'valley', 'cornflower', 'flower', 'tulip', 'poppy', 'dandelion', 'allium',
          'bluet', 'carpet', 'petals', 'bush', 'azalea', 'sapling', 'vine', 'lichen')
TREE = ('leaves', '_log', '_wood')
AWNING = ('light_blue_wool', 'white_wool', 'light_gray_wool', 'blue_wool', 'yellow_wool')
# the four faces of a house as (dz, dx): east, west, south, north; a mask turned so that direction k points along +x
FACES = ((0, 1), (0, -1), (1, 0), (-1, 0))
OPPOSITE = (1, 0, 3, 2)
INF = 1 << 20


def turn(a, k):
    return (a, a[:, ::-1], a.T, a.T[:, ::-1])[k]


def unturn(a, k):
    return (a, a[:, ::-1], a.T, a[:, ::-1].T)[k]


def ahead(target, through):
    """Steps from each cell to the nearest `target` cell ahead (+x), over `through` cells only (0 on a target)."""
    t = np.full(target.shape, INF)
    t[:, -1] = np.where(target[:, -1], 0, INF)
    for x in range(target.shape[1] - 2, -1, -1):
        t[:, x] = np.where(target[:, x], 0, np.where(through[:, x], np.minimum(t[:, x + 1] + 1, INF), INF))
    return t


def behind(face, through):
    """Steps from the last `face` cell behind (-x), over `through` cells only (1 just past the face)."""
    d = np.full(face.shape, INF)
    for x in range(1, face.shape[1]):
        d[:, x] = np.where(through[:, x], np.where(face[:, x - 1], 1, np.minimum(d[:, x - 1] + 1, INF)), INF)
    return d


def across(ok, end):
    """Steps along axis 0 to the nearest `end` cell above and below, over `ok` cells only."""
    up = np.full(ok.shape, INF); down = np.full(ok.shape, INF)
    for z in range(1, ok.shape[0]):
        up[z] = np.where(ok[z] & ~end[z], np.where(end[z - 1], 1, np.minimum(up[z - 1] + 1, INF)), INF)
    for z in range(ok.shape[0] - 2, -1, -1):
        down[z] = np.where(ok[z] & ~end[z], np.where(end[z + 1], 1, np.minimum(down[z + 1] + 1, INF)), INF)
    return up, down

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
        self.cliff, band = self.walls(band, keep)
        self.band = band | self.cliff
        self.town = self.land & ~self.band
        self.d_town = ndimage.distance_transform_edt(~self.town)

    def houses(self):
        """Footprints of the buildings, doors closed and insides filled (lamp posts, benches and statues left out, and
        the cave, the boss spawn and the grail ruins in the forests), labelled one by one."""
        lab, n = ndimage.label(self.building)
        sizes = ndimage.sum(np.ones_like(lab), lab, range(1, n + 1))
        woods = self.forest_nw | self.forest_s
        out = np.zeros(self.land.shape, bool)
        for i, size in enumerate(sizes, 1):
            if size < 30: continue
            m = lab == i
            if (m & woods).sum() > size / 2: continue
            out |= ndimage.binary_fill_holes(morph(ndimage.binary_closing, m, 2))
        return ndimage.label(out)

    def backs(self, lab, n, band, look, reach):
        """Each house's back (an index into FACES, or -1): one face only, and only a face that looks out on the belt
        within `reach` blocks over the paving (an outer face). The back is the face opposite the one the street sees
        (a road within reach); failing that, the face opposite its front (the awning: the v1.3 houses all face south,
        some of them out to sea); failing that, its most outward face. `look` is what a face sees across."""
        w = self.w
        awning = np.isin(self.names[w.v[82 - w.y0:88 - w.y0]], list(AWNING)).any(0)
        idx = np.arange(1, n + 1)
        nxt = lambda a: np.pad(a[:, 1:], ((0, 0), (0, 1)), constant_values=INF)    # the value one block ahead
        outer, street, front = np.zeros((3, 4, n))
        for k in range(4):
            H, O, B = turn(lab > 0, k), turn(look, k), turn(band, k)
            face = H.copy(); face[:, :-1] &= ~H[:, 1:]
            belt = face & (nxt(ahead(B, O)) < reach)
            road = face & (nxt(ahead(turn(self.road, k), O)) < reach)
            awn = turn(awning, k)
            near = np.zeros_like(face)
            for s in (1, 2, 3):
                near[:, :-s] |= awn[:, s:]
            L, f = turn(lab, k), np.maximum(ndimage.sum(face, turn(lab, k), idx), 1)
            outer[k] = ndimage.sum(belt, L, idx) / f
            street[k] = ndimage.sum(road, L, idx) / f
            front[k] = ndimage.sum(face & near, L, idx) / f
        out = np.full(n, -1)
        for i in range(n):
            ok = [k for k in range(4) if outer[k, i] >= 0.5]
            if not ok: continue
            s, f = int(np.argmax(street[:, i])), int(np.argmax(front[:, i]))
            if street[s, i] >= 0.3 and OPPOSITE[s] in ok: out[i] = OPPOSITE[s]
            elif front[f, i] > 0 and OPPOSITE[f] in ok: out[i] = OPPOSITE[f]
            else: out[i] = max(ok, key=lambda k: outer[k, i])
        return out

    def walls(self, band, keep, reach=12, gap=32, clear=4):
        """The huge wall: the ring by the sea where the old wall stood, and wherever the belt lies behind a house on the
        town's edge, filled right up to its back. A house's back (Zones.backs) looks straight out over the paving (no
        road, park, forest or other house) and meets the belt within `reach` blocks; the wall covers the paving behind
        it and the belt beyond it out to the sea. Between neighbouring houses (up to `gap` blocks apart) it runs on
        along their backs. Every other face, the sides and the front, keeps `clear` blocks in front of it as pavement,
        taken out of the belt. Returns the wall and the belt that is left."""
        lab, n = self.houses()
        houses = lab > 0
        ring = band & (self.d_sea <= 4.5) | (self.wall & band)
        look = self.land & ~band & ~houses & ~self.road & ~self.greens
        over = look & ~keep
        backs = self.backs(lab, n, band, look, reach)
        back_of = np.concatenate([[-2], backs])[lab]           # each house cell: its house's back (-2 off houses)
        # the pavement kept clear in front of every face that is not a back (never the ring by the sea)
        clearance = np.zeros_like(band)
        for k in range(4):
            H, side = turn(houses, k), turn(houses & (back_of != k), k)
            face = side.copy(); face[:, :-1] &= ~H[:, 1:]
            clearance |= unturn(behind(face, ~H) <= clear, k)
        clearance &= self.land & ~houses & ~ring & ~keep
        band = band & ~clearance
        town = self.land & ~band
        # the paving a back looks out over (a lamp post on it is buried)
        over &= ~clearance
        wall = ring.copy()
        fill = np.zeros_like(band)
        for k in range(4):
            H, O, B = turn(houses, k), turn(over, k), turn(band, k)
            rows, cols = H.shape
            face = turn(houses & (back_of == k), k); face[:, :-1] &= ~H[:, 1:]
            # steps from the back behind, and on to the belt ahead, over the paving only
            F = O & (behind(face, O) + ahead(B, O) <= reach)
            # into the belt where a back's view reaches it, and on through the belt to the sea
            S = np.zeros_like(B)
            for x in range(1, cols):
                came = (face[:, x - 1] | F[:, x - 1] | S[:, x - 1])
                S[:, x] = B[:, x] & came
            # along the backs: close gaps between neighbouring houses' walls, and between a house's wall and the ring by
            # the sea, across the line they look out on (no gorge of floor between two walls)
            R = F | S
            ok = O | B
            E = R | turn(ring, k)
            uR, dR = across(ok, R)
            uE, dE = across(ok, E)
            R = R | (ok & ~E & ((uR + dE - 1 <= gap) | (uE + dR - 1 <= gap)))
            fill |= unturn(R & O, k)
            wall |= unturn(R, k)
        wall &= ~keep & self.land
        # no thin strips of floor between walls, no thin fins of wall, and no floor shut in by the wall
        floor = band & ~wall
        wall |= floor & ~morph(ndimage.binary_opening, floor, 2)
        wall = (morph(ndimage.binary_opening, wall, 1) | ring) & ~keep & self.land & ~clearance
        floor = band & ~wall
        flab, fn = ndimage.label(floor)
        reached = np.unique(flab[floor & ndimage.binary_dilation(town & ~wall)])
        wall |= floor & ~np.isin(flab, reached[reached > 0]) & ~keep
        # a little floor left among the houses (away from the woods) is paved like the town round it
        floor = band & ~wall
        flab, fn = ndimage.label(floor)
        sizes = ndimage.sum(floor, flab, range(1, fn + 1))
        woods = ndimage.binary_dilation(self.greens, iterations=2)
        for i in range(1, fn + 1):
            if sizes[i - 1] < 300 and not (woods & (flab == i)).any():
                band &= flab != i
        self.backfill = wall & fill            # paving behind a house that the wall now covers
        self.back_of, self.clearance, self.houses_mask = back_of, clearance, houses
        return wall, band

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
