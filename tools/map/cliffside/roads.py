"""The town's roads mended (map v1.4): every road one band of asphalt from end to end, five blocks wide, with nothing
standing on it.

v1.3 drew its roads and then laid its houses, entrance pads and sidewalks over them, so a road was cut where it met
another (a strip of sidewalk across it), dented under a shop's awning (its entrance pad), and the east road, drawn
straight through where the east row of houses stands, shrank to a block or two beside them and vanished for a few
blocks. Here:

- junctions and pads: the strips and pads across a road become asphalt; the crossings' stripes stay
- the east road is paved again as one band five blocks wide that keeps a two-block sidewalk from the houses, bending
  round them as the v1.3 road leans; what is left of the old one becomes sidewalk
- the west road ran into the back of the little shop at its end (the skin remover): it now bends east round the shop and
  meets the middle road, and the stripes across the middle road move with it
- the flower stall that stood on the north road at the shop's crossing moves onto the corner beside it, and the
  x=-5 road runs on into the north road
- the car park, an island of asphalt with sidewalk all round, gets a way in from the middle road and out to the south
  road (between its lamp posts)
- lamp posts that the new asphalt reaches move off it, to the sidewalk
"""
from zones import short

ASPHALT = 'minecraft:black_concrete'
LINE = 'minecraft:light_gray_concrete'
WALK = 'minecraft:polished_andesite'
ROAD = ('minecraft:black_concrete', 'minecraft:light_gray_concrete')
# what a lamp post is made of (the iron street lamps and the garden lamps), its head and the lanterns on its arms
LAMP = ('iron_block', 'sea_lantern', 'waxed_oxidized_cut_copper_slab', 'lantern', 'deepslate_bricks', 'dark_oak_fence',
        'smooth_quartz_slab', 'quartz_pillar')

# strips of sidewalk and entrance pads across a road, filled with asphalt (x0, x1, z0, z1; stripes are kept)
GAPS = [
    (-117, -116, -88, -86),                       # the north road at the west road's corner
    (-65, -64, -90, -86), (-58, -57, -90, -86),   # either side of the x=-62 road where it crosses the north road
    (-49, -47, -90, -89), (28, 30, -90, -89),     # shops' entrance pads on the north road
    (-64, -64, -83, -82),                         # the corner where the x=-62 road steps a block west
    (-68, -68, -30, -22),                         # the x=-62 road a block narrow beside a house
    (-68, -64, -17, -16),                         # the x=-62 road into the middle road
    (-7, -3, -17, -16),                           # the x=-5 road into the middle road
    (-53, -51, -15, -14), (-34, -32, -15, -14), (-15, -13, -15, -14),   # entrance pads on the middle road
    (28, 30, -12, -11),                           # the Anubis kiosk's pad
    (-46, -45, -11, -11), (-39, -38, -11, -11), (6, 7, -11, -11), (12, 13, -11, -11),   # by the car park's lanes
    (74, 74, -15, -11),                           # between the middle road's end and the east road
    (-44, -40, 95, 96), (8, 11, 95, 96),          # the car park's lanes into the south road
    (60, 61, 101, 101), (91, 92, 101, 101),       # a dirt path across the south road; its square end
    (86, 90, 96, 96),                             # the east road into the south road (its stripes kept)
    (-15, -11, -10, -5), (-21, -17, 94, 96),      # the car park, walled in by sidewalk, opened to the middle and south roads
]
# plain asphalt over stripes and pavement alike (x0, x1, z0, z1): the north road's corner into the east road, and the
# north road where the stall stood and the x=-5 road meets it (the shop's blue mat, VenueRenovation's, stays)
PLAIN = [(66, 72, -90, -86), (-10, -2, -88, -86)]
SIDEWALK = [(73, -87), (74, -87), (-9, -85), (-9, -84)] + \
           [(x, z) for x in (-128, -127, -126) for z in (-13, -12, -11)]   # the middle road's ragged west end, squared
# crossings painted across a road's head: x0, x1, z0, z1, stripes on every other block from x0
CROSSINGS = [(66, 70, -85, -83), (-8, -4, -85, -84)]

