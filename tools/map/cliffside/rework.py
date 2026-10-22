"""Builds the whole cliffside plan for Botand Azure Garden v1.3 (map v1.4): the Roblox map's look on the same town.

    python3 rework.py WORLD OUT_DIR      # writes OUT_DIR/plan.json (counts) and OUT_DIR/preview.png (not a game screen)
run_rework.py applies the plan in a real server; this module only decides what goes where.
"""
import json, sys
from pathlib import Path
HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import numpy as np
from scipy import ndimage
from world import World, read_patch
from plan import Plan
from zones import Zones
import cliffs, town

ROOT = HERE.parents[2]
PATCHES = ROOT / 'src/main/resources/data/celestialfall/map'
# the mod's own fixes to this map run whenever it loads (map/FloorRepair, map/VenueRenovation): keep clear of them
VENUES = [(-15, 3, -128, -89), (-58, -41, 33, 58)]
SPAWN = (33, 81, -19)
MERCHANTS = [(-6.5, -118.5), (55.5, -124.5)]
TELEPORTS = [(33, 81, -48), (38, 81, 45), (60, 81, 112)]


def protected(w):
    keep = set()
    def cube(x0, x1, y0, y1, z0, z1):
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                for z in range(z0, z1 + 1):
                    keep.add((x, y, z))
    # supply chests and the spawn's sign with the air round them; the floor under them may be paved like the floor
    # round them (a chest or a standing sign needs only something solid there)
    for be in w.block_entities:
        x, y, z = int(be['x']), int(be['y']), int(be['z'])
        cube(x - 1, x + 1, y, y + 2, z - 1, z + 1)
    sx, sy, sz = SPAWN
    cube(sx - 1, sx + 1, sy, sy + 2, sz - 1, sz + 1)
    for mx, mz in MERCHANTS:
        cube(int(mx) - 2, int(mx) + 2, 80, 83, int(mz) - 2, int(mz) + 2)
    for x, y, z in TELEPORTS:
        cube(x, x, y, y + 2, z, z)
    for x0, x1, z0, z1 in VENUES:
        cube(x0, x1, 79, 112, z0, z1)
    return keep


def keep_points(w):
    """Places the rock must stay away from: the supply spots (the mod spawns field loot there), spawn, merchants, teleports."""
    pts = [(int(be['x']), int(be['z']), 6) for be in w.block_entities]
    pts += [(SPAWN[0], SPAWN[2], 6)] + [(int(x), int(z), 6) for x, z in MERCHANTS] + [(x, z, 4) for x, _, z in TELEPORTS]
    return pts


def trees(w, y0=81, y1=112):
    """The island's trees and bushes: connected log and leaf blocks, each with the columns its logs stand on."""
    is_tree = np.array([any(k in n for k in ('leaves', '_log', '_wood')) for n in w.names])
    is_log = np.array([n.endswith('_log') or n.endswith('_wood') for n in w.names])
    vol = w.v[y0 - w.y0:y1 - w.y0]
    lab, n = ndimage.label(is_tree[vol], structure=np.ones((3, 3, 3)))
    out = []
    for i, sl in enumerate(ndimage.find_objects(lab), 1):
        if sl is None: continue
        ys, zs, xs = np.nonzero(lab[sl] == i)
        ys = ys + sl[0].start + y0; zs = zs + sl[1].start + w.z0; xs = xs + sl[2].start + w.x0
        logs = is_log[w.v[ys - w.y0, zs - w.z0, xs - w.x0]]
        trunks = sorted({(int(x), int(z)) for x, z, lg, y in zip(xs, zs, logs, ys) if lg and y <= y0 + 2})
        out.append({'voxels': list(zip(xs.tolist(), ys.tolist(), zs.tolist())), 'trunks': trunks})
    return out


def clear_belt_trees(p, Z, forest):
    """Trees standing on the belt go (a crown shared with a town tree is split by the nearest trunk)."""
    gone = 0
    for t in forest:
        if t['trunks']:
            on_belt = [Z.at(Z.band, x, z) for x, z in t['trunks']]
            if not any(on_belt): continue
            for x, y, z in t['voxels']:
                d = [(tx - x) ** 2 + (tz - z) ** 2 for tx, tz in t['trunks']]
                if on_belt[int(np.argmin(d))]:
                    p.put(x, y, z, 'air'); gone += 1
        else:
            xs = [v[0] for v in t['voxels']]; zs = [v[2] for v in t['voxels']]
            if Z.at(Z.band, int(round(np.mean(xs))), int(round(np.mean(zs)))):
                for x, y, z in t['voxels']:
                    p.put(x, y, z, 'air'); gone += 1
    return gone


def build(w, stats=None):
    _, fix = read_patch(PATCHES / 'azure_garden_floor_fix.bgp.gz')
    p = Plan(w, protect=protected(w), floor_fix={(int(x), int(y), int(z)) for x, y, z, _, _ in fix})
    Z = Zones(w, keep_points(w))
    forest = trees(w)
    stats = stats if stats is not None else {}
    stats['belt_columns'] = int(Z.band.sum())
    stats['belt_trees_cells'] = clear_belt_trees(p, Z, forest)
    cliffs.rim(p, Z)
    town.rework(p, Z, forest, stats)
    return p


if __name__ == '__main__':
    w = World(sys.argv[1], 44, 200)
    out = Path(sys.argv[2]); out.mkdir(parents=True, exist_ok=True)
    stats = {}
    p = build(w, stats)
    counts = {}
    for s in p.cells.values():
        counts[s.split('[')[0]] = counts.get(s.split('[')[0], 0) + 1
    (out / 'plan.json').write_text(json.dumps({'cells': len(p.cells), 'refused': p.refused, 'entities': len(p.entities),
                                               'commands': len(p.commands()), 'stats': stats,
                                               'blocks': dict(sorted(counts.items(), key=lambda kv: -kv[1]))}, indent=1))
    import preview
    p.apply_to(w)
    preview.render(w, 3).save(out / 'preview.png')
    print('cells', len(p.cells), 'refused', p.refused, 'commands', len(p.commands()), stats)
