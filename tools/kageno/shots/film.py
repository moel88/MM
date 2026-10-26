"""python3 tools/kageno/shots/film.py SHOT_DIR OUT.mp4 START END TITLE [CSV ACTOR MODE]   (FILM_WIDTH=960 by default)
FILM_SKILL=G|GPOSE|GDUMMY|E|EAIM|T|RUSH|RUSHAIM|BASE|R|REND|M1|F picks the stage names in the caption (anything else: none), FILM_SPEED=0.5 plays it at half speed, FILM_FPS sets the frame rate, FILM_CAPTION=center centres the captions at the top, FILM_CAPTION=below puts them in a band under the picture (the HUD's shots, 2.42.0: nothing of the game's screen covered).
MODE motion with the shot's motion.csv (ACTOR -) shows the player's walking speed and the lens's FOV multiplier each tick.
MODE rush with a SUMMON shot's stand.csv (ACTOR -, 2.57.0) shows Star Platinum's rush: the blows heard, the training
dummy's health and, from the motion.csv beside it, the player's walking speed while they walk.
MODE heavy with a SUMMON shot's stand.csv (ACTOR -, 2.59.0) shows its heavy punch: coiling, landed (on how many) or
struck no one, the training dummy's health, its guard (up, broken) and how far it has flown from where it was struck
(FILM_HEAVY_DUMMY=0 leaves the dummy out: it was put away); since 2.60.0 a broken guard is an incapacitation.
MODE finger with a SUMMON shot's stand.csv (ACTOR -, 2.60.0) shows its Star Finger: coiling, the fingers going out (how
long they are drawn), struck, blocked by the guard or struck no one, the training dummy's health, its guard,
incapacitated and how fast it may move (FILM_FINGER_DUMMY=0 leaves the dummy out).
MODE beatdown with a SUMMON shot's stand.csv (ACTOR -, 2.64.0) shows its beatdown: its blow, then its cutscene's part
(by the cutscene's own clock), the training dummy's health and the damage dealt; struck no one or its guard broken
(no cutscene).
MODE timestop with a SUMMON shot's stand.csv (ACTOR -, 2.67.0) shows its time stop: called ("The World!"), stopped (for
how long of its 5 s), moving again; the training dummy's health through it (unchanged while time is stopped, then all
it took at once; FILM_TS_DUMMY=0 leaves it out) and, with FILM_TS_PLAYER=1, the player's own: one it stopped, whose
screen is held still (shown) while time is stopped. Since 2.68.0, with an actors.csv beside it (act=), each husk while
time is stopped: how far it is and whether it stands stopped or walks on (out of its reach).
MODE boss with a SUMMON shot's boss.csv (ACTOR -, 2.69.0) shows Kujo Jotaro: neutral or angry, what he does (his own
moves, or how he answers the player's: a guard, a dash, a blow first), whom he goes after, his health, the player's
(and the second player's, MATE) health and the boxes he left.
MODE arrow with a SUMMON shot's stand.csv (ACTOR -, 2.70.0; ten seconds since 2.71.0) shows the stand arrow: raised, driven in, taken into the
body, then what it chose (the stand awakened, or the death: on the knees, fallen, dead) by the stab's own clock, the
player's stand (the server's) and how many arrows are in their hand.
MODE stagger with a SUMMON shot's stand.csv (ACTOR -, 2.68.0) shows the basic attack's stagger: the training dummy's
blows (its combo) and whether it is staggered, whether the player is staggered (walking only, at a tenth of their speed),
their speed attribute against its base and, from the motion.csv beside it, how fast they walk.
Real-time 60 fps clip from game frames named INDEX_TICKS.png (cast clock in ticks), with Korean captions:
the title, the cast time and the stage of G; with an actors.csv, the actor's state each tick
(MODE run: how far ahead of the pillar's edge; MODE hit: held at 25 % / out at full speed); with a shake.csv
(ACTOR -, MODE wall) how far the camera is from the pillar's wall and the wall shake it gets.
SHOT_DIR is a run_shots.py output folder (START/END in seconds of the cast clock). Needs Pillow, ffmpeg and a font with
Hangul (WenQuanYi Zen Hei, fonts-wqy-zenhei). Every output frame should be its own game frame: capture with a SLOW of
about 12 (run_shots.py) so the client renders more than FILM_FPS frames per second of the cast clock; the script
reports how many output frames had to repeat a game frame.
"""
import sys, os, glob, bisect, csv, math, subprocess, tempfile, shutil
from PIL import Image, ImageDraw, ImageFont

shots, out, start, end, title = sys.argv[1], sys.argv[2], float(sys.argv[3]), float(sys.argv[4]), sys.argv[5]
csv_path, actor, mode = (sys.argv[6], sys.argv[7], sys.argv[8]) if len(sys.argv) > 8 else (None, None, None)
FONT = '/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc'
big, small = ImageFont.truetype(FONT, 26), ImageFont.truetype(FONT, 22)
W = int(os.environ.get('FILM_WIDTH', '960'))
SKILL, SPEED, FPS = os.environ.get('FILM_SKILL', 'G'), float(os.environ.get('FILM_SPEED', '1')), int(os.environ.get('FILM_FPS', '60'))
# FILM_STAGES="UNTIL:NAME|UNTIL:NAME|..." gives the captions directly (seconds of the shot's clock) and FILM_CLOCK the
# words before the time (default "시전 후"): the gacha's shots (tools/gacha/shots) run on the screen's own clock.
CLOCK = os.environ.get('FILM_CLOCK', '시전 후')
# the band is a row taller when a CSV line goes with it (2.57.0: the rush's blows and the dummy's health under the HUD's shots)
BELOW = (76 + (34 if mode else 0)) if os.environ.get('FILM_CAPTION') == 'below' else 0
if os.environ.get('FILM_STAGES'): SKILL = 'CUSTOM'

