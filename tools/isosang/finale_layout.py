"""Where H again's waiting swords come out of the air on her behind camera's screen (2.101.8).

The user drew a red line on a game still of that shot ("빨간선 아래에 전부 골고루 배치하면 돼"); the line, traced
from the drawing (960x540, pixels), is RED_LINE. The swords are spread evenly under it (Lloyd relaxation over the
screen below the line, kept a little off the frame's sides and bottom and off the line), and printed as Java arrays
(-1 to 1 across and up the screen, 16:9) for SwordFinale.SPOTS_27 and SPOTS_28.

    python3 -I tools/isosang/finale_layout.py
"""
import numpy as np

W, H = 960, 540
RED_LINE = [(0, 28), (10, 30), (50, 53), (110, 78), (190, 107), (270, 136), (350, 160), (430, 179), (510, 207),
            (590, 238), (670, 259), (750, 283), (830, 314), (890, 328), (960, 338)]
SIDE, BOTTOM, BELOW = 0.07 * H, 0.1 * H, 0.06 * H


def yline(x):
    return np.interp(x, [p[0] for p in RED_LINE], [p[1] for p in RED_LINE])


def inside(x, y):
    return (x > SIDE) & (x < W - SIDE) & (y < H - BOTTOM) & (y > yline(x) + BELOW)


def spots(n, rounds=80):
    xs, ys = np.meshgrid(np.arange(0, W, 3) + 1.5, np.arange(0, H, 3) + 1.5)
    xs, ys = xs.ravel(), ys.ravel()
    keep = inside(xs, ys)
    samples = np.stack([xs[keep], ys[keep]], 1)
    start, k = [], 0
    while len(start) < n:
        k += 1
        x, y = SIDE + (k * 0.7548776662) % 1 * (W - 2 * SIDE), (k * 0.5698402910) % 1 * H
        if inside(x, y):
            start.append((x, y))
    p = np.array(start, float)
    for _ in range(rounds):
        nearest = ((samples[:, None, :] - p[None, :, :]) ** 2).sum(2).argmin(1)
        for i in range(n):
            mine = samples[nearest == i]
            if len(mine):
                p[i] = mine.mean(0)
    return p


def java(name, p):
    rows = ','.join('{%.3f,%.3f}' % (x / (W / 2) - 1, 1 - y / (H / 2)) for x, y in p)
    return 'static final double[][] %s={%s};' % (name, rows)


if __name__ == '__main__':
    print('static final double[][] RED_LINE={%s};' % ','.join('{%.3f,%.3f}' % (x / (W / 2) - 1, 1 - y / (H / 2)) for x, y in RED_LINE))
    for n in (27, 28):
        print(java('SPOTS_%d' % n, spots(n)))
