"""Gold Experience's muda rush (E, 2.147.0): its sound, from the recording the user sent ("E스킬 무다러시 부터 만들자
효과음은 이거야", titled "gold experience muda rush sound").

usage: python3 -I tools/stand/cut_muda.py MUDA.mp3 [OUT_DIR]

The recording is the stand's voice, "muda muda muda ...", with the blows of its fists in it (noise across the whole
band all through, some twenty-five onsets a second), from about 0.11 s of the file to about 3.25 s and dying away by
3.5 s. It is played as the rush starts (client/stand/StandRush, a tick in), so it is cut where it starts: from LEAD
before its first sound (60 dB under its loudest; the silence before it left out) to where it has died away (and TAIL
after). The rush is as long as it (GoldExperienceRules: its last blow 3.35 s in).

Nothing else is changed: mono (the two channels averaged) at 48 kHz, 3 ms faded in and 10 ms out, its peak put 1 dB
under full scale, as the user's other recordings are (tools/stand/cut_rush.py).
"""
import subprocess
import sys
from pathlib import Path

import numpy as np

# (as tools/stand/cut_rush.py cuts the user's other recordings)
RATE = 48000
TAIL = 0.05
PEAK_DB = -1.0
FADE_IN, FADE_OUT = 0.003, 0.010


def decode(path, channels=2):
    """The recording as one channel: its two averaged."""
    raw = subprocess.run(['ffmpeg', '-v', 'error', '-i', str(path), '-ac', str(channels), '-ar', str(RATE), '-f', 'f32le', '-'],
                         check=True, capture_output=True).stdout
    return np.frombuffer(raw, dtype=np.float32).reshape(-1, channels).astype(np.float64).mean(axis=1)


def ends(x, floor_db=-60.0):
    """The first and last sample louder than floor_db under the loudest."""
    loud = np.abs(x) > np.abs(x).max() * 10 ** (floor_db / 20)
    return int(np.argmax(loud)), len(x) - int(np.argmax(loud[::-1]))


def fade(x):
    fi, fo = int(FADE_IN * RATE), int(FADE_OUT * RATE)
    x = x.copy()
    x[:fi] *= np.linspace(0, 1, fi, endpoint=False)
    x[-fo:] *= np.linspace(1, 0, fo)
    return x


def write(x, path):
    subprocess.run(['ffmpeg', '-v', 'error', '-y', '-f', 'f32le', '-ar', str(RATE), '-ac', '1', '-i', '-',
                    '-c:a', 'libvorbis', '-q:a', '7', str(path)], input=x.astype(np.float32).tobytes(), check=True)


NAME = 'gold_experience_muda'
# kept before the sound's start (seconds)
LEAD = .005


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    out = Path(sys.argv[2]) if len(sys.argv) > 2 else Path(__file__).resolve().parents[2] / 'src/main/resources/assets/celestialfall/sounds'
    x = decode(Path(sys.argv[1]))
    first, last = ends(x)
    first = max(0, first - int(LEAD * RATE))
    x = x[first:min(len(x), last + int(TAIL * RATE))]
    g = 10 ** (PEAK_DB / 20) / np.abs(x).max()
    out.mkdir(parents=True, exist_ok=True)
    write(fade(x * g), out / f'{NAME}.ogg')
    print(f'{NAME}.ogg: {len(x) / RATE:.3f} s from {first / RATE:.3f} s of the recording, gain {20 * np.log10(g):+.2f} dB')


if __name__ == '__main__':
    main()
