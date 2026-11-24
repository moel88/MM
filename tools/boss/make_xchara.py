#!/usr/bin/env python3
"""X Chara, the boss (2.137.0): their skin, what of it glows, and their 3D outfit.

usage: python3 -I tools/boss/make_xchara.py [OUT_DIR]

User request: "이제 엑스차라 보스를 만들자 죠타로 처럼 스킨에 디테일이나 장식을 더 해서 꾸며주고 ... 밤에도 보랏빛으로
빛이나게 만들어줘 눈에는 붉은색 빛과 아우라를 추가해주고". Their body is drawn in the skin the user sent
(tools/boss/xchara_skin.png, a slim-armed skin, copied unchanged), and over it, following their limbs, an outfit of
boxes that stand out of it (mine to make up, in the skin's own colours: its white and lavender, its navy, its violet
streak): the hair's fringe, side locks, tufts and back spikes with its violet streak raised, an X of a hair clip, a
high navy collar and a scarf's two tails down the back, jacket fronts either side of the sweater, the heart locket on
its chain, shoulder pieces, an X across the back, a belt with an X for a buckle, a short torn coat's tails, cuffs,
boot tops and shoes.

It writes, under src/main/resources/assets/celestialfall (or OUT_DIR, leaving the game's files alone):
  textures/entity/boss/xchara.png              the skin
  textures/entity/boss/xchara_glow.png         what of the skin shines by itself (drawn again, added, at full light):
                                               its violet streaks, its purple marks, the red eye, the locket; and
                                               all of it a faint purple, so its body shows purple in the dark
  textures/entity/boss/xchara_outfit.png       the outfit's texture, 2 pixels to the model's pixel (declared 64 x 64)
  textures/entity/boss/xchara_outfit_glow.png  what of the outfit shines (the same layout)
  bosses/xchara_outfit.json                    the outfit's boxes: bone, pivot, rotation, box, uv, swing

Every box is painted face by face from the materials below (a material gives a pixel's colour, or None for a hole,
and what of it glows); the seed is fixed, so the files come out the same every time. Do not edit the outputs by hand.
"""
import json
import math
import os
import random
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
ASSETS = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'celestialfall')
UNITS = 64
SCALE = 2
PAD = 1

# ---- the outfit: (name, bone, pivot, rotation (pitch, yaw, roll degrees), box from, box size, material, swing)
# Model space: pixels, +y down, -z the front, +x the body's own left. Bones: head, body, right_arm, left_arm (each
# with elbow, 4 down the arm), right_leg, left_leg (each with knee, 6 down the leg). The arms are slim: the right
# arm's box runs x -2..1, the left's -1..2.
PARTS = []


def part(name, bone, pivot, rot, frm, size, mat, swing=None):
    PARTS.append(dict(name=name, bone=bone, pivot=list(pivot), rotation=list(rot), frm=list(frm), size=list(size), mat=mat, swing=swing))


