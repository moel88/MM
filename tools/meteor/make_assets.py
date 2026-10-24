"""Draw the meteor's textures (2.49.0): the rock, its glowing cracks and seams, the metal crystals in it, the scorch it
leaves on the ground, and the three items (stand arrow, meteor fragment, meteor pickaxe).
usage: python3 tools/meteor/make_assets.py [PREVIEW_DIR]
Everything is drawn from fixed seeds, so the same pictures come out every time. PREVIEW_DIR, if given, gets every
picture enlarged eight times over (not part of the mod).
"""
from pathlib import Path
import sys
import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
TEX = ROOT / 'src/main/resources/assets/celestialfall/textures'


def tileable_noise(size, beta, seed):
    """Noise that tiles: white noise shaped in frequency space (1/f^beta), scaled to 0..1."""
    rng = np.random.default_rng(seed)
    white = rng.standard_normal((size, size))
    fx = np.fft.fftfreq(size)[:, None]
    fy = np.fft.fftfreq(size)[None, :]
    f = np.sqrt(fx * fx + fy * fy)
    f[0, 0] = 1
    shaped = np.real(np.fft.ifft2(np.fft.fft2(white) / f ** beta))
    shaped -= shaped.min()
    return shaped / shaped.max()


def voronoi(size, count, seed):
    """Distance from each pixel to its nearest cell point and to the second nearest, on a tiling square."""
    rng = np.random.default_rng(seed)
    # points kept apart (a near pair would smear a dark wedge between them), wrapping round the square
    gap = size / np.sqrt(count) * .62
    pts = []
    while len(pts) < count:
        c = rng.random(2) * size
        if all(np.hypot(*np.minimum(np.abs(c - q), size - np.abs(c - q))) >= gap for q in pts):
            pts.append(c)
    ys, xs = np.mgrid[0:size, 0:size] + .5
    d = []
    for px, py in pts:
        dx = np.abs(xs - px)
        dy = np.abs(ys - py)
        dx = np.minimum(dx, size - dx)
        dy = np.minimum(dy, size - dy)
        d.append(np.sqrt(dx * dx + dy * dy))
    d = np.sort(np.stack(d), axis=0)
    return d[0], d[1]


def rgba(img):
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), 'RGBA')


def rock():
    """The rock (64 pixels for four blocks, like the game's own textures): dark stone with a faint violet cast, mottled,
    lighter grains, and dark cracks; the glow textures follow the same cracks."""
    n = 64
    big = tileable_noise(n, 1.6, 11)
    fine = tileable_noise(n, .7, 12)
    d1, d2 = voronoi(n, 13, 13)
    # not every edge between cells is a crack: some fade out, so they read as cracks rather than tiles
    edge = d2 - d1 + 3.5 * (tileable_noise(n, 1.4, 17) > .64)
    rng = np.random.default_rng(14)
    palette = np.array([[19, 16, 20], [30, 26, 31], [41, 37, 43], [52, 47, 54], [64, 59, 67], [82, 77, 86]], float)
    v = .55 * big + .45 * fine
    level = np.digitize(v, [.30, .45, .62, .78]) + 1
    level[rng.random((n, n)) < .035] = 5
    level[edge < 1.9] = np.minimum(level[edge < 1.9], 1)
    level[edge < .9] = 0
    img = np.zeros((n, n, 4))
    img[..., :3] = palette[level]
    # the faint cold cast of space-iron in the darker patches
    cast = tileable_noise(n, 1.2, 15) > .62
    img[cast, :3] = img[cast, :3] * [.92, .95, 1.12]
    img[..., 3] = 255
    rgba(img).save(TEX / 'entity/meteor/rock.png')
    glow = np.zeros((n, n, 4))
    # each crack with a soft light round it, falling off into the plates
    line = np.where(edge < .9, 255, np.where(edge < 1.9, 110, np.where(edge < 3.0, 34, 0)))
    # embers here and there inside the cells
    line = np.maximum(line, np.where((rng.random((n, n)) < .012) & (edge > 4), 150, 0))
    glow[..., :3] = line[..., None]
    glow[..., 3] = 255
    rgba(glow).save(TEX / 'entity/meteor/rock_glow.png')
    # the seams between the rock's plates (and the whole surface while it is fresh) are lit through this: broad soft
    # swells of light, never even, and never a pixel-by-pixel speckle (that read as a checkerboard close by)
    seam = np.zeros((n, n, 4))
    m = 150 + 105 * tileable_noise(n, 2.4, 16)
    seam[..., :3] = m[..., None]
    seam[..., 3] = 255
    rgba(seam).save(TEX / 'entity/meteor/rock_seam.png')


