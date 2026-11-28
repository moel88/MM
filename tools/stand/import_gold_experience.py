"""Gold Experience's body from the model the user sent (2.155.0, user request: "여기서 골드익스피리언스만 추출해서 지금
골드익스피리언스를 대체 할 수 있어?"; asked and answered: its own thickness, its own abs, its own proportions).

usage: python3 -I tools/stand/import_gold_experience.py MODEL.glb [OUT]

The file (Sketchfab, "Giorno Giovanna & gold experience" by shamus, CC-BY 4.0: third_party/gold-experience-model) holds
two skinned figures on Mixamo skeletons sharing one texture: Giorno (Mesh_0113.rip, the lower half of the texture) and
Gold Experience (Mesh_0112.rip, the upper half). This takes Gold Experience alone and writes what the game draws it from:

  geo/entity/gold_experience.geo.json   its skeleton for GeckoLib: the bones the stances turn (tools/stand/
                                        make_gold_experience.py writes the stances), set at this body's joints, no cubes
  stands/gold_experience_mesh.bin       its body: every vertex where it is in the rest pose, its normal, its place on the
                                        texture and up to four bones with their weights (client/stand/GoldExperienceMesh)
  textures/entity/gold_experience.png   the texture's upper half (Gold Experience's)

The skeleton the game turns is the one the stances were made for (the rig's 2.145-2.154 bones, as Gold Experience's
model has had them, less its wings, rings and hide: this body has its shoulder pads, hooks and loincloth on its legs
and hips, as its own skin has them), with each finger's middle and last joints added to fold a fist. Its rest pose is the
game's: standing straight, the arms hanging straight down with the palms in and the thumbs forward, the legs straight
down, the feet as the model has them. The model is bound in a T-pose; its arms and legs are turned down at their joints
and the body is skinned into that pose (linear blend skinning, as the file's skin does it). Then it is set up in the
game's frame (soles at 0, the hips over the origin, the front towards -z, units of a sixteenth of a block, 40.49 to the
top of the helmet: Star Platinum's height to the top of its head at GoldExperienceRenderer.SCALE) and each of the file's
joints hands its weight to the game's bone it moves with.

Coordinates: the geo.json is written as Bedrock writes a model (x mirrored: the right side at -x), the mesh as GeckoLib
bakes the geo.json (x the right way, in blocks), so a vertex sits on the cube grid of its bone.
"""
import io
import json
import re
import struct
import sys
from pathlib import Path

import numpy as np
from PIL import Image

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import glb  # noqa: E402

MODEL = Path(sys.argv[1]) if len(sys.argv) > 1 else None
ROOT = Path(sys.argv[2]) if len(sys.argv) > 2 else HERE.parents[1] / 'src/main/resources/assets/celestialfall'
FIGURE = 'Mesh_0112.rip'          # the figure that is Gold Experience (the other is Giorno)
HEIGHT = 40.49                     # soles to the top of the helmet, units
FINGERS = ('index', 'middle', 'ring', 'pinky')
SIDES = (('right', 'Right'), ('left', 'Left'))


def norm(v):
    v = np.asarray(v, dtype=np.float64)
    return v / np.linalg.norm(v)


def between(a, b):
    """The smallest turn taking direction a to direction b."""
    a, b = norm(a), norm(b)
    v, c = np.cross(a, b), float(a @ b)
    if np.linalg.norm(v) < 1e-9:
        if c > 0:
            return np.eye(3)
        p = norm(np.cross(a, (1, 0, 0)) if abs(a[0]) < .9 else np.cross(a, (0, 1, 0)))
        return 2 * np.outer(p, p) - np.eye(3)
    k = np.array([[0, -v[2], v[1]], [v[2], 0, -v[0]], [-v[1], v[0], 0]])
    return np.eye(3) + k + k @ k / (1 + c)


def joint_name(node):
    """A joint's name without Mixamo's prefix and the number the export put after it (mixamorig:LeftArm_075 -> LeftArm)."""
    return re.sub(r'_\d+$', '', node.replace('mixamorig:', ''))