# the hair: a fringe in three tufts over the brow, a lock down either cheek, two tufts standing off the crown, three
# spikes down the back, and the skin's violet streak raised along the top
part('fringe_mid', 'head', (0, -8, -4.5), (6, 0, 0), (-1.5, 0, -0.25), (3.0, 3.0, 0.5), 'hair')
part('fringe_right', 'head', (-2.75, -8, -4.5), (6, 0, 10), (-1.5, 0, -0.25), (2.5, 3.5, 0.5), 'hair')
part('fringe_left', 'head', (2.75, -8, -4.5), (6, 0, -10), (-1.0, 0, -0.25), (2.5, 3.5, 0.5), 'hair')
part('lock_right', 'head', (-4.25, -6, -2.5), (0, 0, 5), (-0.5, 0, -1.0), (0.5, 6.0, 2.0), 'hair_tip')
part('lock_left', 'head', (4.25, -6, -2.5), (0, 0, -5), (0, 0, -1.0), (0.5, 6.0, 2.0), 'hair_tip')
part('tuft_a', 'head', (-1.25, -8, -1.0), (-14, 0, -20), (-0.75, -2.0, -0.5), (1.5, 2.0, 1.0), 'hair')
part('tuft_a_tip', 'head', (-2.0, -9.8, -1.4), (-22, 0, -48), (-0.5, -2.0, -0.25), (1.0, 2.0, 0.5), 'hair_tip_up')
part('tuft_b', 'head', (1.75, -8, 0.5), (-6, 0, 24), (-0.75, -1.5, -0.5), (1.5, 1.5, 1.0), 'hair')
part('tuft_b_tip', 'head', (2.35, -9.3, 0.4), (-10, 0, 52), (-0.5, -1.5, -0.25), (1.0, 1.5, 0.5), 'hair_tip_up')
for i, (x, yaw) in enumerate(((-2.5, -14), (0, 0), (2.5, 14))):
    part('spike_%d' % i, 'head', (x, -4.0, 4.25), (22, yaw, 0), (-1.0, 0, -0.25), (2.0, 4.5 if i == 1 else 3.5, 0.5), 'hair_tip')
part('streak', 'head', (0, 0, 0), (0, 0, 0), (-1.25, -8.5, -4.5), (1.0, 0.5, 6.5), 'streak')
# an X of a hair clip on the fringe's left
part('clip_a', 'head', (2.5, -6.25, -4.85), (0, 0, 45), (-0.25, -1.0, -0.25), (0.5, 2.0, 0.5), 'neon')
part('clip_b', 'head', (2.5, -6.25, -4.85), (0, 0, -45), (-0.25, -1.0, -0.25), (0.5, 2.0, 0.5), 'neon')
# a high navy collar round the neck (open at the front), and a scarf's two tails down the back
part('collar_back', 'body', (0, 0, 0), (0, 0, 0), (-4.5, -0.75, 1.75), (9.0, 2.0, 1.0), 'navy')
part('collar_left', 'body', (0, 0, 0), (0, 0, 0), (3.5, -0.75, -2.75), (1.0, 2.0, 5.5), 'navy')
part('collar_right', 'body', (0, 0, 0), (0, 0, 0), (-4.5, -0.75, -2.75), (1.0, 2.0, 5.5), 'navy')
part('collar_front_left', 'body', (0, 0, 0), (0, 0, 0), (1.25, -0.75, -2.75), (2.5, 2.0, 1.0), 'navy')
part('collar_front_right', 'body', (0, 0, 0), (0, 0, 0), (-3.75, -0.75, -2.75), (2.5, 2.0, 1.0), 'navy')
part('scarf_left', 'body', (3.25, 0.75, 2.75), (0, 0, 0), (-0.75, 0, 0), (1.5, 10.0, 0.5), 'scarf', 'back_left')
part('scarf_right', 'body', (-3.25, 0.75, 2.75), (0, 0, 0), (-0.75, 0, 0), (1.5, 8.0, 0.5), 'scarf', 'back_right')
# the jacket's fronts either side of the sweater, the heart locket on its chain
part('front_left', 'body', (0, 0, 0), (0, 0, 0), (1.75, 1.25, -2.75), (2.5, 8.5, 0.5), 'front_left')
part('front_right', 'body', (0, 0, 0), (0, 0, 0), (-4.25, 1.25, -2.75), (2.5, 8.5, 0.5), 'front_right')
for i in range(3):
    t = (i + .5) / 3
    part('chain_left_%d' % i, 'body', (1.75 - 1.5 * t, 1.5 + 4.0 * t, -2.6), (0, 0, 20), (-0.25, -0.75, -0.25), (0.5, 1.5, 0.5), 'gold')
    part('chain_right_%d' % i, 'body', (-1.75 + 1.5 * t, 1.5 + 4.0 * t, -2.6), (0, 0, -20), (-0.25, -0.75, -0.25), (0.5, 1.5, 0.5), 'gold')