# the east road: (z0, z1, x of its west edge), five blocks wide and two clear of the houses' walls (x 73, 75, 82, 89,
# 92 down the row), bending round them as the v1.3 road leans and meeting the middle road where it did
EAST = [(-85, -70, 66), (-69, -64, 67), (-63, -29, 68), (-28, -27, 69), (-26, -25, 70), (-24, -23, 71), (-22, -21, 72),
        (-20, -19, 73), (-18, -16, 74), (-15, 29, 75), (30, 31, 76), (32, 33, 77), (34, 35, 78), (36, 37, 79),
        (38, 39, 80), (40, 41, 81), (42, 65, 82), (66, 67, 83), (68, 69, 84), (70, 91, 85), (92, 95, 86)]
EAST_X = (63, 95)                           # the old east road's asphalt in these x from z -85 to 95 is replaced
EAST_STRIPES = [(x, z) for x in (75, 77) for z in range(-15, -10)]   # its crossing over the middle road, as in v1.3
# the west road bends east round the skin remover from z -44 to -31, then runs straight into the middle road
WEST_BEND = (-44, -125, -31, -119)          # z0, west edge there, z1, west edge there
WEST_END = -16
WEST_OLD = (-130, -117, -44, -31)           # old asphalt here that the bend leaves becomes sidewalk
# the stripes across the middle road move with it: the old crossing (its centre dashes stay) and the new
OLD_CROSSING = [(-126, -13), (-126, -12), (-124, -15), (-124, -14), (-124, -13), (-124, -12), (-122, -15), (-122, -14),
                (-122, -12)]
NEW_CROSSING = [(x, z) for x in (-119, -117, -115) for z in range(-15, -11)]
# the flower stall at the shop's crossing (canopy, posts, counter and flowers: x0, x1, y0, y1, z0, z1) moves by (dx, dz)
# onto the paved corner west of the x=-5 road's head
STALL = ((-9, -5, 81, 84, -87, -84), (-8, 4))
# lamp posts the new asphalt reaches (iron street lamps, garden lamps, the battle field's quartz posts), each moved by
# (dx, dz) to the sidewalk beside the road
LAMPS = [((66, -84), (-2, 0)), ((69, -82), (2, 0)), ((66, -74), (-2, 0)), ((69, -68), (-4, 0)), ((70, -55), (-4, 0)),
         ((71, -42), (-5, 0)), ((72, -29), (-6, 0)), ((75, 6), (-2, 0)), ((77, 19), (-4, 0)), ((78, 32), (-3, 0)),
         ((-4, -83), (2, -1)), ((-3, -18), (2, -2)), ((-5, -17), (4, 0))]


def mend(p, Z, stats):
    """All of the above into plan p, after the town is paved: asphalt first, then what stood on it moved off."""
    n = sum(fill(p, *box) for box in GAPS)
    for x0, x1, z0, z1 in PLAIN:
        for z in range(z0, z1 + 1):
            for x in range(x0, x1 + 1):
                p.put(x, 80, z, ASPHALT); n += 1
    n += east(p)
    n += west(p)
    for x, z in SIDEWALK:
        p.put(x, 80, z, WALK)
    for x0, x1, z0, z1 in CROSSINGS:
        for z in range(z0, z1 + 1):
            for x in range(x0, x1 + 1):
                p.put(x, 80, z, LINE if (x - x0) % 2 == 0 else ASPHALT)
    stats['asphalt_cells'] = n
    (x0, x1, y0, y1, z0, z1), (dx, dz) = STALL
    stats['stall_blocks'] = move(p, [(x, y, z) for x in range(x0, x1 + 1) for y in range(y0, y1 + 1)
                                     for z in range(z0, z1 + 1)], dx, dz)
    stats['lamps_moved'] = sum(1 for (x, z), (dx, dz) in LAMPS if lamp(p, x, z, dx, dz))


def fill(p, x0, x1, z0, z1):
    """Asphalt over whatever pavement is there; stripes and asphalt stay as they are."""
    n = 0
    for z in range(z0, z1 + 1):
        for x in range(x0, x1 + 1):
            if p.state(x, 80, z) not in ROAD and p.put(x, 80, z, ASPHALT): n += 1
    return n


def east_edge(z):
    for z0, z1, xw in EAST:
        if z0 <= z <= z1: return xw
    return None


