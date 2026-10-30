"""Synthesises Lee So-sang's basic attack's sounds (2.77.0): the four hits' swings and the blade biting.

usage: python3 -I tools/isosang/make_m1_sounds.py [OUT_DIR]

isosang_m1_1..4: what the game plays as a client hears of a hit (client/isosang/IsosangClient), the moment the hit's
clock starts there too, so every whoosh is cut to land on its blow (IsosangRules: the ticks into the hit / 20 s):
  1. the step in (0.13 s) and the great diagonal cut (0.30 s): a long swish of air through the blade, falling as it
     comes down, a thin ring of the blade on the blow;
  2. the turn: the blade whirling round the body (0.17 to 0.5 s), loudest as it passes the front (0.30 s), a ring;
  3. the thrust's sharp hiss (0.25 s), the cross's two quick swishes (0.45 and 0.55 s) and the sweep's longer one
     (0.70 s), the blade ringing on the thrust and the sweep;
  4. the twirl's whirr over the head (0.06 to 0.23 s), the lunge's step and rush of air (0.36 to 0.45 s), the great cut
     (0.50 s) with a deep thump and the ink's splash after it.
isosang_m1_hit: a blow striking someone (played on them as the server tells it): a crack, a dull thud and a short ring.

Made from noise and tones only (fixed seeds), so there is nothing to license and every run makes the same files. Each is
mono at 48 kHz, set to its own loudness (EBU R 128 / BS.1770, LOUDNESS) with only its peaks turned down under CEILING_DB,
and written as OGG Vorbis by ffmpeg (on the PATH). The helpers are tools/stand/make_inhale_sounds.py's. OUT_DIR defaults
to the mod's sounds folder.
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

# the swings a little under Kageno's recorded ones (-10.8 to -14.6 LUFS), the finisher the loudest; the bite under them
LOUDNESS = {'isosang_m1_1': -16.0, 'isosang_m1_2': -16.5, 'isosang_m1_3': -15.5, 'isosang_m1_4': -14.5, 'isosang_m1_hit': -17.0}
CEILING_DB = -1.0
# the blows (seconds into the hit: IsosangRules.BLOWS / 20)
BLOWS = {1: [.30], 2: [.30], 3: [.25, .45, .55, .70], 4: [.50]}
LENGTH = {1: .9, 2: 1.0, 3: 1.15, 4: 1.45}


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
    block = 256
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
    """A tone whose frequency (Hz, one per sample) moves."""
    return np.sin(2 * np.pi * np.cumsum(f) / SR + phase)


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


def limit(x, ceiling, look=.004, release=.05):
    """Turns down only what would go over the ceiling: the gain eases down over the `look` seconds before such a peak and
    comes back up over `release` seconds after it."""
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


def finish(x, name, fade_in=.002, fade_out=.06):
    x = high(x, 30)
    x[:int(fade_in * SR)] *= np.linspace(0, 1, int(fade_in * SR))
    n = int(fade_out * SR)
    x[-n:] *= np.linspace(1, 0, n)
    ceiling = 10 ** (CEILING_DB / 20)
    for _ in range(4):
        x = limit(x * 10 ** ((LOUDNESS[name] - loudness(x)) / 20), ceiling)
    return x


def place(out, x, at):
    """Adds {x} into {out} starting {at} seconds in (cut at the end)."""
    i = int(round(at * SR))
    if i >= len(out):
        return
    n = min(len(x), len(out) - i)
    out[i:i + n] += x[:n]


def swish(dur, peak, f_lo, f_hi, seed, q=2.2, bright=.35):
    """Air cut by the blade: band-passed noise swelling to {peak} seconds in and dying fast after it, its pitch rising to
    the peak (the blade coming) and falling after (going), a brighter hiss riding the loudest part."""
    tt = t(dur)
    rise = smooth(tt / peak) ** 2.2
    fall = np.exp(-np.maximum(0, tt - peak) / (dur - peak) * 4.5)
    env = np.where(tt < peak, rise, fall)
    centre = np.where(tt < peak, f_lo + (f_hi - f_lo) * smooth(tt / peak), f_hi - (f_hi - f_lo) * .6 * smooth((tt - peak) / (dur - peak)))
    air = swept(noise(len(tt), seed), centre, q)
    hiss = high(noise(len(tt), seed + 1), 3500) * np.exp(-np.abs(tt - peak) / .025)
    return norm(air) * env + bright * norm(hiss) * env


def ring(dur, base, seed, decay=.16, level=1.0):
    """The blade ringing as it bites: a few inharmonic partials beating a little, the high ones dying first."""
    tt = t(dur)
    rng = np.random.default_rng(seed)
    out = np.zeros_like(tt)
    for k, (ratio, amp) in enumerate([(1, 1.0), (2.76, .6), (5.4, .35), (8.93, .2)]):
        f = base * ratio * (1 + rng.uniform(-.004, .004))
        out += amp * np.sin(2 * np.pi * f * tt + rng.uniform(0, 6.28)) * (1 + .15 * np.sin(2 * np.pi * (3 + k) * tt))\
            * np.exp(-tt / (decay / (1 + .6 * k)))
    attack = smooth(tt / .002)
    return level * norm(out) * attack


def thump(dur, f0, seed, level=1.0):
    """A dull low blow: a falling low tone and a puff of low noise."""
    tt = t(dur)
    f = f0 * (1 + 1.2 * np.exp(-tt / .02))
    body = tone(f) * np.exp(-tt / .09)
    puff = low(noise(len(tt), seed), 300) * np.exp(-tt / .04)
    return level * (norm(body) + .5 * norm(puff)) * smooth(tt / .003)


def crack(dur, seed, level=1.0):
    """A sharp crack: bright noise gone in a few milliseconds."""
    tt = t(dur)
    return level * norm(high(noise(len(tt), seed), 1800)) * np.exp(-tt / .006) * smooth(tt / .0006)


def step(seed, level=.5):
    """A foot planted hard: a soft thud and a scuff of grit."""
    tt = t(.18)
    thud = low(noise(len(tt), seed), 180) * np.exp(-tt / .03)
    scuff = band(noise(len(tt), seed + 1), 900, 4000) * np.exp(-tt / .05) * smooth(tt / .01)
    return level * (norm(thud) + .35 * norm(scuff))


def splash(seed, level=1.0):
    """Ink bursting: a wet rush of mid noise breaking up into droplets, and the droplets' little ticks."""
    tt = t(.6)
    grain = np.abs(low(noise(len(tt), seed), 40))
    wet = band(noise(len(tt), seed + 1), 300, 2400) * (grain / (grain.max() + 1e-9)) ** 1.5 * np.exp(-tt / .14) * smooth(tt / .008)
    rng = np.random.default_rng(seed + 2)
    drops = np.zeros_like(tt)
    for _ in range(22):
        at = rng.uniform(.03, .45)
        d = t(.03)
        f = rng.uniform(1400, 3200)
        place_drop = np.sin(2 * np.pi * (f + 2500 * d) * d) * np.exp(-d / .006)
        i = int(at * SR)
        drops[i:i + len(d)] += place_drop[:max(0, min(len(d), len(tt) - i))] * rng.uniform(.3, 1) * np.exp(-at / .25)
    return level * (norm(wet) + .35 * norm(drops))


