"""Synthesises the meteors' sounds (2.49.0): the rush of one coming in, its landing, a pickaxe hit on it, a fragment
breaking off, the rock crumbling, and a stand arrow made at the crafter.

usage: python3 -I tools/meteor/make_sounds.py [OUT_DIR]

Made from noise and tones only, so there is nothing to license. Each is mono (the game places a mono sound where it
is made and fades it with distance; a stereo one would sound the same everywhere) at 48 kHz, set to its own loudness
(EBU R 128 / BS.1770, LOUDNESS) with its peaks under CEILING_DB, and written as OGG Vorbis by ffmpeg (on the PATH).
OUT_DIR defaults to the mod's sounds folder.
"""
import os
import subprocess
import sys
import tempfile
import numpy as np
from scipy import signal
from scipy.io import wavfile

SR = 48000
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, 'src/main/resources/assets/celestialfall/sounds')

LOUDNESS = {'meteor_fall': -15.0, 'meteor_impact': -12.5, 'meteor_hit': -17.0, 'meteor_break': -15.5,
            'meteor_crumble': -17.0, 'meteor_craft': -18.0}
CEILING_DB = -1.0


def t(d):
    return np.arange(int(round(d * SR))) / SR


def noise(n, seed):
    return np.random.default_rng(seed).standard_normal(n)


def brown(n, seed):
    x = np.cumsum(noise(n, seed))
    b, a = signal.butter(1, 20 / (SR / 2), 'high')
    x = signal.lfilter(b, a, x)
    return x / (np.abs(x).max() + 1e-9)


def band(x, lo, hi, order=2):
    b, a = signal.butter(order, [lo / (SR / 2), hi / (SR / 2)], 'band')
    return signal.lfilter(b, a, x)


def low(x, hz, order=2):
    b, a = signal.butter(order, hz / (SR / 2), 'low')
    return signal.lfilter(b, a, x)


def high(x, hz, order=2):
    b, a = signal.butter(order, hz / (SR / 2), 'high')
    return signal.lfilter(b, a, x)


def place(dst, src, at, gain=1.0):
    i = int(round(at * SR))
    n = min(len(src), len(dst) - i)
    if n > 0:
        dst[i:i + n] += src[:n] * gain
    return dst


def swept_low(x, start, end, curve=1.0):
    """A low-pass whose cut-off moves from {start} to {end} Hz over the sound (block by block, state kept)."""
    out = np.zeros_like(x)
    block = 512
    zi = None
    for i in range(0, len(x), block):
        k = (i / len(x)) ** curve
        hz = start * (end / start) ** k
        b, a = signal.butter(2, min(hz, SR * .45) / (SR / 2), 'low')
        if zi is None:
            zi = signal.lfilter_zi(b, a) * 0
        out[i:i + block], zi = signal.lfilter(b, a, x[i:i + block], zi=zi)
    return out


def clicks(d, times, gains, seed, lo=1500, hi=6000, decay=.006):
    """Small hard knocks (stones, grit) at {times}."""
    out = np.zeros(int(d * SR))
    rng = np.random.default_rng(seed)
    for at, g in zip(times, gains):
        n = int(.05 * SR)
        burst = rng.standard_normal(n) * np.exp(-np.arange(n) / SR / decay)
        f = rng.uniform(lo, hi)
        burst = band(burst, f * .6, min(f * 1.6, SR * .45))
        place(out, burst, at, g)
    return out


def bell(f0, ratios, decays, d, gains=None):
    """Struck metal or glass: inharmonic partials, each dying away at its own rate."""
    x = t(d)
    out = np.zeros_like(x)
    gains = gains or [1.0 / (i + 1) for i in range(len(ratios))]
    for r, dec, g in zip(ratios, decays, gains):
        out += g * np.sin(2 * np.pi * f0 * r * x + np.random.default_rng(int(f0 * r)).random() * 6.28) * np.exp(-x / dec)
    attack = np.minimum(1, x / .0015)
    return out * attack


def k_weighted(x):
    b1, a1 = [1.53512485958697, -2.69169618940638, 1.19839281085285], [1, -1.69065929318241, 0.73248077421585]
    b2, a2 = [1.0, -2.0, 1.0], [1, -1.99004745483398, 0.99007225036621]
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


