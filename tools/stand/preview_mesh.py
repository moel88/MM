"""A quick look at Gold Experience's body (2.155.0) in a stance, for working on its import or its stances (not the game:
the game's own render is what checks a model, through the shots tools).

usage: python3 tools/stand/preview_mesh.py OUT.png [STANCE] [YAW,...] [ROOT]

It reads the skeleton (geo/entity/gold_experience_hq.geo.json), the body (stands/gold_experience_hq_mesh.bin), its texture
and the stances (stands/gold_experience_hq_poses.json) under ROOT (the game's resources by default), turns the bones as
the generator's rig does (tools/stand/rig.py: as GeckoLib does) and skins the body by them, then draws it from the
front (yaw 0, its left side on the right of the picture), from its left (90), from behind (180) and so on: an
orthographic view, every texel as the texture has it, lit from one side. Grey lines every 8 units up from the soles.
"""
import json
import struct
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parent))
import rig  # noqa: E402


def load(path):
    data = Path(path).read_bytes()
    off = 4
    (n,) = struct.unpack_from('>i', data, off)
    off += 4
    names = []
    for _ in range(n):
        (k,) = struct.unpack_from('>H', data, off)
        names.append(data[off + 2:off + 2 + k].decode('ascii'))
        off += 2 + k
    (verts,) = struct.unpack_from('>i', data, off)
    off += 4
    pos, nor, uv = np.zeros((verts, 3)), np.zeros((verts, 3)), np.zeros((verts, 2))
    bones, weights = np.zeros((verts, 4), np.int64), np.zeros((verts, 4))
    for i in range(verts):
        v = struct.unpack_from('>8f4B4f', data, off)
        off += 52
        pos[i], nor[i], uv[i], bones[i], weights[i] = v[0:3], v[3:6], v[6:8], v[8:12], v[12:16]
    (tris,) = struct.unpack_from('>i', data, off)
    off += 4
    tri = np.array(struct.unpack_from('>%di' % (3 * tris), data, off)).reshape(-1, 3)
    return names, pos, nor, uv, bones, weights, tri


def posed(root, stance):
    """The body skinned by a stance, in a frame facing +z with its left side at +x (model units)."""
    geo = json.loads((root / 'geo/entity/gold_experience_hq.geo.json').read_text())['minecraft:geometry'][0]
    rig.BONES.clear()
    for b in geo['bones']:
        rig.bone(b['name'], b.get('parent'), b['pivot'], b.get('rotation', (0, 0, 0)))
    world = {}
    for name, b in rig.BONES.items():
        world[name] = (world[b.parent] if b.parent else np.eye(4)) @ rig.bone_matrix(b, stance.get(name, {}))
    names, pos, nor, uv, bones, weights, tri = load(root / 'stands/gold_experience_hq_mesh.bin')
    g, gn = pos * np.array([-16, 16, 16.0]), nor * np.array([-1, 1, 1.0])
    out, on = np.zeros_like(g), np.zeros_like(g)
    for c in range(4):
        for k in np.unique(bones[:, c]):
            if k == 255:
                continue
            sel = bones[:, c] == k
            m = world[names[k]]
            out[sel] += weights[sel, c:c + 1] * (np.c_[g[sel], np.ones(sel.sum())] @ m.T)[:, :3]
            on[sel] += weights[sel, c:c + 1] * (gn[sel] @ m[:3, :3].T)
    on /= np.maximum(np.linalg.norm(on, axis=1, keepdims=True), 1e-9)
    return out * np.array([1, 1, -1]), on * np.array([1, 1, -1]), uv, tri


