"""엑스 차라's E with the hack on, the restore (2.119.0): its sound, made here (the user gave none).
usage: python3 -I tools/xchara/make_e_sound.py [OUT]

  xchara_e   two key taps on the panel (0.27 s and 0.40 s into it: HealClient.TAP1, TAP2; the second is the press, the
             heal lands on it), and on the second a soft chime rising (three notes going up, a sweet fourth held over
             them) with a little shimmer, dying away over the lime light's second

Each client plays it as the skill starts (client/xchara/HealClient: loud for its user, fading out to 40 blocks for the
rest; faded out if the skill stops short). Made of noise and tones only with the parts of the hack's sounds (synth.py),
fixed seeds, mono 48 kHz, brought to its loudness (quiet: it is no blow) under a limiter that keeps peaks below -1 dB.
"""
import os
import subprocess
import sys
import tempfile

import numpy as np
from scipy.io import wavfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import synth as M  # noqa: E402
from synth import SR, band, finish, loudness, low, noise, norm, put, smooth, square, t, tone  # noqa: E402

M.LOUDNESS.update({'xchara_e': -19.5})
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, 'src/main/resources/assets/celestialfall/sounds')
TAP1, TAP2 = .27, .40


def key(seed, f):
    """A key of light tapped: a tiny click and a short soft blip."""
    x = t(.05)
    click = band(noise(len(x), seed), 2500, 7000) * np.exp(-x / .004)
    blip = low(square(np.full(len(x), float(f))), 4200) * smooth(x / .001) * np.exp(-x / .014)
    return .7 * norm(click) + .5 * blip


def bell(f, d, tau):
    """A soft bell: a tone and a quieter octave over it, struck gently and dying away."""
    x = t(d)
    return (tone(np.full(len(x), float(f))) + .32 * tone(np.full(len(x), 2.0 * f)) + .1 * tone(np.full(len(x), 3.01 * f))) * smooth(x / .006) * np.exp(-x / tau)


def restore():
    d = 1.3
    out = np.zeros(len(t(d)))
    put(out, .45 * key(301, 1320), TAP1)
    put(out, .6 * key(302, 1760), TAP2)
    # the chime: D, F sharp, A going up, the D above held over them
    for k, (f, tau) in enumerate(((587.3, .16), (740.0, .18), (880.0, .22), (1174.7, .34))):
        put(out, (.5 if k < 3 else .42) * bell(f, .85, tau), TAP2 + .012 + .07 * k)
    xs = t(.5)
    shimmer = band(noise(len(xs), 303), 5500, 10500) * smooth(xs / .05) * np.exp(-xs / .16)
    put(out, .1 * norm(shimmer), TAP2 + .05)
    return finish(low(out, 9000), 'xchara_e', fade_out=.2)


def main():
    os.makedirs(OUT, exist_ok=True)
    x = restore()
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, 'x.wav')
        wavfile.write(wav, SR, (x * 32767).astype(np.int16))
        subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', wav, '-ac', '1', '-c:a', 'libvorbis', '-q:a', '5', os.path.join(OUT, 'xchara_e.ogg')], check=True)
    print(f'xchara_e: {len(x) / SR:.2f} s, {loudness(x):.1f} LUFS, peak {20 * np.log10(np.abs(x).max()):.1f} dB')


if __name__ == '__main__':
    main()
