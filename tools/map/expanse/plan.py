"""Plans Botand Azure Garden v1.5 'Expanse': Azure Garden's island 2.5 times over in a 1000 x 1000 map with every
building, road, tree and lamp gone, flat grass at the town's height (y 80), and round it the Cliffside wall in grey
natural stone.

The wall is Cliffside's (map v1.4, tools/map/cliffside/cliffs.py) without the houses it hugged, so it now runs round
the whole island at the same distance from the sea: a thin wall about ten blocks thick standing 32 to 46 blocks over
the grass, its face to the grass a sheer ledge at about three quarters of that and the crest a few blocks behind; behind
it the rock steps down over six blocks to a slope of boulders that runs down to the waterline, and boulders stand in the
sea along its foot. The rock is stone, andesite, cobblestone and tuff with a little mossy cobblestone (more of it by
the water), big piled blocks on the tall faces as on Cliffside.

    python3 plan.py OUT_DIR       # OUT_DIR/volume.bin, plan.json, datapack/, preview.png (not a game screen)

The island's outline is Azure Garden v1.3's coast (azure_outline.png, its land in white; x -176..175, z -192..191),
read as a smoothed distance field 2.5 times over so the straight shores stay straight. run_expanse.py writes the plan
into a fresh world on a real server (Expanse.java). The world is a flat ocean as Azure Garden's: bedrock, stone to 42,
gravel to 46, sand to 48, water 49..62; the plan holds every block from 49 up.
"""
import json, math, random, sys
from pathlib import Path
import numpy as np
from scipy import ndimage
from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / 'cliffside'))
from cliffs import Boulder, poisson, smoothstep, RISE, FRONT, THICK, DROP, SLOPE   # Cliffside's wall, as it was

X0 = Z0 = -500
W = D = 1000                 # the map: x and z -500..499
SCALE = 2.5                  # Azure Garden's island (x -167..166, z -189..190) 2.5 times over
OUTLINE = HERE / 'azure_outline.png'
OX, OZ = -176, -192          # the outline's first column and row
SEA_FLOOR, SEA, GROUND = 49, 62, 80
Y0 = SEA_FLOOR               # the plan's lowest layer; below it the flat world's own layers
RIM = 34.0                   # the wall and the slope behind it, from the waterline to the grass: its ten blocks of
                             # crest, six of steps and the slope's eighteen up from the water (three more or less)
SEED = 1931

# the rock in grey: the kind of a boulder (its top block or two, and a short side), the tall faces' piled blocks, and
# boulders by the water (top at most 66) with more moss; weights in percent
ROCK = [('stone', 32), ('andesite', 26), ('cobblestone', 14), ('tuff', 14), ('mossy_cobblestone', 8)]
FACE = [('stone', 36), ('andesite', 28), ('cobblestone', 12), ('tuff', 16), ('mossy_cobblestone', 8)]
WET = [('stone', 26), ('andesite', 22), ('mossy_cobblestone', 26), ('cobblestone', 12), ('tuff', 14)]
BASE = 'stone'               # rock nobody sees (inside the wall, under the slope)
# the palette of the volume (index = byte); air first
PALETTE = ['air', 'water', 'stone', 'dirt', 'grass_block', 'andesite', 'cobblestone', 'tuff', 'mossy_cobblestone']
P = {n: i for i, n in enumerate(PALETTE)}


def noise(rng, sigma):
    """Smooth noise over the map, about -1..1 (one standard deviation, clipped)."""
    n = ndimage.gaussian_filter(rng.standard_normal((D, W)), sigma, mode='wrap')
    return np.clip(n / max(float(n.std()), 1e-9), -1.0, 1.0)


