#!/usr/bin/env python3
"""The mountains of Lee So-sang's H (2.82.16): the user's painted mountains made into the game's textures.

User requests: "이거 쓰면 되는데 투명하게 다듬으면 될듯", with their painted mountain (a PNG with its own transparency: the
background clear, the mountain all but opaque); then, with two more of theirs, "빈 공간에 이거 넣고 그래도 남는 공간에는 이
작은 산들 촘촘하게 끼워넣는데 엄청 투명하게 끼워넣어줘", a peak and a small mountain (in place of the first peak, a tall one
cut out of a bigger picture: "방금 보낸 사진 말고 이걸로"), both painted on black with no transparency of their own. The
paintings themselves are not redrawn; this only:
  - with --black, takes the black round the painting away (the near-black connected to its border; dark ink inside the
    mountain stays, however dark);
  - crops it to the mountain;
  - makes its ink body a little see-through and a little darker, its rims of light kept as they are (earlier: "산을 더
    투명하게 만들고 테두리 제외 산을 더 어둡게");
  - frays its foot away over its lowest FOOT of its height (it floats: "영상에는 산이 중간에 떠있는데");
  - with --sides F, frays its sides away where the picture cut it off: from where the mountain first meets a side, in
    from that side, widening downward to F of its width at its foot;
  - scales it down to --width pixels across (never up) in premultiplied alpha (no dark fringe at its edges) and pads it
    with a clear border of PAD pixels (no clamped edge pixels smeared).

python3 -I tools/isosang/make_mountain_texture.py mountain.png \
    src/main/resources/assets/celestialfall/textures/effect/isosang_mountain.png
python3 -I tools/isosang/make_mountain_texture.py --black --width 800 --seed 8218 peak.png \
    src/main/resources/assets/celestialfall/textures/effect/isosang_mountain_peak.png
python3 -I tools/isosang/make_mountain_texture.py --black --width 960 --seed 8219 small.png \
    src/main/resources/assets/celestialfall/textures/effect/isosang_mountain_small.png
"""
import argparse

import numpy as np
from PIL import Image, ImageFilter
from scipy import ndimage

BODY_ALPHA = .78   # how strongly its ink body shows (1 opaque); its rims stay as painted
BODY_DARK = .85    # its ink body's brightness, of the painting's
FOOT = .12         # of its height, its foot fraying away to nothing
FRAY = .35         # how ragged that is
WIDTH = 1536
PAD = 2
SEED = 8216
BLACK = 6          # (of 255) the most a pixel of the black round a painting on black is
EDGE = 2.5         # (pixels) how far into a painting on black its edge is mixed with the black
EDGE_FULL = .5     # how bright (its brightest channel) its outline is where it covers a pixel whole


def resize(channel, size):
    return np.asarray(Image.fromarray(channel.astype(np.float32), mode='F').resize(size, Image.LANCZOS), dtype=np.float64)


def load(src, black):
    """The painting as RGBA (0 to 1): its own transparency, or (black) the black round it taken away."""
    if not black:
        return np.asarray(Image.open(src).convert('RGBA'), dtype=np.float64) / 255
    rgb = np.asarray(Image.open(src).convert('RGB'), dtype=np.float64) / 255
    dark = rgb.max(2) <= BLACK / 255
    lab, _ = ndimage.label(dark)
    edge = np.unique(np.concatenate([lab[0], lab[-1], lab[:, 0], lab[:, -1]]))
    outside = np.isin(lab, edge[edge > 0])
    solid = ndimage.binary_fill_holes(~outside)
    al = solid.astype(np.float64)
    # its edge was drawn over the black: the pixels next to it are its outline's colour times how much of them it covers
    near = solid & (ndimage.distance_transform_edt(solid) <= EDGE)
    cover = np.clip(rgb.max(2) / EDGE_FULL, 0, 1)
    al = np.where(near, cover, al)
    rgb = np.where(near[..., None], np.clip(rgb / np.maximum(cover, 1e-3)[..., None], 0, 1), rgb)
    return np.dstack([rgb, al])


