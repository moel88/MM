"""Synthesises Star Platinum's inhale's sounds (Y, 2.61.0, user request: "효과음 자체제작"): the suction, the catch and
the air let go.

usage: python3 -I tools/stand/make_inhale_sounds.py [OUT_DIR]

star_platinum_inhale: what the game plays from a tick into the inhale (client/stand/StandInhale.LEAD) for as long as it
lasts, faded out over a few ticks as it ends. First the stand draws its breath in, a breathy rush of air rising through
two formants, louder and louder; then, at 0.35 s into the file, the inhale's catch (StarPlatinumRules.INHALE_CATCH
ticks into it), the suction kicks in with a soft whump and the air roars in for the hold: a low roar, turbulent air
swirling (its loudness turning over a few times a second), a whistle of wind drawn through a narrow mouth that slowly
climbs, the hiss of air rushing in, a whoosh of air swirling past every half second or so, and under it all a faint
low hum, the stand's own. It runs on past the longest hold and dies away by itself.

star_platinum_inhale_catch: played as it catches someone: a deep thump, a pop of air, a short gulp of air sucked in
and its rush dying away.

star_platinum_inhale_release: played as it ends: the air it held let go, a breath out falling in pitch, and a soft puff.

Made from noise and tones only (fixed seeds), so there is nothing to license and every run makes the same files. Each is
mono at 48 kHz (the game plays them where the stand is, as loud as it is near: client/stand/StandInhale), set to its
own loudness (EBU R 128 / BS.1770, LOUDNESS: about the stand's other sounds, the long suction a little under them) with
only its peaks turned down under CEILING_DB, and written as OGG Vorbis by ffmpeg (on the PATH). OUT_DIR defaults to the mod's sounds folder.
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

# the rush's voice is -15 LUFS and the Star Finger -15.8: the suction lasts five seconds and sits a little under them
LOUDNESS = {'star_platinum_inhale': -17.0, 'star_platinum_inhale_catch': -15.5, 'star_platinum_inhale_release': -18.5}
CEILING_DB = -1.0
# where the catch is in the suction's file (seconds): the inhale's catch less the tick its sound starts after it
CATCH = 8 / 20 - 1 / 20


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


def finish(x, name, fade_in=.003, fade_out=.05):
    x = high(x, 28)
    x[:int(fade_in * SR)] *= np.linspace(0, 1, int(fade_in * SR))
    n = int(fade_out * SR)
    x[-n:] *= np.linspace(1, 0, n)
    # to its loudness with only its peaks turned down under the ceiling (not clipped: a clipped thump buzzes); the
    # limiter takes a little off the loudness, so it is set again and limited again until both hold
    ceiling = 10 ** (CEILING_DB / 20)
    for _ in range(4):
        x = limit(x * 10 ** ((LOUDNESS[name] - loudness(x)) / 20), ceiling)
    return x


def limit(x, ceiling, look=.004, release=.05):
    """Turns down only what would go over the ceiling (tools/meteor/user_fall_sound.py's limiter): the gain eases down
    over the `look` seconds before such a peak and comes back up over `release` seconds after it."""
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


def inhale():
    """The suction: the breath drawn in, the kick of the catch and the roar of the hold, dying away by itself."""
    d = 5.9
    x = t(d)
    n = len(x)
    out = np.zeros(n)
    # the breath drawn in: louder and louder through the formants of a breath ("hhhh"), rising a little, cut by the catch
    rise = smooth(x / (CATCH - .02)) ** 1.4 * (1 - smooth((x - CATCH + .01) / .08))
    breath = swept(noise(n, 1), 900 + 500 * smooth(x / CATCH), 2.2) + .7 * swept(noise(n, 2), 2300 + 900 * smooth(x / CATCH), 3.0)
    breath += .25 * high(noise(n, 3), 4500)
    out += norm(breath) * rise * 1.25
    # the suction coming on at the catch (and staying until it dies away at the end)
    on = smooth((x - CATCH + .03) / .1) * (1 - smooth((x - 5.35) / .5))
    # its kick: a soft whump of air
    k = np.clip(x - CATCH, 0, None)
    whump = tone(36 + 46 * np.exp(-k / .12)) * np.exp(-k / .22) * smooth((x - CATCH) / .006) * (x >= CATCH)
    out += whump * .55 + norm(low(noise(n, 4), 260)) * np.exp(-k / .1) * (x >= CATCH) * .35
    # the roar: low air, swelling slowly
    roar = norm(low(brown(n, 5), 240, 3)) * (1 + .18 * np.sin(2 * np.pi * .35 * x + .4))
    out += roar * on * .42
    # turbulent air swirling in: its loudness turning over a few times a second
    swirl = 1 + .35 * np.sin(2 * np.pi * 3.1 * x) * np.sin(2 * np.pi * .7 * x + 1.1) + .2 * np.sin(2 * np.pi * 5.3 * x + .3)
    air = norm(band(noise(n, 6) + .5 * brown(n, 7), 320, 1500)) + .55 * norm(band(noise(n, 12), 1400, 3200))
    out += air * swirl * on * .45
    # a whistle of wind through a narrow mouth: high, a little unsteady, slowly climbing
    climb = 760 + 70 * smooth((x - CATCH) / 4.6) + 35 * np.sin(2 * np.pi * .6 * x) + 12 * np.sin(2 * np.pi * 2.3 * x + 1)
    whistle = norm(swept(noise(n, 8), climb, 45)) + .5 * norm(swept(noise(n, 9), climb * 1.52, 50))
    out += whistle * on * (.3 + .08 * np.sin(2 * np.pi * .9 * x))
    # the hiss of air rushing in, with the swirl
    hiss = norm(band(noise(n, 10), 2600, 7000))
    out += hiss * on * swirl * .32
    # a whoosh of air swirling past every half second or so
    rng = np.random.default_rng(11)
    at = CATCH + .15
    while at < 5.3:
        dur = .48 + .2 * rng.random()
        u = np.clip((x - at) / dur, 0, 1)
        bump = np.sin(np.pi * u) ** 2 * ((x >= at) & (x < at + dur))
        centre = 380 + 1100 * np.sin(np.pi * u) ** 1.5
        w = swept(noise(n, int(rng.integers(1 << 30))), centre, 2.4)
        out += norm(w) * bump * (.32 + .12 * rng.random())
        at += .5 + .3 * rng.random()
    # and under it all a faint low hum, the stand's own
    hum = tone(np.full(n, 55.0)) + .5 * tone(np.full(n, 110.3), 1.0) + .25 * tone(np.full(n, 164.9), 2.0)
    out += hum * on * (.05 + .02 * np.sin(2 * np.pi * 1.25 * x))
    return finish(out, 'star_platinum_inhale', fade_out=.1)


def catch():
    """The catch: a deep thump, a pop of air, a gulp of air sucked in and its rush dying away."""
    d = .9
    x = t(d)
    n = len(x)
    out = np.zeros(n)
    thump = tone(40 + 58 * np.exp(-x / .07)) * np.exp(-x / .2) * smooth(x / .004)
    out += thump * .8
    body = norm(low(noise(n, 21), 320, 3)) * np.exp(-x / .09)
    out += body * .6
    pop = norm(band(noise(n, 22), 700, 2600)) * np.exp(-x / .018) * smooth(x / .002)
    out += pop * .55
    gulp = swept(noise(n, 23), 1900 * np.exp(-x / .09) + 420, 4.0) * smooth(x / .01) * np.exp(-np.clip(x - .05, 0, None) / .07)
    out += norm(gulp) * .45
    tail = norm(low(noise(n, 24) + brown(n, 25), 900)) * smooth(x / .03) * np.exp(-x / .3)
    out += tail * .35
    return finish(out, 'star_platinum_inhale_catch', fade_out=.08)


def release():
    """The air let go: a breath out falling in pitch, and a soft puff."""
    d = .8
    x = t(d)
    n = len(x)
    out = np.zeros(n)
    breath = swept(noise(n, 31), 500 + 1300 * np.exp(-x / .16), 2.0) + .5 * swept(noise(n, 32), 1400 + 1800 * np.exp(-x / .14), 3.0)
    out += norm(breath) * smooth(x / .012) * np.exp(-x / .2) * .9
    puff = tone(44 + 30 * np.exp(-x / .08)) * np.exp(-x / .14) * smooth(x / .005)
    out += puff * .45
    air = norm(low(noise(n, 33), 700)) * smooth(x / .02) * np.exp(-x / .26)
    out += air * .3
    return finish(out, 'star_platinum_inhale_release', fade_out=.08)


def main():
    os.makedirs(OUT, exist_ok=True)
    made = {'star_platinum_inhale': inhale(), 'star_platinum_inhale_catch': catch(), 'star_platinum_inhale_release': release()}
    with tempfile.TemporaryDirectory() as tmp:
        for name, x in made.items():
            wav = os.path.join(tmp, name + '.wav')
            wavfile.write(wav, SR, (x * 32767).astype(np.int16))
            subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', wav, '-ac', '1', '-c:a', 'libvorbis', '-q:a', '5',
                            os.path.join(OUT, name + '.ogg')], check=True)
            print(f'{name}: {len(x) / SR:.2f} s, {loudness(x):.1f} LUFS, peak {20 * np.log10(np.abs(x).max()):.1f} dB')


if __name__ == '__main__':
    main()
