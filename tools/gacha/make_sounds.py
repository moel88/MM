"""Synthesises the gacha's sounds (user requests: 2.40.0 holy and luxurious, every one of them; 2.41.0 remade, they were
too loud and noisy: calm, clear and quiet).

usage: python3 -I tools/gacha/make_sounds.py OUT_DIR [NAME,...]

Everything is made here from first principles, so there are no samples to license: a choir singing "ah" (many detuned
voices with vibrato through vowel formants), glass and church bells (their inharmonic partials), a harp (plucked
strings, Karplus-Strong), a soft pad, a few sparkles, and a small hall's reverb; for Bomi's opening of the ten-pull, a
glass set down on wood, steps, footfalls on stone, a breath of relief and a cushion. 2.41.0 dropped the risers, cymbal
washes, whooshes, booms and brass, shortened the reverb, softened every attack and set each sound to a loudness well
below 2.40.0's (EBU R 128 / BS.1770, its own target in LOUDNESS). The music sits in D major. Each sound is written as
OGG Vorbis (48 kHz stereo) by ffmpeg, which must be on the PATH.
"""
import os
import subprocess
import sys
import tempfile
import numpy as np
from scipy import signal
from scipy.io import wavfile

SR = 48000
RNG = np.random.default_rng(2400)


def secs(d):
    return np.arange(int(round(d * SR))) / SR


NOTES = {'C': -9, 'D': -7, 'E': -5, 'F': -4, 'G': -2, 'A': 0, 'B': 2}


def hz(name):
    """'F#4' -> 369.99 (A4 = 440)."""
    letter, rest = name[0], name[1:]
    shift = 0
    while rest and rest[0] in '#b':
        shift += 1 if rest[0] == '#' else -1
        rest = rest[1:]
    return 440.0 * 2 ** ((NOTES[letter] + shift + 12 * (int(rest) - 4)) / 12)


def canvas(d):
    return np.zeros((2, int(round(d * SR))))


def place(dst, src, at, gain=1.0):
    """Mixes stereo (or mono) {src} into {dst} from {at} seconds."""
    if src.ndim == 1:
        src = np.stack([src, src])
    i = int(round(at * SR))
    n = min(src.shape[1], dst.shape[1] - i)
    if n > 0:
        dst[:, i:i + n] += src[:, :n] * gain
    return dst


def pan(mono, p):
    """Equal-power pan, p in -1..1."""
    a = (p + 1) * np.pi / 4
    return np.stack([mono * np.cos(a), mono * np.sin(a)])


def fade(n, attack, release, shape=2.0):
    """An envelope of n samples rising over {attack} s and falling over the last {release} s (raised cosine)."""
    e = np.ones(n)
    a, r = int(attack * SR), int(release * SR)
    if a > 0:
        e[:a] *= (0.5 - 0.5 * np.cos(np.linspace(0, np.pi, a))) ** (shape / 2)
    if r > 0:
        e[-r:] *= (0.5 + 0.5 * np.cos(np.linspace(0, np.pi, r))) ** (shape / 2)
    return e


# ---- noise shaped in time and frequency ----
def shaped_noise(d, mask, seed=0):
    """White noise whose spectrum at time t is mask(t_frames, f_bins) -> gains; stereo, decorrelated."""
    out = []
    for ch in range(2):
        rng = np.random.default_rng(seed * 7 + ch)
        x = rng.standard_normal(int(d * SR) + 4096)
        f, t, z = signal.stft(x, SR, nperseg=2048, noverlap=1536)
        z *= mask(t[None, :], f[:, None])
        _, y = signal.istft(z, SR, nperseg=2048, noverlap=1536)
        out.append(y[:int(d * SR)])
    return np.stack(out)


def band(f, centre, width):
    """A soft band-pass gain around {centre} Hz, {width} octaves wide."""
    oct_ = np.log2(np.maximum(f, 1) / np.maximum(centre, 1))
    return np.exp(-(oct_ / width) ** 2)


