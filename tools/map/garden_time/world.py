"""Reads a Java 1.20.1 world's overworld blocks into a NumPy volume (time garden tooling, map v1.4).

    w = World(folder, y0=60, y1=200)
    w.state(x, y, z)        -> 'minecraft:oak_stairs[facing=east,half=bottom,...]'
    w.name(x, y, z)         -> 'minecraft:oak_stairs'
    w.v[y - w.y0, z - w.z0, x - w.x0]  -> palette index (w.palette[i] is the state string)

Only what the decorator needs: block states, block entities' positions, the chunks present. Nothing is written back
here; the decorator places its blocks through a real server (see run_decorate.py).
"""
import struct, zlib, gzip
from pathlib import Path
import numpy as np


def _nbt(buf, i, tag):
    if tag == 1: return struct.unpack_from('>b', buf, i)[0], i + 1
    if tag == 2: return struct.unpack_from('>h', buf, i)[0], i + 2
    if tag == 3: return struct.unpack_from('>i', buf, i)[0], i + 4
    if tag == 4: return struct.unpack_from('>q', buf, i)[0], i + 8
    if tag == 5: return struct.unpack_from('>f', buf, i)[0], i + 4
    if tag == 6: return struct.unpack_from('>d', buf, i)[0], i + 8
    if tag == 7:
        n = struct.unpack_from('>i', buf, i)[0]; return bytes(buf[i + 4:i + 4 + n]), i + 4 + n
    if tag == 8:
        n = struct.unpack_from('>H', buf, i)[0]; return buf[i + 2:i + 2 + n].decode('utf-8', 'replace'), i + 2 + n
    if tag == 9:
        t = buf[i]; n = struct.unpack_from('>i', buf, i + 1)[0]; i += 5; out = []
        for _ in range(n):
            v, i = _nbt(buf, i, t); out.append(v)
        return out, i
    if tag == 10:
        out = {}
        while True:
            t = buf[i]; i += 1
            if t == 0: return out, i
            n = struct.unpack_from('>H', buf, i)[0]; name = buf[i + 2:i + 2 + n].decode('utf-8', 'replace'); i += 2 + n
            v, i = _nbt(buf, i, t); out[name] = v
    if tag == 11:
        n = struct.unpack_from('>i', buf, i)[0]; return np.frombuffer(buf, '>i4', n, i + 4).astype(np.int32), i + 4 + 4 * n
    if tag == 12:
        n = struct.unpack_from('>i', buf, i)[0]; return np.frombuffer(buf, '>i8', n, i + 4).astype(np.int64), i + 4 + 8 * n
    raise ValueError(f'bad NBT tag {tag} at {i}')


def read_nbt(data):
    """The root compound of an uncompressed NBT blob."""
    n = struct.unpack_from('>H', data, 1)[0]
    return _nbt(data, 3 + n, 10)[0]


def read_level(path):
    return read_nbt(gzip.decompress(Path(path).read_bytes()))


def chunks(region_dir):
    """Every chunk's root compound, from all region files."""
    for region in sorted(Path(region_dir).glob('r.*.mca')):
        data = region.read_bytes()
        if len(data) < 8192: continue
        for k in range(1024):
            off = int.from_bytes(data[4 * k:4 * k + 3], 'big') * 4096
            if off == 0: continue
            length = struct.unpack_from('>i', data, off)[0]; comp = data[off + 4]
            raw = data[off + 5:off + 4 + length]
            body = zlib.decompress(raw) if comp == 2 else gzip.decompress(raw) if comp == 1 else raw
            yield read_nbt(body)


def state_string(entry):
    props = entry.get('Properties') or {}
    name = entry['Name']
    return name + ('[' + ','.join(f'{k}={props[k]}' for k in sorted(props)) + ']' if props else '')