def bone_of(joint):
    """The game's bone a joint of the file's skeleton moves with."""
    for side, mix in SIDES:
        if not joint.startswith(mix):
            continue
        part = joint[len(mix):]
        simple = {'Shoulder': 'chest', 'Arm': side + '_arm', 'ForeArm': side + '_forearm', 'Hand': side + '_hand',
                  'UpLeg': side + '_leg', 'Leg': side + '_shin', 'Foot': side + '_foot', 'ToeBase': side + '_foot',
                  'Toe_End': side + '_foot', 'Toe_End_end': side + '_foot'}
        if part in simple:
            return simple[part]
        if part.startswith('HandThumb'):
            return side + '_hand'
        for f in FINGERS:
            if part.startswith('Hand' + f.capitalize()):
                n = int(''.join(ch for ch in part[len('Hand' + f.capitalize()):] if ch.isdigit())[:1])
                return side + '_fingers' if n == 1 else side + '_' + f + '_' + ('1' if n == 2 else '2')
        raise KeyError(joint)
    return {'Hips': 'hips', 'Spine': 'torso', 'Spine1': 'torso', 'Spine2': 'chest', 'Neck': 'neck', 'Head': 'head',
            'HeadTop_End': 'head', 'HeadTop_End_end': 'head', '_rootJoint': 'hips'}[joint]


def bones():
    """The game's skeleton: (name, parent, the file's joint(s) whose rest place is its pivot), parents first."""
    out = [('root', None, None), ('hips', 'root', 'Hips'), ('torso', 'hips', 'Spine'), ('chest', 'torso', 'Spine2'),
           ('neck', 'chest', 'Neck'), ('head', 'neck', 'Head')]
    for side, mix in SIDES:
        out += [(side + '_arm', 'chest', mix + 'Arm'), (side + '_forearm', side + '_arm', mix + 'ForeArm'),
                (side + '_hand', side + '_forearm', mix + 'Hand'),
                (side + '_fingers', side + '_hand', tuple(mix + 'Hand' + f.capitalize() + '1' for f in FINGERS))]
        for f in FINGERS:
            out += [(side + '_' + f + '_1', side + '_fingers', mix + 'Hand' + f.capitalize() + '2'),
                    (side + '_' + f + '_2', side + '_' + f + '_1', mix + 'Hand' + f.capitalize() + '3')]
    for side, mix in SIDES:
        out += [(side + '_leg', 'hips', mix + 'UpLeg'), (side + '_shin', side + '_leg', mix + 'Leg'),
                (side + '_foot', side + '_shin', mix + 'Foot')]
    return out