part('locket', 'body', (0, 0, 0), (0, 0, 0), (-1.75, 5.25, -3.25), (3.5, 3.0, 0.5), 'heart')
# a piece on either shoulder
part('shoulder_right', 'right_arm', (0, 0, 0), (0, 0, 0), (-2.5, -2.5, -2.5), (4.0, 1.5, 5.0), 'plate')
part('shoulder_left', 'left_arm', (0, 0, 0), (0, 0, 0), (-1.5, -2.5, -2.5), (4.0, 1.5, 5.0), 'plate')
# an X across the back
part('back_x_a', 'body', (0, 5.0, 2.3), (0, 0, 34), (-0.5, -3.25, 0), (1.0, 6.5, 0.5), 'neon')
part('back_x_b', 'body', (0, 5.0, 2.3), (0, 0, -34), (-0.5, -3.25, 0), (1.0, 6.5, 0.5), 'neon')
# the belt, an X for its buckle
part('belt', 'body', (0, 0, 0), (0, 0, 0), (-4.5, 9.75, -2.5), (9.0, 1.5, 5.0), 'belt')
part('buckle', 'body', (0, 0, 0), (0, 0, 0), (-1.0, 9.5, -3.0), (2.0, 2.0, 0.5), 'buckle')
# a short coat's tails, torn at the hem, that swing with the legs (BossOutfit)
part('tail_back_left', 'body', (2.25, 11, 2.5), (0, 0, 0), (-2.25, 0, 0), (4.5, 6.5, 0.5), 'tail', 'back_left')
part('tail_back_right', 'body', (-2.25, 11, 2.5), (0, 0, 0), (-2.25, 0, 0), (4.5, 6.5, 0.5), 'tail', 'back_right')
part('tail_left', 'body', (4.5, 11, 0), (0, 0, 0), (0, 0, -2.5), (0.5, 5.5, 5.0), 'tail', 'side_left')
part('tail_right', 'body', (-4.5, 11, 0), (0, 0, 0), (-0.5, 0, -2.5), (0.5, 5.5, 5.0), 'tail', 'side_right')
# cuffs above the hands, boot tops and shoes
part('cuff_right', 'right_elbow', (0, 0, 0), (0, 0, 0), (-2.5, 2.75, -2.5), (4.0, 2.0, 5.0), 'cuff')
part('cuff_left', 'left_elbow', (0, 0, 0), (0, 0, 0), (-1.5, 2.75, -2.5), (4.0, 2.0, 5.0), 'cuff')
part('boot_right', 'right_knee', (0, 0, 0), (0, 0, 0), (-2.25, 1.0, -2.25), (4.5, 1.5, 4.5), 'cuff')
part('boot_left', 'left_knee', (0, 0, 0), (0, 0, 0), (-2.25, 1.0, -2.25), (4.5, 1.5, 4.5), 'cuff')
part('shoe_right', 'right_knee', (0, 0, 0), (0, 0, 0), (-2.25, 4.5, -3.25), (4.5, 1.5, 5.5), 'shoe')
part('shoe_left', 'left_knee', (0, 0, 0), (0, 0, 0), (-2.25, 4.5, -3.25), (4.5, 1.5, 5.5), 'shoe')


# ---- painting

def clamp(v):
    return max(0, min(255, int(round(v))))


def shade(rgb, k):
    return tuple(clamp(c * k) for c in rgb)


def mix(a, b, t):
    return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(3))


def hexrgb(h):
    return ((h >> 16) & 255, (h >> 8) & 255, h & 255)


# the skin's own colours
WHITE = hexrgb(0xffffff)
HAIR = hexrgb(0xfbecff)
PALE = hexrgb(0xf7dcff)
LAVENDER = hexrgb(0xe8c2ff)
LILAC = hexrgb(0xcfa4ff)
VIOLET = hexrgb(0x8f6fff)
NAVY = hexrgb(0x151534)
NAVY_LIT = hexrgb(0x241c46)
PLUM = hexrgb(0x372358)
PURPLE = hexrgb(0x492a6a)
NEON = hexrgb(0xb060ff)
NEON_HOT = hexrgb(0xf0d8ff)
GOLD = hexrgb(0xffd3a0)
GOLD_LIT = hexrgb(0xfff7dc)
PINK = hexrgb(0xffaaa8)
RED = hexrgb(0xff2a5e)
BLACK = (0, 0, 0)


