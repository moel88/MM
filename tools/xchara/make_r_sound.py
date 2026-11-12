"""엑스 차라's R with the hack on, the cleave (2.121.0): its sound, made here (the user gave none).
usage: python3 -I tools/xchara/make_r_sound.py [OUT]

  xchara_r   one sound for the whole skill, played as it starts (client/xchara/KnifeClient, GiantCleave's times):
             0.20 s  the panel's key (a tick and a short high note)
             0.22 s  the knife grows: a crushed square sweeping down (it gets big) with a rising shimmer over it,
                     to a heavy low thump at 0.42 s as its weight comes into the hands
             0.55 s  the leap: a short whoosh up
             0.75 s  the wheel: the knife's long whoosh once round, rising, then falling into
             1.20 s  the blow: a deep thud, a crack of noise, a crushed square dropping, rubble of blips after it,
                     and a low tail dying away to 2.2 s

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

M.LOUDNESS.update({'xchara_r': -16.0})
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, 'src/main/resources/assets/celestialfall/sounds')
PRESS, GROW0, GROW1, LEAP, BLOW, END = .20, .22, .42, .55, 1.20, 2.25


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
    # the knife grows: down in pitch as it gets big, a shimmer rising over it, its weight landing in the hands
    d = GROW1 - GROW0
    put(out, .42 * low(sweep(1500, 130, d, 7, 4), 5000), GROW0)
    xs = t(d)
    put(out, .3 * norm(band(noise(len(xs), 302), 5000, 11000) * (xs / d) ** 2 * smooth((d - xs) / .02)), GROW0)
    put(out, .9 * thud(.28, 120, 52, .07, 303), GROW1 - .01)
    put(out, .25 * blips(.14, 8, 304, lo=700.0, hi=2600.0), GROW0 + .04)
    # the leap, the wheel
    put(out, .5 * whoosh(.3, .08, .06, .16, 500, 2400, 900, 305), LEAP - .02)
    put(out, .8 * whoosh(.62, .34, .24, .2, 240, 1700, 420, 306, weight=.5), BLOW - .58)
    # the blow
    put(out, 1.4 * thud(.5, 150, 34, .13, 307), BLOW - .006)
    xc = t(.22)
    put(out, .8 * norm(band(noise(len(xc), 308), 700, 7000) * np.exp(-xc / .03)), BLOW)
    put(out, .5 * low(sweep(900, 60, .3, 9, 3), 3600), BLOW + .005)
    put(out, .3 * blips(.4, 16, 309, lo=300.0, hi=2200.0), BLOW + .06)
    xt = t(END - BLOW)
    tail = (tone(np.full(len(xt), 55.0)) + .5 * tone(np.full(len(xt), 82.5))) * smooth(xt / .01) * np.exp(-xt / .3)
    put(out, .5 * tail, BLOW)
    return finish(out, 'xchara_r', fade_out=.2)


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