def outline():
    """Azure Garden's coast as a signed distance in blocks over the map (+ on land): the v1.3 island's distance field,
    smoothed a little and read 2.5 times over, so its straight shores stay straight and its corners round by a few
    blocks instead of stepping in stairs of two or three."""
    land = np.array(Image.open(OUTLINE)).astype(bool)
    sd = np.where(land, ndimage.distance_transform_edt(land) - 0.5, 0.5 - ndimage.distance_transform_edt(~land))
    sd = ndimage.gaussian_filter(sd, 1.0)
    zz, xx = np.mgrid[Z0:Z0 + D, X0:X0 + W]
    rows = (zz + 0.5) / SCALE - OZ - 0.5
    cols = (xx + 0.5) / SCALE - OX - 0.5
    return ndimage.map_coordinates(sd, [rows, cols], order=1, mode='nearest') * SCALE


def biggest(m):
    lab, n = ndimage.label(m)
    if n <= 1: return m
    return lab == 1 + int(np.argmax(ndimage.sum(m, lab, range(1, n + 1))))


def pick(rng, table):
    total = sum(wt for _, wt in table); r = rng.uniform(0, total)
    for name, wt in table:
        r -= wt
        if r <= 0: return P[name]
    return P[table[-1][0]]


def mix(a):
    """A 64-bit integer hash (splitmix64's finaliser) of an int array."""
    a = a.astype(np.uint64)
    a ^= a >> np.uint64(30); a *= np.uint64(0xbf58476d1ce4e5b9)
    a ^= a >> np.uint64(27); a *= np.uint64(0x94d049bb133111eb)
    a ^= a >> np.uint64(31)
    return a


FACE_IDS = np.array([P[n] for n, _ in FACE], np.uint8)
FACE_CUM = np.cumsum([wt for _, wt in FACE]) / sum(wt for _, wt in FACE)


