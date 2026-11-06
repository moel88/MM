"""Lee So-sang's Z (2.93.0): the sound of her move from one of her formation's swords to another, from the user's video of
it (68d5f592-lv_0_20261107004058.mp4, 60 fps, at its real speed), as it is (the user's choice: "영상 소리 사용").

usage: python3 -I tools/isosang/cut_z_sound.py Z_VIDEO.mp4 [OUT_DIR]

The video has her move three times (she leaves a sword in its picture at 6.43, 12.23 and 17.63 s); the last is cut, its
sound the cleanest (back down to the formation's own sound by 0.95 s after she leaves; the first two have more after
them). The recording's sound comes AV_LAG seconds after its picture (0.35 s, as in the user's other videos: here the
rush of the move begins 0.38 to 0.44 s after she leaves in the picture, as she does, and is loudest as she lands), so
the cut begins that much after she leaves (START + AV_LAG): played as she leaves in the game, its rush begins as hers
does in the video. LENGTH seconds of it, till it is back down to the formation's sound under it, faded out over
END_FADE; 3 ms faded in.

Under it is the formation's own sound (H's, which the game plays too: the video starts 4.86 s into H, found by matching
its loudness over time against isosang_h.ogg's, correlation 0.71 away from the moves). Its level is the game's: the cut
is scaled by GAIN_DB, how much quieter isosang_h.ogg is than the same sound in this video (the median over the video
away from the moves), so the move is as loud against H's sound as in the user's video. Nothing else is changed: mono
(the two channels averaged) at 48 kHz.
"""
import subprocess
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'stand'))
from cut_rush import RATE, FADE_IN, write  # noqa: E402

SOUNDS = Path(__file__).resolve().parents[2] / 'src/main/resources/assets/celestialfall/sounds'
# where she leaves the sword in the video's picture (its third move, real seconds), and how long after its picture the
# recording's sound comes
START = 17.63
AV_LAG = .35
# how long the cut is, and the fade at its end (it is back down to the formation's sound by then)
LENGTH = 1.0
END_FADE = .15
# the game's level of the formation's sound against this video's (isosang_h.ogg, cut_h_sounds.py)
GAIN_DB = -1.9


def decode(path):
    """The video's sound, mono (its two channels averaged) at RATE."""
    raw = subprocess.run(['ffmpeg', '-v', 'error', '-i', str(path), '-vn', '-ac', '2', '-ar', str(RATE), '-f', 'f32le', '-'],
                         check=True, capture_output=True).stdout
    return np.frombuffer(raw, dtype=np.float32).reshape(-1, 2).astype(np.float64).mean(axis=1)


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
    w = int(.005 * RATE)
    env = 20 * np.log10(np.sqrt((y[:len(y) // w * w].reshape(-1, w) ** 2).mean(axis=1)) + 1e-12)
    rise = int(np.nonzero(env > np.median(env[:6]) + 10)[0][0])
    out.mkdir(parents=True, exist_ok=True)
    write(y, out / 'isosang_z.ogg')
    print(f'isosang_z.ogg: {len(y) / RATE:.3f} s from {START + AV_LAG:.3f} s; its rush begins {rise * .005:.3f} s in and '
          f'is loudest {np.argmax(env) * .005:.3f} s in; peak {20 * np.log10(np.abs(y).max()):.1f} dBFS')


if __name__ == '__main__':
    main()
