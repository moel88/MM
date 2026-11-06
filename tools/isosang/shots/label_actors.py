#!/usr/bin/env python3
"""Labels the husks ("act=" actors) of a shot with what the server's log says they took (2.87.1, G's blast measured).

    python3 -I tools/isosang/shots/label_actors.py SHOT_DIR OUT_DIR STRIKE_X STRIKE_Y STRIKE_Z HIT_TICK [CASTER_X CASTER_Z]

Copies every frame of SHOT_DIR (a SkillShots folder: frames named N_TICKS.png, actors.csv, camera.csv) to OUT_DIR with a
small label over each actor's head (the actors stand on the strike's floor, STRIKE_Y): how far it stands from
(STRIKE_X, STRIKE_Z) across the ground, and from HIT_TICK
(shot ticks) on, how much health it lost then (actors.csv, the server's own numbers) or "0 (밖)" if it lost none. The
actors are put where the frame's camera (camera.csv: its place, yaw, pitch and projection) sees them. The labels are an
edit drawn over the game's frames, not the game's own; say so in the film. Run film.py on OUT_DIR as on SHOT_DIR.
With CASTER_X CASTER_Z the caster is labelled too (시전자). LABEL_RECT="X0,Z0,X1,Z1[,TEXT]" also outlines that ground
(dashed, on the strike's floor) with TEXT inside its south-east corner: the blast's 62 × 47 blocks.
"""
import csv
import math
import os
import shutil
import sys

from PIL import Image, ImageDraw, ImageFont

FONT = '/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc'


def main():
    shots, out = sys.argv[1], sys.argv[2]
    sx, sy, sz, hit = float(sys.argv[3]), float(sys.argv[4]), float(sys.argv[5]), float(sys.argv[6])
    caster = (float(sys.argv[7]), float(sys.argv[8])) if len(sys.argv) > 8 else None
    os.makedirs(out, exist_ok=True)
    rows = list(csv.DictReader(open(os.path.join(shots, 'actors.csv'))))
    actors = {}
    for r in rows:
        a = actors.setdefault(r['name'], {'x': float(r['x']), 'z': float(r['z']), 'first': float(r['health']), 'last': float(r['health'])})
        a['last'] = float(r['health'])
    cams = {round(float(r['t']), 2): r for r in csv.DictReader(open(os.path.join(shots, 'camera.csv')))}
    font = ImageFont.truetype(FONT, 17)
    for name in sorted(os.listdir(shots)):
        if not name.endswith('.png'):
            continue
        t = name[:-4].split('_')[1]
        img = Image.open(os.path.join(shots, name)).convert('RGB')
        cam = cams.get(round(float(t), 2))
        if cam is not None:
            draw(img, cam, actors, sx, sy, sz, float(t) >= hit, font, caster)
        img.save(os.path.join(out, name))
    for f in os.listdir(shots):
        if f.endswith('.csv'):
            shutil.copy(os.path.join(shots, f), out)


def draw(img, cam, actors, sx, sy, sz, after, font, caster=None):
    d = ImageDraw.Draw(img, 'RGBA')
    cx, cy, cz = float(cam['x']), float(cam['y']), float(cam['z'])
    yaw, pitch = math.radians(float(cam['yaw'])), math.radians(float(cam['pitch']))
    f = (-math.sin(yaw) * math.cos(pitch), -math.sin(pitch), math.cos(yaw) * math.cos(pitch))
    r = (-math.cos(yaw), 0.0, -math.sin(yaw))
    u = (r[1] * f[2] - r[2] * f[1], r[2] * f[0] - r[0] * f[2], r[0] * f[1] - r[1] * f[0])
    px, py = float(cam['proj_x']), float(cam['proj_y'])
    w, h = img.size
    marks = []

    def screen(wx, wz, up=2.3):
        p = (wx - cx, sy + up - cy, wz - cz)
        depth = sum(p[i] * f[i] for i in range(3))
        if depth <= .5:
            return None
        return ((px * sum(p[i] * r[i] for i in range(3)) / depth + 1) / 2 * w, (1 - py * sum(p[i] * u[i] for i in range(3)) / depth) / 2 * h)
    rect = os.environ.get('LABEL_RECT')
    if rect:
        v = rect.split(',')
        x0, z0, x1, z1 = (float(n) for n in v[:4])
        corners = [screen(x0, z0, .05), screen(x1, z0, .05), screen(x1, z1, .05), screen(x0, z1, .05)]
        if all(c is not None for c in corners):
            for i in range(4):
                (ax, ay), (bx, by) = corners[i], corners[(i + 1) % 4]
                n = max(1, int(math.hypot(bx - ax, by - ay) / 9))
                for k in range(0, n, 2):
                    d.line((ax + (bx - ax) * k / n, ay + (by - ay) * k / n, ax + (bx - ax) * (k + 1) / n, ay + (by - ay) * (k + 1) / n), fill=(120, 230, 255, 235), width=2)
            if len(v) > 4:
                tw = d.textlength(v[4], font=font)
                ax, ay = corners[2]
                d.rectangle((ax - tw - 14, ay - 26, ax - 4, ay - 4), fill=(0, 40, 60, 170))
                d.text((ax - tw - 9, ay - 25), v[4], font=font, fill=(150, 235, 255))
    if caster is not None and screen(*caster) is not None:
        x, y = screen(*caster)
        marks.append((y, x, '시전자', None))
    for name, a in sorted(actors.items()):
        # over its head: the husks stand on the floor the sword strikes
        at = screen(a['x'], a['z'])
        if at is None:
            continue
        x, y = at
        far = f"{math.hypot(a['x'] - sx, a['z'] - sz):.1f}".rstrip('0').rstrip('.')
        lost = a['first'] - a['last']
        text = f'{far}블록'
        if after:
            text += f' -{lost:.1f}' if lost > .01 else ' 0 (밖)'
        marks.append((y, x, text, after and lost > .01))
    # the labels over their heads, put aside (left and right in turn, further each time) where one would cover another
    placed = []
    for y, x, text, struck in sorted(marks):
        tw = d.textlength(text, font=font)
        for k in range(40):
            dx = 0 if k == 0 else (1 if k % 2 else -1) * (tw / 2 + 8) * ((k + 1) // 2) * .5
            box = (x + dx - tw / 2 - 5, y - 26, x + dx + tw / 2 + 5, y - 4)
            if all(box[2] < b[0] or box[0] > b[2] or box[3] < b[1] or box[1] > b[3] for b in placed):
                break
        placed.append(box)
        d.line((x, y + 4, (box[0] + box[2]) / 2, box[3]), fill=(255, 255, 255, 210), width=1)
        d.rectangle(box, fill=(0, 0, 0, 170))
        d.text((box[0] + 5, box[1] + 1), text, font=font, fill=(150, 210, 255) if struck is None else (255, 214, 120) if struck else (235, 235, 235))

if __name__ == '__main__':
    main()
