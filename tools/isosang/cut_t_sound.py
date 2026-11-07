"""Lee So-sang's T (2.96.0): the sound of her six swords fanning out and flying, her dash through the foe, the swords
coming down on it and the three slashes, from the user's video of it (e6830cf0-lv_0_20261107114859.mp4, 60 fps, at its
real speed), as it is.

usage: python3 -I tools/isosang/cut_t_sound.py T_VIDEO.mp4 [OUT_DIR]

The video has her use it twice (the first sword of the fan shows behind her in its picture at 1.18 and 4.36 s); the
first is cut. Its sound is in time with its picture (unlike the user's other videos, whose sound came 0.35 s late): the
swords' loud blows are heard 1.60 to 1.85 s and 4.83 to 5.10 s, as the picture's hit counter climbs (1.67 to 1.90 and
4.85 to 5.15 s), and a crack as she passes through the foe with the picture's beam (1.37 and 4.55 s). So the cut begins
at the moment of the press in the picture (START: the first sword shows IsosangRules.VOLLEY_SHOW0 after it, as the
game's), AV_LAG 0. Played as the press is heard of in the game its rush comes as the fan shows, its crack as she cuts
through and its burst on the swords' blows. LENGTH seconds of it, till it is back down to the formation's sound under it
(2.7 s in the video), faded out over END_FADE; 3 ms faded in.

Under it is the formation's own sound (H's, which the game plays too). Its level is the game's: the cut is scaled by
GAIN_DB, how much quieter isosang_h.ogg is than the same sound in this video (the median of 0.1 s windows over the video
away from T, 0 to 1, 2.7 to 4.1 and 5.95 to 6.8 s, against that over isosang_h.ogg's body, 5 to 38 s: -1.16 dB), so the
skill is as loud against H's sound as in the user's video. Nothing else is changed: mono (the two channels averaged) at
48 kHz.
"""
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'stand'))
from cut_rush import RATE, FADE_IN, write  # noqa: E402
sys.path.insert(0, str(Path(__file__).resolve().parent))
from cut_z_sound import decode  # noqa: E402

SOUNDS = Path(__file__).resolve().parents[2] / 'src/main/resources/assets/celestialfall/sounds'
# the moment of the press in the video's picture (its first T: its first sword shows at 1.18 s, the game's first 0.05 s
# after the press), and how long after its picture the recording's sound comes (none here)
START = 1.18 - .05
AV_LAG = 0.0
# how long the cut is (back down to the formation's sound by then), and the fade at its end
LENGTH = 1.6
END_FADE = .2
# the game's level of the formation's sound against this video's (isosang_h.ogg, cut_h_sounds.py)
GAIN_DB = -1.2


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    out = Path(sys.argv[2]) if len(sys.argv) > 2 else SOUNDS
    x = decode(Path(sys.argv[1]))
    at = int(round((START + AV_LAG) * RATE))
    y = x[at:at + int(round(LENGTH * RATE))].copy() * 10 ** (GAIN_DB / 20)
    n_in, n_out = int(FADE_IN * RATE), int(END_FADE * RATE)
    y[:n_in] *= np.linspace(0, 1, n_in)
    y[-n_out:] *= np.cos(np.linspace(0, np.pi / 2, n_out)) ** 2
    w = int(.01 * RATE)
    env = 20 * np.log10(np.sqrt((y[:len(y) // w * w].reshape(-1, w) ** 2).mean(axis=1)) + 1e-12)
    floor = np.median(env[:6])
    rise = int(np.nonzero(env > floor + 6)[0][0])
    # the swords' blows: the loudest stretch, after the dash (0.35 s on)
    late = int(.35 / .01)
    out.mkdir(parents=True, exist_ok=True)
    write(y, out / 'isosang_t.ogg')
    print(f'isosang_t.ogg: {len(y) / RATE:.3f} s from {START + AV_LAG:.3f} s; its rush begins {rise * .01:.2f} s in, '
          f'its loudest {(late + np.argmax(env[late:])) * .01:.2f} s in; peak {20 * np.log10(np.abs(y).max()):.1f} dBFS')


if __name__ == '__main__':
    main()
