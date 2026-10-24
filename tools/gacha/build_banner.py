"""Builds the gacha banner's textures from the user's second mockup (2.40.0).

usage: python3 tools/gacha/build_banner.py MOCKUP.png OUT_DIR

MOCKUP is the 1671x941 banner the user sent: Kageno in his hood on the right, the glowing "카게노" title with its
lightning on the left, the BOTAND logo, a currency box and a close mark in the top band, a details button and the two
pull buttons along the bottom. The game draws every control itself, so they are painted out of the art; the title and
the logo are lifted off it as the light they add, so the screen can animate them and hold them to the screen's edges.

  banner.png      the art with the logo, title, controls and their glow painted out
  title.png       "카게노" and its lightning as added light (RGB, drawn additively over the banner)
  title_core.png  the letters' white core (alpha), for the light sweeping across them
  title_sub.png   "— KAGENO —" under it as added light
  logo.png        the BOTAND logo as added light
  eyes.png        his red eyes and the red lightning around them as added light, to make them flare
  spark.png       a soft round glow (white, alpha) for motes and embers
  glint.png       a four-pointed glint (white, alpha) for sparkles
"""
import sys
import numpy as np
import cv2
from PIL import Image

W, H = 1671, 941
# the top band (a dark bar over the art) ends at the rule on rows 95-96 (103-104 left of x=330, under the logo)
LOGO = (22, 6, 334, 101)
TOP_RIGHT = (1238, 12, 1652, 88)          # count box and close mark
DETAIL = (48, 764, 244, 840)              # the details button
PULLS = (916, 756, 1636, 894)             # the two pull buttons
PULLS_GLOW = (870, 712, W, H)
DETAIL_GLOW = (20, 740, 280, 870)
# the title's letters and lightning: fairly white pixels (the least of R, G, B) connected to a bright core here
TITLE_BOX = (60, 330, 680, 610)
SUB_Y = 550                               # "— KAGENO —" lies below this


def rect_mask(shape, *rects):
    m = np.zeros(shape[:2], np.uint8)
    for x0, y0, x1, y1 in rects:
        m[y0:y1, x0:x1] = 255
    return m


def grain(img, mask, ref, seed, strength=1.0):
    """Adds back the art's fine grain where it was painted out, as strong as it is in the field nearby."""
    x0, y0, x1, y1 = ref
    patch = img[y0:y1, x0:x1].astype(np.float32)
    hp = patch - cv2.GaussianBlur(patch, (0, 0), 3)
    sd = hp.reshape(-1, 3).std(0)
    rng = np.random.default_rng(seed)
    n = rng.standard_normal(img.shape[:2]).astype(np.float32)
    n = cv2.GaussianBlur(n, (0, 0), .8)
    n /= n.std() + 1e-6
    soft = cv2.GaussianBlur(mask.astype(np.float32) / 255, (0, 0), 6)[..., None]
    out = img.astype(np.float32) + soft * n[..., None] * sd[None, None, :] * strength
    return np.clip(out, 0, 255).astype(np.uint8)


