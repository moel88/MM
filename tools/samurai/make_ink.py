#!/usr/bin/env python3
"""Draws the samurai's G's flat frames (2.143.0): the ink strokes of its dash, the burst as it arrives, the ink wash and
the cut line of its flash, and the brush circle with the character for death. Everything is drawn here, stroke by stroke,
from seeds (nothing is taken from the user's video: it is only what these frames are laid out after).

    python3 -I tools/samurai/make_ink.py [OUT_DIR]

Writes PNGs (1280x720, RGBA) into src/main/resources/assets/celestialfall/textures/samurai (or OUT_DIR)."""
import math, os, random, sys
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

W, H, SS = 1280, 720, 2
OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), '..', '..', 'src', 'main', 'resources', 'assets', 'celestialfall', 'textures', 'samurai')


def canvas(fill=0):
    return Image.new('L', (W * SS, H * SS), fill)


def done(img):
    return img.resize((W, H), Image.LANCZOS)


def px(x, y):
    return (x * W * SS, y * H * SS)


def ribbon(d, pts, widths, fill=255):
    """A stroke along pts (fractions of the frame), as wide as widths (fractions of its height) at each."""
    left, right = [], []
    n = len(pts)
    for i in range(n):
        x, y = px(*pts[i])
        a = px(*pts[max(0, i - 1)]); b = px(*pts[min(n - 1, i + 1)])
        dx, dy = b[0] - a[0], b[1] - a[1]
        l = math.hypot(dx, dy) or 1
        nx, ny = -dy / l, dx / l
        w = widths[i] * H * SS / 2
        left.append((x + nx * w, y + ny * w)); right.append((x - nx * w, y - ny * w))
    d.polygon(left + right[::-1], fill=fill)


def curve(points, n=40):
    """A smooth line through the points (Catmull-Rom), n pieces a span."""
    p = [points[0]] + list(points) + [points[-1]]
    out = []
    for i in range(1, len(p) - 2):
        for k in range(n):
            t = k / n
            out.append(tuple(.5 * ((2 * p[i][c]) + (-p[i - 1][c] + p[i + 1][c]) * t + (2 * p[i - 1][c] - 5 * p[i][c] + 4 * p[i + 1][c] - p[i + 2][c]) * t * t
                                   + (-p[i - 1][c] + 3 * p[i][c] - 3 * p[i + 1][c] + p[i + 2][c]) * t ** 3) for c in (0, 1)))
    out.append(points[-1])
    return out


def brush(d, points, w0, w1, rng, belly=.0, dry=0, fill=255):
    """A brush stroke through the points: w0 wide where it lands, w1 where it leaves, fuller by belly in the middle, and
    dry bristles trailing off its end."""
    c = curve(points)
    n = len(c)
    widths = []
    phase = rng.random() * 6
    for i in range(n):
        u = i / (n - 1)
        w = w0 + (w1 - w0) * u + belly * math.sin(math.pi * u)
        w *= .55 + .45 * min(1, u * 14)  # the brush set down
        widths.append(max(.001, w * (1 + .05 * math.sin(u * 9 + phase))))
    ribbon(d, c, widths, fill)
    # bristles: thin lines running on past the end, and beside the stroke's last third
    for k in range(dry):
        s = int(n * (.55 + .4 * rng.random()))
        off = (rng.random() - .5) * widths[s] * 1.1
        seg = c[s:]
        ex = 1 + rng.random() * .35
        last = seg[-1]; prev = seg[max(0, len(seg) - 6)]
        vx, vy = last[0] - prev[0], last[1] - prev[1]
        l = math.hypot(vx * W, vy * H) or 1
        tail = [(last[0] + vx / l * W * .0 + vx * ex * 3 * j / 4, last[1] + vy * ex * 3 * j / 4) for j in range(1, 5)]
        line = [(x, y) for x, y in seg + tail]
        dx, dy = (line[-1][0] - line[0][0]), (line[-1][1] - line[0][1])
        ll = math.hypot(dx * W, dy * H) or 1
        nx, ny = -dy * H / ll / W * H, dx * W / ll / H * H
        line = [(x + nx * off * H / W, y + ny * off) for x, y in line]
        ribbon(d, line, [max(.0008, .004 * (1 - j / len(line)) * (.5 + rng.random())) for j in range(len(line))], fill)


