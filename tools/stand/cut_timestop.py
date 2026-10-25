"""Star Platinum's time stop (H, 2.67.0): its two sounds, from the two recordings the user sent ("소리 보내줄게 하나는
시전할때 소리 하나는 끝날때 소리").

usage: python3 -I tools/stand/cut_timestop.py CAST.mp3 END.mp3 [OUT_DIR]

The first is the cast: "Star Platinum" (from 0.06 s), digital silence (1.10 to 1.17 s), "The World!" (from 1.20 s), and
then, as the shout ends, the sound of time stopping: a low boom and a rush (from STOP, about 1.95 s) that drops away
sharply (SETTLE, about 2.87 s) into a low hum, which dies by 3.94 s. Time stops where that sound starts: the file is
cut from a whole number of ticks before it (STOP_TICKS: 38 ticks, the cut falling in the near silence before the "S"
of "Star"), so that played a tick after H is pressed (StarPlatinumRules.TS_SOUND) the boom comes on the tick time
stops (StarPlatinumRules.TS_WIND). The time stop's wave spreads out over the rush and the world settles as it drops
(StarPlatinumRules.TS_SETTLE, the time from STOP to SETTLE).

The second is the end: "toki wa ugoki dasu" (time begins to move), its eight syllables' consonants sharp in the highs,
which ends sharply (RESUME, about 1.27 s, then a faint tail): time moves again where it ends. The game plays it
StarPlatinumRules.TS_RESUME_LEAD ticks before time moves again, the whole ticks nearest RESUME (the script says how
near).

Nothing else is changed: mono (the two channels averaged) at 48 kHz, 3 ms faded in and 10 ms out where each has died
away (60 dB under its loudest, and TAIL after), both scaled by one gain, the one that puts the louder of the two 1 dB
under full scale, as the user's other recordings are (tools/stand/cut_rush.py), so that the two keep the balance the
user gave them.
"""
import sys
from pathlib import Path

import numpy as np
from scipy import signal

sys.path.insert(0, str(Path(__file__).resolve().parent))
from cut_rush import RATE, TAIL, PEAK_DB, decode, ends, fade, write  # noqa: E402

# whole ticks from the cut to where time stops (the boom): the cast sound starts a tick after H, time stops 39 ticks after it
STOP_TICKS = 38
WINDOW = .005


def envelope(x, kind=None, cutoff=None):
    """5 ms RMS levels (dB) of x, or of its lows under / highs over cutoff Hz."""
    if kind:
        x = signal.sosfiltfilt(signal.butter(4, cutoff, kind, fs=RATE, output='sos'), x)
    w = int(WINDOW * RATE)
    return 20 * np.log10(np.array([np.sqrt(np.mean(x[i:i + w] ** 2)) for i in range(0, len(x) - w, w)]) + 1e-12)


def silence(x, least=.05):
    """The first run of digital silence at least `least` seconds long: (first, last) samples."""
    quiet = np.abs(x) < 1e-6
    i, n = 0, len(x)
    while i < n:
        if not quiet[i]:
            i += 1
            continue
        j = i
        while j < n and quiet[j]:
            j += 1
        if j - i >= least * RATE:
            return i, j
        i = j
    return None


def stop(x):
    """Where the sound of time stopping starts: past "The World!" (half a second after the silence before it), the
    first 5 ms of the lows (under 250 Hz) within 12 dB of their loudest after the silence (the shout's own lows stay
    some 14 dB under the boom)."""
    gap = silence(x)
    if gap is None:
        sys.exit('no silence between "Star Platinum" and "The World"')
    lows = envelope(x, 'lp', 250)
    a = int((gap[1] / RATE + .5) / WINDOW)
    after = lows[a:]
    return int((a + int(np.argmax(after >= after.max() - 12))) * WINDOW * RATE), gap


def settle(x, at):
    """Where the rush drops away (from 0.5 s after `at`): the first 5 ms of the highs (over 3 kHz) 10 dB or more under
    their mean over the 50 ms before it."""
    highs = envelope(x, 'hp', 3000)
    k0 = int((at / RATE + .5) / WINDOW)
    for k in range(k0, len(highs)):
        if highs[k] <= np.mean(highs[k - 10:k]) - 10:
            return int(k * WINDOW * RATE)
    sys.exit('the rush never drops away')


def resume(x):
    """Where "toki wa ugoki dasu" ends: past the last 5 ms within 20 dB of the loudest that the sound comes back
    above 20 dB under it after (the faint tail never does)."""
    db = envelope(x)
    loud = np.flatnonzero(db >= db.max() - 20)
    return int((loud[-1] + 1) * WINDOW * RATE)


def main():
    cast_src, end_src = Path(sys.argv[1]), Path(sys.argv[2])
    out = Path(sys.argv[3]) if len(sys.argv) > 3 else Path(__file__).resolve().parents[2] / 'src/main/resources/assets/celestialfall/sounds'
    cast, end = decode(cast_src), decode(end_src)
    at, gap = stop(cast)
    start = at - int(round(STOP_TICKS / 20 * RATE))
    first, last = ends(cast)
    if start < 0 or start > first:
        sys.exit(f'the boom is {at / RATE:.3f} s in and the voice starts {first / RATE:.3f} s in: a cut {STOP_TICKS} ticks before '
                 f'the boom ({start / RATE:.3f} s) would cut into it')
    drop = settle(cast, at)
    cast_part = cast[start:min(len(cast), last + int(TAIL * RATE))]
    _, end_last = ends(end)
    end_part = end[:min(len(end), end_last + int(TAIL * RATE))]
    moves = resume(end)
    g = 10 ** (PEAK_DB / 20) / max(np.abs(cast_part).max(), np.abs(end_part).max())
    out.mkdir(parents=True, exist_ok=True)
    write(fade(cast_part * g), out / 'star_platinum_timestop.ogg')
    write(fade(end_part * g), out / 'star_platinum_timestop_end.ogg')
    lost = np.abs(cast[:start]).max() / np.abs(cast).max() if start > 0 else 0
    print(f'star_platinum_timestop.ogg: {len(cast_part) / RATE:.3f} s from {start / RATE:.3f} s of the recording (the silence '
          f'{gap[0] / RATE:.3f} to {gap[1] / RATE:.3f} s; the loudest of what was cut before it {20 * np.log10(lost + 1e-12):.1f} dB under '
          f'the loudest); time stops (the boom) {(at - start) / RATE:.3f} s into it, {at / RATE:.3f} s into the recording: '
          f'{STOP_TICKS} ticks; it settles (the drop) {(drop - at) / RATE:.3f} s after that, at {drop / RATE:.3f} s')
    print(f'star_platinum_timestop_end.ogg: {len(end_part) / RATE:.3f} s; "toki wa ugoki dasu" ends {moves / RATE:.3f} s in: '
          f'{moves / RATE * 20:.2f} ticks (played {round(moves / RATE * 20)} ticks before time moves again: '
          f'{(round(moves / RATE * 20) / 20 - moves / RATE) * 1000:+.0f} ms)')
    print(f'gain {20 * np.log10(g):+.2f} dB, peaks {20 * np.log10(np.abs(cast_part).max() * g):.1f} and '
          f'{20 * np.log10(np.abs(end_part).max() * g):.1f} dBFS')


if __name__ == '__main__':
    main()
