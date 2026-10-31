"""python3 -I tools/isosang/measure_top.py 영상.mp4 OUT_DIR [초 초 ...]   (2.78.0)
위에서 내려다본 평타 영상에서 검격의 크기를 블록으로 재는 눈금 그림을 만듭니다.
1. 영상의 모든 프레임을 OUT_DIR/all/에 뽑고(가변 프레임률: 시각은 showinfo의 pts_time), times.txt에 시각을 씁니다.
2. 카메라의 내려다본 각도는 바닥에 놓인 둥근 것(사용자 영상의 붉은 위험 구역)이 화면에서 보이는 가로세로비로 잽니다:
   비 = sin(각도). 사용자 영상(Screen_Recording_20261031_084141_3rd)은 0.82로 약 55°입니다.
3. 1블록의 화면 픽셀 수는 서 있는 캐릭터의 화면 높이(1.8블록 × cos(각도))로 정합니다: 111픽셀, 55°면 1블록 ≈ 107픽셀,
   바닥의 깊이 1블록은 그 × sin(각도) ≈ 88픽셀입니다.
4. 준 시각마다 캐릭터 발을 원점으로 세로줄(1블록)·가로줄(바닥 깊이 1블록)을 그린 그림을 OUT_DIR/grid_초.jpg로 씁니다.
   검격의 가로 폭을 세로줄로 세어 지름을 읽습니다(가로 폭은 단축되지 않습니다. 세로는 높이에 따라 늘어납니다).
환경 변수: PITCH(도, 기본 55), HEIGHT_PX(캐릭터 키, 기본 111), FEET=x,y(발의 화면 좌표, 기본 1175,660).
"""
import math, os, re, subprocess, sys
from PIL import Image, ImageDraw, ImageFont

video, out = sys.argv[1], sys.argv[2]
times = [float(t) for t in sys.argv[3:]]
pitch = math.radians(float(os.environ.get('PITCH', '55')))
ppb = float(os.environ.get('HEIGHT_PX', '111')) / (1.8 * math.cos(pitch))
depth = ppb * math.sin(pitch)
fx, fy = (int(v) for v in os.environ.get('FEET', '1175,660').split(','))
allf = os.path.join(out, 'all'); os.makedirs(allf, exist_ok=True)
if not os.path.exists(os.path.join(allf, 'times.txt')):
    info = subprocess.run(['ffmpeg', '-hide_banner', '-i', video, '-vf', 'showinfo', '-fps_mode', 'passthrough', '-q:v', '2', os.path.join(allf, 'a_%03d.jpg')],
                          capture_output=True, text=True).stderr
    with open(os.path.join(allf, 'times.txt'), 'w') as f:
        f.write(''.join(m.group(1) + '\n' for m in re.finditer(r'pts_time:([0-9.]+)', info)))
ts = [float(x) for x in open(os.path.join(allf, 'times.txt'))]
font = ImageFont.truetype('/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc', 22)
print(f'1 block = {ppb:.1f} px across, {depth:.1f} px of ground depth (pitch {math.degrees(pitch):.0f}°)')
for t in times:
    i = min(range(len(ts)), key=lambda k: abs(ts[k] - t))
    im = Image.open(os.path.join(allf, f'a_{i + 1:03d}.jpg')).convert('RGB')
    g = ImageDraw.Draw(im, 'RGBA')
    for n in range(-15, 16):
        a = 220 if n % 5 == 0 else 110
        x = fx + n * ppb; g.line((x, 0, x, im.height), fill=(255, 255, 0, a), width=1)
        y = fy + n * depth; g.line((0, y, im.width, y), fill=(255, 255, 0, a), width=1)
    g.text((8, 4), f'{ts[i]:.3f}s  세로줄 1칸 = 1블록({ppb:.0f}px), 가로줄 1칸 = 바닥 1블록({depth:.0f}px), 굵은 줄 5블록, 원점 = 발', fill=(255, 255, 0), font=font)
    path = os.path.join(out, f'grid_{ts[i]:.3f}.jpg'); im.save(path, quality=88); print(path)
