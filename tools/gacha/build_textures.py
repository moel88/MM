"""Builds the gacha screen's textures from the user's mockup and Kageno illustration (2.39.0).

usage: python3 tools/gacha/build_textures.py MOCKUP.png KAGENO.png OUT_DIR

MOCKUP is the 1536x1024 screen draft the user sent: its background (the faint compass emblem, the diagonal light
streaks, the top band and its glowing rule) is kept as the screen's backdrop, and its logo is lifted off it to be drawn
as light over the backdrop. The draft's buttons, currency box and close mark are painted out; the game draws its own
(pulls are free for now, so there is no currency). KAGENO is the illustration shown on an SSR result.
"""
import sys
import numpy as np
import cv2
from PIL import Image

W, H = 1536, 1024
# The glowing rule under the top band (rows 89-95) is kept; the band above and the field below are painted out on
# their own so the rule's light does not bleed into either.
RULE = (89, 96)
LOGO = (18, 4, 304, 89)
TOP_RIGHT = (1146, 10, 1514, 78)          # currency box, divider, close mark
DETAIL = (28, 678, 302, 768)              # the details button and its underline
PULLS = (800, 796, 1514, 944)             # the two pull buttons
# Their glow fades out 70-80 px around them (to the right and bottom edges); there the draft's fine streaks and grain
# are kept and only the glow's soft light is taken away.
PULLS_GLOW = (712, 706, W, H)
DETAIL_GLOW = (0, 650, 340, 800)


def rect_mask(shape, *rects):
    m = np.zeros(shape[:2], np.uint8)
    for x0, y0, x1, y1 in rects:
        m[y0:y1, x0:x1] = 255
    return m


def grain(img, mask, ref, seed):
    """Adds back the draft's fine cloudy grain where it was painted out, as strong as it is in the field nearby."""
    x0, y0, x1, y1 = ref
    patch = img[y0:y1, x0:x1].astype(np.float32)
    hp = patch - cv2.GaussianBlur(patch, (0, 0), 3)
    sd = hp.reshape(-1, 3).std(0)
    rng = np.random.default_rng(seed)
    n = rng.standard_normal(img.shape[:2]).astype(np.float32)
    n = cv2.GaussianBlur(n, (0, 0), .8)
    n /= n.std() + 1e-6
    soft = cv2.GaussianBlur(mask.astype(np.float32) / 255, (0, 0), 6)[..., None]
    out = img.astype(np.float32) + soft * n[..., None] * sd[None, None, :]
    return np.clip(out, 0, 255).astype(np.uint8)


def paint_out(img, rects, ref, seed):
    m = rect_mask(img.shape, *rects)
    out = cv2.inpaint(img, m, 21, cv2.INPAINT_TELEA)
    return grain(out, m, ref, seed)


def deglow(img, bodies, glows, ref, seed, sigma=12):
    """Paints out glowing things: inside each glow the soft light (everything broader than SIGMA) is rebuilt from what
    lies around it, the finer detail (streaks, grain) is kept; the bodies themselves are painted out entirely."""
    f = img.astype(np.float32)
    low = cv2.GaussianBlur(f, (0, 0), sigma)
    detail = f - low
    gm = rect_mask(img.shape, *glows)
    q = 4
    small = cv2.resize(low, (img.shape[1] // q, img.shape[0] // q), interpolation=cv2.INTER_AREA)
    sm = cv2.resize(gm, (small.shape[1], small.shape[0]), interpolation=cv2.INTER_NEAREST)
    fill = np.dstack([cv2.inpaint(np.ascontiguousarray(small[..., c]), sm, 40, cv2.INPAINT_TELEA) for c in range(3)])
    fill = cv2.resize(fill, (img.shape[1], img.shape[0]), interpolation=cv2.INTER_CUBIC)
    # the bodies (and a margin where their glow is still steep) keep none of their own detail
    bm = rect_mask(img.shape, *[(x0 - 26, y0 - 26, x1 + 26, y1 + 26) for x0, y0, x1, y1 in bodies])
    keep = 1 - cv2.GaussianBlur(bm.astype(np.float32) / 255, (0, 0), 8)[..., None]
    soft = cv2.GaussianBlur(gm.astype(np.float32) / 255, (0, 0), 10)[..., None]
    out = f * (1 - soft) + (fill + detail * keep) * soft
    out = np.clip(out, 0, 255).astype(np.uint8)
    return grain(out, bm, ref, seed)


def main():
    mock, kageno, out = sys.argv[1], sys.argv[2], sys.argv[3]
    src = np.asarray(Image.open(mock).convert('RGB'))
    assert src.shape[:2] == (H, W), src.shape
    bgr = cv2.cvtColor(src, cv2.COLOR_RGB2BGR)
    top = paint_out(bgr[:RULE[0]].copy(), [LOGO, TOP_RIGHT], (480, 10, 1100, 60), 1)
    field = bgr[RULE[1]:].copy()
    sh = lambda r: (r[0], r[1] - RULE[1], r[2], r[3] - RULE[1])
    field = deglow(field, [sh(DETAIL), sh(PULLS)], [sh(DETAIL_GLOW), sh(PULLS_GLOW)], sh((330, 800, 700, 1000)), 2)
    bg = np.concatenate([top, bgr[RULE[0]:RULE[1]], field])
    clean = cv2.cvtColor(bg, cv2.COLOR_BGR2RGB)
    Image.fromarray(clean).save(f'{out}/background.png', optimize=True)

    # The logo as the light it adds to the backdrop: drawn additively, it gives back the draft exactly where it was.
    x0, y0, x1, y1 = LOGO
    light = np.clip(src[y0:y1, x0:x1].astype(np.int16) - clean[y0:y1, x0:x1].astype(np.int16), 0, 255).astype(np.uint8)
    light[light.max(2) < 3] = 0
    Image.fromarray(light).save(f'{out}/logo.png', optimize=True)

    art = Image.open(kageno).convert('RGB')
    w = 1280
    art = art.resize((w, round(art.height * w / art.width)), Image.LANCZOS)
    art.save(f'{out}/kageno.png', optimize=True)
    print('background', clean.shape, 'logo', light.shape, 'kageno', art.size)


if __name__ == '__main__':
    main()
