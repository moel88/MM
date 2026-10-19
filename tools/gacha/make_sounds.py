"""Synthesises the gacha's sounds (user request, 2.40.0: holy and luxurious, every one of them).

usage: python3 -I tools/gacha/make_sounds.py OUT_DIR

Everything is made here from first principles, so there are no samples to license: a choir singing "ah" (many detuned
voices with vibrato through vowel formants), glass and church bells (their inharmonic partials), a harp (plucked
strings, Karplus-Strong), sparkles, soft booms, whooshes shaped in time and frequency, and a cathedral's reverb. The
music sits in D major. Each sound is written as OGG Vorbis (48 kHz stereo) by ffmpeg, which must be on the PATH.
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


def whoosh(d, f0, f1, width=0.9, peak=0.5, seed=0, curve=1.0):
    """Air rushing past: a band of noise sliding from f0 to f1 Hz, swelling to its loudest at {peak} of the way."""
    def mask(t, f):
        k = np.clip(t / d, 0, 1) ** curve
        c = f0 * (f1 / f0) ** k
        env = np.where(k < peak, (k / peak) ** 2, ((1 - k) / (1 - peak)) ** 1.5)
        return band(f, c, width) * env
    return shaped_noise(d, mask, seed) * 0.35


def riser(d, seed=0):
    """A cymbal swelling up (a reversed crash): bright noise growing until the end."""
    def mask(t, f):
        k = np.clip(t / d, 0, 1)
        hp = 1 / (1 + (2500 / np.maximum(f, 1)) ** 2)
        tilt = (np.maximum(f, 1) / 6000) ** (-0.3)
        return hp * tilt * k ** 3
    x = shaped_noise(d, mask, seed) * 0.25
    return x * fade(x.shape[1], 0.01, 0.015)


def crash(d, seed=0):
    """A soft cymbal wash: bright noise fading over {d} seconds, the highs first."""
    def mask(t, f):
        hp = 1 / (1 + (1800 / np.maximum(f, 1)) ** 2)
        decay = np.exp(-t / (0.35 + 1.4 * (3000 / np.maximum(f, 3000))))
        return hp * decay * (np.maximum(f, 1) / 6000) ** (-0.25)
    x = shaped_noise(d, mask, seed) * 0.3
    return x * fade(x.shape[1], 0.002, 0.2)


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


def boom(d, f0=58, f1=30, decay=1.1, thump=0.4, seed=0):
    """A deep soft impact: a sine falling in pitch, a felt-mallet thump at its start."""
    n = int(d * SR)
    t = np.arange(n) / SR
    f = f1 + (f0 - f1) * np.exp(-t / 0.18)
    x = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / decay)
    x = np.tanh(x * 1.6) / np.tanh(1.6)
    rng = np.random.default_rng(seed)
    s = rng.standard_normal(n) * np.exp(-t / 0.03)
    b, a = signal.butter(2, 280 / (SR / 2))
    x += signal.lfilter(b, a, s) * thump
    x *= fade(n, 0.003, 0.1)
    return x * 0.5


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


def brass(notes, d, attack=0.06, release=0.8, seed=0, open_=1.0):
    """A warm brass chord: saws whose filter opens with the attack, a slow vibrato."""
    rng = np.random.default_rng(seed)
    n = int(d * SR)
    t = np.arange(n) / SR
    out = np.zeros((2, n))
    for f0 in notes:
        for k in range(3):
            det = 2 ** (rng.normal(0, 5) / 1200)
            vib = 1 + 0.003 * np.sin(2 * np.pi * 5.2 * t + rng.uniform(0, 6)) * np.clip((t - .3) / .5, 0, 1)
            ph = np.cumsum(f0 * det * vib) / SR
            saw = 2 * (ph % 1.0) - 1
            out += pan(saw, rng.uniform(-.5, .5))
    # the brightness: a low-pass whose corner follows the attack's swell
    env = 1 - np.exp(-t / attack)
    corner = 400 + 3200 * open_ * env * np.exp(-t / 2.5)
    y = np.zeros_like(out)
    blocks = 64
    edges = np.linspace(0, n, blocks + 1).astype(int)
    zi = None
    for i in range(blocks):
        c = float(np.clip(corner[edges[i]:edges[i + 1]].mean(), 200, 18000))
        b, a = signal.butter(2, c / (SR / 2))
        if zi is None:
            zi = np.zeros((2, max(len(a), len(b)) - 1))
        y[:, edges[i]:edges[i + 1]], zi = signal.lfilter(b, a, out[:, edges[i]:edges[i + 1]], axis=1, zi=zi)
    return y * fade(n, attack, release)[None, :] * 0.09 / np.sqrt(len(notes))


def timpani_roll(d, f0=73.4, seed=0):
    """A timpani roll swelling: many soft mallet strokes on a low drum."""
    rng = np.random.default_rng(seed)
    out = np.zeros(int(d * SR))
    at = 0.0
    while at < d - 0.02:
        n = int(min(0.6, d - at) * SR)
        t = np.arange(n) / SR
        g = (at / d) ** 1.5 * rng.uniform(.6, 1)
        x = (np.sin(2 * np.pi * f0 * t) + .5 * np.sin(2 * np.pi * f0 * 1.5 * t) + .3 * np.sin(2 * np.pi * f0 * 1.99 * t)) * np.exp(-t / 0.35)
        s = rng.standard_normal(n) * np.exp(-t / 0.01) * 0.3
        out[int(at * SR):int(at * SR) + n] += (x + s) * g * fade(n, 0.002, 0.02)
        at += rng.uniform(0.035, 0.05)
    b, a = signal.butter(2, 900 / (SR / 2))
    return signal.lfilter(b, a, out) * 0.18


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


def master(x, peak_db=-1.0, length=None, tail=0.25):
    """Low rumble taken away, the reverb's inaudible end cut off, a gentle limiter, the peak set, the tail faded so it
    never clicks."""
    b, a = signal.butter(2, 28 / (SR / 2), 'high')
    x = signal.lfilter(b, a, x, axis=1)
    if length is not None:
        x = x[:, :int(length * SR)]
    loud = np.sqrt(np.convolve(np.mean(x ** 2, 0), np.ones(2400) / 2400, 'same'))
    alive = np.nonzero(loud > loud.max() * 10 ** (-52 / 20))[0]
    if len(alive):
        x = x[:, :min(x.shape[1], alive[-1] + int(0.05 * SR))]
    x = x * fade(x.shape[1], 0.002, min(tail, x.shape[1] / SR * .4))[None, :]
    peak = np.abs(x).max() + 1e-12
    target = 10 ** (peak_db / 20)
    x = x / peak * target * 1.25
    x = np.tanh(x / target) * target  # soft knee on the loudest peaks
    x = x / (np.abs(x).max() + 1e-12) * target
    return x


def chord(names):
    return [hz(n) for n in names]


# ---- the sounds ----
def s_open():
    out = canvas(3.2)
    gliss = ['D4', 'E4', 'F#4', 'A4', 'B4', 'D5', 'E5', 'F#5', 'A5', 'B5', 'D6', 'E6']
    for i, nm in enumerate(gliss):
        place(out, pan(pluck(hz(nm), 2.4, bright=.55, seed=i), -0.6 + 1.2 * i / len(gliss)), 0.02 + i * 0.045, 0.7)
    place(out, choir(chord(['A3', 'D4', 'F#4', 'A4', 'E5']), 2.8, 'a', attack=0.45, release=1.4, seed=1), 0.15, 0.9)
    place(out, sparkles(2.4, 26, chord(['D6', 'F#6', 'A6', 'E6']), seed=2, start=0.3), 0.1, 0.9)
    place(out, bell(hz('D6'), 2.5, seed=3), 0.55, 0.45)
    return master(hall(out, 3.0, 0.38, 1), -5.5, tail=0.6)


def s_hover():
    out = canvas(0.5)
    place(out, pan(bell(hz('A6'), 0.4, GLASS, decay=0.08, seed=4, strike=0.01), 0.1), 0, 1.0)
    return master(hall(out, 1.2, 0.2, 2), -19, length=0.5, tail=0.15)


def s_click():
    out = canvas(1.4)
    place(out, bell(hz('D6'), 1.2, GLASS, decay=0.45, seed=5), 0, 1.0)
    place(out, bell(hz('A6'), 0.9, GLASS, decay=0.3, seed=6), 0.012, 0.35)
    place(out, whoosh(0.18, 1500, 5000, 0.8, 0.3, seed=7), 0, 0.25)
    return master(hall(out, 1.8, 0.3, 3), -9, length=1.4, tail=0.4)


def s_panel():
    out = canvas(1.3)
    place(out, whoosh(0.35, 500, 3200, 0.9, 0.6, seed=8), 0, 0.9)
    place(out, bell(hz('F#6'), 1.0, GLASS, decay=0.35, seed=9), 0.18, 0.45)
    place(out, bell(hz('A5'), 1.0, GLASS, decay=0.35, seed=10), 0.2, 0.3)
    return master(hall(out, 1.8, 0.3, 4), -11, length=1.3, tail=0.4)


def s_back():
    out = canvas(1.0)
    place(out, whoosh(0.3, 3000, 600, 0.9, 0.35, seed=11), 0, 0.9)
    place(out, bell(hz('A5'), 0.8, GLASS, decay=0.25, seed=12), 0.05, 0.35)
    return master(hall(out, 1.4, 0.25, 5), -13, length=1.0, tail=0.35)


def s_denied():
    out = canvas(0.8)
    place(out, bell(hz('E5'), 0.5, GLASS, decay=0.12, bright=.4, seed=13), 0, 0.8)
    place(out, bell(hz('C5'), 0.5, GLASS, decay=0.14, bright=.4, seed=14), 0.11, 0.8)
    return master(hall(out, 1.0, 0.18, 6), -14, length=0.8, tail=0.3)


def s_pull():
    out = canvas(2.6)
    place(out, riser(0.7, seed=15), 0, 0.7)
    place(out, bell(hz('D6'), 2.0, GLASS, decay=0.8, seed=16), 0.0, 0.9)
    place(out, bell(hz('A6'), 1.6, GLASS, decay=0.6, seed=17), 0.04, 0.4)
    place(out, choir(chord(['D4', 'A4', 'D5', 'F#5']), 1.6, 'a', attack=0.25, release=0.9, seed=18, swell=lambda t: np.clip(t / .5, 0, 1) ** 1.5), 0.0, 1.0)
    place(out, sparkles(1.8, 22, chord(['D6', 'F#6', 'A6']), seed=19, accel=1.0), 0.05, 1.0)
    place(out, whoosh(0.6, 400, 6000, 1.0, 0.8, seed=20, curve=1.6), 0, 0.5)
    return master(hall(out, 2.6, 0.38, 7), -6, tail=0.6)


def s_one_start():
    out = canvas(5.2)
    place(out, boom(2.5, 52, 30, 1.2, seed=21), 0.05, 0.7)
    place(out, spotlight(), 0.1, 1.0)
    place(out, choir(chord(['B2', 'F#3', 'B3', 'D4', 'C#5']), 4.6, 'o', attack=1.6, release=1.6, seed=22, breath=0.12), 0.3, 0.75)
    place(out, pad(chord(['B2', 'F#3', 'D4']), 4.8, 1.2, 1.8, seed=23), 0.1, 1.0)
    place(out, sparkles(4.5, 18, chord(['B5', 'D6', 'F#6', 'C#6']), seed=24, start=0.8, decay=(.1, .5)), 0.2, 0.6)
    return master(hall(out, 3.6, 0.42, 8), -7, tail=1.0)


def spotlight():
    """A great lamp switched on far above: a deep clunk and the hum of the light coming up."""
    out = canvas(2.0)
    n = int(0.25 * SR)
    t = np.arange(n) / SR
    clunk = np.sin(2 * np.pi * 78 * t) * np.exp(-t / 0.05) + 0.6 * np.sin(2 * np.pi * 156 * t) * np.exp(-t / 0.03)
    rng = np.random.default_rng(25)
    s = rng.standard_normal(n) * np.exp(-t / 0.012)
    b, a = signal.butter(2, [300 / (SR / 2), 2500 / (SR / 2)], 'band')
    clunk += signal.lfilter(b, a, s) * 0.5
    place(out, clunk * 0.5, 0)
    hum_t = secs(1.9)
    hum = (np.sin(2 * np.pi * 110 * hum_t) * .4 + np.sin(2 * np.pi * 220 * hum_t) * .2 + np.sin(2 * np.pi * 330 * hum_t) * .08)
    hum *= fade(len(hum_t), 0.4, 1.0) * 0.08
    place(out, hum, 0.05)
    place(out, whoosh(1.2, 2500, 9000, 1.2, 0.25, seed=26), 0.02, 0.35)
    return out


def s_gather():
    out = canvas(2.6)
    place(out, riser(2.2, seed=27), 0, 0.9)
    place(out, choir(chord(['D4', 'A4', 'D5', 'E5', 'F#5']), 2.3, 'a', attack=1.6, release=0.15, seed=28,
                     swell=lambda t: np.clip(t / 2.2, 0, 1) ** 2), 0, 0.9)
    place(out, sparkles(2.2, 40, chord(['D6', 'E6', 'F#6', 'A6']), seed=29, accel=2.0, decay=(.04, .2)), 0, 1.0)
    return master(hall(out, 2.2, 0.3, 9), -8, length=2.5, tail=0.3)


def reveal(grade):
    if grade == 0:
        out = canvas(3.2)
        place(out, bell(hz('D6'), 3.0, GLASS, decay=0.9, seed=30), 0, 0.9)
        place(out, bell(hz('A6'), 2.6, GLASS, decay=0.7, seed=31), 0.03, 0.45)
        place(out, choir(chord(['D4', 'A4', 'F#5']), 2.2, 'a', attack=0.2, release=1.3, seed=32), 0, 0.55)
        place(out, sparkles(2.6, 16, chord(['D6', 'F#6', 'A6']), seed=33), 0.02, 0.7)
        return master(hall(out, 2.8, 0.38, 10), -7, tail=0.8)
    if grade == 1:
        out = canvas(3.8)
        place(out, boom(1.6, 70, 40, 0.6, seed=34), 0, 0.45)
        for i, nm in enumerate(['D6', 'F#6', 'A6', 'D7']):
            place(out, bell(hz(nm), 3.0, GLASS, decay=0.9, seed=35 + i), i * 0.035, 0.6)
        place(out, choir(chord(['D4', 'F#4', 'A4', 'D5', 'A5']), 3.0, 'a', attack=0.12, release=1.6, seed=40, bright=1.4), 0, 0.9)
        place(out, sparkles(3.2, 34, chord(['D6', 'F#6', 'A6', 'C#7']), seed=41), 0.02, 0.9)
        place(out, crash(2.5, seed=42), 0, 0.3)
        return master(hall(out, 3.2, 0.4, 11), -5, tail=1.0)
    out = canvas(6.0)
    place(out, boom(3.0, 56, 28, 1.6, thump=0.6, seed=43), 0, 0.9)
    place(out, bell(hz('D3'), 5.5, CHURCH, decay=1.0, seed=44, strike=0.05), 0, 0.75)
    for i, nm in enumerate(['A5', 'D6', 'F#6', 'A6', 'D7', 'F#7']):
        place(out, bell(hz(nm), 4.0, GLASS, decay=1.0, seed=45 + i), 0.05 + i * 0.06, 0.45)
    place(out, choir(chord(['D3', 'A3', 'D4', 'F#4', 'A4', 'D5', 'E5', 'A5']), 5.2, 'a', voices=7, attack=0.1, release=2.2, seed=52, bright=1.6), 0, 1.15)
    place(out, brass(chord(['D3', 'A3', 'D4', 'F#4']), 3.5, attack=0.08, release=1.5, seed=53), 0, 0.8)
    place(out, sparkles(5.5, 70, chord(['D6', 'E6', 'F#6', 'A6', 'B6']), seed=54, decay=(.08, .5)), 0.05, 1.0)
    place(out, crash(4.5, seed=55), 0, 0.5)
    return master(hall(out, 4.2, 0.42, 12), -3, tail=1.6)


def s_ten_start():
    out = canvas(4.8)
    place(out, boom(2.5, 50, 28, 1.3, seed=56), 0.02, 0.7)
    place(out, spotlight(), 0.08, 1.0)
    place(out, choir(chord(['B2', 'F#3', 'B3', 'D4', 'C#5']), 4.4, 'u', attack=1.4, release=1.4, seed=57, breath=0.12), 0.4, 0.8)
    place(out, pad(chord(['B2', 'F#3', 'D4']), 4.6, 1.0, 1.8, seed=58), 0.1, 1.0)
    return master(hall(out, 3.8, 0.45, 13), -8, tail=1.0)


def s_rise():
    out = canvas(4.4)
    gliss = ['D3', 'A3', 'D4', 'E4', 'F#4', 'A4', 'B4', 'D5', 'E5', 'F#5', 'A5', 'B5', 'D6', 'E6', 'F#6', 'A6']
    for i, nm in enumerate(gliss):
        place(out, pan(pluck(hz(nm), 3.0, bright=.5, seed=60 + i), -0.7 + 1.4 * i / len(gliss)), 0.15 + i * 0.07, 0.65)
    place(out, choir(chord(['D3', 'A3', 'D4', 'F#4', 'A4', 'E5']), 4.0, 'a', attack=1.3, release=1.6, seed=80, bright=1.2,
                     swell=lambda t: 0.5 + 0.5 * np.clip(t / 1.6, 0, 1)), 0.1, 1.0)
    place(out, sparkles(3.8, 40, chord(['D6', 'E6', 'F#6', 'A6']), seed=81, start=0.5), 0.1, 0.8)
    place(out, riser(1.4, seed=82), 0.1, 0.4)
    return master(hall(out, 3.6, 0.42, 14), -6, tail=1.0)


def light(grade):
    if grade == 0:
        out = canvas(2.6)
        place(out, bell(hz('A5'), 2.4, GLASS, decay=0.75, seed=90), 0, 0.9)
        place(out, bell(hz('E6'), 2.0, GLASS, decay=0.55, seed=91), 0.02, 0.3)
        place(out, sparkles(1.6, 9, chord(['A6', 'E6', 'C#7']), seed=92, decay=(.05, .25)), 0.02, 0.6)
        place(out, whoosh(0.5, 2000, 6000, 1.0, 0.2, seed=93), 0, 0.2)
        return master(hall(out, 2.6, 0.38, 15), -10, tail=0.8)
    if grade == 1:
        out = canvas(3.2)
        place(out, bell(hz('A5'), 2.8, GLASS, decay=0.85, seed=94), 0, 0.85)
        place(out, bell(hz('C#6'), 2.6, GLASS, decay=0.8, seed=95), 0.03, 0.5)
        place(out, bell(hz('E6'), 2.4, GLASS, decay=0.7, seed=96), 0.06, 0.45)
        place(out, choir(chord(['A3', 'E4', 'A4', 'C#5', 'E5']), 2.4, 'a', attack=0.1, release=1.5, seed=97, bright=1.3), 0, 0.75)
        place(out, sparkles(2.6, 26, chord(['A6', 'C#7', 'E6']), seed=98), 0.02, 0.9)
        place(out, boom(1.2, 80, 45, 0.45, seed=99), 0, 0.3)
        return master(hall(out, 3.0, 0.4, 16), -6.5, tail=1.0)
    return reveal(2)


def s_whiteout():
    out = canvas(2.0)
    place(out, riser(1.2, seed=100), 0, 0.9)
    place(out, sparkles(1.3, 40, chord(['D6', 'F#6', 'A6', 'D7']), seed=101, accel=1.5, decay=(.04, .18)), 0, 0.9)
    place(out, whoosh(1.4, 800, 12000, 1.1, 0.85, seed=102, curve=1.4), 0, 0.6)
    return master(hall(out, 2.0, 0.35, 17), -9, tail=0.5)


def card(grade):
    if grade == 0:
        out = canvas(1.6)
        place(out, whoosh(0.22, 1200, 4500, 0.8, 0.35, seed=110), 0, 0.8)
        place(out, bell(hz('E6'), 1.4, GLASS, decay=0.4, seed=111), 0.08, 0.6)
        return master(hall(out, 1.8, 0.3, 18), -11, tail=0.5)
    if grade == 1:
        out = canvas(2.4)
        place(out, whoosh(0.22, 1200, 4500, 0.8, 0.35, seed=112), 0, 0.8)
        for i, nm in enumerate(['A5', 'C#6', 'E6', 'A6']):
            place(out, bell(hz(nm), 2.0, GLASS, decay=0.6, seed=113 + i), 0.08 + i * 0.03, 0.5)
        place(out, choir(chord(['A4', 'C#5', 'E5']), 1.6, 'a', attack=0.08, release=1.0, seed=118, bright=1.3), 0.08, 0.6)
        place(out, sparkles(1.8, 20, chord(['A6', 'C#7', 'E7']), seed=119), 0.08, 0.8)
        return master(hall(out, 2.4, 0.36, 19), -7.5, tail=0.8)
    out = canvas(3.6)
    place(out, whoosh(0.22, 1200, 4500, 0.8, 0.35, seed=120), 0, 0.8)
    place(out, boom(2.0, 60, 32, 0.9, seed=121), 0.08, 0.7)
    place(out, bell(hz('D4'), 3.4, CHURCH, decay=0.7, seed=122), 0.08, 0.5)
    for i, nm in enumerate(['D6', 'F#6', 'A6', 'D7']):
        place(out, bell(hz(nm), 3.0, GLASS, decay=0.9, seed=123 + i), 0.1 + i * 0.04, 0.5)
    place(out, choir(chord(['D4', 'F#4', 'A4', 'D5', 'A5']), 2.8, 'a', attack=0.08, release=1.6, seed=128, bright=1.5), 0.08, 0.9)
    place(out, sparkles(3.2, 44, chord(['D6', 'F#6', 'A6', 'E6']), seed=129), 0.1, 0.9)
    return master(hall(out, 3.2, 0.4, 20), -5, tail=1.0)


def s_fanfare():
    """The SSR splash: the strike, "SSR", the title cut in, the ribbon; then the held chord ringing out."""
    out = canvas(7.5)
    place(out, boom(3.0, 55, 27, 1.5, thump=0.7, seed=130), 0, 0.9)
    place(out, crash(5.5, seed=131), 0, 0.55)
    place(out, bell(hz('D3'), 6.5, CHURCH, decay=1.2, seed=132, strike=0.05), 0, 0.7)
    place(out, brass(chord(['D3', 'A3', 'D4', 'F#4', 'A4']), 1.0, attack=0.04, release=0.4, seed=133), 0, 0.9)
    # "SSR" strikes (0.35 s), the title is cut (0.7 s): a rising run of brass and bells to the held chord (1.35 s)
    place(out, brass(chord(['G3', 'B3', 'D4', 'G4']), 0.5, attack=0.03, release=0.25, seed=134), 0.34, 0.85)
    place(out, boom(1.2, 75, 40, 0.5, seed=135), 0.34, 0.5)
    run = ['A4', 'D5', 'F#5', 'A5', 'D6', 'F#6']
    for i, nm in enumerate(run):
        place(out, bell(hz(nm), 2.5, GLASS, decay=0.8, seed=136 + i), 0.72 + i * 0.1, 0.55)
    place(out, brass(chord(['A3', 'C#4', 'E4', 'A4']), 0.62, attack=0.05, release=0.3, seed=142), 0.72, 0.8)
    place(out, brass(chord(['D3', 'A3', 'D4', 'F#4', 'A4', 'D5']), 4.4, attack=0.09, release=2.2, seed=143, open_=1.2), 1.35, 1.0)
    place(out, choir(chord(['D3', 'A3', 'D4', 'F#4', 'A4', 'D5', 'E5', 'A5']), 5.6, 'a', voices=7, attack=0.3, release=2.6, seed=144, bright=1.6), 0.0, 1.0)
    place(out, sparkles(6.5, 90, chord(['D6', 'E6', 'F#6', 'A6', 'B6']), seed=146, decay=(.08, .55)), 0.1, 1.0)
    place(out, bell(hz('D5'), 4.0, CHURCH, decay=0.8, seed=147), 1.35, 0.45)
    return master(hall(out, 4.4, 0.42, 21), -2.5, tail=2.0)


def s_star():
    out = canvas(0.9)
    place(out, bell(hz('A6'), 0.8, GLASS, decay=0.25, seed=150), 0, 0.8)
    place(out, sparkles(0.5, 5, chord(['A6', 'E7']), seed=151, decay=(.03, .1)), 0, 0.6)
    return master(hall(out, 1.4, 0.3, 22), -14, tail=0.3)


def s_result():
    out = canvas(2.8)
    for i, nm in enumerate(['D5', 'F#5', 'A5', 'D6']):
        place(out, bell(hz(nm), 2.5, GLASS, decay=0.8, seed=160 + i), i * 0.05, 0.5)
    place(out, choir(chord(['D4', 'F#4', 'A4', 'D5']), 2.2, 'a', attack=0.15, release=1.4, seed=165), 0.05, 0.6)
    return master(hall(out, 2.8, 0.38, 23), -9, tail=0.9)


SOUNDS = {
    'open': s_open, 'hover': s_hover, 'click': s_click, 'panel': s_panel, 'back': s_back, 'denied': s_denied,
    'pull': s_pull, 'one_start': s_one_start, 'gather': s_gather,
    'reveal_r': lambda: reveal(0), 'reveal_sr': lambda: reveal(1), 'reveal_ssr': lambda: reveal(2),
    'ten_start': s_ten_start, 'rise': s_rise,
    'light_r': lambda: light(0), 'light_sr': lambda: light(1), 'light_ssr': lambda: light(2),
    'whiteout': s_whiteout, 'card': lambda: card(0), 'card_sr': lambda: card(1), 'card_ssr': lambda: card(2),
    'fanfare': s_fanfare, 'star': s_star, 'result': s_result,
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
        rms = 20 * np.log10(np.sqrt(np.mean(x ** 2)) + 1e-12)
        print(f'{name:12s} {x.shape[1] / SR:5.2f} s  peak {20 * np.log10(np.abs(x).max()):6.1f} dB  rms {rms:6.1f} dB')


if __name__ == '__main__':
    main()
