"""Gold Experience's stances for its skin "고퀄리티" (hq, 2.156.0; 2.155.0 to 2.155.1 it was Gold Experience's own body):
the model the user sent, which tools/stand/import_gold_experience_hq.py takes out of their file with the skeleton the
game turns.

usage: python3 tools/stand/make_gold_experience_hq.py [OUT]

It reads the skeleton (geo/entity/gold_experience_hq.geo.json) and the body (stands/gold_experience_hq_mesh.bin) under
src/main/resources/assets/celestialfall, or under OUT, and writes beside them:

  stands/gold_experience_hq_poses.json   each stance: each bone's turn in degrees as Blockbench writes it (added to the
                                      bone's own) and its offset in units
  stands/gold_experience_hq_fists.json   where each fist's knuckles and each shoulder are in the stances that strike
                                      (client/stand/GoldRush and those that take its fists), and what of the kneeling
                                      stance touches the ground

The box-built figure, painted to the pictures the user sent, is Gold Experience's own look again (2.156.0: the
original, tools/stand/make_gold_experience.py builds it and its stances). The stances are the same ones, written from the same places: where a fist goes
(model units, soles at 0, the front towards -z and the left side towards +x as the geo.json has them), from the top of
the shoulders (SHOULDER_Y). Their heights here are this body's. An arm reaches its grip to a place (rig.pose_table), or
is carried with the chest as it is in the guard (reaching()); every other bone is turned outright or aimed.
"""
import json
import math
import struct
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
import rig  # noqa: E402
from rig import norm  # noqa: E402

ROOT = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parents[2] / 'src/main/resources/assets/celestialfall'

# ---------------------------------------------------------------- the skeleton and the body
GEO = json.loads((ROOT / 'geo/entity/gold_experience_hq.geo.json').read_text())['minecraft:geometry'][0]
for _b in GEO['bones']:
    rig.bone(_b['name'], _b.get('parent'), _b['pivot'], _b.get('rotation', (0, 0, 0)))
PIVOT = {n: np.array(b.pivot, dtype=np.float64) for n, b in rig.BONES.items()}
FINGERS = ('index', 'middle', 'ring', 'pinky')


def load_mesh(path):
    """The body (client/stand/GoldExperienceMesh reads the same file): its bones, every vertex where it is at rest in the
    geo.json's units (x mirrored back the way Bedrock writes it), its bones and their weights."""
    data = path.read_bytes()
    if data[:4] != b'GEM1':
        raise ValueError('not a Gold Experience body: ' + str(path))
    off = 4
    (count,) = struct.unpack_from('>i', data, off)
    off += 4
    names = []
    for _ in range(count):
        (n,) = struct.unpack_from('>H', data, off)
        names.append(data[off + 2:off + 2 + n].decode('ascii'))
        off += 2 + n
    (verts,) = struct.unpack_from('>i', data, off)
    off += 4
    pos, bones, weights = np.zeros((verts, 3)), np.zeros((verts, 4), np.int64), np.zeros((verts, 4))
    for i in range(verts):
        v = struct.unpack_from('>8f4B4f', data, off)
        off += 52
        pos[i] = (-v[0] * 16, v[1] * 16, v[2] * 16)
        bones[i] = v[8:12]
        weights[i] = v[12:16]
    return names, pos, bones, weights


BODY = load_mesh(ROOT / 'stands/gold_experience_hq_mesh.bin')
# each vertex goes with the bone that carries most of it
CARRIER = np.array([BODY[0][BODY[2][i, int(np.argmax(BODY[3][i]))]] for i in range(len(BODY[1]))])

