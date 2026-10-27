"""Checks a built Azure Garden v1.5 'Expanse' world against its plan (analysis only; reads the region files).

    python3 check.py WORLD PLAN_DIR

Prints JSON: the blocks that differ from the plan (by kind), the highest block (under the clouds at 192?), whether a
player can walk from the spawn to the sea (stepping up at most one block, down any: the wall must stop everyone), how
much ground they can reach, and whether the spawn, the merchants and every supply spot stand on grass with room above.
"""
import json, sys
from collections import Counter, deque
from pathlib import Path
import numpy as np

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / 'cliffside'))
from world import World


def main(world_dir, plan_dir):
    plan_dir = Path(plan_dir)
    plan = json.loads((plan_dir / 'plan.json').read_text())
    x0, z0, w, d, y0, h = (plan[k] for k in ('x0', 'z0', 'w', 'd', 'y0', 'h'))
    vol = np.fromfile(plan_dir / 'volume.bin', np.uint8).reshape(d, w, h)
    pal = plan['palette']
    world = World(world_dir, y0, y0 + h + 16)
    # blocks compared by name (the world writes a water block as water[level=0], a grass block as
    # grass_block[snowy=false]): every block kind numbered once, the plan's palette and the world's
    kinds_of = sorted(set(world.names.tolist()) | set(pal))
    number = {n: i for i, n in enumerate(kinds_of)}
    world_kind = np.array([number[n] for n in world.names], np.uint16)
    plan_kind = np.array([number[n] for n in pal], np.uint16)
    sx0, sz0 = x0 - world.x0, z0 - world.z0
    got = world_kind[world.v[0:h, sz0:sz0 + d, sx0:sx0 + w]]           # [y, z, x]
    want = plan_kind[vol].transpose(2, 0, 1)
    diff = got != want
    kinds = Counter()
    if diff.any():
        ys, zs, xs = np.nonzero(diff)
        for y, z, x in list(zip(ys, zs, xs))[:200000]:
            kinds[f'{kinds_of[want[y, z, x]]} -> {kinds_of[got[y, z, x]]}'] += 1
    # nothing over the plan's top
    above = world.v[h:, sz0:sz0 + d, sx0:sx0 + w]
    top_y = int(y0 + h - 1 - np.argmax((got != number['minecraft:air'])[::-1].any(axis=(1, 2))))
    # walking: column heights over the whole loaded world, from the spawn
    solid = ~np.isin(world.names, ['minecraft:air', 'minecraft:water'])[world.v]
    H = solid.shape[0] - 1 - np.argmax(solid[::-1], axis=0) + world.y0
    H[~solid.any(0)] = world.y0 - 1
    sea = H <= plan['sea']
    sx, sy, sz, _ = plan['spawn']
    start = (sz - world.z0, sx - world.x0)
    seen = np.zeros(H.shape, bool); seen[start] = True
    q = deque([start]); reached = None; highest = 0
    while q:
        z, x = q.popleft()
        highest = max(highest, int(H[z, x]))
        if sea[z, x]:
            reached = (int(x + world.x0), int(z + world.z0)); break
        for dz, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nz, nx = z + dz, x + dx
            if 0 <= nz < H.shape[0] and 0 <= nx < H.shape[1] and not seen[nz, nx] and H[nz, nx] - H[z, x] <= 1:
                seen[nz, nx] = True; q.append((nz, nx))
    # the mod's spots: grass underfoot, two blocks of air
    definition = json.loads((plan_dir / 'datapack/data/botand/map/definition.json').read_text())
    spots = [('spawn', sx, sy, sz)] + [(k, int(m['pos'][0] // 1), int(m['pos'][1]), int(m['pos'][2] // 1)) for k, m in definition['merchants'].items()]
    spots += [(f'loot{i}', x, y, z) for i, (x, y, z) in enumerate(definition['loot'])]
    bad = [s for s in spots if not (world.name(s[1], s[2] - 1, s[3]) == 'minecraft:grass_block'
                                    and world.name(s[1], s[2], s[3]) == 'minecraft:air' and world.name(s[1], s[2] + 1, s[3]) == 'minecraft:air')]
    out = {'differing_blocks': int(diff.sum()), 'differences': dict(kinds.most_common(12)),
           'blocks_over_the_plan': int((above != 0).sum()), 'highest_block_y': top_y,
           'walkable_columns_from_spawn': int(seen.sum()), 'highest_standing_y': highest,
           'escape': f'reached the sea at {reached}' if reached else 'the sea cannot be reached',
           'spots_checked': len(spots), 'spots_bad': bad}
    print(json.dumps(out, ensure_ascii=False))
    return out


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
