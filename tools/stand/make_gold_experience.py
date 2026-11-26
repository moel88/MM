"""Gold Experience's 3D model for GeckoLib (2.145.0, user request: "새로운 스탠드 만들자 죠죠의 골드익스피리언스 외형부터
만들자 외형은 Geckolib으로 만들고 사진 보내줄게 스타플래티나 만들때 처럼 만들면 돼", after the two pictures they sent: the
figure with a hand on its hip and its legs crossed, and the turnaround of its side, front and back): its geometry and
its texture, made together so that every face is painted for the place it takes on the body, the way Star Platinum's
are (tools/stand/make_star_platinum.py; the machinery both use is tools/stand/rig.py).

usage: python3 tools/stand/make_gold_experience.py [OUT]
Writes assets/celestialfall/geo/entity/gold_experience.geo.json, textures/entity/gold_experience.png and
stands/gold_experience_poses.json under src/main/resources, or under OUT (the same folders) to look at a change without
touching the game's files.

What the pictures show, and so what is built: a slim figure of pale cream gold, long in the leg, narrow in the hips, its
belly smooth (no muscles drawn on it). Its head is one egg: a smooth dome of a helmet, widest right over its rim and
longer than it is wide, a ridge down its middle, a curl engraved on either side of the ridge
and a glossy arch standing on its front over the brow; under the helmet's rim, as wide as the rim and narrowing to a
small chin, a short face with two purple eyes without pupils right under the arch, a pale line down each cheek from
them to beside the lips and a round purple stone on the chin; an orange disc over each ear. A short orange vest over
the chest, the shoulders and the upper back, its collar a V, rows of pale slots let into it, and a purple ladybird on
each breast; two dark straps down the bare back from under it to the belt. A small silver wing on each shoulder: a
curl at its root and a blade sweeping up, out and back. Bare upper arms; a red-brown cuff and a purple stone at each
elbow, an orange guard with slots down each forearm, a ladybird on the back of each hand and purple beads on the
knuckles. An orange belt, an orange piece with slots at the front under it, a ladybird on each hip, a silver ring
hanging from the belt at each side in front, and a brown hide tied round the hips behind, its end torn into points.
Bare thighs; a purple dome on each knee, brown boots to the knee with an orange guard with slots down the shin and
round the calf, the bare calf an oval standing out of it behind; a ladybird at each ankle and pale pointed shoes with
purple soles and heels. (The stand's own design sheet, looked up when the user asked for it, settled the face, the
helmet's lines and the back.)
"""
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import rig  # noqa: E402
from rig import bone, box, rounded, mirrored, strand, hexc, mix, ramp, noise, norm, pillow, edge_texels  # noqa: E402

ROOT = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parents[2] / 'src/main/resources/assets/celestialfall'

# ---------------------------------------------------------------- palette (from the pictures the user sent)
BODY = [hexc(c) for c in ('#4a4220', '#8a7a38', '#c6b158', '#e8d478', '#f4e492', '#fbeeac', '#fff9d8')]   # olive shade .. shine
HELM = [hexc(c) for c in ('#5a4e22', '#a8964a', '#d8c268', '#eedb84', '#f8e9a0', '#fff8cc')]
ARMOR = [hexc(c) for c in ('#6a3a10', '#a4641e', '#cc8a2e', '#e4a644', '#f2be60', '#ffdc92')]
SLOT, SLOT_LINE = hexc('#f8e6a4'), hexc('#4e2a0a')
BROWN = [hexc(c) for c in ('#231206', '#3d210c', '#573014', '#74431c', '#915a28')]
CUFF = [hexc(c) for c in ('#4a1a14', '#742a20', '#963c2e', '#b4543e')]
PURPLE = [hexc(c) for c in ('#1c0f36', '#38206c', '#583aa2', '#7a56cc', '#a386ea', '#d4c6fb')]
SILVER = [hexc(c) for c in ('#3a4452', '#73808f', '#a4b0bd', '#cfd8e1', '#edf2f6', '#ffffff')]
FUR = [hexc(c) for c in ('#2a1608', '#4a2a10', '#6a3e18', '#8a5422')]
FUR_PALE = [hexc(c) for c in ('#7a4a18', '#a8702a', '#c88e3c', '#dea650')]
LINE = hexc('#231d0c')                 # the ink line
EYE = hexc('#150b26')
LIGHT = norm((0.0, .8, -.6))           # baked light: from above and in front, the same on both sides


def tone(n, u, v, base=.4, face=None):
    t = base + .3 * max(0.0, float(n @ LIGHT)) + .18 * pillow(u, v, .8, .6)
    if face == 'down':
        t -= .22
    if face == 'south':
        t -= .05
    return t


def inked(c, face, u, v, col, strength=.35, width=1.0, ink=LINE):
    return mix(col, ink, strength) if edge_texels(c, face, u, v) < width else col


# ---------------------------------------------------------------- the rig
# Its proportions (2.145.0; the fourth set, after the user's "대가리가 너무 커 존나 버섯같아" (a wide flat helmet on a
# long thin face), "여전히 머리 비율과 몸 비율이 이상해", "더 이상함" (the head sunk into the collar) and "대가리가 너무
# 작고 어깨가 너무 넓어 ... 얼굴이 튀어나오니까 이상해" (a small helmet with the face hanging out of it as a block of
# its own)): of its 40 units the head takes 6.3, the helmet 4 of that and 5.9 wide, a little over half the width across
# the shoulders (11.3); the helmet and the face are one egg, as the design sheet's outline is: the helmet narrows to its
# rim over the brow, the face goes on from there as wide as the rim and narrows to the chin, its front flush under the
# rim. A short neck shows between the chin and the collar; the vest ends at 28.4 over a long narrow waist, the belt
# sits at 23 and the legs part at 19.9: half its height is leg, the knees at 11. Every height below hangs from these
# landmarks, so that they can be moved without repainting.
# 2.145.1 (the user's "복근이 없어야하고 골반과 다리가 너무 크다고 해야하나 다리 모양도 이상하고 전체적으로 바꿔"), measured
# off the sheet's front view against 40 units: 11 across the shoulders, 4 at the waist, 5 across the hips, the thigh 2.6
# narrowing to 2 at the knee, the calf 2.6 and the ankle 1.2, the thigh and the lower leg the same length. So: the hips
# 5.2 (they were 6.5), the legs parting at 20.3, the knees up at 12, thin legs set closer together.
# 2.145.2 (the user's "애가 젓가락이 됐는데"): the sheet's widths carried over as they are make a stick of a model built
# of boxes (the drawing's curves and shading carry its thin limbs; boxes have neither). The lengths stay; the widths
# go half way back: the hips 5.9, the thigh 3.0 to 2.3, the lower leg 2.95 to 2.0, the waist 4.8, the upper arm 2.2.
ANKLE_Y, KNEE_Y, CROTCH_Y, HIP_Y = 2.4, 12.0, 20.3, 21.7
BELT_Y, VEST_Y, SHOULDER_Y = 23.2, 28.4, 32.7          # the belt's lower edge, the vest's, the top of the shoulders
CHIN_Y, BROW_Y = 33.7, 36.0                              # the face: from the chin to the helmet's rim over the brow
ELBOW_Y, WRIST_Y = 25.4, 19.7
ARM_X, LEG_X = -4.45, -1.55
ROOT_BONE = bone('root', None, (0, 0, 0))
HIPS = bone('hips', 'root', (0, HIP_Y + .4, 0))
TORSO = bone('torso', 'hips', (0, BELT_Y + .9, 0))
CHEST = bone('chest', 'torso', (0, VEST_Y - .1, 0))
NECK = bone('neck', 'chest', (0, SHOULDER_Y - .3, 0))
HEAD = bone('head', 'neck', (0, CHIN_Y + .2, 0))