# where the stances are written from (units): the top of the shoulders, 1.1 over the shoulder's joint (as the stances
# were first written for the box-built figure); this body's belt (the orange band at its waist, 24.5 to 25.5) and where
# its legs part (22.5)
SHOULDER_Y = float(PIVOT['left_arm'][1]) + 1.1
BELT_Y, CROTCH_Y = 25.2, 22.5
SY = SHOULDER_Y
# (2.155.1) How much lower than they were first written the arms aim at whom they are for (units). Drawn 1.12 times as
# big as in 2.155.0 (client/stand/GoldExperienceRenderer.SCALE; the user's choice of four drafts: "B 통째로 1.12배")
# this body's shoulders stand a third of a block higher than the box-built figure's did, and fists aimed from them as
# before went over a player's head; aimed this much lower they land where the box-built figure's landed (a player's
# face and the top of their chest), the arms driven down a little at whom they strike. An arm reaching out past its
# length (a blow at full stretch) goes down less than the point it aims at; a hand held within its reach (the hook,
# the left held out as the cross winds up) goes down as far: DROP_HELD.
DROP, DROP_HELD = 9.0, 6.0
# an arm reaches with the middle of its palm (where a fist is), straight on down its forearm at rest; the fist's
# knuckles (where the rush's light is: client/stand/GoldRush) are over the fingers' joints, on the back of the hand
_PALM = float(PIVOT['left_hand'][1] - PIVOT['left_fingers'][1])
GRIPS = {s: (float(PIVOT[s + '_hand'][0]), float(PIVOT[s + '_hand'][1]) - .74 * _PALM, float(PIVOT[s + '_hand'][2])) for s in ('left', 'right')}
KNUCKLES = {s: tuple(PIVOT[s + '_fingers'] + np.array([.3 if s == 'left' else -.3, -.25, 0.0])) for s in ('left', 'right')}


def fist(side, fold):
    """A hand's fingers folded (degrees at their knuckles, the fingers' middle and last joints folding after them): the
    left hand folds about +z, the right about -z. 0 opens it."""
    s = 1 if side == 'left' else -1
    out = {side + '_fingers': {'rot': (0, 0, s * fold)}}
    for f in FINGERS:
        out[side + '_' + f + '_1'] = {'rot': (0, 0, s * fold * .95)}
        out[side + '_' + f + '_2'] = {'rot': (0, 0, s * fold * .7)}
    return out


# ---------------------------------------------------------------- stances
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
        **fist('right', 18), **fist('left', 18),
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
        'left_arm': {'reach': ((4.1, BELT_Y + .3, -1.1), (1, -.25, .55))},
        'left_hand': {'rot': (0, 0, 55)},
        'right_arm': {'reach': ((-5.0, CROTCH_Y - 3.0, -2.8), (-1, .1, .4))},
        'right_hand': {'rot': (-8, 0, 10)},
        # the knees apart, the shins crossing under it, the right in front, the toes pointing down
        'right_leg': {'rot': (-20, 0, 24)}, 'right_shin': {'rot': (26, 0, -50)}, 'right_foot': {'rot': (36, 0, 0)},
        'left_leg': {'rot': (-6, 0, -22)}, 'left_shin': {'rot': (44, 0, 44)}, 'left_foot': {'rot': (40, 0, 0)},
        **fist('right', 24), **fist('left', 40),
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
        **fist('right', 60), **fist('left', 60),
    },
}


def chest_at(spec):
    """(2.154.0) Where a stance puts the chest (4x4, model units, soles at 0): down from the root, as the game's model
    places it."""
    m = np.eye(4)
    for name in ('root', 'hips', 'torso', 'chest'):
        m = m @ rig.bone_matrix(rig.BONES[name], spec.get(name, {}))
    return m


def reaching(spec):
    """A stance with what its arms reach for carried out with the body. An arm can also be carried (2.154.0: "carry":
    where its grip goes and where its elbow points, written as if the body stood in the guard): it reaches there in the
    guard's chest, carried round with this stance's chest. So the arm holds to the chest as it does in the guard however
    the body turns, and a stance whose arms are carried blends with the guard and the others without the arm going round
    the far way (an arm reaching for a point in the model has to turn as far as the body does, and then some)."""
    carry = chest_at(spec) @ np.linalg.inv(chest_at(POSES['guard']))

    def placed(e):
        if 'carry' in e:
            at = carry @ np.array([*e['carry'][0], 1.0])
            pole = carry[:3, :3] @ np.array(e['carry'][1], dtype=np.float64)
            return {**{k: v for k, v in e.items() if k != 'carry'}, 'reach': (tuple(at[:3]), tuple(pole))}
        return e
    return {n: placed(e) for n, e in spec.items()}


