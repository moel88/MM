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
texture); the guard pale champagne gold with navy insets; the grip near-black lacquer with white glints; the pommel
dark gunmetal.

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
planks lying on its facets. The wings, the plate, the socket and the gems are cut in thin slices across the sword,
each slice a box per run of it (the wings', the socket's and the gems' slices a twenty-fourth of a unit; the socket's on
the upper gem's, so that the notch's sides and the gem's meet slice for slice), so that their outlines follow the 3D
model's and the photo's. The knobs are a box and the same box turned an eighth.
"""
import json
import math
import random
import sys
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parents[2] / 'src/main/resources/assets/celestialfall'
DENS = 24       # texels a unit
SS = 3          # samples a texel's side (lines drawn smooth)
ATLAS_W = 512


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
GOLD = [hexc(c) for c in ('#4a2a17', '#8c6440', '#b38f62', '#d2b98a', '#e8d49e', '#f6e8b8', '#fff7dc')]
GOLD_INK = hexc('#1f1815')
NAVY = [hexc(c) for c in ('#191a42', '#2b2d6b', '#3a3f8a', '#6069b8')]
GRIP = [hexc(c) for c in ('#0d0c12', '#191824', '#252436', '#36354d')]
GRIP_GLINT, GRIP_CORD = hexc('#dedbea'), hexc('#4b4966')
CRYSTAL = [hexc(c) for c in ('#ffffff', '#eaeef9', '#c8cee6')]    # the white crystals: their lit facet, their face, their shaded facet
CRYSTAL_LINE = hexc('#8a90ad')
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
GROOVE_W, GROOVE_SPAN = .065, (.56, 1.06)
RIM = .07                                 # the rim: how far in from the outline the wing's body rises
T_RIM, T_BODY, T_CREST, T_CORE = .12, .26, .32, .22   # half thicknesses
KNOB = (.82, .60, .065, .06)              # the knob near each wing's root: along, up, radius, how far it stands out
# the white crystal: each wing's front spike, in front of the line where the spike meets the wing (from the blade's edge
# to the notch); its ridge from its foot to its tip
CRYSTAL_EDGE = [(.01, .5), (.035, .94)]
CRYSTAL_RIDGE = [(.022, .72), (-.38, 1.13)]
# the plate on the flats: its point on the blade, its widest (and its half width), its point on the core; half thick at
# its point and from the guard's front back; the navy diamond inside it
PLATE = (-.856, .073, .43, .42)
PLATE_Z = (.16, .25)
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
# so that the gem is set in it), where each begins (a; each a step up from the one before, towards the core); how far
# out from the notch's sides the ridge runs beside them (on up onto the core, where the two meet in a point)
SOCKET = (.30, 1.37)
SOCKET_LAYERS = (('rim', 0.0, .22, CORE_TO), ('mid', .025, .255, CORE_TO + .01), ('body', .05, .285, P_BASE - GEMS[0][0]))
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
# middle (the flat), both thinning to the tip
for i in range(TIP_SLICES):
    y0 = Y_TIP_FROM + (Y_TIP_TO - Y_TIP_FROM) * i / TIP_SLICES
    y1 = Y_TIP_FROM + (Y_TIP_TO - Y_TIP_FROM) * (i + 1) / TIP_SLICES
    t = (i + .5) / TIP_SLICES
    hw = float(tip_half_width(t))
    k = hw / (TAPER * W / 2)
    faces = ALL if i == 0 else ['north', 'south', 'east', 'west', 'up']
    th = (T_OUTER * (1 - t) + .012 * t) * W
    cube('blade', 'tip', [-hw, y0, -th], [2 * hw, y1 - y0, 2 * th], faces, slice=(i, hw, (FLAT + .03) * W * k))
    hm, th = (FLAT + .03) * W * k, (T_FLAT * (1 - t) + .022 * t) * W
    cube('blade', 'tip_flat', [-hm, y0, -th], [2 * hm, y1 - y0, 2 * th], faces)

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
            cube('grip', part, [-hw, y_of(_pp[k]), -half_t * W], [2 * hw, y_of(_pp[k + 1]) - y_of(_pp[k]), 2 * half_t * W], ALL,
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
                cube('grip', part, [-half * W, y_of(lo_p), lo], [2 * half * W, y_of(hi_p) - y_of(lo_p), hi - lo],
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
            cube('guard', 'socket', [x0 * W, y0, -T * W], [(x1 - x0) * W, y1 - y0, 2 * T * W], faces, layer=layer, depth=T * W, run=(lo, hi))

# the plate on the flats: slices a sixteenth of a unit long, as wide as its outline at their middle, thicker towards the
# guard; through the blade (on both flats at once)
n = int(math.ceil((y_along(PLATE[0]) - y_along(PLATE[3])) * 16))
ys = np.linspace(y_along(PLATE[3]), y_along(PLATE[0]), n + 1)
for k in range(n):
    a = along_of((ys[k] + ys[k + 1]) / 2)
    hw = PLATE[2] * ((a - PLATE[0]) / (PLATE[1] - PLATE[0]) if a < PLATE[1] else (PLATE[3] - a) / (PLATE[3] - PLATE[1]))
    z = PLATE_Z[0] + (PLATE_Z[1] - PLATE_Z[0]) * float(np.clip((a - PLATE[0]) / -PLATE[0], 0, 1))
    if hw * W < .02:
        continue
    faces = ['north', 'south', 'east', 'west'] + (['up'] if a < PLATE[1] else ['down'])
    cube('guard', 'plate', [-hw * W, ys[k], -z * W], [2 * hw * W, ys[k + 1] - ys[k], 2 * z * W], faces, depth=z * W)

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
            cube('guard', 'wing', origin, [x1 - x0, _edges[k1] - _edges[k0], 2 * T * W], f, layer=layer, depth=T * W)

# the knobs near the wings' roots, on each face of each wing (eight-sided, standing out of the body)
ka, ku, kr, kh = KNOB
for s_ in (1, -1):
    for f in (1, -1):
        cx, cy = s_ * ku * W, y_along(ka)
        z0 = T_BODY * W if f > 0 else -(T_BODY + kh) * W
        faces = ['south' if f > 0 else 'north', 'east', 'west', 'up', 'down']
        for turn in (None, (0, 0, 45)):
            cube('guard', 'knob', [cx - kr * W, cy - kr * W, z0], [2 * kr * W, 2 * kr * W, kh * W], faces,
                 rot=turn, pivot=[cx, cy, 0] if turn else None, centre=(cx, cy))


# ---------------------------------------------------------------- the lightning on the flat
LIGHTNING = (FLAT - .06) * W              # how far from the middle it runs


def make_bolts(seed):
    """Jagged bolts along a flat (segments x0, y0, x1, y1, core radius, halo radius): one running most of the blade from
    beyond the guard's plate, a shorter one further up, long strokes with sharp kicks and gaps, now and then a short
    branch; all on the flat and short of the point."""
    rnd = random.Random(seed)
    segs = []
    start = y_along(PLATE[0])
    runs = [(start + .3 + rnd.random(), Y_TIP_FROM - 1.0 - rnd.random() * 2.5, .05, .135),
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
            b = bolt_light(side, x, y)
            col = np.where((b >= 1)[..., None], CRACK_CORE, mix(col, CRACK_HALO, b))
            glow |= b > .45
            g = glint_light(side, x, y)
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
    i, hw, hm = c.opts['slice']
    if face in FLATS:
        seen = max(hw - hm, 1e-6)                    # how much of each side shows beside the flat
        du, dl = (hw - x) / seen, (x + hw) / seen
        col = solid(mix(BEVEL_DARK, BODY_DEEP, .25), x.shape)
        col = np.where(((x > 0) & (du > .62))[..., None], EDGE_LIGHT, col)
        col = np.where((x < 0)[..., None], np.where((dl < .55)[..., None], EDGE_LOW, mix(BODY, BODY_DEEP, .55)), col)
        col = np.where(((hw - np.abs(x)) < .035 * W)[..., None], INK, col)
        return np.where(line, INK, col), glow
    if face in ('up', 'down'):
        return solid(BODY_DEEP, x.shape), glow
    t = np.abs(z) / max(c.size[2] / 2, 1e-6)
    return mix(STEEL_EDGE * 1.05, mix(STEEL_EDGE, BEVEL_DARK, .55), smooth(t)), glow


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
            return np.where((crystal & (np.abs(z) <= c.opts['depth'] - .028 * W))[..., None], CRYSTAL[2], col), glow
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
    groove = inside(GROOVE, a, up)
    g_edge = dist(GROOVE, a, up, closed=True) < .022
    if layer == 'rim':
        col = solid(mix(GOLD[3], GOLD[4], .3), x.shape)
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
        col = np.where((dist(BACK_LINE, a, up) < .016)[..., None], mix(col, GOLD_INK, .7), col)
        col = np.where((g_edge | (d < RIM + .028) | (dist(CREST, a, up, closed=True) < .02))[..., None], GOLD_INK, col)
        return (np.where(crystal[..., None], ccol, col) if crystal.any() else col), glow
    col = solid(GOLD[5], x.shape)
    col = np.where(((dist(CREST, a, up, closed=True) < .026) | (d < RIM + .055))[..., None], GOLD_INK, col)
    return col, glow


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
    lines = (dist([(.42, .5), (.85, -.5)], a, up) < .016) | (dist([(.42, -.5), (.85, .5)], a, up) < .016)
    lines |= (np.abs(a - .635) < .014) & (np.abs(up) < .45)
    lines |= dist([(CORE_TO, -w), (RIDGE_TOP, 0), (CORE_TO, w)], a, up) < .016
    return np.where(lines[..., None], GOLD_INK, col), glow


def paint_socket(c, face, x, y, z, u, v):
    """The socket below the core: on its faces gold, brighter between the notch and the ridges beside it (as on the core
    above), a bright lip along the notch (the gem's own outline inks where they meet), the layers in from its outline
    lighter, inked at its edge; its outer walls gold inked at the face, the notch's and the foot's in shade, and the
    steps up from the core (so that they read as creases, not as light seams across it)."""
    glow = np.zeros_like(x, dtype=bool)
    a, up = along_of(y), np.abs(x) / W
    if face not in FLATS:
        if face == 'up':
            return solid(mix(GOLD[2], GOLD[3], .6), x.shape), glow
        if face == 'down' or np.all(np.abs(up - c.opts['run'][1]) > 1e-3):
            return solid(GOLD[2], x.shape), glow
        return np.where((np.abs(z) > c.opts['depth'] - .02 * W)[..., None], GOLD_INK, solid(GOLD[3], z.shape)), glow
    d_out, d_notch = dist(SOCKET_OUTLINE, a, up), dist(NOTCH_SIDE, a, up)
    between = up < (a - RIDGE_TOP) * NOTCH_SLOPE
    lift = {'rim': .25, 'mid': .6, 'body': 1.0}[c.opts['layer']]          # the layers in from the outline lighter
    col = np.where(between[..., None], mix(GOLD[4], GOLD[5], lift), solid(mix(GOLD[3], GOLD[4], lift), x.shape))
    col = np.where((d_notch < .03)[..., None], GOLD[6], col)
    col = np.where((dist([(RIDGE_TOP, 0.0), (SOCKET[1], (SOCKET[1] - RIDGE_TOP) * NOTCH_SLOPE)], a, up) < .016)[..., None], GOLD_INK, col)
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
    col = np.where((dist(PLATE_POLY, a, up, closed=True) < .03)[..., None], GOLD_INK, col)
    return col, glow


def paint_knob(c, face, x, y, z, u, v):
    glow = np.zeros_like(x, dtype=bool)
    if face in FLATS:
        r = np.hypot(u - .5, v - .5) * 2
        return np.where((r < .5)[..., None], GOLD[6], np.where((r < .85)[..., None], GOLD[4], GOLD_INK)), glow
    return solid(GOLD[3], x.shape), glow


def band(c, face, x, y, z):
    """The collar: gold, lit a little more towards the blade, an ink line at each end and round the edge of an end that
    shows."""
    y0, y1 = c.opts['span']
    if face in ('up', 'down'):
        col = solid(GOLD[5] if face == 'up' else GOLD[3], x.shape)
        return np.where((np.hypot(x, z) > c.opts['r'] - .03 * W)[..., None], GOLD_INK, col)
    col = mix(GOLD[3], GOLD[5], .3 + .45 * (y - y0) / (y1 - y0))
    return np.where(((y - y0 < .03) | (y1 - y < .03))[..., None], GOLD_INK, col)


def paint_gem(c, face, x, y, z):
    """A white gem: its border bevelled in four facets (the one up towards the guard on the left lit), inked round its
    outline; its inner diamond brightest, a fine grey-blue line round it; its sides shaded."""
    if face not in FLATS:
        return solid(CRYSTAL[2], x.shape)
    gem = GEMS[c.opts['gem']]
    poly = gem_poly(gem)
    p, xa = y / W + P_HOLD, x / W
    if c.part == 'gem_top':
        inner = [(q, u * (1 - GEM_INSET / gem[4])) for q, u in poly]
        col = mix(CRYSTAL[0], CRYSTAL[1], np.clip((p - poly[1][0]) * gem[1] / (gem[2] - gem[3]), 0, 1) * .6)
        return np.where((dist(inner, p, xa, closed=True) < .016)[..., None], CRYSTAL_LINE, col)
    upper, left = p > poly[1][0], xa < 0
    col = np.where((upper & left)[..., None], CRYSTAL[0], np.where((upper | left)[..., None], CRYSTAL[1], CRYSTAL[2]))
    return np.where((dist(poly, p, xa, closed=True) < .022)[..., None], GOLD_INK, col)


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
        return paint_gem(c, face, x, y, z), glow
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


def paint(c, face, u, v):
    """A face's colours and glow at points (u, v) of it."""
    x, y, z = face_point(c, face, u, v)
    if c.part in ('grip', 'collar', 'gem', 'gem_top'):
        x, y, z = world_point(c, x, y, z)
    if c.bone == 'blade':
        return paint_blade(c, face, x, y, z, u, v)
    return {'wing': paint_wing, 'core': paint_core, 'socket': paint_socket, 'plate': paint_plate, 'knob': paint_knob}.get(c.part, paint_hilt)(c, face, x, y, z, u, v)


# ---------------------------------------------------------------- the atlas
def texels(c, face):
    a, b = FACE_DIMS[face]
    return max(1, int(math.ceil(c.size[a] * DENS - 1e-6))), max(1, int(math.ceil(c.size[b] * DENS - 1e-6)))


def pack():
    """Every face its own place on the atlas (shelves, tallest first), one texel apart."""
    items = [(ci, f) + texels(c, f) for ci, c in enumerate(CUBES) for f in c.faces]
    items.sort(key=lambda it: (-it[3], -it[2]))
    place, x, y, row = {}, 0, 0, 0
    for ci, f, w, h in items:
        if x + w > ATLAS_W:
            x, y, row = 0, y + row + 1, 0
        place[(ci, f)] = (x, y, w, h)
        x += w + 1
        row = max(row, h)
    height = y + row + 1
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
