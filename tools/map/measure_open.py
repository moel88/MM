"""Measures how open a built map's ground is for fighting (map v2.0, 2.49.0: the seaside town was rebuilt to be wider
than Botand's first map, and stays so).

usage: python3 tools/map/measure_open.py WORLD_DIR NAME OUT.png X0,Z0,X1,Z1
Reads the world's region files directly (no game needed) and prints a line of JSON: the share of the land that is open
(nothing two or more blocks tall above the local ground: buildings, walls, fences, hedges, trunks and poles count;
grass, flowers, crops, leaves overhead and knee-high walls do not), the median and mean distance from an open column
to the nearest obstacle ("room"), the share of open ground with at least 4, 8 and 12 blocks of room, how cut up the
open ground is (open-to-obstacle edges per 100 open columns) and the largest connected area with at least 6 blocks of
room. OUT.png shows it: water blue, obstacles dark, open ground from orange (tight) to green (wide).

Botand's first map (Botand_Azure_Garden_1.20.1, box -256,-256,255,255) measures open 76.9 %, median room 4.1, room>=8
23.0 %, largest room>=6 area 10,752; the seaside town (box -200,-200,199,199) must not fall below it.
"""
import sys, struct, zlib, glob, os, json, re
import numpy as np
from scipy import ndimage

def nbt(buf, i, t):
    if t == 1: return struct.unpack_from('>b', buf, i)[0], i + 1
    if t == 2: return struct.unpack_from('>h', buf, i)[0], i + 2
    if t == 3: return struct.unpack_from('>i', buf, i)[0], i + 4
    if t == 4: return struct.unpack_from('>q', buf, i)[0], i + 8
    if t == 5: return struct.unpack_from('>f', buf, i)[0], i + 4
    if t == 6: return struct.unpack_from('>d', buf, i)[0], i + 8
    if t == 7:
        n = struct.unpack_from('>i', buf, i)[0]; return buf[i + 4:i + 4 + n], i + 4 + n
    if t == 8:
        n = struct.unpack_from('>H', buf, i)[0]; return buf[i + 2:i + 2 + n].decode('utf-8', 'replace'), i + 2 + n
    if t == 9:
        et = buf[i]; n = struct.unpack_from('>i', buf, i + 1)[0]; i += 5; out = []
        for _ in range(n):
            v, i = nbt(buf, i, et); out.append(v)
        return out, i
    if t == 10:
        out = {}
        while True:
            tt = buf[i]; i += 1
            if tt == 0: return out, i
            n = struct.unpack_from('>H', buf, i)[0]; name = buf[i + 2:i + 2 + n].decode('utf-8', 'replace'); i += 2 + n
            v, i = nbt(buf, i, tt); out[name] = v
    if t == 11:
        n = struct.unpack_from('>i', buf, i)[0]; return np.frombuffer(buf, '>i4', n, i + 4), i + 4 + 4 * n
    if t == 12:
        n = struct.unpack_from('>i', buf, i)[0]; return np.frombuffer(buf, '>i8', n, i + 4), i + 4 + 8 * n
    raise ValueError(t)

# blocks that do not stop a walker (or hang above one): air, water handled apart, plants, leaves and crowns, small things
PASS = re.compile(r'(^|:)(air|cave_air|void_air|grass|tall_grass|fern|large_fern|dead_bush|.*_sapling|dandelion|poppy|blue_orchid|allium|azure_bluet|.*_tulip|oxeye_daisy|cornflower|lily_of_the_valley|sunflower|lilac|rose_bush|peony|pink_petals|fallen_petals|vine|ivy|glow_lichen|moss_carpet|.*_carpet|snow|torch|wall_torch|.*_torch|.*_wall_torch|lantern|soul_lantern|.*_sign|.*_wall_sign|.*_hanging_sign|.*_banner|.*_wall_banner|.*_button|lever|rail|.*_rail|redstone_wire|tripwire|.*_pressure_plate|cobweb|seagrass|tall_seagrass|kelp|kelp_plant|sugar_cane|bamboo_sapling|sweet_berry_bush|.*_mushroom|.*leaves|canopy|cherry_blossom|pine_needles|hedge_none|light|structure_void|flower_pot|potted_.*|candle|.*_candle|wheat|carrots|potatoes|beetroots|farmland_none|scaffolding_none|ladder|chain|end_rod|lightning_rod_none|item_frame)$')
WATER = re.compile(r'(^|:)(water|bubble_column|lava)$')

