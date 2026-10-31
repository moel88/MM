"""Lee So-sang's basic attack (2.77.2): the four hits' sounds, from the user's videos of each hit on its own with the
background music taken out ("1타 2타 3타 4타 연결 안되게도 찍었고 배경음악 뺀 효과음과 음성도 넣었어", "효과음은 그대로
쓰면 될듯"): the sword's sounds and the voice, as they are.

usage: python3 -I tools/isosang/cut_m1_sounds.py HIT1.mp4 HIT2.mp4 HIT3.mp4 HIT4.mp4 [OUT_DIR]

Each hit's sound starts with the hit on every client (client/isosang/IsosangClient, the swing's clock starts there too),
so each file starts where its hit starts in its video: START[n] seconds into its picture (before the video's first frame
for the hits whose start was cut off: the third's draw and lunge, the fourth's turn and twirl; the gap is silence), and
the recordings' sound comes AV_LAG seconds after their picture (measured on the fourth's impact, the first's swing and
the second's turn: 0.35 s), so the cut is taken that much later. Then the hit's blows (isosang/IsosangRules.BLOWS, taken
from the same pictures) land where the recording's do. Nothing else is changed: mono (the two channels averaged) at
48 kHz, 3 ms faded in, the end faded out over 10 ms where it has died away (60 dB under its loudest, and TAIL after) or
over END_FADE if the video ends first, and all four scaled by one gain, the one that puts the loudest sample of the four
1 dB under full scale (their balance as recorded).
"""
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'stand'))
from cut_rush import RATE, TAIL, PEAK_DB, FADE_IN, FADE_OUT, decode, write  # noqa: E402

# where each hit starts in its video's picture (seconds; negative: before its first frame; 2.79.2: the fourth's slash is
# thrown at 0.5 s, not 0.7, after the user's video of the whole combo, so it starts 0.2 s later in its video)
START = {1: .583, 2: -.05, 3: -.32, 4: -.33}
# the recordings' sound comes this long after their picture
AV_LAG = .35
# the fade where a video ends before its sound has died away
END_FADE = .08


def cut(x, start):
    """x from `start` seconds (silence before the recording) to where it dies away or ends; and whether it ended first."""
    floor = np.abs(x).max() * 10 ** (-60 / 20)
    loud = np.nonzero(np.abs(x) > floor)[0]
    last = loud[-1] if len(loud) else len(x) - 1
    end = min(len(x), last + int(TAIL * RATE))
    at = int(round(start * RATE))
    part = np.concatenate([np.zeros(max(0, -at)), x[max(0, at):end]])
    return part, end == len(x)


def shape(x, cut_short):
    x = x.copy()
    n_in, n_out = int(FADE_IN * RATE), int((END_FADE if cut_short else FADE_OUT) * RATE)
    x[:n_in] *= np.linspace(0, 1, n_in)
    x[-n_out:] *= np.linspace(1, 0, n_out)
    return x


def main():
    if len(sys.argv) < 5:
        sys.exit(__doc__)
    out = Path(sys.argv[5]) if len(sys.argv) > 5 else Path(__file__).resolve().parents[2] / 'src/main/resources/assets/celestialfall/sounds'
    parts = {}
    for n in range(1, 5):
        x = decode(Path(sys.argv[n]))
        parts[n] = cut(x, START[n] + AV_LAG)
    g = 10 ** (PEAK_DB / 20) / max(np.abs(p).max() for p, _ in parts.values())
    out.mkdir(parents=True, exist_ok=True)
    for n, (p, short) in parts.items():
        y = shape(p * g, short)
        write(y, out / f'isosang_m1_{n}.ogg')
        lead = max(0.0, -(START[n] + AV_LAG))
        print(f'isosang_m1_{n}.ogg: {len(y) / RATE:.3f} s from {START[n] + AV_LAG:+.3f} s of its recording'
              f'{f" ({lead:.3f} s of silence first)" if lead else ""}; peak {20 * np.log10(np.abs(y).max() + 1e-12):.1f} dBFS'
              f'{"; the video ends before it dies away" if short else ""}')
    print(f'one gain for all four: {20 * np.log10(g):+.2f} dB')


if __name__ == '__main__':
    main()
