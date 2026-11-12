"""엑스 차라's R with the hack on, the cleave (2.121.0; 2.122.0: no leap, the blow where they stand, much heavier in the
ground; 2.123.0: the knife grows slowly, and the blow is heavier again, with an echo): its sound, made here (the user
gave none).
usage: python3 -I tools/xchara/make_r_sound.py [OUT]

  xchara_r   one sound for the whole skill, played as it starts (client/xchara/KnifeClient, GiantCleave's times):
             0.20 s  the panel's key (a tick and a short high note)
             0.22 s  the blade grows, slowly, to 0.85 s: a low hum swelling and sinking in pitch as it gets big, a
                     crushed square stepping down under it, a shimmer rising over it, ticks of data quickening (the
                     lines running out along the blade), to a heavy low thump at 0.85 s as it is full grown
             0.72 s  raised over their head: a slow whoosh up, a thin ring at the top (0.90 s)
             1.00 s  down: the blade's long heavy whoosh, falling into
             1.20 s  the blow into the ground: three deep thuds on top of each other, a hard crack of noise, a crushed
                     square dropping, the floor breaking (bursts of low noise, rubble of blips falling for over a
                     second), its echo at 1.46 s (a second thud and crack, smaller), and a low tail dying away to 3.2 s

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
from synth import SR, band, blips, crush, finish, high, loudness, low, noise, norm, put, saw, smooth, square, t, thud, tone, whoosh  # noqa: E402

M.LOUDNESS.update({'xchara_r': -15.0})
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, 'src/main/resources/assets/celestialfall/sounds')
PRESS, GROW0, GROW1, RAISED, BLOW, AFTER, END = .20, .22, .85, .90, 1.20, .26, 3.2


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
    # the blade grows, slowly: a hum swelling as it sinks in pitch, a crushed square stepping down under it,
    # a shimmer rising over it, ticks of data quickening, its weight landing as it is full grown
    d = GROW1 - GROW0
    xs = t(d)
    u = xs / d
    swell = smooth(xs / .05) * (.25 + .75 * u ** 1.5) * smooth((d - xs) / .03)
    hum = tone(180 * (55 / 180.0) ** u) + .5 * tone(360 * (110 / 360.0) ** u) + .25 * saw(90 * (55 / 90.0) ** u)
    put(out, .55 * low(hum, 900) * swell, GROW0)
    steps = 1400 * (120 / 1400.0) ** (np.floor(u * 10) / 10)
    put(out, .3 * low(crush(square(steps) * swell, 7, 4), 5000), GROW0)
    put(out, .3 * norm(band(noise(len(xs), 302), 5000, 11000) * u ** 2 * smooth((d - xs) / .02)), GROW0)
    for k in range(6):
        xt = t(.03)
        put(out, (.16 + .03 * k) * low(square(np.full(len(xt), 1200.0 + 260 * k)) * smooth(xt / .001) * np.exp(-xt / .008), 7000), GROW0 + .03 + .1 * k)
    put(out, 1.0 * thud(.34, 105, 42, .09, 303), GROW1 - .01)
    put(out, .22 * norm(band(noise(len(xk), 311), 2500, 9000) * np.exp(-xk / .01)), GROW1)
    # raised: a slow whoosh up, a thin ring at the top
    put(out, .4 * whoosh(.34, .16, .12, .12, 300, 1500, 700, 305), RAISED - .2)
    xr = t(.22)
    put(out, .22 * (tone(np.full(len(xr), 3520.0)) + .5 * tone(np.full(len(xr), 5280.0))) * smooth(xr / .002) * np.exp(-xr / .05), RAISED)
    # down
    put(out, 1.0 * whoosh(.46, .22, .15, .1, 200, 1500, 300, 306, weight=.8), BLOW - .24)
    # the blow into the ground
    put(out, 1.8 * thud(.8, 130, 26, .2, 307), BLOW - .006)
    put(out, 1.2 * thud(.6, 85, 22, .17, 310), BLOW + .05)
    put(out, .9 * thud(.9, 60, 20, .3, 312), BLOW + .01)
    xc = t(.36)
    put(out, 1.15 * norm(band(noise(len(xc), 308), 400, 8000) * np.exp(-xc / .04)), BLOW)
    put(out, .6 * low(sweep(800, 40, .42, 9, 3), 3200), BLOW + .005)
    # its echo
    put(out, 1.0 * thud(.5, 95, 24, .15, 313), BLOW + AFTER - .006)
    put(out, .55 * norm(band(noise(len(xc), 314), 300, 5000) * np.exp(-xc / .045)), BLOW + AFTER)
    # the floor breaking: bursts of low noise, rubble falling
    for k, (at, tau, lo, hi) in enumerate(((.03, .05, 150, 900), (.1, .06, 120, 700), (.18, .07, 200, 1200), (.33, .06, 150, 800), (.45, .06, 250, 1400), (.62, .05, 200, 1000), (.82, .06, 180, 900), (1.05, .05, 220, 1100))):
        xb = t(.25)
        put(out, (.6 - .055 * k) * norm(band(noise(len(xb), 320 + k), lo, hi) * smooth(xb / .003) * np.exp(-xb / tau)), BLOW + at)
    put(out, .32 * blips(1.3, 44, 309, lo=220.0, hi=2000.0), BLOW + .06)
    xt = t(END - BLOW)
    tail = (tone(np.full(len(xt), 44.0)) + .5 * tone(np.full(len(xt), 66.0))) * smooth(xt / .01) * np.exp(-xt / .5)
    put(out, .7 * tail, BLOW)
    return finish(out, 'xchara_r', fade_out=.3)


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
