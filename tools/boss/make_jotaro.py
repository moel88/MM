#!/usr/bin/env python3
"""Kujo Jotaro, the boss (2.69.0): his skin and his 3D outfit.

usage: python3 -I tools/boss/make_jotaro.py [OUT_DIR]

User request: "스킨은 이거 기반으로 하되 옷이나 모자나 장식을 더 꾸며서 입체감있게 만들어주고". His body is drawn in the
skin the user sent (tools/boss/jotaro_skin.png, copied unchanged), and over it, following his limbs, an outfit of
boxes that stand out of it: the cap's crown, its visor and its gold badge, the hair the cap's back tears into, the
gakuran's collar and the open coat's front edges, the gold chain hanging from the left of the collar, the gold
buttons, the two belts (one slung low), the coat's long skirt in six flaps that swing with his legs (BossOutfit), the
cuffs and the shoes.

It writes, under src/main/resources/assets/celestialfall (or OUT_DIR, leaving the game's files alone):
  textures/entity/boss/jotaro.png         the skin
  textures/entity/boss/jotaro_outfit.png  the outfit's texture, 2 pixels to the model's pixel (declared 64 x 64)
  bosses/jotaro_outfit.json               the outfit's boxes: bone, pivot, rotation, box, uv, material, swing

Every box is painted face by face (each pixel knows which face of which box it is on, and where on it), from the
materials below; the seed is fixed, so the files come out the same every time. Do not edit the outputs by hand.
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
UNITS = 64          # the texture as the model reads it (uv units)
SCALE = 2           # pixels to a unit
PAD = 1             # units between boxes in the atlas

# ---- the outfit: (name, bone, pivot, rotation (pitch, yaw, roll degrees), box from, box size, material, swing)
# Model space: pixels, +y down, -z the front, +x the body's own left. Bones: head, body, right_arm, left_arm (each
# with elbow), right_leg, left_leg (each with knee). Sizes are multiples of half a pixel (a whole pixel of the texture).
PARTS = []


def part(name, bone, pivot, rot, frm, size, mat, swing=None):
    PARTS.append(dict(name=name, bone=bone, pivot=list(pivot), rotation=list(rot), frm=list(frm), size=list(size), mat=mat, swing=swing))


# the cap: a crown a little over the skin's own cap, its visor out front tilted down, the gold badge, and at its back
# the hair it tears into, three tufts sweeping back and down
part('cap_crown', 'head', (0, 0, 0), (0, 0, 0), (-4.75, -9.25, -4.75), (9.5, 3.0, 9.5), 'cap')
part('cap_visor', 'head', (0, -6.25, -4.75), (14, 0, 0), (-4.5, -0.25, -2.5), (9.0, 0.5, 2.5), 'visor')
part('cap_badge', 'head', (0, 0, 0), (0, 0, 0), (-1.25, -8.75, -5.0), (2.5, 2.0, 0.5), 'badge')
for i, (x, yaw) in enumerate(((-2.75, -16), (0, 0), (2.75, 16))):
    part('cap_tuft_%d' % i, 'head', (x, -6.5, 4.25), (-24, yaw, 0), (-1.0, -0.5, -0.5), (2.0, 1.0, 3.0), 'hair')
# the gakuran: its collar round the top of the body (open at the front), the coat's front edges either side of the
# shirt, the gold chain on the left, the gold buttons on the right
part('collar_back', 'body', (0, 0, 0), (0, 0, 0), (-4.75, -0.25, 1.75), (9.5, 1.5, 1.0), 'cloth')
part('collar_left', 'body', (0, 0, 0), (0, 0, 0), (3.75, -0.25, -2.75), (1.0, 1.5, 5.5), 'cloth')
part('collar_right', 'body', (0, 0, 0), (0, 0, 0), (-4.75, -0.25, -2.75), (1.0, 1.5, 5.5), 'cloth')
part('collar_front_left', 'body', (0, 0, 0), (0, 0, 0), (1.5, -0.25, -2.75), (2.5, 1.5, 1.0), 'cloth')
part('collar_front_right', 'body', (0, 0, 0), (0, 0, 0), (-4.0, -0.25, -2.75), (2.5, 1.5, 1.0), 'cloth')
part('lapel_left', 'body', (0, 0, 0), (0, 0, 0), (1.75, 1.25, -2.75), (2.5, 10.0, 0.5), 'lapel_left')
part('lapel_right', 'body', (0, 0, 0), (0, 0, 0), (-4.25, 1.25, -2.75), (2.5, 10.0, 0.5), 'lapel_right')
for i in range(7):
    t = i / 6
    x = 3.45 - 1.05 * t - .55 * math.sin(math.pi * t)
    y = 1.7 + 6.9 * t
    part('chain_%d' % i, 'body', (x, y, -2.95), (0, 0, 32 if i % 2 else -32), (-0.25, -0.5, -0.25), (0.5, 1.0, 0.5), 'gold')
for i, y in enumerate((2.5, 4.5, 6.5, 8.5)):
    part('button_%d' % i, 'body', (-3.0, y, -2.75), (0, 0, 0), (-0.5, -0.5, -0.5), (1.0, 1.0, 0.5), 'button')
# the belts: one at the waist with its buckle in front, one slung low, tilted, its buckle on the left hip
part('belt', 'body', (0, 0, 0), (0, 0, 0), (-4.75, 9.5, -2.75), (9.5, 1.5, 5.5), 'leather')
part('belt_buckle', 'body', (0, 10.25, -2.75), (0, 0, 0), (-1.0, -0.75, -0.5), (2.0, 1.5, 0.5), 'buckle')
part('belt_low', 'body', (0, 11.25, 0), (0, 0, -8), (-5.0, -0.5, -3.0), (10.0, 1.0, 6.0), 'leather_dark')
part('belt_low_buckle', 'body', (0, 11.25, 0), (0, 0, -8), (2.5, -0.75, -3.5), (1.5, 1.5, 0.5), 'buckle')
# the coat's long skirt, from the waist to below the knees, in flaps that swing with the legs (BossOutfit)
part('skirt_back_left', 'body', (2.25, 12, 2.5), (0, 0, 0), (-2.25, 0, 0), (4.5, 9.0, 0.5), 'skirt', 'back_left')
part('skirt_back_right', 'body', (-2.25, 12, 2.5), (0, 0, 0), (-2.25, 0, 0), (4.5, 9.0, 0.5), 'skirt', 'back_right')
part('skirt_left', 'body', (4.5, 12, 0), (0, 0, 0), (0, 0, -2.5), (0.5, 8.5, 5.0), 'skirt', 'side_left')
part('skirt_right', 'body', (-4.5, 12, 0), (0, 0, 0), (-0.5, 0, -2.5), (0.5, 8.5, 5.0), 'skirt', 'side_right')
part('skirt_front_left', 'body', (3.0, 12, -2.5), (0, 0, 0), (-1.25, 0, -0.5), (2.5, 8.5, 0.5), 'skirt', 'front_left')
part('skirt_front_right', 'body', (-3.0, 12, -2.5), (0, 0, 0), (-1.25, 0, -0.5), (2.5, 8.5, 0.5), 'skirt', 'front_right')
# the cuffs above the hands, two gold buttons on the outside of each
part('cuff_right', 'right_elbow', (0, 0, 0), (0, 0, 0), (-3.5, 3.0, -2.5), (5.0, 1.5, 5.0), 'cloth')
part('cuff_left', 'left_elbow', (0, 0, 0), (0, 0, 0), (-1.5, 3.0, -2.5), (5.0, 1.5, 5.0), 'cloth')
for i, z in enumerate((-1.0, 0.5)):
    part('cuff_button_right_%d' % i, 'right_elbow', (-3.6, 3.75, z), (0, 0, 0), (-0.25, -0.25, -0.25), (0.5, 0.5, 0.5), 'gold')
    part('cuff_button_left_%d' % i, 'left_elbow', (3.6, 3.75, z), (0, 0, 0), (-0.25, -0.25, -0.25), (0.5, 0.5, 0.5), 'gold')
# the shoes, the toe a pixel out in front
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


# the skin's own colours, so the outfit reads as the same coat
CLOTH = hexrgb(0x1b1b20)
CLOTH_LIT = hexrgb(0x34343c)
GOLD = hexrgb(0xe0aa36)
GOLD_LIT = hexrgb(0xffe08a)
GOLD_DARK = hexrgb(0x8a5a12)
LEATHER = hexrgb(0x4a2c1c)
LEATHER_LIT = hexrgb(0x6e4630)
HAIR = hexrgb(0x121218)
HAIR_LIT = hexrgb(0x2c3242)


def cloth(face, u, v, w, h, rnd, base=CLOTH, lit=CLOTH_LIT):
    """Black wool: a faint weave, lighter along the face's top edge (light from above), darker at its foot."""
    n = rnd.uniform(-5, 5) + (3 if (int(u * 2) + int(v * 2)) % 2 else -1)
    if face == 'top':
        c = mix(base, lit, .45)
    elif face == 'bottom':
        c = shade(base, .7)
    else:
        c = base
        if v * h < .5:
            c = mix(c, lit, .55)        # a rim of light along the top
        elif (1 - v) * h < .5:
            c = shade(c, .72)
        if face in ('right', 'left'):
            c = shade(c, .9)
    return tuple(clamp(x + n) for x in c)


