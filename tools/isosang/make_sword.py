"""이소상's sword for GeckoLib (2.76.0, user request: "일단 검은 이렇게 생겼어", with the art the user sent; "검 모델링은
GeckoLib로 뽑아줘"; "검 모델링은 이걸 참고해 특히 코등이", with a 3D model of it, then its side view and its sheet of
parts): its geometry, its texture and its glow texture, made together so that every face is painted for the place it
takes on the sword (per-face UV, as make_star_platinum.py does).

usage: python3 tools/isosang/make_sword.py [OUT]
Writes, under src/main/resources/assets/celestialfall (or under OUT, in the same folders, to look at a change without
touching the game's files):
  geo/item/isosang_sword.geo.json                  the model
  textures/item/isosang_sword_model.png            its texture
  textures/item/isosang_sword_model_glow.png       what of it glows, in its own colours (the lightning and its glints:
                                                   drawn over the model at full brightness, IsosangSwordRenderer)
  textures/item/isosang_sword.png                  a 16 px sprite (the item's particles)
  animations/item/isosang_sword.animation.json     its idle (nothing moves yet)

Its shapes follow the 3D model, measured off its sheet of parts (the pieces laid end to end: 22.5 blade widths from the
pommel's end to the point) and its side view, but for its blade, wider for its length (user request: "검 크기가 너무
작고 가로 폭도 너무 작아": the guard, the hilt and the blade's width 1.4 times the 3D model's next to the blade's length):
  the blade long and straight, a flat down its middle and its edges ground to bevels, narrowing a little (to 0.82 of
  its width) to an ogive point, a line across where the point begins;
  the guard (the user's word, 코등이) two wings like flames, one off each edge, mirror images of each other, their
  outline traced off the guard's cut face on the sheet (piece 2b, the same as its outer face, 2a): at the front a short
  spike reaching towards the point, the wing rising steeply to its peak near the front, its back edge running down
  towards the grip to a spike pointing at the grip with a notch under it (the front spike drawn out to a long white
  crystal shard, as on the made copy of the sword in the user's photo); thin at the rim and the tips, thick in the
  body, a navy groove curving down each face with a raised crest behind it (the darker back facet behind that) and a
  small gold knob near the root; each wing's front spike a white crystal (user request: "코등이에 아래부분에 흰 보석이
  박혀있는데", then "그게 아니야 코등이만 봐" with a photo of a made copy of the sword: white faceted crystals reach out
  between the plate and the wings); between the wings a core round the blade's root, a pointed plate on each flat reaching
  onto the blade towards the point (a navy diamond inside it); below the core its gold going on down round the top of
  the grip as a socket (as the 3D model's ring does), the upper white gem set in a notch up the middle of each of its
  faces, a ridge either side of the notch running beside it and on up onto the core, the socket's foot running from the
  gem's widest out and down to its legs either side of the grip (user request: "아니 흰 보석이랑 코등이랑 자연스럽게
  이어지지가 않았잖아", as on the made copy in the user's photo);
  the handle as on the made copy of the sword in the user's photo (user requests: "손잡이 모델링이 너무 이상한데
  손잡이만 자연스럽게 바꿔봐", then "손잡이랑 코등이랑 흰색 보석으로 이어지잖아"): a round grip, blue like the blade,
  swelling a little towards the pommel and slimmer at the guard, on each flat side a long white diamond gem in the guard's
  socket and another against the pommel's collar, white bands running down the grip from the one to the other and
  crossing halfway (the wider coming out from under the upper gem's widest corner); a gold collar; a gold shield of a
  pommel pointing down, a raised panel on each face; slimmer than the 3D model's (user request: as the 3D model has it
  it looked too big for the sword).
Its colours follow the art: the blade deep violet-navy, lighter and bluer by the guard, its upper edge a dark band
with a bright line inside it, its lower a bright line, an ink line along each, lavender-white lightning running along
its flat with little star glints (these glow: painted in the texture, and drawn again at full brightness from the glow
texture); the guard pale champagne gold with navy insets and white crystals; the grip blue like the blade with white
gems and bands; the collar and the pommel gold.
Its decoration (2.76.6, user request: "마지막으로 예쁘게 꾸미면서 다듬어줘"): starlight over it, as in the art. On the
blade's flat by the guard a crescent moon and a star (her name, 월흔, a moon's trace) and tiny stars along it; four-pointed
twinkles on the guard's gold, the crystals, the knobs and the gems; flame strokes engraved in front of the wings'
grooves; the gold's lines engraved (an ink groove with a bright edge on its lit side), a bright rim inside each outline,
the gold a little richer and brighter towards the wings' peaks and the crests' middles; facets cut in the gems' inner
diamonds; a little white gem on each face of the pommel's panel; beads round the collar. The crescent, the stars and
the twinkles glow.

Units are sixteenths of a block. The sword runs along +y with the middle of its grip at the origin (GeckoLib's item
renderer puts the model's origin at the middle of the item's space, so the hand holds it there), its edges across x (the
upper edge towards +x), its flats towards +-z. GeckoLib mirrors x as it bakes a model; the item's display
(models/item/isosang_sword.json) turns it in the hand.

How GeckoLib lays a face's texture (per-face UV), in the cube's own frame (from make_star_platinum.py):
  north (z min)  x = x0 + u*sx   y = y1 - v*sy        south (z max)  x = x1 - u*sx   y = y1 - v*sy
  east (x min)   z = z1 - u*sz   y = y1 - v*sy        west (x max)   z = z0 + u*sz   y = y1 - v*sy
  up (y max)     x = x0 + u*sx   z = z1 - v*sz        down (y min)   x = x0 + u*sx   z = z0 + v*sz
and it turns a cube by Rz(-rz) . Ry(ry) . Rx(-rx) about its pivot (degrees): a positive rz leans a cube's +y towards +x.
The handle's round parts are true sixteen-sided prisms (eight bars across each, each as wide as a side, turned a
sixteenth apart: their long faces its sides); the pommel is turned, each stretch of its outline a ring of sixteen
planks lying on its facets. The wings, the plate, the socket, the gems, the pommel and the point are cut in thin slices
across the sword, each slice a box per run of it (the wings', the socket's and the gems' slices a twenty-fourth of a
unit; the socket's on the upper gem's, so that the notch's sides and the gem's meet slice for slice), each a hair longer
than its stretch so that neighbours overlap (the renderer leaves hair-wide gaps between boxes that only meet), so that
their outlines follow the 3D model's and the photo's; along every edge of them that shows, a thin box laid along the
edge (turned about z, and tilted where the face it edges rises along it) gives the slices' steps a straight side (2.76.5,
user request: "마지막으로 아쉬운 부분 전체적으로 다듬어줘"). Where the plate rises towards the guard its slices are
turned to lie on the slope. The knobs are round domes: sixteen-sided bosses along z (eight bars turned about z). Each
face has a texel of its own colour round it on the atlas, so that sampling a hair past a face's edge never sees through.
"""
import json
import math
import random
import sys
from pathlib import Path
from types import SimpleNamespace

import numpy as np
from PIL import Image

ROOT = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parents[2] / 'src/main/resources/assets/celestialfall'
DENS = 32       # texels a unit (lines drawn finer than the slices, so that they read smooth up close)
SS = 3          # samples a texel's side (lines drawn smooth)
ATLAS_W = 1024


def hexc(h):
    h = h.lstrip('#')
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], dtype=np.float64) / 255


# ---------------------------------------------------------------- palette (sampled from the art)
INK = hexc('#120f18')
BODY_NEAR, BODY, BODY_DEEP = hexc('#76759d'), hexc('#4f4675'), hexc('#3b3252')
BEVEL_DARK = hexc('#454156')
EDGE_LIGHT, EDGE_LOW = hexc('#efe9fc'), hexc('#dcd6f0')
STEEL_EDGE = hexc('#d4cef0')
CRACK_CORE, CRACK_HALO = hexc('#f8f2ff'), hexc('#b2a1ee')
GLINT = hexc('#ffffff')
GOLD = [hexc(c) for c in ('#4a2a17', '#8f6238', '#b98e57', '#d8b77c', '#edd290', '#f8e6a8', '#fff6d6')]   # warm, polished
GOLD_INK = hexc('#1f1815')
NAVY = [hexc(c) for c in ('#191a42', '#2b2d6b', '#3a3f8a', '#6069b8')]
GRIP = [hexc(c) for c in ('#0d0c12', '#191824', '#252436', '#36354d')]
GRIP_GLINT, GRIP_CORD = hexc('#dedbea'), hexc('#4b4966')
CRYSTAL = [hexc(c) for c in ('#ffffff', '#eaeef9', '#c8cee6')]    # the white crystals: their lit facet, their face, their shaded facet
CRYSTAL_LINE = hexc('#8a90ad')
SPARKLE, GEM_TINT = hexc('#fffaf0'), hexc('#d7e3ff')   # the twinkles; the cool in the gems' lower facets
POMMEL = [hexc(c) for c in ('#1a1817', '#2d2a28', '#45413c', '#625b53', '#8c8379')]


def mix(a, b, t):
    t = np.asarray(t, dtype=np.float64)[..., None] if np.ndim(t) else t
    return a + (b - a) * t


def smooth(t):
    t = np.clip(t, 0, 1)
    return t * t * (3 - 2 * t)


# ---------------------------------------------------------------- the sword's measures (after the 3D model)
# Lengths are in blade widths (the blade's width at its root), measured off the sheet of parts; p is from the pommel's
# end towards the point, a (along) from the guard's front (where its wings meet the blade's edges) back towards the grip.
W = 2.03                                  # the blade's width at its root, in units (the guard, the hilt and every
                                          # cross-section in these; the blade wider for its length than the 3D model's,
                                          # user request: "검 크기가 너무 작고 가로 폭도 너무 작아")
BLADE = 20.5                              # the blade from the guard's front to where the point begins, in units
HILT = .72                                # the pommel, the grip and the ring smaller than the 3D model's (user request:
                                          # "검에 비해 손잡이가 너무 크다고 생각 안 하니")
P_GRIP = (1.585 * HILT, (1.585 + 2.985) * HILT)   # the grip as the 3D model has it (0.72 of it), which places the guard
P_BASE = P_GRIP[1] + 1.62                 # the guard's front
P_HOLD = (1.15 + P_BASE - 1.03) / 2       # where the hand holds it, at the model's origin: the middle of the grip between
                                          # the collar and the guard's core
P_TIP = (P_BASE + BLADE / W, P_BASE + BLADE / W + 2.15)   # where the point begins, the point
TAPER = .82                               # the blade's width where the point begins, of its width at the root
FLAT, INNER, INNER_TIP = .24, .37, .325   # half widths: the flat; the inner bevel's outer edge at the root, where the point begins
T_FLAT, T_INNER, T_OUTER = .11, .075, .04  # half thicknesses: the flat, the inner bevel, the outer bevel
TIP_SLICES = 24


def y_of(p):
    return (p - P_HOLD) * W


def y_along(a):
    return y_of(P_BASE - a)


def along_of(y):
    return P_BASE - P_HOLD - y / W


Y_ROOT, Y_TIP_FROM, Y_TIP_TO = y_along(0), y_of(P_TIP[0]), y_of(P_TIP[1])

# the guard's wing off the upper edge, the other its mirror image: (a, up) in blade widths, up from the blade's middle;
# traced off the guard's cut face on the sheet (piece 2b; its outer face, 2a, has the same outline), but for its front
# spike, the white crystal, drawn out to a long shard as on the made copy in the user's photo. The first and last points
# close it inside the core.
WING = [(.02, .45), (.01, .5), (-.07, .57), (-.38, 1.13), (-.10, 1.02), (.035, .938),
        (.078, .975), (.085, 1.05), (.117, 1.19), (.14, 1.215), (.138, 1.41), (.266, 1.542), (.324, 1.521),
        (.66, 1.245), (.717, 1.193), (.756, 1.02), (.864, .825), (.951, .739), (1.085, .696), (1.085, .666),
        (.977, .54), (.995, .5), (.995, .45)]