frames = []
for f in glob.glob(os.path.join(shots, '*.png')):
    try: frames.append((int(os.path.basename(f)[:-4].split('_')[0]), float(os.path.basename(f)[:-4].split('_')[1]) / 20, f))
    except ValueError: pass
# When a cast ends the shot's clock falls back from the cast's clock to its own, up to a tick behind: later frames are
# moved on by that step so the clock keeps running forward (a drop of more than 0.1 s is a restart, below).
frames.sort(); offset = 0; moved = []
for k, (i, t, f) in enumerate(frames):
    if k and 0 < moved[-1][1] - (t + offset) < .1: offset += moved[-1][1] - (t + offset) + .001
    moved.append((i, t + offset, f))
frames = moved
# A held key's frames come first on the shot's own clock (T held: the 0.4 s hold before the cast); keep only the frames
# after the cast clock last started again, so they never mix with the cast's own first frames.
first = 0
for k in range(1, len(frames)):
    if frames[k][1] < frames[k - 1][1] - .05: first = k
frames = sorted((t, f) for _, t, f in frames[first:]); times = [t for t, _ in frames]

rows, wall, motion, rush, stops = {}, [], [], [], []
walkers, husks = [], {}
if csv_path and mode in ('heavy', 'finger', 'inhale', 'beatdown', 'timestop', 'arrow'):
    with open(csv_path) as fh:
        rush = sorted((float(r['t']), r) for r in csv.DictReader(fh) if r[list(r)[-1]] is not None)
    # the finger mode: a husk walking off (act=) beside stand.csv, its speed attribute and how fast it walks
    beside = os.path.join(os.path.dirname(csv_path), 'actors.csv')
    # the time stop's husks (act=, 2.68.0): where each is, to tell those it stopped from those out of its reach
    husks = {}
    if mode == 'timestop' and os.path.exists(beside):
        with open(beside) as fh:
            for r in csv.DictReader(fh): husks.setdefault(r['name'], {})[int(r['age'])] = (float(r['x']), float(r['z']), float(r['out']))
    if mode in ('finger', 'inhale') and os.path.exists(beside):
        with open(beside) as fh:
            seen = list(csv.DictReader(fh))
            # (the inhale's husks: the first one only, how far it has been dragged)
            walkers = sorted((int(r['age']), float(r['x']), float(r['z']), float(r['speed_attr'])) for r in seen if mode == 'finger' or r['name'] == seen[0]['name'])
elif csv_path and mode == 'boss':
    with open(csv_path) as fh:
        rush = sorted((float(r['t']), r) for r in csv.DictReader(fh))
    # what the player's client saw (stand.csv beside boss.csv): his time stop's call and his beatdown's cutscene, both
    # holding him where he stands
    beside = os.path.join(os.path.dirname(csv_path), 'stand.csv')
    if os.path.exists(beside):
        with open(beside) as fh:
            stops = sorted((float(r['t']), r) for r in csv.DictReader(fh))
elif csv_path and mode == 'stagger':
    with open(csv_path) as fh:
        rush = sorted((float(r['t']), r) for r in csv.DictReader(fh) if r.get('player_staggered') not in (None, ''))
    beside = os.path.join(os.path.dirname(csv_path), 'motion.csv')
    if os.path.exists(beside):
        with open(beside) as fh:
            motion = sorted((float(r['t']), float(r['x']), float(r['z']), float(r['fov'])) for r in csv.DictReader(fh))
elif csv_path and mode == 'rush':
    # a row the stand did not show in can be a field short (SkillShots before 2.57.0): its last field is then missing
    with open(csv_path) as fh:
        rush = sorted((float(r['t']), r) for r in csv.DictReader(fh) if r[list(r)[-1]] is not None)
    beside = os.path.join(os.path.dirname(csv_path), 'motion.csv')
    if os.path.exists(beside):
        with open(beside) as fh:
            motion = sorted((float(r['t']), float(r['x']), float(r['z']), float(r['fov'])) for r in csv.DictReader(fh))
elif csv_path and mode == 'motion':
    with open(csv_path) as fh:
        motion = sorted((float(r['t']), float(r['x']), float(r['z']), float(r['fov'])) for r in csv.DictReader(fh))
elif csv_path and mode == 'wall':
    with open(csv_path) as fh:
        wall = sorted((float(r['t']) / 20, float(r['gap']), float(r['wall'])) for r in csv.DictReader(fh))
elif csv_path:
    with open(csv_path) as fh:
        for r in csv.DictReader(fh):
            if r['name'] == actor: rows[int(r['age'])] = r

