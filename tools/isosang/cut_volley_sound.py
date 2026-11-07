"""Lee So-sang's H basic attack, 회풍만검 (2.95.0): the sound of its six swords fanning out behind her and coming down on
the foe, from the user's video of it (5f78fd72-lv_0_20261107085427.mp4, 60 fps, at its real speed), as it is.

usage: python3 -I tools/isosang/cut_volley_sound.py VOLLEY_VIDEO.mp4 [OUT_DIR]

The video has her use it four times (the first sword shows behind her in its picture at 2.267, 6.533, 8.800 and 11.400
s); the second is cut, the formation's own sound under it the steadiest. Each sounds alike: a rush as the swords show
(beginning as the first does, loudest some 0.3 s on as they are thrown) and a burst as they strike (0.9 to 1.4 s after
the first shows; the game's blows come 0.95 to 1.2 s after it). The recording's sound comes AV_LAG seconds after its
picture (0.35 s, as in the user's other videos: here the rush begins 0.35 to 0.40 s after the first sword shows), so the
cut begins that much after the moment of the press (START + AV_LAG, START being IsosangRules.VOLLEY_SHOW0 before the
first sword shows): played as the press is heard of in the game, its rush begins as the swords show and its burst comes
on the blows. LENGTH seconds of it, till it is back down to the formation's sound under it, faded out over END_FADE; 3
ms faded in.

Under it is the formation's own sound (H's, which the game plays too). Its level is the game's: the cut is scaled by
GAIN_DB, how much quieter isosang_h.ogg is than the same sound in this video (the median of 0.1 s windows over the video
away from the volleys against that over isosang_h.ogg's body, 5 to 38 s: -1.2 dB; -1.1 to -1.6 dB by other measures), so
the swords are as loud against H's sound as in the user's video. Nothing else is changed: mono (the two channels
averaged) at 48 kHz.
"""
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'stand'))
from cut_rush import RATE, FADE_IN, write  # noqa: E402
sys.path.insert(0, str(Path(__file__).resolve().parent))
from cut_z_sound import decode  # noqa: E402

SOUNDS = Path(__file__).resolve().parents[2] / 'src/main/resources/assets/celestialfall/sounds'
# the moment of the press in the video's picture (its second volley: its first sword shows at 6.533 s, the game's first
# 0.05 s after the press), and how long after its picture the recording's sound comes
START = 6.533 - .05
AV_LAG = .35
# how long the cut is (back down to the formation's sound by then), and the fade at its end
LENGTH = 1.75
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
    floor = np.median(env[:4])
    rise = int(np.nonzero(env > floor + 8)[0][0])
    # the strikes' burst: the loudest after the rush has died down (0.75 s on)
    late = int(.75 / .01)
    burst = late + int(np.nonzero(env[late:] > floor + 12)[0][0])
    out.mkdir(parents=True, exist_ok=True)
    write(y, out / 'isosang_volley.ogg')
    print(f'isosang_volley.ogg: {len(y) / RATE:.3f} s from {START + AV_LAG:.3f} s; its rush begins {rise * .01:.2f} s in '
          f'(loudest {np.argmax(env[:late]) * .01:.2f} s), its strikes\' burst {burst * .01:.2f} s in (loudest '
          f'{(late + np.argmax(env[late:])) * .01:.2f} s); peak {20 * np.log10(np.abs(y).max()):.1f} dBFS')


if __name__ == '__main__':
    main()
