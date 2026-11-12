#!/usr/bin/env python3
"""Draws the knife's clips as stick figures, without the game: for whoever writes a clip to look at its poses and arcs
quickly. Its pictures are NOT game screens and are never sent as a check of the game (that is filmed in the client).

  BOTAND_CLIP_DUMP=DIR ./gradlew --offline test --tests '*ClipPreviewDump*'     writes DIR/hit_N.csv
  python3 -I tools/xchara/preview_clip.py DIR OUT.png HIT [STEP [FROM TO [COLUMNS]]]   draws hit N every STEP seconds
  (tools/xchara/clip_numbers.py prints the same clips as numbers)

Each cell is one moment, seen from the side (the user's right: they face right), from behind and above as the game's
camera sees them, and from above (ahead is up). The knife is the thick dark line (its heel marked), its arm red, the
other arm blue, the right leg dark red, the left dark blue; the thin grey line is the path of the knife's point over
the whole clip, the thin orange one the fist's. A cell on a blow has a red frame.
"""
import csv
import math
import sys

from PIL import Image, ImageDraw

NAMES = ['head', 'sR', 'eR', 'hR', 'sL', 'eL', 'hL', 'hipR', 'kR', 'fR', 'hipL', 'kL', 'fL', 'rod', 'heel', 'tip']
W, H, S = 330, 280, 50          # a view's size (pixels) and scale (pixels a block)


def load(path):
    rows = []
    with open(path) as f:
        head = f.readline().strip().split(',')
        meta = {k: v for k, v in (h.split('=') for h in head if '=' in h)}
        for line in f:
            c = line.strip().split(',')
            row = {'t': float(c[0]), 'yaw': float(c[1]), 'pitch': float(c[2]), 'roll': float(c[3])}
            for name, v in zip(NAMES, c[4:]):
                row[name] = tuple(float(x) for x in v.split())
            rows.append(row)
    return rows, meta


def side(p):        # from the user's right: ahead to the right of the picture
    return 95 + p[2] * S, H - 22 - p[1] * S


def top(p):         # from above: ahead up the picture, the user's right to the right
    return W / 2 + p[0] * S * .85, H - 75 - p[2] * S * .85


CAM, LOOK, F = (0.0, 2.7, -4.6), (0.0, 0.9, 1.0), 270.0


def behind(p):      # the game's camera: behind and above, looking ahead and down
    fx, fy, fz = LOOK[0] - CAM[0], LOOK[1] - CAM[1], LOOK[2] - CAM[2]
    n = math.sqrt(fx * fx + fy * fy + fz * fz)
    fx, fy, fz = fx / n, fy / n, fz / n
    rx, ry, rz = fz, 0.0, -fx                     # right = forward x up (up = y), for x to the user's right
    n = math.hypot(rx, rz)
    rx, rz = rx / n, rz / n
    ux, uy, uz = ry * fz - rz * fy, rz * fx - rx * fz, rx * fy - ry * fx
    ux, uy, uz = -ux, -uy, -uz
    dx, dy, dz = p[0] - CAM[0], p[1] - CAM[1], p[2] - CAM[2]
    depth = dx * fx + dy * fy + dz * fz
    return W / 2 + F * (dx * rx + dy * ry + dz * rz) / depth, H / 2 - F * (dx * ux + dy * uy + dz * uz) / depth + 30


