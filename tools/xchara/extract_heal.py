"""엑스 차라's E with the hack on, the restore: its sound, lifted out of the user's recording (2.120.0).
usage: python3 -I tools/xchara/extract_heal.py RECORDING.mp4 [OUT]

The user sent a screen recording of the skill used once, its sound under background music ("힐 효과음 배경 음악빼고
추출해줘 '띡'하는 버튼 누르는 소리가 재생되고 힐 되는 효과음이 재생 돼"), and then asked for it as E's sound. The
recording is the user's and is not in the repository; only this tool is. Nothing is synthesised: what is written is the
recording's own sound with the music taken down.

What is in the recording (seconds into it; its first 0.025 s are digital silence):
  0.355-0.43   the press, "띡": three stepped square tones going up, 1688 Hz, 2250 Hz and 3000 Hz, each with its odd
               harmonics (5.0, 8.4 kHz; 6.7, 11.2 kHz; 9.0, 15.0 kHz)
  0.40-0.75    the heal: a tone warbling fast between about 450 and 900 Hz with harmonics up to 6 kHz, and a shimmer
               over it dying away
  the music    a kick and bass under 300 Hz, held notes, and wide (stereo) percussion and pads

The sound plays once, so the plays cannot be laid over each other as for the knife's (extract_slash.py). The music is
taken down by what tells it from the effect:
  1. the effect is in the middle of the stereo picture, much of the music is not: the music's share of the middle is
     estimated from the side (their usual ratio by frequency where the effect is not playing) and taken off (Wiener)
  2. the music's notes hold one pitch; the effect's tones are short steps and a fast warble: what stays on one pitch
     for 75 ms is taken off (but the press's own tones and odd harmonics, kept whole)
  3. under 250 Hz is the music's kick and bass: taken off (the effect has nothing there)
  4. cut to the effect's time (0.345-0.80 s), faded in over 10 ms and out over the last 0.1 s (there it cannot be told
     from the music)
What cannot be told apart stays: music in the middle, at the effect's pitch, at its moment.

The file starts 10 ms before the press. The client plays it SOUND_AT ticks into the skill (client/xchara/HealClient:
0.35 s), so the press sounds as the hand comes down on the panel (0.36 s) and the heal's tone starts as the heal lands
(0.40 s: XCharaRules.HEAL_AT), as in the recording. Mono 48 kHz, brought to its loudness under a limiter that keeps
peaks below -1 dB.
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
# the effect's time in the recording, and where its fade out begins
T0, T1, FADE = .345, .80, .70
# the press's three tones: from, to (seconds), pitch (Hz)
PRESS = [(.348, .374, 1688.), (.364, .404, 2250.), (.392, .438, 3000.)]
# the heal's shimmer begins; its tone begins; nothing of the press is under this pitch
SHIMMER, TONE, PRESS_LOW = .382, .392, 1400.
LUFS = -18.0


def decode(video):
    """The recording's sound: stereo, 48 kHz, as floats."""
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, 'x.wav')
        subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', video, '-vn', '-ac', '2', '-ar', str(SR), wav], check=True)
        _, x = wavfile.read(wav)
    return x.astype(float) / 32768


