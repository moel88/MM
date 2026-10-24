"""A small NBT reader/writer that keeps every tag's type, for editing level.dat (the world's name) without the game.

    root = load(path)              # ('', TAG_COMPOUND, {...}) as (name, tag, value)
    root[2]['Data'][1]             # -> the Data compound's value: {name: (tag, value)}
    save(path, root)
Compound values are dicts of name -> (tag, value); list values are (element tag, [values]).
"""
import gzip, struct

END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BYTES, STRING, LIST, COMPOUND, INTS, LONGS = range(13)
_FMT = {BYTE: '>b', SHORT: '>h', INT: '>i', LONG: '>q', FLOAT: '>f', DOUBLE: '>d'}


def _read(buf, i, tag):
    if tag in _FMT:
        f = _FMT[tag]; return struct.unpack_from(f, buf, i)[0], i + struct.calcsize(f)
    if tag == BYTES:
        n = struct.unpack_from('>i', buf, i)[0]; return bytes(buf[i + 4:i + 4 + n]), i + 4 + n
    if tag == STRING:
        n = struct.unpack_from('>H', buf, i)[0]; return buf[i + 2:i + 2 + n].decode('utf-8'), i + 2 + n
    if tag == LIST:
        t = buf[i]; n = struct.unpack_from('>i', buf, i + 1)[0]; i += 5; out = []
        for _ in range(n):
            v, i = _read(buf, i, t); out.append(v)
        return (t, out), i
    if tag == COMPOUND:
        out = {}
        while True:
            t = buf[i]; i += 1
            if t == END: return out, i
            n = struct.unpack_from('>H', buf, i)[0]; name = buf[i + 2:i + 2 + n].decode('utf-8'); i += 2 + n
            v, i = _read(buf, i, t); out[name] = (t, v)
    if tag in (INTS, LONGS):
        n = struct.unpack_from('>i', buf, i)[0]; f = '>%d%s' % (n, 'i' if tag == INTS else 'q'); size = 4 if tag == INTS else 8
        return list(struct.unpack_from(f, buf, i + 4)), i + 4 + size * n
    raise ValueError(f'bad tag {tag}')


def _write(out, tag, v):
    if tag in _FMT: out += struct.pack(_FMT[tag], v)
    elif tag == BYTES: out += struct.pack('>i', len(v)) + v
    elif tag == STRING:
        b = v.encode('utf-8'); out += struct.pack('>H', len(b)) + b
    elif tag == LIST:
        t, items = v
        out += struct.pack('>bi', t if items else (t or END), len(items))
        for item in items: _write(out, t, item)
    elif tag == COMPOUND:
        for name, (t, val) in v.items():
            b = name.encode('utf-8'); out += struct.pack('>bH', t, len(b)) + b; _write(out, t, val)
        out += b'\0'
    elif tag in (INTS, LONGS):
        out += struct.pack('>i', len(v)) + struct.pack('>%d%s' % (len(v), 'i' if tag == INTS else 'q'), *v)
    else: raise ValueError(f'bad tag {tag}')


def load(path):
    data = gzip.decompress(open(path, 'rb').read())
    t = data[0]; n = struct.unpack_from('>H', data, 1)[0]; name = data[3:3 + n].decode('utf-8')
    v, _ = _read(data, 3 + n, t)
    return name, t, v


def dump(root):
    name, t, v = root; out = bytearray()
    b = name.encode('utf-8'); out += struct.pack('>bH', t, len(b)) + b; _write(out, t, v)
    return bytes(out)


def save(path, root):
    with open(path, 'wb') as f:
        f.write(gzip.compress(dump(root), mtime=0))