def stacked(x, y, z, seed=SEED):
    """The kind of a block in a tall rock face (int arrays, broadcast): the face is cut into strips four blocks wide,
    each strip into blocks five high from its own starting height, one kind each, so it reads as big blocks of rock
    piled up, not as stripes or a pattern (Cliffside's, in grey)."""
    strip = ((x // 4) * 92821 + (z // 4) * 68917 + seed) & 0xFFFFFFFF
    off = strip % 5
    h = mix(strip * 31 + ((y + off) // 5) * 1237)
    u = (h >> np.uint64(11)).astype(np.float64) / float(1 << 53)
    return FACE_IDS[np.minimum(np.searchsorted(FACE_CUM, u, side='right'), len(FACE_IDS) - 1)]


class Island:
    def __init__(self, seed=SEED):
        rng = np.random.default_rng(seed)
        self.sd = outline()
        land = ndimage.binary_fill_holes(biggest(self.sd > 0))
        sea = ~land
        d_sea = ndimage.distance_transform_edt(np.pad(land, 1))[1:-1, 1:-1]     # beyond the map: sea
        rim_w = RIM + 3.0 * noise(rng, 24.0)
        field = land & (d_sea > rim_w)
        field = ndimage.binary_fill_holes(biggest(ndimage.binary_opening(field, np.ones((3, 3)), iterations=2)))
        self.land, self.sea, self.field, self.d_sea = land, sea, field, d_sea
        self.cliff = land & ~field
        self.d_in = ndimage.distance_transform_edt(self.cliff | sea)        # rock columns: distance to the grass
        self.d_land = ndimage.distance_transform_edt(sea)                   # sea columns: distance to the island
        self.d_field = ndimage.distance_transform_edt(field)                # grass columns: distance to the wall
        self.thick = THICK + 2.0 * noise(rng, 6.0)                          # never so thin the crest dips

    def at(self, m, x, z):
        return m[int(math.floor(z)) - Z0, int(math.floor(x)) - X0]

    def rock(self, seed=SEED):
        """Every rock column's top and kind (H = -1 where there is none): the wall, the slope, the boulders."""
        rng = random.Random(seed)
        cliff, sea, d_in, d_sea, d_land = self.cliff, self.sea, self.d_in, self.d_sea, self.d_land
        rise = np.clip(30.0 + 0.35 * (d_in + d_sea), *RISE)
        front = FRONT + (1.0 - FRONT) * smoothstep(1.0, 5.0, d_in)
        slope = np.minimum(SLOPE[1], 62.5 + SLOPE[0] * d_sea)
        crest = GROUND + rise * front
        drop = smoothstep(self.thick, self.thick + DROP, d_in)
        hb = np.where(cliff, crest + (slope - crest) * drop, -1.0)
        wall = cliff & (d_in <= self.thick + DROP / 2)
        scree = cliff & ~wall
        near = ndimage.distance_transform_edt(~cliff, return_distances=False, return_indices=True)
        H = np.where(cliff, np.floor(hb).astype(np.int32), -1)
        kind = np.where(cliff, P[BASE], 0).astype(np.uint8)
        self.wall, self.scree = wall, scree

        def offer(x, z, top, k, only):
            zi, xi = z - Z0, x - X0
            if not (0 <= zi < D and 0 <= xi < W): return
            if not (only[zi, xi] or (sea[zi, xi] and d_land[zi, xi] <= 3.6)): return
            top = int(math.floor(top))
            if top > H[zi, xi]:
                H[zi, xi] = top; kind[zi, xi] = k

        def cells(m):
            zs, xs = np.nonzero(m)
            return [(x + X0 + rng.random(), z + Z0 + rng.random()) for z, x in zip(zs.tolist(), xs.tolist())]

        def tilted(rng, most):
            tilt = math.radians(rng.uniform(0, most)); dirn = rng.uniform(0, 2 * math.pi)
            return math.tan(tilt) * math.cos(dirn), math.tan(tilt) * math.sin(dirn)

        # the wall: boulders all over it make a ragged crest and a ragged ledge, never out onto the grass
        for x, z in poisson(cells(wall), lambda x, z: 4.2, rng):
            a, b = rng.uniform(2.2, 4.2), rng.uniform(2.2, 4.2)
            gu, gv = tilted(rng, 20)
            top = float(self.at(hb, x, z)) + rng.uniform(0.5, 6.5)
            bl = Boulder(x, z, a, b, rng.uniform(0, math.pi / 2), gu, gv, top, pick(rng, ROCK))
            for cx, cz, t in bl.columns():
                offer(cx, cz, t, bl.kind, wall)
        # the slope behind it: boulders a little over it, lower and lower down to the waterline
        for x, z in poisson(cells(scree), lambda x, z: 3.6, rng):
            a, b = rng.uniform(1.8, 3.6), rng.uniform(1.8, 3.6)
            gu, gv = tilted(rng, 16)
            top = float(self.at(hb, x, z)) + rng.uniform(-0.5, 3.5)
            bl = Boulder(x, z, a, b, rng.uniform(0, math.pi / 2), gu, gv, top, pick(rng, WET if top <= 66 else ROCK))
            for cx, cz, t in bl.columns():
                offer(cx, cz, t, bl.kind, scree)
        # boulders in the sea along the foot of the slope: at the waterline, a little up, a few part way up
        for x, z in poisson(cells(sea & (d_land >= 0.9) & (d_land <= 2.8)), lambda x, z: 3.8, rng):
            zi, xi = int(z) - Z0, int(x) - X0
            nzi, nxi = near[0][zi, xi], near[1][zi, xi]
            crest_here = max(int(H[nzi, nxi]), 70)
            r = rng.random()
            top = rng.uniform(62.5, 66) if r < 0.45 else rng.uniform(66, 82) if r < 0.8 else rng.uniform(82, crest_here - 8)
            top = min(top, max(int(H[nzi, nxi]), 63) + 1.5)      # never over the rock behind it
            a, b = rng.uniform(1.4, 2.8), rng.uniform(1.4, 2.8)
            gu, gv = tilted(rng, 14)
            bl = Boulder(x, z, a, b, rng.uniform(0, math.pi / 2), gu, gv, top, pick(rng, WET if top <= 66 else ROCK))
            for cx, cz, t in bl.columns():
                offer(cx, cz, t, bl.kind, cliff)
        # a few stray rocks a little way out (Cliffside's fourteen, for a shore 2.5 times as long)
        far = sea & (d_land > 4.5) & (d_land < 9)
        fz, fx = np.nonzero(far)
        for i in rng.sample(range(len(fz)), min(len(fz), 35)):
            x, z = fx[i] + X0 + 0.5, fz[i] + Z0 + 0.5
            bl = Boulder(x, z, rng.uniform(1.2, 2.4), rng.uniform(1.2, 2.4), rng.uniform(0, math.pi / 2), 0.1, -0.08,
                         rng.uniform(62.2, 64.6), pick(rng, WET))
            for cx, cz, top in bl.columns():
                zi, xi = cz - Z0, cx - X0
                if 0 <= zi < D and 0 <= xi < W and sea[zi, xi] and top >= 62:
                    H[zi, xi] = max(H[zi, xi], int(top)); kind[zi, xi] = bl.kind
        self.H, self.kind = H, kind
        return H, kind

    def volume(self):
        """Every block of the map from the sea floor up, as palette bytes [z][x][y]."""
        H, kind, land, sea, field = self.H, self.kind, self.land, self.sea, self.field
        ny = max(int(H.max()), GROUND) - Y0 + 1
        vol = np.zeros((D, W, ny), np.uint8)
        vol[sea, :SEA - Y0 + 1] = P['water']
        vol[field, :77 - Y0] = P['stone']
        vol[field, 77 - Y0:GROUND - Y0] = P['dirt']
        vol[field, GROUND - Y0] = P['grass_block']
        # a rock column shows its side from its lowest neighbour up; the rest of it is base rock
        lowest = np.where(H >= 0, H, np.where(land, GROUND, SEA))
        pad = np.pad(lowest, 1, constant_values=SEA)
        nb = np.minimum.reduce([pad[:-2, 1:-1], pad[2:, 1:-1], pad[1:-1, :-2], pad[1:-1, 2:]])
        zs, xs = np.nonzero(H >= 0)
        Y = np.arange(Y0, Y0 + ny, dtype=np.int64)[None, :]
        for s in range(0, len(zs), 16384):
            z_, x_ = zs[s:s + 16384], xs[s:s + 16384]
            top = H[z_, x_].astype(np.int64)[:, None]; k = kind[z_, x_][:, None]
            X = (x_ + X0).astype(np.int64)[:, None]; Z = (z_ + Z0).astype(np.int64)[:, None]
            cap = top - (X * 5 + Z * 11) % 2                # the boulder's own kind on its top block or two
            onland = land[z_, x_][:, None]
            low = np.minimum(top, nb[z_, x_].astype(np.int64)[:, None]) - 1
            start = np.where(low >= GROUND - 1, GROUND, np.maximum(SEA_FLOOR, low))
            seen = np.maximum(start, low)
            tall = top - seen >= 4
            st = stacked(X, Y, Z)
            on_land = np.where(Y < seen, P[BASE], np.where((Y >= cap) | ~tall, k, st))
            # the shore's face, clad in big blocks under the waterline as over it
            clad = (self.d_sea[z_, x_] <= 1.6)[:, None] & (Y >= SEA_FLOOR + 5) & (Y < np.minimum(GROUND, top))
            on_land = np.where(clad, st, on_land)
            in_sea = np.where((Y >= cap) | (top - SEA_FLOOR < 16), k, st)    # a boulder in the sea, from the floor up
            col = vol[z_, x_]
            vol[z_, x_] = np.where(Y <= top, np.where(onland, on_land, in_sea), col).astype(np.uint8)
        return vol


def places(isl):
    """Where the mod's things stand on the bare island: the spawn in the middle of the grass facing north, the two
    merchants ten blocks north of it facing it, the field supply spots on a grid 110 blocks apart over the grass
    (as on the 1000-block hourglass map), none within 24 blocks of the wall or 40 of the spawn."""
    zs, xs = np.nonzero(isl.field)
    sx, sz = int(round(xs.mean())) + X0, int(round(zs.mean())) + Z0
    assert isl.d_field[sz - Z0, sx - X0] > 60, 'the spawn is not well inside the grass'
    seller, buyer = (sx - 3, sz - 10), (sx + 3, sz - 10)
    loot = []
    for j in range(-6, 7):
        for i in range(-6, 7):
            x, z = sx + 55 + 110 * i, sz + 55 + 110 * j
            if not (X0 <= x < X0 + W and Z0 <= z < Z0 + D): continue
            if isl.d_field[z - Z0, x - X0] < 24 or math.hypot(x - sx, z - sz) < 40: continue
            loot.append([x, GROUND + 1, z])
    assert len(loot) <= 64, 'more supply spots than the mod takes'
    # a view over the whole island from the south, under the clouds (y 192)
    south = int(zs.max()) + Z0
    overview = [sx, 185, south - 40, 180, 30]
    return {'spawn': [sx, GROUND + 1, sz, 180], 'seller': seller, 'buyer': buyer, 'loot': loot, 'overview': overview}


def datapack(where):
    sx, sy, sz, yaw = where['spawn']
    (ax, az), (bx, bz) = where['seller'], where['buyer']
    ov = where['overview']
    definition = {
        'format': 1, 'id': 'botand:expanse', 'name': '보탠드 넓은 섬 (Azure Garden · Expanse)',
        'spawn': [sx, sy, sz, yaw],
        'merchants': {'seller': {'pos': [ax + 0.5, sy, az + 0.5], 'yaw': 0},
                      'buyer': {'pos': [bx + 0.5, sy, bz + 0.5], 'yaw': 0}},
        'loot': where['loot'], 'places': {'spawn': [sx, sy, sz], 'overview': ov[:3]},
        'wires': [], 'backdrop': [], 'sea': 62.4}
    return {
        'pack.mcmeta': json.dumps({'pack': {'pack_format': 15, 'description': 'Botand: 넓은 섬 (보탠드 전용 맵)'}}, ensure_ascii=False, indent=2) + '\n',
        'data/botand/map/definition.json': json.dumps(definition, ensure_ascii=False, indent=1) + '\n',
        'data/botand/functions/goto/spawn.mcfunction': f'tp @s {sx}.5 {sy} {sz}.5 {yaw} 0\n',
        'data/botand/functions/goto/overview.mcfunction': 'tp @s {}.5 {} {}.5 {} {}\n'.format(*ov),
    }


COLOURS = {'air': (0, 0, 0), 'water': (40, 110, 160), 'stone': (125, 125, 125), 'dirt': (120, 85, 60),
           'grass_block': (96, 150, 70), 'andesite': (136, 136, 137), 'cobblestone': (112, 112, 112), 'tuff': (106, 108, 98),
           'mossy_cobblestone': (100, 118, 90)}


def preview(vol, where, scale=1):
    """A top-down picture of the plan in block colours, shaded by height (not a game screen)."""
    solid = vol != 0
    ny = vol.shape[2]
    top_i = ny - 1 - np.argmax(solid[:, :, ::-1], axis=2)
    ids = np.take_along_axis(vol, top_i[:, :, None], 2)[:, :, 0]
    top = top_i + Y0
    lut = np.array([COLOURS[n] for n in PALETTE], float)
    rgb = lut[ids]
    shade = np.clip(0.8 + (top - GROUND) * 0.012, 0.6, 1.35)[..., None]
    nw = np.roll(np.roll(top, 1, 0), 1, 1)
    rgb = np.clip(rgb * shade * np.where(top > nw, 1.12, np.where(top < nw, 0.82, 1.0))[..., None], 0, 255).astype(np.uint8)
    img = Image.fromarray(rgb).resize((W * scale, D * scale), Image.NEAREST)
    d = ImageDraw.Draw(img)
    try: font = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', 12 * scale)
    except Exception: font = ImageFont.load_default()
    for v in range(-500, 501, 100):
        p = (v - X0) * scale
        d.line([(p, 0), (p, img.height)], fill=(255, 255, 0), width=1)
        d.line([(0, p), (img.width, p)], fill=(255, 255, 0), width=1)
        d.text((p + 3, 3), str(v), fill=(255, 255, 0), font=font)
        d.text((3, p + 3), str(v), fill=(255, 255, 0), font=font)
    for x, _, z in where['loot']:
        d.ellipse([((x - X0) - 4) * scale, ((z - Z0) - 4) * scale, ((x - X0) + 4) * scale, ((z - Z0) + 4) * scale], fill=(255, 150, 0))
    for x, z in (where['seller'], where['buyer']):
        d.rectangle([((x - X0) - 3) * scale, ((z - Z0) - 3) * scale, ((x - X0) + 3) * scale, ((z - Z0) + 3) * scale], fill=(80, 200, 255))
    sx, _, sz, _ = where['spawn']
    d.ellipse([((sx - X0) - 6) * scale, ((sz - Z0) - 6) * scale, ((sx - X0) + 6) * scale, ((sz - Z0) + 6) * scale], fill=(230, 40, 40))
    d.text((10, img.height - 22 * scale), 'plan preview, not a game screen (red spawn, blue merchants, orange supply spots)',
           fill=(255, 255, 255), font=font)
    return img


def main(out):
    out = Path(out); out.mkdir(parents=True, exist_ok=True)
    isl = Island()
    H, kind = isl.rock()
    vol = isl.volume()
    where = places(isl)
    vol.tofile(out / 'volume.bin')
    for name, text in datapack(where).items():
        f = out / 'datapack' / name; f.parent.mkdir(parents=True, exist_ok=True); f.write_text(text, encoding='utf-8')
    rock_top = H[isl.land & (H >= 0)]
    stats = {
        'land_columns': int(isl.land.sum()), 'grass_columns': int(isl.field.sum()), 'rock_columns': int(isl.cliff.sum()),
        'wall_columns': int(isl.wall.sum()), 'slope_columns': int(isl.scree.sum()),
        'sea_rock_columns': int((isl.sea & (H >= 0)).sum()),
        'land_x': [int(np.nonzero(isl.land.any(0))[0].min()) + X0, int(np.nonzero(isl.land.any(0))[0].max()) + X0],
        'land_z': [int(np.nonzero(isl.land.any(1))[0].min()) + Z0, int(np.nonzero(isl.land.any(1))[0].max()) + Z0],
        'grass_x': [int(np.nonzero(isl.field.any(0))[0].min()) + X0, int(np.nonzero(isl.field.any(0))[0].max()) + X0],
        'grass_z': [int(np.nonzero(isl.field.any(1))[0].min()) + Z0, int(np.nonzero(isl.field.any(1))[0].max()) + Z0],
        'wall_top': [int(np.percentile(H[isl.wall], 5)), int(np.median(H[isl.wall])), int(H[isl.wall].max())],
        'rock_top_max': int(rock_top.max()), 'supply_spots': len(where['loot'])}
    plan = {'x0': X0, 'z0': Z0, 'w': W, 'd': D, 'y0': Y0, 'h': int(vol.shape[2]), 'sea': SEA,
            'palette': ['minecraft:' + n for n in PALETTE], 'spawn': where['spawn'], 'stats': stats}
    (out / 'plan.json').write_text(json.dumps(plan, indent=1) + '\n')
    preview(vol, where).save(out / 'preview.png')
    print(json.dumps(stats))
    return plan


if __name__ == '__main__':
    main(sys.argv[1])
