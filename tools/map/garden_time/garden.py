"""Botand Azure Garden v1.4, the garden of time: the theme's pieces, each a function that writes into a Plan.

Everything is vanilla Minecraft 1.20.1 blocks (and a few clocks in item frames), so the map needs nothing new from the
mod, and the mod still knows it as its city (the cliffcity data pack is untouched).
"""
import math
from scipy.interpolate import PchipInterpolator
from plan import disc, ring, segment, tapered, ahead, numeral

GOLD = 'gold_block'
QUARTZ = 'smooth_quartz'
DARK = 'polished_deepslate'
SKY = 'light_blue_concrete'
BLUED = 'blue_concrete'          # heat-blued steel hands
SAND = 'sand'

# The bulb's radius along its height, from the cap (t = 0) to the neck (t = 1), as a share of the widest radius.
_BULB = PchipInterpolator([0.0, 0.1, 0.25, 0.4, 0.55, 0.7, 0.82, 0.92, 1.0], [0.5, 0.78, 0.95, 1.0, 0.93, 0.75, 0.5, 0.28, 0.0])


def hourglass(p, cx, cz, y0, R, Hb, cap_r, neck=1.6, shell='glass', sand=SAND, posts=4, post='quartz_pillar[axis=y]',
              rim=GOLD, gem=SKY, fill_bottom=0.42, fill_top=0.46, finial=True):
    """An upright hourglass on cell layers y0..: bottom cap (2), two bulbs of Hb layers, top cap (2) and a finial.
    Returns the top cap's upper layer."""
    yb, yt = y0 + 2, y0 + 2 + Hb          # first layer of the bottom bulb, of the top bulb
    ytop = y0 + 2 + 2 * Hb               # top cap's lower layer

    def radius(y):
        if yb <= y < yt: t = (y - yb + 0.5) / Hb
        elif yt <= y < ytop: t = (ytop - 1 - y + 0.5) / Hb
        else: return None
        return max(neck, R * float(_BULB(t)))

    span = int(math.ceil(cap_r)) + 1
    xs = range(int(math.floor(cx - span)), int(math.ceil(cx + span)) + 1)
    zs = range(int(math.floor(cz - span)), int(math.ceil(cz + span)) + 1)

    def inside(x, y, z):
        if y < yb or y >= ytop: return y in (yb - 1, ytop) and math.hypot(x + .5 - cx, z + .5 - cz) < cap_r - 0.6
        return math.hypot(x + .5 - cx, z + .5 - cz) < radius(y)

    # caps: a gold-rimmed disc with sky "gems" at eight compass points, and a chiseled ring against the glass
    for yy, outer in ((y0, True), (y0 + 1, False), (ytop, False), (ytop + 1, True)):
        r = cap_r if outer else cap_r - 0.6
        for x, z, d in disc(cx, cz, r):
            if outer and d >= r - 1.0:
                a = (math.degrees(math.atan2(x + .5 - cx, -(z + .5 - cz))) + 360) % 360
                s = gem if min(abs(a - k * 45) for k in range(9)) < 7 else rim
            elif not outer and d >= r - 1.0:
                s = 'chiseled_quartz_block'
            else:
                s = QUARTZ
            p.put(x, yy, z, s)
    # the glass and the sand
    def pile(d):  # bottom bulb: layers of sand from its floor, a mound
        return fill_bottom * Hb + 2.2 - 0.5 * d
    def funnel(d):  # top bulb: the sand's surface counted up from the neck, dipping where it drains
        return fill_top * Hb - 2.2 * math.exp(-(d / 2.0) ** 2)
    stream_top = None
    for y in range(yb, ytop):
        r = radius(y)
        for z in zs:
            for x in xs:
                d = math.hypot(x + .5 - cx, z + .5 - cz)
                if d >= r: continue
                edge = any(not inside(x + dx, y + dy, z + dz) for dx, dy, dz in
                           ((1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1), (0, 1, 0), (0, -1, 0)))
                if edge:
                    p.put(x, y, z, shell); continue
                if y < yt:
                    filled = (y - yb) < pile(d)
                    stream = d < 0.8
                else:
                    filled = (y - yt) < funnel(d)
                    stream = d < 0.8 and (y - yt) < funnel(d) + 1
                if filled or stream:
                    p.put(x, y, z, sand)
                else:
                    p.put(x, y, z, 'air')
    # posts between the caps, outside the bulbs
    if posts:
        pr = cap_r - 0.9
        for k in range(posts):
            a = math.radians(45 + k * 360 / posts)
            px, pz = math.floor(cx + pr * math.sin(a)), math.floor(cz - pr * math.cos(a))
            for y in range(yb, ytop):
                p.put(px, y, pz, post)
    if finial:
        fx, fz = math.floor(cx), math.floor(cz)
        p.put(fx, ytop + 2, fz, GOLD)
        p.put(fx, ytop + 3, fz, 'end_rod[facing=up]')
    return ytop + 1


