"""A quick look at a GeckoLib geo.json and its texture, for working on a model (not the game: the game's own render is
what checks a model, through the shots tools).

usage: python3 tools/stand/preview_geo.py MODEL.geo.json TEXTURE.png OUT.png [yaw,pitch[,centre y,distance];...] [size] [POSES.json:STANCE]

It places every cube as GeckoLib bakes it (x mirrored, per-face UV), turns bones and cubes about their pivots, casts a
ray through every pixel at each face and lights the faces the way Minecraft lights entities (two lights, 0.4 ambient).
Yaw 0 looks at the model's front; positive yaw walks round to its left.
"""
import json
import math
import sys

import numpy as np
from PIL import Image


def rot_matrix(r):
    """A bone's or cube's turn in model space (degrees), as GeckoLib applies it: Rz(-rz) . Ry(ry) . Rx(-rx)."""
    rx, ry, rz = (math.radians(a) for a in r)
    cx, sx = math.cos(-rx), math.sin(-rx)
    cy, sy = math.cos(ry), math.sin(ry)
    cz, sz = math.cos(-rz), math.sin(-rz)
    Rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    Ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    Rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return Rz @ Ry @ Rx


def faces_of(geo, pose=None):
    """Every face as (corner, edge u, edge v, normal, uv rect), x mirrored as in the game: in the rest pose, or in a
    stance (bone -> {"rot": degrees added to the bone's turn, "pos": offset in units}) as the game's model sets it."""
    g = geo['minecraft:geometry'][0]
    bones = {b['name']: b for b in g['bones']}
    pose = pose or {}
    out = []

    def chain(name):
        b = bones[name]
        extra = pose.get(name, {})
        R = rot_matrix([r + d for r, d in zip(b.get('rotation', (0, 0, 0)), extra.get('rot', (0, 0, 0)))])
        piv = np.array(b['pivot'], dtype=np.float64)
        off = np.array(extra.get('pos', (0, 0, 0)), dtype=np.float64)
        rest = (lambda p, R=R, piv=piv, off=off: off + piv + R @ (p - piv)), (lambda n, R=R: R @ n)
        if 'parent' in b:
            up_p, up_n = chain(b['parent'])
            return (lambda p: up_p(rest[0](p))), (lambda n: up_n(rest[1](n)))
        return rest

    for b in g['bones']:
        tp, tn = chain(b['name'])
        for c in b.get('cubes', []):
            inf = c.get('inflate', 0)
            o = np.array(c['origin'], dtype=np.float64) - inf
            s = np.array(c['size'], dtype=np.float64) + 2 * inf
            x0, y0, z0 = o
            x1, y1, z1 = o + s
            if 'rotation' in c:
                R = rot_matrix(c['rotation'])
                piv = np.array(c['pivot'], dtype=np.float64)
                cp, cn = (lambda p, R=R, piv=piv: piv + R @ (p - piv)), (lambda n, R=R: R @ n)
            else:
                cp, cn = (lambda p: p), (lambda n: n)
            # (u=0,v=0) corner, the point at u=1, the point at v=1, normal
            spec = {
                'north': ((x0, y1, z0), (x1, y1, z0), (x0, y0, z0), (0, 0, -1)),
                'south': ((x1, y1, z1), (x0, y1, z1), (x1, y0, z1), (0, 0, 1)),
                'east': ((x0, y1, z1), (x0, y1, z0), (x0, y0, z1), (-1, 0, 0)),
                'west': ((x1, y1, z0), (x1, y1, z1), (x1, y0, z0), (1, 0, 0)),
                'up': ((x0, y1, z1), (x1, y1, z1), (x0, y1, z0), (0, 1, 0)),
                'down': ((x0, y0, z0), (x1, y0, z0), (x0, y0, z1), (0, -1, 0)),
            }
            for f, uv in c['uv'].items():
                p0, pu, pv, n = (np.array(a, dtype=np.float64) for a in spec[f])
                P0, PU, PV, N = tp(cp(p0)), tp(cp(pu)), tp(cp(pv)), tn(cn(n))
                m = np.array([-1.0, 1, 1])     # the game mirrors x when it bakes the model
                out.append((P0 * m, (PU - P0) * m, (PV - P0) * m, N * m, uv['uv'], uv['uv_size']))
    return out