OUTLINE = WING[1:-1]
# the relief on its faces (traced off 2a): the groove's back wall and the crest's front (from its top down), the crest's
# back and above it the line where the back facet begins (from its foot up)
CURVE = [(.50, 1.12), (.445, 1.02), (.432, .93), (.454, .807), (.49, .70), (.535, .613), (.59, .55), (.64, .50)]
BACK_LINE = [(.76, .50), (.73, .68), (.695, .84), (.50, 1.12), (.22, 1.50)]
GROOVE_W, GROOVE_SPAN = .08, (.56, 1.06)    # (the straight edges over its sides' steps take a little off its width)
RIM = .07                                 # the rim: how far in from the outline the wing's body rises
T_RIM, T_BODY, T_CREST, T_CORE = .12, .26, .32, .259  # half thicknesses (the core's as the body's, a hair under: no step
                                                      # where the wings' bodies meet it)
KNOB = (.82, .60, .065, .06)              # the knob near each wing's root, a dome: along, up, radius, how far it stands out
# the white crystal: each wing's front spike, in front of the line where the spike meets the wing (from the blade's edge
# to the notch); its ridge from its foot to its tip
CRYSTAL_EDGE = [(.01, .5), (.035, .94)]
CRYSTAL_RIDGE = [(.022, .72), (-.38, 1.13)]
# the plate on the flats: its point on the blade, its widest (and its half width), its point on the core; half thick at
# its point and from the guard's front back; the navy diamond inside it
PLATE = (-.856, .073, .43, .42)
PLATE_Z = (.16, .30)
PLATE_POLY = [(PLATE[0], 0), (PLATE[1], PLATE[2]), (PLATE[3], 0), (PLATE[1], -PLATE[2])]
INNER_DIAMOND = [(-.36, 0), (.01, .2), (.21, .2), (.39, 0), (.21, -.2), (.01, -.2)]
CORE_TO = 1.03
# the handle as on the made copy in the user's photo (user request: "아니 손잡이랑 코등이랑 흰색 보석으로 이어지잖아 잘 봐",
# "그렇다고 보석만 박으면 어떻게 아니 너 사진 잘 안 보는거야?"), p from the pommel's end, in blade widths: the gold collar
# on the pommel; the grip, blue like the blade, swelling a little towards the pommel (its outline: p, radius), from inside
# the collar up into the guard's core, slimmer towards the guard (inside the core and the socket at its top); on each
# flat side a long white diamond gem in the guard's socket pointing down the grip and another against the collar
# pointing up it, white bands running between them; the pommel a gold shield pointing down, a raised panel on each face
COLLAR = (1.03, 1.15, .30)
GRIP_SHAPE = [(1.0, .29), (1.15, .29), (1.5, .275), (2.2, .255), (3.0, .24), (P_BASE - CORE_TO + .02, .21)]
# the gems: the end towards the guard or against the collar (p), the way they point (-1 down the grip, +1 up it), their
# length, how far from that end they are widest and their half width there; their border's inset; how far their
# border's face and their inner diamond's face stand off the grip
GEMS = [(P_BASE - 1.06, -1, .51, .20, .16), (COLLAR[1] + .02, 1, .7, .15, .2)]
GEM_INSET = .065
GEM_RISE = (.05, .08)
# the guard's socket (user request: "아니 흰 보석이랑 코등이랑 자연스럽게 이어지지가 않았잖아", as on the made copy in the
# user's photo): below the core the guard's gold goes on down round the top of the grip, a little wider than it (as the
# 3D model's ring is), and the upper gem sits in a notch up the middle of each of its faces, the gem's point in the
# notch's and the socket's foot running from the gem's widest out and down to its legs either side of the grip. Its half
# width, where its legs end (a); its layers, each in from the one before round its outline (so that it reads rounded
# from the side): how far in, half thick (the rim flush with the core's face, the body a little above the gem's border,
# so that the gem is set in it), where each begins (a; the two above the rim together, one step up from the core); how far
# out from the notch's sides the ridge runs beside them (on up onto the core, where the two meet in a point)
SOCKET = (.30, 1.37)
SOCKET_LAYERS = (('rim', 0.0, .26, CORE_TO), ('mid', .025, .28, CORE_TO + .01), ('body', .05, .30, CORE_TO + .01))
RIDGE = .1
NOTCH_TOP = P_BASE - GEMS[0][0]                  # the notch's point (a), the gem's
NOTCH_FOOT = NOTCH_TOP + GEMS[0][3]              # where the gem is widest, at the socket's foot
NOTCH_SLOPE = GEMS[0][4] / GEMS[0][3]            # how far the notch widens for its length
RIDGE_TOP = NOTCH_TOP - RIDGE * math.hypot(1, NOTCH_SLOPE) / NOTCH_SLOPE   # where the ridges meet, on the core
# the white bands from the upper gem's point to the lower's, side by side and crossing once halfway, a little waver in
# the wider, which comes out from under the upper gem's widest corner and follows its lower side down to its point: how
# far apart they start and end (radians round the grip, each side of the flat side's middle), the waver, their widths
BANDS = (.3, .06, .09, .045)
# the pommel's shield and its raised panel (their right halves, p and half width, from the point up), half thick
POMMEL_SHIELD = [(0, 0), (.5, .44), (.95, .44), (COLLAR[0], .36)]
POMMEL_PANEL = [(.15, 0), (.52, .3), (.93, .3)]
POMMEL_Z = (.2, .26)
# the pommel's gem (2.76.6), a little white diamond on each face of the panel: its middle (p), half its length and its
# half width, how far its border and its inner diamond stand off the panel (blade widths)
POMMEL_GEM = (.56, .2, .11, (.035, .06))


# ---------------------------------------------------------------- outlines
def inside(poly, a, u):
    """Whether points (a, u) lie inside a closed polygon (even-odd)."""
    a, u = np.broadcast_arrays(np.asarray(a, dtype=np.float64), np.asarray(u, dtype=np.float64))
    res = np.zeros(a.shape, dtype=bool)
    for i in range(len(poly)):
        (a0, u0), (a1, u1) = poly[i], poly[(i + 1) % len(poly)]
        if u0 == u1:
            continue
        cross = a0 + (u - u0) * (a1 - a0) / (u1 - u0)
        res ^= ((u0 > u) != (u1 > u)) & (a < cross)
    return res


def dist(line, a, u, closed=False):
    """Distance from points (a, u) to a polyline."""
    a, u = np.broadcast_arrays(np.asarray(a, dtype=np.float64), np.asarray(u, dtype=np.float64))
    best = np.full(a.shape, np.inf)
    pts = list(line) + ([line[0]] if closed else [])
    for (a0, u0), (a1, u1) in zip(pts, pts[1:]):
        da, du = a1 - a0, u1 - u0
        t = np.clip(((a - a0) * da + (u - u0) * du) / max(da * da + du * du, 1e-12), 0, 1)
        best = np.minimum(best, np.hypot(a - (a0 + t * da), u - (u0 + t * du)))
    return best


def through(points, u):
    """The along of a line through points (each (a, up), up monotonic) at up u."""
    us, as_ = np.array([p[1] for p in points]), np.array([p[0] for p in points])
    order = np.argsort(us)
    return np.interp(u, us[order], as_[order])


_us = np.linspace(GROOVE_SPAN[1], GROOVE_SPAN[0], 24)
GROOVE = [(float(through(CURVE, u)), float(u)) for u in _us] + [(float(through(CURVE, u)) - GROOVE_W, float(u)) for u in _us[::-1]]
CREST = CURVE + BACK_LINE[:3]


def wing_layer(layer, a, u):
    """Where a layer of the wing is: the rim and tips (all of it), the body (in from the outline, not the groove), the
    crest (inside the body)."""
    m = inside(WING, a, u)
    if layer == 'rim':
        return m
    d = dist(OUTLINE, a, u)
    m &= (u >= .5) & (d >= RIM) & ~inside(GROOVE, a, u)
    if layer == 'body':
        return m
    return m & inside(CREST, a, u) & (d >= RIM + .03)


# ---------------------------------------------------------------- the model
class Cube:
    def __init__(self, bone, part, origin, size, faces, rot=None, pivot=None, **opts):
        self.bone, self.part = bone, part
        self.origin, self.size = np.array(origin, dtype=np.float64), np.array(size, dtype=np.float64)
        self.faces = faces
        self.rot = None if rot is None else tuple(float(r) for r in rot)
        self.pivot = None if pivot is None else np.array(pivot, dtype=np.float64)
        self.opts = opts


CUBES = []
ALL = ['north', 'south', 'east', 'west', 'up', 'down']
SIDES = ['north', 'south', 'east', 'west']


def cube(bone, part, origin, size, faces=ALL, **kw):
    CUBES.append(Cube(bone, part, origin, size, list(faces), **kw))


SEAM = .0015                              # how far a slice reaches past its neighbours (units): the renderer leaves
                                          # hair-wide gaps between boxes that only meet, and the far side shows through


def sliced(bone, part, origin, size, faces=ALL, **kw):
    """A slice of a part cut across the sword: a box reaching a hair past its ends along y."""
    cube(bone, part, [origin[0], origin[1] - SEAM, origin[2]], [size[0], size[1] + 2 * SEAM, size[2]], faces, **kw)


def round_prism(bone, part, y0, y1, r, ends=(), sides=16, **kw):
    """A round part along y: a regular polygon of `sides` sides, r across its flats, as sides/2 bars across it, each as
    wide as a side and turned 360/sides apart (each bar's two long faces two of its sides; together they fill it). The
    ends that show are staggered a hair from bar to bar, so that their faces never fight."""
    n, side = sides // 2, 2 * r * math.tan(math.pi / sides)
    for k in range(n):
        lo, hi = y0 + (.002 * k if 'down' in ends else 0), y1 - (.002 * k if 'up' in ends else 0)
        cube(bone, part, [-r, lo, -side / 2], [2 * r, hi - lo, side], ['east', 'west'] + list(ends),
             rot=(0, 180 / n * k, 0), pivot=[0, (y0 + y1) / 2, 0], span=(y0, y1), r=r, **kw)


def turned(bone, part, outline, sides=16, **kw):
    """A body turned about y through an outline of (y, radius): each stretch of it a ring of `sides` planks, each lying on
    one of its facets (its outer face), tilted with the stretch about its foot and turned about the axis."""
    for (y0, r0), (y1, r1) in zip(outline, outline[1:]):
        length, tilt = math.hypot(y1 - y0, r1 - r0), math.atan2(r1 - r0, y1 - y0)
        w, t = 2 * max(r0, r1) * math.tan(math.pi / sides) * 1.01, max(.4 * min(r0, r1), .06)
        for k in range(sides):
            # placed so that tilting it about the axis's point at y0 brings its foot to radius r0 there
            cube(bone, part, [-w / 2, y0 + r0 * math.sin(tilt), r0 * math.cos(tilt) - t], [w, length, t], ['south'],
                 rot=(-math.degrees(tilt), 360 / sides * k, 0), pivot=[0, y0, 0], **kw)


# the blade: its flat down the middle the whole way; each bevel a strip turned a little so that its outer edge draws in
# with the blade's width (an inner bevel a little thinner than the flat, the outer thinner still, so that the edges read
# as ground); where a strip runs inside a thicker part it is hidden there
Y_BLADE_FROM = y_along(.3)               # inside the core
cube('blade', 'flat', [-FLAT * W, Y_BLADE_FROM, -T_FLAT * W], [2 * FLAT * W, Y_TIP_FROM - Y_BLADE_FROM, 2 * T_FLAT * W],
     ['north', 'south', 'east', 'west', 'up'])
L = Y_TIP_FROM - Y_ROOT
for s in (1, -1):
    edge = 'upper' if s > 0 else 'lower'
    lean_in = math.degrees(math.atan((INNER - INNER_TIP) * W / L))
    lean_out = math.degrees(math.atan((1 - TAPER) * W / 2 / L))
    x0, x1 = FLAT * W, INNER * W
    cube('blade', 'bevel_in', [x0 if s > 0 else -x1, Y_BLADE_FROM, -T_INNER * W], [x1 - x0, Y_TIP_FROM - Y_BLADE_FROM, 2 * T_INNER * W],
         ['north', 'south', 'west' if s > 0 else 'east', 'up'], rot=(0, 0, -s * lean_in), pivot=[s * x1, Y_ROOT, 0], edge=edge)
    x0, x1 = INNER * W, W / 2
    cube('blade', 'bevel_out', [x0 if s > 0 else -x1, Y_BLADE_FROM, -T_OUTER * W], [x1 - x0, Y_TIP_FROM - Y_BLADE_FROM, 2 * T_OUTER * W],
         ['north', 'south', 'west' if s > 0 else 'east', 'up'], rot=(0, 0, -s * lean_out), pivot=[s * x1, Y_ROOT, 0], edge=edge)


