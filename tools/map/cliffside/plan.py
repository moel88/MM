"""The cliffside rework's placement plan (map v1.4): every block the rework changes, checked against what must not
move. Coordinates are world blocks.
"""
import numpy as np


def full(state):
    return state if ':' in state.split('[')[0] else 'minecraft:' + state


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
