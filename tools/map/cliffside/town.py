"""The town inside the rock rim (map v1.4): the Roblox map's grey town, dark green parks and plain battle field.

- trees: pink cherry and flowering crowns turn dark green everywhere, as all the reference's trees are
- parks: the west park and the south garden (formal gardens with fountains in v1.3) become dense groves of
  cube-crowned trees on moss and grass, their trunks about nine blocks apart under crowns seven to nine wide that start
  four blocks up, so the groves look dense and dark from above and stay open to walk and fight in
- forests: the north-west and south forests get the same trees in their gaps
- pavement: the town's lawns become grey pavement (polished andesite, as its sidewalks); a tree keeps a bed of grass
- battle field: one plain light grey floor (smooth stone) inside its dark frame: no red border, stripes or emblem
- the cave is clad in the rim's brown rock
- Anubis quest (reference: blue): the kiosk south of the spawn turns sandstone, lapis and gold beside a black
  jackal-headed statue
- skin remover (reference: grey): the little shop at the west road's end gets grey stripes, a washing cauldron,
  a grindstone and two mannequins, one dressed and one not
- bushes: square dark bushes at the battle field's corners, as on the reference
"""
import random
import numpy as np
from scipy import ndimage
from zones import PARK_W, GARDEN_S, RUINS, ARENA, short
import cliffs

DARK = 'minecraft:spruce_leaves[persistent=true]'
MID = 'minecraft:azalea_leaves[persistent=true]'
PAVE = 'minecraft:polished_andesite'
LOG = 'minecraft:dark_oak_log[axis=y]'
PLANT = ('fern', 'grass', 'daisy', 'orchid', 'valley', 'cornflower', 'flower', 'tulip', 'poppy', 'dandelion', 'allium',
         'bluet', 'petals', 'bush', 'sapling', 'lilac', 'peony', 'rose')
CAVE = (-2, 24, 132, 164)                    # the forest's cave: a stone hill with a way in
ANUBIS = (40, -6)                            # the statue's centre, east of the kiosk (reference: blue box)
KIOSK = (23, 35, -11, -2)
SKIN = (-130, -123, -30, -16)                # the little shop at the west road's end (reference: grey box)