def puck(b, at, w, h, t, axis, out, mat, **opts):
    """A stepped disc standing t off a surface: three crossed boxes w by h across (a round of twelve sides), about
    `at` (the middle of its foot), facing out (-1 or 1) along axis ('x' or 'z')."""
    x, y, z = at
    for k, (fw, fh) in enumerate(((1.0, .46), (.78, .78), (.46, 1.0))):
        tt = t + .025 * k                # each a hair thicker: their faces never share a plane
        # no face against what it stands on
        if axis == 'z':
            box(b, (x - w * fw / 2, y - h * fh / 2, z - tt if out < 0 else z), (w * fw, h * fh, tt), mat, at=at, r=(w / 2, h / 2),
                axis=axis, out=out, faces=tuple(f for f in rig.ALL if f != ('south' if out < 0 else 'north')), **opts)
        else:
            box(b, (x - tt if out < 0 else x, y - h * fh / 2, z - w * fw / 2), (tt, h * fh, w * fw), mat, at=at, r=(w / 2, h / 2),
                axis=axis, out=out, faces=tuple(f for f in rig.ALL if f != ('west' if out < 0 else 'east')), **opts)


def badge(b, at, w, h, axis, out, kind):
    """A ladybird or a stone: a dome of two stepped discs, the upper smaller."""
    puck(b, at, w, h, .3, axis, out, 'bug', part=kind, layer=0)
    x, y, z = at
    up = (x, y, z + .3 * out) if axis == 'z' else (x + .3 * out, y, z)
    puck(b, up, w * .68, h * .68, .26, axis, out, 'bug', part=kind, layer=1, full=(at, (w / 2, h / 2)))


# hips: a narrow pelvis under the belt (after the user's "골반과 다리가 너무 크다" and then "젓가락": 5.9 across), the
# orange piece hanging in front a tab that narrows
rounded(HIPS, (-2.95, CROTCH_Y, -1.75), (5.9, BELT_Y + .1 - CROTCH_Y, 3.5), 'body', .5, part='pelvis')
box(HIPS, (-1.1, BELT_Y - 1.9, -2.3), (2.2, 1.95, .55), 'armor', part='cod')
box(HIPS, (-.7, CROTCH_Y - .2, -2.25), (1.4, BELT_Y - 1.8 - CROTCH_Y + .2, .45), 'armor', part='cod')
rounded(HIPS, (-3.15, BELT_Y, -2.0), (6.3, .9, 4.0), 'armor', .45, part='belt')

# torso: a long smooth waist, narrowest a third of the way up, widening to the ribs under the vest. Nothing stands out
# of it (2.145.1, the user's "복근이 없어야하고": the belly's muscles and the long muscles of the back, boxes of their
# own until then, are gone; the belly is painted smooth)
WAIST = VEST_Y - BELT_Y - .8              # the bare waist's height, from the belt's top to the vest
rounded(TORSO, (-2.6, BELT_Y + .8, -1.6), (5.2, WAIST * .3, 3.2), 'body', .5, part='abdomen')
rounded(TORSO, (-2.4, BELT_Y + .8 + WAIST * .28, -1.5), (4.8, WAIST * .28, 3.0), 'body', .48, part='waist')
rounded(TORSO, (-2.65, BELT_Y + .8 + WAIST * .54, -1.6), (5.3, WAIST * .26, 3.2), 'body', .52, part='midribs')
rounded(TORSO, (-2.95, BELT_Y + .8 + WAIST * .78, -1.75), (5.9, WAIST * .27, 3.5), 'body', .55, part='lowribs')

# chest: the vest (its breast plates standing out in front, a plate over the shoulder blades), the yoke over the
# shoulders and a low collar round the neck; a ladybird on each breast
rounded(CHEST, (-3.3, VEST_Y, -2.1), (6.6, SHOULDER_Y - .5 - VEST_Y, 4.2), 'armor', .65, part='vest')
for side in (-1, 1):
    box(CHEST, (.12 if side > 0 else -3.27, VEST_Y + .45, -2.75), (3.15, 3.0, .7), 'armor', part='pec', side=side)
box(CHEST, (-3.0, VEST_Y + .3, 2.05), (6.0, 3.5, .45), 'armor', part='backplate')
rounded(CHEST, (-3.6, SHOULDER_Y - .85, -1.75), (7.2, .85, 3.5), 'armor', .42, part='yoke')
rounded(NECK, (-1.7, SHOULDER_Y - .1, -1.6), (3.4, .5, 3.3), 'armor', .5, part='collar')
rounded(NECK, (-1.15, SHOULDER_Y - .5, -1.15), (2.3, CHIN_Y + .7 - SHOULDER_Y + .5, 2.4), 'body', .42, part='neck')
for side in (-1, 1):
    badge(CHEST, (1.85 * side, VEST_Y + 1.2, -2.75), 1.8, 2.0, 'z', -1, 'bug')

