"""The rig and the atlas a stand's model generator builds with (2.145.0, Gold Experience): bones, boxes, chains of
bones, where every texel of every face sits on the body in the rest pose, the packing of the faces into one texture,
the geo.json GeckoLib reads and the stances' turns. It is tools/stand/make_star_platinum.py's own machinery, lifted out
so that the next stand's generator does not carry a copy (Star Platinum's script keeps its own: its files are not to
change).

Units are sixteenths of a block, y up from the soles, the front towards -z (north) and the right side towards -x, as in
every Bedrock/GeckoLib model (the game mirrors x when it bakes it). Every face has its own place on the texture
(per-face UV).

How GeckoLib lays a face's texture (per-face UV, from its BakedModelFactory and GeoQuad), in model space:
  north (z min)  x = x0 + u*sx   y = y1 - v*sy        south (z max)  x = x1 - u*sx   y = y1 - v*sy
  east (x min)   z = z1 - u*sz   y = y1 - v*sy        west (x max)   z = z0 + u*sz   y = y1 - v*sy
  up (y max)     x = x0 + u*sx   z = z1 - v*sz        down (y min)   x = x0 + u*sx   z = z0 + v*sz
and it turns a bone or cube by Rz(-rz) . Ry(ry) . Rx(-rx) about its pivot (degrees, model space), so a positive rx
tips something standing up towards the front and a positive rz towards the left (+x).
"""
import math

import numpy as np

DENS = 8            # texels a unit, unless a cube says otherwise (dens=)
ATLAS_W = 1024
ALL = ('north', 'south', 'east', 'west', 'up', 'down')


def hexc(h):
    h = h.lstrip('#')
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], dtype=np.float64) / 255


def mix(a, b, t):
    return a + (b - a) * t


def ramp(cols, t):
    """A cel-shaded tone: t in 0..1 picks one of the colours (no blending: crisp bands like the art)."""
    return cols[int(np.clip(t, 0, .9999) * len(cols))]


def noise(x, y, z, seed=0):
    """Small deterministic value noise in -1..1 (grain)."""
    v = math.sin(x * 12.9898 + y * 78.233 + z * 37.719 + seed * 4.1) * 43758.5453
    return (v - math.floor(v)) * 2 - 1


def norm(v):
    v = np.asarray(v, dtype=np.float64)
    return v / np.linalg.norm(v)


def pillow(u, v, kx=1.0, ky=1.0):
    """1 in the middle of a face falling towards its edges: a rounded surface."""
    return 1 - (kx * (2 * u - 1) ** 2 + ky * (2 * v - 1) ** 2) * .5


# ---------------------------------------------------------------- the rig
class Bone:
    def __init__(self, name, parent, pivot, rot=(0, 0, 0)):
        self.name, self.parent, self.pivot, self.rot = name, parent, list(pivot), list(rot)


class Cube:
    def __init__(self, bone, origin, size, mat, rot=None, pivot=None, inflate=0.0, opts=None, faces=None):
        self.bone, self.origin, self.size, self.mat = bone, list(origin), list(size), mat
        self.rot, self.pivot, self.inflate = rot, pivot, inflate
        self.opts = dict(opts or {})
        self.faces = faces or ALL


BONES, CUBES = {}, []


def bone(name, parent, pivot, rot=(0, 0, 0)):
    BONES[name] = Bone(name, parent, pivot, rot)
    return name


def box(b, origin, size, mat, **opts):
    kw = {k: opts.pop(k) for k in ('rot', 'pivot', 'inflate', 'faces') if k in opts}
    c = Cube(b, origin, size, mat, opts=opts, **kw)
    CUBES.append(c)
    return c


def rounded(b, origin, size, mat, notch, **opts):
    """A box with its four long edges stepped in (two crossed boxes, one narrower and one shallower): a limb or a body
    that reads round. notch is how far each edge steps in (x, z)."""
    (x, y, z), (w, h, d) = origin, size
    nx, nz = (notch, notch) if np.isscalar(notch) else notch
    box(b, (x, y, z + nz), (w, h, d - 2 * nz), mat, **opts)
    # its ends a hair inside the other's, so the two never draw the same square
    return box(b, (x + nx, y + .02, z), (w - 2 * nx, h - .04, d), mat, **opts)