# Stage captions per skill: (until second, name), checked in order.
STAGES = {
    'T': [(.65, '준비 · 왼손 별빛'), (.95, '폭발 · 검은 연기 → 파랑·보라'), (4.2, '자홍 불꽃 · 별빛 (3.5초간 13회 타격)'), (4.85, '분홍 불꽃 조각으로 사그라듦')],
    'RUSH': [(.36, '칼을 오른쪽으로 당기고 왼손으로 칼날을 훑어 불을 붙임 · 발밑에 작은 불 고리'),
             (.55, '1타 가로 베기 · 첫 불 참격이 앞으로 30블록 날아감'), (.75, '2타 왼쪽 위에서 내려 벰 · 두 번째 불 참격'),
             (.9, '3타 크게 내려 벰 · 가장 큰 세 번째 불 참격'), (1.2, '돌진 · 처음 맞은 대상에게 (아무도 안 맞으면 끝까지)'),
             (1.3, '도착 타격 · 불 폭발'), (1.45, '맞은 대상에서 불이 다시 터짐 · 회전 준비'),
             (1.8, '한 바퀴 회전 베기 · 불의 고리 (1.55초 마무리 타격)'), (3.0, '바닥에 남은 불의 고리 · 연기 · 갈라진 자국')],
    # G's caster pose around the blast (2.38.0 comparison, a camera in front).
    'GPOSE': [(4.45, '양손으로 칼을 머리 위로'), (5.3, '머리 위에서 칼을 한 바퀴'), (5.59, '회전 뒤 · 폭발 직전'),
              (5.95, '폭발 (화면 5.59초 · 서버 5.60초)'), (30, '기둥 안 (보라 화면)')],
    # 2.38.2: T held aimed while its slashes fly (the camera turns 39 degrees right between 0.45 and 0.55 s).
    'RUSHAIM': [(.36, '칼에 불을 붙임 · 발밑에 작은 불 고리'), (.45, '1타 · 첫 불 참격은 정면으로'),
                (.58, '카메라를 오른쪽으로 39° 돌림 · 몸이 따라 돎'), (.75, '2타 · 돌린 쪽으로 불 참격 → 더미 적중 · 붙잡음'),
                (.9, '3타 · 같은 쪽으로 불 참격'), (1.2, '처음 맞은 더미에게 돌진'), (1.3, '도착 타격 · 불 폭발'),
                (1.8, '한 바퀴 회전 베기 · 불의 고리'), (3.0, '바닥에 남은 불의 고리')],
    # 2.38.2: E walked and aimed (W held, the camera turns 40 degrees right between 0.9 and 1.5 s).
    'EAIM': [(.48, '네온 점화 · 걸으면서 찌르기 자세'), (.9, '연속 찌르기 · 걸으면서'),
             (1.5, '카메라를 오른쪽으로 40° 돌림 · 몸과 찌르기가 따라 돎'), (2.4, '돌린 쪽으로 걸으며 연속 찌르기'),
             (2.6, '마무리 (2.4초부터 제자리) · 검을 뒤로 빼기'), (2.88, '올려베기'), (3.35, '폭발 · 빛기둥 (2.9초 마무리 타격)')],
    'F': [(.12, '방어 올리기'), (2.0, '방어 중'), (3.0, '방어를 풂 (2.0초)')],
    # 2.38.2: R walked (W held through it and after it).
    'REND': [(.92, '걸으면서 R 시전 (R 동안 50% 감속)'), (2.4, '세상이 멈춤 → 베기 · 걸으면서'), (5.5, 'R이 끝남 (2.4초) · 계속 걷기')],
    # 2.38.2: the Atomic dummy seen up close from outside its column.
    'GDUMMY': [(4.45, '영창 · 양손으로 칼을 머리 위로'), (5.3, '머리 위에서 칼을 한 바퀴'), (5.59, '두 팔을 펼침 · 폭발 직전'),
               (6.2, '폭발'), (30, '빛기둥이 넓어짐 (더미는 기둥 안)')],
    'BASE': [(.5, '서 있기'), (3.4, '점프 · 공중 자세 · 착지'), (5.0, '피격 움찔 (2번)'), (7.5, '평타')],
    # The M1 combo shot clicks at 0, 0.65, 1.3 and 1.95 s of the shot clock.
    'M1': [(.65, '1타 발도 · 왼쪽 허리에서 오른쪽 위로 올려베기'), (1.3, '2타 되베기 · 오른쪽 위에서 왼쪽 아래로'),
           (1.95, '3타 회전 내려찍기 · 공중에서 한 바퀴 돌고 바닥을 내려침'),
           (2.85, '4타 관통 찌르기 · 빛창 · 충격 고리 · 맞은 대상이 찌른 방향으로 날아감'), (4.0, '대상 착지 · 흙먼지 · 칼을 털고 자세를 세움')],
    'R': [(.5, '보라 기운 · 몸을 낮췄다가 칼을 머리 위로'), (.92, '세상이 멈춤 (흑백 · 청록 윤곽 · 청록 칼)'),
          (1.45, '베기 4번 · 시전마다 다른 방향으로 앞을 그음 · 허공에 멈춘 선'),
          (1.72, '마지막 큰 베기'), (1.8, '색이 돌아옴'), (1.89, '빛기둥 · 보라 검격 4개 (1.8초 베기 4타)'),
          (2.03, '마무리 순간 반전 번쩍임'),
          (2.6, '큰 보라 검격 · 공기 굴절 · 바닥이 갈라지고 들썩임 (1.9초 마지막 큰 베기 1타, 총 5히트)'), (3.9, '바닥 흔적 · 파편 · 흙먼지 · 연기')],
}

if SKILL == 'CUSTOM':
    STAGES['CUSTOM'] = [(float(a), b) for a, b in (x.split(':', 1) for x in os.environ['FILM_STAGES'].split('|'))]

def stage(t):
    if SKILL == 'E':
        if t < .48: return '네온 점화 · 찌르기 자세'
        if t < 2.42: return '연속 찌르기 (15번, 피해 7회)'
        if t < 2.6: return '검을 뒤로 빼기'
        if t < 2.88: return '올려베기'
        if t < 3.35: return '폭발 · 빛기둥 (2.9초 마무리 타격)'
        return '끝'
    if SKILL in STAGES:
        for until, name in STAGES[SKILL]:
            if t < until: return name
        return '끝'
    if SKILL != 'G': return ''
    if t < 5.59: return '영창'
    if t < 5.95: return '점화'
    if t < 12.06: return '기둥이 넓어지는 중 (초당 최대 4.5블록)'
    if t < 21.21: return '최대 반경 28블록'
    if t < 22.07: return '수축'
    return '소멸'