def noise(x, y, z, k=1):
    """A repeatable value in [0, 1) for a cell (chunked by k)."""
    h = ((x // k) * 73856093) ^ ((y // k) * 19349663) ^ ((z // k) * 83492791)
    return ((h * 2654435761) & 0xFFFFFFFF) / 2 ** 32


def is_plant(state):
    n = short(state.split('[')[0])
    return any(k in n for k in PLANT)


def rework(p, Z, forest, stats):
    rng = random.Random(4242)
    stats['recoloured'] = recolour(p, forest)
    stats['grove_trees'] = grove(p, Z, PARK_W, rng) + grove(p, Z, GARDEN_S, rng)
    stats['forest_trees'] = infill(p, Z, forest, rng)
    stats['paved'] = pave(p, Z, forest)
    battlefield(p)
    stats['cave_cells'] = cave(p)
    anubis(p)
    skin_remover(p)
    stats['bushes'] = bushes(p, Z, rng)
    return p


# --- trees ---
def recolour(p, forest):
    n = 0
    for t in forest:
        for x, y, z in t['voxels']:
            nm = short(p.state(x, y, z).split('[')[0])
            if nm in ('cherry_leaves', 'flowering_azalea_leaves'):
                p.put(x, y, z, DARK if noise(x, y, z, 2) < 0.68 else MID); n += 1
    return n


def cube_tree(p, x, z, rng, ground=80):
    """A tree as the reference draws them: a dark trunk and a crown of leaf cubes, the lowest four blocks up."""
    h = rng.choice([5, 5, 6, 6, 7])
    for y in range(ground + 1, ground + h + 1):
        p.put(x, y, z, LOG)
    s = rng.choice([3, 3, 4]); hh = rng.choice([3, 4, 4]); y0 = ground + h - 1
    boxes = [(x - s, y0, z - s, x + s, y0 + hh - 1, z + s)]
    s2 = max(1, s - 1); ox, oz = rng.randint(-1, 1), rng.randint(-1, 1)
    boxes.append((x + ox - s2, y0 + hh, z + oz - s2, x + ox + s2, y0 + hh + rng.choice([0, 1]), z + oz + s2))
    if rng.random() < 0.5:
        dx, dz = rng.choice([(1, 0), (-1, 0), (0, 1), (0, -1)])
        cx, cz = x + dx * (s + 1), z + dz * (s + 1)
        boxes.append((cx - 1, y0 + 1, cz - 1, cx + 1, y0 + hh - 1, cz + 1))
    for bx0, by0, bz0, bx1, by1, bz1 in boxes:
        for yy in range(by0, by1 + 1):
            for zz in range(bz0, bz1 + 1):
                for xx in range(bx0, bx1 + 1):
                    if (xx, zz) == (x, z) and yy <= ground + h: continue
                    if xx in (bx0, bx1) and zz in (bz0, bz1) and rng.random() < 0.5: continue
                    if p.name(xx, yy, zz) != 'minecraft:air': continue
                    p.put(xx, yy, zz, MID if yy == by1 and noise(xx, yy, zz) < 0.5 else DARK)
    return h


def grove(p, Z, rect, rng):
    """A formal garden cleared and planted with cube trees on moss and grass."""
    x0, x1, z0, z1 = rect
    for z in range(z0, z1 + 1):
        for x in range(x0, x1 + 1):
            for y in range(81, 100):     # (a teleport's spot keeps its air: the plan refuses protected cells)
                if p.name(x, y, z) != 'minecraft:air':
                    p.put(x, y, z, 'air')
            r = noise(x, 0, z, 3)
            p.put(x, 80, z, 'moss_block' if r < 0.55 else 'grass_block')
            if short(p.name(x, 79, z)) not in ('dirt', 'grass_block'):
                p.put(x, 79, z, 'dirt')
            if r > 0.9 and noise(x, 1, z) < 0.3:
                p.put(x, 81, z, 'fern')
    pts = cliffs.poisson([(x + rng.random(), z + rng.random()) for x in range(x0 + 1, x1) for z in range(z0 + 1, z1)],
                         lambda x, z: 8.6, rng)
    n = 0
    for fx, fz in pts:
        x, z = int(fx), int(fz)
        if any((x + dx, 81, z + dz) in p.protect for dx in range(-2, 3) for dz in range(-2, 3)): continue
        cube_tree(p, x, z, rng); n += 1
    return n


def infill(p, Z, forest, rng):
    """Cube trees in the forests' gaps: on open grass, five or more blocks from a trunk and three from anything built."""
    w = Z.w
    zone = (Z.forest_nw | Z.forest_s) & ~Z.band
    zone &= ~Z.rect(CAVE[0] - 3, CAVE[1] + 3, CAVE[2] - 3, CAVE[3] + 3) & ~Z.disc(RUINS[0], RUINS[1], RUINS[2] + 3)
    zone &= ~Z.rect(72, 95, 118, 142)   # the boss spawn
    vol = w.v[81 - w.y0:96 - w.y0]
    tall = np.array([not (n.endswith('air') or is_plant(n)) for n in w.names])
    busy = ndimage.binary_dilation(tall[vol].any(0), iterations=3)
    ok = zone & Z.grass & ~busy
    trunks = [(x + 0.5, z + 0.5) for t in forest for x, z in t['trunks'] if p.name(x, 81, z) != 'minecraft:air']
    cand = [(x + rng.random(), z + rng.random()) for x, z in Z.cells(ok)]
    pts = cliffs.poisson(cand, lambda x, z: 6.8, random.Random(7), fixed=trunks)
    n = 0
    for fx, fz in pts:
        x, z = int(fx), int(fz)
        if not Z.at(ok, x, z): continue
        if any((x + dx, 81, z + dz) in p.protect for dx in range(-2, 3) for dz in range(-2, 3)): continue
        if short(p.name(x, 80, z)) == 'dirt_path': continue
        cube_tree(p, x, z, rng); n += 1
    return n


# --- ground ---
def pave(p, Z, forest):
    """The town's lawns (not the parks, forests or belt) become grey pavement; a kept tree keeps a bed of grass."""
    w = Z.w
    lawn = Z.town & Z.grass & ~(Z.forest_nw | Z.forest_s | Z.park_w | Z.garden_s)
    beds = np.zeros_like(lawn)
    for t in forest:
        for x, z in t['trunks']:
            if Z.at(lawn, x, z) and p.name(x, 81, z) != 'minecraft:air':
                beds |= Z.rect(x - 1, x + 1, z - 1, z + 1)
    n = 0
    for x, z in Z.cells(lawn & ~beds):
        if p.name(x, 80, z) != 'minecraft:grass_block': continue
        p.put(x, 80, z, PAVE); n += 1
        for y in (81, 82):
            s = p.state(x, y, z)
            if is_plant(s): p.put(x, y, z, 'air')
    return n


def battlefield(p):
    """One plain light grey floor inside the dark frame."""
    x0, x1, z0, z1 = ARENA
    for z in range(z0 + 1, z1):
        for x in range(x0 + 1, x1):
            p.put(x, 80, z, 'smooth_stone')


def cave(p):
    """The cave's grey stone hill clad in the rim's brown rock (moss on top stays)."""
    x0, x1, z0, z1 = CAVE
    n = 0
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            for y in range(80, 100):
                nm = short(p.name(x, y, z))
                if nm in ('stone', 'andesite', 'calcite', 'stone_bricks', 'cobblestone', 'tuff'):
                    p.put(x, y, z, cliffs.pick(random.Random(int(noise(x, y, z, 4) * 2 ** 31))))
                    n += 1
    return n


# --- places ---
def anubis(p):
    """The kiosk becomes a sandstone stall in lapis and gold, with a black jackal-headed statue at its east side."""
    x0, x1, z0, z1 = KIOSK
    swap = {'quartz_bricks': 'cut_sandstone', 'quartz_pillar': 'chiseled_sandstone', 'smooth_quartz': 'smooth_sandstone',
            'smooth_quartz_slab': 'smooth_sandstone_slab', 'gray_concrete': 'cut_sandstone', 'blue_concrete': 'lapis_block',
            'calcite': 'smooth_sandstone'}
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            for y in range(81, 90):
                s = p.state(x, y, z); nm = short(s.split('[')[0])
                if nm in swap:
                    # a slab keeps its half; a pillar's axis has nowhere to go
                    props = s[s.index('['):] if '[' in s and nm.endswith('_slab') else ''
                    p.put(x, y, z, swap[nm] + props)
                elif nm in ('light_blue_wool', 'white_wool'):
                    p.put(x, y, z, 'blue_wool' if (x + z) % 2 else 'yellow_wool')
    # corners of the roof frame in gold
    for x, z in ((x0, -9), (x1, -9), (x0, -2), (x1, -2)):
        if short(p.name(x, 86, z)) in ('chiseled_sandstone', 'cut_sandstone'):
            p.put(x, 87, z, 'gold_block')
    cx, cz = ANUBIS
    # plinth
    for x in range(cx - 2, cx + 3):
        for z in range(cz - 2, cz + 3):
            p.put(x, 81, z, 'cut_sandstone')
            p.put(x, 82, z, 'chiseled_sandstone' if abs(x - cx) == 2 and abs(z - cz) == 2 else 'smooth_sandstone')
    for x in range(cx - 2, cx + 3):
        p.put(x, 81, cz - 3, 'sandstone_stairs[facing=south,half=bottom,shape=straight]')
    body = 'polished_blackstone'
    # legs and feet (facing north, -z)
    for x in (cx - 1, cx + 1):
        for y in range(83, 86):
            p.put(x, y, cz, body)
        p.put(x, 83, cz - 1, 'polished_blackstone_slab[type=bottom]')
    # kilt: white and gold
    for x in range(cx - 1, cx + 2):
        p.put(x, 86, cz, 'gold_block' if x == cx else 'smooth_quartz')
        p.put(x, 86, cz + 1, 'smooth_quartz')
    # torso, collar, arms
    for x in range(cx - 1, cx + 2):
        for y in range(87, 90):
            p.put(x, y, cz, body); p.put(x, y, cz + 1, body)
        p.put(x, 89, cz - 1, 'gold_block' if x != cx else 'lapis_block')
    for x in (cx - 2, cx + 2):
        for y in range(86, 90):
            p.put(x, y, cz, body)
    # the staff in the right hand (east), gold
    for y in range(81, 93):
        p.put(cx + 3, y, cz - 1, 'gold_block' if y in (81, 92) else 'yellow_terracotta')
    p.put(cx + 3, 93, cz - 1, 'gold_block'); p.put(cx + 2, 92, cz - 1, 'gold_block')
    p.put(cx + 2, 87, cz - 1, body)   # the hand round the staff
    # head: jackal, snout forward, tall ears, gold eyes
    for x in range(cx - 1, cx + 2):
        for z in range(cz - 1, cz + 2):
            for y in (90, 91):
                p.put(x, y, z, 'black_concrete')
    p.put(cx, 90, cz - 2, 'black_concrete'); p.put(cx, 90, cz - 3, 'black_concrete')
    p.put(cx - 1, 91, cz - 1, 'gold_block'); p.put(cx + 1, 91, cz - 1, 'gold_block')
    for x in (cx - 1, cx + 1):
        for y in (92, 93):
            p.put(x, y, cz, 'black_concrete')
        p.put(x, 94, cz, 'blackstone_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]')
    # headdress stripes down the back
    for y in (90, 91):
        p.put(cx - 1, y, cz + 1, 'lapis_block'); p.put(cx + 1, y, cz + 1, 'lapis_block')


def skin_remover(p):
    """The little shop gets grey and white awning stripes, a washing cauldron, a grindstone and two mannequins."""
    x0, x1, z0, z1 = SKIN
    for x in range(x0, x1 + 1):
        s = p.state(x, 84, z1)
        if short(s.split('[')[0]) in ('light_blue_wool', 'white_wool'):
            p.put(x, 84, z1, 'light_gray_wool' if x % 2 else 'white_wool')
    # inside: the room is x -129..-124, z -29..-18, the door on the south side
    p.put(-124, 81, -28, 'water_cauldron[level=3]')
    p.put(-125, 81, -28, 'water_cauldron[level=3]')
    p.put(-124, 81, -25, 'grindstone[face=floor,facing=west]')
    p.put(-129, 81, -21, 'smooth_stone_slab[type=bottom]'); p.put(-129, 81, -22, 'smooth_stone_slab[type=bottom]')
    p.put(-124, 81, -21, 'smooth_stone_slab[type=bottom]')
    stand = ('{Invulnerable:1b,NoGravity:1b,ShowArms:1b,NoBasePlate:1b,DisabledSlots:4144959,Rotation:[0f,0f],'
             'Tags:["botand_skin_remover"]%s}')
    dressed = (',ArmorItems:[{id:"minecraft:leather_boots",Count:1b,tag:{display:{color:6719955}}},'
               '{id:"minecraft:leather_leggings",Count:1b,tag:{display:{color:6719955}}},'
               '{id:"minecraft:leather_chestplate",Count:1b,tag:{display:{color:11546150}}},'
               '{id:"minecraft:leather_helmet",Count:1b,tag:{display:{color:11546150}}}]')
    p.summon('minecraft:armor_stand', -127.5, 81, -26.5, stand % dressed)
    p.summon('minecraft:armor_stand', -125.5, 81, -26.5, stand % '')


def bushes(p, Z, rng):
    """Square dark bushes at the battle field's corners, on pavement nobody walks."""
    n = 0
    def bush(x, z, s=1, h=2):
        nonlocal n
        cells = [(xx, yy, zz) for xx in range(x - s, x + s + 1) for zz in range(z - s, z + s + 1) for yy in range(81, 81 + h)]
        if any(c in p.protect or p.name(*c) != 'minecraft:air' for c in cells): return False
        if any(short(p.name(xx, 80, zz)) not in ('polished_andesite', 'grass_block') for xx, _, zz in cells): return False
        for xx, yy, zz in cells:
            p.put(xx, yy, zz, MID if yy == 81 + h - 1 and noise(xx, yy, zz) < 0.45 else DARK)
        n += 1
        return True
    for x, z in ((0, -78), (67, -78), (1, -20), (65, -20)):
        bush(x, z)
    return n