def mirror_name(n):
    return n.replace('right', 'left') if 'right' in n else n.replace('left', 'right')


def mirrored(build):
    """Builds a part on the right side, then its mirror on the left: x -> -x, rotations (rx, -ry, -rz)."""
    first = len(CUBES)
    names_before = set(BONES)
    build()
    new_bones = [n for n in BONES if n not in names_before]
    for n in new_bones:
        b = BONES[n]
        m = mirror_name(n)
        BONES[m] = Bone(m, mirror_name(b.parent) if b.parent in new_bones else b.parent,
                        [-b.pivot[0], b.pivot[1], b.pivot[2]], [b.rot[0], -b.rot[1], -b.rot[2]])
    for c in CUBES[first:len(CUBES)]:
        o, s = c.origin, c.size
        opts = dict(c.opts)
        opts['side'] = -opts.get('side', -1)
        if 'at' in opts:
            opts['at'] = (-opts['at'][0], opts['at'][1], opts['at'][2])
        faces = tuple({'east': 'west', 'west': 'east'}.get(f, f) for f in c.faces)
        CUBES.append(Cube(mirror_name(c.bone) if c.bone in new_bones else c.bone, [-(o[0] + s[0]), o[1], o[2]], s, c.mat,
                          rot=None if c.rot is None else [c.rot[0], -c.rot[1], -c.rot[2]],
                          pivot=None if c.pivot is None else [-c.pivot[0], c.pivot[1], c.pivot[2]],
                          inflate=c.inflate, opts=opts, faces=faces))


def rot_matrix(r):
    rx, ry, rz = (math.radians(a) for a in r)
    cx, sx = math.cos(-rx), math.sin(-rx)
    cy, sy = math.cos(ry), math.sin(ry)
    cz, sz = math.cos(-rz), math.sin(-rz)
    Rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    Ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    Rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return Rz @ Ry @ Rx


def euler_of(R):
    """The (rx, ry, rz) for which rot_matrix gives R (R = Rz(a) Ry(b) Rx(c) with a = -rz, b = ry, c = -rx)."""
    b = math.asin(float(np.clip(-R[2, 0], -1, 1)))
    a = math.atan2(R[1, 0], R[0, 0])
    c = math.atan2(R[2, 1], R[2, 2])
    return [-math.degrees(c), math.degrees(b), -math.degrees(a)]


def rest_transform(name):
    """A bone's rest transform as (R, t): a point p of its unturned frame sits at R p + t."""
    R, t = np.eye(3), np.zeros(3)
    chain = []
    b = BONES[name]
    while b is not None:
        chain.append(b)
        b = BONES[b.parent] if b.parent else None
    for b in reversed(chain):          # root first: world = T_root(T_child(p))
        Rb = rot_matrix(b.rot)
        piv = np.array(b.pivot, dtype=np.float64)
        t = R @ (piv - Rb @ piv) + t
        R = R @ Rb
    return R, t


def strand(prefix, parent, points, widths, thicks, normal_hint, mat, **opts):
    """A chain of bones, one a piece, through points (rest pose, model space): each piece a box standing along its
    bone's +y from the bone's pivot, its width across x and its thickness along z, z turned towards normal_hint.
    normal_hint is a vector or a function of the piece's index."""
    R_prev, t_prev = rest_transform(parent)
    names = []
    par = parent
    total = sum(np.linalg.norm(np.subtract(points[k + 1], points[k])) for k in range(len(points) - 1))
    along = 0.0
    for k in range(len(points) - 1):
        P0, P1 = np.array(points[k], dtype=np.float64), np.array(points[k + 1], dtype=np.float64)
        d = P1 - P0
        L = float(np.linalg.norm(d))
        y = d / L
        nh = norm(normal_hint(k) if callable(normal_hint) else normal_hint)
        z = nh - (nh @ y) * y
        if np.linalg.norm(z) < 1e-6:
            z = np.array([0, 0, 1.0]) - y[2] * y
        z = norm(z)
        x = np.cross(y, z)
        Rk = np.column_stack([x, y, z])
        Rrel = R_prev.T @ Rk
        q = R_prev.T @ (P0 - t_prev)
        name = f'{prefix}_{k}' if len(points) > 2 else prefix
        bone(name, par, q, euler_of(Rrel))
        w, th = widths[k], thicks[k]
        sink = .35 if k > 0 else .6     # each piece starts a little inside the one before
        over = .2 if k < len(points) - 2 else 0
        box(name, (q[0] - w / 2, q[1] - sink, q[2] - th / 2), (w, L + sink + over, th), mat,
            piece=k, pieces=len(points) - 1, start=along / total, length=total, seg=L, sink=sink, **opts)
        along += L
        t_prev = R_prev @ (q - Rrel @ q) + t_prev
        R_prev = R_prev @ Rrel
        par = name
        names.append(name)
    return names