def gold(face, u, v, w, h, rnd):
    """Polished gold: light at the top left, deep at the bottom right, a dark rim."""
    edge = min(u * w, (1 - u) * w, v * h, (1 - v) * h)
    if edge < .25 and min(w, h) >= 1:
        return GOLD_DARK
    t = (u + v) / 2
    c = mix(GOLD_LIT, GOLD, min(1, t * 1.6)) if t < .62 else mix(GOLD, GOLD_DARK, (t - .62) / .38)
    if face == 'top':
        c = mix(c, GOLD_LIT, .35)
    if face == 'bottom':
        c = shade(c, .7)
    return tuple(clamp(x + rnd.uniform(-4, 4)) for x in c)


def badge(face, u, v, w, h, rnd):
    """The cap's badge: a gold plate, its edge raised, a dark emblem pressed into its middle."""
    if face != 'front':
        return gold(face, u, v, w, h, rnd)
    x, y = u * w, v * h
    if x < .5 or x > w - .5 or y < .5 or y > h - .5:
        return mix(GOLD, GOLD_LIT, .4) if y < h / 2 else GOLD_DARK
    cx, cy = x - w / 2, y - h / 2
    if abs(cx) < .55 and abs(cy) < .5 or abs(cx) < .25:
        return hexrgb(0x5a3b0e)
    return mix(GOLD_LIT, GOLD, v)


