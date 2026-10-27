"""Star Platinum's beatdown (G, 2.64.0): its sound, from the recording the user sent ("소리대로 설명을 해줄게").

usage: python3 -I tools/stand/cut_beatdown.py RECORDING.mp4 [OUT_DIR]

The recording is two parts with digital silence between them: the stand's "ORA!" as G is pressed and its fist goes in
(0.0 to 0.6 s), and, if it struck someone, the cutscene: "yare yare daze" (from 1.15 s), "Star Platinum!" (2.6 to
3.5 s), the collar grabbed (3.80 s), the uppercut (4.36 s), the ORA rush (4.45 to 8.28 s), the big ORAs and the last
"Ooo-RAAA" whose blow sends them flying (10.80 s), and its long fall away (to 12.2 s). They are cut apart where the
silence is, and keep the recording's own timing: the game plays the first OPENER_TICK ticks into the beatdown and the
second CUTSCENE_TICK ticks in (StarPlatinumRules.BEAT_*), one tick on from the recording's second for the 20 ticks
of every second of it, so everything in it comes where it is in the recording, after the first. The second is cut
from a whole tick (CUTSCENE_AT, in the silence, a little before "yare yare") so that it starts on a tick.

Nothing else is changed: mono (the two channels averaged) at 48 kHz, 3 ms faded in and 10 ms out where each has died
away (60 dB under its loudest, and TAIL after), both scaled by one gain, the one that puts the loudest sample of the
two 1 dB under full scale, as the user's other recordings are (tools/stand/cut_rush.py), so that the two keep the
balance the user gave them. The script prints where the opener's "RA" is (its fist lands there,
StarPlatinumRules.CONTACT[BEATDOWN]) and the cracks and thumps the cutscene times its blows by.
"""
import sys
from pathlib import Path

import numpy as np
from scipy import signal

sys.path.insert(0, str(Path(__file__).resolve().parent))
from cut_rush import RATE, TAIL, PEAK_DB, decode, ends, fade, write  # noqa: E402

# where the second part is cut from (seconds into the recording): a whole tick, in the silence before "yare yare"
CUTSCENE_AT = 1.10
# the ticks into the beatdown each part plays from (StarPlatinumRules.BEAT_OPENER_SOUND, BEAT_SOUND)
OPENER_TICK = 1
CUTSCENE_TICK = OPENER_TICK + round(CUTSCENE_AT * 20)


def silences(x, least=0.2):
    """The runs of digital silence (exact zeros, or all but) at least `least` seconds long: (first, last) samples."""
    quiet = np.abs(x) < 1e-6
    out, i, n = [], 0, len(x)
    while i < n:
        if not quiet[i]:
            i += 1
            continue
        j = i
        while j < n and quiet[j]:
            j += 1
        if j - i >= least * RATE:
            out.append((i, j))
        i = j
    return out


def lpc(seg, order):
    """Linear prediction coefficients of seg (Levinson-Durbin)."""
    a = np.correlate(seg, seg, 'full')[len(seg) - 1:len(seg) + order]
    e, coef = a[0], np.zeros(order + 1)
    coef[0] = 1
    for i in range(1, order + 1):
        k = -(a[i] + np.dot(coef[1:i], a[i - 1:0:-1])) / e
        coef[1:i + 1] = coef[1:i + 1] + k * np.concatenate([coef[i - 1:0:-1], [1]])
        e *= 1 - k * k
    return coef


def second_formant(x, t):
    """The voice's second formant (Hz) in 30 ms from t seconds: the second resonance over 200 Hz of a 12-pole fit at 11025 Hz."""
    y = signal.resample_poly(x[int(t * RATE):int((t + .03) * RATE)], 147, 640)
    y = np.append(y[0], y[1:] - .63 * y[:-1]) * np.hamming(len(y))
    r = np.roots(lpc(y, 12))
    r = r[np.imag(r) > 0]
    f = np.angle(r) * 11025 / (2 * np.pi)
    bw = -11025 / np.pi * np.log(np.abs(r))
    f = sorted(v for v, b in zip(f, bw) if v > 200 and b < 400)
    return f[1] if len(f) > 1 else 0