def render(faces, tex, yaw, pitch, size=(600, 800), center=(0, 21, 0), dist=150.0, fov=26.0):
    W, H = size
    th, tw = tex.shape[:2]
    cy, sy = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cp, sp = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))
    # the camera on a sphere round the model: yaw 0 in front of it (-z), its left side is -x once mirrored, a positive
    # pitch looks down from above
    eye = np.array(center, dtype=np.float64) + dist * np.array([-sy * cp, sp, -cy * cp])
    fwd = np.array(center, dtype=np.float64) - eye
    fwd /= np.linalg.norm(fwd)
    right = np.cross(fwd, [0, 1, 0])
    right /= np.linalg.norm(right)
    up = np.cross(right, fwd)
    f = 1 / math.tan(math.radians(fov) / 2)
    js, iis = np.mgrid[0:H, 0:W]
    sxn = (iis + .5 - W / 2) / (H / 2)
    syn = (H / 2 - js - .5) / (H / 2)
    dirs = fwd[None, None] * f + right[None, None] * sxn[..., None] + up[None, None] * syn[..., None]
    depth = np.full((H, W), np.inf)
    rgb = np.zeros((H, W, 3))
    rgb[:] = (.93, .94, .97)
    # Minecraft's entity lights in the world, brought into the model's frame (it faces south: turned 180 degrees)
    L0 = np.array([0.2, 1.0, -0.7]); L0 /= np.linalg.norm(L0)
    L1 = np.array([-0.2, 1.0, 0.7]); L1 /= np.linalg.norm(L1)
    L0, L1 = L0 * [-1, 1, -1], L1 * [-1, 1, -1]

    def project(p):
        d = p - eye
        z = d @ fwd
        return (d @ right) / z * f * (H / 2) + W / 2, H / 2 - (d @ up) / z * f * (H / 2)

    for P0, EU, EV, N, uv, uvs in faces:
        corners = [P0, P0 + EU, P0 + EV, P0 + EU + EV]
        pts = [project(c) for c in corners]
        xs, ys = [p[0] for p in pts], [p[1] for p in pts]
        i0, i1 = max(0, int(math.floor(min(xs)))), min(W, int(math.ceil(max(xs))) + 1)
        j0, j1 = max(0, int(math.floor(min(ys)))), min(H, int(math.ceil(max(ys))) + 1)
        if i0 >= i1 or j0 >= j1:
            continue
        n = np.cross(EU, EV)
        nn = np.linalg.norm(n)
        if nn < 1e-9:
            continue
        n /= nn
        D = dirs[j0:j1, i0:i1]
        denom = D @ n
        with np.errstate(divide='ignore', invalid='ignore'):
            t = ((P0 - eye) @ n) / denom
        hit = eye + D * t[..., None] - P0
        # solve hit = u EU + v EV (least squares on the face's plane)
        G = np.array([[EU @ EU, EU @ EV], [EU @ EV, EV @ EV]])
        Gi = np.linalg.inv(G)
        a, b = hit @ EU, hit @ EV
        u = Gi[0, 0] * a + Gi[0, 1] * b
        v = Gi[1, 0] * a + Gi[1, 1] * b
        ok = (t > 0) & (u >= 0) & (u < 1) & (v >= 0) & (v < 1) & (t < depth[j0:j1, i0:i1])
        if not ok.any():
            continue
        tx = np.clip((uv[0] + u * uvs[0]).astype(int), 0, tw - 1)
        ty = np.clip((uv[1] + v * uvs[1]).astype(int), 0, th - 1)
        texel = tex[ty, tx]
        ok &= texel[..., 3] > 25
        if not ok.any():
            continue
        light = min(1.0, .4 + .6 * (max(0, N @ L0) + max(0, N @ L1)))
        sub_d = depth[j0:j1, i0:i1]
        sub_c = rgb[j0:j1, i0:i1]
        sub_d[ok] = t[ok]
        sub_c[ok] = texel[ok][:, :3] / 255 * light
    return (np.clip(rgb, 0, 1) * 255 + .5).astype(np.uint8)


def main():
    geo_path, tex_path, out = sys.argv[1:4]
    views = [tuple(float(a) for a in v.split(',')) for v in (sys.argv[4] if len(sys.argv) > 4 else '0,0;35,0;90,0;180,0').split(';')]
    size = tuple(int(a) for a in (sys.argv[5] if len(sys.argv) > 5 else '480x640').split('x'))
    geo = json.load(open(geo_path))
    tex = np.array(Image.open(tex_path).convert('RGBA'))
    pose = None
    if len(sys.argv) > 6:
        file, name = sys.argv[6].rsplit(':', 1)
        pose = json.load(open(file))[name]
    faces = faces_of(geo, pose)
    tiles = [render(faces, tex, v[0], v[1], size, center=(0, v[2] if len(v) > 2 else 21, 0), dist=v[3] if len(v) > 3 else 150.0)
             for v in views]
    Image.fromarray(np.concatenate(tiles, axis=1)).save(out)
    print(out, len(faces), 'faces')


if __name__ == '__main__':
    main()
