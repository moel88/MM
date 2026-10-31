"""Lee So-sang's E (2.80.0): the empowered combo's first and fourth hits' sounds.

usage: python3 -I tools/isosang/cut_e_sounds.py E.mp4 [OUT_DIR]

E.mp4 is the user's video of E from behind (69ae1503-lv_0_20261031212917.mp4): the empowered combo struck once, its
sound the game's (the sword and the voice), cut off as the empowered fourth begins. The user chose (2.80.0): the
empowered first's sound as the video has it, and for the empowered fourth the fourth's own recording, set to its thrust
("지금 4타 소리를 맞춰 씀").

- isosang_m1_5.ogg, the empowered first (three swords thrown): the video's sound from where the empowered first begins
  (where its sound starts out of the silence the user's edit leaves before it, FIRST_LEAD before that) to where the
  second hit begins. The second's sound in the video is its own recording (isosang_m1_2.ogg, the same take: correlation
  0.99), found by its waveform: where it starts is where the second begins (it was cut to start with its hit), and how
  loud it is there is the video's level against the four hits' files (0.6 of theirs): the cut is brought up by as much,
  so it sounds as loud as the others (their one gain, the balance as recorded). Mono 48 kHz, 3 ms faded in and
  END_FADE out where the second begins.
- isosang_m1_6.ogg, the empowered fourth (the step back and the giant sword): the fourth's file from SHIFT on, so the
  sound of its slash thrown (its blow, 0.5 s in) comes on the thrust's blow (0.15 s in); 3 ms faded in.
"""
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'stand'))
from cut_rush import RATE, FADE_IN, decode, write  # noqa: E402

SOUNDS = Path(__file__).resolve().parents[2] / 'src/main/resources/assets/celestialfall/sounds'
# the empowered first's file starts this long before its sound does (the swing's first moment)
FIRST_LEAD = .02
# its fade where the second hit's sound takes over
END_FADE = .04
# the fourth's blow (0.5 s) less the thrust's (0.15 s): IsosangRules.blowAt(4, 0) and blowAt(THRUST, 0), seconds
SHIFT = .5 - .15


def find(template, x):
    """Where the template starts in x (samples) and how loud x is there against it (least squares)."""
    n = min(len(template), int(.5 * RATE))
    t = template[:n]
    c = np.correlate(x, t, mode='valid')
    norm = np.sqrt(np.convolve(x * x, np.ones(n), mode='valid') * np.dot(t, t) + 1e-12)
    k = int(np.argmax(c / norm))
    seg = x[k:k + n]
    return k, float(np.dot(seg, t) / np.dot(t, t)), float(c[k] / norm[k])


def onset(x, floor_db=-40.0, window=.005):
    """Where the sound starts: the first 5 ms window whose loudness is within floor_db of the loudest (the faint room
    tone the recording begins with, before the user's edit cut it to silence, is far under it)."""
    w = int(window * RATE)
    n = len(x) // w
    rms = np.sqrt((x[:n * w].reshape(n, w) ** 2).mean(axis=1))
    loud = np.nonzero(rms > rms.max() * 10 ** (floor_db / 20))[0]
    return int(loud[0] * w)


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    out = Path(sys.argv[2]) if len(sys.argv) > 2 else SOUNDS
    video = decode(Path(sys.argv[1]))
    second = decode(SOUNDS / 'isosang_m1_2.ogg', 1)
    at, level, corr = find(second, video)
    begin = max(0, onset(video) - int(FIRST_LEAD * RATE))
    first = video[begin:at] / level
    n_in, n_out = int(FADE_IN * RATE), int(END_FADE * RATE)
    first[:n_in] *= np.linspace(0, 1, n_in)
    first[-n_out:] *= np.linspace(1, 0, n_out)
    out.mkdir(parents=True, exist_ok=True)
    write(first, out / 'isosang_m1_5.ogg')
    print(f'the second hit\'s sound found at {at / RATE:.3f} s of the video (correlation {corr:.3f}, the video {level:.3f} of the files);'
          f' isosang_m1_5.ogg: {len(first) / RATE:.3f} s from {begin / RATE:.3f} s, peak {20 * np.log10(np.abs(first).max()):.1f} dBFS')
    fourth = decode(SOUNDS / 'isosang_m1_4.ogg', 1)
    thrust = fourth[int(SHIFT * RATE):].copy()
    thrust[:n_in] *= np.linspace(0, 1, n_in)
    write(thrust, out / 'isosang_m1_6.ogg')
    print(f'isosang_m1_6.ogg: {len(thrust) / RATE:.3f} s, the fourth\'s from {SHIFT:.2f} s, peak {20 * np.log10(np.abs(thrust).max()):.1f} dBFS')


if __name__ == '__main__':
    main()