def figure(view, row, rows, blow):
    im = Image.new('RGB', (W, H), (250, 250, 250))
    d = ImageDraw.Draw(im)

    def pt(p):
        return view(p)

    # the ground, and three blocks ahead
    if view is side:
        d.line([pt((0, 0, -1.6)), pt((0, 0, 4.4))], fill=(170, 170, 170))
        d.line([pt((0, 0, 3)), pt((0, .15, 3))], fill=(170, 170, 170))
    elif view is top:
        d.line([pt((0, 0, -.8)), pt((0, 0, 3.6))], fill=(215, 215, 215))
        d.line([pt((-.15, 0, 3)), pt((.15, 0, 3))], fill=(170, 170, 170))
    else:
        for z in (0, 1, 2, 3):
            d.line([pt((-1.5, 0, z)), pt((1.5, 0, z))], fill=(215, 215, 215))
        d.line([pt((0, 0, 0)), pt((0, 0, 3))], fill=(200, 200, 200))
    d.line([pt(r['tip']) for r in rows], fill=(185, 185, 195))
    d.line([pt(r['hR']) for r in rows], fill=(245, 190, 120))

    def seg(a, b, colour, width):
        d.line([pt(row[a]), pt(row[b])], fill=colour, width=width)

    order = [('hipR', 'kR', (150, 40, 40), 4), ('kR', 'fR', (150, 40, 40), 4), ('hipL', 'kL', (40, 60, 150), 4), ('kL', 'fL', (40, 60, 150), 4)]
    for a, b, c, w in order:
        seg(a, b, c, w)
    mid_s = tuple((row['sR'][i] + row['sL'][i]) / 2 for i in range(3))
    mid_h = tuple((row['hipR'][i] + row['hipL'][i]) / 2 for i in range(3))
    d.line([pt(mid_s), pt(mid_h)], fill=(30, 30, 30), width=5)
    seg('sR', 'sL', (30, 30, 30), 4)
    seg('hipR', 'hipL', (30, 30, 30), 3)
    seg('sL', 'eL', (40, 90, 230), 4)
    seg('eL', 'hL', (40, 90, 230), 4)
    seg('sR', 'eR', (230, 50, 50), 4)
    seg('eR', 'hR', (230, 50, 50), 4)
    x, y = pt(row['head'])
    d.ellipse([x - 9, y - 9, x + 9, y + 9], outline=(30, 30, 30), width=2)
    seg('rod', 'tip', (70, 70, 80), 5)
    x, y = pt(row['heel'])
    d.ellipse([x - 3, y - 3, x + 3, y + 3], fill=(255, 210, 0))
    x, y = pt(row['tip'])
    d.ellipse([x - 3, y - 3, x + 3, y + 3], fill=(255, 255, 255), outline=(70, 70, 80))
    d.rectangle([0, 0, W - 1, H - 1], outline=(230, 30, 30) if blow else (225, 225, 225), width=2 if blow else 1)
    return im


def main():
    folder, out, hit = sys.argv[1], sys.argv[2], int(sys.argv[3])
    step = float(sys.argv[4]) if len(sys.argv) > 4 else .05
    rows, meta = load(f'{folder}/hit_{hit}.csv')
    t0 = float(sys.argv[5]) if len(sys.argv) > 5 else 0
    t1 = float(sys.argv[6]) if len(sys.argv) > 6 else rows[-1]['t']
    blows = [float(b) for b in meta.get('blows', '').split('/') if b]
    times = []
    t = t0
    while t <= t1 + 1e-6:
        times.append(t)
        t += step
    cols = int(sys.argv[7]) if len(sys.argv) > 7 else 2
    lines = (len(times) + cols - 1) // cols
    sheet = Image.new('RGB', (cols * W * 3, lines * (H + 18)), (250, 250, 250))
    d = ImageDraw.Draw(sheet)
    for i, t in enumerate(times):
        row = min(rows, key=lambda r: abs(r['t'] - t))
        ox, oy = (i % cols) * W * 3, (i // cols) * (H + 18)
        blow = any(abs(t - b) < step * .5 for b in blows)
        for k, view in enumerate((side, behind, top)):
            sheet.paste(figure(view, row, rows, blow), (ox + k * W, oy + 18))
        d.text((ox + 6, oy + 3), f'hit {hit}  t={t:.3f}  turn {row["yaw"]:.0f} lean {row["pitch"]:.0f}' + ('  BLOW' if blow else ''), fill=(200, 30, 30) if blow else (40, 40, 40))
    sheet.save(out)
    print(out, sheet.size, 'blows', blows, 'step', meta.get('step'), 'hold', meta.get('hold'), 'length', rows[-1]['t'])


main()
