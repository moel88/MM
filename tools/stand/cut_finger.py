"""Star Platinum's Star Finger (T, 2.60.0): its sound, from the recording the user sent ("스타핑거 소리 mp4로 보냈음").

usage: python3 -I tools/stand/cut_finger.py RECORDING.mp4 [OUT_DIR]

The recording swells from nothing into a rush of air and cracks sharply about 0.4 s in: that crack is where the
fingers strike. The file is cut from LEAD_IN seconds before it, so that played StarPlatinumRules.CONTACT[FINGER] - 8
ticks into the Star Finger (client/stand/StandAttack.FINGER_LEAD), the crack comes on the frame the fingers strike;
the first few milliseconds of its faint lead-in go with that (the script says how faint). The crack is the first 5 ms
of the recording's highs (over 6 kHz) to come within ONSET_DB of their loudest 5 ms. Nothing else is changed: mono
(the two channels averaged) at 48 kHz, 3 ms faded in and 10 ms out where it has died away (60 dB under its loudest,
and TAIL after), and scaled by one gain, the one that puts its loudest sample 1 dB under full scale, as the user's
other recordings are (tools/stand/cut_rush.py).
"""
import sys
from pathlib import Path

import numpy as np
from scipy import signal

sys.path.insert(0, str(Path(__file__).resolve().parent))
from cut_rush import RATE, TAIL, PEAK_DB, decode, ends, fade, write  # noqa: E402

# the fingers strike this long into the file (eight ticks: the sound starts CONTACT[FINGER] - 8 ticks into the Star Finger)
LEAD_IN = 0.4
ONSET_DB = 6.0


def crack(x):
    """The first sample of the first 5 ms window of x's highs within ONSET_DB of their loudest window."""
    highs = signal.sosfilt(signal.butter(4, 6000, 'hp', fs=RATE, output='sos'), x)
    w = int(.005 * RATE)
    rms = np.array([np.sqrt(np.mean(highs[i:i + w] ** 2)) for i in range(0, len(highs) - w, w)])
    db = 20 * np.log10(rms + 1e-12)
    return int(np.argmax(db >= db.max() - ONSET_DB)) * w


def main():
    src = Path(sys.argv[1])
    out = Path(sys.argv[2]) if len(sys.argv) > 2 else Path(__file__).resolve().parents[2] / 'src/main/resources/assets/celestialfall/sounds'
    x = decode(src)
    first, last = ends(x)
    at = crack(x)
    start = at - int(LEAD_IN * RATE)
    if start < 0:
        sys.exit(f'the crack is {at / RATE:.3f} s in: less than LEAD_IN')
    end = min(len(x), last + int(TAIL * RATE))
    g = 10 ** (PEAK_DB / 20) / np.abs(x[start:end]).max()
    part = fade(x[start:end] * g)
    out.mkdir(parents=True, exist_ok=True)
    write(part, out / 'star_platinum_finger.ogg')
    lost = np.abs(x[:start]).max() / np.abs(x).max() if start > 0 else 0
    print(f'star_platinum_finger.ogg: {len(part) / RATE:.3f} s from {start / RATE:.3f} s of the recording (its sound starts at '
          f'{first / RATE:.3f} s, the crack at {at / RATE:.3f} s); gain {20 * np.log10(g):+.2f} dB, peak '
          f'{20 * np.log10(np.abs(part).max()):.1f} dBFS; the loudest of what was cut before it {20 * np.log10(lost + 1e-12):.1f} dB under the loudest')


if __name__ == '__main__':
    main()
