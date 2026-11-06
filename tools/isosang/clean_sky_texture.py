#!/usr/bin/env python3
"""Takes the piece of the videos' character out of G's sky texture (2.87.3).

    python3 -I tools/isosang/clean_sky_texture.py src/main/resources/assets/celestialfall/textures/effect/isosang_g_cloud_material.png

The user's sky-shot texture (isosang_g_cloud_material.png, 1536 x 708, laid on the sky shot's frame about one to one)
was made from the videos' frame and still held the bottom of the character there: the edge of her navy skirt with
its white stripe, the ends of her hair and the gold tassel (x 492..652, y 543..708, in all three colour channels).
In 2.87.0 to 2.87.2 the figure drawn over it hid most of it; 2.87.3's smaller figure, moved left, leaves it in the
sky. This fills that patch from its edges inwards (OpenCV's Navier-Stokes inpainting), softens the seam where the
light clouds on its left meet the dark band on its right, and lays the clouds' own fine grain (from the same rows
180 pixels to the right) faintly over it. The alpha channel (0 there) is left as it is. It changes the file in
place; run it once, on the user's texture (it does nothing a second time but smooth the patch again).

Needs numpy and opencv-python-headless.
"""
import sys

import cv2
import numpy as np
from PIL import Image

# the piece: a polygon round the skirt and its dark fringe, and the darker-than-the-clouds marks near it (the hair)
POLYGON = [(492, 708), (512, 668), (534, 628), (552, 590), (562, 552), (600, 543), (648, 554), (652, 640), (646, 708)]
NEAR = (552, 708, 505, 645)  # rows, columns where the hair and the tassel are looked for


def mask_of(rgba):
    h, w = rgba.shape[:2]
    r, g = rgba[..., 0].astype(np.float32), rgba[..., 1].astype(np.float32)
    y0, y1, x0, x1 = NEAR
    box = np.zeros((h, w), bool)
    box[y0:y1, x0:x1] = True
    dark = box & ((r < cv2.GaussianBlur(r, (0, 0), 25) - 18) | (g < cv2.GaussianBlur(g, (0, 0), 25) - 14))
    dark = dark.astype(np.uint8)
    dark[688:708, 512:556] = 1  # the tassel
    dark = cv2.morphologyEx(dark, cv2.MORPH_CLOSE, np.ones((9, 9), np.uint8))
    dark = cv2.dilate(dark, np.ones((7, 7), np.uint8))
    n, lab, stats, _ = cv2.connectedComponentsWithStats(dark, 8)
    core = np.zeros_like(dark)
    for i in range(1, n):
        if stats[i, cv2.CC_STAT_AREA] >= 600:
            core[lab == i] = 1
    poly = np.zeros((h, w), np.uint8)
    cv2.fillPoly(poly, [np.array(POLYGON, np.int32)], 1)
    return ((poly | cv2.dilate(core, np.ones((11, 11), np.uint8))) > 0).astype(np.uint8)


def main():
    path = sys.argv[1]
    im = np.array(Image.open(path).convert('RGBA'))
    mask = mask_of(im)
    rgb = im[..., :3].copy()
    fill = cv2.inpaint(rgb, mask * 255, 9, cv2.INPAINT_NS).astype(np.float32)
    soft = cv2.GaussianBlur(fill, (0, 0), 9)
    inside = cv2.GaussianBlur(cv2.erode(mask, np.ones((9, 9), np.uint8)).astype(np.float32), (0, 0), 5)[..., None]
    fill = fill * (1 - inside) + soft * inside
    src = np.roll(rgb.astype(np.float32), -180, axis=1)
    grain = src - cv2.GaussianBlur(src, (0, 0), 5)
    feather = cv2.GaussianBlur(mask.astype(np.float32), (0, 0), 3)[..., None]
    res = np.clip(fill + grain * 0.9 * feather, 0, 255)
    edge = cv2.GaussianBlur(mask.astype(np.float32), (0, 0), 1.5)[..., None]
    out = im.copy()
    out[..., :3] = np.clip(rgb * (1 - edge) + res * edge, 0, 255).astype(np.uint8)
    Image.fromarray(out, 'RGBA').save(path)
    print(path, 'patched', int(mask.sum()), 'pixels')


if __name__ == '__main__':
    main()