def unpack(longs, bits, count=4096):
    per = 64 // bits
    u = longs.view(np.uint64)
    shifts = (np.arange(per, dtype=np.uint64) * np.uint64(bits))
    vals = (u[:, None] >> shifts[None, :]) & np.uint64((1 << bits) - 1)
    return vals.reshape(-1)[:count].astype(np.int64)


class World:
    def __init__(self, folder, y0=60, y1=200):
        self.folder = Path(folder)
        self.y0, self.y1 = y0, y1
        self.palette = ['minecraft:air']; self.index = {'minecraft:air': 0}
        cs = list(chunks(self.folder / 'region'))
        xs = [c['xPos'] for c in cs]; zs = [c['zPos'] for c in cs]
        self.cx0, self.cz0 = min(xs), min(zs)
        self.x0, self.z0 = self.cx0 * 16, self.cz0 * 16
        self.W = (max(xs) - self.cx0 + 1) * 16; self.D = (max(zs) - self.cz0 + 1) * 16; self.H = y1 - y0
        self.v = np.zeros((self.H, self.D, self.W), np.uint16)
        self.present = set(); self.block_entities = []
        for c in cs:
            cx, cz = c['xPos'], c['zPos']; self.present.add((cx, cz))
            ox, oz = cx * 16 - self.x0, cz * 16 - self.z0
            for be in c.get('block_entities') or []:
                self.block_entities.append(be)
            for sec in c.get('sections') or []:
                sy = sec['Y'] * 16
                if sy + 16 <= y0 or sy >= y1: continue
                bs = sec.get('block_states') or {}
                pal = [self._id(state_string(e)) for e in bs.get('palette') or [{'Name': 'minecraft:air'}]]
                if 'data' in bs and len(pal) > 1:
                    bits = max(4, int(np.ceil(np.log2(len(pal)))))
                    ids = np.asarray(pal, np.uint16)[unpack(bs['data'], bits)].reshape(16, 16, 16)
                else:
                    ids = np.full((16, 16, 16), pal[0], np.uint16)
                lo, hi = max(sy, y0), min(sy + 16, y1)
                self.v[lo - y0:hi - y0, oz:oz + 16, ox:ox + 16] = ids[lo - sy:hi - sy]
        self.names = np.array([s.split('[')[0] for s in self.palette])

    def _id(self, s):
        i = self.index.get(s)
        if i is None:
            i = len(self.palette); self.palette.append(s); self.index[s] = i
        return i

    def inside(self, x, y, z):
        return self.x0 <= x < self.x0 + self.W and self.z0 <= z < self.z0 + self.D and self.y0 <= y < self.y1

    def state(self, x, y, z):
        if not self.inside(x, y, z): return 'minecraft:air'
        return self.palette[self.v[y - self.y0, z - self.z0, x - self.x0]]

    def name(self, x, y, z):
        return self.state(x, y, z).split('[')[0]

    def mask(self, *names):
        """A boolean volume of where any of the named blocks are."""
        ids = [i for i, n in enumerate(self.names) if n in names]
        return np.isin(self.v, ids)

    def top(self):
        """Highest non-air block's y for every column (y0 - 1 where the column is empty)."""
        solid = self.v != 0
        h = self.H - 1 - np.argmax(solid[::-1], axis=0)
        h[~solid.any(axis=0)] = -1
        return h + self.y0


def read_patch(path):
    """A Botand garden patch (.bgp.gz, map/GardenPatch.java): its palette and an int array of rows x, y, z, before, after."""
    import json
    data = gzip.decompress(Path(path).read_bytes())
    if struct.unpack_from('>i', data, 0)[0] != 0x42475031: raise ValueError('not a garden patch')
    n = struct.unpack_from('>i', data, 4)[0]
    header = json.loads(data[8:8 + n].decode('utf-8'))
    cells = np.frombuffer(data, '>i2', header['cells'] * 5, 8 + n).astype(np.int32).reshape(-1, 5)
    cells[:, 3:] &= 0xFFFF
    return header['palette'], cells
