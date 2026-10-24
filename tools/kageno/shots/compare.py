"""Side-by-side sheet: original review frame (top) vs captured game frame (bottom).
usage: python3 compare.py SHOTS_DIR SKILL REF_VIDEO REF_T0 [CLOCK_OFFSET] [N] [CLOCK_FROM] [CLOCK_TO]
REF_T0 is the reference time (s) that matches cast clock 0; CLOCK_OFFSET is subtracted from the
captured clock before mapping (e.g. hold ticks before the cast starts).
"""
import sys, glob, os, subprocess, tempfile
shots, skill, video, t0 = sys.argv[1], sys.argv[2], sys.argv[3], float(sys.argv[4])
offset = float(sys.argv[5]) if len(sys.argv) > 5 else 0
n = int(sys.argv[6]) if len(sys.argv) > 6 else 15
lo = float(sys.argv[7]) if len(sys.argv) > 7 else -1e9
hi = float(sys.argv[8]) if len(sys.argv) > 8 else 1e9
frames = []
for f in sorted(glob.glob(os.path.join(shots, skill, '*.png'))):
    c = float(os.path.basename(f)[6:-4])
    if lo <= c <= hi: frames.append((c, f))
if not frames: sys.exit('no frames')
pick = [frames[round(i * (len(frames) - 1) / max(1, n - 1))] for i in range(min(n, len(frames)))]
tmp = tempfile.mkdtemp()
tiles = []
for i, (c, f) in enumerate(pick):
    rt = t0 + (c - offset) / 20
    ref = os.path.join(tmp, f'r{i}.png')
    subprocess.run(['ffmpeg', '-v', 'error', '-y', '-ss', f'{max(0, rt):.3f}', '-i', video, '-frames:v', '1', '-vf',
                    f"scale=400:-1,drawtext=text='ref {rt:.2f}':x=4:y=4:fontsize=18:fontcolor=yellow:box=1:boxcolor=black@0.6", ref], check=True)
    game = os.path.join(tmp, f'g{i}.png')
    subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', f, '-vf',
                    f"scale=400:225,drawtext=text='age {c - offset:.1f}':x=4:y=4:fontsize=18:fontcolor=cyan:box=1:boxcolor=black@0.6", game], check=True)
    pair = os.path.join(tmp, f'p{i}.png')
    subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', ref, '-i', game, '-filter_complex', '[0][1]vstack', pair], check=True)
    tiles.append(pair)
cols = 5
args = []
for t in tiles: args += ['-i', t]
layout = '|'.join(f'{(i % cols) * 400}_{(i // cols) * 450}' for i in range(len(tiles)))
out = os.path.join(shots, f'compare_{skill}.jpg')
subprocess.run(['ffmpeg', '-v', 'error', '-y', *args, '-filter_complex',
                ''.join(f'[{i}]' for i in range(len(tiles))) + f'xstack=inputs={len(tiles)}:layout={layout}:fill=black', out], check=True)
print(out)