def column_tops(world):
    tops = {}; water = {}
    for path in glob.glob(os.path.join(world, 'region', 'r.*.mca')):
        rx, rz = map(int, os.path.basename(path).split('.')[1:3])
        data = open(path, 'rb').read()
        for k in range(1024):
            off = int.from_bytes(data[k * 4:k * 4 + 3], 'big')
            if off == 0: continue
            ln = struct.unpack_from('>i', data, off * 4096)[0]; comp = data[off * 4096 + 4]
            raw = data[off * 4096 + 5:off * 4096 + 4 + ln]
            buf = zlib.decompress(raw) if comp == 2 else raw
            root, _ = nbt(buf, 3 + struct.unpack_from('>H', buf, 1)[0], 10)
            if root.get('Status', 'minecraft:full') not in ('minecraft:full', 'full'): continue
            cx, cz = root['xPos'], root['zPos']
            top = np.full((16, 16), -999, np.int32); wet = np.zeros((16, 16), bool)
            for sec in sorted(root.get('sections', []), key=lambda s: -s['Y']):
                bs = sec.get('block_states')
                if not bs: continue
                pal = bs['palette']; names = [p['Name'] for p in pal]
                hard = np.array([not PASS.search(n) and not WATER.search(n) for n in names])
                wetp = np.array([bool(WATER.search(n)) for n in names])
                if not hard.any() and not wetp.any(): continue
                if len(pal) == 1: idx = np.zeros(4096, np.int64)
                else:
                    b = max(4, int(np.ceil(np.log2(len(pal))))); epl = 64 // b
                    longs = bs['data'].astype(np.uint64)
                    idx = ((longs[:, None] >> (np.arange(epl, dtype=np.uint64) * np.uint64(b))) & np.uint64((1 << b) - 1)).reshape(-1)[:4096].astype(np.int64)
                idx = idx.reshape(16, 16, 16)  # y, z, x
                h = hard[idx]; w = wetp[idx]
                y0 = sec['Y'] * 16
                for ly in range(15, -1, -1):
                    m = (top == -999) & h[ly]
                    top[m] = y0 + ly
                    wet |= (top == -999) & w[ly]
            tops[(cx, cz)] = top; water[(cx, cz)] = wet
    return tops, water

def grid(tops, water, x0, z0, x1, z1):
    W, D = x1 - x0 + 1, z1 - z0 + 1
    H = np.full((D, W), -999, np.int32); Wet = np.ones((D, W), bool)
    for (cx, cz), t in tops.items():
        for lz in range(16):
            for lx in range(16):
                x, z = cx * 16 + lx, cz * 16 + lz
                if x0 <= x <= x1 and z0 <= z <= z1:
                    H[z - z0, x - x0] = t[lz, lx]; Wet[z - z0, x - x0] = water[(cx, cz)][lz, lx]
    return H, Wet

def measure(world, box, name, out_png):
    tops, water = column_tops(world)
    H, Wet = grid(tops, water, *box)
    land = (~Wet) & (H > -999)
    Hf = np.where(land, H, -64).astype(float)
    # the local ground: the surface with everything narrower than 25 blocks taken off (buildings, walls, trees, poles)
    G = ndimage.grey_opening(Hf, size=(25, 25))
    obstacle = land & (Hf - G >= 2)
    open_ = land & ~obstacle
    clear = ndimage.distance_transform_edt(open_)
    edges = (open_[:, :-1] & obstacle[:, 1:]).sum() + (obstacle[:, :-1] & open_[:, 1:]).sum() + (open_[:-1] & obstacle[1:]).sum() + (obstacle[:-1] & open_[1:]).sum()
    lab, n = ndimage.label(clear >= 6)
    largest = np.bincount(lab.ravel())[1:].max() if n else 0
    r = {'map': name, 'land': int(land.sum()), 'open_pct': round(100 * open_.sum() / land.sum(), 1),
         'obstacle_pct': round(100 * obstacle.sum() / land.sum(), 1),
         'median_room': round(float(np.median(clear[open_])), 1), 'mean_room': round(float(clear[open_].mean()), 1),
         'room_ge4_pct': round(100 * (clear >= 4).sum() / open_.sum(), 1), 'room_ge8_pct': round(100 * (clear >= 8).sum() / open_.sum(), 1),
         'room_ge12_pct': round(100 * (clear >= 12).sum() / open_.sum(), 1),
         'edges_per_100_open': round(100 * edges / open_.sum(), 1), 'largest_room6_area': int(largest)}
    # a picture: water blue, obstacles dark, open ground coloured by the room round it
    from PIL import Image
    img = np.zeros(H.shape + (3,), np.uint8)
    img[Wet | (H == -999)] = (40, 70, 120)
    img[obstacle] = (40, 40, 44)
    c = np.clip(clear / 16.0, 0, 1)
    col = np.stack([255 * (1 - c) + 40 * c, 120 + 120 * c, 60 + 40 * c], -1).astype(np.uint8)
    img[open_] = col[open_]
    Image.fromarray(img).resize((H.shape[1] * 2, H.shape[0] * 2), Image.NEAREST).save(out_png)
    return r

if __name__ == '__main__':
    world, name, out_png = sys.argv[1], sys.argv[2], sys.argv[3]
    box = tuple(map(int, sys.argv[4].split(',')))
    print(json.dumps(measure(world, box, name, out_png), ensure_ascii=False))
