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

It plays once, so the plays cannot be laid over each other as for the knife's (extract_slash.py).

2.128.1 (the user, of 2.128.0's: "처음과 끝에 노랫소리가 들리고"): 2.128.0 took only the music's usual level off the
middle and kept every stretch whole, so where an effect was faint or only very low, the music's notes (louder than
its usual level, and in the middle of the band) were what was heard: the lighting's first 0.18 s (the hiss is over
2 kHz: under it there was only music) and its last 0.3 s, and the landing's last 2.5 s (the rumble there is under
100 Hz, which small speakers do not play: what they played was the music over it). Now:
  - the music as it is at each moment is taken off, not only its usual level: the music is wide (as loud at the
    sides of the stereo picture as in its middle) and the effects are all in the middle, so what is at the sides at
    a moment, by the music's own middle-to-side ratio at each frequency, is what of the middle is music then;
  - each sound keeps only the band its effect is in at each time: the lighting's hiss over 2.5 kHz until the burst,
    then all over 150 Hz; the landing all of it for its knock, then down to under 200 Hz for its rumble;
  - each ends where its effect has gone down to the music: the lighting after its knock (1.40), the landing's
    rumble faded out by 6.9 (after that the music's kick is as loud as it).
Nothing is added; less of the recording is kept.

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
from scipy.ndimage import median_filter, uniform_filter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from synth import CEILING_DB, limit, loudness  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SR = 48000
N, HOP = 2048, 96
# where no effect plays (the music alone)
QUIET = [(.02, .45), (2.95, 3.12), (3.30, 3.60)]
# each sound: its start and end in the recording, where its fade out begins, how long its fade in is, how many times
# the music's usual level is taken off it, and the band kept of it by time: (time, low edge Hz, high edge Hz) points,
# the edges going from one to the next evenly in pitch
CUTS = {
    'xchara_slam_lit': (.565, 1.40, 1.27, .010, 2.0, [(.565, 2500., 24000.), (.735, 2500., 24000.), (.75, 150., 24000.)]),
    'xchara_slam_leap': (1.755, 2.24, 2.10, .020, 1.5, [(1.755, 150., 24000.)]),
    'xchara_slam_land': (4.075, 6.90, 5.90, .008, 1.0, [(4.075, 22., 24000.), (4.65, 22., 24000.), (5.25, 22., 200.)]),
}
# how much of what is at the sides, by the music's own middle-to-side ratio, is taken as music in the middle
WIDE = 1.6
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
    """The recording's middle, the music's usual level in it by frequency, and the music in it at each moment as its sides tell it: (f, t, spectrum, level, music)."""
    f, t, left = signal.stft(x[:, 0], SR, nperseg=N, noverlap=N - HOP)
    right = signal.stft(x[:, 1], SR, nperseg=N, noverlap=N - HOP)[2]
    mid, side = (left + right) / 2, (left - right) / 2
    quiet = np.zeros_like(t, bool)
    for a, b in QUIET:
        quiet |= (t > a) & (t < b)
    pm, ps = np.abs(mid) ** 2, np.abs(side) ** 2
    level = median_filter(np.median(pm[:, quiet], 1), size=9, mode='nearest')
    # the music's middle against its sides by frequency (where no effect plays), and so the music in the middle at
    # each moment (smoothed a little in time and frequency: one frame's sides are a noisy word)
    ratio = median_filter(pm[:, quiet].mean(1) / (ps[:, quiet].mean(1) + 1e-18), size=15, mode='nearest')
    wide = uniform_filter(ps, size=(5, 9), mode='nearest') * np.minimum(ratio, 4.)[:, None]
    return f, t, mid, level, wide


def edges(t, band):
    """The band kept at each time: its low and high edges (Hz), going between the points evenly in pitch."""
    ts = np.array([p[0] for p in band])
    lo = np.exp(np.interp(t, ts, np.log([p[1] for p in band])))
    hi = np.exp(np.interp(t, ts, np.log([p[2] for p in band])))
    return lo, hi


def cut(x, parts, t0, t1, fade, lead, over, band):
    """One sound: the middle from t0 to t1 with the music taken off it (its usual level, and what the sides tell of it then), within its band."""
    f, t, mid, level, wide = parts
    pm = np.abs(mid) ** 2
    music = np.maximum(over * level[:, None], WIDE * wide)
    c = np.sqrt(np.maximum(1 - music / (pm + 1e-18), .03 ** 2)) * mid
    lo, hi = edges(t, band)
    ff = np.maximum(f, 1)[:, None]
    c = c / (1 + (lo[None, :] / ff) ** 8) / (1 + (ff / hi[None, :]) ** 8)
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
    for name, (t0, t1, fade, lead, over, kept) in CUTS.items():
        made[name] = (cut(x, parts, t0, t1, fade, lead, over, kept), t0)
    # what was kept and what was taken down, against the recording's middle (dB)
    checks = [('xchara_slam_lit', "the lighting, 2-20 kHz", .76, 1.0, 2000, 20000), ('xchara_slam_lit', "the lighting, 150 Hz-2 kHz", .76, 1.25, 150, 2000),
              ('xchara_slam_lit', "the kick under it", .76, 1.3, 30, 120), ('xchara_slam_lit', "its hiss, over 2.5 kHz", .6, .73, 2500, 20000),
              ('xchara_slam_lit', "the music under the hiss", .6, .72, 150, 2000),
              ('xchara_slam_leap', "the leap, 150-1000 Hz", 1.8, 2.14, 150, 1000),
              ('xchara_slam_leap', "the leap, over 1 kHz", 1.8, 2.14, 1000, 16000), ('xchara_slam_leap', "the kick under it", 1.8, 2.14, 30, 120),
              ('xchara_slam_land', "the landing, 25-150 Hz", 4.1, 5.5, 25, 150), ('xchara_slam_land', "the landing, 150 Hz-2 kHz", 4.1, 4.6, 150, 2000),
              ('xchara_slam_land', "the landing, over 2 kHz", 4.1, 4.6, 2000, 16000), ('xchara_slam_land', "its rumble, 25-150 Hz", 5.5, 5.9, 25, 150),
              ('xchara_slam_land', "the music over the rumble", 5.4, 6.8, 250, 16000)]
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
