"""Synthesises the sounds of X Chara's basic attack (2.114.0, user request: "엑스차라 평타 만들자 너가 창작해줘 엑스차라
컨셉은 언더테일에 나오는 차라가 해커의 능력을 사용하는거야 4타로 끝나고 멋있게": mine to make up, its sounds too).

usage: python3 -I tools/xchara/make_m1_sounds.py [OUT_DIR]

A knife's whooshes with a hacker's noises in them. Every swing's file starts LEAD seconds (a tick) into its hit: the
client plays it a tick after the hit starts (client/xchara/KnifeClient), so what is at (blow - LEAD) in a file is heard
as that blow lands (XCharaCombo's blows: 4 / 3, 6 / 3, 5, 7 / 7, 11 ticks).

xchara_m1_1 (잘라내기): the cut's whoosh, a short bit-crushed zap as it lands and a run of data blips after it.
xchara_m1_2 (복사·붙여넣기): the backhand's whoosh with a rising two-note blip (copied); then, as its second blow lands,
    the very same whoosh again, sample-reduced and bit-crushed (pasted), and the same blip.
xchara_m1_3 (연타): three quick stabs, each a short whoosh and the zip of a knife of light flying off, and the two-note
    chime of a popup opening (each a tone higher); then the three popups closing, one tick after another.
xchara_m1_4 (삭제): the wind-up (a stuttering tone climbing, noise swelling), the two heavy cuts (the second lower, with
    an error buzz under it), and the X going out as an old screen switched off does (a click, a tone falling right
    away, a last blip).
xchara_m1_hit: a blow landing: a click, a short thud, a crunch of bit-crushed noise and a blip falling.
xchara_m1_hit_last: the finisher's last blow: the same heavier and lower, and a scatter of blips after it (pixels flying).

The plain swings (the hack off, 2.117.0, user request: "이번에는 Q 껐을때 평타를 만들어줘 이펙트 없이 그냥 휘두르기만해
그대신 모션을 좋게 만들어봐": no hacker's noises in these, a knife through the air and nothing else; XCharaCombo's hits
5 to 8, their blows 4 / 4 / 6 / 8 ticks in):
xchara_swing_1 (가로베기): one clean whoosh across.
xchara_swing_2 (올려베기): the backhand's whoosh, rising and ending higher.
xchara_swing_3 (내려찍기): the knife lifted (a faint breath of air), then a long heavy whoosh down, a low woof under it.
xchara_swing_4 (찌르기): the knife drawn back (a faint breath), then a short hard thrust and the front foot stamping.
xchara_swing_hit: a plain blow landing: a click, a thud and a slice of noise falling, nothing crushed.
xchara_swing_hit_last: the lunge landing: the same heavier, lower and longer.

Made from noise and tones only (fixed seeds), so there is nothing to license and every run makes the same files. Each is
mono at 48 kHz, set to its own loudness (EBU R 128 / BS.1770, LOUDNESS) with only its peaks turned down under
CEILING_DB, and written as OGG Vorbis by ffmpeg (on the PATH). OUT_DIR defaults to the mod's sounds folder.
"""
import os
import subprocess
import sys
import tempfile
import numpy as np
from scipy import signal
from scipy.ndimage import minimum_filter1d, uniform_filter1d
from scipy.io import wavfile

SR = 48000
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, 'src/main/resources/assets/celestialfall/sounds')

# the swings sit a little under the blows; the finisher's last blow is the loudest thing in the combo
LOUDNESS = {'xchara_m1_1': -18.0, 'xchara_m1_2': -18.0, 'xchara_m1_3': -18.0, 'xchara_m1_4': -16.5,
            'xchara_m1_hit': -16.0, 'xchara_m1_hit_last': -14.5,
            # the plain swings a little under the hack's (nothing but air in them); their blows as loud as the hack's
            'xchara_swing_1': -19.0, 'xchara_swing_2': -19.0, 'xchara_swing_3': -18.0, 'xchara_swing_4': -18.0,
            'xchara_swing_hit': -16.0, 'xchara_swing_hit_last': -14.5}
