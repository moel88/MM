"""Star Platinum's four basic-attack voices, cut from the user's recording (2.54.0, user request: "평타 소리는 내가 보낸
파일이랑 싱크 맞게 해줘"). The recording holds four shouts of "ORA!" (the first two the same short one, the last two
long, "Ooo-RAAA"); each punch of the four-hit combo plays its own, so that the "RA" of its shout falls on the punch.

usage: python3 -I tools/stand/cut_ora.py RECORDING.mp3 [OUT_DIR]

The recording is not changed: each clip is a stretch of it (both channels are the same, averaged into one), 3 ms faded
in and 10 ms faded out, all four scaled by the one gain that puts the loudest sample 1 dB under full scale, with
silence before it where the stretch would reach back into the shout before. In each clip the "RA" (where the flap of
the r ends and the open "a" begins, read off the waveform and the spectrum: 0.250, 1.030, 1.930 and 3.145 s into the
recording) sits RA_AT seconds in. The game starts a clip one tick after its punch begins (StandAttack.LEAD), so the
RA lands on the punch's contact, StarPlatinumRules.CONTACT ticks after it begins: (CONTACT - LEAD) / 20 s.
"""
import subprocess
import sys
from pathlib import Path

import numpy as np

RATE = 48000
# where each shout's "RA" begins in the recording (seconds)
RA = (0.250, 1.030, 1.930, 3.145)
# the contact of each hit (ticks after it begins, StarPlatinumRules.CONTACT) and the tick the clip starts on
CONTACT = (6, 6, 9, 10)
LEAD = 1
RA_AT = tuple((c - LEAD) / 20 for c in CONTACT)
# each clip may reach back to here and no further (the tail of the shout before, or a click before the second shout,
# ends there) and ends here (its own tail has died away, -70 dB)
CLEAN_FROM = (0.0, 0.790, 1.490, 2.680)
END = (0.720, 1.500, 2.680, 3.800)
FADE_IN, FADE_OUT = .003, .010
PEAK_DB = -1.0


def decode(path):
    raw = subprocess.run(['ffmpeg', '-v', 'error', '-i', str(path), '-ac', '2', '-ar', str(RATE), '-f', 'f32le', '-'],
                         check=True, capture_output=True).stdout
    x = np.frombuffer(raw, dtype=np.float32).reshape(-1, 2).astype(np.float64)
    return x.mean(axis=1)


def cut(x, k):
    start = RA[k] - RA_AT[k]
    begin = max(start, CLEAN_FROM[k])
    pad = int(round((begin - start) * RATE))
    a, b = int(round(begin * RATE)), int(round(END[k] * RATE))
    clip = np.concatenate([np.zeros(pad), x[a:b]])
    fi, fo = int(FADE_IN * RATE), int(FADE_OUT * RATE)
    clip[pad:pad + fi] *= np.linspace(0, 1, fi, endpoint=False)
    clip[-fo:] *= np.linspace(1, 0, fo)
    return clip


def main():
    src = Path(sys.argv[1])
    out = Path(sys.argv[2]) if len(sys.argv) > 2 else Path(__file__).resolve().parents[2] / 'src/main/resources/assets/celestialfall/sounds'
    x = decode(src)
    clips = [cut(x, k) for k in range(4)]
    gain = 10 ** (PEAK_DB / 20) / max(np.abs(c).max() for c in clips)
    out.mkdir(parents=True, exist_ok=True)
    for k, clip in enumerate(clips):
        pcm = (clip * gain).astype(np.float32).tobytes()
        name = out / f'star_platinum_ora_{k + 1}.ogg'
        subprocess.run(['ffmpeg', '-v', 'error', '-y', '-f', 'f32le', '-ar', str(RATE), '-ac', '1', '-i', '-',
                        '-c:a', 'libvorbis', '-q:a', '7', str(name)], input=pcm, check=True)
        print(f'{name.name}: {len(clip) / RATE:.3f} s, RA at {RA_AT[k]:.3f} s (recording {RA[k]:.3f} s), '
              f'silence first {max(0.0, CLEAN_FROM[k] - (RA[k] - RA_AT[k])) * 1000:.0f} ms')
    print(f'gain {20 * np.log10(gain):+.2f} dB')


if __name__ == '__main__':
    main()
