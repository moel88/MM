"""Builds the whole time-garden plan for Botand Azure Garden v1.3 and checks it (map v1.4).

    python3 decorate.py WORLD OUT_DIR      # writes OUT_DIR/plan.json (counts) and OUT_DIR/preview.png (not a game screen)
run_decorate.py applies the plan in a real server; this module only decides what goes where.
"""
import json, sys
from pathlib import Path
HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
from world import World, read_patch
from plan import Plan
import garden

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
    for be in w.block_entities:            # supply chests and the spawn's sign, the floor under them and the air round them
        x, y, z = int(be['x']), int(be['y']), int(be['z'])
        cube(x - 1, x + 1, y - 1, y + 2, z - 1, z + 1)
    sx, sy, sz = SPAWN
    cube(sx - 1, sx + 1, sy - 1, sy + 2, sz - 1, sz + 1)
    for mx, mz in MERCHANTS:
        cube(int(mx) - 2, int(mx) + 2, 80, 83, int(mz) - 2, int(mz) + 2)
    for x, y, z in TELEPORTS:
        cube(x, x, y, y + 2, z, z)
    for x0, x1, z0, z1 in VENUES:
        cube(x0, x1, 79, 110, z0, z1)
    return keep


def build(w):
    _, fix = read_patch(PATCHES / 'azure_garden_floor_fix.bgp.gz')
    p = Plan(w, protect=protected(w), floor_fix={(int(x), int(y), int(z)) for x, y, z, _, _ in fix})
    garden.arena(p)
    garden.fountain(p)
    garden.clock_tower(p)
    garden.sundial(p)
    garden.floral_clock(p)
    garden.clockwork(p)
    garden.ruins(p)
    garden.stone_circle(p)
    garden.roof_clocks(p)
    garden.spawn_hourglasses(p)
    garden.street_clocks(p)
    return p


if __name__ == '__main__':
    w = World(sys.argv[1], 60, 200)
    out = Path(sys.argv[2]); out.mkdir(parents=True, exist_ok=True)
    p = build(w)
    counts = {}
    for s in p.cells.values():
        counts[s.split('[')[0]] = counts.get(s.split('[')[0], 0) + 1
    (out / 'plan.json').write_text(json.dumps({'cells': len(p.cells), 'refused': p.refused, 'entities': len(p.entities),
                                               'commands': len(p.commands()), 'blocks': dict(sorted(counts.items(), key=lambda kv: -kv[1]))}, indent=1))
    import preview
    p.apply_to(w)
    preview.render(w, 3).save(out / 'preview.png')
    print('cells', len(p.cells), 'refused', p.refused, 'commands', len(p.commands()))