def nodule():
    """One face of a metal crystal: amber at the edges, gold, a pale highlight down one side; and its inner glow."""
    n = 16
    cols = np.array([.38, .55, .72, .84, 1.0, .95, .80, .70, .64, .60, .58, .55, .50, .45, .40, .30])
    img = np.zeros((n, n, 4))
    for y in range(n):
        top = 1.0 - .22 * y / (n - 1)
        for x in range(n):
            b = cols[x] * top
            dark = np.array([120, 66, 14.])
            mid = np.array([226, 162, 38.])
            light = np.array([255, 236, 160.])
            c = dark + (mid - dark) * min(1, b / .7) if b < .7 else mid + (light - mid) * (b - .7) / .3
            img[y, x, :3] = c
    img[2, 4, :3] = img[9, 3, :3] = img[5, 9, :3] = [255, 252, 225]
    img[..., 3] = 255
    rgba(img).save(TEX / 'entity/meteor/nodule.png')
    glow = np.zeros((n, n, 4))
    for y in range(n):
        for x in range(n):
            s = max(0, 1 - abs(x - 7.5) / 8) ** 1.4 * (.65 + .35 * y / (n - 1))
            glow[y, x, :3] = 255 * s
    glow[..., 3] = 255
    rgba(glow).save(TEX / 'entity/meteor/nodule_glow.png')


def scorch():
    """The scorch round a landed meteor (128 pixels over about seven blocks): a charred middle with a ragged edge,
    rays of thrown soil, specks; and the embers glowing in the middle while it is hot."""
    n = 128
    rng = np.random.default_rng(21)
    ys, xs = np.mgrid[0:n, 0:n] + .5
    dx, dy = (xs - n / 2) / (n / 2), (ys - n / 2) / (n / 2)
    r = np.sqrt(dx * dx + dy * dy)
    a = np.arctan2(dy, dx)
    coarse = Image.fromarray((rng.random((16, 16)) * 255).astype(np.uint8)).resize((n, n), Image.BICUBIC)
    wobble = np.asarray(coarse, float) / 255
    edge = .50 + .16 * wobble + .05 * np.sin(a * 7 + 1.3)
    img = np.zeros((n, n, 4))
    img[..., :3] = [24, 19, 17]
    alpha = np.where(r < edge - .12, 238, np.where(r < edge, 170, 0)).astype(float)
    # rays of soil thrown out of the hole
    for _ in range(22):
        ra = rng.random() * np.pi * 2
        width = .035 + rng.random() * .05
        reach = .62 + rng.random() * .34
        da = np.angle(np.exp(1j * (a - ra)))
        ray = (np.abs(da) < width * (1.15 - r)) & (r < reach) & (r > .3)
        alpha = np.where(ray & (alpha < 140), 140 - 100 * np.clip((r - .5) / .5, 0, 1), alpha)
    specks = (rng.random((n, n)) < .03) & (r > .45) & (r < .97)
    alpha = np.where(specks & (alpha < 120), 150, alpha)
    soil = (r > edge - .02) & (alpha > 0)
    img[soil, :3] = [44, 34, 27]
    img[..., 3] = np.clip(alpha, 0, 255)
    rgba(img).save(TEX / 'entity/meteor/scorch.png')
    d1, d2 = voronoi(n, 40, 22)
    cracks = np.where(d2 - d1 < 1.6, 255, np.where(d2 - d1 < 3.2, 70, 0)).astype(float)
    cracks *= np.clip((.46 - r) / .2, 0, 1)
    embers = (rng.random((n, n)) < .02) & (r < .55)
    cracks = np.maximum(cracks, np.where(embers, 210 * np.clip(1.1 - r * 1.6, 0, 1), 0))
    glow = np.zeros((n, n, 4))
    glow[..., :3] = cracks[..., None]
    glow[..., 3] = 255
    rgba(glow).save(TEX / 'entity/meteor/scorch_glow.png')


