"""엑스 차라's G with the hack off, the slam ("G - Overwrite Slam"): its sounds, lifted out of the user's recording (2.128.0).
usage: python3 -I tools/xchara/extract_slam.py RECORDING.mp4 [OUT]

The user sent a screen recording of the skill used once, its sound under background music ("효과음은 영상에 있고"). The
recording is the user's and is not in the repository; only this tool is. Nothing is synthesised: what is written is the
recording's own sound.

What is in the recording (seconds into its sound, which runs about 0.4 s behind its picture):
  0.575-0.75   a faint hiss coming on (5 to 20 kHz), leading into
  0.75-1.45    the knife's lighting: a bright burst that comes on within 15 ms, 2 to 20 kHz, some 23 dB over the
               music, dying away over half a second, with a lower knock under its end (120 to 800 Hz, 1.12-1.25)
  1.80-2.15    the leap: a rush in the middle of the band (200 to 800 Hz), 10 dB over the music
  4.085-7.7    the landing: a very deep boom (50 to 100 Hz from 4.085, a knock across the band at 4.13) and its long
               low rumble (25 and 60 to 70 Hz), 25 dB over the music, dying away to the recording's end
  the music    quiet here (25 dB and more under the lighting and the landing): a kick under 150 Hz about once a
               second, a held note near 620 Hz, wide (stereo) pads
  2.3-2.9      a few small sounds after the leap, no louder than the music's own notes: not told from it, left out

It plays once, so the plays cannot be laid over each other as for the knife's (extract_slash.py). The music is so far
under these three that little is done to them (taking more would take the effect: the double's sounds were made dull
that way once): the music's usual level by frequency in the middle of the stereo picture, where no effect plays, is
taken off the middle (once for the landing, a little more for the two quieter ones), and the music's kick under 150 Hz
is taken off the lighting and the leap (the landing's deep boom keeps its low end: it is the effect there). Each is cut
to its time and faded out.

  xchara_slam_lit    from the hiss on: the burst's onset is 0.185 s into it. Started as the skill starts, the burst is
                     there as the knife has come full on (client/xchara/KnifeClient)
  xchara_slam_leap   the rush's onset 0.05 s into it: started a tick before the leap
  xchara_slam_land   the knock across the band 0.055 s into it: started a tick before the landing
Mono 48 kHz. One gain for all three (the recording's own balance between them: the leap's rush is the quietest, as
it is there), set so the loudest peak, the lighting's, is some 2 dB under full.
"""
import os
import subprocess
import sys
import tempfile

import numpy as np
from scipy import signal
from scipy.io import wavfile
from scipy.ndimage import median_filter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from synth import CEILING_DB, limit, loudness  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SR = 48000
N, HOP = 2048, 96
# where no effect plays (the music alone)
QUIET = [(.02, .45), (2.95, 3.12), (3.30, 3.60)]
# each sound: its start and end in the recording, where its fade out begins, how long its fade in is, how many times
# the music's usual level is taken off it, and under what its low end is taken off (Hz)
CUTS = {
    'xchara_slam_lit': (.565, 1.62, 1.30, .010, 1.5, 150.),
    'xchara_slam_leap': (1.755, 2.24, 2.10, .020, 1.5, 150.),
    'xchara_slam_land': (4.075, 7.78, 6.90, .008, 1.0, 22.),
}
# the one gain of all three (dB)
GAIN_DB = 8.0


def decode(video):
    """The recording's sound: stereo, 48 kHz, as floats."""
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, 'x.wav')
        subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', video, '-vn', '-ac', '2', '-ar', str(SR), wav], check=True)
        _, x = wavfile.read(wav)
    return x.astype(float) / 32768


def smooth(v):
    v = np.clip(v, 0, 1)
    return v * v * (3 - 2 * v)