def wall_line(t):
    if t < 5.59 or not wall: return None, None
    i = bisect.bisect_left(wall, (t,)); _, gap, amp = wall[min(i, len(wall) - 1)]
    if gap <= 0: return '기둥 안 (맞는 시점)', (255, 120, 120)
    if gap > 8: return f'기둥 벽까지 {gap:.1f}블록', (255, 255, 255)
    return f'기둥 벽까지 {gap:.1f}블록 · 벽 근처 흔들림 {amp:.1f}°', (255, 150, 90)

def motion_line(t):
    if not motion: return None, None
    i = bisect.bisect_right(motion, (t * 20 + 1e-6, 1e9)) - 1
    if i < 1: return None, None
    # The tick before (two rows can share a tick where the clock falls back as the cast ends).
    j = i - 1
    while j > 0 and motion[i][0] - motion[j][0] < .5: j -= 1
    (t0, x0, z0, _), (t1, x1, z1, fov) = motion[j], motion[i]
    speed = ((x1 - x0) ** 2 + (z1 - z0) ** 2) ** .5 * 20 / max(1e-3, t1 - t0)
    zoom = '' if abs(fov - 1) < .005 else (' (확대됨)' if fov < 1 else ' (넓어짐)')
    return f'이동 속도 초당 {speed:.1f}블록 · 시야 배율 {fov:.3f}{zoom}', ((255, 120, 120) if abs(fov - 1) >= .005 else (255, 255, 255))

def walking(t, ticks=4):
    """The player's speed over the last few ticks before t (blocks a second), None without a motion.csv."""
    if not motion: return None
    i = bisect.bisect_right(motion, (t * 20 + 1e-6, 1e9)) - 1
    j = bisect.bisect_left(motion, (t * 20 - ticks - 1e-6,))
    if i < 1 or j >= i: return None
    (t0, x0, z0, _), (t1, x1, z1, _) = motion[j], motion[i]
    return ((x1 - x0) ** 2 + (z1 - z0) ** 2) ** .5 * 20 / max(1e-3, t1 - t0)

def rush_line(t):
    if not rush: return None, None
    i = bisect.bisect_right([a for a, _ in rush], t * 20 + 1e-6) - 1
    if i < 0: return None, None
    r, parts = rush[i][1], []
    blows, on = int(r['rush_blows'] or 0), r['rush_on'] == '1'
    if blows or on: parts.append(f'러시 {blows}타' + ('' if on else ' (끝)'))
    full = next((float(x['dummy_health']) for _, x in rush if x['dummy_health']), None)
    # FILM_RUSH_DUMMY=0: the shot's dummy is put away (nobody in the rush's way), so its health is not shown
    if r['dummy_health'] and full and os.environ.get('FILM_RUSH_DUMMY') != '0': parts.append(f'더미 체력 {float(r["dummy_health"]):.0f} / {full:.0f} (피해 {full - float(r["dummy_health"]):.0f})')
    speed = walking(t)
    if speed is not None and speed > .05: parts.append(f'걷는 속도 초당 {speed:.2f}블록')
    return (' · '.join(parts), (255, 255, 255)) if parts else (None, None)

def heavy_line(t):
    if not rush: return None, None
    i = bisect.bisect_right([a for a, _ in rush], t * 20 + 1e-6) - 1
    if i < 0: return None, None
    r, parts = rush[i][1], []
    if r['combo'] == '5':
        if r['landed'] != '1': parts.append('강펀치 준비')
        elif int(r['struck'] or 0) > 0: parts.append('강펀치 적중')
        else: parts.append('강펀치 · 아무도 안 맞음')
    # FILM_HEAVY_DUMMY=0: the shot's dummy is put away (nobody in the punch's way), so nothing of it is shown
    if os.environ.get('FILM_HEAVY_DUMMY') == '0': return (' · '.join(parts), (255, 255, 255)) if parts else (None, None)
    full = next((float(x['dummy_health']) for _, x in rush if x['dummy_health']), None)
    if r['dummy_health'] and full: parts.append(f'더미 체력 {float(r["dummy_health"]):.0f} / {full:.0f} (피해 {full - float(r["dummy_health"]):.0f})')
    if r.get('dummy_broken') == '1': parts.append('가드 깨짐 · 무력화')
    elif r.get('dummy_guard') == '1': parts.append('가드 중')
    # how far the dummy has flown: from where it stood as the heavy punch landed (the row before the first that shows
    # it landed: the dummy may already be on its way in that one)
    j = next((k for k, (_, x) in enumerate(rush) if x['combo'] == '5' and x['landed'] == '1' and x.get('dummy_x')), None)
    hit = rush[max(0, j - 1)][1] if j is not None else None
    if hit and r.get('dummy_x') and hit.get('dummy_x') and i >= j:
        d = ((float(r['dummy_x']) - float(hit['dummy_x'])) ** 2 + (float(r['dummy_z']) - float(hit['dummy_z'])) ** 2) ** .5
        if d > .3: parts.append(f'날아간 거리 {d:.1f}블록')
    return (' · '.join(parts), (255, 255, 255)) if parts else (None, None)