def smooth_fill(img, mask, q=4, radius=30):
    """The broad light under a mask, rebuilt from around it at a quarter scale (fine detail is added back separately)."""
    f = img.astype(np.float32)
    small = cv2.resize(f, (img.shape[1] // q, img.shape[0] // q), interpolation=cv2.INTER_AREA)
    sm = cv2.resize(mask, (small.shape[1], small.shape[0]), interpolation=cv2.INTER_AREA)
    sm = (sm > 0).astype(np.uint8) * 255
    fill = np.dstack([cv2.inpaint(np.ascontiguousarray(small[..., c]), sm, radius, cv2.INPAINT_TELEA) for c in range(3)])
    return cv2.resize(fill, (img.shape[1], img.shape[0]), interpolation=cv2.INTER_CUBIC)


def pushpull(img, unknown):
    """Fills the {unknown} pixels smoothly from the known ones around them (a push-pull pyramid): broad, calm light."""
    f = img.astype(np.float32)
    w = (unknown == 0).astype(np.float32)
    if w.min() > 0 or min(f.shape[:2]) < 4:
        return f
    num = cv2.resize(f * w[..., None], (f.shape[1] // 2, f.shape[0] // 2), interpolation=cv2.INTER_AREA)
    den = cv2.resize(w, (f.shape[1] // 2, f.shape[0] // 2), interpolation=cv2.INTER_AREA)
    small = num / np.maximum(den, 1e-6)[..., None]
    coarse = pushpull(small, (den < 1e-3).astype(np.uint8))
    coarse = cv2.GaussianBlur(coarse, (0, 0), .8)
    up = cv2.resize(coarse, (f.shape[1], f.shape[0]), interpolation=cv2.INTER_LINEAR)
    return f * w[..., None] + up * (1 - w[..., None])


def deglow(img, bodies, glows, donor, sigma=12):
    """Paints out glowing things: inside each glow the soft light (everything broader than SIGMA) is rebuilt from what
    lies around it, the finer detail (streaks) is kept; under the bodies themselves, where nothing of the art is left,
    the fine detail is borrowed from {donor} (x0, y, x1: a band of the art, its columns x0..x1 from row y, mirrored to the glow's width)."""
    f = img.astype(np.float32)
    low = cv2.GaussianBlur(f, (0, 0), sigma)
    detail = f - low
    gm = rect_mask(img.shape, *glows)
    fill = smooth_fill(low, gm, 4, 40)
    bm = rect_mask(img.shape, *[(x0 - 22, y0 - 22, x1 + 22, y1 + 22) for x0, y0, x1, y1 in bodies])
    keep = cv2.GaussianBlur(bm.astype(np.float32) / 255, (0, 0), 8)[..., None]
    lent = np.zeros_like(detail)
    for (gx0, gy0, gx1, gy1), (dx0, dy, dx1) in zip(glows, donor):
        # the donor's columns dx0..dx1, mirrored back and forth as often as the glow's box is wide
        patch = detail[dy:dy + gy1 - gy0, dx0:dx1]
        while patch.shape[1] < gx1 - gx0:
            patch = np.concatenate([patch, patch[:, ::-1]], 1)
        lent[gy0:gy1, gx0:gx1] = patch[:, :gx1 - gx0] * .7
    soft = cv2.GaussianBlur(gm.astype(np.float32) / 255, (0, 0), 10)[..., None]
    out = f * (1 - soft) + (fill + detail * (1 - keep) + lent * keep) * soft
    return np.clip(out, 0, 255).astype(np.uint8)


def title_masks(rgb):
    """The title's letters and lightning (above SUB_Y) and the sub line (below), as boolean masks."""
    a = rgb.astype(np.float32) / 255
    white = a.min(2)
    n, lab, stats, cent = cv2.connectedComponentsWithStats((white > .28).astype(np.uint8), 8)
    title, sub = [], []
    x0, y0, x1, y1 = TITLE_BOX
    for i in np.unique(lab[white > .62]):
        if i == 0 or stats[i][4] <= 20:
            continue
        cx, cy = cent[i]
        if not (x0 < cx < x1 and y0 < cy < y1):
            continue
        # a bright patch of the light beams right of the last letter, and a glint under the sub line's end
        if (cx > 600 and cy < 400) or (cx > 640 and cy > 590):
            continue
        (sub if cy > SUB_Y else title).append(i)
    return np.isin(lab, title), np.isin(lab, sub), white


def lift(rgb, clean, mask, pad):
    """The light {mask} adds over {clean}, cut to its bounding box plus {pad}: (image, (x0, y0))."""
    ys, xs = np.nonzero(mask)
    x0, y0 = max(0, xs.min() - pad), max(0, ys.min() - pad)
    x1, y1 = min(rgb.shape[1], xs.max() + pad + 1), min(rgb.shape[0], ys.max() + pad + 1)
    light = np.clip(rgb[y0:y1, x0:x1].astype(np.int16) - clean[y0:y1, x0:x1].astype(np.int16), 0, 255).astype(np.uint8)
    light[light.max(2) < 3] = 0
    return light, (int(x0), int(y0))


def sprites(out):
    """The particles' shapes, drawn here: a soft round glow and a four-pointed glint, white with the shape in alpha."""
    n = 64
    y, x = (np.mgrid[0:n, 0:n] + .5 - n / 2) / (n / 2)
    r = np.hypot(x, y)
    spark = np.clip(np.exp(-(r / .42) ** 2) * .85 + np.exp(-(r / .13) ** 2) * .4, 0, 1) * np.clip((1 - r) / .15, 0, 1)
    n = 128
    y, x = (np.mgrid[0:n, 0:n] + .5 - n / 2) / (n / 2)
    r = np.hypot(x, y)
    arms = np.exp(-np.abs(x) / .035) * np.exp(-np.abs(y) / .42) + np.exp(-np.abs(y) / .035) * np.exp(-np.abs(x) / .42)
    glint = np.clip(arms * .9 + np.exp(-(r / .16) ** 2) * .8, 0, 1) * np.clip((1 - r) / .2, 0, 1)
    for name, a in (('spark', spark), ('glint', glint)):
        rgba = np.zeros(a.shape + (4,), np.uint8)
        rgba[..., :3] = 255
        rgba[..., 3] = np.clip(a * 255, 0, 255).astype(np.uint8)
        Image.fromarray(rgba, 'RGBA').save(f'{out}/{name}.png', optimize=True)


def main():
    mock, out = sys.argv[1], sys.argv[2]
    sprites(out)
    src = np.asarray(Image.open(mock).convert('RGB'))
    assert src.shape[:2] == (H, W), src.shape
    bgr = cv2.cvtColor(src, cv2.COLOR_RGB2BGR)

    # the top band: the logo, the box and the close mark painted out within it, so the rule under it stays sharp
    band = bgr[:94].copy()
    lx0, ly0, lx1, ly1 = LOGO
    band = cv2.inpaint(band, rect_mask(band.shape, (lx0, ly0, lx1, 94), TOP_RIGHT), 15, cv2.INPAINT_TELEA)
    band = grain(band, rect_mask(band.shape, (lx0, ly0, lx1, 94), TOP_RIGHT), (700, 8, 1100, 60), 1)
    clean = bgr.copy()
    clean[:94] = band
    # left of x=330 the band reaches down to its lower rule on rows 103-104: the logo's last rows lie between
    under = clean[:103, :336].copy()
    m = rect_mask(under.shape, (lx0, 90, 330, 102))
    under = cv2.inpaint(under, m, 6, cv2.INPAINT_TELEA)
    clean[:103, :336] = under

    # the controls along the bottom and their glow
    clean = deglow(clean, [DETAIL, PULLS], [DETAIL_GLOW, PULLS_GLOW], [(300, 740, 600), (262, 712, 860)])

    # the title: its letters, lightning and glow painted out, broad light rebuilt from around it, fine grain added
    title, sub, white = title_masks(cv2.cvtColor(clean, cv2.COLOR_BGR2RGB))
    both = (title | sub).astype(np.uint8) * 255
    region = cv2.dilate(both, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (45, 45)))
    fill = pushpull(cv2.GaussianBlur(clean, (0, 0), 2), region)
    # the streaks' fine texture from the art below the title (rows 630-941), laid in faintly so the patch is not flat
    donor = clean[630:].astype(np.float32)
    tex = np.zeros_like(fill)
    tex[300:300 + donor.shape[0]] = donor - cv2.GaussianBlur(donor, (0, 0), 2.5)
    soft = cv2.GaussianBlur(region.astype(np.float32) / 255, (0, 0), 6)[..., None]
    mixed = clean.astype(np.float32) * (1 - soft) + (fill * .92 + tex * .5) * soft
    clean_title = np.clip(mixed, 0, 255).astype(np.uint8)

    rgb_clean = cv2.cvtColor(clean_title, cv2.COLOR_BGR2RGB)
    rgb_src = src
    Image.fromarray(rgb_clean).save(f'{out}/banner.png', optimize=True)

    # lifted layers; positions are reported for GachaArt
    report = {}
    # the light around the sub line's letters is the sub line's, so each layer can be faded on its own
    smask = cv2.dilate(sub.astype(np.uint8) * 255, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (31, 31))) > 0
    tmask = cv2.dilate(title.astype(np.uint8) * 255, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (45, 45))) > 0
    tmask &= ~smask
    for name, mask, pad in (('title', tmask, 2), ('title_sub', smask, 2)):
        light, at = lift(rgb_src * mask[..., None], rgb_clean * mask[..., None], mask, pad)
        Image.fromarray(light).save(f'{out}/{name}.png', optimize=True)
        report[name] = (at, light.shape[1], light.shape[0])
    # the letters' white core, cut to the title's box
    (tx, ty), tw, th = report['title']
    core = np.clip((white - .45) / .35, 0, 1) * title
    core = cv2.GaussianBlur(core.astype(np.float32), (0, 0), .7)[ty:ty + th, tx:tx + tw]
    rgba = np.zeros((th, tw, 4), np.uint8)
    rgba[..., :3] = 255
    rgba[..., 3] = np.clip(core * 255, 0, 255).astype(np.uint8)
    Image.fromarray(rgba, 'RGBA').save(f'{out}/title_core.png', optimize=True)

    # the logo
    lmask = np.zeros((H, W), bool)
    lmask[ly0:103, lx0:lx1] = True
    light, at = lift(rgb_src * lmask[..., None], rgb_clean * lmask[..., None], lmask, 0)
    Image.fromarray(light).save(f'{out}/logo.png', optimize=True)
    report['logo'] = (at, light.shape[1], light.shape[0])

    # his red eyes and the red lightning: strongly red pixels right of the title, softened a little
    a = src.astype(np.float32) / 255
    r, g, b = a[..., 0], a[..., 1], a[..., 2]
    # (the gold of his sleeves and hood is orange, with far more green than the red light has)
    red = np.clip((r - np.maximum(g, b) * 1.25) / .3, 0, 1) * np.clip((r * .42 - g) / (r * .12 + 1e-3), 0, 1)
    red[:, :690] = 0
    red[:200] = 0
    red[420:] = 0
    red = cv2.GaussianBlur(red, (0, 0), 1.2)
    emask = red > .02
    ys, xs = np.nonzero(emask)
    ex0, ey0, ex1, ey1 = xs.min() - 6, ys.min() - 6, xs.max() + 7, ys.max() + 7
    eyes = (src[ey0:ey1, ex0:ex1].astype(np.float32) * red[ey0:ey1, ex0:ex1, None])
    Image.fromarray(np.clip(eyes, 0, 255).astype(np.uint8)).save(f'{out}/eyes.png', optimize=True)
    report['eyes'] = ((int(ex0), int(ey0)), int(ex1 - ex0), int(ey1 - ey0))
    for k, v in report.items():
        print(k, 'at', v[0], 'size', v[1], v[2])


if __name__ == '__main__':
    main()