def clock_floor(p, cx, cz, y, R, face=QUARTZ, numerals=True, hands=(300, 60), hour_len=0.48, minute_len=0.72,
                bezel=GOLD, outline=DARK, ticks=DARK, hour_mark=DARK, digits=DARK, hand=BLUED, hub=GOLD, dots=SKY):
    """A clock face lying on the floor layer y, its 12 to the north, read upright from the south.
    R is the outer radius; the numerals XII, III, VI and IX are 7 cells tall."""
    for x, z, d in disc(cx, cz, R):
        if d >= R - 1.0: s = outline
        elif d >= R - 2.3: s = bezel
        else: s = face
        p.put(x, y, z, s)
    # the minute track: short ticks, the hours long and broad
    for m in range(60):
        dx, dz = ahead(m * 6)
        if m % 5:
            r0, r1, half, s = R - 3.7, R - 2.9, 0.42, ticks
        else:
            r0, r1, half, s = R - 5.2, R - 2.9, 0.75, hour_mark
        for x, z in segment(cx + dx * r0, cz + dz * r0, cx + dx * r1, cz + dz * r1, half):
            p.put(x, y, z, s)
    # numerals at the quarters, a sky dot inside the other hours
    rn = R - 8.2
    if numerals:
        for text, a in (('XII', 0), ('III', 90), ('VI', 180), ('IX', 270)):
            rows = numeral(text)
            dx, dz = ahead(a)
            gx, gz = cx + dx * rn, cz + dz * rn
            w = len(rows[0]); h = len(rows)
            x0 = math.floor(gx - w / 2 + 0.5); z0 = math.floor(gz - h / 2 + 0.5)
            for r, line in enumerate(rows):
                for c, ch in enumerate(line):
                    if ch == 'X': p.put(x0 + c, y, z0 + r, digits)
    for h in range(12):
        if numerals and h % 3 == 0: continue
        dx, dz = ahead(h * 30)
        for x, z, d in disc(cx + dx * (R - 6.4), cz + dz * (R - 6.4), 0.9):
            p.put(x, y, z, dots)
    # hands: the hour hand short and broad, the minute hand long, both with a short tail
    for a, length, w0, w1 in ((hands[0], R * hour_len, 3.4, 1.2), (hands[1], R * minute_len, 2.6, 0.9)):
        dx, dz = ahead(a)
        for x, z in tapered(cx, cz, cx + dx * length, cz + dz * length, w0, w1):
            p.put(x, y, z, hand)
        for x, z in tapered(cx, cz, cx - dx * 2.6, cz - dz * 2.6, w0, w0 * 0.9):
            p.put(x, y, z, hand)
    for x, z, d in disc(cx, cz, 2.9):
        p.put(x, y, z, hub if d >= 1.2 else SKY)


def arena(p):
    """The battle field's floor (x 6..61, z -68..-28 inside its border) becomes a great clock face read from the spawn."""
    for z in range(-68, -27):
        for x in range(6, 62):
            p.put(x, 80, z, 'light_gray_concrete')
    clock_floor(p, 34.0, -47.5, 80, 20.0)


def fountain(p):
    """The south garden's fountain (centre cell 38, 23) becomes the hourglass fountain: a pool round a plinth and a great
    hourglass, on a round plaza laid out as a clock."""
    cx, cz = 38.5, 23.5
    # take away the old fountain, its lamps and the square plaza's furniture inside the new circle
    for x, z, d in disc(cx, cz, 14.5):
        for y in range(81, 87):              # the old spout's water stands to y 84 under a slab at 85
            p.put(x, y, z, 'air')
    # the plaza: white, rimmed dark, its hours in gold and sky blue
    for x, z, d in disc(cx, cz, 14.0):
        if d >= 13.0: s = DARK
        else: s = 'quartz_bricks'
        p.put(x, 80, z, s)
    for h in range(12):
        dx, dz = ahead(h * 30)
        a, b = 10.6, 12.6
        for x, z in segment(cx + dx * a, cz + dz * a, cx + dx * b, cz + dz * b, 0.75 if h % 3 == 0 else 0.5):
            p.put(x, 80, z, GOLD if h % 3 == 0 else SKY)
    # the pool: lit floor one below the plaza, water at the plaza's level, a low rim
    for x, z, d in disc(cx, cz, 10.4):
        if d >= 9.6:
            p.put(x, 80, z, QUARTZ); p.put(x, 81, z, 'smooth_quartz_slab[type=bottom,waterlogged=false]')
        elif d >= 7.6:
            p.put(x, 79, z, 'sea_lantern' if (x + z) % 3 == 0 else 'prismarine_bricks')
            p.put(x, 80, z, 'water[level=0]')
    # the plinth the hourglass stands on
    for x, z, d in disc(cx, cz, 7.6):
        p.put(x, 80, z, 'quartz_bricks')
        p.put(x, 81, z, GOLD if d >= 6.8 else QUARTZ)
    for x, z, d in disc(cx, cz, 7.0):
        p.put(x, 82, z, 'chiseled_quartz_block' if d >= 6.2 else QUARTZ)
    hourglass(p, cx, cz, 83, R=5.6, Hb=12, cap_r=6.6)