def whirr(dur, turns, seed):
    """The sword twirled: whooshes coming round {turns} times, quicker and brighter as it spins up."""
    tt = t(dur)
    phase = 2 * np.pi * turns * (tt / dur) ** 1.15
    pulse = (.5 + .5 * np.cos(phase)) ** 3
    centre = 700 + 1300 * (tt / dur) + 600 * pulse
    air = swept(noise(len(tt), seed), centre, 2.5)
    env = smooth(tt / .03) * (1 - smooth((tt - dur + .04) / .04))
    return norm(air) * (.25 + .75 * pulse) * env


def hit1():
    out = np.zeros(int(LENGTH[1] * SR))
    place(out, step(11, .16), .12)
    place(out, swish(.32, .19, 380, 1500, 12, q=2.0) * .9, .11)
    place(out, ring(.45, 2650, 13, .14, .14), .3)
    place(out, step(14, .14), .29)
    return finish(out, 'isosang_m1_1')


def hit2():
    out = np.zeros(int(LENGTH[2] * SR))
    tt = t(.5)
    # round the body: the air swells each time the blade sweeps past the ear and is loudest across the front
    sweep = 2 * np.pi * smooth((tt - .02) / .4) * 1.0
    env = smooth(tt / .06) * (1 - smooth((tt - .38) / .12)) * (.45 + .55 * np.cos(sweep - 2 * np.pi * .35) ** 2)
    centre = 520 + 900 * env
    air = swept(noise(len(tt), 21), centre, 2.2)
    place(out, norm(air) * env, .14)
    place(out, swish(.22, .14, 500, 1700, 22, q=2.4, bright=.45) * .7, .16)
    place(out, ring(.5, 2350, 23, .16, .12), .3)
    return finish(out, 'isosang_m1_2')