def noise(c, rnd, k=3):
    return tuple(clamp(x + rnd.uniform(-k, k)) for x in c)


def hair(face, u, v, w, h, rnd, tip=False):
    """White hair, lavender in its shadows: strands down the tuft, lilac toward its tip (violet at a lock's very end)."""
    strand = (int(u * w * 2) % 2) == 0
    c = HAIR if strand else PALE
    if face in ('front', 'back', 'left', 'right'):
        c = mix(c, LAVENDER, max(0, v - .45) * 1.1)
        if tip and (1 - v) * h < 1.0:
            c = mix(LILAC, VIOLET, .6 if (1 - v) * h < .5 else .2)
        if face in ('left', 'right'):
            c = shade(c, .95)
    elif face == 'bottom':
        c = LILAC if not tip else VIOLET
    glow = shade(VIOLET, .7) if tip and face != 'top' and (1 - v) * h < .5 else None
    return noise(c, rnd, 2), glow


def hair_up(face, u, v, w, h, rnd):
    """A tuft's tip, standing up: white going lilac and violet toward its top end."""
    strand = (int(u * w * 2) % 2) == 0
    c = HAIR if strand else PALE
    if face == 'top':
        return VIOLET, shade(VIOLET, .7)
    if face == 'bottom':
        return PALE, None
    if v * h < .5:
        return mix(LILAC, VIOLET, .7), shade(VIOLET, .7)
    if v * h < 1.0:
        c = mix(c, LILAC, .7)
    return noise(c, rnd, 2), None


def streak(face, u, v, w, h, rnd):
    """The skin's violet streak, raised: it shines."""
    c = mix(VIOLET, NEON_HOT, .25 if face == 'top' else 0)
    return noise(c, rnd, 2), shade(VIOLET, .9)


def neon(face, u, v, w, h, rnd):
    """A bar of X Chara's neon: a pale core down its middle, violet at its edges; all of it shines."""
    across = abs((u if w <= h else v) - .5) * 2
    c = mix(NEON_HOT, NEON, min(1, across * 1.3)) if face in ('front', 'back') else NEON
    return c, mix(NEON, NEON_HOT, max(0, .6 - across))


def navy(face, u, v, w, h, rnd):
    """Navy cloth: a lit top edge, a violet line of light along it."""
    c = NAVY
    if face == 'top':
        c = mix(NAVY, NAVY_LIT, .7)
    elif face == 'bottom':
        c = shade(NAVY, .7)
    else:
        if v * h < .5:
            return mix(VIOLET, NEON, .3), shade(VIOLET, .55)
        if v * h < 1.0:
            c = NAVY_LIT
        elif (1 - v) * h < .5:
            c = shade(NAVY, .75)
    return noise(c, rnd), None


def scarf(face, u, v, w, h, rnd):
    """The scarf's tails: plum going violet down them, a pale stripe, their ends torn into pixels (holes), glowing."""
    y = v * h
    if face in ('front', 'back'):
        end = h - y
        # the torn end: columns of different lengths, a loose pixel or two beyond
        col = int(u * w * 2)
        cut = (col * 7 + int(w * 3)) % 5 * .5
        if end < cut:
            return None, None
        c = mix(PLUM, PURPLE, v)
        if abs(u - .5) < .12:
            c = mix(c, LILAC, .7)
        if end < cut + 1.0:
            return mix(NEON, NEON_HOT, .3), shade(NEON, .8)
        return noise(c, rnd), (shade(VIOLET, .25) if abs(u - .5) < .12 else None)
    if face == 'bottom':
        return None, None
    return noise(shade(PLUM, .9), rnd), None