# --- clocks on walls: a face in a vertical plane --------------------------------------------------------------------
_RIGHT = {'north': (-1, 0), 'south': (1, 0), 'east': (0, -1), 'west': (0, 1)}   # the viewer's right, as (dx, dz)
_OUT = {'north': (0, -1), 'south': (0, 1), 'east': (1, 0), 'west': (-1, 0)}     # out of the wall


def clock_wall(p, x, y, z, facing, R, face='white_concrete', rim=GOLD, mark=DARK, hand=BLUED, hub=GOLD,
               hands=(300, 60), raised=True):
    """A clock face on a wall: (x, y, z) is the cell at its centre in the wall's outer plane, `facing` the way it looks.
    The rim stands one cell proud of the wall when `raised`."""
    rx, rz = _RIGHT[facing]; ox, oz = _OUT[facing]
    n = int(math.ceil(R))
    cells = {}
    for v in range(-n, n + 1):
        for u in range(-n, n + 1):
            r = math.hypot(u, v)
            if r < R - 0.9: cells[(u, v)] = face
            elif r < R: cells[(u, v)] = rim
    for h in range(12):
        a = math.radians(h * 30)
        for rr in ((R - 1.6, R - 2.4) if h % 3 == 0 else (R - 1.6,)):
            u, v = round(rr * math.sin(a)), round(rr * math.cos(a))
            if (u, v) in cells and cells[(u, v)] == face: cells[(u, v)] = mark
    for a, length in ((hands[0], R * 0.5), (hands[1], R * 0.74)):
        t = math.radians(a)
        steps = int(length * 3) + 1
        for k in range(steps + 1):
            q = length * k / steps
            u, v = round(q * math.sin(t)), round(q * math.cos(t))
            if (u, v) in cells: cells[(u, v)] = hand
    cells[(0, 0)] = hub
    for (u, v), s in cells.items():
        cxx, czz = x + rx * u, z + rz * u
        p.put(cxx, y + v, czz, s)
        if raised and s == rim:
            p.put(cxx + ox, y + v, czz + oz, rim)


def clock_tower(p):
    """The north building's roof turret (around cell 12, -145, roof y 105) becomes a clock tower with a dial on each side."""
    p.clear(7, 106, -151, 17, 122, -139)
    x0, x1, z0, z1 = 7, 17, -150, -140              # the shaft
    for y in range(106, 125):
        for z in range(z0, z1 + 1):
            for x in range(x0, x1 + 1):
                edge_x, edge_z = x in (x0, x1), z in (z0, z1)
                if not (edge_x or edge_z): continue
                if edge_x and edge_z: s = 'quartz_pillar[axis=y]'
                elif y in (115, 116): s = 'light_blue_terracotta'
                elif (x == 12 or z == -145) and 108 <= y <= 122 and y not in (115, 116): s = 'light_blue_stained_glass'
                else: s = 'quartz_bricks'
                p.put(x, y, z, s)
    for x in range(x0 - 1, x1 + 2):                   # cornice
        for z in range(z0 - 1, z1 + 2):
            if x in (x0 - 1, x1 + 1) or z in (z0 - 1, z1 + 1):
                p.put(x, 124, z, 'smooth_quartz_slab[type=top,waterlogged=false]')
    X0, X1, Z0, Z1 = 6, 18, -151, -139               # the clock stage
    for y in range(125, 140):
        for z in range(Z0, Z1 + 1):
            for x in range(X0, X1 + 1):
                ex, ez = x in (X0, X1), z in (Z0, Z1)
                if not (ex or ez): continue
                p.put(x, y, z, 'quartz_pillar[axis=y]' if ex and ez else ('chiseled_quartz_block' if y in (125, 139) else 'quartz_bricks'))
    for facing, (cx, cz) in (('south', (12, Z1)), ('north', (12, Z0)), ('east', (X1, -145)), ('west', (X0, -145))):
        clock_wall(p, cx, 132, cz, facing, 5.6)
    # a copper roof stepping up to a golden orb and a lit spire
    for k in range(7):
        y = 140 + k; h = 6 - k
        for z in range(-145 - h - (1 if k == 0 else 0), -145 + h + (1 if k == 0 else 0) + 1):
            for x in range(12 - h - (1 if k == 0 else 0), 12 + h + (1 if k == 0 else 0) + 1):
                p.put(x, y, z, 'waxed_oxidized_cut_copper')
    for dy, r in ((147, 1.0), (148, 1.6), (149, 1.0)):
        for x, z, d in disc(12.5, -144.5, r):
            p.put(x, dy, z, GOLD)
    for y in range(150, 154):
        p.put(12, y, -145, 'chain[axis=y,waterlogged=false]')
    p.put(12, 154, -145, 'sea_lantern')
    p.put(12, 155, -145, 'end_rod[facing=up]')