# ---- tones ----
def choir(notes, d, vowel='a', voices=6, attack=0.5, release=1.2, breath=0.08, seed=0, bright=1.0, swell=None):
    """A choir holding {notes} for {d} s: each note sung by {voices} voices, slightly apart in pitch and timing, with
    vibrato, through the formants of the vowel; a little breath. {swell}(t) multiplies the loudness."""
    formants = {
        'a': [(800, 80, 1.0), (1150, 90, 0.63), (2900, 120, 0.1), (3900, 130, 0.06), (4950, 140, 0.01)],
        'o': [(450, 70, 1.0), (800, 80, 0.28), (2830, 100, 0.08), (3500, 130, 0.08), (4950, 135, 0.003)],
        'u': [(325, 50, 1.0), (700, 60, 0.16), (2700, 170, 0.02), (3800, 180, 0.01), (4950, 200, 0.001)],
    }[vowel]

    def env(fr):
        g = np.zeros_like(fr)
        for c, bw, a in formants:
            g += a / (1 + ((fr - c) / (bw * 1.6)) ** 2)
        return g * (1 + 0.15 * bright * (fr / 3000))

    rng = np.random.default_rng(seed)
    n = int(d * SR)
    t = np.arange(n) / SR
    out = np.zeros((2, n))
    for f0 in notes:
        for v in range(voices):
            det = 2 ** (rng.normal(0, 6) / 1200)
            rate, depth = 5.0 + rng.uniform(-.5, .5), 0.0045 + rng.uniform(0, .003)
            delay = rng.uniform(0, 0.12)
            vib = depth * np.sin(2 * np.pi * rate * t + rng.uniform(0, 6.3)) * np.clip((t - delay) / 0.6, 0, 1)
            drift = 0.0015 * np.sin(2 * np.pi * rng.uniform(.15, .4) * t + rng.uniform(0, 6.3))
            ph = 2 * np.pi * np.cumsum(f0 * det * (1 + vib + drift)) / SR
            ks = np.arange(1, int(9000 // f0) + 1)
            amps = env(ks * f0) / ks ** 0.6
            amps /= amps.max()
            voice = np.zeros(n)
            for k, a in zip(ks, amps):
                if a < 2e-3:
                    continue
                voice += a * np.sin(k * ph + rng.uniform(0, 6.3))
            # each singer breathes in a little: amplitude wandering slowly
            voice *= 1 + 0.12 * np.sin(2 * np.pi * rng.uniform(.2, .6) * t + rng.uniform(0, 6.3))
            voice *= fade(n, attack + delay, release)
            out += pan(voice, rng.uniform(-.7, .7)) / np.sqrt(voices * len(notes))
    if breath > 0:
        def mask(tt, ff):
            return env(ff) / env(np.array([800.0]))[0]
        air = shaped_noise(d, mask, seed + 3) * fade(n, attack, release)
        out += air * breath
    if swell is not None:
        out *= swell(t)[None, :]
    return out * 0.22


GLASS = [(1, 1.0, 2.6), (2.0, 0.42, 1.6), (2.756, 0.38, 1.1), (3.0, 0.08, 0.8), (5.404, 0.16, 0.45), (8.933, 0.05, 0.22)]
CHURCH = [(0.5, 0.55, 7.0), (1.0, 1.0, 4.5), (1.183, 0.5, 3.2), (1.506, 0.38, 2.6), (2.0, 0.6, 2.2), (2.514, 0.25, 1.4),
          (2.662, 0.2, 1.2), (3.011, 0.18, 1.0), (4.166, 0.12, 0.7), (5.433, 0.07, 0.45), (6.796, 0.04, 0.3)]


def bell(f0, d, partials=GLASS, decay=1.0, bright=1.0, seed=0, strike=0.03):
    """A bell struck at {f0} Hz: its partials ringing down, each with a slow beat, a tiny strike at the start."""
    rng = np.random.default_rng(seed)
    n = int(d * SR)
    t = np.arange(n) / SR
    x = np.zeros(n)
    for ratio, a, dec in partials:
        f = f0 * ratio
        if f > 16000:
            continue
        a = a * (bright if ratio > 2.2 else 1)
        beat = 1 + 0.12 * np.sin(2 * np.pi * rng.uniform(.4, 2.2) * t + rng.uniform(0, 6.3))
        x += a * beat * np.exp(-t / (dec * decay)) * np.sin(2 * np.pi * f * t + rng.uniform(0, 6.3))
    if strike > 0:
        s = rng.standard_normal(n) * np.exp(-t / 0.004)
        b, a2 = signal.butter(2, [min(f0 * 2, 8000) / (SR / 2), min(f0 * 8, 20000) / (SR / 2)], 'band')
        x += signal.lfilter(b, a2, s) * strike
    # rung for {d} s: the last quarter of that fades out, so even the long hum of a big bell never stops short
    x *= fade(n, 0.0015, max(0.05, d * 0.3))
    return x * 0.3


def pluck(f0, d, bright=0.5, decay=0.996, seed=0):
    """A harp string: Karplus-Strong, the burst softened for a warm pluck."""
    rng = np.random.default_rng(seed)
    n = int(d * SR)
    N = max(2, int(round(SR / f0)))
    burst = rng.standard_normal(N)
    b, a = signal.butter(1, min(0.99, 1500 * (1 + 4 * bright) / (SR / 2)))
    burst = signal.lfilter(b, a, burst)
    y = np.zeros(n + N + 1)
    y[:N] = burst
    s = N
    while s < n:
        e = min(s + N, n)
        y[s:e] = decay * 0.5 * (y[s - N:e - N] + y[s - N - 1:e - N - 1] if s - N - 1 >= 0 else y[s - N:e - N])
        s = e
    x = y[:n]
    x -= x.mean()
    x *= fade(n, 0.001, 0.08)
    return x / (np.abs(x).max() + 1e-9) * 0.25


def sparkles(d, count, notes, seed=0, spread=1.0, decay=(.06, .35), start=0.0, accel=0.0):
    """Tiny glassy pings scattered over {d} s (sooner and sooner when {accel} > 0), pitched on {notes}."""
    rng = np.random.default_rng(seed)
    out = canvas(d)
    for i in range(count):
        u = rng.uniform(0, 1)
        if accel > 0:
            u = 1 - (1 - u) ** (1 + accel)
        at = start + u * (d - start - 0.05)
        f = rng.choice(notes) * 2 ** rng.integers(0, 2)
        dec = rng.uniform(*decay)
        n = int(min(dec * 6, d - at) * SR)
        if n <= 16:
            continue
        t = np.arange(n) / SR
        x = (np.sin(2 * np.pi * f * t) + 0.3 * np.sin(2 * np.pi * f * 2.756 * t) * np.exp(-t / (dec * .4))) * np.exp(-t / dec)
        x *= fade(n, 0.0015, 0.01) * rng.uniform(.3, 1)
        place(out, pan(x, rng.uniform(-spread, spread)), at)
    return out * 0.12


def pad(notes, d, attack=0.8, release=1.5, seed=0):
    """A soft warm pad under the choir: detuned saws, filtered dark."""
    rng = np.random.default_rng(seed)
    n = int(d * SR)
    t = np.arange(n) / SR
    out = np.zeros((2, n))
    for f0 in notes:
        for k in range(3):
            det = 2 ** (rng.normal(0, 8) / 1200)
            ph = (f0 * det * t + rng.uniform(0, 1)) % 1.0
            saw = 2 * ph - 1
            out += pan(saw, rng.uniform(-.6, .6))
    b, a = signal.butter(2, 900 / (SR / 2))
    out = signal.lfilter(b, a, out, axis=1)
    return out * fade(n, attack, release)[None, :] * 0.05 / len(notes)


# ---- the cathedral ----
_IRS = {}


def impulse(rt60, seed=0):
    key = (rt60, seed)
    if key in _IRS:
        return _IRS[key]
    d = rt60 * 1.15
    n = int(d * SR)
    t = np.arange(n) / SR
    out = []
    for ch in range(2):
        def mask(tt, ff):
            # high frequencies die sooner, as in stone halls
            rt = rt60 * np.clip(1.0 - 0.55 * np.log2(np.maximum(ff, 500) / 500) / np.log2(32), 0.3, 1.0)
            return np.exp(-6.9 * tt / rt)
        x = shaped_noise(d, mask, seed * 11 + ch)[0]
        x *= fade(n, 0.012, 0.3)
        # a few early reflections
        rng = np.random.default_rng(seed * 5 + ch)
        for k in range(8):
            i = int(rng.uniform(0.008, 0.07) * SR)
            x[i] += rng.uniform(-.6, .6) * 3
        out.append(x / np.sqrt(np.sum(x ** 2)))
    _IRS[key] = np.stack(out)
    return _IRS[key]


def hall(x, rt60=3.2, wet=0.35, seed=0, predelay=0.025):
    ir = impulse(rt60, seed)
    pad_ = int(predelay * SR)
    y = np.zeros((2, x.shape[1] + ir.shape[1] + pad_))
    for ch in range(2):
        y[ch, pad_:pad_ + x.shape[1] + ir.shape[1] - 1] = signal.fftconvolve(x[ch], ir[ch]) * 0.9 + signal.fftconvolve(x[1 - ch], ir[ch]) * 0.25
    out = np.zeros_like(y)
    out[:, :x.shape[1]] = x * (1 - wet * 0.5)
    out += y * wet
    return out


def chord(names):
    return [hz(n) for n in names]


# ---- quiet foley for Bomi's opening ----
def knock(f0, d=0.35, seed=0, ring=1.0):
    """Something set down on wood: a few damped body modes and a soft contact click."""
    rng = np.random.default_rng(seed)
    n = int(d * SR)
    t = np.arange(n) / SR
    x = np.zeros(n)
    for ratio, a, dec in [(1.0, 1.0, .05), (2.31, .45, .03), (3.93, .2, .018), (5.4, .08, .012)]:
        x += a * np.exp(-t / (dec * ring)) * np.sin(2 * np.pi * f0 * ratio * t + rng.uniform(0, 6.3))
    s = rng.standard_normal(n) * np.exp(-t / 0.004)
    b, a = signal.butter(2, 2200 / (SR / 2))
    x += signal.lfilter(b, a, s) * 0.35
    return x * fade(n, 0.0008, 0.05) * 0.5


def thud(d=0.3, f0=72, seed=0, soft=1.0):
    """A soft weight landing: a low thump and a little muffled air."""
    rng = np.random.default_rng(seed)
    n = int(d * SR)
    t = np.arange(n) / SR
    x = np.sin(2 * np.pi * f0 * t * (1 + 0.25 * np.exp(-t / 0.02))) * np.exp(-t / (0.07 * soft))
    s = rng.standard_normal(n) * np.exp(-t / (0.035 * soft))
    b, a = signal.butter(2, 420 / (SR / 2))
    x += signal.lfilter(b, a, s) * 0.9
    return x * fade(n, 0.004 * soft, 0.06) * 0.5


def footfall(seed=0):
    """One footfall on polished stone: the heel, soft, then the toe's light tap."""
    rng = np.random.default_rng(seed)
    out = np.zeros(int(0.3 * SR))
    heel = thud(0.2, 95 + rng.uniform(-6, 6), seed=seed, soft=0.6)
    out[:len(heel)] += heel * 0.8
    n = int(0.05 * SR)
    t = np.arange(n) / SR
    tap = rng.standard_normal(n) * np.exp(-t / 0.006)
    b, a = signal.butter(2, [1200 / (SR / 2), 3600 / (SR / 2)], 'band')
    i = int(rng.uniform(0.05, 0.07) * SR)
    out[i:i + n] += signal.lfilter(b, a, tap) * 0.25
    return out


def breath(d=0.75, seed=0):
    """A soft breath out of relief ("휴"): air through the mouth's resonances, swelling and fading."""
    def mask(t, f):
        k = np.clip(t / d, 0, 1)
        env = np.where(k < 0.22, (k / 0.22) ** 1.5, np.exp(-(k - 0.22) / 0.28))
        voice = band(f, 520, 0.55) * 1.0 + band(f, 1250, 0.5) * 0.55 + band(f, 2600, 0.45) * 0.18
        return voice * env
    x = shaped_noise(d, mask, seed)
    b, a = signal.butter(2, 5000 / (SR / 2))
    return signal.lfilter(b, a, x, axis=1) * 0.6


def cushion(seed=0):
    """Sitting down on a cushion: a muffled soft thump, a breath of air from it."""
    out = canvas(0.6)
    place(out, thud(0.45, 60, seed=seed, soft=2.2), 0, 1.0)
    def mask(t, f):
        return band(f, 700, 0.8) * np.exp(-t / 0.12) * np.clip(t / 0.03, 0, 1)
    place(out, shaped_noise(0.5, mask, seed + 1) * 0.35, 0.01, 1.0)
    return out


def swish(d=0.16, seed=0):
    """A card turned over: a short, soft rush of air, its highs rolled off."""
    def mask(t, f):
        k = np.clip(t / d, 0, 1)
        return band(f, 1400 + 1600 * k, 0.8) * np.sin(np.pi * k) ** 1.5
    x = shaped_noise(d, mask, seed)
    b, a = signal.butter(2, 4500 / (SR / 2))
    return signal.lfilter(b, a, x, axis=1) * 0.25


def soften(x, corner=7000):
    """Rolls the top off, so nothing in the gacha hisses or glares."""
    b, a = signal.butter(2, corner / (SR / 2))
    return signal.lfilter(b, a, x, axis=1)


# ---- how loud each sound is (integrated loudness, LUFS; 2.40.0's ran about 8 LU louder) ----
LOUDNESS = {
    'hover': -31, 'click': -26, 'panel': -27, 'back': -28, 'denied': -28, 'star': -30, 'result': -24,
    'open': -24, 'pull': -23, 'one_start': -28, 'gather': -26, 'reveal_r': -22, 'reveal_sr': -21, 'reveal_ssr': -19.5,
    'ten_start': -29, 'rise': -25, 'light_r': -25, 'light_sr': -23, 'light_ssr': -21, 'whiteout': -26,
    'card': -25, 'card_sr': -23, 'card_ssr': -21, 'fanfare': -19,
    'place': -25, 'step': -29, 'foot': -31, 'sit': -28, 'phew': -31,
}
CEILING_DB = -8.0


def k_weighted(x):
    b1, a1 = [1.53512485958697, -2.69169618940638, 1.19839281085285], [1, -1.69065929318241, 0.73248077421585]
    b2, a2 = [1.0, -2.0, 1.0], [1, -1.99004745483398, 0.99007225036621]
    return signal.lfilter(b2, a2, signal.lfilter(b1, a1, x, axis=1), axis=1)


def loudness(x):
    """Integrated loudness in LUFS (ITU-R BS.1770: K-weighted, 400 ms blocks, gated)."""
    y = k_weighted(x)
    n, hop = int(0.4 * SR), int(0.1 * SR)
    if y.shape[1] < n:
        y = np.pad(y, ((0, 0), (0, n - y.shape[1])))
    z = np.array([np.mean(y[:, i:i + n] ** 2, axis=1).sum() for i in range(0, y.shape[1] - n + 1, hop)])
    l = -0.691 + 10 * np.log10(z + 1e-15)
    g = z[l > -70]
    if len(g) == 0:
        return -99.0
    rel = -0.691 + 10 * np.log10(g.mean()) - 10
    g = z[(l > -70) & (l > rel)]
    return -0.691 + 10 * np.log10(g.mean())


def finish(x, name, tail=0.3, length=None):
    """Low rumble and the highs' glare taken off, the reverb's inaudible end cut, faded out, then set to its loudness
    (LOUDNESS) with its peaks held under CEILING_DB."""
    b, a = signal.butter(2, 40 / (SR / 2), 'high')
    x = soften(signal.lfilter(b, a, x, axis=1))
    if length is not None:
        x = x[:, :int(length * SR)]
    loud = np.sqrt(np.convolve(np.mean(x ** 2, 0), np.ones(2400) / 2400, 'same'))
    alive = np.nonzero(loud > loud.max() * 10 ** (-48 / 20))[0]
    if len(alive):
        x = x[:, :min(x.shape[1], alive[-1] + int(0.05 * SR))]
    x = x * fade(x.shape[1], 0.002, min(tail, x.shape[1] / SR * .4))[None, :]
    x = x * 10 ** ((LOUDNESS[name] - loudness(x)) / 20)
    ceiling = 10 ** (CEILING_DB / 20)
    if np.abs(x).max() > ceiling:
        x = np.tanh(x / ceiling) * ceiling
    return x


def harp(names, at, gap, out, gain=0.6, bright=0.4, seed=0, d=2.0, spread=0.5):
    """A harp's notes {names}, one after another {gap} s apart from {at}, panned across."""
    for i, nm in enumerate(names):
        p = -spread + 2 * spread * i / max(1, len(names) - 1)
        place(out, pan(pluck(hz(nm), d, bright=bright, seed=seed + i), p), at + i * gap, gain)
    return out


# ---- the sounds (2.41.0: calm, clear and quiet) ----
def s_open():
    out = canvas(2.6)
    harp(['D4', 'F#4', 'A4', 'D5', 'E5'], 0.0, 0.07, out, 0.6, seed=1)
    place(out, pad(chord(['D3', 'A3', 'F#4']), 2.2, 0.5, 1.2, seed=2), 0.05, 0.8)
    place(out, bell(hz('A5'), 1.8, GLASS, decay=0.6, seed=3, strike=0.0), 0.36, 0.25)
    return finish(hall(out, 1.8, 0.25, 1), 'open', tail=0.6)


def s_hover():
    out = canvas(0.35)
    place(out, pan(bell(hz('A6'), 0.3, GLASS, decay=0.05, seed=4, strike=0.0), 0.1), 0, 1.0)
    return finish(hall(out, 0.8, 0.12, 2), 'hover', tail=0.12, length=0.35)


def s_click():
    out = canvas(0.8)
    place(out, bell(hz('D6'), 0.7, GLASS, decay=0.18, seed=5, strike=0.0), 0, 1.0)
    place(out, knock(820, 0.12, seed=6), 0, 0.25)
    return finish(hall(out, 1.0, 0.18, 3), 'click', tail=0.3, length=0.8)


def s_panel():
    out = canvas(1.0)
    harp(['A5', 'D6'], 0.0, 0.07, out, 0.55, seed=7, d=1.0, spread=0.3)
    place(out, swish(0.18, seed=8), 0, 0.5)
    return finish(hall(out, 1.2, 0.2, 4), 'panel', tail=0.35)


def s_back():
    out = canvas(0.9)
    harp(['D6', 'A5'], 0.0, 0.07, out, 0.55, seed=9, d=0.9, spread=0.3)
    place(out, swish(0.16, seed=10), 0, 0.4)
    return finish(hall(out, 1.1, 0.18, 5), 'back', tail=0.3)


def s_denied():
    out = canvas(0.6)
    place(out, knock(330, 0.25, seed=11, ring=1.4), 0, 0.9)
    place(out, knock(262, 0.25, seed=12, ring=1.4), 0.12, 0.9)
    return finish(hall(out, 0.8, 0.15, 6), 'denied', tail=0.2)


def s_pull():
    out = canvas(2.2)
    place(out, bell(hz('D6'), 1.8, GLASS, decay=0.55, seed=13, strike=0.0), 0.0, 0.7)
    harp(['A4', 'D5', 'F#5', 'A5'], 0.02, 0.05, out, 0.5, seed=14, d=1.6)
    place(out, choir(chord(['D4', 'A4', 'F#5']), 1.5, 'a', attack=0.35, release=0.9, seed=15, breath=0.03), 0.0, 0.6)
    return finish(hall(out, 1.8, 0.25, 7), 'pull', tail=0.6)


def lamp_click(seed=0):
    """A lamp switched on far above: a small, soft click."""
    out = canvas(0.4)
    place(out, knock(1500, 0.08, seed=seed, ring=0.6), 0, 0.5)
    place(out, knock(190, 0.2, seed=seed + 1, ring=0.8), 0.005, 0.5)
    return out


def s_one_start():
    out = canvas(3.6)
    place(out, lamp_click(16), 0.05, 0.8)
    place(out, pad(chord(['B2', 'F#3', 'D4']), 3.3, 1.2, 1.4, seed=17), 0.1, 1.0)
    place(out, choir(chord(['F#3', 'B3', 'D4']), 3.0, 'u', attack=1.4, release=1.2, seed=18, breath=0.04), 0.4, 0.55)
    return finish(hall(out, 2.2, 0.3, 8), 'one_start', tail=0.9)


def s_gather():
    out = canvas(2.4)
    place(out, choir(chord(['D4', 'A4', 'D5', 'F#5']), 2.3, 'a', attack=1.5, release=0.2, seed=19, breath=0.03,
                     swell=lambda t: np.clip(t / 2.2, 0, 1) ** 2), 0, 0.85)
    place(out, sparkles(2.2, 10, chord(['D6', 'F#6', 'A6']), seed=20, accel=1.5, decay=(.05, .18)), 0, 0.6)
    return finish(hall(out, 1.6, 0.22, 9), 'gather', tail=0.3, length=2.4)


def reveal(grade):
    if grade == 0:
        out = canvas(2.4)
        place(out, bell(hz('A5'), 2.2, GLASS, decay=0.7, seed=21, strike=0.0), 0, 0.9)
        place(out, choir(chord(['D4', 'A4', 'F#5']), 1.8, 'a', attack=0.25, release=1.0, seed=22, breath=0.02), 0, 0.5)
        return finish(hall(out, 1.9, 0.26, 10), 'reveal_r', tail=0.7)
    if grade == 1:
        out = canvas(2.8)
        place(out, bell(hz('D6'), 2.4, GLASS, decay=0.75, seed=23, strike=0.0), 0, 0.75)
        place(out, bell(hz('A6'), 2.2, GLASS, decay=0.65, seed=24, strike=0.0), 0.06, 0.4)
        place(out, choir(chord(['D4', 'F#4', 'A4', 'D5']), 2.2, 'a', attack=0.2, release=1.2, seed=25, bright=1.1, breath=0.02), 0, 0.6)
        place(out, sparkles(1.8, 8, chord(['D6', 'F#6', 'A6']), seed=26, decay=(.06, .2)), 0.04, 0.5)
        return finish(hall(out, 2.1, 0.28, 11), 'reveal_sr', tail=0.8)
    out = canvas(3.8)
    place(out, bell(hz('D3'), 3.4, CHURCH, decay=0.7, bright=0.6, seed=27, strike=0.0), 0, 0.35)
    for i, nm in enumerate(['D6', 'F#6', 'A6']):
        place(out, bell(hz(nm), 2.8, GLASS, decay=0.8, seed=28 + i, strike=0.0), 0.04 + i * 0.12, 0.5)
    place(out, choir(chord(['D3', 'A3', 'D4', 'F#4', 'A4', 'E5']), 3.2, 'a', voices=6, attack=0.3, release=1.5, seed=31, bright=1.2, breath=0.03), 0, 0.85)
    place(out, pad(chord(['D3', 'A3', 'F#4']), 3.2, 0.3, 1.5, seed=32), 0, 0.8)
    place(out, sparkles(3.0, 14, chord(['D6', 'F#6', 'A6', 'E6']), seed=33, decay=(.08, .3)), 0.1, 0.5)
    return finish(hall(out, 2.4, 0.3, 12), 'reveal_ssr', tail=1.1)


def s_ten_start():
    out = canvas(3.2)
    place(out, lamp_click(34), 0.05, 0.8)
    place(out, pad(chord(['D3', 'A3', 'E4']), 3.0, 1.0, 1.4, seed=35), 0.1, 0.9)
    return finish(hall(out, 2.0, 0.28, 13), 'ten_start', tail=0.9)


def s_rise():
    out = canvas(3.0)
    harp(['D4', 'F#4', 'A4', 'D5', 'E5', 'F#5', 'A5', 'D6'], 0.1, 0.09, out, 0.5, seed=36, d=2.2, spread=0.6)
    place(out, choir(chord(['D3', 'A3', 'D4', 'F#4']), 2.6, 'a', attack=1.0, release=1.2, seed=44, breath=0.03,
                     swell=lambda t: 0.4 + 0.6 * np.clip(t / 1.4, 0, 1)), 0.1, 0.7)
    return finish(hall(out, 2.0, 0.28, 14), 'rise', tail=0.9)


def light(grade):
    if grade == 0:
        out = canvas(1.8)
        place(out, bell(hz('A5'), 1.6, GLASS, decay=0.55, seed=45, strike=0.0), 0, 1.0)
        return finish(hall(out, 1.6, 0.25, 15), 'light_r', tail=0.6)
    if grade == 1:
        out = canvas(2.4)
        place(out, bell(hz('A5'), 2.1, GLASS, decay=0.7, seed=46, strike=0.0), 0, 0.8)
        place(out, bell(hz('E6'), 1.9, GLASS, decay=0.6, seed=47, strike=0.0), 0.05, 0.35)
        place(out, choir(chord(['A3', 'E4', 'A4', 'C#5']), 1.8, 'a', attack=0.15, release=1.1, seed=48, breath=0.02), 0, 0.45)
        return finish(hall(out, 1.9, 0.27, 16), 'light_sr', tail=0.8)
    out = canvas(3.4)
    place(out, bell(hz('D3'), 3.0, CHURCH, decay=0.6, bright=0.6, seed=49, strike=0.0), 0, 0.3)
    for i, nm in enumerate(['A5', 'D6', 'F#6', 'A6']):
        place(out, bell(hz(nm), 2.6, GLASS, decay=0.75, seed=50 + i, strike=0.0), 0.03 + i * 0.1, 0.45)
    place(out, choir(chord(['D3', 'A3', 'D4', 'F#4', 'A4']), 2.8, 'a', attack=0.25, release=1.4, seed=54, bright=1.2, breath=0.03), 0, 0.85)
    place(out, sparkles(2.6, 12, chord(['D6', 'F#6', 'A6']), seed=55, decay=(.08, .3)), 0.08, 0.45)
    return finish(hall(out, 2.2, 0.3, 17), 'light_ssr', tail=1.0)


def s_whiteout():
    out = canvas(1.8)
    harp(['A5', 'D6', 'F#6', 'A6'], 0.0, 0.08, out, 0.4, seed=56, d=1.4, spread=0.5)
    place(out, choir(chord(['A4', 'D5', 'F#5']), 1.6, 'a', attack=0.8, release=0.6, seed=60, breath=0.02,
                     swell=lambda t: np.clip(t / 1.0, 0, 1)), 0, 0.6)
    return finish(hall(out, 1.6, 0.25, 18), 'whiteout', tail=0.5)


def card(grade):
    if grade == 0:
        out = canvas(1.2)
        place(out, swish(0.15, seed=61), 0, 0.8)
        place(out, bell(hz('E6'), 1.0, GLASS, decay=0.3, seed=62, strike=0.0), 0.07, 0.7)
        return finish(hall(out, 1.3, 0.22, 19), 'card', tail=0.4)
    if grade == 1:
        out = canvas(1.8)
        place(out, swish(0.15, seed=63), 0, 0.8)
        place(out, bell(hz('A5'), 1.5, GLASS, decay=0.45, seed=64, strike=0.0), 0.07, 0.6)
        place(out, bell(hz('E6'), 1.4, GLASS, decay=0.4, seed=65, strike=0.0), 0.12, 0.45)
        place(out, choir(chord(['A4', 'C#5', 'E5']), 1.2, 'a', attack=0.1, release=0.8, seed=66, breath=0.02), 0.07, 0.4)
        return finish(hall(out, 1.6, 0.25, 20), 'card_sr', tail=0.6)
    out = canvas(2.6)
    place(out, swish(0.15, seed=67), 0, 0.8)
    for i, nm in enumerate(['D6', 'F#6', 'A6']):
        place(out, bell(hz(nm), 2.2, GLASS, decay=0.6, seed=68 + i, strike=0.0), 0.08 + i * 0.08, 0.5)
    place(out, choir(chord(['D4', 'F#4', 'A4', 'D5']), 2.0, 'a', attack=0.12, release=1.2, seed=71, breath=0.02), 0.08, 0.7)
    return finish(hall(out, 2.0, 0.28, 21), 'card_ssr', tail=0.8)


def s_fanfare():
    """The SSR splash: a harp sweeping up, "SSR" (0.35 s) and the title (0.7 s) on clear bells, the held chord (1.35 s)
    sung and ringing out; calm and bright, no drums or brass."""
    out = canvas(5.2)
    harp(['D4', 'F#4', 'A4', 'D5', 'F#5', 'A5', 'D6'], 0.0, 0.045, out, 0.45, seed=72, d=2.4, spread=0.7)
    place(out, bell(hz('D3'), 4.0, CHURCH, decay=0.8, bright=0.5, seed=79, strike=0.0), 0, 0.3)
    for at, nm in [(0.35, 'A5'), (0.7, 'D6'), (1.35, 'F#6'), (1.38, 'A6')]:
        place(out, bell(hz(nm), 2.8, GLASS, decay=0.8, seed=80 + int(at * 10), strike=0.0), at, 0.45)
    place(out, choir(chord(['D3', 'A3', 'D4', 'F#4', 'A4', 'D5', 'E5']), 4.2, 'a', voices=6, attack=0.5, release=2.0, seed=90, bright=1.2, breath=0.03,
                     swell=lambda t: 0.55 + 0.45 * np.clip((t - 1.0) / 0.6, 0, 1)), 0.0, 0.9)
    place(out, pad(chord(['D3', 'A3', 'F#4', 'E5']), 4.4, 0.6, 2.0, seed=91), 0.0, 0.9)
    place(out, sparkles(4.0, 20, chord(['D6', 'E6', 'F#6', 'A6']), seed=92, decay=(.08, .35), start=1.2), 0.1, 0.45)
    return finish(hall(out, 2.6, 0.3, 22), 'fanfare', tail=1.4)


def s_star():
    out = canvas(0.6)
    place(out, bell(hz('A6'), 0.5, GLASS, decay=0.12, seed=93, strike=0.0), 0, 1.0)
    return finish(hall(out, 0.9, 0.18, 23), 'star', tail=0.25)


def s_result():
    out = canvas(2.2)
    harp(['D5', 'F#5', 'A5', 'D6'], 0.0, 0.06, out, 0.5, seed=94, d=1.8, spread=0.4)
    place(out, choir(chord(['D4', 'F#4', 'A4']), 1.6, 'a', attack=0.15, release=1.0, seed=98, breath=0.02), 0.05, 0.5)
    return finish(hall(out, 1.8, 0.26, 24), 'result', tail=0.7)


# Bomi's opening of the ten-pull (2.41.0)
def s_place():
    """The last hourglass set down on the shelf: a soft knock of its foot on the wood and a faint ring of its glass."""
    out = canvas(1.0)
    place(out, knock(240, 0.3, seed=99), 0, 1.0)
    place(out, knock(520, 0.2, seed=100, ring=0.8), 0.03, 0.4)
    place(out, bell(hz('E6'), 0.8, GLASS, decay=0.22, seed=101, strike=0.0), 0.0, 0.12)
    return finish(hall(out, 1.0, 0.18, 25), 'place', tail=0.3)


def s_step():
    """A step down: a soft landing."""
    out = canvas(0.5)
    place(out, thud(0.35, 80, seed=102), 0, 1.0)
    place(out, knock(180, 0.15, seed=103, ring=0.6), 0, 0.25)
    return finish(hall(out, 0.8, 0.15, 26), 'step', tail=0.15)


def s_foot():
    out = canvas(0.4)
    place(out, footfall(104), 0, 1.0)
    return finish(hall(out, 0.8, 0.15, 27), 'foot', tail=0.12)


def s_sit():
    out = canvas(0.7)
    place(out, cushion(105), 0, 1.0)
    return finish(hall(out, 0.8, 0.12, 28), 'sit', tail=0.2)


def s_phew():
    out = canvas(0.9)
    place(out, breath(0.8, seed=106), 0, 1.0)
    return finish(hall(out, 0.8, 0.12, 29), 'phew', tail=0.25)


SOUNDS = {
    'open': s_open, 'hover': s_hover, 'click': s_click, 'panel': s_panel, 'back': s_back, 'denied': s_denied,
    'pull': s_pull, 'one_start': s_one_start, 'gather': s_gather,
    'reveal_r': lambda: reveal(0), 'reveal_sr': lambda: reveal(1), 'reveal_ssr': lambda: reveal(2),
    'ten_start': s_ten_start, 'rise': s_rise,
    'light_r': lambda: light(0), 'light_sr': lambda: light(1), 'light_ssr': lambda: light(2),
    'whiteout': s_whiteout, 'card': lambda: card(0), 'card_sr': lambda: card(1), 'card_ssr': lambda: card(2),
    'fanfare': s_fanfare, 'star': s_star, 'result': s_result,
    'place': s_place, 'step': s_step, 'foot': s_foot, 'sit': s_sit, 'phew': s_phew,
}


def main():
    out = sys.argv[1]
    only = set(sys.argv[2].split(',')) if len(sys.argv) > 2 else None
    os.makedirs(out, exist_ok=True)
    for name, make in SOUNDS.items():
        if only and name not in only:
            continue
        x = make()
        pcm = (np.clip(x.T, -1, 1) * 32767).astype(np.int16)
        with tempfile.TemporaryDirectory() as tmp:
            wav = os.path.join(tmp, name + '.wav')
            wavfile.write(wav, SR, pcm)
            subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', wav, '-c:a', 'libvorbis', '-q:a', '5', os.path.join(out, name + '.ogg')], check=True)
        print(f'{name:12s} {x.shape[1] / SR:5.2f} s  {loudness(x):6.1f} LUFS  peak {20 * np.log10(np.abs(x).max()):6.1f} dB')


if __name__ == '__main__':
    main()