# head (after the user's "얼굴과 뒷모습 생긴게 ... 자연스럽게 수정해봐 너가 죠죠의 골드 익스피리언스 검색해서", from the
# stand's own design sheet): a short narrow face tapering to a small pointed chin, painted at FACE_DENS; no band across
# it: two purple eyes without pupils sit right under the helmet's arch, a line runs down each cheek from them to beside
# the lips, and a round purple stone sits on the chin. The helmet is a bulb of eight layers, widest low down over the
# temples and cut in to the brow under that, a glossy arch standing on its front, lines running from the arch over the
# bulb and a curl on either side of its ridge. An orange disc over each ear.
FACE_DENS, HELM_DENS = 16, 12
FACE_Z = -2.15                            # the face's front, set back under the helmet's rim
FACE = BROW_Y - CHIN_Y                    # the face's height
rounded(HEAD, (-2.0, CHIN_Y + FACE * .63, FACE_Z), (4.0, FACE * .37 + .05, 4.05), 'face', .5, part='head', dens=FACE_DENS)
rounded(HEAD, (-1.75, CHIN_Y + FACE * .33, FACE_Z), (3.5, FACE * .3, 3.8), 'face', .48, part='head', dens=FACE_DENS)
box(HEAD, (-1.3, CHIN_Y + FACE * .13, FACE_Z), (2.6, FACE * .2, 3.0), 'face', part='jaw', dens=FACE_DENS)
box(HEAD, (-.7, CHIN_Y, FACE_Z + .05), (1.4, FACE * .13, 2.2), 'face', part='jaw', dens=FACE_DENS)
box(HEAD, (-.2, CHIN_Y + FACE * .4, FACE_Z - .2), (.4, FACE * .3, .2), 'face', part='nose', dens=FACE_DENS, faces=('north', 'east', 'west', 'down'))
puck(HEAD, (0, CHIN_Y + FACE * .13, FACE_Z), .7, .62, .22, 'z', -1, 'bug', part='chin', layer=1, dens=FACE_DENS)
for side in (-1, 1):
    box(HEAD, (2.0 if side > 0 else -2.26, CHIN_Y + FACE * .42, -.6), (.26, 1.3, 1.3), 'armor', part='ear', side=side, dens=FACE_DENS,
        faces=('west',) if side > 0 else ('east',))
# The helmet (2.145.1, the user's "헬멧도 자연스럽게 고치면서": until then a bulb that began narrow over the brow and
# swelled above it, in eight coarse steps, which read as a stepped mushroom cap). As the sheet draws it: a smooth dome
# that is widest low, right over its rim, and rounds over from there in fine steps; longer than it is wide, running on
# behind the head and falling more gently to the back than to the front; a dark rim round its foot.
HELMET = (  # each layer: how far above the brow it starts, its height, half its width
    (0, .35, 2.6), (.35, .9, 2.75), (1.25, .45, 2.71), (1.7, .45, 2.61), (2.15, .4, 2.46), (2.55, .35, 2.26), (2.9, .3, 2.02),
    (3.2, .27, 1.76), (3.47, .23, 1.46), (3.7, .18, 1.1), (3.88, .12, .69))
HELM_HALF, HELM_TOP = 2.75, 4.0          # half its width where it is widest, its height
HELM_FRONT, HELM_BACK = .9, 1.3           # how far it stands out over the brow and runs back over the nape, in half-widths
for layer, (up, h, hw) in enumerate(HELMET):
    z0, z1 = -HELM_FRONT * hw, HELM_BACK * hw
    mid, hd = (z0 + z1) / 2, (z1 - z0) / 2
    for k, (fw, fd) in enumerate(((1.0, .5), (.82, .82), (.5, 1.0))):
        # each a hair lower than the last, so that their tops never share a plane
        box(HEAD, (-hw * fw, BROW_Y + up + .012 * k, mid - hd * fd), (2 * hw * fw, h - .024 * k, 2 * hd * fd), 'helmet', part='dome',
            layer=layer, dens=HELM_DENS, faces=rig.ALL if layer < 2 else ('north', 'south', 'east', 'west', 'up'))
PLATE = (BROW_Y - .05, BROW_Y + 2.35, 1.35, BROW_Y + 1.15)    # the arch on its front: from, to, half its width, where its curve begins
box(HEAD, (-PLATE[2], PLATE[0], -HELM_FRONT * HELM_HALF - .3), (2 * PLATE[2], PLATE[1] - PLATE[0], .62), 'helmet', part='plate', dens=FACE_DENS,
    faces=('north', 'east', 'west', 'down'))
CURL = (1.0, BROW_Y + 2.85, .42)           # the curl engraved on either side of the helmet's ridge: where, how big

GRIPS = {'right': (ARM_X, WRIST_Y - 1.7, 0.0), 'left': (-ARM_X, WRIST_Y - 1.7, 0.0)}


def right_wing():
    # a small wing on the shoulder: the curl at its root turned out to the front and the side, the blade sweeping up,
    # out and back
    y = SHOULDER_Y
    wing = bone('right_wing', CHEST, (-4.7, y - .4, 0.0), (0, 38, 0))
    puck(wing, (-4.95, y, -1.1), 2.4, 2.4, .5, 'z', -1, 'silver', part='swirl')
    # (four pieces, each narrower than the last, the last cut to a point: a blade that tapers all the way)
    strand('right_wing_blade', wing, [(-5.5, y + .7, -.25), (-5.9, y + 1.7, .1), (-6.25, y + 2.65, .6), (-6.5, y + 3.5, 1.25), (-6.65, y + 4.3, 2.0)],
           [1.8, 1.45, 1.1, .78], [.75, .62, .5, .4], (-.5, 0, -.87), 'silver', part='blade')
    rig.CUBES[-1].faces = ('north', 'south')        # the tip is cut to a point


mirrored(right_wing)


def right_arm():
    top = SHOULDER_Y - .1
    arm = bone('right_arm', CHEST, (ARM_X + .5, top - 1.0, 0.0))
    rounded(arm, (ARM_X - 1.25, top - 2.65, -1.7), (2.5, 2.65, 3.4), 'armor', .5, part='cap')
    upper = top - 2.55 - ELBOW_Y
    rounded(arm, (ARM_X - 1.12, ELBOW_Y + upper * .45, -1.3), (2.24, upper * .55 + .1, 2.6), 'body', .46, part='upperarm')
    rounded(arm, (ARM_X - 1.02, ELBOW_Y - .2, -1.18), (2.04, upper * .45 + .3, 2.36), 'body', .42, part='upperarm')
    fore = bone('right_forearm', arm, (ARM_X, ELBOW_Y, 0.0))
    rounded(fore, (ARM_X - 1.3, ELBOW_Y - .92, -1.45), (2.6, 1.15, 2.9), 'cuff', .5, part='cuff')
    badge(fore, (ARM_X, ELBOW_Y - .4, 1.45), 1.6, 1.6, 'z', 1, 'dome')
    guard = ELBOW_Y - .92 - WRIST_Y - .4
    rounded(fore, (ARM_X - 1.28, WRIST_Y + .4 + guard * .45, -1.4), (2.56, guard * .55, 2.8), 'armor', .53, part='guard')
    rounded(fore, (ARM_X - 1.14, WRIST_Y + .4, -1.26), (2.28, guard * .45 + .05, 2.52), 'armor', .46, part='guard')
    rounded(fore, (ARM_X - 1.18, WRIST_Y - .05, -1.3), (2.36, .5, 2.6), 'cuff', .47, part='wrist')
    hand = bone('right_hand', fore, (ARM_X, WRIST_Y, 0.0))
    box(hand, (ARM_X - .9, WRIST_Y - 2.3, -.8), (1.8, 2.35, 1.6), 'body', part='palm')
    box(hand, (ARM_X - .85, WRIST_Y - 3.9, -.75), (1.7, 1.65, 1.4), 'body', part='fingers')
    box(hand, (ARM_X - .6, WRIST_Y - 2.65, -1.2), (1.1, 1.5, .42), 'body', part='thumb')
    badge(hand, (ARM_X - .9, WRIST_Y - 1.2, 0.0), 1.4, 1.65, 'x', -1, 'bug')