def finish(x, name, fade_out=.05):
    x = high(x, 28)
    n = int(fade_out * SR)
    x[-n:] *= np.linspace(1, 0, n)
    x = x * 10 ** ((LOUDNESS[name] - loudness(x)) / 20)
    ceiling = 10 ** (CEILING_DB / 20)
    if np.abs(x).max() > ceiling:
        x = np.tanh(x / ceiling) * ceiling
    return x


def fall():
    """3.6 s of a meteor coming in, loudest at the end (the landing takes over there): a roar opening up as it nears,
    a crackle of burning growing denser, a whoosh sweeping down and a rumble under it all."""
    d = 3.75
    x = t(d)
    k = np.clip(x / 3.6, 0, 1)
    near = (0.08 + 0.92 * k ** 2.2)
    roar = swept_low(brown(len(x), 1) * .6 + noise(len(x), 2) * .25, 380, 2600, 1.6)
    flutter = 1 + .35 * low(noise(len(x), 3), 14) / .05
    roar *= near * np.clip(flutter, .4, 1.8)
    rng = np.random.default_rng(4)
    times, at = [], 0.05
    while at < 3.6:
        times.append(at)
        at += rng.exponential(1 / (8 + 70 * (at / 3.6) ** 2))
    crackle = clicks(d, times, [rng.uniform(.2, 1) * (.2 + .8 * (a / 3.6) ** 1.5) for a in times], 5, 2000, 9000, .003)
    whoosh = np.zeros_like(x)
    src = noise(len(x), 6)
    for i in range(0, len(x), 1024):
        c = 1900 * (650 / 1900) ** (i / len(x))
        whoosh[i:i + 1024] = band(src[max(0, i - 2048):i + 1024], c * .8, c * 1.25)[-len(src[i:i + 1024]):]
    whoosh *= near * .5
    rumble = low(brown(len(x), 7), 90, 3) * (0.1 + 0.9 * k ** 3) * 1.4
    out = roar * .9 + crackle * .5 + whoosh + rumble
    out[x > 3.6] *= np.exp(-(x[x > 3.6] - 3.6) / .03)
    return finish(out, 'meteor_fall', .02)


def impact():
    """The landing: a hard crack, a deep boom falling in pitch, the blast's roar, stones raining down and rattling for
    two seconds, a long rumble and the hiss of the fire."""
    d = 4.2
    x = t(d)
    out = np.zeros_like(x)
    crack = high(noise(len(x), 11), 900) * np.exp(-x / .012)
    out += crack * 1.6
    f = 32 + 70 * np.exp(-x / .35)
    boom = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-x / .55) * np.minimum(1, x / .004)
    out += boom * 2.2
    thump = low(noise(len(x), 12), 220, 3) * np.exp(-x / .22)
    out += thump / (np.abs(thump).max() + 1e-9) * 1.4
    blast = low(brown(len(x), 13) + .3 * noise(len(x), 14), 1600) * np.exp(-x / .45)
    out += blast / (np.abs(blast).max() + 1e-9) * 1.1
    rng = np.random.default_rng(15)
    times = np.sort(.12 + rng.gamma(1.6, .35, 140))
    times = times[times < 2.6]
    gains = [rng.uniform(.15, 1) * np.exp(-(a - .12) / .9) for a in times]
    out += clicks(d, times, gains, 16, 900, 5200, .01) * 1.1
    rumble = low(brown(len(x), 17), 160, 3) * np.exp(-x / 1.4)
    out += rumble / (np.abs(rumble).max() + 1e-9) * .8
    hiss = high(noise(len(x), 18), 3500) * (1 - np.exp(-x / .2)) * np.exp(-x / 1.6) * .12
    out += hiss
    return finish(out, 'meteor_impact', .3)


