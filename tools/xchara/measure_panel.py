#!/usr/bin/env python3
"""Measures X Chara's hack panel in a picture: its glowing rim's four corners and four sides.

For holding the game's panel against a reference video's (2.116.1): the same crop and the same measure on a frame of
each, filmed from the same camera, give sides whose lengths and directions can be compared; a difference in the long
sides' direction seen from in front is a difference in how far the panel's outer edge is raised.

usage: python3 -I tools/xchara/measure_panel.py PICTURE x0,y0,x1,y1 PX_PER_BLOCK [marked.png]
  the box is the part of the picture to look in (nothing else purple in it: not the knife);
  PX_PER_BLOCK turns the sides into blocks of the model (1 to leave them in pixels).
Needs numpy, scipy and Pillow.
"""
import sys

import numpy as np
from PIL import Image, ImageDraw
from scipy import ndimage
from scipy.spatial import ConvexHull


def main():
    if len(sys.argv) < 4:
        sys.exit(__doc__)
    picture, box, per = sys.argv[1], tuple(int(v) for v in sys.argv[2].split(',')), float(sys.argv[3])
    im = Image.open(picture).convert('RGB').crop(box)
    a = np.asarray(im).astype(float)
    r, g, b = a[..., 0], a[..., 1], a[..., 2]
    # the rim's light, and the dark face inside it (which joins the rim's sides into one piece)
    purple = (b > 150) & (r > 70) & (g < 0.55 * b) & (r < 1.05 * b)
    dark = (r < 70) & (g < 45) & (b < 110) & (b > g + 8)
    pieces, count = ndimage.label(purple | dark)
    if count == 0:
        sys.exit('nothing of the panel in that box')
    sizes = ndimage.sum(purple | dark, pieces, range(1, count + 1))
    ys, xs = np.nonzero((pieces == 1 + int(np.argmax(sizes))) & purple)
    points = np.stack([xs, ys], 1).astype(float)
    hull = points[ConvexHull(points).vertices]
    hull = hull[:: max(1, len(hull) // 28)]
    # the four of the hull's points that span the most
    best, quad = 0, None
    n = len(hull)
    for i in range(n):
        for j in range(i + 1, n):
            for k in range(j + 1, n):
                for l in range(k + 1, n):
                    q = hull[[i, j, k, l]]
                    x, y = q[:, 0], q[:, 1]
                    area = 0.5 * abs(np.dot(x, np.roll(y, 1)) - np.dot(y, np.roll(x, 1)))
                    if area > best:
                        best, quad = area, q
    print('corners:', [(round(float(p[0]) + box[0], 1), round(float(p[1]) + box[1], 1)) for p in quad])
    for i in range(4):
        d = quad[(i + 1) % 4] - quad[i]
        length = float(np.hypot(*d))
        print('  side %d: %6.1f px = %.3f blocks, %6.1f degrees up from level' % (i, length, length / per, np.degrees(np.arctan2(-d[1], d[0]))))
    print('middle:', (round(float(quad[:, 0].mean()) + box[0], 1), round(float(quad[:, 1].mean()) + box[1], 1)))
    if len(sys.argv) > 4:
        big = im.resize((im.width * 6, im.height * 6), Image.LANCZOS)
        ImageDraw.Draw(big).polygon([(float(p[0]) * 6, float(p[1]) * 6) for p in quad], outline=(0, 255, 0))
        big.save(sys.argv[4])


main()