mirrored(right_arm)


SHIN = (  # the lower leg, from the top down: where it starts and ends between the knee (1) and the ankle (0), its width, its
          # depth, how far its middle sits behind the leg's: widest under the knee where the calf swells behind, narrow at the ankle
    (.96, .64, 2.95, 3.15, .12), (.66, .36, 2.72, 2.8, .08), (.38, .14, 2.36, 2.4, .03))


def right_leg():
    # (2.145.1, the user's "골반과 다리가 너무 크다 ... 다리 모양도 이상하고": the thigh was 3.4 across and the lower leg
    # a block with plates stuck on it.) A slim thigh that tapers to the knee in four steps; a small knee; a lower leg as
    # long as the thigh that swells under the knee and narrows to a thin ankle, its guard painted on it
    leg = bone('right_leg', HIPS, (LEG_X, HIP_Y, 0.0))
    thigh = HIP_Y + .3 - KNEE_Y
    for k, (lo, hi, w, d) in enumerate(((.72, 1.0, 3.0, 3.3), (.44, .74, 2.9, 3.2), (.2, .46, 2.64, 2.95), (0, .22, 2.34, 2.65))):
        rounded(leg, (LEG_X - w / 2, KNEE_Y + thigh * lo, -d / 2 + .05), (w, thigh * (hi - lo), d), 'body', .5 - .03 * k, part='thigh', low=k)
    badge(leg, (LEG_X - 1.5, HIP_Y - .55, -.3), 2.0, 2.2, 'x', -1, 'bug')
    shin = bone('right_shin', leg, (LEG_X, KNEE_Y, 0.0))
    rounded(shin, (LEG_X - 1.32, KNEE_Y - 1.0, -1.42), (2.64, 1.75, 3.0), 'brown', .54, part='kneecuff')
    badge(shin, (LEG_X, KNEE_Y - .2, -1.42), 2.2, 2.7, 'z', -1, 'dome')
    calf = KNEE_Y - 1.0 - ANKLE_Y
    for k, (hi, lo, w, d, back) in enumerate(SHIN):
        rounded(shin, (LEG_X - w / 2, ANKLE_Y + calf * lo, -d / 2 + back), (w, calf * (hi - lo) + (.3 if k == 0 else 0), d), 'armor', .5 - .04 * k,
                part='shin', piece=k)
    rounded(shin, (LEG_X - 1.02, ANKLE_Y - .15, -1.05), (2.04, calf * .16 + .15, 2.15), 'brown', .44, part='boot')
    foot = bone('right_foot', shin, (LEG_X, ANKLE_Y, 0.0))
    badge(foot, (LEG_X - 1.1, 2.05, .2), 1.25, 1.35, 'x', -1, 'bug')
    box(foot, (LEG_X - 1.08, .88, -1.4), (2.16, 1.55, 2.85), 'shoe', part='upper')
    box(foot, (LEG_X - 1.03, 0, .3), (2.06, .88, 1.15), 'purple', part='heel')
    for k, (w, z0, d, top) in enumerate(((2.06, -3.15, 1.75, 1.8), (1.5, -4.35, 1.2, 1.32), (.86, -5.15, .8, .92))):
        box(foot, (LEG_X - w / 2, .4, z0), (w, top - .4, d), 'shoe', part='toe', piece=k)
        box(foot, (LEG_X - w / 2 - .04, 0, z0), (w + .08, .4, d), 'purple', part='sole', piece=k)


mirrored(right_leg)


def right_ring():
    # a silver ring hanging from the belt in front of the hip, turned out a little, swung a little forward
    ring = bone('right_ring', HIPS, (-2.3, BELT_Y + .1, -2.2), (-9, 28, 6))
    box(ring, (-3.35, BELT_Y - 3.5, -2.4), (2.1, 3.6, .3), 'silver', part='ring', faces=('north', 'south'),
        at=(-2.3, BELT_Y - 1.75, -2.4), r=(1.0, 1.66))


mirrored(right_ring)


def hide(name, at, width, pieces, across, tilt, tone_, teeth):
    """A panel of the loincloth hanging behind: pieces (length) one under the other, each its own bone so it can sway;
    across 'x' hangs behind the hips, 'z' at their side. teeth: how many points its torn end has, and where they start."""
    x, y, z = at
    par, total, done = HIPS, sum(pieces), 0.0
    for k, L in enumerate(pieces):
        b = bone(name if k == 0 else f'{name}_{k}', par, (x, y, z), tilt if k == 0 else (0, 0, 0))
        over = .15 if k else 0
        if across == 'x':
            box(b, (x - width / 2, y - L, z), (width, L + over, .3), 'cloth', part='hide', start=done / total, share=L / total,
                strip=name, pale=tone_, teeth=teeth, faces=('north', 'south'))
        else:
            box(b, (x - .3 if x < 0 else x, y - L, z - width / 2), (.3, L + over, width), 'cloth', part='hide', start=done / total,
                share=L / total, strip=name, pale=tone_, teeth=teeth, faces=('east', 'west'))
        y -= L
        done += L
        par = b


# a hide tied round the hips behind: longest in the middle, its end torn into big points
for i, (cx, w, pieces, pale, teeth) in enumerate(((-2.1, 2.25, (2.7, 2.4), 0, (2, .15)), (0.0, 2.4, (3.3, 3.4), 0, (2, .6)),
                                                   (2.1, 2.25, (2.9, 2.2), 1, (2, .35)))):
    hide(f'hide_{i}', (cx, BELT_Y + .2, 2.05 + .04 * (i == 1)), w, pieces, 'x', (7, 0, 0), pale, teeth)


def right_hide():
    hide('hide_right', (-3.2, BELT_Y + .2, .85), 2.4, (2.5, 2.0), 'z', (0, 0, 5), 0, (2, .8))


mirrored(right_hide)


# ---------------------------------------------------------------- painters
def slot(a, b, cx, cy, w=1.05, h=.5):
    """A slot let into the armour at (cx, cy), w long: 2 inside it, 1 on its rim, 0 off it."""
    dx = max(abs(a - cx) - (w - h) / 2, 0)
    d = math.hypot(dx, b - cy)
    return 2 if d < h / 2 - .13 else (1 if d < h / 2 else 0)


