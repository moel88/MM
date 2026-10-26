"""Star Platinum coming out and going back (Q, 2.72.0): its two sounds, from the two recordings the user sent ("하나는
스타플래티나 q로 꺼낼때 나는 소리고 하나는 q로 다시 넣을때 나는 소리야", the first titled "Stand Summon (Voiceline)", the
second "Stand Disappear").

usage: python3 -I tools/stand/cut_summon.py SUMMON.mp4 DISAPPEAR.mp4 [OUT_DIR]

The first is the call, "Star Platinum!" (its "s" from about 0.50 s of the recording, the voice over by about 1.45 s, its
echo dying away by 1.90 s), over a rush of air. The second is the sound of the stand going (from about 0.40 s): a
shimmering rush that swells to its loudest about 0.6 s in and holds until the recording ends (about 1.59 s). Each is
played as the stand starts to come out or go back (client/stand/StandCall), so each is cut where it starts: from LEAD
before its first sound (60 dB under its loudest; the silence before it left out) to where it has died away (as far
under, and TAIL after; the second ends as the recording does).

Nothing else is changed: mono (the two channels averaged) at 48 kHz, 3 ms faded in and 10 ms out, both scaled by one
gain, the one that puts the louder of the two 1 dB under full scale, as the user's other recordings are
(tools/stand/cut_rush.py), so that the two keep the balance the user gave them.
"""
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
from cut_rush import RATE, TAIL, PEAK_DB, decode, ends, fade, write  # noqa: E402

NAMES = ('star_platinum_summon', 'star_platinum_dismiss')
# kept before each sound's start (seconds)
LEAD = .005


def sound(x):
    """From LEAD before where it starts (60 dB under its loudest: before it only the codec's faint smear of its onset,
    some 75 dB under it) to where it has died away (and TAIL after)."""
    first, last = ends(x)
    first = max(0, first - int(LEAD * RATE))
    return x[first:min(len(x), last + int(TAIL * RATE))], first


def main():
    sources = [Path(a) for a in sys.argv[1:3]]
    if len(sources) != 2:
        sys.exit(__doc__)
    out = Path(sys.argv[3]) if len(sys.argv) > 3 else Path(__file__).resolve().parents[2] / 'src/main/resources/assets/celestialfall/sounds'
    parts = [sound(decode(p)) for p in sources]
    g = 10 ** (PEAK_DB / 20) / max(np.abs(x).max() for x, _ in parts)
    out.mkdir(parents=True, exist_ok=True)
    for name, (x, first) in zip(NAMES, parts):
        write(fade(x * g), out / f'{name}.ogg')
        print(f'{name}.ogg: {len(x) / RATE:.3f} s from {first / RATE:.3f} s of the recording, peak '
              f'{20 * np.log10(np.abs(x * g).max()):.2f} dBFS')
    print(f'gain {20 * np.log10(g):+.2f} dB')


if __name__ == '__main__':
    main()