def middle(x):
    """The recording's middle and the music's usual level in it by frequency: (f, t, spectrum, level)."""
    f, t, left = signal.stft(x[:, 0], SR, nperseg=N, noverlap=N - HOP)
    right = signal.stft(x[:, 1], SR, nperseg=N, noverlap=N - HOP)[2]
    mid = (left + right) / 2
    quiet = np.zeros_like(t, bool)
    for a, b in QUIET:
        quiet |= (t > a) & (t < b)
    level = median_filter(np.median(np.abs(mid[:, quiet]) ** 2, 1), size=9, mode='nearest')
    return f, t, mid, level


def cut(x, parts, t0, t1, fade, lead, over, low):
    """One sound: the middle from t0 to t1 with the music's usual level taken off it and its low end under low."""
    f, t, mid, level = parts
    pm = np.abs(mid) ** 2
    c = np.sqrt(np.maximum(1 - over * level[:, None] / (pm + 1e-18), .05 ** 2)) * mid
    c = c * (1 / (1 + (low / np.maximum(f, 1)) ** 8))[:, None]
    y = signal.istft(c, SR, nperseg=N, noverlap=N - HOP)[1][:len(x)]
    s = np.arange(len(y)) / SR
    y = y * smooth((s - t0) / lead) * (1 - smooth((s - fade) / (t1 - fade)))
    return y[int(t0 * SR):int(t1 * SR)]


def finish(e):
    return limit(e * 10 ** (GAIN_DB / 20), 10 ** (CEILING_DB / 20))


def band(y, a, b, lo, hi):
    f, t, z = signal.stft(y, SR, nperseg=N, noverlap=N - HOP)
    return 10 * np.log10((np.abs(z[(f >= lo) & (f < hi)][:, (t >= a) & (t < b)]) ** 2).mean() + 1e-18)


def write(y, out, name):
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, 'x.wav')
        wavfile.write(wav, SR, (np.clip(y, -1, 1) * 32767).astype(np.int16))
        subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', wav, '-ac', '1', '-ar', str(SR), '-c:a', 'libvorbis', '-q:a', '7', os.path.join(out, name + '.ogg')], check=True)
    print(f'{name}: {len(y) / SR:.3f} s, {loudness(y):.1f} LUFS, peak {20 * np.log10(np.abs(y).max()):.1f} dB')


def main():
    video = sys.argv[1]
    out = sys.argv[2] if len(sys.argv) > 2 else os.path.join(ROOT, 'src/main/resources/assets/celestialfall/sounds')
    x = decode(video)
    parts = middle(x)
    m = x.mean(1)
    made = {}
    for name, (t0, t1, fade, lead, over, low) in CUTS.items():
        made[name] = (cut(x, parts, t0, t1, fade, lead, over, low), t0)
    # what was kept and what was taken down, against the recording's middle (dB)
    checks = [('xchara_slam_lit', "the lighting, 2-20 kHz", .76, 1.0, 2000, 20000), ('xchara_slam_lit', "the lighting, 150 Hz-2 kHz", .76, 1.25, 150, 2000),
              ('xchara_slam_lit', "the kick under it", .76, 1.3, 30, 120), ('xchara_slam_leap', "the leap, 150-1000 Hz", 1.8, 2.14, 150, 1000),
              ('xchara_slam_leap', "the leap, over 1 kHz", 1.8, 2.14, 1000, 16000), ('xchara_slam_leap', "the kick under it", 1.8, 2.14, 30, 120),
              ('xchara_slam_land', "the landing, 25-150 Hz", 4.1, 5.5, 25, 150), ('xchara_slam_land', "the landing, 150 Hz-2 kHz", 4.1, 4.6, 150, 2000),
              ('xchara_slam_land', "the landing, over 2 kHz", 4.1, 4.6, 2000, 16000), ('xchara_slam_land', "its rumble, 25-150 Hz", 5.5, 6.8, 25, 150)]
    for name, what, a, b, lo, hi in checks:
        y, t0 = made[name]
        full = np.zeros_like(m)
        i = int(t0 * SR)
        full[i:i + len(y)] = y
        print(f'{what:28s} {band(full, a, b, lo, hi) - band(m, a, b, lo, hi):+6.1f} dB')
    os.makedirs(out, exist_ok=True)
    for name, (y, _) in made.items():
        write(finish(y), out, name)


if __name__ == '__main__':
    main()
