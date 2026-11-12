"""엑스 차라's R with the hack on, the cleave (2.121.0; 2.122.0: no leap, the blow where they stand, much heavier in the
ground): its sound, made here (the user gave none).
usage: python3 -I tools/xchara/make_r_sound.py [OUT]

  xchara_r   one sound for the whole skill, played as it starts (client/xchara/KnifeClient, GiantCleave's times):
             0.20 s  the panel's key (a tick and a short high note)
             0.22 s  the blade grows: a crushed square sweeping down (it gets big) with a rising shimmer over it,
                     to a heavy low thump at 0.42 s as its weight comes into the hands
             0.50 s  raised over their head: a slow whoosh up, a thin ring at the top (0.70 s)
             0.80 s  down: the blade's long heavy whoosh, falling into
             1.00 s  the blow into the ground: two deep thuds a moment apart, a hard crack of noise, a crushed square
                     dropping, the floor breaking (bursts of low noise, rubble of blips falling for a second), and a
                     low tail dying away to 2.5 s

Each client plays it as the skill starts (loud for its user, fading out to 40 blocks for the rest; faded out if the
skill stops short). Made of noise and tones only with the parts of the hack's sounds (synth.py), fixed seeds, mono
48 kHz, brought to its loudness under a limiter that keeps peaks below -1 dB.
"""
import os
import subprocess
import sys
import tempfile

import numpy as np
from scipy.io import wavfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import synth as M  # noqa: E402
from synth import SR, band, blips, crush, finish, high, loudness, low, noise, norm, put, smooth, square, t, thud, tone, whoosh  # noqa: E402

M.LOUDNESS.update({'xchara_r': -15.0})
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, 'src/main/resources/assets/celestialfall/sounds')
PRESS, GROW0, GROW1, RAISED, BLOW, END = .20, .22, .42, .70, 1.00, 2.5


def sweep(f0, f1, d, hold, bits):
    x = t(d)
    f = f0 * (f1 / f0) ** (x / d)
    return crush(square(f) * smooth(x / .004) * smooth((d - x) / .03), hold, bits)


def make():
    x = t(END)
    n = len(x)
    out = np.zeros(n)
    # the panel's key
    xk = t(.05)
    put(out, .5 * norm(high(noise(len(xk), 301), 3000) * np.exp(-xk / .0015)), PRESS)
    put(out, .35 * low(square(np.full(len(xk), 1760.0)) * smooth(xk / .001) * np.exp(-xk / .012), 6000), PRESS + .004)
    # the blade grows: down in pitch as it gets big, a shimmer rising over it, its weight landing in the hands
    d = GROW1 - GROW0
    put(out, .42 * low(sweep(1500, 110, d, 7, 4), 5000), GROW0)
    xs = t(d)
    put(out, .3 * norm(band(noise(len(xs), 302), 5000, 11000) * (xs / d) ** 2 * smooth((d - xs) / .02)), GROW0)
    put(out, .9 * thud(.3, 110, 46, .08, 303), GROW1 - .01)
    put(out, .25 * blips(.14, 8, 304, lo=700.0, hi=2600.0), GROW0 + .04)
    # raised: a slow whoosh up, a thin ring at the top
    put(out, .4 * whoosh(.34, .16, .12, .12, 300, 1500, 700, 305), RAISED - .22)
    xr = t(.22)
    put(out, .22 * (tone(np.full(len(xr), 3520.0)) + .5 * tone(np.full(len(xr), 5280.0))) * smooth(xr / .002) * np.exp(-xr / .05), RAISED)
    # down
    put(out, .95 * whoosh(.46, .22, .15, .1, 200, 1500, 300, 306, weight=.7), BLOW - .24)
    # the blow into the ground
    put(out, 1.6 * thud(.7, 130, 28, .17, 307), BLOW - .006)
    put(out, 1.0 * thud(.5, 90, 24, .14, 310), BLOW + .07)
    xc = t(.3)
    put(out, 1.0 * norm(band(noise(len(xc), 308), 500, 7500) * np.exp(-xc / .035)), BLOW)
    put(out, .55 * low(sweep(800, 45, .36, 9, 3), 3200), BLOW + .005)
    # the floor breaking: bursts of low noise, rubble falling
    for k, (at, tau, lo, hi) in enumerate(((.03, .05, 150, 900), (.11, .06, 120, 700), (.2, .07, 200, 1200), (.33, .05, 150, 800), (.5, .06, 250, 1400), (.72, .05, 200, 1000))):
        xb = t(.25)
        put(out, (.55 - .06 * k) * norm(band(noise(len(xb), 320 + k), lo, hi) * smooth(xb / .003) * np.exp(-xb / tau)), BLOW + at)
    put(out, .3 * blips(.9, 30, 309, lo=250.0, hi=2000.0), BLOW + .06)
    xt = t(END - BLOW)
    tail = (tone(np.full(len(xt), 48.0)) + .5 * tone(np.full(len(xt), 72.0))) * smooth(xt / .01) * np.exp(-xt / .4)
    put(out, .6 * tail, BLOW)
    return finish(out, 'xchara_r', fade_out=.25)


def main():
    os.makedirs(OUT, exist_ok=True)
    v = make()
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, 'x.wav')
        wavfile.write(wav, SR, (v * 32767).astype(np.int16))
        subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', wav, '-ac', '1', '-c:a', 'libvorbis', '-q:a', '5', os.path.join(OUT, 'xchara_r.ogg')], check=True)
    print(f'xchara_r: {len(v) / SR:.2f} s, {loudness(v):.1f} LUFS, peak {20 * np.log10(np.abs(v).max()):.1f} dB')


if __name__ == '__main__':
    main()