def button(face, u, v, w, h, rnd):
    """A gakuran button: a gold dome, light at its top."""
    if face != 'front':
        return GOLD_DARK
    d = math.hypot(u - .5, v - .5)
    if d > .48:
        return GOLD_DARK
    c = mix(GOLD_LIT, GOLD, min(1, math.hypot(u - .38, v - .35) * 2.2))
    return c if d < .38 else mix(c, GOLD_DARK, .55)


def buckle(face, u, v, w, h, rnd):
    """A belt buckle: a gold frame round the dark belt."""
    if face != 'front':
        return GOLD_DARK
    x, y = u * w, v * h
    frame = x < .5 or x > w - .5 or y < .5 or y > h - .5
    if frame:
        return mix(GOLD_LIT, GOLD, v) if y < h / 2 else mix(GOLD, GOLD_DARK, .4)
    return hexrgb(0x2e1a10) if abs(x - w * .55) > .25 else mix(GOLD, GOLD_LIT, .4)


def leather(face, u, v, w, h, rnd, base=LEATHER, lit=LEATHER_LIT):
    """A leather belt: a lit top edge, a stitched line along it, rivets now and then."""
    y = v * h
    c = base
    if face in ('front', 'back', 'left', 'right'):
        if y < .5:
            c = lit
        elif y > h - .5:
            c = shade(base, .65)
        elif abs(y - h / 2) < .25 and int(u * w * 2) % 2 == 0:
            c = mix(base, hexrgb(0xa07850), .55)
        if face == 'front' and int(u * w * 2) % 7 == 3 and abs(y - h / 2) < .3:
            c = GOLD
    elif face == 'top':
        c = lit
    else:
        c = shade(base, .6)
    return tuple(clamp(x + rnd.uniform(-4, 4)) for x in c)


def hair(face, u, v, w, h, rnd):
    """The black hair the cap's back tears into: strands along the tuft, bluish where they catch the light."""
    strand = (int(u * w * 2) % 2) == 0
    c = HAIR if strand else shade(HAIR, .75)
    if face == 'top':
        c = mix(c, HAIR_LIT, .6 if strand else .3)
    if face == 'bottom':
        c = shade(c, .7)
    return tuple(clamp(x + rnd.uniform(-3, 3)) for x in c)


def shoe(face, u, v, w, h, rnd):
    """A black leather shoe: a shine along the toe's top, a dark sole."""
    y = v * h
    if face == 'bottom':
        return hexrgb(0x0c0c0e)
    if face in ('front', 'back', 'left', 'right') and y > h - .5:
        return hexrgb(0x2a2a2e)
    c = hexrgb(0x16161a)
    if face == 'top':
        c = mix(c, hexrgb(0x4a4a56), .55 if v < .35 else .2)
    elif face == 'front' and y < .5:
        c = hexrgb(0x3e3e48)
    return tuple(clamp(x + rnd.uniform(-3, 3)) for x in c)