def splatter(d, cx, cy, spread, count, rng, fill=255, aim=None, size=1.0):
    """Drops of ink flung from (cx, cy): most small and near, a few big; streaked along the way they fly (toward aim, an
    angle, if given)."""
    for _ in range(count):
        a = rng.random() * 2 * math.pi if aim is None else aim + rng.gauss(0, .55)
        r = spread * abs(rng.gauss(0, .5)) + spread * .05
        x, y = cx + math.cos(a) * r * H / W, cy + math.sin(a) * r
        s = size * (.0015 + .006 * rng.random() ** 3 + (.012 if rng.random() < .05 else 0))
        X, Y = px(x, y); S = s * H * SS
        d.ellipse((X - S, Y - S, X + S, Y + S), fill=fill)
        if rng.random() < .35:  # a streak behind it
            l = S * (3 + 7 * rng.random())
            d.polygon([(X - math.sin(a) * S * .7, Y + math.cos(a) * S * .7), (X + math.sin(a) * S * .7, Y - math.cos(a) * S * .7), (X - math.cos(a) * l, Y - math.sin(a) * l)], fill=fill)


def spikes(d, cx, cy, r0, r1, count, rng, fill=255, a0=0, a1=2 * math.pi):
    """Sharp spikes thrown out of (cx, cy)."""
    X, Y = px(cx, cy)
    for _ in range(count):
        a = a0 + (a1 - a0) * rng.random()
        r = (r0 + (r1 - r0) * rng.random() ** 1.6) * H * SS
        w = (.004 + .018 * rng.random()) * H * SS
        d.polygon([(X - math.sin(a) * w, Y + math.cos(a) * w), (X + math.sin(a) * w, Y - math.cos(a) * w), (X + math.cos(a) * r, Y + math.sin(a) * r)], fill=fill)


