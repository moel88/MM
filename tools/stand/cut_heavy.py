"""Star Platinum's heavy punch (R, 2.59.0): its sound, from the two recordings the user sent ("이번에는 R 강펀치 맞으면
날아가고 가드 브레이커야 mp4로 소리 보내줄게"): "ORA", the voice alone, for a heavy punch that strikes no one, and
"ORA (SFX)", the same voice with the blow on it, for one that strikes someone.

usage: python3 -I tools/stand/cut_heavy.py ORA.mp4 ORA_SFX.mp4 [OUT_DIR]

As the rush's (tools/stand/cut_rush.py): the voice is the same in both (the one with the blow has it half a second
later and 5.9 dB louder), so the blow is what the second has over the first. The game plays two sounds together
(client/stand/StandAttack): the voice (star_platinum_heavy.ogg, the first recording placed and scaled as it is in the
second) and the blow (star_platinum_heavy_hit.ogg: the second recording less the voice), the blow as loud as the punch
struck someone. Striking, the two together are the second recording; striking no one, the voice alone.

The blow starts with a sharp crack 56 ms into the voice: that is where the punch lands. Both files are cut from
LEAD_IN seconds before it, so that played StarPlatinumRules.CONTACT[HEAVY] - 2 ticks into the punch, the crack comes
on the frame the fist lands. Nothing else is changed: mono (each recording's two channels averaged) at 48 kHz, 3 ms
faded in and 10 ms out (the blow, cut off in the recording while it still rings, 60 ms out), and scaled by one gain,
the one that puts the loudest sample of the second recording 1 dB under full scale. The script checks that the two
written files add up to the second recording again (Vorbis aside) and that no voice is left in the blow.
"""
import sys
from pathlib import Path

import numpy as np
from scipy import signal

sys.path.insert(0, str(Path(__file__).resolve().parent))
from cut_rush import RATE, TAIL, FADE_IN, PEAK_DB, decode, place, ends, write  # noqa: E402

# the punch lands this long into both files (two ticks: the sound starts CONTACT[HEAVY] - 2 ticks into the punch)
LEAD_IN = 0.1
# the blow's crack: the first 5 ms whose loudness comes within this many dB of the blow's loudest 5 ms
ONSET_DB = 6.0
FADE_OUT, FADE_OUT_BLOW = .010, .060


def onset(x):
    """The first sample of the first 5 ms window of x within ONSET_DB of its loudest window."""
    w = int(.005 * RATE)
    rms = np.array([np.sqrt(np.mean(x[i:i + w] ** 2)) for i in range(0, len(x) - w, w)])
    db = 20 * np.log10(rms + 1e-12)
    return int(np.argmax(db >= db.max() - ONSET_DB)) * w


def fade(x, out):
    fi, fo = int(FADE_IN * RATE), int(out * RATE)
    x = x.copy()
    x[:fi] *= np.linspace(0, 1, fi, endpoint=False)
    x[-fo:] *= np.linspace(1, 0, fo)
    return x


def main():
    plain_src, hit_src = Path(sys.argv[1]), Path(sys.argv[2])
    out = Path(sys.argv[3]) if len(sys.argv) > 3 else Path(__file__).resolve().parents[2] / 'src/main/resources/assets/celestialfall/sounds'
    plain, both = decode(plain_src), decode(hit_src)
    lag, gain, voice = place(plain, both)
    blow = both - voice
    crack = onset(blow)
    start = crack - int(LEAD_IN * RATE)
    voice_start = ends(voice)[0]
    if start > voice_start:
        sys.exit(f'the voice starts {(crack - voice_start) / RATE:.3f} s before the blow: more than LEAD_IN')
    voice_end = min(len(voice), ends(voice)[1] + int(TAIL * RATE))
    blow_end = len(blow)
    g = 10 ** (PEAK_DB / 20) / np.abs(both[start:]).max()
    parts = {'star_platinum_heavy.ogg': fade(voice[start:voice_end] * g, FADE_OUT),
             'star_platinum_heavy_hit.ogg': fade(blow[start:blow_end] * g, FADE_OUT_BLOW)}
    out.mkdir(parents=True, exist_ok=True)
    for name, x in parts.items():
        write(x, out / name)
        print(f'{name}: {len(x) / RATE:.3f} s, peak {20 * np.log10(np.abs(x).max()):.1f} dBFS')
    print(f'the voice sits {lag / RATE * 1000:+.1f} ms into the recording with the blow, at {20 * np.log10(gain):+.2f} dB; '
          f'its voice starts {(voice_start - start) / RATE * 1000:.0f} ms into the files, the blow {LEAD_IN * 1000:.0f} ms in '
          f'({(crack - voice_start) / RATE * 1000:.0f} ms after the voice); gain {20 * np.log10(g):+.2f} dB')
    # checks: the blow holds no voice, and the two files add up to the recording with the blow again
    band = signal.butter(4, [1500, 5000], 'bp', fs=RATE, output='sos')
    v, b = signal.sosfilt(band, voice), signal.sosfilt(band, blow)
    print(f'voice left in the blow (1.5-5 kHz): {20 * np.log10(abs(np.dot(b, v) / np.dot(v, v)) + 1e-12):.1f} dB')
    written = [decode(out / name, 1) for name in parts]
    n = min(len(written[1]), len(both) - start)
    total = np.pad(written[0], (0, max(0, n - len(written[0]))))[:n] + written[1][:n]
    want = both[start:start + n] * g
    body = slice(0, n - int(FADE_OUT_BLOW * RATE))
    err = total[body] - want[body]
    print(f'the two written added up against the recording with the blow: error {10 * np.log10(np.mean(err ** 2) / np.mean(want[body] ** 2)):.1f} dB')


if __name__ == '__main__':
    main()