def visor(face, u, v, w, h, rnd):
    """The cap's visor: glossy black, a line of light along its edge."""
    c = hexrgb(0x111114)
    if face == 'top':
        c = mix(c, hexrgb(0x3a3a46), .45 if v > .7 else .15)
    elif face == 'bottom':
        c = hexrgb(0x08080a)
    elif face == 'front':
        c = hexrgb(0x2e2e38)
    return tuple(clamp(x + rnd.uniform(-2, 2)) for x in c)


def cap(face, u, v, w, h, rnd):
    """The cap's crown: black cloth, a hatband round its foot, stitched panels on top."""
    y = v * h
    if face == 'top':
        x, z = u * w - w / 2, v * h - h / 2
        c = mix(CLOTH, CLOTH_LIT, .3)
        if abs(x) < .25 or abs(z) < .25:
            c = shade(c, .8)
        return tuple(clamp(k + rnd.uniform(-4, 4)) for k in c)
    if face == 'bottom':
        return shade(CLOTH, .6)
    c = cloth(face, u, v, w, h, rnd)
    if y > h - 1.0:
        c = mix(hexrgb(0x101013), hexrgb(0x2a2a30), .3 if y > h - .5 else .6)    # the hatband
    return c


def lapel(face, u, v, w, h, rnd, inner_left):
    """An open coat's front edge: wool, its inner edge (by the shirt) lit, a seam a pixel in."""
    c = cloth(face, u, v, w, h, rnd)
    if face == 'front':
        x = u * w
        inner = x < .5 if inner_left else x > w - .5
        seam = abs(x - (1.0 if inner_left else w - 1.0)) < .25
        if inner:
            c = mix(c, CLOTH_LIT, .5)
        elif seam:
            c = shade(c, .75)
    return c


def skirt(face, u, v, w, h, rnd):
    """The coat's skirt: wool, folds running down it, its hem lit."""
    c = cloth(face, u, v, w, h, rnd)
    if face in ('front', 'back', 'left', 'right'):
        fold = .5 + .5 * math.sin(u * w * 2.4 + 1.3)
        c = mix(c, shade(c, .78), fold * .5)
        if (1 - v) * h < .5:
            c = mix(hexrgb(0x2e2e36), c, .2)
    return c


MATERIALS = {
    'cloth': cloth, 'cap': cap, 'visor': visor, 'badge': badge, 'gold': gold, 'button': button, 'buckle': buckle,
    'leather': leather, 'leather_dark': lambda f, u, v, w, h, r: leather(f, u, v, w, h, r, hexrgb(0x2c1a12), hexrgb(0x4a3020)),
    'hair': hair, 'shoe': shoe, 'skirt': skirt,
    'lapel_left': lambda f, u, v, w, h, r: lapel(f, u, v, w, h, r, True),
    'lapel_right': lambda f, u, v, w, h, r: lapel(f, u, v, w, h, r, False),
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
    px = img.load()
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
                    # the face's own size along u and v (units): a side face runs depth by height
                    sw = {'top': w, 'bottom': w, 'front': w, 'back': w, 'right': d, 'left': d}[name]
                    sh = {'top': d, 'bottom': d}.get(name, h)
                    r, g, b = fn(name, u, v, sw, sh, rnd)
                    px[ix, iy] = (r, g, b, 255)
    return img


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else ASSETS
    pack(PARTS)
    tex = os.path.join(out, 'textures', 'entity', 'boss')
    os.makedirs(tex, exist_ok=True)
    os.makedirs(os.path.join(out, 'bosses'), exist_ok=True)
    Image.open(os.path.join(HERE, 'jotaro_skin.png')).convert('RGBA').save(os.path.join(tex, 'jotaro.png'))
    paint(PARTS).save(os.path.join(tex, 'jotaro_outfit.png'))
    model = {
        'texture': [UNITS, UNITS],
        'parts': [{'name': p['name'], 'bone': p['bone'], 'pivot': p['pivot'], 'rotation': p['rotation'],
                   'from': p['frm'], 'size': p['size'], 'uv': p['uv'], 'swing': p['swing']} for p in PARTS],
    }
    with open(os.path.join(out, 'bosses', 'jotaro_outfit.json'), 'w', encoding='utf-8') as f:
        json.dump(model, f, indent=1)
        f.write('\n')
    print(len(PARTS), 'parts; atlas used to row', max(p['uv'][1] + math.ceil(p['size'][2] + p['size'][1]) for p in PARTS), 'of', UNITS)


if __name__ == '__main__':
    main()
