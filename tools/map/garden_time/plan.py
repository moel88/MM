"""The time garden's placement plan (map v1.4): every block the decorator changes, checked against what must not move.

Coordinates are world blocks. Continuous positions (centres, radii) are in block units with a cell's centre at x + 0.5,
so a shape centred on cell 38 uses cx = 38.5. Clock angles are degrees clockwise from north (-z), the way a clock is
read from above with north at the top: 3 o'clock is east (+x).
"""
import math
from pathlib import Path
import numpy as np


def full(state):
    return state if ':' in state.split('[')[0] else 'minecraft:' + state


def ahead(theta):
    """The unit (dx, dz) a floor clock's hand points at angle theta (degrees clockwise from north)."""
    t = math.radians(theta)
    return math.sin(t), -math.cos(t)


class Plan:
    def __init__(self, world, protect=(), floor_fix=()):
        self.w = world
        self.cells = {}
        self.entities = []          # summon commands' arguments: (entity id, x, y, z, nbt)
        self.protect = set(protect)  # cells that keep their block (chests, spawn, merchants, venues...)
        self.floor_fix = set(floor_fix)  # y=80 cells where the mod turns quartz bricks into andesite on every start
        self.refused = 0

    def put(self, x, y, z, state):
        if (x, y, z) in self.protect:
            self.refused += 1
            return False
        state = full(state)
        if (x, y, z) in self.floor_fix and state == 'minecraft:quartz_bricks':
            state = 'minecraft:smooth_quartz'
        self.cells[(x, y, z)] = state
        return True

    def state(self, x, y, z):
        s = self.cells.get((x, y, z))
        return s if s is not None else self.w.state(x, y, z)

    def name(self, x, y, z):
        return self.state(x, y, z).split('[')[0]

    def box(self, x0, y0, z0, x1, y1, z1, state):
        for y in range(min(y0, y1), max(y0, y1) + 1):
            for z in range(min(z0, z1), max(z0, z1) + 1):
                for x in range(min(x0, x1), max(x0, x1) + 1):
                    self.put(x, y, z, state)

    def clear(self, x0, y0, z0, x1, y1, z1):
        self.box(x0, y0, z0, x1, y1, z1, 'air')

    def summon(self, entity, x, y, z, nbt):
        self.entities.append((entity, x, y, z, nbt))

    # --- writing ---
    def commands(self):
        """setblock/fill commands for the plan, runs along x merged into fills; order: by y, then z, then x."""
        out = []
        keys = sorted(self.cells, key=lambda c: (c[1], c[2], c[0]))
        i = 0
        while i < len(keys):
            x, y, z = keys[i]; s = self.cells[keys[i]]
            j = i
            while j + 1 < len(keys) and keys[j + 1] == (keys[j][0] + 1, y, z) and self.cells[keys[j + 1]] == s:
                j += 1
            if j == i:
                out.append(f'setblock {x} {y} {z} {s}')
            else:
                out.append(f'fill {x} {y} {z} {keys[j][0]} {y} {z} {s}')
            i = j + 1
        return out

    def summons(self):
        return [f'summon {e} {x} {y} {z} {nbt}' for e, x, y, z, nbt in self.entities]

    def apply_to(self, w):
        """The plan written into a World's volume (for previews and checks, not the game)."""
        for (x, y, z), s in self.cells.items():
            if w.inside(x, y, z):
                w.v[y - w.y0, z - w.z0, x - w.x0] = w._id(s)
        w.names = np.array([p.split('[')[0] for p in w.palette])


# --- shapes on the floor (cells of a disc, a ring, a thick segment) ---
def disc(cx, cz, r):
    for z in range(math.floor(cz - r) - 1, math.ceil(cz + r) + 1):
        for x in range(math.floor(cx - r) - 1, math.ceil(cx + r) + 1):
            d = math.hypot(x + 0.5 - cx, z + 0.5 - cz)
            if d < r:
                yield x, z, d


def ring(cx, cz, r0, r1):
    for x, z, d in disc(cx, cz, r1):
        if d >= r0:
            yield x, z, d


def segment(ax, az, bx, bz, half):
    """Cells whose centres lie within `half` of the segment a-b (a thick line)."""
    x0, x1 = math.floor(min(ax, bx) - half) - 1, math.ceil(max(ax, bx) + half) + 1
    z0, z1 = math.floor(min(az, bz) - half) - 1, math.ceil(max(az, bz) + half) + 1
    vx, vz = bx - ax, bz - az; ll = vx * vx + vz * vz or 1e-9
    for z in range(z0, z1 + 1):
        for x in range(x0, x1 + 1):
            px, pz = x + 0.5 - ax, z + 0.5 - az
            t = max(0.0, min(1.0, (px * vx + pz * vz) / ll))
            if math.hypot(px - t * vx, pz - t * vz) <= half:
                yield x, z


def tapered(ax, az, bx, bz, w0, w1):
    """Cells of a hand from a (width w0) to b (width w1)."""
    vx, vz = bx - ax, bz - az; ll = vx * vx + vz * vz or 1e-9
    x0, x1 = math.floor(min(ax, bx) - w0) - 1, math.ceil(max(ax, bx) + w0) + 1
    z0, z1 = math.floor(min(az, bz) - w0) - 1, math.ceil(max(az, bz) + w0) + 1
    for z in range(z0, z1 + 1):
        for x in range(x0, x1 + 1):
            px, pz = x + 0.5 - ax, z + 0.5 - az
            t = (px * vx + pz * vz) / ll
            if t < 0 or t > 1: continue
            if math.hypot(px - t * vx, pz - t * vz) <= (w0 + (w1 - w0) * t) / 2:
                yield x, z


# --- pixel glyphs for Roman numerals (7 rows) ---
GLYPHS = {
    'I': ['X'] * 7,
    'V': ['X...X', 'X...X', 'X...X', '.X.X.', '.X.X.', '..X..', '..X..'],
    'X': ['X...X', 'X...X', '.X.X.', '..X..', '.X.X.', 'X...X', 'X...X'],
}


def numeral(text, gap=1):
    """A Roman numeral as rows of 'X' and '.', letters joined with `gap` empty columns."""
    rows = [''] * 7
    for k, ch in enumerate(text):
        g = GLYPHS[ch]
        for r in range(7):
            rows[r] += ('.' * gap if k else '') + g[r]
    return rows
