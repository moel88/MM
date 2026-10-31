"""Lee So-sang's R (2.81.0): the giant sword's sweep's sound, from the user's video of R from above at a tenth of the
speed (1a6ba697-lv_0_20261101045206.mp4), the game's sound (the sword showing, its sweep) as it is.

usage: python3 -I tools/isosang/cut_r_sound.py R_FROM_ABOVE.mp4 [OUT_DIR]

The video was slowed to a tenth and its sound with it (every pitch a tenth: all of it under 2.2 kHz), so its sound is
played back ten times as fast first (its samples read at ten times their rate), which gives the game's sound again, up to
about 10 kHz (the rest the video's compression took). The recordings' sound comes AV_LAG seconds after their picture
(0.35 s, as in the user's videos of the basic attack; here the chime as the sword forms and the rush of the sweep land
that long after them), so the cut is taken that much later than where R starts in the picture (START, real seconds into
the video: where the sword hand begins to move, 0.07 s before the other video's first frame). Nothing else is changed:
mono (the two channels averaged) at 48 kHz, scaled so its loudest sample is 1 dB under full scale (as the basic attack's
files are), 3 ms faded in, and faded out over END_FADE where the video ends before the sound has died away.
"""
import subprocess
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'stand'))
from cut_rush import RATE, PEAK_DB, FADE_IN, write  # noqa: E402

SOUNDS = Path(__file__).resolve().parents[2] / 'src/main/resources/assets/celestialfall/sounds'
# the video's speed (a tenth): its sound is played this many times as fast
SLOW = 10
# where R starts in the video (real seconds), and how long after its picture the recording's sound comes
START = .04
AV_LAG = .35
# the fade where the video ends before the sound has died away
END_FADE = .08


def decode(path):
    """The video's sound at its real speed: read SLOW times as fast (its pitch back), mono at RATE."""
    rate = int(subprocess.run(['ffprobe', '-v', 'error', '-select_streams', 'a:0', '-show_entries', 'stream=sample_rate',
                               '-of', 'csv=p=0', str(path)], check=True, capture_output=True, text=True).stdout.strip())
    raw = subprocess.run(['ffmpeg', '-v', 'error', '-i', str(path), '-vn', '-af', f'asetrate={rate * SLOW},aresample={RATE}',
                          '-ac', '2', '-f', 'f32le', '-'], check=True, capture_output=True).stdout
    return np.frombuffer(raw, dtype=np.float32).reshape(-1, 2).astype(np.float64).mean(axis=1)


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    out = Path(sys.argv[2]) if len(sys.argv) > 2 else SOUNDS
    x = decode(Path(sys.argv[1]))
    at = int(round((START + AV_LAG) * RATE))
    y = x[at:].copy()
    y *= 10 ** (PEAK_DB / 20) / np.abs(y).max()
    n_in, n_out = int(FADE_IN * RATE), int(END_FADE * RATE)
    y[:n_in] *= np.linspace(0, 1, n_in)
    y[-n_out:] *= np.linspace(1, 0, n_out)
    loud = np.nonzero(np.abs(y) > np.abs(y).max() * 10 ** (-40 / 20))[0]
    out.mkdir(parents=True, exist_ok=True)
    write(y, out / 'isosang_r.ogg')
    print(f'isosang_r.ogg: {len(y) / RATE:.3f} s from {(START + AV_LAG):.3f} s of the sound at its real speed; '
          f'it begins {loud[0] / RATE:.3f} s in; peak {20 * np.log10(np.abs(y).max()):.1f} dBFS')


if __name__ == '__main__':
    main()
