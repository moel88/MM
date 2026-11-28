"""Gold Experience's overdrive punch (R) and its two healings (Y): their sounds, from the two videos the user sent
(2.153.0: "저 2개는 R 강펀치 효과음과 힐 효과음 힐 효과음은 2개인데 하나는 적 때릴때 하고 하나는 자힐").

usage: python3 -I tools/stand/cut_gold_sounds.py R_VIDEO HEAL_VIDEO [OUT_DIR]

R_VIDEO (0.97 s, a game's heavy punch, "Heavy Punch" coming up in its cooldown list) has one sound: the stand's shout,
"mu-DA!" (its first syllable from the file's start to 0.128 s, a stop, the second from 0.165 s, dying away by
0.654 s; digital silence after). It is loud and clipped all through as the user sent it: it is left so, only turned
down (to LOUD_R, a shout as loud as Star Platinum's heavy punch's voice, -10.9 LUFS). It is cut with silence before
it so that its second syllable comes DA_AT into the file: the client plays it on the tick that puts that syllable on
the frame the fist lands (client/stand/GoldPunch.SOUND_AT).

HEAL_VIDEO (4.13 s, captions "Life Creation 1" and "Life Creation 2") has two sounds with digital silence between:
the first (0.01 to 1.38 s) for the healing of another (Y tapped: the user's "적 때릴때", the first they named), the
second (2.03 to 3.37 s) for the user's own (Y held). Each is cut from 5 ms before it starts to where it has died away
(60 dB under its loudest), its two channels averaged; both are turned up by one gain (the first to LOUD_HEAL, X
Chara's healing's loudness, -18 LUFS: the two as loud as each other as the user sent them).

Nothing is synthesized and nothing else is changed: mono at 48 kHz, 3 ms faded in and 10 ms out.
"""
import subprocess
import sys
from pathlib import Path

import numpy as np

RATE = 48000
FADE_IN, FADE_OUT = 0.003, 0.010
LEAD = .005
# where the shout's second syllable comes in its file (seconds), and the loudness its file is set to (LUFS)
DA_AT = .2
LOUD_R = -10.9
# the first healing's loudness (LUFS): the second has the same gain
LOUD_HEAL = -18.0


def decode(path):
    """The recording as one channel: its two averaged."""
    raw = subprocess.run(['ffmpeg', '-v', 'error', '-i', str(path), '-ac', '2', '-ar', str(RATE), '-f', 'f32le', '-'],
                         check=True, capture_output=True).stdout
    return np.frombuffer(raw, dtype=np.float32).reshape(-1, 2).astype(np.float64).mean(axis=1)


def ends(x, floor_db=-60.0):
    """The first and last sample louder than floor_db under the loudest."""
    loud = np.abs(x) > np.abs(x).max() * 10 ** (floor_db / 20)
    return int(np.argmax(loud)), len(x) - int(np.argmax(loud[::-1]))


def pieces(x, gap=.2):
    """The stretches of the recording with digital silence (gap seconds or more) between them."""
    on = np.where(np.abs(x) > 1e-6)[0]
    breaks = np.where(np.diff(on) > int(gap * RATE))[0]
    starts = [on[0]] + [on[b + 1] for b in breaks]
    stops = [on[b] + 1 for b in breaks] + [on[-1] + 1]
    return list(zip(starts, stops))


def lufs(x):
    """Integrated loudness (ITU-R BS.1770, as ffmpeg's ebur128 measures it)."""
    out = subprocess.run(['ffmpeg', '-nostats', '-hide_banner', '-f', 'f32le', '-ar', str(RATE), '-ac', '1', '-i', '-',
                          '-af', 'ebur128=framelog=quiet', '-f', 'null', '-'],
                         input=x.astype(np.float32).tobytes(), capture_output=True, check=True).stderr.decode()
    tail = out[out.rfind('Integrated loudness'):]
    return float(tail.split('I:')[1].split('LUFS')[0])


def fade(x):
    fi, fo = int(FADE_IN * RATE), int(FADE_OUT * RATE)
    x = x.copy()
    x[:fi] *= np.linspace(0, 1, fi, endpoint=False)
    x[-fo:] *= np.linspace(1, 0, fo)
    return x


def write(x, path):
    subprocess.run(['ffmpeg', '-v', 'error', '-y', '-f', 'f32le', '-ar', str(RATE), '-ac', '1', '-i', '-',
                    '-c:a', 'libvorbis', '-q:a', '7', str(path)], input=x.astype(np.float32).tobytes(), check=True)


def second_syllable(x):
    """Where the shout's second syllable starts: after the first, past the stop between the two (in 2.5 ms windows:
    the first loud one, the first quiet one after it, 10 dB under the loudest, and the first loud one after that)."""
    w = int(.0025 * RATE)
    level = np.array([np.sqrt(np.mean(x[i:i + w] ** 2)) for i in range(0, len(x) - w, w)])
    loud = level >= level.max() * 10 ** (-10 / 20)
    k = int(np.argmax(loud))
    k += int(np.argmax(~loud[k:]))
    k += int(np.argmax(loud[k:]))
    return k * w


def main():
    if len(sys.argv) < 3:
        sys.exit(__doc__)
    out = Path(sys.argv[3]) if len(sys.argv) > 3 else Path(__file__).resolve().parents[2] / 'src/main/resources/assets/celestialfall/sounds'
    out.mkdir(parents=True, exist_ok=True)

    # R: the shout, its second syllable DA_AT into the file
    r = decode(Path(sys.argv[1]))
    first, last = ends(r)
    r = r[first:last]
    da = second_syllable(r)
    pad = int(DA_AT * RATE) - da
    if pad < 0:
        sys.exit(f'the second syllable is {da / RATE:.3f} s in: more than {DA_AT} s')
    r = np.concatenate([np.zeros(pad), fade(r)])
    g = 10 ** ((LOUD_R - lufs(r)) / 20)
    write(r * g, out / 'gold_experience_overdrive.ogg')
    print(f'gold_experience_overdrive.ogg: {len(r) / RATE:.3f} s, the shout from {first / RATE:.3f} s of the recording, '
          f'its second syllable {da / RATE:.3f} s into it ({DA_AT} s into the file), gain {20 * np.log10(g):+.2f} dB, '
          f'peak {20 * np.log10(np.abs(r * g).max()):+.2f} dBFS')

    # Y: the two healings, by one gain
    h = decode(Path(sys.argv[2]))
    parts = pieces(h)
    if len(parts) != 2:
        sys.exit(f'{len(parts)} sounds in the healing video, not 2')
    cuts = []
    for a, b in parts:
        seg = h[a:b]
        s, e = ends(seg)
        s = max(0, s - int(LEAD * RATE))
        cuts.append((a + s, fade(seg[s:e])))
    g = 10 ** ((LOUD_HEAL - lufs(cuts[0][1])) / 20)
    for (at, x), name in zip(cuts, ['gold_experience_mend', 'gold_experience_self_heal']):
        write(x * g, out / f'{name}.ogg')
        print(f'{name}.ogg: {len(x) / RATE:.3f} s from {at / RATE:.3f} s of the recording, gain {20 * np.log10(g):+.2f} dB, '
              f'{lufs(x * g):.1f} LUFS, peak {20 * np.log10(np.abs(x * g).max()):+.2f} dBFS')


if __name__ == '__main__':
    main()
