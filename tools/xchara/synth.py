"""The synth parts X Chara's made-up sounds are built from (noise and tones only, fixed seeds, nothing to license): the
hack's two (make_q_sounds.py) use them. Until 2.118.0 this file also made the basic attack's sounds (xchara_m1_*, the
hack's hits with a hacker's noises in them, 2.114.0; xchara_swing_*, the plain swings', 2.117.0); those are gone: the
attack's one sound is the user's own now, taken out of their video (extract_slash.py).

Each sound is mono at 48 kHz, set to its own loudness (EBU R 128 / BS.1770, LOUDNESS) with only its peaks turned down
under CEILING_DB.
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

# each sound's loudness (LUFS), filled in by the tool that makes it
LOUDNESS = {}
CEILING_DB = -1.0
# an old screen switched off (screen_off): it folds flat, then goes out (seconds)
X_FOLD, X_OUT = .06, .06

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