def slots(a, b, places, w=1.05, h=.5):
    return max((slot(a, b, cx, cy, w, h) for cx, cy in places), default=0)


VEE = (VEST_Y + 1.9, .62)                          # the collar's V on the chest: its point's height, how fast it opens


def body(c, face, u, v, p, n):
    part, side = c.opts.get('part'), c.opts.get('side', -1)
    x, y, z = p
    t = .64 + .3 * max(0.0, float(n @ LIGHT)) - (.22 if face == 'down' else 0) - (.05 if face == 'south' else 0)
    ink = 0.0
    inner = (side < 0 and face == 'west') or (side > 0 and face == 'east')      # the face a limb turns to the body
    outer = (side < 0 and face == 'east') or (side > 0 and face == 'west')
    if part in ('abdomen', 'waist', 'midribs', 'lowribs'):
        if face == 'north':
            # a smooth belly: light down its middle, falling off to the flanks; no muscles drawn on it
            t += .05 - .16 * (abs(x) / 2.8) ** 2
            if y > VEST_Y - .45:
                t -= .3                                         # the vest's shadow
            if y < BELT_Y + 1.15:
                t -= .1                                         # ... and a little shade over the belt
        elif face == 'south':
            strap = abs(abs(x) - (.4 + (y - BELT_Y - .9) * .36))        # the two dark straps down the back
            if strap < .24:
                return mix(BROWN[1], BROWN[0], .5 if strap > .16 else 0)
            t += .04 - .12 * abs(abs(x) - 1.1)                  # the back's two long muscles, as light and shade only
            t -= .26 if abs(x) < .22 else 0                     # the furrow of the spine
            if y > VEST_Y - .45:
                t -= .25
        else:
            t -= .1 + (.12 if y > VEST_Y - .5 else 0)
    elif part == 'pelvis':
        t -= .12
        if face == 'down' or y < CROTCH_Y + .7:
            return ramp(BROWN, .25 + .3 * pillow(u, v))         # dark between the legs
        if face == 'south':
            t -= .1
    elif part == 'neck':
        t -= .2 if face == 'north' else (.34 if face == 'south' else .16)
        if face == 'north' and abs(abs(x) - .5) < .1:
            t += .25                                            # the two cords of the throat
    elif part == 'upperarm':
        if inner:
            t -= .26
        if outer:
            t += .08 * math.sin(v * math.pi)
        if face == 'north' and abs(u - (.35 if side < 0 else .65)) < .05 and .15 < v < .8:
            ink = .35
    elif part == 'thigh':
        if inner:
            t -= .26
        if face == 'north':
            k = u if side < 0 else 1 - u                        # 0 at the outside
            t += .08 - .3 * (k - .42) ** 2 * 2                  # one long light down the front, no lines
        if face == 'south':
            k = abs(u - .5) * 2
            t += .1 - .3 * k * k
        if outer:
            t -= .04
    elif part in ('palm', 'fingers', 'thumb'):
        if inner:
            t -= .2
        if part == 'fingers':
            if outer or inner:
                if (u * 4) % 1 < .14 and u > .1:
                    ink = .6                                    # the fingers side by side
                if outer and v < .22 and .25 < (u * 4) % 1 < .8:
                    return PURPLE[3] if v > .08 else PURPLE[4]   # the purple beads on the knuckles
            if face == 'down' and (v * 4) % 1 < .14:
                ink = .6
        if part == 'thumb' and face == 'north' and v > .8:
            ink = .3
    t += .02 * noise(x, y, z)
    col = ramp(BODY[1:], t)
    # a line round the pieces that stand out of the body, and round the hands; the limbs themselves stay smooth
    if edge_texels(c, face, u, v) < 1 and part in ('palm', 'fingers', 'thumb'):
        ink = max(ink, .3)
    return mix(col, BODY[0], ink) if ink else col


TEAR = hexc('#e4eaf0')                  # the pale line down each cheek


def eye(ax, y):
    """The eyes: a narrow almond under the arch on either side, slanting down to the nose, purple all through (no
    pupil), a heavy dark lid over it. 0 off it, 1 its lid, 2 inside it."""
    k = (ax - 1.0) / .62
    if abs(k) >= 1:
        return 0
    d = y - (BROW_Y - .45 + .13 * k)
    half = .18 * math.sqrt(1 - k * k) ** .8
    if abs(d) > half + .05:
        return 0
    return 1 if d > half - .07 else 2


def face_(c, face, u, v, p, n):
    part = c.opts.get('part')
    x, y, z = p
    ax, h = abs(x), y - CHIN_Y                                  # h: how far above the chin
    t = tone(n, u, v, .52, face)
    if face == 'south' or (face in ('east', 'west') and z > -.5):
        # the back of the head under the helmet: bare, in the helmet's shade near its rim
        return ramp(BODY[1:], .34 + .1 * pillow(u, v) - .3 * max(0.0, (y - (BROW_Y - .7)) / .7))
    if face in ('east', 'west'):
        t -= .2 + .25 * max(0.0, (z - FACE_Z) / 2)              # the cheeks falling into shade towards the ears
    if face == 'down':
        return ramp(BODY, .2 + .15 * pillow(u, v))
    if part == 'nose':
        if face == 'north':
            col = ramp(BODY[1:], .82 if ax < .08 else .66)      # a thin light down its ridge
            return mix(col, LINE, .55) if h < FACE * .45 and ax > .1 else col
        return ramp(BODY[1:], .34)
    if face != 'north':
        return ramp(BODY[1:], t)
    # the front of the face
    t = .7 - .1 * ax
    if y > BROW_Y - .17:
        t -= .32                                                # the helmet's shade over the brow
    if ax > .9 + (h - .3) * .55 and h < FACE * .68:
        t -= .34                                                # the hollows of the cheeks
    col = ramp(BODY[1:], t)
    e = eye(ax, y)
    if e == 2:
        g = (int(x * 16 + 64) + int(y * 16)) % 3                # purple, grained like the art's
        return PURPLE[4] if g == 0 else PURPLE[3]
    if e == 1:
        return EYE
    # the line down each cheek from the eye, hooking in beside the lips
    tx = .86 + .05 * math.sin((h - 1.0) * 2.4) if h > 1.0 else .86 - (1.0 - h) * .35
    if FACE * .3 < h < FACE - .66:
        d = abs(ax - tx)
        if d < .04:
            return TEAR
        if d < .075:
            col = mix(col, LINE, .45)
    # the mouth: the bow of the upper lip and the shade under it
    lip = FACE * .33 - .1 * abs(ax - .17) / .38
    if ax < .42 and abs(h - lip) < .045:
        col = mix(col, LINE, .85)
    elif ax < .33 and lip - .15 < h < lip - .045:
        col = mix(col, BODY[1], .35)
    return col