def finger_line(t):
    if not rush: return None, None
    i = bisect.bisect_right([a for a, _ in rush], t * 20 + 1e-6) - 1
    if i < 0: return None, None
    r, parts = rush[i][1], []
    if r['combo'] == '6':
        ext = float(r.get('finger_ext') or 0)
        if r['landed'] != '1': parts.append('스타 핑거 준비' if ext < .01 else '손가락이 뻗어 나감')
        elif int(r['struck'] or 0) > 0: parts.append('스타 핑거 적중')
        elif r.get('dummy_guard') == '1' and os.environ.get('FILM_FINGER_DUMMY') != '0': parts.append('가드에 막힘')
        else: parts.append('스타 핑거 · 아무도 안 맞음')
        if r.get('finger_len') and ext > .01: parts.append(f'손가락 {float(r["finger_len"]):.1f}블록')
    if walkers:
        k = bisect.bisect_right(walkers, (t * 20 + 1e-6, 1e9, 0, 0)) - 1
        j = bisect.bisect_left(walkers, (t * 20 - 6 - 1e-6,))
        if k > 0 and j < k:
            (a0, x0, z0, _), (a1, x1, z1, mult) = walkers[j], walkers[k]
            pace = ((x1 - x0) ** 2 + (z1 - z0) ** 2) ** .5 * 20 / max(1, a1 - a0)
            parts.append(f'허스크 이동 속도 {100 * mult:.0f}% · 초당 {pace:.2f}블록')
            if mult < .7: return ' · '.join(parts), (255, 150, 150)
    if os.environ.get('FILM_FINGER_DUMMY') == '0': return (' · '.join(parts), (255, 255, 255)) if parts else (None, None)
    full = next((float(x['dummy_health']) for _, x in rush if x['dummy_health']), None)
    if r['dummy_health'] and full: parts.append(f'더미 체력 {float(r["dummy_health"]):.0f} / {full:.0f} (피해 {full - float(r["dummy_health"]):.0f})')
    if r.get('dummy_broken') == '1': parts.append('가드 깨짐 · 무력화')
    elif r.get('dummy_disabled') == '1': parts.append('무력화')
    elif r.get('dummy_guard') == '1': parts.append('가드 중')
    base = max((float(x['dummy_speed']) for _, x in rush if x.get('dummy_speed')), default=0)
    if r.get('dummy_speed') and base > 0 and float(r['dummy_speed']) < base * .7 and r.get('dummy_guard') != '1':
        parts.append(f'이동 속도 {100 * float(r["dummy_speed"]) / base:.0f}%')
    color = (255, 150, 150) if r.get('dummy_disabled') == '1' or r.get('dummy_broken') == '1' else (255, 255, 255)
    return (' · '.join(parts), color) if parts else (None, None)

def inhale_line(t):
    """The inhale (Y; 2.61.0, held down since 2.62.0): what it does now, whether it holds anyone, and the training dummy
    (where it is in front of the player, its health: the inhale deals no damage, incapacitated) or the first husk (act=:
    how far it has been dragged while held, and its speed once let go: 95% slower for half a second)."""
    if not rush: return None, None
    i = bisect.bisect_right([a for a, _ in rush], t * 20 + 1e-6) - 1
    if i < 0: return None, None
    r, parts = rush[i][1], []
    if r.get('inhale_age'):
        age, on = float(r['inhale_age']), r.get('inhale_on') == '1'
        caught, blocked = int(r.get('inhale_caught') or 0), int(r.get('inhale_blocked') or 0)
        sucked = f'{min(5.0, max(0.0, age - .4)):.1f}초 / 최대 5초'
        if on and age < .4: parts.append('Y 누름 · 숨을 크게 들이쉼')
        elif on and caught: parts.append(f'흡입 {sucked} · 1명을 붙잡아 끌어옴')
        elif on and blocked: parts.append(f'흡입 {sucked} · 가드가 버팀, 안 잡힘')
        elif on: parts.append(f'흡입 {sucked} · 아무도 안 잡힘')
        else: parts.append('흡입 끝')
    if walkers:
        k = bisect.bisect_right(walkers, (t * 20 + 1e-6, 1e9, 0, 0)) - 1
        # (a husk standing still from the start has no speed of its own to measure: NaN)
        if k >= 0 and not math.isnan(walkers[k][3]):
            speed = walkers[k][3]
            if speed < .5: parts.append(f'허스크 이동 속도 {100 * speed:.0f}% (무력화)')
            elif k > 0 and ((walkers[k][1] - walkers[k - 1][1]) ** 2 + (walkers[k][2] - walkers[k - 1][2]) ** 2) ** .5 > .02: parts.append('허스크가 다시 걸어감')
    if os.environ.get('FILM_INHALE_DUMMY') != '0' and r.get('dummy_ahead'):
        parts.append(f'더미: 플레이어 앞 {float(r["dummy_ahead"]):.1f}블록')
        full = next((float(x['dummy_health']) for _, x in rush if x['dummy_health']), None)
        if r['dummy_health'] and full: parts.append(f'체력 {float(r["dummy_health"]):.0f}/{full:.0f} (피해 {full - float(r["dummy_health"]):.0f})')
        if r.get('dummy_disabled') == '1': parts.append('무력화')
        elif r.get('dummy_guard') == '1': parts.append('가드 중')
    color = (255, 150, 150) if (r.get('dummy_disabled') == '1' and os.environ.get('FILM_INHALE_DUMMY') != '0') or any('무력화' in p for p in parts) else (255, 255, 255)
    return (' · '.join(parts), color) if parts else (None, None)

# The beatdown's cutscene (2.64.0; 2.65.0's timeline; 2.66.0's shots from the user's video; 2.66.1's "裁くのは"): its parts by its own clock
# (seconds from G, StarPlatinumRules.BEAT_*)
BEAT_PARTS = [(1.8, '적중 · 컷씬 시작 (레터박스)'), (3.25, '야레야레다제 · 머리를 잡고 흔듦'), (4.4, '스타플래티나! · 스탠드 등장'),
              (4.98, '멱살을 잡음'), (5.3, '올려치기'), (5.55, '러시 · 스탠드 얼굴'), (5.8, '러시 · 주먹이 렌즈로 (상대 시점)'),
              (7.0, '러시 · 상대 시점'), (7.85, '러시 · 어깨 너머 위에서'), (9.0, '러시 · 스탠드 옆에서 상대 얼굴'),
              (9.75, '"심판하는 건" · 상대 등 너머 스탠드 얼굴'), (10.41, '러시 · 옆에서'), (10.89, '"나의" · 두 얼굴'),
              (11.45, '"스탠드" · 강펀치를 당김'), (11.75, '"다!" · 얼굴에 강펀치'), (12.3, '날아감'), (13.1, '착지 · 일어남'), (99, '컷씬 끝')]
