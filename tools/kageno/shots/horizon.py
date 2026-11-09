"""python3 -I tools/kageno/shots/horizon.py SHOT_DIR TICK [TICK ...]   (2.107.2)

The far horizon of a shot's frames (run_shots.py's UI shots on the flat test world: sky above, a strip of grass at the
world's edge): for the frame nearest each tick, how high the top of the grass strip stands (its median over the
columns right of the HUD: the screen zooms in, it moves away from the middle) and its slope (the view rolls, it tilts).
Used to see that the view neither rolls nor closes in while dead (CLAUDE.md, '죽으면 사망 화면 없이').
"""
import math
import os
import re
import statistics
import sys

from PIL import Image


def grass_top(px, x, height):
    for y in range(40, min(height // 2 + 60, height)):
        r, g, b = px[x, y][:3]
        if g > b + 10 and g > r:
            return y
    return None


def measure(path):
    image = Image.open(path).convert('RGB')
    px = image.load()
    width, height = image.size
    points = [(x, y) for x in range(int(width * 0.375), width - 10, 3) if (y := grass_top(px, x, height)) is not None]
    slopes = [(points[j][1] - points[i][1]) / (points[j][0] - points[i][0])
              for i in range(0, len(points), 3) for j in range(i + 20, len(points), 7)]
    return statistics.median(p[1] for p in points), math.degrees(math.atan(statistics.median(slopes))), len(points)


def main():
    shots = sys.argv[1]
    frames = sorted(f for f in os.listdir(shots) if f.endswith('.png'))
    timed = [(float(re.match(r'\d+_(\d+\.\d+)', f).group(1)), f) for f in frames]
    for want in map(float, sys.argv[2:]):
        tick, name = min(timed, key=lambda t: abs(t[0] - want))
        y, roll, columns = measure(os.path.join(shots, name))
        print(f'tick {tick:7.2f}: horizon y {y:6.1f}, roll {roll:+.2f} deg ({columns} columns)')


if __name__ == '__main__':
    main()