def helmet(c, face, u, v, p, n):
    part, layer = c.opts.get('part'), c.opts.get('layer', 0)
    x, y, z = p
    ax, h = abs(x), y - BROW_Y                                  # h: how far above the brow
    t = tone(n, u, v, .52, face)
    if part == 'plate':
        if face != 'north':
            return ramp(HELM[1:], t - .12)
        rise = (y - PLATE[3]) / (PLATE[1] - PLATE[3])
        r = math.hypot(x / PLATE[2], max(0.0, rise))
        if r > 1:
            return None                                         # the arch
        # glossy: a streak of light down its left, darkening to its right and down to the brow
        t = .66 + .12 * rise - .16 * (x / PLATE[2])
        if -.68 < x / PLATE[2] < -.42 and h > .4 and r < .84:
            t = .98
        if .32 < x / PLATE[2] < .46 and .6 < h < 1.5:
            t = .9
        if h < .3:
            t -= .2
        col = ramp(HELM[1:], t)
        edge = min((1 - r) * PLATE[2], y - PLATE[0])
        return mix(col, LINE, .8) if edge < .09 else col
    if face == 'down':
        return ramp(HELM, .1 + .2 * pillow(u, v))               # the dark underside of the bulb
    # lit as one smooth bulb whatever box a texel is on: its facing is the bulb's there
    out = norm((x / HELM_HALF, (h - 1.2) / HELM_TOP + .15, (z - .55) / (HELM_HALF * 1.1)))
    t = .5 + .34 * max(0.0, float(out @ LIGHT)) + .1 * out[1] + (.06 if face == 'up' else 0)
    if face == 'north' and -.78 < x / HELM_HALF < -.6 and .9 < h < 1.8:
        t += .14                                                # a glint on its left
    col = ramp(HELM[1:], t)
    line = 0.0
    if ax < .075 and not (face == 'north' and y < PLATE[1]):
        line = .8                                               # the ridge down its middle, from the arch over to the nape
    if face in ('up', 'north', 'south') and abs(ax - HELM_HALF * (.48 + .035 * (z + HELM_HALF))) < .075 and (face != 'north' or h > 3.3 or z > -1.3) \
            and (face != 'south' or h > 1.5):
        line = .7                                               # a line on either side of it, running back over the bulb
    if face == 'north' and ax > PLATE[2] and abs(h - (1.2 - .5 * (ax - PLATE[2]))) < .075:
        line = .7                                               # a line from either side of the arch round to the temple
    if face in ('east', 'west') and abs(h - (.42 + .3 * (z + HELM_HALF))) < .075 and z < HELM_HALF * 1.05:
        line = .7                                               # ... rising along the side to the back
    if face != 'up' and h < .11 and not (face == 'north' and ax < PLATE[2]):
        line = .75                                              # the dark rim round its foot
    if face == 'north' and y > PLATE[1] - .1:
        # the curl on either side of the ridge: wound one and a half times, its tail running down to the arch's corner
        dx, dy = ax - CURL[0], y - CURL[1]
        r, th = math.hypot(dx, dy), math.atan2(dy, dx)
        k = CURL[2] / (3 * math.pi)
        for turn in range(3):
            a = th + math.pi / 2 + 2 * math.pi * turn
            if 0 <= a <= 3 * math.pi and abs(r - k * a) < .065:
                line = .85
    return mix(col, LINE, line) if line else col


def armor(c, face, u, v, p, n):
    part, side = c.opts.get('part'), c.opts.get('side', -1)
    x, y, z = p
    t = tone(n, u, v, .42, face)
    s = 0
    if part == 'ear':
        r = math.hypot(u - .5, v - .5) * 2
        if r > 1:
            return None
        return ARMOR[0] if r > .8 else (ARMOR[2] if r < .32 else ramp(ARMOR[1:], .5 + .3 * (.5 - v)))
    if part in ('vest', 'pec') and face == 'north':
        open_ = (y - VEE[0]) * VEE[1]                           # how far the V has opened at this height
        if y > VEE[0] and abs(x) < open_:
            return ramp(BODY, .08 + .16 * pillow(u, v))         # the throat in the collar's shade
        if y > VEE[0] - .35 and abs(x) < open_ + .34:
            rim = ramp(ARMOR[2:], .55 + .3 * (1 - abs(abs(x) - open_ - .17) / .17))
            return mix(rim, SLOT_LINE, .6) if abs(abs(x) - open_ - .34) < .07 or abs(abs(x) - open_) < .07 else rim
        s = slots(abs(x), y, ((2.6, VEST_Y + 3.4), (1.55, VEST_Y + 2.6), (2.65, VEST_Y + 2.6), (.6, VEST_Y + 1.35), (.6, VEST_Y + .55)), .8, .42)
        if part == 'pec':
            t += .12 * (1 - v) - .3 * max(0, v - .8) * 4
    elif part == 'collar':
        if face == 'north' and abs(x) < .95:
            return ramp(BODY, .08 + .16 * pillow(u, v))
        t += .1
    elif part in ('vest', 'backplate') and face == 'south':
        s = slots(abs(x), y, [(cx, VEST_Y + dy) for dy in (3.2, 2.15, 1.1) for cx in (.75, 2.2)], .95, .44)
    elif part == 'yoke':
        if face == 'up':
            s = slots(abs(x), z, ((2.95, 0),), .8, .42)
        t += .06
    elif part == 'cap':
        k = (z if face in ('east', 'west') else x)
        if face in ('east', 'west'):
            s = slots(k, y, ((-.6, SHOULDER_Y - .95), (.65, SHOULDER_Y - .95), (0, SHOULDER_Y - 1.9)), .85, .42)
        elif face in ('north', 'south'):
            s = slots(abs(x), y, ((-ARM_X, SHOULDER_Y - 1.4),), .9, .42)
        if y < SHOULDER_Y - 2.4:
            t -= .14
    elif part == 'guard':
        outer = (side < 0 and face == 'east') or (side > 0 and face == 'west')
        if outer:
            s = slots(z, y, [(0, WRIST_Y + .4 + (ELBOW_Y - 1.35 - WRIST_Y) * f) for f in (.14, .38, .62, .86)], .9, .42)
        elif face in ('north', 'south'):
            s = slots(abs(x), y, [(-ARM_X, WRIST_Y + .4 + (ELBOW_Y - 1.35 - WRIST_Y) * f) for f in (.26, .74)], .8, .4)
        else:
            t -= .2
    elif part == 'shin':
        # the lower leg: an orange guard down its front and its outside, slots down the front; brown boot on its inside;
        # behind, the bare calf stands out of the guard as an oval, brown boot under it
        inner = (side < 0 and face == 'west') or (side > 0 and face == 'east')
        top, calf = KNEE_Y - 1.0, KNEE_Y - 1.0 - ANKLE_Y
        f = (y - ANKLE_Y) / calf                                # 0 at the ankle, 1 under the knee
        if inner:
            return inked(c, face, u, v, ramp(BROWN, tone(n, u, v, .4, face)), .3, ink=BROWN[0])
        if face == 'south':
            a, b = (abs(x) + LEG_X) / .95, (f - .66) / .3
            rr = a * a + b * b
            if rr < 1:
                col = ramp(BODY[1:], .72 - .3 * rr + .1 * b)
                return mix(col, BODY[0], .6) if rr > .8 else col
            if f < .3:
                return ramp(BROWN, tone(n, u, v, .4, face))
        if face == 'north':
            s = slots(abs(x), y, [(-LEG_X, ANKLE_Y + calf * g) for g in (.2, .4, .6, .8)], .95, .44)
            t += .08
        if face in ('up', 'down'):
            t -= .15
    elif part == 'cod':
        if face == 'north':
            s = slots(x, y, ((0, BELT_Y - .65), (0, BELT_Y - 1.35), (0, BELT_Y - 2.25)), .8, .4)
            t -= .25 * max(0, v - .7) * 3
    elif part == 'belt':
        t += .08
        if abs(v - .5) < .12 and face != 'up':
            t += .2
    if s == 2:
        return SLOT
    if s == 1:
        return SLOT_LINE
    t += .02 * noise(x, y, z)
    return inked(c, face, u, v, ramp(ARMOR[1:], t), .45, ink=ARMOR[0])


