"""Side by side at half speed, hit by hit: the user's video from above (left) and the game's from a like camera (right),
each hit's segment aligned on its own event (the game's hits run on the server's times, so one offset does not fit all).
usage: python3 sbs_hits.py SHOT_DIR OUT.mp4 VERSION
Segments: (video a, video b, game offset, label); the game's scene second = video second + offset.
"""
import os, subprocess, sys, tempfile
SHOT, OUT, VER = sys.argv[1], sys.argv[2], sys.argv[3]
REF = '/root/.claude/uploads/f2ca800d-2644-54d1-8826-504612a41602/cf582428-Screen_Recording_20261031_084141_3rd.mp4'
FONT = '/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc'
W = '/home/user/isosang_work'
SEG = [(0.50, 1.37, 0.48, '1타', '1타의 베기(영상 0.86초)를 맞춤'),
       (1.37, 2.12, 0.56, '2타', '2타의 원(영상 1.39초)을 맞춤'),
       (2.12, 3.44, 0.58, '3타', '3타의 고리 1(영상 2.20초)을 맞춤'),
       (3.44, 4.73, 0.90, '4타', '4타의 참격(영상 3.855초)을 맞춤: 게임은 칼 돌리기부터 참격까지 0.7초(서버 규칙), 영상은 0.4초')]
NOTES = {
 '1타': "1.05-1.34:칼이 손에, 들어 올려 왼쪽 어깨 뒤로 감음|1.34-1.42:오른쪽으로 도는 한 바퀴 베기, 꼬리는 가늘고 머리는 굵게 먹|1.42-1.9:되올려베기, 검격이 가늘어지며 마름, 뒤왼쪽 바닥의 먹",
 '2타': "1.85-1.95:왼쪽으로 감았다 웅크림|1.95-2.05:오른쪽으로 한 바퀴, 앞왼쪽에서 시작하는 원(갓 그은 곳은 넓은 흰빛)|2.05-2.3:파란 띠, 원 안 바닥이 어두워짐, 오른쪽 뒤의 먹 웅덩이|2.3-2.7:띠가 가늘어져 사라짐",
 '3타': "2.6-2.78:왼쪽으로 감음|2.78-2.86:고리 1(앞이 높음)|2.86-2.98:뒤에서 앞으로 올라가는 긴 올려베기(빛줄기와 장막)|2.98-3.09:칼을 왼쪽으로 되감음|3.09-3.17:고리 2|3.17-3.29:고리 3(빛줄기와 X)|3.29-3.39:가슴 높이의 가는 호|3.39-3.58:고리 4(가장 큼), 뒤왼쪽 바닥의 먹|3.58-4.1:검격이 마름",
 '4타': "4.05-4.37:머리 위에서 칼을 돌림(옅은 띠)|4.37-4.63:왼손으로 겨눔, 칼은 뒤로|4.63-4.75:웅크림·돌진|4.75-4.8:낮게 가로베어 참격을 날림, 바닥의 먹 고리|4.8-5.3:평평한 참격이 천천히 나아가며 넓어짐, 뒤로 뻗는 빛줄기|5.3-5.7:참격이 멀리서 마름"}
tmp = tempfile.mkdtemp(prefix='sbs_', dir=W)
parts = []
for i, (a, b, off, name, how) in enumerate(SEG):
    right = f'{tmp}/r{i}.mp4'; left = f'{tmp}/l{i}.mp4'; both = f'{tmp}/b{i}.mp4'
    env = dict(os.environ, FILM_WIDTH='960', FILM_FPS='30', FILM_SPEED='0.5', FILM_SKILL='ISOSANG', FILM_CAPTION='below', FILM_CLOCK='장면', FILM_NOTES=NOTES[name])
    subprocess.run(['python3', '-I', '/home/user/MM/tools/kageno/shots/film.py', SHOT, right, f'{a+off:.3f}', f'{b+off:.3f}', f'보탠드 {VER} · 실제 게임(소프트웨어 렌더링), 위에서 내려다본 카메라'], cwd='/home/user/MM', env=env, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    n = int(subprocess.check_output(['ffprobe', '-v', 'error', '-count_frames', '-select_streams', 'v', '-show_entries', 'stream=nb_read_frames', '-of', 'csv=p=0', right]).decode().strip())
    h = int(subprocess.check_output(['ffprobe', '-v', 'error', '-select_streams', 'v', '-show_entries', 'stream=height', '-of', 'csv=p=0', right]).decode().strip())
    dt = f'fontfile={FONT}:fontcolor=white'
    vf = (f"trim=start={a}:end={b},setpts=2.0*(PTS-STARTPTS),fps=30:round=near,scale=960:-2,pad=960:540:0:(540-ih)/2:color=0x0c0c10,pad=960:{h}:0:0:color=0x0c0c10,"
          f"drawtext={dt}:fontsize=26:x=12:y=548:text='보내 주신 영상 (위에서 내려다본 게임 화면 녹화) · {name}',"
          f"drawtext=fontfile={FONT}:fontcolor=0xf2d36b:fontsize=22:x=12:y=585:text='영상 %{{eif\\:trunc(({a}+t/2)*10/10)\\:d}}.%{{eif\\:mod(trunc(({a}+t/2)*10)\\,10)\\:d}}초 · 0.5배속',"
          f"drawtext={dt}:fontsize=20:x=12:y=618:text='{how}'")
    subprocess.run(['ffmpeg', '-loglevel', 'error', '-y', '-i', REF, '-an', '-vf', vf, '-frames:v', str(n), '-c:v', 'libx264', '-pix_fmt', 'yuv420p', '-crf', '20', left], check=True)
    subprocess.run(['ffmpeg', '-loglevel', 'error', '-y', '-i', left, '-i', right, '-filter_complex', '[0:v][1:v]hstack=inputs=2,format=yuv420p', '-an', '-c:v', 'libx264', '-crf', '20', both], check=True)
    parts.append(both)
lst = f'{tmp}/list.txt'
open(lst, 'w').write(''.join(f"file '{p}'\n" for p in parts))
subprocess.run(['ffmpeg', '-loglevel', 'error', '-y', '-f', 'concat', '-safe', '0', '-i', lst, '-c:v', 'libx264', '-crf', '22', '-preset', 'slow', '-pix_fmt', 'yuv420p', '-movflags', '+faststart', OUT], check=True)
print(OUT, subprocess.check_output(['ffprobe', '-v', 'error', '-show_entries', 'stream=width,height,nb_frames,r_frame_rate', '-of', 'csv=p=0', OUT]).decode().strip())
