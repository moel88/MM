"""python3 tools/kageno/shots/film.py SHOT_DIR OUT.mp4 START END TITLE [CSV ACTOR MODE]   (FILM_WIDTH=960 by default)
Real-time 30 fps clip from game frames named INDEX_TICKS.png (cast clock in ticks), with Korean captions:
the title, the cast time and the stage of G; with an actors.csv, the actor's state each tick
(MODE run: how far ahead of the pillar's edge; MODE hit: held at 25 % / out at full speed).
SHOT_DIR is a run_shots.py output folder (START/END in seconds of the cast clock). Needs Pillow, ffmpeg and a font with
Hangul (WenQuanYi Zen Hei, fonts-wqy-zenhei).
"""
import sys, os, glob, bisect, csv, subprocess, tempfile, shutil
from PIL import Image, ImageDraw, ImageFont

shots, out, start, end, title = sys.argv[1], sys.argv[2], float(sys.argv[3]), float(sys.argv[4]), sys.argv[5]
csv_path, actor, mode = (sys.argv[6], sys.argv[7], sys.argv[8]) if len(sys.argv) > 8 else (None, None, None)
FONT = '/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc'
big, small = ImageFont.truetype(FONT, 26), ImageFont.truetype(FONT, 22)
W = int(os.environ.get('FILM_WIDTH', '960'))

frames = []
for f in glob.glob(os.path.join(shots, '*.png')):
    try: frames.append((float(os.path.basename(f)[:-4].split('_')[1]) / 20, f))
    except ValueError: pass
frames.sort(); times = [t for t, _ in frames]

rows = {}
if csv_path:
    with open(csv_path) as fh:
        for r in csv.DictReader(fh):
            if r['name'] == actor: rows[int(r['age'])] = r

def stage(t):
    if t < 5.59: return '영창'
    if t < 5.95: return '점화'
    if t < 12.06: return '기둥이 넓어지는 중 (초당 최대 4.5블록)'
    if t < 21.21: return '최대 반경 28블록'
    if t < 22.07: return '수축'
    return '소멸'

def actor_line(t):
    if not rows: return None, None
    age = int(t * 20); r = rows.get(age) or rows.get(max(k for k in rows if k <= age) if any(k <= age for k in rows) else min(rows))
    if r is None: return None, None
    held, out_, edge = r['held'] == '1', float(r['out']), float(r['edge'])
    if mode == 'run':
        if t < 5.59: return '달릴 허스크 (달리기 속도 초당 5.6블록)', (255, 255, 255)
        if edge <= 0: return f'허스크: 기둥 중심에서 {out_:.1f}블록', (255, 255, 255)
        gap = out_ - edge
        if held: return '허스크: 기둥에 잡힘 (이동속도 25%)', (255, 120, 120)
        return f'허스크가 기둥 가장자리보다 {gap:.1f}블록 앞 · 피함', (140, 255, 160)
    if mode == 'hit':
        if t < 5.59: return '서 있는 허스크 (걷기 속도 초당 4.32블록)', (255, 255, 255)
        if held:
            hit = any(k in rows and k - 1 in rows and float(rows[k]['out']) - float(rows[k - 1]['out']) > .3 for k in range(age - 6, age + 1))
            return ('기둥에 잡힘 · 이동속도 25% · 피해 넉백으로 밀려남' if hit else '기둥에 잡힘 · 이동속도 25% (초당 약 1.08블록)'), (255, 120, 120)
        if edge > 0 and out_ > edge + .01 and any(v['held'] == '1' for k, v in rows.items() if k < age):
            return '기둥에서 벗어남 · 이동속도 100% (초당 4.32블록)', (140, 255, 160)
        return f'허스크: 기둥 중심에서 {out_:.1f}블록 · 가장자리 {edge:.1f}블록', (255, 255, 255)
    return None, None

def caption(img, t):
    d = ImageDraw.Draw(img, 'RGBA')
    lines = [(title, big, (255, 255, 255)), (f'시전 후 {max(0, t):.1f}초 · {stage(t)}', small, (255, 230, 120))]
    text, color = actor_line(t)
    y = 10
    for s, font, col in lines:
        w = d.textlength(s, font=font); h = font.size + 10
        d.rectangle((8, y, 8 + w + 16, y + h), fill=(0, 0, 0, 150)); d.text((16, y + 4), s, font=font, fill=col); y += h + 4
    if text:
        w = d.textlength(text, font=big); h = big.size + 14; x = (img.width - w) / 2; yb = img.height - h - 14
        d.rectangle((x - 12, yb, x + w + 12, yb + h), fill=(0, 0, 0, 160)); d.text((x, yb + 6), text, font=big, fill=color)

tmp = tempfile.mkdtemp(dir=os.path.dirname(os.path.abspath(out)))
try:
    n, t = 0, start
    while t <= end:
        i = bisect.bisect_left(times, t); i = min(range(max(0, i - 1), min(len(times), i + 1)), key=lambda j: abs(times[j] - t))
        img = Image.open(frames[i][1]).convert('RGB'); img = img.resize((W, round(img.height * W / img.width / 2) * 2), Image.LANCZOS)
        caption(img, t); img.save(os.path.join(tmp, f'{n:05d}.png')); n += 1; t += 1 / 30
    subprocess.run(['ffmpeg', '-loglevel', 'error', '-y', '-framerate', '30', '-i', os.path.join(tmp, '%05d.png'), '-c:v', 'libx264', '-pix_fmt', 'yuv420p',
                    '-crf', '23', '-preset', 'slow', '-movflags', '+faststart', out], check=True)
finally:
    shutil.rmtree(tmp, ignore_errors=True)
print(out, n, 'frames')
