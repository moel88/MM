"""Compares a built world with the plan: every planned cell must hold its block (states the game reshapes on placement,
such as fences and stairs joining their neighbours, are compared by block only), and nothing else may differ from the
source world except blocks the game itself changes next to the plan (a flower left without ground, a lantern without
its post). usage: python3 check.py SRC_WORLD BUILT_WORLD"""
import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parent))
import numpy as np
from world import World
import decorate

src, built = World(sys.argv[1], 60, 200), World(sys.argv[2], 60, 200)
plan = decorate.build(src)
wrong = []
for (x, y, z), s in plan.cells.items():
    got = built.state(x, y, z)
    if got == s: continue
    if got.split('[')[0] == s.split('[')[0]: continue
    wrong.append(((x, y, z), s, got))
print('planned cells', len(plan.cells), 'not as planned', len(wrong))
for item in wrong[:15]: print('  ', item)
# differences outside the plan
a = np.array([built.index.get(p, -1) for p in src.palette])  # src palette id -> built palette id
H = min(src.H, built.H)
# the server may have added ocean chunks round the island: compare the island's extent only
ox, oz = src.x0 - built.x0, src.z0 - built.z0
same_shape = ox >= 0 and oz >= 0 and built.W - ox >= src.W and built.D - oz >= src.D
if same_shape:
    sv = a[src.v[:H]]; bv = built.v[:H, oz:oz + src.D, ox:ox + src.W]
    diff = np.argwhere(sv != bv)
    planned = set(plan.cells)
    extra = [(int(x) + src.x0, int(y) + src.y0, int(z) + src.z0) for y, z, x in diff]
    extra = [c for c in extra if c not in planned]
    print('changed outside the plan', len(extra))
    from collections import Counter
    cnt = Counter((src.name(*c), built.name(*c)) for c in extra)
    for k, v in cnt.most_common(12): print('  ', v, k)
else:
    print('world extents differ:', (src.x0, src.z0, src.W, src.D), (built.x0, built.z0, built.W, built.D))