def slerp(a, b, t):
    a, b = norm(a), norm(b)
    om = math.acos(float(np.clip(a @ b, -1, 1)))
    if om < 1e-4:
        return a
    return (math.sin((1 - t) * om) * a + math.sin(t * om) * b) / math.sin(om)


def rotate_about(v, axis, deg):
    axis = norm(axis)
    a = math.radians(deg)
    return v * math.cos(a) + np.cross(axis, v) * math.sin(a) + axis * (axis @ v) * (1 - math.cos(a))


# ---------------------------------------------------------------- where each texel sits
def extents(c):
    o, s, i = np.array(c.origin, dtype=np.float64), np.array(c.size, dtype=np.float64), c.inflate
    return o - i, o + s + i


FACE_DIMS = {'north': (0, 1), 'south': (0, 1), 'east': (2, 1), 'west': (2, 1), 'up': (0, 2), 'down': (0, 2)}
NORMALS = {'north': (0, 0, -1), 'south': (0, 0, 1), 'east': (-1, 0, 0), 'west': (1, 0, 0), 'up': (0, 1, 0), 'down': (0, -1, 0)}


def face_point(c, face, u, v):
    """The point at (u, v) of a face (0..1 each) in the cube's own unturned frame."""
    lo, hi = extents(c)
    x0, y0, z0 = lo
    x1, y1, z1 = hi
    sx, sy, sz = hi - lo
    if face == 'north':
        return np.array([x0 + u * sx, y1 - v * sy, z0])
    if face == 'south':
        return np.array([x1 - u * sx, y1 - v * sy, z1])
    if face == 'east':
        return np.array([x0, y1 - v * sy, z1 - u * sz])
    if face == 'west':
        return np.array([x1, y1 - v * sy, z0 + u * sz])
    if face == 'up':
        return np.array([x0 + u * sx, y1, z1 - v * sz])
    return np.array([x0 + u * sx, y0, z0 + v * sz])


def to_rest(c, p, n):
    """A point and normal of a cube in the model's rest pose (its own turn, then its bones' up to the root)."""
    if c.rot is not None:
        R = rot_matrix(c.rot)
        piv = np.array(c.pivot)
        p, n = piv + R @ (p - piv), R @ n
    R, t = rest_transform(c.bone)
    return R @ p + t, R @ n


def dens(c):
    return c.opts.get('dens', DENS)


def face_size(c, face):
    lo, hi = extents(c)
    a, b = FACE_DIMS[face]
    return (hi - lo)[a], (hi - lo)[b]


def edge_texels(c, face, u, v):
    """How many texels a point lies from the nearest edge of its face."""
    w, h = face_size(c, face)
    return min(u * w, (1 - u) * w, v * h, (1 - v) * h) * dens(c)


# ---------------------------------------------------------------- the atlas
def tex_size(c, face):
    w, h = face_size(c, face)
    d = dens(c)
    return max(1, int(math.ceil(w * d - 1e-6))), max(1, int(math.ceil(h * d - 1e-6)))


def pack():
    items = []
    for ci, c in enumerate(CUBES):
        for f in c.faces:
            tw, th = tex_size(c, f)
            items.append((th, tw, ci, f))
    items.sort(key=lambda t: (-t[0], -t[1]))
    x = y = shelf = 0
    place = {}
    for th, tw, ci, f in items:
        if x + tw + 1 > ATLAS_W:
            x, y, shelf = 0, y + shelf + 1, 0
        place[(ci, f)] = (x, y, tw, th)
        x += tw + 1
        shelf = max(shelf, th)
    height = int(math.ceil((y + shelf + 1) / 64) * 64)
    return place, height