# ---------------------------------------------------------------- the muda rush (2.147.0)
# Its E (user request: "E스킬 무다러시 부터 만들자"): floating in a fighting stance it drives its fists out straight one
# after the other, the shoulder behind each, while the other comes back to the guard, as Star Platinum's rush does
# (tools/stand/make_star_platinum.py's rush stances, which these follow at this figure's heights). Each stance has one
# fist out -- at the middle, high or low -- and client/stand/GoldRush goes from one to the next, left and right in turn.
# The guard is what it holds before the first fist and after the last.
FIST = {**fist('right', 100), **fist('left', 100)}
RUSH_OUT = {'mid': ((1.0, SHOULDER_Y - 2.4 - DROP, -24.0), 0.0), 'hi': ((1.8, SHOULDER_Y + 2.8 - DROP, -24.0), -5.0),
            'lo': ((.4, SHOULDER_Y - 7.8 - DROP, -23.0), 8.0)}
RUSH_BACK = {'left': ((3.4, SHOULDER_Y + 1.2, -6.5), (.6, -1, .2)), 'right': ((-3.4, SHOULDER_Y + 1.2, -6.5), (-.6, -1, .2))}
RUSH_RISE = {'mid': 0.0, 'hi': .4, 'lo': -.9}

POSES['guard'] = {
    'root': {'pos': (0, .6, -.9)},
    'hips': {'rot': (14, 24, 0)}, 'torso': {'rot': (9, 4, 0)}, 'chest': {'rot': (7, 4, 0)},
    'neck': {'rot': (-10, -12, 0)}, 'head': {'rot': (-12, -18, 0)},
    'left_arm': {'reach': ((3.4, SHOULDER_Y + 1.4, -8.5), (.6, -1, .2))},
    'right_arm': {'reach': ((-2.0, SHOULDER_Y + 1.0, -4.5), (-.4, -1, .1))},
    'left_leg': {'rot': (-34, 0, -4)}, 'left_shin': {'rot': (42, 0, 0)}, 'left_foot': {'rot': (18, 0, 0)},
    'right_leg': {'rot': (22, 0, 6)}, 'right_shin': {'rot': (44, 0, 0)}, 'right_foot': {'rot': (34, 0, 0)},
    **FIST,
}


def rush_stance(side, where):
    """The barrage with the {side} fist thrown out straight {where} (mid, hi or lo), the other one back in the guard."""
    target, dip = RUSH_OUT[where]
    s = 1 if side == 'left' else -1          # the left side is +x, and turning +y brings the left shoulder forward
    other = 'right' if side == 'left' else 'left'
    return {
        'root': {'pos': (0, .6 + RUSH_RISE[where], -1.4)},
        'hips': {'rot': (11, 6 * s, 0)}, 'torso': {'rot': (8 + .3 * dip, 10 * s, 0)}, 'chest': {'rot': (6 + .6 * dip, 16 * s, 0)},
        'neck': {'rot': (-9 - .3 * dip, -12 * s, 0)}, 'head': {'rot': (-12 - .5 * dip, -16 * s, 0)},
        side + '_arm': {'reach': ((target[0] * s, target[1], target[2]), (.5 * s, -1, 0))},
        other + '_arm': {'reach': RUSH_BACK[other]},
        'left_leg': {'rot': (-24, 0, -4)}, 'left_shin': {'rot': (34, 0, 0)}, 'left_foot': {'rot': (16, 0, 0)},
        'right_leg': {'rot': (16, 0, 6)}, 'right_shin': {'rot': (40, 0, 0)}, 'right_foot': {'rot': (30, 0, 0)},
        **FIST,
    }


for _side in ('left', 'right'):
    for _where in ('mid', 'hi', 'lo'):
        POSES['rush_' + _side[0] + ('' if _where == 'mid' else '_' + _where)] = rush_stance(_side, _where)