def sundial(p):
    """The west park's fountain (centre cell -91, -52) becomes a sundial: a sand-coloured dial with its hour lines for
    six in the morning to six at night, a copper gnomon pointing north and the gnomon's shadow at seven, the hour this
    world always shows."""
    cx, cz = -90.5, -51.5
    for x, z, d in disc(cx, cz, 7.6):
        for y in range(81, 87):              # the old spout's water stands to y 84 under a slab at 85
            p.put(x, y, z, 'air')
    for x, z, d in disc(cx, cz, 7.6):
        p.put(x, 79, z, 'sandstone')
        if d >= 6.6: s = 'cut_sandstone'
        else: s = 'smooth_sandstone'
        p.put(x, 80, z, s)
    lat = math.radians(37.5)
    for hour in range(6, 19):
        h = hour - 12
        a = math.degrees(math.atan2(math.sin(lat) * math.tan(math.radians(h * 15)), 1)) if abs(h) < 6 else 90 * (1 if h > 0 else -1)
        dx, dz = ahead(a)
        r1 = 6.0
        for x, z in segment(cx, cz, cx + dx * r1, cz + dz * r1, 0.42):
            if math.hypot(x + .5 - cx, z + .5 - cz) > 1.6:
                p.put(x, 80, z, GOLD if hour in (6, 12, 18) else DARK)
    # the shadow of the morning sun, falling toward seven o'clock's line (west of north-west)
    a7 = -math.degrees(math.atan(math.sin(lat) * math.tan(math.radians(75))))
    dx, dz = ahead(a7)
    for x, z in tapered(cx, cz, cx + dx * 5.6, cz + dz * 5.6, 2.2, 0.8):
        if math.hypot(x + .5 - cx, z + .5 - cz) > 1.2:
            p.put(x, 80, z, 'light_gray_concrete')
    # the gnomon: a copper fin from the centre rising to the north at the latitude's slope
    gx = -91
    for k in range(0, 6):
        z = -52 - k
        top = 80 + max(1, round((k + 1) * math.tan(lat)))
        for y in range(81, top + 1):
            p.put(gx, y, z, 'waxed_cut_copper')
    p.put(gx, 81, -52, 'waxed_cut_copper')
    for x, z, d in ring(cx, cz, 7.6, 8.6):
        p.put(x, 80, z, 'quartz_bricks')