def paint(place, height, painters, own=()):
    """The texture: every texel of every face asked of its material's painter(cube, face, u, v, p, n), p and n the
    texel's place and facing on the body in the rest pose (in the cube's own frame for the materials in `own`: what is
    painted along itself, a chain's piece or a badge). A painter returning None leaves the texel clear."""
    img = np.zeros((height, ATLAS_W, 4), dtype=np.float64)
    for (ci, f), (x0, y0, tw, th) in place.items():
        c = CUBES[ci]
        painter = painters[c.mat]
        n0 = np.array(NORMALS[f], dtype=np.float64)
        for j in range(th):
            for i in range(tw):
                u, v = (i + .5) / tw, (j + .5) / th
                p_local = face_point(c, f, u, v)
                p_rest, n_rest = to_rest(c, p_local, n0)
                col = painter(c, f, u, v, p_local if c.mat in own else p_rest, n_rest)
                if col is not None:
                    img[y0 + j, x0 + i, :3] = np.clip(col, 0, 1)
                    img[y0 + j, x0 + i, 3] = 1
    return (img * 255 + .5).astype(np.uint8)


def check_coplanar():
    """Faces of different cubes lying in the same plane, facing the same way and overlapping would flicker."""
    rects = []
    for ci, c in enumerate(CUBES):
        if c.rot is not None or any(abs(a) > 1e-9 for a in rest_transform(c.bone)[0].flatten() - np.eye(3).flatten()):
            continue
        lo, hi = extents(c)
        for f in c.faces:
            axis = {'north': 2, 'south': 2, 'east': 0, 'west': 0, 'up': 1, 'down': 1}[f]
            val = lo[axis] if f in ('north', 'east', 'down') else hi[axis]
            others = [a for a in range(3) if a != axis]
            rects.append((axis, f, round(float(val), 4), [(lo[a], hi[a]) for a in others], ci))
    bad = []
    for i in range(len(rects)):
        for j in range(i + 1, len(rects)):
            a, b = rects[i], rects[j]
            if a[0] != b[0] or a[1] != b[1] or a[2] != b[2] or a[4] == b[4]:
                continue
            if all(min(a[3][k][1], b[3][k][1]) - max(a[3][k][0], b[3][k][0]) > 1e-3 for k in range(2)):
                bad.append((a[1], a[2], CUBES[a[4]].opts.get('part'), CUBES[b[4]].opts.get('part')))
    return bad


def geo(place, height, identifier, bounds=(5, 4, (0, 1.5, 0))):
    bones = []
    for name, b in BONES.items():
        entry = {'name': name, 'pivot': [round(float(v), 4) for v in b.pivot]}
        if b.parent:
            entry['parent'] = b.parent
        if any(abs(a) > 1e-6 for a in b.rot):
            entry['rotation'] = [round(float(v), 3) for v in b.rot]
        cubes = []
        for ci, c in enumerate(CUBES):
            if c.bone != name:
                continue
            cube = {'origin': [round(float(v), 4) for v in c.origin], 'size': [round(float(v), 4) for v in c.size]}
            if c.inflate:
                cube['inflate'] = c.inflate
            if c.rot is not None:
                cube['rotation'] = [round(float(v), 3) for v in c.rot]
                cube['pivot'] = [round(float(v), 4) for v in c.pivot]
            cube['uv'] = {f: {'uv': [place[(ci, f)][0], place[(ci, f)][1]], 'uv_size': [place[(ci, f)][2], place[(ci, f)][3]]} for f in c.faces}
            cubes.append(cube)
        if cubes:
            entry['cubes'] = cubes
        bones.append(entry)
    return {'format_version': '1.12.0', 'minecraft:geometry': [{
        'description': {'identifier': identifier, 'texture_width': ATLAS_W, 'texture_height': height,
                        'visible_bounds_width': bounds[0], 'visible_bounds_height': bounds[1], 'visible_bounds_offset': list(bounds[2])},
        'bones': bones}]}