def extract(x):
    """The effect out of the stereo recording `x`: the recording's samples from T0 to T1 with the music taken down."""
    stft = lambda y: signal.stft(y, SR, nperseg=N, noverlap=N - HOP)
    f, t, left = stft(x[:, 0])
    right = stft(x[:, 1])[2]
    mid, side = (left + right) / 2, (left - right) / 2
    pm, ps = np.abs(mid) ** 2, np.abs(side) ** 2
    # 1. the music's middle from its side
    quiet = ((t > .05) & (t < .33)) | ((t > .90) & (t < 1.30)) | ((t > 1.5) & (t < 1.75))
    ratio = np.median(pm[:, quiet], 1) / (np.median(ps[:, quiet], 1) + 1e-18)
    ratio = np.clip(median_filter(ratio, size=15, mode='nearest'), .1, 4.)
    music = ratio[:, None] * uniform_filter(ps, size=(5, 9), mode='nearest')
    c = np.maximum(1 - 1.6 * music / (pm + 1e-18), .03) * mid
    # 2. held pitches
    a = np.abs(c)
    held = median_filter(a, size=(1, int(round(.075 / (HOP / SR))) | 1), mode='nearest')
    around = median_filter(a, size=(int(round(330 / (SR / N))) | 1, 1), mode='nearest')
    g = 1 - np.clip((held / (around + 1e-12) - 1.6) / 1.4, 0, 1) * .97
    g = np.minimum(g, np.roll(g, 1, 0))
    g = np.minimum(g, np.roll(g, -1, 0))
    # the press: each tone's pitch and odd harmonics whole, the rest of that moment the music's
    keep = np.zeros_like(g, bool)
    for a0, b0, f0 in PRESS:
        k = np.round(f[:, None] / f0)
        keep |= (k % 2 == 1) & (np.abs(f[:, None] - k * f0) < 150) & (t[None, :] > a0) & (t[None, :] < b0)
    g = np.where(keep, 1, g)
    g = np.where((t[None, :] < SHIMMER) & ~keep, g * .05, g)
    g = np.where((t[None, :] < TONE) & (f[:, None] < PRESS_LOW), g * .03, g)
    c *= g
    # 3. the low end
    c *= (1 / (1 + (250 / np.maximum(f, 1)) ** 8))[:, None]
    y = signal.istft(c, SR, nperseg=N, noverlap=N - HOP)[1][:len(x)]
    # 4. its time
    s = np.arange(len(y)) / SR
    smooth = lambda v: np.clip(v, 0, 1) ** 2 * (3 - 2 * np.clip(v, 0, 1))
    y *= smooth((s - T0) / .010) * (1 - smooth((s - FADE) / (T1 - FADE)))
    return y[int(T0 * SR):int(T1 * SR)]


def finish(e):
    ceiling = 10 ** (CEILING_DB / 20)
    for _ in range(4):
        e = limit(e * 10 ** ((LUFS - loudness(e)) / 20), ceiling)
    return e


def band(y, a, b, lo, hi):
    f, t, z = signal.stft(y, SR, nperseg=N, noverlap=N - HOP)
    return 10 * np.log10((np.abs(z[(f >= lo) & (f < hi)][:, (t >= a) & (t < b)]) ** 2).mean() + 1e-18)


def main():
    video = sys.argv[1]
    out = sys.argv[2] if len(sys.argv) > 2 else os.path.join(ROOT, 'src/main/resources/assets/celestialfall/sounds')
    x = decode(video)
    e = extract(x)
    # what was kept and what was taken down, against the recording's middle (dB)
    m = x.mean(1)
    full = np.zeros_like(m)
    full[int(T0 * SR):int(T0 * SR) + len(e)] = e
    for name, a, b, lo, hi in [("the music's kick", .50, .62, 30, 250), ("a held note of the music's", .65, .70, 1150, 1300),
                               ("the press's first tone", .355, .37, 1600, 1800), ("its second", .37, .40, 2150, 2350), ("its third", .40, .43, 2900, 3100),
                               ("the heal's tone", .40, .65, 400, 1000), ("its harmonics", .42, .65, 1200, 6000), ("its shimmer", .45, .70, 6000, 12000)]:
        print(f'{name:28s} {band(full, a, b, lo, hi) - band(m, a, b, lo, hi):+6.1f} dB')
    y = finish(e)
    os.makedirs(out, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, 'x.wav')
        wavfile.write(wav, SR, (np.clip(y, -1, 1) * 32767).astype(np.int16))
        subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', wav, '-ac', '1', '-ar', str(SR), '-c:a', 'libvorbis', '-q:a', '7', os.path.join(out, 'xchara_e.ogg')], check=True)
    print(f'xchara_e: {len(y) / SR:.3f} s, {loudness(y):.1f} LUFS, peak {20 * np.log10(np.abs(y).max()):.1f} dB')


if __name__ == '__main__':
    main()
