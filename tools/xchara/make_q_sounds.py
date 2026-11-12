"""엑스 차라's Q, the hack (2.115.0): its two sounds, made here (no recordings: the user's clip of it is a screen recording
with music under it, and nothing in it is the hack's alone).
usage: python3 -I tools/xchara/make_q_sounds.py [OUT]

  xchara_q_on    it comes on: a tick, a crushed tone sweeping up with three quick rising notes (a machine starting),
                 a bright ring as the knife lights (0.12 s in, as its white steadies: HackLook.fill), a short run of
                 data, and an electric hum that dies away
  xchara_q_off   it goes off: three quick falling notes, a crushed tone sweeping down, and an old screen switched off
                 as the panel folds to a line and the line goes out

Each client plays one as it sees the hack change (client/xchara/HackMode: loud for its user, fading out to 40 blocks for
the rest). Made of noise and tones only, with the parts of the knife's basic attack (synth.py: one palette for
the character), fixed seeds, mono 48 kHz; each brought to its loudness (quieter than a hit of the knife: it is a
state, not a blow) under a limiter that keeps peaks below -1 dB.
"""
import os
import subprocess
import sys
import tempfile

import numpy as np
from scipy.io import wavfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import synth as M  # noqa: E402
from synth import SR, band, blips, crush, finish, high, loudness, low, noise, norm, put, screen_off, smooth, square, t, tone  # noqa: E402

M.LOUDNESS.update({'xchara_q_on': -18.5, 'xchara_q_off': -20.0})
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, 'src/main/resources/assets/celestialfall/sounds')
# when the knife's white steadies and its rim flares (HackLook: seconds into its coming on)
LIT = .12


def note(f, d):
    x = t(d)
    return square(np.full(len(x), float(f))) * smooth(x / .0012) * smooth((d - x) / .004)


def sweep(f0, f1, d, hold, bits):
    """A crushed square sweeping from {f0} to {f1} Hz over {d} seconds."""
    x = t(d)
    f = f0 * (f1 / f0) ** (x / d)
    return crush(square(f) * smooth(x / .004) * smooth((d - x) / .02), hold, bits)


def on():
    d = .62
    x = t(d)
    n = len(x)
    out = np.zeros(n)
    out += .5 * norm(high(noise(n, 201), 3200) * np.exp(-x / .0014))
    put(out, .34 * low(sweep(220, 1760, .17, 6, 4), 6000), 0)
    for k, f in enumerate((660, 990, 1320)):
        put(out, .42 * low(note(f, .028), 5200), .03 + .035 * k)
    # the knife lights: a bright ring (a fifth), a shimmer over it
    xr = t(.3)
    ring = (tone(np.full(len(xr), 2640.0)) + .6 * tone(np.full(len(xr), 3960.0)) + .3 * tone(np.full(len(xr), 5280.0))) \
        * smooth(xr / .0015) * np.exp(-xr / .055)
    put(out, .55 * ring, LIT)
    shimmer = band(noise(len(xr), 202), 6000, 11000) * smooth(xr / .002) * np.exp(-xr / .04)
    put(out, .3 * norm(shimmer), LIT)
    put(out, .22 * blips(.16, 10, 203, lo=1100.0), LIT + .05)
    # the panel's hum, dying away
    xh = t(d - LIT)
    hum = (tone(np.full(len(xh), 110.0)) + .5 * low(square(np.full(len(xh), 220.0)), 900)) \
        * smooth(xh / .02) * np.exp(-xh / .15) * (1 + .25 * np.sin(2 * np.pi * 31 * xh))
    put(out, .4 * hum, LIT)
    return finish(out, 'xchara_q_on', fade_out=.12)


def off():
    d = .44
    out = np.zeros(len(t(d)))
    for k, f in enumerate((1320, 990, 660)):
        put(out, .4 * low(note(f, .026), 5200), .032 * k)
    put(out, .32 * low(sweep(1500, 150, .17, 7, 4), 5200), .02)
    put(out, .8 * screen_off(.3), .1)
    return finish(out, 'xchara_q_off', fade_out=.08)


def main():
    os.makedirs(OUT, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        for name, x in {'xchara_q_on': on(), 'xchara_q_off': off()}.items():
            wav = os.path.join(tmp, name + '.wav')
            wavfile.write(wav, SR, (x * 32767).astype(np.int16))
            subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', wav, '-ac', '1', '-c:a', 'libvorbis', '-q:a', '5',
                            os.path.join(OUT, name + '.ogg')], check=True)
            print(f'{name}: {len(x) / SR:.2f} s, {loudness(x):.1f} LUFS, peak {20 * np.log10(np.abs(x).max()):.1f} dB')


if __name__ == '__main__':
    main()