CEILING_DB = -1.0
# a swing's file starts this long into its hit (the client plays it a tick after the hit starts)
LEAD = 1 / 20
# when each hit's blows land (seconds into the hit: XCharaCombo.BLOWS)
BLOWS = {1: [4 / 20], 2: [3 / 20, 6 / 20], 3: [3 / 20, 5 / 20, 7 / 20], 4: [7 / 20, 11 / 20]}
# the plain swings' blows (XCharaCombo.BLOWS, hits 5 to 8)
PLAIN = {1: 4 / 20, 2: 4 / 20, 3: 6 / 20, 4: 8 / 20}
# the third hit's popups start closing this long into it and this far apart (KnifeCombo.WINDOWS_SHUT, WINDOW_STEP,
# GlitchFx.WINDOW_SHUT); the finisher's X starts going out this long after its last blow, folds flat and goes out
# (KnifeCombo.X_HOLD, GlitchFx.X_FOLD, X_OUT)
WINDOWS_SHUT, WINDOW_STEP, WINDOW_FOLD = 7 / 20 + .15, .03, .06
X_HOLD, X_FOLD, X_OUT = .14, .06, .06


def t(d):
    return np.arange(int(round(d * SR))) / SR


def noise(n, seed):
    return np.random.default_rng(seed).standard_normal(n)


def band(x, lo, hi, order=2):
    b, a = signal.butter(order, [lo / (SR / 2), hi / (SR / 2)], 'band')
    return signal.lfilter(b, a, x)


def low(x, hz, order=2):
    b, a = signal.butter(order, hz / (SR / 2), 'low')
    return signal.lfilter(b, a, x)


def high(x, hz, order=2):
    b, a = signal.butter(order, hz / (SR / 2), 'high')
    return signal.lfilter(b, a, x)