def main():
    if MODEL is None:
        sys.exit(__doc__)
    g = glb.Glb(MODEL)
    mesh = next(i for i, m in enumerate(g.json['meshes']) if m.get('name', '').startswith(FIGURE))
    node = next(i for i, n in enumerate(g.nodes) if n.get('mesh') == mesh)
    skin = g.json['skins'][g.nodes[node]['skin']]
    p = g.primitive(mesh)
    pos, nor, uv = p['POSITION'].astype(np.float64), p['NORMAL'].astype(np.float64), p['TEXCOORD_0'].astype(np.float64)
    joints, weights = p['JOINTS_0'].astype(np.int64), p['WEIGHTS_0'].astype(np.float64)
    weights /= weights.sum(1, keepdims=True)
    tri = p['indices']
    if uv[:, 1].max() > .5 + 1e-4:
        raise ValueError('Gold Experience lies on the upper half of the texture')

    names = [joint_name(g.nodes[j]['name']) for j in skin['joints']]
    ibm = g.accessor(skin['inverseBindMatrices']).astype(np.float64)
    index = {n: k for k, n in enumerate(names)}
    up_of = {k: index.get(joint_name(g.nodes[g.parent[j]]['name'])) if j in g.parent else None
             for k, j in enumerate(skin['joints'])}
    kids = {k: [c for c, q in up_of.items() if q == k] for k in range(len(names))}

    # ---- the T-pose it is bound in, and the game's rest pose
    bind = np.array([np.linalg.inv(m) for m in ibm])
    G = bind.copy()

    def under(k):
        out, todo = [], [k]
        while todo:
            q = todo.pop()
            out.append(q)
            todo += kids[q]
        return out

    def turn(k, R):
        piv = G[k][:3, 3].copy()
        M = np.eye(4)
        M[:3, :3] = R
        M[:3, 3] = piv - R @ piv
        for q in under(k):
            G[q] = M @ G[q]

    def at(n):
        return G[index[n]][:3, 3]

    up = np.array([0.0, 1.0, 0.0])
    left = at('LeftUpLeg') - at('RightUpLeg')
    left = norm(left - up * (left @ up))
    fwd = np.cross(left, up)
    down = -up
    # the trunk upright from side to side (it leans a little to its right as bound; its curve front to back stays): each
    # piece of the spine, up to the head, turned in the body's front plane until it stands straight up in it
    for joint, child in (('Spine', 'Spine1'), ('Spine1', 'Spine2'), ('Spine2', 'Neck'), ('Neck', 'Head')):
        d = at(child) - at(joint)
        turn(index[joint], between(d, d - left * (d @ left)))
    for side, mix in SIDES:
        for joint, child in ((mix + 'UpLeg', mix + 'Leg'), (mix + 'Leg', mix + 'Foot')):
            turn(index[joint], between(at(child) - at(joint), down))
        # the foot as the model has it (its sole and heel), wherever the leg has brought its ankle
        f = index[mix + 'Foot']
        turn(f, bind[f][:3, :3] @ G[f][:3, :3].T)
        for joint, child in ((mix + 'Arm', mix + 'ForeArm'), (mix + 'ForeArm', mix + 'Hand'), (mix + 'Hand', mix + 'HandMiddle1')):
            turn(index[joint], between(at(child) - at(joint), down))
        # the palm square to the side, the fingers' knuckles in a line from front to back (the fist folds about it)
        h = index[mix + 'Hand']
        across = at(mix + 'HandIndex1') - at(mix + 'HandPinky1')
        across = across - down * (across @ down)
        turn(h, between(across, fwd))

    skinning = np.einsum('kij,kjl->kil', G, ibm)
    p4 = np.concatenate([pos, np.ones((len(pos), 1))], 1)
    rest = np.zeros((len(pos), 3))
    rnor = np.zeros((len(pos), 3))
    for c in range(4):
        m = skinning[joints[:, c]]
        rest += weights[:, c:c + 1] * np.einsum('nij,nj->ni', m, p4)[:, :3]
        rnor += weights[:, c:c + 1] * np.einsum('nij,nj->ni', m[:, :3, :3], nor)
    rnor /= np.linalg.norm(rnor, axis=1, keepdims=True)

    # ---- into the game's frame: x the left side, y up from the soles, z the front; units
    middle = (at('LeftUpLeg') + at('RightUpLeg')) / 2
    floor = float((rest @ up).min())
    unit = HEIGHT / float((rest @ up).max() - floor)

    def body(v):
        d = np.asarray(v, dtype=np.float64) - middle
        return np.stack([d @ left, (np.asarray(v) @ up) - floor, d @ fwd], -1) * unit

    rb = body(rest)
    nb = np.stack([rnor @ left, rnor @ up, rnor @ fwd], -1)

    # ---- the skeleton: each bone at its joint, both sides alike, the trunk on the middle
    skeleton = bones()
    piv = {}
    for name, parent, source in skeleton:
        if source is None:
            piv[name] = np.zeros(3)
        elif isinstance(source, tuple):
            piv[name] = np.mean([body(at(s)) for s in source], 0)
        else:
            piv[name] = body(at(source))
    for name in piv:
        if name.startswith('right'):
            twin = 'left' + name[5:]
            a, b = piv[name], piv[twin]
            mean = (np.array([-a[0], a[1], a[2]]) + b) / 2
            piv[twin], piv[name] = mean, np.array([-mean[0], mean[1], mean[2]])
        elif not name.startswith('left'):
            piv[name][0] = 0.0
    piv['root'] = np.zeros(3)

    # ---- the weights, joint by joint handed to the game's bones; at most four a vertex
    order = [n for n, _, _ in skeleton]
    slot = {n: k for k, n in enumerate(order)}
    of = [slot[bone_of(n)] for n in names]
    vb = np.zeros((len(pos), 4), np.int64)
    vw = np.zeros((len(pos), 4))
    for i in range(len(pos)):
        acc = {}
        for c in range(4):
            if weights[i, c] > 0:
                b = of[joints[i, c]]
                acc[b] = acc.get(b, 0.0) + weights[i, c]
        top = sorted(acc.items(), key=lambda t: -t[1])[:4]
        total = sum(w for _, w in top)
        for c, (b, w) in enumerate(top):
            vb[i, c], vw[i, c] = b, w / total

    # ---- write: the skeleton (Bedrock's mirrored x), the body (GeckoLib's baked x, blocks), the texture's upper half
    geo_bones = []
    for name, parent, _ in skeleton:
        x, y, z = piv[name]
        entry = {'name': name, 'pivot': [round(float(x), 4), round(float(y), 4), round(float(-z), 4)]}
        if parent:
            entry['parent'] = parent
        geo_bones.append(entry)
    raw, _ = g.image(0)
    tex = Image.open(io.BytesIO(raw)).convert('RGB')
    half = tex.crop((0, 0, tex.width, tex.height // 2))
    geo = {'format_version': '1.12.0', 'minecraft:geometry': [{
        'description': {'identifier': 'geometry.botand.gold_experience', 'texture_width': half.width,
                        'texture_height': half.height, 'visible_bounds_width': 5, 'visible_bounds_height': 4,
                        'visible_bounds_offset': [0, 1.5, 0]},
        'bones': geo_bones}]}
    for d in ('geo/entity', 'textures/entity', 'stands'):
        (ROOT / d).mkdir(parents=True, exist_ok=True)
    (ROOT / 'geo/entity/gold_experience.geo.json').write_text(json.dumps(geo, indent=1))
    half.save(ROOT / 'textures/entity/gold_experience.png', optimize=True)

    out = io.BytesIO()
    out.write(b'GEM1')
    out.write(struct.pack('>i', len(order)))
    for n in order:
        b = n.encode('ascii')
        out.write(struct.pack('>H', len(b)) + b)
    out.write(struct.pack('>i', len(pos)))
    for i in range(len(pos)):
        x, y, z = rb[i]
        nx, ny, nz = nb[i]
        # GeckoLib's baked model: x the right way round (the right side at +x), z towards the back, blocks
        out.write(struct.pack('>8f', -x / 16, y / 16, -z / 16, -nx, ny, -nz, uv[i, 0], uv[i, 1] * 2))
        out.write(struct.pack('>4B', *[int(b) if w > 0 else 255 for b, w in zip(vb[i], vw[i])]))
        out.write(struct.pack('>4f', *vw[i]))
    out.write(struct.pack('>i', len(tri)))
    for a, b, c in tri:
        out.write(struct.pack('>3i', int(a), int(b), int(c)))
    (ROOT / 'stands/gold_experience_mesh.bin').write_bytes(out.getvalue())

    lo, hi = rb.min(0), rb.max(0)
    print(f'{len(pos)} vertices, {len(tri)} triangles, {len(order)} bones, texture {half.width}x{half.height}; '
          f'{hi[1]:.2f} units tall, {hi[0] - lo[0]:.2f} across, {hi[2] - lo[2]:.2f} deep')
    for n in ('hips', 'torso', 'chest', 'neck', 'head', 'left_arm', 'left_forearm', 'left_hand', 'left_fingers',
              'left_index_1', 'left_leg', 'left_shin', 'left_foot'):
        print(f'  {n:14s} {np.round(piv[n], 2).tolist()}')


if __name__ == '__main__':
    main()