# R, the sensory overdrive (2.148.0): one heavy punch (client/stand/GoldPunch goes guard -> over_wind -> over_punch ->
# guard). Wound up: dropped low and back, the hips wound far round to the right, the right fist drawn far back at the
# shoulder's height with the elbow out behind it, the left hand held out open at whoever it is for, the head turned back
# onto them. Thrown: the hips and chest whipped round the other way and the whole body driven forward behind the right
# fist at full stretch, the left fist torn back to the ribs with its elbow behind it, the rear leg straight out behind.
POSES['over_wind'] = {
    'root': {'pos': (0, -.9, 3.2)},
    'hips': {'rot': (6, 60, 0)}, 'torso': {'rot': (4, 20, 0)}, 'chest': {'rot': (2, 16, 0)},
    'neck': {'rot': (-6, -36, 0)}, 'head': {'rot': (-8, -52, 0)},
    # (behind the hip the arm was half a turn from the guard's, and the blend of the two had no short way: it flipped)
    'right_arm': {'reach': ((-7.0, SHOULDER_Y - 1.5, 12.0), (-1, 0, 1))},
    'left_arm': {'reach': ((5.6, SHOULDER_Y + .4 - DROP, -19.0), (.7, -1, 0))},
    'left_leg': {'rot': (-48, 0, -8)}, 'left_shin': {'rot': (66, 0, 0)}, 'left_foot': {'rot': (14, 0, 0)},
    'right_leg': {'rot': (12, 0, 10)}, 'right_shin': {'rot': (72, 0, 0)}, 'right_foot': {'rot': (32, 0, 0)},
    **fist('right', 100), **fist('left', 12),
}
POSES['over_punch'] = {
    'root': {'pos': (0, -.2, -8.6)},
    'hips': {'rot': (30, -40, 0)}, 'torso': {'rot': (14, -20, 0)}, 'chest': {'rot': (9, -18, 0)},
    'neck': {'rot': (-15, 32, 0)}, 'head': {'rot': (-20, 38, 0)},
    'right_arm': {'reach': ((.2, SHOULDER_Y - 5.2 - DROP, -40.0), (-.5, -1, 0))},
    'left_arm': {'reach': ((8.0, SHOULDER_Y - 7.0, -2.0), (.3, .3, 1))},
    'left_leg': {'rot': (-58, 0, -4)}, 'left_shin': {'rot': (46, 0, 0)}, 'left_foot': {'rot': (0, 0, 0)},
    'right_leg': {'rot': (60, 0, 6)}, 'right_shin': {'rot': (0, 0, 0)}, 'right_foot': {'rot': (50, 0, 0)},
    **FIST,
}

# Y, the healing (2.149.0): one fist driven in and left in (user: "주먹을 한 번 꽂아서 주먹을 박아넣고 힐 되게";
# client/stand/GoldHeal goes guard -> mend -> guard, the same for another tapped and for its user held). It leans in on
# its front leg with the hips and chest turned behind the right fist, which is out straight at a body's chest and stays
# there; the left fist is back at the ribs, the head bowed a little onto the fist it has driven in.
POSES['mend'] = {
    'root': {'pos': (0, .3, -3.6)},
    'hips': {'rot': (13, -18, 0)}, 'torso': {'rot': (9, -12, 0)}, 'chest': {'rot': (7, -14, 0)},
    'neck': {'rot': (-2, 16, 0)}, 'head': {'rot': (2, 20, 0)},
    'right_arm': {'reach': ((-.6, SHOULDER_Y - 3.4 - DROP, -24.5), (-.5, -1, 0))},
    'left_arm': {'reach': RUSH_BACK['left']},
    'left_leg': {'rot': (-30, 0, -4)}, 'left_shin': {'rot': (36, 0, 0)}, 'left_foot': {'rot': (14, 0, 0)},
    'right_leg': {'rot': (26, 0, 6)}, 'right_shin': {'rot': (30, 0, 0)}, 'right_foot': {'rot': (36, 0, 0)},
    **FIST,
}