def east_band():
    z0, z1 = EAST[0][0], EAST[-1][1]
    return {(x, z) for z in range(z0, z1 + 1) for x in range(east_edge(z), east_edge(z) + 5)}


def east(p):
    """The east road paved again along EAST, with a centre dash of two blocks every nine where it runs straight; what
    is left of the old one becomes sidewalk."""
    band = east_band()
    for z in range(EAST[0][0], EAST[-1][1] + 1):
        for x in range(EAST_X[0], EAST_X[1] + 1):
            if (x, z) in band or (-15 <= z <= -11 and x < 75): continue    # (the middle road's own end stays)
            if p.state(x, 80, z) in ROAD:
                p.put(x, 80, z, WALK)
    n = 0
    for x, z in band:
        k = z if z % 9 == 0 else z - 1
        straight = east_edge(k) == east_edge(k + 1)
        dash = x == east_edge(z) + 2 and z % 9 in (0, 1) and straight and -80 <= z <= 90 and not -19 <= z <= -9
        p.put(x, 80, z, LINE if dash else ASPHALT); n += 1
    for x, z in EAST_STRIPES:
        p.put(x, 80, z, LINE)
    return n


def west_band():
    za, xa, zb, xb = WEST_BEND
    band = set()
    for z in range(za, WEST_END + 1):
        t = min(max((z - za) / (zb - za), 0.0), 1.0)
        xw = int(round(xa + (xb - xa) * t))
        band |= {(x, z) for x in range(xw, xw + 5)}
    return band


def west(p):
    """The west road bent east round the skin remover into the middle road; the old crossing's stripes moved."""
    band = west_band()
    x0, x1, z0, z1 = WEST_OLD
    for z in range(z0, z1 + 1):
        for x in range(x0, x1 + 1):
            if (x, z) not in band and p.state(x, 80, z) in ROAD:
                p.put(x, 80, z, WALK)
    n = 0
    for x, z in sorted(band):
        if p.put(x, 80, z, ASPHALT): n += 1
    for x, z in OLD_CROSSING:
        p.put(x, 80, z, ASPHALT)
    for x, z in NEW_CROSSING:
        p.put(x, 80, z, LINE)
    return n


def cells():
    """Every column the mended roads pave (for the town to keep its new trees' crowns off them)."""
    out = east_band() | west_band()
    for x0, x1, z0, z1 in GAPS + PLAIN + CROSSINGS:
        out |= {(x, z) for x in range(x0, x1 + 1) for z in range(z0, z1 + 1)}
    return out


def move(p, cells, dx, dz):
    """Blocks moved by (dx, dz): each non-air cell to its new place (which must be air), then the old ones cleared."""
    src = {c: p.state(*c) for c in cells if p.name(*c) != 'minecraft:air'}
    dst = {(x + dx, y, z + dz) for (x, y, z) in src}
    blocked = [c for c in dst if c not in src and p.name(*c) != 'minecraft:air']
    if blocked:
        raise ValueError(f'cannot move {len(src)} blocks by {dx},{dz}: {sorted(blocked)[:4]} taken')
    for c in src:
        if c not in dst:
            p.put(*c, 'air')
    for (x, y, z), s in src.items():
        p.put(x + dx, y, z + dz, s)
    return len(src)


def lamp(p, x, z, dx, dz):
    """A lamp post at (x, z), with the lanterns hanging from its arms, moved by (dx, dz) to the sidewalk."""
    cells = [(x, y, z) for y in range(81, 89) if short(p.name(x, y, z)) in LAMP]
    for ax in (-1, 0, 1):
        for az in (-1, 0, 1):
            if (ax, az) != (0, 0) and short(p.name(x + ax, 85, z + az)) == 'lantern':
                cells += [(x + ax, 85, z + az), (x + ax, 86, z + az)]
    if not cells:
        raise ValueError(f'no lamp post at {x},{z}')
    feet = {(cx + dx, cz + dz) for cx, cy, cz in cells if cy == 81}
    if any(p.state(fx, 80, fz) in ROAD for fx, fz in feet):
        raise ValueError(f'lamp {x},{z} would stand on the road at {sorted(feet)}')
    return move(p, cells, dx, dz)
