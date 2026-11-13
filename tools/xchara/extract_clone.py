"""엑스 차라's R with the hack on, the double: two of its three sounds, lifted out of the user's recording.
usage: python3 -I tools/xchara/extract_clone.py RECORDING.mp4 [OUT]

The user sent a screen recording of the skill used twice, its sound under background music ("효과음은 영상을 참고해 2번
재생할거야"). The recording is the user's and is not in the repository; only this tool is. Nothing is synthesised: what is
written is the recording's own sound with the music taken down.

What is in the recording (seconds into its sound, which runs about 0.24 s behind its picture), each use:
  1.05-1.38  the double coming: a glitching tone, 900 Hz to 16 kHz          (second use 5.2327 s later)
  1.775-2.14 the double's throw: a tone near 2.9 kHz and its overtones, dying away (second use 5.2327 s later: 0.70 s
             after the first sound both times)
  3.20-3.7   the one thrown going into the wall: a crash of noise (second use 4.87 s later, and not the same noise)
  the music  a kick and bass under 150 Hz, held notes, wide (stereo) pads

These two play alike both times (the two plays line up to the sample), so the second play tells what in the first is
music: what the plays do not agree on. 2.125.0 took their mean and turned every cell down by their difference, and the
user heard it muffled ("지금 너무 먹먹해"): over about 5 kHz the recording's codec has the same sound in the two plays
out of step (they agree 0.5 and less over 8 kHz), so the mean and the difference both took the top off it. 2.125.1:
the plays are laid over each other only up to AGREE_FROM Hz; over AGREE_TO it is the first play as it is. And the music
is taken off by its own loudness instead (how loud it gets where no effect plays, per frequency): a cell is kept where
both plays stand over that, and turned down where they do not. Under COME_LOW and THROW_LOW the effects have nothing
and it is all music: cut. (The user, of the less worked cuts 2 and 3 I played them: "2 3번 다 좋은데 노래소리가 들리는게
문제임"; of this one, the crash apart: fine.)

The crash is not made here any more. It plays once under the music, nothing tells its noise from the music's, and the
music could be heard in it whatever was taken off ("마지막 삥 소리에 노래가 들림 그냥 벽에 박히는 소리만 창작해줘"):
tools/xchara/make_clone_wall.py makes that sound anew out of recordings.

xchara_clone starts 10 ms before the first sound; the client plays it as the double comes (CloneRules.CLONE).
xchara_clone_throw starts 50 ms before its onset; the client plays it a tick before the throw, so the onset is on it.
Mono 48 kHz, each brought to its loudness under a limiter that keeps peaks below -1 dB.
"""
import os
import sys

import numpy as np
from scipy import signal
from scipy.ndimage import median_filter, uniform_filter
from scipy.signal import butter, correlate, sosfiltfilt

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from extract_jab import SR, band, decode, finish, smooth, write  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
# the two sounds that play alike both times, in the first use: from, to, where the fade out begins (seconds)
COME = (1.040, 1.43, 1.37)
THROW_ONSET, THROW_LEAD, THROW_END, THROW_FADE = 1.775, .050, 2.24, 2.10
# about how much later the second use plays them (found to the sample by the band above 1.5 kHz)
LATER = 5.23
# where no effect plays (the music alone): for the middle's usual ratio to the side
QUIET = [(.2, .9), (1.46, 1.72), (2.35, 3.1), (3.9, 4.6)]
LUFS_COME, LUFS_THROW = -19.0, -16.0
# under these the effects have nothing and it is all music (the two plays agree 0.2 and less under 900 Hz in the first
# sound; the throw's sound is a tone near 2.9 kHz and its overtones)
COME_LOW, THROW_LOW = 850, 2200
# the two plays are laid over each other up to here, fading into the first play alone by there (Hz)
AGREE_FROM, AGREE_TO = 3400, 4800
# what is kept is what stands this many times over the music's loudness alone; the least a cell is turned down to
OVER, FLOOR = 2.0, .05
N, HOP = 1024, 128


def line_up(m):
    """How many samples later the second use plays the first two sounds, and how loud against the first."""
    hp = sosfiltfilt(butter(4, 1500, 'hp', fs=SR, output='sos'), m)
    a0, a1 = int(1.0 * SR), int(2.2 * SR)
    s0 = hp[a0:a1]
    b0 = int((1.0 + LATER - .1) * SR)
    s1 = hp[b0:b0 + len(s0) + int(.2 * SR)]
    c = correlate(s1, s0, 'valid')
    k = int(np.argmax(c))
    seg = s1[k:k + len(s0)]
    return b0 + k - a0, float(seg @ s0 / (s0 @ s0)), float(c[k] / np.sqrt((s0 @ s0) * (seg @ seg)))


def stft(y):
    return signal.stft(y, SR, nperseg=N, noverlap=N - HOP)


