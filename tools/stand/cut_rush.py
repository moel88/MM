"""Star Platinum's rush (E, 2.57.0): its sound, from the two recordings the user sent ("mp4로 소리 보내줄게 사람 맞을때버전하고
그냥 쓸 때 버전으로 나눠놨어"): "Ora Rush 2 (NO SFX)", the voice alone, for a rush that strikes no one, and "Ora Rush 2
(SFX)", the same voice with the blows on it, for one that strikes someone.

usage: python3 -I tools/stand/cut_rush.py NO_SFX.mp4 SFX.mp4 [OUT_DIR]

The voice is the same in both (the one with blows has it 0.22 s earlier, at the same loudness): the blows are what the
second has over the first. So the game plays two sounds together from the rush's start (client/stand/StandRush): the
voice (star_platinum_rush.ogg, the first recording) all through, and the blows (star_platinum_rush_hits.ogg: the second
recording less the voice, set exactly where it is in it) as loud as the rush is striking someone now. Striking, the two
together are the second recording; striking no one, the voice alone is the first. The voice is never cut or crossfaded,
so it never doubles or phases as the blows come and go.

Nothing else is changed: both are mono (each recording's two channels averaged) at 48 kHz, cut from the same moment, a
little before the voice starts, to where each has died away, 3 ms faded in and 10 ms out, and scaled by one gain, the one
that puts the loudest sample of the second recording 1 dB under full scale (as the M1 shouts, tools/stand/cut_ora.py),
so the two keep the balance the user gave them. The script checks that the two written files add up to the second
recording again (Vorbis aside) and that no voice is left in the blows.
"""
import subprocess
import sys
from pathlib import Path

import numpy as np
from scipy import signal

RATE = 48000
# a little silence kept before the voice (seconds): the game starts the sounds a tick after the rush does
PRE = 0.03
# each part ends where it has died away (-60 dB under its peak) and this much after
TAIL = 0.05
FADE_IN, FADE_OUT = .003, .010
PEAK_DB = -1.0


def decode(path, channels=2):
    """A recording as one channel: its two averaged (a mono file is read as it is: spread to two, it would be 3 dB down)."""
    raw = subprocess.run(['ffmpeg', '-v', 'error', '-i', str(path), '-ac', str(channels), '-ar', str(RATE), '-f', 'f32le', '-'],
                         check=True, capture_output=True).stdout
    return np.frombuffer(raw, dtype=np.float32).reshape(-1, channels).astype(np.float64).mean(axis=1)


def place(plain, hits):
    """Where the voice of the first recording sits in the second (samples, to a fraction), and how loud it is there."""
    c = signal.fftconvolve(hits, plain[::-1], 'full')
    k = int(np.argmax(c))
    y0, y1, y2 = c[k - 1], c[k], c[k + 1]
    lag = k - (len(plain) - 1) + 0.5 * (y0 - y2) / (y0 - 2 * y1 + y2)
    n = len(hits)
    padded = np.zeros(n)
    m = min(len(plain), n)
    padded[:m] = plain[:m]
    shifted = np.fft.irfft(np.fft.rfft(padded) * np.exp(-2j * np.pi * np.fft.rfftfreq(n) * lag), n)
    gain = float(np.dot(hits, shifted) / np.dot(shifted, shifted))
    return lag, gain, shifted * gain


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


def main():
    plain_src, hits_src = Path(sys.argv[1]), Path(sys.argv[2])
    out = Path(sys.argv[3]) if len(sys.argv) > 3 else Path(__file__).resolve().parents[2] / 'src/main/resources/assets/celestialfall/sounds'
    plain, both = decode(plain_src), decode(hits_src)
    lag, gain, voice = place(plain, both)
    blows = both - voice
    start = max(0, ends(voice)[0] - int(PRE * RATE))
    voice_end = min(len(voice), ends(voice)[1] + int(TAIL * RATE))
    blows_end = min(len(blows), ends(blows)[1] + int(TAIL * RATE))
    g = 10 ** (PEAK_DB / 20) / np.abs(both[start:max(voice_end, blows_end)]).max()
    parts = {'star_platinum_rush.ogg': fade(voice[start:voice_end] * g), 'star_platinum_rush_hits.ogg': fade(blows[start:blows_end] * g)}
    out.mkdir(parents=True, exist_ok=True)
    for name, x in parts.items():
        write(x, out / name)
        print(f'{name}: {len(x) / RATE:.3f} s, peak {20 * np.log10(np.abs(x).max()):.1f} dBFS')
    print(f'the voice sits {lag / RATE * 1000:+.2f} ms into the recording with blows, at {20 * np.log10(gain):+.2f} dB; '
          f'both cut from {start / RATE:.3f} s of it (the voice starts {PRE * 1000:.0f} ms in); gain {20 * np.log10(g):+.2f} dB')
    # checks: the blows hold no voice, and the two files add up to the recording with blows again
    band = signal.butter(4, [1500, 5000], 'bp', fs=RATE, output='sos')
    v, b = signal.sosfilt(band, voice), signal.sosfilt(band, blows)
    print(f'voice left in the blows (1.5-5 kHz): {20 * np.log10(abs(np.dot(b, v) / np.dot(v, v)) + 1e-12):.1f} dB')
    written = [decode(out / name, 1) for name in parts]
    n = max(len(w) for w in written)
    total = sum(np.pad(w, (0, n - len(w))) for w in written)
    want = np.pad(both[start:start + n] * g, (0, max(0, n - len(both[start:start + n]))))[:n]
    err = total - want
    print(f'the two written added up against the recording with blows: error {10 * np.log10(np.mean(err ** 2) / np.mean(want ** 2)):.1f} dB')


if __name__ == '__main__':
    main()