def plain(cols, base=.42, line=.4):
    def painter(c, face, u, v, p, n):
        x, y, z = p
        t = tone(n, u, v, base, face)
        part = c.opts.get('part')
        if part == 'boot' and face in ('east', 'west', 'south') and abs(y - ANKLE_Y - .75) < .1:
            t += .3                                             # the strap round the boot
        if part == 'toe' and face == 'up':
            t += .1
        return inked(c, face, u, v, ramp(cols, t), line, ink=cols[0])
    return painter


def purple(c, face, u, v, p, n):
    part = c.opts.get('part')
    t = tone(n, u, v, .42, face)
    col = ramp(PURPLE[1:], t + (.12 if part == 'sole' and face in ('east', 'west') else 0))
    return inked(c, face, u, v, col, .4, ink=PURPLE[0])


def bug(c, face, u, v, p, n):
    """A ladybird (its shell split down the middle, dark spots, its head at the top) or a plain stone, painted about
    its own middle; the upper disc goes on with the lower one's picture."""
    part, axis, out = c.opts.get('part'), c.opts['axis'], c.opts['out']
    at, r = c.opts.get('full', (c.opts['at'], c.opts['r']))
    main = face == {('z', -1): 'north', ('z', 1): 'south', ('x', -1): 'east', ('x', 1): 'west'}[(axis, out)]
    if not main:
        return ramp(PURPLE, .12 + .2 * (1 - v))
    a = ((p[0] - at[0]) if axis == 'z' else (p[2] - at[2])) / r[0]
    b = (p[1] - at[1]) / r[1]
    rr = math.hypot(a, b)
    t = .62 + .22 * (b * .7 - a * .4 * (1 if axis == 'z' else out)) + .1 * (1 - rr)
    if (a + .3) ** 2 + (b - .38) ** 2 < .03:
        t = .95                                                 # the glint
    col = ramp(PURPLE[1:], t)
    if part == 'bug':
        if b > .52:
            col = PURPLE[1]                                     # its head
        if abs(b - .52) < .05 or (abs(a) < .055 and b < .52):
            col = PURPLE[0]
        for sx, sy in ((.42, .2), (-.42, .2), (.5, -.3), (-.5, -.3), (.22, -.68), (-.22, -.68)):
            if (a - sx) ** 2 + (b - sy) ** 2 < .028:
                col = PURPLE[0]
    if part == 'chin':
        col = ramp(PURPLE[2:], .4 + .5 * (1 - v))
    return inked(c, face, u, v, col, .5, ink=PURPLE[0]) if c.opts.get('layer') == 0 else col


def silver(c, face, u, v, p, n):
    part = c.opts.get('part')
    t = tone(n, u, v, .46, face)
    if part == 'ring':
        at, r = c.opts['at'], c.opts['r']
        a, b = (p[0] - at[0]) / r[0], (p[1] - at[1]) / r[1]
        rr = math.hypot(a, b)
        clasp = abs(p[0] - at[0]) < .3 and b > .9                # the clasp it hangs by
        if not clasp and (rr > 1 or rr < .64):
            return None
        col = ramp(SILVER[1:], .45 + .4 * (b * .5 - a * .5) + (.2 if abs(rr - .82) < .05 else 0))
        return mix(col, SILVER[0], .6) if (abs(rr - 1) < .07 or abs(rr - .64) < .07) and not clasp else col
    if part == 'swirl':
        at, r = c.opts['at'], c.opts['r']
        if face not in ('north',):
            return inked(c, face, u, v, ramp(SILVER[:5], t - .15), .4, ink=SILVER[0])
        side = c.opts.get('side', -1)
        a, b = (p[0] - at[0]) / r[0] * (1 if side < 0 else -1), (p[1] - at[1]) / r[1]
        rr, th = math.hypot(a, b), math.atan2(b, a)
        col = ramp(SILVER[1:], .55 + .3 * (b * .6 - a * .3))
        k = .9 / (4 * math.pi)
        for turn in range(3):
            ang = th - math.pi / 2 + 2 * math.pi * turn          # wound twice, running out upwards into the blade
            if 0 <= ang <= 4 * math.pi and abs(rr - k * ang) < .075:
                col = SILVER[0]
        return col
    # the blade: painted along itself, feathers running its length, cut to a point at the tip
    k, pieces = c.opts['piece'], c.opts['pieces']
    along = c.opts['start'] + (1 - v) * c.opts['seg'] / c.opts['length']
    if face in ('north', 'south'):
        w = 1.0
        if k == pieces - 1:
            w = max(0.0, 1 - (1 - v) * 1.02)
        edge = w - abs(2 * u - 1)
        if edge < 0:
            return None
        col = ramp(SILVER[1:], .5 + .25 * (1 - abs(2 * u - 1)) + .15 * (1 - along))
        for f in (-.45, .0, .45):
            if abs((2 * u - 1) - f * w) < .06 and along > .12:
                col = mix(col, SILVER[0], .55)                   # the feathers' lines
        return mix(col, SILVER[0], .7) if edge < .12 else col
    return ramp(SILVER[:5], t - .1)


