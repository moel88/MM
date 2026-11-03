"""Lee So-sang's G (2.83.0): its two sounds, from the user's two videos of it (e3953f00-lv_0_20261103111436.mp4 and
b266d9b5-lv_0_20261103111453.mp4, 60 fps; the 30 fps ones they sent first have the same sound), the game's sound as it is.

usage: python3 -I tools/isosang/cut_g_sounds.py G1.mp4 G2.mp4 [OUT_DIR]

The two recordings' music and effects are the same, only the voice differs (its two lines in the first three seconds);
each begins out of digital silence 0.6 s into its video, as the screen goes dark, which is G's start: each is cut from
there (FROM, where it first rises above silence) to where it is silent again, a few ms of fade in; isosang_g_1.ogg is the
first video's (her face straight on), isosang_g_2.ogg the second's. Nothing else is changed: mono (the two channels
averaged) at 48 kHz, both scaled by one gain so the louder one's loudest sample is 1 dB under full scale (their balance as
recorded), 3 ms faded in, 10 ms out.
"""
import subprocess
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'stand'))
from cut_rush import RATE, PEAK_DB, FADE_IN, write  # noqa: E402

SOUNDS = Path(__file__).resolve().parents[2] / 'src/main/resources/assets/celestialfall/sounds'
FADE_OUT = .01


def decode(path):
    raw = subprocess.run(['ffmpeg', '-v', 'error', '-i', str(path), '-vn', '-ac', '2', '-ar', str(RATE), '-f', 'f32le', '-'],
                         check=True, capture_output=True).stdout
    return np.frombuffer(raw, dtype=np.float32).reshape(-1, 2).astype(np.float64).mean(axis=1)


def span(x):
    """Where the sound rises out of silence and where it falls back into it (samples)."""
    loud = np.nonzero(np.abs(x) > 1e-4)[0]
    return int(loud[0]), int(loud[-1]) + 1


def main():
    if len(sys.argv) < 3:
        sys.exit(__doc__)
    out = Path(sys.argv[3]) if len(sys.argv) > 3 else SOUNDS
    cuts = []
    for path in sys.argv[1:3]:
        x = decode(Path(path))
        a, b = span(x)
        cuts.append(x[a:b].copy())
        print(f'{Path(path).name}: from {a / RATE:.3f} s to {b / RATE:.3f} s')
    gain = 10 ** (PEAK_DB / 20) / max(np.abs(c).max() for c in cuts)
    out.mkdir(parents=True, exist_ok=True)
    n_in, n_out = int(FADE_IN * RATE), int(FADE_OUT * RATE)
    for i, c in enumerate(cuts):
        y = c * gain
        y[:n_in] *= np.linspace(0, 1, n_in)
        y[-n_out:] *= np.linspace(1, 0, n_out)
        name = f'isosang_g_{i + 1}.ogg'
        write(y, out / name)
        print(f'{name}: {len(y) / RATE:.3f} s, peak {20 * np.log10(np.abs(y).max()):.1f} dBFS')


if __name__ == '__main__':
    main()