def roughen(img, rng, amount=1.2):
    """The edge of everything a little torn, as ink on paper."""
    a = np.asarray(img.filter(ImageFilter.GaussianBlur(amount * SS)), dtype=np.float32) / 255
    noise = np.random.default_rng(rng.randrange(1 << 30)).random(a.shape, dtype=np.float32)
    noise = np.asarray(Image.fromarray((noise * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(1.1 * SS)), dtype=np.float32) / 255
    out = (a + (noise - .5) * .55 > .5)
    return Image.fromarray((out * 255).astype(np.uint8))


def save(name, rgb, alpha):
    """rgb: an L image (a grey for every pixel) or an int; alpha: an L image or an int."""
    os.makedirs(OUT, exist_ok=True)
    g = rgb if isinstance(rgb, Image.Image) else Image.new('L', (W, H), rgb)
    a = alpha if isinstance(alpha, Image.Image) else Image.new('L', (W, H), alpha)
    Image.merge('RGBA', (g, g, g, a)).save(os.path.join(OUT, name + '.png'), optimize=True)
    print(name)


# ───────────────────────────── the dash ─────────────────────────────

def dash_a():
    """Black, the brush coming in over the top right and round the right side, ink thrown off it to the left."""
    rng = random.Random(11)
    img = canvas(0); d = ImageDraw.Draw(img)
    brush(d, [(.46, -.12), (.72, .02), (.93, .22), (1.04, .52), (1.02, .86), (.94, 1.12)], .30, .16, rng, belly=.10, dry=26)
    spikes(d, .86, .52, .08, .34, 60, rng, a0=math.pi * .72, a1=math.pi * 1.28)
    splatter(d, .80, .50, .30, 520, rng, aim=math.pi)
    splatter(d, .86, .30, .16, 160, rng, aim=math.pi * 1.2)
    splatter(d, .88, .74, .16, 160, rng, aim=math.pi * .82)
    return done(roughen(img, rng))


def dash_b():
    """Black, the stroke swept on along the bottom to the left, its ink still flying at the upper right."""
    rng = random.Random(23)
    img = canvas(0); d = ImageDraw.Draw(img)
    brush(d, [(1.06, .30), (.96, .62), (.72, .86), (.40, .95), (.10, .90), (-.10, .74)], .13, .26, rng, belly=.06, dry=44)
    brush(d, [(.80, .10), (.50, .13), (.20, .22)], .012, .003, rng)
    brush(d, [(.02, .58), (.12, .66), (.26, .72)], .006, .05, rng, dry=6)
    d.ellipse(px(.74, .26) + px(.80, .36), fill=255)
    spikes(d, .83, .30, .05, .30, 46, rng, a0=math.pi * .55, a1=math.pi * 1.35)
    splatter(d, .84, .30, .26, 620, rng)
    splatter(d, .92, .10, .16, 200, rng)
    return done(roughen(img, rng))


def burst():
    """The blow as it arrives: a star of light low at the left, the air torn round it, and its streak out to the right."""
    rng = random.Random(37)
    cx, cy = .385, .665
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    dx, dy = (xx / W - cx) * W / H, yy / H - cy
    r = np.sqrt(dx * dx + dy * dy); a = np.arctan2(dy, dx)
    # the torn air: streaks out of the middle, bright near it
    n = np.random.default_rng(5).random(720).astype(np.float32)
    ray = n[((a + math.pi) / (2 * math.pi) * 719).astype(np.int32)]
    ray2 = n[(((a + math.pi) / (2 * math.pi) * 240).astype(np.int32) * 3) % 720]
    fuzz = np.clip(1.25 - r / .42, 0, 1) ** 1.5 * (.35 + .65 * ray * ray2 ** .5)
    fuzz *= np.clip((r - .02) / .05, 0, 1) * .5 + .5
    g = np.clip(fuzz * 235 + 26 * np.clip(1 - r / .9, 0, 1), 0, 255)
    img = Image.fromarray(g.astype(np.uint8)).resize((W * SS, H * SS), Image.BILINEAR)
    d = ImageDraw.Draw(img)
    X, Y = px(cx, cy)
    # the star's rays, thin and long
    for ang, length, width in ((-1.18, .95, .016), (1.96, .75, .016), (-2.62, .62, .011), (.5, .5, .011), (-.33, .30, .008), (2.5, .42, .007), (3.0, .5, .01), (-1.9, .3, .007)):
        L, Wd = length * H * SS, width * H * SS
        d.polygon([(X - math.sin(ang) * Wd, Y + math.cos(ang) * Wd), (X + math.sin(ang) * Wd, Y - math.cos(ang) * Wd), (X + math.cos(ang) * L, Y + math.sin(ang) * L)], fill=255)
    for rad, v in ((.085, 150), (.06, 215), (.04, 255)):
        R = rad * H * SS
        d.ellipse((X - R * 1.25, Y - R, X + R * 1.25, Y + R), fill=v)
    # its streak to the right: wedges widening to the edge, slits of black between them
    for y0, y1, x0 in ((.575, .50, .50), (.60, .61, .47), (.635, .70, .52), (.665, .76, .60)):
        d.polygon([px(x0, cy - .008), px(1.02, y0), px(1.02, y1)], fill=255)
    d.polygon([px(.50, cy - .012), px(1.02, .52), px(1.02, .74)], fill=255)
    for y0 in (.585, .628, .668, .705):
        d.polygon([px(.70 + .1 * rng.random(), y0 + .004), px(1.02, y0 - .006), px(1.02, y0 + .012)], fill=14)
    return done(img)


# ───────────────────────────── the flash ─────────────────────────────

def cut_line():
    """A white line torn level across the black, where the blade went."""
    rng = random.Random(41)
    img = canvas(0); d = ImageDraw.Draw(img)
    pts = [(-.02 + 1.04 * i / 60, .5 + .006 * math.sin(i * .7) + rng.gauss(0, .0035)) for i in range(61)]
    ribbon(d, pts, [.011 + .012 * rng.random() + (.012 if 26 < i < 36 else 0) for i in range(61)])
    return done(roughen(img, rng, .7))


def wash():
    """Ink washed down the white, grey, and one black line dropped through its middle."""
    rng = random.Random(53)
    a = np.zeros((H, W), np.float32)
    g = np.random.default_rng(9)
    # blots, pulled down into streaks
    spots = Image.new('L', (W // 4, H // 4), 0); d = ImageDraw.Draw(spots)
    for _ in range(170):
        x = rng.gauss(.36, .2) if rng.random() < .7 else rng.gauss(.72, .1)
        y = rng.gauss(.55, .22) if x < .55 else rng.gauss(.25, .12)
        s = (.01 + .05 * rng.random() ** 2)
        X, Y, S = x * W / 4, y * H / 4, s * H / 4
        d.ellipse((X - S, Y - S * 1.7, X + S, Y + S * 1.7), fill=int(120 + 135 * rng.random()))
    spots = spots.resize((W, H), Image.BILINEAR).filter(ImageFilter.GaussianBlur(5))
    a = np.asarray(spots, np.float32) / 255
    streak = np.asarray(Image.fromarray((g.random((H // 24, W)) * 255).astype(np.uint8)).resize((W, H), Image.BILINEAR), np.float32) / 255
    a = np.clip(a * (.55 + .9 * streak) - .12, 0, 1) * .42
    alpha = Image.fromarray((a * 255).astype(np.uint8))
    line = canvas(0); d = ImageDraw.Draw(line)
    ribbon(d, [(.5 + .002 * math.sin(i * .5), -.02 + 1.04 * i / 40) for i in range(41)], [.02 * W / H * .55 + .003 * rng.random() for _ in range(41)])
    line = done(roughen(line, rng, .6))
    alpha = Image.fromarray(np.maximum(np.asarray(alpha), np.asarray(line)))
    return alpha


SHI = [
    # the bar over it
    ([(.07, .215), (.30, .175), (.62, .155), (.93, .150)], .105, .085, .012, 10),
    # the left half: the stroke falling left, the corner and its long sweep down to the left, the dot
    ([(.36, .23), (.30, .34), (.20, .47), (.10, .56)], .085, .028, .0, 5),
    ([(.27, .385), (.40, .355), (.535, .335)], .060, .078, .0, 0),
    ([(.535, .335), (.49, .50), (.37, .70), (.21, .86), (.06, .94)], .085, .020, .014, 12),
    ([(.265, .525), (.33, .575), (.40, .65)], .040, .085, .0, 3),
    # the right half: its short stroke, and the one down, round the bottom and hooked up
    ([(.90, .355), (.79, .43), (.655, .50)], .075, .034, .0, 5),
    ([(.635, .225), (.628, .45), (.625, .66), (.66, .80), (.80, .845), (.93, .82), (.955, .70)], .090, .050, .020, 9),
]


def shi_strokes(d, rng, box, fill=255, dry=True):
    """The character for death, brushed: its seven strokes within box (x0, y0, x1, y1, fractions of the frame)."""
    x0, y0, x1, y1 = box
    for pts, w0, w1, belly, bristles in SHI:
        brush(d, [(x0 + (x1 - x0) * x, y0 + (y1 - y0) * y) for x, y in pts], w0 * (y1 - y0), w1 * (y1 - y0), rng, belly=belly * (y1 - y0), dry=bristles if dry else 0, fill=fill)


def shi():
    """White paper: ink spattered in a band across it, a level line through the band, a circle brushed round the middle
    and the character for death within it."""
    rng = random.Random(67)
    # the grey spatter (a band through the middle, denser by the circle)
    grey = canvas(0); d = ImageDraw.Draw(grey)
    for _ in range(1500):
        x = rng.random(); y = .5 + rng.gauss(0, .075) * (1.5 if abs(x - .5) < .3 else 1)
        s = (.0012 + .012 * rng.random() ** 4 + (.02 if rng.random() < .02 else 0))
        X, Y = px(x, y); S = s * H * SS
        d.ellipse((X - S, Y - S, X + S, Y + S), fill=255)
    for _ in range(70):
        x = .22 + .56 * rng.random(); y = .5 + rng.gauss(0, .1)
        s = .012 + .03 * rng.random(); X, Y = px(x, y); S = s * H * SS
        d.ellipse((X - S * 1.3, Y - S, X + S * 1.3, Y + S), fill=255)
    grey = done(roughen(grey, rng, 1.0))
    black = canvas(0); d = ImageDraw.Draw(black)
    # the level line
    ribbon(d, [(-.02 + 1.04 * i / 50, .505 + .004 * math.sin(i * .4)) for i in range(51)], [.004 + .007 * (1 - abs(i - 25) / 25) + .003 * rng.random() for i in range(51)])
    # the circle: three turns of the brush, not quite one on another
    cx, cy = .5, .5
    for ry, turn, w in ((.455, .3, .034), (.47, 1.7, .012), (.442, 3.6, .009), (.485, 4.4, .006)):
        rx = ry
        pts = []; ws = []
        for i in range(121):
            u = i / 120; a = turn + u * 2 * math.pi * 1.04
            wob = 1 + .012 * math.sin(a * 3 + turn)
            pts.append((cx + math.cos(a) * rx * wob * H / W * 1.0, cy + math.sin(a) * ry * wob))
            ws.append(max(.0015, w * (.35 + .65 * math.sin(math.pi * min(1, u * 1.15)) ** .6) * (1 + .25 * math.sin(a * 2 + turn * 2))))
        ribbon(d, pts, ws)
    shi_strokes(d, rng, (.5 - .36 * H / W, .14, .5 + .36 * H / W, .86))
    splatter(d, .5, .5, .40, 240, rng, size=.8)
    black = done(roughen(black, rng, 1.1))
    g = 255 - np.asarray(grey, np.float32) * (95 / 255)
    g = np.where(np.asarray(black) > 127, 0, g)
    return Image.fromarray(g.astype(np.uint8))


if __name__ == '__main__':
    save('dash_a', dash_a(), 255)
    save('dash_b', dash_b(), 255)
    save('burst', burst(), 255)
    save('cut_line', 255, cut_line())
    save('wash', 0, wash())
    save('shi', shi(), 255)
