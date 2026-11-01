"""Lee So-sang's H (2.82.0): the sword formation's two sounds, from the user's video of H (c213bfd7-lv_0_20261101094323.mp4,
60 fps, at its real speed), the game's sound as it is.

usage: python3 -I tools/isosang/cut_h_sounds.py H_VIDEO.mp4 [OUT_DIR]

H's clock starts 0.40 s into the video (the crouch is 0.05 s into H; the button shows at 0.2 s), and the recording's
sound comes AV_LAG seconds after its picture (0.35 s, as in the user's other videos), so:
- isosang_h.ogg, played as H starts: the sound from 0.75 s (H's start) through the leap, the swords' rain and the hop
  onto the middle sword, faded out from CAST_FADE0 to CAST_END (where only the place's own sound, its waterfall, is left);
- isosang_h_end.ogg, played as the formation ends (IsosangRules.H_END, 40.32 s into H: the swords flare in the video at
  40.72 s): the sound from 41.07 s to the video's end, faded out over its last END_FADE.
Nothing else is changed: mono (the two channels averaged) at 48 kHz, both scaled by one gain so the louder one's loudest
sample is 1 dB under full scale (their balance as recorded), 3 ms faded in.
"""
import subprocess
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'stand'))
from cut_rush import RATE, PEAK_DB, FADE_IN, write  # noqa: E402

SOUNDS = Path(__file__).resolve().parents[2] / 'src/main/resources/assets/celestialfall/sounds'
# where H starts in the video, how long after its picture the recording's sound comes
START = .40
AV_LAG = .35
# the cast's sound: faded out from CAST_FADE0 to CAST_END (seconds of H)
CAST_FADE0, CAST_END = 3.2, 4.2
# the end's sound: from H_END (seconds of H) to the video's end, its last END_FADE faded out
H_END = 40.32
END_FADE = .4


def decode(path):
    raw = subprocess.run(['ffmpeg', '-v', 'error', '-i', str(path), '-vn', '-ac', '2', '-ar', str(RATE), '-f', 'f32le', '-'],
                         check=True, capture_output=True).stdout
    return np.frombuffer(raw, dtype=np.float32).reshape(-1, 2).astype(np.float64).mean(axis=1)


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    out = Path(sys.argv[2]) if len(sys.argv) > 2 else SOUNDS
    x = decode(Path(sys.argv[1]))
    s0 = int(round((START + AV_LAG) * RATE))
    cast = x[s0:s0 + int(round(CAST_END * RATE))].copy()
    f0 = int(round(CAST_FADE0 * RATE))
    cast[f0:] *= np.linspace(1, 0, len(cast) - f0) ** 2
    e0 = int(round((START + AV_LAG + H_END) * RATE))
    end = x[e0:].copy()
    n_out = int(END_FADE * RATE)
    end[-n_out:] *= np.linspace(1, 0, n_out)
    gain = 10 ** (PEAK_DB / 20) / max(np.abs(cast).max(), np.abs(end).max())
    out.mkdir(parents=True, exist_ok=True)
    n_in = int(FADE_IN * RATE)
    for name, y in (('isosang_h.ogg', cast), ('isosang_h_end.ogg', end)):
        y = y * gain
        y[:n_in] *= np.linspace(0, 1, n_in)
        write(y, out / name)
        print(f'{name}: {len(y) / RATE:.3f} s, peak {20 * np.log10(np.abs(y).max()):.1f} dBFS')


if __name__ == '__main__':
    main()