def outline(img, color):
    """A one-pixel dark outline round everything drawn (sides only, like the game's item art)."""
    solid = img[..., 3] > 0
    out = img.copy()
    n = img.shape[0]
    for y in range(n):
        for x in range(n):
            if solid[y, x]:
                continue
            if any(0 <= y + dy < n and 0 <= x + dx < n and solid[y + dy, x + dx] for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                out[y, x] = [*color, 255]
    return out


def axis_coords(x, y, a, b):
    """Distance along the line a->b (0 at a, 1 at b) and across it (pixels, + to the lower right)."""
    d = np.array(b, float) - np.array(a, float)
    length = np.hypot(*d)
    u = d / length
    p = np.array([x + .5, y + .5]) - np.array(a, float)
    return (p @ u) / length, p @ np.array([-u[1], u[0]]), length


def stand_arrow():
    """The stand arrow: a wooden shaft with pale fletching banded in violet, a gold collar and a broad gold head with two
    swept barbs, a bright ridge and a violet stone, pointing to the upper right."""
    n = 32
    img = np.zeros((n, n, 4))
    gold = [(128, 74, 14), (206, 144, 32), (244, 200, 74), (255, 246, 192)]
    a, b = (3.2, 28.8), (29.6, 2.4)
    for y in range(n):
        for x in range(n):
            t, s, length = axis_coords(x, y, a, b)
            c = None
            if 0 <= t <= .62 and abs(s) <= 1.05:
                c = (158, 110, 62) if s < 0 else (104, 66, 36)
            if .02 <= t <= .23:
                vane = 3.4 * min(1, (t - .02) / .06) * min(1, (.23 - t) / .1)
                if 1.0 < abs(s) <= max(1.0, vane + 1.0):
                    c = (228, 224, 216) if s < 0 else (176, 170, 168)
                    if .10 <= t <= .14:
                        c = (156, 96, 220) if s < 0 else (106, 58, 164)
            if .60 <= t <= .655 and abs(s) <= 2.2:
                c = gold[2] if s < 0 else gold[1]
            if .655 < t <= 1.0:
                u = (t - .655) / .345
                # swept barbs at the back, then a straight taper to the point
                half = 1.8 + 3.2 * u / .16 if u < .16 else 5.0 - 1.2 * (u - .16) / .08 if u < .24 else 3.8 * (1 - (u - .24) / .76)
                if abs(s) <= half + .25:
                    if abs(s) < .55:
                        c = gold[3]
                    elif s < 0:
                        c = gold[2]
                    else:
                        c = gold[1] if s < half * .72 else gold[0]
                if .30 < u < .44 and abs(s) < 1.25:
                    c = (190, 110, 244) if s < 0 else (122, 60, 182)
            if c is not None:
                img[y, x] = [*c, 255]
    img = outline(img, (38, 22, 12))
    Image.fromarray(img.astype(np.uint8), 'RGBA').save(TEX / 'item/stand_arrow.png')


def inside(poly, x, y):
    """Whether a point is inside a polygon (even-odd)."""
    hit = False
    j = len(poly) - 1
    for i in range(len(poly)):
        xi, yi = poly[i]
        xj, yj = poly[j]
        if (yi > y) != (yj > y) and x < (xj - xi) * (y - yi) / (yj - yi) + xi:
            hit = not hit
        j = i
    return hit


def meteor_fragment():
    """A chip of the meteor: a faceted dark stone lit from the upper left, a glowing crack over it, and a gold crystal
    growing out of its top."""
    n = 32
    img = np.zeros((n, n, 4))
    v = {'A': (5, 17), 'B': (11, 10), 'C': (21, 11), 'D': (27, 17), 'E': (24, 25), 'F': (13, 28), 'G': (6, 24), 'M': (15.5, 18.5)}
    facets = [('ABCM', (100, 93, 108)), ('CDM', (76, 70, 84)), ('DEM', (44, 40, 50)), ('EFM', (33, 29, 37)), ('FGM', (56, 51, 62)), ('GAM', (80, 74, 88))]
    for y in range(n):
        for x in range(n):
            for names, tone in facets:
                if inside([v[k] for k in names], x + .5, y + .5):
                    img[y, x] = [*tone, 255]
                    break
    crack = [(8, 19), (11, 18), (13, 20), (16, 17), (19, 18), (22, 15)]
    for (x0, y0), (x1, y1) in zip(crack, crack[1:]):
        for s in np.linspace(0, 1, 12):
            x, y = int(round(x0 + (x1 - x0) * s)), int(round(y0 + (y1 - y0) * s))
            if img[y, x, 3] > 0:
                img[y, x, :3] = (255, 140, 40)
    for x, y in ((13, 20), (16, 17), (19, 18)):
        img[y, x, :3] = (255, 228, 140)
    gold = [(150, 92, 18), (222, 162, 40), (252, 216, 100), (255, 248, 204)]
    for (bx, by), (tx, ty), w in (((15.5, 13.0), (22.5, 3.0), 2.3), ((19.5, 13.5), (25.5, 8.5), 1.4), ((12.5, 12.5), (13.5, 7.0), 1.2)):
        for y in range(n):
            for x in range(n):
                t, s, length = axis_coords(x, y, (bx, by), (tx, ty))
                if 0 <= t <= 1:
                    half = w * (1 if t < .62 else (1 - t) / .38)
                    if abs(s) <= half:
                        k = 3 if abs(s) < .5 and t > .25 else 2 if s < 0 else 1 if s < half * .6 else 0
                        img[y, x] = [*gold[k], 255]
    img = outline(img, (18, 14, 17))
    Image.fromarray(img.astype(np.uint8), 'RGBA').save(TEX / 'item/meteor_fragment.png')


def meteor_pickaxe():
    """The meteor pickaxe: a wooden handle with dark grip bands, an arched head of dark space-iron edged in gold, and a
    gold stone set where the head meets the handle."""
    n = 32
    img = np.zeros((n, n, 4))
    grip_a, top = (5.0, 27.5), (20.0, 12.5)
    for y in range(n):
        for x in range(n):
            t, s, length = axis_coords(x, y, grip_a, top)
            if -.02 <= t <= 1.0 and abs(s) <= 1.05:
                c = (150, 102, 56) if s < 0 else (98, 62, 34)
                if t < .42 and int(t * length) % 3 == 0:
                    c = (58, 40, 60) if s < 0 else (40, 28, 44)
                img[y, x] = [*c, 255]
    d = np.array(top) - np.array(grip_a)
    d /= np.hypot(*d)
    nrm = np.array([d[1], -d[0]])
    centre = np.array(top) + d * .6
    iron = [(34, 34, 46), (62, 63, 80), (104, 106, 126), (160, 164, 184)]
    for y in range(n):
        for x in range(n):
            p = np.array([x + .5, y + .5]) - centre
            across = p @ nrm
            along = p @ d
            if abs(across) > 12.2:
                continue
            k = abs(across) / 12.2
            # the arch: the head bows back towards the handle at both ends, thick in the middle, pointed at the tips
            arch = -4.4 * k * k
            half = 2.3 * (1 - k ** 1.6) + .35
            if abs(along - arch) <= half:
                side = (along - arch) / half
                tone = 3 if side > .55 and k < .75 else 2 if side > 0 else 1 if side > -.6 else 0
                c = iron[tone]
                if k > .82:
                    c = (232, 176, 52) if side > -.2 else (170, 112, 24)
                img[y, x] = [*c, 255]
    cx, cy = int(centre[0]), int(centre[1])
    for (x, y), c in (((cx, cy), (255, 214, 90)), ((cx + 1, cy), (238, 168, 40)), ((cx, cy + 1), (214, 140, 30)), ((cx + 1, cy + 1), (255, 246, 200))):
        img[y, x] = [*c, 255]
    img = outline(img, (24, 20, 28))
    Image.fromarray(img.astype(np.uint8), 'RGBA').save(TEX / 'item/meteor_pickaxe.png')


def main():
    (TEX / 'entity/meteor').mkdir(parents=True, exist_ok=True)
    rock()
    nodule()
    scorch()
    stand_arrow()
    meteor_fragment()
    meteor_pickaxe()
    if len(sys.argv) > 1:
        out = Path(sys.argv[1])
        out.mkdir(parents=True, exist_ok=True)
        for p in [*sorted((TEX / 'entity/meteor').glob('*.png')), *(TEX / 'item' / f for f in ('stand_arrow.png', 'meteor_fragment.png', 'meteor_pickaxe.png'))]:
            im = Image.open(p)
            im.resize((im.width * 8 if im.width <= 64 else im.width * 4, im.height * 8 if im.height <= 64 else im.height * 4), Image.NEAREST).save(out / p.name)


if __name__ == '__main__':
    main()