def front(face, u, v, w, h, rnd, inner_left):
    """A jacket front: lavender, white along its inner edge (by the sweater), a violet seam a pixel in, a navy hem."""
    c = LAVENDER
    glow = None
    if face == 'front':
        x = u * w
        d = x if inner_left else w - x
        if d < .5:
            c = WHITE
        elif d < 1.0:
            c, glow = VIOLET, shade(VIOLET, .5)
        elif (1 - v) * h < .75:
            c = NAVY_LIT
        else:
            c = mix(LAVENDER, PALE, .5 if int(v * h * 2) % 4 == 0 else 0)
    elif face == 'top':
        c = PALE
    else:
        c = shade(LAVENDER, .85)
    return noise(c, rnd, 2), glow


def gold(face, u, v, w, h, rnd):
    """A link of the locket's chain: pale gold."""
    c = mix(GOLD_LIT, GOLD, v) if face != 'bottom' else shade(GOLD, .75)
    return noise(c, rnd, 2), shade(GOLD, .2)


HEART = ('.XX.XX.',
         'XXXXXXX',
         'XXXXXXX',
         '.XXXXX.',
         '..XXX..',
         '...X...')


def heart(face, u, v, w, h, rnd):
    """The heart locket, texel by texel (7 by 6): a gold rim, pink within going red toward its point, a glint; it shines."""
    if face not in ('front', 'back'):
        return None, None
    ix, iy = min(6, int(u * 7)), min(5, int(v * 6))
    if HEART[iy][ix] != 'X':
        return None, None
    def inside(x, y):
        return 0 <= x < 7 and 0 <= y < 6 and HEART[y][x] == 'X'
    rim = not all(inside(ix + dx, iy + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
    if rim:
        c = mix(GOLD_LIT, GOLD, iy / 5)
        return c, shade(GOLD, .45)
    c = mix(PINK, RED, max(0, (iy - 1) / 4))
    if (ix, iy) == (1, 1) or (ix, iy) == (2, 1):
        c = GOLD_LIT
    return c, shade(mix(PINK, RED, .5), .85)


def plate(face, u, v, w, h, rnd):
    """A shoulder piece: navy, a lit top, a violet edge of light round it, an X pressed into its top."""
    if face == 'top':
        x, z = (u - .5) * w, (v - .5) * h
        if abs(abs(x) - abs(z)) < .3 and max(abs(x), abs(z)) < 1.6:
            return NEON, shade(NEON, .7)
        return noise(mix(NAVY, NAVY_LIT, .6), rnd), None
    if face == 'bottom':
        return shade(NAVY, .7), None
    if (1 - v) * h < .5:
        return mix(VIOLET, NEON, .3), shade(VIOLET, .55)
    return noise(NAVY_LIT if v * h < .5 else NAVY, rnd), None


def belt(face, u, v, w, h, rnd):
    """The belt: navy leather, a violet line of light along its middle."""
    y = v * h
    if face in ('top',):
        return NAVY_LIT, None
    if face == 'bottom':
        return shade(NAVY, .6), None
    if abs(y - h / 2) < .25:
        return VIOLET, shade(VIOLET, .5)
    return noise(NAVY_LIT if y < .5 else NAVY, rnd), None


def buckle(face, u, v, w, h, rnd):
    """The buckle: a navy plate with a neon X on it."""
    if face != 'front':
        return NAVY_LIT, None
    x, y = (u - .5) * w, (v - .5) * h
    if abs(abs(x) - abs(y)) < .28:
        return NEON_HOT, NEON
    if min(u * w, (1 - u) * w, v * h, (1 - v) * h) < .25:
        return VIOLET, shade(VIOLET, .5)
    return NAVY, None


def tail(face, u, v, w, h, rnd):
    """A coat's tail: navy, plum folds down it, a violet line a pixel in from its hem, the hem torn into pixels."""
    y = v * h
    if face == 'bottom':
        return None, None
    if face in ('front', 'back', 'left', 'right'):
        end = h - y
        col = int(u * w * 2)
        cut = (col * 5 + int(h * 2)) % 4 * .5
        if end < cut:
            return None, None
        if end < cut + .5:
            return mix(NEON, NEON_HOT, .3), shade(NEON, .8)
        if cut + 1.0 <= end < cut + 1.5:
            return VIOLET, shade(VIOLET, .45)
        fold = .5 + .5 * math.sin(u * w * 2.4 + 1.3)
        c = mix(NAVY, PLUM, fold * .55)
        if y < .5:
            c = NAVY_LIT
        return noise(c, rnd), None
    return NAVY_LIT, None


def cuff(face, u, v, w, h, rnd):
    """A cuff or a boot's top: plum, a neon line round its upper edge."""
    y = v * h
    if face == 'top':
        return mix(PLUM, PURPLE, .5), None
    if face == 'bottom':
        return shade(PLUM, .7), None
    if y < .5:
        return mix(NEON, NEON_HOT, .2), shade(NEON, .7)
    return noise(PURPLE if y < 1.0 else PLUM, rnd), None


def shoe(face, u, v, w, h, rnd):
    """A navy shoe: a shine along its toe's top, a violet sole."""
    y = v * h
    if face == 'bottom':
        return shade(VIOLET, .5), None
    if face in ('front', 'back', 'left', 'right') and y > h - .5:
        return VIOLET, shade(VIOLET, .4)
    c = NAVY
    if face == 'top':
        c = mix(NAVY, NAVY_LIT, .9 if v < .35 else .4)
    return noise(c, rnd), None


MATERIALS = {
    'hair': hair, 'hair_tip': lambda f, u, v, w, h, r: hair(f, u, v, w, h, r, True), 'hair_tip_up': hair_up, 'streak': streak, 'neon': neon, 'navy': navy,
    'scarf': scarf, 'gold': gold, 'heart': heart, 'plate': plate, 'belt': belt, 'buckle': buckle, 'tail': tail, 'cuff': cuff, 'shoe': shoe,
    'front_left': lambda f, u, v, w, h, r: front(f, u, v, w, h, r, True),
    'front_right': lambda f, u, v, w, h, r: front(f, u, v, w, h, r, False),
}


def faces(uv, size):
    """The six faces of a box with its texture at uv (units): (name, x, y, w, h) in units (Minecraft's box layout)."""
    u, v = uv
    w, h, d = size
    return [('top', u + d, v, w, d), ('bottom', u + d + w, v, w, d), ('right', u, v + d, d, h),
            ('front', u + d, v + d, w, h), ('left', u + d + w, v + d, d, h), ('back', u + 2 * d + w, v + d, w, h)]


def pack(parts):
    """Shelf-packs every box's texture into the atlas (whole units, PAD between)."""
    x = y = row = 0
    for p in sorted(parts, key=lambda p: -(p['size'][2] + p['size'][1])):
        w, h, d = p['size']
        fw, fh = math.ceil(2 * d + 2 * w), math.ceil(d + h)
        if x + fw > UNITS:
            x, y, row = 0, y + row + PAD, 0
        if y + fh > UNITS:
            raise SystemExit('outfit atlas full: ' + p['name'])
        p['uv'] = [x, y]
        x += fw + PAD
        row = max(row, fh)


def paint(parts):
    img = Image.new('RGBA', (UNITS * SCALE, UNITS * SCALE), (0, 0, 0, 0))
    glow = Image.new('RGBA', (UNITS * SCALE, UNITS * SCALE), (0, 0, 0, 255))
    px, gx = img.load(), glow.load()
    for p in parts:
        rnd = random.Random(p['name'])
        w, h, d = p['size']
        fn = MATERIALS[p['mat']]
        for name, fx, fy, fw, fh in faces(p['uv'], p['size']):
            if fw <= 0 or fh <= 0:
                continue
            for iy in range(int(fy * SCALE), int(math.ceil((fy + fh) * SCALE))):
                for ix in range(int(fx * SCALE), int(math.ceil((fx + fw) * SCALE))):
                    cx, cy = (ix + .5) / SCALE, (iy + .5) / SCALE
                    if not (fx <= cx < fx + fw and fy <= cy < fy + fh):
                        continue
                    u, v = (cx - fx) / fw, (cy - fy) / fh
                    sw = {'top': w, 'bottom': w, 'front': w, 'back': w, 'right': d, 'left': d}[name]
                    sh = {'top': d, 'bottom': d}.get(name, h)
                    c, g = fn(name, u, v, sw, sh, rnd)
                    if c is None:
                        continue
                    px[ix, iy] = (c[0], c[1], c[2], 255)
                    # (all of it a faint purple by itself, as the skin's: it shows in the dark)
                    base = mix(BLACK, mix(c, VIOLET, .72), DIM)
                    gx[ix, iy] = tuple(max(base[i], g[i]) for i in range(3)) + (255,) if g else base + (255,)
    return img, glow


# how much of everything shines by itself in the dark (the rest of its light is its marks')
DIM = .5
# the skin's colours that shine: its violet streaks, its purple marks, its red eye, its locket
SKIN_GLOW = {
    0x8f6fff: .95, 0x8f6eff: .95, 0xb189ff: .7, 0xb188ff: .7, 0x492a6a: .9, 0x5c317c: 1.0,
    0xe50094: 1.0, 0xff4d79: 1.0, 0xffd3c2: .6, 0xfff7dc: .5, 0xffaaa8: .7,
}


def skin_glow(skin):
    """What of the skin shines: the colours above at their strength, the red eye redder; the rest a faint purple."""
    out = Image.new('RGBA', skin.size, (0, 0, 0, 255))
    sp, op = skin.load(), out.load()
    for y in range(skin.size[1]):
        for x in range(skin.size[0]):
            r, g, b, a = sp[x, y]
            if a == 0:
                continue
            key = (r << 16) | (g << 8) | b
            c = (r, g, b)
            base = mix(BLACK, mix(c, VIOLET, .72), DIM)
            if key in (0xe50094, 0xff4d79):
                c = RED
            elif key in (0x492a6a, 0x5c317c):
                c = NEON
            k = SKIN_GLOW.get(key)
            lit = shade(c, k) if k else base
            op[x, y] = tuple(max(base[i], lit[i]) for i in range(3)) + (255,)
    return out


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else ASSETS
    pack(PARTS)
    tex = os.path.join(out, 'textures', 'entity', 'boss')
    os.makedirs(tex, exist_ok=True)
    os.makedirs(os.path.join(out, 'bosses'), exist_ok=True)
    skin = Image.open(os.path.join(HERE, 'xchara_skin.png')).convert('RGBA')
    skin.save(os.path.join(tex, 'xchara.png'))
    skin_glow(skin).save(os.path.join(tex, 'xchara_glow.png'))
    img, glow = paint(PARTS)
    img.save(os.path.join(tex, 'xchara_outfit.png'))
    glow.save(os.path.join(tex, 'xchara_outfit_glow.png'))
    model = {
        'texture': [UNITS, UNITS],
        'parts': [{'name': p['name'], 'bone': p['bone'], 'pivot': p['pivot'], 'rotation': p['rotation'],
                   'from': p['frm'], 'size': p['size'], 'uv': p['uv'], 'swing': p['swing']} for p in PARTS],
    }
    with open(os.path.join(out, 'bosses', 'xchara_outfit.json'), 'w', encoding='utf-8') as f:
        json.dump(model, f, indent=1)
        f.write('\n')
    print(len(PARTS), 'parts; atlas used to row', max(p['uv'][1] + math.ceil(p['size'][2] + p['size'][1]) for p in PARTS), 'of', UNITS)


if __name__ == '__main__':
    main()