def ra(x, end):
    """The opener's "RA": where its voice turns from the O (its second formant near 1 kHz) to the A (near 1.3 kHz),
    the first of three 10 ms steps whose second formant is between 1150 and 1800 Hz after the O has held it under
    for 30 ms (a fit that misses the second formant in a breathy moment gives the third, near 2.4 kHz: not counted)."""
    f2 = [second_formant(x, k * .01) for k in range(int(min(end / RATE, .4) / .01))]
    under = 0
    for k in range(len(f2) - 2):
        if 0 < f2[k] < 1150:
            under += 1
        elif under >= 3 and all(1150 < f2[k + j] < 1800 for j in range(3)):
            return int(k * .01 * RATE)
    return 0


def marks(x, lo, hi, cutoff, kind):
    """The thumps (lows under cutoff Hz) or cracks (highs over it) between lo and hi (seconds): 10 ms peaks 6 dB over
    the median that stand 4 dB proud of their neighbours."""
    sos = signal.butter(4, cutoff, 'lp' if kind == 'low' else 'hp', fs=RATE, output='sos')
    y = signal.sosfiltfilt(sos, x)
    w = int(.01 * RATE)
    a, b = int(lo * RATE), int(hi * RATE)
    db = np.array([20 * np.log10(np.sqrt(np.mean(y[i:i + w] ** 2)) + 1e-12) for i in range(a, b - w, w)])
    med = np.median(db)
    peaks = signal.find_peaks(db, height=med + 6, distance=4, prominence=4)[0]
    return [(lo + k * .01, db[k] - med) for k in peaks]


def main():
    src = Path(sys.argv[1])
    out = Path(sys.argv[2]) if len(sys.argv) > 2 else Path(__file__).resolve().parents[2] / 'src/main/resources/assets/celestialfall/sounds'
    x = decode(src)
    gaps = silences(x)
    if not gaps:
        sys.exit('no silence between the opener and the cutscene')
    gap = gaps[0]
    cut = int(round(CUTSCENE_AT * RATE))
    if not gap[0] <= cut <= gap[1]:
        sys.exit(f'the silence is {gap[0] / RATE:.3f} to {gap[1] / RATE:.3f} s: CUTSCENE_AT {CUTSCENE_AT} is not in it')
    first, _ = ends(x[:gap[0]])
    if first > int(.005 * RATE):
        sys.exit(f'the opener starts {first / RATE:.3f} s in: the game plays it from the recording\'s start')
    opener_end = min(gap[0], ends(x[:gap[0]])[1] + int(TAIL * RATE))
    _, last = ends(x[cut:])
    cutscene_end = min(len(x), cut + last + int(TAIL * RATE))
    opener, cutscene = x[:opener_end], x[cut:cutscene_end]
    g = 10 ** (PEAK_DB / 20) / max(np.abs(opener).max(), np.abs(cutscene).max())
    out.mkdir(parents=True, exist_ok=True)
    write(fade(opener * g), out / 'star_platinum_beatdown_ora.ogg')
    write(fade(cutscene * g), out / 'star_platinum_beatdown.ogg')
    r = ra(x, gap[0])
    print(f'star_platinum_beatdown_ora.ogg: 0.000 to {opener_end / RATE:.3f} s of the recording ({opener_end / RATE:.3f} s), '
          f'played {OPENER_TICK} tick in; its RA at {r / RATE:.3f} s: tick {OPENER_TICK + r / RATE * 20:.2f}')
    print(f'star_platinum_beatdown.ogg: {cut / RATE:.3f} to {cutscene_end / RATE:.3f} s of the recording '
          f'({(cutscene_end - cut) / RATE:.3f} s), played {CUTSCENE_TICK} ticks in; the silence before it '
          f'{gap[0] / RATE:.3f} to {gap[1] / RATE:.3f} s')
    print(f'gain {20 * np.log10(g):+.2f} dB, peaks {20 * np.log10(np.abs(opener).max() * g):.1f} and '
          f'{20 * np.log10(np.abs(cutscene).max() * g):.1f} dBFS')
    for name, kind, cutoff in (('thumps', 'low', 150), ('cracks', 'high', 4000)):
        found = marks(x, 3.5, cutscene_end / RATE, cutoff, kind)
        print(f'{name}: ' + ' '.join(f'{t:.2f}s/tick {OPENER_TICK + t * 20:.1f}(+{d:.0f})' for t, d in found))


if __name__ == '__main__':
    main()