def tip_half_width(t):
    """The point's half width (units) a fraction t of the way from where it begins to the tip: an ogive."""
    return TAPER * W / 2 * (1 - t ** 2.25) + .018 * t


# the point in slices: in each, a thin box its whole width (the bevels drawn in to the tip) and a thicker one down its
# middle (the flat), both thinning to the tip, each as wide as the point is at the slice's tip-ward end (so that no
# step stands outside its outline: the straight edges below give it its sides)
for i in range(TIP_SLICES):
    y0 = Y_TIP_FROM + (Y_TIP_TO - Y_TIP_FROM) * i / TIP_SLICES
    y1 = Y_TIP_FROM + (Y_TIP_TO - Y_TIP_FROM) * (i + 1) / TIP_SLICES
    t = (i + .5) / TIP_SLICES
    hw = float(tip_half_width((i + 1) / TIP_SLICES))
    k = hw / (TAPER * W / 2)
    faces = ALL if i == 0 else ['north', 'south', 'east', 'west', 'up']
    th = (T_OUTER * (1 - t) + .012 * t) * W
    sliced('blade', 'tip', [-hw, y0, -th], [2 * hw, y1 - y0, 2 * th], faces, slice=(i, hw, (FLAT + .03) * W * k))
    hm, th = (FLAT + .03) * W * k, (T_FLAT * (1 - t) + .022 * t) * W
    sliced('blade', 'tip_flat', [-hm, y0, -th], [2 * hm, y1 - y0, 2 * th], faces)

def half_along(side, p):
    """The half width at p of a shape given by its right half (p, half width), nothing beyond its ends."""
    ps, hs = [q[0] for q in side], [q[1] for q in side]
    return float(np.interp(p, ps, hs)) if ps[0] <= p <= ps[-1] else 0.0


def grip_radius(p):
    return float(np.interp(p, [q[0] for q in GRIP_SHAPE], [q[1] for q in GRIP_SHAPE]))


# the handle: the pommel's shield and its panel in slices a twenty-fourth of a unit long, the collar, the grip turned
_k = int(math.ceil(COLLAR[0] * W * 24))
_pp = np.linspace(0, COLLAR[0], _k + 1)
for k in range(_k):
    pm = (_pp[k] + _pp[k + 1]) / 2
    for part, side, half_t in (('pommel', POMMEL_SHIELD, POMMEL_Z[0]), ('pommel_panel', POMMEL_PANEL, POMMEL_Z[1])):
        hw = half_along(side, pm) * W
        if hw >= .015:
            sliced('grip', part, [-hw, y_of(_pp[k]), -half_t * W], [2 * hw, y_of(_pp[k + 1]) - y_of(_pp[k]), 2 * half_t * W], ALL,
                 depth=half_t * W)
round_prism('grip', 'collar', y_of(COLLAR[0]), y_of(COLLAR[1]), COLLAR[2] * W, ends=('up', 'down'))
turned('grip', 'grip', [(y_of(p), r * W) for p, r in GRIP_SHAPE])

# the guard: the core round the blade's root
cube('guard', 'core', [-W / 2, y_along(CORE_TO), -T_CORE * W], [W, y_along(0) - y_along(CORE_TO), 2 * T_CORE * W])
SLAB = 1 / 24                             # how long the slices are that the gems, the socket and the wings are cut in


def gem_half(gem, p):
    """A gem's half width (blade widths) at p (a number or an array): its long diamond, nothing beyond its ends."""
    end, way, length, widest, half = gem
    t = (np.asarray(p, dtype=np.float64) - end) * way
    h = np.where((t > 0) & (t < length), half * np.where(t < widest, t / widest, (length - t) / (length - widest)), 0.0)
    return float(h) if h.ndim == 0 else h


def gem_poly(gem):
    end, way, length, widest, half = gem
    return [(end, 0), (end + way * widest, half), (end + way * length, 0), (end + way * widest, -half)]


def gem_slices(gem):
    """A gem's slices (p from, p to), a SLAB long from its end towards its point, the last one shorter: the upper gem's
    the socket's own, so that the notch's sides and the gem's meet slice for slice."""
    end, way, length = gem[:3]
    n = int(math.ceil(length * W / SLAB - 1e-9))
    ps = [end + way * min(k * SLAB / W, length) for k in range(n + 1)]
    return [tuple(sorted(pair)) for pair in zip(ps, ps[1:])]


# the gems on each flat side of the grip: each slice its border (from inside the grip out past its surface) and, inset,
# the raised inner diamond
for gi, gem in enumerate(GEMS):
    for f in (1, -1):
        out = 'south' if f > 0 else 'north'
        for lo_p, hi_p in gem_slices(gem):
            pm = (lo_p + hi_p) / 2
            hw, r = gem_half(gem, pm), grip_radius(pm)
            for part, half, a, b in (('gem', hw, .12, r + GEM_RISE[0]), ('gem_top', hw - GEM_INSET, r + GEM_RISE[0] - .01, r + GEM_RISE[1])):
                if half * W < .015:
                    continue
                lo, hi = (a * W, b * W) if f > 0 else (-b * W, -a * W)
                sliced('grip', part, [-half * W, y_of(lo_p), lo], [2 * half * W, y_of(hi_p) - y_of(lo_p), hi - lo],
                     [out, 'east', 'west', 'up', 'down'], gem=gi)


def foot_line(a, inset=0.0):
    """How far out (blade widths) the socket's foot is at a, running from the gem's widest out and down to the end of a
    leg; a layer's (in from the outline by `inset`) from the same corner of the gem to its own leg's end."""
    g = GEMS[0][4]
    return g + (a - NOTCH_FOOT) * (SOCKET[0] - inset - g) / (SOCKET[1] - inset - NOTCH_FOOT)


def socket_run(a, inset=0.0):
    """The socket's run across each side at a (from, to: blade widths out from the middle), a layer's in from the
    outline by `inset` but for the notch's sides (the gem fills the notch); None where it has none."""
    lo = max(gem_half(GEMS[0], P_BASE - a), foot_line(a, inset), 0.0)
    hi = SOCKET[0] - inset
    return (lo, hi) if hi - lo > .012 else None


# the socket: in slices on the upper gem's, each layer of a slice one box across it (above the notch) or one either side
# of the notch and of the gem in it
SOCKET_OUTLINE = [(CORE_TO, SOCKET[0]), (SOCKET[1], SOCKET[0]), (NOTCH_FOOT, GEMS[0][4])]   # its side and its foot
NOTCH_SIDE = [(NOTCH_FOOT, GEMS[0][4]), (NOTCH_TOP, 0.0)]
_s = SLAB / W
_cuts = sorted({min(max(NOTCH_TOP + k * _s, CORE_TO), SOCKET[1])
                for k in range(-int(math.ceil((NOTCH_TOP - CORE_TO) / _s)), int(math.ceil((SOCKET[1] - NOTCH_TOP) / _s)) + 1)})
for layer, inset, T, top in SOCKET_LAYERS:
    runs = [socket_run((a0 + a1) / 2, inset) if (a0 + a1) / 2 > top else None for a0, a1 in zip(_cuts, _cuts[1:])]
    for k, (a0, a1) in enumerate(zip(_cuts, _cuts[1:])):
        if runs[k] is None:
            continue
        lo, hi = runs[k]
        faces = ['north', 'south', 'east', 'west']
        for face, near in (('up', runs[k - 1] if k > 0 else None), ('down', runs[k + 1] if k + 1 < len(runs) else None)):
            if near is None or near[0] > lo + 1e-9 or near[1] < hi - 1e-9:
                faces.append(face)
        y0, y1 = y_along(a1), y_along(a0)
        for x0, x1 in ([(-hi, hi)] if lo <= 0 else [(lo, hi), (-hi, -lo)]):
            sliced('guard', 'socket', [x0 * W, y0, -T * W], [(x1 - x0) * W, y1 - y0, 2 * T * W], faces, layer=layer, depth=T * W, run=(lo, hi))

# the pommel's gem, a little white diamond on each face of its panel (2.76.6): as a gem (its end towards the collar,
# pointing down to the shield's point), cut in slices like the grip's gems, standing off the panel
PG = (POMMEL_GEM[0] + POMMEL_GEM[1], -1, 2 * POMMEL_GEM[1], POMMEL_GEM[1], POMMEL_GEM[2])
GEM_INSETS = (GEM_INSET, GEM_INSET, .035)   # the inner diamonds' insets: the grip's gems', the pommel's
ALL_GEMS = GEMS + [PG]


def gem_tops(gi, p):
    """How far a gem's border and its inner diamond stand from the middle of the sword at p (blade widths): the grip's
    gems on the grip, the pommel's on its panel."""
    if gi == 2:
        return POMMEL_Z[1] + POMMEL_GEM[3][0], POMMEL_Z[1] + POMMEL_GEM[3][1]
    r = np.interp(p, [q[0] for q in GRIP_SHAPE], [q[1] for q in GRIP_SHAPE])
    return r + GEM_RISE[0], r + GEM_RISE[1]


for f in (1, -1):
    out = 'south' if f > 0 else 'north'
    for lo_p, hi_p in gem_slices(PG):
        pm = (lo_p + hi_p) / 2
        hw = gem_half(PG, pm)
        b, t = gem_tops(2, pm)
        for part, half, z0, z1 in (('gem', hw, POMMEL_Z[1] - .01, b), ('gem_top', hw - GEM_INSETS[2], b - .01, t)):
            if half * W < .015:
                continue
            lo, hi = (z0 * W, z1 * W) if f > 0 else (-z1 * W, -z0 * W)
            sliced('grip', part, [-half * W, y_of(lo_p), lo], [2 * half * W, y_of(hi_p) - y_of(lo_p), hi - lo],
                   [out, 'east', 'west', 'up', 'down'], gem=2)

# the plate on the flats: slices a sixteenth of a unit long, as wide as its outline at their middle, thicker towards the
# guard; through the blade (on both flats at once) where it is as thick all along, and where it rises towards the guard
# a slice on each face, turned about x to lie on the slope (so that its faces rise smoothly, not in steps)
n = int(math.ceil((y_along(PLATE[0]) - y_along(PLATE[3])) * 16))
ys = np.linspace(y_along(PLATE[3]), y_along(PLATE[0]), n + 1)
PLATE_SLOPE = math.degrees(math.atan((PLATE_Z[1] - PLATE_Z[0]) / -PLATE[0]))   # how steeply it rises towards the guard
for k in range(n):
    a = along_of((ys[k] + ys[k + 1]) / 2)
    hw = PLATE[2] * ((a - PLATE[0]) / (PLATE[1] - PLATE[0]) if a < PLATE[1] else (PLATE[3] - a) / (PLATE[3] - PLATE[1]))
    z = PLATE_Z[0] + (PLATE_Z[1] - PLATE_Z[0]) * float(np.clip((a - PLATE[0]) / -PLATE[0], 0, 1))
    if hw * W < .02:
        continue
    end = ['up'] if a < PLATE[1] else ['down']
    if a >= 0:
        sliced('guard', 'plate', [-hw * W, ys[k], -z * W], [2 * hw * W, ys[k + 1] - ys[k], 2 * z * W],
             ['north', 'south', 'east', 'west'] + end, depth=z * W)
        continue
    ym, half = (ys[k] + ys[k + 1]) / 2, (ys[k + 1] - ys[k]) / 2 / math.cos(math.radians(PLATE_SLOPE)) + .001
    for f in (1, -1):                      # from inside the blade out to the face, turned about the face's line (each as
                                           # long as its stretch of the slope, so that no hair-wide gap opens between them)
        cube('guard', 'plate', [-hw * W, ym - half, f * z * W - (z * W - .08 * W if f > 0 else 0)],
             [2 * hw * W, 2 * half, z * W - .08 * W], ['south' if f > 0 else 'north', 'east', 'west', 'up', 'down'],
             rot=(f * PLATE_SLOPE, 0, 0), pivot=[0, ym, f * z * W], depth=z * W)