# ---------------------------------------------------------------- stances
def frame(down, front):
    """A bone's turn in the model that sends its -y along down and its -z along front (made square to down)."""
    y = -norm(down)
    f = norm(front)
    z = norm(-(f - (f @ -y) * -y))
    x = np.cross(y, z)
    return np.column_stack([x, y, z])


def bone_matrix(b, entry):
    """A bone's own move in a stance (4x4): its offset, then its turn about its pivot."""
    turn = np.eye(4)
    turn[:3, :3] = rot_matrix([r + d for r, d in zip(b.rot, entry.get('rot', (0, 0, 0)))])
    move, to, back = np.eye(4), np.eye(4), np.eye(4)
    move[:3, 3] = entry.get('pos', (0, 0, 0))
    to[:3, 3] = b.pivot
    back[:3, 3] = -np.array(b.pivot, dtype=np.float64)
    return move @ to @ turn @ back


def reach(spec, side, placed, target, pole, grips):
    """An arm reaching its grip (grips[side]: a point of the hand in the rest pose) to target (model units) with its elbow
    out towards pole: the upper arm aimed at the elbow, folding towards the hand, and the forearm bent at the elbow as a
    hinge (no twist)."""
    arm, fore = BONES[side + '_arm'], BONES[side + '_forearm']
    parent = placed[arm.parent]
    shoulder = (parent @ np.array([*arm.pivot, 1.0]))[:3]
    upper = float(np.linalg.norm(np.subtract(fore.pivot, arm.pivot)))
    lower = float(np.linalg.norm(np.subtract(grips[side], fore.pivot)))
    d = np.array(target, dtype=np.float64) - shoulder
    dist = float(np.clip(np.linalg.norm(d), abs(upper - lower) + 1e-3, upper + lower - 1e-3))
    u = norm(d)
    pole = np.array(pole, dtype=np.float64)
    side_way = pole - (pole @ u) * u
    v = norm(side_way) if np.linalg.norm(side_way) > 1e-6 else norm(np.cross(u, (1, 0, 0)))
    a = (upper * upper - lower * lower + dist * dist) / (2 * dist)
    h = math.sqrt(max(0.0, upper * upper - a * a))
    elbow = shoulder + u * a + v * h
    down = norm(elbow - shoulder)
    to_hand = shoulder + u * dist - elbow
    fold = to_hand - (to_hand @ down) * down
    front = norm(fold) if np.linalg.norm(fold) > 1e-6 else v
    bend = math.degrees(math.acos(float(np.clip(norm(to_hand) @ down, -1, 1))))
    spec[side + '_arm'] = {**{k: v2 for k, v2 in spec.get(side + '_arm', {}).items() if k != 'reach'}, 'aim': (tuple(down), tuple(front))}
    spec[side + '_forearm'] = {**spec.get(side + '_forearm', {}), 'rot': (-bend, 0, 0)}


def pose_table(spec, grips=None):
    """The stance as each bone's turn (degrees, Blockbench's way: added to the bone's own) and offset. A bone can be
    aimed instead ("aim": the way its length runs from its joint to the next, and the way its front faces), and an arm can
    reach ("reach": where its grip goes, and where its elbow points); those turns are worked out down the rig."""
    spec = dict(spec)
    world, placed, out = {}, {}, {}
    for name, b in BONES.items():
        parent = world[b.parent] if b.parent else np.eye(3)
        if name.endswith('_arm') and 'reach' in spec.get(name, {}):
            reach(spec, name[:-4], placed, *spec[name]['reach'], grips)
        entry = spec.get(name, {})
        if 'aim' in entry:
            delta = [d - r for d, r in zip(euler_of(parent.T @ frame(*entry['aim'])), b.rot)]
        else:
            delta = list(entry.get('rot', (0, 0, 0)))
        world[name] = parent @ rot_matrix([r + d for r, d in zip(b.rot, delta)])
        placed[name] = (placed[b.parent] if b.parent else np.eye(4)) @ bone_matrix(b, {**entry, 'rot': delta})
        if any(abs(d) > 1e-6 for d in delta) or 'pos' in entry:
            out[name] = {'rot': [round(float(d), 3) for d in delta]}
            if 'pos' in entry:
                out[name]['pos'] = [float(v) for v in entry['pos']]
    return out