# (the same moments without the camera's shots, for a capture from outside: FILM_BEAT_SHOTS=0)
BEAT_MOMENTS = [(1.8, '적중 · 컷씬 시작 (레터박스)'), (3.25, '야레야레다제 · 머리를 잡고 흔듦'), (4.4, '스타플래티나! · 스탠드 등장'),
                (4.98, '멱살을 잡음'), (5.15, '올려치기'), (9.0, '러시 · 얼굴을 맞대고 주먹마다 꺾임'),
                (10.89, '러시 · "심판하는 건 나의"'), (11.45, '"스탠드" · 강펀치를 당김'), (12.3, '"다!" · 얼굴에 강펀치'),
                (13.1, '착지 · 일어남'), (99, '컷씬 끝')]

def beatdown_line(t):
    """The beatdown (G): its blow, the part of its cutscene, the training dummy's health (the damage dealt), or its guard broken."""
    if not rush: return None, None
    i = bisect.bisect_right([a for a, _ in rush], t * 20 + 1e-6) - 1
    if i < 0: return None, None
    r, parts = rush[i][1], []
    if r.get('beat_age'):
        age = float(r['beat_age'])
        name = next(n for until, n in (BEAT_MOMENTS if os.environ.get('FILM_BEAT_SHOTS') == '0' else BEAT_PARTS) if age < until)
        if r.get('beat_cut') == '1' and r.get('beat_ended') == '1': name = '컷씬이 끊김'
        parts.append(f'컷씬 {age:.1f}초 · {name}')
    elif r.get('combo') == '7':
        # (the dummy's guard is seen broken a tick after the blow lands: the shot's rows from then on tell)
        broke = any(x.get('dummy_broken') == '1' for _, x in rush[i:i + 4])
        if r['landed'] != '1': parts.append('G · 크게 당겨 0.8초에 꽂음')
        elif int(r['struck'] or 0) > 0: parts.append('적중')
        elif broke: parts.append('가드 깨짐 (컷씬 없음) · 무력화')
        else: parts.append('빗나감 (컷씬 없음) · 쿨타임 시작')
    # (2.65.0: the user's body glowing sky blue as they use it)
    if float(r.get('flash') or 0) > .02: parts.append('하늘색 빛남')
    full = next((float(x['dummy_health']) for _, x in rush if x['dummy_health']), None)
    if r['dummy_health'] and full and os.environ.get('FILM_BEAT_DUMMY') != '0': parts.append(f'더미 {float(r["dummy_health"]):.0f}/{full:.0f} (피해 {full - float(r["dummy_health"]):.0f})')
    color = (255, 150, 150) if any('가드 깨짐' in p for p in parts) else (255, 255, 255)
    return (' · '.join(parts), color) if parts else (None, None)

def timestop_line(t):
    """The time stop (H, 2.67.0): called, stopped (how long of its 5 s), moving again; the training dummy's health and,
    with FILM_TS_PLAYER=1, the player's own (one it stopped: their screen held still while time is stopped)."""
    if not rush: return None, None
    i = bisect.bisect_right([a for a, _ in rush], t * 20 + 1e-6) - 1
    if i < 0: return None, None
    r, parts = rush[i][1], []
    if r.get('ts_age'):
        age, ended = float(r['ts_age']), float(r.get('ts_ended') or -1)
        if r.get('ts_cut') == '1': parts.append('시간 정지가 끊김')
        elif r.get('ts_stopped') == '1': parts.append(f'시간 정지 {min(5.0, max(0.0, age - 1.95)):.1f}초 / 5초')
        elif ended >= 0: parts.append('시간이 다시 흐름 · 쌓인 피해가 한꺼번에')
        else: parts.append('H · "스타플래티나 더 월드!"')
    if os.environ.get('FILM_TS_PLAYER') == '1' and r.get('player_health'):
        full = max(float(x['player_health']) for _, x in rush if x.get('player_health'))
        h = float(r['player_health'])
        parts.append(f'내 체력 {h:.0f}/{full:.0f}' + (f' (피해 {full - h:.0f})' if full - h > .5 else ''))
        if r.get('ts_still') == '1': parts.append('내 화면이 멈춤')
    if os.environ.get('FILM_TS_DUMMY') != '0' and r.get('dummy_health'):
        full = next((float(x['dummy_health']) for _, x in rush if x['dummy_health']), None)
        if full: parts.append(f'더미 체력 {float(r["dummy_health"]):.0f}/{full:.0f}' + (f' (피해 {full - float(r["dummy_health"]):.0f})' if full - float(r['dummy_health']) > .5 else ''))
    # (2.68.0) while time is stopped: each husk, how far from where time stopped and whether it stands stopped or walks on
    if r.get('ts_stopped') == '1' and husks:
        age, seen = int(t * 20), []
        for name, at in husks.items():
            now, before = at.get(age), at.get(age - 2)
            if now and before:
                moved = math.hypot(now[0] - before[0], now[1] - before[1])
                seen.append((now[2], f'{now[2]:.0f}블록 ' + ('멈춤' if moved < .01 else '걸어감')))
        if seen: parts.append('허스크 ' + ', '.join(w for _, w in sorted(seen)))
    color = (150, 190, 255) if r.get('ts_stopped') == '1' else (255, 255, 255)
    return (' · '.join(parts), color) if parts else (None, None)

