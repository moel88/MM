#!/usr/bin/env python3
"""Numbers of the knife's clips, without the game: for whoever writes a clip to check it before it is filmed.

  BOTAND_CLIP_DUMP=DIR ./gradlew --offline test --tests '*ClipPreviewDump*'     writes DIR/hit_N.csv
  python3 -I tools/xchara/clip_numbers.py DIR HIT [STEP [FROM TO]]              prints hit N every STEP seconds

Per moment: the body's turn and lean, the fist (right, up, ahead of where its user stands), the way the blade points
(elevation, azimuth right of the front) and the arm's (shoulder to hand), how far apart they are (under 15 degrees the
hand's turn is undefined), where the blade's line crosses three blocks ahead (right, up) and whether the blade reaches
there (yes) or its point is short of it or its heel past it, the point's height, the point's speed (blocks a second)
and how far the edge leads (the cosine of the point's travel with the edge's way: the hand holds the knife with its
edge on the arm's side), where each foot stands (right, ahead) and the hips (ahead, up). A row on a blow is starred.
What to look for: a swing quickest as it lands and its wind-up slower; the edge leading (near 1) from as it is let go
until past the blow; the blade on the middle line at a body's height on the blow; feet that keep their place while the
body moves over them; nothing under the ground.
"""
import csv, math, sys
NAMES = ['head', 'sR', 'eR', 'hR', 'sL', 'eL', 'hL', 'hipR', 'kR', 'fR', 'hipL', 'kL', 'fL', 'rod', 'heel', 'tip']


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


def sub(a, b): return tuple(x - y for x, y in zip(a, b))
def dot(a, b): return sum(x * y for x, y in zip(a, b))
def norm(a):
    n = math.sqrt(dot(a, a)) or 1e-9
    return tuple(x / n for x in a)
def ea(v):
    v = norm(v)
    return math.degrees(math.asin(max(-1, min(1, v[1])))), math.degrees(math.atan2(v[0], v[2]))


def main():
    folder, hit = sys.argv[1], int(sys.argv[2])
    step = float(sys.argv[3]) if len(sys.argv) > 3 else .025
    rows, meta = load(f'{folder}/hit_{hit}.csv')
    t0 = float(sys.argv[4]) if len(sys.argv) > 4 else 0
    t1 = float(sys.argv[5]) if len(sys.argv) > 5 else rows[-1]['t']
    blows = [float(b) for b in meta.get('blows', '').split('/') if b]
    print(f'hit {hit} blows {blows} step {meta.get("step")} hold {meta.get("hold")} length {rows[-1]["t"]:.3f}')
    print('    t    yaw pit |  fist r   u   f | blade el   az | arm el   az | apart | at3: r    u  reach | tipY  | speed lead | footR r    f  footL r    f | rootF rootU')
    t = t0
    while t <= t1 + 1e-6:
        i = min(range(len(rows)), key=lambda k: abs(rows[k]['t'] - t))
        r = rows[i]
        b = norm(sub(r['tip'], r['rod']))
        arm = norm(sub(r['hR'], r['sR']))
        fore = norm(sub(r['hR'], r['eR']))
        apart = math.degrees(math.acos(max(-1, min(1, dot(b, arm)))))
        be, ba = ea(b)
        ae, aa = ea(arm)
        h = r['hR']
        if b[2] > 1e-3:
            s = (3 - h[2]) / b[2]
            cr, cu = h[0] + b[0] * s, h[1] + b[1] * s
            reach = 'yes' if .37 <= s <= 2.63 else ('short' if s > 2.63 else 'in')
            at3 = f'{cr:5.2f} {cu:5.2f} {reach:5s}'
        else:
            at3 = '   -     -      '
        j0, j1 = max(0, i - 1), min(len(rows) - 1, i + 1)
        v = tuple((a - c) / max(1e-9, rows[j1]['t'] - rows[j0]['t']) for a, c in zip(rows[j1]['tip'], rows[j0]['tip']))
        speed = math.sqrt(dot(v, v))
        edge = sub(arm, tuple(x * dot(arm, b) for x in b))
        lead = dot(norm(v), norm(edge)) if speed > .5 else 0
        mid_h = tuple((r['hipR'][k] + r['hipL'][k]) / 2 for k in range(3))
        mark = ' *' if any(abs(t - x) < step * .5 for x in blows) else '  '
        print(f'{t:6.3f}{mark}{r["yaw"]:4.0f} {r["pitch"]:3.0f} | {h[0]:5.2f} {h[1]:4.2f} {h[2]:5.2f} | {be:5.0f} {ba:5.0f} | {ae:4.0f} {aa:5.0f} | {apart:4.0f}  | {at3} | {r["tip"][1]:5.2f} | {speed:5.1f} {lead:5.2f} | {r["fR"][0]:5.2f} {r["fR"][2]:5.2f} {r["fL"][0]:5.2f} {r["fL"][2]:5.2f} | {mid_h[2]:5.2f} {mid_h[1]:5.2f}')
        t += step


main()