def draw(pos, nor, uv, tri, tex, yaw, size, centre, scale):
    w, h = size
    y = np.radians(yaw)
    r = np.array([[np.cos(y), 0, np.sin(y)], [0, 1, 0], [-np.sin(y), 0, np.cos(y)]])
    p, n = pos @ r.T, nor @ r.T
    sx, sy, sz = (p[:, 0] - centre[0]) * scale + w / 2, h / 2 - (p[:, 1] - centre[1]) * scale, p[:, 2]
    img = np.zeros((h, w, 3))
    img[:] = (238, 240, 246)
    depth = np.full((h, w), -1e9)
    th, tw = tex.shape[:2]
    light = np.array([.3, .6, 1.0]) / np.linalg.norm([.3, .6, 1.0])
    for a, b, c in tri:
        xs, ys = (sx[a], sx[b], sx[c]), (sy[a], sy[b], sy[c])
        x0, x1 = int(max(0, np.floor(min(xs)))), int(min(w - 1, np.ceil(max(xs))))
        y0, y1 = int(max(0, np.floor(min(ys)))), int(min(h - 1, np.ceil(max(ys))))
        den = (ys[1] - ys[2]) * (xs[0] - xs[2]) + (xs[2] - xs[1]) * (ys[0] - ys[2])
        if x0 > x1 or y0 > y1 or abs(den) < 1e-12:
            continue
        gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + .5, np.arange(y0, y1 + 1) + .5)
        l0 = ((ys[1] - ys[2]) * (gx - xs[2]) + (xs[2] - xs[1]) * (gy - ys[2])) / den
        l1 = ((ys[2] - ys[0]) * (gx - xs[2]) + (xs[0] - xs[2]) * (gy - ys[2])) / den
        l2 = 1 - l0 - l1
        z = l0 * sz[a] + l1 * sz[b] + l2 * sz[c]
        sub = depth[y0:y1 + 1, x0:x1 + 1]
        take = (l0 >= -1e-6) & (l1 >= -1e-6) & (l2 >= -1e-6) & (z > sub)
        if not take.any():
            continue
        u = l0 * uv[a, 0] + l1 * uv[b, 0] + l2 * uv[c, 0]
        v = l0 * uv[a, 1] + l1 * uv[b, 1] + l2 * uv[c, 1]
        col = tex[np.clip((v * th).astype(int), 0, th - 1), np.clip((u * tw).astype(int), 0, tw - 1)].astype(float)
        nn = l0[..., None] * n[a] + l1[..., None] * n[b] + l2[..., None] * n[c]
        nn /= np.maximum(np.linalg.norm(nn, axis=-1, keepdims=True), 1e-9)
        col *= (.55 + .45 * np.abs(nn @ light))[..., None]
        sub[take] = z[take]
        img[y0:y1 + 1, x0:x1 + 1][take] = col[take]
    return np.clip(img, 0, 255).astype(np.uint8)


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    out = sys.argv[1]
    root = Path(sys.argv[4]) if len(sys.argv) > 4 else Path(__file__).resolve().parents[2] / 'src/main/resources/assets/celestialfall'
    stance = {}
    if len(sys.argv) > 2 and sys.argv[2] not in ('', 'rest'):
        stance = json.loads((root / 'stands/gold_experience_hq_poses.json').read_text())[sys.argv[2]]
    yaws = [float(a) for a in (sys.argv[3] if len(sys.argv) > 3 else '0,90,180,270').split(',')]
    pos, nor, uv, tri = posed(root, stance)
    tex = np.asarray(Image.open(root / 'textures/entity/gold_experience_hq.png').convert('RGB'))
    size, centre = (360, 560), np.array([0, 21, 0])
    scale = .92 * size[1] / 46.0
    img = Image.fromarray(np.concatenate([draw(pos, nor, uv, tri, tex, y, size, centre, scale) for y in yaws], 1))
    d = ImageDraw.Draw(img)
    for yy in range(0, 41, 8):
        sy = size[1] / 2 - (yy - centre[1]) * scale
        d.line([(0, sy), (img.width, sy)], fill=(205, 205, 215))
    img.save(out)


if __name__ == '__main__':
    main()
