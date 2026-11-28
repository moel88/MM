"""A small reader for glTF-binary (.glb) files (2.155.0): accessors as numpy arrays, node transforms, skins.

It reads only what tools/stand/import_gold_experience.py needs: one buffer, triangles, skins. A .glb is a downloaded
file: the tools that read one are run with python3 -I, and nothing in it is ever run.
"""
import json
import struct

import numpy as np

COMP = {5120: np.int8, 5121: np.uint8, 5122: np.int16, 5123: np.uint16, 5125: np.uint32, 5126: np.float32}
WIDTH = {'SCALAR': 1, 'VEC2': 2, 'VEC3': 3, 'VEC4': 4, 'MAT2': 4, 'MAT3': 9, 'MAT4': 16}


class Glb:
    def __init__(self, path):
        data = open(path, 'rb').read()
        magic, version, length = struct.unpack_from('<III', data, 0)
        if magic != 0x46546C67 or version != 2:
            raise ValueError('not a glTF 2.0 binary: ' + str(path))
        off, chunks = 12, []
        while off + 8 <= min(len(data), length):
            clen, ctype = struct.unpack_from('<II', data, off)
            chunks.append((ctype, data[off + 8:off + 8 + clen]))
            off += 8 + clen
        if not chunks or chunks[0][0] != 0x4E4F534A:
            raise ValueError('no JSON chunk')
        self.json = json.loads(chunks[0][1].decode('utf-8'))
        self.bin = next((c for t, c in chunks[1:] if t == 0x004E4942), b'')
        self.nodes = self.json.get('nodes', [])
        self.parent = {}
        for i, n in enumerate(self.nodes):
            for c in n.get('children', []):
                self.parent[c] = i

    def view(self, index):
        bv = self.json['bufferViews'][index]
        start = bv.get('byteOffset', 0)
        return self.bin[start:start + bv['byteLength']], bv.get('byteStride')

    def accessor(self, index):
        a = self.json['accessors'][index]
        dtype = np.dtype(COMP[a['componentType']]).newbyteorder('<')
        width = WIDTH[a['type']]
        count = a['count']
        raw, stride = self.view(a['bufferView'])
        start = a.get('byteOffset', 0)
        item = dtype.itemsize * width
        if stride and stride != item:
            out = np.empty((count, width), dtype)
            for i in range(count):
                out[i] = np.frombuffer(raw, dtype, width, start + i * stride)
        else:
            out = np.frombuffer(raw, dtype, count * width, start).reshape(count, width).copy()
        if a.get('normalized'):
            out = out.astype(np.float64) / np.iinfo(dtype).max
        if a['type'] == 'MAT4':
            out = out.reshape(count, 4, 4).transpose(0, 2, 1)  # column-major in the file
        return out

    def image(self, index):
        im = self.json['images'][index]
        raw, _ = self.view(im['bufferView'])
        return raw, im.get('mimeType')

    def local(self, i):
        n = self.nodes[i]
        if 'matrix' in n:
            return np.array(n['matrix'], dtype=np.float64).reshape(4, 4).T
        return trs(np.array(n.get('translation', [0, 0, 0]), dtype=np.float64),
                   np.array(n.get('rotation', [0, 0, 0, 1]), dtype=np.float64),
                   np.array(n.get('scale', [1, 1, 1]), dtype=np.float64))

    def world(self, i):
        m = self.local(i)
        while i in self.parent:
            i = self.parent[i]
            m = self.local(i) @ m
        return m

    def primitive(self, mesh, prim=0):
        p = self.json['meshes'][mesh]['primitives'][prim]
        if p.get('mode', 4) != 4:
            raise ValueError('only triangles are read')
        out = {k: self.accessor(v) for k, v in p['attributes'].items()}
        out['indices'] = self.accessor(p['indices']).reshape(-1, 3).astype(np.int64)
        out['material'] = p.get('material')
        return out


def quat_matrix(q):
    x, y, z, w = q
    return np.array([
        [1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
        [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
        [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)]])


def trs(t, q, s):
    m = np.eye(4)
    m[:3, :3] = quat_matrix(q) @ np.diag(s)
    m[:3, 3] = t
    return m