def hit():
    """A pickaxe on the meteor: a ringing clink of iron on space-iron, a stony knock and a little grit."""
    d = .45
    x = t(d)
    out = bell(1850, [1, 1.51, 2.27, 3.08, 4.4], [.13, .09, .065, .045, .03], d, [1, .7, .5, .35, .2]) * .8
    knock = low(noise(len(x), 21), 900) * np.exp(-x / .03)
    out += knock / (np.abs(knock).max() + 1e-9) * .9
    grit = high(noise(len(x), 22), 2500) * np.exp(-x / .012) * .5
    out += grit
    rng = np.random.default_rng(23)
    out += clicks(d, .03 + rng.random(5) * .2, rng.uniform(.1, .4, 5), 24, 2000, 7000, .004)
    return finish(out, 'meteor_hit')


def brk():
    """A fragment coming off: a sharp crack and knock, then a clear chime of the metal (D and A), and a few stones."""
    d = 1.3
    x = t(d)
    out = high(noise(len(x), 31), 1800) * np.exp(-x / .02) * 1.2
    knock = low(noise(len(x), 32), 700) * np.exp(-x / .05)
    out += knock / (np.abs(knock).max() + 1e-9)
    chime = bell(1174.66, [1, 2.0, 2.76, 5.40], [.55, .35, .22, .1], d, [1, .45, .3, .12])
    chime2 = bell(1760.0, [1, 2.0, 2.76], [.45, .3, .18], d, [.7, .3, .15])
    out = place(out, chime * .55, .02)
    out = place(out, chime2 * .4, .07)
    rng = np.random.default_rng(33)
    out += clicks(d, .05 + rng.random(8) * .35, rng.uniform(.1, .5, 8), 34, 1200, 5000, .006)
    return finish(out, 'meteor_break', .2)


def crumble():
    """The rock falling apart: a dull collapse, gravel pouring for a second and stones knocking as they settle."""
    d = 2.0
    x = t(d)
    collapse = low(noise(len(x), 41), 300, 3) * np.exp(-x / .3) * np.minimum(1, x / .02)
    out = collapse / (np.abs(collapse).max() + 1e-9) * 1.2
    gravel = band(noise(len(x), 42), 700, 4500) * (np.minimum(1, x / .06) * np.exp(-x / .6))
    out += gravel * .35
    rng = np.random.default_rng(43)
    times = np.sort(rng.gamma(1.5, .25, 90))
    times = times[times < 1.6]
    out += clicks(d, times, [rng.uniform(.15, 1) * np.exp(-a / .7) for a in times], 44, 600, 4000, .012)
    return finish(out, 'meteor_crumble', .25)


def craft():
    """A stand arrow made: a hammer on the anvil, then a bright little rising chime (D, F#, A, D) with a shimmer."""
    d = 1.6
    x = t(d)
    out = bell(620, [1, 2.32, 3.86, 5.1, 6.9], [.5, .32, .2, .12, .08], d, [1, .6, .4, .25, .12]) * .7
    out += high(noise(len(x), 51), 2000) * np.exp(-x / .01) * .8
    notes = [1174.66, 1479.98, 1760.0, 2349.32]
    for i, f0 in enumerate(notes):
        out = place(out, bell(f0, [1, 2.0, 3.0], [.4, .2, .1], 1.0, [1, .25, .1]) * .32, .18 + i * .075)
    shimmer = high(noise(len(x), 52), 6000) * np.exp(-np.abs(x - .45) / .2) * .05
    out += shimmer
    return finish(out, 'meteor_craft', .2)


def main():
    os.makedirs(OUT, exist_ok=True)
    made = {'meteor_fall': fall(), 'meteor_impact': impact(), 'meteor_hit': hit(), 'meteor_break': brk(),
            'meteor_crumble': crumble(), 'meteor_craft': craft()}
    with tempfile.TemporaryDirectory() as tmp:
        for name, x in made.items():
            wav = os.path.join(tmp, name + '.wav')
            wavfile.write(wav, SR, (x * 32767).astype(np.int16))
            subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', wav, '-ac', '1', '-c:a', 'libvorbis', '-q:a', '5',
                            os.path.join(OUT, name + '.ogg')], check=True)
            print(f'{name}: {len(x) / SR:.2f} s, {loudness(x):.1f} LUFS, peak {20 * np.log10(np.abs(x).max()):.1f} dB')


if __name__ == '__main__':
    main()