def stagger_line(t):
    """The basic attack's stagger (2.68.0): the dummy's blows, the player staggered (walking only, a tenth of their
    speed) or free, their speed and how fast they walk, the dummy staggered."""
    if not rush: return None, None
    i = bisect.bisect_right([a for a, _ in rush], t * 20 + 1e-6) - 1
    if i < 0: return None, None
    r, parts = rush[i][1], []
    k = int(r.get('dummy_strike') or 0)
    if 1 <= k <= 4: parts.append(f'더미 평타 {k}타')
    # the player's own Star Platinum punches (their combo's punch)
    # (while it lasts: StarPlatinumRules.LENGTH, 10, 10, 17 and 23 ticks; the column keeps the last punch's age on)
    c = int(float(r.get('combo') or 0))
    if 1 <= c <= 4 and r.get('punch_age') and float(r['punch_age']) <= (.5, .5, .85, 1.15)[c - 1]: parts.append(f'스타플래티나 평타 {c}타')
    base = max(float(x['speed_attr']) for _, x in rush if x.get('speed_attr'))
    sp = float(r['speed_attr']) if r.get('speed_attr') else base
    if r.get('player_staggered') == '1': parts.append(f'내가 경직 · 행동 불가 · 이동 속도 {100 * sp / base:.0f}%')
    else: parts.append('나: 행동 가능')
    speed = walking(t)
    # (faster than a sprint, or than a tenth of a walk while staggered, is the fourth blow's knockback, not a walk)
    if speed is not None and (speed > 5.7 or r.get('player_staggered') == '1' and speed > 1.2): parts.append('4타에 밀려남')
    elif speed is not None and speed > .05: parts.append(f'걷는 속도 초당 {speed:.2f}블록')
    if r.get('dummy_staggered') == '1': parts.append('더미 경직 · 행동 불가')
    # the training dummy's health once it has been struck (the answer landing)
    full = next((float(x['dummy_health']) for _, x in rush if x.get('dummy_health')), None)
    if full and r.get('dummy_health') and full - float(r['dummy_health']) > .5: parts.append(f'더미 체력 {float(r["dummy_health"]):.0f}/{full:.0f}')
    color = (255, 190, 150) if r.get('player_staggered') == '1' else (150, 255, 190) if r.get('dummy_staggered') == '1' else (255, 255, 255)
    return ' · '.join(parts), color

# the boss's words for the moves it reads (BossThreats) and its own (JotaroBrain)
MOVES = {'punch1': '평타', 'punch2': '평타', 'punch3': '평타', 'punch4': '평타 4타', 'punch5': '강펀치', 'punch6': '스타 핑거',
         'punch7': '비트다운', 'rush': '러시', 'inhale': '흡입', 'time_stop': '시간 정지', 'atomic': '아토믹'}
def boss_doing(d):
    """What the boss does, from boss.csv's words: its own plan, or how it answers a player's move (in brackets)."""
    if not d or d == '-': return ''
    w = d.split(' ')
    if len(w) >= 2 and w[-1] in MOVES: return ' '.join(w[:-1]) + f' ({MOVES[w[-1]]})'
    if len(w) >= 2 and (w[-1].startswith('kageno') or w[-1].startswith('bare')): return ' '.join(w[:-1]) + (' (맨손 평타)' if w[-1].startswith('bare') else ' (카게노)')
    if w[0] == '콤보' and len(w) == 2: return '콤보' + (f' {w[1]}타' if w[1] != '0' else '')
    return d
def boss_line(t):
    """Kujo Jotaro (2.69.0): neutral or angry, what he does (his own plan, or how he answers the player's move), whom he
    goes after, his health, the player's (and the second player's) health, the boxes he left."""
    if not rush: return None, None
    i = bisect.bisect_right([a for a, _ in rush], t * 20 + 1e-6) - 1
    if i < 0: return None, None
    r, parts = rush[i][1], []
    if r.get('health'):
        parts.append(('죠타로 화남' if r.get('angry') == '1' else '죠타로 중립(가만히 서 있음)'))
        doing = boss_doing(r.get('doing', ''))
        # held where he stands calling his time stop or in his beatdown's cutscene (the brain before 2.69.0's release
        # said only "묶임")
        j = bisect.bisect_right([a for a, _ in stops], t * 20 + 1e-6) - 1
        seen = stops[j][1] if j >= 0 and t * 20 - stops[j][0] < 1.5 else {}
        if doing == '묶임' and seen.get('ts_age') and seen.get('ts_stopped') != '1' and float(seen.get('ts_ended') or -1) < 0: doing = '시간 정지 외침'
        elif doing == '묶임' and (seen.get('beat_camera') == '1' or seen.get('beat_age') and seen.get('beat_ended') != '1'): doing = '비트다운 컷씬'
        if doing and r.get('angry') == '1': parts.append(doing)
        who = r.get('target') or ''
        if who and r.get('angry') == '1': parts.append('대상 ' + ('나' if who == 'ShotsQA' else '동료'))
        parts.append(f'보스 체력 {float(r["health"]):,.0f}/5,000')
    elif r.get('boxes') and int(r['boxes']) > 0: parts.append(f'죠타로 처치 · 박스 {int(r["boxes"])}개')
    if r.get('player_health'): parts.append(f'내 체력 {float(r["player_health"]):.0f}')
    if r.get('mate_health'): parts.append(f'동료 체력 {float(r["mate_health"]):.0f}')
    color = (255, 170, 160) if r.get('angry') == '1' else (255, 255, 255)
    return ' · '.join(parts), color