# the wings: each layer cut in slices across the sword, a box for each run of it in a slice; runs that go on unchanged
# through the next slices are one box. Faces hidden inside the core, or against the same layer in the next slice, are
# left out.
XQ = 1 / 48
_y0, _y1 = y_along(max(p[0] for p in WING)), y_along(min(p[0] for p in WING))
_n = int(math.ceil((_y1 - _y0) / SLAB))
_edges = _y0 + SLAB * np.arange(_n + 1)
_xs = np.arange(.4 * W, 1.6 * W, XQ / 2) + XQ / 4


def runs_of(m):
    out = []
    idx = np.flatnonzero(np.diff(np.concatenate([[0], m.astype(np.int8), [0]])))
    for s, e in zip(idx[::2], idx[1::2]):
        x0 = round((_xs[s] - XQ / 4) / XQ) * XQ
        x1 = round((_xs[e - 1] + XQ / 4) / XQ) * XQ
        if x1 - x0 >= XQ - 1e-9:
            out.append((round(x0, 6), round(x1, 6)))
    return out


def covers(runs, x0, x1):
    return any(r0 <= x0 + 1e-9 and r1 >= x1 - 1e-9 for r0, r1 in runs)


for layer, T in (('rim', T_RIM), ('body', T_BODY), ('crest', T_CREST)):
    rows = []
    for k in range(_n):
        a = along_of((_edges[k] + _edges[k + 1]) / 2)
        rows.append(runs_of(wing_layer(layer, np.full_like(_xs, a), _xs / W)))
    opened, boxes = {}, []
    for k in range(_n + 1):
        now = set(rows[k]) if k < _n else set()
        for r in list(opened):
            if r not in now:
                boxes.append((r, opened.pop(r), k))
        for r in now:
            opened.setdefault(r, k)
    for (x0, x1), k0, k1 in sorted(boxes, key=lambda b: (b[1], b[0])):
        faces = ['north', 'south']
        if k1 >= _n or not covers(rows[k1], x0, x1):
            faces.append('up')
        if k0 == 0 or not covers(rows[k0 - 1], x0, x1):
            faces.append('down')
        in_core = along_of(_edges[k1]) >= 0 and along_of(_edges[k0]) <= CORE_TO
        inner = not (in_core and x0 <= W / 2 + 1e-6 and T <= T_CORE)
        for s in (1, -1):
            f = faces + (['west'] if s > 0 else ['east']) + ((['east'] if s > 0 else ['west']) if inner else [])
            origin = [x0 if s > 0 else -x1, _edges[k0], -T * W]
            sliced('guard', 'wing', origin, [x1 - x0, _edges[k1] - _edges[k0], 2 * T * W], f, layer=layer, depth=T * W)


def disc(bone, part, cx, cy, z0, z1, r, sides=16, **kw):
    """A round boss standing along z from z0 to its face at z1: a regular polygon of `sides` sides, r across its flats,
    as sides/2 bars turned about z (each bar's two long faces two of its sides), their faces staggered a hair."""
    n, side = sides // 2, 2 * r * math.tan(math.pi / sides)
    way = 1 if z1 > z0 else -1
    for k in range(n):
        lo, hi = sorted((z0, z1 - way * .002 * k))
        cube(bone, part, [cx - r, cy - side / 2, lo], [2 * r, side, hi - lo], ['south' if way > 0 else 'north', 'east', 'west'],
             rot=(0, 0, 180 / n * k), pivot=[cx, cy, 0], centre=(cx, cy), r=r, **kw)


# the knobs near the wings' roots, on each face of each wing: round domes standing out of the body, a boss and on it a
# smaller one
ka, ku, kr, kh = KNOB
for s_ in (1, -1):
    for f in (1, -1):
        cx, cy, z0 = s_ * ku * W, y_along(ka), f * T_BODY * W
        disc('guard', 'knob', cx, cy, z0, z0 + f * .55 * kh * W, kr * W, tier=0)
        disc('guard', 'knob', cx, cy, z0, z0 + f * kh * W, .62 * kr * W, tier=1)


# ---------------------------------------------------------------- straight edges over the slices' steps
# A part cut in slices has stepped sides. Along every edge of one that shows, a thin box laid along the edge (turned
# about z, and tilted where the face it edges rises along it) gives it a straight side: its outer face a little outside
# the steps (none outside its outline where its slices keep inside it), reaching a little in under them, its faces a
# hair below the faces it edges (where the two overlap the slices' show), painted as what it edges is there.
EDGE_OUT, EDGE_IN, EDGE_SINK = .02, .06, .004    # units: how far out past the edge, how far in, how far below the faces


def edge_through(bone, like, p, q, left, half, out=EDGE_OUT, inward=EDGE_IN):
    """An edge box from face to face of a part `half` units half thick, along p→q (model x, y), the part on the left of
    p→q if `left`; `like` says what it edges (for its paint)."""
    (px, py), (qx, qy) = p, q
    length = math.hypot(qx - px, qy - py)
    if length < 1e-4:
        return
    x0, x1 = (px - inward, px + out) if left else (px - out, px + inward)
    h = half - EDGE_SINK
    cube(bone, 'edge', [x0, py, -h], [x1 - x0, length, 2 * h], ['north', 'south', 'west' if left else 'east', 'up', 'down'],
         rot=(0, 0, math.degrees(math.atan2(qx - px, qy - py))), pivot=[px, py, 0], like=like)


def edge_face(bone, like, p3, q3, left, side, thick, out=EDGE_OUT, inward=EDGE_IN):
    """An edge box on one face only (side +1 the south, -1 the north), its face following the line from p3 to q3
    (model x, y and the face's height there) a hair below it, `thick` deep."""
    (px, py, pz), (qx, qy, qz) = p3, q3
    dx, dy, dz = qx - px, qy - py, qz - pz
    length = math.sqrt(dx * dx + dy * dy + dz * dz)
    if length < 1e-4:
        return
    pz -= side * EDGE_SINK
    x0, x1 = (px - inward, px + out) if left else (px - out, px + inward)
    z0, z1 = (pz - thick, pz) if side > 0 else (pz, pz + thick)
    cube(bone, 'edge', [x0, py, z0], [x1 - x0, length, z1 - z0], ['south' if side > 0 else 'north', 'west' if left else 'east', 'up', 'down'],
         rot=(-math.degrees(math.asin(dz / length)), 0, math.degrees(math.atan2(dx, dy))), pivot=[px, py, pz], like=like)


def on_left(p, q, region):
    """Whether a region (a test of model x, y) lies on the left of p→q."""
    (px, py), (qx, qy) = p[:2], q[:2]
    n = math.hypot(qx - px, qy - py)
    return bool(region((px + qx) / 2 - (qy - py) / n * .01, (py + qy) / 2 + (qx - px) / n * .01))


def contour(f, xs, ys):
    """The lines where f (over the grid ys × xs) crosses zero, as polylines of (x, y): marching squares, each crossing
    placed between grid points by f's values, the pieces joined end to end."""
    segs = []
    for j in range(len(ys) - 1):
        for i in range(len(xs) - 1):
            v = (f[j, i], f[j, i + 1], f[j + 1, i + 1], f[j + 1, i])
            pts = ((xs[i], ys[j]), (xs[i + 1], ys[j]), (xs[i + 1], ys[j + 1]), (xs[i], ys[j + 1]))
            cuts = []
            for e in range(4):
                a, b = e, (e + 1) % 4
                if (v[a] >= 0) != (v[b] >= 0):
                    t = v[a] / (v[a] - v[b])
                    cuts.append((pts[a][0] + t * (pts[b][0] - pts[a][0]), pts[a][1] + t * (pts[b][1] - pts[a][1])))
            if len(cuts) == 2:
                segs.append((cuts[0], cuts[1]))
            elif len(cuts) == 4:
                segs += [(cuts[0], cuts[1]), (cuts[2], cuts[3])]
    key = lambda pt: (round(pt[0], 9), round(pt[1], 9))
    ends = {}
    for k, (a, b) in enumerate(segs):
        ends.setdefault(key(a), []).append((k, 1))
        ends.setdefault(key(b), []).append((k, 0))
    used, lines = set(), []
    for k in range(len(segs)):
        if k in used:
            continue
        used.add(k)
        line = [segs[k][0], segs[k][1]]
        for grow in (1, 0):                  # grow from its end, then from its start
            while True:
                tip = line[-1] if grow else line[0]
                nxt = [(m, w) for m, w in ends.get(key(tip), []) if m not in used]
                if not nxt:
                    break
                m, w = nxt[0]
                used.add(m)
                other = segs[m][w]
                line = line + [other] if grow else [other] + line
        lines.append(line)
    return lines


def simplify(line, tol):
    """A polyline with the points it can do without (within tol of the rest) left out (Douglas-Peucker)."""
    if len(line) < 3:
        return list(line)
    (ax, ay), (bx, by) = line[0], line[-1]
    n = math.hypot(bx - ax, by - ay)
    far, at = 0.0, 0
    for k in range(1, len(line) - 1):
        x, y = line[k]
        d = abs((bx - ax) * (ay - y) - (ax - x) * (by - ay)) / n if n > 1e-9 else math.hypot(x - ax, y - ay)   # (a loop)
        if d > far:
            far, at = d, k
    if far <= tol:
        return [line[0], line[-1]]
    return simplify(line[:at + 1], tol)[:-1] + simplify(line[at:], tol)


def edges_along(bone, like, line, region, half, closed=False, skip=None, **kw):
    """Edge boxes through a part along each stretch of a line of model (x, y) points (but those `skip` says to leave)."""
    pts = list(line) + ([line[0]] if closed else [])
    for p, q in zip(pts, pts[1:]):
        if skip is None or not skip(p, q):
            edge_through(bone, like, p, q, on_left(p, q, region), half, **kw)


def wing_region(layer, s):
    return lambda x, y: wing_layer(layer, np.array([along_of(y)]), np.array([s * x / W]))[0]


