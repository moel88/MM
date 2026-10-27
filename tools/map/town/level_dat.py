"""Reads and writes a Minecraft level.dat (gzipped NBT) to set the world's display name and default game mode.

usage: python3 tools/map/town/level_dat.py WORLD_DIR "NAME" [GAMETYPE]   (GAMETYPE 0 survival, 1 creative, 2 adventure)
A small NBT reader and writer: every tag kept as read, only Data.LevelName and Data.GameType replaced.
"""
import gzip, struct, sys
from pathlib import Path

def read_string(b, i):
    n = struct.unpack('>H', b[i:i + 2])[0]
    return b[i + 2:i + 2 + n].decode('utf-8', 'surrogatepass'), i + 2 + n

def read_payload(t, b, i):
    if t == 1: return struct.unpack('>b', b[i:i + 1])[0], i + 1
    if t == 2: return struct.unpack('>h', b[i:i + 2])[0], i + 2
    if t == 3: return struct.unpack('>i', b[i:i + 4])[0], i + 4
    if t == 4: return struct.unpack('>q', b[i:i + 8])[0], i + 8
    if t == 5: return struct.unpack('>f', b[i:i + 4])[0], i + 4
    if t == 6: return struct.unpack('>d', b[i:i + 8])[0], i + 8
    if t == 7:
        n = struct.unpack('>i', b[i:i + 4])[0]; return bytes(b[i + 4:i + 4 + n]), i + 4 + n
    if t == 8: return read_string(b, i)
    if t == 9:
        et, n = b[i], struct.unpack('>i', b[i + 1:i + 5])[0]; i += 5; items = []
        for _ in range(n):
            v, i = read_payload(et, b, i); items.append(v)
        return (et, items), i
    if t == 10:
        out = []
        while True:
            tt = b[i]; i += 1
            if tt == 0: return out, i
            name, i = read_string(b, i)
            v, i = read_payload(tt, b, i)
            out.append([tt, name, v])
    if t == 11:
        n = struct.unpack('>i', b[i:i + 4])[0]; return list(struct.unpack('>%di' % n, b[i + 4:i + 4 + 4 * n])), i + 4 + 4 * n
    if t == 12:
        n = struct.unpack('>i', b[i:i + 4])[0]; return list(struct.unpack('>%dq' % n, b[i + 4:i + 4 + 8 * n])), i + 4 + 8 * n
    raise ValueError('tag %d' % t)

def write_string(s):
    raw = s.encode('utf-8', 'surrogatepass'); return struct.pack('>H', len(raw)) + raw

def write_payload(t, v):
    if t == 1: return struct.pack('>b', v)
    if t == 2: return struct.pack('>h', v)
    if t == 3: return struct.pack('>i', v)
    if t == 4: return struct.pack('>q', v)
    if t == 5: return struct.pack('>f', v)
    if t == 6: return struct.pack('>d', v)
    if t == 7: return struct.pack('>i', len(v)) + v
    if t == 8: return write_string(v)
    if t == 9:
        et, items = v; return bytes([et]) + struct.pack('>i', len(items)) + b''.join(write_payload(et, x) for x in items)
    if t == 10: return b''.join(bytes([tt]) + write_string(name) + write_payload(tt, x) for tt, name, x in v) + b'\0'
    if t == 11: return struct.pack('>i', len(v)) + struct.pack('>%di' % len(v), *v)
    if t == 12: return struct.pack('>i', len(v)) + struct.pack('>%dq' % len(v), *v)
    raise ValueError('tag %d' % t)

def patch(world, name, gametype=None):
    path = Path(world) / 'level.dat'
    b = gzip.decompress(path.read_bytes())
    assert b[0] == 10
    root_name, i = read_string(b, 1)
    root, _ = read_payload(10, b, i)
    data = next(v for t, n, v in root if n == 'Data')
    for entry in data:
        if entry[1] == 'LevelName': entry[2] = name
        if entry[1] == 'GameType' and gametype is not None: entry[2] = gametype
    out = bytes([10]) + write_string(root_name) + write_payload(10, root)
    path.write_bytes(gzip.compress(out))
    # read it back to be sure it parses and holds the change
    check, _ = read_payload(10, gzip.decompress(path.read_bytes()), 1 + 2 + len(root_name.encode()))
    d = next(v for t, n, v in check if n == 'Data')
    assert next(v for t, n, v in d if n == 'LevelName') == name
    return name

if __name__ == '__main__':
    print(patch(sys.argv[1], sys.argv[2], int(sys.argv[3]) if len(sys.argv) > 3 else None))