def swept(x, centre, q):
    """A band-pass whose centre (Hz, one per sample) moves through the sound, {q} its sharpness, block by block (state kept)."""
    out = np.zeros_like(x)
    block = 128
    zi = None
    for i in range(0, len(x), block):
        f = float(np.clip(centre[min(i + block // 2, len(x) - 1)], 40, SR * .45))
        bw = f / q
        lo, hi = max(20.0, f - bw / 2), min(SR * .48, f + bw / 2)
        b, a = signal.butter(1, [lo / (SR / 2), hi / (SR / 2)], 'band')
        if zi is None:
            zi = np.zeros(max(len(a), len(b)) - 1)
        out[i:i + block], zi = signal.lfilter(b, a, x[i:i + block], zi=zi)
    return out


def norm(x):
    return x / (np.abs(x).max() + 1e-12)


def smooth(x):
    x = np.clip(x, 0, 1)
    return x * x * (3 - 2 * x)


def tone(f, phase=0.0):
    """A tone whose frequency (Hz, one per sample, or one number) moves."""
    return np.sin(2 * np.pi * np.cumsum(np.broadcast_to(f, f.shape if hasattr(f, 'shape') else (1,))) / SR + phase)


def square(f, duty=.5):
    """A square wave whose frequency (Hz, one per sample) moves, its edges eased a little (no aliasing whine)."""
    phase = np.cumsum(f) / SR
    return np.tanh(6 * np.sin(2 * np.pi * phase) + 6 * (2 * duty - 1))


def saw(f):
    phase = np.cumsum(f) / SR
    return 2 * (phase - np.floor(phase + .5))


def crush(x, hold, bits):
    """Sample-and-hold every {hold} samples and {bits} bits of depth: what a cheap converter does to a sound."""
    idx = (np.arange(len(x)) // hold) * hold
    y = x[idx]
    q = 2 ** (bits - 1)
    return np.round(y * q) / q


def bell(x, at, rise, fall):
    """An envelope peaking at {at}: a rounded rise over about {rise} seconds before it and a fall over {fall} after it."""
    d = x - at
    return np.where(d < 0, np.exp(-(d / rise) ** 2), np.exp(-(d / fall) ** 2))


def put(out, x, at):
    """Adds {x} into {out} starting {at} seconds in (clipped to its ends)."""
    i = int(round(at * SR))
    if i < 0:
        x = x[-i:]
        i = 0
    n = min(len(x), len(out) - i)
    if n > 0:
        out[i:i + n] += x[:n]


def k_weighted(x):
    b1, a1 = [1.53512485958697, -2.69169618940638, 1.19839281085285], [1, -1.69065929318241, 0.73248077421585]
    b2, a2 = [1, -2, 1], [1, -1.99004745483398, 0.99007225036621]
    return signal.lfilter(b2, a2, signal.lfilter(b1, a1, x))


def loudness(x):
    """Integrated loudness in LUFS (ITU-R BS.1770: K-weighted, 400 ms blocks, gated)."""
    y = k_weighted(x)
    n, hop = int(0.4 * SR), int(0.1 * SR)
    if len(y) < n:
        y = np.pad(y, (0, n - len(y)))
    z = np.array([np.mean(y[i:i + n] ** 2) for i in range(0, len(y) - n + 1, hop)])
    l = -0.691 + 10 * np.log10(z + 1e-15)
    g = z[l > -70]
    rel = -0.691 + 10 * np.log10(g.mean()) - 10
    g = z[(l > -70) & (l > rel)]
    return -0.691 + 10 * np.log10(g.mean())


def limit(x, ceiling, look=.003, release=.04):
    """Turns down only what would go over the ceiling: the gain eases down over the `look` seconds before such a peak
    and comes back up over `release` seconds after it (tools/meteor/user_fall_sound.py's limiter)."""
    n = int(look * SR)
    need = np.minimum(1, ceiling / np.maximum(np.abs(x), 1e-12))
    eased = uniform_filter1d(minimum_filter1d(need, 2 * n + 1), n + 1)
    a = 1 - np.exp(-1 / (release * SR))
    gain = np.empty_like(eased)
    g = 1.0
    for i, e in enumerate(eased):
        g = min(e, g + (1 - g) * a)
        gain[i] = g
    return x * gain


def finish(x, name, fade_in=.002, fade_out=.04):
    x = low(high(x, 35), 13500, 4)
    x[:int(fade_in * SR)] *= np.linspace(0, 1, int(fade_in * SR))
    n = int(fade_out * SR)
    x[-n:] *= np.linspace(1, 0, n)
    ceiling = 10 ** (CEILING_DB / 20)
    for _ in range(4):
        x = limit(x * 10 ** ((LOUDNESS[name] - loudness(x)) / 20), ceiling)
    return x


# ───────────────────────────── the parts ─────────────────────────────

def whoosh(d, at, rise, fall, f0, f1, f2, seed, weight=0.0):
    """A blade through the air: noise through a band that climbs from {f0} to {f1} Hz as it comes ({at}) and drops to
    {f2} after, a hiss of air over it, and (for a heavy cut) {weight} of a low woof under it."""
    x = t(d)
    n = len(x)
    u = np.clip((x - (at - 2.2 * rise)) / (2.2 * rise), 0, 1)
    v = np.clip((x - at) / (2.5 * fall), 0, 1)
    centre = f0 + (f1 - f0) * u ** 1.6 - (f1 - f2) * smooth(v)
    body = swept(noise(n, seed), centre, 1.5) * bell(x, at, rise, fall)
    air = high(noise(n, seed + 1), 5200) * bell(x, at + .004, rise * .55, fall * .7)
    out = norm(body) + .32 * norm(air)
    if weight > 0:
        woof = low(noise(n, seed + 2), 240, 3) * bell(x, at + .01, rise * 1.2, fall * 1.6)
        out += weight * norm(woof)
    return out


def zap(d, seed, f=1900.0):
    """A short bit-crushed zap: a buzzing square falling a little, and crushed noise with it."""
    x = t(d)
    n = len(x)
    env = smooth(x / .002) * np.exp(-x / (d * .32))
    buzz = square(np.full(n, f) * (1 - .35 * x / d) * (1 + .5 * (noise(n, seed) > 1.2)))
    grit = crush(norm(band(noise(n, seed + 1), 900, 6500)), 7, 4)
    return crush((.6 * buzz + .7 * grit) * env, 5, 5)


def blips(d, count, seed, lo=900.0, hi=4200.0):
    """A run of data: {count} short square notes one after another, at pitches out of one scale, fading."""
    rng = np.random.default_rng(seed)
    out = np.zeros(int(d * SR))
    scale = np.array([0, 3, 5, 7, 10, 12, 15, 17, 19, 22, 24])
    at = 0.0
    for i in range(count):
        length = rng.uniform(.007, .014)
        f = lo * 2 ** (rng.choice(scale) / 12)
        while f > hi:
            f /= 2
        x = t(length)
        note = square(np.full(len(x), f)) * smooth(x / .0008) * smooth((length - x) / .0015)
        put(out, note * (1 - .6 * i / count), at)
        at += length + rng.uniform(.001, .006)
        if at >= d:
            break
    return low(out, 7000)


def chime(f_a, f_b, each=.045):
    """A popup's two notes, one after the other: soft squares, the second falling away."""
    x = t(each)
    a = square(np.full(len(x), f_a), .5) * smooth(x / .002) * smooth((each - x) / .008)
    x2 = t(each * 1.9)
    b = square(np.full(len(x2), f_b), .5) * smooth(x2 / .002) * np.exp(-x2 / (each * .7))
    out = np.zeros(len(x) + len(x2))
    put(out, a, 0)
    put(out, b, each * .92)
    return low(out, 5200, 2)


def shut(f=1500.0):
    """A popup closing: a tick and a quick note falling."""
    d = .05
    x = t(d)
    tick = high(noise(len(x), 77), 3000) * np.exp(-x / .0015)
    fallen = tone(f * np.exp(-x / .016) + 240) * np.exp(-x / .014) * smooth(x / .001)
    return .8 * norm(tick) + .7 * fallen


def thud(d, f0, f1, tau, seed):
    """A blow's body: a low tone falling from {f0} to {f1} Hz, and a little low noise with it."""
    x = t(d)
    body = tone(f1 + (f0 - f1) * np.exp(-x / (tau * .45))) * np.exp(-x / tau) * smooth(x / .0015)
    air = low(noise(len(x), seed), 420, 2) * np.exp(-x / (tau * .6))
    return body + .35 * norm(air)


def screen_off(d=.34):
    """An old screen switched off: a click, a tone falling right away as the picture folds flat, a faint crackle, and
    a last blip as its line goes out."""
    x = t(d)
    n = len(x)
    out = np.zeros(n)
    click = high(noise(n, 91), 2500) * np.exp(-x / .0012)
    out += .9 * norm(click)
    fold = tone(60 + 3400 * np.exp(-x / .028)) * np.exp(-x / .05) * smooth(x / .001)
    out += .8 * fold
    low_fall = tone(45 + 240 * np.exp(-x / .04)) * np.exp(-x / .07) * smooth(x / .002)
    out += .55 * low_fall
    crackle = crush(norm(band(noise(n, 92), 1500, 7000)), 9, 3) * np.exp(-x / .05) * (noise(n, 93) > .9)
    out += .25 * crackle
    xb = t(.03)
    blip = tone(np.full(len(xb), 2100.0)) * smooth(xb / .001) * np.exp(-xb / .008)
    put(out, .5 * blip, X_FOLD + X_OUT * .8)
    return out


# ───────────────────────────── the files ─────────────────────────────

def hit1():
    """잘라내기: the cut's whoosh, a zap as it lands, a run of data after it."""
    d = .62
    at = BLOWS[1][0] - LEAD
    out = whoosh(d, at, .05, .034, 420, 3600, 1300, 101)
    put(out, .42 * zap(.045, 111), at + .004)
    put(out, .2 * blips(.11, 9, 121), at + .03)
    return finish(out, 'xchara_m1_1')


def hit2():
    """복사·붙여넣기: the backhand's whoosh and a rising blip (copied); then the same whoosh again, crushed (pasted), and the same blip."""
    d = .7
    a, b = BLOWS[2][0] - LEAD, BLOWS[2][1] - LEAD
    cut = whoosh(.34, .1, .04, .032, 620, 4100, 1500, 201)
    x = t(.028)
    note = np.concatenate([square(np.full(len(x), 1318.5)) * smooth(x / .001) * smooth((.028 - x) / .004),
                           square(np.full(len(x), 1760.0)) * smooth(x / .001) * np.exp(-x / .012)])
    note = low(note, 6000)
    out = np.zeros(int(d * SR))
    put(out, cut, a - .1)
    put(out, .26 * note, a + .006)
    # pasted: the very same samples, as a cheap copy of them
    put(out, .82 * crush(norm(cut), 6, 5) * np.abs(cut).max(), b - .1)
    put(out, .3 * crush(note, 4, 4), b + .006)
    return finish(out, 'xchara_m1_2')


def hit3():
    """연타: three stabs (a short whoosh, the zip of a knife of light, a popup's chime, each higher), then the popups closing."""
    d = .86
    out = np.zeros(int(d * SR))
    chimes = [(784.0, 622.3), (880.0, 698.5), (987.8, 784.0)]
    for i, blow in enumerate(BLOWS[3]):
        at = blow - LEAD
        stab = whoosh(.2, .07, .02, .022, 1500 + 300 * i, 4300 + 500 * i, 2200, 301 + 7 * i)
        put(out, (.8 + .1 * i) * stab, at - .07)
        x = t(.05)
        zip_ = tone(1100 * 2 ** (i / 6) * np.exp(x / .034)) * smooth(x / .002) * smooth((.05 - x) / .02)
        put(out, .22 * zip_, at)
        put(out, .34 * chime(*chimes[i]), at + .014)
    for i in range(3):
        # the last opened closes first
        put(out, .3 * shut(1700 - 250 * i), WINDOWS_SHUT - LEAD + WINDOW_STEP * i + WINDOW_FOLD * .5)
    return finish(out, 'xchara_m1_3')


def hit4():
    """삭제: the wind-up climbing and stuttering, the two heavy cuts, and the X going out as a screen switched off."""
    d = 1.22
    a, b = BLOWS[4][0] - LEAD, BLOWS[4][1] - LEAD
    x = t(d)
    n = len(x)
    out = np.zeros(n)
    # the wind-up: a saw climbing an octave and a half, gated faster and faster, crushed; noise swelling under it
    w0, w1 = .05, a - .04
    u = np.clip((x - w0) / (w1 - w0), 0, 1)
    gate = (np.sin(2 * np.pi * np.cumsum(18 + 46 * u ** 2) / SR) > -.2).astype(float)
    climb = crush(saw(98 * 2 ** (1.6 * u ** 1.3)) * gate, 6, 4) * smooth(u / .15) * (1 - smooth((x - w1) / .03)) * (.35 + .65 * u)
    out += .3 * low(climb, 5000)
    swell = high(noise(n, 401), 2600) * u ** 3 * (1 - smooth((x - w1) / .02)) * (x >= w0)
    out += .22 * norm(swell)
    # the cuts: heavy, the second lower with an error buzz under it
    put(out, whoosh(.4, .13, .042, .045, 300, 3000, 900, 411, weight=.5), a - .13)
    put(out, .45 * zap(.05, 421, 1500), a + .004)
    put(out, 1.12 * whoosh(.46, .15, .046, .06, 240, 2500, 700, 431, weight=.7), b - .15)
    put(out, .5 * zap(.06, 441, 1100), b + .004)
    xb = t(.11)
    buzz = crush(saw(np.full(len(xb), 87.3)) + .6 * saw(np.full(len(xb), 92.5)), 8, 4) * smooth(xb / .003) * np.exp(-xb / .045)
    put(out, .3 * low(buzz, 2600), b + .006)
    # the X goes out
    put(out, .62 * screen_off(), b + X_HOLD)
    return finish(out, 'xchara_m1_4', fade_out=.06)


def landed():
    """A blow landing: a click, a short thud, a crunch of crushed noise and a blip falling."""
    d = .3
    x = t(d)
    n = len(x)
    out = np.zeros(n)
    click = high(noise(n, 501), 3500) * np.exp(-x / .0016)
    out += .8 * norm(click)
    # (the thud kept under the crunch: by itself it is all there is to hear, and small speakers do not play it)
    out += .34 * thud(d, 220, 88, .036, 502)
    crunch = crush(norm(band(noise(n, 503), 900, 5200)), 8, 4) * np.exp(-x / .03) * smooth(x / .001)
    out += .95 * crunch
    slice_ = swept(noise(n, 504), 5200 * np.exp(-x / .03) + 1400, 2.4) * np.exp(-x / .026) * smooth(x / .001)
    out += .85 * norm(slice_)
    fallen = square(880 * np.exp(-x / .03) + 180) * np.exp(-x / .03) * smooth(x / .001)
    out += .2 * low(fallen, 4500)
    return finish(out, 'xchara_m1_hit', fade_out=.05)


def landed_last():
    """The finisher's last blow: heavier and lower, and a scatter of blips after it (pixels flying)."""
    d = .72
    x = t(d)
    n = len(x)
    out = np.zeros(n)
    click = high(noise(n, 601), 3000) * np.exp(-x / .002)
    out += .8 * norm(click)
    out += .5 * thud(d, 190, 60, .075, 602)
    crunch = crush(norm(band(noise(n, 603), 600, 4600)), 10, 4) * np.exp(-x / .055) * smooth(x / .001)
    out += 1.0 * crunch
    slice_ = swept(noise(n, 604), 4600 * np.exp(-x / .045) + 1000, 2.2) * np.exp(-x / .045) * smooth(x / .001)
    out += .85 * norm(slice_)
    rip = crush(saw(np.full(n, 61.7)) + .5 * saw(np.full(n, 65.4)), 9, 4) * np.exp(-x / .06) * smooth(x / .002)
    out += .3 * low(rip, 2200)
    put(out, .3 * blips(.3, 22, 611, 700, 5200), .05)
    return finish(out, 'xchara_m1_hit_last', fade_out=.1)


def breath(d, at, width, seed, f=900.0):
    """A faint breath of air as the knife is moved without cutting (lifted, drawn back): soft noise swelling and gone."""
    x = t(d)
    return low(band(noise(len(x), seed), f * .5, f * 2.2), 2600) * bell(x, at, width, width * 1.3)


def swing1():
    """가로베기: one clean whoosh across."""
    d = .5
    at = PLAIN[1] - LEAD
    out = whoosh(d, at, .05, .04, 380, 3300, 1100, 701, weight=.12)
    return finish(out, 'xchara_swing_1')


def swing2():
    """올려베기: the backhand's whoosh, rising and ending higher."""
    d = .5
    at = PLAIN[2] - LEAD
    out = whoosh(d, at, .046, .042, 520, 4300, 1900, 711, weight=.1)
    return finish(out, 'xchara_swing_2')


def swing3():
    """내려찍기: the knife lifted (a faint breath), then a long heavy whoosh down with a low woof under it."""
    d = .66
    at = PLAIN[3] - LEAD
    out = np.zeros(int(d * SR))
    put(out, .16 * norm(breath(.2, .09, .04, 721)), .01)
    put(out, whoosh(.56, .21, .062, .055, 240, 2700, 620, 722, weight=.6), at - .21)
    # the blade stopped short of the ground: a small dull knock of the body's weight coming down
    put(out, .16 * thud(.18, 150, 62, .045, 723), at + .03)
    return finish(out, 'xchara_swing_3', fade_out=.06)


def swing4():
    """찌르기: the knife drawn back (a faint breath), then a short hard thrust and the front foot stamping."""
    d = .7
    at = PLAIN[4] - LEAD
    out = np.zeros(int(d * SR))
    put(out, .18 * norm(breath(.26, .1, .05, 731, 700)), .0)
    put(out, whoosh(.36, .15, .03, .034, 900, 5200, 2300, 732, weight=.25), at - .15)
    put(out, .3 * thud(.22, 140, 55, .05, 733), at - .012)
    return finish(out, 'xchara_swing_4', fade_out=.06)


def cut():
    """A plain blow landing: a click, a thud, a slice of noise falling. Nothing crushed."""
    d = .3
    x = t(d)
    n = len(x)
    out = np.zeros(n)
    click = high(noise(n, 801), 3500) * np.exp(-x / .0016)
    out += .75 * norm(click)
    out += .5 * thud(d, 210, 84, .04, 802)
    knock = band(noise(n, 803), 320, 1500) * np.exp(-x / .02) * smooth(x / .001)
    out += .7 * norm(knock)
    slice_ = swept(noise(n, 804), 5200 * np.exp(-x / .03) + 1400, 2.4) * np.exp(-x / .028) * smooth(x / .001)
    out += .9 * norm(slice_)
    return finish(out, 'xchara_swing_hit', fade_out=.05)


def cut_last():
    """The lunge landing: heavier, lower and longer."""
    d = .55
    x = t(d)
    n = len(x)
    out = np.zeros(n)
    click = high(noise(n, 811), 3000) * np.exp(-x / .002)
    out += .75 * norm(click)
    out += .75 * thud(d, 180, 56, .08, 812)
    knock = band(noise(n, 813), 240, 1200) * np.exp(-x / .04) * smooth(x / .001)
    out += .8 * norm(knock)
    slice_ = swept(noise(n, 814), 4600 * np.exp(-x / .05) + 900, 2.2) * np.exp(-x / .05) * smooth(x / .001)
    out += .9 * norm(slice_)
    rush = low(noise(n, 815), 500, 2) * bell(x, .05, .03, .14)
    out += .3 * norm(rush)
    return finish(out, 'xchara_swing_hit_last', fade_out=.1)


def main():
    os.makedirs(OUT, exist_ok=True)
    made = {'xchara_m1_1': hit1(), 'xchara_m1_2': hit2(), 'xchara_m1_3': hit3(), 'xchara_m1_4': hit4(),
            'xchara_m1_hit': landed(), 'xchara_m1_hit_last': landed_last(),
            'xchara_swing_1': swing1(), 'xchara_swing_2': swing2(), 'xchara_swing_3': swing3(), 'xchara_swing_4': swing4(),
            'xchara_swing_hit': cut(), 'xchara_swing_hit_last': cut_last()}
    with tempfile.TemporaryDirectory() as tmp:
        for name, x in made.items():
            wav = os.path.join(tmp, name + '.wav')
            wavfile.write(wav, SR, (x * 32767).astype(np.int16))
            subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', wav, '-ac', '1', '-c:a', 'libvorbis', '-q:a', '5',
                            os.path.join(OUT, name + '.ogg')], check=True)
            print(f'{name}: {len(x) / SR:.2f} s, {loudness(x):.1f} LUFS, peak {20 * np.log10(np.abs(x).max()):.1f} dB')


if __name__ == '__main__':
    main()
