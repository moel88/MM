"""Measures how open a built map's ground is for fighting (map v2.0, 2.49.0: the seaside town was rebuilt to be wider
than Botand's first map, and stays so).

usage: python3 tools/map/measure_open.py WORLD_DIR NAME OUT.png X0,Z0,X1,Z1
Reads the world's region files directly (no game needed) and prints a line of JSON: the share of the land that is open
(nothing two or more blocks tall above the local ground: buildings, walls, fences, hedges, trunks and poles count;
grass, flowers, crops, leaves overhead and knee-high walls do not; nor, since map v3.0, anything more than REACH blocks
over the local ground with open air under it, such as the world tree's limbs and the pieces of ground floating over the
rift: they are over a walker's head, not in the way), the median and mean distance from an open column
to the nearest obstacle ("room"), the share of open ground with at least 4, 8 and 12 blocks of room, how cut up the
open ground is (open-to-obstacle edges per 100 open columns) and the largest connected area with at least 6 blocks of
room. OUT.png shows it: water blue, obstacles dark, open ground from orange (tight) to green (wide).

Botand's first map (Botand_Azure_Garden_1.20.1, box -256,-256,255,255) measures open 78.0 %, median room 4.2, room>=8
23.4 %, largest room>=6 area 11,948 (before map v3.0's overhead rule: 76.9 %, 4.1, 23.0 %, 10,752); a new map (box
-200,-200,199,199) must not fall below it. The seaside town v2.0 measures 80.0 %, 5.0, 26.3 %, 42,052; the hourglass
island v3.0 at 400 x 400 measured 89.9 %, 5.0, 25.4 %, 32,458 and at 1000 x 1000 (box -500,-500,499,499) 89.1 %, 6.0,
36.6 %, 240,551.
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
REACH = 8  # blocks over the local ground above which a block with air under it is overhead
Y0, YN = -64, 384
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
            vol = np.zeros((YN, 16, 16), bool); wvol = np.zeros((YN, 16, 16), bool)
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
                if 0 <= y0 - Y0 <= YN - 16: vol[y0 - Y0:y0 - Y0 + 16] = h; wvol[y0 - Y0:y0 - Y0 + 16] = w
                for ly in range(15, -1, -1):
                    m = (top == -999) & h[ly]
                    top[m] = y0 + ly
                    wet |= (top == -999) & w[ly]
            tops[(cx, cz)] = (top, vol, wvol); water[(cx, cz)] = wet
    return tops, water

def grid(tops, water, x0, z0, x1, z1):
    W, D = x1 - x0 + 1, z1 - z0 + 1
    H = np.full((D, W), -999, np.int32); Wet = np.ones((D, W), bool); V = np.zeros((YN, D, W), bool); WV = np.zeros((YN, D, W), bool)
    for (cx, cz), (t, vol, wvol) in tops.items():
        for lz in range(16):
            for lx in range(16):
                x, z = cx * 16 + lx, cz * 16 + lz
                if x0 <= x <= x1 and z0 <= z <= z1:
                    H[z - z0, x - x0] = t[lz, lx]; Wet[z - z0, x - x0] = water[(cx, cz)][lz, lx]
                    V[:, z - z0, x - x0] = vol[:, lz, lx]; WV[:, z - z0, x - x0] = wvol[:, lz, lx]
    return H, Wet, V, WV

def below(V, cap):
    """Each column's highest hard block at or under cap (-999 if none)."""
    ys = np.arange(Y0, Y0 + YN)[:, None, None]
    return np.where(V & (ys <= cap[None]), ys, -999).max(0)

def measure(world, box, name, out_png):
    tops, water = column_tops(world)
    H, Wet, V, WV = grid(tops, water, *box)
    top = np.full(H.shape, 10 ** 6, np.int32)
    solid, wet = H, below(WV, top)
    # the local ground: the surface (land or water) with everything narrower than 25 blocks taken off (buildings, walls,
    # trees, poles); then what is in a walker's way: the column up to REACH over that ground (what floats higher with air
    # under it is overhead, while a wall or a building reaching higher is still there below it), and the ground found
    # again without the rest; water under something overhead is still water
    for _ in range(3):
        surf = np.maximum(solid, wet)
        G = ndimage.grey_opening(np.where(surf > -999, surf, -64).astype(float), size=(25, 25))
        cap = (G + REACH).astype(np.int32)
        solid, wet = below(V, cap), below(WV, cap)
    land = (solid > -999) & (solid > wet)
    Wet = ~land
    Hf = np.where(land, solid, -64).astype(float)
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