STANDS = {'star_platinum': '스타플래티나', 'kageno': '카게노', 'none': '없음'}
def arrow_line(t):
    """The stand arrow (2.70.0; ten seconds since 2.71.0): its part by the stab's own clock (ArrowStabPose: looked at,
    doubted, the deep breath, raised to the sky, aimed and trembling, driven in at 5.4 s, the gasp, down on the knees,
    taken into the body, still, its choice at 9.0 s), what it chose, the player's stand and the arrows in their hand."""
    if not rush: return None, None
    i = bisect.bisect_right([a for a, _ in rush], t * 20 + 1e-6) - 1
    if i < 0: return None, None
    r, parts = rush[i][1], []
    color = (255, 255, 255)
    # (a stab of the entry before, still kept by the client a while after it: not this one)
    if r.get('arrow_age') and float(r['arrow_age']) * 20 <= float(r['t']) + 1:
        a, fate = float(r['arrow_age']), r.get('arrow_fate')
        if r.get('arrow_cut') == '1': parts.append('중간에 끊김 (화살은 그대로)')
        elif fate == '0' or a < 9.0:
            stages = [(1.75, '화살을 들어 바라봄'), (2.6, '망설임'), (3.1, '숨을 깊이 들이쉼'), (4.35, '하늘로 치켜듦'), (5.18, '가슴을 겨눔 · 떨림'),
                      (5.6, '가슴에 꽂음'), (7.0, '헐떡임'), (7.45, '무릎이 꺾임'), (8.82, '화살이 몸속으로 · 심장 박동'), (99, '정적')]
            parts.append(next(name for until, name in stages if a < until))
            color = (255, 225, 140)
        elif fate == '1':
            parts.append('선택받음 · 각성' + (' · 일어섬' if a > 10.2 else '')); color = (200, 180, 255)
        else:
            parts.append('선택받지 못함 · ' + ('경련' if a < 9.75 else '쓰러짐' if a < 10.3 else '사망'))
            color = (255, 140, 140)
        parts.append(f'사용 후 {a:.1f}초')
    if r.get('server_stand') is not None and r.get('server_stand') != '': parts.append('스탠드 ' + STANDS.get(r['server_stand'], r['server_stand']))
    if r.get('arrows') not in (None, ''): parts.append(f'화살 {r["arrows"]}개')
    return ' · '.join(parts), color

def actor_line(t):
    if mode == 'arrow': return arrow_line(t)
    if mode == 'boss': return boss_line(t)
    if mode == 'stagger': return stagger_line(t)
    if mode == 'timestop': return timestop_line(t)
    if mode == 'beatdown': return beatdown_line(t)
    if mode == 'inhale': return inhale_line(t)
    if mode == 'finger': return finger_line(t)
    if mode == 'heavy': return heavy_line(t)
    if mode == 'rush': return rush_line(t)
    if mode == 'motion': return motion_line(t)
    if mode == 'wall': return wall_line(t)
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
    speed = '' if SPEED == 1 else f' · {SPEED:g}배속'
    name = stage(t)
    lines = [(title, big, (255, 255, 255)), (f'{CLOCK} {max(0, t):.{2 if SKILL == "E" else 1}f}초{" · " + name if name else ""}{speed}', small, (255, 230, 120))]
    text, color = actor_line(t)
    y = 10
    if BELOW:
        # the band under the picture: the title, and under it the time and the stage
        y = img.height - BELOW + 6
        d.text((12, y), lines[0][0], font=big, fill=lines[0][2]); d.text((12, y + 36), lines[1][0], font=small, fill=lines[1][2])
        if text: d.text((12, y + 68), text, font=small, fill=color)
        lines, text = [], None
    for s, font, col in lines:
        w = d.textlength(s, font=font); h = font.size + 10
        # FILM_CAPTION=center puts the captions at the top's middle (the gacha keeps its logo at the top left)
        x = (img.width - w - 16) / 2 if os.environ.get('FILM_CAPTION') == 'center' else 8
        d.rectangle((x, y, x + w + 16, y + h), fill=(0, 0, 0, 150)); d.text((x + 8, y + 4), s, font=font, fill=col); y += h + 4
    if text:
        w = d.textlength(text, font=big); h = big.size + 14; x = (img.width - w) / 2; yb = img.height - h - 14
        d.rectangle((x - 12, yb, x + w + 12, yb + h), fill=(0, 0, 0, 160)); d.text((x, yb + 6), text, font=big, fill=color)

tmp = tempfile.mkdtemp(dir=os.path.dirname(os.path.abspath(out)))
try:
    n, t, used = 0, start, []
    while t <= end:
        i = bisect.bisect_left(times, t); i = min(range(max(0, i - 1), min(len(times), i + 1)), key=lambda j: abs(times[j] - t)); used.append(i)
        img = Image.open(frames[i][1]).convert('RGB'); img = img.resize((W, round(img.height * W / img.width / 2) * 2), Image.LANCZOS)
        if BELOW:
            band = Image.new('RGB', (img.width, img.height + BELOW), (12, 12, 16)); band.paste(img, (0, 0)); img = band
        caption(img, t); img.save(os.path.join(tmp, f'{n:05d}.png')); n += 1; t = start + n * SPEED / FPS
    subprocess.run(['ffmpeg', '-loglevel', 'error', '-y', '-framerate', str(FPS), '-i', os.path.join(tmp, '%05d.png'), '-c:v', 'libx264', '-pix_fmt', 'yuv420p',
                    '-crf', '23', '-preset', 'slow', '-movflags', '+faststart', out], check=True)
finally:
    shutil.rmtree(tmp, ignore_errors=True)
repeats = sum(1 for a, b in zip(used, used[1:]) if a == b)
print(out, n, 'frames at', FPS, 'fps,', repeats, 'repeat the game frame before')