# H, the tree of life (2.152.0, user: "내리치지 말고 스탠드가 땅을 만지고 나무가 자라나게"; client/stand/GoldTree goes
# idle -> ground -> idle): down on its left knee, sitting back on that heel with its right foot planted out before it and
# that knee out to its side, it bows over and lays its open right palm flat on the ground ahead of it, fingers ahead and a
# little in, its left arm thrown out behind it, its head bowed over the palm. Its left leg is aimed (the thigh forward
# and down to the knee on the ground, the shin back along the ground, the instep down); kneeling() sets the rest from
# this body's lengths: how low it sits for that knee to touch, where its right knee goes for that foot to stand flat,
# and how far down its right arm reaches for that palm to lie on the ground (GoldExperienceModelTest holds all three to
# the ground).
POSES['ground'] = {
    'root': {'pos': (0, -15.2, 3.5)},
    'hips': {'rot': (36, -8, 0)}, 'torso': {'rot': (14, -6, 0)}, 'chest': {'rot': (8, -4, 0)},
    'neck': {'rot': (-12, 6, 0)}, 'head': {'rot': (-6, 6, 0)},
    'right_arm': {'reach': ((-1.336, -.38, -7.307), (-1, .4, .2))},
    'right_hand': {'aim': ((.173, -.08, -.982), (.982, 0, .173))},
    'left_arm': {'reach': ((6.4, 9.0, 2.4), (1, .3, -.4))},
    'left_leg': {'aim': ((1.231, -3.976, -8.761), (-.119, -.91, .396))},
    'left_shin': {'aim': ((0, .07, .998), (0, -.998, .07))},
    'left_foot': {'aim': ((0, 1, 0), (0, 0, 1))},
    'right_leg': {'aim': ((-7.273, 4.137, -4.907), (-.012, .756, .655))},
    'right_shin': {'aim': ((3.224, -8.314, -3.556), (-.782, -.037, -.622))},
    'right_foot': {'aim': ((0, -1, 0), (-.407, 0, -.914))},
    **fist('left', 10),
}

# The basic attack (2.154.0, user request: "마지막으로 평타 만들자"; asked and answered: "스타플래티나처럼 주먹 4타"):
# Star Platinum's four punches (tools/stand/make_star_platinum.py's) at this figure's heights, thrown from the guard
# above (the left side forward): 1 a left jab, 2 a right cross, 3 a left hook, 4 a right with the whole body behind it.
# Each has its loading stance and its strike (client/stand/GoldCombo goes guard -> wind -> strike -> guard, the next
# taking over before the last is back). Every load coils the body round away from the blow, low and back; every strike
# drives the whole body after the fist, the hips and shoulders wheeled through, leaning over the front leg with the
# back leg kicked out straight. The straights reach out past the stand's own arm's length (the arm then straight).
# The arms that are not punching, and the loads, are carried with the chest (written as if the body stood in the guard,
# reaching() turns them with it): so the guard's hand stays at the chin however the body wheels, and no arm has to turn
# the far way round between one stance and the next (the first build reached every hand for a point in the model, and
# with the body wheeled 60 to 90 degrees off the guard an arm then went over half a turn between stances: it flipped).
GUARD_L = ((3.4, SY + 1.4, -8.5), (.6, -1, .2))
GUARD_R = ((-2.0, SY + 1.0, -4.5), (-.4, -1, .1))
LEGS_LOAD = {'left_leg': {'rot': (-30, 0, -4)}, 'left_shin': {'rot': (50, 0, 0)}, 'left_foot': {'rot': (18, 0, 0)},
             'right_leg': {'rot': (24, 0, 6)}, 'right_shin': {'rot': (50, 0, 0)}, 'right_foot': {'rot': (34, 0, 0)}}
