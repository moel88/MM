"""Lee So-sang's H again, the formation's finale (2.101.0): its three recordings, one per user's video of it (the user:
"음성은 저렇게 3개고 랜덤으로 하나 나오면 돼"), each as it is.

usage: python3 -I tools/isosang/cut_hh_sounds.py VIDEO_1.mp4 VIDEO_2.mp4 VIDEO_3.mp4 [OUT_DIR]

(The user's three screen recordings: f9cd12d0-Screen_Recording_20261108_022341_3rd.mp4,
132cad3f-Screen_Recording_20261108_022418_3rd.mp4, 2ab538b9-Screen_Recording_20261108_022703_3rd.mp4, in that order:
isosang_hh_1.ogg to isosang_hh_3.ogg.)

Each begins with its first sound (a sharp rush of air, ONSET: where the 5 ms windows' loudness first comes within
RISE_DB of the loudest of the first second after it), and it is cut LEAD seconds before that: the game plays it from
H again's start, so its first sound comes with H again's flash (IsosangRules.FINALE_FLASH, 0.1 s). In each video that
sound comes 0.33 to 0.35 s after the picture's flash, and its second rush (the swords launched) 2.2 s after its first,
as the game's launch comes 2.2 s after its flash (FINALE_LAUNCH): cut so, both fall on the game's. LENGTH seconds of
it, its last END_FADE faded out (each has died away to under -45 dB by then); 3 ms faded in.

Its level is the game's: each is scaled by how much quieter isosang_h.ogg is than the formation's sound under it in its
video before it (the median of 0.1 s windows from 0.05 s to 0.15 s before its first sound, against that over
isosang_h.ogg's body, 5 to 38 s), so it is as loud against H's sound as in the user's video. Nothing else is changed:
mono (the two channels averaged) at 48 kHz.
"""
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'stand'))
from cut_rush import RATE, FADE_IN, write, decode as decode_any  # noqa: E402
sys.path.insert(0, str(Path(__file__).resolve().parent))
from cut_z_sound import decode  # noqa: E402

SOUNDS = Path(__file__).resolve().parents[2] / 'src/main/resources/assets/celestialfall/sounds'
# how far before its first sound each is cut (H again's flash, 0.1 s in), how long each is, its fade at the end
LEAD = .1
LENGTH = 5.0
END_FADE = .45
# where a first sound begins: within this of the loudest 5 ms window of the second after it
RISE_DB = 18


def windows(x, w):
    n = len(x) // w * w
    return 20 * np.log10(np.sqrt((x[:n].reshape(-1, w) ** 2).mean(axis=1)) + 1e-12)


def onset(x):
    """Where its first sound begins (seconds): the first 5 ms window within RISE_DB of the loudest of the second after
    the first window 15 dB over the formation's sound before it."""
    w = int(.005 * RATE)
    env = windows(x, w)
    floor = np.median(env[:int(.3 / .005)])
    first = int(np.nonzero(env > floor + 15)[0][0])
    top = env[first:first + 200].max()
    return int(np.nonzero(env[max(0, first - 40):] > top - RISE_DB)[0][0] + max(0, first - 40)) * .005


def level(x, a, b):
    """The median loudness of 0.1 s windows of {@code x} from {@code a} to {@code b} seconds (dB)."""
    w = int(.1 * RATE)
    return float(np.median(windows(x[int(a * RATE):int(b * RATE)], w)))


def main():
    if len(sys.argv) < 4:
        sys.exit(__doc__)
    out = Path(sys.argv[4]) if len(sys.argv) > 4 else SOUNDS
    h = decode_any(SOUNDS / 'isosang_h.ogg', 1)
    game = level(h, 5, 38)
    out.mkdir(parents=True, exist_ok=True)
    for i, src in enumerate(sys.argv[1:4]):
        x = decode(Path(src))
        at = onset(x)
        gain = game - level(x, .05, at - .15)
        start = int(round((at - LEAD) * RATE))
        y = x[start:start + int(round(LENGTH * RATE))].copy() * 10 ** (gain / 20)
        n_in, n_out = int(FADE_IN * RATE), int(END_FADE * RATE)
        y[:n_in] *= np.linspace(0, 1, n_in)
        y[-n_out:] *= np.cos(np.linspace(0, np.pi / 2, n_out)) ** 2
        name = f'isosang_hh_{i + 1}.ogg'
        write(y, out / name)
        env = windows(y, int(.05 * RATE))
        second = .1 + 1.6 + float(np.argmax(np.diff(env[int(1.7 / .05):int(3.0 / .05)])) + 1) * .05
        print(f'{name}: {len(y) / RATE:.2f} s from {at - LEAD:.3f} s (its first sound {at:.3f} s); gain {gain:+.1f} dB; '
              f'its second rush about {second:.2f} s in; peak {20 * np.log10(np.abs(y).max()):.1f} dBFS; '
              f'its last 0.5 s {np.max(env[-10:]):.0f} dB')


if __name__ == '__main__':
    main()
