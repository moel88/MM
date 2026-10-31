#!/bin/bash
# Films of the 2.78.0 shots (shots22): the whole combo from above (the user's camera), behind, the side and the game
# camera; the four hits at half speed from above and behind; no sound (a fix).
set -e
cd /home/user/MM; W=/home/user/isosang_work; IN=${IN:-$W/shots22}; OUT=${OUT:-$W/out22}; mkdir -p $OUT
export FILM_WIDTH=960 FILM_FPS=30 FILM_SKILL=ISOSANG FILM_CAPTION=below
VER=${VER:-2.78.0}
# the taps: hit 1 at tick 20 (1.0 s), hit 2 at 36 (1.8), hit 3 at 51 (2.55), hit 4 at 81 (4.05)
NOTES="0.0-1.05:서 있는 자세|1.05-1.34:1타 · 칼이 손에, 들어 올려 왼쪽 위에 감음|1.34-1.45:1타 · 오른쪽으로 도는 한 바퀴 베기(앞왼쪽→앞→오른쪽→뒤왼쪽), 끝이 굵고 먹|1.45-1.85:1타 · 되올려베기, 검격이 가늘어지며 마름, 뒤왼쪽 바닥의 먹|1.85-2.05:2타 · 웅크려 오른쪽으로 돎|2.05-2.3:2타 · 앞에서 시작하는 원(넓은 흰빛→파랑)|2.3-2.6:2타 · 원 안의 먹 웅덩이, 오른쪽 뒤의 먹 초승달|2.6-2.9:3타 · 고리 1(앞이 높음) → 앞으로 올라가는 빛줄기|2.9-3.2:3타 · 되감기 → 고리 2|3.2-3.4:3타 · 고리 3(빛줄기와 X) → 가슴 높이 가는 호|3.4-3.9:3타 · 고리 4(가장 큼), 뒤왼쪽 바닥의 먹|3.9-4.4:3타의 검격이 마름|4.4-4.75:4타 · 칼 돌리기(옅은 띠) → 겨눔|4.75-5.05:4타 · 웅크림·돌진·낮은 가로베기|5.05-6.2:4타 · 바닥의 먹 고리, 넓고 뒤로 기운 참격이 천천히 나아감|6.2-7.3:참격이 멀리서 마름, 쉼 1.2초('평타')"
if [ "${1:-all}" = all ] || [ "$1" = combo ]; then
FILM_CLOCK="장면" FILM_NOTES="$NOTES" python3 -I tools/kageno/shots/film.py $IN/SUMMON.2 $OUT/top.mp4 0.8 7.3 "이소상 평타 ($VER) · 위에서 내려다본 카메라(보내 주신 영상의 구도)" 2>&1 | tail -1
FILM_CLOCK="장면" FILM_NOTES="$NOTES" python3 -I tools/kageno/shots/film.py $IN/SUMMON.3 $OUT/behind.mp4 0.8 7.3 "이소상 평타 ($VER) · 뒤에서 내려다본 카메라(HUD와 쿨타임 목록)" 2>&1 | tail -1
FILM_CLOCK="장면" FILM_NOTES="$NOTES" python3 -I tools/kageno/shots/film.py $IN/SUMMON.4 $OUT/side.mp4 0.8 7.3 "이소상 평타 ($VER) · 옆에서 본 카메라" 2>&1 | tail -1
FILM_CLOCK="장면" FILM_NOTES="$NOTES" python3 -I tools/kageno/shots/film.py $IN/SUMMON.5 $OUT/game.mp4 0.8 7.3 "이소상 평타 ($VER) · 게임 카메라(쓰는 사람의 3인칭)" 2>&1 | tail -1
fi
if [ "${1:-all}" = all ] || [ "$1" = hits ]; then
export FILM_SPEED=0.5
for cam in 2 3; do
  name=$([ $cam = 2 ] && echo top || echo behind); title=$([ $cam = 2 ] && echo "위에서" || echo "뒤에서")
  FILM_NOTES="0.95-1.05:서 있는 자세|1.05-1.12:칼이 손에 번쩍 나타남(왼쪽 아래)|1.12-1.3:오른쪽 위로 들어 머리 위로, 왼쪽 위에 감아 웅크림|1.3-1.36:오른쪽으로 도는 한 바퀴 베기(적중 1.35초): 앞왼쪽→앞→오른쪽→뒤→뒤왼쪽, 꼬리는 가늘고 머리는 굵게|1.36-1.45:머리 쪽 안에 먹, 뒤왼쪽 바닥에 먹 웅덩이|1.45-1.55:되올려베기(가는 줄기), 칼을 위로|1.55-1.85:검격이 가늘어지며 짙은 파랑으로 마름|1.85-2.0:2타로 이어짐" \
  python3 -I tools/kageno/shots/film.py $IN/SUMMON.$cam $OUT/hit1_$name.mp4 0.95 1.95 "이소상 평타 1타 · 참월 ($VER) · $title" 2>&1 | tail -1
  FILM_NOTES="1.8-1.9:1타 끝|1.9-1.95:왼쪽으로 감았다가|1.95-2.05:오른쪽으로 한 바퀴 돌며 원(1타격 2.0초): 앞→오른쪽→뒤→왼쪽, 갓 그은 곳은 넓은 흰빛|2.05-2.15:원이 파란 띠로, 안쪽에 검은 붓결|2.15-2.3:원이 먹으로 터짐(2타격 2.15초), 원 안 바닥이 어두워짐|2.3-2.6:오른쪽 뒤에 먹 초승달, 띠는 가늘어져 사라짐|2.6-2.75:3타로 이어짐" \
  python3 -I tools/kageno/shots/film.py $IN/SUMMON.$cam $OUT/hit2_$name.mp4 1.8 2.75 "이소상 평타 2타 · 윤월 ($VER) · $title" 2>&1 | tail -1
  FILM_NOTES="2.5-2.6:2타 끝|2.6-2.73:왼쪽으로 감았다가 고리 1(앞이 높음, 앞왼쪽→오른쪽→뒤)|2.73-2.85:뒤에서 앞으로 올라가는 긴 빛줄기(적중 2.85초)|2.85-3.04:칼을 왼쪽으로 되감음|3.04-3.11:고리 2(적중 3.05초), 뒤왼쪽에서 굵게 끝남|3.11-3.24:고리 3(앞왼쪽이 높음, 적중 3.25초), 빛줄기와 X|3.24-3.4:가슴 높이의 가는 호(적중 3.35초)|3.4-3.52:고리 4(가장 큼, 적중 3.45초), 뒤왼쪽 바닥의 먹|3.52-4.0:검격이 차례로 마름|4.0-4.1:4타로 이어짐" \
  python3 -I tools/kageno/shots/film.py $IN/SUMMON.$cam $OUT/hit3_$name.mp4 2.5 4.05 "이소상 평타 3타 · 관월 ($VER) · $title" 2>&1 | tail -1
  FILM_NOTES="3.95-4.05:3타 끝|4.05-4.37:머리 위에서 칼을 한 바퀴 돌림, 옅은 띠가 남음|4.37-4.63:왼손으로 겨누고 칼은 뒤로(칼날의 옅은 빛)|4.63-4.75:웅크렸다가 돌진|4.75-4.8:낮게 가로베어 참격을 날림(4.75초), 바닥에 먹 고리|4.8-5.5:넓고 뒤로 기운 참격이 천천히 나아가며 넓어짐, 뒤로 뻗는 빛줄기, 뒤 가장자리의 먹|5.5-6.2:참격이 멀리서 먹으로 마름|6.2-6.9:1.2초 쉼" \
  python3 -I tools/kageno/shots/film.py $IN/SUMMON.$cam $OUT/hit4_$name.mp4 3.95 6.9 "이소상 평타 4타 · 낙월 ($VER) · $title" 2>&1 | tail -1
done
fi
ls -la $OUT