POSES['jab_wind'] = {
    'root': {'pos': (0, 0, 1.2)},
    'hips': {'rot': (13, 34, 0)}, 'torso': {'rot': (9, 8, 0)}, 'chest': {'rot': (7, 8, 0)},
    'neck': {'rot': (-10, -17, 0)}, 'head': {'rot': (-12, -24, 0)},
    # coiled a little further round, the fists held up where they are in the guard (drawn in to the chin, the left
    # went the far way round from how it is summoned, a hand on the hip, and Q then the attack at once flipped it)
    'left_arm': {'carry': GUARD_L},
    'right_arm': {'carry': GUARD_R},
    **LEGS_LOAD,
    **FIST,
}
POSES['jab'] = {
    'root': {'pos': (0, .7, -4.3)},
    'hips': {'rot': (20, 40, 0)}, 'torso': {'rot': (11, 12, 0)}, 'chest': {'rot': (7, 13, 0)},
    'neck': {'rot': (-11, -21, 0)}, 'head': {'rot': (-14, -32, 0)},
    # the left arm shot out straight at the target, the lead shoulder rammed forward, the body lunging in after it
    'left_arm': {'reach': ((.4, SY - 2.2 - DROP, -28.0), (.5, -1, 0))},
    'right_arm': {'carry': GUARD_R},
    'left_leg': {'rot': (-44, 0, -4)}, 'left_shin': {'rot': (28, 0, 0)}, 'left_foot': {'rot': (10, 0, 0)},
    'right_leg': {'rot': (38, 0, 6)}, 'right_shin': {'rot': (14, 0, 0)}, 'right_foot': {'rot': (46, 0, 0)},
    **FIST,
}
POSES['cross_wind'] = {
    'root': {'pos': (0, 0, 1.6)},
    'hips': {'rot': (13, 44, 0)}, 'torso': {'rot': (9, 12, 0)}, 'chest': {'rot': (7, 10, 0)},
    'neck': {'rot': (-10, -21, 0)}, 'head': {'rot': (-12, -32, 0)},
    # wound hard round to the right and low, the right fist cocked back by the cheek, the left still out at the target
    'left_arm': {'carry': ((3.4, SY + .8 - DROP_HELD, -13.0), (.6, -1, .15))},
    'right_arm': {'carry': ((-2.4, SY + 1.2, -3.5), (-.45, -1, .15))},
    'left_leg': {'rot': (-29, 0, -4)}, 'left_shin': {'rot': (50, 0, 0)}, 'left_foot': {'rot': (18, 0, 0)},
    'right_leg': {'rot': (24, 0, 8)}, 'right_shin': {'rot': (52, 0, 0)}, 'right_foot': {'rot': (34, 0, 0)},
    **FIST,
}
POSES['cross'] = {
    'root': {'pos': (0, .5, -4.7)},
    'hips': {'rot': (20, -24, 0)}, 'torso': {'rot': (11, -10, 0)}, 'chest': {'rot': (7, -10, 0)},
    'neck': {'rot': (-11, 15, 0)}, 'head': {'rot': (-14, 23, 0)},
    # the hips and shoulders wrenched round to the left, the right arm out straight, the left fist pulled back in to the guard
    'right_arm': {'reach': ((-.4, SY - 2.4 - DROP, -28.5), (-.5, -1, 0))},
    'left_arm': {'carry': ((3.2, SY + .9, -7.5), (.6, -1, .2))},
    'left_leg': {'rot': (-42, 0, -4)}, 'left_shin': {'rot': (24, 0, 0)}, 'left_foot': {'rot': (10, 0, 0)},
    'right_leg': {'rot': (42, 0, 6)}, 'right_shin': {'rot': (12, 0, 0)}, 'right_foot': {'rot': (50, 0, 0)},
    **FIST,
}
POSES['hook_wind'] = {
    'root': {'pos': (0, -.7, 1.6)},
    'hips': {'rot': (14, -22, 7)}, 'torso': {'rot': (11, -15, 7)}, 'chest': {'rot': (7, -9, 5)},
    'neck': {'rot': (-8, 16, -6)}, 'head': {'rot': (-12, 20, -6)},
    # dropped low, the weight on the left leg, the body flung open to the left, the left elbow raised out at the side
    # and the fist cocked up beside the head
    'left_arm': {'carry': ((9.5, SY + 1.0, -5.0), (1, -.3, .3))},
    'right_arm': {'carry': GUARD_R},
    'left_leg': {'rot': (-25, 0, -9)}, 'left_shin': {'rot': (58, 0, 0)}, 'left_foot': {'rot': (16, 0, 0)},
    'right_leg': {'rot': (20, 0, 9)}, 'right_shin': {'rot': (50, 0, 0)}, 'right_foot': {'rot': (34, 0, 0)},
    **FIST,
}
POSES['hook'] = {
    'root': {'pos': (0, .5, -3.4)},
    'hips': {'rot': (16, 50, -5)}, 'torso': {'rot': (9, 18, -5)}, 'chest': {'rot': (7, 18, 0)},
    'neck': {'rot': (-8, -30, 0)}, 'head': {'rot': (-11, -46, 0)},
    # the body wrenched round to the right, the left arm whipped round level with the elbow out at the shoulder's
    # height and bent square, the fist smashing in across the front at the target
    'left_arm': {'carry': ((3.5, SY - .8 - DROP_HELD, -8.5), (1, .1, .4))},
    'right_arm': {'carry': GUARD_R},
    'left_leg': {'rot': (-33, 0, -4)}, 'left_shin': {'rot': (26, 0, 0)}, 'left_foot': {'rot': (10, 0, 0)},
    'right_leg': {'rot': (29, 0, 13)}, 'right_shin': {'rot': (28, 0, 0)}, 'right_foot': {'rot': (40, 0, 0)},
    **FIST,
}
POSES['finish_wind'] = {
    'root': {'pos': (0, 3.0, 2.8)},
    'hips': {'rot': (-11, 54, 0)}, 'torso': {'rot': (-9, 17, 0)}, 'chest': {'rot': (-7, 13, 0)},
    'neck': {'rot': (4, -31, 0)}, 'head': {'rot': (8, -43, 0)},
    # rearing up and back, wound right round, the right fist kept up high by the cheek and so carried far back with the
    # body (drawn back by the arm alone it went the far way round from the guard), the left arm out at the target taking
    # aim, the knees drawn up to spring
    'right_arm': {'carry': ((-2.9, SY + 1.9, -2.4), (-.6, -1, .25))},
    'left_arm': {'carry': ((3.6, SY + .6 - DROP, -14.0), (.6, -1, .1))},
    'left_leg': {'rot': (-50, 0, -6)}, 'left_shin': {'rot': (70, 0, 0)}, 'left_foot': {'rot': (16, 0, 0)},
    'right_leg': {'rot': (9, 0, 8)}, 'right_shin': {'rot': (72, 0, 0)}, 'right_foot': {'rot': (34, 0, 0)},
    **FIST,
}
POSES['finish'] = {
    'root': {'pos': (0, 0, -7.8)},
    'hips': {'rot': (27, -26, 0)}, 'torso': {'rot': (13, -12, 0)}, 'chest': {'rot': (9, -10, 0)},
    'neck': {'rot': (-14, 20, 0)}, 'head': {'rot': (-19, 26, 0)},
    # the whole body explodes forward, wheeling round to the left and flung far over the front leg, the right arm driven
    # straight through the target, the left fist pulled back in, low, the back leg flung out straight behind
    'right_arm': {'reach': ((0.0, SY - 4.4 - DROP, -36.0), (-.5, -1, 0))},
    'left_arm': {'carry': ((3.4, SY - .1, -8.0), (.6, -1, .25))},
    'left_leg': {'rot': (-52, 0, -4)}, 'left_shin': {'rot': (42, 0, 0)}, 'left_foot': {'rot': (0, 0, 0)},
    'right_leg': {'rot': (55, 0, 6)}, 'right_shin': {'rot': (0, 0, 0)}, 'right_foot': {'rot': (46, 0, 0)},
    **FIST,
}
M1 = ('jab_wind', 'jab', 'cross_wind', 'cross', 'hook_wind', 'hook', 'finish_wind', 'finish')