def floral_clock(p, cx=135.5, cz=156.5, R=14.0):
    """A clock of flowers on the open south-east lawn: twelve beds of colour round a white rim, a mark at each hour
    and two long dark hands, all at ground level so the lawn stays open."""
    beds = ['red_tulip', 'cornflower', 'dandelion', 'allium', 'pink_tulip', 'blue_orchid',
            'orange_tulip', 'cornflower', 'oxeye_daisy', 'allium', 'poppy', 'blue_orchid']
    for x, z, d in disc(cx, cz, R + 0.6):
        for y in range(81, 83):
            p.put(x, y, z, 'air')
        if d >= R - 0.8:
            p.put(x, 80, z, 'calcite'); continue
        p.put(x, 80, z, 'grass_block[snowy=false]')
        if d < 2.6: continue
        a = (math.degrees(math.atan2(x + .5 - cx, -(z + .5 - cz))) + 360) % 360
        p.put(x, 81, z, beds[int(a // 30)] if d < R - 3.4 else ('oxeye_daisy' if (x + z) % 2 else 'azure_bluet'))
    for h in range(12):
        dx, dz = ahead(h * 30)
        for x, z in segment(cx + dx * (R - 3.2), cz + dz * (R - 3.2), cx + dx * (R - 1.0), cz + dz * (R - 1.0), 0.8 if h % 3 == 0 else 0.55):
            p.put(x, 80, z, GOLD if h % 3 == 0 else DARK)
            p.put(x, 81, z, 'air')
    for a, length, w0, w1 in ((300, R * 0.5, 2.6, 1.1), (60, R * 0.76, 2.0, 0.9)):
        dx, dz = ahead(a)
        for x, z in tapered(cx, cz, cx + dx * length, cz + dz * length, w0, w1):
            p.put(x, 80, z, 'polished_blackstone'); p.put(x, 81, z, 'air')
    for x, z, d in disc(cx, cz, 1.8):
        p.put(x, 80, z, GOLD); p.put(x, 81, z, 'air')


# --- clockwork in the cliffs ------------------------------------------------------------------------------------------
ROCK = ('minecraft:stone', 'minecraft:andesite', 'minecraft:calcite', 'minecraft:terracotta', 'minecraft:brown_terracotta',
        'minecraft:gray_terracotta', 'minecraft:moss_block', 'minecraft:dirt', 'minecraft:grass_block', 'minecraft:polished_andesite')


def wall_plane(p, x, z, facing, half, y=88):
    """The plane just in front of the cliff wall near (x, z): the wall's face nearest the island across +-half cells,
    one cell out. Returns that coordinate (x for east/west walls, z for north/south)."""
    ox, oz = _OUT[facing]; rx, rz = _RIGHT[facing]
    best = None
    for u in range(-half, half + 1):
        cx, cz = x + rx * u, z + rz * u
        for k in range(0, 40):
            qx, qz = cx - ox * k, cz - oz * k
            if p.name(qx, y, qz) in ROCK:
                face = qx if ox else qz
                score = face * (ox or oz)          # larger is nearer the island
                if best is None or score > best[0]: best = (score, face)
                break
    return best[1] + (ox or oz)


def gear(p, x, y, z, facing, Ro, rot=0.0, body='waxed_oxidized_cut_copper', hub=GOLD, spokes=5, depth=2, backing='andesite'):
    """A clockwork gear standing against a wall: (x, y, z) is its centre in its front plane, facing out of the wall.
    Teeth round the rim, a rim, spokes, a gold hub with a dark axle; the cells behind it reach back to the rock."""
    rx, rz = _RIGHT[facing]; ox, oz = _OUT[facing]
    Rr = Ro - 1.6; rim_in = Rr - 1.4; hub_r = max(1.5, Ro * 0.24)
    teeth = max(8, round(2 * math.pi * Rr / 3.3)); P = 360 / teeth
    n = int(math.ceil(Ro)) + 1
    for v in range(-n, n + 1):
        for u in range(-n, n + 1):
            r = math.hypot(u, v)
            a = (math.degrees(math.atan2(u, v)) - rot) % 360
            s = None
            if r < 0.8: s = DARK
            elif r < hub_r: s = hub
            elif r < rim_in:
                for k in range(spokes):
                    d = abs((a - k * 360 / spokes + 180) % 360 - 180)
                    if math.radians(d) * r < 0.7: s = body; break
            elif r < Rr: s = body
            elif r < Ro and (a % P) < P * 0.5: s = body
            if s is None: continue
            cx, cz = x + rx * u, z + rz * u
            for k in range(depth):
                p.put(cx - ox * k, y + v, cz - oz * k, s if k == 0 else body)
            for k in range(depth, depth + 6 if backing else depth):   # back to the rock
                qx, qz = cx - ox * k, cz - oz * k
                if p.name(qx, y + v, qz) != 'minecraft:air': break
                p.put(qx, y + v, qz, backing)


def gear_pair(p, x, z, facing, y, Ro, small, du, dv, rot=0.0, small_body='waxed_cut_copper'):
    """A big gear on the wall near (x, z) and a smaller one meshing with it, du cells to the viewer's right and dv up."""
    half = int(Ro + abs(du) + small)
    plane = wall_plane(p, x, z, facing, half)
    rx, rz = _RIGHT[facing]
    if facing in ('east', 'west'): x = plane
    else: z = plane
    gear(p, x, y, z, facing, Ro, rot=rot)
    if small:
        gear(p, x + rx * du, y + dv, z + rz * du, facing, small, rot=rot + 360 / max(8, round(2 * math.pi * (small - 1.6) / 3.3)) / 2,
             body=small_body, spokes=4)


def clockwork(p):
    """Gears in the cliff wall round the island, facing in."""
    gear_pair(p, 119, -40, 'west', 89, 8.5, 4.6, 8, 6)
    gear_pair(p, 131, 10, 'west', 88, 7.0, 0, 0, 0, rot=10)
    gear_pair(p, -161, -60, 'east', 89, 8.0, 4.4, 8, 6)
    gear_pair(p, -160, -20, 'east', 87, 6.0, 0, 0, 0, rot=12)
    gear_pair(p, -110, 20, 'north', 88, 7.0, 4.0, -8, -4)
    gear_pair(p, 100, 185, 'north', 89, 8.0, 4.4, 8, 5)
    gear_pair(p, 40, -178, 'south', 87, 7.0, 0, 0, 0, rot=6)


# --- smaller pieces: a little hourglass, clocks on roofs, street clocks ---------------------------------------------
_SMALL = [  # layers of a 5 x 5 hourglass, bottom up: Q quartz, G gold, P post, g glass, s sand, y sand-yellow glass
    ['GQQQG', 'QQQQQ', 'QQQQQ', 'QQQQQ', 'GQQQG'],
    ['P...P', '.ggg.', '.gsg.', '.ggg.', 'P...P'],
    ['P...P', '.ggg.', '.gsg.', '.ggg.', 'P...P'],
    ['P...P', '..g..', '.gsg.', '..g..', 'P...P'],
    ['P...P', '.....', '..y..', '.....', 'P...P'],
    ['P...P', '..g..', '.gsg.', '..g..', 'P...P'],
    ['P...P', '.ggg.', '.gsg.', '.ggg.', 'P...P'],
    ['P...P', '.ggg.', '.g.g.', '.ggg.', 'P...P'],
    ['GQQQG', 'QQQQQ', 'QQQQQ', 'QQQQQ', 'GQQQG'],
]


def hourglass_small(p, x, y, z, sand=SAND, rim=GOLD, glass='glass'):
    """A 5 x 5, nine-high hourglass standing on (x, y, z) (its centre cell, bottom layer)."""
    key = {'Q': QUARTZ, 'G': rim, 'P': 'quartz_pillar[axis=y]', 'g': glass, 's': sand, 'y': 'yellow_stained_glass', '.': 'air'}
    for k, layer in enumerate(_SMALL):
        for r, row in enumerate(layer):
            for c, ch in enumerate(row):
                p.put(x - 2 + c, y + k, z - 2 + r, key[ch])
    p.put(x, y + len(_SMALL), z, 'end_rod[facing=up]')


def fits(p, x0, x1, z0, z1, y0, y1, ground=80):
    """True when the box above the ground is empty and stands on solid ground."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if p.name(x, ground, z) in ('minecraft:air', 'minecraft:water'): return False
            for y in range(y0, y1 + 1):
                n = p.name(x, y, z)
                if n != 'minecraft:air' and not n.endswith(('_orchid', 'fern', 'daisy', 'valley', 'cornflower', 'azalea', 'grass', 'dandelion', 'poppy', 'tulip', 'allium', 'bluet')):
                    return False
    return True


def roof_clock(p, x, z, facing, R=3.6, rim=GOLD, face='white_concrete'):
    """A clock standing on a roof's edge above the wall at (x, z), looking out (`facing`), backed by a white plate."""
    ox, oz = _OUT[facing]; rx, rz = _RIGHT[facing]
    n = int(math.ceil(R))
    roof = max(y for y in range(80, 130) for u in range(-n, n + 1)
               if p.name(x + rx * u, y, z + rz * u) not in ('minecraft:air',) and 'leaves' not in p.name(x + rx * u, y, z + rz * u))
    cy = roof + 1 + n
    clock_wall(p, x, cy, z, facing, R, face=face, rim=rim)
    for v in range(-n, n + 1):
        for u in range(-n, n + 1):
            if math.hypot(u, v) < R:
                p.put(x + rx * u - ox, cy + v, z + rz * u - oz, 'quartz_bricks')
    return cy


def street_clock(p, x, z, y=81):
    """A street clock: a dark post with a white head carrying a real clock on each side (fixed, invisible frames)."""
    p.put(x, y, z, DARK)
    for k in range(1, 4):
        p.put(x, y + k, z, 'dark_oak_fence[east=false,north=false,south=false,waterlogged=false,west=false]')
    head = y + 4
    p.put(x, head, z, QUARTZ)
    p.put(x, head + 1, z, 'waxed_oxidized_cut_copper_slab[type=bottom,waterlogged=false]')
    for facing, (dx, dz), code in (('north', (0, -1), 2), ('south', (0, 1), 3), ('west', (-1, 0), 4), ('east', (1, 0), 5)):
        p.summon('minecraft:item_frame', x + dx + 0.5, head + 0.5, z + dz + 0.5,
                 '{Facing:%db,Fixed:1b,Invisible:1b,Silent:1b,Item:{id:"minecraft:clock",Count:1b}}' % code)


def facade(p, x0, x1, z0, z1, facing, y=90):
    """The plane of a building's wall on one side of its box: the most common first solid cell seen from outside
    (slabs, fences, planters and leaves in front of it do not count)."""
    ox, oz = _OUT[facing]
    counts = {}
    span = range(z0 + 2, z1 - 1) if ox else range(x0 + 2, x1 - 1)
    for s in span:
        for yy in (y - 3, y, y + 3):
            for k in range(-3, 14):
                if ox:
                    q = (x1 + 3 - k) if ox > 0 else (x0 - 3 + k); cell = (q, yy, s)
                else:
                    q = (z1 + 3 - k) if oz > 0 else (z0 - 3 + k); cell = (s, yy, q)
                n = p.name(*cell)
                if n == 'minecraft:air' or any(t in n for t in ('slab', 'fence', 'leaves', 'lantern', 'wool', 'stairs', 'azalea', 'copper')):
                    continue
                counts[q] = counts.get(q, 0) + 1
                break
    return max(counts, key=counts.get)


# buildings that get a clock on their roof's edge: box (x0, x1, z0, z1), the side it looks out of, rim colour
ROOF_CLOCKS = [
    ((75, 89, -58, -29), 'west', GOLD),       # east of the battle field
    ((22, 37, -120, -92), 'south', SKY),      # north road, facing the battle field
    ((6, 18, -124, -95), 'south', GOLD),
    ((-55, -35, -81, -64), 'east', SKY),
    ((82, 99, -2, 29), 'west', GOLD),         # by the south garden
    ((-61, -46, 3, 25), 'east', SKY),         # by the car park
    ((-147, -132, -68, -32), 'east', GOLD),   # facing the west park
    ((89, 104, 48, 65), 'west', SKY),
    ((92, 107, 75, 91), 'west', GOLD),
    ((-40, -26, 112, 129), 'north', SKY),     # south road
    ((-22, -8, 112, 129), 'north', GOLD),
    ((41, 71, -157, -128), 'south', SKY),     # the north row
]


def roof_clocks(p):
    for (x0, x1, z0, z1), facing, rim in ROOF_CLOCKS:
        plane = facade(p, x0, x1, z0, z1, facing)
        if facing in ('east', 'west'): roof_clock(p, plane, (z0 + z1) // 2, facing, rim=rim)
        else: roof_clock(p, (x0 + x1) // 2, plane, facing, rim=rim)


def street_clock_near(p, x, z, reach=3):
    """A street clock on the nearest free paved or grassy cell to (x, z), with room for its clocks on every side."""
    best = None
    for dz in range(-reach, reach + 1):
        for dx in range(-reach, reach + 1):
            cx, cz = x + dx, z + dz
            if not fits(p, cx - 1, cx + 1, cz - 1, cz + 1, 81, 87): continue
            d = dx * dx + dz * dz
            if best is None or d < best[0]: best = (d, cx, cz)
    if best:
        street_clock(p, best[1], best[2])
    return best


STREET_CLOCKS = [(1, -73), (66, -73), (1, -22), (66, -22),      # the battle field's corners
                 (-95, -66), (-87, -66),                         # the west park's north gate
                 (34, -1), (43, -1),                             # the south garden's north gate
                 (-44, -8), (-122, -18), (72, 96), (-45, 96)]    # crossings


def street_clocks(p):
    for x, z in STREET_CLOCKS:
        street_clock_near(p, x, z)


def spawn_hourglasses(p):
    """Two hourglasses either side of the spawn, at the battle field's south walk."""
    for x in (20, 47):
        if fits(p, x - 2, x + 2, -24, -20, 81, 90):
            hourglass_small(p, x, 81, -22)


def ruins(p):
    """The grail ruins in the south forest (centre 38, 177) become the ruins of lost time: a great clock face broken
    and sunk into the ground, its hand fallen across it, an hourglass lying on its side and spilling its sand, stumps of
    columns, and gears half buried round about."""
    import random
    rnd = random.Random(1577)
    cx, cz = 38.5, 177.5
    for x, z, d in disc(cx, cz, 9.0):
        for y in range(81, 85):
            n = p.name(x, y, z)
            if 'leaves' in n or 'log' in n: continue
            p.put(x, y, z, 'air')
    for x, z, d in disc(cx, cz, 7.2):
        s = 'calcite' if d < 6.2 else 'polished_deepslate'
        p.put(x, 80, z, s)
    for h in range(12):
        if h in (4, 5): continue                         # those marks went with the missing piece
        dx, dz = ahead(h * 30)
        for x, z in segment(cx + dx * 4.6, cz + dz * 4.6, cx + dx * 5.8, cz + dz * 5.8, 0.6):
            p.put(x, 80, z, DARK)
    # the broken piece (south-east) and cracks of moss
    for x, z, d in disc(cx, cz, 7.4):
        a = (math.degrees(math.atan2(x + .5 - cx, -(z + .5 - cz))) + 360) % 360
        if 110 < a < 170 and d > 2.5 + rnd.random():
            p.put(x, 80, z, rnd.choice(['grass_block[snowy=false]', 'moss_block', 'coarse_dirt']))
    for a0 in (20, 200, 290):
        r, a = 0.8, a0
        while r < 7:
            dx, dz = ahead(a)
            x, z = math.floor(cx + dx * r), math.floor(cz + dz * r)
            p.put(x, 80, z, 'moss_block')
            r += 0.7; a += rnd.uniform(-14, 14)
    # the fallen minute hand, longer than the face, lying across it toward four o'clock
    dx, dz = ahead(128)
    for x, z in tapered(cx - dx * 3, cz - dz * 3, cx + dx * 10.5, cz + dz * 10.5, 2.0, 0.8):
        if p.name(x, 81, z) == 'minecraft:air': p.put(x, 81, z, 'polished_blackstone_slab[type=bottom,waterlogged=false]')
    # an hourglass on its side (axis east-west), its east bulb broken open, sand spilled
    hx, hy, hz = 32, 82, 181
    for k in range(-6, 7):
        t = abs(k) / 6.0
        if abs(k) >= 6: rr = 2.9
        else: rr = max(0.9, 2.5 * math.sin(math.pi * min(1, (abs(k) - 0.3) / 5.4)) if abs(k) > 0 else 0.9)
        for v in range(-3, 4):
            for w in range(-3, 4):
                d = math.hypot(v, w)
                if abs(k) >= 6:
                    if d < 2.9: p.put(hx + k, hy + v, hz + w, 'waxed_weathered_cut_copper' if d > 2.0 else QUARTZ)
                    continue
                if d >= rr: continue
                shell = d >= rr - 1.0
                broken = k > 1 and v >= 0 and rnd.random() < 0.7
                if shell and not broken: p.put(hx + k, hy + v, hz + w, 'glass')
                elif v <= -1 and not shell: p.put(hx + k, hy + v, hz + w, SAND)
                elif shell and broken: p.put(hx + k, hy + v, hz + w, 'air')
    for x, z, d in disc(hx + 8.5, hz + 0.5, 3.2):
        h = 81 + (1 if d < 1.4 else 0)
        for y in range(81, h + 1): p.put(x, y, z, SAND)
    # the four columns, broken at different heights, one fallen
    for (x, z), hgt in (((32, 171), 4), ((44, 171), 2), ((44, 183), 5), ((32, 183), 0)):
        for y in range(81, 85): p.put(x, y, z, 'air')
        for y in range(81, 81 + hgt):
            p.put(x, y, z, 'mossy_stone_bricks' if y % 2 else 'cracked_stone_bricks')
        if hgt: p.put(x, 81 + hgt, z, 'stone_brick_slab[type=bottom,waterlogged=false]')
    for k in range(4):
        p.put(28 + k, 81, 185, 'stone_bricks' if k % 2 else 'mossy_stone_bricks')
    # gears half in the ground
    for (x, z, facing, r) in ((26, 170, 'east', 3.6), (51, 186, 'west', 3.0), (47, 166, 'south', 2.6)):
        gear(p, x, 80, z, facing, r, body='waxed_weathered_cut_copper', hub='waxed_oxidized_copper', spokes=4, depth=1, backing=None)


def stone_circle(p, cx=-129.5, cz=-115.5):
    """The hours in stone: a ring of twelve standing stones in the north-west forest's clearing, the noon stone tallest and
    crowned in gold, an hourglass at the middle."""
    for x, z, d in disc(cx, cz, 9.2):
        for y in range(81, 92):
            n = p.name(x, y, z)
            if 'leaves' in n or 'log' in n: continue
            if y <= 83: p.put(x, y, z, 'air')
    for x, z, d in ring(cx, cz, 5.2, 6.3):
        p.put(x, 80, z, 'dirt_path')
    for h in range(12):
        dx, dz = ahead(h * 30)
        x, z = math.floor(cx + dx * 7.6), math.floor(cz + dz * 7.6)
        hgt = 4 if h == 0 else 3 if h % 3 == 0 else 2
        for y in range(81, 81 + hgt):
            p.put(x, y, z, 'polished_andesite' if y < 80 + hgt else 'chiseled_stone_bricks')
        p.put(x, 81 + hgt, z, GOLD if h == 0 else 'stone_brick_slab[type=bottom,waterlogged=false]')
    hourglass_small(p, math.floor(cx), 81, math.floor(cz), sand='sand')
