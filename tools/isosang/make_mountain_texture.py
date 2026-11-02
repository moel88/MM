#!/usr/bin/env python3
"""The mountain of Lee So-sang's H (2.82.16): the user's painted mountain made into the game's texture.

User request: "이거 쓰면 되는데 투명하게 다듬으면 될듯", with their painted mountain (a PNG with its own transparency: the
background clear, the mountain all but opaque). The painting itself is not redrawn; this only:
  - crops it to the mountain;
  - makes its ink body a little see-through and a little darker, its rims of light kept as they are (earlier: "산을 더
    투명하게 만들고 테두리 제외 산을 더 어둡게");
  - frays its foot away over its lowest FOOT of its height (it floats: "영상에는 산이 중간에 떠있는데");
  - scales it down to WIDTH pixels across in premultiplied alpha (no dark fringe at its edges) and pads it with a clear
    border of PAD pixels (no clamped edge pixels smeared).

python3 -I tools/isosang/make_mountain_texture.py mountain.png \
    src/main/resources/assets/celestialfall/textures/effect/isosang_mountain.png
"""
import sys

import numpy as np
from PIL import Image, ImageFilter

BODY_ALPHA = .78   # how strongly its ink body shows (1 opaque); its rims stay as painted
BODY_DARK = .85    # its ink body's brightness, of the painting's
FOOT = .12         # of its height, its foot fraying away to nothing
FRAY = .35         # how ragged that is
WIDTH = 1536
PAD = 2
SEED = 8216


def resize(channel, size):
    return np.asarray(Image.fromarray(channel.astype(np.float32), mode='F').resize(size, Image.LANCZOS), dtype=np.float64)


def main(src, out):
    a = np.asarray(Image.open(src).convert('RGBA'), dtype=np.float64) / 255
    ys, xs = np.nonzero(a[..., 3] > .02)
    a = a[ys.min():ys.max() + 1, xs.min():xs.max() + 1]
    rgb, al = a[..., :3], a[..., 3]
    h, w = al.shape
    # how much of a rim of light each pixel is: bright and blue (the ink body is dark)
    rim = np.clip((rgb[..., 2] - .35) / .45, 0, 1) * np.clip((rgb.mean(2) - .2) / .35, 0, 1)
    rgb = rgb * (BODY_DARK + (1 - BODY_DARK) * rim[..., None])
    al = al * (BODY_ALPHA + (1 - BODY_ALPHA) * rim)
    # its foot frays away, raggedly (a soft noise, the same every time)
    rng = np.random.default_rng(SEED)
    small = (rng.random((h // 20 + 2, w // 20 + 2)) * 255).astype(np.uint8)
    noise = Image.fromarray(small).resize((w, h), Image.BICUBIC).filter(ImageFilter.GaussianBlur(6))
    noise = np.asarray(noise, dtype=np.float64) / 255 - .5
    noise = noise / max(1e-6, np.abs(noise).max()) * .5
    up = (h - 1 - np.arange(h))[:, None] / (FOOT * h)
    f = np.clip(up + FRAY * noise, 0, 1)
    al = al * f * f * (3 - 2 * f)
    # scaled down in premultiplied alpha, then unpremultiplied
    H = round(h * WIDTH / w)
    pa = resize(al, (WIDTH, H)).clip(0, 1)
    pc = [resize(rgb[..., k] * al, (WIDTH, H)) for k in range(3)]
    col = np.dstack([np.where(pa > 1e-4, c / np.maximum(pa, 1e-4), 0) for c in pc]).clip(0, 1)
    img = np.zeros((H + 2 * PAD, WIDTH + 2 * PAD, 4))
    img[PAD:PAD + H, PAD:PAD + WIDTH, :3] = col
    img[PAD:PAD + H, PAD:PAD + WIDTH, 3] = pa
    Image.fromarray((img * 255 + .5).astype(np.uint8), 'RGBA').save(out, optimize=True)
    print(out, img.shape[1], 'x', img.shape[0], 'aspect', round(img.shape[1] / img.shape[0], 4))


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
