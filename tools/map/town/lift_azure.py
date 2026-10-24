"""Lifts pieces of Botand's first map (Azure Garden v1.3) for the hourglass island's old city (map v3.0, the user's
request: '원조 보탠드 맵처럼 평범한 건물들 몇 개, 주차장, 투기장'): the arena, a stretch of the car park and the plain
white buildings, block for block, into tools/map/town/pieces/azure_city.txt.gz, which the builder (OldCity) pastes.

usage: python3 tools/map/town/lift_azure.py WORLD_DIR
WORLD_DIR is the Azure Garden world folder (Botand_Azure_Garden_1.20.1 from Botand-Azure-Garden-1.20.1-v1.3.zip).

Each piece is a box of the old world from one block under its ground (y 79; the ground is y 80) up; its front is the
side its door faces (the buildings' doors face south), which the builder turns to face its road. Signs and chests are
left out (their text and contents do not come along).
"""
import gzip, sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import numpy as np
from measure_open import nbt
import glob, os, struct, zlib

def chunks(world):
    for path in glob.glob(os.path.join(world, 'region', 'r.*.mca')):
        data = open(path, 'rb').read()
        for k in range(1024):
            off = int.from_bytes(data[k * 4:k * 4 + 3], 'big')
            if off == 0: continue
            ln = struct.unpack_from('>i', data, off * 4096)[0]; comp = data[off * 4096 + 4]
            raw = data[off * 4096 + 5:off * 4096 + 4 + ln]
            buf = zlib.decompress(raw) if comp == 2 else raw
            root, _ = nbt(buf, 3 + struct.unpack_from('>H', buf, 1)[0], 10)
            yield root

def state_str(p):
    props = p.get('Properties')
    if not props: return p['Name']
    return p['Name'] + '[' + ','.join(f'{k}={v}' for k, v in sorted(props.items())) + ']'

def load(world, x0, y0, z0, x1, y1, z1):
    W, H, D = x1 - x0 + 1, y1 - y0 + 1, z1 - z0 + 1
    pal = ['minecraft:air']; pidx = {'minecraft:air': 0}
    arr = np.zeros((H, D, W), np.int32)
    for root in chunks(world):
        cx, cz = root['xPos'], root['zPos']
        if cx * 16 + 15 < x0 or cx * 16 > x1 or cz * 16 + 15 < z0 or cz * 16 > z1: continue
        for sec in root.get('sections', []):
            sy = sec['Y'] * 16
            if sy + 15 < y0 or sy > y1: continue
            bs = sec.get('block_states')
            if not bs: continue
            spal = [state_str(p) for p in bs['palette']]
            ids = []
            for s in spal:
                if s not in pidx: pidx[s] = len(pal); pal.append(s)
                ids.append(pidx[s])
            ids = np.array(ids)
            if len(spal) == 1: idx = np.zeros(4096, np.int64)
            else:
                b = max(4, int(np.ceil(np.log2(len(spal))))); epl = 64 // b
                longs = bs['data'].astype(np.uint64)
                idx = ((longs[:, None] >> (np.arange(epl, dtype=np.uint64) * np.uint64(b))) & np.uint64((1 << b) - 1)).reshape(-1)[:4096].astype(np.int64)
            idx = ids[idx].reshape(16, 16, 16)
            for ly in range(16):
                y = sy + ly
                if not (y0 <= y <= y1): continue
                for lz in range(16):
                    z = cz * 16 + lz
                    if not (z0 <= z <= z1): continue
                    xs = np.arange(16) + cx * 16
                    m = (xs >= x0) & (xs <= x1)
                    arr[y - y0, z - z0, xs[m] - x0] = idx[ly, lz, m]
    return pal, arr

# name, box (x0, z0, x1, z1 in the old world), top y, the side its front faces
PIECES = [
    ('arena', (0, -76, 68, -20), 100, 'none'),
    ('parking', (-40, -8, 3, 43), 96, 'none'),
    ('hall_a', (70, -61, 92, -25), 106, 'south'),
    ('hall_b', (70, -88, 89, -67), 94, 'south'),
    ('hall_c', (79, -5, 102, 33), 107, 'south'),
    ('house_a', (86, 45, 107, 69), 98, 'south'),
    ('house_b', (89, 72, 110, 95), 98, 'south'),
    ('house_c', (-43, 109, -23, 133), 97, 'south'),
    ('house_d', (-25, 109, -5, 133), 93, 'south'),
    ('kiosk', (20, -13, 38, 1), 88, 'south'),
]
SKIP = ('sign', 'chest', 'barrier', 'structure_void', 'command_block', 'jigsaw')

def main():
    world = sys.argv[1]
    out = Path(__file__).resolve().parent / 'pieces' / 'azure_city.txt.gz'
    lines = ['# Botand Azure Garden v1.3 pieces for the hourglass island (tools/map/town/lift_azure.py); y 0 is the old ground minus one (79)']
    for name, (x0, z0, x1, z1), top, front in PIECES:
        pal, arr = load(world, x0, 79, z0, x1, top, z1)
        # signs and chests out
        for i, s in enumerate(pal):
            if any(k in s for k in SKIP): arr[arr == i] = 0
        used = sorted(set(np.unique(arr).tolist()))
        remap = {u: k for k, u in enumerate(used)}
        H, D, W = arr.shape
        lines.append(f'piece {name} {W} {H} {D} {front} {x0} {z0}')
        lines.append(f'palette {len(used)}')
        lines += [pal[u] for u in used]
        flat = np.vectorize(remap.get)(arr).reshape(-1)  # y, z, x order
        runs = []; prev = flat[0]; n = 0
        for v in flat:
            if v == prev: n += 1
            else: runs.append(f'{n}*{prev}' if n > 1 else str(prev)); prev = v; n = 1
        runs.append(f'{n}*{prev}' if n > 1 else str(prev))
        lines.append('data ' + ' '.join(runs))
        print(name, W, H, D, len(used), 'states', len(runs), 'runs')
    out.parent.mkdir(parents=True, exist_ok=True)
    with gzip.open(out, 'wt', encoding='utf-8') as f: f.write('\n'.join(lines) + '\n')
    print(out, out.stat().st_size)

if __name__ == '__main__':
    main()
