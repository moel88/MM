"""The texture of Gold Experience's fly (2.151.0, T with the hand empty): a housefly, drawn for the model in
client/stand/GoldFlyModel (its boxes and where each one's skin is on the sheet).
usage: python3 -I tools/stand/make_gold_fly.py [folder]   (the game's file by default)

A grey thorax with four black stripes down its back, a chequered grey-brown abdomen with a dark line down its middle,
a grey face, big dark-red eyes with lighter facets, clear grey-blue wings with dark veins (see-through), black legs and
bristles. Fixed seed: the same sheet every time.
"""
import os
import random
import sys
from PIL import Image

W = H = 32
img = Image.new('RGBA', (W, H), (0, 0, 0, 0))
px = img.load()
rng = random.Random(151)


def fill(x0, y0, x1, y1, color):
    for y in range(y0, y1):
        for x in range(x0, x1):
            px[x, y] = color


def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c[:3]) + (c[3],)


DARK = (34, 33, 38, 255)
GREY = (138, 136, 142, 255)
BLACK = (18, 18, 21, 255)

# the thorax (box 3x3x3 at 0,0): dark all round, its back grey with four black stripes down it
fill(0, 0, 12, 6, DARK)
fill(3, 0, 6, 3, GREY)
for x in (3, 5):
    for y in range(0, 3):
        px[x, y] = BLACK
for y in range(0, 3):
    px[4, y] = shade(GREY, .78)
# its sides: a little grey bloom high up
for x in list(range(0, 3)) + list(range(6, 12)):
    px[x, 3] = shade(GREY, .62)

# the head (box 2x2x2 at 0,6): dark, the face grey
fill(0, 6, 8, 10, DARK)
fill(2, 8, 4, 10, shade(GREY, 1.08))
fill(2, 6, 4, 8, shade(GREY, .7))

# the eyes (box 1x2x2 at 8,6): dark red, lighter facets, a glint
EYE = (110, 14, 14, 255)
fill(8, 6, 14, 10, EYE)
for y in range(6, 10):
    for x in range(8, 14):
        if (x + y) % 2 == 0:
            px[x, y] = (150, 30, 28, 255)
px[11, 8] = (255, 236, 220, 255)
px[9, 8] = (196, 70, 60, 255)

# the abdomen (box 3x2x4 at 0,10): chequered grey-brown on its back with a dark line down the middle, dark below
fill(0, 10, 14, 16, DARK)
for y in range(10, 14):
    for x in range(4, 7):
        px[x, y] = (110, 104, 94, 255) if (x + y) % 2 == 0 else (66, 62, 56, 255)
for y in range(10, 14):
    px[5, y] = BLACK
for x in list(range(0, 4)) + list(range(7, 14)):
    for y in range(14, 16):
        if rng.random() < .3:
            px[x, y] = shade(GREY, .5)

# the wings (flat box 5x0x3 at 0,16: its top at 3..8 and its underside at 8..13, rows 16..18): clear grey-blue, veined
for x0 in (3, 8):
    for y in range(16, 19):
        for x in range(x0, x0 + 5):
            px[x, y] = (200, 212, 228, 92)
    # the veins: one along the front edge, two out from the root
    for x in range(x0, x0 + 5):
        px[x, 16] = (58, 58, 70, 200)
    px[x0 + 1, 17] = (58, 58, 70, 170)
    px[x0 + 3, 18] = (58, 58, 70, 170)
    px[x0 + 2, 17] = (230, 236, 248, 120)

# the legs (box 1x3x1 at 24,0): black
fill(24, 0, 28, 4, BLACK)

root = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'src', 'main', 'resources', 'assets', 'celestialfall', 'textures', 'entity')
os.makedirs(root, exist_ok=True)
out = os.path.join(root, 'gold_fly.png')
img.save(out, optimize=True)
print('wrote', os.path.normpath(out))