def hit3():
    out = np.zeros(int(LENGTH[3] * SR))
    # the thrust: a short rising hiss, sharp at its end, the blade ringing higher
    tt = t(.12)
    hiss = swept(noise(len(tt), 31), 1200 + 4200 * smooth(tt / .1), 3.0) * smooth(tt / .1) ** 2 * np.exp(-np.maximum(0, tt - .1) / .01)
    place(out, norm(hiss), .14)
    place(out, crack(.03, 32, .45), .25)
    place(out, ring(.5, 3100, 33, .15, .17), .25)
    place(out, swish(.16, .09, 600, 1900, 34, q=2.6) * .8, .36)
    place(out, swish(.16, .09, 650, 2000, 35, q=2.6) * .8, .46)
    place(out, swish(.3, .17, 420, 1600, 36, q=2.1) * 1.0, .53)
    place(out, ring(.45, 2500, 37, .14, .12), .7)
    return finish(out, 'isosang_m1_3')


def hit4():
    out = np.zeros(int(LENGTH[4] * SR))
    place(out, whirr(.19, 3.0, 41) * .55, .05)
    place(out, step(42, .25), .36)
    tt = t(.12)
    rush = swept(noise(len(tt), 43), 300 + 900 * smooth(tt / .1), 1.8) * smooth(tt / .08)
    place(out, norm(rush) * .6, .34)
    place(out, swish(.36, .14, 260, 1300, 44, q=1.8, bright=.5) * 1.1, .36)
    place(out, thump(.45, 55, 45, .85), .5)
    place(out, ring(.6, 2200, 46, .18, .14), .5)
    place(out, splash(47, .7), .515)
    return finish(out, 'isosang_m1_4', fade_out=.12)


def bite():
    out = np.zeros(int(.4 * SR))
    place(out, crack(.03, 51, .9), 0)
    place(out, thump(.3, 85, 52, .7), .002)
    place(out, band(noise(int(.12 * SR), 53), 700, 3000) * np.exp(-t(.12) / .025) * .25, .0)
    place(out, ring(.3, 3400, 54, .12, .22), .003)
    return finish(out, 'isosang_m1_hit', fade_out=.05)


def main():
    os.makedirs(OUT, exist_ok=True)
    made = {'isosang_m1_1': hit1(), 'isosang_m1_2': hit2(), 'isosang_m1_3': hit3(), 'isosang_m1_4': hit4(), 'isosang_m1_hit': bite()}
    with tempfile.TemporaryDirectory() as tmp:
        for name, x in made.items():
            wav = os.path.join(tmp, name + '.wav')
            wavfile.write(wav, SR, (x * 32767).astype(np.int16))
            subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', wav, '-ac', '1', '-c:a', 'libvorbis', '-q:a', '5',
                            os.path.join(OUT, name + '.ogg')], check=True)
            print(f'{name}: {len(x) / SR:.2f} s, {loudness(x):.1f} LUFS, peak {20 * np.log10(np.abs(x).max()):.1f} dB')


if __name__ == '__main__':
    main()