def music_alone(m):
    """How loud the music gets in each frequency where no effect plays (its 80th percentile there)."""
    f, t, z = stft(m)
    quiet = np.zeros_like(t, bool)
    for a, b in QUIET:
        quiet |= (t > a) & (t < b)
    return median_filter(np.percentile(np.abs(z[:, quiet]) ** 2, 80, axis=1), size=9, mode='nearest')


def over_music(power, music):
    """The gain that keeps what stands over the music and turns down what is no louder than it (a spectral subtraction)."""
    return np.sqrt(np.clip(1 - OVER * music[:, None] / (power + 1e-18), FLOOR ** 2, 1))


def agreed(m, lag, gain, t0, t1, music):
    """
    What the two plays agree on between t0 and t1 of the first. Up to AGREE_FROM Hz, where the plays line up to the
    sample, their mean with each cell turned down by their difference. Over AGREE_TO Hz the recording's codec has the
    same sound in the two plays out of step (they agree 0.5 and less over 8 kHz), and their mean and their difference
    both took the top off it (2.125.0 was muffled: "지금 너무 먹먹해"): there it is the first play as it is. All of it
    is kept only where both plays stand over the music.
    """
    pad = .15
    a, b = int((t0 - pad) * SR), int((t1 + pad) * SR)
    p1, p2 = m[a:b], m[a + lag:b + lag] / gain
    f, _, z1 = stft(p1)
    z2 = stft(p2)[2]
    e = (z1 + z2) / 2
    blur = lambda p: uniform_filter(p, size=(3, 5), mode='nearest')
    # the mean's share of what the plays do not agree on (their difference is twice the music's spread)
    told = np.clip(1 - 1.5 * blur(np.abs(z1 - z2) ** 2 / 4) / (blur(np.abs(e) ** 2) + 1e-18), FLOOR, 1)
    low = np.clip((AGREE_TO - f) / (AGREE_TO - AGREE_FROM), 0, 1)[:, None]
    both = np.minimum(blur(np.abs(z1) ** 2), blur(np.abs(z2) ** 2))
    y = signal.istft((low * e * told + (1 - low) * z1) * over_music(both, music), SR, nperseg=N, noverlap=N - HOP)[1][:b - a]
    s = a / SR + np.arange(len(y)) / SR
    return y, s


def come(m, lag, gain, music):
    t0, t1, fade = COME
    y, s = agreed(m, lag, gain, t0, t1, music)
    y = sosfiltfilt(butter(4, COME_LOW, 'hp', fs=SR, output='sos'), y)
    y *= smooth((s - t0) / .008) * (1 - smooth((s - fade) / (t1 - fade)))
    return y[(s >= t0) & (s < t1)]


def throw(m, lag, gain, music):
    t0 = THROW_ONSET - THROW_LEAD
    y, s = agreed(m, lag, gain, t0, THROW_END, music)
    y = sosfiltfilt(butter(4, THROW_LOW, 'hp', fs=SR, output='sos'), y)
    y *= smooth((s - (THROW_ONSET - .014)) / .010) * (1 - smooth((s - THROW_FADE) / (THROW_END - THROW_FADE)))
    return y[(s >= t0) & (s < THROW_END)]


def main():
    video = sys.argv[1]
    out = sys.argv[2] if len(sys.argv) > 2 else os.path.join(ROOT, 'src/main/resources/assets/celestialfall/sounds')
    x = decode(video)
    m = x.mean(1)
    lag, gain, corr = line_up(m)
    print(f'the second use plays the first two sounds {lag / SR:.4f} s later, {gain:.2f} as loud; the plays agree {corr:.2f}')
    music = music_alone(m)
    a, b = come(m, lag, gain, music), throw(m, lag, gain, music)
    # what was kept and what was taken down, against the recording's middle (dB)
    full = np.zeros_like(m)
    for y, t0 in [(a, COME[0]), (b, THROW_ONSET - THROW_LEAD)]:
        full[int(t0 * SR):int(t0 * SR) + len(y)] = y
    for name, t0, t1, lo, hi in [("the double coming, 600 Hz-3 kHz", 1.12, 1.34, 600, 3000), ("the same, 3-6 kHz", 1.12, 1.34, 3000, 6000),
                                 ("the music under it, under 300 Hz", 1.06, 1.36, 30, 300), ("the throw, 1.5-6 kHz", 1.79, 2.0, 1500, 6000),
                                 ("the throw, 6-10 kHz", 1.79, 1.95, 6000, 10000), ("the music under it, under 500 Hz", 1.79, 2.1, 30, 500)]:
        print(f'{name:34s} {band(full, t0, t1, lo, hi) - band(m, t0, t1, lo, hi):+6.1f} dB')
    os.makedirs(out, exist_ok=True)
    write(finish(a, LUFS_COME), out, 'xchara_clone')
    write(finish(b, LUFS_THROW), out, 'xchara_clone_throw')


if __name__ == '__main__':
    main()