def noise(rng, h, w):
    """A soft noise (-.5 to .5), the same every time for its seed."""
    small = (rng.random((h // 20 + 2, w // 20 + 2)) * 255).astype(np.uint8)
    n = Image.fromarray(small).resize((w, h), Image.BICUBIC).filter(ImageFilter.GaussianBlur(6))
    n = np.asarray(n, dtype=np.float64) / 255 - .5
    return n / max(1e-6, np.abs(n).max()) * .5


def main(src, out, width=WIDTH, black=False, sides=0.0, seed=SEED):
    a = load(src, black)
    ys, xs = np.nonzero(a[..., 3] > .02)
    a = a[ys.min():ys.max() + 1, xs.min():xs.max() + 1]
    rgb, al = a[..., :3], a[..., 3]
    h, w = al.shape
    # how much of a rim of light each pixel is: bright and blue (the ink body is dark)
    rim = np.clip((rgb[..., 2] - .35) / .45, 0, 1) * np.clip((rgb.mean(2) - .2) / .35, 0, 1)
    rgb = rgb * (BODY_DARK + (1 - BODY_DARK) * rim[..., None])
    al = al * (BODY_ALPHA + (1 - BODY_ALPHA) * rim)
    # its foot frays away, raggedly (a soft noise, the same every time)
    rng = np.random.default_rng(seed)
    nz = noise(rng, h, w)
    up = (h - 1 - np.arange(h))[:, None] / (FOOT * h)
    f = np.clip(up + FRAY * nz, 0, 1)
    al = al * f * f * (3 - 2 * f)
    # and its sides, where the picture cut it off: from the row where it first meets the side, a fade from that side in,
    # widening downward to `sides` of its width at its foot (so its flank seems to run on into mist, not stop at a wall)
    if sides > 0:
        nz2 = noise(rng, h, w)
        rows = np.arange(h)[:, None]
        for edge_col, dist in ((0, np.arange(w)[None, :]), (w - 1, (w - 1 - np.arange(w))[None, :])):
            hit = np.nonzero(al[:, edge_col] > .5)[0]
            if not len(hit):
                continue
            cut = hit.min()
            band = (.04 + sides * np.clip((rows - cut) / max(1, h - cut), 0, 1)) * w
            f = np.clip(dist / band + FRAY * nz2 * (rows >= cut), 0, 1)
            f = np.where(rows >= cut, f, 1)
            al = al * f * f * (3 - 2 * f)
    # scaled down (never up) in premultiplied alpha, then unpremultiplied
    width = min(width, w)
    H = round(h * width / w)
    pa = resize(al, (width, H)).clip(0, 1)
    pc = [resize(rgb[..., k] * al, (width, H)) for k in range(3)]
    col = np.dstack([np.where(pa > 1e-4, c / np.maximum(pa, 1e-4), 0) for c in pc]).clip(0, 1)
    img = np.zeros((H + 2 * PAD, width + 2 * PAD, 4))
    img[PAD:PAD + H, PAD:PAD + width, :3] = col
    img[PAD:PAD + H, PAD:PAD + width, 3] = pa
    Image.fromarray((img * 255 + .5).astype(np.uint8), 'RGBA').save(out, optimize=True)
    print(out, img.shape[1], 'x', img.shape[0], 'aspect', round(img.shape[1] / img.shape[0], 4))


if __name__ == '__main__':
    p = argparse.ArgumentParser()
    p.add_argument('src')
    p.add_argument('out')
    p.add_argument('--width', type=int, default=WIDTH)
    p.add_argument('--black', action='store_true', help='painted on black, no transparency of its own')
    p.add_argument('--sides', type=float, default=0.0, help='fray its sides away over this much of its width each')
    p.add_argument('--seed', type=int, default=SEED)
    o = p.parse_args()
    main(o.src, o.out, o.width, o.black, o.sides, o.seed)