def cloth(c, face, u, v, p, n):
    """The loincloth: a hide, its end torn into big points; folds running down it, darker towards its end and on its
    inside."""
    cols = FUR_PALE if c.opts['pale'] else FUR
    along = c.opts['start'] + v * c.opts['share']               # 0 at the belt, 1 at its end
    k = u if face in ('north', 'east') else 1 - u               # the same picture on both sides
    count, phase = c.opts['teeth']
    tooth = abs(((k * count + phase) % 1) * 2 - 1)              # 0 at a point's tip, 1 in the notch between two
    end = 1 - .3 * tooth
    if along > end:
        return None
    seed = sum(ord(ch) for ch in c.opts['strip'])
    fold = math.sin(k * 7.5 + seed) * .5 + math.sin(k * 17 + seed * 1.7 + along * 2) * .25
    t = .62 - .22 * along + .2 * fold
    if face in ('north', 'west' if c.opts.get('side', -1) < 0 else 'east'):
        t -= .3                                                 # its inside, against the legs
    if along < .07:
        t += .25                                                # where it is tied under the belt
    col = ramp(cols, t)
    return mix(col, cols[0], .7) if end - along < .035 or min(k, 1 - k) < .03 else col


PAINT = {'body': body, 'face': face_, 'helmet': helmet, 'armor': armor, 'brown': plain(BROWN, .4), 'cuff': plain(CUFF, .42),
         'shoe': plain(BODY[2:], .5, .35), 'purple': purple, 'bug': bug, 'silver': silver, 'cloth': cloth}
OWN = ('bug', 'silver', 'cloth')          # painted in their own frames: the badges about their middles, the wings and the hide along themselves

# ---------------------------------------------------------------- stances
# gold_experience_poses.json: each bone's turn in degrees as Blockbench writes it (added to the bone's own) and its
# offset in units. A limb can be aimed, and an arm can reach its palm to a place (rig.pose_table).
POSES = {
    # standing as it is built: for looking at the model
    'stand': {'right_arm': {'rot': (0, 0, 9)}, 'left_arm': {'rot': (0, 0, -9)}},
    # a stand floating at rest: a little off the ground, toes pointing down, arms loose, a knee bent
    'idle': {
        'root': {'pos': (0, 2.5, 0)},
        'torso': {'rot': (2, 0, 0)},
        'head': {'rot': (3, 0, 0)},
        'right_arm': {'rot': (-5, 0, 11)}, 'right_forearm': {'rot': (-16, 0, 0)},
        'left_arm': {'rot': (-5, 0, -11)}, 'left_forearm': {'rot': (-16, 0, 0)},
        'right_leg': {'rot': (-4, 0, 2)}, 'right_shin': {'rot': (10, 0, 0)}, 'right_foot': {'rot': (22, 0, 0)},
        'left_leg': {'rot': (-14, 0, -2)}, 'left_shin': {'rot': (26, 0, 0)}, 'left_foot': {'rot': (26, 0, 0)},
    },
    # the picture the user sent: floating with its legs crossed, the hips thrown to its left and the chest back over
    # them, its left hand on its hip with the elbow out, its right hand hanging at its thigh, the head tipped down
    'hero': {
        'root': {'pos': (0, 3.0, 0)},
        'hips': {'rot': (0, -8, 7)},
        'torso': {'rot': (-2, 6, -6)},
        'chest': {'rot': (-3, 8, -7)},
        'neck': {'rot': (4, -4, 2)},
        'head': {'rot': (9, -10, 3)},
        'left_arm': {'reach': ((3.9, BELT_Y + .3, -1.1), (1, -.25, .55))},
        'left_hand': {'rot': (0, 0, 55)},
        'right_arm': {'reach': ((-5.0, CROTCH_Y - 3.0, -2.8), (-1, .1, .4))},
        'right_hand': {'rot': (-8, 0, 10)},
        # the knees apart, the shins crossing under it, the right in front, the toes pointing down
        'right_leg': {'rot': (-20, 0, 24)}, 'right_shin': {'rot': (26, 0, -50)}, 'right_foot': {'rot': (36, 0, 0)},
        'left_leg': {'rot': (-6, 0, -22)}, 'left_shin': {'rot': (44, 0, 44)}, 'left_foot': {'rot': (40, 0, 0)},
        'hide_1': {'rot': (10, 0, 0)}, 'hide_2': {'rot': (12, 0, -4)},
        'left_ring': {'rot': (-6, 0, 6)},
    },
    # (2.146.0) coming out of its user (Q) and going back into them: curled up, its forearms crossed before its chest,
    # its knees drawn up, its head down
    'summon': {
        'root': {'pos': (0, 2.5, 0)},
        'hips': {'rot': (6, 0, 0)},
        'torso': {'rot': (14, 0, 0)},
        'chest': {'rot': (12, 0, 0)},
        'head': {'rot': (16, 0, 0)},
        'right_arm': {'reach': ((2.6, SHOULDER_Y - 2.6, -4.4), (-1, -.6, .3))},
        'left_arm': {'reach': ((-2.6, SHOULDER_Y - 3.6, -5.0), (1, -.6, .3))},
        'right_leg': {'rot': (-64, 0, 5)}, 'right_shin': {'rot': (98, 0, 0)}, 'right_foot': {'rot': (28, 0, 0)},
        'left_leg': {'rot': (-50, 0, -5)}, 'left_shin': {'rot': (92, 0, 0)}, 'left_foot': {'rot': (28, 0, 0)},
        'right_wing': {'rot': (0, -18, 0)}, 'left_wing': {'rot': (0, 18, 0)},
    },
}


def main():
    for b in rig.check_coplanar():
        print('coplanar overlap (would flicker):', b)
    place, height = rig.pack()
    img = rig.paint(place, height, PAINT, OWN)
    for d in ('geo/entity', 'textures/entity', 'stands'):
        (ROOT / d).mkdir(parents=True, exist_ok=True)
    (ROOT / 'geo/entity/gold_experience.geo.json').write_text(json.dumps(rig.geo(place, height, 'geometry.botand.gold_experience'), indent=1))
    Image.fromarray(img, 'RGBA').save(ROOT / 'textures/entity/gold_experience.png', optimize=True)
    (ROOT / 'stands/gold_experience_poses.json').write_text(json.dumps({k: rig.pose_table(v, GRIPS) for k, v in POSES.items()}, indent=1))
    top = max(c.origin[1] + c.size[1] for c in rig.CUBES if rig.BONES[c.bone].rot == [0, 0, 0])
    print(f'{len(rig.BONES)} bones, {len(rig.CUBES)} cubes, texture {rig.ATLAS_W}x{height}, {top:.2f} units tall')


if __name__ == '__main__':
    main()