# the wings: their outline (the rim's), the outline of their bodies (where they rise from the rim: found as the line
# where the distance from the outline is the rim's width), the crests, the grooves' fronts and ends (their backs are the
# crests')
_ga, _gu = np.arange(-.45, 1.16, .004), np.arange(.45, 1.62, .004)
_GA, _GU = np.meshgrid(_ga, _gu)
_rise = np.where(inside(WING, _GA, _GU), np.minimum(dist(OUTLINE, _GA, _GU) - RIM, _GU - .5), -1.0)
BODY_LINES = [simplify([(a, u) for a, u in line], .002) for line in contour(_rise, _ga, _gu)]
for s in (1, -1):
    xy = lambda pts: [(s * u * W, y_along(a)) for a, u in pts]
    at_core = lambda p, q: abs(p[0]) < .501 * W and abs(q[0]) < .501 * W       # along the core's side, inside it
    edges_along('guard', {'part': 'wing', 'layer': 'rim', 'depth': T_RIM * W}, xy(OUTLINE), wing_region('rim', s), T_RIM * W,
                out=.025)
    for line in BODY_LINES:
        edges_along('guard', {'part': 'wing', 'layer': 'body', 'depth': T_BODY * W}, xy(line), wing_region('body', s),
                    T_BODY * W, skip=at_core)
    edges_along('guard', {'part': 'wing', 'layer': 'crest', 'depth': T_CREST * W}, xy(CREST), wing_region('crest', s),
                T_CREST * W, closed=True, skip=at_core)
    front = GROOVE[len(GROOVE) // 2 - 1:] + GROOVE[:1]          # its back's last point round its front to its back's first
    edges_along('guard', {'part': 'wing', 'layer': 'body', 'depth': T_BODY * W}, xy(front), wing_region('body', s), T_BODY * W)

# the plate: its sides towards the point, rising with the plate to the guard's front (on each face) and on from there,
# and its sides towards the grip (through it)
plate_z = lambda a: (PLATE_Z[0] + (PLATE_Z[1] - PLATE_Z[0]) * float(np.clip((a - PLATE[0]) / -PLATE[0], 0, 1))) * W
plate_in = lambda x, y: inside(PLATE_POLY, along_of(y), x / W)
for s in (1, -1):
    tip, wide, back = (PLATE[0], 0.0), (PLATE[1], s * PLATE[2]), (PLATE[3], 0.0)
    mid = (0.0, s * PLATE[2] * -PLATE[0] / (PLATE[1] - PLATE[0]))
    p, q = (tip[1] * W, y_along(tip[0])), (mid[1] * W, y_along(mid[0]))
    for side in (1, -1):
        edge_face('guard', {'part': 'plate'}, (*p, side * plate_z(tip[0])), (*q, side * plate_z(0.0)), on_left(p, q, plate_in),
                  side, plate_z(0.0) - .05 * W)
    for a0, a1 in ((mid, wide), (wide, back)):
        p, q = (a0[1] * W, y_along(a0[0])), (a1[1] * W, y_along(a1[0]))
        edge_through('guard', {'part': 'plate'}, p, q, on_left(p, q, plate_in), plate_z(0.0))

# the socket: each layer's foot, from the end of its leg in to the gem's widest; the notch's sides (the body's)
for name, inset, T, top in SOCKET_LAYERS:
    region = (lambda inset, top: lambda x, y: (lambda a, r: a > top and r is not None and r[0] <= abs(x) / W <= r[1])(
        along_of(y), socket_run(along_of(y), inset)))(inset, top)
    like = {'part': 'socket', 'layer': name, 'depth': T * W, 'run': (0.0, 9.0)}
    for s in (1, -1):
        p, q = (s * (SOCKET[0] - inset) * W, y_along(SOCKET[1] - inset)), (s * GEMS[0][4] * W, y_along(NOTCH_FOOT))
        edge_through('guard', like, p, q, on_left(p, q, region), T * W)
        if name == 'body':
            p = (0.0, y_along(NOTCH_TOP))
            edge_through('guard', like, p, q, on_left(p, q, region), T * W)

# the gems: their borders' sides (the upper gem's but where the socket holds it), their inner diamonds' sides; each on
# its face, rising and falling with the grip
for gi, gem in enumerate(ALL_GEMS):
    end, way, length, widest, half = gem
    inset = GEM_INSETS[gi]
    ta, tb = widest * inset / half, length - (length - widest) * inset / half
    outer = gem_poly(gem)
    inner = [(end + way * ta, 0.0), (end + way * widest, half - inset), (end + way * tb, 0.0), (end + way * widest, inset - half)]
    for part, poly, which, sides in (('gem', outer, 0, (1, 2) if gi == 0 else (0, 1, 2, 3)), ('gem_top', inner, 1, (0, 1, 2, 3))):
        region = (lambda part, gem, inset: lambda x, y: gem_half(gem, y / W + P_HOLD) - (inset if part == 'gem_top' else 0) > abs(x) / W)(part, gem, inset)
        top = (lambda gi, which: lambda pp: float(gem_tops(gi, pp)[which]) * W)(gi, which)
        floor = (lambda gi, part: lambda pp: (POMMEL_Z[1] - .01 if gi == 2 else .12) * W if part == 'gem' else (float(gem_tops(gi, pp)[0]) - .01) * W)(gi, part)
        for k in sides:
            (p0, x0), (p1, x1) = poly[k], poly[(k + 1) % 4]
            p, q = (x0 * W, y_of(p0)), (x1 * W, y_of(p1))
            thick = min(top(p0), top(p1)) - max(floor(p0), floor(p1))
            for f in (1, -1):
                edge_face('grip', {'part': part, 'gem': gi}, (*p, f * top(p0)), (*q, f * top(p1)), on_left(p, q, region), f,
                          max(thick, .02))

# the pommel: its shield's slanting sides and its panel's
for part, poly, half_t in (('pommel', POMMEL_SHIELD, POMMEL_Z[0]), ('pommel_panel', POMMEL_PANEL, POMMEL_Z[1])):
    region = (lambda poly: lambda x, y: half_along(poly, y / W + P_HOLD) > abs(x) / W)(poly)
    for (p0, h0), (p1, h1) in zip(poly, poly[1:]):
        if abs(h1 - h0) < 1e-9:
            continue                                         # a side along the grip: no steps
        for s in (1, -1):
            p, q = (s * h0 * W, y_of(p0)), (s * h1 * W, y_of(p1))
            edge_through('grip', {'part': part, 'depth': half_t * W}, p, q, on_left(p, q, region), half_t * W)

# the point: its two edges and the edges of its flat, slice by slice (its slices keep inside them)
tip_t = lambda y: float(np.clip((y - Y_TIP_FROM) / (Y_TIP_TO - Y_TIP_FROM), 0, 1))
tip_in = lambda x, y: abs(x) < float(tip_half_width(tip_t(y)))
flat_in = lambda x, y: abs(x) < (FLAT + .03) * W * float(tip_half_width(tip_t(y))) / (TAPER * W / 2)
for i in range(TIP_SLICES):
    t0, t1, t = i / TIP_SLICES, (i + 1) / TIP_SLICES, (i + .5) / TIP_SLICES
    y0, y1 = Y_TIP_FROM + (Y_TIP_TO - Y_TIP_FROM) * t0, Y_TIP_FROM + (Y_TIP_TO - Y_TIP_FROM) * t1
    h0, h1 = float(tip_half_width(t0)), float(tip_half_width(t1))
    m0, m1 = ((FLAT + .03) * W * h / (TAPER * W / 2) for h in (h0, h1))
    th, tf = (T_OUTER * (1 - t) + .012 * t) * W, (T_FLAT * (1 - t) + .022 * t) * W
    for s in (1, -1):
        p, q = (s * h0, y0), (s * h1, y1)
        edge_through('blade', {'part': 'tip', 'depth': th}, p, q, on_left(p, q, tip_in), th, out=0.0, inward=.1)
        p, q = (s * m0, y0), (s * m1, y1)
        edge_through('blade', {'part': 'tip_flat', 'depth': tf}, p, q, on_left(p, q, flat_in), tf, out=0.0, inward=.06)


# ---------------------------------------------------------------- the lightning on the flat
LIGHTNING = (FLAT - .06) * W              # how far from the middle it runs


def make_bolts(seed):
    """Jagged bolts along a flat (segments x0, y0, x1, y1, core radius, halo radius): one running most of the blade from
    beyond the guard's plate, a shorter one further up, long strokes with sharp kicks and gaps, now and then a short
    branch; all on the flat and short of the point."""
    rnd = random.Random(seed)
    segs = []
    start = y_along(PLATE[0])
    runs = [(start + 1.45 + rnd.random() * .6, Y_TIP_FROM - 1.0 - rnd.random() * 2.5, .05, .135),
            (start + 7.5 + rnd.random() * 4, start + 13.5 + rnd.random() * 4, .036, .1)]
    lim = LIGHTNING
    for begin, end, core, halo in runs:
        y, x, gap = begin, rnd.uniform(-.5, .5) * lim, False
        while y < end:
            ny = y + rnd.uniform(.55, 1.25)
            nx = float(np.clip(x + rnd.uniform(-.3, .3) * lim + (rnd.choice([-.75, .75]) * lim if rnd.random() < .35 else 0), -lim, lim))
            if not gap:
                segs.append((x, y, nx, ny, core, halo))
                if rnd.random() < .18:
                    bx, by, way = nx, ny, rnd.choice([-1, 1])
                    for _ in range(rnd.randint(1, 3)):
                        cx = float(np.clip(bx + way * rnd.uniform(.2, .5) * lim, -lim, lim))
                        cy = by + rnd.uniform(.2, .45)
                        segs.append((bx, by, cx, cy, core * .6, halo * .65))
                        bx, by = cx, cy
            gap = (not gap) and rnd.random() < .16
            x, y = nx, ny
    return np.array(segs)


def make_glints(seed, bolts):
    """Little four-pointed glints (x, y, radius), most of them close by the bolts."""
    rnd = random.Random(seed + 1)
    out = []
    for k in range(11):
        if k < 8:
            s = bolts[rnd.randrange(len(bolts))]
            x = float(np.clip(s[2] + rnd.uniform(-.18, .18), -LIGHTNING, LIGHTNING))
            y = s[3] + rnd.uniform(-.2, .2)
        else:
            x, y = rnd.uniform(-.85, .85) * LIGHTNING, rnd.uniform(y_along(PLATE[0]) + .5, Y_TIP_FROM - 1)
        out.append((x, y, rnd.uniform(.1, .17)))
    return np.array(out)


BOLTS = {1: make_bolts(11), -1: make_bolts(23)}
GLINTS = {s: make_glints(s * 7 + 40, BOLTS[s]) for s in (1, -1)}


# ---------------------------------------------------------------- the decoration
# (2.76.6, user request: "마지막으로 예쁘게 꾸미면서 다듬어줘") A little starlight over the sword, as in the art: on the
# blade's flat by the guard a crescent moon and a star (her name, 월흔, a moon's trace), and tiny stars all along it;
# four-pointed twinkles on the guard's gold and on the gems; flame strokes engraved on the wings; the gold's lines
# engraved (an ink line with a bright edge beside it), a bright rim inside each outline; facets cut in the gems; a little
# white gem on each face of the pommel's panel; beads round the collar. The twinkles, the stars and the crescent glow.
MOON = (.78, .27, (.1, .075), .225)      # the crescent: its centre (units above the plate's point), radius, the bite's
                                          # offset and radius
MOON_STAR = ((-.2, .33), .17)            # the star beside it: its offset from the crescent's centre, its reach
TWINKLES = [(.30, 1.38, .17), (.53, .92, .12)]   # on each face of each wing: along, up, reach (units)
CRYSTAL_TWINKLE = (-.15, .88, .14)
# the flame strokes engraved in front of the groove: how far in front of the groove's front (blade widths), the stretch
# of it (up) they run
STROKES = [(.075, (.64, 1.07)), (.15, (.72, 1.0)), (.225, (.82, .95))]
STROKE_W = .014                           # their half width at their widest (blade widths)
def twinkle(x, y, cx, cy, r):
    """A four-pointed twinkle at (cx, cy) reaching r (units): a bright dot with four thin arms fading to their tips; how
    bright it is at each point (0 to 1)."""
    dx, dy = np.abs(x - cx), np.abs(y - cy)
    arm = lambda a, b: np.clip(1 - a / r, 0, 1) ** 1.3 * (b < .006 + .05 * r * np.clip(1 - a / r, 0, 1))
    return np.maximum(np.maximum(arm(dx, dy), arm(dy, dx)), np.clip(1 - np.hypot(dx, dy) / (.32 * r), 0, 1))


def make_stars(seed):
    """Tiny stars along a flat (x, y, radius, brightness), past the crescent to short of the point."""
    rnd = random.Random(seed)
    lo, hi = y_along(PLATE[0]) + 1.25, Y_TIP_FROM - .6
    return np.array([(rnd.uniform(-.92, .92) * LIGHTNING, rnd.uniform(lo, hi), rnd.uniform(.022, .04), rnd.uniform(.55, 1))
                     for _ in range(30)])


STARS = {1: make_stars(5), -1: make_stars(17)}


def star_light(side, x, y):
    out = np.zeros_like(x)
    for sx, sy, r, b in STARS[side]:
        out = np.maximum(out, b * np.clip(1 - np.hypot(x - sx, y - sy) / r, 0, 1) ** .7)
    return out


def moon_light(x, y):
    """The crescent by the guard and its star: 1 on them, a soft halo round them (like the lightning's)."""
    cy, r, (ox, oy), rb = y_along(PLATE[0]) + MOON[0], MOON[1], MOON[2], MOON[3]
    d_out = np.hypot(x, y - cy) - r                      # signed: outside the moon's disc
    d_bite = rb - np.hypot(x - ox, y - cy - oy)          # signed: inside the bite
    d = np.maximum(d_out, d_bite)                        # outside the crescent
    light = np.where(d < 0, 1.0, np.clip(1 - d / .1, 0, 1) ** 2 * .7)
    (sx, sy), sr = MOON_STAR
    return np.maximum(light, twinkle(x, y, sx, cy + sy, sr))


def engraved(d, w=.018, edge=.016):
    """An engraved line at distance d from its middle line (blade widths): its groove (ink) and the bright edge beside it."""
    return d < w, (d >= w) & (d < w + edge)


def strokes(a, up):
    """The flame strokes on a wing's face: how far each point is from the nearest (blade widths) less its half width
    there (negative inside), and on which side of it the point lies (towards the groove)."""
    best, side = np.full(np.shape(a), np.inf), np.zeros(np.shape(a), dtype=bool)
    for off, (u0, u1) in STROKES:
        us = np.linspace(u0, u1, 28)
        line = [(float(through(CURVE, u)) - GROOVE_W - off, float(u)) for u in us]
        t = np.clip((up - u0) / (u1 - u0), 0, 1)
        half = STROKE_W * np.sin(np.pi * t) ** .55 * ((up >= u0) & (up <= u1))
        d = dist(line, a, up) - half
        nearer = d < best
        best = np.where(nearer, d, best)
        side = np.where(nearer, a > through(CURVE, up) - GROOVE_W - off, side)
    return best, side


def bolt_light(side, x, y):
    """How the lightning lights each point: 1 on a bolt's core, falling off across its halo."""
    best = np.zeros_like(x)
    for x0, y0, x1, y1, core, halo in BOLTS[side]:
        dx, dy = x1 - x0, y1 - y0
        t = np.clip(((x - x0) * dx + (y - y0) * dy) / (dx * dx + dy * dy), 0, 1)
        d = np.hypot(x - (x0 + t * dx), y - (y0 + t * dy))
        v = np.where(d < core, 1.0, np.clip(1 - (d - core) / (halo - core), 0, 1) ** 2 * .78)
        best = np.maximum(best, v)
    return best


def glint_light(side, x, y):
    """The glints: a white dot with four thin arms each, fading to their ends."""
    out = np.zeros_like(x)
    for gx, gy, r in GLINTS[side]:
        dx, dy = np.abs(x - gx), np.abs(y - gy)
        arm = lambda a, b: np.clip(1 - a / r, 0, 1) * (b < .018 + .05 * r * np.clip(1 - a / r, 0, 1))
        dot = np.clip(1 - np.hypot(dx, dy) / (.28 * r), 0, 1)
        out = np.maximum(out, np.maximum(np.maximum(arm(dx, dy), arm(dy, dx)), dot))
    return out


# ---------------------------------------------------------------- painting
def rot_matrix(r):
    """A cube's turn as GeckoLib applies it: Rz(-rz) . Ry(ry) . Rx(-rx) (degrees)."""
    rx, ry, rz = (math.radians(a) for a in r)
    cx, sx = math.cos(-rx), math.sin(-rx)
    cy, sy = math.cos(ry), math.sin(ry)
    cz, sz = math.cos(-rz), math.sin(-rz)
    Rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    Ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    Rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return Rz @ Ry @ Rx


def face_point(c, face, u, v):
    """Points (arrays) at (u, v) on a face, in the cube's own unturned frame."""
    x0, y0, z0 = c.origin
    sx, sy, sz = c.size
    x1, y1, z1 = c.origin + c.size
    one = np.ones_like(u)
    if face == 'north':
        return x0 + u * sx, y1 - v * sy, z0 * one
    if face == 'south':
        return x1 - u * sx, y1 - v * sy, z1 * one
    if face == 'east':
        return x0 * one, y1 - v * sy, z1 - u * sz
    if face == 'west':
        return x1 * one, y1 - v * sy, z0 + u * sz
    if face == 'up':
        return x0 + u * sx, y1 * one, z1 - v * sz
    return x0 + u * sx, y0 * one, z0 + v * sz


def world_point(c, x, y, z):
    """Those points where the cube's turn puts them (in the model)."""
    if c.rot is None:
        return x, y, z
    R, p = rot_matrix(c.rot), c.pivot
    q = np.stack([x - p[0], y - p[1], z - p[2]], axis=-1) @ R.T
    return q[..., 0] + p[0], q[..., 1] + p[1], q[..., 2] + p[2]


FACE_DIMS = {'north': (0, 1), 'south': (0, 1), 'east': (2, 1), 'west': (2, 1), 'up': (0, 2), 'down': (0, 2)}
FLATS = ('north', 'south')


def border(c, face, u, v, width):
    """Whether points lie within width (units) of their face's border."""
    a, b = FACE_DIMS[face]
    w, h = c.size[a], c.size[b]
    return (u * w < width) | ((1 - u) * w < width) | (v * h < width) | ((1 - v) * h < width)


def solid(colour, shape):
    return np.broadcast_to(colour, shape + (3,)).copy()


def wall(c, face, z, base, depth):
    """A wall of a slice (its outline's edge, a step down): the gold of its layer, an ink line where it meets the face."""
    col = solid(base, z.shape)
    return np.where((np.abs(z) > depth - .028 * W)[..., None], GOLD_INK, col)


def tip_face(x, y):
    """The point's faces beside its flat: the bevels' bands drawn in to the tip, the line across where it begins; by
    the point's outline where each texel is (not its slice's), so that the bands run on smoothly from slice to slice."""
    hw = tip_half_width(np.clip((y - Y_TIP_FROM) / (Y_TIP_TO - Y_TIP_FROM), 0, 1))
    seen = np.maximum(hw - (FLAT + .03) * W * hw / (TAPER * W / 2), 1e-6)   # how much of each side shows beside the flat
    du, dl = (hw - x) / seen, (x + hw) / seen
    col = solid(mix(BEVEL_DARK, BODY_DEEP, .25), x.shape)
    col = np.where(((x > 0) & (du > .62))[..., None], EDGE_LIGHT, col)
    col = np.where((x < 0)[..., None], np.where((dl < .55)[..., None], EDGE_LOW, mix(BODY, BODY_DEEP, .55)), col)
    col = np.where(((hw - np.abs(x)) < .035 * W)[..., None], INK, col)
    return np.where((np.abs(y - Y_TIP_FROM) < .03)[..., None], INK, col)


def paint_blade(c, face, x, y, z, u, v):
    """The blade: its flat (the body, lighter by the guard, the lightning and its glints), its bevels (the upper edge a
    dark band with a bright line inside it, the lower a bright line and a deeper band inside it, ink at the edges), its
    ground edges, the point."""
    glow = np.zeros_like(x, dtype=bool)
    near = mix(BODY_NEAR, BODY, smooth((y - Y_ROOT) / 6.5))
    if c.part == 'flat':
        if face in FLATS:
            side = 1 if face == 'south' else -1
            col = near * (1 + .035 * np.sin(x * 41.0 + np.sin(x * 13.0) * 2.0))[..., None]     # brushed lengthwise, faintly
            b = np.maximum(bolt_light(side, x, y), moon_light(x, y))       # the lightning, the crescent and its star
            col = np.where((b >= 1)[..., None], CRACK_CORE, mix(col, CRACK_HALO, b))
            glow |= b > .45
            g = np.maximum(glint_light(side, x, y), star_light(side, x, y))   # the glints, the tiny stars
            col = mix(col, GLINT, g)
            glow |= g > .4
            return col, glow
        return solid(mix(BEVEL_DARK, BODY_DEEP, .3), x.shape), glow       # the step down to a bevel
    if c.part in ('bevel_in', 'bevel_out'):
        upper = c.opts['edge'] == 'upper'
        if face in FLATS:
            out = (c.origin[0] + c.size[0] - x) if upper else (x - c.origin[0])   # from its outer edge (its own frame)
            if c.part == 'bevel_in':
                col = np.where((out < .07 * W)[..., None], EDGE_LIGHT, near) if upper else mix(near, BODY_DEEP, .55)
            else:
                col = solid(mix(BEVEL_DARK, BODY_DEEP, .25) if upper else EDGE_LOW, x.shape)
                col = np.where((out < .035 * W)[..., None], INK, col)
            return col, glow
        if c.part == 'bevel_in':
            return solid(mix(BEVEL_DARK, BODY_DEEP, .3), x.shape), glow
        t = np.abs(z) / max(c.size[2] / 2, 1e-6)                                  # a ground edge
        return mix(STEEL_EDGE * 1.05, mix(STEEL_EDGE, BEVEL_DARK, .55), smooth(t)), glow
    # the point: its flat down the middle, the bevels' bands drawn in either side of it to the tip, the line across
    # where it begins
    line = (np.abs(y - Y_TIP_FROM) < .03)[..., None]
    if c.part == 'tip_flat':
        if face in FLATS:
            return np.where(line, INK, solid(BODY, x.shape)), glow
        return solid(mix(BEVEL_DARK, BODY_DEEP, .3) if face in ('east', 'west') else BODY_DEEP, x.shape), glow
    if face in FLATS:
        return tip_face(x, y), glow
    if face in ('up', 'down'):
        return solid(BODY_DEEP, x.shape), glow
    t = np.abs(z) / max(c.size[2] / 2, 1e-6)
    return mix(STEEL_EDGE * 1.05, mix(STEEL_EDGE, BEVEL_DARK, .55), smooth(t)), glow


def lit_side(line, a, up):
    """Which points lie on the lit side of a straight line (a, up pairs): its side towards the grip, or towards the
    upper edge for a line running along the sword (an engraved groove's far wall catches the light)."""
    (a0, u0), (a1, u1) = line[0], line[-1]
    na, nu = u1 - u0, -(a1 - a0)                    # a normal to it
    if abs(na) < 1e-9 or (abs(na) < abs(nu) * .2 and nu < 0):
        na, nu = (-na, -nu) if nu < 0 else (na, nu)
    elif na < 0:
        na, nu = -na, -nu
    return (a - a0) * na + (up - u0) * nu > 0


def engrave(col, line, a, up, closed=False, w=.018, edge=.016):
    """Engrave a line on a face: its groove inked, a bright edge beside it on the lit side."""
    d = dist(line, a, up, closed)
    groove, rim = engraved(d, w, edge)
    col = np.where((rim & lit_side(line, a, up))[..., None], mix(col, GOLD[6], .85), col)
    return np.where(groove[..., None], GOLD_INK, col)


def wing_twinkles(x, y):
    """The twinkles on a wing's faces (both wings): how bright each point is."""
    out = np.zeros_like(x)
    for ta, tu, r in TWINKLES:
        out = np.maximum(out, twinkle(np.abs(x), y, tu * W, y_along(ta), r))
    return out


def paint_wing(c, face, x, y, z, u, v):
    """A wing's slice: on its faces the rim (an ink line at the outline) and, through the groove, the navy floor; the
    body (lighter in front of the groove, darker on the back facet behind the crest, ink where it steps down); the crest;
    its walls the gold of its layer."""
    glow = np.zeros_like(x, dtype=bool)
    layer = c.opts['layer']
    a, up = along_of(y), np.abs(x) / W
    crystal = a < through(CRYSTAL_EDGE, up)
    if face not in FLATS:
        if layer == 'rim':
            col = wall(c, face, z, GOLD[2], c.opts['depth'])
            # the crystal's edge: two facets either side of a ridge along it (the south's lit), as the rest of it
            ccol = np.where((np.abs(z) < .012 * W)[..., None], CRYSTAL_LINE, np.where((z > 0)[..., None], CRYSTAL[1], CRYSTAL[2]))
            return np.where((crystal & (np.abs(z) <= c.opts['depth'] - .028 * W))[..., None], ccol, col), glow
        # the steps up to the body and the crest read as bevels: no ink, lighter towards the face they rise to
        lift = smooth(np.abs(z) / c.opts['depth'])
        return np.where(crystal[..., None], mix(CRYSTAL[2], CRYSTAL[1], lift), mix(GOLD[2], GOLD[4], lift)), glow
    d = dist(OUTLINE, a, up)
    if crystal.any():
        # the white crystal reaching out between the plate and the wing: two facets either side of its ridge (the one
        # towards the wing's top lit), a fine grey-blue line down the ridge and round the face it rises to, inked round
        # its outline and where it comes out of the gold
        (ba, bu), (ta, tu) = CRYSTAL_RIDGE
        lit = (ta - ba) * (up - bu) - (tu - bu) * (a - ba) < 0
        ccol = np.where(lit[..., None], mix(CRYSTAL[1], CRYSTAL[0], .7), mix(CRYSTAL[1], CRYSTAL[2], .55 if layer == 'rim' else .4))
        ccol = np.where((dist(CRYSTAL_RIDGE, a, up) < .012)[..., None], CRYSTAL_LINE, ccol)
        if layer != 'rim':
            ccol = np.where((d < RIM + .02)[..., None], CRYSTAL_LINE, ccol)
        ccol = np.where(((d < .03) | (np.abs(a - through(CRYSTAL_EDGE, up)) < .016))[..., None], GOLD_INK, ccol)
        tw = twinkle(np.abs(x), y, CRYSTAL_TWINKLE[1] * W, y_along(CRYSTAL_TWINKLE[0]), CRYSTAL_TWINKLE[2]) * crystal
        ccol, glow = mix(ccol, SPARKLE, tw), glow | (tw > .45)
    groove = inside(GROOVE, a, up)
    g_edge = dist(GROOVE, a, up, closed=True) < .022
    if layer == 'rim':
        col = solid(mix(GOLD[3], GOLD[4], .3), x.shape)
        col = np.where(((d >= .03) & (d < .043))[..., None], mix(col, GOLD[6], .8), col)            # a bright rim inside the ink
        col = np.where(((d > RIM - .03) & (d < RIM + .01))[..., None], mix(col, GOLD_INK, .55), col)   # where the body rises
        col = np.where(groove[..., None], mix(NAVY[1], NAVY[2], smooth((up - GROOVE_SPAN[0]) / .5)), col)
        col = np.where((g_edge & (d >= RIM))[..., None], GOLD_INK, col)
        col = np.where((d < .03)[..., None], GOLD_INK, col)
        return (np.where(crystal[..., None], ccol, col) if crystal.any() else col), glow
    if layer == 'body':
        front = a < through(CURVE, up) - GROOVE_W
        back = a > through(BACK_LINE, up)
        col = solid(GOLD[4], x.shape)
        col = np.where(front[..., None], mix(GOLD[4], GOLD[5], .55 * smooth(1 - np.abs(a - through(CURVE, up) + .2) / .12)), col)
        col = np.where(back[..., None], mix(GOLD[3], GOLD[4], .3), col)
        col = mix(col, GOLD[5], .25 * smooth((up - .75) / .65))            # polished: brighter up towards the peak
        sd, groove_side = strokes(a, up)                                   # the flame strokes, a bright edge on their far side
        col = np.where(((sd >= 0) & (sd < .015) & ~groove_side & front)[..., None], mix(col, GOLD[6], .9), col)
        col = np.where(((sd < 0) & front)[..., None], mix(GOLD_INK, GOLD[1], .35), col)
        col = np.where(((d >= RIM + .028) & (d < RIM + .041))[..., None], mix(col, GOLD[6], .75), col)   # a bright rim
        col = np.where((dist(BACK_LINE, a, up) < .016)[..., None], mix(col, GOLD_INK, .7), col)
        col = np.where((g_edge | (d < RIM + .028) | (dist(CREST, a, up, closed=True) < .02))[..., None], GOLD_INK, col)
        tw = wing_twinkles(x, y)
        col, glow = mix(col, SPARKLE, tw), glow | (tw > .45)
        return (np.where(crystal[..., None], ccol, col) if crystal.any() else col), glow
    dc = dist(CREST, a, up, closed=True)
    col = mix(GOLD[5], GOLD[6], .35 * smooth(dc / .12))                 # the crest rounded: brightest along its middle
    col = np.where(((dc >= .026) & (dc < .039))[..., None], mix(col, GOLD[6], .8), col)
    col = np.where(((dc < .026) | (d < RIM + .055))[..., None], GOLD_INK, col)
    tw = wing_twinkles(x, y)
    return mix(col, SPARKLE, tw), glow | (tw > .45)


def paint_core(c, face, x, y, z, u, v):
    """The core between the wings: two lines crossing at the seam across it, the four facets between them, towards the
    grip the ridges beside the socket's notch meeting in a point (brighter between them, as on the socket); its sides
    gold."""
    glow = np.zeros_like(x, dtype=bool)
    if face not in FLATS:
        return wall(c, face, z, GOLD[3], T_CORE * W), glow
    a, up = along_of(y), x / W
    col = np.where((np.abs(up) > np.abs(a - .635) * 1.16)[..., None], GOLD[4], GOLD[3])     # the facets above and below the crossing lighter
    w = (CORE_TO - RIDGE_TOP) * NOTCH_SLOPE
    col = np.where((np.abs(up) < (a - RIDGE_TOP) * NOTCH_SLOPE)[..., None], GOLD[5], col)
    col = mix(col, GOLD[5], .2 * smooth(1 - np.hypot(a - .635, up) / .5))   # polished: brightest about the crossing
    for line in ([(.42, .5), (.85, -.5)], [(.42, -.5), (.85, .5)], [(.635, -.45), (.635, .45)], [(CORE_TO, -w), (RIDGE_TOP, 0)],
                 [(RIDGE_TOP, 0), (CORE_TO, w)]):
        col = engrave(col, line, a, up)
    return col, glow


def paint_socket(c, face, x, y, z, u, v):
    """The socket below the core: on its faces gold, brighter between the notch and the ridges beside it (as on the core
    above), a bright lip along the notch (the gem's own outline inks where they meet), the layers in from its outline
    lighter, inked at its edge; its outer walls gold inked at the face, the notch's and the foot's in shade, and the
    step up from the core dark (so that it reads as a crease, not as a light seam across it)."""
    glow = np.zeros_like(x, dtype=bool)
    a, up = along_of(y), np.abs(x) / W
    if face not in FLATS:
        if face == 'up':
            return solid(mix(GOLD_INK, GOLD[1], .5), x.shape), glow
        if face == 'down' or np.all(np.abs(up - c.opts['run'][1]) > 1e-3):
            return solid(GOLD[2], x.shape), glow
        return np.where((np.abs(z) > c.opts['depth'] - .02 * W)[..., None], GOLD_INK, solid(GOLD[3], z.shape)), glow
    d_out, d_notch = dist(SOCKET_OUTLINE, a, up), dist(NOTCH_SIDE, a, up)
    between = up < (a - RIDGE_TOP) * NOTCH_SLOPE
    lift = {'rim': .25, 'mid': .6, 'body': 1.0}[c.opts['layer']]          # the layers in from the outline lighter
    col = np.where(between[..., None], mix(GOLD[4], GOLD[5], lift), solid(mix(GOLD[3], GOLD[4], lift), x.shape))
    col = np.where((d_notch < .03)[..., None], GOLD[6], col)
    col = engrave(col, [(RIDGE_TOP, 0.0), (SOCKET[1], (SOCKET[1] - RIDGE_TOP) * NOTCH_SLOPE)], a, up)
    return np.where((d_out < .02)[..., None], GOLD_INK, col), glow


def paint_plate(c, face, x, y, z, u, v):
    """The plate on the flats: gold, ink round its outline, the navy diamond inside it (lighter down its middle)."""
    glow = np.zeros_like(x, dtype=bool)
    if face not in FLATS:
        return wall(c, face, z, GOLD[3], c.opts['depth']), glow
    a, up = along_of(y), x / W
    col = mix(GOLD[4], GOLD[5], smooth((a - PLATE[0]) / .6) * .5)
    inner = inside(INNER_DIAMOND, a, up)
    col = np.where(inner[..., None], mix(mix(NAVY[1], NAVY[2], smooth((a + .36) / .7)), NAVY[3], .45 * (np.abs(up) < .05)), col)
    col = np.where((dist(INNER_DIAMOND, a, up, closed=True) < .024)[..., None], GOLD_INK, col)
    dp = dist(PLATE_POLY, a, up, closed=True)
    col = np.where(((dp >= .03) & (dp < .042))[..., None], mix(col, GOLD[6], .8), col)          # a bright rim inside the ink
    col = np.where(((dp >= .068) & (dp < .08) & ~inner)[..., None], mix(GOLD_INK, GOLD[1], .3), col)   # an engraved border
    col = np.where(((dp >= .08) & (dp < .096) & ~inner)[..., None], mix(col, GOLD[6], .8), col)
    col = np.where((dp < .03)[..., None], GOLD_INK, col)
    tw = twinkle(x, y, 0.0, y_along(-.6), .14)
    return mix(col, SPARKLE, tw), glow | (tw > .45)


def paint_knob(c, face, x, y, z, u, v):
    """A knob: its boss's face gold, inked round its rim; the dome on it bright, lit towards its top; their sides
    shaded."""
    glow = np.zeros_like(x, dtype=bool)
    if face in FLATS:
        cx, cy = c.opts['centre']
        r = np.hypot(x - cx, y - cy) / (KNOB[2] * W)          # out from the middle, in the boss's radii
        if c.opts['tier']:
            lit = smooth(1 - np.hypot(x - cx + .15 * KNOB[2] * W, y - cy - .2 * KNOB[2] * W) / (.62 * KNOB[2] * W))
            tw = twinkle(x, y, cx - .15 * KNOB[2] * W, cy + .2 * KNOB[2] * W, .07)
            return np.where((r > .56)[..., None], GOLD_INK, mix(mix(GOLD[5], GOLD[6], lit), SPARKLE, tw)), glow | (tw > .45)
        return np.where((r > .86)[..., None], GOLD_INK, solid(GOLD[4], x.shape)), glow
    return solid(GOLD[3], x.shape), glow


def band(c, face, x, y, z):
    """The collar: gold, lit a little more towards the blade, an ink line at each end and round the edge of an end that
    shows."""
    y0, y1 = c.opts['span']
    if face in ('up', 'down'):
        col = solid(GOLD[5] if face == 'up' else GOLD[3], x.shape)
        return np.where((np.hypot(x, z) > c.opts['r'] - .03 * W)[..., None], GOLD_INK, col)
    col = mix(GOLD[3], GOLD[5], .3 + .45 * (y - y0) / (y1 - y0))
    ang = np.arctan2(z, x)
    near = np.round(ang / (np.pi / 8)) * (np.pi / 8)               # the middle of the side each point is on
    bead = np.hypot((ang - near) * c.opts['r'], y - (y0 + y1) / 2) / (.05 * W)
    lit = smooth(1 - np.hypot((ang - near) * c.opts['r'] + .015 * W, y - (y0 + y1) / 2 - .015 * W) / (.04 * W))
    col = np.where((bead < 1)[..., None], np.where((bead > .78)[..., None], GOLD_INK, mix(GOLD[4], GOLD[6], lit)), col)
    return np.where(((y - y0 < .03) | (y1 - y < .03))[..., None], GOLD_INK, col)


def paint_gem(c, face, x, y, z):
    """A white gem: its border bevelled in four facets (the one up towards the guard on the left lit), inked round its
    outline; its inner diamond brightest, a fine grey-blue line round it; its sides shaded, their top edge the line
    round the face above it (so that where a side stands a hair above a straight edge it reads as that line)."""
    gi = c.opts['gem']
    gem, glow = ALL_GEMS[gi], np.zeros_like(x, dtype=bool)
    p, xa = y / W + P_HOLD, x / W
    if face not in FLATS:
        top = gem_tops(gi, p)[1 if c.part == 'gem_top' else 0] * W
        return np.where((np.abs(z) > top - .02 * W)[..., None], CRYSTAL_LINE if c.part == 'gem_top' else GOLD_INK,
                        solid(CRYSTAL[2], x.shape)), glow
    poly = gem_poly(gem)
    upper, left = p > poly[1][0], xa < 0                 # towards the blade; towards the lower edge
    if c.part == 'gem_top':
        # its facets cut from its middle to its four corners: the one up towards the blade on the left white, the others
        # in from it, the lower two cool; a fine line along each cut and round it; a twinkle on the white facet
        inner = [(q, u * (1 - GEM_INSETS[gi] / gem[4])) for q, u in poly]
        col = np.where((upper & left)[..., None], CRYSTAL[0], np.where(upper[..., None], CRYSTAL[1],
                       np.where(left[..., None], mix(CRYSTAL[1], GEM_TINT, .6), mix(CRYSTAL[2], GEM_TINT, .5))))
        centre = (poly[1][0], 0.0)
        cuts = np.minimum.reduce([dist([centre, (q, u)], p, xa) for q, u in inner])
        col = np.where((cuts < .008)[..., None], mix(CRYSTAL_LINE, CRYSTAL[1], .45), col)
        col = np.where((dist(inner, p, xa, closed=True) < .016)[..., None], CRYSTAL_LINE, col)
        top_end = max(poly[0][0], poly[2][0])
        tp, tx = poly[1][0] + .45 * (top_end - poly[1][0]) * (1 - GEM_INSETS[gi] / gem[4]), -.35 * (gem[4] - GEM_INSETS[gi])
        tw = twinkle(x, y, tx * W, y_of(tp), .1 if gi < 2 else .07)
        return mix(col, SPARKLE, tw), glow | (tw > .45)
    col = np.where((upper & left)[..., None], CRYSTAL[0], np.where((upper | left)[..., None], CRYSTAL[1], CRYSTAL[2]))
    return np.where((dist(poly, p, xa, closed=True) < .022)[..., None], GOLD_INK, col), glow


def paint_hilt(c, face, x, y, z, u, v):
    glow = np.zeros_like(x, dtype=bool)
    part = c.part
    p = y / W + P_HOLD
    if part == 'grip':
        # blue like the blade, a metallic sheen down one side; on each flat side the white bands from the upper gem's
        # point to the lower's, swinging round the grip one each way and crossing halfway, the wider coming out from
        # under the upper gem's widest corner (at the socket's foot) and running down beside its lower side to its point
        ang = np.arctan2(z, x)
        sheen = smooth(1 - np.abs(np.angle(np.exp(1j * (ang - 2.1)))) / .7)
        col = mix(mix(BODY_DEEP, BODY, .55), mix(BODY, BODY_NEAR, .7), sheen)
        r = grip_radius(p) if np.ndim(p) == 0 else np.interp(p, [q[0] for q in GRIP_SHAPE], [q[1] for q in GRIP_SHAPE])
        top, low = GEMS[0][0] + GEMS[0][1] * GEMS[0][2], GEMS[1][0] + GEMS[1][1] * GEMS[1][2]
        corner = GEMS[0][0] + GEMS[0][1] * GEMS[0][3]
        t = (top - p) / (top - low)
        apart, waver, wide, thin = BANDS
        side = np.arctan2(x, np.abs(z))                      # round the grip from the nearer flat side
        tc = np.clip(t, 0, 1)
        a = apart * np.cos(np.pi * tc)
        course = np.where(t < 0, apart + gem_half(GEMS[0], p) / r, a + waver * np.sin(3 * np.pi * tc))
        band_a = (p <= corner) & (t <= 1.08) & (np.abs(side - course) * r < wide / 2)
        band_b = (t >= -.08) & (t <= 1.08) & (np.abs(side + a) * r < thin / 2)    # on under the gems' points
        col = np.where((band_a | band_b)[..., None], CRYSTAL[1], col)
        return col, glow
    if part == 'collar':
        return band(c, face, x, y, z), glow
    if part in ('gem', 'gem_top'):
        return paint_gem(c, face, x, y, z)
    # the pommel: a gold shield, its rim bevelled darker and inked round, the raised panel paler, inked round
    xa = x / W
    shield = POMMEL_SHIELD + [(q[0], -q[1]) for q in POMMEL_SHIELD[:0:-1]]
    panel = POMMEL_PANEL + [(q[0], -q[1]) for q in POMMEL_PANEL[:0:-1]]
    if face not in FLATS:
        return wall(c, face, z, GOLD[3], c.opts['depth']), glow
    if part == 'pommel_panel':
        col = mix(GOLD[5], GOLD[4], np.clip((.93 - p) / .8, 0, 1) * .5)
        return np.where((dist(panel, p, xa, closed=True) < .022)[..., None], GOLD_INK, col), glow
    col = mix(GOLD[4], GOLD[3], np.clip((.9 - p) / .9, 0, 1) * .6)
    return np.where((dist(shield, p, xa, closed=True) < .026)[..., None], GOLD_INK, col), glow


def paint_edge(c, face, x, y, z, u, v):
    """An edge box, painted as what it edges is there: its front and back as that part's faces, its outer side and its
    ends as that part's walls."""
    glow = np.zeros_like(x, dtype=bool)
    like = dict(c.opts['like'])
    part = like.pop('part')
    if part == 'tip':                         # the point's edge: its faces as the point's, ground steel along it
        if face in FLATS:
            return tip_face(x, y), glow
        return mix(STEEL_EDGE * 1.05, mix(STEEL_EDGE, BEVEL_DARK, .55), smooth(np.abs(z) / like['depth'])), glow
    if part == 'tip_flat':
        if face in FLATS:
            return np.where((np.abs(y - Y_TIP_FROM) < .03)[..., None], INK, solid(BODY, x.shape)), glow
        return solid(mix(BEVEL_DARK, BODY_DEEP, .3), x.shape), glow
    if part == 'plate':                       # the plate's walls as deep as the plate is there
        like['depth'] = (PLATE_Z[0] + (PLATE_Z[1] - PLATE_Z[0]) * np.clip((along_of(y) - PLATE[0]) / -PLATE[0], 0, 1)) * W
    stand_in = SimpleNamespace(part=part, bone=c.bone, opts=like)
    return {'wing': paint_wing, 'plate': paint_plate, 'socket': paint_socket}.get(part, paint_hilt)(stand_in, face, x, y, z, u, v)


def paint(c, face, u, v):
    """A face's colours and glow at points (u, v) of it."""
    x, y, z = face_point(c, face, u, v)
    if c.part in ('grip', 'collar', 'gem', 'gem_top', 'knob', 'edge', 'plate'):
        x, y, z = world_point(c, x, y, z)
    if c.part == 'edge':
        return paint_edge(c, face, x, y, z, u, v)
    if c.bone == 'blade':
        return paint_blade(c, face, x, y, z, u, v)
    return {'wing': paint_wing, 'core': paint_core, 'socket': paint_socket, 'plate': paint_plate, 'knob': paint_knob}.get(c.part, paint_hilt)(c, face, x, y, z, u, v)


# ---------------------------------------------------------------- the atlas
def texels(c, face):
    a, b = FACE_DIMS[face]
    return max(1, int(math.ceil(c.size[a] * DENS - 1e-6))), max(1, int(math.ceil(c.size[b] * DENS - 1e-6)))


def pack():
    """Every face its own place on the atlas (shelves, tallest first), with a texel round it of its own (draw fills it
    with the face's edge, so that a texel sampled a hair outside a face, at its edge, is still the face's colour and
    never the empty atlas, which the cutout would see through as a white speck)."""
    items = [(ci, f) + texels(c, f) for ci, c in enumerate(CUBES) for f in c.faces]
    items.sort(key=lambda it: (-it[3], -it[2]))
    place, x, y, row = {}, 0, 0, 0
    for ci, f, w, h in items:
        if x + w + 2 > ATLAS_W:
            x, y, row = 0, y + row, 0
        place[(ci, f)] = (x + 1, y + 1, w, h)
        x += w + 2
        row = max(row, h + 2)
    height = y + row
    return place, int(math.ceil(height / 16) * 16)


def draw(place, height):
    img = np.zeros((height, ATLAS_W, 4), dtype=np.float64)
    glow = np.zeros((height, ATLAS_W), dtype=bool)
    for (ci, f), (px, py, w, h) in place.items():
        c = CUBES[ci]
        # SS samples a texel's side: their colours averaged, a texel glowing if a third of them do
        su = (np.arange(w * SS) + .5) / (w * SS)
        sv = (np.arange(h * SS) + .5) / (h * SS)
        U, V = np.meshgrid(su, sv)
        col, g = paint(c, f, U, V)
        col = col.reshape(h, SS, w, SS, 3).mean(axis=(1, 3))
        g = g.reshape(h, SS, w, SS).mean(axis=(1, 3)) >= .34
        img[py:py + h, px:px + w, :3] = col
        img[py:py + h, px:px + w, 3] = 1
        img[py - 1, px:px + w], img[py + h, px:px + w] = img[py, px:px + w], img[py + h - 1, px:px + w]   # its border
        img[py - 1:py + h + 1, px - 1], img[py - 1:py + h + 1, px + w] = img[py - 1:py + h + 1, px], img[py - 1:py + h + 1, px + w - 1]
        glow[py:py + h, px:px + w] = g
    return img, glow


def geo(place, height):
    bones = [{'name': 'root', 'pivot': [0, 0, 0]}]
    for name in ('grip', 'guard', 'blade'):
        entry = {'name': name, 'parent': 'root', 'pivot': [0, 0, 0]}
        cubes = []
        for ci, c in enumerate(CUBES):
            if c.bone != name:
                continue
            cb = {'origin': [round(float(v), 4) for v in c.origin], 'size': [round(float(v), 4) for v in c.size]}
            if c.rot is not None:
                cb['rotation'] = [round(r, 4) for r in c.rot]
                cb['pivot'] = [round(float(v), 4) for v in c.pivot]
            cb['uv'] = {f: {'uv': [place[(ci, f)][0], place[(ci, f)][1]], 'uv_size': [place[(ci, f)][2], place[(ci, f)][3]]} for f in c.faces}
            cubes.append(cb)
        entry['cubes'] = cubes
        bones.append(entry)
    return {'format_version': '1.12.0', 'minecraft:geometry': [{
        'description': {'identifier': 'geometry.botand.isosang_sword', 'texture_width': ATLAS_W, 'texture_height': height,
                        'visible_bounds_width': 3, 'visible_bounds_height': 3, 'visible_bounds_offset': [0, 1, 0]},
        'bones': bones}]}


def sprite():
    """The item's 16 px sprite (its particles): the sword on the diagonal, the same colours."""
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    p = img.load()
    c = lambda a: tuple(int(round(v * 255)) for v in a) + (255,)
    for k in range(10):                      # the blade, its upper edge bright
        x, y = 5 + k, 10 - k
        p[x, y] = c(BODY if k > 1 else BODY_NEAR)
        if y - 1 >= 0:
            p[x, y - 1] = c(EDGE_LIGHT)
        if x + 1 < 16:
            p[x + 1, y] = c(INK)
    p[14, 0] = c(EDGE_LIGHT)
    for x, y in ((4, 11), (3, 10), (2, 9), (5, 12), (6, 13), (3, 8), (7, 14), (4, 10)):   # the wings across it
        p[x, y] = c(GOLD[4])
    p[2, 9], p[7, 14], p[3, 8] = c(GOLD[6]), c(GOLD[6]), c(GOLD[5])
    p[5, 11] = c(NAVY[2])
    for x, y in ((3, 12), (2, 13)):           # the grip, the pommel
        p[x, y] = c(GRIP[2])
    p[1, 14] = c(POMMEL[3])
    p[0, 15] = c(POMMEL[2])
    return img


def main():
    place, height = pack()
    img, glow = draw(place, height)
    rgba = (np.clip(img, 0, 1) * 255).round().astype(np.uint8)
    # the glow: those texels as they are painted, the rest clear (not a GeckoLib glow mask: GeckoLib cuts a mask's
    # texels out of the texture and draws them with back faces culled, which on this model hid them)
    lit = np.zeros_like(rgba)
    lit[glow] = rgba[glow]
    for sub in ('geo/item', 'textures/item', 'animations/item'):
        (ROOT / sub).mkdir(parents=True, exist_ok=True)
    Image.fromarray(rgba, 'RGBA').save(ROOT / 'textures/item/isosang_sword_model.png')
    Image.fromarray(lit, 'RGBA').save(ROOT / 'textures/item/isosang_sword_model_glow.png')
    sprite().save(ROOT / 'textures/item/isosang_sword.png')
    (ROOT / 'geo/item/isosang_sword.geo.json').write_text(json.dumps(geo(place, height), indent=1) + '\n')
    anim = {'format_version': '1.8.0', 'animations': {'animation.isosang_sword.idle': {
        'loop': True, 'animation_length': 1.0, 'bones': {'root': {'rotation': {'0.0': [0, 0, 0]}}}}}}
    (ROOT / 'animations/item/isosang_sword.animation.json').write_text(json.dumps(anim, indent=1) + '\n')
    print(f'{len(CUBES)} cubes, {len(place)} faces, atlas {ATLAS_W}x{height}, {int(glow.sum())} glowing texels')


if __name__ == '__main__':
    main()