# ---------------------------------------------------------------- where things are in a stance
def placed(spec):
    """Every bone's place in a stance (4x4, model units, soles at 0), down from the root as the game's model moves it."""
    table = rig.pose_table(spec, GRIPS)
    world = {}
    for name, b in rig.BONES.items():
        world[name] = (world[b.parent] if b.parent else np.eye(4)) @ rig.bone_matrix(b, table.get(name, {}))
    return world


def skinned(spec):
    """The body in a stance: every vertex moved by its bones as their weights share it (linear blend skinning, as the
    game draws it: client/stand/GoldExperienceMesh)."""
    world = placed(spec)
    names, pos, bones, weights = BODY
    out = np.zeros_like(pos)
    p4 = np.concatenate([pos, np.ones((len(pos), 1))], 1)
    for c in range(4):
        for k in np.unique(bones[:, c]):
            if k == 255:
                continue
            sel = (bones[:, c] == k) & (weights[:, c] > 0)
            out[sel] += weights[sel, c:c + 1] * (p4[sel] @ world[names[k]].T)[:, :3]
    return out


def stance_points(spec):
    """Where each fist's knuckles and each shoulder are in a stance (model units, soles at 0)."""
    world = placed(spec)
    out = {}
    for side, p in KNUCKLES.items():
        q = world[side + '_hand'] @ np.array([*p, 1.0])
        out[side] = [round(float(v), 3) for v in q[:3]]
        q = world[side + '_arm'] @ np.array([*rig.BONES[side + '_arm'].pivot, 1.0])
        out[side + '_shoulder'] = [round(float(v), 3) for v in q[:3]]
    return out


HAND = ('right_hand', 'right_fingers', *('right_' + f + '_' + k for f in FINGERS for k in '12'))


