"""Lee So-sang's Y (2.97.0): the sound of her six giant swords coming down two at a time round the foe and the ink
bursting up round it, from the user's second video of it (c6bb25f6-Screen_Recording_20261107_205313_3rd_1.mp4, a
screen recording at its real speed), as it is.

usage: python3 -I tools/isosang/cut_y_sound.py Y_VIDEO.mp4 [OUT_DIR]

In its picture Y begins at 0.125 s (its first frame of the camera swinging round; START). Its sound comes AV_LAG seconds
after its picture: 0.12 s, measured on the user's screen recording of T made the same way the same day
(5ea09bd2-Screen_Recording_20261107_114615_3rd.mp4): T's fan shows there 0.49 s into the picture (the game's first sword
0.05 s after the press, so the press at 0.44 s) and the game's T sound (isosang_t.ogg, cut from a video in time with its
picture) matches its sound best from 0.56 s. (The user's edited videos came 0.35 s late, or in time.) So cut: its first
sound, a chime, comes 0.55 s in, as the first two swords strike in the game (IsosangRules.CAGE_DROPS: 0.55 s); before
that only the formation's sound (H's, which the game plays too) is heard: the leap and the swords showing are silent in
the recording. LENGTH seconds of it, till it is back down to the formation's sound (2.85 s into the video), faded out
over END_FADE; 3 ms faded in.

Its level is the game's: the cut is scaled by GAIN_DB, how much quieter isosang_h.ogg is than the formation's sound in
this video (the median of 0.1 s windows away from Y, 0.05 to 0.75 and 2.85 to 3.35 s, against that over
isosang_h.ogg's body, 5 to 38 s: -1.1 dB), so the skill is as loud against H's sound as in the user's video. Nothing else
is changed: mono (the two channels averaged) at 48 kHz.
"""
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'stand'))
from cut_rush import RATE, FADE_IN, write  # noqa: E402
sys.path.insert(0, str(Path(__file__).resolve().parent))
from cut_z_sound import decode  # noqa: E402

SOUNDS = Path(__file__).resolve().parents[2] / 'src/main/resources/assets/celestialfall/sounds'
# Y's start in the video's picture (the camera's first swing round), and how long after its picture its sound comes
START = .125
AV_LAG = .12
# how long the cut is (back down to the formation's sound by then), and the fade at its end
LENGTH = 2.6
END_FADE = .35
# the game's level of the formation's sound against this video's (isosang_h.ogg, cut_h_sounds.py)
GAIN_DB = -1.1


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
    floor = np.median(env[:30])
    rise = int(np.nonzero(env > floor + 6)[0][0])
    out.mkdir(parents=True, exist_ok=True)
    write(y, out / 'isosang_y.ogg')
    print(f'isosang_y.ogg: {len(y) / RATE:.3f} s from {START + AV_LAG:.3f} s; its first sound {rise * .01:.2f} s in, '
          f'its loudest {np.argmax(env) * .01:.2f} s in; peak {20 * np.log10(np.abs(y).max()):.1f} dBFS')


if __name__ == '__main__':
    main()