def lowest(body, carriers=None):
    """The lowest point of the body, or of what the named bones carry (model units)."""
    sel = np.ones(len(body), bool) if carriers is None else np.isin(CARRIER, carriers)
    return body[sel][int(np.argmin(body[sel, 1]))]


def touches(spec):
    """(2.152.0) What of the stance touches the ground: the lowest of the palm, the knee down, the foot planted, and of it
    all (GoldExperienceModelTest holds them to the ground)."""
    body = skinned(spec)
    pick = {'palm': lowest(body, HAND), 'knee': lowest(body, ['left_shin']), 'sole': lowest(body, ['right_foot']),
            'lowest': lowest(body)}
    return {k: [round(float(v), 3) for v in p] for k, p in pick.items()}


def kneeling(spec):
    """(2.155.0) The kneeling stance on this body: its right knee placed so that foot stands flat where it stood before
    (out before it and to its right, its ankle as high off the ground as it is standing), its root lowered until the
    left knee touches the ground, and its right arm reaching down until the palm lies on it. The left leg's aims, the
    trunk's turns and where the palm goes along the ground are the stance's own."""
    spec = {k: dict(v) for k, v in spec.items()}
    leg = float(np.linalg.norm(PIVOT['right_shin'] - PIVOT['right_leg']))
    shin = float(np.linalg.norm(PIVOT['right_foot'] - PIVOT['right_shin']))
    # the box-built figure's right ankle from its hip in this stance (its thigh 9.7 and its shin 9.6 along their aims),
    # out and forward as far again for these longer legs
    then = norm(spec['right_leg']['aim'][0]) * 9.7 + norm(spec['right_shin']['aim'][0]) * 9.6
    ahead = np.array([then[0], 0.0, then[2]]) * (leg + shin) / (9.7 + 9.6)
    pole = norm(spec['right_leg']['aim'][0])
    for _ in range(6):
        world = placed(reaching(spec))
        hip = (world['right_leg'] @ np.array([*PIVOT['right_leg'], 1.0]))[:3]
        ankle = hip + ahead
        ankle[1] = PIVOT['right_foot'][1]
        d = ankle - hip
        dist = float(np.clip(np.linalg.norm(d), abs(leg - shin) + 1e-3, leg + shin - 1e-3))
        u = norm(d)
        v = norm(pole - (pole @ u) * u)
        a = (leg * leg - shin * shin + dist * dist) / (2 * dist)
        knee = hip + u * a + v * math.sqrt(max(0.0, leg * leg - a * a))
        spec['right_leg'] = {'aim': (tuple(knee - hip), spec['right_leg']['aim'][1])}
        spec['right_shin'] = {'aim': (tuple(hip + u * dist - knee), spec['right_shin']['aim'][1])}
        body = skinned(reaching(spec))
        drop = float(lowest(body, ['left_shin'])[1])
        root = list(spec['root']['pos'])
        root[1] -= drop
        spec['root'] = {'pos': tuple(root)}
        body = skinned(reaching(spec))
        palm = float(lowest(body, HAND)[1]) - .05
        target, pole_arm = spec['right_arm']['reach']
        spec['right_arm'] = {'reach': ((target[0], target[1] - palm, target[2]), pole_arm)}
    return spec


POSES['ground'] = kneeling(POSES['ground'])


def main():
    poses = json.dumps({k: rig.pose_table(reaching(v), GRIPS) for k, v in POSES.items()}, indent=1)
    # where the fists are in the stances that strike, for their light (client/stand/GoldRush)
    fists = json.dumps({k: {**stance_points(reaching(v)), **(touches(reaching(v)) if k == 'ground' else {})}
                        for k, v in POSES.items() if k == 'guard' or k == 'mend' or k == 'ground' or k.startswith('rush_') or k.startswith('over_') or k in M1}, indent=1)
    (ROOT / 'stands').mkdir(parents=True, exist_ok=True)
    (ROOT / 'stands/gold_experience_hq_poses.json').write_text(poses)
    (ROOT / 'stands/gold_experience_hq_fists.json').write_text(fists)
    g = POSES['ground']
    print(f'{len(rig.BONES)} bones, {len(POSES)} stances; kneeling: root {g["root"]["pos"]}, touches {touches(reaching(g))}')


if __name__ == '__main__':
    main()
